package com.rentflow.scanner.ui.checkout

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rentflow.scanner.R
import com.rentflow.scanner.ui.components.EquipmentCard

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.checkout_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
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
                Spacer(Modifier.height(16.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.projects) { project ->
                        Card(onClick = { viewModel.selectProject(project) }, modifier = Modifier.fillMaxWidth()) {
                            Text(project.name, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            } else {
                Text("${stringResource(R.string.checkout_title)}: ${state.selectedProject!!.name}", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.checkout_scan_items), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                }
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.scannedItems) { item ->
                        EquipmentCard(item)
                    }
                }
                if (state.scannedItems.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = viewModel::completeCheckOut,
                        enabled = !state.isLoading,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) {
                        Text("${stringResource(R.string.checkout_confirm)} (${state.scannedItems.size})")
                    }
                }
            }
        }
    }
}
