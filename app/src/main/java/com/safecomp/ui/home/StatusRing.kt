package com.safecomp.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.safecomp.ui.theme.Sc
import kotlin.math.cos
import kotlin.math.sin

enum class RingMode {
    /** State settled: full ring, slow breathing glow. */
    STEADY,
    /** Waiting for a fix: a short arc spins. */
    CHECKING,
    /** Dwell in progress: the ring fills clockwise towards "unlocked". */
    FILLING,
    /** Grace period: the ring drains towards "locked". */
    DRAINING,
}

@Composable
fun StatusRing(
    color: Color,
    mode: RingMode,
    progress: Float,
    icon: ImageVector,
    label: String,
    caption: String,
    modifier: Modifier = Modifier,
) {
    val tint by animateColorAsState(color, tween(700), label = "ringColor")
    val animatedProgress by animateFloatAsState(progress.coerceIn(0f, 1f), tween(900), label = "ringProgress")
    val infinite = rememberInfiniteTransition(label = "ring")
    // Kept as State and read only inside graphicsLayer {}: each animation frame then only
    // updates a layer property (alpha / rotation), with no recomposition and no redraw.
    val breathe = infinite.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400), RepeatMode.Reverse),
        label = "breathe",
    )

    Box(modifier.size(280.dp), contentAlignment = Alignment.Center) {
        // Layer 1: glow, drawn once; breathing = layer alpha.
        Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = breathe.value }) {
            val r = size.minDimension / 2f * 0.74f
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(tint.copy(alpha = 0.32f), tint.copy(alpha = 0.06f), Color.Transparent),
                    center = center,
                    radius = r * 1.55f,
                ),
                radius = r * 1.55f,
                center = center,
            )
        }

        // Layer 2: dial, track, progress and inner disc. Redrawn only when progress/colour change.
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val r = size.minDimension / 2f * 0.74f
            val stroke = 9.dp.toPx()

            val ticks = 72
            val lit = when (mode) {
                RingMode.FILLING, RingMode.DRAINING -> (animatedProgress * ticks).toInt()
                RingMode.STEADY -> ticks
                RingMode.CHECKING -> 0
            }
            for (i in 0 until ticks) {
                val a = Math.toRadians(i * 360.0 / ticks - 90.0)
                val major = i % 6 == 0
                val inner = r + 16.dp.toPx()
                val outer = inner + (if (major) 9.dp else 5.dp).toPx()
                drawLine(
                    color = if (i < lit) tint.copy(alpha = if (mode == RingMode.STEADY) 0.35f else 0.85f) else Sc.TextTertiary.copy(alpha = 0.35f),
                    start = Offset(c.x + inner * cos(a).toFloat(), c.y + inner * sin(a).toFloat()),
                    end = Offset(c.x + outer * cos(a).toFloat(), c.y + outer * sin(a).toFloat()),
                    strokeWidth = (if (major) 2.dp else 1.dp).toPx(),
                    cap = StrokeCap.Round,
                )
            }

            drawCircle(Sc.Hairline, radius = r, center = c, style = Stroke(stroke))

            when (mode) {
                RingMode.STEADY -> drawCircle(
                    brush = Brush.sweepGradient(listOf(tint.copy(alpha = 0.55f), tint, tint.copy(alpha = 0.55f)), c),
                    radius = r,
                    center = c,
                    style = Stroke(stroke),
                )
                RingMode.FILLING, RingMode.DRAINING -> drawArc(
                    color = tint,
                    startAngle = -90f,
                    sweepAngle = 360f * animatedProgress,
                    useCenter = false,
                    topLeft = Offset(c.x - r, c.y - r),
                    size = Size(r * 2, r * 2),
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
                RingMode.CHECKING -> Unit // drawn by the spinner layer
            }

            val inner = r - stroke * 1.4f
            drawCircle(Sc.Surface, radius = inner, center = c)
            drawCircle(
                brush = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.06f), Color.Transparent),
                    startY = c.y - inner,
                    endY = c.y + inner,
                ),
                radius = inner,
                center = c,
            )
        }

        // Layer 3: spinner, only while checking; spinning = layer rotation.
        if (mode == RingMode.CHECKING) {
            val spin = infinite.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
                label = "spin",
            )
            Canvas(Modifier.fillMaxSize().graphicsLayer { rotationZ = spin.value }) {
                val c = center
                val r = size.minDimension / 2f * 0.74f
                drawArc(
                    brush = Brush.sweepGradient(listOf(Color.Transparent, tint), c),
                    startAngle = 0f,
                    sweepAngle = 110f,
                    useCenter = false,
                    topLeft = Offset(c.x - r, c.y - r),
                    size = Size(r * 2, r * 2),
                    style = Stroke(9.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(34.dp))
            Text(
                label,
                style = MaterialTheme.typography.headlineSmall.copy(letterSpacing = 0.14.em, fontWeight = FontWeight.ExtraBold),
                color = Sc.TextPrimary,
            )
            Text(caption, style = MaterialTheme.typography.labelMedium, color = tint)
        }
    }
}
