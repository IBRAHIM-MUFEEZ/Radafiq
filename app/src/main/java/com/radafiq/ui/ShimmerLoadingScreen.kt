package com.radafiq.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Lightweight shimmer loading placeholder shown during app startup while
 * Firestore profile/data loads. Replaces the old Lottie animation which
 * required JSON parsing and frame decoding.
 *
 * Renders skeleton card shapes with an animated gradient sweep — no asset
 * loading, no frame decode, just GPU-composited colors.
 */
@Composable
fun ShimmerLoadingScreen(
    shimmerColor: Color = Color(0x33FFFFFF),
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "shimmerTransition")
    val shimmerTranslate by transition.animateFloat(
        initialValue = -300f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )

    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            Color.Transparent,
            shimmerColor,
            Color.Transparent
        ),
        start = Offset(shimmerTranslate, 0f),
        end = Offset(shimmerTranslate + 200f, 0f)
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.Top
    ) {
        // App brand header placeholder
        ShimmerBlock(
            width = 120.dp,
            height = 20.dp,
            brush = shimmerBrush,
            shape = RoundedCornerShape(4.dp)
        )

        Spacer(Modifier.height(32.dp))

        // Summary card row — two skeleton cards side by side
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ShimmerBlock(
                modifier = Modifier.weight(1f),
                height = 80.dp,
                brush = shimmerBrush,
                shape = RoundedCornerShape(16.dp)
            )
            ShimmerBlock(
                modifier = Modifier.weight(1f),
                height = 80.dp,
                brush = shimmerBrush,
                shape = RoundedCornerShape(16.dp)
            )
        }

        Spacer(Modifier.height(24.dp))

        // Account list header
        ShimmerBlock(
            width = 100.dp,
            height = 16.dp,
            brush = shimmerBrush,
            shape = RoundedCornerShape(4.dp)
        )

        Spacer(Modifier.height(12.dp))

        // Account card skeleton
        ShimmerBlock(
            modifier = Modifier.fillMaxWidth(),
            height = 72.dp,
            brush = shimmerBrush,
            shape = RoundedCornerShape(16.dp)
        )

        Spacer(Modifier.height(10.dp))

        // Activity header
        ShimmerBlock(
            width = 80.dp,
            height = 16.dp,
            brush = shimmerBrush,
            shape = RoundedCornerShape(4.dp)
        )

        Spacer(Modifier.height(12.dp))

        // Activity list items (3 skeleton rows)
        repeat(3) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Avatar circle
                ShimmerBlock(
                    width = 40.dp,
                    height = 40.dp,
                    brush = shimmerBrush,
                    shape = CircleShape
                )
                // Text lines
                Column(modifier = Modifier.weight(1f)) {
                    ShimmerBlock(
                        modifier = Modifier.fillMaxWidth(0.6f),
                        height = 14.dp,
                        brush = shimmerBrush,
                        shape = RoundedCornerShape(4.dp)
                    )
                    Spacer(Modifier.height(6.dp))
                    ShimmerBlock(
                        modifier = Modifier.fillMaxWidth(0.4f),
                        height = 12.dp,
                        brush = shimmerBrush,
                        shape = RoundedCornerShape(4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ShimmerBlock(
    modifier: Modifier = Modifier,
    width: Dp = Dp.Unspecified,
    height: Dp,
    brush: Brush,
    shape: RoundedCornerShape
) {
    val baseColor = Color(0x1A666666)
    Box(
        modifier = modifier
            .then(if (width != Dp.Unspecified) Modifier.width(width) else Modifier)
            .height(height)
            .clip(shape)
            .background(baseColor)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(brush = brush)
        }
    }
}
