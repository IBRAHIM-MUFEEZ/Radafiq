package com.radafiq.ui

import android.app.Activity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.radafiq.data.settings.AppThemeMode

// ── New "Warm Modern Fintech" palette ─────────────────────────────────────
// Dark mode neutrals (warm slate)
private val DarkBg             = Color(0xFF0A0A0A)
private val DarkSurface         = Color(0xFF1C1C1E)
private val DarkElevated        = Color(0xFF32324A)
private val DarkOutline         = Color(0xFF333344)
private val DarkTextPrimary     = Color(0xFFF0F0F4)
private val DarkTextMuted       = Color(0xFF8888A0)

// Light mode neutrals (warm gray)
private val LightBg             = Color(0xFFF5F3F0)
private val LightSurface        = Color(0xFFFFFFFF)
private val LightElevated       = Color(0xFFFFFFFF)
private val LightOutline        = Color(0xFFDDD8D0)
private val LightTextPrimary    = Color(0xFF1A1A22)
private val LightTextMuted      = Color(0xFF777780)

// Accent palette — warm cobalt + supporting hues
private val AccentCobalt        = Color(0xFF5B7FFF)
private val AccentCobaltDeep    = Color(0xFF4A6CF0)
private val AccentTeal          = Color(0xFF2DD4A0)
private val AccentTealDeep      = Color(0xFF10B981)
private val AccentAmber         = Color(0xFFFBBF24)
private val AccentWarmAmber     = Color(0xFFF59E5A)
private val AccentRose          = Color(0xFFFB7185)
private val AccentCyan          = Color(0xFF22D3EE)
private val AccentOrange        = Color(0xFFFB923C)
private val AccentCoralRed      = Color(0xFFFF6B6A)

private val RadafiqLightColors: ColorScheme = lightColorScheme(
    primary = AccentCobalt,
    onPrimary = LightSurface,
    primaryContainer = Color(0xFFEEF2FF),
    onPrimaryContainer = Color(0xFF1A1A3E),
    secondary = AccentTealDeep,
    onSecondary = LightSurface,
    secondaryContainer = Color(0xFFECFDF5),
    onSecondaryContainer = Color(0xFF0F2D24),
    tertiary = Color(0xFFD97706),
    onTertiary = LightSurface,
    error = Color(0xFFDC2626),
    onError = LightSurface,
    background = LightBg,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = Color(0xFFF0EEF8),
    onSurfaceVariant = LightTextMuted,
    outline = LightOutline
)

private val RadafiqDarkColors: ColorScheme = darkColorScheme(
    primary = AccentCobalt,
    onPrimary = DarkTextPrimary,
    primaryContainer = Color(0xFF1A2355),
    onPrimaryContainer = DarkTextPrimary,
    secondary = AccentTeal,
    onSecondary = DarkBg,
    secondaryContainer = Color(0xFF0F2D24),
    onSecondaryContainer = Color(0xFFA7F3D0),
    tertiary = AccentWarmAmber,
    onTertiary = DarkBg,
    error = AccentCoralRed,
    onError = DarkTextPrimary,
    background = DarkBg,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurface,
    onSurfaceVariant = DarkTextMuted,
    outline = DarkOutline
)

internal val LocalRadafiqDarkTheme = staticCompositionLocalOf { true }

/**
 * Shared Switch colors for every toggle in the app.
 * Dark OFF → #4B5563 (visible slate), Light OFF → #D1D5DB (neutral gray),
 * ON → primary color.
 */
@Composable
fun radafiqSwitchColors() = SwitchDefaults.colors(
    uncheckedTrackColor = if (LocalRadafiqDarkTheme.current) Color(0xFF4B5563) else Color(0xFFD1D5DB),
    uncheckedThumbColor = if (LocalRadafiqDarkTheme.current) Color(0xFFE0E0E0) else Color.White,
    checkedTrackColor = MaterialTheme.colorScheme.primary,
    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
)

private val BaseTypography = Typography()

private fun appStyle(
    base: TextStyle,
    weight: FontWeight,
    letterSpacing: Float = 0f
): TextStyle {
    return base.copy(
        fontWeight = weight,
        letterSpacing = letterSpacing.sp
    )
}

private val RadafiqTypography = Typography(
    headlineLarge = appStyle(BaseTypography.headlineLarge, FontWeight.Bold, 0.01f),
    headlineMedium = appStyle(BaseTypography.headlineMedium, FontWeight.Bold, 0.005f),
    headlineSmall = appStyle(BaseTypography.headlineSmall, FontWeight.SemiBold, 0.01f),
    titleLarge = appStyle(BaseTypography.titleLarge, FontWeight.SemiBold, 0.01f),
    titleMedium = appStyle(BaseTypography.titleMedium, FontWeight.SemiBold, 0.01f),
    titleSmall = appStyle(BaseTypography.titleSmall, FontWeight.Medium, 0.01f),
    bodyLarge = appStyle(BaseTypography.bodyLarge, FontWeight.Normal),
    bodyMedium = appStyle(BaseTypography.bodyMedium, FontWeight.Normal),
    bodySmall = appStyle(BaseTypography.bodySmall, FontWeight.Normal),
    labelLarge = appStyle(BaseTypography.labelLarge, FontWeight.SemiBold, 0.02f),
    labelMedium = appStyle(BaseTypography.labelMedium, FontWeight.Medium, 0.02f),
    labelSmall = appStyle(BaseTypography.labelSmall, FontWeight.Medium, 0.03f)
)

private val RadafiqShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp)
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
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !useDarkTheme
            controller.isAppearanceLightNavigationBars = !useDarkTheme

            val windowBg = if (useDarkTheme) 0xFF0A0A0A.toInt() else 0xFFF5F3F0.toInt()
            window.decorView.setBackgroundColor(windowBg)
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
fun radafiqSolidBg(): Color {
    val useDarkTheme = LocalRadafiqDarkTheme.current
    return if (useDarkTheme) DarkBg else LightBg
}

@Composable
fun Modifier.radafiqScrollBackground(): Modifier {
    val bg = radafiqSolidBg()
    return this.background(bg)
}

@Composable
fun RadafiqBackground(content: @Composable () -> Unit) {
    val useDarkTheme = LocalRadafiqDarkTheme.current
    val solidBase = if (useDarkTheme) DarkBg else LightBg

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(solidBase)
    ) {
        RadafiqBackdrop()
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = solidBase,
            content = content
        )
    }
}

@Composable
private fun RadafiqBackdrop() {
    val useDarkTheme = LocalRadafiqDarkTheme.current
    // Very subtle radial glows — removed the heavy glass-backdrop aesthetic
    val glow1 = AccentCobalt.copy(alpha = if (useDarkTheme) 0.06f else 0.03f)
    val glow2 = AccentTeal.copy(alpha = if (useDarkTheme) 0.04f else 0.02f)

    Canvas(modifier = Modifier.fillMaxSize()) {
        drawCircle(
            color = glow1,
            radius = size.minDimension * 0.35f,
            center = Offset(size.width * 0.12f, size.height * 0.06f),
            style = Fill
        )
        drawCircle(
            color = glow2,
            radius = size.minDimension * 0.25f,
            center = Offset(size.width * 0.85f, size.height * 0.90f),
            style = Fill
        )
    }
}

// ── Layout utilities (unchanged in structure, kept for reference) ─────────

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

// ── Reusable components — NEW WARM MODERN FINANCIAL STYLING ───────────────

/**
 * Elevated card with solid surface, subtle shadow, no border.
 * Replaces the old glass-morphism card (semi-transparent bg + border).
 */
@Composable
fun FlowCard(
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    verticalPadding: Dp = 12.dp,
    content: @Composable () -> Unit
) {
    val useDarkTheme = LocalRadafiqDarkTheme.current

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(
            containerColor = if (useDarkTheme) DarkSurface else LightSurface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 4.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = verticalPadding)
        ) {
            content()
        }
    }
}

/**
 * Hero panel for prominent financial metrics (Outstanding Balance).
 * Solid primary container background, elevated, no decorative circles.
 */
@Composable
fun HeroPanel(
    title: String,
    amount: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    val useDarkTheme = LocalRadafiqDarkTheme.current

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (useDarkTheme)
                DarkSurface
            else
                Color(0xFFEEF2FF),
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 6.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 24.dp)
        ) {
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
 * HeroPanel overload that accepts a raw [Double] with animated count-up.
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

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (useDarkTheme)
                DarkSurface
            else
                Color(0xFFEEF2FF),
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 6.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 24.dp)
        ) {
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

/**
 * Metric pill — solid surface card with a colored 3dp left accent bar.
 * Replaces old glass card with a circle dot.
 */
@Composable
fun MetricPill(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
    elevatedSurface: Boolean = false,
    transparentBackground: Boolean = false,
    compact: Boolean = false
) {
    val useDarkTheme = LocalRadafiqDarkTheme.current
    val bg = when {
        transparentBackground -> Color.Transparent
        useDarkTheme && elevatedSurface -> DarkElevated
        useDarkTheme -> DarkSurface
        else -> LightSurface
    }
    val contentPadding = if (compact) 8.dp else 12.dp
    val verticalPad = if (compact) 10.dp else 14.dp
    val labelSpacer = if (compact) 4.dp else 6.dp

    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(bg)
            .then(if (compact || transparentBackground) Modifier else Modifier.defaultMinSize(minHeight = 72.dp))
    ) {
        // Left accent bar
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp))
                .background(color)
        )
        Column(
            modifier = Modifier.padding(start = contentPadding, top = verticalPad, bottom = verticalPad, end = contentPadding + 2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(labelSpacer))
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
 * MetricPill overload with animated count-up.
 */
@Composable
fun MetricPill(
    label: String,
    amountValue: Double,
    color: Color,
    modifier: Modifier = Modifier,
    animationKey: Any = Unit,
    elevatedSurface: Boolean = false,
    transparentBackground: Boolean = false,
    compact: Boolean = false
) {
    val useDarkTheme = LocalRadafiqDarkTheme.current
    val bg = when {
        transparentBackground -> Color.Transparent
        useDarkTheme && elevatedSurface -> DarkElevated
        useDarkTheme -> DarkSurface
        else -> LightSurface
    }
    val contentPadding = if (compact) 8.dp else 12.dp
    val verticalPad = if (compact) 10.dp else 14.dp
    val labelSpacer = if (compact) 4.dp else 6.dp
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
            .clip(MaterialTheme.shapes.small)
            .background(bg)
            .then(if (compact || transparentBackground) Modifier else Modifier.defaultMinSize(minHeight = 72.dp))
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp))
                .background(color)
        )
        Column(
            modifier = Modifier.padding(start = contentPadding, top = verticalPad, bottom = verticalPad, end = contentPadding + 2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(labelSpacer))
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
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (useDarkTheme) color.copy(alpha = 0.14f)
                else color.copy(alpha = 0.10f)
            )
            .border(
                1.dp,
                color.copy(alpha = if (useDarkTheme) 0.25f else 0.15f),
                RoundedCornerShape(12.dp)
            )
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
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f))
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
 * AccentValueRow overload with animated count-up.
 */
@Composable
fun AccentValueRow(
    label: String,
    amountValue: Double,
    color: Color,
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
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f))
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
        Card(
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = if (useDarkTheme) DarkSurface else LightSurface
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 2.dp
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
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
}

// ── bounceClick: press-scale micro-interaction ────────────────────────────

/**
 * Adds a subtle press animation (scale 1.0 → 0.97) using spring physics.
 * Apply to clickable FlowCards, tab items, list rows, HeroPanel, etc.
 */
@Composable
fun Modifier.bounceClick(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (!enabled) 1f else if (isPressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "bounce"
    )
    val focusAlpha by animateFloatAsState(
        targetValue = if (isFocused) 1f else 0f,
        animationSpec = tween(200),
        label = "focus-ring-alpha"
    )

    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .then(
            if (enabled) {
                Modifier
                    .drawWithContent {
                        drawContent()
                        // Draw a visible focus ring for keyboard/switch accessibility
                        if (isFocused || focusAlpha > 0.01f) {
                            drawRect(
                                color = Color.White.copy(alpha = 0.25f * focusAlpha),
                                topLeft = Offset.Zero,
                                size = size,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                    width = 3.dp.toPx()
                                )
                            )
                        }
                    }
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick
                    )
            } else this
        )
}

// ── Money formatting & date utilities ─────────────────────────────────────

fun formatMoney(value: Double): String {
    val formatter = java.text.NumberFormat.getNumberInstance(java.util.Locale("en", "IN"))
    formatter.minimumFractionDigits = 2
    formatter.maximumFractionDigits = 2
    return "₹${formatter.format(value)}"
}

/**
 * Displays a currency value with a smooth count-up animation whenever [value] changes.
 */
@Composable
fun AnimatedMoney(
    value: Double,
    style: TextStyle = MaterialTheme.typography.titleSmall,
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
fun incomingColor(): Color = AccentTeal

@Composable
fun outgoingColor(): Color = AccentOrange

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

// ── Shimmer / Skeleton Loading Components ───────────────────────────────────

/**
 * Shimmer brush that animates a highlight traveling diagonally across the element.
 * Apply via [Modifier.shimmer] instead of calling this directly.
 */
@Composable
private fun shimmerBrush(): Brush {
    val useDarkTheme = LocalRadafiqDarkTheme.current
    val baseColor = if (useDarkTheme) Color(0xFF2A2A38) else Color(0xFFE8E6E0)
    val highlightColor = if (useDarkTheme) Color(0xFF353545) else Color(0xFFF2F0EC)

    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val translateX by infiniteTransition.animateFloat(
        initialValue = -200f,
        targetValue = 2000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer-offset"
    )

    return Brush.linearGradient(
        colors = listOf(baseColor, highlightColor, baseColor),
        start = Offset(translateX, 0f),
        end = Offset(translateX + 300f, 300f)
    )
}

/**
 * Apply shimmer loading animation (skeleton pulse) to any component.
 * When [loading] is false, renders the [content] normally.
 */
@Composable
fun ShimmerLayout(
    loading: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    if (loading) {
        val brush = shimmerBrush()
        Box(
            modifier = modifier
                .clip(MaterialTheme.shapes.small)
                .drawWithContent {
                    drawRect(brush = brush, size = size)
                }
        )
    } else {
        content()
    }
}

/**
 * Skeleton card matching [FlowCard] layout — placeholder while data loads.
 */
@Composable
fun ShimmerCard(
    modifier: Modifier = Modifier,
    lineCount: Int = 3,
    lineHeight: Dp = 14.dp
) {
    val useDarkTheme = LocalRadafiqDarkTheme.current
    val brush = shimmerBrush()

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(
            containerColor = if (useDarkTheme) DarkSurface else LightSurface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(lineCount) { index ->
                val widthFraction = when (index) {
                    0 -> 0.45f   // title width
                    1 -> 0.90f   // body width
                    else -> 0.65f // shorter line
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = widthFraction)
                        .height(lineHeight)
                        .clip(RoundedCornerShape(4.dp))
                        .drawWithContent {
                            drawRect(brush = brush, size = size)
                        }
                )
            }
        }
    }
}

/**
 * Skeleton for [MetricPill] — left accent bar with two lines of text.
 */
@Composable
fun ShimmerMetricPill(modifier: Modifier = Modifier) {
    val useDarkTheme = LocalRadafiqDarkTheme.current
    val brush = shimmerBrush()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(if (useDarkTheme) DarkSurface else LightSurface)
            .defaultMinSize(minHeight = 72.dp)
    ) {
        // Left accent bar shimmer
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp))
                .background(
                    if (useDarkTheme) Color(0xFF333344) else Color(0xFFDDD8D0)
                )
        )
        Column(
            modifier = Modifier.padding(start = 12.dp, top = 14.dp, bottom = 14.dp, end = 14.dp)
        ) {
            // Label line
            Box(
                modifier = Modifier
                    .width(80.dp)
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .drawWithContent {
                        drawRect(brush = brush, size = size)
                    }
            )
            Spacer(modifier = Modifier.height(10.dp))
            // Value line
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(18.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .drawWithContent {
                        drawRect(brush = brush, size = size)
                    }
            )
        }
    }
}

/**
 * Skeleton for [HeroPanel] — title, large amount, subtitle.
 */
@Composable
fun ShimmerHeroPanel(modifier: Modifier = Modifier) {
    val useDarkTheme = LocalRadafiqDarkTheme.current
    val brush = shimmerBrush()

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (useDarkTheme) DarkSurface else Color(0xFFEEF2FF),
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Title line
            Box(
                modifier = Modifier
                    .width(100.dp)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .drawWithContent {
                        drawRect(brush = brush, size = size)
                    }
            )
            Spacer(modifier = Modifier.height(8.dp))
            // Large amount line
            Box(
                modifier = Modifier
                    .width(200.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .drawWithContent {
                        drawRect(brush = brush, size = size)
                    }
            )
            Spacer(modifier = Modifier.height(6.dp))
            // Subtitle line
            Box(
                modifier = Modifier
                    .width(170.dp)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .drawWithContent {
                        drawRect(brush = brush, size = size)
                    }
            )
        }
    }
}

/**
 * Skeleton for [PageHeader] — title + subtitle shimmer lines.
 */
@Composable
fun ShimmerPageHeader(modifier: Modifier = Modifier) {
    val brush = shimmerBrush()
    Box(modifier = modifier.fillMaxWidth()) {
        Column {
            Box(
                modifier = Modifier
                    .width(160.dp)
                    .height(28.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .drawWithContent {
                        drawRect(brush = brush, size = size)
                    }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = 0.72f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .drawWithContent {
                        drawRect(brush = brush, size = size)
                    }
            )
        }
    }
}
