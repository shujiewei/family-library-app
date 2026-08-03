package com.familylibrary.app.ui.admin

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AdminModeController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _isAdminMode = MutableStateFlow(false)
    val isAdminMode: StateFlow<Boolean> = _isAdminMode.asStateFlow()

    private var idleJob: Job? = null

    private val _failedAttempts = MutableStateFlow(0)
    val failedAttempts: StateFlow<Int> = _failedAttempts.asStateFlow()

    private val _cooldownUntilMs = MutableStateFlow(0L)
    val cooldownUntilMs: StateFlow<Long> = _cooldownUntilMs.asStateFlow()

    fun enter() {
        _isAdminMode.value = true
        _failedAttempts.value = 0
        restartIdleTimer()
    }

    fun extend() {
        if (_isAdminMode.value) restartIdleTimer()
    }

    fun exit() {
        _isAdminMode.value = false
        idleJob?.cancel()
        idleJob = null
    }

    fun recordFailedAttempt() {
        val n = _failedAttempts.value + 1
        _failedAttempts.value = n
        if (n >= MAX_FAILED) {
            _cooldownUntilMs.value = SystemClock.elapsedRealtime() + COOLDOWN_MS
        }
    }

    fun isInCooldown(): Boolean = SystemClock.elapsedRealtime() < _cooldownUntilMs.value

    fun cooldownRemainingMs(): Long =
        (_cooldownUntilMs.value - SystemClock.elapsedRealtime()).coerceAtLeast(0L)

    fun resetCooldown() {
        _failedAttempts.value = 0
        _cooldownUntilMs.value = 0
    }

    private fun restartIdleTimer() {
        idleJob?.cancel()
        idleJob = scope.launch {
            delay(IDLE_TIMEOUT_MS)
            _isAdminMode.value = false
            idleJob = null
        }
    }

    companion object {
        const val IDLE_TIMEOUT_MS = 10 * 60 * 1000L
        const val MAX_FAILED = 3
        const val COOLDOWN_MS = 5_000L
        const val DEFAULT_PIN = "1234"
    }
}
