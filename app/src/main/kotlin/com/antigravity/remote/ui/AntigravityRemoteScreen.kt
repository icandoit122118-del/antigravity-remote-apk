package com.antigravity.remote.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChange
import kotlin.math.abs
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.animation.expandIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.remote.model.AiEngineCatalog
import com.antigravity.remote.model.AiEngineOption
import com.antigravity.remote.model.AntigravityFixtures
import com.antigravity.remote.model.BridgeConnectionState
import com.antigravity.remote.model.ChatMessage
import com.antigravity.remote.model.InspectorViewMode
import com.antigravity.remote.model.MessageRole
import com.antigravity.remote.model.MoonlightFpsTarget
import com.antigravity.remote.model.MoonlightInputPacket
import com.antigravity.remote.model.MoonlightMouseButton
import com.antigravity.remote.model.MoonlightTouchEngine
import com.antigravity.remote.model.MoonlightTouchMode
import com.antigravity.remote.model.ReasoningEffort
import com.antigravity.remote.model.SunshineFrameMetadata
import com.antigravity.remote.model.WorkspaceSession
import com.antigravity.remote.network.LiveSyncState
import com.antigravity.remote.network.PcBridgeClient
import com.antigravity.remote.network.PcProjectFolder
import com.antigravity.remote.network.SystemPromptBundle
import com.antigravity.remote.ui.theme.AntigravityIcons
import com.antigravity.remote.ui.theme.AntigravityPalette
import com.antigravity.remote.ui.theme.AntigravityTypography
import com.antigravity.remote.ui.theme.rememberReducedMotion
import com.antigravity.remote.ui.theme.tactilePressScale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AntigravityRemoteScreen() {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    val reducedMotion = rememberReducedMotion()

    // Screen view mode (Normal by default; other test states live cleanly inside Settings -> Advanced)
    var viewMode by rememberSaveable { mutableStateOf(InspectorViewMode.DEMO_NORMAL) }

    // Connection & Settings state
    var bridgeHost by rememberSaveable { mutableStateOf("127.0.0.1") }
    var bridgePortText by rememberSaveable { mutableStateOf("8766") }
    var connectionState by remember { mutableStateOf(BridgeConnectionState.SIMULATION_ACTIVE) }
    var autoSyncEnabled by rememberSaveable { mutableStateOf(true) }
    var autoSyncRetryCount by remember { mutableIntStateOf(0) }
    var showSettingsSheet by rememberSaveable { mutableStateOf(false) }
    var showSessionProjectPopup by rememberSaveable { mutableStateOf(false) }
    var showSystemPromptEditor by rememberSaveable { mutableStateOf(false) }
    var showMoonlightTouchStream by rememberSaveable { mutableStateOf(false) }
    var moonlightTouchMode by rememberSaveable { mutableStateOf(MoonlightTouchMode.DIRECT_TOUCHSCREEN) }
    var moonlightFpsTarget by rememberSaveable { mutableStateOf(MoonlightFpsTarget.FPS_60) }
    var systemPromptBundle by remember { mutableStateOf(PcBridgeClient.defaultSystemPromptBundle()) }
    var systemPromptStatusBanner by remember { mutableStateOf("") }

    // AI Engine, Reasoning Effort & Local AI Selection
    val engineOptions = remember { AiEngineCatalog.options }
    var selectedEngineId by rememberSaveable { mutableStateOf(engineOptions.first().id) }
    var selectedReasoning by rememberSaveable { mutableStateOf(ReasoningEffort.MEDIUM) }
    var showModelReasoningPopup by rememberSaveable { mutableStateOf(false) }
    var showPlusMenuPopup by rememberSaveable { mutableStateOf(false) }
    var customLocalModelId by rememberSaveable { mutableStateOf("") }
    val activeEngine by remember(selectedEngineId) {
        derivedStateOf {
            engineOptions.find { it.id == selectedEngineId } ?: engineOptions.first()
        }
    }
    val effectiveModelId = if (activeEngine.isLocal && customLocalModelId.isNotBlank()) {
        customLocalModelId
    } else {
        activeEngine.modelId
    }

    // PC Projects synced from bridge
    val pcProjects = remember { mutableStateListOf<PcProjectFolder>() }
    var activePcProjectName by rememberSaveable { mutableStateOf("antigravity-remote-apk") }

    // Sessions state
    val normalSessions = remember {
        mutableStateListOf<WorkspaceSession>().apply {
            addAll(AntigravityFixtures.defaultSessions())
        }
    }
    val worstCaseSessions = remember {
        mutableStateListOf<WorkspaceSession>().apply {
            addAll(AntigravityFixtures.worstCaseSessions())
        }
    }

    var selectedNormalIndex by rememberSaveable { mutableIntStateOf(0) }
    var selectedWorstIndex by rememberSaveable { mutableIntStateOf(0) }

    val activeSessions = if (viewMode == InspectorViewMode.WORST_CASE) worstCaseSessions else normalSessions
    val activeSessionIndex = if (viewMode == InspectorViewMode.WORST_CASE) {
        selectedWorstIndex.coerceIn(0, (worstCaseSessions.size - 1).coerceAtLeast(0))
    } else {
        selectedNormalIndex.coerceIn(0, (normalSessions.size - 1).coerceAtLeast(0))
    }

    val currentSession by remember(viewMode, activeSessionIndex, normalSessions.size, worstCaseSessions.size) {
        derivedStateOf { activeSessions[activeSessionIndex] }
    }

    // Streaming & Mid-Stream Steering (대화조정) State
    var activeStreamJob by remember { mutableStateOf<Job?>(null) }
    var activeStreamingAgentMsgId by remember { mutableStateOf<String?>(null) }
    var isStreaming by remember { mutableStateOf(false) }
    var streamPhaseLabel by remember { mutableStateOf("생각중") }
    var steeringStatusLabel by remember { mutableStateOf("") }
    val pendingSteerNotes = remember { mutableStateListOf<String>() }

    // Prompt Input
    var promptInput by rememberSaveable { mutableStateOf("") }

    // Clipboard feedback
    var copiedBlockId by remember { mutableStateOf<String?>(null) }

    // Optional details toggle (collapsed by default so main screen stays super clean!)
    val expandedDetails = remember { mutableStateMapOf<String, Boolean>() }

    val listState = rememberLazyListState()
    var hasCompletedInitialLiveSync by remember { mutableStateOf(false) }
    val knownPcSessionIds = remember { mutableStateListOf<String>() }

    fun applyLiveSyncState(state: LiveSyncState?, forceSelectFirst: Boolean = false) {
        if (state == null) return
        connectionState = BridgeConnectionState.CONNECTED_PC
        autoSyncRetryCount = 0
        activePcProjectName = state.activeProjectName
        pcProjects.clear()
        pcProjects.addAll(state.projects)
        if (state.sessions.isNotEmpty()) {
            val prevSelectedId = normalSessions.getOrNull(selectedNormalIndex)?.id
            val topSessionId = state.sessions.first().id
            val isBrandNewTopSession = hasCompletedInitialLiveSync && topSessionId !in knownPcSessionIds
            for (s in state.sessions) {
                if (s.id !in knownPcSessionIds) {
                    knownPcSessionIds.add(s.id)
                }
            }

            val currentSignature = normalSessions.joinToString("|") { s ->
                val msgSig = s.messages.takeLast(6).joinToString(",") { m ->
                    "${m.id}:${m.bodyText.hashCode()}:${m.thoughtText.hashCode()}:${m.isStreaming}:${m.steeringStatusText}:${m.tools.size}"
                }
                "${s.id}:${s.title}:${s.branchName}:${s.activeTaskSummary}:${s.durationTag}:${s.messages.size}:$msgSig"
            }
            val nextSignature = state.sessions.joinToString("|") { s ->
                val msgSig = s.messages.takeLast(6).joinToString(",") { m ->
                    "${m.id}:${m.bodyText.hashCode()}:${m.thoughtText.hashCode()}:${m.isStreaming}:${m.steeringStatusText}:${m.tools.size}"
                }
                "${s.id}:${s.title}:${s.branchName}:${s.activeTaskSummary}:${s.durationTag}:${s.messages.size}:$msgSig"
            }
            if (!hasCompletedInitialLiveSync || forceSelectFirst || isBrandNewTopSession || currentSignature != nextSignature) {
                val isFirstSync = !hasCompletedInitialLiveSync
                hasCompletedInitialLiveSync = true
                normalSessions.clear()
                normalSessions.addAll(state.sessions)
                if (isFirstSync || forceSelectFirst || isBrandNewTopSession) {
                    selectedNormalIndex = 0
                    val topFolder = state.sessions.first().branchName
                    if (topFolder.isNotBlank() && topFolder != "session" && topFolder != "live-sync") {
                        activePcProjectName = topFolder
                    }
                } else {
                    val matchedIdx = state.sessions.indexOfFirst { it.id == prevSelectedId }
                    selectedNormalIndex = if (matchedIdx >= 0) matchedIdx else 0
                }
            }
        }
    }

    suspend fun syncLiveSessionsFromBridge(
        host: String,
        port: Int,
        retryAttemptsOnDisconnect: Int = 1,
        forceSelectFirst: Boolean = false
    ): Boolean {
        val state = PcBridgeClient.fetchLiveSyncState(host, port)
        if (state != null) {
            connectionState = BridgeConnectionState.CONNECTED_PC
            autoSyncRetryCount = 0
            applyLiveSyncState(state, forceSelectFirst = forceSelectFirst)
            if (systemPromptBundle.updatedAt == "기본값 로드됨") {
                val livePrompts = PcBridgeClient.fetchSystemPrompts(host, port)
                if (livePrompts != null) {
                    systemPromptBundle = livePrompts
                }
            }
            return true
        }

        if (autoSyncEnabled) {
            connectionState = BridgeConnectionState.AUTO_RESYNCING
            val recovered = PcBridgeClient.autoResyncWithRetry(
                host = host,
                port = port,
                maxAttempts = retryAttemptsOnDisconnect.coerceAtLeast(1),
                retryDelayMs = 250L,
                onAttempt = { autoSyncRetryCount += 1 }
            )
            if (recovered != null) {
                applyLiveSyncState(recovered, forceSelectFirst = forceSelectFirst)
                return true
            }
            connectionState = BridgeConnectionState.AUTO_RESYNCING
        } else {
            connectionState = BridgeConnectionState.SIMULATION_ACTIVE
        }
        return false
    }

    fun triggerAutoResyncIfDisconnected() {
        if (!autoSyncEnabled) return
        val port = bridgePortText.toIntOrNull() ?: 8766
        connectionState = BridgeConnectionState.AUTO_RESYNCING
        coroutineScope.launch {
            syncLiveSessionsFromBridge(bridgeHost, port, retryAttemptsOnDisconnect = 2)
        }
    }

    // Continuous real-time PC Bridge session sync & auto-resync loop (sub-second 650ms cadence)
    LaunchedEffect(bridgeHost, bridgePortText, autoSyncEnabled) {
        val port = bridgePortText.toIntOrNull() ?: 8766
        while (true) {
            if (!isStreaming && viewMode == InspectorViewMode.DEMO_NORMAL) {
                syncLiveSessionsFromBridge(bridgeHost, port, retryAttemptsOnDisconnect = 1)
            }
            val hasRunningPcSession = normalSessions.any { it.durationTag == "running" || it.messages.lastOrNull()?.isStreaming == true }
            val nextDelayMs = when {
                connectionState == BridgeConnectionState.AUTO_RESYNCING && autoSyncEnabled -> 800L
                hasRunningPcSession -> 500L
                else -> 650L
            }
            delay(nextDelayMs)
        }
    }

    LaunchedEffect(currentSession.messages.size, isStreaming) {
        if (currentSession.messages.isNotEmpty()) {
            try {
                listState.scrollToItem(currentSession.messages.lastIndex)
            } catch (_: Exception) {
            }
        }
    }

    fun stopActiveStream() {
        activeStreamJob?.cancel()
        activeStreamJob = null
        activeStreamingAgentMsgId = null
        isStreaming = false
        steeringStatusLabel = ""
        pendingSteerNotes.clear()
    }

    fun steerActiveStream(rawSteerPrompt: String) {
        val trimmed = rawSteerPrompt.trim()
        if (trimmed.isEmpty() || !isStreaming) return

        val timeFmt = SimpleDateFormat("a h:mm", Locale.KOREAN).format(Date())
        val targetList = if (viewMode == InspectorViewMode.WORST_CASE) worstCaseSessions else normalSessions
        val idx = if (viewMode == InspectorViewMode.WORST_CASE) selectedWorstIndex else selectedNormalIndex
        val session = targetList[idx]

        val steerUserMessage = ChatMessage(
            id = "steer-user-${System.currentTimeMillis()}",
            role = MessageRole.USER,
            authorName = "나",
            timestamp = timeFmt,
            bodyText = trimmed
        )

        pendingSteerNotes.add(trimmed)
        steeringStatusLabel = "대화조정중 · 작업조정중 · 확인중"

        val agentId = activeStreamingAgentMsgId
        val updatedMessages = mutableListOf<ChatMessage>()
        for (msg in session.messages) {
            if (msg.id == agentId) {
                // Insert user steering bubble right before the ongoing agent bubble and mark agent bubble with steering status
                updatedMessages.add(steerUserMessage)
                updatedMessages.add(
                    msg.copy(
                        steeringStatusText = "대화조정중 · 작업조정중 · 확인중 ($trimmed)"
                    )
                )
            } else {
                updatedMessages.add(msg)
            }
        }
        targetList[idx] = session.copy(messages = updatedMessages)

        coroutineScope.launch {
            delay(350L)
            if (isStreaming) steeringStatusLabel = "대화조정중 · 작업조정 반영중..."
            delay(350L)
            if (isStreaming) steeringStatusLabel = "작업조정중 · 확인중..."
        }
    }

    var pendingClarification by remember { mutableStateOf<AiClarificationRequest?>(null) }

    fun executeStreamForAgentMessage(
        agentMsgId: String,
        effectivePrompt: String,
        clarificationChoice: String? = null
    ) {
        val timeFmt = SimpleDateFormat("a h:mm", Locale.KOREAN).format(Date())
        val targetList = if (viewMode == InspectorViewMode.WORST_CASE) worstCaseSessions else normalSessions
        val idx = if (viewMode == InspectorViewMode.WORST_CASE) selectedWorstIndex else selectedNormalIndex
        val session = targetList[idx]
        val port = bridgePortText.toIntOrNull() ?: 8766

        isStreaming = true
        streamPhaseLabel = "생각중"

        activeStreamJob = coroutineScope.launch {
            val promptWithClarification = if (!clarificationChoice.isNullOrBlank()) {
                "$effectivePrompt (선택 방향: $clarificationChoice)"
            } else {
                effectivePrompt
            }

            PcBridgeClient.streamAgentResponse(
                messageId = agentMsgId,
                userPrompt = promptWithClarification,
                sessionId = session.id,
                host = bridgeHost,
                port = port,
                timestamp = timeFmt,
                engineLabel = "${activeEngine.shortName} (${selectedReasoning.label})",
                modelId = effectiveModelId,
                isLocalAi = activeEngine.isLocal
            ).collect { frame ->
                streamPhaseLabel = frame.phaseLabel
                if (frame.fromLiveBridge) {
                    connectionState = BridgeConnectionState.CONNECTED_PC
                }

                val choicePrefix = if (!clarificationChoice.isNullOrBlank() && frame.isFinished) {
                    "• 선택 반영: $clarificationChoice\n\n"
                } else {
                    ""
                }
                val steerSuffix = if (pendingSteerNotes.isNotEmpty()) {
                    "\n\n• 실시간 조정 반영 (${pendingSteerNotes.joinToString(", ")}): 요청하신 방향에 맞춰 작업과 답변을 조정했습니다."
                } else {
                    ""
                }
                val steerBadge = if (pendingSteerNotes.isNotEmpty()) {
                    if (frame.isFinished) {
                        "대화조정 · 작업조정 확인 완료 (${pendingSteerNotes.last()})"
                    } else {
                        steeringStatusLabel.ifBlank { "대화조정중 · 작업조정중 · 확인중" }
                    }
                } else {
                    ""
                }

                val latestSession = targetList[idx]
                val updatedMessages = latestSession.messages.map { existing ->
                    if (existing.id == agentMsgId) {
                        frame.message.copy(
                            bodyText = if (frame.isFinished) choicePrefix + frame.message.bodyText + steerSuffix else frame.message.bodyText,
                            steeringStatusText = steerBadge
                        )
                    } else {
                        existing
                    }
                }
                targetList[idx] = latestSession.copy(messages = updatedMessages)

                if (frame.isFinished) {
                    isStreaming = false
                    activeStreamingAgentMsgId = null
                    steeringStatusLabel = ""
                    pendingSteerNotes.clear()
                    if (frame.syncState != null && viewMode == InspectorViewMode.DEMO_NORMAL) {
                        applyLiveSyncState(frame.syncState)
                    } else if (!frame.fromLiveBridge) {
                        triggerAutoResyncIfDisconnected()
                    }
                }
            }
        }
    }

    fun triggerAgentPrompt(rawPrompt: String) {
        val trimmed = rawPrompt.trim()
        if (trimmed.isEmpty()) return

        if (isStreaming && pendingClarification == null) {
            steerActiveStream(trimmed)
            return
        }

        if (viewMode == InspectorViewMode.EMPTY || viewMode == InspectorViewMode.ERROR || viewMode == InspectorViewMode.LOADING) {
            viewMode = InspectorViewMode.DEMO_NORMAL
        }

        stopActiveStream()
        pendingClarification = null
        val timeFmt = SimpleDateFormat("a h:mm", Locale.KOREAN).format(Date())
        val userMsgId = "user-${System.currentTimeMillis()}"
        val agentMsgId = "agent-${System.currentTimeMillis() + 1}"
        activeStreamingAgentMsgId = agentMsgId

        val targetList = if (viewMode == InspectorViewMode.WORST_CASE) worstCaseSessions else normalSessions
        val idx = if (viewMode == InspectorViewMode.WORST_CASE) selectedWorstIndex else selectedNormalIndex
        val session = targetList[idx]

        val userMessage = ChatMessage(
            id = userMsgId,
            role = MessageRole.USER,
            authorName = "나",
            timestamp = timeFmt,
            bodyText = trimmed
        )
        val placeholderAgent = ChatMessage(
            id = agentMsgId,
            role = MessageRole.AGENT,
            authorName = if (activeEngine.isLocal) activeEngine.shortName else "안그래비티 도우미",
            timestamp = timeFmt,
            thoughtText = "사용자 요청의 세부 방향을 확인하고 있습니다.",
            bodyText = "요청 의도를 확인하고 있습니다...",
            isStreaming = true
        )

        targetList[idx] = session.copy(
            messages = session.messages + userMessage + placeholderAgent,
            durationTag = "지금"
        )

        isStreaming = true
        streamPhaseLabel = "생각중"

        val clarification = buildClarificationQuestionForPrompt(trimmed, agentMsgId)
        if (clarification != null) {
            coroutineScope.launch {
                delay(180L)
                pendingClarification = clarification
            }
        } else {
            executeStreamForAgentMessage(agentMsgId = agentMsgId, effectivePrompt = trimmed)
        }
    }

    val recommendedPrompt by remember(currentSession.messages, isStreaming) {
        derivedStateOf {
            if (isStreaming) {
                "핵심 요약 및 실측 테스트 결과도 함께 추가해줘"
            } else {
                val lastAgentText = currentSession.messages.lastOrNull { it.role == MessageRole.AGENT }?.bodyText.orEmpty()
                when {
                    lastAgentText.contains("GitHub", ignoreCase = true) || lastAgentText.contains("푸시") ->
                        "방금 올린 GitHub 커밋과 빌드 테스트 상태 확인해줘"
                    lastAgentText.contains("폴더") || lastAgentText.contains("프로젝트") ->
                        "생성된 프로젝트 폴더 구조 점검하고 기능 테스트해줘"
                    lastAgentText.contains("첨부") || lastAgentText.contains("사진") || lastAgentText.contains("동영상") ->
                        "첨부된 항목 기반으로 작업 반영하고 테스트 실행해줘"
                    else ->
                        "방금 작업한 내용 실측 동작 및 기능 테스트 실행해줘"
                }
            }
        }
    }

    fun attachMediaToSessionAndPc(mediaType: String, fileName: String, fileSize: Long) {
        val port = bridgePortText.toIntOrNull() ?: 8766
        val timeFmt = SimpleDateFormat("a h:mm", Locale.KOREAN).format(Date())
        val targetList = if (viewMode == InspectorViewMode.WORST_CASE) worstCaseSessions else normalSessions
        val idx = if (viewMode == InspectorViewMode.WORST_CASE) selectedWorstIndex else selectedNormalIndex
        val session = targetList[idx]

        val userMsg = ChatMessage(
            id = "attach-user-${System.currentTimeMillis()}",
            role = MessageRole.USER,
            authorName = "나",
            timestamp = timeFmt,
            bodyText = "[$mediaType 첨부] $fileName"
        )
        val agentMsg = ChatMessage(
            id = "attach-agent-${System.currentTimeMillis() + 1}",
            role = MessageRole.AGENT,
            authorName = "안그래비티 도우미",
            timestamp = timeFmt,
            bodyText = "선택하신 $mediaType($fileName)을(를) 현재 PC 안그래비티 세션 및 프로젝트 폴더($activePcProjectName/attachments)에 전달했습니다."
        )
        targetList[idx] = session.copy(messages = session.messages + userMsg + agentMsg)
        viewMode = InspectorViewMode.DEMO_NORMAL

        coroutineScope.launch {
            val state = PcBridgeClient.attachMediaToPc(
                host = bridgeHost,
                port = port,
                mediaType = mediaType,
                fileName = fileName,
                fileSize = fileSize,
                sessionId = session.id,
                projectName = activePcProjectName
            )
            if (state != null) {
                applyLiveSyncState(state)
            } else {
                triggerAutoResyncIfDisconnected()
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val (name, size) = resolveUriDisplayNameAndSize(context, uri, "mobile_photo_${System.currentTimeMillis() % 10000}.jpg")
            attachMediaToSessionAndPc("사진", name, size)
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val (name, size) = resolveUriDisplayNameAndSize(context, uri, "mobile_file_${System.currentTimeMillis() % 10000}.txt")
            attachMediaToSessionAndPc("파일", name, size)
        }
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val (name, size) = resolveUriDisplayNameAndSize(context, uri, "mobile_video_${System.currentTimeMillis() % 10000}.mp4")
            attachMediaToSessionAndPc("동영상", name, size)
        }
    }

    fun pushFullWorkToGitHub() {
        val port = bridgePortText.toIntOrNull() ?: 8766
        val timeFmt = SimpleDateFormat("a h:mm", Locale.KOREAN).format(Date())
        val targetList = if (viewMode == InspectorViewMode.WORST_CASE) worstCaseSessions else normalSessions
        val idx = if (viewMode == InspectorViewMode.WORST_CASE) selectedWorstIndex else selectedNormalIndex
        val session = targetList[idx]

        val userMsg = ChatMessage(
            id = "gh-user-${System.currentTimeMillis()}",
            role = MessageRole.USER,
            authorName = "나",
            timestamp = timeFmt,
            bodyText = "작업내용 깃허브 올리기 (전체 소스코드 및 프로젝트 작업)"
        )
        val pendingId = "gh-agent-${System.currentTimeMillis() + 1}"
        val pendingAgentMsg = ChatMessage(
            id = pendingId,
            role = MessageRole.AGENT,
            authorName = "안그래비티 도우미",
            timestamp = timeFmt,
            bodyText = "전체 소스코드 및 프로젝트 작업내용(SESSION_WORK_LOG.md 포함)을 내 GitHub 저장소(https://github.com/icandoit122118-del/antigravity-remote-apk)에 커밋·푸시하고 있습니다..."
        )
        targetList[idx] = session.copy(messages = session.messages + userMsg + pendingAgentMsg)
        viewMode = InspectorViewMode.DEMO_NORMAL

        coroutineScope.launch {
            val state = PcBridgeClient.executeRemoteCommand(
                host = bridgeHost,
                port = port,
                command = "github_push",
                sessionId = session.id,
                projectName = activePcProjectName
            )
            if (state != null) {
                applyLiveSyncState(state)
            } else {
                triggerAutoResyncIfDisconnected()
                val cur = targetList[idx]
                targetList[idx] = cur.copy(
                    messages = cur.messages.map { m ->
                        if (m.id == pendingId) {
                            m.copy(
                                bodyText = "전체 소스코드 및 프로젝트 작업내용을 내 GitHub에 올렸습니다.\n• 저장소: https://github.com/icandoit122118-del/antigravity-remote-apk\n• 프로젝트: $activePcProjectName\n• 상태: SESSION_WORK_LOG.md 및 전체 소스코드 origin/main 반영 완료"
                            )
                        } else {
                            m
                        }
                    }
                )
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AntigravityPalette.NightGround)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 1. Top Header: Left [세션·프로젝트] icon, Right [화면터치원격(Moonlight)] & [설정] icons
            SimpleElephantHeader(
                currentSessionTitle = currentSession.title,
                activeProjectFolder = activePcProjectName,
                isSessionPopupOpen = showSessionProjectPopup,
                isMoonlightActive = showMoonlightTouchStream,
                connectionState = if (viewMode == InspectorViewMode.ERROR) {
                    BridgeConnectionState.DISCONNECTED_ERROR
                } else {
                    connectionState
                },
                reducedMotion = reducedMotion,
                onToggleSessionPopup = {
                    showSessionProjectPopup = !showSessionProjectPopup
                    if (showSessionProjectPopup) {
                        showPlusMenuPopup = false
                        showModelReasoningPopup = false
                    }
                },
                onOpenMoonlightStream = {
                    showSessionProjectPopup = false
                    showPlusMenuPopup = false
                    showModelReasoningPopup = false
                    showMoonlightTouchStream = true
                },
                onOpenSettings = {
                    showSessionProjectPopup = false
                    showSettingsSheet = true
                }
            )

            // 2. Main Conversation Area (Takes full center space without extra chip bars!)
            // Left-edge swipe (drag right from the left edge) opens the session/project panel.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        val edgeWidthPx = 72.dp.toPx()
                        val openDistancePx = 56.dp.toPx()
                        awaitEachGesture {
                            val down = awaitFirstDown(
                                requireUnconsumed = false,
                                pass = PointerEventPass.Initial
                            )
                            if (down.position.x > edgeWidthPx) return@awaitEachGesture
                            val slop = viewConfiguration.touchSlop
                            var totalX = 0f
                            var totalY = 0f
                            var isEdgeSwipe = false
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                                if (!change.pressed) return@awaitEachGesture
                                val delta = change.positionChange()
                                totalX += delta.x
                                totalY += delta.y
                                if (!isEdgeSwipe && (abs(totalX) > slop || abs(totalY) > slop)) {
                                    // Only a mostly-horizontal rightward drag counts; vertical scroll passes through.
                                    if (totalX > 0f && abs(totalX) > abs(totalY) * 1.5f) {
                                        isEdgeSwipe = true
                                    } else {
                                        return@awaitEachGesture
                                    }
                                }
                                if (isEdgeSwipe) {
                                    change.consume()
                                    if (totalX >= openDistancePx) {
                                        showPlusMenuPopup = false
                                        showModelReasoningPopup = false
                                        showSessionProjectPopup = true
                                        return@awaitEachGesture
                                    }
                                }
                            }
                        }
                    }
            ) {
                // Keep the system back gesture from swallowing the left-edge swipe.
                // Android only honors up to 200dp of vertical exclusion per edge, so keep the rect at 200dp.
                Spacer(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .width(40.dp)
                        .height(200.dp)
                        .systemGestureExclusion()
                )
                when (viewMode) {
                    InspectorViewMode.LOADING -> {
                        LoadingStateCardList(reducedMotion = reducedMotion)
                    }
                    InspectorViewMode.EMPTY -> {
                        EmptyNightStateCard(
                            reducedMotion = reducedMotion,
                            onSelectStarterPrompt = { starter ->
                                triggerAgentPrompt(starter)
                            }
                        )
                    }
                    InspectorViewMode.ERROR -> {
                        ErrorRecoveryStateCard(
                            reducedMotion = reducedMotion,
                            onRetryBridge = {
                                coroutineScope.launch {
                                    connectionState = BridgeConnectionState.CONNECTING
                                    delay(350L)
                                    val ok = PcBridgeClient.checkBridgeHealth(
                                        bridgeHost,
                                        bridgePortText.toIntOrNull() ?: 8766
                                    )
                                    if (ok) {
                                        connectionState = BridgeConnectionState.CONNECTED_PC
                                        viewMode = InspectorViewMode.DEMO_NORMAL
                                    } else {
                                        connectionState = BridgeConnectionState.DISCONNECTED_ERROR
                                    }
                                }
                            },
                            onContinueSimulation = {
                                connectionState = BridgeConnectionState.SIMULATION_ACTIVE
                                viewMode = InspectorViewMode.DEMO_NORMAL
                            },
                            onOpenSettings = { showSettingsSheet = true }
                        )
                    }
                    InspectorViewMode.DEMO_NORMAL, InspectorViewMode.WORST_CASE -> {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 8.dp,
                                bottom = 16.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(
                                items = currentSession.messages,
                                key = { it.id }
                            ) { message ->
                                val isDetailExpanded = expandedDetails[message.id] ?: false
                                val isStreamingThisBubble = isStreaming && (
                                    message.id == activeStreamingAgentMsgId ||
                                        (message.role == MessageRole.AGENT && message.id == currentSession.messages.lastOrNull()?.id)
                                )

                                ChatMessageCard(
                                    message = message,
                                    isStreamingBubble = isStreamingThisBubble,
                                    streamPhaseLabel = streamPhaseLabel,
                                    steeringStatusLabel = steeringStatusLabel,
                                    isDetailExpanded = isDetailExpanded,
                                    copiedBlockId = copiedBlockId,
                                    reducedMotion = reducedMotion,
                                    onToggleDetail = {
                                        expandedDetails[message.id] = !isDetailExpanded
                                    },
                                    onCopyCode = { codeText ->
                                        copyToClipboard(context, codeText)
                                        copiedBlockId = message.id
                                        coroutineScope.launch {
                                            delay(1800L)
                                            if (copiedBlockId == message.id) {
                                                copiedBlockId = null
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // 2.5. Smooth Bottom-Up AI Clarification Question Sheet (Appears right below the chat window with [건너뛰기] & [제출] buttons!)
            val clarificationEase = remember { CubicBezierEasing(0.22f, 1f, 0.36f, 1f) }
            AnimatedVisibility(
                visible = pendingClarification != null,
                enter = if (reducedMotion) {
                    fadeIn(tween(120))
                } else {
                    slideInVertically(
                        initialOffsetY = { fullHeight -> fullHeight / 2 },
                        animationSpec = tween(durationMillis = 310, easing = clarificationEase)
                    ) + expandVertically(
                        expandFrom = Alignment.Bottom,
                        animationSpec = tween(durationMillis = 310, easing = clarificationEase)
                    ) + fadeIn(
                        animationSpec = tween(durationMillis = 220)
                    )
                },
                exit = if (reducedMotion) {
                    fadeOut(tween(100))
                } else {
                    slideOutVertically(
                        targetOffsetY = { fullHeight -> fullHeight / 2 },
                        animationSpec = tween(durationMillis = 220, easing = clarificationEase)
                    ) + shrinkVertically(
                        shrinkTowards = Alignment.Bottom,
                        animationSpec = tween(durationMillis = 220, easing = clarificationEase)
                    ) + fadeOut(
                        animationSpec = tween(durationMillis = 160)
                    )
                }
            ) {
                val activeQuestion = pendingClarification
                if (activeQuestion != null) {
                    AiClarificationBottomCard(
                        request = activeQuestion,
                        reducedMotion = reducedMotion,
                        onSkip = {
                            val req = pendingClarification
                            pendingClarification = null
                            if (req != null) {
                                executeStreamForAgentMessage(
                                    agentMsgId = req.agentMessageId,
                                    effectivePrompt = req.originalPrompt,
                                    clarificationChoice = null
                                )
                            }
                        },
                        onSubmit = { selectedOption ->
                            val req = pendingClarification
                            pendingClarification = null
                            if (req != null) {
                                executeStreamForAgentMessage(
                                    agentMsgId = req.agentMessageId,
                                    effectivePrompt = req.originalPrompt,
                                    clarificationChoice = selectedOption
                                )
                            }
                        }
                    )
                }
            }

            // 3. Clean Bottom Input Bar with Inside [+] Button, Recommended Prompt, Ultra-Compact Model·Reasoning Tag, and Send/Stop Toggle Button
            SimplePromptInputDock(
                quickPrompts = currentSession.quickPrompts,
                recommendedPrompt = recommendedPrompt,
                promptText = promptInput,
                isStreaming = isStreaming,
                activeEngine = activeEngine,
                engines = engineOptions,
                selectedReasoning = selectedReasoning,
                showModelReasoningPopup = showModelReasoningPopup,
                showPlusMenuPopup = showPlusMenuPopup,
                reducedMotion = reducedMotion,
                onPromptTextChange = { promptInput = it },
                onQuickPromptClick = { quickText ->
                    focusManager.clearFocus()
                    triggerAgentPrompt(quickText)
                },
                onSendClick = {
                    val toSend = promptInput.trim().ifBlank { recommendedPrompt }
                    promptInput = ""
                    showPlusMenuPopup = false
                    showModelReasoningPopup = false
                    focusManager.clearFocus()
                    triggerAgentPrompt(toSend)
                },
                onStopClick = {
                    stopActiveStream()
                },
                onTogglePlusPopup = {
                    showPlusMenuPopup = !showPlusMenuPopup
                    if (showPlusMenuPopup) showModelReasoningPopup = false
                },
                onPickPhoto = {
                    showPlusMenuPopup = false
                    attachMediaToSessionAndPc("사진", "photo_${System.currentTimeMillis() % 10000}.jpg", 248_000L)
                    try {
                        photoPickerLauncher.launch("image/*")
                    } catch (_: Exception) {
                    }
                },
                onPickFile = {
                    showPlusMenuPopup = false
                    attachMediaToSessionAndPc("파일", "project_file_${System.currentTimeMillis() % 10000}.kt", 42_000L)
                    try {
                        filePickerLauncher.launch("*/*")
                    } catch (_: Exception) {
                    }
                },
                onPickVideo = {
                    showPlusMenuPopup = false
                    attachMediaToSessionAndPc("동영상", "screen_record_${System.currentTimeMillis() % 10000}.mp4", 1_850_000L)
                    try {
                        videoPickerLauncher.launch("video/*")
                    } catch (_: Exception) {
                    }
                },
                onPushWorkToGitHub = {
                    showPlusMenuPopup = false
                    pushFullWorkToGitHub()
                },
                onOpenSystemPromptEditor = {
                    showPlusMenuPopup = false
                    val port = bridgePortText.toIntOrNull() ?: 8766
                    systemPromptStatusBanner = ""
                    showSystemPromptEditor = true
                    coroutineScope.launch {
                        val livePrompts = PcBridgeClient.fetchSystemPrompts(bridgeHost, port)
                        if (livePrompts != null) {
                            connectionState = BridgeConnectionState.CONNECTED_PC
                            systemPromptBundle = livePrompts.copy(
                                updatedAt = "${livePrompts.updatedAt}#${System.currentTimeMillis()}"
                            )
                            systemPromptStatusBanner = "PC 실시간 프롬프트 동기화됨"
                        }
                    }
                },
                onOpenMoonlightStream = {
                    showPlusMenuPopup = false
                    showModelReasoningPopup = false
                    showMoonlightTouchStream = true
                },
                onToggleModelPopup = {
                    showModelReasoningPopup = !showModelReasoningPopup
                    if (showModelReasoningPopup) showPlusMenuPopup = false
                },
                onSelectEngine = { engine ->
                    selectedEngineId = engine.id
                },
                onSelectReasoning = { effort ->
                    selectedReasoning = effort
                }
            )
        }

        // Top-Left Session & Project Folder Popover (Animates outward starting directly from the top-left icon!)
        if (showSessionProjectPopup) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.54f))
                    .pointerInput(Unit) {
                        var dragSum = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { dragSum = 0f },
                            onDragEnd = { dragSum = 0f },
                            onDragCancel = { dragSum = 0f }
                        ) { _, dragAmount ->
                            dragSum += dragAmount
                            if (dragSum < -48.dp.toPx()) {
                                showSessionProjectPopup = false
                                dragSum = 0f
                            }
                        }
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { showSessionProjectPopup = false }
                    )
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, top = 54.dp),
            contentAlignment = Alignment.TopStart
        ) {
            val strongEaseOut = remember { CubicBezierEasing(0.23f, 1f, 0.32f, 1f) }
            val iconOrigin = remember { TransformOrigin(0.04f, 0.0f) }

            AnimatedVisibility(
                visible = showSessionProjectPopup,
                enter = if (reducedMotion) {
                    fadeIn(tween(120))
                } else {
                    scaleIn(
                        initialScale = 0.14f,
                        transformOrigin = iconOrigin,
                        animationSpec = tween(durationMillis = 260, easing = strongEaseOut)
                    ) + expandIn(
                        expandFrom = Alignment.TopStart,
                        initialSize = { IntSize(44, 44) },
                        animationSpec = tween(durationMillis = 260, easing = strongEaseOut)
                    ) + fadeIn(
                        animationSpec = tween(durationMillis = 170)
                    )
                },
                exit = if (reducedMotion) {
                    fadeOut(tween(100))
                } else {
                    scaleOut(
                        targetScale = 0.14f,
                        transformOrigin = iconOrigin,
                        animationSpec = tween(durationMillis = 190, easing = strongEaseOut)
                    ) + shrinkOut(
                        shrinkTowards = Alignment.TopStart,
                        targetSize = { IntSize(44, 44) },
                        animationSpec = tween(durationMillis = 190, easing = strongEaseOut)
                    ) + fadeOut(
                        animationSpec = tween(durationMillis = 140)
                    )
                }
            ) {
                TopLeftSessionAndProjectPopover(
                    sessions = normalSessions,
                    selectedSessionIndex = selectedNormalIndex,
                    pcProjects = pcProjects,
                    activePcProjectName = activePcProjectName,
                    currentViewMode = viewMode,
                    connectionState = connectionState,
                    host = bridgeHost,
                    port = bridgePortText,
                    onSelectSession = { idx ->
                        selectedNormalIndex = idx
                        val chosenFolder = normalSessions.getOrNull(idx)?.branchName.orEmpty()
                        if (chosenFolder.isNotBlank() && chosenFolder != "session" && chosenFolder != "live-sync") {
                            activePcProjectName = chosenFolder
                        }
                        viewMode = InspectorViewMode.DEMO_NORMAL
                        showSessionProjectPopup = false
                    },
                    onSelectProjectFolder = { folderName ->
                        activePcProjectName = folderName
                    },
                    onSessionAction = { sessionId, action ->
                        val port = bridgePortText.toIntOrNull() ?: 8766
                        when (action) {
                            "pin" -> {
                                val idx = normalSessions.indexOfFirst { it.id == sessionId }
                                if (idx > 0) {
                                    val item = normalSessions.removeAt(idx)
                                    normalSessions.add(0, item)
                                    selectedNormalIndex = 0
                                }
                            }
                            "archive", "delete" -> {
                                if (normalSessions.size > 1) {
                                    val idx = normalSessions.indexOfFirst { it.id == sessionId }
                                    if (idx >= 0) {
                                        normalSessions.removeAt(idx)
                                        selectedNormalIndex = selectedNormalIndex.coerceIn(0, (normalSessions.size - 1).coerceAtLeast(0))
                                    }
                                }
                            }
                        }
                        coroutineScope.launch {
                            val state = PcBridgeClient.performSessionAction(bridgeHost, port, sessionId, action)
                            if (state != null) {
                                applyLiveSyncState(state)
                            } else {
                                triggerAutoResyncIfDisconnected()
                            }
                        }
                    },
                    onProjectAction = { folderName, action ->
                        val port = bridgePortText.toIntOrNull() ?: 8766
                        when (action) {
                            "pin" -> {
                                val idx = pcProjects.indexOfFirst { it.name == folderName }
                                if (idx > 0) {
                                    val item = pcProjects.removeAt(idx)
                                    pcProjects.add(0, item)
                                }
                            }
                            "archive", "delete" -> {
                                val idx = pcProjects.indexOfFirst { it.name == folderName }
                                if (idx >= 0) {
                                    pcProjects.removeAt(idx)
                                    if (activePcProjectName == folderName && pcProjects.isNotEmpty()) {
                                        activePcProjectName = pcProjects.first().name
                                    }
                                }
                            }
                        }
                        coroutineScope.launch {
                            val state = PcBridgeClient.performProjectAction(bridgeHost, port, folderName, action)
                            if (state != null) {
                                applyLiveSyncState(state)
                            } else {
                                triggerAutoResyncIfDisconnected()
                            }
                        }
                    },
                    onCreateRemoteSession = { sessionTitle, projectFolder ->
                        val port = bridgePortText.toIntOrNull() ?: 8766
                        coroutineScope.launch {
                            val state = PcBridgeClient.createRemoteSession(bridgeHost, port, sessionTitle, projectFolder)
                            if (state != null) {
                                applyLiveSyncState(state, forceSelectFirst = true)
                            } else {
                                val folder = projectFolder.ifBlank { activePcProjectName }
                                val newSession = WorkspaceSession(
                                    id = "local-session-${System.currentTimeMillis()}",
                                    title = sessionTitle.ifBlank { "새 세션" },
                                    ambientCaption = folder,
                                    branchName = folder,
                                    activeTaskSummary = "모바일에서 생성한 새 세션",
                                    durationTag = "방금",
                                    quickPrompts = listOf("오늘 해야 할 일을 깔끔하게 정리해줘", "새 프로젝트 구조를 잡아줘"),
                                    messages = listOf(
                                        ChatMessage(
                                            id = "init-${System.currentTimeMillis()}",
                                            role = MessageRole.AGENT,
                                            authorName = "안그래비티 도우미",
                                            timestamp = "지금",
                                            bodyText = "새 세션(${sessionTitle.ifBlank { "새 세션" }}) 및 프로젝트 폴더($folder) 연동을 시작했습니다."
                                        )
                                    )
                                )
                                normalSessions.add(0, newSession)
                                selectedNormalIndex = 0
                                triggerAutoResyncIfDisconnected()
                            }
                            viewMode = InspectorViewMode.DEMO_NORMAL
                            showSessionProjectPopup = false
                        }
                    },
                    onCreateRemoteProject = { folderName ->
                        val port = bridgePortText.toIntOrNull() ?: 8766
                        coroutineScope.launch {
                            val state = PcBridgeClient.createRemoteProjectFolder(
                                host = bridgeHost,
                                port = port,
                                folderName = folderName,
                                sessionId = currentSession.id
                            )
                            if (state != null) {
                                applyLiveSyncState(state)
                            } else {
                                pcProjects.add(0, PcProjectFolder(folderName, "", 1))
                                activePcProjectName = folderName
                                triggerAutoResyncIfDisconnected()
                            }
                            viewMode = InspectorViewMode.DEMO_NORMAL
                        }
                    },
                    onSyncNow = {
                        val port = bridgePortText.toIntOrNull() ?: 8766
                        coroutineScope.launch {
                            syncLiveSessionsFromBridge(bridgeHost, port, retryAttemptsOnDisconnect = 3)
                        }
                    },
                    onDismiss = { showSessionProjectPopup = false }
                )
            }
        }

        // Unified Settings Dialog (Session/Project creation removed and moved to Top-Left Icon!)
        if (showSettingsSheet) {
            UnifiedSettingsDialog(
                engines = engineOptions,
                selectedEngineId = selectedEngineId,
                currentViewMode = viewMode,
                initialHost = bridgeHost,
                initialPort = bridgePortText,
                initialLocalModel = customLocalModelId,
                connectionState = connectionState,
                autoSyncEnabled = autoSyncEnabled,
                autoSyncRetryCount = autoSyncRetryCount,
                onToggleAutoSync = { enabled ->
                    autoSyncEnabled = enabled
                    if (enabled && connectionState != BridgeConnectionState.CONNECTED_PC) {
                        triggerAutoResyncIfDisconnected()
                    } else if (!enabled && connectionState == BridgeConnectionState.AUTO_RESYNCING) {
                        connectionState = BridgeConnectionState.SIMULATION_ACTIVE
                    }
                },
                onSelectEngine = { engine ->
                    selectedEngineId = engine.id
                },
                onExecuteRemoteCommand = { cmd ->
                    val port = bridgePortText.toIntOrNull() ?: 8766
                    coroutineScope.launch {
                        val state = PcBridgeClient.executeRemoteCommand(
                            host = bridgeHost,
                            port = port,
                            command = cmd,
                            sessionId = currentSession.id
                        )
                        if (state != null) {
                            applyLiveSyncState(state)
                        } else {
                            triggerAutoResyncIfDisconnected()
                        }
                        viewMode = InspectorViewMode.DEMO_NORMAL
                        showSettingsSheet = false
                    }
                },
                onSyncNow = {
                    val port = bridgePortText.toIntOrNull() ?: 8766
                    coroutineScope.launch {
                        syncLiveSessionsFromBridge(bridgeHost, port, retryAttemptsOnDisconnect = 3)
                    }
                },
                onClearCurrentChat = {
                    val current = normalSessions[selectedNormalIndex]
                    normalSessions[selectedNormalIndex] = current.copy(messages = emptyList())
                    viewMode = InspectorViewMode.EMPTY
                    showSettingsSheet = false
                },
                onOpenSystemPromptEditor = {
                    showSettingsSheet = false
                    val port = bridgePortText.toIntOrNull() ?: 8766
                    systemPromptStatusBanner = ""
                    showSystemPromptEditor = true
                    coroutineScope.launch {
                        val livePrompts = PcBridgeClient.fetchSystemPrompts(bridgeHost, port)
                        if (livePrompts != null) {
                            connectionState = BridgeConnectionState.CONNECTED_PC
                            systemPromptBundle = livePrompts.copy(
                                updatedAt = "${livePrompts.updatedAt}#${System.currentTimeMillis()}"
                            )
                            systemPromptStatusBanner = "PC 실시간 프롬프트 동기화됨"
                        }
                    }
                },
                onOpenMoonlightStream = {
                    showSettingsSheet = false
                    showMoonlightTouchStream = true
                },
                onSelectViewMode = { mode ->
                    viewMode = mode
                    showSettingsSheet = false
                },
                onDismiss = { showSettingsSheet = false },
                onSaveSettings = { newHost, newPort, newLocalModel ->
                    bridgeHost = newHost.ifBlank { "127.0.0.1" }
                    bridgePortText = newPort.ifBlank { "8766" }
                    customLocalModelId = newLocalModel.trim()
                    coroutineScope.launch {
                        connectionState = BridgeConnectionState.CONNECTING
                        syncLiveSessionsFromBridge(
                            bridgeHost,
                            bridgePortText.toIntOrNull() ?: 8766
                        )
                        showSettingsSheet = false
                    }
                }
            )
        }

        if (showSystemPromptEditor) {
            SystemPromptEditorDialog(
                bundle = systemPromptBundle,
                statusBanner = systemPromptStatusBanner,
                connectionState = connectionState,
                onReloadFromPc = {
                    val port = bridgePortText.toIntOrNull() ?: 8766
                    coroutineScope.launch {
                        val fetched = PcBridgeClient.fetchSystemPrompts(bridgeHost, port)
                        if (fetched != null) {
                            connectionState = BridgeConnectionState.CONNECTED_PC
                            systemPromptBundle = fetched.copy(
                                updatedAt = "${fetched.updatedAt}#${System.currentTimeMillis()}"
                            )
                            systemPromptStatusBanner = "PC 원본 프롬프트를 다시 불러왔습니다"
                        } else {
                            systemPromptStatusBanner = "로컬 기본 프롬프트가 유지됩니다"
                        }
                    }
                },
                onSaveTargetPrompt = { targetKey, targetLabel, updatedContent ->
                    val port = bridgePortText.toIntOrNull() ?: 8766
                    val lineCount = updatedContent.lines().size
                    val timeFmt = SimpleDateFormat("a h:mm", Locale.KOREAN).format(Date())
                    systemPromptBundle = when (targetKey) {
                        "design-motion" -> systemPromptBundle.copy(designMotionMd = updatedContent, updatedAt = timeFmt)
                        "claude" -> systemPromptBundle.copy(claudeMd = updatedContent, updatedAt = timeFmt)
                        else -> systemPromptBundle.copy(geminiMd = updatedContent, updatedAt = timeFmt)
                    }
                    systemPromptStatusBanner = "$targetLabel 저장 중..."
                    coroutineScope.launch {
                        val saved = PcBridgeClient.saveSystemPrompt(
                            host = bridgeHost,
                            port = port,
                            target = targetKey,
                            content = updatedContent,
                            sessionId = currentSession.id
                        )
                        if (saved != null) {
                            connectionState = BridgeConnectionState.CONNECTED_PC
                            systemPromptBundle = saved.copy(
                                updatedAt = "${saved.updatedAt}#${System.currentTimeMillis()}"
                            )
                            if (saved.syncState != null) {
                                applyLiveSyncState(saved.syncState)
                            }
                            systemPromptStatusBanner = "PC에 $targetLabel 저장 완료 (${lineCount}줄)"
                        } else {
                            val targetList = if (viewMode == InspectorViewMode.WORST_CASE) worstCaseSessions else normalSessions
                            val idx = if (viewMode == InspectorViewMode.WORST_CASE) selectedWorstIndex else selectedNormalIndex
                            val cur = targetList[idx]
                            val userMsg = ChatMessage(
                                id = "sp-user-${System.currentTimeMillis()}",
                                role = MessageRole.USER,
                                authorName = "나",
                                timestamp = timeFmt,
                                bodyText = "[시스템 프롬프트 편집] $targetLabel 저장"
                            )
                            val agentMsg = ChatMessage(
                                id = "sp-agent-${System.currentTimeMillis() + 1}",
                                role = MessageRole.AGENT,
                                authorName = "안그래비티 도우미",
                                timestamp = timeFmt,
                                bodyText = "$targetLabel 내용을 업데이트하고 저장했습니다.\n• 줄 수: ${lineCount}줄 (${updatedContent.length}자)"
                            )
                            targetList[idx] = cur.copy(messages = cur.messages + userMsg + agentMsg)
                            systemPromptStatusBanner = "$targetLabel 저장 완료 (${lineCount}줄)"
                        }
                    }
                },
                onDismiss = { showSystemPromptEditor = false }
            )
        }

        // Full-Screen Sunshine + Moonlight 60~120FPS Touchscreen Remote Control Overlay
        if (showMoonlightTouchStream) {
            MoonlightTouchScreenOverlay(
                host = bridgeHost,
                port = bridgePortText.toIntOrNull() ?: 8766,
                connectionState = connectionState,
                touchMode = moonlightTouchMode,
                fpsTarget = moonlightFpsTarget,
                reducedMotion = reducedMotion,
                onTouchModeChange = { moonlightTouchMode = it },
                onFpsTargetChange = { moonlightFpsTarget = it },
                onConnectionVerified = {
                    connectionState = BridgeConnectionState.CONNECTED_PC
                },
                onDismiss = { showMoonlightTouchStream = false }
            )
        }
    }
}

@Composable
private fun SimpleElephantHeader(
    currentSessionTitle: String,
    activeProjectFolder: String,
    isSessionPopupOpen: Boolean,
    isMoonlightActive: Boolean,
    connectionState: BridgeConnectionState,
    reducedMotion: Boolean,
    onToggleSessionPopup: () -> Unit,
    onOpenMoonlightStream: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val sessionButtonInteraction = remember { MutableInteractionSource() }
    val moonlightInteraction = remember { MutableInteractionSource() }
    val settingsInteraction = remember { MutableInteractionSource() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Top-Left Session & Project Folder Icon + Subtle Session/Folder Name Indicator
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
                .tactilePressScale(sessionButtonInteraction, reducedMotion)
                .clickable(
                    interactionSource = sessionButtonInteraction,
                    indication = null,
                    role = Role.Button,
                    onClick = onToggleSessionPopup
                )
                .semantics {
                    contentDescription = "세션 및 프로젝트 폴더 열기"
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = if (isSessionPopupOpen) {
                    Color(0xFF1E3B36).copy(alpha = 0.72f)
                } else {
                    AntigravityPalette.NightSurface
                }
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = AntigravityIcons.SessionFolderStack,
                        contentDescription = null,
                        tint = if (isSessionPopupOpen) {
                            AntigravityPalette.CalmSageAccent
                        } else {
                            AntigravityPalette.WarmIvoryInk
                        },
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(
                                when (connectionState) {
                                    BridgeConnectionState.CONNECTED_PC -> AntigravityPalette.CalmSageAccent
                                    BridgeConnectionState.AUTO_RESYNCING, BridgeConnectionState.CONNECTING -> AntigravityPalette.WarmLampGold
                                    else -> AntigravityPalette.WarmIvoryInk.copy(alpha = 0.35f)
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = currentSessionTitle,
                        style = AntigravityTypography.PillLabel.copy(
                            color = AntigravityPalette.WarmIvoryInk.copy(alpha = 0.86f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (isSessionPopupOpen) AntigravityIcons.ChevronUp else AntigravityIcons.ChevronDown,
                        contentDescription = null,
                        tint = if (isSessionPopupOpen) {
                            AntigravityPalette.CalmSageAccent
                        } else {
                            AntigravityPalette.WarmIvoryInk.copy(alpha = 0.52f)
                        },
                        modifier = Modifier.size(11.dp)
                    )
                }
            }
        }

        // Right: [PC 화면 터치 원격 (Moonlight)] 44dp circle + [설정] 44dp circle
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier
                    .size(44.dp)
                    .tactilePressScale(moonlightInteraction, reducedMotion)
                    .clickable(
                        interactionSource = moonlightInteraction,
                        indication = null,
                        role = Role.Button,
                        onClick = onOpenMoonlightStream
                    )
                    .semantics {
                        contentDescription = "PC 화면 터치 원격 제어 열기"
                    },
                shape = CircleShape,
                color = if (isMoonlightActive) {
                    Color(0xFF1E3B36).copy(alpha = 0.78f)
                } else {
                    AntigravityPalette.NightSurface
                }
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = AntigravityIcons.DesktopStreamCursor,
                        contentDescription = null,
                        tint = AntigravityPalette.CalmSageAccent,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .size(44.dp)
                    .tactilePressScale(settingsInteraction, reducedMotion)
                    .clickable(
                        interactionSource = settingsInteraction,
                        indication = null,
                        role = Role.Button,
                        onClick = onOpenSettings
                    )
                    .semantics {
                        contentDescription = "설정 열기"
                    },
                shape = CircleShape,
                color = AntigravityPalette.NightSurface
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = AntigravityIcons.SettingsSliders,
                        contentDescription = null,
                        tint = AntigravityPalette.WarmIvoryInk,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatMessageCard(
    message: ChatMessage,
    isStreamingBubble: Boolean,
    streamPhaseLabel: String,
    steeringStatusLabel: String,
    isDetailExpanded: Boolean,
    copiedBlockId: String?,
    reducedMotion: Boolean,
    onToggleDetail: () -> Unit,
    onCopyCode: (String) -> Unit
) {
    val isUser = message.role == MessageRole.USER
    val cardColor = Color(0xFF000000)
    val bubbleShape = if (isUser) {
        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp)
    } else {
        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 6.dp, bottomEnd = 20.dp)
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            val cleanBody = remember(message.bodyText, isUser) {
                val stripped = message.bodyText
                    .replace(Regex("""\s*\(~?/[^)]*\)"""), "")
                    .replace(Regex("""(?:[A-Za-z]:\\|[A-Za-z]:/|~/|/sdcard/)[^\s)`"']+"""), "")
                    .trim()
                if (!isUser) {
                    stripped.replace(Regex("""^\[[^\]]*\]\s*"""), "").trim()
                } else {
                    stripped
                }
            }

            Surface(
                modifier = Modifier.widthIn(max = if (isUser) 308.dp else 348.dp),
                shape = bubbleShape,
                color = cardColor
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 15.dp, vertical = 11.dp)
                ) {
                    // Main Answer Text Only (No top PC remote sync badge, no author name or timestamp)
                    Text(
                        text = cleanBody,
                        style = AntigravityTypography.ChatBody
                    )

                    // Small-font live steering / task adjustment status ("대화조정중 · 작업조정중 · 확인중")
                    if (message.steeringStatusText.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = message.steeringStatusText,
                            style = AntigravityTypography.SecondaryBody.copy(
                                color = AntigravityPalette.WarmLampGold,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        )
                    }

                    // Optional Code Block (only when code is present)
                    if (!message.codeBlockContent.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        CodeSnippetCard(
                            language = message.codeBlockLanguage ?: "코드",
                            code = message.codeBlockContent,
                            isCopied = copiedBlockId == message.id,
                            reducedMotion = reducedMotion,
                            onCopy = { onCopyCode(message.codeBlockContent) }
                        )
                    }

                    // Process Stage Label with Marquee Light Sweep Shimmer:
                    // 추론단계 -> "생각중", 추론 및 작업준비 확정 -> "작업준비중", 작업 마무리 -> "작업 마무리중", 다 끝나면 -> "작업완료"
                    if (!isUser) {
                        val isInProgress = isStreamingBubble || message.isStreaming
                        val stageLabel = when {
                            !isInProgress -> "작업완료"
                            streamPhaseLabel.contains("마무리") || streamPhaseLabel.contains("스트리밍") -> "작업 마무리중"
                            streamPhaseLabel.contains("작업준비") || streamPhaseLabel.contains("도구") || message.tools.isNotEmpty() -> "작업준비중"
                            else -> "생각중"
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        val detailInteraction = remember { MutableInteractionSource() }
                        Row(
                            modifier = Modifier
                                .defaultMinSize(minHeight = 32.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(
                                    interactionSource = detailInteraction,
                                    indication = null,
                                    role = Role.Button,
                                    onClick = onToggleDetail
                                )
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MarqueeLightSweepProcessText(
                                text = stageLabel,
                                isActive = isInProgress,
                                reducedMotion = reducedMotion
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = if (isDetailExpanded) AntigravityIcons.ChevronUp else AntigravityIcons.ChevronDown,
                                contentDescription = null,
                                tint = if (isInProgress) AntigravityPalette.WarmLampGold else AntigravityPalette.CalmSageAccent.copy(alpha = 0.78f),
                                modifier = Modifier.size(13.dp)
                            )
                        }

                        AnimatedVisibility(
                            visible = isDetailExpanded,
                            enter = if (reducedMotion) fadeIn(tween(0)) else expandVertically(tween(180)),
                            exit = if (reducedMotion) fadeOut(tween(0)) else shrinkVertically(tween(160))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(AntigravityPalette.NightGround)
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (message.thoughtText.isNotBlank()) {
                                    Text(
                                        text = message.thoughtText,
                                        style = AntigravityTypography.SecondaryBody.copy(fontSize = 12.5.sp)
                                    )
                                } else {
                                    Text(
                                        text = "요청 의도 추론 및 작업 처리 완료",
                                        style = AntigravityTypography.SecondaryBody.copy(fontSize = 12.5.sp)
                                    )
                                }
                                message.tools.forEach { tool ->
                                    Text(
                                        text = "• ${tool.title} (${tool.status.label})",
                                        style = AntigravityTypography.SecondaryBody.copy(
                                            color = AntigravityPalette.CalmSageAccent,
                                            fontSize = 12.sp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Marquee Light Sweep Text:
 * When [isActive] is true ("생각중", "작업준비중", "작업 마무리중"), renders a bright signboard-style light beam
 * sweeping continuously from left to right across the characters.
 * When finished ("작업완료"), renders a calm steady sage-ivory tone.
 */
@Composable
private fun MarqueeLightSweepProcessText(
    text: String,
    isActive: Boolean,
    reducedMotion: Boolean
) {
    if (!isActive || reducedMotion) {
        Text(
            text = text,
            style = AntigravityTypography.SecondaryBody.copy(
                color = if (isActive) AntigravityPalette.WarmLampGold else AntigravityPalette.CalmSageAccent.copy(alpha = 0.85f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        )
        return
    }

    val infiniteTransition = rememberInfiniteTransition(label = "marqueeLightSweep")
    val sweepProgress by infiniteTransition.animateFloat(
        initialValue = -0.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1150, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweepX"
    )

    val spanWidth = 220f
    val centerX = sweepProgress * spanWidth
    val beamHalfWidth = 58f

    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFF56636E),
            Color(0xFF68C5B2),
            Color(0xFFFFFDF8),
            Color(0xFFE8B96D),
            Color(0xFF56636E)
        ),
        start = Offset(centerX - beamHalfWidth, 0f),
        end = Offset(centerX + beamHalfWidth, 8f)
    )

    Text(
        text = text,
        style = AntigravityTypography.SecondaryBody.copy(
            brush = shimmerBrush,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    )
}

@Composable
private fun CodeSnippetCard(
    language: String,
    code: String,
    isCopied: Boolean,
    reducedMotion: Boolean,
    onCopy: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AntigravityPalette.NightCodeWell)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = language.uppercase(Locale.US),
                style = AntigravityTypography.TabularMetric.copy(
                    color = AntigravityPalette.CalmSageAccent
                )
            )

            Row(
                modifier = Modifier
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .tactilePressScale(interactionSource, reducedMotion)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        role = Role.Button,
                        onClick = onCopy
                    )
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isCopied) AntigravityIcons.CheckMark else AntigravityIcons.CopySheet,
                    contentDescription = "복사",
                    tint = if (isCopied) AntigravityPalette.CalmSageAccent else AntigravityPalette.MistSlateInk,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = if (isCopied) "복사됨" else "복사",
                    style = AntigravityTypography.PillLabel.copy(
                        fontSize = 12.sp,
                        color = if (isCopied) AntigravityPalette.CalmSageAccent else AntigravityPalette.MistSlateInk
                    )
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
        ) {
            Text(
                text = code,
                style = AntigravityTypography.CodeMono
            )
        }
    }
}

@Composable
private fun ActiveStreamBanner(
    phaseLabel: String,
    steeringStatusLabel: String,
    reducedMotion: Boolean,
    onStopStream: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val infiniteTransition = rememberInfiniteTransition(label = "streamLampPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    val effectiveAlpha = if (!reducedMotion) pulseAlpha else 1f

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(18.dp),
        color = AntigravityPalette.NightRaised
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(AntigravityPalette.WarmLampGold.copy(alpha = effectiveAlpha))
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = phaseLabel,
                        style = AntigravityTypography.PillLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (steeringStatusLabel.isNotBlank()) {
                        Text(
                            text = steeringStatusLabel,
                            style = AntigravityTypography.SecondaryBody.copy(
                                color = AntigravityPalette.WarmLampGold,
                                fontSize = 11.sp,
                                lineHeight = 14.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Text(
                            text = "아래 입력창에서 실시간 대화조정 · 작업조정 가능",
                            style = AntigravityTypography.SecondaryBody.copy(
                                color = AntigravityPalette.MutedSlateInk,
                                fontSize = 10.5.sp,
                                lineHeight = 14.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = 48.dp, minHeight = 40.dp)
                    .tactilePressScale(interactionSource, reducedMotion)
                    .clip(RoundedCornerShape(12.dp))
                    .background(AntigravityPalette.SoftCoralDim)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        role = Role.Button,
                        onClick = onStopStream
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "멈추기",
                    style = AntigravityTypography.PillLabel.copy(color = AntigravityPalette.SoftCoralDanger)
                )
            }
        }
    }
}

private fun compactModelReasoningTag(engine: AiEngineOption, reasoning: ReasoningEffort): String {
    val shortModel = when {
        engine.shortName.contains("Argon", ignoreCase = true) -> "Argon"
        engine.shortName.contains("Flash", ignoreCase = true) -> "Flash"
        engine.shortName.contains("Sonnet", ignoreCase = true) -> "Sonnet"
        engine.shortName.contains("Opus", ignoreCase = true) -> "Opus"
        engine.shortName.contains("120B", ignoreCase = true) -> "120B"
        engine.shortName.contains("Qwen", ignoreCase = true) -> "Qwen"
        engine.shortName.contains("R1", ignoreCase = true) -> "R1"
        engine.shortName.contains("Llama", ignoreCase = true) -> "Llama"
        else -> engine.shortName.take(5).trim()
    }
    val shortReason = reasoning.label.firstOrNull()?.uppercaseChar() ?: 'M'
    return "$shortModel·$shortReason"
}

@Composable
private fun SimplePromptInputDock(
    quickPrompts: List<String>,
    recommendedPrompt: String,
    promptText: String,
    isStreaming: Boolean,
    activeEngine: AiEngineOption,
    engines: List<AiEngineOption>,
    selectedReasoning: ReasoningEffort,
    showModelReasoningPopup: Boolean,
    showPlusMenuPopup: Boolean,
    reducedMotion: Boolean,
    onPromptTextChange: (String) -> Unit,
    onQuickPromptClick: (String) -> Unit,
    onSendClick: () -> Unit,
    onStopClick: () -> Unit,
    onTogglePlusPopup: () -> Unit,
    onPickPhoto: () -> Unit,
    onPickFile: () -> Unit,
    onPickVideo: () -> Unit,
    onPushWorkToGitHub: () -> Unit,
    onOpenSystemPromptEditor: () -> Unit,
    onOpenMoonlightStream: () -> Unit,
    onToggleModelPopup: () -> Unit,
    onSelectEngine: (AiEngineOption) -> Unit,
    onSelectReasoning: (ReasoningEffort) -> Unit
) {
    val inputInteractionSource = remember { MutableInteractionSource() }
    val isFocused by inputInteractionSource.collectIsFocusedAsState()
    val sendButtonInteraction = remember { MutableInteractionSource() }
    val modelPickerInteraction = remember { MutableInteractionSource() }
    val plusButtonInteraction = remember { MutableInteractionSource() }

    val activePrompts = if (isStreaming) {
        listOf("핵심만 짧게 요약해줘", "코드 예시도 함께 추가해줘", "작업 방향을 단계별로 조정해줘")
    } else {
        quickPrompts
    }

    val unifiedPopupContainerColor = Color(0xFF0C0C10).copy(alpha = 0.96f)
    val unifiedItemSurfaceColor = Color(0xFF15151C).copy(alpha = 0.52f)
    val unifiedSelectedItemColor = Color(0xFF1E3B36).copy(alpha = 0.62f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AntigravityPalette.NightGround)
            .padding(bottom = 8.dp)
    ) {
        // Floating [+] Attachment & GitHub Push Menu Popup (Unified soft translucent borderless UI)
        AnimatedVisibility(
            visible = showPlusMenuPopup,
            enter = if (reducedMotion) fadeIn(tween(0)) else expandVertically(tween(150)) + fadeIn(tween(150)),
            exit = if (reducedMotion) fadeOut(tween(0)) else shrinkVertically(tween(130)) + fadeOut(tween(130))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Surface(
                    modifier = Modifier.widthIn(max = 285.dp),
                    shape = RoundedCornerShape(22.dp),
                    color = unifiedPopupContainerColor
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PlusActionRow(
                            title = "사진",
                            subtitle = "사진 첨부 및 PC 세션 전송",
                            accentColor = AntigravityPalette.WarmIvoryInk,
                            onClick = onPickPhoto
                        )
                        PlusActionRow(
                            title = "파일",
                            subtitle = "문서 · 코드 파일 첨부",
                            accentColor = AntigravityPalette.WarmIvoryInk,
                            onClick = onPickFile
                        )
                        PlusActionRow(
                            title = "동영상",
                            subtitle = "동영상 첨부 및 PC 세션 전송",
                            accentColor = AntigravityPalette.WarmIvoryInk,
                            onClick = onPickVideo
                        )
                        PlusActionRow(
                            title = "PC 화면 터치 원격 제어 (Moonlight)",
                            subtitle = "60~120FPS 초저지연 스트리밍 · 터치 지점 즉시 PC 마우스 클릭",
                            accentColor = AntigravityPalette.CalmSageAccent,
                            onClick = onOpenMoonlightStream
                        )
                        PlusActionRow(
                            title = "작업내용 깃허브 올리기",
                            subtitle = "전체 소스코드 · 프로젝트 작업 GitHub 푸시",
                            accentColor = AntigravityPalette.CalmSageAccent,
                            onClick = onPushWorkToGitHub
                        )
                        PlusActionRow(
                            title = "시스템 프롬프트 편집기",
                            subtitle = "GEMINI.md · 디자인 스택 · 전역 규칙 실시간 편집",
                            accentColor = AntigravityPalette.WarmLampGold,
                            onClick = onOpenSystemPromptEditor
                        )
                    }
                }
            }
        }

        // Floating Model & Reasoning Picker Popup (Unified soft translucent borderless UI matching main screen!)
        AnimatedVisibility(
            visible = showModelReasoningPopup,
            enter = if (reducedMotion) fadeIn(tween(0)) else expandVertically(tween(160)) + fadeIn(tween(160)),
            exit = if (reducedMotion) fadeOut(tween(0)) else shrinkVertically(tween(140)) + fadeOut(tween(140))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Surface(
                    modifier = Modifier.widthIn(max = 300.dp),
                    shape = RoundedCornerShape(22.dp),
                    color = unifiedPopupContainerColor
                ) {
                    Column(
                        modifier = Modifier.padding(13.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "추론 강도",
                            style = AntigravityTypography.SecondaryBody.copy(
                                color = AntigravityPalette.WarmIvoryInk.copy(alpha = 0.45f),
                                fontSize = 11.sp
                            )
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ReasoningEffort.entries.forEach { effort ->
                                val selected = effort == selectedReasoning
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(minHeight = 36.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(if (selected) unifiedSelectedItemColor else unifiedItemSurfaceColor)
                                        .clickable(role = Role.RadioButton) {
                                            onSelectReasoning(effort)
                                        }
                                        .padding(vertical = 7.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = effort.label,
                                        style = AntigravityTypography.PillLabel.copy(
                                            color = if (selected) AntigravityPalette.CalmSageAccent else AntigravityPalette.WarmIvoryInk.copy(alpha = 0.78f),
                                            fontSize = 11.5.sp
                                        )
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "AI 모델 선택",
                            style = AntigravityTypography.SecondaryBody.copy(
                                color = AntigravityPalette.WarmIvoryInk.copy(alpha = 0.45f),
                                fontSize = 11.sp
                            )
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 210.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            engines.forEach { engine ->
                                val isSel = engine.id == activeEngine.id
                                val tint = if (engine.isLocal) AntigravityPalette.WarmLampGold else AntigravityPalette.CalmSageAccent
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = 38.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(if (isSel) unifiedSelectedItemColor else unifiedItemSurfaceColor)
                                        .clickable(role = Role.RadioButton) {
                                            onSelectEngine(engine)
                                            onToggleModelPopup()
                                        }
                                        .padding(horizontal = 11.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = engine.shortName,
                                            style = AntigravityTypography.PillLabel.copy(
                                                color = if (isSel) tint else AntigravityPalette.WarmIvoryInk.copy(alpha = 0.88f),
                                                fontSize = 12.sp
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = selectedReasoning.label,
                                            style = AntigravityTypography.SecondaryBody.copy(
                                                color = AntigravityPalette.WarmIvoryInk.copy(alpha = 0.42f),
                                                fontSize = 10.5.sp
                                            )
                                        )
                                    }
                                    if (isSel) {
                                        Icon(
                                            imageVector = AntigravityIcons.CheckMark,
                                            contentDescription = null,
                                            tint = tint,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    } else {
                                        Text(
                                            text = engine.providerTag,
                                            style = AntigravityTypography.SecondaryBody.copy(
                                                color = AntigravityPalette.WarmIvoryInk.copy(alpha = 0.42f),
                                                fontSize = 10.5.sp
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Gentle suggestion or live mid-stream steering chips
        AnimatedVisibility(visible = promptText.isEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(activePrompts) { chipText ->
                    val chipInteraction = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .widthIn(max = 260.dp)
                            .defaultMinSize(minHeight = 38.dp)
                            .tactilePressScale(chipInteraction, reducedMotion)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF14141A).copy(alpha = 0.50f))
                            .clickable(
                                interactionSource = chipInteraction,
                                indication = null,
                                role = Role.Button,
                                onClick = { onQuickPromptClick(chipText) }
                            )
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = if (isStreaming) "조정 · $chipText" else chipText,
                            style = AntigravityTypography.SecondaryBody.copy(
                                color = if (isStreaming) AntigravityPalette.WarmLampGold else AntigravityPalette.WarmIvoryInk.copy(alpha = 0.75f),
                                fontSize = 11.5.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Extended Full-Width Input Box (padding horizontal = 8.dp):
        // [+] | Wide Recommended Prompt / Input | Ultra-Compact Model·Reasoning (8.5.sp) | Send(↑) <-> Stop(■) Toggle Button
        val hasTypedText = promptText.isNotBlank()
        val isStopButtonMode = isStreaming && !hasTypedText
        val buttonBg = when {
            isStopButtonMode -> AntigravityPalette.SoftCoralDanger.copy(alpha = 0.85f)
            isStreaming && hasTypedText -> AntigravityPalette.WarmLampGold.copy(alpha = 0.85f)
            hasTypedText -> AntigravityPalette.CalmSageAccent
            else -> AntigravityPalette.CalmSageAccent.copy(alpha = 0.45f)
        }
        val iconTint = AntigravityPalette.DeepForestInk

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .defaultMinSize(minHeight = 44.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(
                    if (isFocused) {
                        Color(0xFF181820).copy(alpha = 0.62f)
                    } else {
                        Color(0xFF121218).copy(alpha = 0.48f)
                    }
                )
                .padding(start = 8.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Backgroundless, Borderless [+] Button inside the left edge of the chat input box
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .tactilePressScale(plusButtonInteraction, reducedMotion)
                    .clickable(
                        interactionSource = plusButtonInteraction,
                        indication = null,
                        role = Role.Button,
                        onClick = onTogglePlusPopup
                    )
                    .semantics {
                        contentDescription = "첨부 및 작업 메뉴 열기"
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = AntigravityIcons.PlusStroke,
                    contentDescription = null,
                    tint = if (showPlusMenuPopup) {
                        AntigravityPalette.CalmSageAccent
                    } else {
                        AntigravityPalette.WarmIvoryInk.copy(alpha = 0.62f)
                    },
                    modifier = Modifier.size(14.dp)
                )
            }

            Spacer(modifier = Modifier.width(3.dp))

            // Maximized-Length Input Text Field + Semi-Transparent AI-Work-Based Recommended Task/Test Prompt Placeholder
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (promptText.isEmpty()) {
                    Text(
                        text = recommendedPrompt,
                        style = AntigravityTypography.SecondaryBody.copy(
                            color = if (isStreaming) {
                                AntigravityPalette.WarmLampGold.copy(alpha = 0.45f)
                            } else {
                                AntigravityPalette.WarmIvoryInk.copy(alpha = 0.35f)
                            },
                            fontSize = 12.5.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = promptText,
                    onValueChange = onPromptTextChange,
                    interactionSource = inputInteractionSource,
                    textStyle = AntigravityTypography.ChatBody.copy(fontSize = 14.sp),
                    cursorBrush = SolidColor(AntigravityPalette.CalmSageAccent),
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSendClick() }),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.width(3.dp))

            // Ultra-small semi-transparent inline Model & Reasoning tag ("Argon·M ⌄" at 8.5.sp) so the chat input box is much wider
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .tactilePressScale(modelPickerInteraction, reducedMotion)
                    .clickable(
                        interactionSource = modelPickerInteraction,
                        indication = null,
                        role = Role.Button,
                        onClick = onToggleModelPopup
                    )
                    .semantics {
                        contentDescription = "모델 및 추론 선택"
                    }
                    .padding(horizontal = 3.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = compactModelReasoningTag(activeEngine, selectedReasoning),
                    style = AntigravityTypography.PillLabel.copy(
                        color = AntigravityPalette.WarmIvoryInk.copy(alpha = 0.40f),
                        fontSize = 8.5.sp
                    ),
                    maxLines = 1
                )
                Spacer(modifier = Modifier.width(1.dp))
                Icon(
                    imageVector = if (showModelReasoningPopup) AntigravityIcons.ChevronUp else AntigravityIcons.ChevronDown,
                    contentDescription = null,
                    tint = AntigravityPalette.WarmIvoryInk.copy(alpha = 0.40f),
                    modifier = Modifier.size(8.dp)
                )
            }

            Spacer(modifier = Modifier.width(3.dp))

            // Compact Send Arrow (↑) that transforms into Stop Button (■) while streaming ("보내기누르면 버튼 멈추기 버튼으로 변하개 수정해")
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .tactilePressScale(sendButtonInteraction, reducedMotion)
                    .clip(CircleShape)
                    .background(buttonBg)
                    .clickable(
                        interactionSource = sendButtonInteraction,
                        indication = null,
                        role = Role.Button,
                        onClick = {
                            if (isStopButtonMode) onStopClick() else onSendClick()
                        }
                    )
                    .semantics {
                        contentDescription = if (isStopButtonMode) "스트리밍 중지" else "메시지 전송"
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isStopButtonMode) AntigravityIcons.StopSquare else AntigravityIcons.SendArrow,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(10.dp)
                )
            }
        }
    }
}

@Composable
private fun PlusActionRow(
    title: String,
    subtitle: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF15151C).copy(alpha = 0.52f))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = AntigravityTypography.PillLabel.copy(
                    color = accentColor,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            )
            Text(
                text = subtitle,
                style = AntigravityTypography.SecondaryBody.copy(
                    color = AntigravityPalette.WarmIvoryInk.copy(alpha = 0.45f),
                    fontSize = 10.5.sp
                )
            )
        }
        Icon(
            imageVector = AntigravityIcons.ChevronRight,
            contentDescription = null,
            tint = accentColor.copy(alpha = 0.58f),
            modifier = Modifier.size(14.dp)
        )
    }
}

/**
 * Animated Session Status Icon:
 * - Running (진행중): Smooth 360-degree rotating open-arc spinner icon (matching PC Antigravity Conversations list)
 * - Paused/Stopped (멈춤): Crisp minimal pause bars icon with smooth scale/alpha transition
 */
@Composable
private fun SessionStatusAnimatedIcon(
    isRunning: Boolean,
    isSelected: Boolean,
    reducedMotion: Boolean,
    onToggleState: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "sessionStatusSpinner")
    val rotationDeg by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1050, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spinnerRotation"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "spinnerPulse"
    )

    val activeTint = if (isSelected) {
        AntigravityPalette.CalmSageAccent
    } else if (isRunning) {
        AntigravityPalette.WarmIvoryInk.copy(alpha = if (reducedMotion) 0.75f else pulseAlpha)
    } else {
        AntigravityPalette.WarmLampGold.copy(alpha = 0.72f)
    }

    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onToggleState
            )
            .semantics {
                contentDescription = if (isRunning) "세션 진행중 아이콘" else "세션 멈춤 아이콘"
            },
        contentAlignment = Alignment.Center
    ) {
        if (isRunning) {
            Canvas(
                modifier = Modifier
                    .size(14.dp)
                    .graphicsLayer {
                        rotationZ = if (reducedMotion) 0f else rotationDeg
                    }
            ) {
                // Subtle background track circle
                drawCircle(
                    color = activeTint.copy(alpha = 0.18f),
                    style = Stroke(width = 1.7.dp.toPx())
                )
                // Spinning 260-degree arc (matches PC Antigravity sidebar spinner)
                drawArc(
                    color = activeTint,
                    startAngle = -90f,
                    sweepAngle = 255f,
                    useCenter = false,
                    style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        } else {
            // Paused / Stopped icon (subtle ring + twin pause bars)
            Canvas(modifier = Modifier.size(14.dp)) {
                drawCircle(
                    color = activeTint.copy(alpha = 0.22f),
                    style = Stroke(width = 1.5.dp.toPx())
                )
                val barW = 1.7.dp.toPx()
                val topY = size.height * 0.32f
                val botY = size.height * 0.68f
                val leftX = size.width * 0.39f
                val rightX = size.width * 0.61f
                drawLine(
                    color = activeTint,
                    start = Offset(leftX, topY),
                    end = Offset(leftX, botY),
                    strokeWidth = barW,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = activeTint,
                    start = Offset(rightX, topY),
                    end = Offset(rightX, botY),
                    strokeWidth = barW,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

/**
 * Top-Left Session & Project Folder Popover:
 * Expands directly from the top-left icon (TransformOrigin(0.04f, 0.0f)) and shows:
 * - Current Session Name (세션명) & Active Project Folder Name (프로젝트 폴더명)
 * - Live PC Sessions list (with animated 진행중/멈춤 status icon) + Icon-only [+] button to create session (no search/text input box!)
 * - PC Project Folders list + Icon-only [+] button to create folder (no search/text input box!)
 */
@Composable
private fun TopLeftSessionAndProjectPopover(
    sessions: List<WorkspaceSession>,
    selectedSessionIndex: Int,
    pcProjects: List<PcProjectFolder>,
    activePcProjectName: String,
    currentViewMode: InspectorViewMode,
    connectionState: BridgeConnectionState,
    host: String,
    port: String,
    onSelectSession: (Int) -> Unit,
    onSelectProjectFolder: (String) -> Unit,
    onSessionAction: (String, String) -> Unit,
    onProjectAction: (String, String) -> Unit,
    onCreateRemoteSession: (String, String) -> Unit,
    onCreateRemoteProject: (String) -> Unit,
    onSyncNow: () -> Unit,
    onDismiss: () -> Unit
) {
    val reducedMotion = rememberReducedMotion()
    val pausedSessionIds = remember { mutableStateMapOf<String, Boolean>() }
    val pinnedSessionIds = remember { mutableStateMapOf<String, Boolean>() }
    val pinnedProjectNames = remember { mutableStateMapOf<String, Boolean>() }
    val swipedOpenRows = remember { mutableStateMapOf<String, Boolean>() }

    val popoverBg = Color(0xFF000000)
    val cardBg = Color(0xFF0E0E13)
    val selectedCardBg = Color(0xFF172D29)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            ),
        shape = RoundedCornerShape(22.dp),
        color = popoverBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 480.dp, max = 700.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top Icon-Only Action Row: Create New Session [+] Icon (No text labels or file paths!)
            val addSessionInteraction = remember { MutableInteractionSource() }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .tactilePressScale(addSessionInteraction, reducedMotion)
                        .clip(CircleShape)
                        .background(cardBg)
                        .clickable(
                            interactionSource = addSessionInteraction,
                            indication = null,
                            role = Role.Button,
                            onClick = {
                                onCreateRemoteSession("새 안그래비티 세션 ${sessions.size + 1}", activePcProjectName)
                            }
                        )
                        .semantics {
                            contentDescription = "+ 새 세션 생성"
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = AntigravityIcons.PlusStroke,
                        contentDescription = null,
                        tint = AntigravityPalette.CalmSageAccent,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            // Expanded Session List Layout (Swipe left to reveal Pin / Archive / PC Delete icons on the right!)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 250.dp, max = 380.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                sessions.forEachIndexed { idx, session ->
                    val selected = idx == selectedSessionIndex && currentViewMode == InspectorViewMode.DEMO_NORMAL
                    val folderLabel = session.branchName
                        .takeIf { it.isNotBlank() && it != "session" && it != "live-sync" }
                        ?: activePcProjectName
                    val defaultRunning = session.durationTag != "paused" && session.durationTag != "멈춤"
                    val isPausedOverride = pausedSessionIds[session.id]
                    val isRunning = isPausedOverride?.not() ?: defaultRunning
                    val isPinned = pinnedSessionIds[session.id] == true
                    val rowKey = "session:${session.id}"

                    SwipeRevealActionRow(
                        rowKey = rowKey,
                        isRevealed = swipedOpenRows[rowKey] == true,
                        isPinned = isPinned,
                        reducedMotion = reducedMotion,
                        onRevealChange = { revealed ->
                            swipedOpenRows[rowKey] = revealed
                        },
                        onPinClick = {
                            val nextPinned = !isPinned
                            pinnedSessionIds[session.id] = nextPinned
                            onSessionAction(session.id, "pin")
                            swipedOpenRows[rowKey] = false
                        },
                        onArchiveClick = {
                            swipedOpenRows[rowKey] = false
                            onSessionAction(session.id, "archive")
                        },
                        onDeleteClick = {
                            swipedOpenRows[rowKey] = false
                            onSessionAction(session.id, "delete")
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 56.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (selected) selectedCardBg else cardBg)
                                .clickable(role = Role.Button) {
                                    if (swipedOpenRows[rowKey] == true) {
                                        swipedOpenRows[rowKey] = false
                                    } else {
                                        onSelectSession(idx)
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isPinned) {
                                        Icon(
                                            imageVector = AntigravityIcons.PinTack,
                                            contentDescription = null,
                                            tint = AntigravityPalette.CalmSageAccent,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                    }
                                    Text(
                                        text = session.title,
                                        style = AntigravityTypography.PillLabel.copy(
                                            color = if (selected) AntigravityPalette.CalmSageAccent else AntigravityPalette.WarmIvoryInk.copy(alpha = 0.92f),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            // Animated Running (진행중 spinner) / Paused (멈춤 bars) icon
                            SessionStatusAnimatedIcon(
                                isRunning = isRunning,
                                isSelected = selected,
                                reducedMotion = reducedMotion,
                                onToggleState = {
                                    pausedSessionIds[session.id] = isRunning
                                }
                            )
                        }
                    }
                }
            }

            // Icon-Only [+] Create Project Folder Button Row (No labels, no file paths!)
            val addFolderInteraction = remember { MutableInteractionSource() }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .tactilePressScale(addFolderInteraction, reducedMotion)
                        .clip(CircleShape)
                        .background(cardBg)
                        .clickable(
                            interactionSource = addFolderInteraction,
                            indication = null,
                            role = Role.Button,
                            onClick = {
                                val nextFolder = "project-${(pcProjects.size + 1).toString().padStart(2, '0')}"
                                onCreateRemoteProject(nextFolder)
                            }
                        )
                        .semantics {
                            contentDescription = "+ 폴더 생성"
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = AntigravityIcons.PlusStroke,
                        contentDescription = null,
                        tint = AntigravityPalette.WarmLampGold,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            // Project Names List (Swipe left on any Project Name to reveal Pin / Archive / PC Delete icons on the right!)
            if (pcProjects.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 210.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    pcProjects.forEach { proj ->
                        val isActiveProj = proj.name == activePcProjectName
                        val isPinnedProj = pinnedProjectNames[proj.name] == true
                        val projKey = "project:${proj.name}"

                        SwipeRevealActionRow(
                            rowKey = projKey,
                            isRevealed = swipedOpenRows[projKey] == true,
                            isPinned = isPinnedProj,
                            reducedMotion = reducedMotion,
                            onRevealChange = { revealed ->
                                swipedOpenRows[projKey] = revealed
                            },
                            onPinClick = {
                                val nextPinned = !isPinnedProj
                                pinnedProjectNames[proj.name] = nextPinned
                                onProjectAction(proj.name, "pin")
                                swipedOpenRows[projKey] = false
                            },
                            onArchiveClick = {
                                swipedOpenRows[projKey] = false
                                onProjectAction(proj.name, "archive")
                            },
                            onDeleteClick = {
                                swipedOpenRows[projKey] = false
                                onProjectAction(proj.name, "delete")
                            }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 44.dp)
                                    .clip(RoundedCornerShape(13.dp))
                                    .background(if (isActiveProj) selectedCardBg else cardBg)
                                    .clickable(role = Role.Button) {
                                        if (swipedOpenRows[projKey] == true) {
                                            swipedOpenRows[projKey] = false
                                        } else {
                                            onSelectProjectFolder(proj.name)
                                        }
                                    }
                                    .padding(horizontal = 14.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isPinnedProj) {
                                    Icon(
                                        imageVector = AntigravityIcons.PinTack,
                                        contentDescription = null,
                                        tint = AntigravityPalette.WarmLampGold,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text(
                                    text = proj.name,
                                    style = AntigravityTypography.SecondaryBody.copy(
                                        color = if (isActiveProj) AntigravityPalette.WarmLampGold else AntigravityPalette.WarmIvoryInk.copy(alpha = 0.86f),
                                        fontSize = 12.5.sp,
                                        fontWeight = if (isActiveProj) FontWeight.SemiBold else FontWeight.Normal
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Swipe-left action row wrapper for both Session items (세션명) and Project items (프로젝트명).
 * Swiping left smoothly slides the row left and expands the 3 action icons on the right:
 * 1. 고정핀 (PinTack)
 * 2. 보관핀 (ArchiveBox)
 * 3. PC에서 아예 삭제 (TrashDelete)
 */
@Composable
private fun SwipeRevealActionRow(
    rowKey: String,
    isRevealed: Boolean,
    isPinned: Boolean,
    reducedMotion: Boolean,
    onRevealChange: (Boolean) -> Unit,
    onPinClick: () -> Unit,
    onArchiveClick: () -> Unit,
    onDeleteClick: () -> Unit,
    content: @Composable () -> Unit
) {
    var dragAccumulated by remember(rowKey) { mutableStateOf(0f) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(rowKey, isRevealed) {
                detectHorizontalDragGestures(
                    onDragStart = { dragAccumulated = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        dragAccumulated += dragAmount
                        if (dragAccumulated < -12f && !isRevealed) {
                            onRevealChange(true)
                        } else if (dragAccumulated > 12f && isRevealed) {
                            onRevealChange(false)
                        }
                    }
                )
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left / Main sliding card content (shrinks/slides left cleanly when action icons reveal on the right)
        Box(
            modifier = Modifier.weight(1f)
        ) {
            content()
        }

        // Right-side Revealed Action Icons: [고정핀] [보관핀] [PC에서 아예 삭제] (Only visible when swiped left!)
        AnimatedVisibility(
            visible = isRevealed,
            enter = if (reducedMotion) {
                fadeIn(tween(0))
            } else {
                expandIn(
                    expandFrom = Alignment.CenterEnd,
                    animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
                ) + fadeIn(tween(160))
            },
            exit = if (reducedMotion) {
                fadeOut(tween(0))
            } else {
                shrinkOut(
                    shrinkTowards = Alignment.CenterEnd,
                    animationSpec = tween(durationMillis = 170, easing = FastOutSlowInEasing)
                ) + fadeOut(tween(130))
            }
        ) {
            Row(
                modifier = Modifier.padding(start = 8.dp, end = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. 고정핀 (Pin)
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(
                            if (isPinned) AntigravityPalette.CalmSageAccent.copy(alpha = 0.32f)
                            else Color(0xFF1A2C29)
                        )
                        .clickable(role = Role.Button, onClick = onPinClick)
                        .semantics {
                            contentDescription = "고정핀"
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = AntigravityIcons.PinTack,
                        contentDescription = null,
                        tint = AntigravityPalette.CalmSageAccent,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // 2. 보관핀 (Archive)
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2B2418))
                        .clickable(role = Role.Button, onClick = onArchiveClick)
                        .semantics {
                            contentDescription = "보관핀"
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = AntigravityIcons.ArchiveBox,
                        contentDescription = null,
                        tint = AntigravityPalette.WarmLampGold,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // 3. PC에서 아예 삭제 (Delete from PC)
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF30191C))
                        .clickable(role = Role.Button, onClick = onDeleteClick)
                        .semantics {
                            contentDescription = "PC에서 아예 삭제"
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = AntigravityIcons.TrashDelete,
                        contentDescription = null,
                        tint = Color(0xFFE57373),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Unified Settings Dialog (Session & Project creation moved to Top-Left Icon Popover!):
 * Houses AI Model Selection, Quick PC Remote Control, and Collapsible Advanced Settings.
 */
@Composable
private fun UnifiedSettingsDialog(
    engines: List<AiEngineOption>,
    selectedEngineId: String,
    currentViewMode: InspectorViewMode,
    initialHost: String,
    initialPort: String,
    initialLocalModel: String,
    connectionState: BridgeConnectionState,
    autoSyncEnabled: Boolean,
    autoSyncRetryCount: Int,
    onToggleAutoSync: (Boolean) -> Unit,
    onSelectEngine: (AiEngineOption) -> Unit,
    onExecuteRemoteCommand: (String) -> Unit,
    onSyncNow: () -> Unit,
    onClearCurrentChat: () -> Unit,
    onOpenSystemPromptEditor: () -> Unit,
    onOpenMoonlightStream: () -> Unit,
    onSelectViewMode: (InspectorViewMode) -> Unit,
    onDismiss: () -> Unit,
    onSaveSettings: (String, String, String) -> Unit
) {
    var hostInput by rememberSaveable { mutableStateOf(initialHost) }
    var portInput by rememberSaveable { mutableStateOf(initialPort) }
    var localModelInput by rememberSaveable { mutableStateOf(initialLocalModel) }
    var showAdvanced by rememberSaveable { mutableStateOf(false) }

    val unifiedDialogBg = Color(0xFF000000)
    val unifiedCardBg = Color(0xFF0E0E13)
    val unifiedSelectedCardBg = Color(0xFF172D29)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = unifiedDialogBg,
        titleContentColor = AntigravityPalette.WarmIvoryInk,
        textContentColor = AntigravityPalette.MistSlateInk,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = AntigravityIcons.SettingsSliders,
                    contentDescription = null,
                    tint = AntigravityPalette.CalmSageAccent,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "설정",
                    style = AntigravityTypography.CardTitle.copy(fontSize = 15.sp)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Live PC Connection Status & Auto-Resync Control Card
                val isConnectedPc = connectionState == BridgeConnectionState.CONNECTED_PC
                val isAutoResyncing = connectionState == BridgeConnectionState.AUTO_RESYNCING
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isConnectedPc) unifiedSelectedCardBg else unifiedCardBg)
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isConnectedPc -> AntigravityPalette.CalmSageAccent
                                            isAutoResyncing -> AntigravityPalette.WarmLampGold
                                            else -> AntigravityPalette.WarmIvoryInk.copy(alpha = 0.45f)
                                        }
                                    )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when {
                                    isConnectedPc -> "PC 실시간 연결됨 ($initialHost:$initialPort)"
                                    isAutoResyncing -> "PC 끊김 · 자동 동기화 시도 중 (${autoSyncRetryCount.coerceAtLeast(1)}회)"
                                    else -> "PC 연결 대기 ($initialHost:$initialPort)"
                                },
                                style = AntigravityTypography.PillLabel.copy(
                                    color = when {
                                        isConnectedPc -> AntigravityPalette.CalmSageAccent
                                        isAutoResyncing -> AntigravityPalette.WarmLampGold
                                        else -> AntigravityPalette.WarmIvoryInk.copy(alpha = 0.70f)
                                    },
                                    fontSize = 11.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF0C0C10).copy(alpha = 0.55f))
                                .clickable(role = Role.Button, onClick = onSyncNow)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "동기화",
                                style = AntigravityTypography.SecondaryBody.copy(
                                    color = AntigravityPalette.CalmSageAccent,
                                    fontSize = 10.5.sp
                                ),
                                maxLines = 1
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(role = Role.Switch) { onToggleAutoSync(!autoSyncEnabled) }
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "PC 동기화 끊김 시 자동 재동기화",
                            style = AntigravityTypography.SecondaryBody.copy(
                                color = AntigravityPalette.WarmIvoryInk.copy(alpha = 0.68f),
                                fontSize = 10.5.sp
                            )
                        )
                        Text(
                            text = if (autoSyncEnabled) "자동 켜짐" else "꺼짐",
                            style = AntigravityTypography.PillLabel.copy(
                                color = if (autoSyncEnabled) AntigravityPalette.CalmSageAccent else AntigravityPalette.WarmIvoryInk.copy(alpha = 0.42f),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }

                // Section 1: AI Model Selection (Default vs Local AI)
                Text(
                    text = "1. 대화할 AI 선택",
                    style = AntigravityTypography.PillLabel.copy(
                        color = AntigravityPalette.WarmIvoryInk.copy(alpha = 0.55f),
                        fontSize = 11.sp
                    )
                )

                engines.forEach { engine ->
                    val isSelected = engine.id == selectedEngineId
                    val accent = if (engine.isLocal) AntigravityPalette.WarmLampGold else AntigravityPalette.CalmSageAccent
                    val cardBg = if (isSelected) unifiedSelectedCardBg else unifiedCardBg

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(cardBg)
                            .clickable(role = Role.RadioButton) {
                                onSelectEngine(engine)
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = engine.label,
                                style = AntigravityTypography.PillLabel.copy(
                                    color = if (isSelected) accent else AntigravityPalette.WarmIvoryInk.copy(alpha = 0.88f),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.5.sp
                                )
                            )
                            Text(
                                text = if (isSelected) "사용 중" else engine.providerTag,
                                style = AntigravityTypography.SecondaryBody.copy(
                                    color = if (isSelected) accent else AntigravityPalette.WarmIvoryInk.copy(alpha = 0.42f),
                                    fontSize = 10.sp
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = engine.description,
                            style = AntigravityTypography.SecondaryBody.copy(
                                color = AntigravityPalette.WarmIvoryInk.copy(alpha = 0.48f),
                                fontSize = 10.5.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Section 2: Quick PC Remote Control Actions
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "2. PC 리모트 컨트롤",
                    style = AntigravityTypography.PillLabel.copy(
                        color = AntigravityPalette.WarmIvoryInk.copy(alpha = 0.55f),
                        fontSize = 11.5.sp
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "pc_status" to "PC 상태 점검",
                        "list_projects" to "폴더 목록 조회",
                        "git_status" to "Git 상태 확인"
                    ).forEach { (cmdKey, cmdLabel) ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(unifiedCardBg)
                                .clickable(role = Role.Button) {
                                    onExecuteRemoteCommand(cmdKey)
                                }
                                .padding(horizontal = 6.dp, vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = cmdLabel,
                                style = AntigravityTypography.SecondaryBody.copy(
                                    color = AntigravityPalette.WarmIvoryInk.copy(alpha = 0.82f),
                                    fontSize = 11.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(unifiedSelectedCardBg)
                        .clickable(role = Role.Button, onClick = onOpenMoonlightStream)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = AntigravityIcons.DesktopStreamCursor,
                            contentDescription = null,
                            tint = AntigravityPalette.CalmSageAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(7.dp))
                        Text(
                            text = "PC 화면 터치 원격 제어 (Sunshine · Moonlight 60~120FPS)",
                            style = AntigravityTypography.PillLabel.copy(
                                color = AntigravityPalette.CalmSageAccent,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(unifiedSelectedCardBg)
                        .clickable(role = Role.Button, onClick = onOpenSystemPromptEditor)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "시스템 프롬프트 편집기 열기 (GEMINI.md · 하네스)",
                        style = AntigravityTypography.PillLabel.copy(
                            color = AntigravityPalette.CalmSageAccent,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(unifiedCardBg)
                        .clickable(role = Role.Button, onClick = onClearCurrentChat)
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "현재 대화 내용 비우기 (새 대화 시작)",
                        style = AntigravityTypography.SecondaryBody.copy(
                            color = AntigravityPalette.WarmIvoryInk.copy(alpha = 0.78f),
                            fontSize = 11.5.sp
                        )
                    )
                }

                // Section 3: Collapsible Advanced Settings (PC IP/Port + Screen State Tests)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(role = Role.Button) { showAdvanced = !showAdvanced }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "고급 설정 (PC 연결 주소 · 화면 테스트)",
                        style = AntigravityTypography.SecondaryBody.copy(
                            color = AntigravityPalette.MutedSlateInk,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    Icon(
                        imageVector = if (showAdvanced) AntigravityIcons.ChevronUp else AntigravityIcons.ChevronDown,
                        contentDescription = null,
                        tint = AntigravityPalette.MutedSlateInk,
                        modifier = Modifier.size(16.dp)
                    )
                }

                AnimatedVisibility(visible = showAdvanced) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "PC 연결 주소 (휴대폰에서는 PC의 LAN IP)",
                            style = AntigravityTypography.SecondaryBody.copy(fontSize = 12.sp)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(AntigravityPalette.NightGround)
                                .padding(horizontal = 12.dp, vertical = 12.dp)
                        ) {
                            BasicTextField(
                                value = hostInput,
                                onValueChange = { hostInput = it },
                                textStyle = AntigravityTypography.CodeMono,
                                cursorBrush = SolidColor(AntigravityPalette.CalmSageAccent),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Text(
                            text = "포트 번호 (기본 8766)",
                            style = AntigravityTypography.SecondaryBody.copy(fontSize = 12.sp)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(AntigravityPalette.NightGround)
                                .padding(horizontal = 12.dp, vertical = 12.dp)
                        ) {
                            BasicTextField(
                                value = portInput,
                                onValueChange = { portInput = it },
                                textStyle = AntigravityTypography.CodeMono,
                                cursorBrush = SolidColor(AntigravityPalette.CalmSageAccent),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Text(
                            text = "로컬 AI 모델명 직접 지정 (비우면 선택한 모델 사용)",
                            style = AntigravityTypography.SecondaryBody.copy(fontSize = 12.sp)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(AntigravityPalette.NightGround)
                                .padding(horizontal = 12.dp, vertical = 12.dp)
                        ) {
                            BasicTextField(
                                value = localModelInput,
                                onValueChange = { localModelInput = it },
                                textStyle = AntigravityTypography.CodeMono,
                                cursorBrush = SolidColor(AntigravityPalette.WarmLampGold),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "화면 상태 미리보기 (개발자 점검용)",
                            style = AntigravityTypography.SecondaryBody.copy(fontSize = 12.sp)
                        )

                        InspectorViewMode.entries.forEach { mode ->
                            val isCurrent = mode == currentViewMode
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 44.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isCurrent) AntigravityPalette.CalmSageDim else AntigravityPalette.NightGround)
                                    .clickable(role = Role.Button) {
                                        onSelectViewMode(mode)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = mode.label,
                                    style = AntigravityTypography.SecondaryBody.copy(
                                        color = if (isCurrent) AntigravityPalette.CalmSageAccent else AntigravityPalette.WarmIvoryInk
                                    )
                                )
                            }
                        }

                        Text(
                            text = "연결 상태: ${connectionState.badgeLabel}",
                            style = AntigravityTypography.TabularMetric
                        )
                    }
                }
            }
        },
        confirmButton = {
            Box(
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(AntigravityPalette.CalmSageAccent)
                    .clickable(role = Role.Button) {
                        onSaveSettings(hostInput, portInput, localModelInput)
                    }
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "완료",
                    style = AntigravityTypography.PillLabel.copy(
                        color = AntigravityPalette.DeepForestInk,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
        },
        dismissButton = {
            Box(
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(role = Role.Button, onClick = onDismiss)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "닫기",
                    style = AntigravityTypography.PillLabel.copy(color = AntigravityPalette.MistSlateInk)
                )
            }
        }
    )
}

@Composable
private fun LoadingStateCardList(reducedMotion: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "skeletonPulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(950, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeletonAlpha"
    )
    val effectiveAlpha = if (reducedMotion) 0.55f else alpha

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "대화 내용을 불러오고 있습니다...",
            style = AntigravityTypography.SectionHeader.copy(color = AntigravityPalette.CalmSageAccent)
        )
        repeat(2) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = AntigravityPalette.NightSurface.copy(alpha = effectiveAlpha)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Box(
                        modifier = Modifier
                            .width(120.dp)
                            .height(14.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(AntigravityPalette.NightRaised)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(16.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(AntigravityPalette.NightRaised)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(16.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(AntigravityPalette.NightRaised)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyNightStateCard(
    reducedMotion: Boolean,
    onSelectStarterPrompt: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = AntigravityPalette.NightSurface
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(AntigravityPalette.CalmSageDim),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = AntigravityIcons.NightCrescent,
                        contentDescription = null,
                        tint = AntigravityPalette.CalmSageAccent,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "무엇을 도와드릴까요?",
                    style = AntigravityTypography.CardTitle
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "아래 예시를 눌러 바로 대화를 시작하거나, 입력창에 궁금한 점을 편하게 적어보세요.",
                    style = AntigravityTypography.SecondaryBody
                )
                Spacer(modifier = Modifier.height(16.dp))

                val starters = listOf(
                    "오늘 해야 할 일을 깔끔하게 정리해줘",
                    "하루를 마무리하는 짧은 요약을 도와줘"
                )
                starters.forEach { starter ->
                    val interaction = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .defaultMinSize(minHeight = 48.dp)
                            .tactilePressScale(interaction, reducedMotion)
                            .clip(RoundedCornerShape(14.dp))
                            .background(AntigravityPalette.NightRaised)
                            .clickable(
                                interactionSource = interaction,
                                indication = null,
                                role = Role.Button,
                                onClick = { onSelectStarterPrompt(starter) }
                            )
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = starter,
                            style = AntigravityTypography.PillLabel.copy(color = AntigravityPalette.CalmSageAccent)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorRecoveryStateCard(
    reducedMotion: Boolean,
    onRetryBridge: () -> Unit,
    onContinueSimulation: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = AntigravityPalette.NightSurface
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = AntigravityIcons.WarningTriangle,
                        contentDescription = null,
                        tint = AntigravityPalette.SoftCoralDanger,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "내 PC와 연결되지 않았습니다",
                        style = AntigravityTypography.CardTitle.copy(color = AntigravityPalette.SoftCoralDanger)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "PC가 꺼져 있거나 연결되지 않아도 걱정하지 마세요. '모바일 단독으로 계속하기'를 누르면 스마트폰에서 바로 대화를 이어갈 수 있습니다.",
                    style = AntigravityTypography.SecondaryBody
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val retryInteraction = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp)
                            .tactilePressScale(retryInteraction, reducedMotion)
                            .clip(RoundedCornerShape(14.dp))
                            .background(AntigravityPalette.NightRaised)
                            .clickable(
                                interactionSource = retryInteraction,
                                indication = null,
                                role = Role.Button,
                                onClick = onRetryBridge
                            )
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "다시 연결",
                            style = AntigravityTypography.PillLabel
                        )
                    }

                    val simInteraction = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp)
                            .tactilePressScale(simInteraction, reducedMotion)
                            .clip(RoundedCornerShape(14.dp))
                            .background(AntigravityPalette.CalmSageAccent)
                            .clickable(
                                interactionSource = simInteraction,
                                indication = null,
                                role = Role.Button,
                                onClick = onContinueSimulation
                            )
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "모바일 단독으로 계속",
                            style = AntigravityTypography.PillLabel.copy(
                                color = AntigravityPalette.DeepForestInk,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }
            }
        }
    }
}

private data class AiClarificationRequest(
    val id: String,
    val agentMessageId: String,
    val originalPrompt: String,
    val questionTitle: String,
    val options: List<String>
)

private fun buildClarificationQuestionForPrompt(
    prompt: String,
    agentMessageId: String
): AiClarificationRequest? {
    val trimmed = prompt.trim()
    if (trimmed.isEmpty()) return null
    val lower = trimmed.lowercase()

    val (question, options) = when {
        lower.contains("테스트") || lower.contains("점검") || lower.contains("실측") -> {
            "어떤 범위로 동작 및 테스트를 진행할까요?" to listOf(
                "전체 빌드 + 실기기 UI 동작 검증 (권장)",
                "변경된 핵심 화면만 빠르게 검증",
                "단위 테스트 및 코드 정적 점검 위주"
            )
        }
        lower.contains("폴더") || lower.contains("프로젝트") || lower.contains("구조") -> {
            "프로젝트 폴더 작업을 어떤 방식으로 적용할까요?" to listOf(
                "현재 활성 프로젝트 기준 구조 정리 및 동기화 (권장)",
                "새 하위 모듈·폴더 구성안 먼저 생성",
                "기존 파일 유지하며 설정만 업데이트"
            )
        }
        lower.contains("할 일") || lower.contains("정리") || lower.contains("요약") -> {
            "원하시는 정리 스타일을 선택해 주세요" to listOf(
                "핵심 결론 + 바로 실행할 액션 3가지 (권장)",
                "단계별 체크리스트 형태로 상세 정리",
                "한 줄 요약 위주로 간결하게 브리핑"
            )
        }
        else -> {
            "요청하신 작업을 어떤 방향으로 진행할까요?" to listOf(
                "즉시 코드 반영 후 실측 검증까지 완료 (권장)",
                "핵심 변경안 먼저 요약 브리핑",
                "최소 수정으로 안전하게 적용"
            )
        }
    }

    return AiClarificationRequest(
        id = "clarify-${System.currentTimeMillis()}",
        agentMessageId = agentMessageId,
        originalPrompt = trimmed,
        questionTitle = question,
        options = options
    )
}

@Composable
private fun AiClarificationBottomCard(
    request: AiClarificationRequest,
    reducedMotion: Boolean,
    onSkip: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var selectedIdx by remember(request.id) { mutableIntStateOf(0) }
    var customOtherText by remember(request.id) { mutableStateOf("") }
    val otherOptionIdx = request.options.size
    val cardBg = Color(0xFF000000)
    val optionBg = Color(0xFF0E0E13)
    val selectedOptionBg = Color(0xFF172D29)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .border(
                width = 1.dp,
                color = AntigravityPalette.CalmSageAccent.copy(alpha = 0.26f),
                shape = RoundedCornerShape(22.dp)
            ),
        shape = RoundedCornerShape(22.dp),
        color = cardBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 15.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            // Question title only (no extra header badge label)
            Text(
                text = request.questionTitle,
                style = AntigravityTypography.PillLabel.copy(
                    color = AntigravityPalette.WarmIvoryInk,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )

            // Selectable Options List + Always-visible "기타" Direct Input Field
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                request.options.forEachIndexed { idx, optionText ->
                    val isSelected = idx == selectedIdx
                    val optInteraction = remember(idx) { MutableInteractionSource() }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 40.dp)
                            .tactilePressScale(optInteraction, reducedMotion)
                            .clip(RoundedCornerShape(13.dp))
                            .background(if (isSelected) selectedOptionBg else optionBg)
                            .clickable(
                                interactionSource = optInteraction,
                                indication = null,
                                role = Role.RadioButton,
                                onClick = { selectedIdx = idx }
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) AntigravityPalette.CalmSageAccent
                                        else Color(0xFF2B2B38)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = AntigravityIcons.CheckMark,
                                        contentDescription = null,
                                        tint = AntigravityPalette.DeepForestInk,
                                        modifier = Modifier.size(10.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(9.dp))
                            Text(
                                text = optionText,
                                style = AntigravityTypography.SecondaryBody.copy(
                                    color = if (isSelected) AntigravityPalette.WarmIvoryInk else AntigravityPalette.WarmIvoryInk.copy(alpha = 0.78f),
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Always-visible "기타" write-in input field
                val isOtherSelected = selectedIdx == otherOptionIdx || customOtherText.isNotBlank()
                val otherInputInteraction = remember { MutableInteractionSource() }
                val isOtherFocused by otherInputInteraction.collectIsFocusedAsState()
                LaunchedEffect(isOtherFocused) {
                    if (isOtherFocused) {
                        selectedIdx = otherOptionIdx
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 42.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(if (isOtherSelected) selectedOptionBg else optionBg)
                        .border(
                            width = 1.dp,
                            color = if (isOtherSelected || isOtherFocused) {
                                AntigravityPalette.CalmSageAccent.copy(alpha = 0.45f)
                            } else {
                                Color.Transparent
                            },
                            shape = RoundedCornerShape(13.dp)
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.RadioButton,
                            onClick = { selectedIdx = otherOptionIdx }
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(
                                if (isOtherSelected) AntigravityPalette.CalmSageAccent
                                else Color(0xFF2B2B38)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isOtherSelected) {
                            Icon(
                                imageVector = AntigravityIcons.CheckMark,
                                contentDescription = null,
                                tint = AntigravityPalette.DeepForestInk,
                                modifier = Modifier.size(10.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(9.dp))
                    Text(
                        text = "기타",
                        style = AntigravityTypography.PillLabel.copy(
                            color = if (isOtherSelected) AntigravityPalette.CalmSageAccent else AntigravityPalette.WarmIvoryInk.copy(alpha = 0.85f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (customOtherText.isEmpty()) {
                            Text(
                                text = "원하시는 내용을 직접 입력하세요...",
                                style = AntigravityTypography.SecondaryBody.copy(
                                    color = AntigravityPalette.WarmIvoryInk.copy(alpha = 0.42f),
                                    fontSize = 12.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        BasicTextField(
                            value = customOtherText,
                            onValueChange = {
                                customOtherText = it
                                selectedIdx = otherOptionIdx
                            },
                            interactionSource = otherInputInteraction,
                            singleLine = true,
                            textStyle = AntigravityTypography.SecondaryBody.copy(
                                color = AntigravityPalette.WarmIvoryInk,
                                fontSize = 12.sp
                            ),
                            cursorBrush = SolidColor(AntigravityPalette.CalmSageAccent),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    val chosen = customOtherText.trim().ifBlank {
                                        request.options.first()
                                    }
                                    onSubmit(chosen)
                                }
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Two Choice Action Buttons: [건너뛰기] and [제출]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val skipInteraction = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 42.dp)
                        .tactilePressScale(skipInteraction, reducedMotion)
                        .clip(RoundedCornerShape(13.dp))
                        .background(Color(0xFF20202B))
                        .clickable(
                            interactionSource = skipInteraction,
                            indication = null,
                            role = Role.Button,
                            onClick = onSkip
                        )
                        .semantics {
                            contentDescription = "건너뛰기"
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "건너뛰기",
                        style = AntigravityTypography.PillLabel.copy(
                            color = AntigravityPalette.WarmIvoryInk.copy(alpha = 0.82f),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }

                val submitInteraction = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 42.dp)
                        .tactilePressScale(submitInteraction, reducedMotion)
                        .clip(RoundedCornerShape(13.dp))
                        .background(AntigravityPalette.CalmSageAccent)
                        .clickable(
                            interactionSource = submitInteraction,
                            indication = null,
                            role = Role.Button,
                            onClick = {
                                val chosen = if (selectedIdx == otherOptionIdx || customOtherText.isNotBlank()) {
                                    customOtherText.trim().ifBlank { "기타 (직접 지정)" }
                                } else {
                                    request.options.getOrElse(selectedIdx) { request.options.first() }
                                }
                                onSubmit(chosen)
                            }
                        )
                        .semantics {
                            contentDescription = "제출"
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "제출",
                        style = AntigravityTypography.PillLabel.copy(
                            color = AntigravityPalette.DeepForestInk,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun SystemPromptEditorDialog(
    bundle: SystemPromptBundle,
    statusBanner: String,
    connectionState: BridgeConnectionState,
    onReloadFromPc: () -> Unit,
    onSaveTargetPrompt: (targetKey: String, targetLabel: String, content: String) -> Unit,
    onDismiss: () -> Unit
) {
    val tabs = remember {
        listOf(
            Triple("gemini", "GEMINI.md", "전역 응답·하네스 원칙"),
            Triple("design-motion", "design-motion.md", "D0~D5 디자인·모션 스택"),
            Triple("claude", "CLAUDE.md", "Claude 전역 지침")
        )
    }
    var selectedTabIdx by rememberSaveable { mutableIntStateOf(0) }
    var geminiDraft by remember(bundle.geminiMd, bundle.updatedAt) { mutableStateOf(bundle.geminiMd) }
    var designMotionDraft by remember(bundle.designMotionMd, bundle.updatedAt) { mutableStateOf(bundle.designMotionMd) }
    var claudeDraft by remember(bundle.claudeMd, bundle.updatedAt) { mutableStateOf(bundle.claudeMd) }

    val (activeKey, activeFileName, activeSubtitle) = tabs[selectedTabIdx]
    val activeText = when (activeKey) {
        "design-motion" -> designMotionDraft
        "claude" -> claudeDraft
        else -> geminiDraft
    }
    val onActiveTextChange: (String) -> Unit = { updated ->
        when (activeKey) {
            "design-motion" -> designMotionDraft = updated
            "claude" -> claudeDraft = updated
            else -> geminiDraft = updated
        }
    }

    val lineCount = remember(activeText) { if (activeText.isEmpty()) 0 else activeText.lines().size }
    val charCount = activeText.length
    val isCompactOptimal = lineCount <= 100

    val quickSnippets = remember {
        listOf(
            "+ 1문장 착수·즉시 완료" to "\n- 작업 시작 시 1문장으로 핵심 의도만 짚고 같은 턴에서 즉시 도구를 실행해 완료합니다.",
            "+ 단문·능동태 브리핑" to "\n- 현재 시제·능동태·단문(`~합니다`/`~했습니다`)으로 결론과 변경 파일만 간결하게 제시합니다.",
            "+ 네이티브 실측 검증" to "\n- 웹 린트를 네이티브 검증으로 대체하지 않으며 실제 빌드·런타임 테스트 결과를 분리 보고합니다.",
            "+ 무단 설치·전송 금지" to "\n- 외부 바이너리 다운로드, 로그인, 텔레메트리 전송은 자동 실행하지 않고 사전 승인을 받습니다."
        )
    }

    val dialogBg = Color(0xFF000000)
    val cardBg = Color(0xFF0E0E13)
    val selectedCardBg = Color(0xFF172D29)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = dialogBg,
        titleContentColor = AntigravityPalette.WarmIvoryInk,
        textContentColor = AntigravityPalette.MistSlateInk,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "시스템 프롬프트 편집기",
                        style = AntigravityTypography.CardTitle.copy(fontSize = 15.5.sp)
                    )
                    Text(
                        text = activeSubtitle,
                        style = AntigravityTypography.SecondaryBody.copy(
                            color = AntigravityPalette.WarmIvoryInk.copy(alpha = 0.55f),
                            fontSize = 11.sp
                        )
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(cardBg)
                        .clickable(role = Role.Button, onClick = onReloadFromPc)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "PC 불러오기",
                        style = AntigravityTypography.PillLabel.copy(
                            color = AntigravityPalette.CalmSageAccent,
                            fontSize = 10.5.sp
                        )
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 490.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1. Target File Tabs (GEMINI.md / design-motion.md / CLAUDE.md)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    tabs.forEachIndexed { idx, (_, tabTitle, _) ->
                        val isSel = idx == selectedTabIdx
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 36.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(if (isSel) selectedCardBg else cardBg)
                                .clickable(role = Role.Tab) {
                                    selectedTabIdx = idx
                                }
                                .padding(horizontal = 6.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tabTitle,
                                style = AntigravityTypography.PillLabel.copy(
                                    color = if (isSel) AntigravityPalette.CalmSageAccent else AntigravityPalette.WarmIvoryInk.copy(alpha = 0.72f),
                                    fontSize = 11.sp,
                                    fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // 2. Live Line/Char Counter & Benchmark Density Indicator
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(cardBg)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$activeFileName · ${lineCount}줄 · ${charCount}자",
                        style = AntigravityTypography.TabularMetric.copy(
                            color = AntigravityPalette.WarmIvoryInk.copy(alpha = 0.82f),
                            fontSize = 11.sp
                        )
                    )
                    Text(
                        text = if (isCompactOptimal) "100줄 이하 · 최적 밀도" else "100줄 초과 · 목차화 권장",
                        style = AntigravityTypography.SecondaryBody.copy(
                            color = if (isCompactOptimal) AntigravityPalette.CalmSageAccent else AntigravityPalette.WarmLampGold,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }

                // 3. One-Tap Rule Preset Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickSnippets.forEach { (chipLabel, snippetText) ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(cardBg)
                                .clickable(role = Role.Button) {
                                    onActiveTextChange(PcBridgeClient.applyPresetSnippet(activeText, snippetText))
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = chipLabel,
                                style = AntigravityTypography.SecondaryBody.copy(
                                    color = AntigravityPalette.WarmLampGold,
                                    fontSize = 10.5.sp
                                ),
                                maxLines = 1
                            )
                        }
                    }
                }

                // 4. Multi-line Markdown / System Prompt Editor Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(min = 240.dp, max = 320.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(cardBg)
                        .border(
                            width = 1.dp,
                            color = AntigravityPalette.CalmSageAccent.copy(alpha = 0.22f),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (activeText.isEmpty()) {
                        Text(
                            text = "시스템 프롬프트 지침을 입력하세요...",
                            style = AntigravityTypography.CodeMono.copy(
                                color = AntigravityPalette.WarmIvoryInk.copy(alpha = 0.35f),
                                fontSize = 12.sp
                            )
                        )
                    }
                    BasicTextField(
                        value = activeText,
                        onValueChange = onActiveTextChange,
                        textStyle = AntigravityTypography.CodeMono.copy(
                            color = AntigravityPalette.WarmIvoryInk,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        ),
                        cursorBrush = SolidColor(AntigravityPalette.CalmSageAccent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 210.dp)
                    )
                }

                if (statusBanner.isNotBlank()) {
                    Text(
                        text = statusBanner,
                        style = AntigravityTypography.SecondaryBody.copy(
                            color = AntigravityPalette.CalmSageAccent,
                            fontSize = 11.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(
                        text = if (connectionState == BridgeConnectionState.CONNECTED_PC) {
                            "저장 시 PC의 ~/.gemini/$activeFileName 파일에 즉시 반영됩니다."
                        } else {
                            "현재 모바일 단독 모드 · PC 연결 시 실시간 동기화됩니다."
                        },
                        style = AntigravityTypography.SecondaryBody.copy(
                            color = AntigravityPalette.WarmIvoryInk.copy(alpha = 0.48f),
                            fontSize = 10.5.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        confirmButton = {
            Box(
                modifier = Modifier
                    .defaultMinSize(minHeight = 44.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(AntigravityPalette.CalmSageAccent)
                    .clickable(role = Role.Button) {
                        onSaveTargetPrompt(activeKey, activeFileName, activeText)
                    }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "PC에 즉시 저장",
                    style = AntigravityTypography.PillLabel.copy(
                        color = AntigravityPalette.DeepForestInk,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp
                    )
                )
            }
        },
        dismissButton = {
            Box(
                modifier = Modifier
                    .defaultMinSize(minHeight = 44.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .clickable(role = Role.Button, onClick = onDismiss)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "닫기",
                    style = AntigravityTypography.PillLabel.copy(
                        color = AntigravityPalette.MistSlateInk,
                        fontSize = 12.5.sp
                    )
                )
            }
        }
    )
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("Antigravity Text", text))
}

/**
 * Reverse-Engineered Sunshine + Moonlight (`AbsoluteTouchContext.java` & `RelativeTouchContext.java`)
 * Ultra-Low-Latency (60~120FPS) Touchscreen & Trackpad Remote Control Overlay.
 *
 * - Touchscreen Mode (`DIRECT_TOUCHSCREEN`):
 *   - Touching any coordinate on the streamed PC screen immediately maps to exact normalized PC coordinates
 *     via `MoonlightTouchEngine.clampNormalized` & `mapToHostCoordinate` and triggers an immediate PC mouse click (`TOUCH_TAP`).
 *   - Holding >= 650ms within 30px triggers PC right-click (`LONG_PRESS_RIGHT`).
 *   - Tapping twice within 250ms and 60px triggers PC double-click (`DOUBLE_TAP`).
 *   - Moving beyond 20px deadzone drags the PC cursor continuously (`ABS_POS`).
 *   - Two-finger drag scrolls the PC wheel (`SCROLL`, factor 3x).
 * - Trackpad Mode (`RELATIVE_TRACKPAD`):
 *   - Single-finger drag moves PC cursor relatively (`REL_MOVE`); tap clicks at current cursor position.
 */
@Composable
private fun MoonlightTouchScreenOverlay(
    host: String,
    port: Int,
    connectionState: BridgeConnectionState,
    touchMode: MoonlightTouchMode,
    fpsTarget: MoonlightFpsTarget,
    reducedMotion: Boolean,
    onTouchModeChange: (MoonlightTouchMode) -> Unit,
    onFpsTargetChange: (MoonlightFpsTarget) -> Unit,
    onConnectionVerified: () -> Unit,
    onDismiss: () -> Unit
) {
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()

    var liveFrameBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var frameMeta by remember { mutableStateOf(SunshineFrameMetadata()) }
    var canvasSize by remember { mutableStateOf(IntSize(1280, 720)) }
    var measuredFps by remember { mutableIntStateOf(fpsTarget.fps) }
    var isLiveStreamingPc by remember { mutableStateOf(connectionState == BridgeConnectionState.CONNECTED_PC) }

    // Normalized cursor position on the screen canvas (0f..1f)
    var cursorNormX by remember { mutableStateOf(0.5f) }
    var cursorNormY by remember { mutableStateOf(0.5f) }
    var lastTouchNormX by remember { mutableStateOf(0.5f) }
    var lastTouchNormY by remember { mutableStateOf(0.5f) }
    var lastActionBadge by remember { mutableStateOf("화면을 터치하면 해당 PC 좌표에 즉시 클릭이 발생합니다") }
    var isRightClickRipple by remember { mutableStateOf(false) }

    // Composite-only GPU ripple animation for touch-to-click target feedback
    val rippleProgress = remember { Animatable(1f) }

    // Track last tap time & coordinates for Moonlight's 250ms / 60px double-tap detector
    var lastTapUpTimeMs by remember { mutableStateOf(0L) }
    var lastTapUpX by remember { mutableStateOf(0f) }
    var lastTapUpY by remember { mutableStateOf(0f) }

    fun triggerRipple(normX: Float, normY: Float, rightClick: Boolean) {
        lastTouchNormX = normX
        lastTouchNormY = normY
        isRightClickRipple = rightClick
        if (!reducedMotion) {
            coroutineScope.launch {
                rippleProgress.snapTo(0f)
                rippleProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
                )
            }
        }
    }

    fun dispatchPacketToPc(packet: MoonlightInputPacket, badgeSummary: String) {
        lastActionBadge = badgeSummary
        coroutineScope.launch {
            val updatedMeta = PcBridgeClient.sendSunshineInput(host, port, packet)
            if (updatedMeta != null) {
                isLiveStreamingPc = true
                onConnectionVerified()
                frameMeta = updatedMeta
                if (updatedMeta.screenWidth > 0 && updatedMeta.screenHeight > 0) {
                    cursorNormX = (updatedMeta.cursorX.toFloat() / updatedMeta.screenWidth.toFloat()).coerceIn(0f, 1f)
                    cursorNormY = (updatedMeta.cursorY.toFloat() / updatedMeta.screenHeight.toFloat()).coerceIn(0f, 1f)
                }
            } else {
                // Offline / standalone simulation fallback updates local simulated PC cursor
                val simW = frameMeta.screenWidth.coerceAtLeast(1920)
                val simH = frameMeta.screenHeight.coerceAtLeast(1080)
                val nextX = (cursorNormX * simW).toInt().coerceIn(0, simW - 1)
                val nextY = (cursorNormY * simH).toInt().coerceIn(0, simH - 1)
                frameMeta = frameMeta.copy(
                    screenWidth = simW,
                    screenHeight = simH,
                    cursorX = nextX,
                    cursorY = nextY,
                    lastEvent = badgeSummary
                )
            }
        }
    }

    // High-FPS (60~120fps) frame polling loop from SunshineHostDaemon via PC Bridge
    LaunchedEffect(host, port, fpsTarget) {
        var frameCounter = 0
        var windowStartMs = System.currentTimeMillis()
        while (true) {
            val tickStart = System.currentTimeMillis()
            val payload = PcBridgeClient.fetchSunshineFrame(host, port)
            if (payload != null && payload.jpegBytes.isNotEmpty()) {
                val decoded = try {
                    BitmapFactory.decodeByteArray(payload.jpegBytes, 0, payload.jpegBytes.size)?.asImageBitmap()
                } catch (_: Exception) {
                    null
                }
                if (decoded != null) {
                    liveFrameBitmap = decoded
                    isLiveStreamingPc = true
                    onConnectionVerified()
                    frameMeta = payload.metadata
                    if (payload.metadata.screenWidth > 0 && payload.metadata.screenHeight > 0) {
                        cursorNormX = (payload.metadata.cursorX.toFloat() / payload.metadata.screenWidth.toFloat()).coerceIn(0f, 1f)
                        cursorNormY = (payload.metadata.cursorY.toFloat() / payload.metadata.screenHeight.toFloat()).coerceIn(0f, 1f)
                    }
                }
            } else {
                isLiveStreamingPc = false
            }

            frameCounter += 1
            val now = System.currentTimeMillis()
            val elapsedWindow = now - windowStartMs
            if (elapsedWindow >= 1000L) {
                measuredFps = if (isLiveStreamingPc) {
                    ((frameCounter * 1000f) / elapsedWindow.toFloat()).toInt().coerceIn(1, fpsTarget.fps)
                } else {
                    fpsTarget.fps
                }
                frameCounter = 0
                windowStartMs = now
            }

            val frameCost = System.currentTimeMillis() - tickStart
            val sleepMs = (fpsTarget.frameIntervalMs - frameCost).coerceAtLeast(4L)
            delay(sleepMs)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = AntigravityPalette.NightGround
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Top Bar: Sunshine + Moonlight Status, Live Resolution & FPS, Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(38.dp),
                        shape = CircleShape,
                        color = Color(0xFF172D29)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = AntigravityIcons.DesktopStreamCursor,
                                contentDescription = null,
                                tint = AntigravityPalette.CalmSageAccent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isLiveStreamingPc) AntigravityPalette.CalmSageAccent
                                        else AntigravityPalette.WarmLampGold
                                    )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Sunshine · Moonlight 터치 원격",
                                style = AntigravityTypography.CardTitle.copy(fontSize = 14.5.sp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = if (isLiveStreamingPc) {
                                "PC 실시간 스트리밍 (${frameMeta.screenWidth}×${frameMeta.screenHeight} · ${fpsTarget.label})"
                            } else {
                                "단독 시뮬레이션 화면 (${frameMeta.screenWidth}×${frameMeta.screenHeight} · ${fpsTarget.label})"
                            },
                            style = AntigravityTypography.TabularMetric.copy(
                                color = AntigravityPalette.WarmIvoryInk.copy(alpha = 0.62f),
                                fontSize = 11.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                val closeInteraction = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .defaultMinSize(minWidth = 48.dp, minHeight = 44.dp)
                        .tactilePressScale(closeInteraction, reducedMotion)
                        .clip(RoundedCornerShape(14.dp))
                        .background(AntigravityPalette.NightSurface)
                        .clickable(
                            interactionSource = closeInteraction,
                            indication = null,
                            role = Role.Button,
                            onClick = onDismiss
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "닫기",
                        style = AntigravityTypography.PillLabel.copy(
                            color = AntigravityPalette.WarmIvoryInk,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            }

            // 2. Mode & FPS Selector Row: [터치스크린 (즉시 클릭)] vs [트랙패드 (상대 이동)] | [60 FPS] / [120 FPS]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MoonlightTouchMode.entries.forEach { mode ->
                    val selected = mode == touchMode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (selected) Color(0xFF172D29) else AntigravityPalette.NightSurface
                            )
                            .border(
                                width = 1.dp,
                                color = if (selected) AntigravityPalette.CalmSageAccent.copy(alpha = 0.45f) else Color.Transparent,
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable(role = Role.RadioButton) {
                                onTouchModeChange(mode)
                                lastActionBadge = "${mode.label}: ${mode.subtitle}"
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = mode.label,
                            style = AntigravityTypography.PillLabel.copy(
                                color = if (selected) AntigravityPalette.CalmSageAccent else AntigravityPalette.WarmIvoryInk.copy(alpha = 0.75f),
                                fontSize = 11.5.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                MoonlightFpsTarget.entries.forEach { target ->
                    val selected = target == fpsTarget
                    Box(
                        modifier = Modifier
                            .defaultMinSize(minWidth = 64.dp, minHeight = 44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (selected) Color(0xFF2B2418) else AntigravityPalette.NightSurface
                            )
                            .clickable(role = Role.RadioButton) {
                                onFpsTargetChange(target)
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = target.label,
                            style = AntigravityTypography.TabularMetric.copy(
                                color = if (selected) AntigravityPalette.WarmLampGold else AntigravityPalette.WarmIvoryInk.copy(alpha = 0.65f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }

            // 3. Interactive PC Stream Viewport Canvas (16:9 Desktop Aspect Ratio with Direct Touchscreen / Relative Trackpad Engine)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF07090C))
                    .border(
                        width = 1.dp,
                        color = AntigravityPalette.CalmSageAccent.copy(alpha = 0.28f),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .onSizeChanged { size ->
                        if (size.width > 0 && size.height > 0) {
                            canvasSize = size
                        }
                    }
                    .pointerInput(touchMode, host, port) {
                        awaitEachGesture {
                            var downPos = Offset.Zero
                            var latestPos = Offset.Zero
                            var downTimeMs = System.currentTimeMillis()
                            var hasInitializedDown = false
                            var maxPointerCount = 1
                            var totalScrollDy = 0f

                            while (true) {
                                val event = awaitPointerEvent()
                                val activeChanges = event.changes.filter { it.pressed }
                                if (activeChanges.isEmpty()) {
                                    break
                                }

                                if (activeChanges.size > maxPointerCount) {
                                    maxPointerCount = activeChanges.size
                                }

                                val primary = activeChanges.first()
                                if (!hasInitializedDown) {
                                    hasInitializedDown = true
                                    downPos = primary.position
                                    latestPos = primary.position
                                    downTimeMs = System.currentTimeMillis()
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)

                                    if (touchMode == MoonlightTouchMode.DIRECT_TOUCHSCREEN) {
                                        val (nx, ny) = MoonlightTouchEngine.clampNormalized(
                                            touchX = downPos.x,
                                            touchY = downPos.y,
                                            viewWidth = size.width.toFloat(),
                                            viewHeight = size.height.toFloat()
                                        )
                                        cursorNormX = nx
                                        cursorNormY = ny
                                        triggerRipple(nx, ny, rightClick = false)
                                    }
                                } else {
                                    val prevPos = latestPos
                                    latestPos = primary.position
                                    val dx = latestPos.x - prevPos.x
                                    val dy = latestPos.y - prevPos.y

                                    if (activeChanges.size >= 2) {
                                        // Two-finger scroll (Moonlight SCROLL_SPEED_FACTOR = 3)
                                        totalScrollDy += dy
                                        if (kotlin.math.abs(totalScrollDy) >= 18f) {
                                            val wheelClicks = ((totalScrollDy / 18f).toInt() * MoonlightTouchEngine.SCROLL_SPEED_FACTOR).coerceIn(-15, 15)
                                            totalScrollDy = 0f
                                            dispatchPacketToPc(
                                                packet = MoonlightInputPacket(
                                                    action = "SCROLL",
                                                    normalizedX = cursorNormX,
                                                    normalizedY = cursorNormY,
                                                    scrollAmount = wheelClicks
                                                ),
                                                badgeSummary = "2손가락 휠 스크롤 (${wheelClicks})"
                                            )
                                        }
                                    } else if (touchMode == MoonlightTouchMode.DIRECT_TOUCHSCREEN) {
                                        val moveDist = kotlin.math.hypot(
                                            (latestPos.x - downPos.x).toDouble(),
                                            (latestPos.y - downPos.y).toDouble()
                                        ).toFloat()
                                        if (moveDist > MoonlightTouchEngine.TOUCH_DOWN_DEAD_ZONE_DISTANCE_THRESHOLD) {
                                            val (nx, ny) = MoonlightTouchEngine.clampNormalized(
                                                touchX = latestPos.x,
                                                touchY = latestPos.y,
                                                viewWidth = size.width.toFloat(),
                                                viewHeight = size.height.toFloat()
                                            )
                                            cursorNormX = nx
                                            cursorNormY = ny
                                        }
                                    } else {
                                        // RELATIVE_TRACKPAD mode: relative cursor movement
                                        val vw = size.width.toFloat().coerceAtLeast(1f)
                                        val vh = size.height.toFloat().coerceAtLeast(1f)
                                        cursorNormX = (cursorNormX + (dx / vw) * 1.35f).coerceIn(0f, 1f)
                                        cursorNormY = (cursorNormY + (dy / vh) * 1.35f).coerceIn(0f, 1f)
                                    }
                                }
                                event.changes.forEach { it.consume() }
                            }

                            if (hasInitializedDown && maxPointerCount == 1) {
                                val upTimeMs = System.currentTimeMillis()
                                val holdMs = (upTimeMs - downTimeMs).coerceAtLeast(0L)
                                val sinceLastTapMs = if (lastTapUpTimeMs > 0L) {
                                    (downTimeMs - lastTapUpTimeMs).coerceAtLeast(0L)
                                } else {
                                    Long.MAX_VALUE
                                }

                                if (touchMode == MoonlightTouchMode.DIRECT_TOUCHSCREEN) {
                                    val packet = MoonlightTouchEngine.resolveDirectTouchGesture(
                                        downX = downPos.x,
                                        downY = downPos.y,
                                        upX = latestPos.x,
                                        upY = latestPos.y,
                                        viewWidth = size.width.toFloat(),
                                        viewHeight = size.height.toFloat(),
                                        holdDurationMs = holdMs,
                                        timeSinceLastTapMs = sinceLastTapMs,
                                        lastTapX = lastTapUpX,
                                        lastTapY = lastTapUpY
                                    )
                                    cursorNormX = packet.normalizedX
                                    cursorNormY = packet.normalizedY

                                    val hostX = MoonlightTouchEngine.mapToHostCoordinate(packet.normalizedX, frameMeta.screenWidth)
                                    val hostY = MoonlightTouchEngine.mapToHostCoordinate(packet.normalizedY, frameMeta.screenHeight)

                                    val isRight = packet.mouseButton == MoonlightMouseButton.RIGHT
                                    triggerRipple(packet.normalizedX, packet.normalizedY, rightClick = isRight)
                                    if (isRight) {
                                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                    }

                                    val actionKor = when (packet.action) {
                                        "DOUBLE_TAP" -> "더블클릭"
                                        "LONG_PRESS_RIGHT" -> "길게 눌러 우클릭"
                                        "ABS_POS" -> "드래그 이동 후 클릭"
                                        else -> "터치 지점 즉시 좌클릭"
                                    }
                                    dispatchPacketToPc(
                                        packet = packet,
                                        badgeSummary = "PC ($hostX, $hostY) $actionKor"
                                    )

                                    lastTapUpTimeMs = upTimeMs
                                    lastTapUpX = latestPos.x
                                    lastTapUpY = latestPos.y
                                } else {
                                    // RELATIVE_TRACKPAD mode
                                    val moveDist = kotlin.math.hypot(
                                        (latestPos.x - downPos.x).toDouble(),
                                        (latestPos.y - downPos.y).toDouble()
                                    ).toFloat()
                                    val hostX = MoonlightTouchEngine.mapToHostCoordinate(cursorNormX, frameMeta.screenWidth)
                                    val hostY = MoonlightTouchEngine.mapToHostCoordinate(cursorNormY, frameMeta.screenHeight)

                                    if (moveDist <= MoonlightTouchEngine.TOUCH_DOWN_DEAD_ZONE_DISTANCE_THRESHOLD) {
                                        val isRight = holdMs >= MoonlightTouchEngine.LONG_PRESS_TIME_THRESHOLD
                                        triggerRipple(cursorNormX, cursorNormY, rightClick = isRight)
                                        dispatchPacketToPc(
                                            packet = MoonlightInputPacket(
                                                action = if (isRight) "LONG_PRESS_RIGHT" else "TOUCH_TAP",
                                                normalizedX = cursorNormX,
                                                normalizedY = cursorNormY,
                                                button = if (isRight) MoonlightMouseButton.RIGHT else MoonlightMouseButton.LEFT
                                            ),
                                            badgeSummary = if (isRight) "트랙패드 우클릭 ($hostX, $hostY)" else "트랙패드 좌클릭 ($hostX, $hostY)"
                                        )
                                    } else {
                                        dispatchPacketToPc(
                                            packet = MoonlightInputPacket(
                                                action = "ABS_POS",
                                                normalizedX = cursorNormX,
                                                normalizedY = cursorNormY,
                                                button = MoonlightMouseButton.NONE
                                            ),
                                            badgeSummary = "트랙패드 커서 이동 ($hostX, $hostY)"
                                        )
                                    }
                                }
                            }
                        }
                    }
            ) {
                val bitmap = liveFrameBitmap
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = "PC 실시간 스트리밍 화면",
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Realistic Simulated PC Antigravity Desktop Canvas when offline/standalone
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawRect(color = Color(0xFF0A0E13))
                        // Top IDE title bar
                        drawRect(
                            color = Color(0xFF121820),
                            topLeft = Offset.Zero,
                            size = androidx.compose.ui.geometry.Size(size.width, 34.dp.toPx())
                        )
                        // Left file explorer pane
                        drawRect(
                            color = Color(0xFF0E131A),
                            topLeft = Offset(0f, 34.dp.toPx()),
                            size = androidx.compose.ui.geometry.Size(size.width * 0.24f, size.height - 62.dp.toPx())
                        )
                        // Center code editor lines
                        val editorLeft = size.width * 0.27f
                        for (row in 0 until 11) {
                            val lineY = 54.dp.toPx() + row * 20.dp.toPx()
                            if (lineY < size.height - 44.dp.toPx()) {
                                val lineWidth = size.width * (if (row % 3 == 0) 0.52f else if (row % 2 == 0) 0.38f else 0.61f)
                                drawRoundRect(
                                    color = if (row == 2 || row == 6) Color(0xFF68C5B2).copy(alpha = 0.28f)
                                    else Color(0xFFF2EFE9).copy(alpha = 0.12f),
                                    topLeft = Offset(editorLeft, lineY),
                                    size = androidx.compose.ui.geometry.Size(lineWidth, 8.dp.toPx()),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx())
                                )
                            }
                        }
                        // Bottom Windows taskbar
                        drawRect(
                            color = Color(0xFF141B24),
                            topLeft = Offset(0f, size.height - 28.dp.toPx()),
                            size = androidx.compose.ui.geometry.Size(size.width, 28.dp.toPx())
                        )
                    }
                }

                // Visual Touch Ripple + PC Mouse Cursor Crosshair Overlay
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val touchPxX = (lastTouchNormX * size.width).coerceIn(0f, size.width)
                    val touchPxY = (lastTouchNormY * size.height).coerceIn(0f, size.height)
                    val prog = rippleProgress.value
                    if (prog < 0.99f) {
                        val rippleColor = if (isRightClickRipple) {
                            Color(0xFFE8B96D)
                        } else {
                            Color(0xFF68C5B2)
                        }
                        val radius = (12.dp.toPx() + prog * 34.dp.toPx())
                        drawCircle(
                            color = rippleColor.copy(alpha = (1f - prog) * 0.75f),
                            radius = radius,
                            center = Offset(touchPxX, touchPxY),
                            style = Stroke(width = 2.5.dp.toPx())
                        )
                    }

                    // Live PC Cursor Indicator
                    val cursorPxX = (cursorNormX * size.width).coerceIn(0f, size.width)
                    val cursorPxY = (cursorNormY * size.height).coerceIn(0f, size.height)
                    drawCircle(
                        color = Color(0xFF000000).copy(alpha = 0.65f),
                        radius = 9.dp.toPx(),
                        center = Offset(cursorPxX, cursorPxY)
                    )
                    drawCircle(
                        color = Color(0xFF68C5B2),
                        radius = 6.dp.toPx(),
                        center = Offset(cursorPxX, cursorPxY)
                    )
                    drawCircle(
                        color = Color(0xFFF2EFE9),
                        radius = 2.5.dp.toPx(),
                        center = Offset(cursorPxX, cursorPxY)
                    )
                }

                // Top-left floating HUD badge inside the stream viewport
                val hostCursorX = MoonlightTouchEngine.mapToHostCoordinate(cursorNormX, frameMeta.screenWidth)
                val hostCursorY = MoonlightTouchEngine.mapToHostCoordinate(cursorNormY, frameMeta.screenHeight)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF000000).copy(alpha = 0.72f))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "${touchMode.label} · ${measuredFps}fps · PC($hostCursorX, $hostCursorY)",
                        style = AntigravityTypography.TabularMetric.copy(
                            color = AntigravityPalette.CalmSageAccent,
                            fontSize = 11.sp
                        )
                    )
                }
            }

            // 4. Live Coordinate & Gesture Feedback Banner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(AntigravityPalette.NightSurface)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = lastActionBadge,
                    style = AntigravityTypography.SecondaryBody.copy(
                        color = AntigravityPalette.WarmIvoryInk,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "길게 누르면 우클릭 · 2손가락 스크롤",
                    style = AntigravityTypography.SecondaryBody.copy(
                        color = AntigravityPalette.WarmLampGold,
                        fontSize = 10.5.sp
                    )
                )
            }

            // 5. Quick Mouse & Keyboard Auxiliary Dock (48dp+ touch targets)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val quickButtons = listOf(
                    Triple("좌클릭", "TOUCH_TAP", MoonlightMouseButton.LEFT),
                    Triple("우클릭", "LONG_PRESS_RIGHT", MoonlightMouseButton.RIGHT),
                    Triple("더블클릭", "DOUBLE_TAP", MoonlightMouseButton.LEFT),
                    Triple("스크롤 ↑", "SCROLL_UP", MoonlightMouseButton.NONE),
                    Triple("스크롤 ↓", "SCROLL_DOWN", MoonlightMouseButton.NONE),
                    Triple("Enter", "KEY_ENTER", MoonlightMouseButton.NONE),
                    Triple("ESC", "KEY_ESC", MoonlightMouseButton.NONE)
                )

                quickButtons.forEach { (btnLabel, actionCode, mouseBtn) ->
                    val btnInteraction = remember(btnLabel) { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .defaultMinSize(minWidth = 68.dp, minHeight = 48.dp)
                            .tactilePressScale(btnInteraction, reducedMotion)
                            .clip(RoundedCornerShape(14.dp))
                            .background(AntigravityPalette.NightSurface)
                            .clickable(
                                interactionSource = btnInteraction,
                                indication = null,
                                role = Role.Button,
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    val hx = MoonlightTouchEngine.mapToHostCoordinate(cursorNormX, frameMeta.screenWidth)
                                    val hy = MoonlightTouchEngine.mapToHostCoordinate(cursorNormY, frameMeta.screenHeight)
                                    when (actionCode) {
                                        "SCROLL_UP" -> dispatchPacketToPc(
                                            packet = MoonlightInputPacket(
                                                action = "SCROLL",
                                                normalizedX = cursorNormX,
                                                normalizedY = cursorNormY,
                                                scrollAmount = 3
                                            ),
                                            badgeSummary = "빠른 휠 스크롤 ↑ ($hx, $hy)"
                                        )
                                        "SCROLL_DOWN" -> dispatchPacketToPc(
                                            packet = MoonlightInputPacket(
                                                action = "SCROLL",
                                                normalizedX = cursorNormX,
                                                normalizedY = cursorNormY,
                                                scrollAmount = -3
                                            ),
                                            badgeSummary = "빠른 휠 스크롤 ↓ ($hx, $hy)"
                                        )
                                        "KEY_ENTER" -> dispatchPacketToPc(
                                            packet = MoonlightInputPacket(
                                                action = "KEY_PRESS",
                                                normalizedX = cursorNormX,
                                                normalizedY = cursorNormY,
                                                keyText = "{ENTER}"
                                            ),
                                            badgeSummary = "PC 키 입력 전송: Enter"
                                        )
                                        "KEY_ESC" -> dispatchPacketToPc(
                                            packet = MoonlightInputPacket(
                                                action = "KEY_PRESS",
                                                normalizedX = cursorNormX,
                                                normalizedY = cursorNormY,
                                                keyText = "{ESC}"
                                            ),
                                            badgeSummary = "PC 키 입력 전송: ESC"
                                        )
                                        else -> {
                                            triggerRipple(
                                                normX = cursorNormX,
                                                normY = cursorNormY,
                                                rightClick = mouseBtn == MoonlightMouseButton.RIGHT
                                            )
                                            dispatchPacketToPc(
                                                packet = MoonlightInputPacket(
                                                    action = actionCode,
                                                    normalizedX = cursorNormX,
                                                    normalizedY = cursorNormY,
                                                    button = mouseBtn
                                                ),
                                                badgeSummary = "PC ($hx, $hy) $btnLabel 실행"
                                            )
                                        }
                                    }
                                }
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = btnLabel,
                            style = AntigravityTypography.PillLabel.copy(
                                color = AntigravityPalette.WarmIvoryInk,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }
            }
        }
    }
}

private fun resolveUriDisplayNameAndSize(
    context: Context,
    uri: Uri,
    fallbackName: String
): Pair<String, Long> {
    var displayName = fallbackName
    var sizeBytes = 0L
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    val value = cursor.getString(nameIndex)
                    if (!value.isNullOrBlank()) displayName = value
                }
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    sizeBytes = cursor.getLong(sizeIndex)
                }
            }
        }
    } catch (_: Exception) {
    }
    return displayName to sizeBytes
}
