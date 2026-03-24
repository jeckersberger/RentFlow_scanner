package com.rentflow.scanner.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rentflow.scanner.data.repository.AuthRepository
import com.rentflow.scanner.data.repository.ScannerRepository
import com.rentflow.scanner.data.service.AppUpdateService
import com.rentflow.scanner.data.service.UpdateInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val userName: String = "",
    val pendingScanCount: Int = 0,
    val updateInfo: UpdateInfo? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val scannerRepository: ScannerRepository,
    private val updateService: AppUpdateService,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    init {
        viewModelScope.launch {
            authRepository.getCurrentUser().onSuccess { user ->
                _uiState.update { it.copy(userName = user.name) }
            }
        }
        viewModelScope.launch {
            scannerRepository.observePendingCount().collect { count ->
                _uiState.update { it.copy(pendingScanCount = count) }
            }
        }
        // Check for updates on start
        viewModelScope.launch {
            updateService.checkForUpdate()
        }
        viewModelScope.launch {
            updateService.updateAvailable.collect { info ->
                _uiState.update { it.copy(updateInfo = info) }
            }
        }
    }

    fun logout() {
        authRepository.logout()
    }

    fun downloadUpdate() {
        val info = _uiState.value.updateInfo ?: return
        updateService.downloadAndInstall(info)
    }
}
