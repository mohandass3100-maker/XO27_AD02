package com.acoulink.audio

import kotlin.math.max

/**
 * Result of analyzing incoming audio samples for acoustic preamble and pilot tone detection.
 */
data class DetectionResult(
    val isSignalDetected: Boolean,
    val isSynchronized: Boolean,
    val pilotPower: Double,
    val noiseFloor: Double,
    val snr: Double,
    val signalQualityPercent: Int
)

/**
 * Detects pilot tone preambles and estimates signal quality and ambient noise baseline.
 */
class SignalDetector(
    private val config: AudioConfig = AudioConfig()
) {
    private val demodulator = FSKDemodulator(config)
    private var baselineNoiseFloor = 0.0005
    private var consecutivePilotHits = 0
    private val requiredPilotHits = 3 // Minimum consecutive windows of pilot tone to confirm sync

    /**
     * Processes an audio chunk to evaluate pilot tone presence and signal quality.
     */
    fun processChunk(samples: ShortArray, offset: Int, length: Int): DetectionResult {
        val pilotPower = demodulator.computeGoertzelPower(samples, offset, length, config.pilotFreq)
        val spacePower = demodulator.computeGoertzelPower(samples, offset, length, config.spaceFreq)
        val markPower = demodulator.computeGoertzelPower(samples, offset, length, config.markFreq)

        val totalAmbient = (spacePower + markPower) / 2.0
        
        // Update exponential moving average of noise floor if no pilot is present
        if (pilotPower < baselineNoiseFloor * config.detectionThresholdRatio) {
            baselineNoiseFloor = (0.95 * baselineNoiseFloor) + (0.05 * max(totalAmbient, 0.0001))
        }

        val snr = pilotPower / max(baselineNoiseFloor, 1e-9)
        val isDetected = snr >= config.detectionThresholdRatio && pilotPower > 0.0005

        if (isDetected) {
            consecutivePilotHits++
        } else {
            consecutivePilotHits = max(0, consecutivePilotHits - 1)
        }

        val isSynchronized = consecutivePilotHits >= requiredPilotHits

        // Map SNR to a 0..100% quality metric for UI display
        val quality = ((snr - 1.0) / 10.0 * 100.0).toInt().coerceIn(0, 100)

        return DetectionResult(
            isSignalDetected = isDetected,
            isSynchronized = isSynchronized,
            pilotPower = pilotPower,
            noiseFloor = baselineNoiseFloor,
            snr = snr,
            signalQualityPercent = quality
        )
    }

    fun reset() {
        consecutivePilotHits = 0
    }
}
