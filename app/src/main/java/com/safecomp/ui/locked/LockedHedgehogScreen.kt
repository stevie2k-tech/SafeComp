package com.safecomp.ui.locked

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.safecomp.security.StateReason
import com.safecomp.ui.components.description
import kotlin.math.cos
import kotlin.math.sin

/**
 * Shown instead of the vault (or a file) whenever the phone isn't confirmed INSIDE.
 * A hedgehog curls into a spiky ball when it feels unsafe, which is what the vault does too.
 */
@Composable
fun LockedHedgehogScreen(reason: StateReason?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        CurledHedgehog(
            Modifier
                .size(220.dp)
                .semantics { contentDescription = "A hedgehog curled up around a padlock" },
        )
        Text("The vault is locked", style = MaterialTheme.typography.headlineSmall)
        Text(
            "SafeComp only opens company files while this phone is in the office.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        reason?.let {
            Text(
                it.description(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private val Spikes = Color(0xFF5C3D22)
private val Body = Color(0xFF8B6240)
private val Face = Color(0xFFE9CFA6)
private val Nose = Color(0xFF2B1A0E)
private val LockGold = Color(0xFFE0A458)
private val LockDark = Color(0xFF8A5A1C)

@Composable
private fun CurledHedgehog(modifier: Modifier) {
    Canvas(modifier) {
        val r = size.minDimension * 0.33f
        val c = Offset(size.width / 2, size.height * 0.46f)

        // Shadow.
        drawOval(
            color = Color.Black.copy(alpha = 0.10f),
            topLeft = Offset(c.x - r * 1.1f, c.y + r * 0.95f),
            size = Size(r * 2.2f, r * 0.32f),
        )
        drawSpikes(c, r)
        drawCircle(Body, radius = r * 0.92f, center = c)

        // Face tucked in at the lower right, eyes closed.
        val face = Offset(c.x + r * 0.42f, c.y + r * 0.30f)
        drawCircle(Face, radius = r * 0.44f, center = face)
        drawOval(Face, topLeft = Offset(face.x + r * 0.10f, face.y - r * 0.02f), size = Size(r * 0.55f, r * 0.30f))
        drawCircle(Nose, radius = r * 0.075f, center = Offset(face.x + r * 0.64f, face.y + r * 0.12f))
        drawArc(
            color = Nose,
            startAngle = 20f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(face.x - r * 0.05f, face.y - r * 0.20f),
            size = Size(r * 0.22f, r * 0.14f),
            style = Stroke(width = r * 0.04f),
        )
        drawCircle(Color(0xFFF2A7A0).copy(alpha = 0.6f), radius = r * 0.07f, center = Offset(face.x - r * 0.12f, face.y + r * 0.12f))

        drawPadlock(Offset(c.x - r * 0.35f, c.y + r * 0.35f), r)
    }
}

private fun DrawScope.drawSpikes(c: Offset, r: Float) {
    val count = 30
    val half = Math.toRadians(360.0 / count / 1.6)
    for (i in 0 until count) {
        val deg = i * 360.0 / count
        if (deg in 5.0..80.0) continue // leave room for the face
        val a = Math.toRadians(deg)
        val tipLen = if (i % 2 == 0) 1.22f else 1.12f
        val path = Path().apply {
            moveTo(c.x + r * 0.8f * cos(a - half).toFloat(), c.y + r * 0.8f * sin(a - half).toFloat())
            lineTo(c.x + r * tipLen * cos(a).toFloat(), c.y + r * tipLen * sin(a).toFloat())
            lineTo(c.x + r * 0.8f * cos(a + half).toFloat(), c.y + r * 0.8f * sin(a + half).toFloat())
            close()
        }
        drawPath(path, Spikes)
    }
}

private fun DrawScope.drawPadlock(center: Offset, r: Float) {
    val w = r * 0.62f
    val h = r * 0.48f
    val bodyTopLeft = Offset(center.x - w / 2, center.y - h / 2)
    drawArc(
        color = LockDark,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(center.x - w * 0.32f, bodyTopLeft.y - w * 0.36f),
        size = Size(w * 0.64f, w * 0.72f),
        style = Stroke(width = r * 0.09f),
    )
    drawRoundRect(LockGold, topLeft = bodyTopLeft, size = Size(w, h), cornerRadius = CornerRadius(r * 0.08f))
    drawCircle(LockDark, radius = r * 0.07f, center = Offset(center.x, center.y - h * 0.05f))
    drawRect(LockDark, topLeft = Offset(center.x - r * 0.025f, center.y), size = Size(r * 0.05f, h * 0.28f))
}
