package com.example.lightluxmeter.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lightluxmeter.network.UnsplashApi
import com.example.lightluxmeter.network.UnsplashPhoto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class GalleryUiState {
    object Idle : GalleryUiState()
    object Loading : GalleryUiState()
    data class Success(val photos: List<UnsplashPhoto>) : GalleryUiState()
    data class Error(val message: String) : GalleryUiState()
}

class FilmGalleryViewModel : ViewModel() {
    private val api = UnsplashApi.create()

    private val _uiState = MutableStateFlow<GalleryUiState>(GalleryUiState.Idle)
    val uiState: StateFlow<GalleryUiState> = _uiState

    init {
        searchFilms("Kodak Portra 400")
    }

    fun searchFilms(filmType: String) {
        viewModelScope.launch {
            _uiState.value = GalleryUiState.Loading
            try {
                val response = api.searchPhotos(query = "$filmType film photography")
                val photos = response.results
                _uiState.value = GalleryUiState.Success(photos)
            } catch (e: Exception) {
                _uiState.value = GalleryUiState.Error(e.message ?: "Unknown error occurred")
            }
        }
    }
}
