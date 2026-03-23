package com.rentflow.scanner.ui.checkin

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

data class CheckInItem(
    val equipment: Equipment,
    val condition: Int = 5,
    val notes: String = "",
)

data class CheckInUiState(
    val scannedItems: List<CheckInItem> = emptyList(),
    val sessionId: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val completed: Boolean = false,
)

@HiltViewModel
class CheckInViewModel @Inject constructor(
    private val scannerRepository: ScannerRepository,
    private val hardwareScanner: HardwareScanner,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CheckInUiState())
    val uiState: StateFlow<CheckInUiState> = _uiState

    init {
        startSession()
        viewModelScope.launch {
            hardwareScanner.barcodeScanEvents.collect { event ->
                onBarcodeScanned(event.barcode)
            }
        }
    }

    private fun startSession() {
        viewModelScope.launch {
            scannerRepository.createSession("in").fold(
                onSuccess = { session -> _uiState.update { it.copy(sessionId = session.id) } },
                onFailure = { e -> _uiState.update { it.copy(error = e.message) } },
            )
        }
    }

    private fun onBarcodeScanned(barcode: String) {
        val state = _uiState.value
        if (state.sessionId == null) return
        if (state.scannedItems.any { it.equipment.barcode == barcode }) return

        viewModelScope.launch {
            scannerRepository.resolveBarcode(barcode).fold(
                onSuccess = { equipment ->
                    scannerRepository.sessionScan(state.sessionId, barcode, "in")
                    _uiState.update { it.copy(scannedItems = it.scannedItems + CheckInItem(equipment)) }
                },
                onFailure = { e -> _uiState.update { it.copy(error = e.message) } },
            )
        }
    }

    fun updateCondition(index: Int, condition: Int) {
        _uiState.update {
            val items = it.scannedItems.toMutableList()
            items[index] = items[index].copy(condition = condition)
            it.copy(scannedItems = items)
        }
    }

    fun updateNotes(index: Int, notes: String) {
        _uiState.update {
            val items = it.scannedItems.toMutableList()
            items[index] = items[index].copy(notes = notes)
            it.copy(scannedItems = items)
        }
    }

    fun completeCheckIn() {
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
