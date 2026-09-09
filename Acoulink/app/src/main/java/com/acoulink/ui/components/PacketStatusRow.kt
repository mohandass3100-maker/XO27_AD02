package com.acoulink.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.acoulink.ui.theme.ErrorRed
import com.acoulink.ui.theme.ErrorRedLight
import com.acoulink.ui.theme.PrimaryBlue
import com.acoulink.ui.theme.PrimaryBlueLight
import com.acoulink.ui.theme.SuccessGreen
import com.acoulink.ui.theme.SuccessGreenLight
import com.acoulink.ui.theme.TextPrimary
import com.acoulink.ui.theme.WarningAmber
import com.acoulink.ui.theme.WarningAmberLight

enum class PacketVisualState {
    CONFIRMED,
    TRANSMITTING,
    MISSING,
    RETRANSMITTING,
    PENDING
}

@Composable
fun PacketStatusRow(
    sequenceNumber: Int,
    state: PacketVisualState,
    modifier: Modifier = Modifier,
    details: String? = null
) {
    val (bgColor, iconColor, icon, stateLabel) = when (state) {
        PacketVisualState.CONFIRMED -> Quadruple(SuccessGreenLight, SuccessGreen, Icons.Default.Check, "Verified")
        PacketVisualState.TRANSMITTING -> Quadruple(PrimaryBlueLight, PrimaryBlue, Icons.Default.Refresh, "Transmitting")
        PacketVisualState.MISSING -> Quadruple(ErrorRedLight, ErrorRed, Icons.Default.ErrorOutline, "Missing")
        PacketVisualState.RETRANSMITTING -> Quadruple(WarningAmberLight, WarningAmber, Icons.Default.Refresh, "Recovering")
        PacketVisualState.PENDING -> Quadruple(Color(0xFFF3F4F6), Color(0xFF9CA3AF), Icons.Default.Schedule, "Pending")
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor.copy(alpha = 0.6f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(bgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = "Packet " + String.format("%02d", sequenceNumber),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (details != null) {
                Text(
                    text = details,
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
            Text(
                text = stateLabel,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = iconColor
            )
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
