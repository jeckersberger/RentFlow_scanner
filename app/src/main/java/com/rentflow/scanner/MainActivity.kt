package com.rentflow.scanner

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.rentflow.scanner.data.hardware.HardwareScanner
import com.rentflow.scanner.data.preferences.SettingsDataStore
import com.rentflow.scanner.data.repository.AuthRepository
import com.rentflow.scanner.data.service.FindMyScannerService
import com.rentflow.scanner.data.service.FindMyScannerWorker
import com.rentflow.scanner.data.service.LockState
import com.rentflow.scanner.data.service.SessionTimeoutManager
import com.rentflow.scanner.ui.findme.FindMeOverlay
import com.rentflow.scanner.ui.lock.LockScreen
import com.rentflow.scanner.ui.navigation.AppNavigation
import com.rentflow.scanner.ui.navigation.Routes
import com.rentflow.scanner.ui.theme.RentFlowScannerTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var findMyScannerService: FindMyScannerService
    @Inject lateinit var sessionTimeoutManager: SessionTimeoutManager
    @Inject lateinit var hardwareScanner: HardwareScanner
    @Inject lateinit var settingsDataStore: SettingsDataStore

    private var isRfidScanning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val startDest = if (authRepository.isLoggedIn()) Routes.HOME else Routes.LOGIN

        // Start polling for ring commands
        FindMyScannerWorker.startPolling(this)

        // Track lifecycle for session timeout
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> sessionTimeoutManager.onAppForeground()
                Lifecycle.Event.ON_PAUSE -> sessionTimeoutManager.onAppBackground()
                else -> {}
            }
        })

        setContent {
            RentFlowScannerTheme {
                var isRinging by remember { mutableStateOf(false) }
                val lockState by sessionTimeoutManager.lockState.collectAsState()

                // Check ring state periodically
                LaunchedEffect(Unit) {
                    while (true) {
                        isRinging = findMyScannerService.isRinging()
                        kotlinx.coroutines.delay(500)
                    }
                }

                // Check inactivity periodically
                LaunchedEffect(Unit) {
                    while (true) {
                        sessionTimeoutManager.checkInactivity()
                        kotlinx.coroutines.delay(60_000)
                    }
                }

                when {
                    isRinging -> {
                        FindMeOverlay(onStop = {
                            findMyScannerService.stop()
                            isRinging = false
                        })
                    }
                    lockState == LockState.FULL_RELOGIN -> {
                        // Full re-login required
                        authRepository.logout()
                        sessionTimeoutManager.reset()
                        AppNavigation(startDestination = Routes.LOGIN)
                    }
                    lockState == LockState.LOCKED -> {
                        var lockError by remember { mutableStateOf<String?>(null) }
                        var isUnlocking by remember { mutableStateOf(false) }

                        LockScreen(
                            onUnlock = { sessionTimeoutManager.unlock() },
                            onFullLogin = {
                                authRepository.logout()
                                sessionTimeoutManager.reset()
                            },
                            onPasswordSubmit = { password ->
                                isUnlocking = true
                                lockError = null
                                // For demo/offline, accept any non-empty password if logged in
                                if (authRepository.isLoggedIn()) {
                                    sessionTimeoutManager.unlock()
                                } else {
                                    lockError = "Falsches Passwort"
                                }
                                isUnlocking = false
                            },
                            error = lockError,
                            isLoading = isUnlocking,
                        )
                    }
                    else -> {
                        AppNavigation(startDestination = startDest)
                    }
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Hardware trigger = KEY_NUMERIC_POUND (523) or KEY_F9 (0x208=520) / KEY_F10 (0x209=521)
        if (keyCode == 523 || keyCode == 520 || keyCode == 521 || keyCode == KeyEvent.KEYCODE_F9 || keyCode == KeyEvent.KEYCODE_F10) {
            val mode = kotlinx.coroutines.runBlocking {
                settingsDataStore.scanMode.first()
            }
            if (mode == SettingsDataStore.SCAN_MODE_RFID) {
                if (!isRfidScanning) {
                    hardwareScanner.startRfidBulkRead()
                    isRfidScanning = true
                }
                return true
            }
            // Barcode mode: let ScanManager handle it via broadcast
            return super.onKeyDown(keyCode, event)
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == 523 || keyCode == 520 || keyCode == 521 || keyCode == KeyEvent.KEYCODE_F9 || keyCode == KeyEvent.KEYCODE_F10) {
            val mode = kotlinx.coroutines.runBlocking {
                settingsDataStore.scanMode.first()
            }
            if (mode == SettingsDataStore.SCAN_MODE_RFID) {
                if (isRfidScanning) {
                    hardwareScanner.stopRfid()
                    isRfidScanning = false
                }
                return true
            }
            return super.onKeyUp(keyCode, event)
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        sessionTimeoutManager.onUserActivity()
    }

    override fun onDestroy() {
        super.onDestroy()
        findMyScannerService.stop()
    }
}
