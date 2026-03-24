package com.rentflow.scanner.ui.checkin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rentflow.scanner.R
import com.rentflow.scanner.domain.model.Project
import com.rentflow.scanner.ui.theme.Warning

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckInScreen(
    onBack: () -> Unit,
    onCompleted: () -> Unit,
    viewModel: CheckInViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.completed) {
        if (state.completed) onCompleted()
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

                // Toggle button
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
                Spacer(Modifier.height(16.dp))

                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(state.scannedItems) { index, item ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(item.equipment.name, style = MaterialTheme.typography.titleLarge)
                                Text("Barcode: ${item.equipment.barcode}", style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.height(8.dp))
                                Text(stringResource(R.string.checkin_condition), style = MaterialTheme.typography.bodyLarge)
                                Row {
                                    (1..5).forEach { star ->
                                        IconButton(onClick = { viewModel.updateCondition(index, star) }) {
                                            Icon(
                                                if (star <= item.condition) Icons.Default.Star else Icons.Default.StarBorder,
                                                contentDescription = null,
                                                tint = Warning,
                                            )
                                        }
                                    }
                                }
                                OutlinedTextField(
                                    value = item.notes,
                                    onValueChange = { viewModel.updateNotes(index, it) },
                                    label = { Text(stringResource(R.string.checkin_notes)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    maxLines = 2,
                                )
                            }
                        }
                    }
                }
                if (state.scannedItems.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = viewModel::completeCheckIn,
                        enabled = !state.isLoading,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) {
                        Text("${stringResource(R.string.checkin_confirm)} (${state.scannedItems.size})")
                    }
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
                    "Rückgabe: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
