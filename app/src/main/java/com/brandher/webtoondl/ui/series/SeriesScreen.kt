package com.brandher.webtoondl.ui.series

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.brandher.webtoondl.domain.model.ChapterItem
import com.brandher.webtoondl.domain.model.OutputFormat
import com.brandher.webtoondl.domain.model.QueueStatus
import com.brandher.webtoondl.domain.model.Series
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesScreen(
    onBack: () -> Unit,
    onOpenReader: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SeriesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showDeleteSeriesDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    when (val s = state) {
                        is SeriesUiState.Loaded -> Text(s.series.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        else -> Text("Webtoon")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteSeriesDialog = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Eliminar serie")
                    }
                },
            )
        },
    ) { innerPadding ->
        when (val s = state) {
            SeriesUiState.Loading -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                }
            }

            is SeriesUiState.Loaded -> {
                val loaded = s
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .imePadding(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item { SeriesHeader(loaded.series) }
                    item { DownloadPanel(vm = viewModel, state = loaded) }

                    item {
                        Text(
                            text = "Capítulos (${loaded.totalChapters})",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                        )
                    }

                    if (loaded.items.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    "No se pudieron cargar los capítulos. Es posible que el servidor " +
                                        "esté limitando las peticiones.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                OutlinedButton(onClick = { viewModel.retryChapters() }) {
                                    Text("Reintentar")
                                }
                            }
                        }
                    } else {
                        items(loaded.items, key = { it.chapter.id }) { item ->
                            ChapterRow(
                                item = item,
                                selected = item.chapter.id in loaded.selected,
                                onToggle = { viewModel.toggleChapter(item.chapter.id) },
                                onOpenReader = { onOpenReader(item.chapter.id) },
                                onDeleteChapter = { viewModel.deleteChapter(item.chapter.id) },
                            )
                        }
                    }
                }
            }
        }
    }
if (showDeleteSeriesDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteSeriesDialog = false },
            title = { Text("Eliminar serie") },
            text = { Text("Se borrarán del dispositivo la serie y sus capítulos descargados. ¿Continuar?") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteSeriesDialog = false
                    viewModel.deleteSeries()
                    onBack()
                }) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSeriesDialog = false }) {
                    Text("Cancelar")
                }
            },
        )
    }
}

@Composable
private fun DownloadPanel(vm: SeriesViewModel, state: SeriesUiState.Loaded) {
    var fromText by rememberSaveable { mutableStateOf("") }
    var toText by rememberSaveable { mutableStateOf("") }
    var showExportDialog by rememberSaveable { mutableStateOf(false) }
    var pendingFormat by rememberSaveable { mutableStateOf<OutputFormat?>(null) }
    var pendingSeriesId by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val exporting by vm.exporting.collectAsStateWithLifecycle()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            val format = pendingFormat
            val seriesId = pendingSeriesId
            pendingFormat = null
            pendingSeriesId = null
            if (format != null && seriesId != null) {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                } catch (_: Exception) {
                    // Permiso persistente opcional; la copia funciona con el temporal.
                }
                vm.exportSeries(format, uri)
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Descargar para leer", style = MaterialTheme.typography.titleSmall)
            Text(
                text = "Se descargan las imágenes dentro de la app para leerlas sin conexión desde la Biblioteca.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = fromText,
                    onValueChange = { fromText = it.filter(Char::isDigit).take(4) },
                    label = { Text("Desde") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = toText,
                    onValueChange = { toText = it.filter(Char::isDigit).take(4) },
                    label = { Text("Hasta") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        val from = fromText.toIntOrNull()
                        val to = toText.toIntOrNull()
                        if (from != null && to != null) vm.downloadRange(from, to)
                    },
                    enabled = fromText.isNotBlank() && toText.isNotBlank(),
                ) {
                    Text("Rango")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val allSelected = state.totalChapters > 0 && state.selectedCount == state.totalChapters
                OutlinedButton(
                    onClick = { vm.toggleSelectAll() },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (allSelected) "Quitar selección" else "Seleccionar todo")
                }
                Button(
                    onClick = { vm.downloadAll() },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Descargar todo")
                }
            }

            Button(
                onClick = { vm.downloadSelected() },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.selectedCount > 0,
            ) {
                Text("Descargar selección (${state.selectedCount})")
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = vm::pauseAll) {
                    Icon(Icons.Filled.PauseCircle, contentDescription = null)
                    Text("Pausar", modifier = Modifier.padding(start = 4.dp))
                }
                OutlinedButton(onClick = vm::resumeAll) {
                    Text("Reanudar")
                }
            }

            Text("Exportar archivos", style = MaterialTheme.typography.titleSmall)
            Text(
                text = "Copia las imágenes o genera CBZ/PDF a la carpeta que elijas (solo capítulos descargados).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = {
                    pendingSeriesId = state.series.id
                    showExportDialog = true
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.downloadedCount > 0 && !exporting,
            ) {
                if (exporting) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                } else {
                    Text("Exportar descargados (${state.downloadedCount})")
                }
            }

            val notice by vm.notice.collectAsStateWithLifecycle()
            LaunchedEffect(notice) {
                if (notice != null) {
                    delay(3_000)
                    vm.consumeNotice()
                }
            }
            notice?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
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
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutputFormat.entries.forEach { format ->
                        Row(Modifier.clickable {
                            pendingFormat = format
                            showExportDialog = false
                            exportLauncher.launch(null)
                        }) {
                            Text(
                                when (format) {
                                    OutputFormat.IMAGES -> "Imágenes (carpetas)"
                                    OutputFormat.CBZ -> "CBZ (cada capítulo en un archivo)"
                                    OutputFormat.PDF -> "PDF (cada capítulo en un archivo)"
                                },
                            )
                        }
                    }
                    Text(
                        text = "Después elige dónde guardarlo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
}

@Composable
private fun SeriesHeader(series: Series) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(text = series.title, style = MaterialTheme.typography.headlineSmall)
            val meta = listOfNotNull(series.author, series.genre).joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            series.summary?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ChapterRow(
    item: ChapterItem,
    selected: Boolean,
    onToggle: () -> Unit,
    onOpenReader: () -> Unit,
    onDeleteChapter: () -> Unit,
) {
    Card(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = selected,
                onCheckedChange = { onToggle() },
                enabled = item.status != QueueStatus.COMPLETED,
            )
            Text(
                text = item.chapter.number.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 12.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.chapter.title.ifBlank { "Capítulo ${item.chapter.number}" },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                item.chapter.date?.let {
                    Text(
                        text = DateFormat.getDateInstance().format(Date(it)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            StatusBadge(item)
            if (item.status == QueueStatus.COMPLETED) {
                TextButton(onClick = onOpenReader) {
                    Text("Leer")
                }
                IconButton(onClick = onDeleteChapter) {
                    Icon(Icons.Filled.Delete, contentDescription = "Eliminar capítulo")
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(item: ChapterItem) {
    when (item.status) {
        QueueStatus.NONE -> Unit
        QueueStatus.COMPLETED -> AssistChip(
            onClick = {},
            label = { Text(item.pagesTotal?.let { "✓ $it" } ?: "✓") },
            leadingIcon = { Icon(Icons.Filled.Check, contentDescription = "Descargado") },
        )

        QueueStatus.DOWNLOADING -> AssistChip(
            onClick = {},
            label = { Text(item.progressText()) },
            leadingIcon = {
                CircularProgressIndicator(
                    modifier = Modifier.padding(end = 4.dp),
                    strokeWidth = 2.dp,
                )
            },
        )

        QueueStatus.QUEUED -> AssistChip(onClick = {}, label = { Text("En cola") })
        QueueStatus.PAUSED -> AssistChip(onClick = {}, label = { Text(item.progressText()) })
        QueueStatus.FAILED -> AssistChip(
            onClick = {},
            label = { Text(item.progressText()) },
            leadingIcon = { Icon(Icons.Filled.ErrorOutline, contentDescription = "Error") },
        )

        QueueStatus.CANCELLED -> Unit
    }
}

private fun ChapterItem.progressText(): String {
    val total = pagesTotal ?: return "…"
    return if (pagesDone >= total) "$total" else "$pagesDone/$total"
}