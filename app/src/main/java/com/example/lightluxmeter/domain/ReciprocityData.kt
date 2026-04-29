package com.example.lightluxmeter.domain

import kotlin.math.pow

/**
 * Film stock reciprocity failure data.
 * t_corrected = t_metered ^ pFactor
 *
 * The p-factor values are approximations based on publicly available reciprocity data.
 */
data class FilmStock(
    val name: String,
    val pFactor: Double
)

object ReciprocityData {

    val filmStocks = listOf(
        // Kodak Color Negative
        FilmStock("Kodak Portra 160", 1.30),
        FilmStock("Kodak Portra 400", 1.30),
        FilmStock("Kodak Portra 800", 1.30),
        FilmStock("Kodak Ektar 100", 1.30),
        FilmStock("Kodak Gold 200", 1.33),
        FilmStock("Kodak ColorPlus 200", 1.33),
        FilmStock("Kodak UltraMax 400", 1.33), 

        // Kodak B&W
        FilmStock("Kodak T-Max 100", 1.15),
        FilmStock("Kodak T-Max 400", 1.15),
        FilmStock("Kodak Tri-X 400", 1.33),

        // Ilford / Harman B&W
        FilmStock("Ilford HP5 Plus 400", 1.31),
        FilmStock("Ilford FP4 Plus 125", 1.40),
        FilmStock("Ilford Delta 100", 1.26),
        FilmStock("Ilford Delta 400", 1.33),
        FilmStock("Ilford Delta 3200", 1.33),
        FilmStock("Ilford XP2 Super 400", 1.31), 
        FilmStock("Ilford SFX 200", 1.33),       
        FilmStock("Kentmere Pan 100", 1.33),
        FilmStock("Kentmere Pan 200", 1.32),
        FilmStock("Kentmere Pan 400", 1.31),
        FilmStock("Harman Phoenix 200", 1.35), 

        // Fujifilm
        FilmStock("Fujifilm Neopan Acros 100 II", 1.00),
        FilmStock("Fujifilm Provia 100F", 1.10),
        FilmStock("Fujifilm Velvia 50", 1.20),
        FilmStock("Fujifilm 200 (New)", 1.33),
        FilmStock("Fujifilm 400 (New)", 1.33),
        FilmStock("Fujifilm Superia Premium 400", 1.33),
        FilmStock("Fujifilm C200", 1.33),

        // CineStill / Vision3
        FilmStock("CineStill 800T", 1.30),
        FilmStock("CineStill 50D", 1.30),
        FilmStock("CineStill 400D", 1.30),

        // Other
        FilmStock("Lucky Color C200", 1.35),
        FilmStock("Fomapan 100", 1.54),
        FilmStock("Lomography Color 100", 1.30),
        FilmStock("Lomography Color 400/800", 1.33)
    )

    /**
     * Applies reciprocity correction.
     * Only applies when metered time > 1 second (reciprocity failure is negligible below 1s).
     */
    fun calculateCorrectedTime(meteredSeconds: Double, pFactor: Double): Double {
        if (meteredSeconds <= 1.0) return meteredSeconds
        return meteredSeconds.pow(pFactor)
    }

    /**
     * Formats a time in seconds to a human-readable string.
     */
    fun formatTime(seconds: Double): String {
        return when {
            seconds < 1.0 -> {
                val denom = (1.0 / seconds).toInt()
                "1/${denom}s"
            }
            seconds < 60.0 -> String.format("%.1fs", seconds)
            seconds < 3600.0 -> {
                val mins = (seconds / 60.0).toInt()
                val secs = (seconds % 60.0).toInt()
                "${mins}m ${secs}s"
            }
            else -> {
                val hrs = (seconds / 3600.0).toInt()
                val mins = ((seconds % 3600.0) / 60.0).toInt()
                "${hrs}h ${mins}m"
            }
        }
    }

    /**
     * Returns a curated list of long exposure times in seconds,
     * from 1s to 5m+ with 1/3 stop granularity.
     */
    fun getLongExposureValues(): List<Double> {
        return listOf(
            // 1s to 20s
            1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0, 
            11.0, 12.0, 13.0, 14.0, 15.0, 16.0, 17.0, 18.0, 19.0, 20.0,
            // 25s to 60s
            25.0, 30.0, 35.0, 40.0, 45.0, 50.0, 55.0, 60.0,
            // 80s to 600s
            80.0, 100.0, 120.0, 150.0, 180.0, 240.0, 300.0, 
            360.0, 420.0, 480.0, 540.0, 600.0
        )
    }
}
