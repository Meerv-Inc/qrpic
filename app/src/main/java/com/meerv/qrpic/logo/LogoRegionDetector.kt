package com.meerv.qrpic.logo

import com.google.zxing.common.BitMatrix
import com.meerv.qrpic.ecc.LogoRegion

/**
 * Auto-detects the central "dead space" where a logo sits.
 *
 * Heuristic: a logo is normally placed over a solid quiet box, so the modules under it are
 * dominated by a single colour. Starting from the centre, we grow a square while each new ring is
 * still mostly that dominant colour, and stop when we hit the surrounding (pseudo-random) data.
 * The user can fine-tune the result on the overlay, so an approximate box is acceptable.
 */
object LogoRegionDetector {

    private const val RING_MATCH_THRESHOLD = 0.7f

    fun detect(bits: BitMatrix): LogoRegion? {
        val dim = bits.height
        val cx = dim / 2
        val cy = dim / 2

        // Dominant colour in the central 3x3.
        var dark = 0
        var total = 0
        for (x in cx - 1..cx + 1) {
            for (y in cy - 1..cy + 1) {
                if (x in 0 until dim && y in 0 until dim) {
                    total++
                    if (bits.get(x, y)) dark++
                }
            }
        }
        val centreDark = dark * 2 >= total

        var best = 0
        var radius = 1
        val maxRadius = dim / 2 - 4
        while (radius <= maxRadius) {
            var match = 0
            var count = 0
            for (x in cx - radius..cx + radius) {
                for (y in cy - radius..cy + radius) {
                    if (x < 0 || y < 0 || x >= dim || y >= dim) continue
                    val onRing = x == cx - radius || x == cx + radius ||
                        y == cy - radius || y == cy + radius
                    if (!onRing) continue
                    count++
                    if (bits.get(x, y) == centreDark) match++
                }
            }
            if (count == 0) break
            if (match.toFloat() / count >= RING_MATCH_THRESHOLD) {
                best = radius
                radius++
            } else {
                break
            }
        }

        if (best < 1) return null
        return LogoRegion(
            centerX = cx + 0.5f,
            centerY = cy + 0.5f,
            halfWidth = best + 0.5f,
            halfHeight = best + 0.5f,
        )
    }
}
