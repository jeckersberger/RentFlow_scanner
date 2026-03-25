package com.rentflow.scanner.ui.equipment

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rentflow.scanner.R
import com.rentflow.scanner.ui.components.EquipmentCard
import com.rentflow.scanner.ui.components.LoadingScreen
import com.rentflow.scanner.ui.theme.Cyan
import com.rentflow.scanner.ui.theme.Success
import com.rentflow.scanner.ui.theme.Warning

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EquipmentDetailScreen(
    onBack: () -> Unit,
    viewModel: EquipmentDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var showLocationDialog by remember { mutableStateOf(false) }

    // Overwrite confirmation dialog
    if (state.showOverwriteDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissOverwrite,
            icon = {
                Icon(Icons.Default.Warning, contentDescription = null, tint = Warning, modifier = Modifier.size(32.dp))
            },
            title = { Text(stringResource(R.string.rfid_overwrite_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.rfid_overwrite_existing))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        state.existingTagEpc ?: "",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = Warning,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.rfid_overwrite_confirm))
                }
            },
            confirmButton = {
                Button(
                    onClick = viewModel::confirmOverwrite,
                    colors = ButtonDefaults.buttonColors(containerColor = Warning),
                ) {
                    Text(stringResource(R.string.rfid_overwrite_button))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = viewModel::dismissOverwrite) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    // Location picker dialog
    if (showLocationDialog && state.zones.isNotEmpty()) {
        val currentLocation = state.equipment?.location
        AlertDialog(
            onDismissRequest = { showLocationDialog = false },
            title = { Text(stringResource(R.string.equipment_select_location)) },
            text = {
                LazyColumn {
                    items(state.zones) { zone ->
                        val isSelected = zone.name == currentLocation
                        ListItem(
                            headlineContent = {
                                Text(
                                    zone.name,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                )
                            },
                            trailingContent = {
                                if (isSelected) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Cyan)
                                }
                            },
                            modifier = Modifier.clickable {
                                viewModel.updateLocation(zone.name)
                                showLocationDialog = false
                            },
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                OutlinedButton(onClick = { showLocationDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.equipment_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            if (state.isLoading) {
                LoadingScreen()
            } else if (state.equipment != null) {
                EquipmentCard(state.equipment!!)

                Spacer(Modifier.height(24.dp))

                // Location change
                Button(
                    onClick = { showLocationDialog = true },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Cyan),
                ) {
                    Icon(Icons.Default.EditLocationAlt, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text(stringResource(R.string.equipment_change_location))
                }

                state.locationSaved?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = Success)
                }

                Spacer(Modifier.height(16.dp))

                // RFID section
                state.equipment!!.rfidTag?.let {
                    Text(stringResource(R.string.equipment_rfid_label, it), style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(16.dp))
                }

                Button(
                    onClick = viewModel::writeRfidTag,
                    enabled = !state.isWritingRfid,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    if (state.isWritingRfid) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.rfid_writing))
                    } else {
                        Icon(Icons.Default.Nfc, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text(stringResource(R.string.equipment_write_rfid))
                    }
                }

                state.rfidWriteResult?.let { result ->
                    Spacer(Modifier.height(16.dp))
                    if (result.success) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Success.copy(alpha = 0.15f)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (state.rfidVerified) Icons.Default.VerifiedUser else Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Success,
                                )
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(stringResource(R.string.rfid_write_success), color = Success, fontWeight = FontWeight.Bold)
                                    if (state.rfidVerified) {
                                        Text(stringResource(R.string.rfid_write_verified), style = MaterialTheme.typography.bodySmall, color = Success)
                                    } else {
                                        Text(stringResource(R.string.rfid_write_not_verified), style = MaterialTheme.typography.bodySmall, color = Warning)
                                    }
                                }
                            }
                        }
                    } else {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(
                                        result.error ?: stringResource(R.string.rfid_write_failed),
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    OutlinedButton(onClick = viewModel::writeRfidTag) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(stringResource(R.string.retry))
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}
