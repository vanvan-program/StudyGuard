package com.focusforceplus.app.study.ui

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 气泡定位算法的纯几何测试：方向选择、夹紧、三角位置与高度上限。 */
class SpotlightPlacementTest {

    private val safe = Rect(0f, 0f, 390f, 844f)
    private val margin = 16f
    private val gap = 12f
    private val arrowInset = 16f

    private fun place(target: Rect, card: Size, area: Rect = safe) =
        placeBubble(target, card, area, margin, gap, arrowInset)

    @Test
    fun `prefers below when there is room`() {
        val target = Rect(100f, 100f, 290f, 160f)
        val result = place(target, Size(280f, 120f))
        assertFalse(result.above)
        assertEquals(172f, result.y, 0.01f)
        assertEquals(55f, result.x, 0.01f)
        assertEquals(844f - 172f, result.maxHeight, 0.01f)
        assertEquals(195f, result.arrowX, 0.01f)
    }

    @Test
    fun `moves above when below is too tight`() {
        val target = Rect(100f, 700f, 290f, 760f)
        val result = place(target, Size(280f, 200f))
        assertTrue(result.above)
        assertEquals(488f, result.y, 0.01f)
        assertEquals(195f, result.arrowX, 0.01f)
    }

    @Test
    fun `falls back to the roomier side and caps height`() {
        val target = Rect(60f, 380f, 330f, 440f)
        val result = place(target, Size(280f, 500f))
        assertFalse(result.above)
        assertEquals(844f - 452f, result.maxHeight, 0.01f)
        assertEquals(452f, result.y, 0.01f)
    }

    @Test
    fun `clamps horizontally inside the safe area`() {
        val target = Rect(0f, 200f, 40f, 240f)
        val result = place(target, Size(300f, 120f))
        assertEquals(margin, result.x, 0.01f)
        assertTrue(result.arrowX >= result.x + arrowInset - 0.01f)

        val rightTarget = Rect(350f, 200f, 390f, 240f)
        val right = place(rightTarget, Size(300f, 120f))
        assertEquals(390f - margin - 300f, right.x, 0.01f)
        assertTrue(right.arrowX <= right.x + 300f - arrowInset + 0.01f)
    }

    @Test
    fun `keeps the arrow inside the card corner radius`() {
        val target = Rect(0f, 200f, 20f, 240f)
        val result = place(target, Size(300f, 120f))
        assertTrue(result.arrowX >= result.x + arrowInset - 0.01f)
        assertEquals(result.x + arrowInset, result.arrowX, 0.01f)
    }

    @Test
    fun `handles landscape with a wider safe area`() {
        val wide = Rect(0f, 0f, 844f, 390f)
        val target = Rect(400f, 80f, 480f, 140f)
        val result = place(target, Size(360f, 120f), wide)
        assertFalse(result.above)
        assertEquals(152f, result.y, 0.01f)
        assertEquals(440f - 180f, result.x, 0.01f)
    }

    @Test
    fun `never produces negative height when no space is left`() {
        val tiny = Rect(0f, 0f, 320f, 200f)
        val target = Rect(60f, 120f, 260f, 180f)
        val result = place(target, Size(280f, 300f), tiny)
        assertTrue(result.maxHeight >= 0f)
        assertTrue(result.y >= tiny.top - 0.01f)
    }
}