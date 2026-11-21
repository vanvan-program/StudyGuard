package com.focusforceplus.app.study.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import com.focusforceplus.app.study.core.StudyDurationPresets
import com.focusforceplus.app.study.core.suggestedStudySubjects
import com.focusforceplus.app.study.data.StudyProfile
import com.focusforceplus.app.study.data.StudyTask
import java.time.LocalDate

@Composable
internal fun PlanPage(viewModel: StudyViewModel) {
    val stored by viewModel.profile.collectAsState()
    val draft by viewModel.draft.collectAsState()
    val tasks by viewModel.tasks.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val colors = StudyTheme.colors
    val profile = stored ?: StudyProfile()
    var goal by rememberSaveable(stored?.goal) { mutableStateOf(profile.goal) }
    var subjects by rememberSaveable(stored?.subjects) { mutableStateOf(profile.subjects) }
    var exam by rememberSaveable(stored?.examDay) {
        mutableStateOf(profile.examDay.ifBlank { LocalDate.now().plusDays(30).toString() })
    }
    var daily by rememberSaveable(stored?.dailyMinutes) { mutableStateOf(profile.dailyMinutes.toString()) }
    var progressPercent by rememberSaveable(stored?.progressPercent) { mutableStateOf(profile.progressPercent.toString()) }
    var creatingTask by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<StudyTask?>(null) }
    var editingDraftIndex by remember { mutableStateOf<Int?>(null) }
    var filterDay by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var showExamPicker by rememberSaveable { mutableStateOf(false) }
    var showFilterPicker by rememberSaveable { mutableStateOf(false) }
    val today = LocalDate.now()
    val parsedExam = parseStudyDate(exam)
    val examError = when {
        exam.isNotBlank() && parsedExam == null -> "请选择有效日期"
        parsedExam != null && parsedExam.isBefore(today) -> "考试日期不能早于今天，请重新选择"
        else -> null
    }
    val taskDays = remember(tasks) { tasks.mapNotNull { parseStudyDate(it.day) }.toSet() }
    val existingDaySubjects = remember(tasks) { tasks.map { it.day to it.subject }.toSet() }
    val suggestedSubjects = remember(subjects, tasks, today) {
        suggestedStudySubjects(
            profileSubjects = subjects,
            pendingTodaySubjects = tasks.filter { it.day == today.toString() && !it.isCompleted }
                .map { it.subject },
            allTaskSubjects = tasks.map { it.subject },
        )
    }

    PageList(modifier = Modifier.testTag("study-plan-list")) {
        item {
            Text(
                "把目标变成每天可执行的任务",
                style = MaterialTheme.typography.headlineSmall,
                color = colors.ink,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                "当前使用本地规则，未连接在线 AI。根据考试日期和进度生成未来最多 7 天草案，请按实际教材调整。",
                style = MaterialTheme.typography.bodySmall,
                color = colors.muted,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        item {
            StudyPanel {
                Text(
                    "目标与进度",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.ink,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                StudyField(goal, "学习目标", { goal = it.take(500) }, singleLine = false)
                Spacer(Modifier.height(10.dp))
                StudyField(subjects, "科目（用逗号分隔，最多 12 科）", { subjects = it.take(720) })
                Spacer(Modifier.height(10.dp))
                StudyDateField(
                    value = exam,
                    label = "考试日期",
                    onClick = { showExamPicker = true },
                    error = examError,
                    today = today,
                    modifier = Modifier.testTag("study-exam-date-field"),
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1f)) {
                        StudyField(daily, "每日可学习分钟数", { daily = it.take(3) }, numeric = true)
                    }
                    Box(Modifier.weight(1f)) {
                        StudyField(progressPercent, "当前进度（0–100%）", { progressPercent = it.take(3) }, numeric = true)
                    }
                }
                StudyPrimaryButton(
                    text = if (busy) "正在生成" else "生成本地计划草案",
                    enabled = !busy && examError == null,
                    onClick = {
                        viewModel.generate(
                            profile.copy(
                                goal = goal,
                                subjects = subjects,
                                examDay = exam,
                                dailyMinutes = daily.toIntOrNull() ?: 0,
                                progressPercent = progressPercent.toIntOrNull() ?: -1,
                            ),
                        )
                    },
                    modifier = Modifier.padding(top = 14.dp),
                )
            }
        }
        if (draft.isNotEmpty()) {
            item {
                val addableCount = draft
                    .distinctBy { it.day to it.subject }
                    .count { (it.day to it.subject) !in existingDaySubjects }
                val skippedCount = draft.size - addableCount
                StudyPanel {
                    Text("待确认草案 · ${draft.size} 项", style = MaterialTheme.typography.titleLarge, color = colors.ink)
                    Text(
                        "已有同日同科安排会保留，不会被覆盖。加入前可点击单条调整或移除。",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.muted,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    StudyPrimaryButton(
                        text = if (skippedCount == 0) {
                            "加入这 ${draft.size} 项任务"
                        } else {
                            "加入 $addableCount 项新任务（跳过 $skippedCount 项重复）"
                        },
                        onClick = viewModel::acceptDraft,
                        enabled = addableCount > 0,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    StudyTextAction("丢弃草案", { viewModel.draft.value = emptyList() }, tone = StudyTone.Neutral)
                    StudyDivider(Modifier.padding(top = 4.dp))
                    draft.forEachIndexed { index, task ->
                        val duplicate = (task.day to task.subject) in existingDaySubjects
                        Column(Modifier.padding(vertical = 10.dp)) {
                            Text(
                                "${task.day} · ${task.subject} · ${task.minutes} 分钟",
                                style = MaterialTheme.typography.titleSmall,
                                color = if (duplicate) colors.muted else colors.ink,
                            )
                            Text(
                                task.title,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.muted,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                            if (duplicate) {
                                Text(
                                    "已有同日同科任务，加入时将跳过",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.tertiary,
                                    modifier = Modifier.padding(top = 3.dp),
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                StudyTextAction("调整", { editingDraftIndex = index })
                                StudyTextAction("移除", { viewModel.removeDraftTask(index) }, tone = StudyTone.Neutral)
                            }
                        }
                        if (index != draft.lastIndex) StudyDivider()
                    }
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionHeading("我的任务", modifier = Modifier.weight(1f).padding(top = 0.dp))
                StudyTextAction(
                    text = "＋ 新建任务",
                    onClick = { creatingTask = true },
                    modifier = Modifier.testTag("study-add-task"),
                )
            }
        }
        val visible = tasks.filter { filterDay.isBlank() || it.day == filterDay }
        item {
            StudyPanel(contentPadding = PaddingValues(16.dp)) {
                StudyDateField(
                    value = filterDay,
                    label = "按日期筛选",
                    placeholder = "全部日期",
                    onClick = { showFilterPicker = true },
                    onClear = if (filterDay.isNotBlank()) ({ filterDay = "" }) else null,
                    today = today,
                    modifier = Modifier.testTag("study-filter-date-field"),
                )
                if (visible.isNotEmpty()) {
                    val doneCount = visible.count { it.isCompleted }
                    Text(
                        text = "当前显示 ${visible.size} 项 · 已完成 $doneCount 项",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.muted,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
        if (visible.isEmpty()) {
            item {
                StudyPanel {
                    StudyEmptyState(
                        if (filterDay.isBlank()) "还没有学习任务" else "这个日期还没有任务",
                        if (filterDay.isBlank()) "可直接手动新建任务，或生成草案后加入。"
                        else "可直接为此日期新建任务，或切换为全部日期查看。",
                    )
                    StudyPrimaryButton(
                        text = "＋ 手动添加一条任务",
                        onClick = { creatingTask = true },
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    if (filterDay.isNotBlank()) {
                        StudySecondaryButton(
                            text = "查看全部日期",
                            onClick = { filterDay = "" },
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }
        items(visible, key = { it.id }) { row ->
            StudyPanel {
                Row(verticalAlignment = Alignment.Top) {
                    Checkbox(
                        checked = row.isCompleted,
                        onCheckedChange = { viewModel.toggleTask(row) },
                        colors = CheckboxDefaults.colors(
                            checkedColor = colors.accent,
                            uncheckedColor = colors.lineStrong,
                            checkmarkColor = colors.surface,
                        ),
                        modifier = Modifier.padding(end = 4.dp),
                    )
                    Column(Modifier.weight(1f).padding(top = 2.dp)) {
                        Text(
                            row.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (row.isCompleted) colors.muted else colors.ink,
                        )
                        Text(
                            "${row.day} · ${row.subject} · ${row.plannedMinutes} 分钟",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.muted,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    StudyTextAction("编辑与改期", { editing = row })
                    StudyTextAction("删除任务", { viewModel.deleteTask(row) }, tone = StudyTone.Danger)
                }
            }
        }
    }
    if (showExamPicker) {
        StudyCalendarDialog(
            title = "选择考试日期",
            initialDate = parsedExam,
            minDate = today,
            hint = "仅可选择今天及之后的日期",
            onDismiss = { showExamPicker = false },
            onConfirm = { picked -> exam = picked.toString() },
            today = today,
        )
    }
    if (showFilterPicker) {
        StudyCalendarDialog(
            title = "按日期筛选任务",
            initialDate = parseStudyDate(filterDay),
            markedDays = taskDays,
            hint = if (taskDays.isNotEmpty()) "圆点标记表示当天已有学习任务" else null,
            onClear = { filterDay = "" },
            clearLabel = "全部日期",
            onDismiss = { showFilterPicker = false },
            onConfirm = { picked -> filterDay = picked.toString() },
            today = today,
        )
    }
    if (creatingTask) {
        TaskFormDialog(
            dialogKey = "create",
            dialogTitle = "新建学习任务",
            confirmLabel = "添加任务",
            initialTitle = "",
            initialSubject = suggestedSubjects.firstOrNull().orEmpty(),
            initialDay = filterDay.ifBlank { today.toString() },
            initialMinutes = "30",
            suggestedSubjects = suggestedSubjects,
            onDismiss = { creatingTask = false },
            onSave = { title, subject, day, minutes ->
                viewModel.addTask(title, subject, day, minutes) {
                    creatingTask = false
                    if (filterDay.isNotBlank() && filterDay != day) {
                        filterDay = day
                    }
                }
            },
        )
    }
    editing?.let { row ->
        TaskFormDialog(
            dialogKey = "edit-${row.id}",
            dialogTitle = "编辑学习任务",
            confirmLabel = "保存修改",
            initialTitle = row.title,
            initialSubject = row.subject,
            initialDay = row.day,
            initialMinutes = row.plannedMinutes.toString(),
            suggestedSubjects = suggestedSubjects,
            onDismiss = { editing = null },
            onSave = { title, subject, day, minutes ->
                viewModel.editTask(row, title, subject, day, minutes)
                editing = null
            },
        )
    }
    editingDraftIndex?.let { idx ->
        draft.getOrNull(idx)?.let { item ->
            TaskFormDialog(
                dialogKey = "draft-$idx",
                dialogTitle = "调整草案任务",
                confirmLabel = "确认调整",
                initialTitle = item.title,
                initialSubject = item.subject,
                initialDay = item.day,
                initialMinutes = item.minutes.toString(),
                suggestedSubjects = suggestedSubjects,
                onDismiss = { editingDraftIndex = null },
                onSave = { title, subject, day, minutes ->
                    val mins = minutes.toIntOrNull() ?: item.minutes
                    viewModel.updateDraftTask(
                        idx,
                        item.copy(title = title.trim(), subject = subject.trim(), day = day, minutes = mins),
                    )
                    editingDraftIndex = null
                },
            )
        }
    }
}

@Composable
private fun TaskFormDialog(
    dialogKey: String,
    dialogTitle: String,
    confirmLabel: String,
    initialTitle: String,
    initialSubject: String,
    initialDay: String,
    initialMinutes: String,
    suggestedSubjects: List<String>,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit,
) {
    val colors = StudyTheme.colors
    val today = LocalDate.now()
    var title by rememberSaveable(dialogKey) { mutableStateOf(initialTitle) }
    var subject by rememberSaveable(dialogKey) { mutableStateOf(initialSubject) }
    var day by rememberSaveable(dialogKey) { mutableStateOf(initialDay) }
    var minutes by rememberSaveable(dialogKey) { mutableStateOf(initialMinutes) }
    var showDayPicker by rememberSaveable(dialogKey) { mutableStateOf(false) }
    val validDay = parseStudyDate(day) != null
    val canSave = title.isNotBlank() && subject.isNotBlank() && validDay && minutes.toIntOrNull() in 1..720

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        shape = RoundedCornerShape(12.dp),
        title = { Text(dialogTitle, style = MaterialTheme.typography.headlineMedium, color = colors.ink) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StudyField(
                    title,
                    "任务内容",
                    { title = it.take(500) },
                    singleLine = false,
                    modifier = Modifier.testTag("task-dialog-title"),
                )
                StudyField(
                    subject,
                    "科目",
                    { subject = it.take(60) },
                    modifier = Modifier.testTag("task-dialog-subject"),
                )
                if (suggestedSubjects.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(suggestedSubjects, key = { it }) { chipSubject ->
                            StudyQuickChip(
                                label = chipSubject,
                                selected = subject.trim() == chipSubject,
                                onClick = { subject = chipSubject },
                            )
                        }
                    }
                }
                StudyDateField(
                    value = day,
                    label = "日期",
                    onClick = { showDayPicker = true },
                    error = if (!validDay) "请选择有效日期" else null,
                    today = today,
                    modifier = Modifier.testTag("study-task-date-field"),
                )
                StudyField(
                    minutes,
                    "计划分钟数（1–720）",
                    { minutes = it.take(3) },
                    numeric = true,
                    error = if (minutes.isNotBlank() && minutes.toIntOrNull() !in 1..720) "请输入1–720分钟" else null,
                    modifier = Modifier.testTag("task-dialog-minutes"),
                )
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(StudyDurationPresets, key = { it }) { preset ->
                        StudyQuickChip(
                            label = "${preset}分",
                            selected = minutes.trim() == preset.toString(),
                            onClick = { minutes = preset.toString() },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = { onSave(title, subject, day, minutes) },
                modifier = Modifier.testTag("task-dialog-confirm"),
            ) {
                Text(confirmLabel, color = if (canSave) colors.accent else colors.tertiary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("task-dialog-cancel")) {
                Text("取消", color = colors.muted)
            }
        },
    )
    if (showDayPicker) {
        StudyCalendarDialog(
            title = "选择任务日期",
            initialDate = parseStudyDate(day),
            onDismiss = { showDayPicker = false },
            onConfirm = { picked -> day = picked.toString() },
            today = today,
        )
    }
}
