package com.brandher.webtoondl.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.brandher.webtoondl.ui.downloads.DownloadsScreen
import com.brandher.webtoondl.ui.home.HomeScreen
import com.brandher.webtoondl.ui.library.LibraryScreen
import com.brandher.webtoondl.ui.reader.ReaderScreen
import com.brandher.webtoondl.ui.series.SeriesScreen
import com.brandher.webtoondl.ui.settings.SettingsScreen

object Routes {
    const val SERIES = "series/{seriesId}"
    const val READER = "reader/{chapterId}"

    fun series(seriesId: String): String = "series/$seriesId"

    fun reader(chapterId: String): String = "reader/$chapterId"
}

@Composable
fun WebtoonDLApp() {
    val navController = rememberNavController()
    val entries by navController.currentBackStackEntryAsState()
    val currentRoute = entries?.destination?.route

    // Se incrementa cada vez que el usuario pulsa la pestaña Inicio para volver arriba.
    var homeScrollRequest by remember { mutableIntStateOf(0) }

    // La barra inferior solo se muestra en las pestañas principales.
    val showBottomBar = Destination.entries.any { it.route == currentRoute }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    Destination.entries.forEach { destination ->
                        val selectedTab = currentRoute == destination.route
                        NavigationBarItem(
                            selected = selectedTab,
                            onClick = {
                                if (selectedTab) {
                                    // Ya está en la pestaña: Inicio sube al tope.
                                    if (destination == Destination.Home) homeScrollRequest++
                                    return@NavigationBarItem
                                }
                                if (destination == Destination.Home) {
                                    // Sale de las pantallas empujadas (ficha/lector) y vuelve al Home.
                                    navController.popBackStack(
                                        navController.graph.findStartDestination().id,
                                        false,
                                    )
                                    homeScrollRequest++
                                } else {
                                    navController.navigate(destination.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(destination.icon, contentDescription = destination.label) },
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Home.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Destination.Home.route) {
                HomeScreen(
                    onOpenSeries = { seriesId -> navController.navigate(Routes.series(seriesId)) },
                    scrollRequest = homeScrollRequest,
                )
            }
            composable(Destination.Library.route) {
                LibraryScreen(
                    onOpenSeries = { seriesId -> navController.navigate(Routes.series(seriesId)) },
                    onOpenReader = { chapterId -> navController.navigate(Routes.reader(chapterId)) },
                )
            }
            composable(Destination.Downloads.route) { DownloadsScreen() }
            composable(Destination.Settings.route) { SettingsScreen() }

            composable(
                route = Routes.SERIES,
                arguments = listOf(navArgument("seriesId") { type = NavType.StringType }),
            ) {
                SeriesScreen(
                    onBack = { navController.popBackStack() },
                    onOpenReader = { chapterId -> navController.navigate(Routes.reader(chapterId)) },
                )
            }

            composable(
                route = Routes.READER,
                arguments = listOf(navArgument("chapterId") { type = NavType.StringType }),
            ) {
                ReaderScreen(
                    onBack = { navController.popBackStack() },
                    onOpenChapter = { chapterId ->
                        // Reemplaza la entrada del lector para que Atrás no recorra todos los capítulos.
                        navController.navigate(Routes.reader(chapterId)) {
                            popUpTo(Routes.READER) { inclusive = true }
                        }
                    },
                )
            }
        }
    }
}