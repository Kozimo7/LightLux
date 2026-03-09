package com.example.lightluxmeter.domain

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.pow

class LuminosityAnalyzer(private val listener: (luma: Double) -> Unit) : ImageAnalysis.Analyzer {

    private fun ByteBuffer.toByteArray(): ByteArray {
        rewind()
        val data = ByteArray(remaining())
        get(data)
        return data
    }

    override fun analyze(image: ImageProxy) {
        val buffer = image.planes[0].buffer
        val data = buffer.toByteArray()

        // Spot metering: sample center 10% of the frame
        val width = image.width
        val height = image.height
        val spotSize = 0.10
        val left = ((1.0 - spotSize) / 2.0 * width).toInt()
        val right = ((1.0 + spotSize) / 2.0 * width).toInt()
        val top = ((1.0 - spotSize) / 2.0 * height).toInt()
        val bottom = ((1.0 + spotSize) / 2.0 * height).toInt()

        val rowStride = image.planes[0].rowStride
        var sum = 0L
        var count = 0
        for (y in top until bottom) {
            for (x in left until right) {
                val index = y * rowStride + x
                if (index < data.size) {
                    sum += (data[index].toInt() and 0xFF)
                    count++
                }
            }
        }

        val luma = if (count > 0) sum.toDouble() / count else 0.0

        listener(luma)
        image.close()
    }

    companion object {

        // ─────────────────────────────────────────────────
        // Step 1: EV_cam = log2(N_cam^2 / t_cam)
        // ─────────────────────────────────────────────────
        fun calculateEvCamera(aperture: Float, exposureTimeSec: Double): Double {
            if (exposureTimeSec <= 0.0) return 0.0
            val nSquared = (aperture * aperture).toDouble()
            return log2(nSquared / exposureTimeSec)
        }

        // ─────────────────────────────────────────────────
        // Step 2: EV_100 = EV_cam - log2(S_cam / 100) + C
        // ─────────────────────────────────────────────────
        fun calculateEv100(
                evCamera: Double,
                cameraIso: Int,
                calibrationOffset: Double = 0.0
        ): Double {
            val isoFactor = cameraIso.toDouble() / 100.0
            return evCamera - log2(isoFactor) + calibrationOffset
        }

        // ─────────────────────────────────────────────────
        // Step 3: t_film = N_film^2 / (2^EV100 * (S_film / 100))
        // ─────────────────────────────────────────────────
        fun calculateFilmShutterSpeed(ev100: Double, filmIso: Int, filmAperture: Double): Double {
            val nSquared = filmAperture * filmAperture
            val denominator = 2.0.pow(ev100) * (filmIso.toDouble() / 100.0)
            if (denominator <= 0.0) return 60.0 // safety
            return nSquared / denominator
        }

        /** Convenience: given camera metadata, compute EV100 directly. */
        fun computeEv100FromMetadata(
                cameraAperture: Float,
                exposureTimeNs: Long,
                cameraIso: Int,
                calibrationOffset: Double = 0.0
        ): Double {
            val exposureTimeSec = exposureTimeNs / 1_000_000_000.0
            val evCam = calculateEvCamera(cameraAperture, exposureTimeSec)
            return calculateEv100(evCam, cameraIso, calibrationOffset)
        }

        /** Rough lux approximation from EV100. Lux ≈ 2.5 * 2^EV100 */
        fun ev100ToLux(ev100: Double): Double {
            return 2.5 * 2.0.pow(ev100)
        }

        /**
         * Fallback: Maps a luminance value (0-255) to an Exposure Value (EV100). Used when Camera2
         * metadata is not available.
         */
        fun mapLumaToEv(luma: Double): Double {
            val normalized = (luma / 255.0).coerceIn(0.01, 1.0)
            return (ln(normalized) / ln(2.0)) + 15.0
        }

        private val standardSpeeds =
                listOf(
                        1.0 / 8000,
                        1.0 / 4000,
                        1.0 / 2000,
                        1.0 / 1000,
                        1.0 / 500,
                        1.0 / 250,
                        1.0 / 125,
                        1.0 / 60,
                        1.0 / 30,
                        1.0 / 15,
                        1.0 / 8,
                        1.0 / 4,
                        1.0 / 2,
                        1.0,
                        2.0,
                        4.0,
                        8.0,
                        15.0,
                        30.0,
                        60.0
                )
        private val standardSpeedLabels =
                listOf(
                        "1/8000",
                        "1/4000",
                        "1/2000",
                        "1/1000",
                        "1/500",
                        "1/250",
                        "1/125",
                        "1/60",
                        "1/30",
                        "1/15",
                        "1/8",
                        "1/4",
                        "1/2",
                        "1s",
                        "2s",
                        "4s",
                        "8s",
                        "15s",
                        "30s",
                        "60s"
                )

        fun getStandardSpeedLabels(): List<String> = standardSpeedLabels

        /** Formats shutter speed to standard camera fractions (e.g., 1/500). */
        fun formatShutterSpeed(timeSeconds: Double): String {
            var bestDistance = Double.MAX_VALUE
            var bestIndex = 0
            for (i in standardSpeeds.indices) {
                val distance = abs(ln(timeSeconds / standardSpeeds[i]))
                if (distance < bestDistance) {
                    bestDistance = distance
                    bestIndex = i
                }
            }
            return standardSpeedLabels[bestIndex]
        }
    }
}
