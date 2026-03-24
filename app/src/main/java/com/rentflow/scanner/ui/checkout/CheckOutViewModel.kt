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
    val selectedProject: Project? = null,
    val scannedItems: List<Equipment> = emptyList(),
    val sessionId: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val completed: Boolean = false,
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
        viewModelScope.launch {
            hardwareScanner.barcodeScanEvents.collect { event ->
                onBarcodeScanned(event.barcode)
            }
        }
    }

    private fun loadProjects() {
        viewModelScope.launch {
            projectRepository.listActiveProjects().fold(
                onSuccess = { projects ->
                    val today = LocalDate.now()
                    val sorted = projects.map { project ->
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
                    _uiState.update { s -> s.copy(projects = sorted) }
                },
                onFailure = { _uiState.update { s -> s.copy(error = it.message) } },
            )
        }
    }

    fun selectProject(project: Project) {
        viewModelScope.launch {
            _uiState.update { it.copy(selectedProject = project, isLoading = true) }
            scannerRepository.createSession("out", project.id).fold(
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
                    scannerRepository.sessionScan(state.sessionId, barcode, "out")
                    _uiState.update { it.copy(scannedItems = it.scannedItems + equipment) }
                },
                onFailure = { e -> _uiState.update { it.copy(error = e.message) } },
            )
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
            scannerRepository.endSession(state.sessionId).fold(
                onSuccess = { _uiState.update { it.copy(isLoading = false, completed = true) } },
                onFailure = { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } },
            )
        }
    }
}
