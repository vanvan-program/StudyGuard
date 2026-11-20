package com.focusforceplus.app.study.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

data class ActiveRange(val startWall: Long, val endWall: Long)
data class LedgerStep(
    val creditedMs: Long,
    val interruption: String? = null,
    val ranges: List<ActiveRange> = emptyList(),
)
data class DaySlice(val day: String, val startedWall: Long, val durationMs: Long)

object StudyLedger {
    const val MAX_OBSERVATION_GAP_MS = 5_000L

    fun advance(
        previousElapsed: Long,
        nowElapsed: Long,
        previousWall: Long,
        nowWall: Long,
        remainingMs: Long,
        eligible: Boolean,
    ): LedgerStep = advanceRanges(
        previousElapsed, nowElapsed, previousWall, nowWall, remainingMs,
        if (eligible) listOf(ActiveRange(previousWall, nowWall)) else emptyList(),
    )

    fun advanceRanges(
        previousElapsed: Long,
        nowElapsed: Long,
        previousWall: Long,
        nowWall: Long,
        remainingMs: Long,
        ranges: List<ActiveRange>,
    ): LedgerStep {
        val delta = nowElapsed - previousElapsed
        if (delta < 0) return LedgerStep(0, "CLOCK_RESET")
        if (delta > MAX_OBSERVATION_GAP_MS) return LedgerStep(0, "MONITOR_GAP")
        if (abs((nowWall - previousWall) - delta) > 2_000) return LedgerStep(0, "CLOCK_CHANGED")
        val clipped = ranges.mapNotNull { range ->
            val start = maxOf(range.startWall, previousWall)
            val end = minOf(range.endWall, nowWall)
            if (end > start) ActiveRange(start, end) else null
        }.sortedBy { it.startWall }
        val merged = mutableListOf<ActiveRange>()
        clipped.forEach { range ->
            val last = merged.lastOrNull()
            if (last != null && range.startWall <= last.endWall) {
                merged[merged.lastIndex] = last.copy(endWall = maxOf(last.endWall, range.endWall))
            } else {
                merged += range
            }
        }
        var budget = minOf(delta, remainingMs.coerceAtLeast(0))
        val limited = mutableListOf<ActiveRange>()
        for (range in merged) {
            if (budget <= 0) break
            val duration = minOf(range.endWall - range.startWall, budget)
            limited += ActiveRange(range.startWall, range.startWall + duration)
            budget -= duration
        }
        return LedgerStep(limited.sumOf { it.endWall - it.startWall }, ranges = limited)
    }

    fun splitDays(startWall: Long, durationMs: Long, zone: ZoneId): List<DaySlice> {
        require(durationMs >= 0)
        val slices = mutableListOf<DaySlice>()
        var cursor = startWall
        var remaining = durationMs
        while (remaining > 0) {
            val day = Instant.ofEpochMilli(cursor).atZone(zone).toLocalDate()
            val midnight = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val span = minOf(remaining, midnight - cursor)
            require(span > 0)
            slices += DaySlice(day.toString(), cursor, span)
            cursor += span
            remaining -= span
        }
        return slices
    }

    fun streak(days: Set<LocalDate>, today: LocalDate): Int {
        var cursor = if (today in days) today else today.minusDays(1)
        var count = 0
        while (cursor in days) {
            count++
            cursor = cursor.minusDays(1)
        }
        return count
    }
}

data class RestrictionLease(
    val sessionId: String,
    val expiresElapsed: Long,
    val blocked: Set<String>,
    val allowed: Set<String>,
) {
    fun shouldBlock(packageName: String, nowElapsed: Long, protected: Set<String>): Boolean =
        nowElapsed < expiresElapsed &&
            packageName in blocked &&
            packageName !in allowed &&
            packageName !in protected
}

fun packageSet(csv: String): Set<String> =
    csv.split(',').map(String::trim).filter(String::isNotEmpty).toSet()
