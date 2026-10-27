package com.antigravity.remote

import com.antigravity.remote.model.AiEngineCatalog
import com.antigravity.remote.model.AntigravityFixtures
import com.antigravity.remote.model.InspectorViewMode
import com.antigravity.remote.ui.theme.AntigravityPalette
import org.junit.Assert.assertEquals
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
        assertEquals("qwen2.5-coder:7b", options.first { it.isLocal }.modelId)
    }

    @Test
    fun inspectorModes_coverAllFourDataStatesPlusBreakUi() {
        val modes = InspectorViewMode.entries
        assertEquals(5, modes.size)
        assertEquals(0xFF000000L, AntigravityPalette.NightGround.value.toLong() shr 32 and 0xFFFFFFFFL)
    }
}
