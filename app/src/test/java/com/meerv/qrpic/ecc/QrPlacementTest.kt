package com.meerv.qrpic.ecc

import com.google.zxing.qrcode.decoder.Version
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QrPlacementTest {

    @Test
    fun dataModuleCountMatchesCodewordsPlusRemainder() {
        for (v in 1..40) {
            val version = Version.getVersionForNumber(v)
            val placement = QrPlacement(version)
            val dim = placement.dimension

            // Count reserved (function) modules directly from the reconstructed map.
            var functionModules = 0
            for (x in 0 until dim) {
                for (y in 0 until dim) {
                    if (placement.isFunction(x, y)) functionModules++
                }
            }
            val expectedDataModules = dim * dim - functionModules

            // The walker must visit exactly every non-function module, once.
            assertEquals(
                "version $v data module count",
                expectedDataModules,
                placement.dataModules.size,
            )
            assertEquals(
                "version $v data modules must be distinct",
                placement.dataModules.size,
                placement.dataModules.toSet().size,
            )

            // Data modules = totalCodewords*8 + remainder bits (0..7 per the QR spec).
            val remainder = placement.dataModules.size - version.totalCodewords * 8
            assertTrue("version $v remainder bits $remainder in 0..7", remainder in 0..7)

            // No data module may be a function module.
            assertTrue(
                "version $v no data module is a function module",
                placement.dataModules.none { placement.isFunction(it.x, it.y) },
            )
        }
    }
}
