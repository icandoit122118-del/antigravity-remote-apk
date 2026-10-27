package com.antigravity.remote

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AntigravityRemoteInstrumentedTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun borderlessLeftRightChatAndSettings_workCorrectly() {
        // 1. Main screen shows icon-only settings button, input bar, and far-right model/reasoning selector
        composeTestRule.onNodeWithContentDescription("설정 열기").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("메시지 전송").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("모델 및 추론 선택").assertIsDisplayed()

        // Verify removed clutter (header title, status, author names, timestamps) does NOT exist on the main screen
        assertTrue(composeTestRule.onAllNodesWithText("편안한 밤, 안그래비티").fetchSemanticsNodes().isEmpty())
        assertTrue(composeTestRule.onAllNodesWithText("설정").fetchSemanticsNodes().isEmpty())
        assertTrue(composeTestRule.onAllNodesWithText("기본 AI · 내 PC와 연결됨").fetchSemanticsNodes().isEmpty())
        assertTrue(composeTestRule.onAllNodesWithText("나").fetchSemanticsNodes().isEmpty())
        assertTrue(composeTestRule.onAllNodesWithText("안그래비티 도우미").fetchSemanticsNodes().isEmpty())

        // 2. Open Model & Reasoning popup on the far right of the chat bar
        composeTestRule.onNodeWithContentDescription("모델 및 추론 선택").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("High").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("모델 및 추론 선택").performClick()
        composeTestRule.waitForIdle()

        // 3. Open Settings via icon-only button to select Local AI and inspect live session/project options
        composeTestRule.onNodeWithContentDescription("설정 열기").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("안그래비티 설정").assertIsDisplayed()
        composeTestRule.onNodeWithText("내 PC 로컬 AI (Qwen 2.5)").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // 4. Close Settings and trigger a quick prompt chip
        composeTestRule.onNodeWithText("완료").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("오늘 해야 할 일을 깔끔하게 정리해줘").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("설정 열기").assertIsDisplayed()
    }
}
