package com.focusforceplus.app.study.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.focusforceplus.app.study.core.StudyDurationPresets
import com.focusforceplus.app.study.core.packageSet
import com.focusforceplus.app.study.core.remainingDurationLabel
import com.focusforceplus.app.study.core.studyDurationLabel
import com.focusforceplus.app.study.core.suggestedStudySubjects
import com.focusforceplus.app.study.data.StudyTask
import java.time.LocalDate

internal data class StudyStartDraft(
    val subject: String = "",
    val minutes: String = "25",
    val taskId: Long? = null,
) : java.io.Serializable {
    companion object {
        fun forTask(task: StudyTask, creditedMs: Long): StudyStartDraft {
            val remaining = (task.plannedMinutes * 60_000L - creditedMs).coerceAtLeast(0)
            val minutes = if (remaining > 0) ((remaining + 59_999) / 60_000).toString() else ""
            return StudyStartDraft(task.subject, minutes, task.id)
        }
    }
}

@Composable
internal fun LearningPage(
    viewModel: StudyViewModel,
    draft: StudyStartDraft,
    onDraftChange: (StudyStartDraft) -> Unit,
    onUsageSettings: () -> Unit,
    onPlan: () -> Unit,
    onSettings: () -> Unit,
    listState: LazyListState = rememberLazyListState(),
    anchors: SpotlightAnchorState = remember { SpotlightAnchorState() },
) {
    val session by viewModel.runtime.current.collectAsState()
    val linkedTaskId by viewModel.runtime.linkedTaskId.collectAsState()
    val status by viewModel.runtime.status.collectAsState()
    val profile by viewModel.profile.collectAsState()
    val tasks by viewModel.tasks.collectAsState()
    val feedback by viewModel.sessionFeedback.collectAsState()
    val taskTimes by viewModel.taskStudyTimes.collectAsState()
    val usage by viewModel.usageGranted.collectAsState()
    val accessibility by viewModel.runtime.accessibilityConnected.collectAsState()
    val colors = StudyTheme.colors
    val todayStr = LocalDate.now().toString()
    val subject = draft.subject
    val minutes = draft.minutes
    var mode by rememberSaveable { mutableStateOf("PAPER") }
    var reason by rememberSaveable { mutableStateOf("") }
    var progress by rememberSaveable { mutableStateOf("") }

    val current = session
    val selectedCount = packageSet(current?.blockedPackages ?: profile?.blockedPackages.orEmpty()).size
    val learningCount = packageSet(current?.allowedPackages ?: profile?.allowedPackages.orEmpty()).size
    val todayPendingTasks = remember(tasks, todayStr) {
        tasks.filter { it.day == todayStr && !it.isCompleted }
    }
    val suggestedSubjects = remember(profile?.subjects, tasks, todayStr) {
        suggestedStudySubjects(
            profileSubjects = profile?.subjects.orEmpty(),
            pendingTodaySubjects = tasks.filter { it.day == todayStr && !it.isCompleted }.map { it.subject },
            allTaskSubjects = tasks.map { it.subject },
        )
    }
    val selectedTask = draft.taskId?.let { id -> tasks.firstOrNull { it.id == id && !it.isCompleted } }
    val addingTime = selectedTask != null && (taskTimes[selectedTask.id] ?: 0L) >= selectedTask.plannedMinutes * 60_000L

    PageList(state = listState, modifier = Modifier.testTag("study-learning-list")) {
        item {
            Text(
                text = if (current == null) "为下一段学习做好准备" else current.subject.ifBlank { "本次学习" },
                style = MaterialTheme.typography.headlineSmall,
                color = colors.ink,
                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
            )
        }
        item {
            StudyPanel {
                val state = when {
                    !usage -> "尚未具备监督权限" to StudyTone.Danger
                    current == null -> "未开始学习，限制未生效" to StudyTone.Neutral
                    current.state == "RUNNING" -> "学习中 · 正在监督" to StudyTone.Accent
                    current.state == "PAUSED" -> "已暂停 · 限制已暂时解除" to StudyTone.Neutral
                    else -> "监督中断，请手动恢复" to StudyTone.Danger
                }
                StatusPill(state.first, state.second)
                val statusLine = status.takeIf {
                    it.isNotBlank() && it != "未开始学习，限制未生效" && it != "正在启动学习监督"
                }
                Text(
                    statusLine ?: when {
                        !usage -> "使用情况访问未授予：无法累计受监督时长，也无法记录受限应用访问。"
                        current == null -> "确认科目和目标后开始；开始前不会限制任何应用。"
                        current.state == "RUNNING" -> "正在按已确认区间计时；暂停、中断和无法确认的时间不补计。"
                        current.state == "PAUSED" -> "计时已停止，本次限制已解除；恢复后从下一段已确认区间继续累计。"
                        else -> "监督已中断，限制已解除；请手动恢复或结束本次学习。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted,
                    modifier = Modifier.padding(top = 10.dp),
                )
                if (usage) {
                    Text(
                        if (accessibility) "使用情况访问已授予 · 无障碍已连接" else "使用情况访问已授予 · 基础提醒",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.muted,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                Text(
                    "已选择 $selectedCount 个受限应用" +
                        "、$learningCount 个计时学习应用" +
                        "；结束或紧急退出后解除本次限制。",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted,
                    modifier = Modifier.padding(top = 6.dp),
                )
                StudyTextAction(
                    text = if (!usage) "去设置监督权限" else "管理受限应用与权限",
                    onClick = onSettings,
                    modifier = Modifier.padding(top = 2.dp).spotlightAnchor(anchors, "spotlight-permissions"),
                )
            }
        }
        if (current == null) {
            item {
                StudyPanel {
                    Text(
                        "本次学习",
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.ink,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                    if (todayPendingTasks.isNotEmpty()) {
                        Text(
                            "今日待学任务（点选带入科目与时长）",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.muted,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 10.dp),
                            modifier = Modifier.fillMaxWidth().testTag("study-today-tasks"),
                        ) {
                            items(todayPendingTasks, key = { it.id }) { row ->
                                val active = selectedTask?.id == row.id
                                StudyQuickChip(
                                    label = "${row.title} · ${row.plannedMinutes}分",
                                    selected = active,
                                    onClick = {
                                        if (active) {
                                            onDraftChange(draft.copy(taskId = null))
                                        } else {
                                            onDraftChange(StudyStartDraft.forTask(row, taskTimes[row.id] ?: 0L))
                                        }
                                    },
                                    modifier = Modifier.widthIn(max = 220.dp),
                                )
                            }
                        }
                    }
                    StudyField(
                        value = subject,
                        label = "本次学习科目",
                        onChange = { input ->
                            val clean = input.take(60)
                            onDraftChange(draft.copy(subject = clean, taskId = selectedTask?.takeIf { it.subject == clean.trim() }?.id))
                        },
                        modifier = Modifier.testTag("study-subject-field"),
                    )
                    if (suggestedSubjects.isNotEmpty()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(top = 8.dp),
                            modifier = Modifier.fillMaxWidth().testTag("study-subject-chips"),
                        ) {
                            items(suggestedSubjects, key = { it }) { chipSubject ->
                                val isCurrent = subject.trim() == chipSubject
                                StudyQuickChip(
                                    label = chipSubject,
                                    selected = isCurrent,
                                    onClick = {
                                        onDraftChange(draft.copy(subject = chipSubject, taskId = selectedTask?.takeIf { it.subject == chipSubject }?.id))
                                    },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    StudyField(
                        value = minutes,
                        label = if (addingTime) "已学满，追加多少分钟？" else "目标有效学习时长（分钟，1–720）",
                        onChange = { onDraftChange(draft.copy(minutes = it.take(3))) },
                        numeric = true,
                        error = if (minutes.isNotBlank() && minutes.toIntOrNull() !in 1..720) "请输入1–720分钟" else null,
                        modifier = Modifier.testTag("study-minutes-field"),
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(top = 8.dp),
                        modifier = Modifier.fillMaxWidth().testTag("study-duration-presets"),
                    ) {
                        items(if (addingTime) listOf(5, 10, 15) else StudyDurationPresets, key = { it }) { preset ->
                            StudyQuickChip(
                                label = "${preset}分钟",
                                selected = minutes.trim() == preset.toString(),
                                onClick = { onDraftChange(draft.copy(minutes = preset.toString())) },
                            )
                        }
                    }
                    if (selectedTask != null) {
                        if (addingTime) StudyTextAction("直接标记任务完成", {
                            viewModel.markComplete(selectedTask)
                            onDraftChange(StudyStartDraft())
                        })
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "已关联：${selectedTask.title} · 已累计 ${studyDurationLabel(taskTimes[selectedTask.id] ?: 0L)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.accent,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            StudyTextAction(
                                text = "取消关联",
                                onClick = { onDraftChange(draft.copy(taskId = null)) },
                                tone = StudyTone.Neutral,
                            )
                        }
                    }
                    SegmentedControl(
                        options = listOf("纸质书学习", "手机学习"),
                        selected = if (mode == "PAPER") 0 else 1,
                        onSelect = { mode = if (it == 0) "PAPER" else "PHONE" },
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Text(
                        if (mode == "PAPER") "锁屏不算分心。仅在监督服务持续运行的已确认区间累计时长。"
                        else "只累计 StudyGuard 和已选学习应用中的时间。锁屏不算分心，也不累计手机学习时长。",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.muted,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    StudyPrimaryButton(
                        text = if (usage) "开始学习" else "去开启监督权限",
                        onClick = { if (usage) viewModel.start(subject, mode, minutes, selectedTask?.id) else onUsageSettings() },
                        enabled = !usage || (status != "正在启动学习监督" && subject.isNotBlank() && minutes.toIntOrNull() in 1..720),
                        modifier = Modifier.padding(top = 14.dp).spotlightAnchor(anchors, "spotlight-start"),
                    )
                    StudySecondaryButton("创建学习计划", onPlan, modifier = Modifier.padding(top = 8.dp))
                }
            }
        } else {
            val linkedTask = linkedTaskId?.let { id -> tasks.firstOrNull { it.id == id } }
            val sessionFeedback = feedback?.takeIf { it.sessionId == current.id }
            val sessionDistractions = sessionFeedback?.distractions ?: 0
            item {
                StudyPanel {
                    Text("已记录有效学习", style = MaterialTheme.typography.labelSmall, color = colors.muted)
                    HeroDuration(timeLabel(current.effectiveMs), modifier = Modifier.padding(top = 2.dp))
                    Text(
                        "目标 ${current.targetMs / 60_000} 分钟 · " +
                            "${remainingDurationLabel(current.effectiveMs, current.targetMs)} · " +
                            "${if (current.mode == "PAPER") "纸质书" else "手机"}学习",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.muted,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    ProgressBar(
                        fraction = (current.effectiveMs.toFloat() / current.targetMs.coerceAtLeast(1)),
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Text(
                        buildString {
                            append("受限应用访问 $sessionDistractions 次")
                            if (linkedTask != null) append(" · 关联任务：${linkedTask.title}")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (sessionDistractions > 0) colors.danger else colors.muted,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        "暂停、中断和无法确认的区间不计时。记录时长不等于证明认真学习。",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.muted,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            item {
                StudyPanel {
                    StudySecondaryButton(
                        text = if (current.state == "RUNNING") "暂停并暂时解除限制" else "恢复监督与计时",
                        onClick = { if (current.state == "RUNNING") viewModel.pause() else viewModel.resume() },
                    )
                    Text(
                        sessionFeedback?.latestCheck ?: "本地监督：请专注完成一个具体任务。每 ${current.checkMinutes} 分钟有效学习检查一次，系统调度可能延迟。",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.muted,
                        modifier = Modifier.padding(top = 14.dp, bottom = 10.dp),
                    )
                    StudyField(progress, "目前完成了什么？（自报进度）", { progress = it.take(500) }, singleLine = false)
                    StudyPrimaryButton(
                        text = "记录进度",
                        onClick = { viewModel.reportProgress(progress); progress = "" },
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
            item {
                var completeLinked by remember(current.id, linkedTask?.id) { mutableStateOf(false) }
                StudyPanel {
                    Text("提前结束", style = MaterialTheme.typography.titleLarge, color = colors.ink)
                    Text(
                        "结束时立即解除本次限制，并记录结束原因。",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.muted,
                        modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
                    )
                    StudyField(reason, "提前结束原因（可不填）", { reason = it.take(500) }, singleLine = false)
                    if (linkedTask != null && !linkedTask.isCompleted) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp)
                                .heightIn(min = 44.dp)
                                .toggleable(value = completeLinked, role = Role.Checkbox) { completeLinked = it },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = completeLinked,
                                onCheckedChange = null,
                                colors = CheckboxDefaults.colors(
                                    checkedColor = colors.accent,
                                    uncheckedColor = colors.lineStrong,
                                    checkmarkColor = colors.surface,
                                ),
                            )
                            Text(
                                text = "同时将「${linkedTask.title}」标为已完成",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.ink,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 6.dp),
                            )
                        }
                    }
                    StudySecondaryButton(
                        text = "提前结束并解除限制",
                        onClick = { viewModel.finish(false, reason, completeLinked) },
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
            item {
                StudyPanel {
                    Text("安全出口", style = MaterialTheme.typography.titleLarge, color = colors.ink)
                    Text(
                        "无需密码或等待。紧急退出会记录事件，不会阻止通信、系统设置或卸载。",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.muted,
                        modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
                    )
                    StudyDangerButton("紧急解除所有学习限制", { viewModel.finish(true, "") })
                }
            }
        }
        item { Spacer(Modifier.height(4.dp)) }
    }
}

private fun timeLabel(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1_000
    return "%02d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
}
