package com.example.lightluxmeter.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.lightluxmeter.R
import com.example.lightluxmeter.ui.screens.FilmGalleryScreen
import com.example.lightluxmeter.ui.screens.LiveMeterScreen
import com.example.lightluxmeter.ui.screens.SettingsScreen

sealed class Screen(val route: String, val titleResId: Int, val icon: ImageVector) {
    object LiveMeter : Screen("live_meter", R.string.nav_meter, Icons.Filled.PlayArrow)
    object FilmGallery : Screen("film_gallery", R.string.nav_films, Icons.AutoMirrored.Filled.List)
    object Settings : Screen("settings", R.string.nav_settings, Icons.Filled.Settings)
}

val items = listOf(Screen.LiveMeter, Screen.FilmGallery, Screen.Settings)

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    Scaffold(bottomBar = { AppBottomNavigation(navController) }) { innerPadding ->
        NavHost(
                navController = navController,
                startDestination = Screen.LiveMeter.route,
                modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.LiveMeter.route) { LiveMeterScreen() }
            composable(Screen.FilmGallery.route) { FilmGalleryScreen() }
            composable(Screen.Settings.route) { SettingsScreen() }
        }
    }
}

@Composable
fun AppBottomNavigation(navController: NavHostController) {
    NavigationBar {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route

        items.forEach { screen ->
            val titleStr = stringResource(screen.titleResId)
            NavigationBarItem(
                    icon = { Icon(screen.icon, contentDescription = titleStr) },
                    label = { Text(titleStr) },
                    selected = currentRoute == screen.route,
                    onClick = {
                        navController.navigate(screen.route) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
            )
        }
    }
}
