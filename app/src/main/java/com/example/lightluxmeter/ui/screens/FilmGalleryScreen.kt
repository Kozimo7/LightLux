package com.example.lightluxmeter.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.lightluxmeter.R
import com.example.lightluxmeter.ui.viewmodels.FilmGalleryViewModel
import com.example.lightluxmeter.ui.viewmodels.GalleryUiState

@Composable
fun FilmGalleryScreen(viewModel: FilmGalleryViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var searchQuery by remember { mutableStateOf("Kodak Portra 400") }

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
                                AsyncImage(
                                        model = photo.urls.small,
                                        contentDescription = photo.description
                                                        ?: photo.alt_description ?: "Film Photo",
                                        modifier = Modifier.aspectRatio(1f).fillMaxWidth(),
                                        contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                }
                is GalleryUiState.Error -> {
                    Text(
                            text = "Error: ${state.message}",
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }
}
