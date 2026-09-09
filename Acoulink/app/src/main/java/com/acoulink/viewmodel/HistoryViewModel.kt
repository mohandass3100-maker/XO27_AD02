package com.acoulink.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.acoulink.data.MessageDirection
import com.acoulink.data.MessageEntity
import com.acoulink.data.MessageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

enum class HistoryCategory {
    ALL,
    SENT,
    RECEIVED,
    RECOVERED,
    FAILED
}

data class HistoryUiState(
    val selectedCategory: HistoryCategory = HistoryCategory.ALL,
    val searchQuery: String = "",
    val sortAscending: Boolean = false, // Default: newest first
    val messages: List<MessageEntity> = emptyList(),
    val showClearConfirmation: Boolean = false,
    val selectedMessageForDetails: MessageEntity? = null
)

class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MessageRepository.getInstance(application)

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadMessages()
    }

    fun setCategory(category: HistoryCategory) {
        _uiState.value = _uiState.value.copy(selectedCategory = category)
        loadMessages()
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        loadMessages()
    }

    fun toggleSortOrder() {
        _uiState.value = _uiState.value.copy(sortAscending = !_uiState.value.sortAscending)
        loadMessages()
    }

    fun showClearConfirmation(show: Boolean) {
        _uiState.value = _uiState.value.copy(showClearConfirmation = show)
    }

    fun selectMessageForDetails(message: MessageEntity?) {
        _uiState.value = _uiState.value.copy(selectedMessageForDetails = message)
    }

    fun deleteMessage(id: Long) {
        viewModelScope.launch {
            repository.deleteMessage(id)
            if (_uiState.value.selectedMessageForDetails?.id == id) {
                _uiState.value = _uiState.value.copy(selectedMessageForDetails = null)
            }
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearAllHistory()
            _uiState.value = _uiState.value.copy(showClearConfirmation = false, selectedMessageForDetails = null)
        }
    }

    private fun loadMessages() {
        viewModelScope.launch {
            val query = _uiState.value.searchQuery.trim()
            val category = _uiState.value.selectedCategory
            val sortAsc = _uiState.value.sortAscending

            val flow = when {
                query.isNotEmpty() -> repository.searchMessages(query)
                category == HistoryCategory.SENT -> repository.getMessagesByDirection(MessageDirection.SENT)
                category == HistoryCategory.RECEIVED -> repository.getMessagesByDirection(MessageDirection.RECEIVED)
                category == HistoryCategory.RECOVERED -> repository.getRecoveredMessages()
                category == HistoryCategory.FAILED -> repository.getFailedMessages()
                else -> repository.getAllMessages(sortAsc)
            }

            flow.collectLatest { list ->
                _uiState.value = _uiState.value.copy(messages = list)
            }
        }
    }
}
