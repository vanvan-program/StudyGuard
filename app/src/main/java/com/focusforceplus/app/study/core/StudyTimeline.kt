package com.focusforceplus.app.study.core

data class MonitorState(
    val packageName: String?,
    val interactive: Boolean,
    val keyguard: Boolean,
)

data class MonitorTransition(
    val atWall: Long,
    val packageObserved: Boolean = false,
    val packageName: String? = null,
    val interactive: Boolean? = null,
    val keyguard: Boolean? = null,
)

data class BlockedEntry(val atWall: Long, val packageName: String)

data class TimelineResult(
    val ranges: List<ActiveRange>,
    val finalState: MonitorState,
    val blockedEntries: List<BlockedEntry>,
)

object StudyTimeline {
    fun evaluate(
        startWall: Long,
        endWall: Long,
        initial: MonitorState,
        transitions: List<MonitorTransition>,
        mode: String,
        selfPackage: String,
        blocked: Set<String>,
        allowed: Set<String>,
        protected: Set<String>,
    ): TimelineResult {
        require(endWall >= startWall)
        require(mode == "PAPER" || mode == "PHONE")
        val ranged = mutableListOf<ActiveRange>()
        val blockedEntries = mutableListOf<BlockedEntry>()
        val effectiveBlocked = blocked - allowed - protected
        var state = initial
        var cursor = startWall
        val ordered = transitions
            .filter { it.atWall in startWall..endWall }
            .sortedBy { it.atWall }
        var index = 0
        while (index < ordered.size) {
            val timestamp = ordered[index].atWall
            if (timestamp > cursor && isEligible(state, mode, selfPackage, effectiveBlocked, allowed)) {
                append(ranged, cursor, timestamp)
            }
            val previousPackage = state.packageName
            while (index < ordered.size && ordered[index].atWall == timestamp) {
                val transition = ordered[index]
                state = state.copy(
                    packageName = if (transition.packageObserved) transition.packageName else state.packageName,
                    interactive = transition.interactive ?: state.interactive,
                    keyguard = transition.keyguard ?: state.keyguard,
                )
                index++
            }
            val currentPackage = state.packageName
            if (currentPackage != null && currentPackage != previousPackage &&
                currentPackage in effectiveBlocked) {
                blockedEntries += BlockedEntry(timestamp, currentPackage)
            }
            cursor = maxOf(cursor, timestamp)
        }
        if (endWall > cursor && isEligible(state, mode, selfPackage, effectiveBlocked, allowed)) {
            append(ranged, cursor, endWall)
        }
        return TimelineResult(ranged, state, blockedEntries)
    }

    private fun isEligible(
        state: MonitorState,
        mode: String,
        selfPackage: String,
        blocked: Set<String>,
        allowed: Set<String>,
    ): Boolean {
        if (mode == "PAPER") {
            if (!state.interactive || state.keyguard) return true
            return state.packageName != null && state.packageName !in blocked
        }
        if (!state.interactive || state.keyguard) return false
        val current = state.packageName ?: return false
        return current == selfPackage || current !in blocked && current in allowed
    }

    private fun append(ranges: MutableList<ActiveRange>, start: Long, end: Long) {
        val last = ranges.lastOrNull()
        if (last != null && last.endWall == start) {
            ranges[ranges.lastIndex] = last.copy(endWall = end)
        } else {
            ranges += ActiveRange(start, end)
        }
    }
}
