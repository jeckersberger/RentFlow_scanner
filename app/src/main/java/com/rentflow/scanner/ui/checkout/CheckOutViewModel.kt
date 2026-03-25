package com.rentflow.scanner.ui.checkout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rentflow.scanner.data.hardware.HardwareScanner
import com.rentflow.scanner.data.repository.ProjectRepository
import com.rentflow.scanner.data.repository.ScannerRepository
import com.rentflow.scanner.domain.model.Equipment
import com.rentflow.scanner.domain.model.Project
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject

enum class ProjectUrgency { OVERDUE, TODAY, UPCOMING, NORMAL }

data class ProjectWithUrgency(
    val project: Project,
    val urgency: ProjectUrgency,
    val daysUntilStart: Long,
)

data class CheckOutUiState(
    val projects: List<ProjectWithUrgency> = emptyList(),
    val allProjects: List<ProjectWithUrgency> = emptyList(),
    val showAllJobs: Boolean = false,
    val selectedProject: Project? = null,
    val expectedEquipmentIds: Set<String> = emptySet(),
    val scannedItems: List<Equipment> = emptyList(),
    val sessionId: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val completed: Boolean = false,
    val adHocEquipment: Equipment? = null,
    val isRfidBulkActive: Boolean = false,
    val rfidResolving: Set<String> = emptySet(),
)

@HiltViewModel
class CheckOutViewModel @Inject constructor(
    private val scannerRepository: ScannerRepository,
    private val projectRepository: ProjectRepository,
    private val hardwareScanner: HardwareScanner,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CheckOutUiState())
    val uiState: StateFlow<CheckOutUiState> = _uiState

    init {
        loadProjects()
        // Barcode scan events
        viewModelScope.launch {
            hardwareScanner.barcodeScanEvents.collect { event ->
                onBarcodeScanned(event.barcode)
            }
        }
        // RFID scan events for bulk mode
        viewModelScope.launch {
            hardwareScanner.rfidReadEvents.collect { event ->
                if (_uiState.value.isRfidBulkActive) {
                    onRfidTagScanned(event.epc)
                }
            }
        }
    }

    private fun loadProjects() {
        viewModelScope.launch {
            projectRepository.listActiveProjects().fold(
                onSuccess = { projects ->
                    val sorted = projects.toProjectsWithUrgency()
                    _uiState.update { s -> s.copy(projects = sorted) }
                },
                onFailure = { _uiState.update { s -> s.copy(error = it.message) } },
            )
        }
    }

    private fun List<Project>.toProjectsWithUrgency(): List<ProjectWithUrgency> {
        val today = LocalDate.now()
        return map { project ->
            val startDate = project.start_date?.let {
                runCatching { LocalDate.parse(it, DateTimeFormatter.ISO_DATE) }.getOrNull()
            }
            val days = startDate?.let { ChronoUnit.DAYS.between(today, it) } ?: Long.MAX_VALUE
            val urgency = when {
                days < 0 -> ProjectUrgency.OVERDUE
                days == 0L -> ProjectUrgency.TODAY
                days <= 2 -> ProjectUrgency.UPCOMING
                else -> ProjectUrgency.NORMAL
            }
            ProjectWithUrgency(project, urgency, days)
        }.sortedBy { it.daysUntilStart }
    }

    fun toggleShowAll() {
        val current = _uiState.value
        if (!current.showAllJobs && current.allProjects.isEmpty()) {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true) }
                projectRepository.listProjects().fold(
                    onSuccess = { projects ->
                        val sorted = projects.toProjectsWithUrgency()
                        _uiState.update { it.copy(allProjects = sorted, showAllJobs = true, isLoading = false) }
                    },
                    onFailure = { e -> _uiState.update { it.copy(error = e.message, isLoading = false) } },
                )
            }
        } else {
            _uiState.update { it.copy(showAllJobs = !it.showAllJobs) }
        }
    }

    fun selectProject(project: Project) {
        viewModelScope.launch {
            _uiState.update { it.copy(selectedProject = project, isLoading = true) }
            projectRepository.listProjectEquipment(project.id).fold(
                onSuccess = { equipment ->
                    val ids = equipment.map { it.id }.toSet()
                    _uiState.update { it.copy(expectedEquipmentIds = ids) }
                },
                onFailure = { /* No equipment list available */ },
            )
            scannerRepository.createSession("out", project.id).fold(
                onSuccess = { session -> _uiState.update { it.copy(sessionId = session.id, isLoading = false) } },
                onFailure = { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } },
            )
        }
    }

    fun toggleRfidBulk() {
        val current = _uiState.value.isRfidBulkActive
        if (!current) {
            hardwareScanner.startRfidBulkRead()
        } else {
            hardwareScanner.stopRfid()
        }
        _uiState.update { it.copy(isRfidBulkActive = !current) }
    }

    private fun onRfidTagScanned(epc: String) {
        val state = _uiState.value
        if (state.sessionId == null) return
        if (state.scannedItems.any { it.rfidTag == epc || it.barcode == epc }) return
        if (epc in state.rfidResolving) return

        _uiState.update { it.copy(rfidResolving = it.rfidResolving + epc) }
        viewModelScope.launch {
            scannerRepository.resolveBarcode(epc).fold(
                onSuccess = { equipment ->
                    _uiState.update { it.copy(rfidResolving = it.rfidResolving - epc) }
                    val isExpected = state.expectedEquipmentIds.isEmpty() || equipment.id in state.expectedEquipmentIds
                    if (isExpected) {
                        addEquipment(equipment)
                    }
                    // In RFID bulk mode, skip ad-hoc confirmation for speed
                },
                onFailure = {
                    _uiState.update { it.copy(rfidResolving = it.rfidResolving - epc) }
                },
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
                    val isExpected = state.expectedEquipmentIds.isEmpty() || equipment.id in state.expectedEquipmentIds
                    if (isExpected) {
                        addEquipment(equipment)
                    } else {
                        _uiState.update { it.copy(adHocEquipment = equipment) }
                    }
                },
                onFailure = { e -> _uiState.update { it.copy(error = e.message) } },
            )
        }
    }

    fun confirmAdHoc() {
        val equipment = _uiState.value.adHocEquipment ?: return
        _uiState.update { it.copy(adHocEquipment = null) }
        addEquipment(equipment)
    }

    fun dismissAdHoc() {
        _uiState.update { it.copy(adHocEquipment = null) }
    }

    private fun addEquipment(equipment: Equipment) {
        val state = _uiState.value
        if (state.sessionId == null) return
        if (state.scannedItems.any { it.id == equipment.id }) return
        viewModelScope.launch {
            scannerRepository.sessionScan(state.sessionId, equipment.barcode, "out")
            _uiState.update { it.copy(scannedItems = it.scannedItems + equipment) }
        }
    }

    fun removeItem(equipment: Equipment) {
        _uiState.update { it.copy(scannedItems = it.scannedItems - equipment) }
    }

    fun completeCheckOut() {
        val state = _uiState.value
        if (state.sessionId == null) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            if (state.isRfidBulkActive) {
                hardwareScanner.stopRfid()
                _uiState.update { it.copy(isRfidBulkActive = false) }
            }
            scannerRepository.endSession(state.sessionId).fold(
                onSuccess = { _uiState.update { it.copy(isLoading = false, completed = true) } },
                onFailure = { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } },
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (_uiState.value.isRfidBulkActive) {
            hardwareScanner.stopRfid()
        }
    }
}
