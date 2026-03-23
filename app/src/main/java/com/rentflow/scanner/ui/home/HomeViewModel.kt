package com.rentflow.scanner.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rentflow.scanner.data.repository.AuthRepository
import com.rentflow.scanner.data.repository.ScannerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val userName: String = "",
    val pendingScanCount: Int = 0,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val scannerRepository: ScannerRepository,
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
    }

    fun logout() {
        authRepository.logout()
    }
}
