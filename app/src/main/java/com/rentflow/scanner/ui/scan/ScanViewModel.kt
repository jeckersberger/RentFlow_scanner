package com.rentflow.scanner.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rentflow.scanner.data.hardware.HardwareScanner
import com.rentflow.scanner.data.repository.ScannerRepository
import com.rentflow.scanner.domain.model.Equipment
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ScanUiState(
    val equipment: Equipment? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val lastBarcode: String? = null,
)

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val scannerRepository: ScannerRepository,
    private val hardwareScanner: HardwareScanner,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState

    init {
        viewModelScope.launch {
            hardwareScanner.barcodeScanEvents.collect { event ->
                onBarcodeScanned(event.barcode)
            }
        }
    }

    fun onBarcodeScanned(barcode: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, lastBarcode = barcode) }
            scannerRepository.resolveBarcode(barcode).fold(
                onSuccess = { equipment ->
                    _uiState.update { it.copy(isLoading = false, equipment = equipment) }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                },
            )
        }
    }

    fun clearResult() {
        _uiState.update { ScanUiState() }
    }
}
