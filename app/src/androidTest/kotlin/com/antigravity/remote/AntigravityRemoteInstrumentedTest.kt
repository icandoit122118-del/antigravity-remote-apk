package com.antigravity.remote

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
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
        // 1. Main screen shows top-left session/project icon, top-right settings icon, inline [+] button, inline model/reasoning text, and compact send arrow
        composeTestRule.onNodeWithContentDescription("세션 및 프로젝트 폴더 열기").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("설정 열기").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("첨부 및 작업 메뉴 열기").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("메시지 전송").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("모델 및 추론 선택").assertIsDisplayed()

        // 1-b. Tap top-left icon to expand the Session & Project Folder Popover from the icon
        composeTestRule.onNodeWithContentDescription("세션 및 프로젝트 폴더 열기").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("+ 새 세션 생성").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("+ 폴더 생성").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("세션 및 프로젝트 폴더 열기").performClick()
        composeTestRule.waitForIdle()

        // Verify removed clutter (header title, status, author names, timestamps) does NOT exist on the main screen
        assertTrue(composeTestRule.onAllNodesWithText("편안한 밤, 안그래비티").fetchSemanticsNodes().isEmpty())
        assertTrue(composeTestRule.onAllNodesWithText("설정").fetchSemanticsNodes().isEmpty())
        assertTrue(composeTestRule.onAllNodesWithText("기본 AI · 내 PC와 연결됨").fetchSemanticsNodes().isEmpty())
        assertTrue(composeTestRule.onAllNodesWithText("나").fetchSemanticsNodes().isEmpty())
        assertTrue(composeTestRule.onAllNodesWithText("안그래비티 도우미").fetchSemanticsNodes().isEmpty())

        // 1-c. Press and drag across chat message text to select text, then test Copy, Paste, and Annotation (주석)
        composeTestRule.onAllNodesWithTag("chat_message_text").onFirst().performTouchInput {
            swipeRight(startX = left + 20f, endX = right - 20f, durationMillis = 260L)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("선택 글자 복사").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("선택 글자 붙여넣기").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("선택 글자 주석").assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription("선택 글자 복사").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("복사한 글자 붙여넣기").assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription("선택 글자 붙여넣기").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("선택 글자 주석").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("주석 저장").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("주석 저장").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("주석 삭제").assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription("글자 선택 해제").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("붙여넣기 배너 닫기").performClick()
        composeTestRule.waitForIdle()

        // 2. Tap inline [+] button to verify Photo, File, Video, and GitHub Push actions appear
        composeTestRule.onNodeWithContentDescription("첨부 및 작업 메뉴 열기").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("사진").assertIsDisplayed()
        composeTestRule.onNodeWithText("파일").assertIsDisplayed()
        composeTestRule.onNodeWithText("동영상").assertIsDisplayed()
        composeTestRule.onNodeWithText("작업내용 깃허브 올리기").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("첨부 및 작업 메뉴 열기").performClick()
        composeTestRule.waitForIdle()

        // 3. Tap semi-transparent Model & Reasoning text inside the chat input box
        composeTestRule.onNodeWithContentDescription("모델 및 추론 선택").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("높음").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("모델 및 추론 선택").performClick()
        composeTestRule.waitForIdle()

        // 4. Tap compact Send arrow to send prompt; verify the button transforms into Stop ("스트리밍 중지") and tapping it stops streaming
        composeTestRule.onNodeWithContentDescription("메시지 전송").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("스트리밍 중지").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("스트리밍 중지").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("메시지 전송").assertIsDisplayed()

        // 5. Open Settings via icon-only button and verify title is "설정" and auto-resync control is shown
        composeTestRule.onNodeWithContentDescription("설정 열기").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("설정").assertIsDisplayed()
        composeTestRule.onNodeWithText("PC 동기화 끊김 시 자동 재동기화").assertIsDisplayed()
        assertTrue(composeTestRule.onAllNodesWithText("+ 새 세션 생성").fetchSemanticsNodes().isEmpty())
        composeTestRule.onNodeWithText("완료").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("설정 열기").assertIsDisplayed()
    }
}
