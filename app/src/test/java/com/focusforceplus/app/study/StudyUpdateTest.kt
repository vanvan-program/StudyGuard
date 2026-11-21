package com.focusforceplus.app.study

import com.focusforceplus.app.study.data.StudyProfile
import com.focusforceplus.app.study.data.StudyTask
import com.focusforceplus.app.study.ui.StudyStartDraft
import com.focusforceplus.app.study.ui.samePlanAs
import com.focusforceplus.app.study.update.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class StudyUpdateTest {
    private val hash = "ab".repeat(32)
    private val catalog = """{"version":"0.3.8","artifacts":[{"name":"StudyGuard-0.3.8-debug.apk"},{"name":"StudyGuard-0.3.8-release.apk","bytes":200,"sha256":"$hash"}]}"""

    @Test fun catalogSelectsReleaseAndRejectsInvalidMetadata() {
        assertEquals(StudyRelease("0.3.8", 200, hash), parseRelease(catalog))
        for (invalid in listOf(catalog.replace("-release.apk", "-source.zip"), catalog.replace(hash, "00"),
                catalog.replace("200", "0"), catalog.replace("0.3.8", "../file"))) {
            assertThrows(Exception::class.java) { parseRelease(invalid) }
        }
    }

    @Test fun comparesHistoricalDevAndFinalVersionsNumerically() {
        assertTrue(isNewer("0.3.8", "0.3.7-dev"))
        assertTrue(isNewer("0.3.10", "0.3.9"))
        assertTrue(isNewer("0.3.8", "0.3.8-dev"))
        assertFalse(isNewer("0.3.8-dev", "0.3.8"))
        assertFalse(isNewer("0.3.8", "0.3.8"))
    }

    @Test fun automaticSelectionTriesNextRouteAfterFailure() = runBlocking {
        val calls = mutableListOf<UpdateSource>()
        val result = tryUpdateSources(UpdateSource.AUTO) { route, canSwitch ->
            calls += route
            if (canSwitch) throw IOException("timeout")
            route
        }
        assertEquals(listOf(UpdateSource.OFFICIAL, UpdateSource.GH_PROXY, UpdateSource.GH_FAST), calls)
        assertEquals(UpdateSource.GH_FAST, result)
    }

    @Test fun sustainedSlowDownloadSwitchesOnlyWhenAnAlternativeExists() {
        fun receive(canSwitch: Boolean): Long = runBlocking {
            var tick = -9_000_000_000L
            StudyUpdateClient.receive(byteArrayOf(1).inputStream(), 8, canSwitch, { _, _, _ -> }) {
                tick += 9_000_000_000L
                tick
            }
        }
        assertThrows(IOException::class.java) { receive(true) }
        assertEquals(1L, receive(false))
    }

    @Test fun manualSourceIsPinnedAndCancellationStopsFailover() {
        val calls = mutableListOf<UpdateSource>()
        assertThrows(IOException::class.java) {
            runBlocking { tryUpdateSources(UpdateSource.GH_FAST) { route, next ->
                calls += route
                assertFalse(next)
                throw IOException("offline")
            } }
        }
        assertEquals(listOf(UpdateSource.GH_FAST), calls)
        calls.clear()
        assertThrows(CancellationException::class.java) {
            runBlocking { tryUpdateSources(UpdateSource.AUTO) { route, _ ->
                calls += route
                throw CancellationException()
            } }
        }
        assertEquals(listOf(UpdateSource.OFFICIAL), calls)
    }

    @Test fun reachingTargetRequiresExplicitExtraMinutes() {
        val task = StudyTask(id = 1, title = "复习", subject = "物理", day = "2026-11-20", plannedMinutes = 10, createdAt = 1)
        assertEquals("1", StudyStartDraft.forTask(task, 599_999).minutes)
        assertEquals("", StudyStartDraft.forTask(task, 600_000).minutes)
        assertEquals("", StudyStartDraft.forTask(task, 660_000).minutes)
    }

    @Test fun onlyPlanFieldsInvalidateGeneratedDraft() {
        val input = StudyProfile(goal = "复习")
        assertTrue(input.samePlanAs(input.copy(exemptPackages = "chat", checkMinutes = 15)))
        for (changed in listOf(input.copy(goal = "练习"), input.copy(subjects = "语文"), input.copy(examDay = "2026-11-21"),
                input.copy(dailyMinutes = 60), input.copy(progressPercent = 45))) {
            assertFalse(input.samePlanAs(changed))
        }
    }
}
