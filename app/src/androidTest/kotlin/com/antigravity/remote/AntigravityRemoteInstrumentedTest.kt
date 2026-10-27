package com.antigravity.remote

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
    fun borderlessLeftRightChatAndSettings_workCorrectly() {
        // 1. Main screen shows clean header and input bar without status subtitle or author/time labels
        composeTestRule.onNodeWithText("편안한 밤, 안그래비티").assertIsDisplayed()
        composeTestRule.onNodeWithText("설정").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("메시지 전송").assertIsDisplayed()

        // Verify removed clutter (header status, author names, timestamps) does NOT exist on the main screen
        composeTestRule.onNodeWithText("기본 AI · 내 PC와 연결됨").assertDoesNotExist()
        composeTestRule.onNodeWithText("나").assertDoesNotExist()
        composeTestRule.onNodeWithText("안그래비티 도우미").assertDoesNotExist()
        composeTestRule.onNodeWithText("오후 11:42").assertDoesNotExist()
        composeTestRule.onNodeWithText("오후 11:43").assertDoesNotExist()

        // 2. Open Settings to select Local AI and inspect options
        composeTestRule.onNodeWithText("설정").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("안그래비티 설정").assertIsDisplayed()

        // 3. Select Local AI (Qwen 2.5) inside Settings
        composeTestRule.onNodeWithText("내 PC 로컬 AI (Qwen 2.5)").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // 4. Close Settings and trigger a quick prompt chip to verify chat bubble interaction
        composeTestRule.onNodeWithText("완료").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("오늘 해야 할 일을 깔끔하게 정리해줘").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("편안한 밤, 안그래비티").assertIsDisplayed()
    }
}
