package com.arflix.tv.ui.startup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * StartupViewModel - Handles parallel loading during splash screen
 * Pre-loads all data needed for instant home screen display
 */
data class StartupState(
    val isLoading: Boolean = true,
    val isReady: Boolean = false,
    val loadingProgress: Float = 0f,
    val loadingMessage: String = "Starting...",
    val isAuthenticated: Boolean = false,
    val error: String? = null
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class StartupViewModel @Inject constructor() : ViewModel() {
    private val _state = MutableStateFlow(StartupState())
    val state: StateFlow<StartupState> = _state.asStateFlow()

    init {
        startParallelLoading()
    }

    private fun startParallelLoading() {
        viewModelScope.launch {
            try {
                // App always opens on profile selection first, so defer heavy
                // home network preloading to HomeViewModel after profile is chosen.
                updateProgress(0.7f, "Preparing...")

                _state.value = _state.value.copy(
                    isLoading = false,
                    isReady = true,
                    isAuthenticated = false
                )

                updateProgress(1.0f, "Ready!")

            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e

                _state.value = _state.value.copy(
                    isLoading = false,
                    isReady = true,
                    error = e.message
                )
            }
        }
    }

    private fun updateProgress(progress: Float, message: String) {
        _state.value = _state.value.copy(
            loadingProgress = progress,
            loadingMessage = message
        )
    }


}
