package com.eqcoach.ui.screens.indicator

import android.app.Activity
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.eqcoach.config.AppConfig
import com.eqcoach.viewmodel.SessionViewModel

@Composable
fun IndicatorScreen(
    sessionViewModel: SessionViewModel,
    onStopSession: () -> Unit,
) {
    val verdict by sessionViewModel.currentVerdict.collectAsState()
    val errorMessage by sessionViewModel.errorMessage.collectAsState()
    val animatedColor by animateColorAsState(
        targetValue = verdict.toColor(),
        animationSpec = tween(durationMillis = AppConfig.COLOR_TRANSITION_MS),
        label = "verdictColor",
    )

    val context = LocalContext.current
    val activity = context as? Activity

    LaunchedEffect(errorMessage) {
        errorMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            sessionViewModel.clearError()
        }
    }

    // Acquire wake lock and set max brightness while this screen is displayed
    DisposableEffect(Unit) {
        activity?.window?.apply {
            addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            val params = attributes
            params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
            attributes = params
        }

        onDispose {
            activity?.window?.apply {
                clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                val params = attributes
                params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                attributes = params
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(animatedColor),
    ) {
        // Stop button — bottom-end corner, semi-transparent
        IconButton(
            onClick = onStopSession,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .size(56.dp)
                .clip(CircleShape),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = Color.Black.copy(alpha = 0.3f),
                contentColor = Color.White.copy(alpha = 0.8f),
            ),
        ) {
            Icon(
                imageVector = Icons.Filled.Stop,
                contentDescription = "Stop session",
                modifier = Modifier.size(32.dp),
            )
        }
    }
}
