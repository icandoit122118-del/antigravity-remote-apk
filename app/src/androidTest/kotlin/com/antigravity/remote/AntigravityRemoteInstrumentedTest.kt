package com.antigravity.remote

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AntigravityRemoteInstrumentedTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun elephantHeaderAndAllStates_areDisplayedAndInteractive() {
        // 1. Verify Elephant Night Header & Workspace Carousel render on physical device
        composeTestRule.onNodeWithText("고요한 밤, 안그래비티").assertIsDisplayed()
        composeTestRule.onNodeWithText("antigravity-remote-apk").assertIsDisplayed()

        // 2. Switch to Break-UI Worst-Case Mode and verify extreme data renders without crash
        composeTestRule.onNodeWithText("최악 데이터 (Break-UI)").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodesWithText("A").onFirst().assertIsDisplayed()

        // 3. Switch to Loading State and verify loading indicator
        composeTestRule.onNodeWithText("로딩 상태").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("PC 브릿지 워크스페이스 동기화 중...").assertIsDisplayed()

        // 4. Switch to Empty State and verify empty night card
        composeTestRule.onNodeWithText("빈 화면").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("고요한 밤, 새로운 세션을 시작해보세요").assertIsDisplayed()

        // 5. Scroll to and switch to Error State and verify recovery actions
        composeTestRule.onNodeWithText("연결 오류").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("시뮬레이션 계속").assertIsDisplayed()

        // 6. Click "시뮬레이션 계속" to recover back to Normal Demo mode
        composeTestRule.onNodeWithText("시뮬레이션 계속").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("antigravity-remote-apk").assertIsDisplayed()

        // 7. Verify Floating Stream Mini-Bar and Prompt Input Send button
        composeTestRule.onNodeWithText("스트림").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("메시지 전송").assertIsDisplayed()
    }
}
