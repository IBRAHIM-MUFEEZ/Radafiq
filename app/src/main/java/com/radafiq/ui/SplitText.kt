package com.radafiq.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.delay

enum class SplitType { CHARS, WORDS }

@Composable
fun SplitText(
    text: String,
    modifier: Modifier = Modifier,
    delayMs: Int = 50,
    durationMs: Int = 400,
    splitType: SplitType = SplitType.CHARS,
    fromOpacity: Float = 0f,
    fromY: Float = 20f,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = Color.Unspecified,
    onAnimationComplete: (() -> Unit)? = null
) {
    val elements = remember(text, splitType) {
        when (splitType) {
            SplitType.WORDS -> {
                val regex = Regex("""\S+\s*""")
                regex.findAll(text).map { it.value }.toList()
            }
            SplitType.CHARS -> text.map { it.toString() }
        }
    }

    val animations = remember(elements.size) {
        List(elements.size) { Animatable(0f) }
    }

    LaunchedEffect(Unit) {
        animations.forEachIndexed { index, anim ->
            if (index > 0) delay(delayMs.toLong())
            anim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = durationMs)
            )
        }
        onAnimationComplete?.invoke()
    }

    Row(modifier = modifier) {
        elements.forEachIndexed { index, element ->
            val progress = animations[index].value
            val alpha = fromOpacity + (1f - fromOpacity) * progress
            val yOffset = fromY * (1f - progress)

            Text(
                text = element,
                style = style,
                color = color,
                modifier = Modifier
                    .graphicsLayer {
                        this.alpha = alpha
                        translationY = yOffset
                    }
            )
        }
    }
}
