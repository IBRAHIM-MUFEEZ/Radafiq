package com.radafiq.ui

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.radafiq.data.settings.AppThemeMode

private val RadafiqNight = Color(0xFF020D18)
private val RadafiqNightDeep = Color(0xFF071525)
private val RadafiqNightSoft = Color(0xFF0C2035)
private val RadafiqNightRaised = Color(0xFF102840)
private val RadafiqOutline = Color(0xFF1A4060)
private val RadafiqCanvas = Color(0xFFF0F8FF)
private val RadafiqWhite = Color(0xFFFFFFFF)
private val RadafiqText = Color(0xFFE8F4FF)
private val RadafiqMuted = Color(0xFF6BAED4)

// Logo brand colors — deep blue → teal → green
private val RadafiqBlueDeep = Color(0xFF1A4FD4)
private val RadafiqBlue = Color(0xFF1A8FD4)
private val RadafiqTeal = Color(0xFF1AABCF)
private val RadafiqGreen = Color(0xFF1DD9A0)
private val RadafiqRed = Color(0xFFE8445A)
private val RadafiqRedSoft = Color(0xFFFFAAB5)

private val RadafiqLightColors: ColorScheme = lightColorScheme(
    primary = RadafiqBlue,
    onPrimary = RadafiqWhite,
    primaryContainer = Color(0xFFD8EFFA),
    onPrimaryContainer = Color(0xFF071525),
    secondary = RadafiqBlueDeep,
    onSecondary = RadafiqWhite,
    secondaryContainer = Color(0xFFD0E8F8),
    onSecondaryContainer = Color(0xFF071525),
    tertiary = RadafiqGreen,
    onTertiary = Color(0xFF071525),
    error = RadafiqRed,
    onError = RadafiqWhite,
    background = RadafiqCanvas,
    onBackground = Color(0xFF071525),
    surface = RadafiqWhite,
    onSurface = Color(0xFF071525),
    surfaceVariant = Color(0xFFD8EFFA),
    onSurfaceVariant = Color(0xFF3A7FA8),
    outline = Color(0xFFA8D4EA)
)

private val RadafiqDarkColors: ColorScheme = darkColorScheme(
    primary = RadafiqBlue,
    onPrimary = RadafiqWhite,
    primaryContainer = RadafiqBlueDeep,
    onPrimaryContainer = RadafiqText,
    secondary = RadafiqTeal,
    onSecondary = Color(0xFF071525),
    secondaryContainer = Color(0xFF0C2035),
    onSecondaryContainer = Color(0xFFB8E8F8),
    tertiary = RadafiqGreen,
    onTertiary = RadafiqNightDeep,
    error = RadafiqRed,
    onError = RadafiqWhite,
    background = RadafiqNight,
    onBackground = RadafiqText,
    surface = RadafiqNightDeep,
    onSurface = RadafiqText,
    surfaceVariant = RadafiqNightSoft,
    onSurfaceVariant = RadafiqMuted,
    outline = RadafiqOutline
)

private val LocalRadafiqDarkTheme = staticCompositionLocalOf { true }
private val AppSans = FontFamily.SansSerif
private val BaseTypography = Typography()

private fun appStyle(
    base: TextStyle,
    weight: FontWeight,
    letterSpacing: Float = 0f
): TextStyle {
    return base.copy(
        fontFamily = AppSans,
        fontWeight = weight,
        letterSpacing = letterSpacing.sp
    )
}

private val RadafiqTypography = Typography(
    headlineLarge = appStyle(BaseTypography.headlineLarge, FontWeight.Bold, 0.02f),
    headlineMedium = appStyle(BaseTypography.headlineMedium, FontWeight.Bold, 0.02f),
    headlineSmall = appStyle(BaseTypography.headlineSmall, FontWeight.SemiBold, 0.01f),
    titleLarge = appStyle(BaseTypography.titleLarge, FontWeight.SemiBold, 0.01f),
    titleMedium = appStyle(BaseTypography.titleMedium, FontWeight.SemiBold, 0.01f),
    titleSmall = appStyle(BaseTypography.titleSmall, FontWeight.Medium, 0.01f),
    bodyLarge = appStyle(BaseTypography.bodyLarge, FontWeight.Normal),
    bodyMedium = appStyle(BaseTypography.bodyMedium, FontWeight.Normal),
    bodySmall = appStyle(BaseTypography.bodySmall, FontWeight.Normal),
    labelLarge = appStyle(BaseTypography.labelLarge, FontWeight.SemiBold, 0.03f),
    labelMedium = appStyle(BaseTypography.labelMedium, FontWeight.Medium, 0.03f),
    labelSmall = appStyle(BaseTypography.labelSmall, FontWeight.Medium, 0.04f)
)

private val RadafiqShapes = Shapes(
    extraSmall = RoundedCornerShape(18.dp),
    small = RoundedCornerShape(22.dp),
    medium = RoundedCornerShape(28.dp),
    large = RoundedCornerShape(32.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

@Composable
fun RadafiqTheme(
    themeMode: AppThemeMode = AppThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val useDarkTheme = themeMode == AppThemeMode.DARK
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val statusBarColor = if (useDarkTheme) {
                RadafiqNightDeep
            } else {
                Color(0xFFD8EFFA)
            }
            window.statusBarColor = statusBarColor.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !useDarkTheme
        }
    }

    CompositionLocalProvider(LocalRadafiqDarkTheme provides useDarkTheme) {
        MaterialTheme(
            colorScheme = if (useDarkTheme) RadafiqDarkColors else RadafiqLightColors,
            typography = RadafiqTypography,
            shapes = RadafiqShapes,
            content = content
        )
    }
}

@Composable
fun RadafiqBackground(content: @Composable () -> Unit) {
    val useDarkTheme = LocalRadafiqDarkTheme.current
    val backgroundBrush = if (useDarkTheme) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF020D18),
                Color(0xFF071525),
                MaterialTheme.colorScheme.background
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                RadafiqWhite,
                Color(0xFFEAF6FF),
                MaterialTheme.colorScheme.background
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        GlassBackdrop()
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Transparent,
            content = content
        )
    }
}

@Composable
private fun GlassBackdrop() {
    val useDarkTheme = LocalRadafiqDarkTheme.current
    val primaryGlow = RadafiqBlue.copy(alpha = if (useDarkTheme) 0.18f else 0.12f)
    val secondaryGlow = RadafiqGreen.copy(alpha = if (useDarkTheme) 0.12f else 0.10f)
    val tertiaryGlow = RadafiqTeal.copy(alpha = if (useDarkTheme) 0.10f else 0.08f)

    Canvas(modifier = Modifier.fillMaxSize()) {
        drawCircle(
            color = primaryGlow,
            radius = size.minDimension * 0.28f,
            center = Offset(size.width * 0.18f, size.height * 0.1f),
            style = Fill
        )
        drawCircle(
            color = secondaryGlow,
            radius = size.minDimension * 0.24f,
            center = Offset(size.width * 0.92f, size.height * 0.18f),
            style = Fill
        )
        drawCircle(
            color = tertiaryGlow,
            radius = size.minDimension * 0.22f,
            center = Offset(size.width * 0.76f, size.height * 0.86f),
            style = Fill
        )
    }
}

@Composable
fun AdaptiveHeaderRow(
    modifier: Modifier = Modifier,
    breakpoint: Dp = 420.dp,
    leading: @Composable () -> Unit,
    trailing: (@Composable () -> Unit)? = null
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val stackVertically = trailing == null || maxWidth < breakpoint

        if (stackVertically) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                leading()
                if (trailing != null) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        trailing()
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    leading()
                }
                trailing()
            }
        }
    }
}

@Composable
fun ResponsiveTwoPane(
    modifier: Modifier = Modifier,
    breakpoint: Dp = 420.dp,
    spacing: Dp = 10.dp,
    first: @Composable (Modifier) -> Unit,
    second: @Composable (Modifier) -> Unit
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        if (maxWidth < breakpoint) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
                first(Modifier.fillMaxWidth())
                second(Modifier.fillMaxWidth())
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing)
            ) {
                first(Modifier.weight(1f))
                second(Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun PageHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    AdaptiveHeaderRow(
        modifier = modifier,
        leading = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        trailing = trailing
    )
}

@Composable
fun FlowCard(
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    content: @Composable () -> Unit
) {
    val useDarkTheme = LocalRadafiqDarkTheme.current

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (useDarkTheme) {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
            } else {
                RadafiqWhite.copy(alpha = 0.82f)
            },
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (useDarkTheme) {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.56f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.38f)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            content()
        }
    }
}

@Composable
fun HeroPanel(
    title: String,
    amount: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    val useDarkTheme = LocalRadafiqDarkTheme.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .border(
                width = 1.dp,
                color = if (useDarkTheme) {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.56f)
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                },
                shape = RoundedCornerShape(32.dp)
            )
            .background(
                Brush.linearGradient(
                    colors = if (useDarkTheme) {
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.82f)
                        )
                    } else {
                        listOf(
                            RadafiqWhite.copy(alpha = 0.92f),
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f),
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.88f)
                        )
                    }
                )
            )
            .padding(horizontal = 22.dp, vertical = 24.dp)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(120.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = if (useDarkTheme) 0.14f else 0.12f))
        )
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = amount,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun MetricPill(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
        .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.34f), RoundedCornerShape(18.dp))
            .defaultMinSize(minHeight = 72.dp)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color)
        )
        Column(modifier = Modifier.padding(start = 10.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun StatusBadge(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.22f), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

@Composable
fun AccentValueRow(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.32f), RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun DividerSpacer(height: Dp = 14.dp) {
    Spacer(modifier = Modifier.height(height))
}

@Composable
fun EmptyState(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.74f))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.34f), RoundedCornerShape(28.dp))
                .padding(24.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

fun formatMoney(value: Double): String {
    val formatter = java.text.NumberFormat.getNumberInstance(java.util.Locale("en", "IN"))
    formatter.minimumFractionDigits = 2
    formatter.maximumFractionDigits = 2
    return "₹${formatter.format(value)}"
}

fun formatDisplayDate(isoDate: String): String {
    return try {
        val d = java.time.LocalDate.parse(isoDate)
        "%02d/%02d/%04d".format(d.dayOfMonth, d.monthValue, d.year)
    } catch (_: Exception) { isoDate }
}

fun parseDisplayDate(display: String): String {
    return try {
        val parts = display.split("/")
        if (parts.size == 3) {
            java.time.LocalDate.of(parts[2].toInt(), parts[1].toInt(), parts[0].toInt()).toString()
        } else display
    } catch (_: Exception) { display }
}

fun accountAccent(accountKind: com.radafiq.data.models.AccountKind): Color {
    return when (accountKind) {
        com.radafiq.data.models.AccountKind.BANK_ACCOUNT -> RadafiqBlue
        com.radafiq.data.models.AccountKind.CREDIT_CARD  -> RadafiqRed
        com.radafiq.data.models.AccountKind.PERSON       -> RadafiqGreen
    }
}

fun warningColor(): Color = RadafiqRed

fun dangerColor(): Color = RadafiqRedSoft
