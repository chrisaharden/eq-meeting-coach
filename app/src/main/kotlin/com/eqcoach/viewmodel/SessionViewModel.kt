package com.eqcoach.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eqcoach.config.AppConfig
import com.eqcoach.model.SessionState
import com.eqcoach.model.Verdict
import com.eqcoach.network.ServerException
import com.eqcoach.service.CaptureService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SessionViewModel : ViewModel() {

    companion object {
        private const val TAG = "SessionViewModel"
    }

    private val _sessionState = MutableStateFlow(SessionState.IDLE)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    private val _currentVerdict = MutableStateFlow(Verdict.GRAY)
    val currentVerdict: StateFlow<Verdict> = _currentVerdict.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var captureService: CaptureService? = null
    private var pollJob: Job? = null

    fun setCaptureService(service: CaptureService) {
        captureService = service
    }

    fun startSession() {
        if (_sessionState.value == SessionState.ACTIVE) return

        _sessionState.value = SessionState.ACTIVE
        _currentVerdict.value = Verdict.GRAY

        captureService?.startCapture()
        startPolling()
    }

    fun stopSession() {
        pollJob?.cancel()
        pollJob = null

        captureService?.stopCapture()

        _currentVerdict.value = Verdict.GRAY
        _sessionState.value = SessionState.IDLE
    }

    fun clearError() {
        _errorMessage.value = null
    }

    private fun startPolling() {
        pollJob = viewModelScope.launch {
            while (isActive && _sessionState.value == SessionState.ACTIVE) {
                try {
                    captureService?.let { service ->
                        val verdict = service.getCurrentVerdict()
                        _currentVerdict.value = verdict
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: ServerException) {
                    _currentVerdict.value = Verdict.GRAY
                    _errorMessage.value = e.message ?: "Connection error"
                    Log.w(TAG, "Server error", e)
                } catch (e: Exception) {
                    Log.w(TAG, "Polling error", e)
                }
                delay(AppConfig.CAPTURE_INTERVAL_SECONDS * 1000)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopSession()
    }
}
