package com.example.photocad.ui

import androidx.compose.ui.geometry.Offset

data class PageRect(val left: Float, val top: Float, val width: Float, val height: Float)

data class PageTransform(
    val contentRect: PageRect,
    val viewportCenter: Offset,
    val scale: Float,
    val pan: Offset
) {
    init {
        require(contentRect.width > 0f && contentRect.height > 0f)
        require(scale > 0f)
    }

    fun pageToScreen(point: Offset): Offset {
        val base = Offset(contentRect.left + point.x * contentRect.width, contentRect.top + point.y * contentRect.height)
        return Offset(
            viewportCenter.x + pan.x + (base.x - viewportCenter.x) * scale,
            viewportCenter.y + pan.y + (base.y - viewportCenter.y) * scale
        )
    }

    fun screenToPage(screen: Offset): Offset? {
        val normalized = screenToPageUnbounded(screen)
        return normalized.takeIf { it.x in 0f..1f && it.y in 0f..1f }
    }

    fun screenToPageClamped(screen: Offset): Offset = normalizedPagePoint(screenToPageUnbounded(screen))

    fun screenToPageUnbounded(screen: Offset): Offset {
        val base = Offset(
            viewportCenter.x + (screen.x - viewportCenter.x - pan.x) / scale,
            viewportCenter.y + (screen.y - viewportCenter.y - pan.y) / scale
        )
        return Offset((base.x - contentRect.left) / contentRect.width, (base.y - contentRect.top) / contentRect.height)
    }
}

fun fitPageRect(viewportWidth: Float, viewportHeight: Float, pageWidth: Float, pageHeight: Float): PageRect {
    require(viewportWidth > 0f && viewportHeight > 0f && pageWidth > 0f && pageHeight > 0f)
    val fit = minOf(viewportWidth / pageWidth, viewportHeight / pageHeight)
    val width = pageWidth * fit
    val height = pageHeight * fit
    return PageRect((viewportWidth - width) / 2f, (viewportHeight - height) / 2f, width, height)
}

fun normalizedPagePoint(point: Offset): Offset = Offset(point.x.coerceIn(0f, 1f), point.y.coerceIn(0f, 1f))

fun clampPagePan(pan: Offset, scale: Float, contentRect: PageRect, viewportSize: Offset): Offset {
    require(scale >= 1f && viewportSize.x > 0f && viewportSize.y > 0f)
    val center = Offset(viewportSize.x / 2f, viewportSize.y / 2f)
    fun clampAxis(value: Float, start: Float, extent: Float, centerAxis: Float, viewportExtent: Float): Float {
        if (extent * scale <= viewportExtent) return 0f
        val startAtZero = centerAxis + (start - centerAxis) * scale
        val endAtZero = centerAxis + (start + extent - centerAxis) * scale
        return value.coerceIn(viewportExtent - endAtZero, -startAtZero)
    }
    return Offset(
        clampAxis(pan.x, contentRect.left, contentRect.width, center.x, viewportSize.x),
        clampAxis(pan.y, contentRect.top, contentRect.height, center.y, viewportSize.y)
    )
}
