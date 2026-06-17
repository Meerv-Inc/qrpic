package com.meerv.qrpic.ecc

import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.decoder.Version

/**
 * The core analysis engine. Given a QR [version] and [ecLevel], it can attribute any set of
 * logo-covered modules to exact codewords and Reed-Solomon blocks, then report the impact on the
 * error-correction budget.
 */
class EccModel(
    val version: Version,
    val ecLevel: ErrorCorrectionLevel,
    private val decodedText: String? = null,
) {
    private val placement = QrPlacement(version)
    private val interleaver = BlockInterleaver(version, ecLevel)
    val dimension: Int = placement.dimension

    fun analyze(region: LogoRegion): EccReport = analyze(region.coveredModules(dimension))

    fun analyze(covered: Set<Module>): EccReport {
        val totalCw = interleaver.totalCodewords
        val nsym = interleaver.ecCodewordsPerBlock
        val corruptedByBlock = IntArray(interleaver.numBlocks)
        val corruptedCodewords = HashSet<Int>()

        val data = placement.dataModules
        val nBits = totalCw * 8
        var coveredDataModules = 0
        val limit = minOf(nBits, data.size)
        for (bit in 0 until limit) {
            if (data[bit] in covered) {
                coveredDataModules++
                corruptedCodewords.add(bit / 8)
            }
        }
        for (cw in corruptedCodewords) {
            corruptedByBlock[interleaver.blockOfRawIndex[cw]]++
        }

        val blocks = (0 until interleaver.numBlocks).map { b ->
            val e = corruptedByBlock[b]
            BlockImpact(
                index = b,
                totalCodewords = interleaver.blockTotalCodewords[b],
                dataCodewords = interleaver.blockDataCodewords[b],
                ecCodewords = nsym,
                erasures = e,
                residualErrorCapacity = maxOf(0, (nsym - e) / 2),
                recoverableAsErasures = e <= nsym,
                recoverableAsErrors = e <= nsym / 2,
            )
        }

        return EccReport(
            version = version.versionNumber,
            dimension = dimension,
            ecLevel = ecLevel.toString(),
            decodedText = decodedText,
            totalCodewords = totalCw,
            totalEcCodewords = nsym * interleaver.numBlocks,
            coveredModules = covered.size,
            coveredDataModules = coveredDataModules,
            corruptedCodewords = corruptedCodewords.size,
            blocks = blocks,
        )
    }
}
