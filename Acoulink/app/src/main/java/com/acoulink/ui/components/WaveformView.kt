package com.acoulink.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.acoulink.ui.theme.PrimaryBlue
import com.acoulink.ui.theme.SecondaryCyan

@Composable
fun WaveformView(
    isActive: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 16,
    baseColor: Color = PrimaryBlue,
    activeColor: Color = SecondaryCyan
) {
    val transition = rememberInfiniteTransition(label = "waveform_anim")

    // Heights multipliers for natural acoustic wave appearance
    val basePattern = listOf(0.3f, 0.5f, 0.8f, 1.0f, 0.7f, 0.4f, 0.6f, 0.9f, 0.75f, 0.5f, 0.85f, 0.6f, 0.4f, 0.7f, 0.5f, 0.3f)

    val waveOffset by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wave_scale"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until barCount) {
            val patternFactor = basePattern[i % basePattern.size]
            val barHeightFraction = if (isActive) {
                (patternFactor * waveOffset).coerceIn(0.15f, 1.0f)
            } else {
                (patternFactor * 0.25f).coerceIn(0.1f, 0.35f)
            }

            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height((72 * barHeightFraction).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (isActive) activeColor else baseColor.copy(alpha = 0.3f))
            )
            if (i < barCount - 1) {
                Box(modifier = Modifier.width(5.dp))
            }
        }
    }
}
