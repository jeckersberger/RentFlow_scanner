package com.rentflow.scanner.ui.adhoc

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rentflow.scanner.R
import com.rentflow.scanner.ui.components.EquipmentCard
import com.rentflow.scanner.ui.theme.Error
import com.rentflow.scanner.ui.theme.Success
import com.rentflow.scanner.ui.theme.Warning

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdHocBookingScreen(
    onBack: () -> Unit,
    viewModel: AdHocBookingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    // Confirmation dialog with extra protection
    if (state.showConfirmDialog && state.scannedEquipment != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissConfirm,
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
                Text(stringResource(R.string.adhoc_confirm_message, state.scannedEquipment!!.name))
            },
            confirmButton = {
                Button(
                    onClick = viewModel::confirmBooking,
                    colors = ButtonDefaults.buttonColors(containerColor = Warning),
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = viewModel::dismissConfirm) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.adhoc_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Warning banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Warning.copy(alpha = 0.15f)),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Warning, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.adhoc_description),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Warning,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // Success message
            state.successMessage?.let { name ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Success.copy(alpha = 0.15f)),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Success, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.adhoc_success, name),
                            color = Success,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }

            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.padding(32.dp))
            } else if (state.scannedEquipment != null) {
                EquipmentCard(state.scannedEquipment!!)
                Spacer(Modifier.height(24.dp))

                // Two-step confirmation: Button opens dialog
                Button(
                    onClick = viewModel::requestBooking,
                    colors = ButtonDefaults.buttonColors(containerColor = Warning),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    Icon(Icons.Default.Warehouse, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text(stringResource(R.string.adhoc_title), fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = viewModel::clearResult) {
                    Text(stringResource(R.string.cancel))
                }
            } else {
                Spacer(Modifier.height(48.dp))
                Icon(
                    Icons.Default.QrCodeScanner,
                    contentDescription = null,
                    modifier = Modifier.size(96.dp),
                    tint = Warning,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.adhoc_scan_prompt),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.error?.let {
                    Spacer(Modifier.height(16.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
