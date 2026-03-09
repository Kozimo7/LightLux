package com.example.lightluxmeter

import com.example.lightluxmeter.domain.LuminosityAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainTests {

    // ── calculateEvCamera ──

    @Test
    fun evCamera_brightSunlight() {
        // f/16, 1/100s → EV ≈ log2(256 / 0.01) = log2(25600) ≈ 14.64
        val ev = LuminosityAnalyzer.calculateEvCamera(16f, 1.0 / 100.0)
        assertEquals(14.64, ev, 0.1)
    }

    @Test
    fun evCamera_indoorDim() {
        // f/2.0, 1/30s → EV = log2(4 / 0.0333) = log2(120) ≈ 6.91
        val ev = LuminosityAnalyzer.calculateEvCamera(2.0f, 1.0 / 30.0)
        assertEquals(6.91, ev, 0.1)
    }

    @Test
    fun evCamera_zeroExposureReturnsZero() {
        val ev = LuminosityAnalyzer.calculateEvCamera(2.0f, 0.0)
        assertEquals(0.0, ev, 0.001)
    }

    @Test
    fun evCamera_negativeExposureReturnsZero() {
        val ev = LuminosityAnalyzer.calculateEvCamera(2.0f, -1.0)
        assertEquals(0.0, ev, 0.001)
    }

    // ── calculateEv100 ──

    @Test
    fun ev100_iso100_noOffset() {
        // At ISO 100, EV100 = EV_cam - log2(1) + 0 = EV_cam
        val ev100 = LuminosityAnalyzer.calculateEv100(14.0, 100, 0.0)
        assertEquals(14.0, ev100, 0.001)
    }

    @Test
    fun ev100_iso400_noOffset() {
        // At ISO 400, EV100 = 14 - log2(4) = 14 - 2 = 12
        val ev100 = LuminosityAnalyzer.calculateEv100(14.0, 400, 0.0)
        assertEquals(12.0, ev100, 0.001)
    }

    @Test
    fun ev100_withCalibrationOffset() {
        // At ISO 100 with +1.5 offset: EV100 = 14 - 0 + 1.5 = 15.5
        val ev100 = LuminosityAnalyzer.calculateEv100(14.0, 100, 1.5)
        assertEquals(15.5, ev100, 0.001)
    }

    // ── calculateFilmShutterSpeed ──

    @Test
    fun filmShutter_ev15_iso100_f16() {
        // t = 16^2 / (2^15 * 1) = 256 / 32768 ≈ 0.0078s (1/128)
        val t = LuminosityAnalyzer.calculateFilmShutterSpeed(15.0, 100, 16.0)
        assertEquals(0.0078, t, 0.001)
    }

    @Test
    fun filmShutter_ev10_iso400_f5_6() {
        // t = 5.6^2 / (2^10 * 4) = 31.36 / 4096 ≈ 0.00766s
        val t = LuminosityAnalyzer.calculateFilmShutterSpeed(10.0, 400, 5.6)
        assertEquals(0.00766, t, 0.001)
    }

    @Test
    fun filmShutter_lowEv_givesLongExposure() {
        // Low EV → slow shutter speed (longer exposure)
        val t = LuminosityAnalyzer.calculateFilmShutterSpeed(3.0, 100, 8.0)
        assertTrue("Expected exposure > 1s at EV 3", t > 1.0)
    }

    // ── computeEv100FromMetadata ──

    @Test
    fun ev100FromMetadata_brightScene() {
        // f/2.4, 1/1000s (1_000_000 ns), ISO 100
        val ev100 =
                LuminosityAnalyzer.computeEv100FromMetadata(
                        cameraAperture = 2.4f,
                        exposureTimeNs = 1_000_000L, // 1ms
                        cameraIso = 100,
                        calibrationOffset = 0.0
                )
        // EV_cam = log2(5.76 / 0.001) = log2(5760) ≈ 12.49
        // EV100 = 12.49 - 0 = 12.49
        assertEquals(12.49, ev100, 0.2)
    }

    @Test
    fun ev100FromMetadata_darkScene() {
        // f/1.8, 1/30s (33_333_333 ns), ISO 800
        val ev100 =
                LuminosityAnalyzer.computeEv100FromMetadata(
                        cameraAperture = 1.8f,
                        exposureTimeNs = 33_333_333L, // ~1/30s
                        cameraIso = 800,
                        calibrationOffset = 0.0
                )
        // EV_cam = log2(3.24 / 0.0333) ≈ log2(97.3) ≈ 6.60
        // EV100 = 6.60 - log2(8) = 6.60 - 3.0 = 3.60
        assertEquals(3.60, ev100, 0.3)
    }

    // ── ev100ToLux ──

    @Test
    fun ev100ToLux_brightSunlight() {
        // EV 15 → lux = 2.5 * 2^15 = 2.5 * 32768 = 81920
        val lux = LuminosityAnalyzer.ev100ToLux(15.0)
        assertEquals(81920.0, lux, 0.1)
    }

    @Test
    fun ev100ToLux_indoors() {
        // EV 7 → lux = 2.5 * 128 = 320
        val lux = LuminosityAnalyzer.ev100ToLux(7.0)
        assertEquals(320.0, lux, 0.1)
    }

    @Test
    fun ev100ToLux_zeroEv() {
        // EV 0 → lux = 2.5 * 1 = 2.5
        val lux = LuminosityAnalyzer.ev100ToLux(0.0)
        assertEquals(2.5, lux, 0.001)
    }

    // ── mapLumaToEv ──

    @Test
    fun mapLuma_maxBrightnessReturns15() {
        // Luma=255 (full white) → normalized=1.0, log2(1)=0, result=15.0
        val ev = LuminosityAnalyzer.mapLumaToEv(255.0)
        assertEquals(15.0, ev, 0.1)
    }

    @Test
    fun mapLuma_midGrayReturnsAbout14() {
        // Luma=128 → normalized≈0.502, log2(0.502)≈-0.995, result≈14.0
        val ev = LuminosityAnalyzer.mapLumaToEv(128.0)
        assertEquals(14.0, ev, 0.1)
    }

    @Test
    fun mapLuma_darkReturnsLowEv() {
        // Luma=10 → very low EV
        val ev = LuminosityAnalyzer.mapLumaToEv(10.0)
        assertTrue("Low luma should give EV well below 15", ev < 11.0)
    }

    // ── formatShutterSpeed ──

    @Test
    fun formatShutter_fast() {
        // 1/500s should format as "1/500"
        val label = LuminosityAnalyzer.formatShutterSpeed(1.0 / 500.0)
        assertEquals("1/500", label)
    }

    @Test
    fun formatShutter_oneSec() {
        // 1.0s should format as "1s"
        val label = LuminosityAnalyzer.formatShutterSpeed(1.0)
        assertEquals("1s", label)
    }

    @Test
    fun formatShutter_slow() {
        // 30s should format as "30s"
        val label = LuminosityAnalyzer.formatShutterSpeed(30.0)
        assertEquals("30s", label)
    }

    @Test
    fun formatShutter_snapsToNearest() {
        // 1/400 is between 1/500 and 1/250 — should snap to 1/500 (closer on log scale)
        val label = LuminosityAnalyzer.formatShutterSpeed(1.0 / 400.0)
        assertEquals("1/500", label)
    }

    @Test
    fun formatShutter_veryFast() {
        // 1/8000 should format correctly
        val label = LuminosityAnalyzer.formatShutterSpeed(1.0 / 8000.0)
        assertEquals("1/8000", label)
    }
}
