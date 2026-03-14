package com.example.lightluxmeter.ui.screens

import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.lightluxmeter.R
import com.example.lightluxmeter.network.UnsplashPhoto
import com.example.lightluxmeter.ui.viewmodels.FilmGalleryViewModel
import com.example.lightluxmeter.ui.viewmodels.GalleryUiState

@Composable
fun FilmGalleryScreen(viewModel: FilmGalleryViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var searchQuery by remember { mutableStateOf("Kodak Portra 400") }
    var selectedPhoto by remember { mutableStateOf<UnsplashPhoto?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                        text = stringResource(R.string.nav_films),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                        text = stringResource(R.string.film_gallery_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 16.dp)
                )

                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.weight(1f),
                            label = { Text(stringResource(R.string.search_film_hint)) },
                            singleLine = true
                    )
                    Button(onClick = { viewModel.searchFilms(searchQuery) }) {
                        Text(stringResource(R.string.search))
                    }
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            when (val state = uiState) {
                is GalleryUiState.Idle, is GalleryUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is GalleryUiState.Success -> {
                    if (state.photos.isEmpty()) {
                        Text(
                                stringResource(R.string.no_photos_found),
                                modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(bottom = 16.dp)
                        ) {
                            items(state.photos) { photo ->
                                Card(
                                    modifier = Modifier
                                        .aspectRatio(1f)
                                        .fillMaxWidth(),
                                    onClick = { selectedPhoto = photo }
                                ) {
                                    AsyncImage(
                                            model = photo.urls.small,
                                            contentDescription = photo.description
                                                            ?: photo.altDescription ?: stringResource(R.string.film_photo_desc),
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                    )
                                }
                            }
                        }
                    }
                }
                is GalleryUiState.Error -> {
                    Text(
                            text = stringResource(R.string.error_prefix, state.message),
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }

    selectedPhoto?.let { photo ->
        ZoomableImageDialog(
            photo = photo,
            onDismiss = { selectedPhoto = null }
        )
    }
}

@Composable
fun ZoomableImageDialog(photo: UnsplashPhoto, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = true
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black.copy(alpha = 0.9f)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                var scale by remember { mutableStateOf(1f) }
                var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

                AsyncImage(
                    model = photo.urls.regular,
                    contentDescription = photo.description ?: photo.altDescription,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 3f)
                                offset += pan
                            }
                        }
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        ),
                    contentScale = ContentScale.Fit
                )

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.close),
                        tint = Color.White
                    )
                }
            }
        }
    }
}
