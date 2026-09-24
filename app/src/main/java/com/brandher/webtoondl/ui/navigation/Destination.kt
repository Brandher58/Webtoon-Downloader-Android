package com.brandher.webtoondl.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

enum class Destination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    Home(route = "home", label = "Inicio", icon = Icons.Filled.Home),
    Library(route = "library", label = "Biblioteca", icon = Icons.Filled.Collections),
    Downloads(route = "downloads", label = "Descargas", icon = Icons.Filled.Download),
    Settings(route = "settings", label = "Ajustes", icon = Icons.Filled.Settings),
}