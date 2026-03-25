package com.rentflow.scanner.ui.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rentflow.scanner.data.hardware.HardwareScanner
import com.rentflow.scanner.data.hardware.ScanFeedback
import com.rentflow.scanner.data.preferences.SettingsDataStore
import com.rentflow.scanner.data.repository.ScannerRepository
import com.rentflow.scanner.data.repository.WarehouseRepository
import com.rentflow.scanner.domain.model.Equipment
import com.rentflow.scanner.domain.model.EquipmentStatus
import com.rentflow.scanner.domain.model.WarehouseZone
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InventoryUiState(
    val zones: List<WarehouseZone> = emptyList(),
    val selectedZone: WarehouseZone? = null,
    val scannedItems: List<Equipment> = emptyList(),
    val expectedItems: List<Equipment> = emptyList(),
    val sessionId: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val completed: Boolean = false,
) {
    /** Scanned AND in expectedItems (match by barcode) */
    val foundItems: List<Equipment>
        get() {
            val expectedBarcodes = expectedItems.map { it.barcode }.toSet()
            return scannedItems.filter { it.barcode in expectedBarcodes }
        }

    /** In expectedItems but NOT scanned */
    val missingItems: List<Equipment>
        get() {
            val scannedBarcodes = scannedItems.map { it.barcode }.toSet()
            return expectedItems.filter { it.barcode !in scannedBarcodes }
        }

    /** Scanned but NOT in expectedItems */
    val unexpectedItems: List<Equipment>
        get() {
            val expectedBarcodes = expectedItems.map { it.barcode }.toSet()
            return scannedItems.filter { it.barcode !in expectedBarcodes }
        }
}

@HiltViewModel
class InventoryViewModel @Inject constructor(
    private val scannerRepository: ScannerRepository,
    private val warehouseRepository: WarehouseRepository,
    private val hardwareScanner: HardwareScanner,
    private val settingsDataStore: SettingsDataStore,
    private val scanFeedback: ScanFeedback,
) : ViewModel() {
    private val _uiState = MutableStateFlow(InventoryUiState())
    val uiState: StateFlow<InventoryUiState> = _uiState

    init {
        loadZones()
        viewModelScope.launch {
            val mode = settingsDataStore.scanMode.first()
            if (mode == SettingsDataStore.SCAN_MODE_BARCODE) {
                hardwareScanner.initBarcodeScan()
            }
        }
        viewModelScope.launch {
            hardwareScanner.barcodeScanEvents.collect { event ->
                onBarcodeScanned(event.barcode)
            }
        }
        viewModelScope.launch {
            hardwareScanner.rfidReadEvents.collect { event ->
                scanFeedback.onRfidTagFound()
                onBarcodeScanned(event.tid.ifBlank { event.epc })
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        hardwareScanner.closeBarcodeScan()
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
            _uiState.update { it.copy(selectedZone = zone, isLoading = true, error = null) }

            val sessionResult = scannerRepository.createSession("inventory")
            val isDemo = sessionResult.isFailure

            if (sessionResult.isSuccess) {
                _uiState.update { it.copy(sessionId = sessionResult.getOrNull()!!.id) }
            } else {
                // Demo mode: use a mock session id
                _uiState.update { it.copy(sessionId = "demo-session-${zone.id}") }
            }

            // Load expected items for this zone
            // TODO: replace with real API call when backend supports it
            if (isDemo) {
                val demoExpected = generateDemoExpectedItems(zone)
                _uiState.update { it.copy(expectedItems = demoExpected, isLoading = false) }
            } else {
                // For real backend, call API to get expected items for the zone
                // For now, fall back to demo items if no dedicated endpoint exists
                val demoExpected = generateDemoExpectedItems(zone)
                _uiState.update { it.copy(expectedItems = demoExpected, isLoading = false) }
            }
        }
    }

    private fun generateDemoExpectedItems(zone: WarehouseZone): List<Equipment> {
        val categories = listOf("Bohrmaschine", "Stichsaege", "Kompressor", "Stromerzeuger", "Ruettler")
        return (1..5).map { index ->
            Equipment(
                id = "expected-${zone.id}-$index",
                barcode = "INV-${zone.id}-${String.format("%04d", index)}",
                name = "${categories[index - 1]} #${zone.id}.$index",
                category = categories[index - 1],
                status = EquipmentStatus.AVAILABLE,
                location = zone.name,
                projectName = null,
                rfidTag = "RFID-${zone.id}-${String.format("%04d", index)}",
                imageUrl = null,
            )
        }
    }

    private fun onBarcodeScanned(barcode: String) {
        val state = _uiState.value
        if (state.sessionId == null) return
        if (state.scannedItems.any { it.barcode == barcode || it.rfidTag == barcode }) return

        // Check if the barcode/EPC matches an expected item's rfidTag
        val expectedByRfid = state.expectedItems.find { it.rfidTag == barcode }
        if (expectedByRfid != null && state.scannedItems.any { it.barcode == expectedByRfid.barcode }) return

        viewModelScope.launch {
            scannerRepository.resolveBarcode(barcode).fold(
                onSuccess = { equipment ->
                    scanFeedback.onScanSuccess()
                    scannerRepository.sessionScan(state.sessionId, equipment.barcode, "inventory")
                    _uiState.update { it.copy(scannedItems = it.scannedItems + equipment) }
                },
                onFailure = { e ->
                    scanFeedback.onScanError()
                    // If resolve fails but we matched an expected item by RFID, use that
                    if (expectedByRfid != null) {
                        scannerRepository.sessionScan(state.sessionId, expectedByRfid.barcode, "inventory")
                        _uiState.update { it.copy(scannedItems = it.scannedItems + expectedByRfid) }
                    } else {
                        // Create a placeholder for unknown scanned items
                        val placeholder = Equipment(
                            id = "unknown-$barcode",
                            barcode = barcode,
                            name = "Unbekannt ($barcode)",
                            category = "Unbekannt",
                            status = EquipmentStatus.AVAILABLE,
                            location = null,
                            projectName = null,
                            rfidTag = null,
                            imageUrl = null,
                        )
                        _uiState.update { it.copy(scannedItems = it.scannedItems + placeholder) }
                    }
                },
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
                onFailure = {
                    // In demo mode, still allow completing
                    if (state.sessionId.startsWith("demo-session-")) {
                        _uiState.update { it.copy(isLoading = false, completed = true) }
                    } else {
                        _uiState.update { s -> s.copy(isLoading = false, error = it.message) }
                    }
                },
            )
        }
    }
}
