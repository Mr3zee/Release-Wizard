package com.github.mr3zee

import androidx.compose.ui.geometry.Offset
import com.github.mr3zee.editor.MAX_ZOOM
import com.github.mr3zee.editor.MIN_ZOOM
import com.github.mr3zee.editor.handleScrollZoom
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HandleScrollZoomTest {

    private val defaultDensity = 1f
    private val defaultZoom = 1f
    private val defaultPanOffset = Offset.Zero

    @Test
    fun `zero scroll returns null`() {
        val result = handleScrollZoom(
            scrollY = 0f,
            pointerPos = Offset(100f, 100f),
            zoom = defaultZoom,
            panOffset = defaultPanOffset,
            density = defaultDensity,
        )
        assertNull(result)
    }

    @Test
    fun `negative scrollY zooms in`() {
        val result = handleScrollZoom(
            scrollY = -1f,
            pointerPos = Offset(100f, 100f),
            zoom = defaultZoom,
            panOffset = defaultPanOffset,
            density = defaultDensity,
        )
        assertNotNull(result)
        assertTrue(result.first > defaultZoom, "Zoom should increase on negative scroll: ${result.first}")
    }

    @Test
    fun `positive scrollY zooms out`() {
        val result = handleScrollZoom(
            scrollY = 1f,
            pointerPos = Offset(100f, 100f),
            zoom = defaultZoom,
            panOffset = defaultPanOffset,
            density = defaultDensity,
        )
        assertNotNull(result)
        assertTrue(result.first < defaultZoom, "Zoom should decrease on positive scroll: ${result.first}")
    }

    @Test
    fun `zoom in clamped to MAX_ZOOM`() {
        val result = handleScrollZoom(
            scrollY = -1f,
            pointerPos = Offset(100f, 100f),
            zoom = MAX_ZOOM,
            panOffset = defaultPanOffset,
            density = defaultDensity,
        )
        assertNotNull(result)
        assertEquals(MAX_ZOOM, result.first, "Zoom should be clamped to MAX_ZOOM")
    }

    @Test
    fun `zoom out clamped to MIN_ZOOM`() {
        val result = handleScrollZoom(
            scrollY = 1f,
            pointerPos = Offset(100f, 100f),
            zoom = MIN_ZOOM,
            panOffset = defaultPanOffset,
            density = defaultDensity,
        )
        assertNotNull(result)
        assertEquals(MIN_ZOOM, result.first, "Zoom should be clamped to MIN_ZOOM")
    }

    @Test
    fun `zoom toward pointer preserves logical position under pointer`() {
        val pointerPos = Offset(200f, 200f)
        val zoom = 1f
        val panOffset = Offset.Zero
        val density = 1f

        val result = handleScrollZoom(
            scrollY = -1f,
            pointerPos = pointerPos,
            zoom = zoom,
            panOffset = panOffset,
            density = density,
        )
        assertNotNull(result)

        val newZoom = result.first
        val newPanOffset = result.second

        // The logical position under the pointer before and after zooming should be the same.
        // logicalBefore = (pointerPos - panOffset) / (density * zoom)
        val logicalBeforeX = (pointerPos.x - panOffset.x) / (density * zoom)
        val logicalBeforeY = (pointerPos.y - panOffset.y) / (density * zoom)

        // logicalAfter = (pointerPos - newPanOffset) / (density * newZoom)
        val logicalAfterX = (pointerPos.x - newPanOffset.x) / (density * newZoom)
        val logicalAfterY = (pointerPos.y - newPanOffset.y) / (density * newZoom)

        val tolerance = 0.01f
        assertTrue(
            kotlin.math.abs(logicalBeforeX - logicalAfterX) < tolerance,
            "Logical X under pointer should be preserved: before=$logicalBeforeX, after=$logicalAfterX",
        )
        assertTrue(
            kotlin.math.abs(logicalBeforeY - logicalAfterY) < tolerance,
            "Logical Y under pointer should be preserved: before=$logicalBeforeY, after=$logicalAfterY",
        )
    }
}
