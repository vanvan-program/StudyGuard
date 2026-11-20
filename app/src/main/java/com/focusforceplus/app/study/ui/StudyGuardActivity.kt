package com.focusforceplus.app.study.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.focusforceplus.app.MainActivity
import com.focusforceplus.app.study.monitor.StudySummaryWorker
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.TimeUnit

@AndroidEntryPoint
class StudyGuardActivity : ComponentActivity() {
    private val viewModel: StudyViewModel by viewModels()
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            StudySummaryWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<StudySummaryWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(StudySummaryWorker.initialDelayMillis(), TimeUnit.MILLISECONDS)
                .build(),
        )
        setContent {
            val owner = LocalLifecycleOwner.current
            DisposableEffect(owner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
                }
                owner.lifecycle.addObserver(observer)
                onDispose { owner.lifecycle.removeObserver(observer) }
            }
            StudyGuardTheme {
                StudyGuardScreen(
                    viewModel = viewModel,
                    onUsageSettings = { openSettings(Settings.ACTION_USAGE_ACCESS_SETTINGS) },
                    onAccessibilitySettings = { openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS) },
                    onNotifications = {
                        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else openSettings(Settings.ACTION_APP_NOTIFICATION_SETTINGS, notificationSettings = true)
                    },
                    onLegacyTools = { startActivity(Intent(this, MainActivity::class.java)) },
                )
            }
        }
    }

    private fun openSettings(action: String, notificationSettings: Boolean = false) {
        runCatching {
            startActivity(Intent(action).apply {
                if (notificationSettings) putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            })
        }.onFailure {
            viewModel.message.value = "设备未提供此设置入口，请在系统设置中搜索对应权限"
        }
    }
}
