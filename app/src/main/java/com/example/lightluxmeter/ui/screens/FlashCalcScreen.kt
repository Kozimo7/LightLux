package com.example.lightluxmeter.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lightluxmeter.domain.LuminosityAnalyzer
import kotlin.math.pow
import kotlin.math.roundToInt

private val DarkBg = Color(0xFF1A1A1A)
private val CardBg = Color(0xFF2A2A2A)
private val Amber = Color(0xFFFFB74D)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashCalcScreen() {
        // 1. Definition of State variables with default values
        var gnIndex by remember { mutableIntStateOf(1) } // 28 default
        var isoIndex by remember { mutableIntStateOf(3) } // 400 ISO default
        var apertureIndex by remember { mutableIntStateOf(6) } // f/5.6 default
        var powerIndex by remember { mutableIntStateOf(12) } // 1/4 default
        var previousPowerIndex by remember { mutableIntStateOf(12) }

        // Scale mappings for discrete sliders
        val gnValues = listOf(15f, 28f, 30f, 36f, 42f, 50f, 53f, 58f)
        val gnFocalLengths = listOf(14, 24, 28, 35, 50, 70, 80, 105)
        val isoValues = listOf(50, 100, 200, 400, 800, 1600, 3200, 6400)
        val apertureValues =
                listOf(
                        1.8f,
                        2.4f,
                        2.8f,
                        3.5f,
                        4.0f,
                        4.8f,
                        5.6f,
                        6.7f,
                        8.0f,
                        9.5f,
                        11.0f,
                        13.0f,
                        16.0f,
                        19.0f,
                        22.0f
                )
        val powerBases = listOf("1/64", "1/32", "1/16", "1/8", "1/4", "1/2", "1/1")
        val powerValues =
                mutableListOf<Double>().apply {
                        for (i in powerBases.indices) {
                                val stop = -6.0 + i
                                add(2.0.pow(stop))
                                if (i < powerBases.size - 1) {
                                        add(2.0.pow(stop + 1.0 / 3.0))
                                        add(2.0.pow(stop + 2.0 / 3.0))
                                }
                        }
                }

        // Current derived values
        val currentGn = gnValues[gnIndex]
        val currentIso = isoValues[isoIndex]
        val currentAperture = apertureValues[apertureIndex]

        val currentPowerValue = powerValues[powerIndex]
        val currentPowerName =
                if (powerIndex % 3 == 0) {
                        powerBases[powerIndex / 3]
                } else {
                        val baseIndex = powerIndex / 3
                        val offset = powerIndex % 3
                        val goingRight = powerIndex >= previousPowerIndex
                        if (offset == 1) {
                                if (goingRight) "${powerBases[baseIndex]} + 0.3"
                                else "${powerBases[baseIndex + 1]} - 0.7"
                        } else {
                                if (goingRight) "${powerBases[baseIndex]} + 0.7"
                                else "${powerBases[baseIndex + 1]} - 0.3"
                        }
                }

        // Calculate real-time distance
        val distance =
                LuminosityAnalyzer.calculateFlashDistance(
                        baseGN = currentGn.toDouble(),
                        iso = currentIso,
                        aperture = currentAperture,
                        powerFraction = currentPowerValue
                )

        Scaffold(containerColor = DarkBg) { paddingValues ->
                Column(
                        modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                ) {
                        // Header Result View
                        Card(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                                colors =
                                        CardDefaults.cardColors(
                                                containerColor = Amber.copy(alpha = 0.1f)
                                        ),
                                shape = RoundedCornerShape(16.dp)
                        ) {
                                Column(
                                        modifier = Modifier.padding(24.dp).fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                        Text(
                                                "DISTANCE",
                                                fontSize = 14.sp,
                                                color = Amber,
                                                fontWeight = FontWeight.SemiBold,
                                                letterSpacing = 2.sp
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        val displayDistance =
                                                if (distance < 1.0) "%.1f cm".format(distance * 100)
                                                else "%.1f m".format(distance)
                                        Text(
                                                text = displayDistance,
                                                fontSize = 48.sp,
                                                color = Amber,
                                                fontWeight = FontWeight.Bold
                                        )
                                }
                        }

                        // Aperture Slider
                        CustomSliderCard(
                                label = "Aperture",
                                valueText = "f/$currentAperture",
                                value = apertureIndex.toFloat(),
                                onValueChange = { apertureIndex = it.roundToInt() },
                                valueRange = 0f..(apertureValues.size - 1).toFloat(),
                                steps = apertureValues.size - 2
                        )

                        // ISO Slider
                        CustomSliderCard(
                                label = "ISO",
                                valueText = currentIso.toString(),
                                value = isoIndex.toFloat(),
                                onValueChange = { isoIndex = it.roundToInt() },
                                valueRange = 0f..(isoValues.size - 1).toFloat(),
                                steps = isoValues.size - 2
                        )

                        // Power Slider
                        CustomSliderCard(
                                label = "Flash Power",
                                valueText = currentPowerName,
                                value = powerIndex.toFloat(),
                                onValueChange = {
                                        val newIndex = it.roundToInt()
                                        powerIndex = newIndex
                                },
                                valueRange = 0f..(powerValues.size - 1).toFloat(),
                                steps = powerValues.size - 2 // discrete markers
                        )

                        // GN Slider
                        CustomSliderCard(
                                label = "Guide Number (GN)",
                                valueText =
                                        "${currentGn.toInt()} m @ ${gnFocalLengths[gnIndex]} mm",
                                value = gnIndex.toFloat(),
                                onValueChange = { gnIndex = it.roundToInt() },
                                valueRange = 0f..(gnValues.size - 1).toFloat(),
                                steps = gnValues.size - 2
                        )

                        Spacer(modifier = Modifier.height(32.dp))
                }
        }
}

@Composable
fun CustomSliderCard(
        label: String,
        valueText: String,
        value: Float,
        onValueChange: (Float) -> Unit,
        valueRange: ClosedFloatingPointRange<Float>,
        steps: Int = 0
) {
        Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(12.dp)
        ) {
                Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                                Text(text = label, color = Color.LightGray, fontSize = 16.sp)
                                Text(
                                        text = valueText,
                                        color = Color.White,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Slider(
                                value = value,
                                onValueChange = onValueChange,
                                valueRange = valueRange,
                                steps = steps,
                                colors =
                                        SliderDefaults.colors(
                                                thumbColor = Amber,
                                                activeTrackColor = Amber,
                                                inactiveTrackColor = Color.DarkGray
                                        )
                        )
                }
        }
}
