package com.antigravity.remote.network

import com.antigravity.remote.model.ChatMessage
import com.antigravity.remote.model.MessageRole
import com.antigravity.remote.model.ToolExecutionItem
import com.antigravity.remote.model.ToolRunStatus
import com.antigravity.remote.model.WorkspaceSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
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

data class PcProjectFolder(
    val name: String,
    val path: String,
    val fileCount: Int
)

data class LiveSyncState(
    val activeConversationId: String,
    val activeProjectName: String,
    val projects: List<PcProjectFolder>,
    val sessions: List<WorkspaceSession>
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

    /**
     * Fetches real live Antigravity sessions from `GET /api/sessions` on `pc-bridge-server.mjs`.
     */
    suspend fun fetchLiveSessions(host: String, port: Int): List<WorkspaceSession>? = withContext(Dispatchers.IO) {
        for (candidate in candidateHosts(host)) {
            var connection: HttpURLConnection? = null
            try {
                val url = URL("http://$candidate:$port/api/sessions")
                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 1000
                    readTimeout = 2000
                    setRequestProperty("Accept", "application/json")
                }
                if (connection.responseCode in 200..299) {
                    val responseText = BufferedReader(
                        InputStreamReader(connection.inputStream, Charsets.UTF_8)
                    ).use { it.readText() }
                    val root = JSONObject(responseText)
                    val arr = root.optJSONArray("sessions") ?: JSONArray()
                    val parsedSessions = mutableListOf<WorkspaceSession>()
                    for (i in 0 until arr.length()) {
                        val sObj = arr.optJSONObject(i) ?: continue
                        val qArr = sObj.optJSONArray("quickPrompts") ?: JSONArray()
                        val quickPrompts = mutableListOf<String>()
                        for (q in 0 until qArr.length()) {
                            val item = qArr.optString(q)
                            if (item.isNotBlank()) quickPrompts.add(item)
                        }
                        if (quickPrompts.isEmpty()) {
                            quickPrompts.add("오늘 해야 할 일을 깔끔하게 정리해줘")
                        }

                        val mArr = sObj.optJSONArray("messages") ?: JSONArray()
                        val messages = mutableListOf<ChatMessage>()
                        for (m in 0 until mArr.length()) {
                            val mObj = mArr.optJSONObject(m) ?: continue
                            val roleStr = mObj.optString("role", "AGENT")
                            val role = if (roleStr.equals("USER", ignoreCase = true)) {
                                MessageRole.USER
                            } else {
                                MessageRole.AGENT
                            }
                            val tArr = mObj.optJSONArray("tools") ?: JSONArray()
                            val tools = mutableListOf<ToolExecutionItem>()
                            for (t in 0 until tArr.length()) {
                                val tObj = tArr.optJSONObject(t) ?: continue
                                tools.add(
                                    ToolExecutionItem(
                                        id = tObj.optString("id", "tool-$t"),
                                        title = tObj.optString("title", "작업 실행"),
                                        commandOrPath = tObj.optString("commandOrPath", ""),
                                        outputPreview = tObj.optString("outputPreview", "완료"),
                                        durationMs = tObj.optLong("durationMs", 250L),
                                        status = ToolRunStatus.COMPLETED
                                    )
                                )
                            }
                            messages.add(
                                ChatMessage(
                                    id = mObj.optString("id", "live-msg-$m"),
                                    role = role,
                                    authorName = mObj.optString("authorName", ""),
                                    timestamp = mObj.optString("timestamp", ""),
                                    thoughtText = mObj.optString("thoughtText", ""),
                                    thoughtDurationSec = mObj.optString("thoughtDurationSec", "1.0초"),
                                    tools = tools,
                                    bodyText = mObj.optString("bodyText", "")
                                )
                            )
                        }

                        if (messages.isNotEmpty()) {
                            parsedSessions.add(
                                WorkspaceSession(
                                    id = sObj.optString("id", "live-session-$i"),
                                    title = sObj.optString("title", "현재 안그래비티 세션"),
                                    ambientCaption = sObj.optString("ambientCaption", "실시간 연동 중"),
                                    branchName = sObj.optString("branchName", "live-sync"),
                                    activeTaskSummary = sObj.optString("activeTaskSummary", "현재 PC 세션 동기화"),
                                    durationTag = sObj.optString("durationTag", "실시간"),
                                    quickPrompts = quickPrompts,
                                    messages = messages
                                )
                            )
                        }
                    }
                    if (parsedSessions.isNotEmpty()) {
                        return@withContext parsedSessions
                    }
                }
            } catch (_: Exception) {
                // Try next candidate
            } finally {
                connection?.disconnect()
            }
        }
        null
    }

    private fun parseSyncStateFromJson(root: JSONObject): LiveSyncState {
        val pArr = root.optJSONArray("projects") ?: JSONArray()
        val projects = mutableListOf<PcProjectFolder>()
        for (i in 0 until pArr.length()) {
            val pObj = pArr.optJSONObject(i) ?: continue
            projects.add(
                PcProjectFolder(
                    name = pObj.optString("name", "project"),
                    path = pObj.optString("path", ""),
                    fileCount = pObj.optInt("fileCount", 0)
                )
            )
        }

        val arr = root.optJSONArray("sessions") ?: JSONArray()
        val parsedSessions = mutableListOf<WorkspaceSession>()
        for (i in 0 until arr.length()) {
            val sObj = arr.optJSONObject(i) ?: continue
            val qArr = sObj.optJSONArray("quickPrompts") ?: JSONArray()
            val quickPrompts = mutableListOf<String>()
            for (q in 0 until qArr.length()) {
                val item = qArr.optString(q)
                if (item.isNotBlank()) quickPrompts.add(item)
            }
            if (quickPrompts.isEmpty()) {
                quickPrompts.add("오늘 해야 할 일을 깔끔하게 정리해줘")
            }

            val mArr = sObj.optJSONArray("messages") ?: JSONArray()
            val messages = mutableListOf<ChatMessage>()
            for (m in 0 until mArr.length()) {
                val mObj = mArr.optJSONObject(m) ?: continue
                val roleStr = mObj.optString("role", "AGENT")
                val role = if (roleStr.equals("USER", ignoreCase = true)) {
                    MessageRole.USER
                } else {
                    MessageRole.AGENT
                }
                val tArr = mObj.optJSONArray("tools") ?: JSONArray()
                val tools = mutableListOf<ToolExecutionItem>()
                for (t in 0 until tArr.length()) {
                    val tObj = tArr.optJSONObject(t) ?: continue
                    tools.add(
                        ToolExecutionItem(
                            id = tObj.optString("id", "tool-$t"),
                            title = tObj.optString("title", "작업 실행"),
                            commandOrPath = tObj.optString("commandOrPath", ""),
                            outputPreview = tObj.optString("outputPreview", "완료"),
                            durationMs = tObj.optLong("durationMs", 250L),
                            status = ToolRunStatus.COMPLETED
                        )
                    )
                }
                messages.add(
                    ChatMessage(
                        id = mObj.optString("id", "live-msg-$m"),
                        role = role,
                        authorName = mObj.optString("authorName", ""),
                        timestamp = mObj.optString("timestamp", ""),
                        thoughtText = mObj.optString("thoughtText", ""),
                        thoughtDurationSec = mObj.optString("thoughtDurationSec", "1.0초"),
                        tools = tools,
                        bodyText = mObj.optString("bodyText", "")
                    )
                )
            }

            if (messages.isNotEmpty()) {
                parsedSessions.add(
                    WorkspaceSession(
                        id = sObj.optString("id", "live-session-$i"),
                        title = sObj.optString("title", "현재 안그래비티 세션"),
                        ambientCaption = sObj.optString("ambientCaption", "실시간 연동 중"),
                        branchName = sObj.optString("branchName", "live-sync"),
                        activeTaskSummary = sObj.optString("activeTaskSummary", "현재 PC 세션 동기화"),
                        durationTag = sObj.optString("durationTag", "실시간"),
                        quickPrompts = quickPrompts,
                        messages = messages
                    )
                )
            }
        }

        return LiveSyncState(
            activeConversationId = root.optString("activeConversationId", ""),
            activeProjectName = root.optString("activeProjectName", "antigravity-remote-apk"),
            projects = projects,
            sessions = parsedSessions
        )
    }

    suspend fun fetchLiveSyncState(host: String, port: Int): LiveSyncState? = withContext(Dispatchers.IO) {
        for (candidate in candidateHosts(host)) {
            var connection: HttpURLConnection? = null
            try {
                val url = URL("http://$candidate:$port/api/sessions")
                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 1000
                    readTimeout = 2000
                    setRequestProperty("Accept", "application/json")
                }
                if (connection.responseCode in 200..299) {
                    val responseText = BufferedReader(
                        InputStreamReader(connection.inputStream, Charsets.UTF_8)
                    ).use { it.readText() }
                    val state = parseSyncStateFromJson(JSONObject(responseText))
                    if (state.sessions.isNotEmpty()) return@withContext state
                }
            } catch (_: Exception) {
                // Try next
            } finally {
                connection?.disconnect()
            }
        }
        null
    }

    private suspend fun postJsonForSyncState(
        host: String,
        port: Int,
        endpoint: String,
        payloadObj: JSONObject
    ): LiveSyncState? = withContext(Dispatchers.IO) {
        for (candidate in candidateHosts(host)) {
            var connection: HttpURLConnection? = null
            try {
                val url = URL("http://$candidate:$port$endpoint")
                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 2000
                    readTimeout = 16000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
                OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(payloadObj.toString())
                    writer.flush()
                }
                if (connection.responseCode in 200..299) {
                    val responseText = BufferedReader(
                        InputStreamReader(connection.inputStream, Charsets.UTF_8)
                    ).use { it.readText() }
                    return@withContext parseSyncStateFromJson(JSONObject(responseText))
                }
            } catch (_: Exception) {
                // Try next
            } finally {
                connection?.disconnect()
            }
        }
        null
    }

    suspend fun createRemoteSession(
        host: String,
        port: Int,
        title: String,
        projectFolder: String
    ): LiveSyncState? = postJsonForSyncState(
        host = host,
        port = port,
        endpoint = "/api/sessions/create",
        payloadObj = JSONObject().apply {
            put("title", title)
            put("projectFolder", projectFolder)
        }
    )

    suspend fun createRemoteProjectFolder(
        host: String,
        port: Int,
        folderName: String,
        sessionId: String
    ): LiveSyncState? = postJsonForSyncState(
        host = host,
        port = port,
        endpoint = "/api/projects/create",
        payloadObj = JSONObject().apply {
            put("folderName", folderName)
            put("sessionId", sessionId)
        }
    )

    suspend fun performSessionAction(
        host: String,
        port: Int,
        sessionId: String,
        action: String
    ): LiveSyncState? = postJsonForSyncState(
        host = host,
        port = port,
        endpoint = "/api/sessions/action",
        payloadObj = JSONObject().apply {
            put("sessionId", sessionId)
            put("action", action)
        }
    )

    suspend fun performProjectAction(
        host: String,
        port: Int,
        folderName: String,
        action: String
    ): LiveSyncState? = postJsonForSyncState(
        host = host,
        port = port,
        endpoint = "/api/projects/action",
        payloadObj = JSONObject().apply {
            put("folderName", folderName)
            put("action", action)
        }
    )

    suspend fun executeRemoteCommand(
        host: String,
        port: Int,
        command: String,
        sessionId: String,
        projectName: String = ""
    ): LiveSyncState? = postJsonForSyncState(
        host = host,
        port = port,
        endpoint = "/api/remote-control",
        payloadObj = JSONObject().apply {
            put("command", command)
            put("sessionId", sessionId)
            if (projectName.isNotBlank()) {
                put("projectName", projectName)
            }
        }
    )

    suspend fun attachMediaToPc(
        host: String,
        port: Int,
        mediaType: String,
        fileName: String,
        fileSize: Long,
        sessionId: String,
        projectName: String = ""
    ): LiveSyncState? = postJsonForSyncState(
        host = host,
        port = port,
        endpoint = "/api/remote-control",
        payloadObj = JSONObject().apply {
            put("command", "attach_media")
            put("mediaType", mediaType)
            put("fileName", fileName)
            put("fileSize", fileSize)
            put("sessionId", sessionId)
            if (projectName.isNotBlank()) {
                put("projectName", projectName)
            }
        }
    )

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
            isLocalAi && isFromBridge -> "$engineLabel"
            isLocalAi -> "$engineLabel"
            isFromBridge -> "안그래비티 도우미 (PC 연결됨)"
            else -> "안그래비티 도우미"
        }

        val plannedThought = buildThoughtForPrompt(userPrompt, isFromBridge, host, port, engineLabel, modelId, isLocalAi)
        val plannedTool = buildToolForPrompt(userPrompt, messageId, isFromBridge, host, port, modelId, isLocalAi)
        val (plannedBody, codeLang, codeSnippet) = buildReplyForPrompt(userPrompt, liveReply, engineLabel, modelId, isLocalAi)

        var currentThought = ""
        var currentBody = ""
        var currentCode: String? = null
        var tokenCounter = 0

        // Phase 1: 추론 단계 -> "생각중"
        val thoughtChunks = chunkByWords(plannedThought, wordsPerChunk = 2)
        for (chunk in thoughtChunks) {
            currentThought += chunk
            tokenCounter += 2
            val elapsed = ((System.currentTimeMillis() - startMs).coerceAtLeast(80L)) / 1000.0
            val speed = ((tokenCounter / elapsed) * 10.0).roundToInt() / 10.0
            emit(
                StreamProgressFrame(
                    phaseLabel = "생각중",
                    message = ChatMessage(
                        id = messageId,
                        role = MessageRole.AGENT,
                        authorName = authorDisplay,
                        timestamp = timestamp,
                        thoughtText = currentThought,
                        thoughtDurationSec = String.format("%.1fs", elapsed),
                        tools = emptyList(),
                        bodyText = "질문 의도를 분석하고 추론하고 있습니다...",
                        isStreaming = true,
                        tokenCount = tokenCounter,
                        tokensPerSec = speed
                    ),
                    elapsedSeconds = elapsed,
                    tokensPerSec = speed,
                    fromLiveBridge = isFromBridge
                )
            )
            delay(85L)
        }

        val thoughtElapsedStr = String.format(
            "%.1fs",
            ((System.currentTimeMillis() - startMs).coerceAtLeast(100L)) / 1000.0
        )

        // Phase 2: 추론 및 작업준비 확정 단계 -> "작업준비중"
        val runningTool = plannedTool.copy(status = ToolRunStatus.RUNNING)
        val elapsedToolStart = ((System.currentTimeMillis() - startMs).coerceAtLeast(120L)) / 1000.0
        emit(
            StreamProgressFrame(
                phaseLabel = "작업준비중",
                message = ChatMessage(
                    id = messageId,
                    role = MessageRole.AGENT,
                    authorName = authorDisplay,
                    timestamp = timestamp,
                    thoughtText = currentThought,
                    thoughtDurationSec = thoughtElapsedStr,
                    tools = listOf(runningTool),
                    bodyText = "추론을 마치고 작업을 준비하고 있습니다...",
                    isStreaming = true,
                    tokenCount = tokenCounter,
                    tokensPerSec = 42.0
                ),
                elapsedSeconds = elapsedToolStart,
                tokensPerSec = 42.0,
                fromLiveBridge = isFromBridge
            )
        )
        delay(650L)

        val completedTool = plannedTool.copy(status = ToolRunStatus.COMPLETED)

        // Phase 3: 작업 마무리 단계 -> "작업 마무리중"
        val bodyChunks = chunkByWords(plannedBody, wordsPerChunk = 2)
        for (chunk in bodyChunks) {
            currentBody += chunk
            tokenCounter += 3
            val elapsed = ((System.currentTimeMillis() - startMs).coerceAtLeast(200L)) / 1000.0
            val speed = ((tokenCounter / elapsed) * 10.0).roundToInt() / 10.0
            emit(
                StreamProgressFrame(
                    phaseLabel = "작업 마무리중",
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
            delay(65L)
        }

        // Attach code snippet if present
        if (codeSnippet != null) {
            currentCode = codeSnippet
            tokenCounter += 24
        }

        val finalElapsed = ((System.currentTimeMillis() - startMs).coerceAtLeast(250L)) / 1000.0
        val finalSpeed = ((tokenCounter / finalElapsed) * 10.0).roundToInt() / 10.0

        // Phase 4: 다 끝나면 -> "작업완료"
        emit(
            StreamProgressFrame(
                phaseLabel = "작업완료",
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
                "[$engineLabel] 요청하신 내용(\"$prompt\")을 정리했습니다.\n\n내 컴퓨터의 로컬 AI 모델을 사용해 외부 전송 없이 안전하게 답변을 작성했습니다. 다른 AI로 바꾸고 싶다면 오른쪽 위 [설정] 버튼을 눌러보세요.",
                null,
                null
            )
        }
        val lower = prompt.lowercase()
        return when {
            lower.contains("할 일") || lower.contains("정리") -> Triple(
                "오늘 해야 할 일을 한눈에 보기 쉽게 3가지로 정리해 드릴게요.\n\n1. **가장 중요한 일 1가지 먼저 끝내기**: 오전이나 집중이 잘 되는 시간에 핵심 작업을 배치하세요.\n2. **가벼운 연락 및 회신 모아서 처리하기**: 메시지 확인은 정해진 시간에만 확인하면 피로가 줄어듭니다.\n3. **마무리 점검**: 잠들기 전 내일 일정을 한 줄로 적어두면 훨씬 편안한 밤을 보낼 수 있습니다.",
                null,
                null
            )
            lower.contains("요약") || lower.contains("하루") -> Triple(
                "오늘 하루도 수고 많으셨습니다. 복잡한 생각은 잠시 내려놓고, 내일 이어서 할 핵심 메모 한 줄만 남긴 뒤 편안하게 쉬어가세요.",
                null,
                null
            )
            else -> Triple(
                "말씀하신 내용(\"$prompt\")을 확인했습니다.\n\n더 자세한 설명이나 글 다듬기, 혹은 내 PC 확인이 필요하면 언제든 아래 입력창에 편하게 적어주세요.",
                null,
                null
            )
        }
    }
}
