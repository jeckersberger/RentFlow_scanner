package com.rentflow.scanner.ui.equipment

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rentflow.scanner.data.hardware.HardwareScanner
import com.rentflow.scanner.data.hardware.RfidReadEvent
import com.rentflow.scanner.data.hardware.RfidWriteResult
import com.rentflow.scanner.data.repository.ScannerRepository
import com.rentflow.scanner.data.repository.WarehouseRepository
import com.rentflow.scanner.domain.model.Equipment
import com.rentflow.scanner.domain.model.ScanResult
import com.rentflow.scanner.domain.model.ScanType
import com.rentflow.scanner.domain.model.WarehouseZone
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
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
    val locationSaved: String? = null,
    val zones: List<WarehouseZone> = emptyList(),
    val showOverwriteDialog: Boolean = false,
    val existingTagEpc: String? = null,
    val rfidVerified: Boolean = false,
    val history: List<ScanResult> = emptyList(),
    val isLocating: Boolean = false,
    val rssiValue: Int = 0,
)

@HiltViewModel
class EquipmentDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val scannerRepository: ScannerRepository,
    private val warehouseRepository: WarehouseRepository,
    private val hardwareScanner: HardwareScanner,
) : ViewModel() {
    private val barcode: String = savedStateHandle["barcode"] ?: ""
    private val _uiState = MutableStateFlow(EquipmentDetailUiState())
    val uiState: StateFlow<EquipmentDetailUiState> = _uiState

    private var locatingJob: Job? = null

    init {
        loadEquipment()
        loadZones()
        loadHistory()
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

    private fun loadZones() {
        viewModelScope.launch {
            warehouseRepository.listZones().onSuccess { zones ->
                _uiState.update { it.copy(zones = zones) }
            }
        }
    }

    private fun loadHistory() {
        viewModelScope.launch {
            scannerRepository.getScanHistory(barcode).onSuccess { history ->
                if (history.isEmpty()) {
                    // Demo mode: generate fake history entries
                    val now = System.currentTimeMillis()
                    val demoHistory = listOf(
                        ScanResult(
                            id = "demo-h1",
                            barcode = barcode,
                            equipment = null,
                            timestamp = now - 86_400_000L * 2,
                            scanType = ScanType.OUT,
                        ),
                        ScanResult(
                            id = "demo-h2",
                            barcode = barcode,
                            equipment = null,
                            timestamp = now - 86_400_000L,
                            scanType = ScanType.IN,
                        ),
                        ScanResult(
                            id = "demo-h3",
                            barcode = barcode,
                            equipment = null,
                            timestamp = now - 3_600_000L,
                            scanType = ScanType.INVENTORY,
                        ),
                    )
                    _uiState.update { it.copy(history = demoHistory) }
                } else {
                    _uiState.update { it.copy(history = history) }
                }
            }
        }
    }

    fun startLocating() {
        _uiState.update { it.copy(isLocating = true, rssiValue = 0) }
        hardwareScanner.startRfidRead()
        locatingJob = viewModelScope.launch {
            hardwareScanner.rfidReadEvents.collect { event ->
                _uiState.update { it.copy(rssiValue = event.rssi) }
            }
        }
    }

    fun stopLocating() {
        locatingJob?.cancel()
        locatingJob = null
        hardwareScanner.stopRfid()
        _uiState.update { it.copy(isLocating = false, rssiValue = 0) }
    }

    fun updateLocation(newLocation: String) {
        val eq = _uiState.value.equipment ?: return
        viewModelScope.launch {
            scannerRepository.updateEquipmentLocation(eq.id, newLocation).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            equipment = eq.copy(location = newLocation),
                            locationSaved = "Lagerort auf \"$newLocation\" geändert",
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(error = e.message) }
                },
            )
        }
    }

    fun writeRfidTag() {
        val eq = _uiState.value.equipment ?: return

        // First read the tag to check if it already has data
        viewModelScope.launch {
            _uiState.update { it.copy(isWritingRfid = true, rfidWriteResult = null, rfidVerified = false) }

            // Try to read existing tag first
            hardwareScanner.startRfidRead()
            // Give it a moment to read
            kotlinx.coroutines.delay(500)
            hardwareScanner.stopRfid()

            // Collect any tag that was read
            var existingEpc: String? = null
            val readJob = viewModelScope.launch {
                hardwareScanner.rfidReadEvents.collect { event ->
                    existingEpc = event.epc
                }
            }
            kotlinx.coroutines.delay(600)
            readJob.cancel()

            if (existingEpc != null && existingEpc != eq.barcode && existingEpc != "0000000000000000") {
                // Tag has existing data — show overwrite dialog
                _uiState.update {
                    it.copy(
                        isWritingRfid = false,
                        showOverwriteDialog = true,
                        existingTagEpc = existingEpc,
                    )
                }
            } else {
                // Tag is empty or has same data — write directly
                performWrite(eq.barcode)
            }
        }
    }

    fun confirmOverwrite() {
        val eq = _uiState.value.equipment ?: return
        _uiState.update { it.copy(showOverwriteDialog = false, existingTagEpc = null) }
        viewModelScope.launch {
            _uiState.update { it.copy(isWritingRfid = true) }
            performWrite(eq.barcode)
        }
    }

    fun dismissOverwrite() {
        _uiState.update { it.copy(showOverwriteDialog = false, existingTagEpc = null, isWritingRfid = false) }
    }

    private suspend fun performWrite(epc: String) {
        val result = hardwareScanner.writeRfidTag(epc)
        if (result.success) {
            // Verify by reading back
            kotlinx.coroutines.delay(300)
            hardwareScanner.startRfidRead()
            var verifiedEpc: String? = null
            val verifyJob = viewModelScope.launch {
                hardwareScanner.rfidReadEvents.collect { event ->
                    verifiedEpc = event.epc
                }
            }
            kotlinx.coroutines.delay(600)
            verifyJob.cancel()
            hardwareScanner.stopRfid()

            val verified = verifiedEpc?.uppercase() == epc.uppercase()
            _uiState.update {
                it.copy(
                    isWritingRfid = false,
                    rfidWriteResult = result,
                    rfidVerified = verified,
                )
            }
        } else {
            _uiState.update { it.copy(isWritingRfid = false, rfidWriteResult = result) }
        }
    }
}
