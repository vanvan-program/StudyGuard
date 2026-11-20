package com.focusforceplus.app.study.monitor

import android.app.AppOpsManager
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import com.focusforceplus.app.compliance.NEVER_BLOCK_PACKAGES
import com.focusforceplus.app.study.core.RestrictionLease
import com.focusforceplus.app.study.core.packageSet
import com.focusforceplus.app.study.data.StudySession
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

data class ForegroundObservation(val packageName: String, val atElapsed: Long)

data class StudyStopRequest(
    val sessionId: String?,
    val kind: String,
    val reason: String,
    val completeLinkedTask: Boolean?,
)

@Singleton
class StudyRuntime @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val generation = AtomicLong()
    private val _lease = MutableStateFlow<RestrictionLease?>(null)
    val lease = _lease.asStateFlow()
    private val _linkedTaskId = MutableStateFlow<Long?>(null)
    val linkedTaskId = _linkedTaskId.asStateFlow()
    val current = MutableStateFlow<StudySession?>(null)
    val status = MutableStateFlow("未开始学习，限制未生效")
    val accessibilityConnected = MutableStateFlow(false)
    val observations = Channel<ForegroundObservation>(64, BufferOverflow.DROP_OLDEST)
    @Volatile var serviceAlive = false
    @Volatile var lastHeartbeatElapsed = 0L
    @Volatile var stopRequest: StudyStopRequest? = null
        private set
    val stopKind: String? get() = stopRequest?.kind
    val stopReason: String get() = stopRequest?.reason.orEmpty()
    @Volatile private var cachedProtected: Set<String>? = null
    @Volatile private var cachedProtectedElapsed = 0L

    fun beginRequest(): Long {
        stopRequest = null
        status.value = "正在启动学习监督"
        return generation.incrementAndGet()
    }

    fun isCurrentRequest(token: Long): Boolean = token == generation.get()

    fun activate(session: StudySession, token: Long) {
        if (!isCurrentRequest(token)) return
        current.value = session
        setLinkedTask(session.linkedTaskId)
        lastHeartbeatElapsed = SystemClock.elapsedRealtime()
        _lease.value = if (session.state == "RUNNING") RestrictionLease(
            session.id,
            lastHeartbeatElapsed + 5_000,
            packageSet(session.blockedPackages),
            packageSet(session.allowedPackages),
        ) else null
    }

    fun setLinkedTask(taskId: Long?) {
        _linkedTaskId.value = taskId
    }

    fun release(kind: String? = null, reason: String = "", completeLinkedTask: Boolean? = null) {
        if (kind != null) {
            // Publish the complete intent before invalidating the monitoring request.
            stopRequest = StudyStopRequest(current.value?.id, kind, reason.take(500), completeLinkedTask)
            _linkedTaskId.value = null
        }
        generation.incrementAndGet()
        _lease.value = null
        status.value = "本次限制已解除"
    }

    fun suspendRestrictions() {
        _lease.value = null
    }

    fun shouldBlock(packageName: String): Boolean =
        _lease.value?.shouldBlock(packageName, SystemClock.elapsedRealtime(), protectedPackages()) == true &&
            hasUsageAccess()

    fun observe(packageName: String) {
        if (serviceAlive) observations.trySend(ForegroundObservation(packageName, SystemClock.elapsedRealtime()))
    }

    fun protectedPackages(): Set<String> {
        val now = SystemClock.elapsedRealtime()
        cachedProtected?.takeIf { now - cachedProtectedElapsed < 60_000L }?.let { return it }
        val intents = listOf(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            Intent(Settings.ACTION_SETTINGS),
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
            Intent(Intent.ACTION_DIAL),
            Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS),
        )
        val resolved = intents.flatMap { intent ->
            runCatching {
                context.packageManager.queryIntentActivities(intent, 0).map { it.activityInfo.packageName }
            }.getOrDefault(emptyList())
        }
        val communication = listOfNotNull(
            android.provider.Telephony.Sms.getDefaultSmsPackage(context),
            (context.getSystemService(Context.TELECOM_SERVICE) as? android.telecom.TelecomManager)?.defaultDialerPackage,
        )
        val computed = NEVER_BLOCK_PACKAGES + resolved + communication + context.packageName +
            setOf("com.android.permissioncontroller", "com.google.android.permissioncontroller",
                "com.android.packageinstaller", "com.google.android.packageinstaller")
        cachedProtected = computed
        cachedProtectedElapsed = now
        return computed
    }

    fun hasUsageAccess(): Boolean = runCatching {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) ==
            AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    fun isLocked(): Boolean =
        (context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked
}
