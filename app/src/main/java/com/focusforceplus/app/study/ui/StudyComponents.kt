package com.focusforceplus.app.study.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal enum class StudyTone { Accent, Neutral, Success, Danger }

@Composable
internal fun StudyPanel(
    modifier: Modifier = Modifier,
    contentPadding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = StudyTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, colors.line, RoundedCornerShape(12.dp))
            .background(colors.surface, RoundedCornerShape(12.dp))
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(0.dp),
        content = content,
    )
}

/** 页面内的分节标题：16sp／600，间距上大下小。 */
@Composable
internal fun SectionHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = StudyTheme.colors.ink,
        modifier = modifier.padding(top = 24.dp, bottom = 8.dp),
    )
}

/** 设置页分组标签：12sp／500，位于卡片之外。 */
@Composable
internal fun GroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = StudyTheme.colors.tertiary,
        modifier = modifier.padding(top = 20.dp, bottom = 8.dp, start = 2.dp),
    )
}

@Composable
internal fun StudyDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier, thickness = 1.dp, color = StudyTheme.colors.lineSubtle)
}

/** 卡片内的一行：标题、说明，可带尾部控件；行间用 1px 细分隔线。 */
@Composable
internal fun PanelRow(
    title: String,
    description: String? = null,
    last: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    val colors = StudyTheme.colors
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .defaultMinSize(minHeight = 64.dp)
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            leading?.invoke()
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = colors.ink)
                if (description != null) {
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.muted,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
            if (trailing != null) {
                Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
            } else if (onClick != null) {
                Icon(
                    Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    tint = colors.muted,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        if (!last) HorizontalDivider(thickness = 1.dp, color = colors.lineSubtle)
    }
}

/** 大时长：46sp 数字在上，单位 15sp 灰色。 */
@Composable
internal fun HeroDuration(value: String, modifier: Modifier = Modifier, unit: String? = null) {
    val colors = StudyTheme.colors
    Row(modifier = modifier, verticalAlignment = Alignment.Bottom) {
        Text(
            value,
            style = MaterialTheme.typography.displayMedium,
            color = colors.ink,
            maxLines = 1,
        )
        if (unit != null) {
            Text(
                unit,
                style = MaterialTheme.typography.labelLarge,
                color = colors.muted,
                modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
            )
        }
    }
}

/** 分段开关：control 底、选中项白底 4dp 圆角。 */
@Composable
internal fun SegmentedControl(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = StudyTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, colors.lineSubtle, RoundedCornerShape(6.dp))
            .background(colors.control, RoundedCornerShape(6.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEachIndexed { index, label ->
            val active = index == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .then(
                        if (active) {
                            Modifier
                                .background(colors.surface, RoundedCornerShape(4.dp))
                                .border(1.dp, colors.lineSubtle, RoundedCornerShape(4.dp))
                        } else {
                            Modifier
                        },
                    )
                    .selectable(selected = active, role = Role.Tab) { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (active) colors.ink else colors.muted,
                    fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun ButtonSurface(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    container: Color,
    content: Color,
    borderColor: Color,
    modifier: Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .border(1.dp, if (enabled) borderColor else StudyTheme.colors.line, RoundedCornerShape(6.dp))
            .background(if (enabled) container else StudyTheme.colors.control, RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) content else StudyTheme.colors.tertiary,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** 主操作：浅色近黑实底、深色近白实底，文字取反。 */
@Composable
internal fun StudyPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = StudyTheme.colors
    ButtonSurface(text, onClick, enabled, colors.solid, colors.onSolid, colors.solidEdge, modifier)
}

/** 次操作：表面底加中性描边。 */
@Composable
internal fun StudySecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = StudyTheme.colors
    ButtonSurface(text, onClick, enabled, colors.surface, colors.ink, colors.line, modifier)
}

/** 紧急出口：中性表面加语义色描边，不用整块红色。 */
@Composable
internal fun StudyDangerButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = StudyTheme.colors
    ButtonSurface(text, onClick, enabled, colors.surface, colors.danger, colors.danger.copy(alpha = 0.55f), modifier)
}

@Composable
internal fun StudyTextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: StudyTone = StudyTone.Accent,
) {
    val colors = StudyTheme.colors
    val color = when (tone) {
        StudyTone.Accent -> colors.accent
        StudyTone.Neutral -> colors.muted
        StudyTone.Success -> colors.success
        StudyTone.Danger -> colors.danger
    }
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = color, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun StudyField(
    value: String,
    label: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    numeric: Boolean = false,
    error: String? = null,
) {
    val colors = StudyTheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, style = MaterialTheme.typography.bodySmall) },
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        shape = RoundedCornerShape(6.dp),
        textStyle = MaterialTheme.typography.bodyMedium,
        isError = error != null,
        keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text),
        supportingText = if (error != null) {
            { Text(error, style = MaterialTheme.typography.bodySmall) }
        } else {
            null
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = colors.ink,
            unfocusedTextColor = colors.ink,
            focusedBorderColor = colors.accent,
            unfocusedBorderColor = colors.line,
            errorBorderColor = colors.danger,
            focusedLabelColor = colors.accent,
            unfocusedLabelColor = colors.muted,
            cursorColor = colors.accent,
            focusedContainerColor = colors.surface,
            unfocusedContainerColor = colors.surface,
            errorContainerColor = colors.surface,
            errorSupportingTextColor = colors.danger,
        ),
    )
}

@Composable
internal fun StatusPill(text: String, tone: StudyTone, modifier: Modifier = Modifier) {
    val colors = StudyTheme.colors
    val (bg, fg) = when (tone) {
        StudyTone.Accent -> colors.accentSoft to colors.accent
        StudyTone.Success -> colors.accentSoft to colors.success
        StudyTone.Danger -> colors.dangerSoft to colors.danger
        StudyTone.Neutral -> colors.control to colors.muted
    }
    Row(
        modifier = modifier
            .background(bg, RoundedCornerShape(6.dp))
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(6.dp).background(fg, RoundedCornerShape(3.dp)))
        Text(text, style = MaterialTheme.typography.labelSmall, color = fg, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun StudyEmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    val colors = StudyTheme.colors
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = colors.ink, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodySmall, color = colors.muted, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun StudyTopBar(title: String, subtitle: String? = null) {
    val colors = StudyTheme.colors
    Column(Modifier.fillMaxWidth().background(colors.surface)) {
        Column(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = colors.ink)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
        HorizontalDivider(thickness = 1.dp, color = colors.line)
    }
}

internal data class StudyNavItem(val label: String, val icon: ImageVector)

@Composable
private fun NavCell(item: StudyNavItem, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = StudyTheme.colors
    Column(
        modifier = modifier
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .background(if (selected) colors.accentSoft else Color.Transparent, RoundedCornerShape(6.dp))
                .padding(horizontal = 16.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                item.icon,
                contentDescription = null,
                tint = if (selected) colors.accent else colors.muted,
                modifier = Modifier.size(21.dp),
            )
        }
        Text(
            item.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) colors.accent else colors.muted,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
internal fun StudyBottomBar(
    items: List<StudyNavItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    anchorFor: @Composable (Int) -> Modifier = { Modifier },
) {
    val colors = StudyTheme.colors
    Column(Modifier.fillMaxWidth().background(colors.surface)) {
        HorizontalDivider(thickness = 1.dp, color = colors.line)
        Row(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().heightIn(min = 64.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { index, item ->
                NavCell(item, selected == index, { onSelect(index) }, Modifier.weight(1f).then(anchorFor(index)))
            }
        }
    }
}

@Composable
internal fun StudyRail(
    items: List<StudyNavItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    anchorFor: @Composable (Int) -> Modifier = { Modifier },
) {
    val colors = StudyTheme.colors
    Row {
        Column(
            modifier = Modifier
                .width(96.dp)
                .fillMaxHeight()
                .background(colors.surface)
                .statusBarsPadding(),
        ) {
            Spacer(Modifier.height(12.dp))
            items.forEachIndexed { index, item ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(anchorFor(index))
                        .clickable { onSelect(index) }
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .background(if (selected == index) colors.accentSoft else Color.Transparent, RoundedCornerShape(6.dp))
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                    ) {
                        Icon(
                            item.icon,
                            contentDescription = null,
                            tint = if (selected == index) colors.accent else colors.muted,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Text(
                        item.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected == index) colors.accent else colors.muted,
                        fontWeight = if (selected == index) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(colors.line))
    }
}

@Composable
internal fun ProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color? = null,
    height: Dp = 4.dp,
) {
    val colors = StudyTheme.colors
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(colors.control),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(color ?: colors.accent),
        )
    }
}

@Composable
internal fun StudyQuickChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = StudyTheme.colors
    Box(
        modifier = modifier
            .heightIn(min = 38.dp)
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, if (selected) colors.accent else colors.line, RoundedCornerShape(6.dp))
            .background(if (selected) colors.accentSoft else colors.surface, RoundedCornerShape(6.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) colors.accent else colors.ink,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun PageList(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(
        state = state,
        modifier = modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}
