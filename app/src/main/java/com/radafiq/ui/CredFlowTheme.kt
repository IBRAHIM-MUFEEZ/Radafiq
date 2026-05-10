package com.radafiq.ui

import android.app.Activity
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

// ── Salt & Pepper Monochrome Theme ────────────────────────────────────────
private val PepperWhite = Color(0xFFFFFFFF)
private val CardGray = Color(0xFFD4D4D4)
private val BorderGray = Color(0xFFB3B3B3)
private val TextDark = Color(0xFF2B2B2B)
private val MutedGray = Color(0xFF808080)
private val SubtleGray = Color(0xFFE8E8E8)

private val DarkBg = Color(0xFF2B2B2B)
private val DarkSurface = Color(0xFF3C3C3C)
private val DarkBorder = Color(0xFF555555)
private val DarkText = Color(0xFFF5F5F5)
private val DarkMuted = Color(0xFFAAAAAA)

private val RadafiqLightColors: ColorScheme = lightColorScheme(
    primary = TextDark,
    onPrimary = PepperWhite,
    primaryContainer = CardGray,
    onPrimaryContainer = TextDark,
    secondary = TextDark,
    onSecondary = PepperWhite,
    secondaryContainer = CardGray,
    onSecondaryContainer = TextDark,
    tertiary = TextDark,
    onTertiary = PepperWhite,
    error = TextDark,
    onError = PepperWhite,
    background = PepperWhite,
    onBackground = TextDark,
    surface = PepperWhite,
    onSurface = TextDark,
    surfaceVariant = SubtleGray,
    onSurfaceVariant = MutedGray,
    outline = BorderGray
)

private val RadafiqDarkColors: ColorScheme = darkColorScheme(
    primary = DarkText,
    onPrimary = DarkBg,
    primaryContainer = DarkSurface,
    onPrimaryContainer = DarkText,
    secondary = DarkText,
    onSecondary = DarkBg,
    secondaryContainer = DarkSurface,
    onSecondaryContainer = DarkText,
    tertiary = DarkText,
    onTertiary = DarkBg,
    error = DarkText,
    onError = DarkBg,
    background = DarkBg,
    onBackground = DarkText,
    surface = DarkSurface,
    onSurface = DarkText,
    surfaceVariant = DarkSurface,
    onSurfaceVariant = DarkMuted,
    outline = DarkBorder
)

private val LocalRadafiqDarkTheme = staticCompositionLocalOf { true }
private val AppSans = FontFamily.SansSerif
private val BaseTypography = Typography()

private fun appStyle(
    base: TextStyle,
    weight: FontWeight,
    letterSpacing: Float = 0f,
    lineHeight: Float = 1.3f
): TextStyle {
    return base.copy(
        fontFamily = AppSans,
        fontWeight = weight,
        letterSpacing = letterSpacing.sp,
        lineHeight = (base.fontSize.value * lineHeight).sp
    )
}

private val RadafiqTypography = Typography(
    headlineLarge = appStyle(BaseTypography.headlineLarge, FontWeight.Bold, -0.02f, 1.2f),
    headlineMedium = appStyle(BaseTypography.headlineMedium, FontWeight.Bold, -0.01f, 1.25f),
    headlineSmall = appStyle(BaseTypography.headlineSmall, FontWeight.SemiBold, 0f, 1.3f),
    titleLarge = appStyle(BaseTypography.titleLarge, FontWeight.SemiBold, -0.01f, 1.3f),
    titleMedium = appStyle(BaseTypography.titleMedium, FontWeight.SemiBold, 0f, 1.35f),
    titleSmall = appStyle(BaseTypography.titleSmall, FontWeight.Medium, 0.01f, 1.4f),
    bodyLarge = appStyle(BaseTypography.bodyLarge, FontWeight.Normal, 0f, 1.6f),
    bodyMedium = appStyle(BaseTypography.bodyMedium, FontWeight.Normal, 0f, 1.5f),
    bodySmall = appStyle(BaseTypography.bodySmall, FontWeight.Normal, 0f, 1.4f),
    labelLarge = appStyle(BaseTypography.labelLarge, FontWeight.SemiBold, 0.02f, 1.3f),
    labelMedium = appStyle(BaseTypography.labelMedium, FontWeight.Medium, 0.02f, 1.3f),
    labelSmall = appStyle(BaseTypography.labelSmall, FontWeight.Medium, 0.03f, 1.3f)
)

private val RadafiqShapes = Shapes(
    extraSmall = RoundedCornerShape(14.dp),
    small = RoundedCornerShape(18.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(30.dp)
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
            val statusBarColor = if (useDarkTheme) DarkBg else PepperWhite
            @Suppress("DEPRECATION")
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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Transparent,
            content = content
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
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (useDarkTheme) DarkSurface else CardGray,
            contentColor = if (useDarkTheme) DarkText else TextDark
        ),
        border = BorderStroke(
            width = 0.5.dp,
            color = if (useDarkTheme) DarkBorder else BorderGray
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (useDarkTheme) 0.dp else 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
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
    val bgColor = if (useDarkTheme) DarkSurface else CardGray
    val textColor = if (useDarkTheme) DarkText else TextDark
    val mutedColor = if (useDarkTheme) DarkMuted else MutedGray

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(0.5.dp, if (useDarkTheme) DarkBorder else BorderGray, RoundedCornerShape(12.dp))
            .padding(horizontal = 22.dp, vertical = 24.dp)
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = mutedColor
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = amount,
                style = MaterialTheme.typography.headlineLarge,
                color = textColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = mutedColor,
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
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(0.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .defaultMinSize(minHeight = 60.dp)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
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
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp)
        )
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
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(0.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
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
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f), RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
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
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.70f))
                .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.30f), RoundedCornerShape(24.dp))
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

@Composable
fun AnimatedCounter(value: String, style: TextStyle = MaterialTheme.typography.headlineLarge, color: Color = MaterialTheme.colorScheme.onSurface) {
    AnimatedContent(
        targetState = value,
        transitionSpec = {
            fadeIn(animationSpec = tween(300)) togetherWith
            fadeOut(animationSpec = tween(200))
        },
        label = "counter"
    ) { v ->
        Text(text = v, style = style, color = color, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun AnimatedCard(
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    content: @Composable () -> Unit
) {
    val useDarkTheme = LocalRadafiqDarkTheme.current

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (useDarkTheme) DarkSurface else CardGray,
            contentColor = if (useDarkTheme) DarkText else TextDark
        ),
        border = BorderStroke(
            width = 0.5.dp,
            color = if (useDarkTheme) DarkBorder else BorderGray
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (useDarkTheme) 0.dp else 1.dp)
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

fun formatMoney(value: Double): String {
    val formatter = java.text.NumberFormat.getNumberInstance(java.util.Locale("en", "IN"))
    formatter.minimumFractionDigits = 2
    formatter.maximumFractionDigits = 2
    return "\u20B9${formatter.format(value)}"
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
    return TextDark
}

fun warningColor(): Color = TextDark

fun dangerColor(): Color = TextDark
