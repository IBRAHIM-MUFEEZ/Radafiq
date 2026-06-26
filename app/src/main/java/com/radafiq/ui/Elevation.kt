package com.radafiq.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object Elevation {
    val none = 0.dp
    val xxs = 1.dp
    val xs = 2.dp
    val sm = 4.dp
    val md = 6.dp
    val lg = 8.dp
    val xl = 12.dp
    val xxl = 16.dp
}

object BorderWidth {
    val thin = 0.5.dp
    val normal = 1.dp
    val thick = 2.dp
}

object CardElevation {
    val flat = Elevation.sm     // 4.dp — FlowCard, MetricPill
    val raised = Elevation.md   // 6.dp — HeroPanel, dialogs
    val floating = Elevation.lg // 8.dp — FAB, bottom bar
}
