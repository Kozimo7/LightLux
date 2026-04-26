package com.example.lightluxmeter.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.CenterFocusWeak
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.lightluxmeter.R
import com.example.lightluxmeter.domain.LuminosityAnalyzer
import com.example.lightluxmeter.domain.MeteringMode
import com.example.lightluxmeter.ui.viewmodels.ExposureViewModel
import com.example.lightluxmeter.ui.viewmodels.SettingsViewModel
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.roundToInt

// Design tokens
private val DarkBg = Color(0xFF1A1A1A)
private val CardBg = Color(0xFF2A2A2A)
private val Amber = Color(0xFFFFB74D)
private val TextPrimary = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFFAAAAAA)
private val SelectedBg = Color(0xFF3A3A3A)

enum class SolveMode { SHUTTER, APERTURE, ISO }

@SuppressLint("DefaultLocale")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LiveMeterScreen(
    settingsViewModel: SettingsViewModel = viewModel(),
    exposureViewModel: ExposureViewModel = viewModel()
) {
        val context = LocalContext.current
        val shutterSteps by settingsViewModel.shutterSpeedSteps.collectAsState()
        val apertureSteps by settingsViewModel.apertureSteps.collectAsState()

        var hasCameraPermission by remember {
                mutableStateOf(
                        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                                PackageManager.PERMISSION_GRANTED
                )
        }

        val launcher =
                rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestPermission(),
                        onResult = { granted -> hasCameraPermission = granted }
                )

        LaunchedEffect(Unit) {
                if (!hasCameraPermission) {
                        launcher.launch(Manifest.permission.CAMERA)
                }
        }


        val currentEv100 by exposureViewModel.currentEv100.collectAsState()
        val currentLux by exposureViewModel.currentLux.collectAsState()

        var cameraApertureState by remember { mutableFloatStateOf(0f) }
        var cameraExposureNs by remember { mutableLongStateOf(0L) }
        var cameraIsoState by remember { mutableIntStateOf(0) }

        var selectedIsoIndex by remember { mutableIntStateOf(3) } // Default 400
        var selectedApertureIndex by remember { mutableIntStateOf(6) } // Default f/5.6
        var selectedShutterIndex by remember { mutableIntStateOf(-1) } // Used when shutter is user-set
        var solveMode by remember { mutableStateOf(SolveMode.SHUTTER) }

        val apertureOptions = LiveMeterConstants.getApertureOptions(apertureSteps)
        // Clamp index when list size changes due to step setting change
        val clampedApertureIndex = selectedApertureIndex.coerceIn(0, apertureOptions.lastIndex)
        if (clampedApertureIndex != selectedApertureIndex) selectedApertureIndex = clampedApertureIndex

        val allSpeeds = LuminosityAnalyzer.fetchStandardSpeedLabels(shutterSteps)
        val allSpeedValues = LuminosityAnalyzer.fetchStandardSpeedValues(shutterSteps)

        // Compute exposure values based on solve mode
        val selectedIso: Int
        val selectedAperture: Double
        val shutterSeconds: Double
        val displayShutterIndex: Int
        val displayApertureIndex: Int
        val displayIsoIndex: Int

        when (solveMode) {
            SolveMode.SHUTTER -> {
                selectedIso = LiveMeterConstants.ISO_OPTIONS[selectedIsoIndex]
                selectedAperture = apertureOptions[selectedApertureIndex]
                shutterSeconds = LuminosityAnalyzer.calculateFilmShutterSpeed(
                    currentEv100, selectedIso, selectedAperture
                )
                val bestSpeedStr = LuminosityAnalyzer.formatShutterSpeed(shutterSeconds, shutterSteps)
                displayShutterIndex = allSpeeds.indexOf(bestSpeedStr)
                displayApertureIndex = selectedApertureIndex
                displayIsoIndex = selectedIsoIndex
            }
            SolveMode.APERTURE -> {
                selectedIso = LiveMeterConstants.ISO_OPTIONS[selectedIsoIndex]
                // Get shutter speed from user selection
                val clampedShutterIdx = selectedShutterIndex.coerceIn(0, allSpeedValues.lastIndex)
                if (clampedShutterIdx != selectedShutterIndex) selectedShutterIndex = clampedShutterIdx
                shutterSeconds = allSpeedValues[clampedShutterIdx]
                val computedAperture = LuminosityAnalyzer.calculateFilmAperture(
                    currentEv100, selectedIso, shutterSeconds
                )
                val snappedIdx = LuminosityAnalyzer.snapToNearestAperture(computedAperture, apertureOptions)
                selectedAperture = apertureOptions[snappedIdx]
                displayShutterIndex = clampedShutterIdx
                displayApertureIndex = snappedIdx
                displayIsoIndex = selectedIsoIndex
            }
            SolveMode.ISO -> {
                selectedAperture = apertureOptions[selectedApertureIndex]
                // Get shutter speed from user selection
                val clampedShutterIdx = selectedShutterIndex.coerceIn(0, allSpeedValues.lastIndex)
                if (clampedShutterIdx != selectedShutterIndex) selectedShutterIndex = clampedShutterIdx
                shutterSeconds = allSpeedValues[clampedShutterIdx]
                val computedIso = LuminosityAnalyzer.calculateFilmIso(
                    currentEv100, selectedAperture, shutterSeconds
                )
                val snappedIsoIdx = LuminosityAnalyzer.snapToNearestIso(computedIso, LiveMeterConstants.ISO_OPTIONS)
                selectedIso = LiveMeterConstants.ISO_OPTIONS[snappedIsoIdx]
                displayShutterIndex = clampedShutterIdx
                displayApertureIndex = selectedApertureIndex
                displayIsoIndex = snappedIsoIdx
            }
        }

        var isLocked by remember { mutableStateOf(false) }
        var currentMeteringMode by remember { mutableStateOf(MeteringMode.SPOT) }

        var showSaveDialog by remember { mutableStateOf(false) }
        var saveNote by remember { mutableStateOf("") }

        if (hasCameraPermission) {
                Column(
                        modifier = Modifier.fillMaxSize().background(DarkBg).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                        // ── Section 1: EV & Lux Info Card ──
                        Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = CardBg)
                        ) {
                                Column(
                                        modifier = Modifier.padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                        // Title and Lock/Metering Button Row
                                        Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                        ) {
                                         IconButton(onClick = { showSaveDialog = true }) {
                                                Icon(
                                                        imageVector = Icons.Filled.Save,
                                                        contentDescription = stringResource(R.string.save_reading),
                                                        tint = TextSecondary
                                                )
                                        }
                                                Text(
                                                        text = "LightLux",
                                                        color = Amber,
                                                        fontSize = 18.sp,
                                                        fontWeight = FontWeight.Bold
                                                )
                                                // Lock button
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                        IconButton(onClick = { isLocked = !isLocked }, modifier = Modifier.size(36.dp)) {
                                                                Icon(
                                                                        imageVector =
                                                                                if (isLocked)
                                                                                        Icons.Filled.Lock
                                                                                else Icons.Filled.LockOpen,
                                                                        contentDescription =
                                                                        if (isLocked)
                                                                                stringResource(R.string.meter_unlock_content_desc)
                                                                        else stringResource(R.string.meter_lock_content_desc),
                                                                        tint =
                                                                                if (isLocked) Amber
                                                                                else TextSecondary
                                                                )
                                                        }
                                                }
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))

                                        // EV and Lux in a row
                                        Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceEvenly
                                        ) {
                                                // EV100 display
                                                Column(
                                                        horizontalAlignment =
                                                                Alignment.CenterHorizontally
                                                ) {
                                                        Text(
                                                                text = stringResource(R.string.meter_ev_label),
                                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                                                fontSize = 13.sp
                                                        )
                                                        Text(
                                                                text =
                                                                        String.format(
                                                                                "%.1f",
                                                                                currentEv100
                                                                        ),
                                                                color = TextPrimary,
                                                                fontSize = 36.sp,
                                                                fontWeight = FontWeight.Bold
                                                        )
                                                }

                                                // Lux display
                                                Column(
                                                        horizontalAlignment =
                                                                Alignment.CenterHorizontally
                                                ) {
                                                        Text(
                                                                text = stringResource(R.string.meter_lux_label),
                                                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f),
                                                                fontSize = 13.sp
                                                        )
                                                        Text(
                                                                text =
                                                                        String.format(
                                                                                "%.0f",
                                                                                currentLux
                                                                        ),
                                                                color = TextPrimary,
                                                                fontSize = 36.sp,
                                                                fontWeight = FontWeight.Bold
                                                        )
                                                }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        // Camera metadata info
                                        Text(
                                                text = stringResource(
                                                        R.string.camera_metadata_format,
                                                        cameraApertureState,
                                                        if (cameraExposureNs > 0) formatExposureTime(cameraExposureNs) else "—",
                                                        cameraIsoState
                                                ),
                                                color = TextSecondary,
                                                fontSize = 11.sp
                                        )

                                        // Metering mode row
                                        Box(
                                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                                        ) {
                                                Column(
                                                        modifier = Modifier.align(Alignment.CenterEnd),
                                                        horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                        IconButton(
                                                                onClick = {
                                                                        currentMeteringMode = if (currentMeteringMode == MeteringMode.SPOT)
                                                                                MeteringMode.CENTER_WEIGHTED else MeteringMode.SPOT
                                                                },
                                                                modifier = Modifier.size(36.dp)
                                                        ) {
                                                                Icon(
                                                                        imageVector = if (currentMeteringMode == MeteringMode.SPOT)
                                                                                Icons.Filled.CenterFocusStrong
                                                                        else Icons.Filled.CenterFocusWeak,
                                                                        contentDescription = if (currentMeteringMode == MeteringMode.SPOT)
                                                                                stringResource(R.string.metering_spot_desc) else stringResource(R.string.metering_cw_desc),
                                                                        tint = if (currentMeteringMode == MeteringMode.CENTER_WEIGHTED) Amber
                                                                                else TextSecondary
                                                                )
                                                        }
                                                        Text(
                                                                text = if (currentMeteringMode == MeteringMode.SPOT) stringResource(R.string.metering_spot_label) else stringResource(R.string.metering_cw_label),
                                                                color = if (currentMeteringMode == MeteringMode.CENTER_WEIGHTED) Amber else TextSecondary,
                                                                fontSize = 9.sp
                                                        )
                                                }
                                        }
                                }
                        }

                        // ── Section 2: Exposure Controls Card ──
                        Card(
                                modifier = Modifier.fillMaxWidth().height(185.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = CardBg)
                        ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                        // Headers — long-press to set solve mode
                                        Row(modifier = Modifier.fillMaxWidth()) {
                                                // Aperture header
                                                Text(
                                                        text = stringResource(R.string.exposure_table_aperture),
                                                        color = if (solveMode == SolveMode.APERTURE) Amber else TextSecondary,
                                                        fontSize = 14.sp,
                                                        fontWeight = if (solveMode == SolveMode.APERTURE) FontWeight.Bold else FontWeight.Normal,
                                                        modifier = Modifier
                                                                .weight(1f)
                                                                .combinedClickable(
                                                                        onClick = { },
                                                                        onLongClick = { solveMode = SolveMode.APERTURE }
                                                                ),
                                                        textAlign = TextAlign.Center
                                                )
                                                // Shutter header
                                                Text(
                                                        text = stringResource(R.string.exposure_table_shutter_speed),
                                                        color = if (solveMode == SolveMode.SHUTTER) Amber else TextSecondary,
                                                        fontSize = 14.sp,
                                                        fontWeight = if (solveMode == SolveMode.SHUTTER) FontWeight.Bold else FontWeight.Normal,
                                                        modifier = Modifier
                                                                .weight(1f)
                                                                .combinedClickable(
                                                                        onClick = { },
                                                                        onLongClick = { solveMode = SolveMode.SHUTTER }
                                                                ),
                                                        textAlign = TextAlign.Center
                                                )
                                                // ISO header
                                                Text(
                                                        text = stringResource(R.string.flash_calc_iso),
                                                        color = if (solveMode == SolveMode.ISO) Amber else TextSecondary,
                                                        fontSize = 14.sp,
                                                        fontWeight = if (solveMode == SolveMode.ISO) FontWeight.Bold else FontWeight.Normal,
                                                        modifier = Modifier
                                                                .weight(1f)
                                                                .combinedClickable(
                                                                        onClick = { },
                                                                        onLongClick = { solveMode = SolveMode.ISO }
                                                                ),
                                                        textAlign = TextAlign.Center
                                                )
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        // Three scrollable columns
                                        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                                                // Aperture column
                                                ScrollableSelector(
                                                        items = apertureOptions.map { "f/${it}" },
                                                        selectedIndex = displayApertureIndex,
                                                        onSelect = { if (solveMode != SolveMode.APERTURE) selectedApertureIndex = it },
                                                        modifier = Modifier.weight(1f)
                                                )

                                                // Shutter Speed column
                                                ScrollableSelector(
                                                        items = allSpeeds,
                                                        selectedIndex = displayShutterIndex,
                                                        onSelect = { if (solveMode != SolveMode.SHUTTER) selectedShutterIndex = it },
                                                        modifier = Modifier.weight(1f)
                                                )

                                                // ISO column
                                                ScrollableSelector(
                                                        items = LiveMeterConstants.ISO_OPTIONS.map { it.toString() },
                                                        selectedIndex = displayIsoIndex,
                                                        onSelect = { if (solveMode != SolveMode.ISO) selectedIsoIndex = it },
                                                        modifier = Modifier.weight(1f)
                                                )
                                        }
                                }
                        }

                        // ── Section 3: Camera Preview Card ──
                        Card(
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = CardBg)
                        ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                        CameraPreviewWithMetadata(
                                                meteringMode = currentMeteringMode,
                                                onMetadataUpdate = { aperture, exposureNs, iso ->
                                                        if (!isLocked) {
                                                                cameraApertureState = aperture
                                                                cameraExposureNs = exposureNs
                                                                cameraIsoState = iso

                                                                // Calculate EV100 from Camera2
                                                                // metadata
                                                                val ev100 =
                                                                        LuminosityAnalyzer
                                                                                .computeEv100FromMetadata(
                                                                                        aperture,
                                                                                        exposureNs,
                                                                                        iso
                                                                                )
                                                                
                                                                exposureViewModel.updateLiveMetadata(ev100)
                                                        }
                                                }
                                        )
                                }
                        }

                        if (showSaveDialog) {
                                AlertDialog(
                                        onDismissRequest = { showSaveDialog = false },
                                        title = { Text(stringResource(R.string.save_reading)) },
                                        text = {
                                                Column {
                                                        Text(stringResource(R.string.add_note))
                                                        Spacer(modifier = Modifier.height(8.dp))
                                                        TextField(
                                                                value = saveNote,
                                                                onValueChange = { saveNote = it },
                                                                modifier = Modifier.fillMaxWidth(),
                                                                placeholder = { Text("...") }
                                                        )
                                                }
                                        },
                                        confirmButton = {
                                                TextButton(
                                                        onClick = {
                                                                val saveAperture = apertureOptions[selectedApertureIndex]
                                                                val formattedSpeed = LuminosityAnalyzer.formatShutterSpeed(shutterSeconds, shutterSteps)
                                                                val saveIso = LiveMeterConstants.ISO_OPTIONS[selectedIsoIndex]
                                                                exposureViewModel.saveReading(
                                                                        currentEv100.toFloat(),
                                                                        currentLux.toFloat(),
                                                                        saveAperture,
                                                                        formattedSpeed,
                                                                        saveIso,
                                                                        saveNote
                                                                )
                                                                showSaveDialog = false
                                                                saveNote = ""
                                                        }
                                                ) { Text(stringResource(R.string.save)) }
                                        },
                                        dismissButton = {
                                                TextButton(onClick = { 
                                                    showSaveDialog = false 
                                                    saveNote = ""
                                                }) { Text(stringResource(R.string.cancel)) }
                                        }
                                )
                        }
                }
        } else {
                Box(
                        modifier = Modifier.fillMaxSize().background(DarkBg),
                        contentAlignment = Alignment.Center
                ) { Text(stringResource(R.string.camera_permission_required), color = TextPrimary) }
        }
}

/** Format camera exposure time (nanoseconds) to readable string */
@SuppressLint("DefaultLocale")
private fun formatExposureTime(ns: Long): String {
        val sec = ns / 1_000_000_000.0
        return if (sec >= 1.0) {
                String.format("%.1fs", sec)
        } else {
                val denom = (1.0 / sec).roundToInt()
                "1/${denom}"
        }
}

// ─────────────────────────────────────────────────────────
// Camera Preview with Camera2 Interop for metadata
// ─────────────────────────────────────────────────────────
@SuppressLint("UnusedBoxWithConstraintsScope")
@androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
@Composable
fun CameraPreviewWithMetadata(
        meteringMode: MeteringMode = MeteringMode.SPOT,
        onMetadataUpdate: (aperture: Float, exposureTimeNs: Long, iso: Int) -> Unit
) {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        val previewView = remember { PreviewView(context) }
        val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }

        var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
        var minZoomRatio by remember { mutableFloatStateOf(1f) }
        var maxZoomRatio by remember { mutableFloatStateOf(1f) }
        var currentZoomRatio by remember { mutableFloatStateOf(1f) }
        var tapPosition by remember { mutableStateOf<Offset?>(null) }

        val analyzer = remember { LuminosityAnalyzer { } }

        // Update analyzer metering mode when it changes
        LaunchedEffect(meteringMode) {
                analyzer.meteringMode = meteringMode
        }

        DisposableEffect(Unit) {
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProviderFuture.addListener(
                        {
                                val cameraProvider = cameraProviderFuture.get()
                                val imageAnalysisBuilder = ImageAnalysis.Builder()

                                Camera2Interop.Extender(imageAnalysisBuilder)
                                        .setCaptureRequestOption(
                                                CaptureRequest.CONTROL_AE_MODE,
                                                CaptureRequest.CONTROL_AE_MODE_ON
                                        )

                                val imageAnalysis =
                                        imageAnalysisBuilder.build().also {
                                                it.setAnalyzer(cameraExecutor, analyzer)
                                        }

                                val previewBuilder = Preview.Builder()
                                
                                Camera2Interop.Extender(previewBuilder)
                                        .setSessionCaptureCallback(
                                                object : CameraCaptureSession.CaptureCallback() {
                                                        override fun onCaptureCompleted(
                                                                session: CameraCaptureSession,
                                                                request: CaptureRequest,
                                                                result: TotalCaptureResult
                                                        ) {
                                                                val aperture =
                                                                        result.get(
                                                                                CaptureResult
                                                                                        .LENS_APERTURE
                                                                        )
                                                                                ?: return
                                                                val exposureTime =
                                                                        result.get(
                                                                                CaptureResult
                                                                                        .SENSOR_EXPOSURE_TIME
                                                                        )
                                                                                ?: return
                                                                val iso =
                                                                        result.get(
                                                                                CaptureResult
                                                                                        .SENSOR_SENSITIVITY
                                                                        )
                                                                                ?: return

                                                                onMetadataUpdate(
                                                                        aperture,
                                                                        exposureTime,
                                                                        iso
                                                                )
                                                        }
                                                }
                                        )

                                val previewWithMetadata =
                                        previewBuilder.build().also {
                                                it.surfaceProvider = previewView.surfaceProvider
                                        }

                                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                                try {
                                        cameraProvider.unbindAll()
                                        val camera =
                                                cameraProvider.bindToLifecycle(
                                                        lifecycleOwner,
                                                        cameraSelector,
                                                        previewWithMetadata,
                                                        imageAnalysis
                                                )
                                        cameraControl = camera.cameraControl
                                        camera.cameraInfo.zoomState.observe(lifecycleOwner) {
                                                zoomState ->
                                                minZoomRatio = zoomState.minZoomRatio
                                                maxZoomRatio = zoomState.maxZoomRatio
                                                currentZoomRatio = zoomState.zoomRatio
                                        }
                                } catch (e: Exception) {
                                        Log.e("CameraPreview", "Use case binding failed", e)
                                }
                        },
                        ContextCompat.getMainExecutor(context)
                )

                onDispose { cameraExecutor.shutdown() }
        }

        Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                        factory = { previewView },
                        modifier =
                                Modifier.fillMaxSize()
                                        .pointerInput(meteringMode) {
                                                if (meteringMode == MeteringMode.SPOT) {
                                                        detectTapGestures(
                                                                onTap = { offset ->
                                                                        tapPosition = offset
                                                                        analyzer.spotPosition = (offset.x / size.width).toDouble() to (offset.y / size.height).toDouble()
                                                                        val factory = previewView.meteringPointFactory
                                                                        val point = factory.createPoint(offset.x, offset.y)
                                                                        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AE).build()
                                                                        cameraControl?.startFocusAndMetering(action)
                                                                }
                                                        )
                                                }
                                        }
                                        .pointerInput(meteringMode) {
                                                if (meteringMode == MeteringMode.SPOT) {
                                                        detectDragGestures(
                                                                onDragStart = { offset ->
                                                                        tapPosition = offset
                                                                        analyzer.spotPosition = (offset.x / size.width).toDouble() to (offset.y / size.height).toDouble()
                                                                        val factory = previewView.meteringPointFactory
                                                                        val point = factory.createPoint(offset.x, offset.y)
                                                                        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AE).build()
                                                                        cameraControl?.startFocusAndMetering(action)
                                                                },
                                                                onDrag = { change, dragAmount ->
                                                                        change.consume()
                                                                        val newPos = (tapPosition ?: change.position) + dragAmount
                                                                        tapPosition = newPos
                                                                        analyzer.spotPosition = (newPos.x / size.width).toDouble() to (newPos.y / size.height).toDouble()
                                                                        val factory = previewView.meteringPointFactory
                                                                        val point = factory.createPoint(newPos.x, newPos.y)
                                                                        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AE).build()
                                                                        cameraControl?.setZoomRatio(currentZoomRatio)
                                                                        cameraControl?.startFocusAndMetering(action)
                                                                }
                                                        )
                                                }
                                        }
                )

                // Spot metering: circle indicator at tap position
                if (meteringMode == MeteringMode.SPOT) {
                        val currentTapPosition = tapPosition
                        if (currentTapPosition != null) {
                                val circleSize = 50.dp
                                val density = LocalDensity.current
                                val offsetX = with(density) { currentTapPosition.x.toDp() - circleSize / 2 }
                                val offsetY = with(density) { currentTapPosition.y.toDp() - circleSize / 2 }
                                Box(
                                        modifier =
                                                Modifier.offset(x = offsetX, y = offsetY)
                                                        .size(circleSize)
                                                        .border(
                                                                width = 1.5.dp,
                                                                color = Color.White,
                                                                shape = RoundedCornerShape(50)
                                                        )
                                )
                        }
                }

                // Center-weighted metering: show 60% zone circle in center
                if (meteringMode == MeteringMode.CENTER_WEIGHTED) {
                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                                val cwSize = with(LocalDensity.current) {
                                        (kotlin.math.min(constraints.maxWidth, constraints.maxHeight) * 0.30f).toDp()
                                }
                                Box(
                                        modifier = Modifier
                                                .align(Alignment.Center)
                                                .size(cwSize)
                                                .border(
                                                        width = 1.dp,
                                                        color = Amber.copy(alpha = 0.5f),
                                                        shape = RoundedCornerShape(50)
                                                )
                                )
                        }
                }

                // Custom Zoom bar on the far right
                if (maxZoomRatio > minZoomRatio) {
                        val baseFocalLength = 26f
                        val clampedMinZoom = 1f.coerceAtLeast(minZoomRatio)
                        val clampedMaxZoom = 15f.coerceAtMost(maxZoomRatio)
                        val currentMm = (baseFocalLength * currentZoomRatio).toInt()

                        Column(
                                modifier =
                                        Modifier.align(Alignment.CenterEnd)
                                                .padding(top = 40.dp, bottom = 40.dp, end = 12.dp)
                                                .fillMaxHeight(0.6f)
                                                .width(48.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Bottom
                        ) {
                                BoxWithConstraints(
                                        modifier =
                                                Modifier.weight(1f).fillMaxWidth().pointerInput(
                                                                clampedMinZoom,
                                                                clampedMaxZoom
                                                        ) {
                                                        detectVerticalDragGestures { change, _ ->
                                                                change.consume()
                                                                val dragAmount = change.position.y
                                                                val height = size.height
                                                                if (height > 0) {
                                                                        val fraction =
                                                                                (1f -
                                                                                                (dragAmount /
                                                                                                        height))
                                                                                        .coerceIn(0f, 1f)

                                                                        val midZ = 150f / 26f
                                                                        val maxZ = 15.0f
                                                                        
                                                                        val newZoom = if (fraction <= 0.75f) {
                                                                            1.0f + (midZ - 1.0f) * (fraction / 0.75f)
                                                                        } else {
                                                                            midZ + (maxZ - midZ) * ((fraction - 0.75f) / 0.25f)
                                                                        }
                                                                        
                                                                        cameraControl?.setZoomRatio(
                                                                                newZoom.coerceIn(clampedMinZoom, clampedMaxZoom)
                                                                        )
                                                                        currentZoomRatio = newZoom
                                                                }
                                                        }
                                                },
                                        contentAlignment = Alignment.Center
                                ) {
                                        val trackHeight = maxHeight

                                        // Track line
                                        Box(
                                                modifier =
                                                        Modifier.fillMaxHeight()
                                                                .width(2.dp)
                                                                .background(
                                                                        Color.White.copy(
                                                                                alpha = 0.2f
                                                                        ),
                                                                        RoundedCornerShape(1.dp)
                                                                )
                                        )

                                        val midZ = 150f / 26f
                                        val maxZ = 15.0f
                                        val thumbFraction = if (currentZoomRatio <= midZ) {
                                            ((currentZoomRatio - 1.0f) / (midZ - 1.0f)) * 0.75f
                                        } else {
                                            0.75f + ((currentZoomRatio - midZ) / (maxZ - midZ)) * 0.25f
                                        }

                                        Box(
                                                modifier =
                                                        Modifier.align(Alignment.BottomCenter)
                                                                .offset(
                                                                        y =
                                                                                -(thumbFraction *
                                                                                                trackHeight
                                                                                                        .value)
                                                                                        .dp
                                                                )
                                                                .size(24.dp, 6.dp)
                                                                .background(
                                                                        Amber,
                                                                        RoundedCornerShape(3.dp)
                                                                )
                                        )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                        text = "${currentMm}mm",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        softWrap = false
                                )
                        }
                }
        }
}

// ─────────────────────────────────────────────────────────
// Scrollable Selector Column
// ─────────────────────────────────────────────────────────
@Composable
fun ScrollableSelector(
        items: List<String>,
        selectedIndex: Int,
        onSelect: (Int) -> Unit,
        modifier: Modifier = Modifier
) {
        val listState = rememberLazyListState()

        LaunchedEffect(selectedIndex) {
                listState.animateScrollToItem(index = (selectedIndex - 1).coerceAtLeast(0))
        }

        LazyColumn(
                modifier = modifier.fillMaxHeight().padding(horizontal = 4.dp),
                state = listState,
                horizontalAlignment = Alignment.CenterHorizontally
        ) {
                itemsIndexed(items) { index, item ->
                        val isSelected = index == selectedIndex
                        Box(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .clickable { onSelect(index) }
                                                .background(
                                                        if (isSelected) SelectedBg
                                                        else Color.Transparent,
                                                        RoundedCornerShape(8.dp)
                                                )
                                                .padding(vertical = 10.dp, horizontal = 8.dp),
                                contentAlignment = Alignment.Center
                        ) {
                                Text(
                                        text = item,
                                        color = if (isSelected) TextPrimary else TextSecondary,
                                        fontSize = if (isSelected) 20.sp else 14.sp,
                                        fontWeight =
                                                if (isSelected) FontWeight.Bold
                                                else FontWeight.Normal,
                                        textAlign = TextAlign.Center
                                )
                        }
                }
        }
}

object LiveMeterConstants {
    val ISO_OPTIONS = listOf(50, 100, 200, 400, 800, 1600, 3200, 6400)

    val APERTURE_FULL = listOf(1.4, 2.0, 2.8, 4.0, 5.6, 8.0, 11.0, 16.0, 22.0)
    val APERTURE_HALF = listOf(1.4, 1.7, 2.0, 2.4, 2.8, 3.3, 4.0, 4.8, 5.6, 6.7, 8.0, 9.5, 11.0, 13.0, 16.0, 19.0, 22.0)
    val APERTURE_THIRD = listOf(1.4, 1.6, 1.8, 2.0, 2.2, 2.5, 2.8, 3.2, 3.5, 4.0, 4.5, 5.0, 5.6, 6.3, 7.1, 8.0, 9.0, 10.0, 11.0, 13.0, 14.0, 16.0, 18.0, 20.0, 22.0)

    fun getApertureOptions(stepsConfig: String): List<Double> = when (stepsConfig) {
        "full" -> APERTURE_FULL
        "half" -> APERTURE_HALF
        else -> APERTURE_THIRD
    }
}
