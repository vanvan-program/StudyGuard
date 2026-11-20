package com.focusforceplus.app.study.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

private val CalendarWeekdayHeaders = listOf("一", "二", "三", "四", "五", "六", "日")
private val DefaultMinMonth: YearMonth = YearMonth.of(2000, 1)
private val DefaultMaxMonth: YearMonth = YearMonth.of(2100, 12)

internal fun parseStudyDate(value: String): LocalDate? =
    runCatching { LocalDate.parse(value.trim()) }.getOrNull()

internal fun weekdayLabel(dayOfWeek: DayOfWeek): String = when (dayOfWeek) {
    DayOfWeek.MONDAY -> "周一"
    DayOfWeek.TUESDAY -> "周二"
    DayOfWeek.WEDNESDAY -> "周三"
    DayOfWeek.THURSDAY -> "周四"
    DayOfWeek.FRIDAY -> "周五"
    DayOfWeek.SATURDAY -> "周六"
    DayOfWeek.SUNDAY -> "周日"
}

/** 格式化已选日期字符串；仅用于界面展示，持久化仍保存 `YYYY-MM-DD`。 */
internal fun formatStudyDateDisplay(value: String, today: LocalDate = LocalDate.now()): String {
    if (value.isBlank()) return ""
    val date = parseStudyDate(value) ?: return value
    val tag = when (date) {
        today -> "今天"
        today.plusDays(1) -> "明天"
        today.minusDays(1) -> "昨天"
        else -> weekdayLabel(date.dayOfWeek)
    }
    return "$date · $tag"
}

internal fun formatCalendarPreview(date: LocalDate?, today: LocalDate = LocalDate.now()): String {
    if (date == null) return "请选择日期"
    val suffix = if (date == today) "（今天）" else ""
    return "${date.year}年${date.monthValue}月${date.dayOfMonth}日 · ${weekdayLabel(date.dayOfWeek)}$suffix"
}

internal fun isDateSelectable(
    date: LocalDate,
    minDate: LocalDate? = null,
    maxDate: LocalDate? = null,
): Boolean = (minDate == null || !date.isBefore(minDate)) && (maxDate == null || !date.isAfter(maxDate))

internal fun clampMonth(month: YearMonth, minMonth: YearMonth, maxMonth: YearMonth): YearMonth = when {
    month < minMonth -> minMonth
    month > maxMonth -> maxMonth
    else -> month
}

internal fun canSelectMonth(
    month: YearMonth,
    minDate: LocalDate? = null,
    maxDate: LocalDate? = null,
): Boolean {
    val minMonth = minDate?.let(YearMonth::from)
    val maxMonth = maxDate?.let(YearMonth::from)
    return (minMonth == null || month >= minMonth) && (maxMonth == null || month <= maxMonth)
}

/**
 * 生成固定 6 行 × 7 列（42 格）的周一至周日网格。
 * 固定 6 行避免翻月时对话框高度跳动导致按钮错位。
 */
internal fun calendarMonthGrid(month: YearMonth): List<LocalDate?> {
    val leading = month.atDay(1).dayOfWeek.value - 1
    val totalDays = month.lengthOfMonth()
    val trailing = (42 - leading - totalDays).coerceAtLeast(0)
    return buildList(42) {
        repeat(leading) { add(null) }
        for (day in 1..totalDays) {
            add(month.atDay(day))
        }
        repeat(trailing) { add(null) }
    }
}

/** 点击弹出日历选择的日期字段，禁止手动输入日期。 */
@Composable
internal fun StudyDateField(
    value: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "点击选择日期",
    onClear: (() -> Unit)? = null,
    error: String? = null,
    today: LocalDate = LocalDate.now(),
) {
    val colors = StudyTheme.colors
    val displayText = formatStudyDateDisplay(value, today)
    val borderColor = if (error != null) colors.danger else colors.line
    val hasClear = onClear != null && value.isNotBlank()

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clip(RoundedCornerShape(6.dp))
                .border(1.dp, borderColor, RoundedCornerShape(6.dp))
                .background(colors.surface, RoundedCornerShape(6.dp))
                .clickable(role = Role.Button, onClick = onClick)
                .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (error != null) colors.danger else colors.muted,
                )
                Text(
                    text = displayText.ifBlank { placeholder },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (value.isBlank()) colors.muted else colors.ink,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (hasClear) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(role = Role.Button, onClick = onClear)
                        .semantics { contentDescription = "清除$label" }
                        .testTag("date-field-clear"),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = null,
                        tint = colors.muted,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.CalendarMonth,
                    contentDescription = "打开日历选择$label",
                    tint = if (error != null) colors.danger else colors.accent,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        if (error != null) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = colors.danger,
                modifier = Modifier.padding(start = 14.dp, top = 4.dp),
            )
        }
    }
}

/** 遵循 StudyGuard 浅/深色卡片规范的纯点选日历弹窗。 */
@Composable
internal fun StudyCalendarDialog(
    title: String,
    initialDate: LocalDate?,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
    minDate: LocalDate? = null,
    maxDate: LocalDate? = null,
    markedDays: Set<LocalDate> = emptySet(),
    onClear: (() -> Unit)? = null,
    clearLabel: String = "全部日期",
    hint: String? = null,
    today: LocalDate = LocalDate.now(),
) {
    val colors = StudyTheme.colors
    val minMonth = minDate?.let(YearMonth::from) ?: DefaultMinMonth
    val maxMonth = maxDate?.let(YearMonth::from) ?: DefaultMaxMonth
    val initialSelected = initialDate?.takeIf { isDateSelectable(it, minDate, maxDate) }
        ?: minDate?.takeIf { isDateSelectable(it, minDate, maxDate) }
        ?: today.takeIf { isDateSelectable(it, minDate, maxDate) }

    var draftDate by remember(initialDate, minDate, maxDate) { mutableStateOf(initialSelected) }
    var visibleMonth by remember(initialDate, minDate, maxDate) {
        mutableStateOf(clampMonth(YearMonth.from(initialSelected ?: initialDate ?: today), minMonth, maxMonth))
    }
    var choosingMonth by remember { mutableStateOf(false) }

    val selectedForeground = if (colors.dark) colors.canvas else Color.White
    val todaySelectable = isDateSelectable(today, minDate, maxDate)
    val canGoPrev = if (choosingMonth) visibleMonth.year > minMonth.year else visibleMonth > minMonth
    val canGoNext = if (choosingMonth) visibleMonth.year < maxMonth.year else visibleMonth < maxMonth

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .widthIn(max = 400.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surface)
                .border(1.dp, colors.line, RoundedCornerShape(12.dp))
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .testTag("study-calendar-dialog"),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.ink,
                    modifier = Modifier.weight(1f),
                )
                if (todaySelectable) {
                    Box(
                        modifier = Modifier
                            .defaultMinSize(minHeight = 36.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.accentSoft)
                            .clickable(role = Role.Button) {
                                draftDate = today
                                visibleMonth = clampMonth(YearMonth.from(today), minMonth, maxMonth)
                                choosingMonth = false
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .testTag("calendar-today"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("今天", style = MaterialTheme.typography.labelMedium, color = colors.accent)
                    }
                }
            }
            Text(
                text = formatCalendarPreview(draftDate, today),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.accent,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (hint != null) {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.tertiary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            StudyDivider(Modifier.padding(vertical = 12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        visibleMonth = if (choosingMonth) {
                            clampMonth(visibleMonth.minusYears(1), minMonth, maxMonth)
                        } else {
                            clampMonth(visibleMonth.minusMonths(1), minMonth, maxMonth)
                        }
                    },
                    enabled = canGoPrev,
                    modifier = Modifier.testTag("calendar-prev"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                        contentDescription = if (choosingMonth) "上一年" else "上个月",
                        tint = if (canGoPrev) colors.ink else colors.tertiary.copy(alpha = 0.4f),
                    )
                }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(role = Role.Button) { choosingMonth = !choosingMonth }
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (choosingMonth) "${visibleMonth.year}年" else "${visibleMonth.year}年${visibleMonth.monthValue}月",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.ink,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.testTag("calendar-month-title"),
                    )
                    Icon(
                        imageVector = Icons.Outlined.ArrowDropDown,
                        contentDescription = if (choosingMonth) "返回日历日期" else "切换年份与月份",
                        tint = colors.muted,
                        modifier = Modifier.size(20.dp),
                    )
                }
                IconButton(
                    onClick = {
                        visibleMonth = if (choosingMonth) {
                            clampMonth(visibleMonth.plusYears(1), minMonth, maxMonth)
                        } else {
                            clampMonth(visibleMonth.plusMonths(1), minMonth, maxMonth)
                        }
                    },
                    enabled = canGoNext,
                    modifier = Modifier.testTag("calendar-next"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = if (choosingMonth) "下一年" else "下个月",
                        tint = if (canGoNext) colors.ink else colors.tertiary.copy(alpha = 0.4f),
                    )
                }
            }

            if (choosingMonth) {
                Text(
                    text = "选择年份与月份",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..12).chunked(3).forEach { rowMonths ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            rowMonths.forEach { monthValue ->
                                val candidate = YearMonth.of(visibleMonth.year, monthValue)
                                val enabled = canSelectMonth(candidate, minDate, maxDate)
                                val isCurrent = candidate == visibleMonth
                                val isThisMonth = candidate == YearMonth.from(today)
                                val bg = when {
                                    isCurrent -> colors.accent
                                    isThisMonth && enabled -> colors.accentSoft
                                    enabled -> colors.control
                                    else -> Color.Transparent
                                }
                                val fg = when {
                                    isCurrent -> selectedForeground
                                    !enabled -> colors.tertiary.copy(alpha = 0.4f)
                                    isThisMonth -> colors.accent
                                    else -> colors.ink
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 44.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(bg)
                                        .then(
                                            if (isThisMonth && !isCurrent && enabled) {
                                                Modifier.border(1.dp, colors.accent, RoundedCornerShape(6.dp))
                                            } else {
                                                Modifier
                                            },
                                        )
                                        .clickable(enabled = enabled, role = Role.Button) {
                                            visibleMonth = candidate
                                            choosingMonth = false
                                        }
                                        .testTag("calendar-month-$monthValue"),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "${monthValue}月",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = fg,
                                        fontWeight = if (isCurrent || isThisMonth) FontWeight.SemiBold else FontWeight.Medium,
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp),
                ) {
                    CalendarWeekdayHeaders.forEach { header ->
                        Text(
                            text = header,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.muted,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                val weeks = remember(visibleMonth) { calendarMonthGrid(visibleMonth).chunked(7) }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    weeks.forEach { week ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            week.forEach { date ->
                                if (date == null) {
                                    Spacer(Modifier.weight(1f).height(44.dp))
                                } else {
                                    val isSelected = date == draftDate
                                    val isToday = date == today
                                    val enabled = isDateSelectable(date, minDate, maxDate)
                                    val hasMarker = date in markedDays
                                    val bg = when {
                                        isSelected -> colors.accent
                                        isToday -> colors.accentSoft
                                        else -> Color.Transparent
                                    }
                                    val fg = when {
                                        isSelected -> selectedForeground
                                        !enabled -> colors.tertiary.copy(alpha = 0.4f)
                                        isToday -> colors.accent
                                        else -> colors.ink
                                    }
                                    val description = buildString {
                                        append(date.toString())
                                        if (isToday) append("，今天")
                                        if (hasMarker) append("，有任务")
                                        if (!enabled) append("，不可选")
                                    }
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(2.dp)
                                            .heightIn(min = 42.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(bg)
                                            .then(
                                                if (isToday && !isSelected) {
                                                    Modifier.border(1.dp, colors.accent, RoundedCornerShape(6.dp))
                                                } else {
                                                    Modifier
                                                },
                                            )
                                            .clickable(enabled = enabled, role = Role.Button) {
                                                draftDate = date
                                            }
                                            .semantics {
                                                contentDescription = description
                                                selected = isSelected
                                            }
                                            .testTag("calendar-day-$date"),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = date.dayOfMonth.toString(),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = fg,
                                            fontWeight = if (isSelected || isToday) FontWeight.SemiBold else FontWeight.Normal,
                                            maxLines = 1,
                                        )
                                        if (hasMarker) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.BottomCenter)
                                                    .padding(bottom = 4.dp)
                                                    .size(4.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        when {
                                                            isSelected -> selectedForeground
                                                            !enabled -> colors.tertiary.copy(alpha = 0.4f)
                                                            else -> colors.accent
                                                        },
                                                    ),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            StudyDivider(Modifier.padding(top = 12.dp, bottom = 6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onClear != null) {
                    TextButton(
                        onClick = {
                            onClear()
                            onDismiss()
                        },
                        modifier = Modifier.testTag("calendar-clear"),
                    ) {
                        Text(clearLabel, color = colors.muted)
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("calendar-cancel"),
                ) {
                    Text("取消", color = colors.muted)
                }
                val confirmEnabled = draftDate?.let { isDateSelectable(it, minDate, maxDate) } == true
                TextButton(
                    enabled = confirmEnabled,
                    onClick = {
                        val chosen = draftDate
                        if (chosen != null && isDateSelectable(chosen, minDate, maxDate)) {
                            onConfirm(chosen)
                            onDismiss()
                        }
                    },
                    modifier = Modifier.testTag("calendar-confirm"),
                ) {
                    Text(
                        "确定",
                        color = if (confirmEnabled) colors.accent else colors.tertiary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
