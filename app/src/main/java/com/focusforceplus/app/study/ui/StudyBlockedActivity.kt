package com.focusforceplus.app.study.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay

@AndroidEntryPoint
class StudyBlockedActivity : ComponentActivity() {
    private val viewModel: StudyViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val target = intent.getStringExtra("blockedPackage").orEmpty()
        if (!viewModel.runtime.shouldBlock(target)) {
            finish()
            return
        }
        setContent {
            LaunchedEffect(target) {
                while (viewModel.runtime.shouldBlock(target)) delay(250)
                finish()
            }
            StudyGuardTheme {
                BlockedScreen(
                    onStudy = {
                        startActivity(Intent(this@StudyBlockedActivity, StudyGuardActivity::class.java))
                        finish()
                    },
                    onHome = {
                        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
                        finish()
                    },
                    onEmergency = {
                        viewModel.finish(true, "拦截页面紧急退出")
                        finish()
                    },
                )
            }
        }
    }
}

@Composable
internal fun BlockedScreen(onStudy: () -> Unit, onHome: () -> Unit, onEmergency: () -> Unit) {
    val colors = StudyTheme.colors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .safeDrawingPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.widthIn(max = 460.dp).padding(horizontal = 24.dp)) {
            Column {
                StatusPill("学习中 · 已限制当前应用", StudyTone.Accent)
                Text(
                    "先回到当前学习任务",
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.ink,
                    modifier = Modifier.padding(top = 14.dp),
                )
                Text(
                    "这是你为本次学习选择限制的应用。StudyGuard 只记录应用访问，不知道你是否认真学习。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.muted,
                    modifier = Modifier.padding(top = 8.dp),
                )
                StudyPrimaryButton("返回学习与汇报进度", onStudy, modifier = Modifier.padding(top = 24.dp))
                StudySecondaryButton("返回桌面", onHome, modifier = Modifier.padding(top = 8.dp))
                StudyDangerButton("紧急结束并解除全部限制", onEmergency, modifier = Modifier.padding(top = 8.dp))
                Text(
                    "紧急结束不需要原因，也不会阻止通信、系统设置或卸载。",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.tertiary,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
    }
}
