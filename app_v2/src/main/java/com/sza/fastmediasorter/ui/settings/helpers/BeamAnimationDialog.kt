package com.sza.fastmediasorter.ui.settings.helpers

import android.app.Dialog
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.common.compose.FastMediaSorterComposeTheme
import com.sza.fastmediasorter.ui.settings.WearSyncUiState
import com.sza.fastmediasorter.ui.settings.WearSyncViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay

@AndroidEntryPoint
class BeamAnimationDialog : DialogFragment() {

    private val viewModel: WearSyncViewModel by viewModels({ requireParentFragment().also { } },
        factoryProducer = { defaultViewModelProviderFactory })

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val composeView = ComposeView(requireContext()).apply {
            setContent {
                FastMediaSorterComposeTheme {
                    BeamDialogContent(
                        viewModel = viewModel,
                        onDismiss = { dismissAllowingStateLoss() }
                    )
                }
            }
        }
        return Dialog(requireContext()).apply {
            setContentView(composeView)
            window?.setBackgroundDrawableResource(android.R.color.transparent)
            setCanceledOnTouchOutside(false)
        }
    }

    override fun onResume() {
        super.onResume()
        dialog?.window?.setLayout(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
}

@Composable
private fun BeamDialogContent(
    viewModel: WearSyncViewModel,
    onDismiss: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(state) {
        if (state is WearSyncUiState.Success) {
            val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                context.getSystemService<VibratorManager>()?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService<Vibrator>()
            }
            // VibrationEffect.createOneShot is API 26+; fall back to deprecated overload on older devices (legacy@23).
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(120L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(120L)
            }
            delay(2000)
            onDismiss()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (state) {
            WearSyncUiState.Idle -> {
                Text(stringResource(R.string.wear_sync_ready), style = MaterialTheme.typography.titleMedium)
            }
            WearSyncUiState.Sending -> {
                PulsingBeamAnimation()
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.wear_sync_sending), style = MaterialTheme.typography.bodyMedium)
            }
            is WearSyncUiState.Success -> {
                val s = state as WearSyncUiState.Success
                Text("✓", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.wear_sync_success, s.sent),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
            is WearSyncUiState.Error -> {
                val e = state as WearSyncUiState.Error
                Text("✗", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                Text(e.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { viewModel.startPush() }) {
                    Text(stringResource(R.string.retry))
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { viewModel.reset(); onDismiss() }) {
                    Text(stringResource(R.string.cancel))
                }
            }
            WearSyncUiState.NothingSelected -> {
                Text(
                    stringResource(R.string.wear_sync_nothing_selected),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = { viewModel.reset(); onDismiss() }) {
                    Text(stringResource(R.string.close))
                }
            }
            WearSyncUiState.SettingsPushed -> {
                // Settings push completed - dialog auto-dismisses via LaunchedEffect above
                Text("✓", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun PulsingBeamAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "beam")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "pulse"
    )
    val color = MaterialTheme.colorScheme.primary
    Box(modifier = Modifier.height(120.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2
            val cy = size.height / 2
            for (i in 0..2) {
                val phase = (pulse + i / 3f) % 1f
                drawCircle(
                    color = color.copy(alpha = (1f - phase) * 0.5f),
                    radius = 20.dp.toPx() + phase * 50.dp.toPx(),
                    center = androidx.compose.ui.geometry.Offset(cx, cy),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }
        CircularProgressIndicator(modifier = Modifier.padding(8.dp))
    }
}
