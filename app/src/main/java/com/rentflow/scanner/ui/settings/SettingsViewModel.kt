package com.rentflow.scanner.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rentflow.scanner.data.preferences.SettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val serverUrl: String = "",
    val language: String = "de",
    val saved: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState

    init {
        viewModelScope.launch {
            settingsDataStore.serverUrl.collect { url ->
                _uiState.update { it.copy(serverUrl = url) }
            }
        }
        viewModelScope.launch {
            settingsDataStore.language.collect { lang ->
                _uiState.update { it.copy(language = lang) }
            }
        }
    }

    fun onServerUrlChange(url: String) {
        _uiState.update { it.copy(serverUrl = url, saved = false) }
    }

    fun onLanguageChange(lang: String) {
        _uiState.update { it.copy(language = lang, saved = false) }
    }

    fun save() {
        viewModelScope.launch {
            settingsDataStore.setServerUrl(_uiState.value.serverUrl)
            settingsDataStore.setLanguage(_uiState.value.language)
            _uiState.update { it.copy(saved = true) }
        }
    }
}
