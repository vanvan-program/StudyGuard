package com.focusforceplus.app.study.ai

import com.focusforceplus.app.study.core.parseStudySubjects
import com.focusforceplus.app.study.data.StudyProfile
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

data class PlannedTask(
    val title: String,
    val subject: String,
    val day: String,
    val minutes: Int,
    val source: String = "OFFLINE_RULES",
)

data class StudyEvidence(
    val minutes: Int,
    val distractions: Int,
    val earlyEnds: Int,
    val completedTasks: Int,
    val totalTasks: Int,
)

interface StudyAiProvider {
    val name: String
    suspend fun plan(profile: StudyProfile, today: LocalDate): List<PlannedTask>
    suspend fun check(evidence: StudyEvidence): String
    suspend fun summary(evidence: StudyEvidence): String
}

class OfflineStudyAiProvider @Inject constructor() : StudyAiProvider {
    override val name = "本地规则（未连接在线 AI）"

    override suspend fun plan(profile: StudyProfile, today: LocalDate): List<PlannedTask> {
        require(profile.goal.isNotBlank()) { "请先填写学习目标" }
        val subjects = parseStudySubjects(profile.subjects)
        require(subjects.isNotEmpty() && subjects.size <= 12) { "请填写 1–12 个科目，用逗号分隔" }
        require(profile.dailyMinutes in subjects.size..720) { "每天时间须足够给每科分配至少 1 分钟，且不超过 720 分钟" }
        require(profile.progressPercent in 0..100)
        val exam = LocalDate.parse(profile.examDay)
        require(!exam.isBefore(today)) { "考试日期不能早于今天" }
        val days = (ChronoUnit.DAYS.between(today, exam) + 1).coerceAtMost(7).toInt()
        val phase = when {
            profile.progressPercent >= 80 -> "限时练习与错题复盘"
            profile.progressPercent >= 40 -> "巩固练习并整理薄弱点"
            else -> "梳理知识要点并完成基础练习"
        }
        return (0 until days).flatMap { offset ->
            subjects.mapIndexed { index, subject ->
                PlannedTask(
                    title = "$subject：$phase（${profile.goal.take(80)}）",
                    subject = subject.take(60),
                    day = today.plusDays(offset.toLong()).toString(),
                    minutes = profile.dailyMinutes / subjects.size +
                        if (index < profile.dailyMinutes % subjects.size) 1 else 0,
                )
            }
        }
    }

    override suspend fun check(evidence: StudyEvidence): String =
        "请汇报当前任务进度。已记录有效学习 ${evidence.minutes} 分钟、" +
            "${evidence.distractions} 次受限应用访问。请说明完成了什么，以及下一步要完成什么。" +
            "这些记录不能证明你是否认真阅读。"

    override suspend fun summary(evidence: StudyEvidence): String {
        val advice = when {
            evidence.distractions > 0 -> "下一次先把最容易分心的应用加入限制，再开始一个可完成的小任务。"
            evidence.earlyEnds > 0 -> "请检查计划是否过长，下一次缩短单次学习目标并说明提前结束原因。"
            evidence.totalTasks > evidence.completedTasks -> "还有任务未完成，请调整安排，不要把未完成标为完成。"
            else -> "下一次用练习或复述检验掌握程度，再安排复习。"
        }
        return "记录到有效学习 ${evidence.minutes} 分钟；任务 ${evidence.completedTasks}/${evidence.totalTasks}，" +
            "受限应用访问 ${evidence.distractions} 次，提前结束 ${evidence.earlyEnds} 次。$advice" +
            "以上为本地规则总结，仅依据应用记录，不评价未观察到的学习行为。"
    }
}

class ResilientStudyAiProvider(
    private val primary: StudyAiProvider,
    private val fallback: StudyAiProvider,
) : StudyAiProvider {
    override val name = "${primary.name} / 失败时使用${fallback.name}"
    private data class Response<Value>(val value: Value)

    private suspend fun <Result> safely(block: suspend (StudyAiProvider) -> Result): Result =
        try {
            kotlinx.coroutines.withTimeoutOrNull(15_000) { Response(block(primary)) }?.value ?: block(fallback)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            block(fallback)
        }

    override suspend fun plan(profile: StudyProfile, today: LocalDate) =
        safely { it.plan(profile, today) }
    override suspend fun check(evidence: StudyEvidence) = safely { it.check(evidence) }
    override suspend fun summary(evidence: StudyEvidence) = safely { it.summary(evidence) }
}
