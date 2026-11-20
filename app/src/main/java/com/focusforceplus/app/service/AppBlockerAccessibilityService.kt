package com.focusforceplus.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.focusforceplus.app.study.core.packageSet
import com.focusforceplus.app.study.data.StudyRepository
import com.focusforceplus.app.study.monitor.StudyRuntime
import com.focusforceplus.app.study.ui.StudyBlockedActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AppBlockerAccessibilityService : AccessibilityService() {
    @Inject lateinit var runtime: StudyRuntime
    @Inject lateinit var repository: StudyRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var lastShownElapsed = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        runtime.accessibilityConnected.value = true
        scope.launch {
            combine(repository.dao.profile(), runtime.lease) { profile, lease ->
                (lease?.blocked ?: packageSet(profile?.blockedPackages.orEmpty())) + packageName
            }.distinctUntilChanged().collect { packages ->
                serviceInfo = serviceInfo?.apply { packageNames = packages.toTypedArray() }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val target = event.packageName?.toString() ?: return
        if (!runtime.shouldBlock(target)) return
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastShownElapsed < 1_000) return
        lastShownElapsed = now
        if (!runtime.shouldBlock(target)) return
        performGlobalAction(GLOBAL_ACTION_HOME)
        if (!runtime.shouldBlock(target)) return
        runCatching {
            startActivity(Intent(this, StudyBlockedActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("blockedPackage", target)
            })
        }.onFailure {
            runtime.status.value = "已尝试返回桌面；提醒界面未能显示，拦截能力受限"
        }
    }

    override fun onInterrupt() {
        runtime.accessibilityConnected.value = false
    }

    override fun onUnbind(intent: Intent?): Boolean {
        runtime.accessibilityConnected.value = false
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        runtime.accessibilityConnected.value = false
        scope.cancel()
        super.onDestroy()
    }
}
