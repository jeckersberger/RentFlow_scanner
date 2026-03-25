package com.rentflow.scanner.ui.checkout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rentflow.scanner.R
import com.rentflow.scanner.ui.components.EquipmentCard
import com.rentflow.scanner.ui.theme.Cyan
import com.rentflow.scanner.ui.theme.Error
import com.rentflow.scanner.ui.theme.Warning

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckOutScreen(
    onBack: () -> Unit,
    onCompleted: () -> Unit,
    viewModel: CheckOutViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.completed) {
        if (state.completed) onCompleted()
    }

    // Ad-hoc confirmation dialog
    state.adHocEquipment?.let { equipment ->
        AlertDialog(
            onDismissRequest = viewModel::dismissAdHoc,
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = Warning,
                    modifier = Modifier.size(32.dp),
                )
            },
            title = { Text(stringResource(R.string.adhoc_confirm_title)) },
            text = {
                Text(stringResource(R.string.adhoc_not_on_job, equipment.name, state.selectedProject?.name ?: ""))
            },
            confirmButton = {
                Button(
                    onClick = viewModel::confirmAdHoc,
                    colors = ButtonDefaults.buttonColors(containerColor = Warning),
                ) {
                    Text(stringResource(R.string.adhoc_add_anyway))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = viewModel::dismissAdHoc) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.checkout_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    if (state.selectedProject != null) {
                        // RFID bulk toggle
                        IconButton(onClick = viewModel::toggleRfidBulk) {
                            Icon(
                                Icons.Default.Sensors,
                                contentDescription = "RFID Bulk",
                                tint = if (state.isRfidBulkActive) Cyan else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
        ) {
            if (state.selectedProject == null) {
                Text(stringResource(R.string.checkout_select_project), style = MaterialTheme.typography.titleLarge)
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
                            if (state.showAllJobs) stringResource(R.string.checkout_active_only)
                            else stringResource(R.string.checkin_all_jobs)
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                val jobs = if (state.showAllJobs) state.allProjects else state.projects
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(jobs) { item ->
                        ProjectCard(
                            item = item,
                            onClick = { viewModel.selectProject(item.project) },
                        )
                    }
                }
            } else {
                // Scanning phase
                Text("${stringResource(R.string.checkout_title)}: ${state.selectedProject!!.name}", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))

                // RFID bulk indicator
                if (state.isRfidBulkActive) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Cyan.copy(alpha = 0.15f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Sensors, contentDescription = null, tint = Cyan)
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.checkout_rfid_bulk), fontWeight = FontWeight.Bold, color = Cyan)
                                Text(
                                    stringResource(R.string.checkout_rfid_auto_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (state.rfidResolving.isNotEmpty()) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                Text(
                    stringResource(R.string.checkout_items_scanned, state.scannedItems.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))

                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                }

                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.scannedItems, key = { it.id }) { item ->
                        val isAdHoc = state.expectedEquipmentIds.isNotEmpty() && item.id !in state.expectedEquipmentIds
                        EquipmentCard(
                            item,
                            adHoc = isAdHoc,
                            onRemove = { viewModel.removeItem(item) },
                        )
                    }
                }
                if (state.scannedItems.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = viewModel::completeCheckOut,
                        enabled = !state.isLoading,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Text("${stringResource(R.string.checkout_confirm)} (${state.scannedItems.size})")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectCard(
    item: ProjectWithUrgency,
    onClick: () -> Unit,
) {
    val isOverdue = item.urgency == ProjectUrgency.OVERDUE
    val bgColor = if (isOverdue) Error.copy(alpha = 0.15f) else Color.Transparent
    val borderColor = when (item.urgency) {
        ProjectUrgency.OVERDUE -> Error
        ProjectUrgency.TODAY -> Warning
        ProjectUrgency.UPCOMING -> MaterialTheme.colorScheme.primary
        ProjectUrgency.NORMAL -> MaterialTheme.colorScheme.outline
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(borderColor)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (isOverdue) Modifier.background(bgColor) else Modifier)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.project.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                item.project.client?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (item.project.equipment_count > 0) {
                    Text(
                        stringResource(R.string.checkout_items, item.project.equipment_count),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            UrgencyBadge(item)
        }
    }
}

@Composable
private fun UrgencyBadge(item: ProjectWithUrgency) {
    val (text, color) = when (item.urgency) {
        ProjectUrgency.OVERDUE -> stringResource(R.string.checkout_overdue) to Error
        ProjectUrgency.TODAY -> stringResource(R.string.checkout_starts_today) to Warning
        ProjectUrgency.UPCOMING -> stringResource(R.string.checkout_days_left, item.daysUntilStart.toInt()) to MaterialTheme.colorScheme.primary
        ProjectUrgency.NORMAL -> stringResource(R.string.checkout_days_left, item.daysUntilStart.toInt()) to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        if (item.urgency == ProjectUrgency.OVERDUE) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp).padding(end = 4.dp),
            )
        }
        Text(text, color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}
