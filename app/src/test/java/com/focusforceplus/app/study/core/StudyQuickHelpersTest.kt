package com.focusforceplus.app.study.core

import com.focusforceplus.app.study.monitor.StudySummaryWorker
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class StudyQuickHelpersTest {

    @Test
    fun `parseStudySubjects splits multiple delimiters trims and deduplicates`() {
        val parsed = parseStudySubjects(" 数学 , 英语，物理、 化学\n数学\r\n  ")
        assertEquals(listOf("数学", "英语", "物理", "化学"), parsed)
    }

    @Test
    fun `suggestedStudySubjects prioritizes pending today subjects then profile and history`() {
        val result = suggestedStudySubjects(
            profileSubjects = "英语，数学，生物",
            pendingTodaySubjects = listOf("物理", "数学"),
            allTaskSubjects = listOf("历史", "物理", "地理"),
            limit = 5,
        )
        assertEquals(listOf("物理", "数学", "英语", "生物", "历史"), result)
    }

    @Test
    fun `remainingDurationLabel formats remaining time and completed goal`() {
        assertEquals("还剩 5分30秒", remainingDurationLabel(effectiveMs = 270_000L, targetMs = 600_000L))
        assertEquals("已达成目标", remainingDurationLabel(effectiveMs = 600_000L, targetMs = 600_000L))
        assertEquals("已达成目标", remainingDurationLabel(effectiveMs = 700_000L, targetMs = 600_000L))
    }

    @Test
    fun `blocked presets match exact or subpackage boundaries only`() {
        val shortVideo = StudyBlockedPresets.ALL.first { it.id == "short_video" }
        val social = StudyBlockedPresets.ALL.first { it.id == "social" }
        val installed = listOf(
            "com.ss.android.ugc.aweme",
            "tv.danmaku.bili",
            "com.tencent.mm",
            "com.tencent.mms", // Must not match com.tencent.mm prefix without dot boundary.
            "io.studyguard.app",
        )
        assertEquals(setOf("com.ss.android.ugc.aweme", "tv.danmaku.bili"), StudyBlockedPresets.matchPackages(shortVideo, installed))
        assertEquals(setOf("com.tencent.mm"), StudyBlockedPresets.matchPackages(social, installed))
    }

    @Test
    fun `sortByPinnedOrder pins selected keys first while preserving relative order`() {
        val ordered = sortByPinnedOrder(
            items = listOf("alpha", "beta", "gamma", "delta"),
            pinnedKeys = setOf("delta", "beta"),
        ) { it }
        assertEquals(listOf("beta", "delta", "alpha", "gamma"), ordered)
    }

    @Test
    fun `throttleWhile emits immediately when not throttling and flushes on exit`() = runBlocking {
        var now = 0L
        val running = MutableStateFlow(false)
        val emitted = mutableListOf<Int>()
        val source = flow {
            emit(1)
            emit(2)
            running.value = true
            delay(20)
            emit(3) // Emits immediately (elapsed >= interval on entry or first after running).
            now += 100
            emit(4) // Held while running (< 5000ms).
            emit(5) // Replaces pending 4.
            delay(20)
            running.value = false // Exiting running should flush 5 without waiting 5000ms.
            delay(30)
            emit(6)
        }
        source.throttleWhile(
            intervalMs = 5_000L,
            shouldThrottle = running,
            initialThrottling = false,
            elapsedMs = { now },
        ).toList(emitted)

        assertEquals(listOf(1, 2, 3, 5, 6), emitted)
    }

    @Test
    fun `summary worker initialDelayMillis aligns to next day 00_10`() {
        val zone = ZoneId.of("Asia/Shanghai")
        val beforeMidnight = ZonedDateTime.of(2026, 11, 15, 23, 50, 0, 0, zone)
        assertEquals(20 * 60_000L, StudySummaryWorker.initialDelayMillis(beforeMidnight))

        val afternoon = ZonedDateTime.of(2026, 11, 15, 14, 10, 0, 0, zone)
        assertEquals(10 * 60 * 60_000L, StudySummaryWorker.initialDelayMillis(afternoon))
    }
}
