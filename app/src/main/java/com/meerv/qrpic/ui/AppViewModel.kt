package com.meerv.qrpic.ui

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.zxing.common.BitMatrix
import com.meerv.qrpic.ecc.EccModel
import com.meerv.qrpic.ecc.EccReport
import com.meerv.qrpic.ecc.LogoRegion
import com.meerv.qrpic.ecc.ScalingSimulator
import com.meerv.qrpic.logo.LogoRegionDetector
import com.meerv.qrpic.qr.QrDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface UiState {
    data object Camera : UiState
    data object Analyzing : UiState
    data class Error(val message: String) : UiState
    data class Result(
        val bits: BitMatrix,
        val photo: android.graphics.Bitmap?,
        val dimension: Int,
        val region: LogoRegion,
        val report: EccReport,
        val scalePercent: Int,
        val simReport: EccReport,
    ) : UiState
}

class AppViewModel : ViewModel() {

    var state by mutableStateOf<UiState>(UiState.Camera)
        private set

    private var model: EccModel? = null
    private var baseRegion: LogoRegion? = null

    fun analyzeBitmap(bitmap: Bitmap) {
        state = UiState.Analyzing
        viewModelScope.launch(Dispatchers.Default) {
            val result = runCatching {
                val symbol = QrDetector.detect(bitmap)
                    ?: return@runCatching null
                val m = EccModel(symbol.version, symbol.ecLevel, symbol.decodedText)
                val region = LogoRegionDetector.detect(symbol.bits) ?: defaultRegion(symbol.dimension)
                Detected(symbol.bits, symbol.deskewed, m, region)
            }

            withContext(Dispatchers.Main) {
                val detected = result.getOrNull()
                if (result.isFailure) {
                    state = UiState.Error(
                        result.exceptionOrNull()?.message ?: "Could not analyze the image.",
                    )
                } else if (detected == null) {
                    state = UiState.Error(
                        "No QR code found. Frame the whole code so all three corner squares are visible, " +
                            "hold steady, and ensure good lighting.",
                    )
                } else {
                    model = detected.model
                    baseRegion = detected.region
                    val report = detected.model.analyze(detected.region)
                    state = UiState.Result(
                        bits = detected.bits,
                        photo = detected.photo,
                        dimension = detected.model.dimension,
                        region = detected.region,
                        report = report,
                        scalePercent = 0,
                        simReport = report,
                    )
                }
            }
        }
    }

    /** User dragged or resized the logo box on the overlay. */
    fun updateRegion(region: LogoRegion) {
        val m = model ?: return
        val s = state as? UiState.Result ?: return
        baseRegion = region
        val report = m.analyze(region)
        val sim = ScalingSimulator(m, region).at(s.scalePercent)
        state = s.copy(region = region, report = report, simReport = sim)
    }

    /** User moved the "what-if" shrink/expand slider. */
    fun updateScale(percent: Int) {
        val m = model ?: return
        val base = baseRegion ?: return
        val s = state as? UiState.Result ?: return
        val sim = ScalingSimulator(m, base).at(percent)
        state = s.copy(scalePercent = percent, simReport = sim)
    }

    fun reset() {
        model = null
        baseRegion = null
        state = UiState.Camera
    }

    private class Detected(
        val bits: BitMatrix,
        val photo: android.graphics.Bitmap?,
        val model: EccModel,
        val region: LogoRegion,
    )

    private fun defaultRegion(dimension: Int): LogoRegion {
        val half = dimension * 0.15f
        return LogoRegion(
            centerX = dimension / 2 + 0.5f,
            centerY = dimension / 2 + 0.5f,
            halfWidth = half,
            halfHeight = half,
        )
    }
}
