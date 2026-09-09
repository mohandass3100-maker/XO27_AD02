package com.acoulink.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.acoulink.audio.AudioConfig
import com.acoulink.audio.AudioSender
import com.acoulink.audio.TransmissionProgressListener
import com.acoulink.data.MessageDirection
import com.acoulink.data.MessageEntity
import com.acoulink.data.MessageRepository
import com.acoulink.protocol.MessageAssembler
import com.acoulink.protocol.Packet
import com.acoulink.protocol.PacketEncoder
import com.acoulink.protocol.ProtocolConstants
import com.acoulink.recovery.RetransmissionManager
import com.acoulink.ui.components.PacketVisualState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class TransmissionState {
    IDLE,
    BROADCASTING,
    WAITING_ACK,
    RETRANSMITTING,
    COMPLETED,
    FAILED,
    STOPPED
}

data class PacketItemUiState(
    val sequenceNumber: Int,
    val total: Int,
    val visualState: PacketVisualState,
    val details: String? = null
)

data class SendUiState(
    val inputText: String = "",
    val characterCount: Int = 0,
    val maxCharacters: Int = ProtocolConstants.MAX_MESSAGE_CHARACTERS,
    val isUrl: Boolean = false,
    val calculatedPackets: Int = 0,
    val state: TransmissionState = TransmissionState.IDLE,
    val currentPacketIndex: Int = 0,
    val totalPackets: Int = 0,
    val confirmedCount: Int = 0,
    val pendingCount: Int = 0,
    val retransmittedCount: Int = 0,
    val statusMessage: String = "Ready to broadcast",
    val packetList: List<PacketItemUiState> = emptyList(),
    val errorMessage: String? = null,
    // Demo simulator option
    val simulateMissingPacketOnReceiver: Boolean = false,
    val simulatePacketDropSeq: Int = 3
)

class SendViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MessageRepository.getInstance(application)
    private val audioSender = AudioSender(AudioConfig())
    private val retransmissionManager = RetransmissionManager()

    private val _uiState = MutableStateFlow(SendUiState())
    val uiState = _uiState.asStateFlow()

    private var activeTransmissionJob: Job? = null
    private var generatedPackets: List<Packet> = emptyList()

    fun onInputTextChanged(newText: String) {
        if (newText.length <= ProtocolConstants.MAX_MESSAGE_CHARACTERS) {
            val isUrl = MessageAssembler.isUrl(newText)
            val packetCount = if (newText.isEmpty()) 0 else (newText.toByteArray(Charsets.UTF_8).size + ProtocolConstants.DEFAULT_MAX_PAYLOAD_SIZE - 1) / ProtocolConstants.DEFAULT_MAX_PAYLOAD_SIZE
            _uiState.value = _uiState.value.copy(
                inputText = newText,
                characterCount = newText.length,
                isUrl = isUrl,
                calculatedPackets = packetCount,
                errorMessage = null
            )
        }
    }

    fun clearInput() {
        _uiState.value = _uiState.value.copy(
            inputText = "",
            characterCount = 0,
            isUrl = false,
            calculatedPackets = 0,
            errorMessage = null
        )
    }

    fun setSimulateMissingPacket(simulate: Boolean, seq: Int = 3) {
        _uiState.value = _uiState.value.copy(
            simulateMissingPacketOnReceiver = simulate,
            simulatePacketDropSeq = seq
        )
    }

    fun startBroadcast() {
        val text = _uiState.value.inputText.trim()
        if (text.isEmpty()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Please enter a message before broadcasting.")
            return
        }

        generatedPackets = PacketEncoder.createDataPackets(text)
        retransmissionManager.initialize(generatedPackets)

        val initialPacketItems = generatedPackets.map {
            PacketItemUiState(
                sequenceNumber = it.sequenceNumber,
                total = it.totalPackets,
                visualState = PacketVisualState.PENDING
            )
        }

        _uiState.value = _uiState.value.copy(
            state = TransmissionState.BROADCASTING,
            currentPacketIndex = 0,
            totalPackets = generatedPackets.size,
            confirmedCount = 0,
            pendingCount = generatedPackets.size,
            retransmittedCount = 0,
            statusMessage = "Broadcasting acoustic packets...",
            packetList = initialPacketItems,
            errorMessage = null
        )

        activeTransmissionJob?.cancel()
        activeTransmissionJob = viewModelScope.launch {
            audioSender.transmitPackets(
                packets = generatedPackets,
                listener = object : TransmissionProgressListener {
                    override fun onPacketTransmitting(currentPacket: Int, totalPackets: Int, packet: Packet) {
                        _uiState.value = _uiState.value.let { state ->
                            val updatedList = state.packetList.map { item ->
                                if (item.sequenceNumber == currentPacket) {
                                    item.copy(visualState = PacketVisualState.TRANSMITTING)
                                } else {
                                    item
                                }
                            }
                            state.copy(
                                currentPacketIndex = currentPacket,
                                statusMessage = "Broadcasting packet $currentPacket of $totalPackets...",
                                packetList = updatedList
                            )
                        }
                    }

                    override fun onPacketTransmitted(currentPacket: Int, totalPackets: Int, packet: Packet) {
                        _uiState.value = _uiState.value.let { state ->
                            val updatedList = state.packetList.map { item ->
                                if (item.sequenceNumber == currentPacket) {
                                    item.copy(visualState = PacketVisualState.CONFIRMED)
                                } else {
                                    item
                                }
                            }
                            val confirmed = updatedList.count { it.visualState == PacketVisualState.CONFIRMED }
                            val pending = totalPackets - confirmed
                            state.copy(
                                confirmedCount = confirmed,
                                pendingCount = pending,
                                packetList = updatedList
                            )
                        }
                    }

                    override fun onTransmissionComplete(totalPackets: Int) {
                        // After sending all packets, transition to WAITING_ACK
                        _uiState.value = _uiState.value.copy(
                            state = TransmissionState.WAITING_ACK,
                            statusMessage = "Waiting for receiver acknowledgements..."
                        )

                        // Check if demo simulation was requested for retransmission
                        viewModelScope.launch {
                            if (_uiState.value.simulateMissingPacketOnReceiver) {
                                delay(1200) // Simulated slot delay for NACK arrival
                                handleIncomingNack(listOf(_uiState.value.simulatePacketDropSeq))
                            } else {
                                delay(ProtocolConstants.ACK_WAIT_TIMEOUT_MS)
                                finishTransmissionSuccess()
                            }
                        }
                    }

                    override fun onTransmissionStopped() {
                        _uiState.value = _uiState.value.copy(
                            state = TransmissionState.STOPPED,
                            statusMessage = "Transmission stopped by user"
                        )
                    }

                    override fun onError(message: String) {
                        _uiState.value = _uiState.value.copy(
                            state = TransmissionState.FAILED,
                            errorMessage = message,
                            statusMessage = "Broadcast failed"
                        )
                    }
                }
            )
        }
    }

    private suspend fun handleIncomingNack(missingSeqs: List<Int>) {
        if (!retransmissionManager.canRetry) {
            finishTransmissionSuccess()
            return
        }

        _uiState.value = _uiState.value.copy(
            state = TransmissionState.RETRANSMITTING,
            statusMessage = "NACK received: Retransmitting packet(s) ${missingSeqs.joinToString()}..."
        )

        val packetsToResend = retransmissionManager.getPacketsForRetransmission(missingSeqs)
        if (packetsToResend.isNotEmpty()) {
            // Update UI packet state
            _uiState.value = _uiState.value.let { state ->
                val updatedList = state.packetList.map { item ->
                    if (missingSeqs.contains(item.sequenceNumber)) {
                        item.copy(visualState = PacketVisualState.RETRANSMITTING, details = "Retransmitted")
                    } else item
                }
                state.copy(
                    packetList = updatedList,
                    retransmittedCount = state.retransmittedCount + packetsToResend.size
                )
            }

            // Retransmit
            audioSender.transmitPackets(packetsToResend)
            delay(500)
        }

        finishTransmissionSuccess()
    }

    private suspend fun finishTransmissionSuccess() {
        val text = _uiState.value.inputText.trim()
        val firstPacket = generatedPackets.firstOrNull()

        // Auto-save sent message if history enabled
        val entity = MessageEntity(
            messageId = firstPacket?.formattedMessageId ?: "AC0000",
            content = text,
            type = if (_uiState.value.isUrl) "URL" else "TEXT",
            direction = MessageDirection.SENT,
            packetCount = generatedPackets.size,
            validPacketCount = generatedPackets.size,
            retransmittedPacketCount = _uiState.value.retransmittedCount,
            crcStatus = "VERIFIED",
            recoveryStatus = if (_uiState.value.retransmittedCount > 0) "RECOVERED" else "NONE",
            transmissionStatus = "SUCCESS"
        )
        repository.saveMessageWithConsent(entity)

        _uiState.value = _uiState.value.copy(
            state = TransmissionState.COMPLETED,
            statusMessage = "Broadcast completed successfully"
        )
    }

    fun stopBroadcast() {
        activeTransmissionJob?.cancel()
        audioSender.stop()
        _uiState.value = _uiState.value.copy(
            state = TransmissionState.STOPPED,
            statusMessage = "Broadcast stopped by user"
        )
    }

    fun reset() {
        stopBroadcast()
        _uiState.value = SendUiState()
    }

    override fun onCleared() {
        super.onCleared()
        audioSender.stop()
    }
}
