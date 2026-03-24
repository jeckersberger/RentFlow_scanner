package com.rentflow.scanner.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rentflow.scanner.R
import com.rentflow.scanner.ui.components.PendingQueueBadge
import com.rentflow.scanner.ui.components.UpdateBanner
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToScan: () -> Unit,
    onNavigateToCheckOut: () -> Unit,
    onNavigateToCheckIn: () -> Unit,
    onNavigateToInventory: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onLogout: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_title)) },
                actions = {
                    PendingQueueBadge(state.pendingScanCount)
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.nav_settings))
                    }
                    IconButton(onClick = {
                        viewModel.logout()
                        onLogout()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Logout")
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            state.updateInfo?.let { info ->
                UpdateBanner(
                    updateInfo = info,
                    onUpdate = { viewModel.downloadUpdate() },
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            if (state.userName.isNotBlank()) {
                Text("Hallo, ${state.userName}", style = MaterialTheme.typography.headlineMedium)
            }

            WorkflowCard(Icons.Default.QrCodeScanner, stringResource(R.string.nav_scan), onClick = onNavigateToScan)
            WorkflowCard(Icons.Default.Output, stringResource(R.string.nav_checkout), onClick = onNavigateToCheckOut)
            WorkflowCard(Icons.Default.Input, stringResource(R.string.nav_checkin), onClick = onNavigateToCheckIn)
            WorkflowCard(Icons.Default.Inventory, stringResource(R.string.nav_inventory), onClick = onNavigateToInventory)
        }
    }
}

@Composable
private fun WorkflowCard(icon: ImageVector, label: String, onClick: () -> Unit, iconTint: Color? = null) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp), tint = iconTint ?: LocalContentColor.current)
            Text(label, style = MaterialTheme.typography.titleLarge)
        }
    }
}
