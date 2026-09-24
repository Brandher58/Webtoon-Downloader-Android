package com.brandher.webtoondl.ui.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.brandher.webtoondl.domain.model.QueueItem
import com.brandher.webtoondl.domain.model.QueueStatus

@Composable
fun DownloadsScreen(
    modifier: Modifier = Modifier,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { QueueHeader(state, viewModel::pauseAll, viewModel::resumeAll, viewModel::cancelAll) }

        if (state.items.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("No hay descargas", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Selecciona capítulos en una serie y aparecerán aquí con su progreso.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(state.items, key = { it.chapter.id }) { item ->
                QueueRow(item = item, onCancel = { viewModel.cancel(item.chapter.id) })
            }
        }
    }
}

@Composable
private fun QueueHeader(state: DownloadsUiState, onPause: () -> Unit, onResume: () -> Unit, onCancelAll: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Cola de descargas", style = MaterialTheme.typography.titleMedium)

            val total = state.totalPages
            val global = if (total > 0) (state.donePages / total.toFloat()).coerceIn(0f, 1f) else 0f
            LinearProgressIndicator(
                progress = { global },
                modifier = Modifier.fillMaxWidth(),
            )
            val label = buildString {
                if (total > 0) append("Páginas: ${state.donePages}/$total")
                if (state.activeCount > 0) {
                    if (total > 0) append(" · ")
                    append("Descargando: ${state.activeCount}")
                }
                append(" · Cola: ${state.items.size}")
            }
            Text(text = label, style = MaterialTheme.typography.bodySmall)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPause, enabled = state.hasActive) { Text("Pausar todo") }
                OutlinedButton(
                    onClick = onResume,
                    enabled = state.items.any { it.status == QueueStatus.PAUSED || it.status == QueueStatus.FAILED },
                ) {
                    Text("Reanudar todo")
                }
                OutlinedButton(onClick = onCancelAll, enabled = state.items.isNotEmpty()) { Text("Cancelar todo") }
            }
        }
    }
}

@Composable
private fun QueueRow(item: QueueItem, onCancel: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.seriesTitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Cap. ${item.chapter.number} · ${item.chapter.title}",
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                val total = item.pagesTotal
                if (total != null && total > 0) {
                    LinearProgressIndicator(
                        progress = { (item.pagesDone / total.toFloat()).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                    )
                    Text(
                        text = "Página ${item.pagesDone.coerceAtMost(total)}/$total · ${item.statusLabel()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (item.status == QueueStatus.FAILED) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            text = " Fallido",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                } else {
                    Text(
                        text = item.statusLabel(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            val cancellable = item.status == QueueStatus.QUEUED ||
                item.status == QueueStatus.DOWNLOADING ||
                item.status == QueueStatus.FAILED
            if (cancellable) {
                IconButton(onClick = onCancel) {
                    Icon(Icons.Filled.Close, contentDescription = "Cancelar descarga")
                }
            }
        }
    }
}

private fun QueueItem.statusLabel(): String =
    when (status) {
        QueueStatus.NONE -> "Sin descargar"
        QueueStatus.QUEUED -> "En cola"
        QueueStatus.DOWNLOADING -> "Descargando…"
        QueueStatus.PAUSED -> "Pausado"
        QueueStatus.FAILED -> "Fallido"
        QueueStatus.CANCELLED -> "Cancelado"
        QueueStatus.COMPLETED -> "Completado"
    }