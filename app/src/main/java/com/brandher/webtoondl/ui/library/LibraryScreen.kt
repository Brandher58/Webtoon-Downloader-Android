package com.brandher.webtoondl.ui.library

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.brandher.webtoondl.domain.model.OutputFormat
import com.brandher.webtoondl.domain.model.SeriesStats
import com.brandher.webtoondl.ui.common.FormatPickerContent
import kotlinx.coroutines.delay

@Composable
fun LibraryScreen(
    onOpenSeries: (String) -> Unit,
    onOpenReader: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val exportingId by viewModel.exporting.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingExport by remember { mutableStateOf<String?>(null) }
    var pendingFormat by remember { mutableStateOf<OutputFormat?>(null) }
    var showExportDialog by remember { mutableStateOf(false) }
    var seriesToDelete by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            } catch (_: Exception) {
                // Algunos proveedores no lo permiten; la copia funciona igualmente con el permiso temporal.
            }
            val seriesId = pendingExport
            val format = pendingFormat
            pendingExport = null
            pendingFormat = null
            if (seriesId != null && format != null) {
                viewModel.exportSeries(seriesId, format, uri)
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Biblioteca", style = MaterialTheme.typography.headlineSmall)
        }

        if (state.items.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "No hay series con descargas todavía",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "Descarga capítulos de una serie (desde Inicio o su ficha) y aparecerán aquí " +
                            "para leerlos sin conexión. Las series que solo abriste quedan en Recientes.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(state.items, key = { it.stats.series.id }) { item ->
                LibraryRow(
                    item = item,
                    exporting = exportingId == item.stats.series.id,
                    onOpen = { onOpenSeries(item.stats.series.id) },
                    onContinue = item.lastReadChapterId?.let { chapterId ->
                        { onOpenReader(chapterId) }
                    },
                    onExport = {
                        pendingExport = item.stats.series.id
                        showExportDialog = true
                    },
                    onDelete = { seriesToDelete = item.stats.series.id },
                )
            }
        }
    }

    when {
        message != null -> {
            val text = message
            LaunchedEffect(text) {
                delay(3_000)
                viewModel.consumeMessage()
            }
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Text(
                    text = text.orEmpty(),
                    modifier = Modifier
                        .padding(16.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            MaterialTheme.shapes.medium,
                        )
                        .padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }

    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Formato de exportación") },
            text = {
                FormatPickerContent { format ->
                    pendingFormat = format
                    showExportDialog = false
                    exportLauncher.launch(null)
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("Cancelar")
                }
            },
        )
    }

    seriesToDelete?.let { seriesId ->
        AlertDialog(
            onDismissRequest = { seriesToDelete = null },
            title = { Text("Eliminar serie") },
            text = { Text("Se borrarán del dispositivo la serie y sus capítulos descargados. ¿Continuar?") },
            confirmButton = {
                TextButton(onClick = {
                    seriesToDelete = null
                    viewModel.deleteSeries(seriesId)
                }) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { seriesToDelete = null }) {
                    Text("Cancelar")
                }
            },
        )
    }
}

@Composable
private fun LibraryRow(
    item: LibraryItem,
    exporting: Boolean,
    onOpen: () -> Unit,
    onContinue: (() -> Unit)?,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    val stats: SeriesStats = item.stats
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium),
                contentAlignment = Alignment.Center,
            ) {
                if (exporting) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stats.series.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                stats.series.author?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                val progress = if (stats.totalChapters > 0) {
                    (stats.downloadedChapters / stats.totalChapters.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "Descargados: ${stats.downloadedChapters}/${stats.totalChapters}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.End,
            ) {
                if (onContinue != null) {
                    Button(onClick = onContinue) {
                        Text("Continuar")
                    }
                } else {
                    OutlinedButton(onClick = onOpen) {
                        Text("Abrir")
                    }
                }
                IconButton(onClick = onExport, enabled = !exporting) {
                    Icon(Icons.Filled.Share, contentDescription = "Exportar")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Eliminar serie")
                }
            }
        }
    }
}