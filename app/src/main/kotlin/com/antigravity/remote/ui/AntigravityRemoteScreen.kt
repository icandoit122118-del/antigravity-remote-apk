package com.antigravity.remote.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
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

    // AI Engine, Reasoning Effort & Local AI Selection
    val engineOptions = remember { AiEngineCatalog.options }
    var selectedEngineId by rememberSaveable { mutableStateOf(engineOptions.first().id) }
    var selectedReasoning by rememberSaveable { mutableStateOf(ReasoningEffort.MEDIUM) }
    var showModelReasoningPopup by rememberSaveable { mutableStateOf(false) }
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
            normalSessions.clear()
            normalSessions.addAll(state.sessions)
            selectedNormalIndex = 0
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

    // Non-blocking background check & live Antigravity session sync from PC Bridge
    LaunchedEffect(bridgeHost, bridgePortText) {
        val port = bridgePortText.toIntOrNull() ?: 8766
        syncLiveSessionsFromBridge(bridgeHost, port)
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
            // 1. Clean Top Header: Greeting + Active AI Subtitle on left, single [설정] button on right
            SimpleElephantHeader(
                activeEngine = activeEngine,
                connectionState = if (viewMode == InspectorViewMode.ERROR) {
                    BridgeConnectionState.DISCONNECTED_ERROR
                } else {
                    connectionState
                },
                reducedMotion = reducedMotion,
                onOpenSettings = { showSettingsSheet = true }
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

                                ChatMessageCard(
                                    message = message,
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

            // 3. Active Streaming Pill (Only visible WHILE generating an answer; hidden when idle!)
            AnimatedVisibility(
                visible = isStreaming,
                enter = if (reducedMotion) fadeIn(tween(0)) else expandVertically() + fadeIn(),
                exit = if (reducedMotion) fadeOut(tween(0)) else shrinkVertically() + fadeOut()
            ) {
                ActiveStreamBanner(
                    phaseLabel = streamPhaseLabel,
                    steeringStatusLabel = steeringStatusLabel,
                    reducedMotion = reducedMotion,
                    onStopStream = { stopActiveStream() }
                )
            }

            // 4. Clean Bottom Input Bar with Far-Right Model & Reasoning Selector + Mid-Stream Steering
            SimplePromptInputDock(
                quickPrompts = currentSession.quickPrompts,
                promptText = promptInput,
                isStreaming = isStreaming,
                activeEngine = activeEngine,
                engines = engineOptions,
                selectedReasoning = selectedReasoning,
                showModelReasoningPopup = showModelReasoningPopup,
                reducedMotion = reducedMotion,
                onPromptTextChange = { promptInput = it },
                onQuickPromptClick = { quickText ->
                    focusManager.clearFocus()
                    triggerAgentPrompt(quickText)
                },
                onSendClick = {
                    if (promptInput.isNotBlank()) {
                        val toSend = promptInput
                        promptInput = ""
                        focusManager.clearFocus()
                        triggerAgentPrompt(toSend)
                    }
                },
                onStopClick = {
                    stopActiveStream()
                },
                onToggleModelPopup = {
                    showModelReasoningPopup = !showModelReasoningPopup
                },
                onSelectEngine = { engine ->
                    selectedEngineId = engine.id
                },
                onSelectReasoning = { effort ->
                    selectedReasoning = effort
                }
            )
        }

        // Unified Settings Dialog (Houses AI Model Choice, PC Live Session Creation, PC Project Folder Creation, Remote Control)
        if (showSettingsSheet) {
            UnifiedSettingsDialog(
                engines = engineOptions,
                selectedEngineId = selectedEngineId,
                sessions = normalSessions,
                selectedSessionIndex = selectedNormalIndex,
                pcProjects = pcProjects,
                activePcProjectName = activePcProjectName,
                currentViewMode = viewMode,
                initialHost = bridgeHost,
                initialPort = bridgePortText,
                initialLocalModel = customLocalModelId,
                connectionState = connectionState,
                reducedMotion = reducedMotion,
                onSelectEngine = { engine ->
                    selectedEngineId = engine.id
                },
                onSelectSession = { idx ->
                    selectedNormalIndex = idx
                    viewMode = InspectorViewMode.DEMO_NORMAL
                },
                onCreateRemoteSession = { sessionTitle, projectFolder ->
                    val port = bridgePortText.toIntOrNull() ?: 8766
                    coroutineScope.launch {
                        val state = PcBridgeClient.createRemoteSession(bridgeHost, port, sessionTitle, projectFolder)
                        if (state != null) {
                            applyLiveSyncState(state)
                        } else {
                            val newSession = WorkspaceSession(
                                id = "local-session-${System.currentTimeMillis()}",
                                title = sessionTitle.ifBlank { "새 세션" },
                                ambientCaption = if (projectFolder.isNotBlank()) "프로젝트: $projectFolder" else "모바일 생성 세션",
                                branchName = projectFolder.ifBlank { "main" },
                                activeTaskSummary = "모바일에서 생성한 새 세션",
                                durationTag = "방금",
                                quickPrompts = listOf("오늘 해야 할 일을 깔끔하게 정리해줘", "새 프로젝트 구조를 잡아줘"),
                                messages = listOf(
                                    ChatMessage(
                                        id = "init-${System.currentTimeMillis()}",
                                        role = MessageRole.AGENT,
                                        authorName = "안그래비티 도우미",
                                        timestamp = "지금",
                                        bodyText = "새 세션(${sessionTitle.ifBlank { "새 세션" }})을 시작했습니다."
                                    )
                                )
                            )
                            normalSessions.add(0, newSession)
                            selectedNormalIndex = 0
                        }
                        viewMode = InspectorViewMode.DEMO_NORMAL
                        showSettingsSheet = false
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
    activeEngine: AiEngineOption,
    connectionState: BridgeConnectionState,
    reducedMotion: Boolean,
    onOpenSettings: () -> Unit
) {
    val settingsInteraction = remember { MutableInteractionSource() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon-only [설정] button (48dp touch target, borderless dark surface, no text or logo)
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
    isDetailExpanded: Boolean,
    copiedBlockId: String?,
    reducedMotion: Boolean,
    onToggleDetail: () -> Unit,
    onCopyCode: (String) -> Unit
) {
    val isUser = message.role == MessageRole.USER
    val cardColor = if (isUser) AntigravityPalette.NightRaised else AntigravityPalette.NightSurface
    val bubbleShape = if (isUser) {
        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp)
    } else {
        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 6.dp, bottomEnd = 20.dp)
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 310.dp),
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

@Composable
private fun SimplePromptInputDock(
    quickPrompts: List<String>,
    promptText: String,
    isStreaming: Boolean,
    activeEngine: AiEngineOption,
    engines: List<AiEngineOption>,
    selectedReasoning: ReasoningEffort,
    showModelReasoningPopup: Boolean,
    reducedMotion: Boolean,
    onPromptTextChange: (String) -> Unit,
    onQuickPromptClick: (String) -> Unit,
    onSendClick: () -> Unit,
    onStopClick: () -> Unit,
    onToggleModelPopup: () -> Unit,
    onSelectEngine: (AiEngineOption) -> Unit,
    onSelectReasoning: (ReasoningEffort) -> Unit
) {
    val inputInteractionSource = remember { MutableInteractionSource() }
    val isFocused by inputInteractionSource.collectIsFocusedAsState()
    val sendButtonInteraction = remember { MutableInteractionSource() }
    val modelPickerInteraction = remember { MutableInteractionSource() }

    val activePrompts = if (isStreaming) {
        listOf("핵심만 짧게 요약해줘", "코드 예시도 함께 추가해줘", "작업 방향을 단계별로 조정해줘")
    } else {
        quickPrompts
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AntigravityPalette.NightGround)
            .padding(bottom = 8.dp)
    ) {
        // Floating Model & Reasoning Picker Popup (Aligned to the right above the chat dock, like Antigravity Desktop!)
        AnimatedVisibility(
            visible = showModelReasoningPopup,
            enter = if (reducedMotion) fadeIn(tween(0)) else expandVertically(tween(160)) + fadeIn(tween(160)),
            exit = if (reducedMotion) fadeOut(tween(0)) else shrinkVertically(tween(140)) + fadeOut(tween(140))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Surface(
                    modifier = Modifier.widthIn(max = 310.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = AntigravityPalette.NightSurface
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "추론 강도 (Reasoning)",
                            style = AntigravityTypography.SecondaryBody.copy(
                                color = AntigravityPalette.MutedSlateInk,
                                fontSize = 11.5.sp
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
                                        .defaultMinSize(minHeight = 38.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (selected) AntigravityPalette.NightRaised else AntigravityPalette.NightGround)
                                        .clickable(role = Role.RadioButton) {
                                            onSelectReasoning(effort)
                                        }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = effort.label,
                                        style = AntigravityTypography.PillLabel.copy(
                                            color = if (selected) AntigravityPalette.CalmSageAccent else AntigravityPalette.WarmIvoryInk,
                                            fontSize = 12.sp
                                        )
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "AI 모델 선택 (Model)",
                            style = AntigravityTypography.SecondaryBody.copy(
                                color = AntigravityPalette.MutedSlateInk,
                                fontSize = 11.5.sp
                            )
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 210.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            engines.forEach { engine ->
                                val isSel = engine.id == activeEngine.id
                                val tint = if (engine.isLocal) AntigravityPalette.WarmLampGold else AntigravityPalette.CalmSageAccent
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = 40.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isSel) AntigravityPalette.NightRaised else Color.Transparent)
                                        .clickable(role = Role.RadioButton) {
                                            onSelectEngine(engine)
                                            onToggleModelPopup()
                                        }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
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
                                                color = if (isSel) tint else AntigravityPalette.WarmIvoryInk,
                                                fontSize = 12.5.sp
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = selectedReasoning.label,
                                            style = AntigravityTypography.SecondaryBody.copy(
                                                color = AntigravityPalette.MutedSlateInk,
                                                fontSize = 11.sp
                                            )
                                        )
                                    }
                                    Text(
                                        text = if (isSel) "✓" else engine.providerTag,
                                        style = AntigravityTypography.SecondaryBody.copy(
                                            color = if (isSel) tint else AntigravityPalette.MutedSlateInk,
                                            fontSize = 11.sp
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
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(activePrompts) { chipText ->
                    val chipInteraction = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .widthIn(max = 260.dp)
                            .defaultMinSize(minHeight = 42.dp)
                            .tactilePressScale(chipInteraction, reducedMotion)
                            .clip(RoundedCornerShape(22.dp))
                            .background(AntigravityPalette.NightSurface)
                            .clickable(
                                interactionSource = chipInteraction,
                                indication = null,
                                role = Role.Button,
                                onClick = { onQuickPromptClick(chipText) }
                            )
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = if (isStreaming) "조정 · $chipText" else chipText,
                            style = AntigravityTypography.SecondaryBody.copy(
                                color = if (isStreaming) AntigravityPalette.WarmLampGold else AntigravityPalette.WarmIvoryInk,
                                fontSize = 12.5.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Clean Borderless Input Row + Send/Steer Button + Far-Right Model & Reasoning Selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(if (isFocused) AntigravityPalette.NightRaised else AntigravityPalette.NightSurface)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (promptText.isEmpty()) {
                    Text(
                        text = if (isStreaming) "생각·작업 중 추가 대화조정 입력..." else "편하게 물어보세요...",
                        style = AntigravityTypography.SecondaryBody.copy(
                            color = if (isStreaming) AntigravityPalette.WarmLampGold else AntigravityPalette.MutedSlateInk,
                            fontSize = 13.5.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = promptText,
                    onValueChange = onPromptTextChange,
                    interactionSource = inputInteractionSource,
                    textStyle = AntigravityTypography.ChatBody.copy(fontSize = 14.5.sp),
                    cursorBrush = SolidColor(AntigravityPalette.CalmSageAccent),
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSendClick() }),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            val canSend = promptText.isNotBlank()
            val buttonBg = when {
                isStreaming && canSend -> AntigravityPalette.WarmLampGold
                canSend -> AntigravityPalette.CalmSageAccent
                isStreaming -> AntigravityPalette.SoftCoralDanger
                else -> AntigravityPalette.NightRaised
            }
            val iconTint = when {
                isStreaming || canSend -> AntigravityPalette.DeepForestInk
                else -> AntigravityPalette.MutedSlateInk
            }

            Box(
                modifier = Modifier
                    .size(46.dp)
                    .tactilePressScale(sendButtonInteraction, reducedMotion)
                    .clip(CircleShape)
                    .background(buttonBg)
                    .clickable(
                        interactionSource = sendButtonInteraction,
                        indication = null,
                        role = Role.Button,
                        onClick = {
                            if (isStreaming && !canSend) onStopClick() else onSendClick()
                        }
                    )
                    .semantics {
                        contentDescription = if (isStreaming && !canSend) "스트리밍 중지" else "메시지 전송"
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isStreaming && !canSend) AntigravityIcons.StopSquare else AntigravityIcons.SendArrow,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(19.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Far-Right Model & Reasoning Selector Button ("채팅창 맨오른쪽에 모델 및 추론 선택")
            Surface(
                modifier = Modifier
                    .defaultMinSize(minHeight = 46.dp)
                    .tactilePressScale(modelPickerInteraction, reducedMotion)
                    .clickable(
                        interactionSource = modelPickerInteraction,
                        indication = null,
                        role = Role.Button,
                        onClick = onToggleModelPopup
                    )
                    .semantics {
                        contentDescription = "모델 및 추론 선택"
                    },
                shape = RoundedCornerShape(18.dp),
                color = AntigravityPalette.NightSurface
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${activeEngine.shortName.take(8)}·${selectedReasoning.label}",
                        style = AntigravityTypography.PillLabel.copy(
                            color = if (activeEngine.isLocal) AntigravityPalette.WarmLampGold else AntigravityPalette.CalmSageAccent,
                            fontSize = 11.5.sp
                        ),
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Icon(
                        imageVector = if (showModelReasoningPopup) AntigravityIcons.ChevronUp else AntigravityIcons.ChevronDown,
                        contentDescription = null,
                        tint = AntigravityPalette.MistSlateInk,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}

/**
 * Unified Settings Dialog:
 * Houses AI Model Selection (Default AI vs Local AI), Conversation Topic Switching,
 * and a collapsible Advanced section (PC IP/Port + Screen State Tests).
 */
@Composable
private fun UnifiedSettingsDialog(
    engines: List<AiEngineOption>,
    selectedEngineId: String,
    sessions: List<WorkspaceSession>,
    selectedSessionIndex: Int,
    pcProjects: List<PcProjectFolder>,
    activePcProjectName: String,
    currentViewMode: InspectorViewMode,
    initialHost: String,
    initialPort: String,
    initialLocalModel: String,
    connectionState: BridgeConnectionState,
    reducedMotion: Boolean,
    onSelectEngine: (AiEngineOption) -> Unit,
    onSelectSession: (Int) -> Unit,
    onCreateRemoteSession: (String, String) -> Unit,
    onCreateRemoteProject: (String) -> Unit,
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
    var newSessionTitleInput by rememberSaveable { mutableStateOf("") }
    var newProjectFolderInput by rememberSaveable { mutableStateOf("") }
    var showAdvanced by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AntigravityPalette.NightSurface,
        titleContentColor = AntigravityPalette.WarmIvoryInk,
        textContentColor = AntigravityPalette.MistSlateInk,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = AntigravityIcons.SettingsSliders,
                    contentDescription = null,
                    tint = AntigravityPalette.CalmSageAccent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "안그래비티 설정",
                    style = AntigravityTypography.CardTitle
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Section 1: AI Model Selection (Default vs Local AI)
                Text(
                    text = "1. 대화할 AI 선택",
                    style = AntigravityTypography.PillLabel.copy(color = AntigravityPalette.CalmSageAccent)
                )

                engines.forEach { engine ->
                    val isSelected = engine.id == selectedEngineId
                    val accent = if (engine.isLocal) AntigravityPalette.WarmLampGold else AntigravityPalette.CalmSageAccent
                    val cardBg = if (isSelected) AntigravityPalette.NightRaised else AntigravityPalette.NightGround

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(cardBg)
                            .clickable(role = Role.RadioButton) {
                                onSelectEngine(engine)
                            }
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = engine.label,
                                style = AntigravityTypography.PillLabel.copy(
                                    color = if (isSelected) accent else AntigravityPalette.WarmIvoryInk,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                            Text(
                                text = if (isSelected) "사용 중" else engine.providerTag,
                                style = AntigravityTypography.SecondaryBody.copy(
                                    color = if (isSelected) accent else AntigravityPalette.MutedSlateInk,
                                    fontSize = 11.5.sp
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = engine.description,
                            style = AntigravityTypography.SecondaryBody.copy(fontSize = 12.sp)
                        )
                    }
                }

                // Section 2: PC Live Session Sync & New Session Creation
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "2. PC 실시간 세션 연동 · 새 세션 생성",
                        style = AntigravityTypography.PillLabel.copy(color = AntigravityPalette.CalmSageAccent)
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(AntigravityPalette.NightGround)
                            .clickable(role = Role.Button, onClick = onSyncNow)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "실시간 동기화",
                            style = AntigravityTypography.SecondaryBody.copy(
                                color = AntigravityPalette.CalmSageAccent,
                                fontSize = 11.5.sp
                            )
                        )
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    sessions.forEachIndexed { idx, session ->
                        val selected = idx == selectedSessionIndex && currentViewMode == InspectorViewMode.DEMO_NORMAL
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) AntigravityPalette.CalmSageDim else AntigravityPalette.NightGround)
                                .clickable(role = Role.Button) {
                                    onSelectSession(idx)
                                    onDismiss()
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = session.title,
                                style = AntigravityTypography.PillLabel.copy(
                                    color = if (selected) AntigravityPalette.CalmSageAccent else AntigravityPalette.WarmIvoryInk,
                                    fontSize = 12.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = session.durationTag,
                                style = AntigravityTypography.SecondaryBody.copy(
                                    color = AntigravityPalette.MutedSlateInk,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                }

                // Create New Session on PC from Mobile
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(AntigravityPalette.NightGround)
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (newSessionTitleInput.isEmpty()) {
                            Text(
                                text = "새 세션 이름 입력...",
                                style = AntigravityTypography.SecondaryBody.copy(
                                    color = AntigravityPalette.MutedSlateInk,
                                    fontSize = 12.sp
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
                            .defaultMinSize(minHeight = 44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(AntigravityPalette.CalmSageAccent)
                            .clickable(role = Role.Button) {
                                val title = newSessionTitleInput.ifBlank { "모바일 새 세션" }
                                newSessionTitleInput = ""
                                onCreateRemoteSession(title, newProjectFolderInput)
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "+ 새 세션 생성",
                            style = AntigravityTypography.PillLabel.copy(
                                color = AntigravityPalette.DeepForestInk,
                                fontSize = 12.sp
                            )
                        )
                    }
                }

                // Section 3: PC Project Folder Creation & PC Remote Control
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "3. PC 프로젝트 폴더 생성 · 리모트 컨트롤",
                    style = AntigravityTypography.PillLabel.copy(color = AntigravityPalette.WarmLampGold)
                )

                Text(
                    text = "활성 폴더: ~/.gemini/antigravity/scratch/$activePcProjectName (총 ${pcProjects.size.coerceAtLeast(1)}개)",
                    style = AntigravityTypography.SecondaryBody.copy(fontSize = 11.5.sp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(AntigravityPalette.NightGround)
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (newProjectFolderInput.isEmpty()) {
                            Text(
                                text = "새 프로젝트 폴더명 (예: my-new-app)",
                                style = AntigravityTypography.SecondaryBody.copy(
                                    color = AntigravityPalette.MutedSlateInk,
                                    fontSize = 12.sp
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
                            .defaultMinSize(minHeight = 44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(AntigravityPalette.WarmLampGold)
                            .clickable(role = Role.Button) {
                                val folder = newProjectFolderInput.ifBlank { "mobile-project-${System.currentTimeMillis() % 1000}" }
                                newProjectFolderInput = ""
                                onCreateRemoteProject(folder)
                                onDismiss()
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "+ 폴더 생성",
                            style = AntigravityTypography.PillLabel.copy(
                                color = AntigravityPalette.DeepForestInk,
                                fontSize = 12.sp
                            )
                        )
                    }
                }

                // Quick PC Remote Control Actions
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
                                .defaultMinSize(minHeight = 42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(AntigravityPalette.NightGround)
                                .clickable(role = Role.Button) {
                                    onExecuteRemoteCommand(cmdKey)
                                }
                                .padding(horizontal = 6.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = cmdLabel,
                                style = AntigravityTypography.SecondaryBody.copy(
                                    color = AntigravityPalette.WarmIvoryInk,
                                    fontSize = 11.5.sp
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
                        .background(AntigravityPalette.NightGround)
                        .clickable(role = Role.Button, onClick = onClearCurrentChat)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "현재 대화 내용 비우기 (새 대화 시작)",
                        style = AntigravityTypography.SecondaryBody.copy(color = AntigravityPalette.WarmIvoryInk)
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
