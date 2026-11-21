package com.focusforceplus.app.study.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.focusforceplus.app.study.update.StudyUpdateModel
import com.focusforceplus.app.study.update.UpdateSource

@Composable
internal fun StudyUpdatePanel(sessionActive: Boolean, model: StudyUpdateModel = viewModel()) {
    val context = LocalContext.current
    val state by model.state.collectAsState()
    var showSources by remember { mutableStateOf(false) }
    fun install() {
        if (sessionActive) { model.status("请先结束当前学习，再安装更新"); return }
        model.prepareInstaller { intent ->
            runCatching { context.startActivity(intent) }.onFailure { model.status("无法打开系统安装器") }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (context.packageManager.canRequestPackageInstalls()) install() else model.status("安装包已保留，可授权后继续")
    }
    StudyPanel {
        Text("应用更新", style = MaterialTheme.typography.titleLarge)
        Text("当前版本 ${model.installedVersion}", style = MaterialTheme.typography.bodyMedium)
        Column {
            StudyTextAction("下载源：${state.mode.label}", { if (!state.busy) showSources = true })
            DropdownMenu(expanded = showSources, onDismissRequest = { showSources = false }) {
                UpdateSource.entries.forEach { source ->
                    DropdownMenuItem(text = { Text(source.label) }, onClick = { model.select(source); showSources = false })
                }
            }
        }
        Text(if (state.mode == UpdateSource.AUTO) "官方线路优先，超时或持续低速时自动换源。" else "仅使用选定线路，失败后可手动换源。", style = MaterialTheme.typography.bodySmall)
        if (state.message.isNotBlank()) Text(state.message + (state.percent?.let { " · $it%" } ?: ""), style = MaterialTheme.typography.bodyMedium)
        if (state.busy) StudySecondaryButton("取消", model::cancel)
        else {
            if (state.ready != null) StudyPrimaryButton("安装更新", {
                when {
                    sessionActive -> model.status("请先结束当前学习，再安装更新")
                    context.packageManager.canRequestPackageInstalls() -> install()
                    else -> runCatching { permission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}"))) }.onFailure { model.status("请在系统设置中允许本应用安装更新") }
                }
            })
            else state.release?.let { StudyPrimaryButton("下载 ${it.version}", model::download) }
            StudySecondaryButton("检查更新", model::check)
        }
    }
}
