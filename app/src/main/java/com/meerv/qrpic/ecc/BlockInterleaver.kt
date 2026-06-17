package com.meerv.qrpic.ecc

import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.decoder.Version

/**
 * Inverts the QR codeword interleaving so a read-order codeword index can be attributed to its
 * Reed-Solomon block. This replicates the fill order of ZXing's `DataBlocks.getDataBlocks`, but
 * records only `rawIndex -> block` instead of copying bytes.
 *
 * QR places all data codewords (interleaved across blocks) first, then all error-correction
 * codewords (also interleaved). For multi-group versions the later blocks hold one extra data
 * codeword, which is why the data region is filled in two passes.
 */
class BlockInterleaver(version: Version, ecLevel: ErrorCorrectionLevel) {

    /** Error-correction codewords per block (identical for every block of a symbol). */
    val ecCodewordsPerBlock: Int
    val numBlocks: Int
    val blockTotalCodewords: IntArray
    val blockDataCodewords: IntArray

    /** blockOfRawIndex[readOrderCodewordIndex] = block index. */
    val blockOfRawIndex: IntArray
    val totalCodewords: Int

    init {
        val ecBlocks = version.getECBlocksForLevel(ecLevel)
        ecCodewordsPerBlock = ecBlocks.getECCodewordsPerBlock()

        val totals = ArrayList<Int>()
        val datas = ArrayList<Int>()
        for (ecb in ecBlocks.getECBlocks()) {
            repeat(ecb.count) {
                datas.add(ecb.dataCodewords)
                totals.add(ecCodewordsPerBlock + ecb.dataCodewords)
            }
        }
        numBlocks = totals.size
        blockTotalCodewords = totals.toIntArray()
        blockDataCodewords = datas.toIntArray()
        totalCodewords = blockTotalCodewords.sum()

        // Mirror DataBlocks.getDataBlocks placement order.
        val shorterTotal = blockTotalCodewords[0]
        var longerStart = numBlocks - 1
        while (longerStart >= 0 && blockTotalCodewords[longerStart] != shorterTotal) longerStart--
        longerStart++
        val shorterData = shorterTotal - ecCodewordsPerBlock

        val map = IntArray(totalCodewords)
        var off = 0
        // Data codewords shared by all blocks.
        for (i in 0 until shorterData) {
            for (j in 0 until numBlocks) map[off++] = j
        }
        // Extra data codeword held only by the longer blocks.
        for (j in longerStart until numBlocks) map[off++] = j
        // Error-correction codewords, interleaved across all blocks. The EC region spans
        // (shorterTotal - shorterData) = ecCodewordsPerBlock rows, matching ZXing's result[0].length.
        for (i in shorterData until shorterTotal) {
            for (j in 0 until numBlocks) map[off++] = j
        }
        blockOfRawIndex = map
    }
}
