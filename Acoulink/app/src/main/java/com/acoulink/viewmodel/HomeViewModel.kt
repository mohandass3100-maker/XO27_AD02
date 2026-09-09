package com.acoulink.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.acoulink.data.MessageDirection
import com.acoulink.data.MessageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class HomeUiState(
    val statusText: String = "Offline • Ready",
    val isReady: Boolean = true,
    val totalSentCount: Int = 0,
    val totalReceivedCount: Int = 0
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MessageRepository.getInstance(application)

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getAllMessages().collectLatest { messages ->
                val sent = messages.count { it.direction == MessageDirection.SENT }
                val received = messages.count { it.direction == MessageDirection.RECEIVED }
                _uiState.value = _uiState.value.copy(
                    totalSentCount = sent,
                    totalReceivedCount = received
                )
            }
        }
    }
}
