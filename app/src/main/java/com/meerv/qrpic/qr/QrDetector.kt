package com.meerv.qrpic.qr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.ResultPoint
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.decoder.Decoder
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.decoder.Version
import com.google.zxing.qrcode.detector.Detector

/**
 * Detects a QR symbol in a captured [Bitmap] using ZXing's detector, samples it to a clean module
 * grid, and extracts the metadata the ECC engine needs: version, error-correction level, and the
 * decoded text.
 *
 * The ECC level is read directly from the QR *format information* (a BCH-protected field next to
 * the finder patterns) rather than from a full decode. That makes detection robust even when a
 * large central logo prevents the payload from decoding — exactly the case this app targets.
 */
object QrDetector {

    /** Result of detection, including whether the payload actually decoded. */
    fun detect(bitmap: Bitmap): QrSymbol? {
        val width = bitmap.width
        val height = bitmap.height
        if (width == 0 || height == 0) return null

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val source = RGBLuminanceSource(width, height, pixels)
        val binary = BinaryBitmap(HybridBinarizer(source))

        val detectorResult = try {
            Detector(binary.blackMatrix).detect()
        } catch (e: Exception) {
            return null
        }

        val bits = detectorResult.bits
        val dimension = bits.height
        val version = Version.getProvisionalVersionForDimension(dimension)
        val ecLevel = readEcLevel(bits, dimension) ?: return null

        val decodedText = try {
            Decoder().decode(bits).text
        } catch (e: Exception) {
            null
        }

        val deskewed = runCatching { deskew(bitmap, detectorResult.points, dimension) }.getOrNull()

        return QrSymbol(
            version = version,
            ecLevel = ecLevel,
            dimension = dimension,
            bits = bits,
            decodedText = decodedText,
            deskewed = deskewed,
        )
    }

    /** Edge length (px) of the deskewed photo. The module grid 0..dimension maps onto 0..[DESKEW_SIZE]. */
    private const val DESKEW_SIZE = 720

    /**
     * Produces a deskewed, module-aligned crop of the original photo so the *entire* QR code (logo and
     * all) can be shown straightened in the UI. Reuses the QR's own finder/alignment points so the
     * result lines up exactly with module coordinates: module (m) -> pixel (m * DESKEW_SIZE / dimension).
     */
    private fun deskew(src: Bitmap, points: Array<ResultPoint>, dimension: Int): Bitmap? {
        if (points.size < 3) return null
        val cell = DESKEW_SIZE.toFloat() / dimension

        // ZXing QR point order: [bottomLeft, topLeft, topRight, (alignment)] — all finder centres.
        val bottomLeft = points[0]
        val topLeft = points[1]
        val topRight = points[2]

        val srcPts = ArrayList<Float>()
        val dstPts = ArrayList<Float>()
        fun pair(p: ResultPoint, moduleX: Float, moduleY: Float) {
            srcPts.add(p.x); srcPts.add(p.y)
            dstPts.add(moduleX * cell); dstPts.add(moduleY * cell)
        }
        pair(topLeft, 3.5f, 3.5f)
        pair(topRight, dimension - 3.5f, 3.5f)
        pair(bottomLeft, 3.5f, dimension - 3.5f)
        var count = 3
        if (points.size >= 4) {
            pair(points[3], dimension - 6.5f, dimension - 6.5f) // alignment pattern centre
            count = 4
        }

        val matrix = Matrix()
        if (!matrix.setPolyToPoly(srcPts.toFloatArray(), 0, dstPts.toFloatArray(), 0, count)) return null

        val out = Bitmap.createBitmap(DESKEW_SIZE, DESKEW_SIZE, Bitmap.Config.ARGB_8888)
        Canvas(out).apply {
            drawColor(Color.WHITE)
            drawBitmap(src, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
        }
        return out
    }

    // --- QR format-information decoding (15-bit BCH(15,5), generator 0x537, mask 0x5412) ---

    private const val FORMAT_GENERATOR = 0x537
    private const val FORMAT_MASK = 0x5412

    private fun readEcLevel(bits: BitMatrix, dimension: Int): ErrorCorrectionLevel? {
        // Copy 1: along the top-left finder pattern.
        var info1 = 0
        for (i in 0 until 6) info1 = copyBit(bits, i, 8, info1)
        info1 = copyBit(bits, 7, 8, info1)
        info1 = copyBit(bits, 8, 8, info1)
        info1 = copyBit(bits, 8, 7, info1)
        for (j in 5 downTo 0) info1 = copyBit(bits, 8, j, info1)

        // Copy 2: split across the top-right and bottom-left finder patterns.
        var info2 = 0
        val jMin = dimension - 7
        for (j in dimension - 1 downTo jMin) info2 = copyBit(bits, 8, j, info2)
        for (i in dimension - 8 until dimension) info2 = copyBit(bits, i, 8, info2)

        val data5 = decodeFormat(info1) ?: decodeFormat(info2) ?: bestFormat(info1, info2) ?: return null
        val ecBits = (data5 shr 3) and 0x3
        return ErrorCorrectionLevel.forBits(ecBits)
    }

    private fun copyBit(bits: BitMatrix, x: Int, y: Int, acc: Int): Int =
        if (bits.get(x, y)) (acc shl 1) or 1 else acc shl 1

    /** Exact decode: returns the 5-bit data value if [received] is a valid format codeword. */
    private fun decodeFormat(received: Int): Int? {
        for (data5 in 0 until 32) {
            if (encodeFormat(data5) == received) return data5
        }
        return null
    }

    /** Best-effort decode: nearest valid codeword across both copies (BCH corrects up to 3 bits). */
    private fun bestFormat(info1: Int, info2: Int): Int? {
        var best = -1
        var bestDist = Int.MAX_VALUE
        for (data5 in 0 until 32) {
            val cw = encodeFormat(data5)
            val d = minOf(Integer.bitCount(cw xor info1), Integer.bitCount(cw xor info2))
            if (d < bestDist) {
                bestDist = d
                best = data5
            }
        }
        return if (bestDist <= 3) best else null
    }

    /** Encodes a 5-bit format value into its 15-bit masked codeword. */
    private fun encodeFormat(data5: Int): Int {
        var rem = data5 shl 10
        while (highestBit(rem) >= 10) {
            rem = rem xor (FORMAT_GENERATOR shl (highestBit(rem) - 10))
        }
        return ((data5 shl 10) or rem) xor FORMAT_MASK
    }

    /** Index of the most-significant set bit, or -1 for zero. */
    private fun highestBit(value: Int): Int = 31 - Integer.numberOfLeadingZeros(value or 0)
}
