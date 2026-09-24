package com.brandher.webtoondl.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Ajustes", style = MaterialTheme.typography.headlineSmall)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Descargas", style = MaterialTheme.typography.titleMedium)

                SettingSlider(
                    title = "Capítulos en paralelo: ${state.chapterConcurrency}",
                    value = state.chapterConcurrency,
                    range = 1..4,
                    onChange = viewModel::setChapterConcurrency,
                )
                SettingSlider(
                    title = "Páginas en paralelo: ${state.pageConcurrency}",
                    value = state.pageConcurrency,
                    range = 1..8,
                    onChange = viewModel::setPageConcurrency,
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Almacenamiento", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Los capítulos se guardan dentro del almacenamiento de la app " +
                        "(no requiere permisos). Se borran al desinstalar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                context.getExternalFilesDir(null)?.let {
                    Text(
                        text = it.absolutePath,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Webtoon Downloader · v0.1.0",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingSlider(
    title: String,
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
) {
    Column {
        Text(title, style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first) - 1,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}