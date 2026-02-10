package com.eqcoach.ui.navigation

sealed class Screen(val route: String) {
    data object Permission : Screen("permission")
    data object Home : Screen("home")
    data object Indicator : Screen("indicator")
}
