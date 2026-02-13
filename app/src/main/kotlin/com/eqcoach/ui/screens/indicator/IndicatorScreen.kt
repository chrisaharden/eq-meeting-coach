package com.eqcoach.ui.screens.indicator

import android.app.Activity
import android.graphics.BitmapFactory
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eqcoach.config.AppConfig
import com.eqcoach.viewmodel.SessionViewModel

@Composable
fun IndicatorScreen(
    sessionViewModel: SessionViewModel,
    onStopSession: () -> Unit,
) {
    val verdict by sessionViewModel.currentVerdict.collectAsState()
    val errorMessage by sessionViewModel.errorMessage.collectAsState()
    val debugInfo by sessionViewModel.debugInfo.collectAsState()
    val lastFrame by sessionViewModel.lastFrame.collectAsState()
    val audioLevel by sessionViewModel.audioLevel.collectAsState()
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
        // Debug overlay — top-start corner
        debugInfo?.let { info ->
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .background(
                        Color.Black.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(8.dp),
                    )
                    .padding(12.dp),
            ) {
                Text(
                    text = "Verdict: ${verdict.name}  |  Fused: ${String.format("%.3f", info.fused_score)}",
                    color = Color.White, fontSize = 13.sp, fontFamily = FontFamily.Monospace,
                )
                Text(
                    text = "Face: ${info.facial_dominant}",
                    color = Color.White, fontSize = 13.sp, fontFamily = FontFamily.Monospace,
                )
                info.facial_emotions.entries
                    .sortedByDescending { it.value }
                    .take(4)
                    .forEach { (emotion, score) ->
                        Text(
                            text = "  $emotion: ${String.format("%.1f%%", score * 100)}",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                Text(
                    text = "Speech: ${info.speech_dominant}",
                    color = Color.White, fontSize = 13.sp, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    text = "Audio RMS: ${String.format("%.3f", audioLevel)}",
                    color = Color.White, fontSize = 13.sp, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 4.dp),
                )

                // Camera preview thumbnail
                lastFrame?.let { jpegBytes ->
                    val bitmap = remember(jpegBytes) {
                        BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
                    }
                    if (bitmap != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Camera (${bitmap.width}x${bitmap.height}):",
                            color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                        )
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Camera preview",
                            modifier = Modifier
                                .size(120.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(4.dp)),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            }
        }

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
