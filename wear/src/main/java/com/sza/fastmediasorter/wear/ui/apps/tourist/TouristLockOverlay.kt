package com.sza.fastmediasorter.wear.ui.apps.tourist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R

/**
 * S3015: Fullscreen touch-lock overlay preventing accidental screen interactions during activity.
 * Unlocks only via long-press gesture.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TouristLockOverlay(
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xD9000000))
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { /* Intercept normal tap */ },
                onLongClick = onUnlock,
            )
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "🔒",
                fontSize = 36.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.wear_tourist_screen_locked),
                style = MaterialTheme.typography.title3,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.wear_tourist_hold_to_unlock),
                style = MaterialTheme.typography.caption2,
                color = Color(0xFFFFCC00),
                textAlign = TextAlign.Center,
            )
        }
    }
}
