package com.focusforceplus.app.study

import com.focusforceplus.app.study.core.RestrictionLease
import com.focusforceplus.app.study.core.ActiveRange
import com.focusforceplus.app.study.core.StudyLedger
import com.focusforceplus.app.study.core.packageSet
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class StudyLedgerTest {
    @Test fun monotonicIntervalIsCredited() {
        assertEquals(2_000, StudyLedger.advance(1_000, 3_000, 10_000, 12_000, 60_000, true).creditedMs)
    }

    @Test fun unobservedGapNeverCountsAsLearning() {
        val step = StudyLedger.advance(0, 60_000, 0, 60_000, 120_000, true)
        assertEquals(0, step.creditedMs)
        assertEquals("MONITOR_GAP", step.interruption)
    }

    @Test fun wallClockChangeDoesNotAddLearning() {
        val step = StudyLedger.advance(1_000, 2_000, 10_000, 3_610_000, 60_000, true)
        assertEquals(0, step.creditedMs)
        assertEquals("CLOCK_CHANGED", step.interruption)
    }

    @Test fun rebootDoesNotAddLearning() {
        assertEquals("CLOCK_RESET", StudyLedger.advance(10_000, 1_000, 10_000, 11_000, 50_000, true).interruption)
    }

    @Test fun pausedOrDistractedIntervalsDoNotCount() {
        assertEquals(0, StudyLedger.advance(1_000, 3_000, 1_000, 3_000, 60_000, false).creditedMs)
    }

    @Test fun creditCannotExceedTarget() {
        assertEquals(300, StudyLedger.advance(1_000, 3_000, 1_000, 3_000, 300, true).creditedMs)
    }

    @Test fun wallClockMovingBackIsDetected() {
        assertEquals(0, StudyLedger.advance(1_000, 3_000, 10_000, 1_000, 60_000, true).creditedMs)
    }

    @Test fun resumedSessionOnlyCreditsSinceResumeCheckpoint() {
        assertEquals(2_000, StudyLedger.advance(600_000, 602_000, 600_000, 602_000, 60_000, true).creditedMs)
    }

    @Test fun disjointEligibleRangesKeepTheirRealPositions() {
        val step = StudyLedger.advanceRanges(
            1_000, 3_000, 10_000, 12_000, 60_000,
            listOf(ActiveRange(10_200, 10_700), ActiveRange(11_100, 11_900)),
        )
        assertEquals(1_300, step.creditedMs)
        assertEquals(listOf(ActiveRange(10_200, 10_700), ActiveRange(11_100, 11_900)), step.ranges)
    }

    @Test fun eligibleRangesAreClippedAndCannotExceedTarget() {
        val step = StudyLedger.advanceRanges(
            1_000, 3_000, 10_000, 12_000, 500,
            listOf(ActiveRange(9_000, 10_400), ActiveRange(11_900, 13_000)),
        )
        assertEquals(500, step.creditedMs)
        assertEquals(listOf(ActiveRange(10_000, 10_400), ActiveRange(11_900, 12_000)), step.ranges)
    }

    @Test fun midnightIsSplitWithoutLosingTime() {
        val zone = ZoneId.of("Asia/Shanghai")
        val start = ZonedDateTime.of(2026, 11, 10, 23, 59, 59, 0, zone).toInstant().toEpochMilli()
        val slices = StudyLedger.splitDays(start, 3_000, zone)
        assertEquals(listOf("2026-11-10", "2026-11-11"), slices.map { it.day })
        assertEquals(listOf(1_000L, 2_000L), slices.map { it.durationMs })
    }

    @Test fun daylightSavingDayUsesActualMidnight() {
        val zone = ZoneId.of("America/New_York")
        val start = LocalDate.of(2026, 3, 8).atStartOfDay(zone).toInstant().toEpochMilli()
        val slices = StudyLedger.splitDays(start, 24 * 3_600_000L, zone)
        assertEquals(23 * 3_600_000L, slices[0].durationMs)
        assertEquals(3_600_000L, slices[1].durationMs)
    }

    @Test fun zeroDurationProducesNoCheckin() {
        assertTrue(StudyLedger.splitDays(0, 0, ZoneId.of("UTC")).isEmpty())
    }

    @Test fun streakCanContinueFromYesterday() {
        val today = LocalDate.of(2026, 11, 10)
        assertEquals(2, StudyLedger.streak(setOf(today.minusDays(1), today.minusDays(2)), today))
    }

    @Test fun streakBreaksAtMissingDay() {
        val today = LocalDate.of(2026, 11, 10)
        assertEquals(1, StudyLedger.streak(setOf(today, today.minusDays(2)), today))
    }

    @Test fun noStudyHasNoStreak() {
        assertEquals(0, StudyLedger.streak(emptySet(), LocalDate.of(2026, 11, 10)))
    }

    private val lease = RestrictionLease("current", 5_000, setOf("game", "dialer", "book"), setOf("book"))

    @Test fun selectedEntertainmentIsBlockedDuringLease() {
        assertTrue(lease.shouldBlock("game", 4_999, emptySet()))
    }

    @Test fun expiredLeaseCannotBlock() {
        assertFalse(lease.shouldBlock("game", 5_000, emptySet()))
    }

    @Test fun learningWhitelistOverridesBlockedSelection() {
        assertFalse(lease.shouldBlock("book", 1_000, emptySet()))
    }

    @Test fun emergencyAndSystemPackagesRemainReachable() {
        assertFalse(lease.shouldBlock("dialer", 1_000, setOf("dialer")))
    }

    @Test fun unselectedAppCannotBeBlocked() {
        assertFalse(lease.shouldBlock("other", 1_000, emptySet()))
    }

    @Test fun packageParsingDropsEmptyAndDuplicateValues() {
        assertEquals(setOf("a", "b"), packageSet("a, b,,a, "))
    }
}
