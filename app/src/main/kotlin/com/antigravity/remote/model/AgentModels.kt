package com.antigravity.remote.model

enum class InspectorViewMode(val label: String) {
    DEMO_NORMAL("기본 화면"),
    WORST_CASE("긴 글자·특수문자 테스트 (Break-UI)"),
    LOADING("불러오는 중 화면"),
    EMPTY("빈 대화 화면"),
    ERROR("연결 확인 화면")
}

enum class BridgeConnectionState(val badgeLabel: String) {
    CONNECTED_PC("내 PC와 연결됨"),
    SIMULATION_ACTIVE("모바일 단독 모드"),
    CONNECTING("연결 확인 중..."),
    DISCONNECTED_ERROR("연결 확인 필요")
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
            label = "Antigravity 기본 AI",
            shortName = "기본 AI",
            modelId = "antigravity-agent",
            providerTag = "추천 · 자동 도우미",
            description = "질문 답변, 문서 요약, PC 작업까지 가장 빠르고 정확하게 처리합니다.",
            isLocal = false,
            defaultPort = 8765
        ),
        AiEngineOption(
            id = "engine-local-qwen",
            label = "내 PC 로컬 AI (Qwen 2.5)",
            shortName = "로컬 AI · Qwen",
            modelId = "qwen2.5-coder:7b",
            providerTag = "내 컴퓨터에서 직접 실행",
            description = "외부 서버 전송 없이 내 PC 안에서 안전하게 답변과 코드를 생성합니다.",
            isLocal = true,
            defaultPort = 11434
        ),
        AiEngineOption(
            id = "engine-local-deepseek",
            label = "내 PC 로컬 AI (DeepSeek R1)",
            shortName = "로컬 AI · DeepSeek",
            modelId = "deepseek-r1:8b",
            providerTag = "깊이 생각하는 추론형",
            description = "복잡한 고민이나 단계별 계획이 필요할 때 차분하게 생각한 뒤 답합니다.",
            isLocal = true,
            defaultPort = 11434
        ),
        AiEngineOption(
            id = "engine-local-llama",
            label = "내 PC 로컬 AI (Llama 3.1)",
            shortName = "로컬 AI · Llama",
            modelId = "llama-3.1-8b-instruct",
            providerTag = "자연스러운 한국어 대화",
            description = "일상적인 대화와 글 다듬기에 적합한 가벼운 로컬 AI 모델입니다.",
            isLocal = true,
            defaultPort = 1234
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
            id = "session-apk-build",
            title = "오늘의 대화",
            ambientCaption = "편안한 밤 · 무엇이든 물어보세요",
            branchName = "기본 대화",
            activeTaskSummary = "일상 질문, 글 정리, PC 원격 작업을 도와드립니다",
            durationTag = "지금",
            quickPrompts = listOf(
                "오늘 해야 할 일을 깔끔하게 정리해줘",
                "하루를 마무리하는 짧은 요약을 도와줘",
                "내 PC 상태가 괜찮은지 확인해줘"
            ),
            messages = listOf(
                ChatMessage(
                    id = "msg-1",
                    role = MessageRole.USER,
                    authorName = "나",
                    timestamp = "오후 11:42",
                    bodyText = "안녕! 복잡한 버튼 없이 편하게 대화하고 필요할 때 내 PC 작업도 부탁하고 싶어."
                ),
                ChatMessage(
                    id = "msg-2",
                    role = MessageRole.AGENT,
                    authorName = "안그래비티 도우미",
                    timestamp = "오후 11:43",
                    thoughtText = "사용자가 한눈에 보기 편한 화면을 원하므로 불필요한 상태 표시줄과 전문 용어를 숨기고 핵심 대화에 집중합니다.",
                    thoughtDurationSec = "1.2초",
                    tools = listOf(
                        ToolExecutionItem(
                            id = "tool-1",
                            title = "화면 정리 및 연결 상태 확인",
                            commandOrPath = "안그래비티 간편 모드 적용 완료",
                            outputPreview = "메인 화면: 대화창 + 입력창 중심으로 단순화\nAI 모델 변경 및 상세 설정: 우측 상단 [설정] 버튼으로 이동",
                            durationMs = 420,
                            status = ToolRunStatus.COMPLETED
                        )
                    ),
                    bodyText = "반갑습니다. 화면을 훨씬 깔끔하게 정리했어요.\n\n• **바로 대화하기**: 아래 입력창에 궁금한 점이나 시키고 싶은 일을 편하게 적어주세요.\n• **AI 바꾸기**: 오른쪽 위 **[설정]** 버튼을 누르면 언제든 **기본 AI**와 **내 PC 로컬 AI** 중 원하는 모델을 고를 수 있습니다.",
                    tokenCount = 180,
                    tokensPerSec = 46.4
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
