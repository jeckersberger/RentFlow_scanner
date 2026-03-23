package com.rentflow.scanner.ui.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rentflow.scanner.data.hardware.HardwareScanner
import com.rentflow.scanner.data.repository.ScannerRepository
import com.rentflow.scanner.data.repository.WarehouseRepository
import com.rentflow.scanner.domain.model.Equipment
import com.rentflow.scanner.domain.model.WarehouseZone
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InventoryUiState(
    val zones: List<WarehouseZone> = emptyList(),
    val selectedZone: WarehouseZone? = null,
    val scannedItems: List<Equipment> = emptyList(),
    val sessionId: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val completed: Boolean = false,
)

@HiltViewModel
class InventoryViewModel @Inject constructor(
    private val scannerRepository: ScannerRepository,
    private val warehouseRepository: WarehouseRepository,
    private val hardwareScanner: HardwareScanner,
) : ViewModel() {
    private val _uiState = MutableStateFlow(InventoryUiState())
    val uiState: StateFlow<InventoryUiState> = _uiState

    init {
        loadZones()
        viewModelScope.launch {
            hardwareScanner.barcodeScanEvents.collect { event ->
                onBarcodeScanned(event.barcode)
            }
        }
    }

    private fun loadZones() {
        viewModelScope.launch {
            warehouseRepository.listZones().fold(
                onSuccess = { _uiState.update { s -> s.copy(zones = it) } },
                onFailure = { _uiState.update { s -> s.copy(error = it.message) } },
            )
        }
    }

    fun selectZone(zone: WarehouseZone) {
        viewModelScope.launch {
            _uiState.update { it.copy(selectedZone = zone, isLoading = true) }
            scannerRepository.createSession("inventory").fold(
                onSuccess = { session -> _uiState.update { it.copy(sessionId = session.id, isLoading = false) } },
                onFailure = { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } },
            )
        }
    }

    private fun onBarcodeScanned(barcode: String) {
        val state = _uiState.value
        if (state.sessionId == null) return
        if (state.scannedItems.any { it.barcode == barcode }) return

        viewModelScope.launch {
            scannerRepository.resolveBarcode(barcode).fold(
                onSuccess = { equipment ->
                    scannerRepository.sessionScan(state.sessionId, barcode, "inventory")
                    _uiState.update { it.copy(scannedItems = it.scannedItems + equipment) }
                },
                onFailure = { e -> _uiState.update { it.copy(error = e.message) } },
            )
        }
    }

    fun completeInventory() {
        val state = _uiState.value
        if (state.sessionId == null) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            scannerRepository.endSession(state.sessionId).fold(
                onSuccess = { _uiState.update { it.copy(isLoading = false, completed = true) } },
                onFailure = { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } },
            )
        }
    }
}
