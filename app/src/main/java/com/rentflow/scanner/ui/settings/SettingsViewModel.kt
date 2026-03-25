package com.rentflow.scanner.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rentflow.scanner.RentFlowScannerApp
import com.rentflow.scanner.data.preferences.SettingsDataStore
import com.rentflow.scanner.data.service.AppUpdateService
import com.rentflow.scanner.data.service.DownloadState
import com.rentflow.scanner.data.service.UpdateInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val serverUrl: String = "",
    val language: String = "de",
    val scanMode: String = "barcode",
    val saved: Boolean = false,
    val updateInfo: UpdateInfo? = null,
    val isCheckingUpdate: Boolean = false,
    val downloadState: DownloadState = DownloadState.IDLE,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val updateService: AppUpdateService,
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
        viewModelScope.launch {
            settingsDataStore.scanMode.collect { mode ->
                _uiState.update { it.copy(scanMode = mode) }
            }
        }
        viewModelScope.launch {
            updateService.updateAvailable.collect { info ->
                _uiState.update { it.copy(updateInfo = info) }
            }
        }
        viewModelScope.launch {
            updateService.downloadState.collect { state ->
                _uiState.update { it.copy(downloadState = state) }
            }
        }
    }

    fun onServerUrlChange(url: String) {
        _uiState.update { it.copy(serverUrl = url, saved = false) }
    }

    fun onLanguageChange(lang: String) {
        _uiState.update { it.copy(language = lang, saved = false) }
    }

    fun onScanModeChange(mode: String) {
        _uiState.update { it.copy(scanMode = mode, saved = false) }
    }

    fun save() {
        viewModelScope.launch {
            settingsDataStore.setServerUrl(_uiState.value.serverUrl)
            settingsDataStore.setLanguage(_uiState.value.language)
            settingsDataStore.setScanMode(_uiState.value.scanMode)
            // Apply language change immediately
            RentFlowScannerApp.applyLanguage(_uiState.value.language)
            _uiState.update { it.copy(saved = true) }
        }
    }

    fun checkForUpdate() {
        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingUpdate = true) }
            updateService.checkForUpdate()
            _uiState.update { it.copy(isCheckingUpdate = false) }
        }
    }

    fun downloadUpdate() {
        val info = _uiState.value.updateInfo ?: return
        updateService.downloadAndInstall(info)
    }
}
