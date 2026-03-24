package com.rentflow.scanner.ui.adhoc

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

data class AdHocUiState(
    val scannedEquipment: Equipment? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val showConfirmDialog: Boolean = false,
    val successMessage: String? = null,
)

@HiltViewModel
class AdHocBookingViewModel @Inject constructor(
    private val scannerRepository: ScannerRepository,
    private val hardwareScanner: HardwareScanner,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AdHocUiState())
    val uiState: StateFlow<AdHocUiState> = _uiState

    init {
        viewModelScope.launch {
            hardwareScanner.barcodeScanEvents.collect { event ->
                onBarcodeScanned(event.barcode)
            }
        }
    }

    private fun onBarcodeScanned(barcode: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, successMessage = null) }
            scannerRepository.resolveBarcode(barcode).fold(
                onSuccess = { equipment ->
                    _uiState.update { it.copy(isLoading = false, scannedEquipment = equipment) }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                },
            )
        }
    }

    fun requestBooking() {
        _uiState.update { it.copy(showConfirmDialog = true) }
    }

    fun dismissConfirm() {
        _uiState.update { it.copy(showConfirmDialog = false) }
    }

    fun confirmBooking() {
        val equipment = _uiState.value.scannedEquipment ?: return
        _uiState.update { it.copy(showConfirmDialog = false, isLoading = true) }
        viewModelScope.launch {
            scannerRepository.adHocBooking(equipment.id, equipment.barcode).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            scannedEquipment = null,
                            successMessage = equipment.name,
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                },
            )
        }
    }

    fun clearResult() {
        _uiState.update { AdHocUiState() }
    }
}
