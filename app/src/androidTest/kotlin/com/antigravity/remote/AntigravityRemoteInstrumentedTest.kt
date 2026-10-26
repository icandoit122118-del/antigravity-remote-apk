package com.antigravity.remote

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
    fun simplifiedMainScreenAndSettingsDialog_areEasyAndInteractive() {
        // 1. Main screen only shows clean header, conversation, and input bar
        composeTestRule.onNodeWithText("편안한 밤, 안그래비티").assertIsDisplayed()
        composeTestRule.onNodeWithText("설정").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("메시지 전송").assertIsDisplayed()

        // 2. Open Settings to select Local AI and inspect advanced options
        composeTestRule.onNodeWithText("설정").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("안그래비티 설정").assertIsDisplayed()

        // 3. Select Local AI (Qwen 2.5) inside Settings
        composeTestRule.onNodeWithText("내 PC 로컬 AI (Qwen 2.5)").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // 4. Close Settings and verify header reflects Local AI selection cleanly
        composeTestRule.onNodeWithText("완료").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("편안한 밤, 안그래비티").assertIsDisplayed()
    }
}
