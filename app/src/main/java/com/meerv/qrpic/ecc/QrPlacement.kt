package com.meerv.qrpic.ecc

import com.google.zxing.qrcode.decoder.Version

/** A module coordinate (column [x], row [y]) inside a QR matrix. */
data class Module(val x: Int, val y: Int)

/**
 * Reconstructs, for a given QR [version], the QR-spec function-pattern map and the canonical
 * zig-zag data-placement order.
 *
 * The placement order is exactly the order in which a decoder reads data bits
 * (mirrors ZXing's `BitMatrixParser.readCodewords`). Every 8 consecutive data bits form one
 * codeword in read order, so the mapping `module -> read-order codeword index` follows directly:
 * `codewordIndex = dataBitIndex / 8`.
 *
 * Note: the data mask only flips module *values*, never their positions, so it is irrelevant to
 * the position -> codeword mapping and is intentionally not modelled here.
 */
class QrPlacement(val version: Version) {

    /** Side length in modules = 17 + 4 * versionNumber. */
    val dimension: Int = version.dimensionForVersion

    /** function[x][y] == true  =>  reserved module (finder/separator/timing/alignment/format/version/dark). */
    private val function: Array<BooleanArray> = buildFunctionPattern()

    /** Data modules in canonical read order. The list index is the data-bit index. */
    val dataModules: List<Module> = buildDataModuleOrder()

    fun isFunction(x: Int, y: Int): Boolean = function[x][y]

    private fun setRegion(m: Array<BooleanArray>, left: Int, top: Int, width: Int, height: Int) {
        for (x in left until left + width) {
            for (y in top until top + height) {
                m[x][y] = true
            }
        }
    }

    private fun buildFunctionPattern(): Array<BooleanArray> {
        val dim = dimension
        val m = Array(dim) { BooleanArray(dim) }

        // Three finder patterns + separators + reserved format-info strips.
        setRegion(m, 0, 0, 9, 9)
        setRegion(m, dim - 8, 0, 8, 9)
        setRegion(m, 0, dim - 8, 9, 8)

        // Alignment patterns (skipping the three that would overlap the finder patterns).
        val centers = version.alignmentPatternCenters
        val max = centers.size
        for (rowIdx in 0 until max) {
            val top = centers[rowIdx] - 2
            for (colIdx in 0 until max) {
                val place = (rowIdx != 0 || (colIdx != 0 && colIdx != max - 1)) &&
                    (rowIdx != max - 1 || colIdx != 0)
                if (place) setRegion(m, centers[colIdx] - 2, top, 5, 5)
            }
        }

        // Timing patterns.
        setRegion(m, 6, 9, 1, dim - 17)
        setRegion(m, 9, 6, dim - 17, 1)

        // Version information (version 7 and above).
        if (version.versionNumber > 6) {
            setRegion(m, dim - 11, 0, 3, 6)
            setRegion(m, 0, dim - 11, 6, 3)
        }

        return m
    }

    private fun buildDataModuleOrder(): List<Module> {
        val dim = dimension
        val order = ArrayList<Module>(dim * dim)
        var readingUp = true
        var j = dim - 1
        while (j > 0) {
            if (j == 6) j-- // skip the vertical timing column
            for (count in 0 until dim) {
                val i = if (readingUp) dim - 1 - count else count
                for (col in 0 until 2) {
                    val x = j - col
                    if (!function[x][i]) order.add(Module(x, i))
                }
            }
            readingUp = !readingUp
            j -= 2
        }
        return order
    }
}
