package com.focusforceplus.app.study.ui

import android.animation.ValueAnimator
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.edit
import android.os.SystemClock
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/** 一步引导：只描述目标锚点与文案，不写坐标。 */
internal data class SpotlightStep(
    val anchor: String,
    val title: String,
    val body: String,
)

internal val StudySpotlightSteps = listOf(
    SpotlightStep(
        anchor = "spotlight-start",
        title = "开始学习",
        body = "填好科目和目标分钟再点这里；开始前不会限制任何应用。",
    ),
    SpotlightStep(
        anchor = "spotlight-permissions",
        title = "先配好权限和受限应用",
        body = "授予使用情况访问，再选出要限制的应用；没授权就不累计监督时长。",
    ),
    SpotlightStep(
        anchor = "spotlight-statistics",
        title = "回看学习记录",
        body = "统计页只汇总已保存的有效学习区间，可按今日、近 7 日、近 30 日查看。",
    ),
)

/** 首次引导完成标记：SharedPreferences 里存完成时间戳，0 表示未完成。 */
internal object SpotlightStore {
    private const val PREFS = "study_onboarding"
    private const val KEY = "spotlight_v1"

    fun isDone(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY, 0L) > 0L

    fun markDone(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit { putLong(KEY, System.currentTimeMillis()) }
    }

    fun reset(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { remove(KEY) }
    }
}

/** 各步骤目标控件的实测位置；由 [Modifier.spotlightAnchor] 上报。 */
internal class SpotlightAnchorState {
    private val rects = mutableStateMapOf<String, Rect>()

    fun rect(key: String): Rect? = rects[key]

    fun update(key: String, rect: Rect) {
        if (rect.width <= 0f || rect.height <= 0f) return
        if (rects[key] != rect) rects[key] = rect
    }

    fun remove(key: String) {
        rects.remove(key)
    }
}

/**
 * 把当前控件登记为引导目标。位置在 [onGloballyPositioned] 里实测，
 * 页面重排、旋转、分屏、折叠屏展开后都会自动更新，不需要写死坐标。
 */
@Composable
internal fun Modifier.spotlightAnchor(anchors: SpotlightAnchorState, key: String): Modifier {
    DisposableEffect(anchors, key) {
        onDispose { anchors.remove(key) }
    }
    return onGloballyPositioned { coordinates ->
        if (coordinates.isAttached) anchors.update(key, coordinates.boundsInRoot())
    }
}

internal data class BubblePlacement(
    val x: Float,
    val y: Float,
    val maxHeight: Float,
    val above: Boolean,
    val arrowX: Float,
)

/**
 * 纯几何计算：给定目标镂空、气泡实测尺寸与可用安全区，决定上下方向、水平夹紧与三角位置。
 * 不依赖 Compose 运行时，便于单元测试。
 */
internal fun placeBubble(
    target: Rect,
    card: Size,
    safe: Rect,
    margin: Float,
    gap: Float,
    cornerInset: Float,
): BubblePlacement {
    val belowSpace = (safe.bottom - (target.bottom + gap)).coerceAtLeast(0f)
    val aboveSpace = ((target.top - gap) - safe.top).coerceAtLeast(0f)
    val fitsBelow = card.height <= belowSpace
    val fitsAbove = card.height <= aboveSpace
    val above = when {
        fitsBelow -> false
        fitsAbove -> true
        else -> aboveSpace > belowSpace
    }
    val available = (if (above) aboveSpace else belowSpace).coerceAtLeast(0f)
    val height = minOf(card.height, available)
    val rawY = if (above) target.top - gap - height else target.bottom + gap
    val y = rawY.coerceIn(safe.top, (safe.bottom - height).coerceAtLeast(safe.top))
    val rawX = target.center.x - card.width / 2f
    val minX = safe.left + margin
    val maxX = (safe.right - margin - card.width).coerceAtLeast(minX)
    val x = rawX.coerceIn(minX, maxX)
    val arrowMin = x + cornerInset
    val arrowMax = (x + card.width - cornerInset).coerceAtLeast(arrowMin)
    val arrowX = target.center.x.coerceIn(arrowMin, arrowMax)
    return BubblePlacement(x, y, available, above, arrowX)
}

private val SpotlightEase = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

private fun lerp(start: Float, stop: Float, fraction: Float): Float = start + (stop - start) * fraction

private fun lerpRect(start: Rect, stop: Rect, fraction: Float): Rect = Rect(
    lerp(start.left, stop.left, fraction),
    lerp(start.top, stop.top, fraction),
    lerp(start.right, stop.right, fraction),
    lerp(start.bottom, stop.bottom, fraction),
)

/**
 * 聚光灯引导层：全屏 70% 遮罩 + 圆角镂空 + 呼吸光晕 + 指向目标的气泡卡片。
 *
 * 所有位置都来自 [SpotlightAnchorState] 的实测值与安全区，不写死任何坐标；
 * 步骤切换在同一层内插值镂空，遮罩不重新挂载、不闪烁。
 */
@Composable
internal fun StudySpotlightHost(
    anchors: SpotlightAnchorState,
    onFinish: () -> Unit,
    listState: LazyListState? = null,
    steps: List<SpotlightStep> = StudySpotlightSteps,
) {
    if (steps.isEmpty()) return
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val colors = StudyTheme.colors
    val motion = remember { ValueAnimator.areAnimatorsEnabled() }
    val scope = rememberCoroutineScope()

    var index by rememberSaveable { mutableIntStateOf(0) }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    var closing by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    val holeProgress = remember { Animatable(1f) }
    var holeFrom by remember { mutableStateOf(Rect.Zero) }
    var holeTo by remember { mutableStateOf(Rect.Zero) }
    var radiusFrom by remember { mutableFloatStateOf(0f) }
    var radiusTo by remember { mutableFloatStateOf(0f) }
    val layerAlpha = remember { Animatable(1f) }
    val bubbleAlpha = remember { Animatable(0f) }

    val insetPadding = WindowInsets.safeDrawing.asPaddingValues()
    val safeOf: (IntSize) -> Rect = { size ->
        with(density) {
            Rect(
                insetPadding.calculateLeftPadding(layoutDirection).toPx(),
                insetPadding.calculateTopPadding().toPx(),
                (size.width - insetPadding.calculateRightPadding(layoutDirection).toPx()).coerceAtLeast(0f),
                (size.height - insetPadding.calculateBottomPadding().toPx()).coerceAtLeast(0f),
            )
        }
    }
    val safe = safeOf(rootSize)

    val outer = with(density) { 8.dp.toPx() }
    val baseRadius = with(density) { 6.dp.toPx() }
    val margin = with(density) { 16.dp.toPx() }
    val gap = with(density) { 12.dp.toPx() }
    val arrowInset = with(density) { 16.dp.toPx() }
    val compact = with(density) { rootSize.width < 360.dp.toPx() || rootSize.height < 640.dp.toPx() }
    val landscape = with(density) { rootSize.width > rootSize.height }
    val cardMaxWidth = with(density) {
        minOf(if (landscape) 360.dp.toPx() else 300.dp.toPx(), safe.width - 2 * margin)
            .coerceAtLeast(200.dp.toPx())
    }

    val step = steps[index.coerceIn(0, steps.lastIndex)]
    val stepScrollIndex = List(steps.size) { position ->
        when (position) {
            0 -> 2
            1 -> 1
            else -> null
        }
    }
    val canInteract = !busy && !closing && holeTo != Rect.Zero

    val finish: () -> Unit = {
        if (!closing) {
            closing = true
            scope.launch {
                layerAlpha.animateTo(0f, if (motion) tween(300) else tween(0))
                onFinish()
            }
        }
    }

    val goNext: () -> Unit = {
        if (!busy && !closing && holeTo != Rect.Zero) {
            if (index >= steps.lastIndex) {
                finish()
            } else {
                scope.launch {
                    busy = true
                    bubbleAlpha.animateTo(0f, if (motion) tween(120) else tween(0))
                    index += 1
                }
            }
        }
    }

    BackHandler(enabled = true) { finish() }

    // 每隔一步：先把目标滚进可视区、等锚点上报，再把镂空从当前位置插值过去。
    LaunchedEffect(index) {
        val current = steps[index]
        // 等首帧布局完成再算安全区，避免用 0 尺寸把列表滚飞。
        var waited = 0L
        while (rootSize == IntSize.Zero && waited < 1200L) {
            withFrameNanos { }
            waited += 16L
        }
        val liveSafe = safeOf(rootSize)
        val scrollIndex = stepScrollIndex.getOrNull(index)
        if (scrollIndex != null && listState != null) {
            runCatching { listState.animateScrollToItem(scrollIndex) }
            val deadline = SystemClock.uptimeMillis() + 1200
            while (anchors.rect(current.anchor) == null && SystemClock.uptimeMillis() < deadline) {
                withFrameNanos { }
            }
            repeat(2) {
                val rect = anchors.rect(current.anchor) ?: return@repeat
                val topLimit = with(density) { 88.dp.toPx() }
                val bottomLimit = with(density) {
                    if (rootSize.width < 840.dp.toPx()) 88.dp.toPx() else 24.dp.toPx()
                }
                val delta = when {
                    rect.bottom > liveSafe.bottom - bottomLimit -> rect.bottom - (liveSafe.bottom - bottomLimit)
                    rect.top < liveSafe.top + topLimit -> rect.top - (liveSafe.top + topLimit)
                    else -> 0f
                }
                if (delta != 0f) runCatching { listState.scrollBy(delta) }
                withFrameNanos { }
            }
        }

        val deadline = SystemClock.uptimeMillis() + 1200
        var target = anchors.rect(current.anchor)
        while (target == null && SystemClock.uptimeMillis() < deadline) {
            withFrameNanos { }
            target = anchors.rect(current.anchor)
        }
        if (target == null) {
            // 锚点确实不存在（控件被条件渲染或不可见）：跳过该步，不空转。
            busy = false
            if (index >= steps.lastIndex) {
                finish()
            } else {
                index += 1
            }
            return@LaunchedEffect
        }

        val nextHole = target.inflate(outer)
        val nextRadius = baseRadius + outer
        if (holeTo == Rect.Zero) {
            holeFrom = nextHole
            holeTo = nextHole
            radiusFrom = nextRadius
            radiusTo = nextRadius
            holeProgress.snapTo(1f)
            // 首帧：遮罩淡入，避免突然压黑。
            layerAlpha.snapTo(0f)
            bubbleAlpha.snapTo(0f)
            launch { layerAlpha.animateTo(1f, if (motion) tween(200) else tween(0)) }
            bubbleAlpha.animateTo(1f, if (motion) tween(160) else tween(0))
        } else {
            holeFrom = lerpRect(holeFrom, holeTo, holeProgress.value)
            radiusFrom = lerp(radiusFrom, radiusTo, holeProgress.value)
            holeTo = nextHole
            radiusTo = nextRadius
            holeProgress.snapTo(0f)
            bubbleAlpha.snapTo(0f)
            // 气泡在镂空滑动过程中就淡入，避免 400ms 空档；位置按最终目标计算，不随镂空抖动。
            launch { bubbleAlpha.animateTo(1f, if (motion) tween(160) else tween(0)) }
            holeProgress.animateTo(1f, if (motion) tween(400, easing = SpotlightEase) else tween(0))
        }
        busy = false
    }

    // 布局尺寸变化（旋转、分屏、折叠、键盘）或目标自身尺寸变化时，直接把镂空对齐到新位置。
    // 用 snapshotFlow 订阅锚点：滚动期间 onGloballyPositioned 高频上报时不再触发整层重组。
    LaunchedEffect(step.anchor, safe.width, safe.height, rootSize, busy) {
        if (busy) return@LaunchedEffect
        snapshotFlow { anchors.rect(step.anchor) }
            .filterNotNull()
            .collect { target ->
                if (busy || holeProgress.isRunning) return@collect
                val nextHole = target.inflate(outer)
                if (holeTo != Rect.Zero && nextHole != holeTo) {
                    holeFrom = nextHole
                    holeTo = nextHole
                    radiusFrom = baseRadius + outer
                    radiusTo = baseRadius + outer
                    holeProgress.snapTo(1f)
                }
            }
    }

    // 呼吸光晕：保留 State 只在绘制阶段读，避免每帧重组整棵引导层。
    val pulseState: State<Float> = if (motion) {
        val transition = rememberInfiniteTransition(label = "spotlight-glow")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "spotlight-glow-value",
        )
    } else {
        remember { mutableFloatStateOf(0.5f) }
    }
    val hasTarget = holeTo != Rect.Zero

    Box(
        Modifier
            .fillMaxSize()
            .zIndex(20f)
            .onSizeChanged { rootSize = it }
            .testTag("spotlight-overlay")
            .graphicsLayer { alpha = layerAlpha.value },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val hole = lerpRect(holeFrom, holeTo, holeProgress.value)
            val radius = lerp(radiusFrom, radiusTo, holeProgress.value)
            // 还没有目标时不铺黑：避免首帧闪一下全黑。
            if (hole.width <= 0f || hole.height <= 0f) return@Canvas
            val scrim = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(0f, 0f, size.width, size.height))
                addRoundRect(RoundRect(hole, CornerRadius(radius)))
            }
            drawPath(scrim, Color.Black.copy(alpha = 0.7f))
        }

        if (hasTarget) {
            // 独立图层：呼吸动画只重录这一层，不牵动遮罩与气泡。
            Canvas(Modifier.fillMaxSize().graphicsLayer { }) {
                val hole = lerpRect(holeFrom, holeTo, holeProgress.value)
                val radius = lerp(radiusFrom, radiusTo, holeProgress.value)
                val pulse = pulseState.value
                val glowColor = colors.accent.copy(alpha = 0.30f + 0.35f * pulse)
                val spread = 10.dp.toPx() * pulse
                drawRoundRect(
                    color = glowColor,
                    topLeft = Offset(hole.left - spread, hole.top - spread),
                    size = Size(hole.width + 2 * spread, hole.height + 2 * spread),
                    cornerRadius = CornerRadius(radius + spread),
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
        }

        // 阻止背景误触：只放行气泡与“跳过”。
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            event.changes.forEach { it.consume() }
                        }
                    }
                },
        )

        if (hasTarget && rootSize != IntSize.Zero) {
            SubcomposeLayout(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = bubbleAlpha.value },
            ) { constraints ->
                val maxCardWidth = cardMaxWidth.toInt()
                val cardMeasurable = subcompose("spotlight-card") {
                    key(index, canInteract, compact) {
                        SpotlightCard(
                            step = step,
                            number = index + 1,
                            total = steps.size,
                            compact = compact,
                            appearScale = { 0.96f + 0.04f * bubbleAlpha.value },
                            primaryLabel = if (index >= steps.lastIndex) "知道了" else "下一步",
                            enabled = canInteract,
                            onPrimary = { if (index >= steps.lastIndex) finish() else goNext() },
                        )
                    }
                }.first()
                var card = cardMeasurable.measure(
                    Constraints(
                        maxWidth = maxCardWidth,
                        maxHeight = (safe.height * 0.5f).toInt().coerceIn(1, safe.height.toInt().coerceAtLeast(1)),
                    ),
                )
                var placement = placeBubble(
                    target = holeTo,
                    card = Size(card.width.toFloat(), card.height.toFloat()),
                    safe = safe,
                    margin = margin,
                    gap = gap,
                    cornerInset = arrowInset,
                )
                if (card.height > placement.maxHeight + 0.5f) {
                    card = cardMeasurable.measure(
                        Constraints(
                            maxWidth = maxCardWidth,
                            maxHeight = placement.maxHeight.toInt().coerceAtLeast(1),
                        ),
                    )
                    placement = placeBubble(
                        target = holeTo,
                        card = Size(card.width.toFloat(), card.height.toFloat()),
                        safe = safe,
                        margin = margin,
                        gap = gap,
                        cornerInset = arrowInset,
                    )
                }
                val arrowWidth = 16.dp.roundToPx()
                val arrowHeight = 8.dp.roundToPx()
                val arrow = subcompose("spotlight-arrow") {
                    key(placement.above) {
                        SpotlightArrow(
                            pointUp = !placement.above,
                            fill = colors.surface,
                            stroke = colors.line,
                        )
                    }
                }.first().measure(Constraints.fixed(arrowWidth, arrowHeight))

                layout(constraints.maxWidth, constraints.maxHeight) {
                    val cardX = placement.x.toInt()
                    val cardY = placement.y.toInt()
                    card.place(cardX, cardY)
                    arrow.place(
                        x = (placement.arrowX - arrowWidth / 2f).toInt(),
                        y = if (placement.above) cardY + card.height else cardY - arrowHeight,
                    )
                }
            }
        }

        // “跳过”：固定在安全区右上角，避开刘海与挖孔。
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(
                    top = insetPadding.calculateTopPadding() + 12.dp,
                    end = insetPadding.calculateRightPadding(layoutDirection) + 16.dp,
                )
                .heightIn(min = 44.dp)
                .clickable(enabled = !closing) { finish() }
                .padding(horizontal = 8.dp, vertical = 10.dp)
                .testTag("spotlight-skip"),
        ) {
            Text("跳过", fontSize = 13.sp, color = Color.White.copy(alpha = 0.92f))
        }
    }
}

@Composable
private fun SpotlightCard(
    step: SpotlightStep,
    number: Int,
    total: Int,
    compact: Boolean,
    appearScale: () -> Float,
    primaryLabel: String,
    enabled: Boolean,
    onPrimary: () -> Unit,
) {
    val colors = StudyTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .testTag("spotlight-card")
            .semantics { paneTitle = "新手引导" }
            .graphicsLayer {
                val scale = appearScale()
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.line, shape)
            .verticalScroll(rememberScrollState())
            .padding(if (compact) 14.dp else 18.dp),
    ) {
        Text(
            text = step.title,
            fontSize = if (compact) 15.sp else 16.sp,
            lineHeight = if (compact) 21.sp else 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.ink,
        )
        Text(
            text = step.body,
            fontSize = 13.sp,
            lineHeight = if (compact) 19.sp else 20.sp,
            color = colors.muted,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            text = "$number/$total",
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = colors.tertiary,
            modifier = Modifier.padding(top = 10.dp).testTag("spotlight-counter"),
        )
        StudyPrimaryButton(
            text = primaryLabel,
            onClick = onPrimary,
            enabled = enabled,
            modifier = Modifier.padding(top = 12.dp).testTag("spotlight-primary"),
        )
    }
}

@Composable
private fun SpotlightArrow(pointUp: Boolean, fill: Color, stroke: Color) {
    Canvas(Modifier.size(width = 16.dp, height = 8.dp)) {
        val path = Path().apply {
            if (pointUp) {
                moveTo(0f, size.height)
                lineTo(size.width / 2f, 0f)
                lineTo(size.width, size.height)
            } else {
                moveTo(0f, 0f)
                lineTo(size.width / 2f, size.height)
                lineTo(size.width, 0f)
            }
            close()
        }
        drawPath(path, fill)
        drawPath(path, stroke, style = Stroke(width = 1.dp.toPx()))
    }
}
