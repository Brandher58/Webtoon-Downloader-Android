package com.brandher.webtoondl.ui.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import java.io.File
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce

@Composable
fun ReaderScreen(
    onBack: () -> Unit,
    onOpenChapter: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalContext.current.findActivity()
    var anyZoomed by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val controller = activity?.window?.let {
            WindowInsetsControllerCompat(it, it.decorView)
        }
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        when {
            !state.loaded -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White,
            )

            state.files.isEmpty() -> Text(
                text = "Este capítulo no está descargado.",
                color = Color.White,
                modifier = Modifier.align(Alignment.Center),
            )

            else -> {
                val listState = rememberLazyListState()
                var restored by remember { mutableStateOf(false) }

                LaunchedEffect(state.savedPageIndex, state.files.size) {
                    if (!restored && state.files.isNotEmpty()) {
                        val index = state.savedPageIndex.coerceIn(0, state.files.lastIndex)
                        if (index > 0) listState.scrollToItem(index)
                        restored = true
                    }
                }

                LaunchedEffect(listState) {
                    snapshotFlow {
                        val info = listState.layoutInfo
                        val first = info.visibleItemsInfo.firstOrNull()
                        Triple(info.totalItemsCount, first?.index ?: 0, first?.offset ?: 0)
                    }.debounce(600).collectLatest { (total, index, offset) ->
                        val lastIndex = state.files.lastIndex
                        if (lastIndex == -1 || (index <= lastIndex && !anyZoomed)) {
                            viewModel.savePosition(index.coerceAtLeast(0), offset.toFloat())
                        }
                    }
                }

                LazyColumn(
                    state = listState,
                    userScrollEnabled = !anyZoomed,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(
                        count = state.files.size,
                        key = { state.files[it].absolutePath },
                    ) { index ->
                        ZoomablePage(
                            file = state.files[index],
                            onZoomChanged = { anyZoomed = it },
                        )
                    }

                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            val next = state.nextChapter()
                            if (next != null) {
                                Button(
                                    onClick = { onOpenChapter(next.id) },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("Siguiente · ${next.title.ifBlank { "Cap. ${next.number}" }}")
                                }
                            } else {
                                Text(
                                    text = "— Fin de la serie —",
                                    color = Color.Gray,
                                )
                            }
                        }
                    }
                }
            }
        }

        ReaderTopBar(
            seriesTitle = state.seriesTitle,
            chapterNumber = state.chapter?.number,
            position = "${state.chapters.indexOfFirst { it.id == state.chapter?.id } + 1}/${state.chapters.size}",
            onBack = onBack,
        )
    }
}

@Composable
private fun ReaderTopBar(
    seriesTitle: String,
    chapterNumber: Int?,
    position: String,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0x99000000))
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = seriesTitle,
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Cap. ${chapterNumber ?: ""} · $position",
                color = Color.LightGray,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ZoomablePage(
    file: File,
    onZoomChanged: (Boolean) -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var itemSize by remember { mutableStateOf(IntSize.Zero) }
    val zoomed = scale > 1.02f

    LaunchedEffect(zoomed) { onZoomChanged(zoomed) }

    val tapModifier = Modifier.pointerInput(Unit) {
        detectTapGestures(
            onDoubleTap = {
                scale = if (scale > 1f) 1f else 2.5f
                offset = Offset.Zero
            },
        )
    }

    val transformModifier = if (zoomed) {
        Modifier.pointerInput(Unit) {
            detectTransformGestures { _, pan, zoom, _ ->
                val newScale = (scale * zoom).coerceIn(1f, 8f)
                if (newScale <= 1.01f) {
                    scale = 1f
                    offset = Offset.Zero
                } else {
                    offset = clampOffset(offset + pan, itemSize, newScale)
                    scale = newScale
                }
            }
        }
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { itemSize = it }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            }
            .clipToBounds()
            .then(tapModifier)
            .then(transformModifier),
    ) {
        AsyncImage(
            model = file,
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun clampOffset(offset: Offset, size: IntSize, scale: Float): Offset {
    val maxX = ((scale - 1f) * size.width / 2f).coerceAtLeast(0f)
    val maxY = ((scale - 1f) * size.height / 2f).coerceAtLeast(0f)
    return Offset(
        offset.x.coerceIn(-maxX, maxX),
        offset.y.coerceIn(-maxY, maxY),
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}