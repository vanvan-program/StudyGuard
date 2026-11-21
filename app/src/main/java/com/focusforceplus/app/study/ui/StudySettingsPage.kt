package com.focusforceplus.app.study.ui

import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import com.focusforceplus.app.study.core.StudyBlockedPresets
import com.focusforceplus.app.study.core.packageSet
import com.focusforceplus.app.study.core.sortByPinnedOrder
import com.focusforceplus.app.study.data.StudyProfile
import com.focusforceplus.app.util.InstalledApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
internal fun SettingsPage(
    viewModel: StudyViewModel,
    onUsage: () -> Unit,
    onAccessibility: () -> Unit,
    onNotifications: () -> Unit,
    onLegacy: () -> Unit,
    onReplaySpotlight: () -> Unit = {},
) {
    val stored by viewModel.profile.collectAsState()
    val apps by viewModel.apps.collectAsState()
    val appsLoading by viewModel.appsLoading.collectAsState()
    val usage by viewModel.usageGranted.collectAsState()
    val connected by viewModel.runtime.accessibilityConnected.collectAsState()
    val notifications by viewModel.notificationsEnabled.collectAsState()
    val session by viewModel.runtime.current.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val colors = StudyTheme.colors
    val profile = stored ?: StudyProfile()
    var check by rememberSaveable(stored?.checkMinutes) { mutableStateOf(profile.checkMinutes.toString()) }
    var choosing by rememberSaveable { mutableStateOf<String?>(null) }
    var accessibilityDisclosure by rememberSaveable { mutableStateOf(false) }
    val pendingRestore by viewModel.restorePreview.collectAsState()
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(viewModel::exportBackup)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::previewBackup)
    }

    LaunchedEffect(Unit) {
        viewModel.ensureAppsLoaded()
    }

    LaunchedEffect(choosing) {
        if (choosing != null) viewModel.ensureAppsLoaded()
    }

    PageList(modifier = Modifier.testTag("study-settings-list")) {
        item { GroupLabel("监督与权限", modifier = Modifier.padding(top = 4.dp)) }
        item {
            StudyPanel(contentPadding = PaddingValues(0.dp)) {
                PanelRow(
                    title = "设置使用情况访问权限",
                    description = if (usage) "已授予。仅读取应用切换事件，保存在本机；撤销后解除限制并停止累计。"
                    else "未授予，不能累计受监督时长。仅读取应用切换事件，保存在本机。",
                    trailing = { StatusPill(if (usage) "已授予" else "未授予", if (usage) StudyTone.Success else StudyTone.Danger) },
                    onClick = onUsage,
                )
                PanelRow(
                    title = "了解并设置无障碍增强",
                    description = if (connected) "已连接。关闭后仍可通过使用情况访问记录受限应用访问。"
                    else "未连接，仅通知提醒。普通应用不能系统级禁用其他应用。",
                    trailing = { StatusPill(if (connected) "已连接" else "未连接", if (connected) StudyTone.Accent else StudyTone.Neutral) },
                    onClick = { accessibilityDisclosure = true },
                )
                PanelRow(
                    title = "允许或设置通知",
                    description = "用于学习状态、进度检查和紧急解除。关闭后检查记录仍会保存，但可能看不到系统提醒。",
                    trailing = {
                        StatusPill(
                            if (notifications) "已开启" else "已关闭",
                            if (notifications) StudyTone.Success else StudyTone.Danger,
                        )
                    },
                    last = true,
                    onClick = onNotifications,
                )
            }
        }
        item { GroupLabel("应用选择") }
        item {
            StudyPanel(contentPadding = PaddingValues(0.dp)) {
                PanelRow(
                    title = "受限娱乐应用",
                    description = "受限 ${packageSet(profile.blockedPackages).size} 个 · 不受限 " +
                        "${packageSet(profile.exemptPackages).size} 个。仅放行的应用不累计手机学习时间。" +
                        "下次开始学习时生效。",
                    onClick = { choosing = "BLOCKED" },
                )
                PanelRow(
                    title = "允许的学习应用",
                    description = "计时学习应用 ${packageSet(profile.allowedPackages).size} 个。请选择题库、教材等需要计时的应用。旧版混合名单升级后仅放行，需重新勾选学习应用。",
                    last = true,
                    onClick = { choosing = "ALLOWED" },
                )
            }
        }
        item {
            StudyPanel {
                StudyField(check, "进度检查间隔（1–180 分钟有效学习）", { check = it.take(3) }, numeric = true)
                StudyPrimaryButton(
                    text = "保存检查设置",
                    onClick = {
                        viewModel.saveProfile(
                            profile.copy(
                                examDay = profile.examDay.ifBlank { LocalDate.now().plusDays(30).toString() },
                                checkMinutes = check.toIntOrNull() ?: 0,
                            ),
                        )
                    },
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
        item { StudyUpdatePanel(sessionActive = session != null) }
        item { GroupLabel("数据与 AI") }
        item {
            StudyPanel(contentPadding = PaddingValues(0.dp)) {
                PanelRow(
                    title = "重新播放新手引导",
                    description = if (session != null) "结束当前学习后可重新播放；不影响已保存的学习数据。"
                    else "再次显示学习页的 3 步聚光灯引导，不影响已保存的学习数据。",
                    onClick = if (session != null) null else onReplaySpotlight,
                )
                PanelRow(
                    title = "导出完整学习备份",
                    description = "包含设置、任务、会话、有效分段、事件和每日总结。",
                    onClick = {
                        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))
                        exportLauncher.launch("StudyGuard-backup-$stamp.json")
                    },
                )
                PanelRow(
                    title = "从本地备份恢复",
                    description = if (session != null) "结束当前学习后才能恢复备份。" else "会覆盖当前 StudyGuard 学习数据。",
                    last = true,
                    onClick = if (busy || session != null) null else ({
                        importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                    }),
                )
            }
        }
        item {
            StudyPanel {
                Text(
                    "学习数据保存在本机。仅手动检查和下载更新时访问更新源。卸载会删除本应用数据。",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted,
                )
                Text(
                    "计划、督促与总结均为本地规则，未连接在线 AI，不收集 API Key。在线服务须另行确认提供商、费用和上传内容。",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    "后台检查和每日总结可能受系统节电影响延迟。监督中断的未知区间不会补算为有效学习。",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    "备份是未加密 JSON。应用不自行上传，请选择本机存储；云盘文档提供方可能同步所选文件。",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        item { GroupLabel("保留的原版工具") }
        item {
            StudyPanel(contentPadding = PaddingValues(0.dp)) {
                PanelRow(
                    title = "打开原版任务工具",
                    description = "保留原版待办、例行和历史界面；旧独立拦截规则不再执行。工具界面尚未完整汉化。",
                    last = true,
                    onClick = onLegacy,
                )
            }
        }
        item {
            Text(
                "基于 FocusForcePlus（GPLv3）二次开发。保留原始 LICENSE 与归属；构建版本 ${viewModel.appVersion}。",
                style = MaterialTheme.typography.bodySmall,
                color = colors.tertiary,
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp),
            )
        }
    }
    if (accessibilityDisclosure) AlertDialog(
        onDismissRequest = { accessibilityDisclosure = false },
        containerColor = colors.surface,
        shape = RoundedCornerShape(12.dp),
        title = { Text("可选无障碍增强拦截", style = MaterialTheme.typography.headlineMedium, color = colors.ink) },
        text = {
            Text(
                "仅在你手动开始学习后，检测所选娱乐应用的窗口包名，并尝试返回桌面、显示提醒。不读取屏幕文字、输入内容或浏览器页面，不联网。你可随时关闭，结束学习立即解除本次限制。系统与厂商限制可能使拦截失效。是否前往系统设置？",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.muted,
            )
        },
        confirmButton = {
            TextButton(onClick = { accessibilityDisclosure = false; onAccessibility() }) {
                Text("前往设置", color = colors.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = { accessibilityDisclosure = false }) { Text("暂不开启", color = colors.muted) }
        },
    )
    pendingRestore?.let { preview ->
        AlertDialog(
            onDismissRequest = { viewModel.restorePreview.value = null },
            containerColor = colors.surface,
            shape = RoundedCornerShape(12.dp),
            title = { Text("覆盖学习数据并恢复？", style = MaterialTheme.typography.headlineMedium, color = colors.ink) },
            text = {
                Text(
                    "备份日期 " + java.time.Instant.ofEpochMilli(preview.backup.exportedWall)
                        .atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) +
                        "\n备份：${preview.backup.payload.tasks.size} 项任务 · ${com.focusforceplus.app.study.core.studyDurationLabel(preview.backupMs)}" +
                        "\n本机：${preview.localTasks} 项任务 · ${com.focusforceplus.app.study.core.studyDurationLabel(preview.localMs)}" +
                        "\n确认后会覆盖本机学习设置、任务和全部学习记录。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.muted,
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmRestore) {
                    Text("确认覆盖并恢复", color = colors.danger)
                }
            },
            dismissButton = { TextButton(onClick = { viewModel.restorePreview.value = null }) { Text("取消", color = colors.muted) } },
        )
    }
    choosing?.let { kind ->
        val blocked = packageSet(profile.blockedPackages)
        val allowed = packageSet(profile.allowedPackages)
        AppChooser(
            apps = apps,
            loading = appsLoading,
            initial = if (kind == "BLOCKED") blocked else allowed,
            exempt = if (kind == "BLOCKED") packageSet(profile.exemptPackages) else null,
            title = if (kind == "BLOCKED") "受限娱乐应用" else "允许的学习应用",
            loadIcon = viewModel::loadAppIcon,
            onDismiss = { choosing = null },
            onSave = { selectedSet, exemptSet ->
                val next = if (kind == "BLOCKED") {
                    profile.copy(
                        blockedPackages = (selectedSet - exemptSet).sorted().joinToString(","),
                        exemptPackages = exemptSet.sorted().joinToString(","),
                        allowedPackages = (allowed - selectedSet - exemptSet).sorted().joinToString(","),
                    )
                } else {
                    profile.copy(
                        blockedPackages = (blocked - selectedSet).sorted().joinToString(","),
                        allowedPackages = selectedSet.sorted().joinToString(","),
                        exemptPackages = (packageSet(profile.exemptPackages) - selectedSet).sorted().joinToString(","),
                    )
                }
                viewModel.saveProfile(next.copy(examDay = next.examDay.ifBlank { LocalDate.now().plusDays(30).toString() }))
                choosing = null
            },
        )
    }
}

/**
 * 应用多选器。[exempt] 非空时表示这是「受限娱乐应用」：每行除勾选受限外，还能单独标为「不受限」，
 * 两者互斥；「全选」只作用于当前搜索结果。
 */
@Composable
private fun AppChooser(
    apps: List<InstalledApp>,
    loading: Boolean,
    initial: Set<String>,
    exempt: Set<String>?,
    title: String,
    loadIcon: suspend (String) -> Drawable?,
    onDismiss: () -> Unit,
    onSave: (Set<String>, Set<String>) -> Unit,
) {
    val colors = StudyTheme.colors
    var selected by remember { mutableStateOf(initial) }
    var exempted by remember { mutableStateOf(exempt.orEmpty()) }
    var search by rememberSaveable { mutableStateOf("") }
    val hasExempt = exempt != null
    val pinnedOnOpen = remember(initial, exempt) { initial + exempt.orEmpty() }
    val orderedApps = remember(apps, pinnedOnOpen) {
        sortByPinnedOrder(apps, pinnedOnOpen) { it.packageName }
    }
    val categoryMatches = remember(apps) {
        val packages = apps.map { it.packageName }
        StudyBlockedPresets.ALL.mapNotNull { preset ->
            val matched = StudyBlockedPresets.matchPackages(preset, packages)
            if (matched.isEmpty()) null else preset to matched
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(colors.canvas)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface)
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            title,
                            style = MaterialTheme.typography.headlineMedium,
                            color = colors.ink,
                            modifier = Modifier.weight(1f),
                        )
                        StudyTextAction("取消", onDismiss)
                    }
                    Text(
                        if (hasExempt) "受限 ${selected.size} 个 · 不受限 ${exempted.size} 个"
                        else "已选 ${selected.size} 个应用",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.muted,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
            Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                Box(Modifier.padding(top = 14.dp)) {
                    StudyField(search, "搜索应用名或包名", { search = it })
                }
                if (hasExempt && categoryMatches.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(top = 10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(categoryMatches, key = { it.first.id }) { (preset, matched) ->
                            val candidates = matched - exempted
                            if (candidates.isNotEmpty()) {
                                val allChosen = candidates.all { it in selected }
                                StudyQuickChip(
                                    label = "${preset.label} (${candidates.size})",
                                    selected = allChosen,
                                    onClick = {
                                        selected = if (allChosen) selected - candidates else selected + candidates
                                    },
                                )
                            }
                        }
                    }
                }
                val filtered = orderedApps.filter {
                    it.appName.contains(search, true) || it.packageName.contains(search, true)
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StudyTextAction("全选当前 ${filtered.size} 个", {
                        selected = selected + filtered.map { it.packageName }.filterNot { it in exempted }
                    })
                    StudyTextAction("清空", {
                        selected = selected - filtered.map { it.packageName }.toSet()
                    }, tone = StudyTone.Neutral)
                }
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp)) {
                    if (filtered.isEmpty()) {
                        item {
                            if (loading) {
                                StudyEmptyState("正在读取应用列表", "仅列出可启动的非系统必需应用。")
                            } else {
                                StudyEmptyState("未找到可选应用", "系统必需应用不会列入。")
                            }
                        }
                    }
                    items(filtered, key = { it.packageName }) { app ->
                        val isExempt = app.packageName in exempted
                        val isSelected = app.packageName in selected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .toggleable(
                                    value = isSelected,
                                    role = Role.Checkbox,
                                ) { checked ->
                                    selected = if (checked) selected + app.packageName else selected - app.packageName
                                    if (checked && isExempt) exempted = exempted - app.packageName
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = null,
                                colors = CheckboxDefaults.colors(
                                    checkedColor = colors.accent,
                                    uncheckedColor = colors.lineStrong,
                                    checkmarkColor = colors.surface,
                                ),
                            )
                            AppIconPreview(
                                packageName = app.packageName,
                                appName = app.appName,
                                loadIcon = loadIcon,
                                modifier = Modifier.padding(start = 4.dp, end = 10.dp),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        app.appName,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = colors.ink,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, false),
                                    )
                                    if (isExempt) {
                                        StatusPill("不受限", StudyTone.Success, modifier = Modifier.padding(start = 8.dp))
                                    }
                                }
                                Text(
                                    app.packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.muted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (hasExempt) {
                                StudyTextAction(
                                    if (isExempt) "恢复" else "不受限",
                                    {
                                        exempted = if (isExempt) exempted - app.packageName else exempted + app.packageName
                                        if (!isExempt) selected = selected - app.packageName
                                    },
                                    tone = StudyTone.Neutral,
                                )
                            }
                        }
                        StudyDivider()
                    }
                }
                StudyDivider()
                StudyPrimaryButton(
                    "保存",
                    { onSave(selected, exempted) },
                    modifier = Modifier.padding(top = 12.dp, bottom = 16.dp).navigationBarsPadding(),
                )
            }
        }
    }
}

@Composable
private fun AppIconPreview(
    packageName: String,
    appName: String,
    loadIcon: suspend (String) -> Drawable?,
    modifier: Modifier = Modifier,
) {
    val colors = StudyTheme.colors
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = packageName) {
        val drawable = loadIcon(packageName)
        value = if (drawable != null) {
            withContext(Dispatchers.Default) {
                runCatching { drawable.toBitmap(width = 96, height = 96).asImageBitmap() }.getOrNull()
            }
        } else {
            null
        }
    }
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(colors.control),
        contentAlignment = Alignment.Center,
    ) {
        val loaded = bitmap
        if (loaded != null) {
            Image(
                bitmap = loaded,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = appName.firstOrNull()?.uppercaseChar()?.toString() ?: "A",
                style = MaterialTheme.typography.labelMedium,
                color = colors.muted,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
