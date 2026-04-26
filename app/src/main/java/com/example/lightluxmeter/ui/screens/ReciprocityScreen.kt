package com.example.lightluxmeter.ui.screens

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.lightluxmeter.R
import com.example.lightluxmeter.domain.ReciprocityData
import com.example.lightluxmeter.ui.viewmodels.SettingsViewModel

// Design tokens (match LiveMeterScreen)
private val DarkBg = Color(0xFF1A1A1A)
private val CardBg = Color(0xFF2A2A2A)
private val Amber = Color(0xFFFFB74D)
private val TextPrimary = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFFAAAAAA)
private val SelectedBg = Color(0xFF3A3A3A)

@SuppressLint("DefaultLocale")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReciprocityScreen(settingsViewModel: SettingsViewModel = viewModel()) {

    // Film stock selection
    var selectedFilmIndex by remember { mutableIntStateOf(0) }
    var dropdownExpanded by remember { mutableStateOf(false) }
    val selectedFilm = ReciprocityData.filmStocks[selectedFilmIndex]

    // Shutter speed selection
    val allSpeedValues = remember { ReciprocityData.getLongExposureValues() }
    val allSpeeds = remember { allSpeedValues.map { ReciprocityData.formatTime(it) } }
    var selectedSpeedIndex by remember { mutableIntStateOf(allSpeeds.size / 2) }
    val clampedSpeedIndex = selectedSpeedIndex.coerceIn(0, allSpeedValues.lastIndex)
    if (clampedSpeedIndex != selectedSpeedIndex) selectedSpeedIndex = clampedSpeedIndex

    val meteredTime = allSpeedValues[selectedSpeedIndex]
    val correctedTime = ReciprocityData.calculateCorrectedTime(meteredTime, selectedFilm.pFactor)

    Scaffold(containerColor = DarkBg) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Result Card ──
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Amber.copy(alpha = 0.1f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.reciprocity_corrected_label),
                        fontSize = 12.sp,
                        color = Amber,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 2.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = ReciprocityData.formatTime(correctedTime),
                        fontSize = 42.sp,
                        color = Amber,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Show metered vs corrected
                    if (meteredTime > 1.0) {
                        Text(
                            text = stringResource(
                                R.string.reciprocity_metered_format,
                                ReciprocityData.formatTime(meteredTime)
                            ),
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                        Text(
                            text = stringResource(
                                R.string.reciprocity_factor_format,
                                String.format("%.2f", selectedFilm.pFactor)
                            ),
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }
            }

            // ── Film Stock Dropdown ──
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.reciprocity_film_stock),
                        color = Amber,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    ExposedDropdownMenuBox(
                        expanded = dropdownExpanded,
                        onExpandedChange = { dropdownExpanded = !dropdownExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedFilm.name,
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedBorderColor = Amber,
                                unfocusedBorderColor = TextSecondary
                            )
                        )

                        ExposedDropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false }
                        ) {
                            ReciprocityData.filmStocks.forEachIndexed { index, film ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "${film.name}  (p=${String.format("%.2f", film.pFactor)})",
                                            color = if (index == selectedFilmIndex) Amber else Color.Unspecified
                                        )
                                    },
                                    onClick = {
                                        selectedFilmIndex = index
                                        dropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // ── Shutter Speed Selector ──
            Card(
                modifier = Modifier.fillMaxWidth().weight(1f),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = stringResource(R.string.reciprocity_metered_speed),
                        color = Amber,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    ReciprocitySpeedSelector(
                        items = allSpeeds,
                        selectedIndex = selectedSpeedIndex,
                        onSelect = { selectedSpeedIndex = it },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
fun ReciprocitySpeedSelector(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(selectedIndex) {
        listState.animateScrollToItem(index = (selectedIndex - 2).coerceAtLeast(0))
    }

    LazyColumn(
        modifier = modifier.padding(horizontal = 4.dp),
        state = listState,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        itemsIndexed(items) { index, item ->
            val isSelected = index == selectedIndex
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(index) }
                    .background(
                        if (isSelected) SelectedBg else Color.Transparent,
                        RoundedCornerShape(8.dp)
                    )
                    .padding(vertical = 10.dp, horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = item,
                    color = if (isSelected) TextPrimary else TextSecondary,
                    fontSize = if (isSelected) 22.sp else 14.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
