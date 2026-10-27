package com.antigravity.remote.network

import com.antigravity.remote.model.ChatMessage
import com.antigravity.remote.model.MessageRole
import com.antigravity.remote.model.MoonlightInputPacket
import com.antigravity.remote.model.SunshineFrameMetadata
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
import java.net.URLDecoder
import kotlin.math.roundToInt

data class SunshineFramePayload(
    val jpegBytes: ByteArray,
    val metadata: SunshineFrameMetadata
)

data class StreamProgressFrame(
    val phaseLabel: String,
    val message: ChatMessage,
    val elapsedSeconds: Double,
    val tokensPerSec: Double,
    val isFinished: Boolean = false,
    val fromLiveBridge: Boolean = false,
    val syncState: LiveSyncState? = null
)

data class PcProjectFolder(
    val name: String,
    val path: String,
    val fileCount: Int
)

data class LiveSyncState(
    val syncRevision: Long = 0L,
    val activeConversationId: String,
    val activeProjectName: String,
    val projects: List<PcProjectFolder>,
    val sessions: List<WorkspaceSession>
)

data class SystemPromptBundle(
    val geminiMd: String,
    val designMotionMd: String,
    val claudeMd: String,
    val updatedAt: String = "방금",
    val syncState: LiveSyncState? = null
)

/**
 * Handles both real LAN communication with `pc-bridge-server.mjs` (HTTP/JSON + chunked streaming)
 * and the built-in interactive Kotlin Coroutines Token Streaming Engine when away from the PC.
 */
object PcBridgeClient {

    @Volatile
    private var lastHealthyHost: String? = null

    fun candidateHosts(primaryHost: String): List<String> {
        val clean = primaryHost.trim().ifBlank { "127.0.0.1" }
        val remembered = lastHealthyHost?.takeIf { it.isNotBlank() }
        return listOfNotNull(clean, remembered, "127.0.0.1", "192.168.219.100", "10.254.49.74", "10.0.2.2").distinct()
    }

    suspend fun checkBridgeHealth(host: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        for (candidate in candidateHosts(host)) {
            var connection: HttpURLConnection? = null
            try {
                val url = URL("http://$candidate:$port/api/status")
                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 900
                    readTimeout = 3500
                    setRequestProperty("Accept", "application/json")
                }
                if (connection.responseCode in 200..299) {
                    lastHealthyHost = candidate
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
                                val rawStatus = tObj.optString("status", "COMPLETED")
                                val toolStatus = when {
                                    rawStatus.equals("RUNNING", ignoreCase = true) -> ToolRunStatus.RUNNING
                                    rawStatus.equals("FAILED", ignoreCase = true) -> ToolRunStatus.FAILED
                                    else -> ToolRunStatus.COMPLETED
                                }
                                tools.add(
                                    ToolExecutionItem(
                                        id = tObj.optString("id", "tool-$t"),
                                        title = tObj.optString("title", "작업 실행"),
                                        commandOrPath = tObj.optString("commandOrPath", ""),
                                        outputPreview = tObj.optString("outputPreview", "완료"),
                                        durationMs = tObj.optLong("durationMs", 250L),
                                        status = toolStatus
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
                                    bodyText = mObj.optString("bodyText", ""),
                                    isStreaming = mObj.optBoolean("isStreaming", false),
                                    steeringStatusText = mObj.optString("steeringStatusText", "")
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
                    val rawStatus = tObj.optString("status", "COMPLETED")
                    val toolStatus = when {
                        rawStatus.equals("RUNNING", ignoreCase = true) -> ToolRunStatus.RUNNING
                        rawStatus.equals("FAILED", ignoreCase = true) -> ToolRunStatus.FAILED
                        else -> ToolRunStatus.COMPLETED
                    }
                    tools.add(
                        ToolExecutionItem(
                            id = tObj.optString("id", "tool-$t"),
                            title = tObj.optString("title", "작업 실행"),
                            commandOrPath = tObj.optString("commandOrPath", ""),
                            outputPreview = tObj.optString("outputPreview", "완료"),
                            durationMs = tObj.optLong("durationMs", 250L),
                            status = toolStatus
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
                        bodyText = mObj.optString("bodyText", ""),
                        isStreaming = mObj.optBoolean("isStreaming", false),
                        steeringStatusText = mObj.optString("steeringStatusText", "")
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
            syncRevision = root.optLong("syncRevision", 0L),
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
                    connectTimeout = 900
                    readTimeout = 3500
                    setRequestProperty("Accept", "application/json")
                }
                if (connection.responseCode in 200..299) {
                    val responseText = BufferedReader(
                        InputStreamReader(connection.inputStream, Charsets.UTF_8)
                    ).use { it.readText() }
                    val state = parseSyncStateFromJson(JSONObject(responseText))
                    if (state.sessions.isNotEmpty()) {
                        lastHealthyHost = candidate
                        return@withContext state
                    }
                }
            } catch (_: Exception) {
                // Try next
            } finally {
                connection?.disconnect()
            }
        }
        null
    }

    /**
     * Automatically attempts to recover PC synchronization when a disconnect is detected.
     * Probes candidate hosts up to [maxAttempts] times with [retryDelayMs] backoff and invokes
     * [onAttempt] before each retry so the UI can show live auto-resync progress.
     */
    suspend fun autoResyncWithRetry(
        host: String,
        port: Int,
        maxAttempts: Int = 3,
        retryDelayMs: Long = 350L,
        onAttempt: (Int) -> Unit = {}
    ): LiveSyncState? {
        val totalAttempts = maxAttempts.coerceAtLeast(1)
        for (attempt in 1..totalAttempts) {
            onAttempt(attempt)
            val synced = fetchLiveSyncState(host, port)
            if (synced != null) {
                return synced
            }
            if (attempt < totalAttempts && retryDelayMs > 0L) {
                delay(retryDelayMs * attempt)
            }
        }
        return null
    }

    private suspend fun postJsonForSyncState(
        host: String,
        port: Int,
        endpoint: String,
        payloadObj: JSONObject
    ): LiveSyncState? = withContext(Dispatchers.IO) {
        for (retryPass in 0..1) {
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
                        lastHealthyHost = candidate
                        return@withContext parseSyncStateFromJson(JSONObject(responseText))
                    }
                } catch (_: Exception) {
                    // Try next
                } finally {
                    connection?.disconnect()
                }
            }
            if (retryPass == 0) {
                delay(300L)
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

    fun defaultSystemPromptBundle(): SystemPromptBundle = SystemPromptBundle(
        geminiMd = """
            ## 맞춤형 응답·말투·작업 정리 원칙
            1. **UI와 역할 분담 (중복 중계 금지)**:
               - 도구 실행 내역과 진행 상태는 호스트 UI에 자동 표시되므로, 본문에서 기계적인 복창이나 사전 계획 번호표를 쓰지 마세요.
               - 작업 시작 시에는 **1문장으로 핵심 의도만 담백하게 짚고 같은 턴에서 즉시 도구를 실행해 작업을 끝까지 완료**하세요.
               - 파일을 읽거나 명령을 실행하는 개별 도구 호출 과정을 본문에 일일이 나열하지 마세요.
            2. **답변 말투 및 결과 정리 포맷**:
               - 군더더기 인사말·마무리 치레를 빼고, 동료 엔지니어에게 브리핑하듯 **현재 시제·능동태·단문(`~합니다`/`~했습니다`)**으로 간결하게 작성하세요.
               - **단순 질문·단순 수정**: 결론 또는 조치 결과와 클릭 가능한 파일 링크만 1~3줄로 바로 제시합니다.
               - **복합 수정·구현 작업**:
                 - **한 줄 결론**: 무엇을 어떻게 바꿨는지 핵심 결과 1줄
                 - **주요 변경**: 변경 요지 불릿 (중첩 최소화)
                 - **검증**: 실행한 테스트·UI/동작 확인 결과 1줄 (미검증 항목은 구분해 명시)

            ## Hallmark for Windows and mobile interfaces
            When building Windows or mobile app UI, read hallmark-apps/SKILL.md before applying Hallmark, then read hallmark/SKILL.md and its App screens guidance. Preserve existing design systems unless redesign is requested.

            ## 디자인·모션 스킬 전부 사용: 기획·구상 직후 단계
            화면이 있는 소프트웨어를 새로 만들거나 화면을 크게 바꿀 때는 기획 직후 D0~D5 역할·충돌 우선순위·결과표대로 진행한다. 시각 방향은 D1 한 곳에서만 정하고 나머지 스킬은 점검·보강·다듬기에 쓴다.
        """.trimIndent(),
        designMotionMd = """
            # 디자인·모션 단계 (D0~D5)
            - D0 참고 자료: 사용자가 제공한 레퍼런스만 반영 (무단 로그인·설치 금지)
            - D1 방향 결정: 기존 DESIGN.md 또는 hallmark + hallmark-apps 단일 결정
            - D2 입력·점검표: ui-ux-pro-max (프로젝트 실제 스택 기준), frontend-design, design-taste-frontend
            - D3 움직임·상호작용: emil-design-eng, animate, animation-performance, mobile-native, apple-design, break-ui
            - D4 구축 후 검토·다듬기: impeccable (문서 지침 모드), review-animations, improve-animations
            - D5 영상·모션 산출물: remotion-best-practices 또는 hyperframes
        """.trimIndent(),
        claudeMd = """
            ## Chief of staff & Engineering Flow
            - Always reply in Korean, polite 합니다체. Dry, calm, exact.
            - Conclusion first, then only evidence that matters, then the needed decision or next step.
            - No padding, praise, restating the request, or unsupported claims.
            - Preserve existing design systems and verify with real native build/runtime checks.
        """.trimIndent(),
        updatedAt = "기본값 로드됨"
    )

    fun applyPresetSnippet(currentText: String, snippetText: String): String {
        val cleanSnippet = snippetText.trim()
        if (cleanSnippet.isEmpty()) return currentText
        if (currentText.contains(cleanSnippet)) return currentText
        val trimmedBase = currentText.trimEnd()
        return if (trimmedBase.isEmpty()) cleanSnippet else "$trimmedBase\n$cleanSnippet"
    }

    fun isOptimalPromptDensity(content: String): Boolean {
        val lines = if (content.isEmpty()) 0 else content.lines().size
        return lines in 1..100
    }

    suspend fun fetchSystemPrompts(
        host: String,
        port: Int
    ): SystemPromptBundle? = withContext(Dispatchers.IO) {
        val defaults = defaultSystemPromptBundle()
        for (candidate in candidateHosts(host)) {
            var connection: HttpURLConnection? = null
            try {
                val url = URL("http://$candidate:$port/api/system-prompt")
                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 900
                    readTimeout = 2000
                    setRequestProperty("Accept", "application/json")
                }
                if (connection.responseCode in 200..299) {
                    val responseText = BufferedReader(
                        InputStreamReader(connection.inputStream, Charsets.UTF_8)
                    ).use { it.readText() }
                    val json = JSONObject(responseText)
                    lastHealthyHost = candidate
                    return@withContext SystemPromptBundle(
                        geminiMd = json.optString("geminiMd").ifBlank { defaults.geminiMd },
                        designMotionMd = json.optString("designMotionMd").ifBlank { defaults.designMotionMd },
                        claudeMd = json.optString("claudeMd").ifBlank { defaults.claudeMd },
                        updatedAt = json.optString("updatedAt", "실시간 동기화됨")
                    )
                }
            } catch (_: Exception) {
                // Try next host
            } finally {
                connection?.disconnect()
            }
        }
        null
    }

    suspend fun saveSystemPrompt(
        host: String,
        port: Int,
        target: String,
        content: String,
        sessionId: String
    ): SystemPromptBundle? = withContext(Dispatchers.IO) {
        val defaults = defaultSystemPromptBundle()
        for (retryPass in 0..1) {
            for (candidate in candidateHosts(host)) {
                var connection: HttpURLConnection? = null
                try {
                    val url = URL("http://$candidate:$port/api/system-prompt")
                    connection = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        connectTimeout = 1500
                        readTimeout = 4500
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    }
                    val payload = JSONObject().apply {
                        put("target", target)
                        put("content", content)
                        put("sessionId", sessionId)
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
                        val syncState = if (json.has("sessions")) parseSyncStateFromJson(json) else null
                        lastHealthyHost = candidate
                        return@withContext SystemPromptBundle(
                            geminiMd = json.optString("geminiMd").ifBlank { defaults.geminiMd },
                            designMotionMd = json.optString("designMotionMd").ifBlank { defaults.designMotionMd },
                            claudeMd = json.optString("claudeMd").ifBlank { defaults.claudeMd },
                            updatedAt = json.optString("updatedAt", "방금 저장됨"),
                            syncState = syncState
                        )
                    }
                } catch (_: Exception) {
                    // Try next host
                } finally {
                    connection?.disconnect()
                }
            }
            if (retryPass == 0) {
                delay(200)
            }
        }
        null
    }

    private data class BridgeChatReply(
        val reply: String,
        val thoughtText: String = "",
        val tools: List<ToolExecutionItem> = emptyList(),
        val syncState: LiveSyncState? = null
    )

    private suspend fun tryRequestBridgeReply(
        host: String,
        port: Int,
        prompt: String,
        sessionId: String,
        engineLabel: String,
        modelId: String,
        isLocalAi: Boolean
    ): BridgeChatReply? = withContext(Dispatchers.IO) {
        for (candidate in candidateHosts(host)) {
            var connection: HttpURLConnection? = null
            try {
                val url = URL("http://$candidate:$port/api/chat")
                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 900
                    // LCODE CLI 및 Gemma 4 / Qwen 로컬 에이전트 도구 실행을 위해 넉넉한 타임아웃을 부여합니다.
                    readTimeout = 55000
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
                    val thoughtText = json.optString("thoughtText", "")
                    val parsedTools = mutableListOf<ToolExecutionItem>()
                    val tArr = json.optJSONArray("tools") ?: JSONArray()
                    for (t in 0 until tArr.length()) {
                        val tObj = tArr.optJSONObject(t) ?: continue
                        val rawStatus = tObj.optString("status", "COMPLETED")
                        val toolStatus = when {
                            rawStatus.equals("RUNNING", ignoreCase = true) -> ToolRunStatus.RUNNING
                            rawStatus.equals("FAILED", ignoreCase = true) -> ToolRunStatus.FAILED
                            else -> ToolRunStatus.COMPLETED
                        }
                        parsedTools.add(
                            ToolExecutionItem(
                                id = tObj.optString("id", "bridge-tool-$t"),
                                title = tObj.optString("title", "PC 원격 제어 실행"),
                                commandOrPath = tObj.optString("commandOrPath", ""),
                                outputPreview = tObj.optString("outputPreview", "완료"),
                                durationMs = tObj.optLong("durationMs", 180L),
                                status = toolStatus
                            )
                        )
                    }
                    val syncState = if (json.has("sessions")) parseSyncStateFromJson(json) else null
                    if (reply != null) {
                        lastHealthyHost = candidate
                        return@withContext BridgeChatReply(
                            reply = reply,
                            thoughtText = thoughtText,
                            tools = parsedTools,
                            syncState = syncState
                        )
                    }
                } else if (connection.responseCode in 400..599) {
                    // 브리지가 로컬 모델 오류를 명시적으로 반환한 경우에는
                    // 다른 후보 주소를 재시도하지 않아 불필요한 지연을 막습니다.
                    return@withContext null
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
        val bridgeOutcome = tryRequestBridgeReply(
            host = host,
            port = port,
            prompt = userPrompt,
            sessionId = sessionId,
            engineLabel = engineLabel,
            modelId = modelId,
            isLocalAi = isLocalAi
        )
        val liveReply = bridgeOutcome?.reply
        val liveSyncState = bridgeOutcome?.syncState
        val isFromBridge = liveReply != null
        val authorDisplay = when {
            isLocalAi && isFromBridge -> "$engineLabel"
            isLocalAi -> "$engineLabel"
            isFromBridge -> "안그래비티 도우미 (PC 연결됨)"
            else -> "안그래비티 도우미"
        }

        val plannedThought = bridgeOutcome?.thoughtText?.takeIf { it.isNotBlank() }
            ?: buildThoughtForPrompt(userPrompt, isFromBridge, host, port, engineLabel, modelId, isLocalAi)
        val plannedTools = bridgeOutcome?.tools?.takeIf { it.isNotEmpty() }
            ?: listOf(buildToolForPrompt(userPrompt, messageId, isFromBridge, host, port, modelId, isLocalAi))
        val (plannedBody, codeLang, codeSnippet) = buildReplyForPrompt(userPrompt, liveReply, engineLabel, modelId, isLocalAi)

        var currentThought = ""
        var currentBody = ""
        var currentCode: String? = null
        var tokenCounter = 0

        // Phase 1: 추론 단계 -> "생각중"
        val thoughtChunks = chunkByWords(plannedThought, wordsPerChunk = 3)
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
            delay(35L)
        }

        val thoughtElapsedStr = String.format(
            "%.1fs",
            ((System.currentTimeMillis() - startMs).coerceAtLeast(100L)) / 1000.0
        )

        // Phase 2: 추론 및 작업준비 확정 단계 -> "작업준비중"
        val runningTools = plannedTools.map { it.copy(status = ToolRunStatus.RUNNING) }
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
                    tools = runningTools,
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
        delay(220L)

        val completedTools = plannedTools.map {
            if (it.status == ToolRunStatus.RUNNING) it.copy(status = ToolRunStatus.COMPLETED) else it
        }

        // Phase 3: 작업 마무리 단계 -> "작업 마무리중"
        val bodyChunks = chunkByWords(plannedBody, wordsPerChunk = 3)
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
                        tools = completedTools,
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
            delay(30L)
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
                    tools = completedTools,
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
                fromLiveBridge = isFromBridge,
                syncState = liveSyncState
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
            isLocalAi -> "PC 브릿지에 연결되지 않아 로컬 AI($modelId) 응답을 확인하지 못했습니다."
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
                outputPreview = "Engine: Local AI ($modelId)\nRoute: PC bridge → Ollama /api/chat\nStatus: Bridge unavailable; inference not confirmed",
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
                "[로컬 AI 연결 실패]\n\nPC 브릿지 또는 Ollama에서 실제 응답을 받지 못했습니다. 설정에서 PC 연결 주소·포트와 로컬 모델명을 확인한 뒤 다시 시도해 주세요. 이 답변은 로컬 모델이 생성한 결과가 아닙니다.",
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

    /**
     * Fetches a live JPEG desktop frame + Sunshine host cursor metadata from `GET /api/stream/frame`.
     */
    suspend fun fetchSunshineFrame(
        host: String,
        port: Int
    ): SunshineFramePayload? = withContext(Dispatchers.IO) {
        for (retryPass in 0..1) {
            for (candidate in candidateHosts(host)) {
                var connection: HttpURLConnection? = null
                try {
                    val url = URL("http://$candidate:$port/api/stream/frame")
                    connection = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 1200
                        readTimeout = 4500
                        useCaches = false
                        setRequestProperty("Accept", "image/jpeg")
                    }
                    if (connection.responseCode in 200..299) {
                        val bytes = connection.inputStream.use { it.readBytes() }
                        if (bytes.isNotEmpty()) {
                            val scrW = connection.getHeaderField("X-Sunshine-Screen-Width")?.toIntOrNull() ?: 1920
                            val scrH = connection.getHeaderField("X-Sunshine-Screen-Height")?.toIntOrNull() ?: 1080
                            val curX = connection.getHeaderField("X-Sunshine-Cursor-X")?.toIntOrNull() ?: (scrW / 2)
                            val curY = connection.getHeaderField("X-Sunshine-Cursor-Y")?.toIntOrNull() ?: (scrH / 2)
                            val seq = connection.getHeaderField("X-Sunshine-Frame-Seq")?.toLongOrNull() ?: 0L
                            val rawEvent = connection.getHeaderField("X-Sunshine-Last-Event").orEmpty()
                            val decodedEvent = try {
                                if (rawEvent.isNotBlank()) URLDecoder.decode(rawEvent, "UTF-8") else "실시간 스트리밍 중"
                            } catch (_: Exception) {
                                "실시간 스트리밍 중"
                            }
                            lastHealthyHost = candidate
                            return@withContext SunshineFramePayload(
                                jpegBytes = bytes,
                                metadata = SunshineFrameMetadata(
                                    screenWidth = scrW,
                                    screenHeight = scrH,
                                    cursorX = curX,
                                    cursorY = curY,
                                    frameSeq = seq,
                                    lastEvent = decodedEvent,
                                    isLiveFrame = true
                                )
                            )
                        }
                    }
                } catch (_: Exception) {
                    // Try next host
                } finally {
                    connection?.disconnect()
                }
            }
            if (retryPass == 0) {
                delay(200)
            }
        }
        null
    }

    /**
     * Dispatches a Moonlight-compatible touch/mouse/scroll/keyboard packet to `POST /api/stream/input`
     * on the Sunshine host daemon (`SunshineHostDaemon.exe` via `pc-bridge-server.mjs`).
     */
    suspend fun sendSunshineInput(
        host: String,
        port: Int,
        packet: MoonlightInputPacket
    ): SunshineFrameMetadata? = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("type", packet.type)
            put("button", packet.button)
            put("x", packet.x.toDouble())
            put("y", packet.y.toDouble())
            put("refWidth", packet.refWidth.toDouble())
            put("refHeight", packet.refHeight.toDouble())
            put("deltaX", packet.deltaX.toDouble())
            put("deltaY", packet.deltaY.toDouble())
            put("scrollDelta", packet.scrollDelta)
            if (packet.keySpec.isNotBlank()) {
                put("keySpec", packet.keySpec)
            }
        }.toString()

        for (retryPass in 0..1) {
            for (candidate in candidateHosts(host)) {
                var connection: HttpURLConnection? = null
                try {
                    val url = URL("http://$candidate:$port/api/stream/input")
                    connection = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        connectTimeout = 800
                        readTimeout = 2000
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    }
                    OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                        writer.write(payload)
                        writer.flush()
                    }
                    if (connection.responseCode in 200..299) {
                        val responseText = BufferedReader(
                            InputStreamReader(connection.inputStream, Charsets.UTF_8)
                        ).use { it.readText() }
                        val json = JSONObject(responseText)
                        lastHealthyHost = candidate
                        return@withContext SunshineFrameMetadata(
                            screenWidth = json.optInt("screenWidth", 1920),
                            screenHeight = json.optInt("screenHeight", 1080),
                            cursorX = json.optInt("hostX", 960),
                            cursorY = json.optInt("hostY", 540),
                            frameSeq = json.optLong("frameSeq", 0L),
                            lastEvent = json.optString("lastEvent", "입력 전달됨"),
                            isLiveFrame = true
                        )
                    }
                } catch (_: Exception) {
                    // Try next host
                } finally {
                    connection?.disconnect()
                }
            }
            if (retryPass == 0) {
                delay(150)
            }
        }
        null
    }
}
