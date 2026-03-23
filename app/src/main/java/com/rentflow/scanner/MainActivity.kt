package com.rentflow.scanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.rentflow.scanner.data.repository.AuthRepository
import com.rentflow.scanner.ui.navigation.AppNavigation
import com.rentflow.scanner.ui.navigation.Routes
import com.rentflow.scanner.ui.theme.RentFlowScannerTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var authRepository: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val startDest = if (authRepository.isLoggedIn()) Routes.HOME else Routes.LOGIN
        setContent {
            RentFlowScannerTheme {
                AppNavigation(startDestination = startDest)
            }
        }
    }
}
