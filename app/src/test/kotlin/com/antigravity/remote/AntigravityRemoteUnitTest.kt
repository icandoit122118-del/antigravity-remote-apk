package com.antigravity.remote

import com.antigravity.remote.model.AiEngineCatalog
import com.antigravity.remote.model.AntigravityFixtures
import com.antigravity.remote.model.BridgeConnectionState
import com.antigravity.remote.model.InspectorViewMode
import com.antigravity.remote.network.PcBridgeClient
import com.antigravity.remote.ui.theme.AntigravityPalette
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AntigravityRemoteUnitTest {

    @Test
    fun defaultSessions_containFriendlyKoreanMessages() {
        val sessions = AntigravityFixtures.defaultSessions()
        assertTrue("Default sessions must not be empty", sessions.isNotEmpty())
        val first = sessions.first()
        assertEquals("ace251b1-f9a3-4165-a54b-3fe8ea9bc9e8", first.id)
        assertEquals("현재 안그래비티 세션 (실시간 연동)", first.title)
        assertTrue("First session must contain messages", first.messages.size >= 2)
    }

    @Test
    fun worstCaseFixtures_includeExtremeEdgeCasesForBreakUi() {
        val worst = AntigravityFixtures.worstCaseSessions()
        assertTrue(worst.isNotEmpty())
        val session = worst.first()
        assertTrue("Session title should stress-test >80 chars", session.title.length > 80)
        val userMsg = session.messages.first()
        assertEquals("A", userMsg.authorName)
        val agentMsg = session.messages.last()
        assertEquals(98420, agentMsg.tokenCount)
    }

    @Test
    fun aiEngineCatalog_includesAntigravityAndSelectableLocalAiModels() {
        val options = AiEngineCatalog.options
        assertTrue("Must provide multiple AI engines", options.size >= 4)
        assertTrue("Must include Local AI options", options.count { it.isLocal } >= 3)
        assertEquals("gemma4-26b-a4b-local", options.first { it.isLocal }.modelId)
        assertTrue("Gemma 4 local model must be selectable", options.any {
            it.modelId == "gemma4-26b-a4b-local" && it.isLocal
        })
    }

    @Test
    fun inspectorModes_coverAllFourDataStatesPlusBreakUi() {
        val modes = InspectorViewMode.entries
        assertEquals(5, modes.size)
        assertEquals(0xFF000000L, AntigravityPalette.NightGround.value.toLong() shr 32 and 0xFFFFFFFFL)
    }

    @Test
    fun systemPromptBundle_providesCleanDefaultsWithoutWpfTag() {
        val bundle = PcBridgeClient.defaultSystemPromptBundle()
        assertTrue(bundle.geminiMd.contains("맞춤형 응답·말투·작업 정리 원칙"))
        assertTrue(bundle.designMotionMd.contains("D0~D5"))
        assertTrue(bundle.claudeMd.isNotBlank())
        assertFalse(bundle.geminiMd.contains("WPF", ignoreCase = true))
        assertFalse(bundle.designMotionMd.contains("WPF", ignoreCase = true))
    }

    @Test
    fun pcBridgeClient_fetchesAndSavesLiveSystemPromptsRoundTrip() = runBlocking {
        val healthy = PcBridgeClient.checkBridgeHealth("127.0.0.1", 8766)
        assertTrue("Local PC bridge server on port 8766 should be healthy", healthy)

        val fetched = PcBridgeClient.fetchSystemPrompts("127.0.0.1", 8766)
        assertNotNull("Fetched SystemPromptBundle must not be null", fetched)
        val originalGemini = fetched!!.geminiMd
        assertTrue("Live GEMINI.md must contain response principles", originalGemini.contains("맞춤형 응답·말투·작업 정리 원칙"))
        assertFalse("Live GEMINI.md must not contain WPF tag", originalGemini.contains("WPF", ignoreCase = true))
        assertFalse("Live design-motion.md must not contain WPF tag", fetched.designMotionMd.contains("WPF", ignoreCase = true))

        // Round-trip save across all 3 editor tabs (gemini, design-motion, claude)
        val savedGemini = PcBridgeClient.saveSystemPrompt(
            host = "127.0.0.1",
            port = 8766,
            target = "gemini",
            content = originalGemini,
            sessionId = "ace251b1-f9a3-4165-a54b-3fe8ea9bc9e8"
        )
        assertNotNull("Saved GEMINI.md response must not be null", savedGemini)
        assertEquals(originalGemini, savedGemini!!.geminiMd)
        assertNotNull("Saved response should include updated LiveSyncState", savedGemini.syncState)

        val savedDesign = PcBridgeClient.saveSystemPrompt(
            host = "127.0.0.1",
            port = 8766,
            target = "design-motion",
            content = fetched.designMotionMd,
            sessionId = "ace251b1-f9a3-4165-a54b-3fe8ea9bc9e8"
        )
        assertNotNull("Saved design-motion.md response must not be null", savedDesign)
        assertEquals(fetched.designMotionMd, savedDesign!!.designMotionMd)

        val savedClaude = PcBridgeClient.saveSystemPrompt(
            host = "127.0.0.1",
            port = 8766,
            target = "claude",
            content = fetched.claudeMd,
            sessionId = "ace251b1-f9a3-4165-a54b-3fe8ea9bc9e8"
        )
        assertNotNull("Saved CLAUDE.md response must not be null", savedClaude)
        assertEquals(fetched.claudeMd, savedClaude!!.claudeMd)
    }

    @Test
    fun systemPromptEditor_presetSnippetsDeduplicateAndCheckDensity() {
        val base = "# 규칙\n- 기본 원칙"
        val snippet = "\n- 작업 시작 시 1문장으로 핵심 의도만 짚고 같은 턴에서 즉시 도구를 실행해 완료합니다."
        val once = PcBridgeClient.applyPresetSnippet(base, snippet)
        val twice = PcBridgeClient.applyPresetSnippet(once, snippet)
        assertEquals("Tapping preset chip twice must not duplicate rule line", once, twice)
        assertTrue(PcBridgeClient.isOptimalPromptDensity(once))

        val over100Lines = (1..105).joinToString("\n") { "- line $it" }
        assertFalse(PcBridgeClient.isOptimalPromptDensity(over100Lines))
    }

    @Test
    fun systemPromptEditor_gracefullyFallsBackWhenOffline() = runBlocking {
        val offlineFetch = PcBridgeClient.fetchSystemPrompts("127.0.0.1", 59999)
        assertNull("Unreachable bridge port must return null so UI keeps local defaults", offlineFetch)
    }

    @Test
    fun autoResyncWithRetry_recoversConnectionAndTracksAttemptsOnDisconnect() = runBlocking {
        assertEquals("PC 자동 재동기화 중...", BridgeConnectionState.AUTO_RESYNCING.badgeLabel)

        val attemptsOnOfflinePort = mutableListOf<Int>()
        val offlineState = PcBridgeClient.autoResyncWithRetry(
            host = "127.0.0.1",
            port = 59999,
            maxAttempts = 2,
            retryDelayMs = 10L,
            onAttempt = { attemptsOnOfflinePort.add(it) }
        )
        assertNull("Unreachable port should return null after retries", offlineState)
        assertEquals(listOf(1, 2), attemptsOnOfflinePort)

        val attemptsOnLivePort = mutableListOf<Int>()
        val recoveredState = PcBridgeClient.autoResyncWithRetry(
            host = "127.0.0.1",
            port = 8766,
            maxAttempts = 3,
            retryDelayMs = 10L,
            onAttempt = { attemptsOnLivePort.add(it) }
        )
        assertNotNull("Live bridge port 8766 should recover state on first attempt", recoveredState)
        assertEquals(listOf(1), attemptsOnLivePort)
        assertTrue(recoveredState!!.sessions.isNotEmpty())
    }
}
