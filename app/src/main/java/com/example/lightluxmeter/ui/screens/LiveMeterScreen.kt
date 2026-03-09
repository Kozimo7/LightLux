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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.lightluxmeter.R
import com.example.lightluxmeter.domain.LuminosityAnalyzer
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

@SuppressLint("DefaultLocale")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveMeterScreen() {
        val context = LocalContext.current

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

        val isoOptions = listOf(50, 100, 200, 400, 800, 1600, 3200, 6400)
        val apertureOptions =
                listOf(
                        1.8,
                        2.4,
                        2.8,
                        3.5,
                        4.0,
                        4.8,
                        5.6,
                        6.7,
                        8.0,
                        9.5,
                        11.0,
                        13.0,
                        16.0,
                        19.0,
                        22.0
                )

        var currentEv100 by remember { mutableDoubleStateOf(0.0) }
        var currentLux by remember { mutableDoubleStateOf(0.0) }
        var cameraApertureState by remember { mutableFloatStateOf(0f) }
        var cameraExposureNs by remember { mutableLongStateOf(0L) }
        var cameraIsoState by remember { mutableIntStateOf(0) }
        var calibrationOffset by remember { mutableDoubleStateOf(0.0) }

        var selectedIsoIndex by remember { mutableIntStateOf(3) } // Default 400
        var selectedApertureIndex by remember { mutableIntStateOf(6) } // Default f/5.6

        val selectedIso = isoOptions[selectedIsoIndex]
        val selectedAperture = apertureOptions[selectedApertureIndex]
        val shutterSeconds =
                LuminosityAnalyzer.calculateFilmShutterSpeed(
                        currentEv100,
                        selectedIso,
                        selectedAperture
                )

        var isLocked by remember { mutableStateOf(false) }

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
                                        // Title and Lock Button Row
                                        Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                        ) {
                                                Spacer(
                                                        modifier = Modifier.size(48.dp)
                                                ) // Balance the icon button
                                                Text(
                                                        text = "LightLux",
                                                        color = Amber,
                                                        fontSize = 18.sp,
                                                        fontWeight = FontWeight.Bold
                                                )
                                                IconButton(onClick = { isLocked = !isLocked }) {
                                                        Icon(
                                                                imageVector =
                                                                        if (isLocked)
                                                                                Icons.Filled.Lock
                                                                        else Icons.Filled.LockOpen,
                                                                contentDescription =
                                                                        if (isLocked)
                                                                                "Unlock Exposure"
                                                                        else "Lock Exposure",
                                                                tint =
                                                                        if (isLocked) Amber
                                                                        else TextSecondary
                                                        )
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
                                                                text = "EV₁₀₀",
                                                                color = TextSecondary,
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
                                                                text = "Lux",
                                                                color = TextSecondary,
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
                                                text =
                                                        "Camera: f/${String.format("%.1f", cameraApertureState)} · " +
                                                                "${if (cameraExposureNs > 0) formatExposureTime(cameraExposureNs) else "—"} · " +
                                                                "ISO $cameraIsoState",
                                                color = TextSecondary,
                                                fontSize = 11.sp
                                        )

                                        // Calibration offset row
                                        Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center,
                                                modifier = Modifier.padding(top = 6.dp)
                                        ) {
                                                Text(
                                                        text = "Cal:",
                                                        color = TextSecondary,
                                                        fontSize = 11.sp
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                TextButton(
                                                        onClick = { calibrationOffset -= 0.5 },
                                                        modifier = Modifier.size(28.dp),
                                                        contentPadding = PaddingValues(0.dp)
                                                ) { Text("−", color = Amber, fontSize = 16.sp) }
                                                Text(
                                                        text =
                                                                String.format(
                                                                        "%+.1f",
                                                                        calibrationOffset
                                                                ),
                                                        color = TextPrimary,
                                                        fontSize = 12.sp,
                                                        modifier =
                                                                Modifier.padding(horizontal = 4.dp)
                                                )
                                                TextButton(
                                                        onClick = { calibrationOffset += 0.5 },
                                                        modifier = Modifier.size(28.dp),
                                                        contentPadding = PaddingValues(0.dp)
                                                ) { Text("+", color = Amber, fontSize = 16.sp) }
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
                                        // Headers
                                        Row(modifier = Modifier.fillMaxWidth()) {
                                                Text(
                                                        text =
                                                                stringResource(
                                                                        R.string
                                                                                .exposure_table_aperture
                                                                ),
                                                        color = Amber,
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.weight(1f),
                                                        textAlign = TextAlign.Center
                                                )
                                                Text(
                                                        text =
                                                                stringResource(
                                                                        R.string
                                                                                .exposure_table_shutter_speed
                                                                ),
                                                        color = Amber,
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.weight(1f),
                                                        textAlign = TextAlign.Center
                                                )
                                                Text(
                                                        text = "ISO",
                                                        color = Amber,
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.weight(1f),
                                                        textAlign = TextAlign.Center
                                                )
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        // Three scrollable columns
                                        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                                                // Aperture column
                                                ScrollableSelector(
                                                        items = apertureOptions.map { "f/${it}" },
                                                        selectedIndex = selectedApertureIndex,
                                                        onSelect = { selectedApertureIndex = it },
                                                        modifier = Modifier.weight(1f)
                                                )

                                                // Shutter Speed column
                                                val allSpeeds =
                                                        LuminosityAnalyzer.getStandardSpeedLabels()
                                                val bestSpeedStr =
                                                        LuminosityAnalyzer.formatShutterSpeed(
                                                                shutterSeconds
                                                        )
                                                val bestSpeedIndex = allSpeeds.indexOf(bestSpeedStr)

                                                ScrollableSelector(
                                                        items = allSpeeds,
                                                        selectedIndex = bestSpeedIndex,
                                                        onSelect = { /* Read only from user perspective, auto-scrolls */
                                                        },
                                                        modifier = Modifier.weight(1f)
                                                )

                                                // ISO column
                                                ScrollableSelector(
                                                        items = isoOptions.map { it.toString() },
                                                        selectedIndex = selectedIsoIndex,
                                                        onSelect = { selectedIsoIndex = it },
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
                                                                                        iso,
                                                                                        calibrationOffset
                                                                                )
                                                                val rounded =
                                                                        (ev100 * 10.0)
                                                                                .roundToInt() / 10.0
                                                                if (currentEv100 != rounded) {
                                                                        currentEv100 = rounded
                                                                }
                                                                currentLux =
                                                                        LuminosityAnalyzer
                                                                                .ev100ToLux(ev100)
                                                        }
                                                }
                                        )
                                }
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
        onMetadataUpdate: (aperture: Float, exposureTimeNs: Long, iso: Int) -> Unit
) {
        val context = LocalContext.current
        val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
        val previewView = remember { PreviewView(context) }
        val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }

        var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
        var minZoomRatio by remember { mutableFloatStateOf(1f) }
        var maxZoomRatio by remember { mutableFloatStateOf(1f) }
        var currentZoomRatio by remember { mutableFloatStateOf(1f) }
        var tapPosition by remember { mutableStateOf<Offset?>(null) }

        DisposableEffect(Unit) {
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProviderFuture.addListener(
                        {
                                val cameraProvider = cameraProviderFuture.get()

                                // Build ImageAnalysis with Camera2 interop to get per-frame
                                // metadata
                                val imageAnalysisBuilder = ImageAnalysis.Builder()

                                Camera2Interop.Extender(imageAnalysisBuilder)
                                        .setCaptureRequestOption(
                                                CaptureRequest.CONTROL_AE_MODE,
                                                CaptureRequest.CONTROL_AE_MODE_ON
                                        )

                                val imageAnalysis =
                                        imageAnalysisBuilder.build().also {
                                                it.setAnalyzer(
                                                        cameraExecutor,
                                                        LuminosityAnalyzer { /* luma not needed */}
                                                )
                                        }

                                // Use Preview's Camera2 interop for capture callback to get
                                // metadata
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
                                Modifier.fillMaxSize().pointerInput(Unit) {
                                        detectTapGestures(
                                                onTap = { offset ->
                                                        tapPosition = offset
                                                        val factory =
                                                                previewView.meteringPointFactory
                                                        val point =
                                                                factory.createPoint(
                                                                        offset.x,
                                                                        offset.y
                                                                )
                                                        val action =
                                                                FocusMeteringAction.Builder(
                                                                                point,
                                                                                FocusMeteringAction
                                                                                        .FLAG_AE
                                                                        )
                                                                        .build()
                                                        cameraControl?.startFocusAndMetering(action)
                                                }
                                        )
                                }
                )

                // Circle indicator at tap position
                val currentTapPosition = tapPosition
                if (currentTapPosition != null) {
                        val circleSize = 80.dp
                        val density = androidx.compose.ui.platform.LocalDensity.current
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
                                                                                        .coerceIn(
                                                                                                0f,
                                                                                                1f
                                                                                        )
                                                                        val newZoom =
                                                                                clampedMinZoom +
                                                                                        (clampedMaxZoom -
                                                                                                clampedMinZoom) *
                                                                                                fraction
                                                                        cameraControl?.setZoomRatio(
                                                                                newZoom
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

                                        // Thumb indicator
                                        val thumbFraction =
                                                (currentZoomRatio - clampedMinZoom) /
                                                        (clampedMaxZoom - clampedMinZoom)
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
