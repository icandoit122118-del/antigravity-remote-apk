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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
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
import com.antigravity.remote.model.AntigravityFixtures
import com.antigravity.remote.model.BridgeConnectionState
import com.antigravity.remote.model.ChatMessage
import com.antigravity.remote.model.InspectorViewMode
import com.antigravity.remote.model.MessageRole
import com.antigravity.remote.model.ToolExecutionItem
import com.antigravity.remote.model.ToolRunStatus
import com.antigravity.remote.model.WorkspaceSession
import com.antigravity.remote.network.PcBridgeClient
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

    // Inspector & Data State mode (Normal Demo, Break-UI Worst-Case, Loading, Empty, Error)
    var viewMode by rememberSaveable { mutableStateOf(InspectorViewMode.DEMO_NORMAL) }

    // Bridge Connection settings (127.0.0.1 works over USB adb reverse; 10.0.2.2 fallback for emulator)
    var bridgeHost by rememberSaveable { mutableStateOf("127.0.0.1") }
    var bridgePortText by rememberSaveable { mutableStateOf("8765") }
    var connectionState by remember { mutableStateOf(BridgeConnectionState.SIMULATION_ACTIVE) }
    var showBridgeDialog by rememberSaveable { mutableStateOf(false) }

    // Mutable session states for Normal and Worst-case modes
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

    // Streaming Engine State
    var activeStreamJob by remember { mutableStateOf<Job?>(null) }
    var isStreaming by remember { mutableStateOf(false) }
    var streamPhaseLabel by remember { mutableStateOf("대기 중 · 코끼리 나이트 세이지 모드") }
    var liveTokensPerSec by remember { mutableDoubleStateOf(46.4) }
    var liveElapsedSec by remember { mutableDoubleStateOf(2.1) }

    // Prompt Input
    var promptInput by rememberSaveable { mutableStateOf("") }

    // Clipboard feedback
    var copiedBlockId by remember { mutableStateOf<String?>(null) }

    // Expandable Thought & Tool cards state
    val expandedThoughts = remember { mutableStateMapOf<String, Boolean>() }
    val expandedTools = remember { mutableStateMapOf<String, Boolean>() }

    val listState = rememberLazyListState()

    // Initial non-blocking probe of PC Bridge
    LaunchedEffect(Unit) {
        val port = bridgePortText.toIntOrNull() ?: 8765
        val healthy = PcBridgeClient.checkBridgeHealth(bridgeHost, port)
        connectionState = if (healthy) {
            BridgeConnectionState.CONNECTED_PC
        } else {
            BridgeConnectionState.SIMULATION_ACTIVE
        }
    }

    fun stopActiveStream() {
        activeStreamJob?.cancel()
        activeStreamJob = null
        isStreaming = false
        streamPhaseLabel = "스트리밍 일시 중지됨"
    }

    fun triggerAgentPrompt(rawPrompt: String) {
        val trimmed = rawPrompt.trim()
        if (trimmed.isEmpty()) return

        // If user submits from Empty or Error state, switch smoothly to Normal state
        if (viewMode == InspectorViewMode.EMPTY || viewMode == InspectorViewMode.ERROR || viewMode == InspectorViewMode.LOADING) {
            viewMode = InspectorViewMode.DEMO_NORMAL
        }

        stopActiveStream()
        val timeFmt = SimpleDateFormat("a h:mm", Locale.KOREAN).format(Date())
        val userMsgId = "user-${System.currentTimeMillis()}"
        val agentMsgId = "agent-${System.currentTimeMillis() + 1}"

        val targetList = if (viewMode == InspectorViewMode.WORST_CASE) worstCaseSessions else normalSessions
        val idx = if (viewMode == InspectorViewMode.WORST_CASE) selectedWorstIndex else selectedNormalIndex
        val session = targetList[idx]

        val userMessage = ChatMessage(
            id = userMsgId,
            role = MessageRole.USER,
            authorName = "나 (모바일 리모트)",
            timestamp = timeFmt,
            bodyText = trimmed
        )
        val placeholderAgent = ChatMessage(
            id = agentMsgId,
            role = MessageRole.AGENT,
            authorName = "Antigravity Agent",
            timestamp = timeFmt,
            thoughtText = "요청 의도를 분석하고 있습니다...",
            bodyText = "...",
            isStreaming = true
        )

        expandedThoughts[agentMsgId] = true
        expandedTools[agentMsgId] = true

        targetList[idx] = session.copy(
            messages = session.messages + userMessage + placeholderAgent,
            durationTag = "방금 전"
        )

        isStreaming = true
        val port = bridgePortText.toIntOrNull() ?: 8765

        activeStreamJob = coroutineScope.launch {
            PcBridgeClient.streamAgentResponse(
                messageId = agentMsgId,
                userPrompt = trimmed,
                sessionId = session.id,
                host = bridgeHost,
                port = port,
                timestamp = timeFmt
            ).collect { frame ->
                streamPhaseLabel = frame.phaseLabel
                liveTokensPerSec = frame.tokensPerSec
                liveElapsedSec = frame.elapsedSeconds
                if (frame.fromLiveBridge) {
                    connectionState = BridgeConnectionState.CONNECTED_PC
                }

                val latestSession = targetList[idx]
                val updatedMessages = latestSession.messages.map { existing ->
                    if (existing.id == agentMsgId) frame.message else existing
                }
                targetList[idx] = latestSession.copy(messages = updatedMessages)

                if (frame.isFinished) {
                    isStreaming = false
                    streamPhaseLabel = "응답 완료 · ${frame.message.tokenCount} tok"
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
        // Elephant-style subtle ambient night horizon glow at the top of the screen
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp)
        ) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        AntigravityPalette.CalmSageAccent.copy(alpha = 0.11f),
                        AntigravityPalette.WarmLampGold.copy(alpha = 0.04f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.82f, size.height * 0.18f),
                    radius = size.width * 0.72f
                ),
                radius = size.width * 0.72f,
                center = Offset(size.width * 0.82f, size.height * 0.18f)
            )
        }

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 1. Calm Elephant Night Header & Bridge Status Pill
            ElephantNightHeader(
                connectionState = if (viewMode == InspectorViewMode.ERROR) {
                    BridgeConnectionState.DISCONNECTED_ERROR
                } else {
                    connectionState
                },
                bridgeEndpoint = "$bridgeHost:$bridgePortText",
                reducedMotion = reducedMotion,
                onOpenBridgeSettings = { showBridgeDialog = true }
            )

            // 2. D0-D5 State & Break-UI Mode Switcher Strip
            InspectorModeStrip(
                currentMode = viewMode,
                reducedMotion = reducedMotion,
                onSelectMode = { selected ->
                    viewMode = selected
                }
            )

            // 3. Elephant-Style Rounded 20dp Workspace Session Carousel
            WorkspaceSessionCarousel(
                sessions = activeSessions,
                selectedIndex = activeSessionIndex,
                reducedMotion = reducedMotion,
                onSelectSession = { idx ->
                    if (viewMode == InspectorViewMode.WORST_CASE) {
                        selectedWorstIndex = idx
                    } else {
                        selectedNormalIndex = idx
                    }
                }
            )

            // 4. Main Content Area (Handles Normal, Worst-Case, Loading, Empty, Error States)
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
                            endpoint = "$bridgeHost:$bridgePortText",
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
                            onOpenSettings = { showBridgeDialog = true }
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
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(
                                items = currentSession.messages,
                                key = { it.id }
                            ) { message ->
                                val isThoughtExpanded = expandedThoughts[message.id] ?: false
                                val isToolsExpanded = expandedTools[message.id] ?: true

                                ChatMessageCard(
                                    message = message,
                                    isThoughtExpanded = isThoughtExpanded,
                                    isToolsExpanded = isToolsExpanded,
                                    copiedBlockId = copiedBlockId,
                                    reducedMotion = reducedMotion,
                                    onToggleThought = {
                                        expandedThoughts[message.id] = !isThoughtExpanded
                                    },
                                    onToggleTools = {
                                        expandedTools[message.id] = !isToolsExpanded
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

            // 5. Elephant-Style Floating Live Stream Mini-Bar (Audio-Player Inspired)
            ElephantFloatingStreamBar(
                isStreaming = isStreaming,
                phaseLabel = streamPhaseLabel,
                tokensPerSec = liveTokensPerSec,
                elapsedSeconds = liveElapsedSec,
                reducedMotion = reducedMotion,
                onToggleStream = {
                    if (isStreaming) {
                        stopActiveStream()
                    } else {
                        triggerAgentPrompt("실시간 토큰 스트리밍 및 빌드 상태 브리핑 실행해줘")
                    }
                }
            )

            // 6. Bottom Prompt Dock with Quick Chips & 48x48dp Send Action
            PromptInputDock(
                quickPrompts = currentSession.quickPrompts,
                promptText = promptInput,
                isStreaming = isStreaming,
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
                }
            )
        }

        if (showBridgeDialog) {
            PcBridgeSettingsDialog(
                initialHost = bridgeHost,
                initialPort = bridgePortText,
                connectionState = connectionState,
                onDismiss = { showBridgeDialog = false },
                onSaveAndTest = { newHost, newPort ->
                    bridgeHost = newHost.ifBlank { "10.0.2.2" }
                    bridgePortText = newPort.ifBlank { "8765" }
                    coroutineScope.launch {
                        connectionState = BridgeConnectionState.CONNECTING
                        val ok = PcBridgeClient.checkBridgeHealth(
                            bridgeHost,
                            bridgePortText.toIntOrNull() ?: 8765
                        )
                        connectionState = if (ok) {
                            BridgeConnectionState.CONNECTED_PC
                        } else {
                            BridgeConnectionState.SIMULATION_ACTIVE
                        }
                        showBridgeDialog = false
                    }
                }
            )
        }
    }
}

@Composable
private fun ElephantNightHeader(
    connectionState: BridgeConnectionState,
    bridgeEndpoint: String,
    reducedMotion: Boolean,
    onOpenBridgeSettings: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rounded 14dp Night Emblem
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(AntigravityPalette.NightSurface)
                    .border(1.dp, AntigravityPalette.NightRule, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = AntigravityIcons.OrbitDelta,
                    contentDescription = "Antigravity 로고",
                    tint = AntigravityPalette.CalmSageAccent,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = AntigravityIcons.NightCrescent,
                        contentDescription = null,
                        tint = AntigravityPalette.WarmLampGold,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "코끼리 나이트 에디션 · 리모트",
                        style = AntigravityTypography.SectionHeader.copy(
                            color = AntigravityPalette.WarmLampGold,
                            fontSize = 12.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "고요한 밤, 안그래비티",
                    style = AntigravityTypography.GreetingDisplay,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Connection Status Pill (48dp min touch target height)
        val dotColor = when (connectionState) {
            BridgeConnectionState.CONNECTED_PC -> AntigravityPalette.CalmSageAccent
            BridgeConnectionState.SIMULATION_ACTIVE -> AntigravityPalette.WarmLampGold
            BridgeConnectionState.CONNECTING -> AntigravityPalette.MistSlateInk
            BridgeConnectionState.DISCONNECTED_ERROR -> AntigravityPalette.SoftCoralDanger
        }

        Surface(
            modifier = Modifier
                .defaultMinSize(minHeight = 48.dp)
                .tactilePressScale(interactionSource, reducedMotion)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    onClick = onOpenBridgeSettings
                )
                .semantics {
                    contentDescription = "PC 브릿지 연결 설정 열기. 현재 상태: ${connectionState.badgeLabel}"
                },
            shape = RoundedCornerShape(24.dp),
            color = AntigravityPalette.NightSurface,
            border = BorderStroke(1.dp, AntigravityPalette.NightRule)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
                Spacer(modifier = Modifier.width(7.dp))
                Column {
                    Text(
                        text = connectionState.badgeLabel,
                        style = AntigravityTypography.PillLabel,
                        maxLines = 1
                    )
                    Text(
                        text = bridgeEndpoint,
                        style = AntigravityTypography.TabularMetric.copy(fontSize = 10.5.sp),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun InspectorModeStrip(
    currentMode: InspectorViewMode,
    reducedMotion: Boolean,
    onSelectMode: (InspectorViewMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        InspectorViewMode.entries.forEach { mode ->
            val selected = mode == currentMode
            val interactionSource = remember { MutableInteractionSource() }
            val bgColor = if (selected) AntigravityPalette.CalmSageDim else AntigravityPalette.NightSurface
            val borderColor = if (selected) AntigravityPalette.CalmSageAccent else AntigravityPalette.NightRule
            val textColor = if (selected) AntigravityPalette.CalmSageAccent else AntigravityPalette.MistSlateInk

            Box(
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .tactilePressScale(interactionSource, reducedMotion)
                    .clip(RoundedCornerShape(16.dp))
                    .background(bgColor)
                    .border(1.dp, borderColor, RoundedCornerShape(16.dp))
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        role = Role.Tab,
                        onClick = { onSelectMode(mode) }
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = mode.label,
                    style = AntigravityTypography.PillLabel.copy(
                        color = textColor,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                    ),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun WorkspaceSessionCarousel(
    sessions: List<WorkspaceSession>,
    selectedIndex: Int,
    reducedMotion: Boolean,
    onSelectSession: (Int) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(sessions.size) { index ->
            val session = sessions[index]
            val isSelected = index == selectedIndex
            val interactionSource = remember { MutableInteractionSource() }

            Surface(
                modifier = Modifier
                    .width(276.dp)
                    .defaultMinSize(minHeight = 96.dp)
                    .tactilePressScale(interactionSource, reducedMotion)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        role = Role.Button,
                        onClick = { onSelectSession(index) }
                    ),
                shape = RoundedCornerShape(20.dp),
                color = if (isSelected) AntigravityPalette.NightRaised else AntigravityPalette.NightSurface,
                border = BorderStroke(
                    width = if (isSelected) 1.5.dp else 1.dp,
                    color = if (isSelected) AntigravityPalette.CalmSageAccent else AntigravityPalette.NightRule
                )
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = session.title,
                            style = AntigravityTypography.CardTitle.copy(fontSize = 15.sp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(AntigravityPalette.NightGround)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = session.durationTag,
                                style = AntigravityTypography.TabularMetric.copy(
                                    color = if (isSelected) AntigravityPalette.CalmSageAccent else AntigravityPalette.MutedSlateInk,
                                    fontSize = 11.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = session.ambientCaption,
                        style = AntigravityTypography.SecondaryBody.copy(
                            color = AntigravityPalette.WarmLampGold.copy(alpha = 0.9f),
                            fontSize = 12.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = session.activeTaskSummary,
                        style = AntigravityTypography.SecondaryBody.copy(fontSize = 12.5.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatMessageCard(
    message: ChatMessage,
    isThoughtExpanded: Boolean,
    isToolsExpanded: Boolean,
    copiedBlockId: String?,
    reducedMotion: Boolean,
    onToggleThought: () -> Unit,
    onToggleTools: () -> Unit,
    onCopyCode: (String) -> Unit
) {
    val isUser = message.role == MessageRole.USER
    val cardColor = if (isUser) AntigravityPalette.NightRaised else AntigravityPalette.NightSurface
    val borderColor = if (message.isStreaming) {
        AntigravityPalette.CalmSageAccent.copy(alpha = 0.75f)
    } else {
        AntigravityPalette.NightRule
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = cardColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Author & Timestamp Header
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
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(
                                if (isUser) AntigravityPalette.CalmSageDim else AntigravityPalette.WarmLampDim
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isUser) AntigravityIcons.NightCrescent else AntigravityIcons.OrbitDelta,
                            contentDescription = null,
                            tint = if (isUser) AntigravityPalette.CalmSageAccent else AntigravityPalette.WarmLampGold,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = message.authorName,
                        style = AntigravityTypography.SectionHeader.copy(
                            color = AntigravityPalette.WarmIvoryInk,
                            fontWeight = FontWeight.SemiBold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = message.timestamp,
                    style = AntigravityTypography.TabularMetric,
                    maxLines = 1
                )
            }

            // Collapsible Thought Process Card (Agent only)
            if (message.thoughtText.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                ThoughtProcessAccordion(
                    thoughtText = message.thoughtText,
                    durationLabel = message.thoughtDurationSec,
                    isExpanded = isThoughtExpanded,
                    isStreaming = message.isStreaming,
                    reducedMotion = reducedMotion,
                    onToggle = onToggleThought
                )
            }

            // Collapsible Tool Execution Cards (실행됨 명령/작업 N개)
            if (message.tools.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                ToolExecutionAccordion(
                    tools = message.tools,
                    isExpanded = isToolsExpanded,
                    reducedMotion = reducedMotion,
                    onToggle = onToggleTools
                )
            }

            // Main Message Body
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = message.bodyText,
                style = AntigravityTypography.ChatBody
            )

            // Optional Code / Terminal Block with Horizontal Scroll & 48dp Copy Button
            if (!message.codeBlockContent.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                CodeSnippetCard(
                    language = message.codeBlockLanguage ?: "code",
                    code = message.codeBlockContent,
                    isCopied = copiedBlockId == message.id,
                    reducedMotion = reducedMotion,
                    onCopy = { onCopyCode(message.codeBlockContent) }
                )
            }

            // Token Metrics Footer for Agent Messages
            if (!isUser && message.tokenCount > 0) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${String.format(Locale.US, "%,d", message.tokenCount)} tok · ${
                            String.format(Locale.US, "%.1f", message.tokensPerSec)
                        } tok/s",
                        style = AntigravityTypography.TabularMetric.copy(
                            color = AntigravityPalette.MutedSlateInk
                        ),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun ThoughtProcessAccordion(
    thoughtText: String,
    durationLabel: String,
    isExpanded: Boolean,
    isStreaming: Boolean,
    reducedMotion: Boolean,
    onToggle: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AntigravityPalette.NightGround)
            .border(1.dp, AntigravityPalette.NightRuleSubtle, RoundedCornerShape(16.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .tactilePressScale(interactionSource, reducedMotion)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    onClick = onToggle
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = AntigravityIcons.ThoughtLamp,
                    contentDescription = null,
                    tint = AntigravityPalette.WarmLampGold,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isStreaming) "생각 과정 스트리밍 중..." else "생각 과정 요약",
                    style = AntigravityTypography.PillLabel.copy(
                        color = AntigravityPalette.WarmLampGold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = durationLabel,
                    style = AntigravityTypography.TabularMetric,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = if (isExpanded) AntigravityIcons.ChevronUp else AntigravityIcons.ChevronDown,
                    contentDescription = if (isExpanded) "생각 과정 접기" else "생각 과정 펼치기",
                    tint = AntigravityPalette.MistSlateInk,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = if (reducedMotion) fadeIn(tween(0)) else expandVertically(tween(200, easing = FastOutSlowInEasing)) + fadeIn(tween(180)),
            exit = if (reducedMotion) fadeOut(tween(0)) else shrinkVertically(tween(180, easing = FastOutSlowInEasing)) + fadeOut(tween(140))
        ) {
            Text(
                text = thoughtText,
                style = AntigravityTypography.SecondaryBody.copy(
                    color = AntigravityPalette.MistSlateInk,
                    lineHeight = 20.sp
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
            )
        }
    }
}

@Composable
private fun ToolExecutionAccordion(
    tools: List<ToolExecutionItem>,
    isExpanded: Boolean,
    reducedMotion: Boolean,
    onToggle: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AntigravityPalette.NightCodeWell)
            .border(1.dp, AntigravityPalette.NightRule, RoundedCornerShape(16.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .tactilePressScale(interactionSource, reducedMotion)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    onClick = onToggle
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = AntigravityIcons.TerminalBox,
                    contentDescription = null,
                    tint = AntigravityPalette.CalmSageAccent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "실행됨 명령/작업 ${tools.size}개",
                    style = AntigravityTypography.PillLabel.copy(
                        color = AntigravityPalette.CalmSageAccent
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = if (isExpanded) AntigravityIcons.ChevronUp else AntigravityIcons.ChevronDown,
                contentDescription = if (isExpanded) "도구 내역 접기" else "도구 내역 펼치기",
                tint = AntigravityPalette.MistSlateInk,
                modifier = Modifier.size(16.dp)
            )
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = if (reducedMotion) fadeIn(tween(0)) else expandVertically(tween(200, easing = FastOutSlowInEasing)),
            exit = if (reducedMotion) fadeOut(tween(0)) else shrinkVertically(tween(180, easing = FastOutSlowInEasing))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tools.forEach { tool ->
                    val statusColor = when (tool.status) {
                        ToolRunStatus.COMPLETED -> AntigravityPalette.CalmSageAccent
                        ToolRunStatus.RUNNING -> AntigravityPalette.WarmLampGold
                        ToolRunStatus.FAILED -> AntigravityPalette.SoftCoralDanger
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(AntigravityPalette.NightSurface)
                            .border(1.dp, AntigravityPalette.NightRuleSubtle, RoundedCornerShape(12.dp))
                            .padding(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = tool.title,
                                style = AntigravityTypography.PillLabel,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${tool.status.label} · ${tool.durationMs}ms",
                                style = AntigravityTypography.TabularMetric.copy(
                                    color = statusColor,
                                    fontSize = 11.sp
                                ),
                                maxLines = 1
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = tool.commandOrPath,
                            style = AntigravityTypography.CodeMono.copy(
                                color = AntigravityPalette.MistSlateInk,
                                fontSize = 11.5.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (tool.outputPreview.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .background(AntigravityPalette.NightCodeWell, RoundedCornerShape(8.dp))
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = tool.outputPreview,
                                    style = AntigravityTypography.CodeMono.copy(fontSize = 11.5.sp)
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
            .border(1.dp, AntigravityPalette.NightRule, RoundedCornerShape(16.dp))
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

            // 48x48dp accessible copy button
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
                    contentDescription = "코드 복사",
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

/**
 * Elephant Meditation Audio Mini-Player Inspired Live Stream Bar.
 * Shows pulsing/steady lamp indicator, current phase, tabular token speed, and 48x48dp Stop/Play control.
 */
@Composable
private fun ElephantFloatingStreamBar(
    isStreaming: Boolean,
    phaseLabel: String,
    tokensPerSec: Double,
    elapsedSeconds: Double,
    reducedMotion: Boolean,
    onToggleStream: () -> Unit
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
    val effectiveAlpha = if (isStreaming && !reducedMotion) pulseAlpha else 1f

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        color = AntigravityPalette.NightRaised,
        border = BorderStroke(
            1.dp,
            if (isStreaming) AntigravityPalette.WarmLampGold.copy(alpha = 0.65f) else AntigravityPalette.NightRule
        )
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
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(
                            if (isStreaming) {
                                AntigravityPalette.WarmLampGold.copy(alpha = effectiveAlpha)
                            } else {
                                AntigravityPalette.CalmSageAccent
                            }
                        )
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = phaseLabel,
                        style = AntigravityTypography.PillLabel.copy(
                            color = AntigravityPalette.WarmIvoryInk
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = String.format(
                            Locale.US,
                            "%.1f tok/s · %04.1fs",
                            tokensPerSec,
                            elapsedSeconds
                        ),
                        style = AntigravityTypography.TabularMetric.copy(
                            color = if (isStreaming) AntigravityPalette.WarmLampGold else AntigravityPalette.MistSlateInk,
                            fontSize = 11.5.sp
                        ),
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 48x48dp Play/Stop button
            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .tactilePressScale(interactionSource, reducedMotion)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (isStreaming) AntigravityPalette.SoftCoralDim else AntigravityPalette.CalmSageDim
                    )
                    .border(
                        1.dp,
                        if (isStreaming) AntigravityPalette.SoftCoralDanger else AntigravityPalette.CalmSageAccent,
                        RoundedCornerShape(14.dp)
                    )
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        role = Role.Button,
                        onClick = onToggleStream
                    )
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isStreaming) AntigravityIcons.StopSquare else AntigravityIcons.PlayTriangle,
                        contentDescription = if (isStreaming) "스트리밍 중지" else "데모 스트리밍 재생",
                        tint = if (isStreaming) AntigravityPalette.SoftCoralDanger else AntigravityPalette.CalmSageAccent,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (isStreaming) "중지" else "스트림",
                        style = AntigravityTypography.PillLabel.copy(
                            color = if (isStreaming) AntigravityPalette.SoftCoralDanger else AntigravityPalette.CalmSageAccent
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun PromptInputDock(
    quickPrompts: List<String>,
    promptText: String,
    isStreaming: Boolean,
    reducedMotion: Boolean,
    onPromptTextChange: (String) -> Unit,
    onQuickPromptClick: (String) -> Unit,
    onSendClick: () -> Unit,
    onStopClick: () -> Unit
) {
    val inputInteractionSource = remember { MutableInteractionSource() }
    val isFocused by inputInteractionSource.collectIsFocusedAsState()
    val sendButtonInteraction = remember { MutableInteractionSource() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AntigravityPalette.NightGround)
            .padding(bottom = 8.dp)
    ) {
        // Quick Prompt Chips Row (48dp touch height)
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(quickPrompts) { chipText ->
                val chipInteraction = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .defaultMinSize(minHeight = 48.dp)
                        .tactilePressScale(chipInteraction, reducedMotion)
                        .clip(RoundedCornerShape(16.dp))
                        .background(AntigravityPalette.NightSurface)
                        .border(1.dp, AntigravityPalette.NightRule, RoundedCornerShape(16.dp))
                        .clickable(
                            interactionSource = chipInteraction,
                            indication = null,
                            role = Role.Button,
                            onClick = { onQuickPromptClick(chipText) }
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = chipText,
                        style = AntigravityTypography.SecondaryBody.copy(
                            color = AntigravityPalette.WarmIvoryInk,
                            fontSize = 12.5.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Input Box + 48x48dp Send/Stop Button
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
                    .clip(RoundedCornerShape(20.dp))
                    .background(AntigravityPalette.NightSurface)
                    .border(
                        width = if (isFocused) 1.5.dp else 1.dp,
                        color = if (isFocused) AntigravityPalette.CalmSageAccent else AntigravityPalette.NightRule,
                        shape = RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (promptText.isEmpty()) {
                    Text(
                        text = "안그래비티에게 원격 명령이나 질문을 남겨보세요...",
                        style = AntigravityTypography.SecondaryBody.copy(
                            color = AntigravityPalette.MutedSlateInk
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

            Spacer(modifier = Modifier.width(10.dp))

            val canSend = promptText.isNotBlank()
            val buttonBg = when {
                isStreaming -> AntigravityPalette.SoftCoralDanger
                canSend -> AntigravityPalette.CalmSageAccent
                else -> AntigravityPalette.NightRaised
            }
            val iconTint = when {
                isStreaming || canSend -> AntigravityPalette.DeepForestInk
                else -> AntigravityPalette.MutedSlateInk
            }

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .tactilePressScale(sendButtonInteraction, reducedMotion)
                    .clip(RoundedCornerShape(18.dp))
                    .background(buttonBg)
                    .border(1.dp, AntigravityPalette.NightRule, RoundedCornerShape(18.dp))
                    .clickable(
                        interactionSource = sendButtonInteraction,
                        indication = null,
                        role = Role.Button,
                        onClick = {
                            if (isStreaming) onStopClick() else onSendClick()
                        }
                    )
                    .semantics {
                        contentDescription = if (isStreaming) "스트리밍 중지" else "메시지 전송"
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isStreaming) AntigravityIcons.StopSquare else AntigravityIcons.SendArrow,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
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
            text = "PC 브릿지 워크스페이스 동기화 중...",
            style = AntigravityTypography.SectionHeader.copy(color = AntigravityPalette.CalmSageAccent)
        )
        repeat(3) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = AntigravityPalette.NightSurface.copy(alpha = effectiveAlpha),
                border = BorderStroke(1.dp, AntigravityPalette.NightRule)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Box(
                        modifier = Modifier
                            .width(140.dp)
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
                            .fillMaxWidth(0.72f)
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
            color = AntigravityPalette.NightSurface,
            border = BorderStroke(1.dp, AntigravityPalette.NightRule)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(AntigravityPalette.CalmSageDim),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = AntigravityIcons.NightCrescent,
                        contentDescription = null,
                        tint = AntigravityPalette.CalmSageAccent,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "고요한 밤, 새로운 세션을 시작해보세요",
                    style = AntigravityTypography.CardTitle
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "아직 기록된 대화가 없습니다. 아래 추천 프롬프트를 눌러 안그래비티 에이전트의 실시간 스트리밍을 바로 시작할 수 있습니다.",
                    style = AntigravityTypography.SecondaryBody
                )
                Spacer(modifier = Modifier.height(16.dp))

                val starters = listOf(
                    "APK 빌드 상태와 서명 정보를 요약해줘",
                    "코끼리(Elephant) 다크 세이지 테마 토큰 보여줘"
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
                            .border(1.dp, AntigravityPalette.CalmSageAccent.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
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
    endpoint: String,
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
            color = AntigravityPalette.NightSurface,
            border = BorderStroke(1.dp, AntigravityPalette.SoftCoralDanger)
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
                        text = "PC 브릿지 서버 응답 없음 ($endpoint)",
                        style = AntigravityTypography.CardTitle.copy(color = AntigravityPalette.SoftCoralDanger)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "동일 Wi-Fi에서 PC의 `node pc-bridge-server.mjs`가 실행 중인지 확인하세요. PC 연결 없이도 내장 라이브 시뮬레이션 모드로 모든 기능을 즉시 사용할 수 있습니다.",
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
                            .border(1.dp, AntigravityPalette.NightRule, RoundedCornerShape(14.dp))
                            .clickable(
                                interactionSource = retryInteraction,
                                indication = null,
                                role = Role.Button,
                                onClick = onRetryBridge
                            )
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = AntigravityIcons.RefreshArc,
                                contentDescription = null,
                                tint = AntigravityPalette.WarmIvoryInk,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "다시 연결",
                                style = AntigravityTypography.PillLabel
                            )
                        }
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
                            text = "시뮬레이션 계속",
                            style = AntigravityTypography.PillLabel.copy(
                                color = AntigravityPalette.DeepForestInk,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                val ipInteraction = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .tactilePressScale(ipInteraction, reducedMotion)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(
                            interactionSource = ipInteraction,
                            indication = null,
                            role = Role.Button,
                            onClick = onOpenSettings
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "PC IP 주소 및 포트 변경하기 ($endpoint)",
                        style = AntigravityTypography.SecondaryBody.copy(
                            color = AntigravityPalette.CalmSageAccent
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun PcBridgeSettingsDialog(
    initialHost: String,
    initialPort: String,
    connectionState: BridgeConnectionState,
    onDismiss: () -> Unit,
    onSaveAndTest: (String, String) -> Unit
) {
    var hostInput by rememberSaveable { mutableStateOf(initialHost) }
    var portInput by rememberSaveable { mutableStateOf(initialPort) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AntigravityPalette.NightSurface,
        titleContentColor = AntigravityPalette.WarmIvoryInk,
        textContentColor = AntigravityPalette.MistSlateInk,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = AntigravityIcons.LinkBridge,
                    contentDescription = null,
                    tint = AntigravityPalette.CalmSageAccent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "PC 브릿지 연결 설정",
                    style = AntigravityTypography.CardTitle
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "PC에서 `node pc-bridge-server.mjs` 실행 후 로컬 IP(예: 192.168.0.10)를 입력하세요. 에뮬레이터 기본값은 10.0.2.2입니다.",
                    style = AntigravityTypography.SecondaryBody
                )

                Text(
                    text = "PC 호스트 IP",
                    style = AntigravityTypography.PillLabel.copy(color = AntigravityPalette.CalmSageAccent)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(AntigravityPalette.NightGround)
                        .border(1.dp, AntigravityPalette.NightRule, RoundedCornerShape(12.dp))
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
                    text = "포트 (기본 8765)",
                    style = AntigravityTypography.PillLabel.copy(color = AntigravityPalette.CalmSageAccent)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(AntigravityPalette.NightGround)
                        .border(1.dp, AntigravityPalette.NightRule, RoundedCornerShape(12.dp))
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
                    text = "현재 상태: ${connectionState.badgeLabel}",
                    style = AntigravityTypography.TabularMetric
                )
            }
        },
        confirmButton = {
            Box(
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(AntigravityPalette.CalmSageAccent)
                    .clickable(role = Role.Button) {
                        onSaveAndTest(hostInput, portInput)
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "저장 및 연결 확인",
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

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("Antigravity Code", text))
}
