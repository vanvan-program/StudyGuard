package com.focusforceplus.app.study

import com.focusforceplus.app.study.core.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class StudyStatisticsTest {
    private val today = LocalDate.of(2026, 11, 12)
    private val zone = ZoneId.of("Asia/Shanghai")
    private fun filter(days: Int = 7, subject: String? = null) = StatisticsFilter(days, subject, today, zone)
    private fun segment(offset: Long = 0, minutes: Long = 1, subject: String = "数学") =
        StatisticsSegment(today.plusDays(offset).toString(), subject, minutes * 60_000)
    private fun wall(offset: Long = 0) = today.plusDays(offset).atStartOfDay(zone).toInstant().toEpochMilli()
    private fun calculate(
        filter: StatisticsFilter = filter(),
        segments: List<StatisticsSegment> = emptyList(),
        tasks: List<StatisticsTask> = emptyList(),
        sessions: List<StatisticsSession> = emptyList(),
        events: List<StatisticsEvent> = emptyList(),
    ) = StudyStatistics.calculate(filter, segments, tasks, sessions, events)

    @Test fun emptyRangeHasSevenZeroDaysAndNoInventedCompletionRate() {
        val result = calculate()
        assertEquals(7, result.daily.size)
        assertEquals(today.minusDays(6), result.from)
        assertEquals(today, result.daily.last().day)
        assertEquals(0L, result.totalMs)
        assertNull(result.completionPercent)
        assertEquals(0, result.streak)
    }

    @Test fun todayExcludesYesterdayAndFuture() {
        val result = calculate(filter(1), listOf(segment(-1, 3), segment(0, 2), segment(1, 9)))
        assertEquals(120_000L, result.totalMs)
        assertEquals(180_000L, result.previousMs)
        assertEquals(1, result.daily.size)
    }

    @Test fun sevenDayBoundariesDoNotOverlapPreviousPeriod() {
        val result = calculate(segments = listOf(segment(-14, 8), segment(-13, 2), segment(-7, 3), segment(-6, 4), segment(0, 5)))
        assertEquals(9 * 60_000L, result.totalMs)
        assertEquals(5 * 60_000L, result.previousMs)
    }

    @Test fun thirtyDayRangeCrossesMonthWithoutMissingDates() {
        val result = calculate(filter(30), listOf(segment(-29), segment(-30), segment()))
        assertEquals(30, result.daily.size)
        assertEquals(LocalDate.of(2026, 10, 14), result.from)
        assertEquals(120_000L, result.totalMs)
        assertEquals(60_000L, result.previousMs)
    }

    @Test fun subjectFilterAppliesToDurationTasksEventsAndEndedSessions() {
        val result = calculate(
            filter(subject = "数学"),
            listOf(segment(), segment(subject = "英语")),
            listOf(StatisticsTask(today.toString(), "数学", true), StatisticsTask(today.toString(), "英语", false)),
            listOf(StatisticsSession("math", "数学", "EARLY_END", wall()), StatisticsSession("english", "英语", "EARLY_END", wall())),
            listOf(StatisticsEvent(1, "math", "DISTRACTION", wall()), StatisticsEvent(2, "english", "DISTRACTION", wall())),
        )
        assertEquals(60_000L, result.totalMs)
        assertEquals(100, result.completionPercent)
        assertEquals(1, result.distractions)
        assertEquals(1, result.earlyEnds)
        assertEquals(listOf("数学"), result.subjects.map { it.subject })
        assertEquals(listOf("数学", "英语").sorted(), result.availableSubjects)
    }

    @Test fun taskRateUsesScheduledDayAndCurrentState() {
        val result = calculate(tasks = listOf(
            StatisticsTask(today.toString(), "数学", true),
            StatisticsTask(today.toString(), "数学", false),
            StatisticsTask(today.plusDays(1).toString(), "数学", true),
        ))
        assertEquals(2, result.totalTasks)
        assertEquals(50, result.completionPercent)
        assertEquals(1, result.daily.last().completedTasks)
    }

    @Test fun zeroDaysContributeToCalendarAverage() {
        val result = calculate(segments = listOf(segment(minutes = 7)))
        assertEquals(60_000L, result.averageMs)
        assertEquals(1, result.activeDays)
    }

    @Test fun subMinuteRecordsRemainVisibleButDoNotCreateCheckin() {
        val result = calculate(segments = listOf(segment().copy(durationMs = 59_999)))
        assertEquals(59_999L, result.totalMs)
        assertEquals(0, result.activeDays)
        assertEquals(0, result.streak)
    }

    @Test fun multipleSlicesCombineBeforeCheckinThreshold() {
        val result = calculate(segments = listOf(segment().copy(durationMs = 30_000), segment().copy(durationMs = 30_000)))
        assertEquals(1, result.activeDays)
        assertEquals(1, result.streak)
    }

    @Test fun streakExtendsBeyondSelectedRange() {
        val result = calculate(filter(1), (-8L..0L).map { segment(it) })
        assertEquals(9, result.streak)
        assertEquals(1, result.activeDays)
    }

    @Test fun yesterdayStreakPersistsBeforeTodaysThreshold() {
        val result = calculate(segments = listOf(segment(-2), segment(-1), segment().copy(durationMs = 10_000)))
        assertEquals(2, result.streak)
    }

    @Test fun futureRecordsCannotExtendStreak() {
        val result = calculate(segments = listOf(segment(1), segment(2)))
        assertEquals(0, result.streak)
        assertEquals(0L, result.totalMs)
    }

    @Test fun invalidDatesAndNonpositiveDurationsAreIgnoredWithoutChangingInput() {
        val source = listOf(segment(), segment().copy(day = "2026-02-30"), segment().copy(durationMs = -5), segment().copy(durationMs = 0))
        val result = calculate(segments = source, tasks = listOf(StatisticsTask("bad", "数学", true)))
        assertEquals(4, result.invalidRecords)
        assertEquals(60_000L, result.totalMs)
        assertEquals(-5L, source[2].durationMs)
    }

    @Test fun eventEndBoundaryIsExclusive() {
        val result = calculate(filter(1), events = listOf(
            StatisticsEvent(1, "test", "DISTRACTION", wall() - 1),
            StatisticsEvent(2, "test", "DISTRACTION", wall()),
            StatisticsEvent(3, "test", "DISTRACTION", wall(1) - 1),
            StatisticsEvent(4, "test", "DISTRACTION", wall(1)),
        ))
        assertEquals(2, result.distractions)
        assertEquals(listOf(3L, 2L), result.recentEvents.map { it.id })
    }

    @Test fun endedSessionIsCountedByEndDayWithoutRequiringStartInsideRange() {
        val result = calculate(filter(1), sessions = listOf(
            StatisticsSession("early", "数学", "EARLY_END", wall()),
            StatisticsSession("emergency", "数学", "EMERGENCY_END", wall()),
            StatisticsSession("completed", "数学", "COMPLETED", wall()),
            StatisticsSession("running", "数学", "RUNNING", null),
            StatisticsSession("old", "数学", "EARLY_END", wall(-1)),
        ))
        assertEquals(2, result.earlyEnds)
        assertEquals(2, result.daily.single().earlyEnds)
    }

    @Test fun interruptionsAreSeparateFromDistractions() {
        val result = calculate(events = listOf(StatisticsEvent(1, "test", "INTERRUPTED", wall())))
        assertEquals(1, result.interruptions)
        assertEquals(0, result.distractions)
    }

    @Test fun recentEventsAreLimitedAndSortedDeterministically() {
        val result = calculate(events = (1L..25L).map { StatisticsEvent(it, "test", "CHECK", wall()) })
        assertEquals((25L downTo 6L).toList(), result.recentEvents.map { it.id })
    }

    @Test fun subjectDetailsSurviveTwoHundredNewerEventsFromAnotherSubject() {
        val math = StatisticsEvent(1, "math", "DISTRACTION", wall(), "切换到娱乐应用")
        val newer = (2L..201L).map { StatisticsEvent(it, "english", "CHECK", wall() + it) }
        val result = calculate(filter(subject = "数学"),
            sessions = listOf(StatisticsSession("math", "数学", "COMPLETED", wall())),
            events = newer + math)
        assertEquals(1, result.distractions)
        assertEquals(listOf(math), result.recentEvents)
    }

    @Test fun subjectRankingUsesDurationThenName() {
        val result = calculate(segments = listOf(segment(minutes = 3, subject = "英语"), segment(subject = "数学"), segment(subject = "历史")))
        assertEquals(listOf("英语", "历史", "数学"), result.subjects.map { it.subject })
    }

    @Test fun daylightSavingDayUsesLocalBoundaries() {
        val localToday = LocalDate.of(2026, 11, 1)
        val localZone = ZoneId.of("America/New_York")
        val start = localToday.atStartOfDay(localZone).toInstant().toEpochMilli()
        val end = localToday.plusDays(1).atStartOfDay(localZone).toInstant().toEpochMilli()
        assertEquals(25 * 3_600_000L, end - start)
        val result = calculate(StatisticsFilter(1, null, localToday, localZone), events = listOf(
            StatisticsEvent(1, "test", "DISTRACTION", end - 1),
            StatisticsEvent(2, "test", "DISTRACTION", end),
        ))
        assertEquals(1, result.distractions)
    }

    @Test fun overflowingDurationNeverWrapsNegative() {
        val result = calculate(segments = listOf(segment().copy(durationMs = Long.MAX_VALUE), segment()))
        assertEquals(Long.MAX_VALUE, result.totalMs)
        assertTrue(result.averageMs > 0)
    }

    @Test fun unsupportedRangeIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { calculate(filter(14)) }
    }

    @Test fun durationAndUnknownEventLabelsRemainReadable() {
        assertEquals("0秒", studyDurationLabel(-1))
        assertEquals("30秒", studyDurationLabel(30_000))
        assertEquals("1分5秒", studyDurationLabel(65_000))
        assertEquals("1小时1分1秒", studyDurationLabel(3_661_000))
        assertEquals("紧急退出", studyEventLabel("EMERGENCY_END"))
        assertEquals("学习记录", studyEventLabel("FUTURE_EVENT"))
    }
}
