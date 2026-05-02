package com.openclaw.clawchat.network.protocol

import kotlinx.serialization.json.JsonObject

/**
 * Typed Gateway events — emitted by [GatewayConnection.events] after parsing,
 * so downstream consumers don't need to re-parse raw JSON frames.
 */
sealed class GatewayEvent {
    /** Raw event name as received from the gateway */
    abstract val name: String
    abstract val payload: JsonObject

    data class Agent(
        override val payload: JsonObject,
        val stream: String
    ) : GatewayEvent() {
        override val name = "agent"
    }

    /** agent.internal events (e.g., task_completion from subagents/cron) */
    data class AgentInternal(
        override val payload: JsonObject,
        val eventType: String  // "task_completion" etc.
    ) : GatewayEvent() {
        override val name = "agent.internal.$eventType"
    }

    data class ToolStream(override val payload: JsonObject) : GatewayEvent() {
        override val name = "tool.stream"
    }

    /** Plan stream event — multi-step work tracking (v4.24+) */
    data class PlanStream(override val payload: JsonObject) : GatewayEvent() {
        override val name = "plan.stream"
    }

    /** Item stream event — work items, tasks, checkpoints (v4.24+) */
    data class ItemStream(override val payload: JsonObject) : GatewayEvent() {
        override val name = "item.stream"
    }

    /** Patch stream event — context/session state changes (v4.24+) */
    data class PatchStream(override val payload: JsonObject) : GatewayEvent() {
        override val name = "patch.stream"
    }

    data class Chat(override val payload: JsonObject) : GatewayEvent() {
        override val name = "chat"
    }

    data class SessionsChanged(override val payload: JsonObject) : GatewayEvent() {
        override val name = "sessions.changed"
    }

    data class SessionMessage(override val payload: JsonObject) : GatewayEvent() {
        override val name = "session.message"
    }

    data class SessionTool(override val payload: JsonObject) : GatewayEvent() {
        override val name = "session.tool"
    }

    data class Shutdown(override val payload: JsonObject) : GatewayEvent() {
        override val name = "shutdown"
    }

    data class ApprovalRequested(
        override val payload: JsonObject,
        val approvalType: String  // "exec.approval.requested" or "plugin.approval.requested"
    ) : GatewayEvent() {
        override val name = approvalType
    }

    data class ApprovalResolved(
        override val payload: JsonObject,
        val approvalType: String
    ) : GatewayEvent() {
        override val name = approvalType
    }

    data class DevicePairEvent(
        override val payload: JsonObject,
        val pairEvent: String  // "device.pair.requested" or "device.pair.resolved"
    ) : GatewayEvent() {
        override val name = pairEvent
    }

    data class UpdateAvailable(override val payload: JsonObject) : GatewayEvent() {
        override val name = "update.available"
    }

    data class TalkMode(override val payload: JsonObject) : GatewayEvent() {
        override val name = "talk.mode"
    }

    data class Health(override val payload: JsonObject) : GatewayEvent() {
        override val name = "health"
    }

    /** Catch-all for events that don't need typed handling (cron, heartbeat, presence, voicewake, tick) */
    data class Passthrough(
        override val name: String,
        override val payload: JsonObject
    ) : GatewayEvent()
}
