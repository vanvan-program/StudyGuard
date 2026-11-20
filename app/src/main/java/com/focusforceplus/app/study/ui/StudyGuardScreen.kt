package com.focusforceplus.app.study.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

@Composable
fun StudyGuardScreen(
    viewModel: StudyViewModel,
    onUsageSettings: () -> Unit,
    onAccessibilitySettings: () -> Unit,
    onNotifications: () -> Unit,
    onLegacyTools: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val message by viewModel.message.collectAsState()
    val colors = StudyTheme.colors
    val context = LocalContext.current
    val session by viewModel.runtime.current.collectAsState()
    val anchors = remember { SpotlightAnchorState() }
    var spotlightVisible by remember { mutableStateOf(!SpotlightStore.isDone(context)) }
    val learningListState = rememberLazyListState()
    val tabAnchor: @Composable (Int) -> Modifier = { index ->
        if (index == 2) Modifier.spotlightAnchor(anchors, "spotlight-statistics") else Modifier
    }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.message.value = null
        }
    }
    val items = listOf(
        StudyNavItem("学习", Icons.AutoMirrored.Outlined.MenuBook),
        StudyNavItem("计划", Icons.Outlined.CheckCircle),
        StudyNavItem("统计", Icons.Outlined.BarChart),
        StudyNavItem("设置", Icons.Outlined.Settings),
    )
    val titles = listOf("StudyGuard", "学习计划", "学习记录", "设置与权限")
    val subtitles = listOf(
        "本地监督 · 数据只保存在本机",
        "本地规则草案 · 未连接在线 AI",
        "只统计已保存的有效学习区间",
        "权限、应用与数据",
    )
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(colors.canvas)) {
        val expanded = maxWidth >= 840.dp
        val guideShowing = spotlightVisible && tab == 0 && session == null
        Row(
            Modifier
                .fillMaxSize()
                // 引导期间把背景页面移出无障碍树：读屏焦点只落在气泡与“跳过”上。
                .then(if (guideShowing) Modifier.clearAndSetSemantics { } else Modifier),
        ) {
            if (expanded) StudyRail(items, tab, { tab = it }, anchorFor = tabAnchor)
            Column(Modifier.weight(1f).fillMaxHeight()) {
                StudyTopBar(titles[tab], subtitles[tab])
                Box(Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        Box(Modifier.widthIn(max = 900.dp).fillMaxSize()) {
                            when (tab) {
                                0 -> LearningPage(
                                    viewModel,
                                    onPlan = { tab = 1 },
                                    onSettings = { tab = 3 },
                                    listState = learningListState,
                                    anchors = anchors,
                                )
                                1 -> PlanPage(viewModel)
                                2 -> StudyStatisticsPage(viewModel, onStart = { tab = 0 }, onPlan = { tab = 1 })
                                3 -> SettingsPage(
                                    viewModel,
                                    onUsageSettings,
                                    onAccessibilitySettings,
                                    onNotifications,
                                    onLegacyTools,
                                    onReplaySpotlight = {
                                        SpotlightStore.reset(context)
                                        tab = 0
                                        spotlightVisible = true
                                    },
                                )
                            }
                        }
                    }
                    SnackbarHost(
                        hostState = snackbar,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                    ) { data ->
                        Snackbar(
                            containerColor = colors.ink,
                            contentColor = colors.canvas,
                            shape = RoundedCornerShape(8.dp),
                        ) { Text(data.visuals.message, style = MaterialTheme.typography.bodyMedium) }
                    }
                }
                if (!expanded) StudyBottomBar(items, tab, { tab = it }, anchorFor = tabAnchor)
            }
        }
        if (guideShowing) {
            StudySpotlightHost(
                anchors = anchors,
                listState = learningListState,
                onFinish = {
                    SpotlightStore.markDone(context)
                    spotlightVisible = false
                },
            )
        }
    }
}
