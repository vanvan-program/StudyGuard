package com.focusforceplus.app.study

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.focusforceplus.app.study.core.StatisticsFilter
import com.focusforceplus.app.study.core.StatisticsSegment
import com.focusforceplus.app.study.core.StudyStatistics
import com.focusforceplus.app.study.ui.DailyTrend
import com.focusforceplus.app.study.ui.StudyGuardTheme
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class StudyStatisticsUiTest {
    @get:Rule val compose = createComposeRule()
    private val today = LocalDate.of(2026, 11, 12)

    @Test fun selectingDateShowsItsActualDuration() {
        val snapshot = StudyStatistics.calculate(
            StatisticsFilter(today = today),
            listOf(StatisticsSegment("2026-11-11", "数学", 60_000)),
            emptyList(), emptyList(), emptyList(),
        )
        compose.setContent {
            StudyGuardTheme { Column(Modifier.width(320.dp)) { DailyTrend(snapshot) } }
        }
        compose.onNodeWithContentDescription("2026-11-11，有效学习1分0秒").performClick()
        compose.onNodeWithText("2026-11-11 · 1分0秒").assertIsDisplayed()
    }

    @Test fun emptyChartRemainsReadableAtLargeFontScale() {
        val snapshot = StudyStatistics.calculate(StatisticsFilter(today = today), emptyList(), emptyList(), emptyList(), emptyList())
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.3f)) {
                StudyGuardTheme { Column(Modifier.width(320.dp)) { DailyTrend(snapshot) } }
            }
        }
        compose.onNodeWithText("2026-11-12 · 0秒").assertIsDisplayed()
        compose.onNodeWithText("当日暂无安排任务").assertIsDisplayed()
    }
}
