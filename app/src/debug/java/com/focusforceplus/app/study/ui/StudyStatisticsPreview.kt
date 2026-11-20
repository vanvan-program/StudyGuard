package com.focusforceplus.app.study.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.focusforceplus.app.study.core.StatisticsFilter
import com.focusforceplus.app.study.core.StatisticsSegment
import com.focusforceplus.app.study.core.StudyStatistics
import java.time.LocalDate

@Preview(name = "演示数据 · 手机", widthDp = 360, showBackground = true)
@Preview(name = "演示数据 · 大字体", widthDp = 320, fontScale = 1.3f, showBackground = true)
@Preview(name = "演示数据 · 宽屏", widthDp = 840, showBackground = true)
@Composable
private fun StudyStatisticsPreview() {
    val snapshot = remember {
        val today = LocalDate.of(2026, 11, 12)
        StudyStatistics.calculate(
            StatisticsFilter(today = today),
            listOf(20L, 45L, 0L, 15L, 60L, 25L, 35L).mapIndexed { index, minutes ->
                StatisticsSegment(today.minusDays(6L - index).toString(), "数学", minutes * 60_000)
            }.filter { it.durationMs > 0 },
            emptyList(), emptyList(), emptyList(),
        )
    }
    StudyGuardTheme {
        Surface {
            Column(Modifier.padding(16.dp)) {
                Text("趋势预览 · 仅演示数据", style = MaterialTheme.typography.titleLarge)
                DailyTrend(snapshot)
            }
        }
    }
}
