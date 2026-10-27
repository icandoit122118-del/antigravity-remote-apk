package com.antigravity.remote.model

enum class InspectorViewMode(val label: String) {
    DEMO_NORMAL("기본 화면"),
    WORST_CASE("긴 글자·특수문자 테스트"),
    LOADING("불러오는 중 화면"),
    EMPTY("빈 대화 화면"),
    ERROR("연결 확인 화면")
}

enum class BridgeConnectionState(val badgeLabel: String) {
    CONNECTED_PC("내 PC와 연결됨"),
    AUTO_RESYNCING("PC 자동 재동기화 중..."),
    SIMULATION_ACTIVE("모바일 단독 모드"),
    CONNECTING("연결 확인 중..."),
    DISCONNECTED_ERROR("연결 확인 필요")
}

enum class ReasoningEffort(val label: String, val korDesc: String) {
    LOW("낮음", "빠른 응답"),
    MEDIUM("보통", "균형 추론"),
    HIGH("높음", "심층 추론")
}

/**
 * Moonlight-Android (`com.limelight.binding.input.touch`) Touch Mode:
 * - DIRECT_TOUCHSCREEN (`AbsoluteTouchContext`): Tapping a point on the mobile screen immediately sends
 *   `sendMousePosition(x, y, viewW, viewH)` + `BUTTON_LEFT` click at that exact PC coordinate;
 *   holding >= 650ms within 30px triggers `BUTTON_RIGHT` (right-click); 2-finger vertical drag triggers high-res scroll.
 * - RELATIVE_TRACKPAD (`RelativeTouchContext`): Dragging moves cursor relatively (`deltaX`, `deltaY`), tap clicks at current cursor.
 */
enum class MoonlightTouchMode(val label: String, val badgeDesc: String) {
    DIRECT_TOUCHSCREEN("터치스크린 (즉시 클릭)", "화면 터치 지점 즉시 PC 좌클릭 · 길게 눌러 우클릭"),
    RELATIVE_TRACKPAD("트랙패드 (상대 커서)", "드래그로 커서 이동 · 탭하여 현재 위치 클릭");

    val subtitle: String get() = badgeDesc
}

enum class MoonlightFpsTarget(val fps: Int, val label: String, val frameIntervalMs: Long) {
    FPS_60(60, "60 FPS", 16L),
    FPS_120(120, "120 FPS", 8L)
}

enum class MoonlightMouseButton(val wireName: String, val label: String) {
    LEFT("LEFT", "좌클릭"),
    RIGHT("RIGHT", "우클릭"),
    MIDDLE("MIDDLE", "휠클릭"),
    DOUBLE("DOUBLE", "더블클릭"),
    NONE("NONE", "이동")
}

data class MoonlightInputPacket(
    val type: String,
    val button: String = "LEFT",
    val x: Float = 0f,
    val y: Float = 0f,
    val refWidth: Float = 1920f,
    val refHeight: Float = 1080f,
    val deltaX: Float = 0f,
    val deltaY: Float = 0f,
    val scrollDelta: Int = 0,
    val keySpec: String = ""
) {
    constructor(
        action: String,
        normalizedX: Float = 0.5f,
        normalizedY: Float = 0.5f,
        button: MoonlightMouseButton = MoonlightMouseButton.LEFT,
        scrollAmount: Int = 0,
        keyText: String = ""
    ) : this(
        type = if (action == "KEY_PRESS") "KEY" else action,
        button = button.wireName,
        x = normalizedX.coerceIn(0f, 1f) * 1920f,
        y = normalizedY.coerceIn(0f, 1f) * 1080f,
        refWidth = 1920f,
        refHeight = 1080f,
        deltaX = 0f,
        deltaY = 0f,
        scrollDelta = scrollAmount * 120,
        keySpec = keyText
    )

    val action: String get() = type
    val normalizedX: Float get() = if (refWidth > 0f) (x / refWidth).coerceIn(0f, 1f) else 0.5f
    val normalizedY: Float get() = if (refHeight > 0f) (y / refHeight).coerceIn(0f, 1f) else 0.5f
    val mouseButton: MoonlightMouseButton
        get() = MoonlightMouseButton.entries.find { it.wireName.equals(button, ignoreCase = true) }
            ?: MoonlightMouseButton.LEFT
}

data class SunshineFrameMetadata(
    val screenWidth: Int = 1920,
    val screenHeight: Int = 1080,
    val cursorX: Int = 960,
    val cursorY: Int = 540,
    val frameSeq: Long = 0L,
    val lastEvent: String = "Sunshine + Moonlight 60~120fps 준비됨",
    val isLiveFrame: Boolean = false
)

/**
 * Pure Kotlin reverse-engineered port of Moonlight-Android's `AbsoluteTouchContext` & `RelativeTouchContext`
 * (`com.limelight.binding.input.touch`), keeping exact thresholds and coordinate normalization testable in unit tests.
 */
object MoonlightTouchEngine {
    const val LONG_PRESS_TIME_THRESHOLD_MS = 650L
    const val LONG_PRESS_DISTANCE_THRESHOLD_PX = 30f
    const val DOUBLE_TAP_TIME_THRESHOLD_MS = 250L
    const val DOUBLE_TAP_DISTANCE_THRESHOLD_PX = 60f
    const val TOUCH_DOWN_DEAD_ZONE_TIME_THRESHOLD_MS = 100L
    const val TOUCH_DOWN_DEAD_ZONE_DISTANCE_THRESHOLD_PX = 20f
    const val LONG_PRESS_TIME_THRESHOLD = LONG_PRESS_TIME_THRESHOLD_MS
    const val TOUCH_DOWN_DEAD_ZONE_DISTANCE_THRESHOLD = TOUCH_DOWN_DEAD_ZONE_DISTANCE_THRESHOLD_PX
    const val SCROLL_SPEED_FACTOR = 3

    fun clampToStreamView(coord: Float, maxDim: Float): Float {
        if (maxDim <= 1f) return 0f
        return coord.coerceIn(0f, maxDim)
    }

    fun clampNormalized(
        touchX: Float,
        touchY: Float,
        viewWidth: Float,
        viewHeight: Float
    ): Pair<Float, Float> {
        val safeW = viewWidth.coerceAtLeast(1f)
        val safeH = viewHeight.coerceAtLeast(1f)
        val nx = (clampToStreamView(touchX, safeW) / safeW).coerceIn(0f, 1f)
        val ny = (clampToStreamView(touchY, safeH) / safeH).coerceIn(0f, 1f)
        return nx to ny
    }

    fun mapToHostCoordinate(normalized: Float, hostDimension: Int): Int {
        val safeDim = hostDimension.coerceAtLeast(1)
        return kotlin.math.round(normalized.coerceIn(0f, 1f) * (safeDim - 1).coerceAtLeast(1)).toInt()
            .coerceIn(0, (safeDim - 1).coerceAtLeast(0))
    }

    fun mapStreamViewToHostCoord(
        eventX: Float,
        eventY: Float,
        viewWidth: Float,
        viewHeight: Float,
        hostWidth: Int = 1920,
        hostHeight: Int = 1080
    ): Pair<Int, Int> {
        val safeW = viewWidth.coerceAtLeast(1f)
        val safeH = viewHeight.coerceAtLeast(1f)
        val clampedX = clampToStreamView(eventX, safeW)
        val clampedY = clampToStreamView(eventY, safeH)
        val normX = (clampedX / safeW).coerceIn(0f, 1f)
        val normY = (clampedY / safeH).coerceIn(0f, 1f)
        val targetX = Math.round(normX * (hostWidth - 1).coerceAtLeast(1))
        val targetY = Math.round(normY * (hostHeight - 1).coerceAtLeast(1))
        return targetX to targetY
    }

    fun distanceExceeds(deltaX: Float, deltaY: Float, limit: Float): Boolean {
        return kotlin.math.hypot(deltaX.toDouble(), deltaY.toDouble()) > limit.toDouble()
    }

    fun resolveDirectTouchGesture(
        downX: Float,
        downY: Float,
        upX: Float,
        upY: Float,
        durationMs: Long,
        viewWidth: Float,
        viewHeight: Float,
        previousTapUpTimeMs: Long = 0L,
        previousTapUpX: Float = -999f,
        previousTapUpY: Float = -999f,
        currentUpTimeMs: Long = 0L
    ): MoonlightInputPacket {
        val safeW = viewWidth.coerceAtLeast(1f)
        val safeH = viewHeight.coerceAtLeast(1f)
        val clampedUpX = clampToStreamView(upX, safeW)
        val clampedUpY = clampToStreamView(upY, safeH)
        val clampedDownX = clampToStreamView(downX, safeW)
        val clampedDownY = clampToStreamView(downY, safeH)

        val movedBeyondLongPress = distanceExceeds(
            upX - downX,
            upY - downY,
            LONG_PRESS_DISTANCE_THRESHOLD_PX
        )
        val movedBeyondDeadZone = distanceExceeds(
            upX - downX,
            upY - downY,
            TOUCH_DOWN_DEAD_ZONE_DISTANCE_THRESHOLD_PX
        )

        // 1. Long press (>= 650ms within 30px) -> Right Click at touch-down coordinate
        if (durationMs >= LONG_PRESS_TIME_THRESHOLD_MS && !movedBeyondLongPress) {
            return MoonlightInputPacket(
                type = "LONG_PRESS_RIGHT",
                button = "RIGHT",
                x = clampedDownX,
                y = clampedDownY,
                refWidth = safeW,
                refHeight = safeH
            )
        }

        // 2. Drag movement beyond deadzone -> Move cursor to final coordinate
        if (movedBeyondDeadZone) {
            return MoonlightInputPacket(
                type = "ABS_POS",
                button = "LEFT",
                x = clampedUpX,
                y = clampedUpY,
                refWidth = safeW,
                refHeight = safeH
            )
        }

        // 3. Double tap within 250ms and 60px -> Double Left Click at original tap position
        val isDoubleTap = previousTapUpTimeMs > 0L &&
            (currentUpTimeMs - previousTapUpTimeMs) in 1L..DOUBLE_TAP_TIME_THRESHOLD_MS &&
            !distanceExceeds(clampedDownX - previousTapUpX, clampedDownY - previousTapUpY, DOUBLE_TAP_DISTANCE_THRESHOLD_PX)

        if (isDoubleTap) {
            return MoonlightInputPacket(
                type = "DOUBLE_TAP",
                button = "DOUBLE",
                x = clampedDownX,
                y = clampedDownY,
                refWidth = safeW,
                refHeight = safeH
            )
        }

        // 4. Standard Touchscreen Tap -> Immediate Left Click at exact touch point
        return MoonlightInputPacket(
            type = "TOUCH_TAP",
            button = "LEFT",
            x = clampedDownX,
            y = clampedDownY,
            refWidth = safeW,
            refHeight = safeH
        )
    }

    fun resolveDirectTouchGesture(
        downX: Float,
        downY: Float,
        upX: Float,
        upY: Float,
        viewWidth: Float,
        viewHeight: Float,
        holdDurationMs: Long,
        timeSinceLastTapMs: Long,
        lastTapX: Float,
        lastTapY: Float
    ): MoonlightInputPacket {
        val safeW = viewWidth.coerceAtLeast(1f)
        val safeH = viewHeight.coerceAtLeast(1f)
        val clampedUpX = clampToStreamView(upX, safeW)
        val clampedUpY = clampToStreamView(upY, safeH)
        val clampedDownX = clampToStreamView(downX, safeW)
        val clampedDownY = clampToStreamView(downY, safeH)

        val movedBeyondLongPress = distanceExceeds(
            upX - downX,
            upY - downY,
            LONG_PRESS_DISTANCE_THRESHOLD_PX
        )
        val movedBeyondDeadZone = distanceExceeds(
            upX - downX,
            upY - downY,
            TOUCH_DOWN_DEAD_ZONE_DISTANCE_THRESHOLD_PX
        )

        if (holdDurationMs >= LONG_PRESS_TIME_THRESHOLD_MS && !movedBeyondLongPress) {
            return MoonlightInputPacket(
                type = "LONG_PRESS_RIGHT",
                button = "RIGHT",
                x = clampedUpX,
                y = clampedUpY,
                refWidth = safeW,
                refHeight = safeH
            )
        }

        if (movedBeyondDeadZone) {
            return MoonlightInputPacket(
                type = "ABS_POS",
                button = "LEFT",
                x = clampedUpX,
                y = clampedUpY,
                refWidth = safeW,
                refHeight = safeH
            )
        }

        val isDoubleTap = timeSinceLastTapMs in 1L..DOUBLE_TAP_TIME_THRESHOLD_MS &&
            !distanceExceeds(clampedUpX - lastTapX, clampedUpY - lastTapY, DOUBLE_TAP_DISTANCE_THRESHOLD_PX)

        if (isDoubleTap) {
            return MoonlightInputPacket(
                type = "DOUBLE_TAP",
                button = "LEFT",
                x = clampedUpX,
                y = clampedUpY,
                refWidth = safeW,
                refHeight = safeH
            )
        }

        return MoonlightInputPacket(
            type = "TOUCH_TAP",
            button = "LEFT",
            x = clampedUpX,
            y = clampedUpY,
            refWidth = safeW,
            refHeight = safeH
        )
    }
}

data class AiEngineOption(
    val id: String,
    val label: String,
    val shortName: String,
    val modelId: String,
    val providerTag: String,
    val description: String,
    val isLocal: Boolean,
    val defaultPort: Int = 11434
)

object AiEngineCatalog {
    val options: List<AiEngineOption> = listOf(
        AiEngineOption(
            id = "engine-antigravity",
            label = "Gemini 4 Argon",
            shortName = "Gemini 4 Argon",
            modelId = "gemini-4-argon",
            providerTag = "신규 · 기본",
            description = "안그래비티 기본 최신 멀티모달·코딩 에이전트 모델입니다.",
            isLocal = false,
            defaultPort = 8766
        ),
        AiEngineOption(
            id = "engine-gemini-flash",
            label = "Gemini 3.8 Flash",
            shortName = "Gemini 3.8 Flash",
            modelId = "gemini-3.8-flash",
            providerTag = "빠른 응답",
            description = "빠른 응답과 실시간 대화·요약에 최적화된 경량 모델입니다.",
            isLocal = false,
            defaultPort = 8766
        ),
        AiEngineOption(
            id = "engine-claude-sonnet",
            label = "Claude Sonnet 5.5",
            shortName = "Claude Sonnet 5.5",
            modelId = "claude-sonnet-5.5",
            providerTag = "신규",
            description = "정교한 코드 작성과 UI/UX 설계에 강한 밸런스 모델입니다.",
            isLocal = false,
            defaultPort = 8766
        ),
        AiEngineOption(
            id = "engine-claude-opus",
            label = "Claude Opus 5.5",
            shortName = "Claude Opus 5.5",
            modelId = "claude-opus-5.5",
            providerTag = "신규 · 심층",
            description = "복합 아키텍처 설계와 깊은 추론을 수행하는 플래그십 모델입니다.",
            isLocal = false,
            defaultPort = 8766
        ),
        AiEngineOption(
            id = "engine-gpt-oss",
            label = "GPT-OSS 120B",
            shortName = "GPT-OSS 120B",
            modelId = "gpt-oss-120b",
            providerTag = "오픈 웨이트",
            description = "대규모 오픈 웨이트 기반 범용 추론 및 작업 모델입니다.",
            isLocal = false,
            defaultPort = 8766
        ),
        AiEngineOption(
            id = "engine-local-gemma4",
            label = "내 PC 로컬 AI (Gemma 4 26B A4B · LCODE)",
            shortName = "Gemma 4 26B A4B",
            modelId = "gemma4-26b-a4b-local",
            providerTag = "내 PC · LCODE CLI",
            description = "내 PC의 LCODE CLI 하네스(lcode.py)와 Gemma 4 로컬 모델을 통해 PC 제어 및 추론을 수행합니다.",
            isLocal = true,
            defaultPort = 8766
        ),
        AiEngineOption(
            id = "engine-local-qwen3",
            label = "내 PC 로컬 AI CLI (Qwen 3 4B · 초고속 제어)",
            shortName = "LCODE Qwen 3 4B",
            modelId = "qwen3:4b",
            providerTag = "초고속 · PC 제어",
            description = "LCODE CLI 기본 초경량 모델(68 tok/s)로 대화형 PC 제어와 빠른 응답에 최적화되어 있습니다.",
            isLocal = true,
            defaultPort = 8766
        ),
        AiEngineOption(
            id = "engine-local-qwen",
            label = "내 PC 로컬 AI CLI (Qwen 2.5 Coder 7B)",
            shortName = "LCODE Qwen 2.5",
            modelId = "qwen2.5-coder:7b",
            providerTag = "로컬 코딩 · 제어",
            description = "LCODE CLI 코딩·쉘 실행 하네스와 연동되어 내 PC 파일 편집 및 명령어를 수행합니다.",
            isLocal = true,
            defaultPort = 8766
        ),
        AiEngineOption(
            id = "engine-local-deepseek",
            label = "내 PC 로컬 AI (DeepSeek R1)",
            shortName = "DeepSeek R1",
            modelId = "deepseek-r1:8b",
            providerTag = "로컬 추론형",
            description = "복잡한 고민이나 단계별 계획이 필요할 때 차분하게 생각한 뒤 답합니다.",
            isLocal = true,
            defaultPort = 8766
        ),
        AiEngineOption(
            id = "engine-local-llama",
            label = "내 PC 로컬 AI (Llama 3.1)",
            shortName = "Llama 3.1",
            modelId = "llama-3.1-8b-instruct",
            providerTag = "로컬 대화형",
            description = "일상적인 대화와 글 다듬기에 적합한 가벼운 로컬 AI 모델입니다.",
            isLocal = true,
            defaultPort = 8766
        )
    )
}

enum class MessageRole {
    USER,
    AGENT
}

enum class ToolRunStatus(val label: String) {
    COMPLETED("완료"),
    RUNNING("진행 중"),
    FAILED("확인 필요")
}

data class ToolExecutionItem(
    val id: String,
    val title: String,
    val commandOrPath: String,
    val outputPreview: String,
    val durationMs: Long,
    val status: ToolRunStatus = ToolRunStatus.COMPLETED
)

data class ChatMessage(
    val id: String,
    val role: MessageRole,
    val authorName: String,
    val timestamp: String,
    val thoughtText: String = "",
    val thoughtDurationSec: String = "1.4s",
    val tools: List<ToolExecutionItem> = emptyList(),
    val bodyText: String,
    val codeBlockLanguage: String? = null,
    val codeBlockContent: String? = null,
    val isStreaming: Boolean = false,
    val steeringStatusText: String = "",
    val tokenCount: Int = 0,
    val tokensPerSec: Double = 0.0
)

data class WorkspaceSession(
    val id: String,
    val title: String,
    val ambientCaption: String,
    val branchName: String,
    val activeTaskSummary: String,
    val durationTag: String,
    val messages: List<ChatMessage>,
    val quickPrompts: List<String>
)

object AntigravityFixtures {

    fun defaultSessions(): List<WorkspaceSession> = listOf(
        WorkspaceSession(
            id = "ace251b1-f9a3-4165-a54b-3fe8ea9bc9e8",
            title = "현재 안그래비티 세션 (실시간 연동)",
            ambientCaption = "현재 PC의 안그래비티 대화창과 실시간 동기화 중",
            branchName = "live-sync",
            activeTaskSummary = "안그래비티 APK 제작 · 무경계 블랙 테마 · 실시간 세션 연동",
            durationTag = "실시간",
            quickPrompts = listOf(
                "오늘 해야 할 일을 깔끔하게 정리해줘",
                "현재 세션 진행 상황을 요약해줘",
                "내 PC 상태가 괜찮은지 확인해줘"
            ),
            messages = listOf(
                ChatMessage(
                    id = "msg-live-1",
                    role = MessageRole.USER,
                    authorName = "나",
                    timestamp = "오후 12:01",
                    bodyText = "대화창 시간 문구 삭제하고 AI는 왼쪽 대화풍선으로 배치 사용자 오른쪽 나 , 안그래비티 도우미 이름 문구 삭제해"
                ),
                ChatMessage(
                    id = "msg-live-2",
                    role = MessageRole.AGENT,
                    authorName = "안그래비티 도우미",
                    timestamp = "오후 12:03",
                    thoughtText = "발신자 이름과 시간 문구를 제거하고 AI는 좌측, 사용자는 우측 말풍선으로 배치합니다.",
                    thoughtDurationSec = "1.1초",
                    tools = listOf(
                        ToolExecutionItem(
                            id = "tool-1",
                            title = "좌/우 말풍선 및 무경계 블랙 테마 적용",
                            commandOrPath = "AntigravityRemoteScreen.kt · AntigravityTheme.kt",
                            outputPreview = "배경 #000000 적용 및 실시간 세션 브릿지(ace251b1) 연동 완료",
                            durationMs = 380,
                            status = ToolRunStatus.COMPLETED
                        )
                    ),
                    bodyText = "대화풍선의 이름·시간 문구를 모두 제거하고 AI는 왼쪽, 사용자는 오른쪽 말풍선으로 배치했습니다.\n\n현재 열려 있는 PC 안그래비티 세션(ace251b1)과 이 앱이 실시간으로 연동되어 있습니다.",
                    tokenCount = 180,
                    tokensPerSec = 48.2
                ),
                ChatMessage(
                    id = "msg-live-3",
                    role = MessageRole.USER,
                    authorName = "나",
                    timestamp = "오후 12:12",
                    bodyText = "전체적인 배경 검정색으로 변경해 / 지금 안그래비티 이앱으로 세션 연동되게만들어"
                ),
                ChatMessage(
                    id = "msg-live-4",
                    role = MessageRole.AGENT,
                    authorName = "안그래비티 도우미",
                    timestamp = "오후 12:15",
                    thoughtText = "전체 배경을 순수 검정(#000000)으로 전환하고 PC 브릿지 서버(/api/sessions)를 통해 현재 세션의 transcript.jsonl을 실시간 동기화합니다.",
                    thoughtDurationSec = "0.9초",
                    tools = listOf(
                        ToolExecutionItem(
                            id = "tool-2",
                            title = "현재 안그래비티 세션(ace251b1) 실시간 동기화",
                            commandOrPath = "GET http://127.0.0.1:8766/api/sessions",
                            outputPreview = "transcript.jsonl 실시간 파싱 및 모바일 양방향 메시지 연동 활성화",
                            durationMs = 210,
                            status = ToolRunStatus.COMPLETED
                        )
                    ),
                    bodyText = "전체 배경을 순수 검정색(#000000)으로 변경하고, 지금 사용 중인 PC 안그래비티 세션(ace251b1)의 대화 내역이 앱에 실시간으로 연동되도록 연결했습니다.",
                    tokenCount = 165,
                    tokensPerSec = 51.0
                )
            )
        ),
        WorkspaceSession(
            id = "session-pc-bridge",
            title = "글쓰기 · 요약 노트",
            ambientCaption = "차분한 정리 · 긴 글이나 생각을 쉽게 요약합니다",
            branchName = "요약 도우미",
            activeTaskSummary = "메모, 이메일, 문서 초안을 자연스럽게 다듬기",
            durationTag = "어제",
            quickPrompts = listOf(
                "정중한 안내 메시지 초안을 작성해줘",
                "복잡한 내용을 3줄로 쉽게 요약해줘"
            ),
            messages = listOf(
                ChatMessage(
                    id = "msg-bridge-1",
                    role = MessageRole.AGENT,
                    authorName = "안그래비티 도우미",
                    timestamp = "오후 10:15",
                    bodyText = "정리하고 싶은 메모나 긴 글을 보내주시면 핵심만 읽기 쉽게 추려드릴게요.",
                    tokenCount = 92,
                    tokensPerSec = 48.0
                )
            )
        )
    )

    /**
     * Break-UI Skill Worst-Case Fixtures (Accessible from Settings -> Advanced Screen Test):
     * Tests 120+ char unbroken paths, 1-char labels, huge token numbers, multilingual/RTL strings.
     */
    fun worstCaseSessions(): List<WorkspaceSession> = listOf(
        WorkspaceSession(
            id = "worst-session-1",
            title = "초장문_세션_이름_레이아웃_오버플로우_검증_Antigravity_Remote_Ultra_Long_Workspace_Identifier_Without_Spaces_2026_V99",
            ambientCaption = "극한 엣지 케이스 · 줄바꿈 없는 140자 파일 경로 · 다국어 RTL 혼합 · 99,999 토큰 카운트",
            branchName = "fix/extremely-long-branch-name-that-never-wraps-cleanly-in-small-chips-9999",
            activeTaskSummary = "1글자 이름('A')부터 98,420 토큰, 아랍어/히브리어 RTL, 수식 기호까지 레이아웃 깨짐 제로 검증",
            durationTag = "999시간 59분",
            quickPrompts = listOf(
                "A",
                "줄바꿈없는초장문프롬프트칩레이아웃테스트_ABCDEFGHIJKLMNOPQRSTUVWXYZ_0123456789_가나다라마바사아자차카타파하",
                " العربية / עברית / 日本語 / 한국어 혼합 렌더링 확인"
            ),
            messages = listOf(
                ChatMessage(
                    id = "worst-msg-1",
                    role = MessageRole.USER,
                    authorName = "A",
                    timestamp = "오전 12:00",
                    bodyText = "1글자 작성자('A'), 공백 없는 130자 경로, 다국어(한국어·日本語·العربية·עברית), 극단적 숫자(98,420 tok)를 동시에 렌더링해도 카드 밖으로 글자가 삐져나가지 않는지 검증해줘."
                ),
                ChatMessage(
                    id = "worst-msg-2",
                    role = MessageRole.AGENT,
                    authorName = "Antigravity Ultra-Long Agent Name For Stress Testing Header Row Ellipsis And Badge Alignment",
                    timestamp = "오전 12:01",
                    thoughtText = "Break-UI 최악 데이터 검증 중: 공백 없는 식별자와 다국어 RTL 혼합 텍스트를 점검합니다.",
                    thoughtDurationSec = "99.9s",
                    tools = listOf(
                        ToolExecutionItem(
                            id = "worst-tool-1",
                            title = "초장문 파일 경로 말줄임표 처리 확인",
                            commandOrPath = "app/src/main/kotlin/com/antigravity/remote/bridge/hyperlong_unbroken_identifier_for_stress_testing_layout_overflow_and_ellipsis_2026_v999_final_release.kt",
                            outputPreview = "Line 1042: VeryLongClassNameWithoutAnySpacesOrHyphensToTestHorizontalScroll_0123456789 -> PASS",
                            durationMs = 98420,
                            status = ToolRunStatus.COMPLETED
                        )
                    ),
                    bodyText = "최악 데이터(Worst-case) 스트레스 테스트 결과입니다:\n\n1. 공백 없는 초장문 경로와 다국어(한국어 · English · 日本語 · العربية · עברית) 모두 화면 밖으로 넘치지 않고 안정적으로 표시됩니다.",
                    codeBlockLanguage = "text",
                    codeBlockContent = "UNBROKEN_LINE_TEST_0123456789_ABCDEFGHIJKLMNOPQRSTUVWXYZ_abcdefghijklmnopqrstuvwxyz_가나다라마바사아자차카타파하_9876543210_END",
                    tokenCount = 98420,
                    tokensPerSec = 1240.5
                )
            )
        )
    )
}
