package com.focusforceplus.app.study

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.espresso.Espresso.closeSoftKeyboard
import com.focusforceplus.app.study.ui.StudyGuardActivity
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class StudyUiTest {
    @get:Rule val compose = createAndroidComposeRule<StudyGuardActivity>()

    /** 首次启动会自动展示聚光灯引导，跳转其他页面前先跳过。 */
    private fun dismissSpotlightIfShown() {
        compose.waitForIdle()
        if (compose.onAllNodesWithTag("spotlight-skip").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithTag("spotlight-skip").performClick()
            compose.waitUntil(5_000) {
                compose.onAllNodesWithTag("spotlight-overlay").fetchSemanticsNodes().isEmpty()
            }
        }
    }

    /** 统计页先读数据库再渲染；低配设备可能尚未出结果，按文本等待再滚动。 */
    private fun awaitStatistics() {
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("有效学习").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test fun settingsShowPermissionAndDegradedMode() {
        dismissSpotlightIfShown()
        compose.onNodeWithText("设置").performClick()
        compose.onNodeWithText("设置使用情况访问权限").assertIsDisplayed()
        compose.onNodeWithText("了解并设置无障碍增强").assertIsDisplayed()
        compose.onNodeWithTag("study-settings-list").performScrollToNode(hasText("导出完整学习备份"))
        compose.onNodeWithText("导出完整学习备份").assertIsDisplayed()
    }

    @Test fun planDisclosesOfflineRulesAndStatisticsAreReachable() {
        dismissSpotlightIfShown()
        compose.onNodeWithText("计划").performClick()
        compose.onNodeWithText("把目标变成每天可执行的任务").assertIsDisplayed()
        compose.onNodeWithText("统计").performClick()
        awaitStatistics()
        compose.onNodeWithTag("study-statistics-list").performScrollToNode(hasText("每日趋势"))
        compose.onNodeWithText("每日趋势").assertIsDisplayed()
    }

    @Test fun planDateFieldsOpenCalendarPickerInsteadOfTextInput() {
        dismissSpotlightIfShown()
        compose.onNodeWithText("计划").performClick()
        compose.onNodeWithTag("study-plan-list").performScrollToNode(hasTestTag("study-exam-date-field"))
        compose.onNodeWithTag("study-exam-date-field").performClick()
        compose.onNodeWithTag("study-calendar-dialog").assertIsDisplayed()
        compose.onNodeWithTag("calendar-today").performClick()
        val today = LocalDate.now()
        compose.onNodeWithTag("calendar-day-$today").performClick()
        compose.onNodeWithTag("calendar-confirm").performClick()
        // The tag belongs to the field container; the displayed value is its child.
        compose.onNodeWithTag("study-exam-date-field")
            .assert(hasAnyDescendant(hasText("$today · 今天")))

        compose.onNodeWithTag("study-plan-list").performScrollToNode(hasTestTag("study-filter-date-field"))
        compose.onNodeWithTag("study-filter-date-field").performClick()
        compose.onNodeWithTag("study-calendar-dialog").assertIsDisplayed()
        compose.onNodeWithTag("calendar-clear").performClick()
        compose.onNodeWithTag("study-filter-date-field")
            .assert(hasAnyDescendant(hasText("全部日期")))
    }

    @Test fun statisticsFiltersAndDefinitionsAreReachable() {
        dismissSpotlightIfShown()
        compose.onNodeWithText("统计").performClick()
        compose.onNodeWithText("近 30 日").performClick()
        awaitStatistics()
        compose.onNodeWithText("近 30 日").assertIsSelected()
        compose.onNodeWithText("近 7 日").assertIsNotSelected()
        compose.onNodeWithText("科目：全部科目").performClick()
        compose.onNodeWithText("全部科目").performClick()
        awaitStatistics()
        compose.onNodeWithTag("study-statistics-list").performScrollToNode(hasText("了解统计口径"))
        compose.onNodeWithText("了解统计口径").performClick()
        compose.onNodeWithText("收起统计口径").assertIsDisplayed()
    }

    @Test fun blockedAppsSupportSelectAllAndPerAppExemption() {
        dismissSpotlightIfShown()
        compose.onNodeWithText("设置").performClick()
        compose.onNodeWithTag("study-settings-list").performScrollToNode(hasText("受限娱乐应用"))
        compose.onNodeWithText("受限娱乐应用").performClick()

        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("不受限").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText("恢复").fetchSemanticsNodes().isNotEmpty()
        }

        // 先把选择器清成已知状态，避免沿用上次运行的数据。
        compose.onNodeWithText("清空").performClick()
        while (compose.onAllNodesWithText("恢复").fetchSemanticsNodes().isNotEmpty()) {
            compose.onAllNodesWithText("恢复").onFirst().performClick()
        }
        compose.onNodeWithText("受限 0 个 · 不受限 0 个").assertIsDisplayed()

        // 全选只改勾选、不落库：计数必须变化，再清空复原。
        compose.onNodeWithText("全选当前", substring = true).performClick()
        compose.onNodeWithText("受限 0 个 · 不受限 0 个").assertDoesNotExist()
        compose.onNodeWithText("清空").performClick()
        compose.onNodeWithText("受限 0 个 · 不受限 0 个").assertIsDisplayed()

        // 单独把一个应用设为不受限并保存。
        compose.onAllNodesWithText("不受限").onFirst().performClick()
        compose.onNodeWithText("受限 0 个 · 不受限 1 个").assertIsDisplayed()
        compose.onNodeWithText("保存").performClick()
        compose.onNodeWithText("不受限 1 个。白名单优先于娱乐限制", substring = true).assertIsDisplayed()

        // 复原，避免影响其它用例。
        compose.onNodeWithText("受限娱乐应用").performClick()
        compose.onNodeWithText("恢复").performClick()
        compose.onNodeWithText("保存").performClick()
        compose.onNodeWithText("不受限 0 个。白名单优先于娱乐限制", substring = true).assertIsDisplayed()
    }

    private fun advanceSpotlightStep(counterText: String, buttonText: String) {
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText(counterText).fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodes(hasTestTag("spotlight-primary") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("spotlight-counter").assertIsDisplayed()
        compose.onNodeWithText(counterText).assertIsDisplayed()
        compose.onNodeWithText(buttonText).performClick()
    }

    @Test fun spotlightOnboardingReplaysAndCompletesThreeSteps() {
        dismissSpotlightIfShown()
        compose.onNodeWithText("设置").performClick()
        compose.onNodeWithTag("study-settings-list").performScrollToNode(hasText("重新播放新手引导"))
        compose.onNodeWithText("重新播放新手引导").performClick()

        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("spotlight-card").fetchSemanticsNodes().isNotEmpty()
        }
        advanceSpotlightStep("1/3", "下一步")
        advanceSpotlightStep("2/3", "下一步")
        advanceSpotlightStep("3/3", "知道了")
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("spotlight-overlay").fetchSemanticsNodes().isEmpty()
        }
    }

    @Test fun learningPageDurationPresetsFillMinutesAndHideIdleEmergencyExit() {
        dismissSpotlightIfShown()
        compose.onNodeWithText("紧急解除所有学习限制").assertDoesNotExist()
        compose.onNodeWithTag("study-learning-list").performScrollToNode(hasTestTag("study-duration-presets"))
        compose.onNodeWithTag("study-duration-presets").performScrollToNode(hasText("45分钟"))
        compose.onNodeWithText("45分钟").performClick()
        compose.onNodeWithTag("study-minutes-field").assertTextContains("45")
    }

    @Test fun planPageAllowsManualTaskCreationAndDeletion() {
        val taskTitle = "手动测试任务-${System.nanoTime()}"
        dismissSpotlightIfShown()
        compose.onNodeWithText("计划").performClick()
        compose.onNodeWithTag("study-plan-list").performScrollToNode(hasTestTag("study-add-task"))
        compose.onNodeWithTag("study-add-task").performClick()
        compose.onNodeWithTag("task-dialog-title").performTextReplacement(taskTitle)
        compose.onNodeWithTag("task-dialog-subject").performTextReplacement("政治")
        compose.onNodeWithText("60分").performClick()
        compose.onNodeWithTag("task-dialog-confirm").performClick()
        // Dialog disposal and the system IME animate independently of Compose idling.
        closeSoftKeyboard()

        compose.onNodeWithTag("study-plan-list").performScrollToNode(hasText(taskTitle))
        compose.onNodeWithText(taskTitle).assertIsDisplayed()
        // The success snackbar temporarily covers this card's action row.
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("已添加学习任务").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithTag("study-plan-list").performScrollToNode(hasText("删除任务"))
        compose.onAllNodesWithText("删除任务").onFirst().assertIsDisplayed().performClick()
        // Wait for the database write before the activity and its ViewModel are torn down.
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText(taskTitle).fetchSemanticsNodes().isEmpty()
        }
    }
}
