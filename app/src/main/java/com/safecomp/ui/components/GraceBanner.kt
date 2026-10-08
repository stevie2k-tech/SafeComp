package com.safecomp.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.safecomp.ui.theme.Sc
import kotlinx.coroutines.flow.Flow

/**
 * "Leaving the office area: locking in 42 s". Shown on top of the vault and open files during
 * the grace period, so the user can finish reading or walk back in.
 */
@Composable
fun GraceBanner(secondsLeft: Flow<Long?>) {
    val left by secondsLeft.collectAsStateWithLifecycle(initialValue = null)
    AnimatedVisibility(
        visible = left != null,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        val shape = RoundedCornerShape(16.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .clip(shape)
                .background(Sc.Outside.copy(alpha = 0.14f))
                .border(1.dp, Sc.Outside.copy(alpha = 0.35f), shape)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Outlined.Timer, contentDescription = null, tint = Sc.Outside, modifier = Modifier.size(20.dp))
            Text("Leaving the office area", style = MaterialTheme.typography.titleSmall, color = Sc.TextPrimary)
            Spacer(Modifier.weight(1f))
            Text("LOCK IN ${left ?: 0}S", style = MaterialTheme.typography.labelMedium, color = Sc.Outside)
        }
    }
}
