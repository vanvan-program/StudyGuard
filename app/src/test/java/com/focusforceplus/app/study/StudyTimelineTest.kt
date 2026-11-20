package com.focusforceplus.app.study

import com.focusforceplus.app.study.core.ActiveRange
import com.focusforceplus.app.study.core.MonitorState
import com.focusforceplus.app.study.core.MonitorTransition
import com.focusforceplus.app.study.core.StudyTimeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyTimelineTest {
    private val blocked = setOf("game")
    private val allowed = setOf("book")
    private val protected = setOf("dialer")

    @Test fun paperStudySubtractsOnlyTheBlockedVisit() {
        val timeline = StudyTimeline.evaluate(
            1_000, 5_000,
            MonitorState("book", true, false),
            listOf(
                MonitorTransition(2_000, packageObserved = true, packageName = "game"),
                MonitorTransition(3_000, packageObserved = true, packageName = "book"),
            ),
            "PAPER", "io.studyguard.app", blocked, allowed, protected,
        )
        assertEquals(listOf(ActiveRange(1_000, 2_000), ActiveRange(3_000, 5_000)), timeline.ranges)
        assertEquals(listOf(2_000L), timeline.blockedEntries.map { it.atWall })
    }

    @Test fun phoneStudyCountsOnlyLearningAppsWhileUnlocked() {
        val timeline = StudyTimeline.evaluate(
            0, 6_000,
            MonitorState("book", true, false),
            listOf(
                MonitorTransition(1_000, packageObserved = true, packageName = "game"),
                MonitorTransition(2_000, packageObserved = true, packageName = "other"),
                MonitorTransition(3_000, packageObserved = true, packageName = "book"),
                MonitorTransition(4_000, interactive = false),
                MonitorTransition(5_000, interactive = true, keyguard = false),
            ),
            "PHONE", "io.studyguard.app", blocked, allowed, protected,
        )
        assertEquals(listOf(ActiveRange(0, 1_000), ActiveRange(3_000, 4_000), ActiveRange(5_000, 6_000)), timeline.ranges)
    }

    @Test fun paperStudyCountsLockedTime() {
        val timeline = StudyTimeline.evaluate(
            0, 4_000,
            MonitorState("book", true, false),
            listOf(MonitorTransition(1_000, interactive = false, keyguard = true)),
            "PAPER", "io.studyguard.app", blocked, allowed, protected,
        )
        assertEquals(listOf(ActiveRange(0, 4_000)), timeline.ranges)
    }

    @Test fun protectedCommunicationRemainsEligibleInPaperMode() {
        val timeline = StudyTimeline.evaluate(
            0, 1_000,
            MonitorState("dialer", true, false),
            emptyList(), "PAPER", "io.studyguard.app",
            blocked + "dialer", allowed, protected,
        )
        assertEquals(1_000, timeline.ranges.single().endWall)
    }

    @Test fun simultaneousTransitionsUseTheFinalState() {
        val timeline = StudyTimeline.evaluate(
            0, 2_000,
            MonitorState("book", true, false),
            listOf(
                MonitorTransition(1_000, packageObserved = true, packageName = "game"),
                MonitorTransition(1_000, packageObserved = true, packageName = "book"),
                MonitorTransition(1_000, interactive = true),
            ),
            "PHONE", "io.studyguard.app", blocked, allowed, protected,
        )
        assertEquals(listOf(ActiveRange(0, 2_000)), timeline.ranges)
        assertTrue(timeline.blockedEntries.isEmpty())
    }

    @Test fun leavingAndReturningToBlockedAppRecordsBothVisits() {
        val timeline = StudyTimeline.evaluate(
            0, 4_000,
            MonitorState("book", true, false),
            listOf(
                MonitorTransition(1_000, packageObserved = true, packageName = "game"),
                MonitorTransition(2_000, packageObserved = true, packageName = "book"),
                MonitorTransition(3_000, packageObserved = true, packageName = "game"),
            ),
            "PAPER", "io.studyguard.app", blocked, allowed, protected,
        )
        assertEquals(listOf(1_000L, 3_000L), timeline.blockedEntries.map { it.atWall })
    }
}
