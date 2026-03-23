package com.rentflow.scanner.ui.equipment

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rentflow.scanner.data.hardware.HardwareScanner
import com.rentflow.scanner.data.hardware.RfidWriteResult
import com.rentflow.scanner.data.repository.ScannerRepository
import com.rentflow.scanner.domain.model.Equipment
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EquipmentDetailUiState(
    val equipment: Equipment? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val rfidWriteResult: RfidWriteResult? = null,
    val isWritingRfid: Boolean = false,
)

@HiltViewModel
class EquipmentDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val scannerRepository: ScannerRepository,
    private val hardwareScanner: HardwareScanner,
) : ViewModel() {
    private val barcode: String = savedStateHandle["barcode"] ?: ""
    private val _uiState = MutableStateFlow(EquipmentDetailUiState())
    val uiState: StateFlow<EquipmentDetailUiState> = _uiState

    init {
        loadEquipment()
    }

    private fun loadEquipment() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            scannerRepository.resolveBarcode(barcode).fold(
                onSuccess = { eq -> _uiState.update { it.copy(isLoading = false, equipment = eq) } },
                onFailure = { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } },
            )
        }
    }

    fun writeRfidTag() {
        val eq = _uiState.value.equipment ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isWritingRfid = true, rfidWriteResult = null) }
            val result = hardwareScanner.writeRfidTag(eq.barcode)
            _uiState.update { it.copy(isWritingRfid = false, rfidWriteResult = result) }
        }
    }
}
