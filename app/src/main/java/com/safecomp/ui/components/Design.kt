package com.safecomp.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.safecomp.security.SecurityState
import com.safecomp.ui.theme.Sc

/** The colour that represents a security state everywhere in the app. */
fun SecurityState.color(): Color = when (this) {
    SecurityState.INSIDE -> Sc.Inside
    SecurityState.OUTSIDE -> Sc.Outside
    SecurityState.UNKNOWN -> Sc.Unknown
}

/** Frosted surface: a faint top-lit gradient and a hairline border that fades downwards. */
fun Modifier.glass(shape: Shape = RoundedCornerShape(24.dp), tint: Color? = null): Modifier = this
    .clip(shape)
    .background(
        Brush.verticalGradient(
            listOf(
                (tint ?: Color.White).copy(alpha = if (tint != null) 0.10f else 0.045f),
                (tint ?: Color.White).copy(alpha = if (tint != null) 0.04f else 0.015f),
            ),
        ),
    )
    .background(Sc.Surface.copy(alpha = 0.55f))
    .border(
        width = 1.dp,
        brush = Brush.verticalGradient(
            listOf(
                (tint ?: Color.White).copy(alpha = if (tint != null) 0.35f else 0.10f),
                (tint ?: Color.White).copy(alpha = if (tint != null) 0.10f else 0.03f),
            ),
        ),
        shape = shape,
    )

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    tint: Color? = null,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .glass(tint = tint)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(contentPadding),
        content = content,
    )
}

/** A tinted rounded square holding an icon. */
@Composable
fun IconBadge(icon: ImageVector, tint: Color, size: Dp = 44.dp, iconSize: Dp = 22.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.32f))
            .background(tint.copy(alpha = 0.14f))
            .border(1.dp, tint.copy(alpha = 0.28f), RoundedCornerShape(size * 0.32f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** "● INSIDE" style pill. */
@Composable
fun StatusPill(text: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.32f), CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Text(text, color = color, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

/** Small monospaced, upper-case section heading. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = Sc.TextTertiary,
        modifier = modifier,
    )
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    trailing: String? = null,
) {
    Row(
        modifier = modifier
            .height(56.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(CircleShape)
            .background(Brush.horizontalGradient(listOf(Sc.Accent, Color(0xFF7DD3FC))))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        icon?.let {
            Icon(it, contentDescription = null, tint = Sc.Bg, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, color = Sc.Bg, style = MaterialTheme.typography.labelLarge)
        trailing?.let {
            Spacer(Modifier.weight(1f))
            Text(it, color = Sc.Bg.copy(alpha = 0.6f), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    color: Color = Sc.TextPrimary,
) {
    Row(
        modifier = modifier
            .height(52.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.04f))
            .border(1.dp, Sc.Hairline, CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        icon?.let {
            Icon(it, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = color, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    tint: Color = Sc.TextPrimary,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .alpha(if (enabled) 1f else 0.35f)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Sc.Hairline, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(20.dp))
    }
}

/** Transparent top bar: optional back button, title (+ subtitle), and round action buttons. */
/**
 * Extra content every top bar shows under its title. Used for the debug-only DEMO MODE badge,
 * so it's always visible without covering buttons. Empty unless a screen tree provides one.
 */
val LocalTopBarAccessory = staticCompositionLocalOf<@Composable () -> Unit> { {} }

@Composable
fun ScTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier.fillMaxWidth().statusBarsPadding()) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        onBack?.let { CircleIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "Back", it) }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = Sc.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = Sc.TextSecondary, maxLines = 1)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
    }
    Box(Modifier.padding(start = 16.dp, bottom = 4.dp)) { LocalTopBarAccessory.current() }
    }
}

/**
 * Screen background: near-black with a soft glow at the top in the current state's colour.
 * The colour animates, so the whole app subtly shifts as the vault locks and unlocks.
 */
@Composable
fun ScreenBackground(glow: Color?, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val color by animateColorAsState(glow ?: Sc.Accent.copy(alpha = 0.5f), tween(900), label = "glow")
    Box(modifier.fillMaxSize().background(Sc.Bg)) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = 0.20f), Color.Transparent),
                    center = Offset(size.width / 2, -size.width * 0.15f),
                    radius = size.width * 1.1f,
                ),
                radius = size.width * 1.1f,
                center = Offset(size.width / 2, -size.width * 0.15f),
            )
        }
        content()
    }
}
