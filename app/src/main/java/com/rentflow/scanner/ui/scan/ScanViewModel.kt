package com.rentflow.scanner.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rentflow.scanner.data.hardware.HardwareScanner
import com.rentflow.scanner.data.hardware.RfidReadEvent
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
    val rfidTags: List<RfidReadEvent> = emptyList(),
    val isRfidScanning: Boolean = false,
)

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val scannerRepository: ScannerRepository,
    private val hardwareScanner: HardwareScanner,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState

    init {
        // Listen for barcode scan events
        viewModelScope.launch {
            hardwareScanner.barcodeScanEvents.collect { event ->
                onBarcodeScanned(event.barcode)
            }
        }
        // Listen for RFID read events
        viewModelScope.launch {
            hardwareScanner.rfidReadEvents.collect { event ->
                _uiState.update { state ->
                    val existing = state.rfidTags.indexOfFirst { it.epc == event.epc }
                    val updated = if (existing >= 0) {
                        state.rfidTags.toMutableList().also { it[existing] = event }
                    } else {
                        state.rfidTags + event
                    }
                    state.copy(rfidTags = updated)
                }
            }
        }
        // Start barcode scanner
        hardwareScanner.startBarcodeScan()
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

    fun toggleRfidScan() {
        val isScanning = _uiState.value.isRfidScanning
        if (isScanning) {
            hardwareScanner.stopRfid()
            _uiState.update { it.copy(isRfidScanning = false) }
        } else {
            _uiState.update { it.copy(rfidTags = emptyList(), isRfidScanning = true) }
            hardwareScanner.startRfidBulkRead()
        }
    }

    fun clearResult() {
        hardwareScanner.stopRfid()
        _uiState.update { ScanUiState() }
    }

    override fun onCleared() {
        super.onCleared()
        hardwareScanner.stopBarcodeScan()
        hardwareScanner.stopRfid()
    }
}
