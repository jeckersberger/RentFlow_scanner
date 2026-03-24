package com.rentflow.scanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.rentflow.scanner.data.repository.AuthRepository
import com.rentflow.scanner.data.service.FindMyScannerService
import com.rentflow.scanner.data.service.FindMyScannerWorker
import com.rentflow.scanner.ui.findme.FindMeOverlay
import com.rentflow.scanner.ui.navigation.AppNavigation
import com.rentflow.scanner.ui.navigation.Routes
import com.rentflow.scanner.ui.theme.RentFlowScannerTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var findMyScannerService: FindMyScannerService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val startDest = if (authRepository.isLoggedIn()) Routes.HOME else Routes.LOGIN

        // Start polling for ring commands
        FindMyScannerWorker.startPolling(this)

        setContent {
            RentFlowScannerTheme {
                var isRinging by remember { mutableStateOf(false) }

                // Check ring state periodically
                LaunchedEffect(Unit) {
                    while (true) {
                        isRinging = findMyScannerService.isRinging()
                        kotlinx.coroutines.delay(500)
                    }
                }

                if (isRinging) {
                    FindMeOverlay(onStop = {
                        findMyScannerService.stop()
                        isRinging = false
                    })
                } else {
                    AppNavigation(startDestination = startDest)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        findMyScannerService.stop()
    }
}
