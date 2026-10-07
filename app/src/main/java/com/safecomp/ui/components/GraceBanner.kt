package com.safecomp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.safecomp.ui.theme.OutsideRed
import kotlinx.coroutines.flow.Flow

/**
 * "Leaving the office area: locking in 42 s". Shown on top of the vault and open files during
 * the grace period, so the user can finish reading or walk back in.
 */
@Composable
fun GraceBanner(secondsLeft: Flow<Long?>) {
    val left by secondsLeft.collectAsStateWithLifecycle(initialValue = null)
    val seconds = left ?: return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(OutsideRed)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Outlined.Timer, contentDescription = null, tint = Color.White)
        Text(
            "Leaving the office area. Locking in $seconds s.",
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
