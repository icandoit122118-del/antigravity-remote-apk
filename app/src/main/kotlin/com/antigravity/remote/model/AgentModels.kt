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
            label = "내 PC 로컬 AI (Gemma 4 26B A4B)",
            shortName = "Gemma 4 26B A4B",
            modelId = "gemma4-26b-a4b-local",
            providerTag = "내 PC · Ollama",
            description = "현재 PC에 설치된 Gemma 4 로컬 모델을 사용합니다. 응답은 PC 브릿지를 통해 처리됩니다.",
            isLocal = true,
            defaultPort = 8766
        ),
        AiEngineOption(
            id = "engine-local-qwen",
            label = "내 PC 로컬 AI (Qwen 2.5)",
            shortName = "Qwen 2.5",
            modelId = "qwen2.5-coder:7b",
            providerTag = "로컬 실행",
            description = "외부 서버 전송 없이 내 PC 안에서 안전하게 답변과 코드를 생성합니다.",
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
