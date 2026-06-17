package com.meerv.qrpic.ecc

import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.decoder.Version
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EccModelTest {

    private val levels = arrayOf(
        ErrorCorrectionLevel.L,
        ErrorCorrectionLevel.M,
        ErrorCorrectionLevel.Q,
        ErrorCorrectionLevel.H,
    )

    @Test
    fun interleaverTotalsMatchZxingTables() {
        for (v in 1..40) {
            val version = Version.getVersionForNumber(v)
            for (level in levels) {
                val bi = BlockInterleaver(version, level)
                assertEquals(
                    "v$v $level total codewords",
                    version.totalCodewords,
                    bi.totalCodewords,
                )
                val dataPlusEc = bi.blockDataCodewords.sum() + bi.ecCodewordsPerBlock * bi.numBlocks
                assertEquals("v$v $level data+ec == total", bi.totalCodewords, dataPlusEc)
                // Every raw index maps to a real block.
                assertTrue(bi.blockOfRawIndex.all { it in 0 until bi.numBlocks })
            }
        }
    }

    @Test
    fun fullCoverageCorruptsEveryCodeword() {
        for (v in intArrayOf(1, 2, 7, 10, 25, 40)) {
            val version = Version.getVersionForNumber(v)
            for (level in levels) {
                val model = EccModel(version, level)
                val whole = LogoRegion(
                    centerX = model.dimension / 2f,
                    centerY = model.dimension / 2f,
                    halfWidth = model.dimension.toFloat(),
                    halfHeight = model.dimension.toFloat(),
                )
                val report = model.analyze(whole)
                assertEquals(
                    "v$v $level full coverage corrupts all codewords",
                    report.totalCodewords,
                    report.corruptedCodewords,
                )
                // Each block's erasures equal its full codeword count, so it is unrecoverable.
                report.blocks.forEach { b ->
                    assertEquals(b.totalCodewords, b.erasures)
                }
                assertEquals(Verdict.UNRECOVERABLE, report.verdict)
            }
        }
    }

    @Test
    fun noCoverageLeavesFullBudget() {
        val version = Version.getVersionForNumber(5)
        val model = EccModel(version, ErrorCorrectionLevel.Q)
        val report = model.analyze(emptySet())
        assertEquals(0, report.corruptedCodewords)
        assertEquals(Verdict.DECODES_WITH_MARGIN, report.verdict)
        // Residual headroom equals floor(ec/2) per block when nothing is corrupted.
        report.blocks.forEach { b ->
            assertEquals(b.ecCodewords / 2, b.residualErrorCapacity)
        }
    }

    @Test
    fun largerLogoNeverIncreasesHeadroom() {
        val version = Version.getVersionForNumber(6)
        val model = EccModel(version, ErrorCorrectionLevel.H)
        val center = model.dimension / 2f
        var previousHeadroom = Int.MAX_VALUE
        var previousCorrupted = -1
        var half = 1f
        while (half < model.dimension / 2f) {
            val region = LogoRegion(center, center, half, half)
            val report = model.analyze(region)
            assertTrue(
                "corrupted codewords must be monotonic in logo size",
                report.corruptedCodewords >= previousCorrupted,
            )
            assertTrue(
                "headroom must not increase as the logo grows",
                report.totalResidualErrorCapacity <= previousHeadroom,
            )
            previousCorrupted = report.corruptedCodewords
            previousHeadroom = report.totalResidualErrorCapacity
            half += 1f
        }
    }

    @Test
    fun corruptedCodewordsBoundedByCoveredDataModules() {
        val version = Version.getVersionForNumber(4)
        val model = EccModel(version, ErrorCorrectionLevel.M)
        val center = model.dimension / 2f
        val region = LogoRegion(center, center, 4f, 4f)
        val report = model.analyze(region)
        // Each covered data module belongs to exactly one codeword.
        assertTrue(report.corruptedCodewords <= report.coveredDataModules)
        // ...and 8 modules make a codeword, so at least ceil(covered/8) codewords are touched.
        val lowerBound = (report.coveredDataModules + 7) / 8
        assertTrue(report.corruptedCodewords >= lowerBound)
    }
}
