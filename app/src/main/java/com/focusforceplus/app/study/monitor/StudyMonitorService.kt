package com.focusforceplus.app.study.monitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.focusforceplus.app.study.data.StudyRepository
import com.focusforceplus.app.study.data.StudySession
import com.focusforceplus.app.study.core.MonitorState
import com.focusforceplus.app.study.core.MonitorTransition
import com.focusforceplus.app.study.core.StudyTimeline
import com.focusforceplus.app.study.core.packageSet
import com.focusforceplus.app.study.ui.StudyGuardActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@AndroidEntryPoint
class StudyMonitorService : Service() {
    @Inject lateinit var repository: StudyRepository
    @Inject lateinit var runtime: StudyRuntime
    @Inject lateinit var applicationScope: CoroutineScope
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var monitorJob: Job? = null
    private var checkJob: Job? = null
    private var token = 0L
    private var ended = false
    private var observedState = MonitorState(null, true, false)
    private var queryCursor = 0L
    private var lastNotificationElapsed = 0L
    private var ownedSessionId: String? = null
    private var observationWakeLock: PowerManager.WakeLock? = null
    private var wakeLockRenewedElapsed = 0L

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null || intent.action !in setOf(START, RESUME)) {
            stopSelf()
            return START_NOT_STICKY
        }
        monitorJob?.cancel()
        token = intent.getLongExtra("token", -1)
        if (!runtime.isCurrentRequest(token)) {
            stopSelf()
            return START_NOT_STICKY
        }
        ended = false
        runtime.serviceAlive = true
        createChannels()
        val notification = ongoing("正在准备学习监督")
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else startForeground(NOTIFICATION_ID, notification)
        monitorJob = scope.launch {
            try {
                val taskId = intent.getLongExtra("taskId", -1L).takeIf { it > 0L }
                val session = if (intent.action == RESUME) repository.resume(token) else repository.start(
                    intent.getStringExtra("subject") ?: "学习",
                    intent.getStringExtra("mode") ?: "PAPER",
                    intent.getIntExtra("minutes", 25),
                    token,
                    taskId,
                )
                if (session == null) {
                    ended = true
                    stopSelf()
                    return@launch
                }
                ownedSessionId = session.id
                queryCursor = System.currentTimeMillis()
                observedState = MonitorState(
                    packageName,
                    (getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive,
                    runtime.isLocked(),
                )
                while (runtime.isCurrentRequest(token)) {
                    val current = runtime.current.value ?: break
                    maintainObservationWakeLock(current.state == "RUNNING" && runtime.hasUsageAccess())
                    if (current.state == "RUNNING") {
                        if (!runtime.hasUsageAccess()) {
                            maintainObservationWakeLock(false)
                            repository.interrupt("使用情况访问权限已撤销；未累计后续时间，限制已解除")
                        } else {
                            val nowWall = System.currentTimeMillis()
                            val intervalState = observedState
                            val scan = scanUsageEvents(nowWall)
                            val blocked = packageSet(current.blockedPackages)
                            val allowed = packageSet(current.allowedPackages)
                            val protected = runtime.protectedPackages()
                            val timeline = StudyTimeline.evaluate(
                                current.lastWall, nowWall, intervalState, scan,
                                current.mode, packageName, blocked, allowed, protected,
                            )
                            observedState = timeline.finalState
                            timeline.blockedEntries.forEach { entry ->
                                repository.event(
                                    current.id, "DISTRACTION",
                                    "进入受限应用：${entry.packageName}", entry.atWall,
                                )
                            }
                            val capability = if (runtime.accessibilityConnected.value) "ACCESSIBILITY_CONNECTED" else "USAGE_EVENTS_ONLY"
                            val next = repository.checkpoint(token, timeline.ranges, capability, nowWall)
                            if (next?.state != "RUNNING") maintainObservationWakeLock(false)
                            if (timeline.blockedEntries.isNotEmpty()) {
                                showReminder("记录到受限应用访问。请返回当前学习任务。", 7203)
                            }
                            if (next?.state == "RUNNING") {
                                val locked = timeline.finalState.keyguard || !timeline.finalState.interactive
                                val foreground = timeline.finalState.packageName
                                runtime.status.value = when {
                                    current.mode == "PHONE" && locked -> "手机学习已锁屏：不累计有效时长；限制仍在"
                                    current.mode == "PAPER" && locked -> "纸书学习锁屏中：锁屏本身不算分心；仍在观察受限应用"
                                    foreground == null -> "前台应用未知：此区间不累计有效学习"
                                    foreground != packageName && foreground in blocked &&
                                        foreground !in allowed && foreground !in protected ->
                                        "检测到受限应用：$foreground；请立即返回学习任务"
                                    current.mode == "PHONE" && foreground != packageName && foreground !in allowed ->
                                        "当前不在学习应用中：此区间不计时"
                                    runtime.accessibilityConnected.value -> "使用情况监测中；无障碍已连接，尽力拦截"
                                    else -> "使用情况监测中；仅通知提醒，无强拦截"
                                }
                                if (next.effectiveMs - next.lastCheckEffectiveMs >= next.checkMinutes * 60_000L &&
                                    checkJob?.isActive != true) {
                                    checkJob = scope.launch {
                                        repository.checkIfDue(next)?.let { showReminder(it, 7202) }
                                    }
                                }
                                if (next.effectiveMs >= next.targetMs) {
                                    ended = true
                                    repository.finish("COMPLETED", "达到目标有效时长")
                                    repository.createSummary(LocalDate.now())
                                    stopSelf()
                                    break
                                }
                            }
                        }
                    }
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastNotificationElapsed >= 10_000) {
                        lastNotificationElapsed = now
                        val label = runtime.current.value?.let {
                            "${it.subject} · 有效 ${it.effectiveMs / 60_000} 分钟 · ${runtime.status.value}"
                        } ?: runtime.status.value
                        manager().notify(NOTIFICATION_ID, ongoing(label))
                    }
                    delay(2_000)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                maintainObservationWakeLock(false)
                runtime.suspendRestrictions()
                runtime.status.value = "监督启动或运行失败，限制已解除；请检查权限后重试"
                runCatching { repository.interrupt("监督异常：${failure.javaClass.simpleName}，未知时间未计入") }
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun scanUsageEvents(now: Long): List<MonitorTransition> {
        if (now < queryCursor) {
            queryCursor = now
            return emptyList()
        }
        val usage = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val events = usage.queryEvents(queryCursor, now)
            ?: throw IllegalStateException("UsageEvents unavailable")
        queryCursor = now
        val event = UsageEvents.Event()
        var state = observedState
        val transitions = mutableListOf<MonitorTransition>()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    val nextPackage = event.packageName ?: continue
                    val transition = MonitorTransition(event.timeStamp, true, nextPackage)
                    transitions += transition
                    state = state.copy(packageName = nextPackage)
                }
                UsageEvents.Event.ACTIVITY_PAUSED -> if (event.packageName == state.packageName) {
                    transitions += MonitorTransition(event.timeStamp, true, null)
                    state = state.copy(packageName = null)
                }
                UsageEvents.Event.SCREEN_INTERACTIVE -> {
                    transitions += MonitorTransition(event.timeStamp, interactive = true)
                    state = state.copy(interactive = true)
                }
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                    transitions += MonitorTransition(event.timeStamp, interactive = false)
                    state = state.copy(interactive = false)
                }
                UsageEvents.Event.KEYGUARD_SHOWN -> {
                    transitions += MonitorTransition(event.timeStamp, keyguard = true)
                    state = state.copy(keyguard = true)
                }
                UsageEvents.Event.KEYGUARD_HIDDEN -> {
                    transitions += MonitorTransition(event.timeStamp, keyguard = false)
                    state = state.copy(keyguard = false)
                }
            }
        }
        val power = getSystemService(Context.POWER_SERVICE) as PowerManager
        val interactive = power.isInteractive
        val keyguard = runtime.isLocked()
        if (interactive != state.interactive || keyguard != state.keyguard) {
            transitions += MonitorTransition(now, interactive = interactive, keyguard = keyguard)
            state = state.copy(interactive = interactive, keyguard = keyguard)
        }
        observedState = state
        return transitions
    }

    private fun createChannels() {
        manager().createNotificationChannel(NotificationChannel(CHANNEL, "学习监督状态", NotificationManager.IMPORTANCE_LOW))
        manager().createNotificationChannel(NotificationChannel(REMINDER_CHANNEL, "学习进度检查", NotificationManager.IMPORTANCE_DEFAULT))
    }

    private fun manager() = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun maintainObservationWakeLock(needed: Boolean) {
        if (!needed) {
            observationWakeLock?.let { lock -> if (lock.isHeld) runCatching { lock.release() } }
            return
        }
        val now = SystemClock.elapsedRealtime()
        val lock = observationWakeLock ?: (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:StudyGuardObservation")
            .also { it.setReferenceCounted(false); observationWakeLock = it }
        if (!lock.isHeld || now - wakeLockRenewedElapsed >= 30_000) {
            lock.acquire(60_000)
            wakeLockRenewedElapsed = now
        }
    }

    private fun openApp() = PendingIntent.getActivity(
        this, 0, Intent(this, StudyGuardActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun ongoing(text: String): Notification = NotificationCompat.Builder(this, CHANNEL)
        .setSmallIcon(android.R.drawable.ic_menu_edit)
        .setContentTitle("StudyGuard · 学习监督")
        .setContentText(text)
        .setStyle(NotificationCompat.BigTextStyle().bigText(text))
        .setContentIntent(openApp())
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .addAction(0, "紧急结束并解除", PendingIntent.getBroadcast(
            this, 1, Intent(this, StudyStopReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        ))
        .build()

    private fun showReminder(text: String, id: Int) {
        runCatching {
            manager().notify(id, NotificationCompat.Builder(this, REMINDER_CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_edit)
                .setContentTitle("StudyGuard · 请汇报进度")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(openApp()).setAutoCancel(true).build())
        }
    }

    override fun onDestroy() {
        maintainObservationWakeLock(false)
        runtime.serviceAlive = false
        runtime.suspendRestrictions()
        scope.cancel()
        val id = ownedSessionId
        val kind = runtime.stopKind
        val reason = runtime.stopReason
        if (!ended && id != null) applicationScope.launch {
            if (kind != null) repository.finish(kind, reason, id)
            else repository.interrupt("监督服务已停止，未补计停止后的时间；请手动恢复", id)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val START = "studyguard.START"
        private const val RESUME = "studyguard.RESUME"
        private const val CHANNEL = "studyguard_session"
        private const val REMINDER_CHANNEL = "studyguard_check"
        private const val NOTIFICATION_ID = 7201

        fun start(
            context: Context,
            runtime: StudyRuntime,
            subject: String,
            mode: String,
            minutes: Int,
            resume: Boolean = false,
            taskId: Long? = null,
        ) {
            val token = runtime.beginRequest()
            try {
                context.startForegroundService(Intent(context, StudyMonitorService::class.java).apply {
                    action = if (resume) RESUME else START
                    putExtra("token", token)
                    putExtra("subject", subject)
                    putExtra("mode", mode)
                    putExtra("minutes", minutes)
                    putExtra("taskId", taskId ?: -1L)
                })
            } catch (failure: Exception) {
                runtime.release()
                runtime.status.value = "无法启动监督服务，请返回应用检查权限"
                throw failure
            }
        }
    }
}

@AndroidEntryPoint
class StudyStopReceiver : BroadcastReceiver() {
    @Inject lateinit var runtime: StudyRuntime
    @Inject lateinit var repository: StudyRepository
    @Inject lateinit var applicationScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val id = runtime.current.value?.id
        runtime.release("EMERGENCY_END", "通知栏紧急退出")
        context.stopService(Intent(context, StudyMonitorService::class.java))
        context.stopService(Intent(context, com.focusforceplus.app.service.FocusForegroundService::class.java))
        com.focusforceplus.app.service.RoutineForegroundService.cancel(context)
        val pending = goAsync()
        applicationScope.launch {
            try {
                if (id != null) repository.finish("EMERGENCY_END", "通知栏紧急退出", id)
                repository.createSummary(LocalDate.now())
            } finally {
                pending.finish()
            }
        }
    }
}
