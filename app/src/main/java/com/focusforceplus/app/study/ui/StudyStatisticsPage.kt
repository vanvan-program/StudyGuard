package com.focusforceplus.app.study.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.focusforceplus.app.study.core.DailyStatistics
import com.focusforceplus.app.study.core.StudyStatisticsSnapshot
import com.focusforceplus.app.study.core.studyDurationLabel
import com.focusforceplus.app.study.core.studyEventLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.Instant
import java.time.format.DateTimeFormatter

@Composable
internal fun StudyStatisticsPage(viewModel: StudyViewModel, onStart: () -> Unit, onPlan: () -> Unit) {
    val result by viewModel.statistics.collectAsState()
    val filter by viewModel.statisticsFilter.collectAsState()
    val events by viewModel.events.collectAsState()
    val summaries by viewModel.summaries.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val colors = StudyTheme.colors
    var subjectMenu by remember { mutableStateOf(false) }
    var showDefinitions by rememberSaveable { mutableStateOf(false) }
    val snapshot = result?.takeIf { it.filter == filter }
    LaunchedEffect(Unit) {
        while (isActive) {
            viewModel.refreshStatisticsDate()
            delay(30_000)
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxWidth().imePadding().testTag("study-statistics-list"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp, end = 20.dp, top = 16.dp, bottom = 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            StudyPanel {
                SegmentedControl(
                    options = listOf("今日", "近 7 日", "近 30 日"),
                    selected = when (filter.days) {
                        1 -> 0
                        7 -> 1
                        else -> 2
                    },
                    onSelect = { index ->
                        viewModel.selectStatisticsRange(listOf(1, 7, 30)[index])
                    },
                )
                Box(Modifier.padding(top = 10.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 46.dp)
                            .border(1.dp, colors.line, RoundedCornerShape(6.dp))
                            .background(colors.surface, RoundedCornerShape(6.dp))
                            .clickable { subjectMenu = true }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "科目：${filter.subject ?: "全部科目"}",
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.ink,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    DropdownMenu(
                        expanded = subjectMenu,
                        onDismissRequest = { subjectMenu = false },
                        modifier = Modifier.heightIn(max = 360.dp),
                        containerColor = colors.surface,
                    ) {
                        DropdownMenuItem(
                            text = { Text("全部科目", color = colors.ink) },
                            onClick = { viewModel.selectStatisticsSubject(null); subjectMenu = false },
                        )
                        result?.availableSubjects.orEmpty().forEach { subject ->
                            DropdownMenuItem(
                                text = { Text(subject, color = colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                                onClick = { viewModel.selectStatisticsSubject(subject); subjectMenu = false },
                            )
                        }
                    }
                }
            }
        }
        if (snapshot == null) {
            item {
                StudyPanel {
                    Text("正在整理学习记录", style = MaterialTheme.typography.titleMedium, color = colors.muted)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                            .height(2.dp)
                            .clip(CircleShape)
                            .background(colors.control),
                    ) {
                        Box(
                            Modifier.fillMaxWidth(0.35f).fillMaxHeight().background(colors.accent),
                        )
                    }
                }
            }
        } else {
            item {
                StudyPanel {
                    Text("有效学习", style = MaterialTheme.typography.labelSmall, color = colors.muted)
                    HeroDuration(studyDurationLabel(snapshot.totalMs), modifier = Modifier.padding(top = 2.dp))
                    val change = snapshot.totalMs - snapshot.previousMs
                    val comparisonText = when {
                        snapshot.previousMs == 0L -> "前 ${filter.days} 日暂无有效学习记录"
                        change == 0L -> "与前 ${filter.days} 日有效学习时长相同"
                        change > 0 -> "比前 ${filter.days} 日多 ${studyDurationLabel(change)}"
                        else -> "比前 ${filter.days} 日少 ${studyDurationLabel(-change)}"
                    }
                    Text(
                        comparisonText,
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            snapshot.previousMs == 0L -> colors.muted
                            change > 0 -> colors.success
                            change < 0 -> colors.muted
                            else -> colors.muted
                        },
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Text(
                        "${snapshot.from} 至 ${filter.today} · 今日尚未结束",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.muted,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    StudyDivider(Modifier.padding(top = 14.dp, bottom = 6.dp))
                    Row(Modifier.fillMaxWidth()) {
                        MetricCell("任务完成", snapshot.completionPercent?.let {
                            "${snapshot.completedTasks}/${snapshot.totalTasks} · $it%"
                        } ?: "尚无安排", Modifier.weight(1f))
                        MetricCell("日均有效学习", studyDurationLabel(snapshot.averageMs), Modifier.weight(1f))
                    }
                    StudyDivider(Modifier.padding(vertical = 2.dp))
                    Row(Modifier.fillMaxWidth()) {
                        MetricCell("达标学习日", "${snapshot.activeDays}/${filter.days} 天", Modifier.weight(1f))
                        MetricCell("连续打卡", "${snapshot.streak} 天", Modifier.weight(1f))
                    }
                    StudyDivider(Modifier.padding(vertical = 2.dp))
                    Row(Modifier.fillMaxWidth()) {
                        MetricCell("访问受限应用", "${snapshot.distractions} 次", Modifier.weight(1f))
                        MetricCell("提前结束", "${snapshot.earlyEnds} 次", Modifier.weight(1f))
                    }
                    StudyDivider(Modifier.padding(vertical = 2.dp))
                    Row(Modifier.fillMaxWidth()) {
                        MetricCell("监督中断", "${snapshot.interruptions} 次", Modifier.weight(1f))
                        MetricCell("统计范围", "${filter.days} 天", Modifier.weight(1f))
                    }
                }
            }
            if (snapshot.totalMs == 0L) {
                item {
                    StudyPanel {
                        StudyEmptyState(
                            title = "这个范围还没有有效学习记录",
                            body = "开始一段学习，或切换日期范围、科目查看已有记录。暂停和无法确认的时间不会补入统计。",
                        )
                        StudyPrimaryButton("去开始学习", onStart)
                        StudySecondaryButton("安排学习任务", onPlan, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
            item { SectionHeading("每日趋势", modifier = Modifier.padding(top = 12.dp)) }
            item {
                Text(
                    "点选日期查看明细；30 日图可左右滑动。",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                DailyTrend(snapshot)
            }
            item { SectionHeading("各科学习", modifier = Modifier.padding(top = 12.dp)) }
            if (snapshot.subjects.isEmpty()) {
                item { Text("尚无可分配到科目的有效时长。", color = colors.muted,
                    style = MaterialTheme.typography.bodySmall) }
            }
            items(snapshot.subjects, key = { it.subject }) { subject ->
                val share = subject.durationMs.toDouble() / snapshot.totalMs.coerceAtLeast(1)
                StudyPanel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(subject.subject, style = MaterialTheme.typography.titleMedium, color = colors.ink,
                            modifier = Modifier.weight(1f))
                        Text(
                            studyDurationLabel(subject.durationMs),
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.ink,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            " · ${(share * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.muted,
                        )
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 9.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(colors.control)
                            .semantics {
                                contentDescription =
                                    "${subject.subject}，占所选范围有效学习时长的${(share * 100).toInt()}%"
                            },
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(share.toFloat().coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .background(colors.accent),
                        )
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(role = Role.Button) { showDefinitions = !showDefinitions }
                        .semantics { stateDescription = if (showDefinitions) "已展开" else "已收起" }
                        .padding(horizontal = 4.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (showDefinitions) "收起统计口径" else "了解统计口径",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.accent,
                    )
                }
            }
            if (snapshot.invalidRecords > 0) {
                item {
                    StudyPanel {
                        Text(
                            "发现 ${snapshot.invalidRecords} 条日期或时长异常的历史记录，已排除，原始数据未改动。",
                            color = colors.danger,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            if (showDefinitions) {
                item {
                    StudyPanel {
                        Text(
                            "日均按所选范围的全部自然日计算，包括零记录日。达标学习日要求至少 1 分钟有效学习。" +
                                "连续打卡按当前科目统计，不受范围长度限制；今日未达标时接续昨日。" +
                                "任务完成率按任务安排日期及当前完成状态计算，不代表当天实际完成日期。" +
                                "分心次数表示受限应用访问，不代表心理状态；提前结束按结束时间归入日期。" +
                                "历史有效分段保留记录当时的日期；事件按当前系统时区归日。" +
                                "无记录表示没有已保存的有效时长，不证明当天没有学习。",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.muted,
                        )
                    }
                }
            }
            item { SectionHeading("每日总结 · 本地规则", modifier = Modifier.padding(top = 12.dp)) }
            item {
                Text(
                    "总结基于全科记录；下列内容按日期范围筛选。",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                StudySecondaryButton("根据记录生成今日总结", viewModel::summarize, enabled = !busy)
            }
            val rangedSummaries = summaries.filter {
                it.day >= snapshot.from.toString() && it.day <= filter.today.toString()
            }
            if (rangedSummaries.isEmpty()) {
                item { Text("该范围尚无每日总结，可生成今日总结。", color = colors.muted,
                    style = MaterialTheme.typography.bodySmall) }
            }
            items(rangedSummaries, key = { it.day }) { summary ->
                StudyPanel {
                    Text(summary.day, style = MaterialTheme.typography.titleSmall, color = colors.muted)
                    Text(summary.content, style = MaterialTheme.typography.bodyMedium, color = colors.ink,
                        modifier = Modifier.padding(top = 4.dp))
                }
            }
            item { SectionHeading("最近监督事件", modifier = Modifier.padding(top = 12.dp)) }
            val byId = events.associateBy { it.id }
            val rangedEvents = snapshot.recentEventIds.mapNotNull(byId::get)
            if (rangedEvents.isEmpty()) {
                item { Text("所选范围与科目暂无监督事件。", color = colors.muted,
                    style = MaterialTheme.typography.bodySmall) }
            }
            items(rangedEvents, key = { it.id }) { event ->
                StudyPanel {
                    val timestamp = Instant.ofEpochMilli(event.atWall).atZone(filter.zone)
                        .format(DateTimeFormatter.ofPattern("MM-dd HH:mm:ss"))
                    Text("$timestamp · ${studyEventLabel(event.kind)}",
                        style = MaterialTheme.typography.titleSmall, color = colors.ink)
                    Text(event.detail, style = MaterialTheme.typography.bodySmall, color = colors.muted,
                        modifier = Modifier.padding(top = 3.dp))
                }
            }
        }
    }
}

@Composable
private fun MetricCell(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = StudyTheme.colors
    Column(modifier = modifier.padding(vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.muted)
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            color = colors.ink,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

@Composable
internal fun DailyTrend(snapshot: StudyStatisticsSnapshot) {
    val colors = StudyTheme.colors
    var selectedDay by rememberSaveable(snapshot.filter) { mutableStateOf(snapshot.filter.today.toString()) }
    val scroll = rememberScrollState()
    val maximum = snapshot.daily.maxOfOrNull { it.durationMs }?.coerceAtLeast(1) ?: 1L
    val fits = snapshot.daily.size <= 7
    LaunchedEffect(snapshot.filter, scroll.maxValue, fits) {
        if (!fits) scroll.scrollTo(scroll.maxValue)
    }
    StudyPanel {
        Text(
            "柱高代表有效学习时长 · 峰值 ${studyDurationLabel(snapshot.daily.maxOfOrNull { it.durationMs } ?: 0L)}",
            style = MaterialTheme.typography.labelSmall,
            color = colors.muted,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (fits) Modifier else Modifier.horizontalScroll(scroll)),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            snapshot.daily.forEach { day ->
                val selected = selectedDay == day.day.toString()
                Column(
                    modifier = Modifier
                        .then(if (fits) Modifier.weight(1f) else Modifier.width(48.dp))
                        .selectable(
                            selected = selected,
                            role = Role.RadioButton,
                            onClick = { selectedDay = day.day.toString() },
                        )
                        .semantics(mergeDescendants = true) {
                            contentDescription = "${day.day}，有效学习${studyDurationLabel(day.durationMs)}"
                        }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier.height(112.dp).fillMaxWidth(),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        if (day.durationMs > 0) {
                            Box(
                                modifier = Modifier
                                    .width(18.dp)
                                    .height((day.durationMs.toDouble() / maximum * 112).toFloat().coerceAtLeast(3f).dp)
                                    .background(colors.accent, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)),
                            )
                        }
                    }
                    Text(
                        day.day.format(DateTimeFormatter.ofPattern("M/d")),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) colors.accent else colors.muted,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .then(
                                if (selected) {
                                    Modifier
                                        .background(colors.accentSoft, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                } else {
                                    Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                },
                            ),
                    )
                }
            }
        }
        StudyDivider(Modifier.padding(top = 4.dp, bottom = 10.dp))
        val selected = snapshot.daily.firstOrNull { it.day.toString() == selectedDay } ?: snapshot.daily.last()
        DayDetail(selected)
    }
}

@Composable
private fun DayDetail(day: DailyStatistics) {
    val colors = StudyTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "${day.day} · ${studyDurationLabel(day.durationMs)}",
            style = MaterialTheme.typography.titleMedium,
            color = colors.ink,
        )
        Text(
            if (day.totalTasks == 0) "当日暂无安排任务" else "安排任务完成 ${day.completedTasks}/${day.totalTasks}",
            style = MaterialTheme.typography.bodySmall,
            color = colors.muted,
        )
        Text(
            "受限应用访问 ${day.distractions} 次 · 提前结束 ${day.earlyEnds} 次",
            style = MaterialTheme.typography.bodySmall,
            color = colors.muted,
        )
    }
}
