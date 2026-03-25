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

enum class ItemCondition { OK, DAMAGED, DEFECTIVE }

data class CheckInItem(
    val equipment: Equipment,
    val condition: ItemCondition = ItemCondition.OK,
    val notes: String = "",
    val photos: List<Uri> = emptyList(),
)

data class CheckInUiState(
    val returningToday: List<Project> = emptyList(),
    val allCheckedOut: List<Project> = emptyList(),
    val showAllJobs: Boolean = false,
    val selectedProject: Project? = null,
    val autoDetectedProject: Project? = null,
    val expectedReturnCount: Int = 0,
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
            // Load expected return count
            val returnCount = project.equipment_count.takeIf { it > 0 }
                ?: projectRepository.listProjectEquipment(project.id).fold(
                    onSuccess = { it.size },
                    onFailure = { 0 },
                )
            _uiState.update { it.copy(expectedReturnCount = returnCount) }
            scannerRepository.createSession("in", project.id).fold(
                onSuccess = { session -> _uiState.update { it.copy(sessionId = session.id, isLoading = false) } },
                onFailure = { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } },
            )
        }
    }

    private fun onBarcodeScanned(barcode: String) {
        val state = _uiState.value
        if (state.scannedItems.any { it.equipment.barcode == barcode }) return

        // Auto-detect project when no project selected yet
        if (state.selectedProject == null && state.sessionId == null) {
            viewModelScope.launch {
                scannerRepository.resolveBarcode(barcode).fold(
                    onSuccess = { equipment ->
                        if (equipment.projectId != null && equipment.projectName != null) {
                            val project = Project(
                                id = equipment.projectId,
                                name = equipment.projectName,
                                status = "checked_out",
                                color = null,
                                equipment_count = 0,
                            )
                            _uiState.update { it.copy(autoDetectedProject = project) }
                        }
                    },
                    onFailure = { e -> _uiState.update { it.copy(error = e.message) } },
                )
            }
            return
        }

        if (state.sessionId == null) return

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

    fun confirmAutoDetectedProject() {
        val project = _uiState.value.autoDetectedProject ?: return
        _uiState.update { it.copy(autoDetectedProject = null) }
        selectProject(project)
    }

    fun dismissAutoDetectedProject() {
        _uiState.update { it.copy(autoDetectedProject = null) }
    }

    fun updateCondition(index: Int, condition: ItemCondition) {
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
            if (index in items.indices) {
                val current = items[index]
                if (current.photos.size < 5) {
                    items[index] = current.copy(photos = current.photos + compressed)
                }
            }
            it.copy(scannedItems = items, photoTargetIndex = -1)
        }
    }

    fun addPhoto(index: Int, uri: Uri) {
        val compressed = ImageCompressor.compressPhoto(context, uri) ?: uri
        _uiState.update {
            val items = it.scannedItems.toMutableList()
            if (index in items.indices) {
                val current = items[index]
                if (current.photos.size < 5) {
                    items[index] = current.copy(photos = current.photos + compressed)
                }
            }
            it.copy(scannedItems = items)
        }
    }

    fun removePhoto(itemIndex: Int, photoIndex: Int) {
        _uiState.update {
            val items = it.scannedItems.toMutableList()
            if (itemIndex in items.indices) {
                val current = items[itemIndex]
                if (photoIndex in current.photos.indices) {
                    items[itemIndex] = current.copy(photos = current.photos.toMutableList().apply { removeAt(photoIndex) })
                }
            }
            it.copy(scannedItems = items)
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
