package com.safecomp.ui.locked

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safecomp.security.StateReason
import com.safecomp.ui.components.StatusPill
import com.safecomp.ui.components.description
import com.safecomp.ui.theme.MonoFamily
import com.safecomp.ui.theme.Sc
import kotlin.math.cos
import kotlin.math.sin

/**
 * Shown instead of the vault (or a file) whenever the phone isn't confirmed INSIDE.
 * A hedgehog curls into a spiky ball when it feels unsafe, which is what the vault does too.
 */
@Composable
fun LockedHedgehogScreen(reason: StateReason?, modifier: Modifier = Modifier) {
    val tint = when (reason) {
        StateReason.OUTSIDE_FIX, StateReason.OUTSIDE_GEOFENCE -> Sc.Outside
        else -> Sc.Unknown
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    ) {
        SleepingHedgehog(
            glow = tint,
            modifier = Modifier
                .size(250.dp)
                .semantics { contentDescription = "A hedgehog curled up around a padlock, asleep" },
        )
        Text("Vault locked", style = MaterialTheme.typography.headlineMedium, color = Sc.TextPrimary)
        Text(
            "SafeComp only opens company files while this phone is in the office.",
            style = MaterialTheme.typography.bodyLarge,
            color = Sc.TextSecondary,
            textAlign = TextAlign.Center,
        )
        reason?.let {
            StatusPill(it.description().removeSuffix(".").uppercase().take(42), tint)
        }
    }
}

private object Hog {
    val QuillBase = Color(0xFF4A2C17)
    val QuillMid = Color(0xFF85552F)
    val QuillTip = Color(0xFFEBD6B4)
    val BodyLight = Color(0xFFA9774C)
    val BodyDark = Color(0xFF6A4227)
    val Face = Color(0xFFF3DCB8)
    val FaceShade = Color(0xFFD9B98E)
    val Nose = Color(0xFF24160D)
    val Blush = Color(0xFFF59E9E)
    val GoldLight = Color(0xFFFFD98A)
    val GoldDark = Color(0xFFD9922E)
    val Steel = Color(0xFFC9D3DE)
    val SteelDark = Color(0xFF7D8896)
}

@Composable
private fun SleepingHedgehog(glow: Color, modifier: Modifier) {
    // Entrance: quills bristle out, the lock settles with a little wobble.
    val bristle = remember { Animatable(0.72f) }
    val lockTilt = remember { Animatable(-14f) }
    LaunchedEffect(Unit) {
        bristle.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow))
    }
    LaunchedEffect(Unit) {
        lockTilt.animateTo(0f, spring(dampingRatio = 0.3f, stiffness = Spring.StiffnessMediumLow))
    }
    val infinite = rememberInfiniteTransition(label = "hog")
    val breathe by infinite.animateFloat(1f, 1.03f, infiniteRepeatable(tween(2600), RepeatMode.Reverse), label = "breathe")
    val zzz by infinite.animateFloat(0f, 1f, infiniteRepeatable(tween(3200)), label = "zzz")

    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val u = size.minDimension / 100f
            val c = Offset(size.width * 0.48f, size.height * 0.52f)
            val r = 30f * u

            // Glow + ground shadow.
            drawCircle(
                Brush.radialGradient(listOf(glow.copy(alpha = 0.28f), Color.Transparent), c, r * 2.0f),
                radius = r * 2.0f,
                center = c,
            )
            drawOval(
                Color.Black.copy(alpha = 0.45f),
                topLeft = Offset(c.x - r * 1.15f, c.y + r * 1.02f),
                size = Size(r * 2.3f, r * 0.34f),
            )

            scale(scaleX = 1f, scaleY = breathe, pivot = Offset(c.x, c.y + r)) {
                // Back row of quills (longer, darker), then the front row.
                drawQuills(c, r, bristle.value, length = 1.42f, count = 26, phase = 0f, dim = 0.75f)
                drawQuills(c, r, bristle.value, length = 1.26f, count = 24, phase = 0.5f, dim = 1f)

                // Body.
                drawCircle(
                    Brush.radialGradient(listOf(Hog.BodyLight, Hog.BodyDark), Offset(c.x - r * 0.3f, c.y - r * 0.4f), r * 1.3f),
                    radius = r * 0.9f,
                    center = c,
                )

                drawFace(c, r)
                drawPaws(c, r)
            }
            rotate(lockTilt.value, pivot = Offset(c.x - r * 0.15f, c.y + r * 0.2f)) {
                drawPadlock(Offset(c.x - r * 0.15f, c.y + r * 0.5f), r)
            }
        }
        // Sleeping "z"s floating up from the face.
        listOf(0f, 0.33f, 0.66f).forEachIndexed { i, offset ->
            val t = (zzz + offset) % 1f
            Text(
                "z",
                fontFamily = MonoFamily,
                fontSize = (12 + i * 4).sp,
                color = Sc.TextSecondary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-28 - i * 10).dp, y = (78 - t * 48).dp)
                    .alpha(if (t < 0.15f) t / 0.15f else 1f - t),
            )
        }
    }
}

private fun DrawScope.drawQuills(c: Offset, r: Float, bristle: Float, length: Float, count: Int, phase: Float, dim: Float) {
    // Quills cover the back and top. The lower right is left open for the face.
    val start = 95.0
    val end = 395.0
    for (i in 0 until count) {
        val deg = start + (end - start) * (i + phase) / count
        val norm = ((deg % 360) + 360) % 360
        if (norm in 0.0..58.0) continue
        val a = Math.toRadians(deg)
        val jitter = 1f + 0.06f * sin(i * 2.7f)
        val len = r * (0.78f + (length - 0.78f) * bristle) * jitter
        val half = 0.10
        val base1 = Offset(c.x + r * 0.78f * cos(a - half).toFloat(), c.y + r * 0.78f * sin(a - half).toFloat())
        val base2 = Offset(c.x + r * 0.78f * cos(a + half).toFloat(), c.y + r * 0.78f * sin(a + half).toFloat())
        val tip = Offset(c.x + len * cos(a + 0.05).toFloat(), c.y + len * sin(a + 0.05).toFloat())
        val ctrl1 = Offset(c.x + len * 0.86f * cos(a - 0.07).toFloat(), c.y + len * 0.86f * sin(a - 0.07).toFloat())
        val ctrl2 = Offset(c.x + len * 0.86f * cos(a + 0.12).toFloat(), c.y + len * 0.86f * sin(a + 0.12).toFloat())
        val path = Path().apply {
            moveTo(base1.x, base1.y)
            quadraticTo(ctrl1.x, ctrl1.y, tip.x, tip.y)
            quadraticTo(ctrl2.x, ctrl2.y, base2.x, base2.y)
            close()
        }
        drawPath(
            path,
            Brush.linearGradient(
                listOf(Hog.QuillBase.copy(alpha = dim), Hog.QuillMid.copy(alpha = dim), Hog.QuillTip.copy(alpha = dim)),
                start = Offset((base1.x + base2.x) / 2, (base1.y + base2.y) / 2),
                end = tip,
            ),
        )
    }
}

private fun DrawScope.drawFace(c: Offset, r: Float) {
    val face = Offset(c.x + r * 0.46f, c.y + r * 0.26f)
    // Head and snout.
    drawCircle(
        Brush.radialGradient(listOf(Hog.Face, Hog.FaceShade), Offset(face.x - r * 0.1f, face.y - r * 0.15f), r * 0.6f),
        radius = r * 0.46f,
        center = face,
    )
    val snout = Path().apply {
        moveTo(face.x + r * 0.1f, face.y - r * 0.2f)
        quadraticTo(face.x + r * 0.78f, face.y - r * 0.02f, face.x + r * 0.74f, face.y + r * 0.12f)
        quadraticTo(face.x + r * 0.5f, face.y + r * 0.3f, face.x + r * 0.05f, face.y + r * 0.26f)
        close()
    }
    drawPath(snout, Brush.linearGradient(listOf(Hog.Face, Hog.FaceShade), face, Offset(face.x + r * 0.8f, face.y + r * 0.2f)))
    // Ear.
    drawCircle(Hog.FaceShade, radius = r * 0.11f, center = Offset(face.x - r * 0.12f, face.y - r * 0.4f))
    drawCircle(Hog.Blush.copy(alpha = 0.7f), radius = r * 0.055f, center = Offset(face.x - r * 0.12f, face.y - r * 0.4f))
    // Nose, with a highlight.
    val nose = Offset(face.x + r * 0.74f, face.y + r * 0.04f)
    drawCircle(Hog.Nose, radius = r * 0.085f, center = nose)
    drawCircle(Color.White.copy(alpha = 0.7f), radius = r * 0.025f, center = Offset(nose.x - r * 0.025f, nose.y - r * 0.03f))
    // Closed, sleeping eye.
    drawArc(
        color = Hog.Nose,
        startAngle = 10f,
        sweepAngle = 160f,
        useCenter = false,
        topLeft = Offset(face.x + r * 0.12f, face.y - r * 0.16f),
        size = Size(r * 0.22f, r * 0.14f),
        style = Stroke(width = r * 0.04f, cap = StrokeCap.Round),
    )
    // Blush.
    drawCircle(Hog.Blush.copy(alpha = 0.45f), radius = r * 0.09f, center = Offset(face.x + r * 0.08f, face.y + r * 0.1f))
}

private fun DrawScope.drawPaws(c: Offset, r: Float) {
    listOf(-0.42f, 0.05f).forEach { dx ->
        drawOval(
            Hog.FaceShade,
            topLeft = Offset(c.x + r * dx, c.y + r * 0.78f),
            size = Size(r * 0.3f, r * 0.2f),
        )
    }
}

private fun DrawScope.drawPadlock(center: Offset, r: Float) {
    val w = r * 0.66f
    val h = r * 0.52f
    val top = center.y - h / 2
    // Shackle.
    drawArc(
        brush = Brush.verticalGradient(listOf(Hog.Steel, Hog.SteelDark), startY = top - w * 0.5f, endY = top),
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(center.x - w * 0.3f, top - w * 0.36f),
        size = Size(w * 0.6f, w * 0.72f),
        style = Stroke(width = r * 0.1f, cap = StrokeCap.Round),
    )
    // Body.
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(Hog.GoldLight, Hog.GoldDark), startY = top, endY = top + h),
        topLeft = Offset(center.x - w / 2, top),
        size = Size(w, h),
        cornerRadius = CornerRadius(r * 0.12f),
    )
    drawRoundRect(
        color = Color.White.copy(alpha = 0.35f),
        topLeft = Offset(center.x - w / 2 + r * 0.05f, top + r * 0.04f),
        size = Size(w - r * 0.1f, h * 0.18f),
        cornerRadius = CornerRadius(r * 0.06f),
    )
    // Keyhole.
    drawCircle(Hog.Nose.copy(alpha = 0.8f), radius = r * 0.07f, center = Offset(center.x, center.y - h * 0.04f))
    drawRoundRect(
        Hog.Nose.copy(alpha = 0.8f),
        topLeft = Offset(center.x - r * 0.03f, center.y),
        size = Size(r * 0.06f, h * 0.26f),
        cornerRadius = CornerRadius(r * 0.03f),
    )
}
