package com.acoulink.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import com.acoulink.audio.AudioReceiver
import com.acoulink.audio.AudioSender
import com.acoulink.audio.ReceiverEventListener
import com.acoulink.audio.ReceiverState
import com.acoulink.protocol.Packet
import com.acoulink.protocol.PacketEncoder
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.acoulink.audio.AudioConfig
import com.acoulink.data.LatestMessageRepository
import com.acoulink.ui.theme.ErrorRed
import com.acoulink.ui.theme.PrimaryBlue
import com.acoulink.ui.theme.SecondaryCyan
import com.acoulink.ui.theme.SuccessGreen
import com.acoulink.ui.theme.SuccessGreenLight
import com.acoulink.ui.theme.TextPrimary
import com.acoulink.ui.theme.TextSecondary
import com.acoulink.ui.theme.WarningAmber
import com.acoulink.viewmodel.HomeViewModel
import com.acoulink.viewmodel.ReceiveViewModel
import com.acoulink.viewmodel.SendViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/**
 * Live Diagnostics and Hardware Telemetry Screen (PS02 Requirement 22).
 * Verifies Audio Input/Output, FSK tone configs, packet counts, CRC error telemetry, and Beacon status.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    homeViewModel: HomeViewModel,
    sendViewModel: SendViewModel,
    receiveViewModel: ReceiveViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val audioConfig = remember { AudioConfig() }
    val latestRepo = remember { LatestMessageRepository.getInstance(context) }

    val homeUiState by homeViewModel.uiState.collectAsState()
    val sendUiState by sendViewModel.uiState.collectAsState()
    val receiveUiState by receiveViewModel.uiState.collectAsState()
    val isBeaconActive by latestRepo.isBeaconActive.collectAsState()

    var isPlayingTestTone by remember { mutableStateOf(false) }
    var isRunningLoopbackTest by remember { mutableStateOf(false) }
    var loopbackStatusText by remember { mutableStateOf<String?>(null) }
    var loopbackSuccess by remember { mutableStateOf<Boolean?>(null) }

    // Check Hardware & Permissions
    val hasMicPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    val isAudioInputSupported = try {
        val minBuf = AudioRecord.getMinBufferSize(
            audioConfig.sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        minBuf > 0
    } catch (_: Exception) {
        false
    }

    val isAudioOutputSupported = try {
        val minBuf = AudioTrack.getMinBufferSize(
            audioConfig.sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        minBuf > 0
    } catch (_: Exception) {
        false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Acoustic Diagnostics",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Hardware & System Health Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "AUDIO HARDWARE & PERMISSIONS",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 1.sp
                    )

                    DiagnosticItem("Audio Input", if (isAudioInputSupported) "OK (44.1 kHz)" else "ERROR", isAudioInputSupported)
                    DiagnosticItem("Audio Output", if (isAudioOutputSupported) "OK (44.1 kHz)" else "ERROR", isAudioOutputSupported)
                    DiagnosticItem("Microphone Permission", if (hasMicPermission) "GRANTED" else "REQUIRED", hasMicPermission)
                }
            }

            // 2. FSK Physical Layer Configuration
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "FSK MODULATION PARAMETERS",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 1.sp
                    )

                    TelemetryRow("Modulation Type", "BFSK / CPFSK")
                    TelemetryRow("Mark Frequency (F1)", "${audioConfig.markFreq.toInt()} Hz (Bit 1)")
                    TelemetryRow("Space Frequency (F0)", "${audioConfig.spaceFreq.toInt()} Hz (Bit 0)")
                    TelemetryRow("Pilot Sync Tone", "${audioConfig.pilotFreq.toInt()} Hz")
                    TelemetryRow("Sample Rate", "${audioConfig.sampleRate} Hz")
                    TelemetryRow("Symbol Duration", "${audioConfig.symbolDurationMs} ms")
                    TelemetryRow("Samples / Symbol", "${audioConfig.samplesPerSymbol}")
                }
            }

            // 3. Protocol Telemetry Card (Live Counters)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "COMMUNICATION TELEMETRY",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 1.sp
                    )

                    TelemetryRow("Packets Sent", "${sendUiState.confirmedCount} / ${sendUiState.totalPackets}")
                    TelemetryRow("Packets Received", "${receiveUiState.currentPacketsReceived} / ${receiveUiState.totalExpectedPackets}")
                    TelemetryRow("CRC Errors", "${receiveUiState.crcErrorsCount}")
                    TelemetryRow("NACK Recovery Events", "${receiveUiState.missingSequences.size}")
                    TelemetryRow("Retransmissions", "${sendUiState.retransmittedCount}")
                    TelemetryRow("Latest Message ID", "#${latestRepo.getLatestBroadcast()?.messageId ?: sendUiState.latestMessageId ?: "None"}")
                    TelemetryRow("Beacon Status", if (isBeaconActive || sendUiState.isBeaconActive) "ACTIVE (Period: 7s)" else "STANDBY")
                }
            }

            // 4. Acoustic Self-Test (Speaker -> Microphone Loopback)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when (loopbackSuccess) {
                        true -> SuccessGreenLight.copy(alpha = 0.35f)
                        false -> ErrorRed.copy(alpha = 0.1f)
                        else -> MaterialTheme.colorScheme.surface
                    }
                ),
                border = BorderStroke(
                    1.dp,
                    when (loopbackSuccess) {
                        true -> SuccessGreen
                        false -> ErrorRed
                        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    }
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "ACOUSTIC SELF-TEST (SPEAKER → MIC)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 1.sp
                    )

                    Text(
                        text = "Verifies end-to-end encoding, BFSK modulation, speaker playback, mic recording, Goertzel demodulation, and CRC-16 on this single device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    if (loopbackStatusText != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (isRunningLoopbackTest) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.5.dp,
                                        color = PrimaryBlue
                                    )
                                } else if (loopbackSuccess == true) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = "Success",
                                        tint = SuccessGreen,
                                        modifier = Modifier.size(22.dp)
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.Error,
                                        contentDescription = "Error",
                                        tint = ErrorRed,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Text(
                                    text = loopbackStatusText ?: "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = TextPrimary
                                )
                            }
                        }
                    }

                    Button(
                        onClick = {
                            if (!isRunningLoopbackTest) {
                                isRunningLoopbackTest = true
                                loopbackSuccess = null
                                loopbackStatusText = "1. Initializing mic AudioRecord..."
                                scope.launch(Dispatchers.IO) {
                                    val testSender = AudioSender(audioConfig)
                                    val testReceiver = AudioReceiver(audioConfig)
                                    val testPacket = PacketEncoder.createDataPackets(message = "AcouLink OK", messageId = 0x55AA).first()
                                    var packetReceived: Packet? = null

                                    val listenJob = launch {
                                        testReceiver.startListening(object : ReceiverEventListener {
                                            override fun onStateChanged(state: ReceiverState, signalQualityPercent: Int) {
                                                when (state) {
                                                    ReceiverState.LISTENING -> loopbackStatusText = "2. Mic active... Starting speaker transmission"
                                                    ReceiverState.SIGNAL_DETECTED -> loopbackStatusText = "3. Carrier tone detected ($signalQualityPercent%)"
                                                    ReceiverState.SYNCHRONIZING -> loopbackStatusText = "4. Pilot sync locked! Carrier aligned"
                                                    ReceiverState.RECEIVING -> loopbackStatusText = "5. Demodulating BFSK symbols..."
                                                    else -> Unit
                                                }
                                            }
                                            override fun onPacketDecoded(packet: Packet) {
                                                packetReceived = packet
                                            }
                                            override fun onError(message: String) {
                                                loopbackStatusText = "Error: $message"
                                            }
                                        })
                                    }

                                    delay(400)
                                    loopbackStatusText = "2. Broadcasting acoustic packet via speaker..."
                                    testSender.transmitPackets(listOf(testPacket))

                                    val startWait = System.currentTimeMillis()
                                    while (packetReceived == null && System.currentTimeMillis() - startWait < 4000) {
                                        delay(100)
                                    }

                                    listenJob.cancel()
                                    testReceiver.stop()
                                    testSender.stop()

                                    val received = packetReceived
                                    if (received != null) {
                                        val decodedText = String(received.payload, Charsets.UTF_8)
                                        val isCrcOk = !received.isCorrupted
                                        if (isCrcOk) {
                                            loopbackSuccess = true
                                            loopbackStatusText = "✓ PASSED! Decoded: '$decodedText' (CRC: 0x${received.crc16.toString(16).uppercase()}) - 100% Functional"
                                        } else {
                                            loopbackSuccess = false
                                            loopbackStatusText = "Packet received with CRC checksum error"
                                        }
                                    } else {
                                        loopbackSuccess = false
                                        loopbackStatusText = "Self-test timed out. Increase device volume to 80% and retry."
                                    }
                                    isRunningLoopbackTest = false
                                }
                            }
                        },
                        enabled = !isRunningLoopbackTest,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (loopbackSuccess == true) SuccessGreen else PrimaryBlue
                        )
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            if (isRunningLoopbackTest) "Running Acoustic Self-Test..."
                            else if (loopbackSuccess == true) "Re-run Acoustic Self-Test"
                            else "Run Acoustic Self-Test (Speaker → Mic)"
                        )
                    }
                }
            }

            // 5. Quick Audio Test Tone Generator
            Button(
                onClick = {
                    if (!isPlayingTestTone) {
                        isPlayingTestTone = true
                        scope.launch(Dispatchers.IO) {
                            playTestTone(audioConfig.sampleRate, 1000.0, 500)
                            isPlayingTestTone = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text(if (isPlayingTestTone) "Playing 1000 Hz Tone..." else "Test Speaker Output (1 kHz Tone)")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DiagnosticItem(label: String, value: String, isOk: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = if (isOk) Icons.Default.CheckCircle else Icons.Default.Error,
                contentDescription = null,
                tint = if (isOk) SuccessGreen else ErrorRed,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (isOk) SuccessGreen else ErrorRed
            )
        }
    }
}

@Composable
private fun TelemetryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = TextPrimary)
    }
}

private fun playTestTone(sampleRate: Int, freq: Double, durationMs: Int) {
    try {
        val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
        val samples = ShortArray(numSamples)
        var phase = 0.0
        val phaseInc = 2.0 * PI * freq / sampleRate
        val maxAmp = (Short.MAX_VALUE * 0.7).toInt()

        for (i in 0 until numSamples) {
            samples[i] = (sin(phase) * maxAmp).toInt().toShort()
            phase = (phase + phaseInc) % (2.0 * PI)
        }

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(samples.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        track.write(samples, 0, samples.size)
        track.play()
        Thread.sleep(durationMs.toLong() + 100)
        track.stop()
        track.release()
    } catch (_: Exception) {}
}
