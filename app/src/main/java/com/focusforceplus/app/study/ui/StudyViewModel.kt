package com.focusforceplus.app.study.ui

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.SystemClock
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusforceplus.app.study.ai.PlannedTask
import com.focusforceplus.app.study.backup.StudyBackupCodec
import com.focusforceplus.app.study.core.StatisticsFilter
import com.focusforceplus.app.study.core.StatisticsSegment
import com.focusforceplus.app.study.core.StatisticsTask
import com.focusforceplus.app.study.core.StatisticsSession
import com.focusforceplus.app.study.core.StatisticsEvent
import com.focusforceplus.app.study.core.StudyStatistics
import com.focusforceplus.app.study.core.throttleWhile
import com.focusforceplus.app.study.data.StudyProfile
import com.focusforceplus.app.study.data.StudyRepository
import com.focusforceplus.app.study.data.StudyTask
import com.focusforceplus.app.study.data.PendingTaskReview
import com.focusforceplus.app.study.monitor.StudyMonitorService
import com.focusforceplus.app.study.monitor.StudyRuntime
import com.focusforceplus.app.util.InstalledApp
import com.focusforceplus.app.util.InstalledAppsHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class StudyViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    val repository: StudyRepository,
    val runtime: StudyRuntime,
    private val installedApps: InstalledAppsHelper,
) : ViewModel() {
    private val sharing = SharingStarted.WhileSubscribed(5_000)
    val profile = repository.dao.profile().stateIn(viewModelScope, sharing, null)
    val tasks = repository.dao.tasks().stateIn(viewModelScope, sharing, emptyList())
    val sessionFeedback = repository.dao.sessionFeedback().stateIn(viewModelScope, sharing, null)
    val taskStudyTimes = repository.dao.taskStudyTimes().map { rows -> rows.associate { it.taskId to it.effectiveMs } }
        .stateIn(viewModelScope, sharing, emptyMap())
    val pendingTaskReview = repository.dao.pendingTaskReview().stateIn(viewModelScope, sharing, null)

    fun reviewTask(review: PendingTaskReview, complete: Boolean) = action { repository.reviewTask(review, complete) }
    val summaries = repository.dao.summaries().stateIn(viewModelScope, sharing, emptyList())
    val statisticsFilter = MutableStateFlow(StatisticsFilter())
    private val throttledSegments = repository.dao.segments().throttleWhile(
        intervalMs = 5_000L,
        shouldThrottle = runtime.current.map { it?.state == "RUNNING" },
        initialThrottling = runtime.current.value?.state == "RUNNING",
        elapsedMs = { SystemClock.elapsedRealtime() },
    )
    private val statisticsSource = combine(
        throttledSegments,
        repository.dao.tasks(),
        repository.dao.sessions(),
        repository.dao.recentEvents(),
    ) { storedSegments, storedTasks, storedSessions, storedEvents ->
        StatisticsSource(storedSegments, storedTasks, storedSessions, storedEvents)
    }
    val statistics = combine(statisticsSource, statisticsFilter) { source, filter ->
        StudyStatistics.calculate(
            filter,
            source.segments.map { StatisticsSegment(it.day, it.subject, it.durationMs) },
            source.tasks.map { StatisticsTask(it.day, it.subject, it.isCompleted) },
            source.sessions.map { StatisticsSession(it.id, it.subject, it.state, it.endedWall) },
            source.events.map { StatisticsEvent(it.id, it.sessionId, it.kind, it.atWall, it.detail) },
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, sharing, null)
    val apps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val appsLoading = MutableStateFlow(false)
    val draft = MutableStateFlow<List<PlannedTask>>(emptyList())
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    val usageGranted = MutableStateFlow(runtime.hasUsageAccess())
    val notificationsEnabled = MutableStateFlow(NotificationManagerCompat.from(context).areNotificationsEnabled())
    val appVersion = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "unknown"

    init {
        action {
            repository.recover()
            repository.createSummary(LocalDate.now().minusDays(1))
        }
    }

    fun ensureAppsLoaded(forceRefresh: Boolean = false) {
        if (appsLoading.value) return
        if (!forceRefresh && apps.value.isNotEmpty()) return
        appsLoading.value = true
        action {
            try {
                val protected = runtime.protectedPackages()
                apps.value = installedApps.getInstalledApps(forceRefresh)
                    .filter { it.packageName !in protected }
            } finally {
                appsLoading.value = false
            }
        }
    }

    private fun action(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                message.value = failure.message ?: "操作未完成，请重试"
            }
        }
    }

    fun refresh() {
        refreshStatisticsDate()
        usageGranted.value = runtime.hasUsageAccess()
        notificationsEnabled.value = NotificationManagerCompat.from(context).areNotificationsEnabled()
        action {
            repository.recover()
            val session = runtime.current.value
            if (session?.state == "RUNNING" && (!usageGranted.value ||
                    SystemClock.elapsedRealtime() - runtime.lastHeartbeatElapsed > 5_000)) {
                runtime.suspendRestrictions()
                repository.interrupt("权限或监督心跳中断；未知时间未计入")
            }
        }
    }

    fun selectStatisticsRange(days: Int) {
        require(days in setOf(1, 7, 30))
        statisticsFilter.update { it.copy(days = days, today = LocalDate.now(), zone = ZoneId.systemDefault()) }
    }

    fun selectStatisticsSubject(subject: String?) {
        statisticsFilter.update { it.copy(subject = subject) }
    }

    fun refreshStatisticsDate() {
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        statisticsFilter.update { if (it.today == today && it.zone == zone) it else it.copy(today = today, zone = zone) }
    }

    fun saveProfile(profile: StudyProfile) = action {
        repository.saveProfile(profile)
        message.value = "设置已保存；应用选择将在下一次学习会话生效"
    }

    fun generate(profile: StudyProfile) {
        if (busy.value) return
        busy.value = true
        action {
            try {
                repository.saveProfile(profile)
                draft.value = repository.ai.plan(profile, LocalDate.now())
                message.value = "已生成本地规则草案，请检查后加入任务"
            } finally { busy.value = false }
        }
    }

    fun acceptDraft() = action {
        val added = repository.acceptPlan(draft.value)
        draft.value = emptyList()
        message.value = "已加入 $added 项任务；已有同日同科任务已保留"
    }

    suspend fun loadAppIcon(packageName: String): Drawable? = installedApps.loadIcon(packageName)

    fun toggleTask(row: StudyTask) = action { repository.toggleTask(row.id) }
    fun deleteTask(row: StudyTask) = action { repository.deleteTask(row.id) }
    fun addTask(title: String, subject: String, day: String, minutes: String, onAdded: () -> Unit = {}) = action {
        repository.addTask(title, subject, day, minutes.toIntOrNull() ?: error("请输入有效分钟数"))
        onAdded()
        message.value = "已添加学习任务"
    }
    fun editTask(row: StudyTask, title: String, subject: String, day: String, minutes: String) = action {
        repository.updateTask(row.id, title, subject, day, minutes.toIntOrNull() ?: error("请输入有效分钟数"))
    }
    fun removeDraftTask(index: Int) {
        draft.update { list -> list.filterIndexed { i, _ -> i != index } }
    }
    fun updateDraftTask(index: Int, updated: PlannedTask) {
        draft.update { list -> list.mapIndexed { i, item -> if (i == index) updated else item } }
    }

    fun start(subject: String, mode: String, minutes: String, taskId: Long? = null) {
        if (busy.value || runtime.status.value == "正在启动学习监督") return
        if (!runtime.hasUsageAccess()) {
            usageGranted.value = false
            message.value = "请先开启使用情况访问权限"
            return
        }
        val duration = minutes.toIntOrNull()
        if (subject.isBlank() || duration !in 1..720) {
            message.value = "请填写科目，学习时长为 1–720 分钟"
            return
        }
        if (runtime.current.value != null) {
            message.value = "请先结束或恢复当前会话"
            return
        }
        action { StudyMonitorService.start(context, runtime, subject, mode, duration!!, taskId = taskId) }
    }

    fun pause() {
        runtime.release()
        action {
            repository.pause()
            context.stopService(Intent(context, StudyMonitorService::class.java))
        }
    }

    fun resume() = action {
        val session = runtime.current.value ?: error("没有可恢复的学习会话")
        require(runtime.hasUsageAccess()) { "请先授予使用情况访问权限" }
        require(session.state != "RUNNING") { "会话已经运行中" }
        StudyMonitorService.start(
            context = context,
            runtime = runtime,
            subject = session.subject,
            mode = session.mode,
            minutes = (session.targetMs / 60_000L).toInt(),
            resume = true,
        )
    }

    fun finish(emergency: Boolean, reason: String, completeLinkedTask: Boolean? = null) {
        val kind = if (emergency) "EMERGENCY_END" else "EARLY_END"
        val id = runtime.current.value?.id
        val linkedId = runtime.linkedTaskId.value
        runtime.release(kind, reason, completeLinkedTask)
        context.stopService(Intent(context, StudyMonitorService::class.java))
        if (emergency) {
            context.stopService(Intent(context, com.focusforceplus.app.service.FocusForegroundService::class.java))
            com.focusforceplus.app.service.RoutineForegroundService.cancel(context)
        }
        action {
            if (id != null) {
                repository.finish(kind, reason, id, completeLinkedTask)
            } else if (completeLinkedTask == true && linkedId != null) {
                repository.completeTask(linkedId)
            }
            repository.createSummary(LocalDate.now())
        }
    }

    fun reportProgress(text: String) = action {
        require(text.isNotBlank()) { "请填写完成情况" }
        val id = runtime.current.value?.id ?: error("请先开始学习")
        repository.event(id, "SELF_REPORT", "用户自报：${text.take(500)}")
        message.value = "已记录自报进度；请继续完成具体任务"
    }

    fun summarize() = action {
        repository.createSummary(LocalDate.now())
        message.value = "已根据现有记录生成本地规则总结"
    }

    fun exportBackup(uri: Uri) {
        if (busy.value) return
        busy.value = true
        action {
            try {
                val bytes = withContext(Dispatchers.Default) {
                    repository.exportBackup(appVersion).toByteArray(Charsets.UTF_8).also {
                        require(it.size <= StudyBackupCodec.MAX_BYTES) { "备份超过 25 MB，暂不能导出" }
                    }
                }
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")?.use {
                        it.write(bytes)
                    } ?: error("无法写入所选文件")
                }
                message.value = "学习数据已导出到所选文件"
            } finally {
                busy.value = false
            }
        }
    }

    fun restoreBackup(uri: Uri) {
        if (busy.value) return
        if (runtime.current.value != null || runtime.serviceAlive) {
            message.value = "请先结束当前学习，再恢复备份"
            return
        }
        busy.value = true
        action {
            try {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(8_192)
                        var total = 0
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            if (total > StudyBackupCodec.MAX_BYTES) error("备份文件超过 25 MB 限制")
                            output.write(buffer, 0, read)
                        }
                        output.toString(Charsets.UTF_8.name())
                    } ?: error("无法读取所选文件")
                }
                val summary = withContext(Dispatchers.Default) { repository.restoreBackup(text) }
                message.value = "恢复完成：${summary.tasks} 个学习任务、${summary.sessions} 次会话、" +
                    "${summary.segments} 个有效分段、${summary.events} 条事件、${summary.summaries} 份总结"
            } finally {
                busy.value = false
            }
        }
    }
}

private data class StatisticsSource(
    val segments: List<com.focusforceplus.app.study.data.StudySegment>,
    val tasks: List<StudyTask>,
    val sessions: List<com.focusforceplus.app.study.data.StudySession>,
    val events: List<com.focusforceplus.app.study.data.StudyEvent>,
)
