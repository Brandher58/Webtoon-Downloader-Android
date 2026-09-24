package com.brandher.webtoondl.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.brandher.webtoondl.ui.downloads.DownloadsScreen
import com.brandher.webtoondl.ui.home.HomeScreen
import com.brandher.webtoondl.ui.library.LibraryScreen
import com.brandher.webtoondl.ui.settings.SettingsScreen

@Composable
fun WebtoonDLApp() {
    val navController = rememberNavController()
    val entries by navController.currentBackStackEntryAsState()
    val currentRoute = entries?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                Destination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Home.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Destination.Home.route) { HomeScreen() }
            composable(Destination.Library.route) { LibraryScreen() }
            composable(Destination.Downloads.route) { DownloadsScreen() }
            composable(Destination.Settings.route) { SettingsScreen() }
        }
    }
}