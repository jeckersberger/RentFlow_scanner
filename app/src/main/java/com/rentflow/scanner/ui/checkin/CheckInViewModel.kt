package com.rentflow.scanner.ui.checkin

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
import javax.inject.Inject

data class CheckInItem(
    val equipment: Equipment,
    val condition: Int = 5,
    val notes: String = "",
)

data class CheckInUiState(
    val returningToday: List<Project> = emptyList(),
    val allCheckedOut: List<Project> = emptyList(),
    val showAllJobs: Boolean = false,
    val selectedProject: Project? = null,
    val scannedItems: List<CheckInItem> = emptyList(),
    val sessionId: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val completed: Boolean = false,
)

@HiltViewModel
class CheckInViewModel @Inject constructor(
    private val scannerRepository: ScannerRepository,
    private val projectRepository: ProjectRepository,
    private val hardwareScanner: HardwareScanner,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CheckInUiState())
    val uiState: StateFlow<CheckInUiState> = _uiState

    init {
        loadJobs()
        viewModelScope.launch {
            hardwareScanner.barcodeScanEvents.collect { event ->
                onBarcodeScanned(event.barcode)
            }
        }
    }

    private fun loadJobs() {
        viewModelScope.launch {
            projectRepository.listReturningToday().fold(
                onSuccess = { _uiState.update { s -> s.copy(returningToday = it) } },
                onFailure = { _uiState.update { s -> s.copy(error = it.message) } },
            )
        }
    }

    fun toggleShowAll() {
        val current = _uiState.value
        if (!current.showAllJobs && current.allCheckedOut.isEmpty()) {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true) }
                projectRepository.listCheckedOut().fold(
                    onSuccess = { projects ->
                        _uiState.update { it.copy(allCheckedOut = projects, showAllJobs = true, isLoading = false) }
                    },
                    onFailure = { e ->
                        _uiState.update { it.copy(error = e.message, isLoading = false) }
                    },
                )
            }
        } else {
            _uiState.update { it.copy(showAllJobs = !it.showAllJobs) }
        }
    }

    fun selectProject(project: Project) {
        viewModelScope.launch {
            _uiState.update { it.copy(selectedProject = project, isLoading = true) }
            scannerRepository.createSession("in", project.id).fold(
                onSuccess = { session -> _uiState.update { it.copy(sessionId = session.id, isLoading = false) } },
                onFailure = { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } },
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
