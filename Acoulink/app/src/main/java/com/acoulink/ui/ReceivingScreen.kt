package com.acoulink.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.acoulink.ui.components.PacketProgressBar
import com.acoulink.ui.components.PacketStatusRow
import com.acoulink.ui.components.PrimaryButton
import com.acoulink.ui.components.WaveformView
import com.acoulink.ui.theme.ErrorRed
import com.acoulink.ui.theme.PrimaryBlue
import com.acoulink.ui.theme.SecondaryCyan
import com.acoulink.ui.theme.SuccessGreen
import com.acoulink.ui.theme.TextPrimary
import com.acoulink.ui.theme.TextSecondary
import com.acoulink.ui.theme.WarningAmber
import com.acoulink.viewmodel.ReceiveViewModel
import com.acoulink.viewmodel.ReceptionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceivingScreen(
    viewModel: ReceiveViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToRecovery: () -> Unit,
    onNavigateToReceived: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.state) {
        when (uiState.state) {
            ReceptionState.RECOVERING -> onNavigateToRecovery()
            ReceptionState.MESSAGE_RECEIVED -> onNavigateToReceived()
            ReceptionState.STOPPED, ReceptionState.IDLE -> onNavigateBack()
            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Receiving Message",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.stopListening()
                        onNavigateBack()
                    }) {
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
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Live Acoustic Waveform Visualizer
            WaveformView(
                isActive = true,
                barCount = 20,
                baseColor = SecondaryCyan,
                activeColor = SecondaryCyan,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            )

            // Dynamic State Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when (uiState.state) {
                        ReceptionState.SIGNAL_DETECTED -> WarningAmber.copy(alpha = 0.15f)
                        ReceptionState.SYNCHRONIZING -> SecondaryCyan.copy(alpha = 0.15f)
                        ReceptionState.RECEIVING -> PrimaryBlue.copy(alpha = 0.12f)
                        else -> MaterialTheme.colorScheme.surface
                    }
                )
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = when (uiState.state) {
                                ReceptionState.SIGNAL_DETECTED -> "Signal detected"
                                ReceptionState.SYNCHRONIZING -> "Synchronizing..."
                                else -> "Receiving packets"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = uiState.statusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }

                    // Signal Quality Indicator
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SignalCellularAlt,
                            contentDescription = "Signal Quality",
                            tint = if (uiState.signalQualityPercent > 50) SuccessGreen else WarningAmber,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "${uiState.signalQualityPercent}%",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (uiState.signalQualityPercent > 50) SuccessGreen else WarningAmber
                        )
                    }
                }
            }

            // Packet Reception Progress
            PacketProgressBar(
                current = uiState.currentPacketsReceived,
                total = uiState.totalExpectedPackets.coerceAtLeast(1),
                barColor = SecondaryCyan
            )

            // Packet Checklist Header
            Text(
                text = "RECEIVED PACKET STREAM",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                letterSpacing = 1.sp
            )

            // Real-time Packet Status List
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.packetList) { item ->
                    PacketStatusRow(
                        sequenceNumber = item.sequenceNumber,
                        state = item.visualState,
                        details = item.details
                    )
                }
            }

            // Stop Reception Button
            PrimaryButton(
                text = "Stop Listening",
                onClick = {
                    viewModel.stopListening()
                    onNavigateBack()
                },
                containerColor = ErrorRed,
                icon = Icons.Default.Stop
            )

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
