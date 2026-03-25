package com.rentflow.scanner.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rentflow.scanner.data.hardware.HardwareScanner
import com.rentflow.scanner.data.hardware.RfidReadEvent
import com.rentflow.scanner.data.hardware.ScanFeedback
import com.rentflow.scanner.data.preferences.SettingsDataStore
import com.rentflow.scanner.data.repository.ScannerRepository
import com.rentflow.scanner.domain.model.Equipment
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ScanUiState(
    val equipment: Equipment? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val lastBarcode: String? = null,
    val rfidTags: List<RfidReadEvent> = emptyList(),
    val isScanning: Boolean = false,
    val scanMode: String = SettingsDataStore.SCAN_MODE_BARCODE,
)

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val scannerRepository: ScannerRepository,
    private val hardwareScanner: HardwareScanner,
    private val settingsDataStore: SettingsDataStore,
    private val scanFeedback: ScanFeedback,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState

    init {
        // Load scan mode and initialize correct hardware
        viewModelScope.launch {
            val mode = settingsDataStore.scanMode.first()
            _uiState.update { it.copy(scanMode = mode) }
            activateMode(mode)
        }

        // Keep listening for mode changes
        viewModelScope.launch {
            settingsDataStore.scanMode.collect { mode ->
                _uiState.update { it.copy(scanMode = mode) }
            }
        }

        // Listen for barcode scan events (hardware trigger)
        viewModelScope.launch {
            hardwareScanner.barcodeScanEvents.collect { event ->
                if (_uiState.value.scanMode == SettingsDataStore.SCAN_MODE_BARCODE) {
                    onBarcodeScanned(event.barcode)
                }
            }
        }

        // Listen for RFID read events (hardware trigger)
        viewModelScope.launch {
            hardwareScanner.rfidReadEvents.collect { event ->
                if (_uiState.value.scanMode == SettingsDataStore.SCAN_MODE_RFID) {
                    scanFeedback.onRfidTagFound()
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
        }
    }

    private fun activateMode(mode: String) {
        if (mode == SettingsDataStore.SCAN_MODE_BARCODE) {
            hardwareScanner.closeRfid()
            hardwareScanner.initBarcodeScan()
        } else {
            hardwareScanner.closeBarcodeScan()
            hardwareScanner.startRfidBulkRead()
            _uiState.update { it.copy(isScanning = true) }
        }
    }

    fun onBarcodeScanned(barcode: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, lastBarcode = barcode) }
            scannerRepository.resolveBarcode(barcode).fold(
                onSuccess = { equipment ->
                    scanFeedback.onScanSuccess()
                    _uiState.update { it.copy(isLoading = false, equipment = equipment) }
                },
                onFailure = { e ->
                    scanFeedback.onScanError()
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                },
            )
        }
    }

    fun clearResult() {
        _uiState.update { it.copy(equipment = null, error = null, lastBarcode = null, rfidTags = emptyList()) }
    }

    override fun onCleared() {
        super.onCleared()
        hardwareScanner.stopBarcodeScan()
        hardwareScanner.closeBarcodeScan()
        hardwareScanner.stopRfid()
        hardwareScanner.closeRfid()
    }
}
