package com.acoulink.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.acoulink.audio.FrequencyProfile
import com.acoulink.data.MessageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

data class SettingsUiState(
    // Communication
    val frequencyProfile: FrequencyProfile = FrequencyProfile.STANDARD_AUDIBLE,
    val transmissionSpeedMs: Int = 35,
    val maxPayloadSize: Int = 24,
    val maxRetries: Int = 3,
    val responseSlotMs: Long = 300L,

    // Reliability
    val isCrcEnabled: Boolean = true,
    val isAutoRetransmitEnabled: Boolean = true,
    val isDuplicateDetectionEnabled: Boolean = true,
    val isSequenceVerificationEnabled: Boolean = true,

    // Privacy & Storage
    val askBeforeSaving: Boolean = true,
    val isHistoryEnabled: Boolean = true,
    val showClearConfirmation: Boolean = false,

    // Appearance
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,

    // Demo Mode Testing
    val isDemoModeActive: Boolean = true,
    val demoDropPacketSeq: Int = 3
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MessageRepository.getInstance(application)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.isHistoryEnabled.collect { enabled ->
                _uiState.value = _uiState.value.copy(isHistoryEnabled = enabled)
            }
        }
        viewModelScope.launch {
            repository.askBeforeSaving.collect { ask ->
                _uiState.value = _uiState.value.copy(askBeforeSaving = ask)
            }
        }
    }

    fun setFrequencyProfile(profile: FrequencyProfile) {
        _uiState.value = _uiState.value.copy(
            frequencyProfile = profile,
            transmissionSpeedMs = profile.defaultSymbolDurationMs
        )
    }

    fun setMaxRetries(retries: Int) {
        _uiState.value = _uiState.value.copy(maxRetries = retries)
    }

    fun setAskBeforeSaving(ask: Boolean) {
        repository.setAskBeforeSaving(ask)
    }

    fun setHistoryEnabled(enabled: Boolean) {
        repository.setHistoryEnabled(enabled)
    }

    fun setThemeMode(mode: AppThemeMode) {
        _uiState.value = _uiState.value.copy(themeMode = mode)
    }

    fun setDemoModeActive(active: Boolean) {
        _uiState.value = _uiState.value.copy(isDemoModeActive = active)
    }

    fun setDemoDropPacketSeq(seq: Int) {
        _uiState.value = _uiState.value.copy(demoDropPacketSeq = seq)
    }

    fun showClearConfirmation(show: Boolean) {
        _uiState.value = _uiState.value.copy(showClearConfirmation = show)
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearAllHistory()
            _uiState.value = _uiState.value.copy(showClearConfirmation = false)
        }
    }
}
