package com.rentflow.scanner.data.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionTimeoutManager @Inject constructor() {

    companion object {
        private const val LOCK_TIMEOUT_MS = 30 * 60 * 1000L // 30 minutes
        private const val FULL_RELOGIN_TIMEOUT_MS = 4 * 60 * 60 * 1000L // 4 hours
    }

    private var lastActivityTimestamp: Long = System.currentTimeMillis()
    private var backgroundTimestamp: Long = 0L

    private val _lockState = MutableStateFlow(LockState.UNLOCKED)
    val lockState: StateFlow<LockState> = _lockState

    fun onUserActivity() {
        lastActivityTimestamp = System.currentTimeMillis()
        if (_lockState.value == LockState.UNLOCKED) return
    }

    fun onAppForeground() {
        if (backgroundTimestamp == 0L) return
        val elapsed = System.currentTimeMillis() - backgroundTimestamp
        backgroundTimestamp = 0L

        when {
            elapsed >= FULL_RELOGIN_TIMEOUT_MS -> _lockState.value = LockState.FULL_RELOGIN
            elapsed >= LOCK_TIMEOUT_MS -> _lockState.value = LockState.LOCKED
        }
    }

    fun onAppBackground() {
        backgroundTimestamp = System.currentTimeMillis()
    }

    fun checkInactivity() {
        if (_lockState.value != LockState.UNLOCKED) return
        val elapsed = System.currentTimeMillis() - lastActivityTimestamp
        if (elapsed >= LOCK_TIMEOUT_MS) {
            _lockState.value = LockState.LOCKED
        }
    }

    fun unlock() {
        _lockState.value = LockState.UNLOCKED
        lastActivityTimestamp = System.currentTimeMillis()
    }

    fun requireFullLogin() {
        _lockState.value = LockState.FULL_RELOGIN
    }

    fun reset() {
        _lockState.value = LockState.UNLOCKED
        lastActivityTimestamp = System.currentTimeMillis()
        backgroundTimestamp = 0L
    }
}

enum class LockState {
    UNLOCKED,
    LOCKED,
    FULL_RELOGIN,
}
