package com.rentflow.scanner.ui.scan

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rentflow.scanner.R
import com.rentflow.scanner.ui.components.EquipmentCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    onBack: () -> Unit,
    onCheckOut: () -> Unit,
    onCheckIn: () -> Unit,
    onEquipmentDetail: (String) -> Unit,
    viewModel: ScanViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_scan)) },
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
            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.padding(32.dp))
            } else if (state.equipment != null) {
                val eq = state.equipment!!
                EquipmentCard(eq, onClick = { onEquipmentDetail(eq.barcode) })
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(onClick = onCheckOut) {
                        Icon(Icons.Default.Output, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text(stringResource(R.string.nav_checkout))
                    }
                    Button(onClick = onCheckIn) {
                        Icon(Icons.Default.Input, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text(stringResource(R.string.nav_checkin))
                    }
                }
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = { viewModel.clearResult() }) {
                    Text(stringResource(R.string.scan_ready))
                }
            } else {
                Spacer(Modifier.height(64.dp))
                Icon(
                    Icons.Default.QrCodeScanner,
                    contentDescription = null,
                    modifier = Modifier.size(96.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.scan_ready), style = MaterialTheme.typography.headlineMedium)
                state.error?.let {
                    Spacer(Modifier.height(16.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
