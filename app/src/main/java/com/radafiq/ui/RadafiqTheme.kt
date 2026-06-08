package com.radafiq.ui

import android.app.Activity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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

private val GlassDark = Color(0xFF0A0A1A)
private val GlassDarkDeep = Color(0xFF070714)
private val GlassDarkSoft = Color(0xFF12122A)
private val GlassDarkRaised = Color(0xFF1E1E40)

private val GlassOutline = Color(0xFF3A3A6A)
private val GlassWhite = Color(0xFFFFFFFF)
private val GlassText = Color(0xFFF1F1F7)
private val GlassMuted = Color(0xFF8888BB)

// Web-matching accent palette — indigo, emerald, amber, rose
private val AccentIndigo = Color(0xFF818CF8)
private val AccentIndigoDeep = Color(0xFF6366F1)
private val AccentEmerald = Color(0xFF34D399)
private val AccentAmber = Color(0xFFFBBF24)
private val AccentRose = Color(0xFFFB7185)
private val AccentViolet = Color(0xFFA78BFA)
private val AccentCyan = Color(0xFF22D3EE)
private val AccentOrange = Color(0xFFFB923C)
private val AccentRed = Color(0xFFEF4444)

private val RadafiqLightColors: ColorScheme = lightColorScheme(
    primary = AccentIndigoDeep,
    onPrimary = GlassWhite,
    primaryContainer = Color(0xFFE8E4F8),
    onPrimaryContainer = Color(0xFF1A1A3E),
    secondary = AccentViolet,
    onSecondary = GlassWhite,
    secondaryContainer = Color(0xFFEEEAFA),
    onSecondaryContainer = Color(0xFF1A1A3E),
    tertiary = Color(0xFFEA580C),
    onTertiary = GlassWhite,
    error = Color(0xFFDC2626),
    onError = GlassWhite,
    background = Color(0xFFF0F0F8),
    onBackground = Color(0xFF1A1A3E),
    surface = GlassWhite.copy(alpha = 0.70f),
    onSurface = Color(0xFF1A1A3E),
    surfaceVariant = Color(0xFFF0EEF8),
    onSurfaceVariant = Color(0xFF6666AA),
    outline = Color(0xFFD0D0E8)
)

private val RadafiqDarkColors: ColorScheme = darkColorScheme(
    primary = AccentIndigo,
    onPrimary = GlassWhite,
    primaryContainer = AccentIndigoDeep,
    onPrimaryContainer = GlassText,
    secondary = AccentViolet,
    onSecondary = GlassDark,
    secondaryContainer = GlassDarkRaised,
    onSecondaryContainer = Color(0xFFD0D0F0),
    tertiary = AccentOrange,
    onTertiary = GlassDark,
    error = AccentRed,
    onError = GlassWhite,
    background = GlassDark,
    onBackground = GlassText,
    surface = Color(0x16FFFFFF),
    onSurface = GlassText,
    surfaceVariant = GlassDarkSoft,
    onSurfaceVariant = GlassMuted,
    outline = GlassOutline
)

internal val LocalRadafiqDarkTheme = staticCompositionLocalOf { true }
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
                GlassDarkDeep
            } else {
                Color(0xFFE8E4F4)
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
                Color(0xFF0A0A1A),
                Color(0xFF0E0E28),
                Color(0xFF141430),
                Color(0xFF0F0F20)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFFF0F0F8),
                Color(0xFFE8E4F4),
                Color(0xFFE0ECF8),
                Color(0xFFF0F0F8)
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
    val primaryGlow = AccentIndigo.copy(alpha = if (useDarkTheme) 0.15f else 0.08f)
    val secondaryGlow = AccentViolet.copy(alpha = if (useDarkTheme) 0.12f else 0.06f)
    val tertiaryGlow = AccentEmerald.copy(alpha = if (useDarkTheme) 0.08f else 0.04f)

    Canvas(modifier = Modifier.fillMaxSize()) {
        drawCircle(
            color = primaryGlow,
            radius = size.minDimension * 0.30f,
            center = Offset(size.width * 0.15f, size.height * 0.08f),
            style = Fill
        )
        drawCircle(
            color = secondaryGlow,
            radius = size.minDimension * 0.25f,
            center = Offset(size.width * 0.90f, size.height * 0.15f),
            style = Fill
        )
        drawCircle(
            color = tertiaryGlow,
            radius = size.minDimension * 0.20f,
            center = Offset(size.width * 0.70f, size.height * 0.88f),
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
                Color(0x16FFFFFF)
            } else {
                GlassWhite.copy(alpha = 0.75f)
            },
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (useDarkTheme) {
                GlassOutline.copy(alpha = 0.56f)
            } else {
                Color(0xFFD0D0E8).copy(alpha = 0.60f)
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
                    GlassOutline.copy(alpha = 0.56f)
                } else {
                    Color(0xFFD0D0E8).copy(alpha = 0.50f)
                },
                shape = RoundedCornerShape(32.dp)
            )
            .background(
                Brush.linearGradient(
                    colors = if (useDarkTheme) {
                        listOf(
                            Color(0x22FFFFFF),
                            Color(0x18FFFFFF),
                            Color(0x1EFFFFFF)
                        )
                    } else {
                        listOf(
                            GlassWhite.copy(alpha = 0.88f),
                            Color(0xFFF0EEF8).copy(alpha = 0.75f),
                            Color(0xFFEEEAFA).copy(alpha = 0.82f)
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
                .background(AccentIndigo.copy(alpha = if (useDarkTheme) 0.10f else 0.08f))
        )
        Column {
            GradientText(
                text = title,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                animationSpeedMs = 4000
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

/**
 * HeroPanel overload that accepts a raw [Double] and animates the count-up
 * internally. Prefer this over the String overload for live data values.
 */
@Composable
fun HeroPanel(
    title: String,
    amountValue: Double,
    subtitle: String,
    modifier: Modifier = Modifier,
    animationKey: Any = Unit
) {
    val useDarkTheme = LocalRadafiqDarkTheme.current
    val animatable = remember(animationKey) { Animatable(0f) }
    var hasAnimated by rememberSaveable(animationKey) { mutableStateOf(false) }
    LaunchedEffect(animationKey) {
        if (!hasAnimated) {
            hasAnimated = true
            animatable.snapTo(0f)
            animatable.animateTo(
                targetValue = amountValue.toFloat(),
                animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing)
            )
        } else {
            animatable.snapTo(amountValue.toFloat())
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .border(
                width = 1.dp,
                color = if (useDarkTheme) {
                    GlassOutline.copy(alpha = 0.60f)
                } else {
                    Color(0xFFD0D0E8).copy(alpha = 0.50f)
                },
                shape = RoundedCornerShape(32.dp)
            )
            .background(
                Brush.linearGradient(
                    colors = if (useDarkTheme) {
                        listOf(
                            Color(0x22FFFFFF),
                            Color(0x18FFFFFF),
                            Color(0x20FFFFFF)
                        )
                    } else {
                        listOf(
                            GlassWhite.copy(alpha = 0.88f),
                            GlassWhite.copy(alpha = 0.75f),
                            Color(0xFFF0EEF8).copy(alpha = 0.80f)
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
                .background(AccentIndigo.copy(alpha = if (useDarkTheme) 0.12f else 0.08f))
        )
        Column {
            GradientText(
                text = title,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                animationSpeedMs = 4000
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = formatMoney(animatable.value.toDouble()),
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
private fun GlassCardModifier(useDarkTheme: Boolean): Modifier = Modifier
    .clip(RoundedCornerShape(18.dp))
    .background(if (useDarkTheme) Color(0x16FFFFFF) else GlassWhite.copy(alpha = 0.75f))
    .border(
        1.dp,
        if (useDarkTheme) GlassOutline.copy(alpha = 0.55f) else Color(0xFFD0D0E8).copy(alpha = 0.58f),
        RoundedCornerShape(18.dp)
    )

@Composable
fun MetricPill(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val useDarkTheme = LocalRadafiqDarkTheme.current
    Row(
        modifier = modifier
            .then(GlassCardModifier(useDarkTheme))
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
            Spacer(modifier = Modifier.height(6.dp))
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

/**
 * MetricPill overload that accepts a raw [Double] and animates the count-up
 * internally. Prefer this over the String overload for live data values.
 */
@Composable
fun MetricPill(
    label: String,
    amountValue: Double,
    color: Color,
    modifier: Modifier = Modifier,
    animationKey: Any = Unit
) {
    val useDarkTheme = LocalRadafiqDarkTheme.current
    val animatable = remember(animationKey) { Animatable(0f) }
    var hasAnimated by rememberSaveable(animationKey) { mutableStateOf(false) }
    LaunchedEffect(animationKey) {
        if (!hasAnimated) {
            hasAnimated = true
            animatable.snapTo(0f)
            animatable.animateTo(
                targetValue = amountValue.toFloat(),
                animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing)
            )
        } else {
            animatable.snapTo(amountValue.toFloat())
        }
    }
    Row(
        modifier = modifier
            .then(GlassCardModifier(useDarkTheme))
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
                text = formatMoney(animatable.value.toDouble()),
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
    val useDarkTheme = LocalRadafiqDarkTheme.current
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (useDarkTheme) Color(0x16FFFFFF) else color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = if (useDarkTheme) 0.35f else 0.20f), RoundedCornerShape(16.dp))
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
    val useDarkTheme = LocalRadafiqDarkTheme.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (useDarkTheme) Color(0x16FFFFFF) else GlassWhite.copy(alpha = 0.75f))
            .border(1.dp, if (useDarkTheme) GlassOutline.copy(alpha = 0.55f) else Color(0xFFD0D0E8).copy(alpha = 0.58f), RoundedCornerShape(18.dp))
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

/**
 * AccentValueRow overload that accepts a raw [Double] and animates the count-up
 * internally. Prefer this over the String overload for live data values.
 */
@Composable
fun AccentValueRow(
    label: String,
    amountValue: Double,
    color: Color,
    modifier: Modifier = Modifier,
    animationKey: Any = Unit
) {
    val useDarkTheme = LocalRadafiqDarkTheme.current
    val animatable = remember(animationKey) { Animatable(0f) }
    var hasAnimated by rememberSaveable(animationKey) { mutableStateOf(false) }
    LaunchedEffect(animationKey) {
        if (!hasAnimated) {
            hasAnimated = true
            animatable.snapTo(0f)
            animatable.animateTo(
                targetValue = amountValue.toFloat(),
                animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing)
            )
        } else {
            animatable.snapTo(amountValue.toFloat())
        }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (useDarkTheme) Color(0x16FFFFFF) else GlassWhite.copy(alpha = 0.75f))
            .border(1.dp, if (useDarkTheme) GlassOutline.copy(alpha = 0.55f) else Color(0xFFD0D0E8).copy(alpha = 0.58f), RoundedCornerShape(18.dp))
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
            text = formatMoney(animatable.value.toDouble()),
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
    val useDarkTheme = LocalRadafiqDarkTheme.current
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(RoundedCornerShape(28.dp))
                .background(if (useDarkTheme) Color(0x16FFFFFF) else GlassWhite.copy(alpha = 0.75f))
                .border(1.dp, if (useDarkTheme) GlassOutline.copy(alpha = 0.55f) else Color(0xFFD0D0E8).copy(alpha = 0.58f), RoundedCornerShape(28.dp))
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

/**
 * Displays a currency value with a smooth count-up animation whenever [value] changes.
 * Uses a 700ms ease-out tween — the same duration as the web app.
 * Renders as a [Text] composable so it can be dropped in anywhere a Text is used.
 */
@Composable
fun AnimatedMoney(
    value: Double,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleSmall,
    color: Color = MaterialTheme.colorScheme.onSurface,
    fontWeight: FontWeight? = null,
    maxLines: Int = 1,
    modifier: Modifier = Modifier,
    animationKey: Any = Unit
) {
    val animatable = remember(animationKey) { Animatable(0f) }
    var hasAnimated by rememberSaveable(animationKey) { mutableStateOf(false) }
    LaunchedEffect(animationKey) {
        if (!hasAnimated) {
            hasAnimated = true
            animatable.snapTo(0f)
            animatable.animateTo(
                targetValue = value.toFloat(),
                animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing)
            )
        } else {
            animatable.snapTo(value.toFloat())
        }
    }
    Text(
        text = formatMoney(animatable.value.toDouble()),
        style = style,
        color = color,
        fontWeight = fontWeight,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
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

@Composable
fun incomingColor(): Color = AccentEmerald

@Composable
fun outgoingColor(): Color = dangerColor()

@Composable
fun accountAccent(accountKind: com.radafiq.data.models.AccountKind): Color {
    return when (accountKind) {
        com.radafiq.data.models.AccountKind.BANK_ACCOUNT -> incomingColor()
        com.radafiq.data.models.AccountKind.CREDIT_CARD  -> outgoingColor()
        com.radafiq.data.models.AccountKind.PERSON       -> MaterialTheme.colorScheme.secondary
    }
}

@Composable
fun warningColor(): Color = MaterialTheme.colorScheme.tertiary

@Composable
fun dangerColor(): Color = MaterialTheme.colorScheme.error
