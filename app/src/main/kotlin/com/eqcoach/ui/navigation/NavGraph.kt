package com.eqcoach.ui.navigation

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.eqcoach.service.CaptureServiceImpl
import com.eqcoach.ui.screens.home.HomeScreen
import com.eqcoach.ui.screens.indicator.IndicatorScreen
import com.eqcoach.ui.screens.permission.PermissionScreen
import com.eqcoach.viewmodel.SessionViewModel

@Composable
fun EQCoachNavGraph(
    navController: NavHostController,
    sessionViewModel: SessionViewModel = viewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Wire CaptureServiceImpl into the ViewModel (once)
    val captureService = androidx.compose.runtime.remember { CaptureServiceImpl(context) }
    sessionViewModel.setCaptureService(captureService)

    // Re-check permissions every time the app resumes
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val cameraGranted = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
                val audioGranted = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED

                val currentRoute = navController.currentDestination?.route
                if ((!cameraGranted || !audioGranted) && currentRoute != Screen.Permission.route) {
                    // Permissions were revoked — stop session and redirect
                    sessionViewModel.stopSession()
                    navController.navigate(Screen.Permission.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Permission.route,
    ) {
        composable(Screen.Permission.route) {
            PermissionScreen(
                onPermissionsGranted = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Permission.route) { inclusive = true }
                    }
                },
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(
                onStartSession = {
                    sessionViewModel.startSession()
                    navController.navigate(Screen.Indicator.route)
                },
            )
        }

        composable(Screen.Indicator.route) {
            IndicatorScreen(
                sessionViewModel = sessionViewModel,
                onStopSession = {
                    sessionViewModel.stopSession()
                    navController.popBackStack(Screen.Home.route, inclusive = false)
                },
            )
        }
    }
}
