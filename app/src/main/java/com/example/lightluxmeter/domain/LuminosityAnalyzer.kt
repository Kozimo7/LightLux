package com.example.lightluxmeter.domain

import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import kotlin.collections.listOf
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

enum class MeteringMode { SPOT, CENTER_WEIGHTED }

class LuminosityAnalyzer(private val listener: (luma: Double) -> Unit) : ImageAnalysis.Analyzer {

    @Volatile
    var spotPosition: Pair<Double, Double> = 0.5 to 0.5

    @Volatile
    var meteringMode: MeteringMode = MeteringMode.SPOT

    override fun analyze(image: ImageProxy) {
        try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val width = image.width
            val height = image.height
            val rowStride = plane.rowStride

            val luma = when (meteringMode) {
                MeteringMode.SPOT -> analyzeSpot(buffer, width, height, rowStride)
                MeteringMode.CENTER_WEIGHTED -> analyzeCenterWeighted(buffer, width, height, rowStride)
            }

            listener(luma)
        } catch (e: Exception) {
            Log.e("LuminosityAnalyzer", "Error analyzing image", e)
        } finally {
            image.close()
        }
    }

    /** Original spot metering: small 5% region around the tap position. */
    private fun analyzeSpot(buffer: java.nio.ByteBuffer, width: Int, height: Int, rowStride: Int): Double {
        val spotSize = 0.05

        val currentPos = spotPosition
        val centerX = (currentPos.first * width).coerceIn(0.0, width.toDouble())
        val centerY = (currentPos.second * height).coerceIn(0.0, height.toDouble())

        val left = (centerX - (spotSize / 2.0 * width)).toInt().coerceAtLeast(0)
        val right = (centerX + (spotSize / 2.0 * width)).toInt().coerceAtMost(width)
        val top = (centerY - (spotSize / 2.0 * height)).toInt().coerceAtLeast(0)
        val bottom = (centerY + (spotSize / 2.0 * height)).toInt().coerceAtMost(height)

        var sum = 0L
        var count = 0
        
        for (y in top until bottom) {
            for (x in left until right) {
                val index = y * rowStride + x
                if (index < buffer.capacity()) {
                    sum += (buffer.get(index).toInt() and 0xFF)
                    count++
                }
            }
        }

        return if (count > 0) sum.toDouble() / count else 0.0
    }

    /**
     * 60/40 center-weighted metering (Nikon FE2 style).
     * 60% weight from the center circle (30% of min dimension radius),
     * 40% weight from the surrounding area.
     * Samples every 4th pixel for performance.
     */
    private fun analyzeCenterWeighted(buffer: java.nio.ByteBuffer, width: Int, height: Int, rowStride: Int): Double {
        val cx = width / 2.0
        val cy = height / 2.0
        val radius = kotlin.math.min(width, height) * 0.15 // 30% of min dim diameter = 15% radius
        val radiusSq = radius * radius

        var centerSum = 0L
        var centerCount = 0
        var surroundSum = 0L
        var surroundCount = 0

        // Sample every 4th pixel for performance
        val step = 4
        for (y in 0 until height step step) {
            for (x in 0 until width step step) {
                val index = y * rowStride + x
                if (index < buffer.capacity()) {
                    val pixel = buffer.get(index).toInt() and 0xFF
                    val dx = x - cx
                    val dy = y - cy
                    if (dx * dx + dy * dy <= radiusSq) {
                        centerSum += pixel
                        centerCount++
                    } else {
                        surroundSum += pixel
                        surroundCount++
                    }
                }
            }
        }

        val centerMean = if (centerCount > 0) centerSum.toDouble() / centerCount else 0.0
        val surroundMean = if (surroundCount > 0) surroundSum.toDouble() / surroundCount else 0.0

        return 0.6 * centerMean + 0.4 * surroundMean
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
                cameraIso: Int
        ): Double {
            val isoFactor = cameraIso.toDouble() / 100.0
            return evCamera - log2(isoFactor)
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

        /**
         * Solve for aperture: N = sqrt(t * 2^EV100 * (ISO/100))
         */
        fun calculateFilmAperture(ev100: Double, filmIso: Int, filmShutterSpeed: Double): Double {
            val value = filmShutterSpeed * 2.0.pow(ev100) * (filmIso.toDouble() / 100.0)
            if (value <= 0.0) return 1.4
            return kotlin.math.sqrt(value)
        }

        /**
         * Solve for ISO: ISO = 100 * N^2 / (t * 2^EV100)
         */
        fun calculateFilmIso(ev100: Double, filmAperture: Double, filmShutterSpeed: Double): Int {
            val denominator = filmShutterSpeed * 2.0.pow(ev100)
            if (denominator <= 0.0) return 100
            val iso = 100.0 * (filmAperture * filmAperture) / denominator
            return iso.roundToInt().coerceIn(25, 12800)
        }

        /**
         * Snaps a computed aperture value to the nearest entry in the given list.
         * Returns the index into the list.
         */
        fun snapToNearestAperture(computed: Double, options: List<Double>): Int {
            var bestIndex = 0
            var bestDiff = Double.MAX_VALUE
            for ((i, f) in options.withIndex()) {
                val diff = abs(computed - f)
                if (diff < bestDiff) {
                    bestDiff = diff
                    bestIndex = i
                }
            }
            return bestIndex
        }

        /**
         * Snaps a computed ISO value to the nearest entry in the given list.
         * Returns the index into the list.
         */
        fun snapToNearestIso(computed: Int, options: List<Int>): Int {
            var bestIndex = 0
            var bestDiff = Int.MAX_VALUE
            for ((i, iso) in options.withIndex()) {
                val diff = abs(computed - iso)
                if (diff < bestDiff) {
                    bestDiff = diff
                    bestIndex = i
                }
            }
            return bestIndex
        }

        /** Rough lux approximation from EV100. Lux ≈ 2.5 * 2^EV100 */
        fun ev100ToLux(ev100: Double): Double {
            return 2.5 * 2.0.pow(ev100)
        }

        /** Convenience: given camera metadata, compute EV100 directly. */
        fun computeEv100FromMetadata(
                cameraAperture: Float,
                exposureTimeNs: Long,
                cameraIso: Int
        ): Double {
            val exposureTimeSec = exposureTimeNs / 1_000_000_000.0
            val evCam = calculateEvCamera(cameraAperture, exposureTimeSec)
            return calculateEv100(evCam, cameraIso)
        }

        /**
         * Fallback: Maps a luminance value (0-255) to an Exposure Value (EV100). Used when Camera2
         * metadata is not available.
         */
        fun mapLumaToEv(luma: Double): Double {
            val normalized = (luma / 255.0).coerceIn(0.01, 1.0)
            return (ln(normalized) / ln(2.0)) + 15.0
        }

        /**
         * Calculates the flash distance based on Aperture, ISO, Flash Power, and GN. Returns
         * distance rounded to 1 decimal place.
         */
        fun calculateFlashDistance(
                baseGN: Double,
                iso: Int,
                aperture: Float,
                powerFraction: Double
        ): Double {
            val isoMultiplier = kotlin.math.sqrt(iso / 100.0)
            val powerMultiplier = kotlin.math.sqrt(powerFraction)

            val effectiveGN = baseGN * isoMultiplier * powerMultiplier
            val distance = effectiveGN / aperture

            return distance
        }

        private val FULL_STOPS: List<Pair<Double, String>> =
                listOf(
                        30.0 to "30s",
                        25.0 to "25s",
                        20.0 to "20s",
                        15.0 to "15s",
                        12.0 to "12s",
                        10.0 to "10s",
                        8.0 to "8s",
                        6.0 to "6s",
                        5.0 to "5s",
                        4.0 to "4s",
                        2.0 to "2s",
                        1.0 to "1s",
                        0.5 to "1/2",
                        0.25 to "1/4",
                        0.125 to "1/8",
                        0.0666 to "1/15",
                        0.0333 to "1/30",
                        0.0166 to "1/60",
                        0.008 to "1/125",
                        0.004 to "1/250",
                        0.002 to "1/500",
                        0.001 to "1/1000",
                        0.0005 to "1/2000",
                        0.00025 to "1/4000",
                        0.000125 to "1/8000"
                )

        private val HALF_STOPS: List<Pair<Double, String>> =
                listOf(
                        30.0 to "30s",
                        25.0 to "25s",
                        20.0 to "20s",
                        15.0 to "15s",
                        12.0 to "12s",
                        10.0 to "10s",
                        8.0 to "8s",
                        6.0 to "6s",
                        5.0 to "5s",
                        4.0 to "4s",
                        2.0 to "2s",
                        1.0 to "1s",
                        0.5 to "1/2",
                        0.25 to "1/4",
                        0.166 to "1/6",
                        0.125 to "1/8",
                        0.1 to "1/10",
                        0.0666 to "1/15",
                        0.05 to "1/20",
                        0.0333 to "1/30",
                        0.0222 to "1/45",
                        0.0166 to "1/60",
                        0.0111 to "1/90",
                        0.008 to "1/125",
                        0.0055 to "1/180",
                        0.004 to "1/250",
                        0.0028 to "1/350",
                        0.002 to "1/500",
                        0.0013 to "1/750",
                        0.001 to "1/1000",
                        0.00066 to "1/1500",
                        0.0005 to "1/2000",
                        0.00033 to "1/3000",
                        0.00025 to "1/4000",
                        0.00016 to "1/6000",
                        0.000125 to "1/8000"
                )

        private val THIRD_STOPS: List<Pair<Double, String>> =
                listOf(
                        30.0 to "30s",
                        25.0 to "25s",
                        20.0 to "20s",
                        15.0 to "15s",
                        12.0 to "12s",
                        10.0 to "10s",
                        8.0 to "8s",
                        6.0 to "6s",
                        5.0 to "5s",
                        4.0 to "4s",
                        2.0 to "2s",
                        1.0 to "1s",
                        0.5 to "1/2",
                        0.25 to "1/4",
                        0.2 to "1/5",
                        0.166 to "1/6",
                        0.125 to "1/8",
                        0.1 to "1/10",
                        0.0769 to "1/13",
                        0.0666 to "1/15",
                        0.05 to "1/20",
                        0.04 to "1/25",
                        0.0333 to "1/30",
                        0.025 to "1/40",
                        0.02 to "1/50",
                        0.0166 to "1/60",
                        0.0125 to "1/80",
                        0.01 to "1/100",
                        0.008 to "1/125",
                        0.00625 to "1/160",
                        0.005 to "1/200",
                        0.004 to "1/250",
                        0.00312 to "1/320",
                        0.0025 to "1/400",
                        0.002 to "1/500",
                        0.00156 to "1/640",
                        0.00125 to "1/800",
                        0.001 to "1/1000",
                        0.0008 to "1/1250",
                        0.00062 to "1/1600",
                        0.0005 to "1/2000",
                        0.0004 to "1/2500",
                        0.00031 to "1/3200",
                        0.00025 to "1/4000",
                        0.0002 to "1/5000",
                        0.00015 to "1/6400",
                        0.000125 to "1/8000"
                )

        /**
         * Formats shutter speed to standard camera fractions (e.g., 1/500) based on configuration.
         */
        fun formatShutterSpeed(timeSeconds: Double, stepsConfig: String = "third"): String {

            val targetList =
                    when (stepsConfig) {
                        "full" -> FULL_STOPS
                        "half" -> HALF_STOPS
                        else -> THIRD_STOPS // Default is "third"
                    }

            var closestMatch = targetList[0].second
            var smallestDiff = Double.MAX_VALUE

            for ((value, label) in targetList) {
                val diff = abs(timeSeconds - value)
                if (diff < smallestDiff) {
                    smallestDiff = diff
                    closestMatch = label
                }
            }

            return closestMatch
        }

        /** Returns the list of standard speed labels based on configuration. */
        fun fetchStandardSpeedLabels(stepsConfig: String = "third"): List<String> {
            val targetList =
                    when (stepsConfig) {
                        "full" -> FULL_STOPS
                        "half" -> HALF_STOPS
                        else -> THIRD_STOPS
                    }
            return targetList.map { it.second }.reversed() // Reversed from shortest to longest
        }

        /** Returns the list of standard speed values (seconds) matching the label order. */
        fun fetchStandardSpeedValues(stepsConfig: String = "third"): List<Double> {
            val targetList =
                    when (stepsConfig) {
                        "full" -> FULL_STOPS
                        "half" -> HALF_STOPS
                        else -> THIRD_STOPS
                    }
            return targetList.map { it.first }.reversed() // Reversed from shortest to longest
        }
    }
}
