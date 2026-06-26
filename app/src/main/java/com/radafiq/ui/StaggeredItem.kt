package com.radafiq.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

@Composable
fun StaggeredItem(
    index: Int,
    modifier: Modifier = Modifier,
    delayMs: Int = 80,
    visible: Boolean = true,
    content: @Composable () -> Unit
) {
    val isVisible = remember { mutableStateOf(false) }

    LaunchedEffect(visible) {
        if (visible && !isVisible.value) {
            kotlinx.coroutines.delay((index * delayMs).toLong())
            isVisible.value = true
        }
    }

    AnimatedVisibility(
        visible = isVisible.value,
        enter = fadeIn(animationSpec = tween(300)) +
            slideInVertically(
                animationSpec = tween(300),
                initialOffsetY = { it / 4 }
            ),
        modifier = modifier
    ) {
        content()
    }
}
