package com.meerv.qrpic.ecc

/**
 * Recomputes the error-correction impact as the logo is shrunk or expanded about its centre,
 * answering "if the logo grows/shrinks by X%, what is left for ECC?".
 *
 * [scalePercent] is a *linear* change: +20 means each side grows 20% (area grows ~44%).
 */
class ScalingSimulator(
    private val model: EccModel,
    private val baseRegion: LogoRegion,
) {
    data class Point(
        val scalePercent: Int,
        val areaPercent: Int,
        val report: EccReport,
    )

    fun at(scalePercent: Int): EccReport =
        model.analyze(baseRegion.scaled(1f + scalePercent / 100f))

    fun curve(fromPercent: Int = -50, toPercent: Int = 100, step: Int = 10): List<Point> {
        val points = ArrayList<Point>()
        var p = fromPercent
        while (p <= toPercent) {
            val factor = 1f + p / 100f
            val areaPercent = (factor * factor * 100f).toInt()
            points.add(Point(p, areaPercent, model.analyze(baseRegion.scaled(factor))))
            p += step
        }
        return points
    }
}
