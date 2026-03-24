package com.rentflow.scanner.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rentflow.scanner.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val loginSuccess: Boolean = false,
    val showQrScanner: Boolean = false,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState

    fun onEmailChange(email: String) {
        _uiState.update { it.copy(email = email, error = null) }
    }

    fun onPasswordChange(password: String) {
        _uiState.update { it.copy(password = password, error = null) }
    }

    fun onLoginClick() {
        val state = _uiState.value
        if (state.email.isBlank() || state.password.isBlank()) {
            _uiState.update { it.copy(error = "Email und Passwort erforderlich") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = authRepository.login(state.email, state.password)
            _uiState.update {
                if (result.isSuccess) {
                    it.copy(isLoading = false, loginSuccess = true)
                } else {
                    it.copy(isLoading = false, error = result.exceptionOrNull()?.message ?: "Login fehlgeschlagen")
                }
            }
        }
    }

    fun openQrScanner() {
        _uiState.update { it.copy(showQrScanner = true, error = null) }
    }

    fun closeQrScanner() {
        _uiState.update { it.copy(showQrScanner = false) }
    }

    fun onQrCodeScanned(qrToken: String) {
        _uiState.update { it.copy(showQrScanner = false, isLoading = true, error = null) }
        viewModelScope.launch {
            val result = authRepository.qrLogin(qrToken)
            _uiState.update {
                if (result.isSuccess) {
                    it.copy(isLoading = false, loginSuccess = true)
                } else {
                    it.copy(isLoading = false, error = result.exceptionOrNull()?.message ?: "QR-Login fehlgeschlagen")
                }
            }
        }
    }
}
