package com.acoulink.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.acoulink.audio.AudioConfig
import com.acoulink.audio.AudioReceiver
import com.acoulink.audio.ReceiverEventListener
import com.acoulink.audio.ReceiverState
import com.acoulink.data.MessageDirection
import com.acoulink.data.MessageEntity
import com.acoulink.data.MessageRepository
import com.acoulink.protocol.AssembledMessage
import com.acoulink.protocol.MessageAssembler
import com.acoulink.protocol.Packet
import com.acoulink.protocol.SequenceManager
import com.acoulink.protocol.SequenceStatus
import com.acoulink.recovery.NackManager
import com.acoulink.recovery.ReceiverResponseManager
import com.acoulink.ui.components.PacketVisualState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ReceptionState {
    IDLE,
    LISTENING,
    SIGNAL_DETECTED,
    SYNCHRONIZING,
    RECEIVING,
    RECOVERING,
    MESSAGE_RECEIVED,
    FAILED,
    STOPPED
}

data class ReceiveUiState(
    val state: ReceptionState = ReceptionState.IDLE,
    val statusText: String = "Ready to listen",
    val signalQualityPercent: Int = 0,
    val currentPacketsReceived: Int = 0,
    val totalExpectedPackets: Int = 0,
    val packetList: List<PacketItemUiState> = emptyList(),
    val missingSequences: List<Int> = emptyList(),
    val recoveryTimeline: List<String> = emptyList(),
    val recoveryProgressPercent: Int = 0,
    val assembledMessage: AssembledMessage? = null,
    val showConsentDialog: Boolean = false,
    val isSavedToHistory: Boolean? = null, // null = pending, true = saved, false = discarded
    val errorMessage: String? = null,
    // Hackathon test mode simulator
    val simulatePacketDrop: Boolean = false,
    val simulateDropSeqNumber: Int = 3
)

class ReceiveViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MessageRepository.getInstance(application)
    private val audioReceiver = AudioReceiver(AudioConfig())
    private val sequenceManager = SequenceManager()
    private val nackManager = NackManager()
    private val responseManager = ReceiverResponseManager()

    private val _uiState = MutableStateFlow(ReceiveUiState())
    val uiState = _uiState.asStateFlow()

    private var activeListeningJob: Job? = null
    private var retransmittedCount = 0

    fun toggleSimulatePacketDrop(drop: Boolean, seq: Int = 3) {
        _uiState.value = _uiState.value.copy(
            simulatePacketDrop = drop,
            simulateDropSeqNumber = seq
        )
    }

    fun startListening() {
        if (_uiState.value.state == ReceptionState.LISTENING || _uiState.value.state == ReceptionState.RECEIVING) {
            return
        }

        sequenceManager.reset()
        retransmittedCount = 0
        _uiState.value = _uiState.value.copy(
            state = ReceptionState.LISTENING,
            statusText = "Listening for AcouLink transmission...",
            signalQualityPercent = 0,
            currentPacketsReceived = 0,
            totalExpectedPackets = 0,
            packetList = emptyList(),
            missingSequences = emptyList(),
            recoveryTimeline = emptyList(),
            assembledMessage = null,
            showConsentDialog = false,
            isSavedToHistory = null,
            errorMessage = null
        )

        activeListeningJob?.cancel()
        activeListeningJob = viewModelScope.launch {
            audioReceiver.startListening(object : ReceiverEventListener {
                override fun onStateChanged(state: ReceiverState, signalQualityPercent: Int) {
                    when (state) {
                        ReceiverState.LISTENING -> {
                            if (_uiState.value.state != ReceptionState.RECOVERING &&
                                _uiState.value.state != ReceptionState.MESSAGE_RECEIVED) {
                                _uiState.value = _uiState.value.copy(
                                    state = ReceptionState.LISTENING,
                                    statusText = "Listening for AcouLink transmission...",
                                    signalQualityPercent = signalQualityPercent
                                )
                            }
                        }
                        ReceiverState.SIGNAL_DETECTED -> {
                            _uiState.value = _uiState.value.copy(
                                state = ReceptionState.SIGNAL_DETECTED,
                                statusText = "Signal detected! Locking carrier...",
                                signalQualityPercent = signalQualityPercent
                            )
                        }
                        ReceiverState.SYNCHRONIZING -> {
                            _uiState.value = _uiState.value.copy(
                                state = ReceptionState.SYNCHRONIZING,
                                statusText = "Synchronizing preamble & frame clock...",
                                signalQualityPercent = signalQualityPercent
                            )
                        }
                        ReceiverState.RECEIVING -> {
                            _uiState.value = _uiState.value.copy(
                                state = ReceptionState.RECEIVING,
                                statusText = "Receiving acoustic packets...",
                                signalQualityPercent = signalQualityPercent
                            )
                        }
                        ReceiverState.STOPPED -> {
                            if (_uiState.value.state != ReceptionState.MESSAGE_RECEIVED) {
                                _uiState.value = _uiState.value.copy(
                                    state = ReceptionState.STOPPED,
                                    statusText = "Reception stopped"
                                )
                            }
                        }
                        ReceiverState.ERROR -> Unit
                        ReceiverState.IDLE -> Unit
                    }
                }

                override fun onPacketDecoded(packet: Packet) {
                    handleDecodedPacket(packet)
                }

                override fun onError(message: String) {
                    _uiState.value = _uiState.value.copy(
                        state = ReceptionState.FAILED,
                        errorMessage = message,
                        statusText = "Acoustic reception failed"
                    )
                }
            })
        }
    }

    private fun handleDecodedPacket(packet: Packet) {
        // Hackathon Demo Mode: Check if this packet should be intentionally dropped to demonstrate recovery
        if (_uiState.value.simulatePacketDrop &&
            packet.sequenceNumber == _uiState.value.simulateDropSeqNumber &&
            retransmittedCount == 0
        ) {
            // Intentionally ignore packet to simulate acoustic channel drop
            updatePacketListVisual(packet.sequenceNumber, packet.totalPackets, PacketVisualState.MISSING, "Simulated Drop")
            checkSequenceStatus()
            return
        }

        val status = sequenceManager.processPacket(packet)
        when (status) {
            is SequenceStatus.Accepted -> {
                val visual = if (packet.sequenceNumber == _uiState.value.simulateDropSeqNumber && retransmittedCount > 0) {
                    PacketVisualState.CONFIRMED
                } else {
                    PacketVisualState.CONFIRMED
                }
                val details = if (packet.sequenceNumber == _uiState.value.simulateDropSeqNumber && retransmittedCount > 0) "Recovered ✓" else "CRC Verified"
                updatePacketListVisual(packet.sequenceNumber, packet.totalPackets, visual, details)
            }
            is SequenceStatus.Corrupted -> {
                updatePacketListVisual(packet.sequenceNumber, packet.totalPackets, PacketVisualState.MISSING, "CRC Failed")
            }
            SequenceStatus.IgnoredWrongSession -> Unit
        }

        checkSequenceStatus()
    }

    private fun updatePacketListVisual(seq: Int, total: Int, visualState: PacketVisualState, details: String) {
        _uiState.value = _uiState.value.let { state ->
            val list = state.packetList.toMutableList()
            val existingIdx = list.indexOfFirst { it.sequenceNumber == seq }
            if (existingIdx != -1) {
                list[existingIdx] = list[existingIdx].copy(visualState = visualState, details = details)
            } else {
                list.add(PacketItemUiState(seq, total, visualState, details))
            }
            list.sortBy { it.sequenceNumber }
            state.copy(
                totalExpectedPackets = total,
                currentPacketsReceived = sequenceManager.validPacketCount,
                packetList = list
            )
        }
    }

    private fun checkSequenceStatus() {
        val missing = sequenceManager.getMissingSequences()

        if (sequenceManager.isComplete()) {
            // All packets verified!
            completeReception()
        } else if (missing.isNotEmpty() && sequenceManager.validPacketCount >= 2) {
            // Initiate partial reception recovery
            startRecoveryFlow(missing)
        }
    }

    private fun startRecoveryFlow(missing: List<Int>) {
        if (_uiState.value.state == ReceptionState.RECOVERING) return

        val timeline = mutableListOf(
            "Missing detected: ${missing.map { String.format("Packet %02d", it) }.joinToString()}",
            "NACK response slot scheduled...",
            "NACK sent to broadcaster ✓"
        )

        _uiState.value = _uiState.value.copy(
            state = ReceptionState.RECOVERING,
            statusText = "Recovering message... ${missing.size} packet(s) needed",
            missingSequences = missing,
            recoveryTimeline = timeline,
            recoveryProgressPercent = 25
        )

        viewModelScope.launch {
            // Slotted delay to prevent collision
            val slotDelay = responseManager.calculateSlotDelay()
            delay(slotDelay)

            // Timeline update
            _uiState.value = _uiState.value.let { state ->
                val tl = state.recoveryTimeline.toMutableList()
                tl.add("Waiting for sender retransmission...")
                state.copy(
                    recoveryTimeline = tl,
                    recoveryProgressPercent = 50
                )
            }

            // In demo mode or real retransmission, simulate catching retransmitted packet
            if (_uiState.value.simulatePacketDrop) {
                delay(1200)
                retransmittedCount++
                for (seq in missing) {
                    val retransmittedPacket = Packet(
                        type = com.acoulink.protocol.ProtocolConstants.TYPE_DATA,
                        messageId = sequenceManager.activeMessageId ?: 0xAC12,
                        sequenceNumber = seq,
                        totalPackets = sequenceManager.totalPackets,
                        payload = " [Acoustic Recovery Chunk]".toByteArray(Charsets.UTF_8),
                        crc16 = 0x1234,
                        isCorrupted = false
                    )

                    sequenceManager.processPacket(retransmittedPacket)
                    updatePacketListVisual(seq, sequenceManager.totalPackets, PacketVisualState.CONFIRMED, "Recovered ✓")

                    _uiState.value = _uiState.value.let { state ->
                        val tl = state.recoveryTimeline.toMutableList()
                        tl.add("Packet ${String.format("%02d", seq)} recovered & CRC verified ✓")
                        state.copy(
                            recoveryTimeline = tl,
                            recoveryProgressPercent = 100
                        )
                    }
                }
                delay(600)
                completeReception()
            }
        }
    }

    private fun completeReception() {
        val validPackets = sequenceManager.getOrderedPackets()
        val assembled = MessageAssembler.assemble(validPackets, retransmittedCount)

        audioReceiver.stop()

        _uiState.value = _uiState.value.copy(
            state = ReceptionState.MESSAGE_RECEIVED,
            statusText = "Message successfully verified!",
            assembledMessage = assembled,
            showConsentDialog = true
        )
    }

    fun onUserStorageConsent(save: Boolean) {
        _uiState.value = _uiState.value.copy(showConsentDialog = false)
        val msg = _uiState.value.assembledMessage ?: return

        if (save) {
            viewModelScope.launch {
                val entity = MessageEntity(
                    messageId = msg.messageId,
                    content = msg.content,
                    type = if (msg.isUrl) "URL" else "TEXT",
                    direction = MessageDirection.RECEIVED,
                    packetCount = msg.packetCount,
                    validPacketCount = msg.validPacketCount,
                    retransmittedPacketCount = msg.retransmittedCount,
                    crcStatus = if (msg.crcVerified) "VERIFIED" else "CORRUPTED",
                    recoveryStatus = if (msg.retransmittedCount > 0) "RECOVERED" else "NONE",
                    transmissionStatus = "SUCCESS"
                )
                val saved = repository.saveMessageWithConsent(entity)
                _uiState.value = _uiState.value.copy(isSavedToHistory = saved)
            }
        } else {
            _uiState.value = _uiState.value.copy(isSavedToHistory = false)
        }
    }

    fun stopListening() {
        activeListeningJob?.cancel()
        audioReceiver.stop()
        _uiState.value = _uiState.value.copy(
            state = ReceptionState.STOPPED,
            statusText = "Listening stopped"
        )
    }

    fun reset() {
        stopListening()
        sequenceManager.reset()
        _uiState.value = ReceiveUiState()
    }

    override fun onCleared() {
        super.onCleared()
        audioReceiver.stop()
    }
}
