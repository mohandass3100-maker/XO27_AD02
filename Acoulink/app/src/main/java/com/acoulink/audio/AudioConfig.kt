package com.acoulink.audio

/**
 * Acoustic modulation and frequency profiles for AcouLink.
 */
enum class FrequencyProfile(
    val displayName: String,
    val markFreq: Double,    // Bit 1 (Hz)
    val spaceFreq: Double,   // Bit 0 (Hz)
    val pilotFreq: Double,   // Synchronization tone (Hz)
    val defaultSymbolDurationMs: Int
) {
    STANDARD_AUDIBLE(
        displayName = "Standard Audible (1.75 - 2.15 kHz)",
        markFreq = 2150.0,
        spaceFreq = 1750.0,
        pilotFreq = 1400.0,
        defaultSymbolDurationMs = 35
    ),
    HIGH_AUDIBLE(
        displayName = "High Audible (3.2 - 3.8 kHz)",
        markFreq = 3800.0,
        spaceFreq = 3200.0,
        pilotFreq = 2600.0,
        defaultSymbolDurationMs = 30
    ),
    NEAR_ULTRASONIC(
        displayName = "Near Ultrasonic (17.5 - 18.5 kHz)",
        markFreq = 18500.0,
        spaceFreq = 17500.0,
        pilotFreq = 16500.0,
        defaultSymbolDurationMs = 40
    )
}

/**
 * Complete runtime audio engine configuration.
 */
data class AudioConfig(
    val sampleRate: Int = 44100,
    val profile: FrequencyProfile = FrequencyProfile.STANDARD_AUDIBLE,
    val symbolDurationMs: Int = 35,
    val pilotDurationMs: Int = 120,
    val guardSilenceMs: Int = 40,
    val amplitude: Double = 0.85, // 0.0 to 1.0 peak amplitude
    val detectionThresholdRatio: Double = 2.2 // Detection energy ratio over baseline noise floor
) {
    val markFreq: Double get() = profile.markFreq
    val spaceFreq: Double get() = profile.spaceFreq
    val pilotFreq: Double get() = profile.pilotFreq

    val samplesPerSymbol: Int
        get() = (sampleRate * (symbolDurationMs / 1000.0)).toInt()

    val pilotSampleCount: Int
        get() = (sampleRate * (pilotDurationMs / 1000.0)).toInt()

    val guardSampleCount: Int
        get() = (sampleRate * (guardSilenceMs / 1000.0)).toInt()
}
