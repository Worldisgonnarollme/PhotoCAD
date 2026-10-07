package com.example.photocad.report

data class NormalizedCrop(val left: Float, val top: Float, val right: Float, val bottom: Float)

/** A 70% context window, fitted to the PDF slot and clamped at page edges. */
fun pointCrop(
    pointX: Float,
    pointY: Float,
    sourceWidth: Int,
    sourceHeight: Int,
    targetWidth: Float,
    targetHeight: Float
): NormalizedCrop {
    require(sourceWidth > 0 && sourceHeight > 0 && targetWidth > 0f && targetHeight > 0f)
    val aspect = targetWidth / targetHeight
    val cropWidth = minOf(sourceWidth * 0.7f, sourceHeight * 0.7f * aspect)
    val cropHeight = cropWidth / aspect
    val widthFraction = cropWidth / sourceWidth
    val heightFraction = cropHeight / sourceHeight
    val left = (pointX.coerceIn(0f, 1f) - widthFraction / 2f).coerceIn(0f, 1f - widthFraction)
    val top = (pointY.coerceIn(0f, 1f) - heightFraction / 2f).coerceIn(0f, 1f - heightFraction)
    return NormalizedCrop(left, top, left + widthFraction, top + heightFraction)
}
