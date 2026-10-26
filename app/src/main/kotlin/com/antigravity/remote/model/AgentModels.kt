package com.antigravity.remote.model

enum class InspectorViewMode(val label: String) {
    DEMO_NORMAL("기본 데모"),
    WORST_CASE("최악 데이터 (Break-UI)"),
    LOADING("로딩 상태"),
    EMPTY("빈 화면"),
    ERROR("연결 오류")
}

enum class BridgeConnectionState(val badgeLabel: String) {
    CONNECTED_PC("연결됨 · PC 브릿지"),
    SIMULATION_ACTIVE("라이브 시뮬레이션"),
    CONNECTING("브릿지 탐색 중..."),
    DISCONNECTED_ERROR("브릿지 응답 없음")
}

enum class MessageRole {
    USER,
    AGENT
}

enum class ToolRunStatus(val label: String) {
    COMPLETED("완료"),
    RUNNING("실행 중"),
    FAILED("실패")
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
            title = "antigravity-remote-apk",
            ambientCaption = "고요한 밤의 집중 · 안드로이드 APK 빌드 파이프라인",
            branchName = "main · API 36",
            activeTaskSummary = "코끼리(Elephant) 다크 세이지 테마 및 실시간 스트리밍 엔진 검증",
            durationTag = "14분 경과",
            quickPrompts = listOf(
                "APK 서명 및 용량 최적화 상태 점검해줘",
                "코끼리 다크 세이지 색상 대비율(WCAG) 리포트 보여줘",
                "PC 브릿지 웹소켓 재연결 로직 테스트 실행해줘",
                "새로운 스트리밍 데모 응답 실행해줘"
            ),
            messages = listOf(
                ChatMessage(
                    id = "msg-1",
                    role = MessageRole.USER,
                    authorName = "나 (모바일 리모트)",
                    timestamp = "오후 11:42",
                    bodyText = "안그래비티 프로그램을 코끼리(Elephant) 앱 스타일의 다크 세이지 감성 APK로 빌드하고 실시간 스트리밍 상태를 확인해줘."
                ),
                ChatMessage(
                    id = "msg-2",
                    role = MessageRole.AGENT,
                    authorName = "Antigravity Agent",
                    timestamp = "오후 11:43",
                    thoughtText = "사용자가 WWIT 코끼리(Elephant) 레퍼런스의 차분한 밤 공기(Night Sage #11161B, 카드 #1A2229, 세이지 민트 #68C5B2, 램프 골드 #E8B96D)를 적용한 안드로이드 리모트 APK를 요청함. 20dp 라운드 카드와 플로팅 미니 스트림 바, 실시간 토큰 스트리밍 엔진을 구성하고 SDK 36으로 빌드 검증을 수행합니다.",
                    thoughtDurationSec = "2.1s",
                    tools = listOf(
                        ToolExecutionItem(
                            id = "tool-1",
                            title = "SDK 36 플랫폼 및 빌드 도구 검증",
                            commandOrPath = "android-cli sdk install platforms/android-36 build-tools/36.0.0",
                            outputPreview = "Unzipping platforms/android-36... 100% (101.3 MB)\nUnzipping build-tools/36.0.0... 100% (136.6 MB)\nStatus: INSTALLED",
                            durationMs = 1840,
                            status = ToolRunStatus.COMPLETED
                        ),
                        ToolExecutionItem(
                            id = "tool-2",
                            title = "디자인 토큰 및 WCAG 명도 대비 검증",
                            commandOrPath = "app/src/main/kotlin/com/antigravity/remote/ui/theme/AntigravityTheme.kt",
                            outputPreview = "NightGround(#11161B) vs WarmIvoryInk(#F2EFE9) -> 13.8:1 (AAA Pass)\nNightGround(#11161B) vs CalmSageAccent(#68C5B2) -> 8.2:1 (AAA Pass)",
                            durationMs = 420,
                            status = ToolRunStatus.COMPLETED
                        )
                    ),
                    bodyText = "코끼리(Elephant) 앱의 고요한 밤 감성과 Hallmark `Ribbon` 팔레트를 결합해 `Antigravity Remote` 레이아웃과 스트리밍 엔진을 구성했습니다.\n\n• 20dp 라운드 표면 카드(`#1A2229`)와 1dp 미세 보더(`#2A3742`)로 눈의 피로를 줄였습니다.\n• 하단 플로팅 미니 스트림 바에서 실시간 토큰 속도(`tok/s`)와 경과 시간을 고정폭 숫자(`tnum`)로 확인할 수 있습니다.",
                    codeBlockLanguage = "kotlin",
                    codeBlockContent = """
                        // 코끼리(Elephant) x Hallmark Ribbon 핵심 컬러 토큰
                        val NightGround = Color(0xFF11161B)      // 깊은 밤 배경
                        val NightSurface = Color(0xFF1A2229)     // 20dp 라운드 카드 표면
                        val CalmSageAccent = Color(0xFF68C5B2)   // 차분한 세이지 민트 포인트
                        val WarmLampGold = Color(0xFFE8B96D)     // 스트리밍 활성 램프 골드
                    """.trimIndent(),
                    tokenCount = 318,
                    tokensPerSec = 46.4
                )
            )
        ),
        WorkspaceSession(
            id = "session-pc-bridge",
            title = "pc-bridge-server",
            ambientCaption = "새벽 공기 · 로컬 LAN 브릿지 및 모바일 동기화",
            branchName = "feat/lan-stream",
            activeTaskSummary = "ws://<PC_IP>:8765 양방향 프롬프트 전달 및 무중단 폴백",
            durationTag = "방금 전",
            quickPrompts = listOf(
                "현재 PC 브릿지 서버 연결 상태 확인해줘",
                "로컬 네트워크 지연시간 벤치마크 실행해줘",
                "방화벽 포트 8765 허용 명령어 알려줘"
            ),
            messages = listOf(
                ChatMessage(
                    id = "msg-bridge-1",
                    role = MessageRole.USER,
                    authorName = "나 (모바일 리모트)",
                    timestamp = "오후 11:50",
                    bodyText = "집 밖이나 침대에서 스마트폰으로 PC의 안그래비티에 명령을 보낼 때 연결 방식은 어떻게 작동해?"
                ),
                ChatMessage(
                    id = "msg-bridge-2",
                    role = MessageRole.AGENT,
                    authorName = "Antigravity Agent",
                    timestamp = "오후 11:51",
                    thoughtText = "PC 브릿지 서버(`pc-bridge-server.mjs`)와 안드로이드 앱 간의 HTTP/SSE 스트리밍 및 오프라인 시뮬레이터 자동 폴백 구조를 설명합니다.",
                    thoughtDurationSec = "1.3s",
                    tools = listOf(
                        ToolExecutionItem(
                            id = "tool-b1",
                            title = "브릿지 엔드포인트 헬스체크",
                            commandOrPath = "GET http://10.0.2.2:8765/api/status",
                            outputPreview = "{\"ok\":true,\"service\":\"antigravity-pc-bridge\",\"port\":8765,\"activeSessions\":3}",
                            durationMs = 190,
                            status = ToolRunStatus.COMPLETED
                        )
                    ),
                    bodyText = "동일 Wi-Fi 또는 Tailscale 망에서 PC의 `pc-bridge-server.mjs`(포트 `8765`)에 즉시 연결됩니다. PC 서버가 꺼져 있을 때도 앱 내장 라이브 스트리밍 엔진이 자동으로 동작해 언제든 인터페이스와 응답 흐름을 테스트할 수 있습니다.",
                    codeBlockLanguage = "bash",
                    codeBlockContent = """
                        # PC에서 브릿지 서버 실행 (Node.js 무의존성 단일 스크립트)
                        node pc-bridge-server.mjs --port 8765
                    """.trimIndent(),
                    tokenCount = 214,
                    tokensPerSec = 51.2
                )
            )
        ),
        WorkspaceSession(
            id = "session-motion-audit",
            title = "design-motion-audit",
            ambientCaption = "고요한 파동 · 접근성 및 모션 감리 리포트",
            branchName = "audit/d0-d5",
            activeTaskSummary = "터치 타깃 48dp · prefers-reduced-motion · Break-UI 검증",
            durationTag = "32분 전",
            quickPrompts = listOf(
                "최악 데이터(Break-UI) 스트레스 테스트 결과 요약해줘",
                "저시력 및 동작 줄이기(Reduced Motion) 대응 내역 보여줘"
            ),
            messages = listOf(
                ChatMessage(
                    id = "msg-audit-1",
                    role = MessageRole.AGENT,
                    authorName = "Antigravity Agent",
                    timestamp = "오후 11:25",
                    thoughtText = "D0~D5 디자인·모션 스택 감리 기준에 맞춰 터치 영역(48x48dp), 고정폭 숫자(tnum), 동작 줄이기(ANIMATOR_DURATION_SCALE) 설정을 점검했습니다.",
                    thoughtDurationSec = "0.9s",
                    bodyText = "모든 인터랙티브 버튼과 칩에 최소 `48dp × 48dp` 터치 영역을 적용했고, OS 설정에서 '동작 줄이기'가 켜져 있으면 무한 펄스 애니메이션을 멈추고 즉시 전환되도록 설계했습니다.",
                    tokenCount = 142,
                    tokensPerSec = 48.0
                )
            )
        )
    )

    /**
     * Break-UI Skill Worst-Case Fixtures:
     * Tests 120+ char unbroken paths, 1-char labels, huge token numbers, multilingual/RTL strings,
     * and zero-tool / empty-thought edge cases to guarantee zero layout overlap or clipping.
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
                    authorName = "A", // 1-letter author name edge case
                    timestamp = "오전 12:00:00.999",
                    bodyText = "1글자 작성자('A'), 공백 없는 130자 경로, 다국어(한국어·日本語·العربية·עברית), 극단적 숫자(98,420 tok)를 동시에 렌더링해도 카드 밖으로 글자가 삐져나가지 않는지 검증해줘."
                ),
                ChatMessage(
                    id = "worst-msg-2",
                    role = MessageRole.AGENT,
                    authorName = "Antigravity Ultra-Long Agent Name For Stress Testing Header Row Ellipsis And Badge Alignment",
                    timestamp = "오전 12:00:04.128",
                    thoughtText = "Break-UI 최악 데이터 검증 중: 1) 공백 없는 135자 식별자 2) 5개 연속 실행 도구 카드 3) RTL(العربية: مرحبا بكم في اختبار التصميم) 및 수식(∑_{i=1}^n x_i = α·β) 4) 98,420 토큰 표기 시 고정폭 숫자(tnum) 정렬 유지.",
                    thoughtDurationSec = "99.9s",
                    tools = listOf(
                        ToolExecutionItem(
                            id = "worst-tool-1",
                            title = "초장문 파일 경로 정적 분석 및 말줄임표(Ellipsis) 처리 확인",
                            commandOrPath = "app/src/main/kotlin/com/antigravity/remote/bridge/hyperlong_unbroken_identifier_for_stress_testing_layout_overflow_and_ellipsis_2026_v999_final_release.kt",
                            outputPreview = "Line 1042: VeryLongClassNameWithoutAnySpacesOrHyphensToTestHorizontalScrollInMonospaceTerminalContainer_0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ -> PASS (No Clipping)",
                            durationMs = 98420,
                            status = ToolRunStatus.COMPLETED
                        ),
                        ToolExecutionItem(
                            id = "worst-tool-2",
                            title = "비어 있는 출력 및 실패 상태 배지 렌더링 확인",
                            commandOrPath = "adb connect 255.255.255.255:65535 --timeout-ms=999999",
                            outputPreview = "Error: Connection timed out after 999999ms (Handled gracefully without crash)",
                            durationMs = 12050,
                            status = ToolRunStatus.FAILED
                        )
                    ),
                    bodyText = "최악 데이터(Worst-case) 스트레스 테스트 결과입니다:\n\n1. 공백 없는 초장문 경로: `maxLines`와 `TextOverflow.Ellipsis` 및 코드 블록 가로 스크롤로 카드 폭 침범을 방지했습니다.\n2. 다국어 및 RTL 혼합: 한국어 · English · 日本語 · العربية (اختبار واجهة المستخدم) · עברית (בדיקת ממשק) 모두 줄간격 `24sp` 내에서 안정적으로 표시됩니다.\n3. 극단적 수치: `98,420 tok` 및 `1,240.5 tok/s`에서도 하단 플로팅 미니바의 중지 버튼(`48×48dp`)이 밀려나지 않습니다.",
                    codeBlockLanguage = "text",
                    codeBlockContent = "UNBROKEN_LINE_TEST_0123456789_ABCDEFGHIJKLMNOPQRSTUVWXYZ_abcdefghijklmnopqrstuvwxyz_가나다라마바사아자차카타파하_9876543210_END",
                    tokenCount = 98420,
                    tokensPerSec = 1240.5
                )
            )
        )
    )
}
