package com.meerv.qrpic.ecc

import kotlin.math.ceil
import kotlin.math.floor

/**
 * A located logo / dead space over the QR symbol, expressed in module coordinates.
 * [centerX]/[centerY] and the half-extents are in module units (a module is one QR cell).
 */
data class LogoRegion(
    val centerX: Float,
    val centerY: Float,
    val halfWidth: Float,
    val halfHeight: Float,
    val shape: Shape = Shape.RECT,
) {
    enum class Shape { RECT, CIRCLE }

    /** Returns a copy scaled about its centre by [factor] (e.g. 1.2f = +20% linear). */
    fun scaled(factor: Float): LogoRegion =
        copy(halfWidth = halfWidth * factor, halfHeight = halfHeight * factor)

    /** Modules whose centre falls inside the region. */
    fun coveredModules(dimension: Int): Set<Module> {
        val result = HashSet<Module>()
        val minX = maxOf(0, floor(centerX - halfWidth).toInt())
        val maxX = minOf(dimension - 1, ceil(centerX + halfWidth).toInt())
        val minY = maxOf(0, floor(centerY - halfHeight).toInt())
        val maxY = minOf(dimension - 1, ceil(centerY + halfHeight).toInt())
        for (x in minX..maxX) {
            for (y in minY..maxY) {
                val px = x + 0.5f
                val py = y + 0.5f
                val inside = when (shape) {
                    Shape.RECT ->
                        px >= centerX - halfWidth && px <= centerX + halfWidth &&
                            py >= centerY - halfHeight && py <= centerY + halfHeight
                    Shape.CIRCLE -> {
                        val dx = (px - centerX) / halfWidth
                        val dy = (py - centerY) / halfHeight
                        dx * dx + dy * dy <= 1f
                    }
                }
                if (inside) result.add(Module(x, y))
            }
        }
        return result
    }
}
