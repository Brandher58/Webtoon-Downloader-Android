package com.brandher.webtoondl.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.brandher.webtoondl.domain.model.OutputFormat

/** Contenido del diálogo de exportación: elección del formato. */
@Composable
fun FormatPickerContent(onPick: (OutputFormat) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutputFormat.entries.forEach { format ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(format) }
                    .padding(vertical = 6.dp),
            ) {
                Text(
                    text = when (format) {
                        OutputFormat.IMAGES -> "Imágenes (carpetas)"
                        OutputFormat.CBZ -> "CBZ (cada capítulo en un archivo)"
                        OutputFormat.PDF -> "PDF (cada capítulo en un archivo)"
                    },
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
        Text(
            text = "Usa tus capítulos descargados; si falta alguno, lo descarga del web sobre la marcha. " +
                "Después elige dónde guardarlo.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}