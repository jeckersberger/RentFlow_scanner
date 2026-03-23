package com.rentflow.scanner.ui.equipment

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rentflow.scanner.R
import com.rentflow.scanner.ui.components.EquipmentCard
import com.rentflow.scanner.ui.components.LoadingScreen
import com.rentflow.scanner.ui.theme.Success

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EquipmentDetailScreen(
    onBack: () -> Unit,
    viewModel: EquipmentDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

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
                state.equipment!!.rfidTag?.let {
                    Text("RFID: $it", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(16.dp))
                }
                Button(
                    onClick = viewModel::writeRfidTag,
                    enabled = !state.isWritingRfid,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    if (state.isWritingRfid) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    } else {
                        Icon(Icons.Default.Nfc, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text(stringResource(R.string.equipment_write_rfid))
                    }
                }
                state.rfidWriteResult?.let { result ->
                    Spacer(Modifier.height(16.dp))
                    if (result.success) {
                        Text("RFID-Tag erfolgreich geschrieben", color = Success)
                    } else {
                        Text(result.error ?: stringResource(R.string.rfid_write_failed), color = MaterialTheme.colorScheme.error)
                    }
                }
            } else {
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}
