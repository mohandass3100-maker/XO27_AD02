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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.acoulink.ui.components.MetricBadge
import com.acoulink.ui.components.PacketProgressBar
import com.acoulink.ui.components.PacketStatusRow
import com.acoulink.ui.components.PrimaryButton
import com.acoulink.ui.components.SecondaryButton
import com.acoulink.ui.components.WaveformView
import com.acoulink.ui.theme.ErrorRed
import com.acoulink.ui.theme.PrimaryBlue
import com.acoulink.ui.theme.SuccessGreen
import com.acoulink.ui.theme.TextPrimary
import com.acoulink.ui.theme.TextSecondary
import com.acoulink.ui.theme.WarningAmber
import com.acoulink.viewmodel.SendViewModel
import com.acoulink.viewmodel.TransmissionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransmissionScreen(
    viewModel: SendViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val isBroadcasting = uiState.state == TransmissionState.BROADCASTING || uiState.state == TransmissionState.RETRANSMITTING

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (uiState.state == TransmissionState.COMPLETED) "Broadcast Complete" else "Broadcasting",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isBroadcasting) {
                            viewModel.stopBroadcast()
                        }
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
            // Animated Acoustic Waveform
            WaveformView(
                isActive = isBroadcasting,
                barCount = 20,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )

            // Progress Bar & Percentage
            PacketProgressBar(
                current = uiState.confirmedCount,
                total = uiState.totalPackets,
                barColor = if (uiState.state == TransmissionState.COMPLETED) SuccessGreen else PrimaryBlue
            )

            // Current Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when (uiState.state) {
                        TransmissionState.COMPLETED -> SuccessGreen.copy(alpha = 0.12f)
                        TransmissionState.WAITING_ACK -> WarningAmber.copy(alpha = 0.12f)
                        TransmissionState.RETRANSMITTING -> WarningAmber.copy(alpha = 0.15f)
                        else -> PrimaryBlue.copy(alpha = 0.08f)
                    }
                )
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = uiState.statusMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = when (uiState.state) {
                            TransmissionState.COMPLETED -> SuccessGreen
                            TransmissionState.WAITING_ACK -> WarningAmber
                            TransmissionState.RETRANSMITTING -> WarningAmber
                            else -> PrimaryBlue
                        }
                    )
                }
            }

            // Statistics Metric Grid (Sent, Confirmed, Pending, Retransmitted)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricBadge(label = "Sent", value = "${uiState.currentPacketIndex}", modifier = Modifier.weight(1f), color = PrimaryBlue)
                MetricBadge(label = "Confirmed", value = "${uiState.confirmedCount}", modifier = Modifier.weight(1f), color = SuccessGreen)
                MetricBadge(label = "Pending", value = "${uiState.pendingCount}", modifier = Modifier.weight(1f), color = TextSecondary)
                MetricBadge(label = "Retransmitted", value = "${uiState.retransmittedCount}", modifier = Modifier.weight(1f), color = WarningAmber)
            }

            // Packet Checklist Header
            Text(
                text = "PACKET STREAM",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                letterSpacing = 1.sp
            )

            // Packet List
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

            // Bottom Action
            if (isBroadcasting || uiState.state == TransmissionState.WAITING_ACK) {
                PrimaryButton(
                    text = "Stop Broadcast",
                    onClick = { viewModel.stopBroadcast() },
                    containerColor = ErrorRed,
                    icon = Icons.Default.Stop
                )
            } else {
                PrimaryButton(
                    text = "Done",
                    onClick = onNavigateBack,
                    containerColor = PrimaryBlue
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
