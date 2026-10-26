package com.antigravity.remote

import com.antigravity.remote.model.AiEngineCatalog
import com.antigravity.remote.model.AntigravityFixtures
import com.antigravity.remote.model.InspectorViewMode
import com.antigravity.remote.ui.theme.AntigravityPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AntigravityRemoteUnitTest {

    @Test
    fun defaultSessions_containValidMessagesAndTools() {
        val sessions = AntigravityFixtures.defaultSessions()
        assertTrue("Default sessions must not be empty", sessions.isNotEmpty())
        val first = sessions.first()
        assertEquals("session-apk-build", first.id)
        assertTrue("First session must contain messages", first.messages.size >= 2)
        val agentMsg = first.messages.last()
        assertTrue("Agent message must include tool execution cards", agentMsg.tools.isNotEmpty())
        assertNotNull("Agent message must include code snippet", agentMsg.codeBlockContent)
    }

    @Test
    fun worstCaseFixtures_includeExtremeEdgeCasesForBreakUi() {
        val worst = AntigravityFixtures.worstCaseSessions()
        assertTrue(worst.isNotEmpty())
        val session = worst.first()
        assertTrue("Session title should stress-test >80 chars", session.title.length > 80)
        val userMsg = session.messages.first()
        assertEquals("A", userMsg.authorName) // 1-char name edge case
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
        assertEquals(0xFF11161BL, AntigravityPalette.NightGround.value.toLong() shr 32 and 0xFFFFFFFFL)
    }
}
