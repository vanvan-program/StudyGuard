package com.focusforceplus.app.study.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class StatisticsFilter(
    val days: Int = 7,
    val subject: String? = null,
    val today: LocalDate = LocalDate.now(),
    val zone: ZoneId = ZoneId.systemDefault(),
)

data class StatisticsSegment(val day: String, val subject: String, val durationMs: Long)
data class StatisticsTask(val day: String, val subject: String, val completed: Boolean)
data class StatisticsSession(val id: String, val subject: String, val state: String, val endedWall: Long?)
data class StatisticsEvent(val id: Long, val sessionId: String, val kind: String, val atWall: Long, val detail: String = "")
data class DailyStatistics(
    val day: LocalDate,
    val durationMs: Long,
    val completedTasks: Int,
    val totalTasks: Int,
    val distractions: Int,
    val earlyEnds: Int,
)

data class SubjectStatistics(val subject: String, val durationMs: Long)
data class StudyStatisticsSnapshot(
    val filter: StatisticsFilter,
    val from: LocalDate,
    val totalMs: Long,
    val previousMs: Long,
    val completedTasks: Int,
    val totalTasks: Int,
    val activeDays: Int,
    val streak: Int,
    val distractions: Int,
    val earlyEnds: Int,
    val interruptions: Int,
    val daily: List<DailyStatistics>,
    val subjects: List<SubjectStatistics>,
    val availableSubjects: List<String>,
    val recentEvents: List<StatisticsEvent>,
    val invalidRecords: Int,
) {
    val completionPercent: Int?
        get() = if (totalTasks == 0) null else (completedTasks.toLong() * 100 / totalTasks).toInt()
    val averageMs: Long get() = totalMs / filter.days
}

object StudyStatistics {
    fun calculate(
        filter: StatisticsFilter,
        segments: List<StatisticsSegment>,
        tasks: List<StatisticsTask>,
        sessions: List<StatisticsSession>,
        events: List<StatisticsEvent>,
    ): StudyStatisticsSnapshot {
        require(filter.days in setOf(1, 7, 30))
        val from = filter.today.minusDays(filter.days.toLong() - 1)
        val previousFrom = from.minusDays(filter.days.toLong())
        var invalid = 0
        val datedSegments = segments.mapNotNull { segment ->
            val day = parseDay(segment.day)
            if (day == null || segment.durationMs <= 0) {
                invalid++
                null
            } else day to segment
        }.filter { filter.subject == null || it.second.subject == filter.subject }
        val datedTasks = tasks.mapNotNull { task ->
            val day = parseDay(task.day)
            if (day == null) {
                invalid++
                null
            } else day to task
        }.filter { filter.subject == null || it.second.subject == filter.subject }
        val selectedSegments = datedSegments.filter { it.first in from..filter.today }
        val selectedTasks = datedTasks.filter { it.first in from..filter.today }
        val selectedSessions = sessions.filter { filter.subject == null || it.subject == filter.subject }
        val sessionIds = selectedSessions.map { it.id }.toSet()
        val selectedEvents = events.filter { filter.subject == null || it.sessionId in sessionIds }
        val startWall = from.atStartOfDay(filter.zone).toInstant().toEpochMilli()
        val endWall = filter.today.plusDays(1).atStartOfDay(filter.zone).toInstant().toEpochMilli()
        val rangedEvents = selectedEvents.filter { it.atWall in startWall until endWall }
        val ended = selectedSessions.filter {
            it.state in setOf("EARLY_END", "EMERGENCY_END") &&
                it.endedWall != null && it.endedWall in startWall until endWall
        }
        val segmentsByDay = selectedSegments.groupBy({ it.first }, { it.second })
        val tasksByDay = selectedTasks.groupBy({ it.first }, { it.second })
        val eventsByDay = rangedEvents.groupBy { Instant.ofEpochMilli(it.atWall).atZone(filter.zone).toLocalDate() }
        val endedByDay = ended.groupBy { Instant.ofEpochMilli(requireNotNull(it.endedWall)).atZone(filter.zone).toLocalDate() }
        val allDaily = datedSegments.filter { it.first <= filter.today }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, values) -> sum(values.map { it.durationMs }) }
        val daily = (0 until filter.days).map { offset ->
            val day = from.plusDays(offset.toLong())
            val dayTasks = tasksByDay[day].orEmpty()
            DailyStatistics(
                day = day,
                durationMs = sum(segmentsByDay[day].orEmpty().map { it.durationMs }),
                completedTasks = dayTasks.count { it.completed },
                totalTasks = dayTasks.size,
                distractions = eventsByDay[day].orEmpty().count { it.kind == "DISTRACTION" },
                earlyEnds = endedByDay[day].orEmpty().size,
            )
        }
        val subjects = selectedSegments.map { it.second }.groupBy { it.subject }
            .map { (subject, values) -> SubjectStatistics(subject, sum(values.map { it.durationMs })) }
            .sortedWith(compareByDescending<SubjectStatistics> { it.durationMs }.thenBy { it.subject })
        return StudyStatisticsSnapshot(
            filter = filter,
            from = from,
            totalMs = sum(selectedSegments.map { it.second.durationMs }),
            previousMs = sum(datedSegments.filter { it.first >= previousFrom && it.first < from }.map { it.second.durationMs }),
            completedTasks = selectedTasks.count { it.second.completed },
            totalTasks = selectedTasks.size,
            activeDays = daily.count { it.durationMs >= 60_000 },
            streak = StudyLedger.streak(allDaily.filterValues { it >= 60_000 }.keys, filter.today),
            distractions = rangedEvents.count { it.kind == "DISTRACTION" },
            earlyEnds = ended.size,
            interruptions = rangedEvents.count { it.kind == "INTERRUPTED" },
            daily = daily,
            subjects = subjects,
            availableSubjects = (segments.map { it.subject } + tasks.map { it.subject } + sessions.map { it.subject })
                .filter { it.isNotBlank() }.distinct().sorted(),
            recentEvents = rangedEvents.sortedWith(compareByDescending<StatisticsEvent> { it.atWall }.thenByDescending { it.id })
                .take(20),
            invalidRecords = invalid,
        )
    }

    private fun parseDay(value: String): LocalDate? = runCatching { LocalDate.parse(value) }.getOrNull()

    private fun sum(values: List<Long>): Long = values.fold(0L) { total, value ->
        if (value > Long.MAX_VALUE - total) Long.MAX_VALUE else total + value
    }
}

fun studyDurationLabel(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1_000
    val hours = seconds / 3_600
    val minutes = seconds / 60 % 60
    return when {
        hours > 0 -> "${hours}小时${minutes}分${seconds % 60}秒"
        minutes > 0 -> "${minutes}分${seconds % 60}秒"
        else -> "${seconds}秒"
    }
}

fun studyEventLabel(kind: String): String = when (kind) {
    "STARTED" -> "开始学习"
    "RESUMED" -> "恢复监督"
    "PAUSED" -> "暂停学习"
    "INTERRUPTED" -> "监督中断"
    "DISTRACTION" -> "访问受限应用"
    "CHECK" -> "进度检查"
    "SELF_REPORT" -> "自报进度"
    "COMPLETED" -> "达到学习目标"
    "TASK_REVIEW_PENDING" -> "等待确认任务"
    "TASK_CONFIRMED" -> "确认完成任务"
    "TASK_CONTINUED" -> "留待下次继续"
    "EARLY_END" -> "提前结束"
    "EMERGENCY_END" -> "紧急退出"
    else -> "学习记录"
}
