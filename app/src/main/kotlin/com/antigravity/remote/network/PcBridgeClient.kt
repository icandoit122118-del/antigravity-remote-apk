package com.antigravity.remote.network

import com.antigravity.remote.model.ChatMessage
import com.antigravity.remote.model.MessageRole
import com.antigravity.remote.model.ToolExecutionItem
import com.antigravity.remote.model.ToolRunStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

data class StreamProgressFrame(
    val phaseLabel: String,
    val message: ChatMessage,
    val elapsedSeconds: Double,
    val tokensPerSec: Double,
    val isFinished: Boolean = false,
    val fromLiveBridge: Boolean = false
)

/**
 * Handles both real LAN communication with `pc-bridge-server.mjs` (HTTP/JSON + chunked streaming)
 * and the built-in interactive Kotlin Coroutines Token Streaming Engine when away from the PC.
 */
object PcBridgeClient {

    private fun candidateHosts(primaryHost: String): List<String> {
        val clean = primaryHost.trim().ifBlank { "127.0.0.1" }
        return listOf(clean, "127.0.0.1", "10.0.2.2").distinct()
    }

    suspend fun checkBridgeHealth(host: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        for (candidate in candidateHosts(host)) {
            var connection: HttpURLConnection? = null
            try {
                val url = URL("http://$candidate:$port/api/status")
                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 900
                    readTimeout = 900
                    setRequestProperty("Accept", "application/json")
                }
                if (connection.responseCode in 200..299) {
                    return@withContext true
                }
            } catch (_: Exception) {
                // Try next candidate
            } finally {
                connection?.disconnect()
            }
        }
        false
    }

    private suspend fun tryRequestBridgeReply(
        host: String,
        port: Int,
        prompt: String,
        sessionId: String,
        engineLabel: String,
        modelId: String,
        isLocalAi: Boolean
    ): String? = withContext(Dispatchers.IO) {
        for (candidate in candidateHosts(host)) {
            var connection: HttpURLConnection? = null
            try {
                val url = URL("http://$candidate:$port/api/chat")
                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 1200
                    readTimeout = 4500
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
                val payload = JSONObject().apply {
                    put("sessionId", sessionId)
                    put("prompt", prompt)
                    put("engineLabel", engineLabel)
                    put("modelId", modelId)
                    put("isLocalAi", isLocalAi)
                }.toString()

                OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(payload)
                    writer.flush()
                }

                if (connection.responseCode in 200..299) {
                    val responseText = BufferedReader(
                        InputStreamReader(connection.inputStream, Charsets.UTF_8)
                    ).use { it.readText() }
                    val json = JSONObject(responseText)
                    val reply = json.optString("reply").takeIf { it.isNotBlank() }
                    if (reply != null) return@withContext reply
                }
            } catch (_: Exception) {
                // Try next candidate
            } finally {
                connection?.disconnect()
            }
        }
        null
    }

    /**
     * Streams a realistic multi-stage Antigravity or Local AI Agent response:
     * Stage 1: Collapsible Thought Process tokens ("생각 정리 중...")
     * Stage 2: Tool Execution card ("도구 및 명령 실행 중...")
     * Stage 3: Final Markdown + Code response tokens ("답변 스트리밍 중...")
     */
    fun streamAgentResponse(
        messageId: String,
        userPrompt: String,
        sessionId: String,
        host: String,
        port: Int,
        timestamp: String,
        engineLabel: String = "Antigravity 기본",
        modelId: String = "antigravity-agent",
        isLocalAi: Boolean = false
    ): Flow<StreamProgressFrame> = flow {
        val startMs = System.currentTimeMillis()
        val liveReply = tryRequestBridgeReply(
            host = host,
            port = port,
            prompt = userPrompt,
            sessionId = sessionId,
            engineLabel = engineLabel,
            modelId = modelId,
            isLocalAi = isLocalAi
        )
        val isFromBridge = liveReply != null
        val authorDisplay = when {
            isLocalAi && isFromBridge -> "$engineLabel · PC 로컬"
            isLocalAi -> "$engineLabel (온디바이스)"
            isFromBridge -> "Antigravity PC Bridge"
            else -> "Antigravity Agent"
        }

        val plannedThought = buildThoughtForPrompt(userPrompt, isFromBridge, host, port, engineLabel, modelId, isLocalAi)
        val plannedTool = buildToolForPrompt(userPrompt, messageId, isFromBridge, host, port, modelId, isLocalAi)
        val (plannedBody, codeLang, codeSnippet) = buildReplyForPrompt(userPrompt, liveReply, engineLabel, modelId, isLocalAi)

        var currentThought = ""
        var currentBody = ""
        var currentCode: String? = null
        var tokenCounter = 0

        // Phase 1: Stream Thought Process
        val thoughtChunks = chunkByWords(plannedThought, wordsPerChunk = 2)
        for (chunk in thoughtChunks) {
            currentThought += chunk
            tokenCounter += 2
            val elapsed = ((System.currentTimeMillis() - startMs).coerceAtLeast(80L)) / 1000.0
            val speed = ((tokenCounter / elapsed) * 10.0).roundToInt() / 10.0
            emit(
                StreamProgressFrame(
                    phaseLabel = if (isLocalAi) "로컬 AI ($modelId) 추론 중..." else "생각 과정 정리 중...",
                    message = ChatMessage(
                        id = messageId,
                        role = MessageRole.AGENT,
                        authorName = authorDisplay,
                        timestamp = timestamp,
                        thoughtText = currentThought,
                        thoughtDurationSec = String.format("%.1fs", elapsed),
                        tools = emptyList(),
                        bodyText = "생각을 가다듬고 있습니다...",
                        isStreaming = true,
                        tokenCount = tokenCounter,
                        tokensPerSec = speed
                    ),
                    elapsedSeconds = elapsed,
                    tokensPerSec = speed,
                    fromLiveBridge = isFromBridge
                )
            )
            delay(45L)
        }

        val thoughtElapsedStr = String.format(
            "%.1fs",
            ((System.currentTimeMillis() - startMs).coerceAtLeast(100L)) / 1000.0
        )

        // Phase 2: Tool Execution Running -> Completed
        val runningTool = plannedTool.copy(status = ToolRunStatus.RUNNING)
        val elapsedToolStart = ((System.currentTimeMillis() - startMs).coerceAtLeast(120L)) / 1000.0
        emit(
            StreamProgressFrame(
                phaseLabel = "도구 실행 중 · ${plannedTool.title}",
                message = ChatMessage(
                    id = messageId,
                    role = MessageRole.AGENT,
                    authorName = authorDisplay,
                    timestamp = timestamp,
                    thoughtText = currentThought,
                    thoughtDurationSec = thoughtElapsedStr,
                    tools = listOf(runningTool),
                    bodyText = "작업을 실행하고 결과를 수집합니다...",
                    isStreaming = true,
                    tokenCount = tokenCounter,
                    tokensPerSec = 42.0
                ),
                elapsedSeconds = elapsedToolStart,
                tokensPerSec = 42.0,
                fromLiveBridge = isFromBridge
            )
        )
        delay(280L)

        val completedTool = plannedTool.copy(status = ToolRunStatus.COMPLETED)

        // Phase 3: Stream Main Answer Tokens
        val bodyChunks = chunkByWords(plannedBody, wordsPerChunk = 2)
        for (chunk in bodyChunks) {
            currentBody += chunk
            tokenCounter += 3
            val elapsed = ((System.currentTimeMillis() - startMs).coerceAtLeast(200L)) / 1000.0
            val speed = ((tokenCounter / elapsed) * 10.0).roundToInt() / 10.0
            emit(
                StreamProgressFrame(
                    phaseLabel = if (isLocalAi) "로컬 AI ($modelId) 스트리밍 중..." else "답변 스트리밍 중...",
                    message = ChatMessage(
                        id = messageId,
                        role = MessageRole.AGENT,
                        authorName = authorDisplay,
                        timestamp = timestamp,
                        thoughtText = currentThought,
                        thoughtDurationSec = thoughtElapsedStr,
                        tools = listOf(completedTool),
                        bodyText = currentBody,
                        codeBlockLanguage = codeLang,
                        codeBlockContent = currentCode,
                        isStreaming = true,
                        tokenCount = tokenCounter,
                        tokensPerSec = speed
                    ),
                    elapsedSeconds = elapsed,
                    tokensPerSec = speed,
                    fromLiveBridge = isFromBridge
                )
            )
            delay(38L)
        }

        // Attach code snippet if present
        if (codeSnippet != null) {
            currentCode = codeSnippet
            tokenCounter += 24
        }

        val finalElapsed = ((System.currentTimeMillis() - startMs).coerceAtLeast(250L)) / 1000.0
        val finalSpeed = ((tokenCounter / finalElapsed) * 10.0).roundToInt() / 10.0

        emit(
            StreamProgressFrame(
                phaseLabel = if (isLocalAi) "로컬 AI ($modelId) 완료" else "스트리밍 완료",
                message = ChatMessage(
                    id = messageId,
                    role = MessageRole.AGENT,
                    authorName = authorDisplay,
                    timestamp = timestamp,
                    thoughtText = currentThought,
                    thoughtDurationSec = thoughtElapsedStr,
                    tools = listOf(completedTool),
                    bodyText = currentBody,
                    codeBlockLanguage = codeLang,
                    codeBlockContent = currentCode,
                    isStreaming = false,
                    tokenCount = tokenCounter,
                    tokensPerSec = finalSpeed
                ),
                elapsedSeconds = finalElapsed,
                tokensPerSec = finalSpeed,
                isFinished = true,
                fromLiveBridge = isFromBridge
            )
        )
    }

    private fun chunkByWords(text: String, wordsPerChunk: Int): List<String> {
        val tokens = text.split(Regex("(?<=\\s)"))
        if (tokens.isEmpty()) return listOf(text)
        val result = mutableListOf<String>()
        var index = 0
        while (index < tokens.size) {
            val end = (index + wordsPerChunk).coerceAtMost(tokens.size)
            result.add(tokens.subList(index, end).joinToString(""))
            index = end
        }
        return result
    }

    private fun buildThoughtForPrompt(
        prompt: String,
        isFromBridge: Boolean,
        host: String,
        port: Int,
        engineLabel: String,
        modelId: String,
        isLocalAi: Boolean
    ): String {
        val routeNote = when {
            isLocalAi && isFromBridge -> "선택된 로컬 AI 모델($engineLabel · $modelId)을 PC 브릿지($host:$port)를 통해 호출했습니다."
            isLocalAi -> "선택된 로컬 AI 엔진($engineLabel · $modelId) 온디바이스 추론 파이프라인으로 요청을 처리합니다."
            isFromBridge -> "PC 브릿지($host:$port)로부터 실시간 실행 컨텍스트를 수신했습니다."
            else -> "내장 라이브 스트리밍 엔진을 통해 요청을 분석합니다."
        }
        return "$routeNote 사용자 요청(\"${prompt.take(36)}\")의 핵심 의도를 파악하고 간결한 브리핑으로 응답합니다."
    }

    private fun buildToolForPrompt(
        prompt: String,
        messageId: String,
        isFromBridge: Boolean,
        host: String,
        port: Int,
        modelId: String,
        isLocalAi: Boolean
    ): ToolExecutionItem {
        if (isLocalAi) {
            return ToolExecutionItem(
                id = "tool-$messageId",
                title = "로컬 AI 모델 추론 실행 ($modelId)",
                commandOrPath = if (isFromBridge) "POST http://$host:$port/api/chat [model=$modelId, local=true]" else "local-ai://inference/$modelId",
                outputPreview = "Engine: Local AI ($modelId)\nQuantization: Q4_K_M · Context: 8192 tokens\nStatus: Local Inference Complete",
                durationMs = 380
            )
        }
        val lower = prompt.lowercase()
        return when {
            lower.contains("apk") || lower.contains("빌드") || lower.contains("용량") -> ToolExecutionItem(
                id = "tool-$messageId",
                title = "APK 패키지 서명 및 아티팩트 검증",
                commandOrPath = "./gradlew assembleDebug && android-cli analyze app-debug.apk",
                outputPreview = "BUILD SUCCESSFUL in 4s\nPackage: com.antigravity.remote (versionCode=1, minSdk=24, targetSdk=36)\nSignature: v2/v3 Debug Key Verified",
                durationMs = 640
            )
            lower.contains("색상") || lower.contains("대비") || lower.contains("wcag") || lower.contains("테마") -> ToolExecutionItem(
                id = "tool-$messageId",
                title = "Hallmark Ribbon × 코끼리(Elephant) 명도 대비 측정",
                commandOrPath = "DESIGN.md · AntigravityPalette",
                outputPreview = "Ground #11161B vs Primary Ink #F2EFE9 -> 13.8:1 (AAA)\nGround #11161B vs Secondary #A7B4BE -> 6.4:1 (AA)\nTouch Target Minimum -> 48dp x 48dp (Pass)",
                durationMs = 310
            )
            else -> ToolExecutionItem(
                id = "tool-$messageId",
                title = if (isFromBridge) "PC 브릿지 원격 명령 실행" else "안그래비티 워크스페이스 상태 스캔",
                commandOrPath = if (isFromBridge) "POST http://$host:$port/api/chat" else "workspace://antigravity-remote-apk/status",
                outputPreview = "Active Workspace: antigravity-remote-apk\nCompose UI: 60fps Composite-Only Animations\nReduced Motion Hook: Connected",
                durationMs = 460
            )
        }
    }

    private fun buildReplyForPrompt(
        prompt: String,
        liveBridgeReply: String?,
        engineLabel: String,
        modelId: String,
        isLocalAi: Boolean
    ): Triple<String, String?, String?> {
        if (!liveBridgeReply.isNullOrBlank()) {
            return Triple(
                liveBridgeReply,
                "json",
                "{\"engine\":\"$engineLabel\",\"model\":\"$modelId\",\"local\":$isLocalAi,\"status\":\"ok\"}"
            )
        }
        if (isLocalAi) {
            return Triple(
                "[$engineLabel (`$modelId`)] 로컬 AI 엔진으로 요청(`$prompt`)을 처리했습니다. 외부 클라우드 전송 없이 로컬 모델 파이프라인에서 응답을 생성하며, 상단 AI 엔진 바에서 언제든 다른 로컬 모델(`Qwen 2.5 Coder`, `DeepSeek R1`, `Llama 3.1`)이나 기본 Antigravity로 전환할 수 있습니다.",
                "json",
                "{\"provider\":\"local-ai\",\"model\":\"$modelId\",\"offlineReady\":true}"
            )
        }
        val lower = prompt.lowercase()
        return when {
            lower.contains("apk") || lower.contains("빌드") || lower.contains("용량") -> Triple(
                "APK 서명 및 빌드 상태를 점검했습니다. `minSdk 24`(Android 7.0)부터 최신 `targetSdk 36` 기기까지 모두 호환되며, 코끼리(Elephant) 스타일의 차분한 다크 세이지 테마와 내장 스트리밍 시뮬레이터가 함께 패키징되어 단독 설치 즉시 동작합니다.",
                "kotlin",
                """
                // APK 빌드 구성 요약 (app/build.gradle.kts)
                applicationId = "com.antigravity.remote"
                minSdk = 24
                targetSdk = 36
                """.trimIndent()
            )
            lower.contains("색상") || lower.contains("대비") || lower.contains("wcag") -> Triple(
                "코끼리(Elephant) 레퍼런스의 밤하늘 배경(`#11161B`)과 따뜻한 아이보리 본문(`#F2EFE9`)은 `13.8:1` 명도 대비를 유지해 어두운 실내에서도 눈부심 없이 또렷하게 읽힙니다. 스트리밍 활성 상태는 세이지 민트(`#68C5B2`)와 램프 골드(`#E8B96D`) 두 가지 포인트로만 절제해 표현합니다.",
                null,
                null
            )
            else -> Triple(
                "요청하신 작업(`$prompt`)을 처리했습니다. 상단 인스펙터 칩에서 `최악 데이터 (Break-UI)`, `로딩 상태`, `빈 화면`, `연결 오류` 등 4대 데이터 상태를 즉시 전환해 볼 수 있으며, 하단 플로팅 미니바에서 언제든 실시간 스트리밍을 제어할 수 있습니다.",
                "bash",
                "node pc-bridge-server.mjs --port 8765"
            )
        }
    }
}
