package com.focusforceplus.app.study

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.focusforceplus.app.study.ui.BlockedScreen
import com.focusforceplus.app.study.ui.StudyGuardTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class StudyBlockedUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun blockedPageKeepsAllThreeExitsAndEmergencyIsDirect() {
        var study = 0
        var home = 0
        var emergency = 0
        compose.setContent {
            StudyGuardTheme {
                BlockedScreen(
                    onStudy = { study++ },
                    onHome = { home++ },
                    onEmergency = { emergency++ },
                )
            }
        }
        compose.onNodeWithText("先回到当前学习任务").assertIsDisplayed()
        compose.onNodeWithText("返回学习与汇报进度").performClick()
        compose.onNodeWithText("返回桌面").performClick()
        compose.onNodeWithText("紧急结束并解除全部限制").performClick()
        assertEquals(1, study)
        assertEquals(1, home)
        assertEquals(1, emergency)
    }
}