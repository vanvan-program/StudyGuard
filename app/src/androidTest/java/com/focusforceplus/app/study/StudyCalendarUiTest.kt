package com.focusforceplus.app.study

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.focusforceplus.app.study.ui.StudyCalendarDialog
import com.focusforceplus.app.study.ui.StudyDateField
import com.focusforceplus.app.study.ui.StudyGuardTheme
import com.focusforceplus.app.study.ui.parseStudyDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class StudyCalendarUiTest {
    @get:Rule val compose = createComposeRule()

    private val today = LocalDate.of(2026, 11, 14)

    @Test fun pickingDateFromCalendarUpdatesFieldOnlyAfterConfirm() {
        var selected by mutableStateOf("2026-11-14")
        var open by mutableStateOf(false)
        compose.setContent {
            StudyGuardTheme {
                StudyDateField(
                    value = selected,
                    label = "考试日期",
                    onClick = { open = true },
                    today = today,
                )
                if (open) {
                    StudyCalendarDialog(
                        title = "选择考试日期",
                        initialDate = parseStudyDate(selected),
                        minDate = today,
                        onDismiss = { open = false },
                        onConfirm = { picked -> selected = picked.toString() },
                        today = today,
                    )
                }
            }
        }

        compose.onNodeWithText("2026-11-14 · 今天").performClick()
        compose.onNodeWithTag("study-calendar-dialog").assertIsDisplayed()
        compose.onNodeWithTag("calendar-day-2026-11-13").assertIsNotEnabled()
        compose.onNodeWithTag("calendar-day-2026-11-20").assertIsEnabled().performClick()
        compose.onNodeWithTag("calendar-cancel").performClick()
        assertEquals("2026-11-14", selected)

        compose.onNodeWithText("2026-11-14 · 今天").performClick()
        compose.onNodeWithTag("calendar-day-2026-11-20").performClick()
        compose.onNodeWithTag("calendar-confirm").performClick()
        assertEquals("2026-11-20", selected)
        compose.onNodeWithText("2026-11-20 · 周五").assertIsDisplayed()
    }

    @Test fun monthYearSwitcherNavigatesAcrossMonths() {
        var selected = LocalDate.of(2026, 11, 14)
        var open by mutableStateOf(true)
        compose.setContent {
            StudyGuardTheme {
                if (open) {
                    StudyCalendarDialog(
                        title = "选择任务日期",
                        initialDate = selected,
                        onDismiss = { open = false },
                        onConfirm = { selected = it },
                        today = today,
                    )
                }
            }
        }

        compose.onNodeWithTag("calendar-month-title").performClick()
        compose.onNodeWithTag("calendar-next").performClick()
        compose.onNodeWithText("2027年").assertIsDisplayed()
        compose.onNodeWithTag("calendar-month-6").performClick()
        compose.onNodeWithText("2027年6月").assertIsDisplayed()
        compose.onNodeWithTag("calendar-day-2027-06-07").performClick()
        compose.onNodeWithTag("calendar-confirm").performClick()
        assertEquals(LocalDate.of(2027, 6, 7), selected)
    }

    @Test fun filterDateFieldAndDialogSupportClearingToAllDates() {
        var filterDay by mutableStateOf("2026-11-14")
        var open by mutableStateOf(false)
        compose.setContent {
            StudyGuardTheme {
                StudyDateField(
                    value = filterDay,
                    label = "按日期筛选",
                    placeholder = "全部日期",
                    onClick = { open = true },
                    onClear = if (filterDay.isNotBlank()) ({ filterDay = "" }) else null,
                    today = today,
                )
                if (open) {
                    StudyCalendarDialog(
                        title = "按日期筛选任务",
                        initialDate = parseStudyDate(filterDay),
                        markedDays = setOf(LocalDate.of(2026, 11, 15)),
                        onClear = { filterDay = "" },
                        onDismiss = { open = false },
                        onConfirm = { filterDay = it.toString() },
                        today = today,
                    )
                }
            }
        }

        compose.onNodeWithTag("date-field-clear").performClick()
        assertEquals("", filterDay)
        compose.onNodeWithText("全部日期").performClick()
        compose.onNodeWithTag("study-calendar-dialog").assertIsDisplayed()
        compose.onNodeWithTag("calendar-day-2026-11-15").performClick()
        compose.onNodeWithTag("calendar-confirm").performClick()
        assertEquals("2026-11-15", filterDay)

        compose.onNodeWithText("2026-11-15 · 明天").performClick()
        compose.onNodeWithTag("calendar-clear").performClick()
        assertEquals("", filterDay)
    }
}
