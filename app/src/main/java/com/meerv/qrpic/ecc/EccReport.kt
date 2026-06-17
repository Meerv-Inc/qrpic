package com.meerv.qrpic.ecc

/** Error-correction impact on a single Reed-Solomon block. */
data class BlockImpact(
    val index: Int,
    val totalCodewords: Int,
    val dataCodewords: Int,
    val ecCodewords: Int,
    /** Codewords in this block touched by the logo (known positions => erasures). */
    val erasures: Int,
    /**
     * Headroom left for additional *unknown* errors after the logo's erasures are accounted for:
     * floor((ecCodewords - erasures) / 2).
     */
    val residualErrorCapacity: Int,
    /** Decodable if a decoder uses the known logo positions as erasures (capacity = ecCodewords). */
    val recoverableAsErasures: Boolean,
    /** Decodable for a normal field decoder that treats the logo as errors (capacity = ec/2). */
    val recoverableAsErrors: Boolean,
)

enum class Verdict { DECODES_WITH_MARGIN, DECODES_FRAGILE, UNRECOVERABLE }

/**
 * Aggregate error-correction analysis for a QR symbol with a logo over its centre.
 *
 * Reed-Solomon bound per block: `2 * errors + erasures <= ecCodewords`.
 * A logo at a *known* position consumes erasures; the remaining budget for real-world (unknown)
 * damage is the residual error capacity. A normal field decoder, which does not know the logo
 * positions, instead treats every covered codeword as an error (half the capacity).
 */
data class EccReport(
    val version: Int,
    val dimension: Int,
    val ecLevel: String,
    val decodedText: String?,
    val totalCodewords: Int,
    val totalEcCodewords: Int,
    val coveredModules: Int,
    val coveredDataModules: Int,
    val corruptedCodewords: Int,
    val blocks: List<BlockImpact>,
) {
    /** Nominal recovery capability of the symbol's ECC level (per ISO/IEC 18004). */
    val nominalRecoveryPercent: Int = when (ecLevel) {
        "L" -> 7
        "M" -> 15
        "Q" -> 25
        "H" -> 30
        else -> 0
    }

    /** Fraction of the total ECC budget consumed by the logo (erasure view). */
    val budgetConsumedFraction: Float =
        if (totalEcCodewords == 0) 0f else corruptedCodewords.toFloat() / totalEcCodewords

    /** Worst-case residual headroom (codewords) for unknown errors across all blocks. */
    val minResidualErrorCapacity: Int = blocks.minOfOrNull { it.residualErrorCapacity } ?: 0
    val totalResidualErrorCapacity: Int = blocks.sumOf { it.residualErrorCapacity }

    /** Block with the least remaining erasure budget. */
    val worstBlock: BlockImpact? = blocks.minByOrNull { it.ecCodewords - it.erasures }

    val recoverableAsErasures: Boolean = blocks.all { it.recoverableAsErasures }
    val recoverableAsErrors: Boolean = blocks.all { it.recoverableAsErrors }

    val verdict: Verdict = when {
        !recoverableAsErasures -> Verdict.UNRECOVERABLE
        minResidualErrorCapacity <= 0 || !recoverableAsErrors -> Verdict.DECODES_FRAGILE
        else -> Verdict.DECODES_WITH_MARGIN
    }
}
