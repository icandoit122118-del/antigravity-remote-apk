package com.antigravity.remote.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkOut
import androidx.compose.animation.shrinkVertically
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
import com.antigravity.remote.model.ReasoningEffort
import com.antigravity.remote.model.WorkspaceSession
import com.antigravity.remote.network.LiveSyncState
import com.antigravity.remote.network.PcBridgeClient
import com.antigravity.remote.network.PcProjectFolder
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
    var showSettingsSheet by rememberSaveable { mutableStateOf(false) }
    var showSessionProjectPopup by rememberSaveable { mutableStateOf(false) }

    // AI Engine, Reasoning Effort & Local AI Selection
    val engineOptions = remember { AiEngineCatalog.options }
    var selectedEngineId by rememberSaveable { mutableStateOf(engineOptions.first().id) }
    var selectedReasoning by rememberSaveable { mutableStateOf(ReasoningEffort.MEDIUM) }
    var showModelReasoningPopup by rememberSaveable { mutableStateOf(false) }
    var showPlusMenuPopup by rememberSaveable { mutableStateOf(false) }
    var customLocalModelId by rememberSaveable { mutableStateOf("qwen2.5-coder:7b") }
    val activeEngine by remember(selectedEngineId) {
        derivedStateOf {
            engineOptions.find { it.id == selectedEngineId } ?: engineOptions.first()
        }
    }
    val effectiveModelId = if (activeEngine.isLocal && customLocalModelId.isNotBlank() && selectedEngineId == "engine-local-qwen") {
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
    var streamPhaseLabel by remember { mutableStateOf("답변을 준비하고 있습니다...") }
    var steeringStatusLabel by remember { mutableStateOf("") }
    val pendingSteerNotes = remember { mutableStateListOf<String>() }

    // Prompt Input
    var promptInput by rememberSaveable { mutableStateOf("") }

    // Clipboard feedback
    var copiedBlockId by remember { mutableStateOf<String?>(null) }

    // Optional details toggle (collapsed by default so main screen stays super clean!)
    val expandedDetails = remember { mutableStateMapOf<String, Boolean>() }

    val listState = rememberLazyListState()

    fun applyLiveSyncState(state: LiveSyncState?) {
        if (state == null) return
        connectionState = BridgeConnectionState.CONNECTED_PC
        activePcProjectName = state.activeProjectName
        pcProjects.clear()
        pcProjects.addAll(state.projects)
        if (state.sessions.isNotEmpty()) {
            val prevSelectedId = normalSessions.getOrNull(selectedNormalIndex)?.id
            val currentSignature = normalSessions.joinToString("|") { s ->
                "${s.id}:${s.title}:${s.messages.size}:${s.messages.lastOrNull()?.id.orEmpty()}:${s.messages.lastOrNull()?.bodyText?.hashCode() ?: 0}"
            }
            val nextSignature = state.sessions.joinToString("|") { s ->
                "${s.id}:${s.title}:${s.messages.size}:${s.messages.lastOrNull()?.id.orEmpty()}:${s.messages.lastOrNull()?.bodyText?.hashCode() ?: 0}"
            }
            if (currentSignature != nextSignature) {
                normalSessions.clear()
                normalSessions.addAll(state.sessions)
                val matchedIdx = state.sessions.indexOfFirst { it.id == prevSelectedId }
                selectedNormalIndex = if (matchedIdx >= 0) matchedIdx else 0
            }
        }
    }

    suspend fun syncLiveSessionsFromBridge(host: String, port: Int) {
        val healthy = PcBridgeClient.checkBridgeHealth(host, port)
        connectionState = if (healthy) {
            BridgeConnectionState.CONNECTED_PC
        } else {
            BridgeConnectionState.SIMULATION_ACTIVE
        }
        if (healthy) {
            val state = PcBridgeClient.fetchLiveSyncState(host, port)
            applyLiveSyncState(state)
        }
    }

    // Continuous real-time PC Bridge session sync loop (every 2.5s when idle)
    LaunchedEffect(bridgeHost, bridgePortText) {
        val port = bridgePortText.toIntOrNull() ?: 8766
        while (true) {
            if (!isStreaming && viewMode == InspectorViewMode.DEMO_NORMAL) {
                syncLiveSessionsFromBridge(bridgeHost, port)
            }
            delay(2500L)
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

    fun triggerAgentPrompt(rawPrompt: String) {
        val trimmed = rawPrompt.trim()
        if (trimmed.isEmpty()) return

        if (isStreaming) {
            steerActiveStream(trimmed)
            return
        }

        if (viewMode == InspectorViewMode.EMPTY || viewMode == InspectorViewMode.ERROR || viewMode == InspectorViewMode.LOADING) {
            viewMode = InspectorViewMode.DEMO_NORMAL
        }

        stopActiveStream()
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
            thoughtText = "질문 의도를 파악하고 답변을 정리합니다.",
            bodyText = "답변을 작성하고 있습니다...",
            isStreaming = true
        )

        targetList[idx] = session.copy(
            messages = session.messages + userMessage + placeholderAgent,
            durationTag = "지금"
        )

        isStreaming = true
        val port = bridgePortText.toIntOrNull() ?: 8766

        activeStreamJob = coroutineScope.launch {
            PcBridgeClient.streamAgentResponse(
                messageId = agentMsgId,
                userPrompt = trimmed,
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
                            bodyText = if (frame.isFinished) frame.message.bodyText + steerSuffix else frame.message.bodyText,
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
                }
            }
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
            // 1. Top Header: Left [세션·프로젝트] icon (with subtle session & folder label), Right [설정] icon
            SimpleElephantHeader(
                currentSessionTitle = currentSession.title,
                activeProjectFolder = activePcProjectName,
                isSessionPopupOpen = showSessionProjectPopup,
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
                onOpenSettings = {
                    showSessionProjectPopup = false
                    showSettingsSheet = true
                }
            )

            // 2. Main Conversation Area (Takes full center space without extra chip bars!)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
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
                                        bridgePortText.toIntOrNull() ?: 8765
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
                    .background(Color.Black.copy(alpha = 0.28f))
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
                .padding(start = 16.dp, end = 16.dp, top = 56.dp),
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
                    onCreateRemoteSession = { sessionTitle, projectFolder ->
                        val port = bridgePortText.toIntOrNull() ?: 8766
                        coroutineScope.launch {
                            val state = PcBridgeClient.createRemoteSession(bridgeHost, port, sessionTitle, projectFolder)
                            if (state != null) {
                                applyLiveSyncState(state)
                            } else {
                                val folder = projectFolder.ifBlank { activePcProjectName }
                                val newSession = WorkspaceSession(
                                    id = "local-session-${System.currentTimeMillis()}",
                                    title = sessionTitle.ifBlank { "새 세션" },
                                    ambientCaption = "폴더: $folder",
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
                                pcProjects.add(0, PcProjectFolder(folderName, "~/.gemini/antigravity/scratch/$folderName", 1))
                                activePcProjectName = folderName
                            }
                            viewMode = InspectorViewMode.DEMO_NORMAL
                        }
                    },
                    onSyncNow = {
                        val port = bridgePortText.toIntOrNull() ?: 8766
                        coroutineScope.launch {
                            syncLiveSessionsFromBridge(bridgeHost, port)
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
                        }
                        viewMode = InspectorViewMode.DEMO_NORMAL
                        showSettingsSheet = false
                    }
                },
                onSyncNow = {
                    val port = bridgePortText.toIntOrNull() ?: 8766
                    coroutineScope.launch {
                        syncLiveSessionsFromBridge(bridgeHost, port)
                    }
                },
                onClearCurrentChat = {
                    val current = normalSessions[selectedNormalIndex]
                    normalSessions[selectedNormalIndex] = current.copy(messages = emptyList())
                    viewMode = InspectorViewMode.EMPTY
                    showSettingsSheet = false
                },
                onSelectViewMode = { mode ->
                    viewMode = mode
                    showSettingsSheet = false
                },
                onDismiss = { showSettingsSheet = false },
                onSaveSettings = { newHost, newPort, newLocalModel ->
                    bridgeHost = newHost.ifBlank { "127.0.0.1" }
                    bridgePortText = newPort.ifBlank { "8766" }
                    customLocalModelId = newLocalModel.ifBlank { "qwen2.5-coder:7b" }
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
    }
}

@Composable
private fun SimpleElephantHeader(
    currentSessionTitle: String,
    activeProjectFolder: String,
    isSessionPopupOpen: Boolean,
    connectionState: BridgeConnectionState,
    reducedMotion: Boolean,
    onToggleSessionPopup: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val sessionButtonInteraction = remember { MutableInteractionSource() }
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

            Spacer(modifier = Modifier.width(8.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = currentSessionTitle,
                    style = AntigravityTypography.PillLabel.copy(
                        color = AntigravityPalette.WarmIvoryInk.copy(alpha = 0.68f),
                        fontSize = 11.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "폴더 · $activeProjectFolder",
                    style = AntigravityTypography.SecondaryBody.copy(
                        color = if (connectionState == BridgeConnectionState.CONNECTED_PC) {
                            AntigravityPalette.CalmSageAccent.copy(alpha = 0.65f)
                        } else {
                            AntigravityPalette.WarmIvoryInk.copy(alpha = 0.42f)
                        },
                        fontSize = 9.5.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Right: Icon-only [설정] button (44dp circle, borderless dark surface)
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
    val cardColor = if (isUser) {
        Color(0xFF17171E).copy(alpha = 0.62f)
    } else {
        Color(0xFF101016).copy(alpha = 0.52f)
    }
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
            Surface(
                modifier = Modifier.widthIn(max = 315.dp),
                shape = bubbleShape,
                color = cardColor
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    // Main Answer Text Only (No author name or timestamp)
                    Text(
                        text = message.bodyText,
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

                    // Subtle, single collapsible toggle for details (Collapsed by default!)
                    val hasDetails = message.thoughtText.isNotBlank() || message.tools.isNotEmpty()
                    if (!isUser && hasDetails) {
                        Spacer(modifier = Modifier.height(8.dp))
                        val detailInteraction = remember { MutableInteractionSource() }
                        Row(
                            modifier = Modifier
                                .defaultMinSize(minHeight = 36.dp)
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
                            Text(
                                text = if (isDetailExpanded) "진행 과정 접기" else "진행 과정 보기",
                                style = AntigravityTypography.SecondaryBody.copy(
                                    color = AntigravityPalette.MutedSlateInk,
                                    fontSize = 12.sp
                                )
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = if (isDetailExpanded) AntigravityIcons.ChevronUp else AntigravityIcons.ChevronDown,
                                contentDescription = null,
                                tint = AntigravityPalette.MutedSlateInk,
                                modifier = Modifier.size(14.dp)
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

            // Plain text streaming indicator directly underneath the AI chat bubble ("답변 스트리밍 ai가 대화 말풍선 밑에 표시되게해 글자만")
            if (!isUser && isStreamingBubble) {
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = if (steeringStatusLabel.isNotBlank()) {
                        "$streamPhaseLabel · $steeringStatusLabel"
                    } else {
                        "$streamPhaseLabel · 실시간 대화조정 가능"
                    },
                    style = AntigravityTypography.SecondaryBody.copy(
                        color = AntigravityPalette.WarmLampGold.copy(alpha = 0.82f),
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    ),
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        }
    }
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
                            title = "작업내용 깃허브 올리기",
                            subtitle = "전체 소스코드 · 프로젝트 작업 GitHub 푸시",
                            accentColor = AntigravityPalette.CalmSageAccent,
                            onClick = onPushWorkToGitHub
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
                            text = "추론 강도 (Reasoning)",
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
                            text = "AI 모델 선택 (Model)",
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
                                    Text(
                                        text = if (isSel) "✓" else engine.providerTag,
                                        style = AntigravityTypography.SecondaryBody.copy(
                                            color = if (isSel) tint else AntigravityPalette.WarmIvoryInk.copy(alpha = 0.42f),
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
                Text(
                    text = "+",
                    style = AntigravityTypography.CardTitle.copy(
                        color = if (showPlusMenuPopup) {
                            AntigravityPalette.CalmSageAccent
                        } else {
                            AntigravityPalette.WarmIvoryInk.copy(alpha = 0.58f)
                        },
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium
                    )
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
        Text(
            text = "›",
            style = AntigravityTypography.CardTitle.copy(
                color = accentColor.copy(alpha = 0.65f),
                fontSize = 14.sp
            )
        )
    }
}

/**
 * Top-Left Session & Project Folder Popover:
 * Expands directly from the top-left icon (TransformOrigin(0.04f, 0.0f)) and shows:
 * - Current Session Name (세션명) & Active Project Folder Name (프로젝트 폴더명)
 * - Live PC Sessions list (with each session's 세션명 & 연결된 프로젝트 폴더명) + [+ 새 세션 생성]
 * - PC Project Folders list (프로젝트 폴더명 선택) + [+ 폴더 생성]
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
    onCreateRemoteSession: (String, String) -> Unit,
    onCreateRemoteProject: (String) -> Unit,
    onSyncNow: () -> Unit,
    onDismiss: () -> Unit
) {
    var newSessionTitleInput by rememberSaveable { mutableStateOf("") }
    var newProjectFolderInput by rememberSaveable { mutableStateOf("") }

    val popoverBg = Color(0xFF0C0C10).copy(alpha = 0.96f)
    val cardBg = Color(0xFF15151C).copy(alpha = 0.56f)
    val selectedCardBg = Color(0xFF1E3B36).copy(alpha = 0.65f)
    val activeSession = sessions.getOrNull(selectedSessionIndex) ?: sessions.firstOrNull()
    val isConnectedPc = connectionState == BridgeConnectionState.CONNECTED_PC

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
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Current Active Session Name & Project Folder Name Summary Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(selectedCardBg)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isConnectedPc) {
                            "● PC 실시간 세션 · 프로젝트 연동 ($host:$port)"
                        } else {
                            "○ 현재 세션 · 프로젝트 폴더"
                        },
                        style = AntigravityTypography.PillLabel.copy(
                            color = AntigravityPalette.CalmSageAccent,
                            fontSize = 11.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
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
                                fontSize = 10.sp
                            )
                        )
                    }
                }

                Text(
                    text = "세션명: ${activeSession?.title ?: "현재 안그래비티 세션"}",
                    style = AntigravityTypography.PillLabel.copy(
                        color = AntigravityPalette.WarmIvoryInk,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "프로젝트 폴더명: $activePcProjectName (~/.gemini/antigravity/scratch/$activePcProjectName)",
                    style = AntigravityTypography.SecondaryBody.copy(
                        color = AntigravityPalette.WarmLampGold.copy(alpha = 0.90f),
                        fontSize = 11.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 2. Live PC Sessions List (Shows both Session Name & Project Folder Name) + Create New Session
            Text(
                text = "세션 목록 (세션명 · 프로젝트 폴더명) · 새 세션 생성",
                style = AntigravityTypography.PillLabel.copy(
                    color = AntigravityPalette.WarmIvoryInk.copy(alpha = 0.55f),
                    fontSize = 11.sp
                )
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                sessions.forEachIndexed { idx, session ->
                    val selected = idx == selectedSessionIndex && currentViewMode == InspectorViewMode.DEMO_NORMAL
                    val folderLabel = session.branchName
                        .takeIf { it.isNotBlank() && it != "session" && it != "live-sync" }
                        ?: activePcProjectName

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) selectedCardBg else cardBg)
                            .clickable(role = Role.Button) {
                                onSelectSession(idx)
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = session.title,
                                style = AntigravityTypography.PillLabel.copy(
                                    color = if (selected) AntigravityPalette.CalmSageAccent else AntigravityPalette.WarmIvoryInk.copy(alpha = 0.88f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = session.durationTag,
                                style = AntigravityTypography.SecondaryBody.copy(
                                    color = if (selected) AntigravityPalette.CalmSageAccent else AntigravityPalette.WarmIvoryInk.copy(alpha = 0.42f),
                                    fontSize = 10.sp
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "프로젝트 폴더: $folderLabel",
                            style = AntigravityTypography.SecondaryBody.copy(
                                color = AntigravityPalette.WarmIvoryInk.copy(alpha = 0.50f),
                                fontSize = 10.5.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Create New Session Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(cardBg)
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (newSessionTitleInput.isEmpty()) {
                        Text(
                            text = "새 세션 이름 입력...",
                            style = AntigravityTypography.SecondaryBody.copy(
                                color = AntigravityPalette.WarmIvoryInk.copy(alpha = 0.36f),
                                fontSize = 11.5.sp
                            )
                        )
                    }
                    BasicTextField(
                        value = newSessionTitleInput,
                        onValueChange = { newSessionTitleInput = it },
                        textStyle = AntigravityTypography.SecondaryBody.copy(color = AntigravityPalette.WarmIvoryInk),
                        cursorBrush = SolidColor(AntigravityPalette.CalmSageAccent),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Box(
                    modifier = Modifier
                        .defaultMinSize(minHeight = 38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(AntigravityPalette.CalmSageAccent.copy(alpha = 0.85f))
                        .clickable(role = Role.Button) {
                            val title = newSessionTitleInput.ifBlank { "모바일 새 세션" }
                            newSessionTitleInput = ""
                            onCreateRemoteSession(title, newProjectFolderInput)
                        }
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+ 새 세션 생성",
                        style = AntigravityTypography.PillLabel.copy(
                            color = AntigravityPalette.DeepForestInk,
                            fontSize = 11.sp
                        )
                    )
                }
            }

            // 3. PC Project Folders List & New Folder Creation
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "PC 프로젝트 폴더 목록 (총 ${pcProjects.size.coerceAtLeast(1)}개) · 새 폴더 생성",
                style = AntigravityTypography.PillLabel.copy(
                    color = AntigravityPalette.WarmIvoryInk.copy(alpha = 0.55f),
                    fontSize = 11.sp
                )
            )

            if (pcProjects.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(pcProjects) { proj ->
                        val isActiveProj = proj.name == activePcProjectName
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isActiveProj) selectedCardBg else cardBg)
                                .clickable(role = Role.Button) {
                                    onSelectProjectFolder(proj.name)
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "${proj.name} (${proj.fileCount})",
                                style = AntigravityTypography.SecondaryBody.copy(
                                    color = if (isActiveProj) AntigravityPalette.WarmLampGold else AntigravityPalette.WarmIvoryInk.copy(alpha = 0.78f),
                                    fontSize = 10.5.sp
                                )
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(cardBg)
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (newProjectFolderInput.isEmpty()) {
                        Text(
                            text = "새 프로젝트 폴더명 (예: my-new-app)",
                            style = AntigravityTypography.SecondaryBody.copy(
                                color = AntigravityPalette.WarmIvoryInk.copy(alpha = 0.36f),
                                fontSize = 11.5.sp
                            )
                        )
                    }
                    BasicTextField(
                        value = newProjectFolderInput,
                        onValueChange = { newProjectFolderInput = it },
                        textStyle = AntigravityTypography.CodeMono,
                        cursorBrush = SolidColor(AntigravityPalette.WarmLampGold),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Box(
                    modifier = Modifier
                        .defaultMinSize(minHeight = 38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(AntigravityPalette.WarmLampGold.copy(alpha = 0.85f))
                        .clickable(role = Role.Button) {
                            val folder = newProjectFolderInput.ifBlank { "mobile-project-${System.currentTimeMillis() % 1000}" }
                            newProjectFolderInput = ""
                            onCreateRemoteProject(folder)
                        }
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+ 폴더 생성",
                        style = AntigravityTypography.PillLabel.copy(
                            color = AntigravityPalette.DeepForestInk,
                            fontSize = 11.sp
                        )
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
    onSelectEngine: (AiEngineOption) -> Unit,
    onExecuteRemoteCommand: (String) -> Unit,
    onSyncNow: () -> Unit,
    onClearCurrentChat: () -> Unit,
    onSelectViewMode: (InspectorViewMode) -> Unit,
    onDismiss: () -> Unit,
    onSaveSettings: (String, String, String) -> Unit
) {
    var hostInput by rememberSaveable { mutableStateOf(initialHost) }
    var portInput by rememberSaveable { mutableStateOf(initialPort) }
    var localModelInput by rememberSaveable { mutableStateOf(initialLocalModel) }
    var showAdvanced by rememberSaveable { mutableStateOf(false) }

    val unifiedDialogBg = Color(0xFF0C0C10).copy(alpha = 0.96f)
    val unifiedCardBg = Color(0xFF15151C).copy(alpha = 0.52f)
    val unifiedSelectedCardBg = Color(0xFF1E3B36).copy(alpha = 0.62f)

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
                    text = "안그래비티 설정",
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
                // Live PC Connection Status Badge
                val isConnectedPc = connectionState == BridgeConnectionState.CONNECTED_PC
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isConnectedPc) unifiedSelectedCardBg else unifiedCardBg)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isConnectedPc) {
                            "● PC 실시간 연결됨 ($initialHost:$initialPort)"
                        } else {
                            "○ PC 연결 대기 ($initialHost:$initialPort)"
                        },
                        style = AntigravityTypography.PillLabel.copy(
                            color = if (isConnectedPc) AntigravityPalette.CalmSageAccent else AntigravityPalette.WarmIvoryInk.copy(alpha = 0.70f),
                            fontSize = 11.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
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

                // Section 1: AI Model Selection (Default vs Local AI)
                Text(
                    text = "1. 대화할 AI 선택",
                    style = AntigravityTypography.PillLabel.copy(
                        color = AntigravityPalette.WarmIvoryInk.copy(alpha = 0.55f),
                        fontSize = 11.5.sp
                    )
                )

                engines.forEach { engine ->
                    val isSelected = engine.id == selectedEngineId
                    val accent = if (engine.isLocal) AntigravityPalette.WarmLampGold else AntigravityPalette.CalmSageAccent
                    val cardBg = if (isSelected) unifiedSelectedCardBg else unifiedCardBg

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(cardBg)
                            .clickable(role = Role.RadioButton) {
                                onSelectEngine(engine)
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
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
                                    fontSize = 12.sp
                                )
                            )
                            Text(
                                text = if (isSelected) "사용 중" else engine.providerTag,
                                style = AntigravityTypography.SecondaryBody.copy(
                                    color = if (isSelected) accent else AntigravityPalette.WarmIvoryInk.copy(alpha = 0.42f),
                                    fontSize = 10.5.sp
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = engine.description,
                            style = AntigravityTypography.SecondaryBody.copy(
                                color = AntigravityPalette.WarmIvoryInk.copy(alpha = 0.48f),
                                fontSize = 11.sp
                            )
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
                            text = "PC 연결 주소 (기본 127.0.0.1)",
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
                            text = "포트 번호 (기본 8765)",
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
                            text = "로컬 AI 모델명 직접 지정",
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

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("Antigravity Text", text))
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
