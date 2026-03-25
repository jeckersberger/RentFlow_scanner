package com.rentflow.scanner.ui.checkin

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rentflow.scanner.data.hardware.HardwareScanner
import com.rentflow.scanner.data.util.ImageCompressor
import com.rentflow.scanner.data.repository.ProjectRepository
import com.rentflow.scanner.data.repository.ScannerRepository
import com.rentflow.scanner.domain.model.Equipment
import com.rentflow.scanner.domain.model.Project
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CheckInItem(
    val equipment: Equipment,
    val condition: Int = 5,
    val notes: String = "",
    val photoUri: Uri? = null,
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
    val photoTargetIndex: Int = -1,
)

@HiltViewModel
class CheckInViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
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
        viewModelScope.launch {
            hardwareScanner.rfidReadEvents.collect { event ->
                onBarcodeScanned(event.epc)
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
            if (index in items.indices) items[index] = items[index].copy(condition = condition)
            it.copy(scannedItems = items)
        }
    }

    fun updateNotes(index: Int, notes: String) {
        _uiState.update {
            val items = it.scannedItems.toMutableList()
            if (index in items.indices) items[index] = items[index].copy(notes = notes)
            it.copy(scannedItems = items)
        }
    }

    fun requestPhoto(index: Int) {
        _uiState.update { it.copy(photoTargetIndex = index) }
    }

    fun onPhotoTaken(uri: Uri?) {
        val index = _uiState.value.photoTargetIndex
        if (index < 0 || uri == null) {
            _uiState.update { it.copy(photoTargetIndex = -1) }
            return
        }
        // Compress photo to max 1920px, 80% JPEG quality
        val compressed = ImageCompressor.compressPhoto(context, uri) ?: uri
        _uiState.update {
            val items = it.scannedItems.toMutableList()
            if (index in items.indices) items[index] = items[index].copy(photoUri = compressed)
            it.copy(scannedItems = items, photoTargetIndex = -1)
        }
    }

    fun removeItem(index: Int) {
        _uiState.update {
            val items = it.scannedItems.toMutableList()
            if (index in items.indices) items.removeAt(index)
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
