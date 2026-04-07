package com.openclaw.clawchat.ui.components

import org.junit.Assert.*
import org.junit.Test

/**
 * AgentItem and ModelItem data class tests
 */
class AgentModelSelectorTest {

    // ─────────────────────────────────────────────────────────────
    // AgentItem Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `AgentItem creates with required fields`() {
        val agent = AgentItem(
            id = "agent-1",
            name = "Test Agent"
        )

        assertEquals("agent-1", agent.id)
        assertEquals("Test Agent", agent.name)
        assertNull(agent.emoji)
        assertNull(agent.avatar)
        assertNull(agent.model)
        assertNull(agent.description)
    }

    @Test
    fun `AgentItem creates with all fields`() {
        val agent = AgentItem(
            id = "agent-2",
            name = "Full Agent",
            emoji = "🤖",
            avatar = "avatar-url",
            model = "claude-3",
            description = "A test agent"
        )

        assertEquals("agent-2", agent.id)
        assertEquals("Full Agent", agent.name)
        assertEquals("🤖", agent.emoji)
        assertEquals("avatar-url", agent.avatar)
        assertEquals("claude-3", agent.model)
        assertEquals("A test agent", agent.description)
    }

    @Test
    fun `AgentItem copy preserves values`() {
        val original = AgentItem(
            id = "agent-3",
            name = "Original",
            emoji = "🧠"
        )

        val copied = original.copy(name = "Copied")

        assertEquals("agent-3", copied.id)
        assertEquals("Copied", copied.name)
        assertEquals("🧠", copied.emoji)
    }

    // ─────────────────────────────────────────────────────────────
    // ModelItem Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `ModelItem creates with required fields`() {
        val model = ModelItem(
            id = "model-1",
            name = "Test Model"
        )

        assertEquals("model-1", model.id)
        assertEquals("Test Model", model.name)
        assertNull(model.provider)
        assertFalse(model.supportsVision)
        assertNull(model.description)
        assertNull(model.contextWindow)
    }

    @Test
    fun `ModelItem creates with all fields`() {
        val model = ModelItem(
            id = "model-2",
            name = "Full Model",
            provider = "Anthropic",
            supportsVision = true,
            description = "A powerful model",
            contextWindow = 200000
        )

        assertEquals("model-2", model.id)
        assertEquals("Full Model", model.name)
        assertEquals("Anthropic", model.provider)
        assertTrue(model.supportsVision)
        assertEquals("A powerful model", model.description)
        assertEquals(200000, model.contextWindow)
    }

    @Test
    fun `ModelItem copy preserves values`() {
        val original = ModelItem(
            id = "model-3",
            name = "Original",
            contextWindow = 100000
        )

        val copied = original.copy(contextWindow = 200000)

        assertEquals("model-3", copied.id)
        assertEquals("Original", copied.name)
        assertEquals(200000, copied.contextWindow)
    }

    @Test
    fun `ModelItem supportsVision defaults to false`() {
        val model = ModelItem(id = "model-4", name = "Default Vision")

        assertFalse(model.supportsVision)
    }

    // ─────────────────────────────────────────────────────────────
    // Equality Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `AgentItem equality works correctly`() {
        val agent1 = AgentItem(id = "agent-1", name = "Agent", emoji = "🤖")
        val agent2 = AgentItem(id = "agent-1", name = "Agent", emoji = "🤖")
        val agent3 = AgentItem(id = "agent-2", name = "Agent", emoji = "🤖")

        assertEquals(agent1, agent2)
        assertNotEquals(agent1, agent3)
    }

    @Test
    fun `ModelItem equality works correctly`() {
        val model1 = ModelItem(id = "model-1", name = "Model", supportsVision = true)
        val model2 = ModelItem(id = "model-1", name = "Model", supportsVision = true)
        val model3 = ModelItem(id = "model-2", name = "Model", supportsVision = true)

        assertEquals(model1, model2)
        assertNotEquals(model1, model3)
    }
}