package com.rentflow.scanner.ui.checkin

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.rentflow.scanner.R
import com.rentflow.scanner.domain.model.Project
import com.rentflow.scanner.ui.theme.Cyan
import com.rentflow.scanner.ui.theme.Warning
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckInScreen(
    onBack: () -> Unit,
    onCompleted: () -> Unit,
    viewModel: CheckInViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(state.completed) {
        if (state.completed) onCompleted()
    }

    // Photo capture
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        viewModel.onPhotoTaken(if (success) photoUri else null)
    }

    // When photoTargetIndex changes, launch camera
    LaunchedEffect(state.photoTargetIndex) {
        if (state.photoTargetIndex >= 0) {
            val file = File(context.cacheDir, "checkin_photo_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            photoUri = uri
            cameraLauncher.launch(uri)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.checkin_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
            }

            if (state.selectedProject == null) {
                // Job selection phase
                Text(stringResource(R.string.checkin_returning_today), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    FilledTonalButton(onClick = viewModel::toggleShowAll) {
                        Icon(
                            if (state.showAllJobs) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (state.showAllJobs) stringResource(R.string.checkin_today_only)
                            else stringResource(R.string.checkin_all_jobs)
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))

                val jobs = if (state.showAllJobs) state.allCheckedOut else state.returningToday

                if (jobs.isEmpty() && !state.isLoading) {
                    Text(
                        stringResource(R.string.checkin_no_returns_today),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (state.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.padding(16.dp).align(Alignment.CenterHorizontally))
                }

                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(jobs) { project ->
                        JobCard(project = project, onClick = { viewModel.selectProject(project) })
                    }
                }
            } else {
                // Scanning phase
                Text(
                    "${stringResource(R.string.checkin_title)}: ${state.selectedProject!!.name}",
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.checkin_items_scanned, state.scannedItems.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))

                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(state.scannedItems) { index, item ->
                        CheckInItemCard(
                            item = item,
                            onConditionChange = { viewModel.updateCondition(index, it) },
                            onNotesChange = { viewModel.updateNotes(index, it) },
                            onPhoto = { viewModel.requestPhoto(index) },
                            onRemove = { viewModel.removeItem(index) },
                        )
                    }
                }
                if (state.scannedItems.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = viewModel::completeCheckIn,
                        enabled = !state.isLoading,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Text("${stringResource(R.string.checkin_confirm)} (${state.scannedItems.size})")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckInItemCard(
    item: CheckInItem,
    onConditionChange: (Int) -> Unit,
    onNotesChange: (String) -> Unit,
    onPhoto: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.equipment.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(item.equipment.barcode, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                }
            }

            Spacer(Modifier.height(8.dp))

            // Star rating
            Text(stringResource(R.string.checkin_condition), style = MaterialTheme.typography.bodyMedium)
            Row {
                (1..5).forEach { star ->
                    IconButton(
                        onClick = { onConditionChange(star) },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            if (star <= item.condition) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = null,
                            tint = Warning,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    when (item.condition) {
                        1 -> stringResource(R.string.checkin_condition_1)
                        2 -> stringResource(R.string.checkin_condition_2)
                        3 -> stringResource(R.string.checkin_condition_3)
                        4 -> stringResource(R.string.checkin_condition_4)
                        5 -> stringResource(R.string.checkin_condition_5)
                        else -> ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }

            Spacer(Modifier.height(8.dp))

            // Notes
            OutlinedTextField(
                value = item.notes,
                onValueChange = onNotesChange,
                label = { Text(stringResource(R.string.checkin_notes)) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2,
            )

            Spacer(Modifier.height(8.dp))

            // Photo
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onPhoto) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (item.photoUri != null) stringResource(R.string.checkin_photo_change) else stringResource(R.string.checkin_photo))
                }
                item.photoUri?.let { uri ->
                    Spacer(Modifier.width(12.dp))
                    AsyncImage(
                        model = uri,
                        contentDescription = null,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, Cyan, RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Cyan, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun JobCard(project: Project, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(project.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            project.client?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            project.end_date?.let {
                Text(
                    stringResource(R.string.checkin_return_date, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
