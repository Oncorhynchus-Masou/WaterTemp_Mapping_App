package com.example.watertemperaturemap.api

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Shares expired-session events between the network authenticator and Compose UI. */
object SessionManager {
    private val _sessionExpired = MutableStateFlow(false)
    val sessionExpired: StateFlow<Boolean> = _sessionExpired.asStateFlow()

    fun notifySessionExpired() {
        _sessionExpired.value = true
    }

    fun resetSessionExpired() {
        _sessionExpired.value = false
    }
}
