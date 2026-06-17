package com.meerv.qrpic.qr

import android.graphics.Bitmap
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.decoder.Version

/** A detected QR symbol with everything needed for error-correction analysis. */
data class QrSymbol(
    val version: Version,
    val ecLevel: ErrorCorrectionLevel,
    val dimension: Int,
    /** The deskewed, sampled module grid (dimension x dimension). */
    val bits: BitMatrix,
    /** The text encoded in the QR code, or null if it could not be decoded (e.g. logo too large). */
    val decodedText: String?,
    /** A straightened, module-aligned crop of the original photo (the whole code, logo included). */
    val deskewed: Bitmap?,
)
