package com.focusforceplus.app.study.data

import android.os.SystemClock
import androidx.room.withTransaction
import com.focusforceplus.app.data.db.AppDatabase
import com.focusforceplus.app.study.ai.StudyAiProvider
import com.focusforceplus.app.study.ai.PlannedTask
import com.focusforceplus.app.study.ai.StudyEvidence
import com.focusforceplus.app.study.backup.BackupEvent
import com.focusforceplus.app.study.backup.BackupProfile
import com.focusforceplus.app.study.backup.BackupSegment
import com.focusforceplus.app.study.backup.BackupSession
import com.focusforceplus.app.study.backup.BackupSummary
import com.focusforceplus.app.study.backup.BackupTask
import com.focusforceplus.app.study.backup.StudyBackupCodec
import com.focusforceplus.app.study.backup.StudyBackupPayload
import com.focusforceplus.app.study.backup.StudyBackupSummary
import com.focusforceplus.app.study.core.StudyLedger
import com.focusforceplus.app.study.monitor.StudyRuntime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StudyRepository @Inject constructor(
    val database: AppDatabase,
    private val runtime: StudyRuntime,
    val ai: StudyAiProvider,
) {
    val dao = database.studyDao()
    private val mutex = Mutex()

    suspend fun saveProfile(profile: StudyProfile) {
        require(profile.goal.length <= 500 && profile.subjects.length <= 720)
        require(profile.dailyMinutes in 1..720 && profile.progressPercent in 0..100)
        require(profile.checkMinutes in 1..180) { "检查间隔为 1–180 分钟" }
        LocalDate.parse(profile.examDay)
        dao.saveProfile(profile)
    }

    suspend fun acceptPlan(tasks: List<PlannedTask>): Int = database.withTransaction {
        require(tasks.size <= 84)
        val existing = dao.tasksOnce().map { it.day to it.subject }.toSet()
        var added = 0
        tasks.distinctBy { it.day to it.subject }.filter { (it.day to it.subject) !in existing }.forEach { task ->
            insertTaskEntry(
                title = task.title,
                subject = task.subject,
                day = task.day,
                minutes = task.minutes,
                source = task.source,
            )
            added++
        }
        added
    }

    suspend fun addTask(title: String, subject: String, day: String, minutes: Int): Long =
        insertTaskEntry(title, subject, day, minutes, source = "MANUAL")

    private suspend fun insertTaskEntry(
        title: String,
        subject: String,
        day: String,
        minutes: Int,
        source: String,
    ): Long {
        LocalDate.parse(day)
        require(title.isNotBlank() && subject.isNotBlank() && minutes in 1..720) {
            "请填写任务名称、科目与 1–720 分钟"
        }
        return dao.insertTask(
            StudyTask(
                title = title.trim().take(500),
                subject = subject.trim().take(60),
                day = day,
                plannedMinutes = minutes,
                source = source,
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun updateTask(id: Long, title: String, subject: String, day: String, minutes: Int) {
        require(title.isNotBlank() && subject.isNotBlank() && minutes in 1..720)
        LocalDate.parse(day)
        dao.updateTask(id, title.trim().take(500), subject.trim().take(60), day, minutes)
    }

    suspend fun toggleTask(id: Long) = database.withTransaction {
        dao.toggleTask(id, System.currentTimeMillis())
        if (dao.taskById(id)?.isCompleted == true) resolveReviews(id, true)
    }

    suspend fun deleteTask(id: Long) = dao.deleteTask(id)

    suspend fun recover() = mutex.withLock {
        if (runtime.serviceAlive) return@withLock
        val session = dao.currentSession()
        if (session == null) {
            runtime.current.value = null
            runtime.setLinkedTask(null)
            return@withLock
        }
        runtime.suspendRestrictions()
        if (session.state == "RUNNING") {
            val interrupted = session.copy(state = "INTERRUPTED")
            database.withTransaction {
                dao.saveSession(interrupted)
                event(session.id, "INTERRUPTED", "监督服务未连续运行，未知时间未计入学习")
            }
            runtime.current.value = interrupted
        } else runtime.current.value = session
        runtime.setLinkedTask(session.linkedTaskId)
        runtime.status.value = "监督已中断或暂停；限制已解除，请手动恢复或结束"
    }

    suspend fun start(
        subject: String,
        mode: String,
        minutes: Int,
        token: Long,
        taskId: Long? = null,
    ): StudySession? =
        mutex.withLock {
            require(subject.isNotBlank() && minutes in 1..720)
            require(mode == "PAPER" || mode == "PHONE")
            require(dao.currentSession() == null) { "请先结束或恢复已有会话" }
            if (!runtime.isCurrentRequest(token)) return@withLock null
            require(runtime.hasUsageAccess()) { "请先开启使用情况访问权限" }
            val profile = dao.profileOnce() ?: StudyProfile()
            val wall = System.currentTimeMillis()
            val session = StudySession(
                id = UUID.randomUUID().toString(),
                subject = subject.take(60), mode = mode, targetMs = minutes * 60_000L,
                state = "RUNNING",
                startedWall = wall, lastWall = wall, lastElapsed = SystemClock.elapsedRealtime(),
                blockedPackages = profile.blockedPackages, allowedPackages = profile.allowedPackages,
                checkMinutes = profile.checkMinutes,
                linkedTaskId = taskId?.takeIf { dao.taskById(it) != null },
            )
            database.withTransaction {
                dao.saveSession(session)
                event(session.id, "STARTED", "用户手动开始")
            }
            if (!runtime.isCurrentRequest(token)) {
                val kind = runtime.stopKind?.takeIf { it in setOf("EARLY_END", "EMERGENCY_END") } ?: "EARLY_END"
                val reason = runtime.stopReason.ifBlank { "启动期间取消" }
                dao.saveSession(session.copy(state = kind, endedWall = System.currentTimeMillis(), endReason = reason))
                event(session.id, kind, reason)
                runtime.setLinkedTask(null)
                return@withLock null
            }
            runtime.activate(session, token)
            session
        }

    suspend fun resume(token: Long): StudySession? = mutex.withLock {
        require(runtime.hasUsageAccess()) { "请先授予使用情况访问权限，再恢复学习" }
        val session = dao.currentSession() ?: return@withLock null
        if (!runtime.isCurrentRequest(token)) return@withLock null
        val resumed = session.copy(
            state = "RUNNING", lastElapsed = SystemClock.elapsedRealtime(), lastWall = System.currentTimeMillis(),
        )
        database.withTransaction {
            dao.saveSession(resumed)
            event(session.id, "RESUMED", "用户手动恢复，未补计中断期间")
        }
        runtime.activate(resumed, token)
        resumed
    }

    suspend fun pause() = mutex.withLock {
        runtime.suspendRestrictions()
        val session = dao.currentSession() ?: return@withLock
        if (session.state != "RUNNING") return@withLock
        val paused = session.copy(state = "PAUSED")
        database.withTransaction {
            dao.saveSession(paused)
            event(session.id, "PAUSED", "暂停，限制解除，不累计时长")
        }
        runtime.current.value = paused
        runtime.status.value = "已暂停；本次限制已解除"
    }

    suspend fun completeTask(taskId: Long) = database.withTransaction {
        dao.completeTask(taskId, System.currentTimeMillis())
        resolveReviews(taskId, true)
    }

    private suspend fun resolveReviews(taskId: Long, complete: Boolean) {
        if (complete) dao.resolveTaskReviews(taskId, "TASK_CONFIRMED", "已确认完成关联任务")
        else dao.resolveTaskReviews(taskId, "TASK_CONTINUED", "本段时长已累计，任务留待下次继续")
    }

    suspend fun reviewTask(review: PendingTaskReview, complete: Boolean) = database.withTransaction {
        val pending = dao.pendingTaskReview().first()
        if (pending?.sessionId != review.sessionId || pending.taskId != review.taskId) return@withTransaction
        if (complete) dao.completeTask(review.taskId, System.currentTimeMillis())
        resolveReviews(review.taskId, complete)
    }

    suspend fun finish(
        kind: String,
        reason: String = "",
        expectedId: String? = null,
        completeLinkedTask: Boolean? = null,
    ): StudySession? = mutex.withLock {
        val session = dao.currentSession() ?: return@withLock null
        if (expectedId != null && expectedId != session.id) return@withLock null
        // Service teardown can arrive before the UI coroutine. Both must honor the same request.
        val requested = runtime.stopRequest?.takeIf { it.sessionId == session.id }
        val requestedKind = requested?.kind ?: kind
        val requestedReason = requested?.reason ?: reason
        val finalKind = if (requestedKind == "COMPLETED" && session.effectiveMs < session.targetMs) "EARLY_END" else requestedKind
        val shouldCompleteTask = requested?.completeLinkedTask ?: completeLinkedTask ?: false
        runtime.release(finalKind, requestedReason, shouldCompleteTask)
        val nowWall = System.currentTimeMillis()
        val ended = session.copy(
            state = finalKind, endedWall = nowWall, endReason = requestedReason.take(500),
        )
        database.withTransaction {
            dao.saveSession(ended)
            event(session.id, finalKind, requestedReason.take(500))
            if (shouldCompleteTask && session.linkedTaskId != null) {
                dao.completeTask(session.linkedTaskId, nowWall)
                resolveReviews(session.linkedTaskId, true)
            } else if (finalKind == "COMPLETED" && session.linkedTaskId != null &&
                dao.taskById(session.linkedTaskId)?.isCompleted == false) {
                event(session.id, "TASK_REVIEW_PENDING", "本段学习达标，等待确认任务进度")
            }
        }
        if (runtime.current.value?.id == session.id) runtime.current.value = null
        ended
    }

    suspend fun interrupt(detail: String, expectedId: String? = null) = mutex.withLock {
        val session = dao.currentSession() ?: return@withLock
        if (expectedId != null && expectedId != session.id) return@withLock
        runtime.suspendRestrictions()
        if (session.state == "PAUSED") return@withLock
        if (session.state == "RUNNING") {
            val changed = session.copy(state = "INTERRUPTED")
            database.withTransaction {
                dao.saveSession(changed)
                event(session.id, "INTERRUPTED", detail)
            }
            runtime.current.value = changed
        }
        runtime.status.value = detail
    }

    suspend fun checkpoint(
        token: Long,
        eligibleRanges: List<com.focusforceplus.app.study.core.ActiveRange>,
        capability: String,
        observedWall: Long = System.currentTimeMillis(),
    ): StudySession? = mutex.withLock {
        val session = dao.currentSession() ?: return@withLock null
        if (!runtime.isCurrentRequest(token) || session.state != "RUNNING") return@withLock session
        val wall = observedWall
        val elapsed = SystemClock.elapsedRealtime()
        val step = StudyLedger.advanceRanges(
            session.lastElapsed, elapsed, session.lastWall, wall,
            session.targetMs - session.effectiveMs, eligibleRanges,
        )
        val next = session.copy(
            lastWall = wall, lastElapsed = elapsed,
            effectiveMs = session.effectiveMs + step.creditedMs,
            state = if (step.interruption != null) "INTERRUPTED" else "RUNNING",
        )
        val segments = step.ranges.flatMap { range ->
            StudyLedger.splitDays(range.startWall, range.endWall - range.startWall, ZoneId.systemDefault())
                .map { StudySegment(
                    sessionId = session.id, subject = session.subject, day = it.day,
                    startedWall = it.startedWall, durationMs = it.durationMs, capability = capability,
                ) }
        }
        database.withTransaction {
            dao.saveSession(next)
            segments.forEach { segment ->
                val last = dao.lastSegment(session.id)
                if (last != null && last.day == segment.day && last.capability == segment.capability &&
                    last.startedWall + last.durationMs == segment.startedWall) {
                    dao.updateSegment(last.copy(durationMs = last.durationMs + segment.durationMs))
                } else dao.insertSegments(listOf(segment))
            }
            step.interruption?.let { event(session.id, "INTERRUPTED", "$it：未知区间未计时，请手动恢复") }
        }
        if (runtime.isCurrentRequest(token)) runtime.activate(next, token)
        if (step.interruption != null) runtime.status.value = "监督中断：${step.interruption}；限制已解除"
        next
    }

    suspend fun event(sessionId: String, kind: String, detail: String, atWall: Long = System.currentTimeMillis()) {
        dao.insertEvent(StudyEvent(sessionId = sessionId, kind = kind, atWall = atWall, detail = detail.take(1000)))
    }

    suspend fun checkIfDue(session: StudySession): String? {
        val evidence = mutex.withLock {
            val latest = dao.currentSession() ?: return@withLock null
            if (latest.id != session.id || latest.state != "RUNNING" ||
                latest.effectiveMs - latest.lastCheckEffectiveMs < latest.checkMinutes * 60_000L) {
                return@withLock null
            }
            val events = dao.eventsBetween(latest.startedWall, System.currentTimeMillis() + 1)
            dao.saveSession(latest.copy(lastCheckEffectiveMs = latest.effectiveMs))
            latest.id to StudyEvidence(
                (latest.effectiveMs / 60_000).toInt(),
                events.count { it.sessionId == latest.id && it.kind == "DISTRACTION" }, 0, 0, 0,
            )
        } ?: return null
        val prompt = try {
            withTimeoutOrNull(CHECK_TIMEOUT_MS) { ai.check(evidence.second) }
                ?: "本次进度检查超时；监督计时继续进行。请主动汇报当前完成情况。"
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            "本次进度检查失败；监督计时继续进行。请主动汇报当前完成情况。"
        }
        return mutex.withLock {
            val latest = dao.currentSession()
            if (latest?.id == session.id && latest.state == "RUNNING") {
                event(latest.id, "CHECK", prompt)
                prompt
            } else {
                null
            }
        }
    }

    suspend fun createSummary(day: LocalDate) {
        val zone = ZoneId.systemDefault()
        val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val segments = dao.segmentsForDay(day.toString())
        val events = dao.eventsBetween(start, end)
        val tasks = dao.tasks().first().filter { it.day == day.toString() }
        val content = ai.summary(StudyEvidence(
            (segments.sumOf { it.durationMs } / 60_000).toInt(),
            events.count { it.kind == "DISTRACTION" },
            events.count { it.kind == "EARLY_END" || it.kind == "EMERGENCY_END" },
            tasks.count { it.isCompleted }, tasks.size,
        ))
        dao.saveSummary(StudySummary(day.toString(), content, System.currentTimeMillis()))
    }

    suspend fun exportBackup(appVersion: String): String {
        val payload = database.withTransaction {
            val storedTasks = dao.tasksOnce()
            val taskIds = storedTasks.map { it.id }.toSet()
            StudyBackupPayload(
                profile = dao.profilesOnce().firstOrNull()?.let {
                    BackupProfile(
                        id = it.id, goal = it.goal, subjects = it.subjects, examDay = it.examDay,
                        dailyMinutes = it.dailyMinutes, progressPercent = it.progressPercent,
                        checkMinutes = it.checkMinutes, blockedPackages = it.blockedPackages,
                        allowedPackages = it.allowedPackages,
                    )
                },
                tasks = storedTasks.map {
                    BackupTask(
                        it.id, it.title, it.subject, it.day, it.plannedMinutes, it.source,
                        it.isCompleted, it.createdAt, it.completedAt,
                    )
                },
                sessions = dao.sessionsOnce().map {
                    BackupSession(
                        it.id, it.subject, it.mode, it.targetMs, it.state, it.startedWall,
                        it.lastWall, it.lastElapsed, it.effectiveMs, it.endedWall, it.endReason,
                        it.blockedPackages, it.allowedPackages, it.checkMinutes, it.lastCheckEffectiveMs,
                        it.linkedTaskId?.takeIf(taskIds::contains),
                    )
                },
                segments = dao.segmentsOnce().map {
                    BackupSegment(it.id, it.sessionId, it.subject, it.day, it.startedWall, it.durationMs, it.capability)
                },
                events = dao.eventsOnce().map {
                    BackupEvent(it.id, it.sessionId, it.kind, it.atWall, it.detail)
                },
                summaries = dao.summariesOnce().map {
                    BackupSummary(it.day, it.content, it.generatedWall, it.source)
                },
            )
        }
        return StudyBackupCodec.encode(payload, System.currentTimeMillis(), appVersion)
    }

    suspend fun restoreBackup(text: String): StudyBackupSummary = mutex.withLock {
        require(!runtime.serviceAlive && runtime.current.value == null) {
            "请先结束当前学习，再恢复备份"
        }
        require(dao.currentSession() == null) { "请先结束现有学习会话，再恢复备份" }
        val payload = StudyBackupCodec.normalizeForRestore(
            StudyBackupCodec.decode(text), System.currentTimeMillis(),
        )
        database.withTransaction {
            dao.deleteAllEvents()
            dao.deleteAllSegments()
            dao.deleteAllSummaries()
            dao.deleteAllTasks()
            dao.deleteAllSessions()
            dao.deleteProfile()
            val taskIds = payload.tasks.associate { task ->
                task.id to dao.insertTask(task.toEntity())
            }
            dao.insertSessions(payload.sessions.map {
                it.toEntity().copy(linkedTaskId = it.linkedTaskId?.let(taskIds::getValue))
            })
            dao.insertSegmentsOnce(payload.segments.map { it.toEntity().copy(id = 0) })
            dao.insertEvents(payload.events.map { it.toEntity().copy(id = 0) })
            dao.insertSummaries(payload.summaries.map { it.toEntity() })
            payload.profile?.let { dao.saveProfile(it.toEntity()) }
        }
        runtime.current.value = null
        runtime.setLinkedTask(null)
        runtime.suspendRestrictions()
        runtime.status.value = "学习数据已从备份恢复；限制未生效"
        StudyBackupSummary(
            payload.tasks.size, payload.sessions.size, payload.segments.size,
            payload.events.size, payload.summaries.size,
        )
    }

    private fun BackupProfile.toEntity() = StudyProfile(
        id = 1, goal = goal, subjects = subjects, examDay = examDay,
        dailyMinutes = dailyMinutes, progressPercent = progressPercent,
        checkMinutes = checkMinutes, blockedPackages = blockedPackages,
        allowedPackages = allowedPackages,
    )

    private fun BackupTask.toEntity() = StudyTask(
        title = title, subject = subject, day = day,
        plannedMinutes = plannedMinutes, source = source,
        isCompleted = isCompleted, createdAt = createdAt, completedAt = completedAt,
    )

    private fun BackupSession.toEntity() = StudySession(
        id = id, subject = subject, mode = mode, targetMs = targetMs, state = state,
        startedWall = startedWall, lastWall = lastWall, lastElapsed = lastElapsed,
        effectiveMs = effectiveMs, endedWall = endedWall, endReason = endReason,
        blockedPackages = blockedPackages, allowedPackages = allowedPackages,
        checkMinutes = checkMinutes, lastCheckEffectiveMs = lastCheckEffectiveMs,
        linkedTaskId = linkedTaskId,
    )

    private fun BackupSegment.toEntity() = StudySegment(
        sessionId = sessionId, subject = subject, day = day,
        startedWall = startedWall, durationMs = durationMs, capability = capability,
    )

    private fun BackupEvent.toEntity() = StudyEvent(
        sessionId = sessionId, kind = kind, atWall = atWall, detail = detail,
    )

    private fun BackupSummary.toEntity() = StudySummary(
        day = day, content = content, generatedWall = generatedWall, source = source,
    )

    companion object {
        private const val CHECK_TIMEOUT_MS = 20_000L
    }
}
