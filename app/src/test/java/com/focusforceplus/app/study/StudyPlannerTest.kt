package com.focusforceplus.app.study

import com.focusforceplus.app.study.ai.*
import com.focusforceplus.app.study.data.StudyProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class StudyPlannerTest {
    private val provider = OfflineStudyAiProvider()
    private val today = LocalDate.of(2026, 11, 10)
    private val profile = StudyProfile(
        goal = "完成第一轮复习", subjects = "数学，英语,物理", examDay = "2026-12-10",
        dailyMinutes = 121, progressPercent = 40,
    )

    @Test fun generatedDailyTasksRespectBudget() = runBlocking {
        val tasks = provider.plan(profile, today)
        assertEquals(21, tasks.size)
        tasks.groupBy { it.day }.values.forEach { day ->
            assertEquals(121, day.sumOf { it.minutes })
            assertTrue(day.all { it.minutes > 0 })
        }
    }

    @Test fun planNeverGoesPastExam() = runBlocking {
        val tasks = provider.plan(profile.copy(examDay = today.plusDays(1).toString()), today)
        assertEquals(6, tasks.size)
        assertEquals(today.plusDays(1).toString(), tasks.last().day)
    }

    @Test fun todayExamStillGetsTodayPlan() = runBlocking {
        assertEquals(3, provider.plan(profile.copy(examDay = today.toString()), today).size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun pastExamIsRejected() { runBlocking { provider.plan(profile.copy(examDay = "2026-11-09"), today) } }

    @Test(expected = IllegalArgumentException::class)
    fun zeroDailyBudgetIsRejected() { runBlocking { provider.plan(profile.copy(dailyMinutes = 0), today) } }

    @Test(expected = IllegalArgumentException::class)
    fun insufficientBudgetForSubjectsIsRejected() { runBlocking { provider.plan(profile.copy(dailyMinutes = 2), today) } }

    @Test(expected = IllegalArgumentException::class)
    fun emptyGoalIsRejected() { runBlocking { provider.plan(profile.copy(goal = ""), today) } }

    @Test fun duplicatedSubjectsDoNotCreateDuplicateTasks() = runBlocking {
        assertEquals(7, provider.plan(profile.copy(subjects = "数学,数学"), today).size)
    }

    @Test fun progressChangesTaskStage() = runBlocking {
        assertTrue(provider.plan(profile.copy(progressPercent = 90), today).first().title.contains("错题"))
    }

    @Test fun summaryDisclosesEvidenceLimitations() = runBlocking {
        val text = provider.summary(StudyEvidence(15, 2, 1, 1, 4))
        assertTrue(text.contains("15 分钟"))
        assertTrue(text.contains("1/4"))
        assertTrue(text.contains("不评价未观察到"))
    }

    @Test fun networkOrMalformedResponseFallsBackOffline() = runBlocking {
        val failing = object : StudyAiProvider {
            override val name = "测试失败提供商"
            override suspend fun plan(profile: StudyProfile, today: LocalDate): List<PlannedTask> = error("429 or invalid JSON")
            override suspend fun check(evidence: StudyEvidence): String = error("offline")
            override suspend fun summary(evidence: StudyEvidence): String = error("network unavailable")
        }
        val resilient = ResilientStudyAiProvider(failing, provider)
        assertEquals(21, resilient.plan(profile, today).size)
        assertTrue(resilient.summary(StudyEvidence(0, 0, 0, 0, 0)).contains("本地规则"))
    }

    @Test(expected = CancellationException::class)
    fun userCancellationIsNotSwallowed() {
        runBlocking {
            val cancelled = object : StudyAiProvider {
                override val name = "取消测试"
                override suspend fun plan(profile: StudyProfile, today: LocalDate): List<PlannedTask> = throw CancellationException()
                override suspend fun check(evidence: StudyEvidence): String = throw CancellationException()
                override suspend fun summary(evidence: StudyEvidence): String = throw CancellationException()
            }
            ResilientStudyAiProvider(cancelled, provider).plan(profile, today)
        }
    }
}
