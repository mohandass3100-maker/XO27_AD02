package com.acoulink.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.acoulink.audio.AudioConfig
import com.acoulink.audio.AudioReceiver
import com.acoulink.audio.AudioSender
import com.acoulink.audio.ReceiverEventListener
import com.acoulink.audio.ReceiverState
import com.acoulink.data.MessageDirection
import com.acoulink.data.MessageEntity
import com.acoulink.data.MessageRepository
import com.acoulink.protocol.AssembledMessage
import com.acoulink.protocol.MessageAssembler
import com.acoulink.protocol.Packet
import com.acoulink.protocol.PacketEncoder
import com.acoulink.protocol.ProtocolConstants
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
    // Dynamic Group / Surprise Challenge 2 Discovery
    val senderDetected: Boolean = false,
    val latestDiscoveredMessageId: Int? = null,
    val crcErrorsCount: Int = 0,
    // Hackathon test mode simulator
    val simulatePacketDrop: Boolean = false,
    val simulateDropSeqNumber: Int = 3
)

class ReceiveViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MessageRepository.getInstance(application)
    private val audioConfig = AudioConfig()
    private val audioReceiver = AudioReceiver(audioConfig)
    private val audioSender = AudioSender(audioConfig)
    private val sequenceManager = SequenceManager()
    private val nackManager = NackManager()
    private val responseManager = ReceiverResponseManager()

    private val _uiState = MutableStateFlow(ReceiveUiState())
    val uiState = _uiState.asStateFlow()

    private var activeListeningJob: Job? = null
    private var retransmittedCount = 0
    private var isSendingControlPacket = false

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
            senderDetected = false,
            latestDiscoveredMessageId = null,
            crcErrorsCount = 0,
            errorMessage = null
        )

        activeListeningJob?.cancel()
        activeListeningJob = viewModelScope.launch {
            runListeningSession()
        }
    }

    private suspend fun runListeningSession() {
        audioReceiver.startListening(object : ReceiverEventListener {
            override fun onStateChanged(state: ReceiverState, signalQualityPercent: Int) {
                if (isSendingControlPacket) return

                when (state) {
                    ReceiverState.LISTENING -> {
                        if (_uiState.value.state != ReceptionState.RECOVERING &&
                            _uiState.value.state != ReceptionState.MESSAGE_RECEIVED) {
                            _uiState.value = _uiState.value.copy(
                                state = ReceptionState.LISTENING,
                                statusText = if (_uiState.value.senderDetected) {
                                    "Sender detected • Waiting for packets..."
                                } else {
                                    "Listening for AcouLink transmission..."
                                },
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
                        if (_uiState.value.state != ReceptionState.MESSAGE_RECEIVED && !isSendingControlPacket) {
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
                if (!isSendingControlPacket) {
                    _uiState.value = _uiState.value.copy(
                        state = ReceptionState.FAILED,
                        errorMessage = message,
                        statusText = "Acoustic reception error: $message"
                    )
                }
            }
        })
    }

    private fun handleDecodedPacket(packet: Packet) {
        if (packet.isCorrupted) {
            _uiState.value = _uiState.value.copy(
                crcErrorsCount = _uiState.value.crcErrorsCount + 1
            )
            updatePacketListVisual(packet.sequenceNumber, packet.totalPackets, PacketVisualState.MISSING, "CRC Failed")
            checkSequenceStatus()
            return
        }

        // 1. Handle BEACON packet (Surprise Challenge 2 - Dynamic Group)
        if (packet.isBeacon) {
            val messageId = packet.messageId
            val totalPackets = packet.totalPackets

            // Check if we already received this message
            if (sequenceManager.activeMessageId == messageId && sequenceManager.isComplete()) {
                // Already completed this message; ignore duplicate beacon
                return
            }

            _uiState.value = _uiState.value.copy(
                senderDetected = true,
                latestDiscoveredMessageId = messageId,
                totalExpectedPackets = totalPackets,
                statusText = "Discovered sender! Latest Message #$messageId ($totalPackets segments). Requesting..."
            )

            sequenceManager.setActiveSession(messageId, totalPackets)
            requestLatestMessageAcoustically(messageId)
            return
        }

        // 2. Handle DATA packet
        if (packet.isData) {
            // Hackathon Demo Mode: Check if this packet should be intentionally dropped on first pass
            if (_uiState.value.simulatePacketDrop &&
                packet.sequenceNumber == _uiState.value.simulateDropSeqNumber &&
                retransmittedCount == 0
            ) {
                // Drop packet intentionally to demonstrate acoustic NACK recovery
                updatePacketListVisual(packet.sequenceNumber, packet.totalPackets, PacketVisualState.MISSING, "Simulated Drop")
                checkSequenceStatus()
                return
            }

            val status = sequenceManager.processPacket(packet)
            when (status) {
                is SequenceStatus.Accepted -> {
                    val visual = PacketVisualState.CONFIRMED
                    val details = if (retransmittedCount > 0 && packet.sequenceNumber == _uiState.value.simulateDropSeqNumber) {
                        "Recovered ✓"
                    } else {
                        "CRC PASS"
                    }
                    updatePacketListVisual(packet.sequenceNumber, packet.totalPackets, visual, details)
                }
                is SequenceStatus.Corrupted -> {
                    _uiState.value = _uiState.value.copy(crcErrorsCount = _uiState.value.crcErrorsCount + 1)
                    updatePacketListVisual(packet.sequenceNumber, packet.totalPackets, PacketVisualState.MISSING, "CRC Failed")
                }
                SequenceStatus.IgnoredWrongSession -> Unit
                SequenceStatus.IgnoredControlPacket -> Unit
            }

            checkSequenceStatus()
        }
    }

    /**
     * Surprise Challenge 2:
     * When a BEACON is detected, the newly joined receiver acoustically broadcasts a
     * REQUEST packet so the sender automatically retransmits the stored message.
     */
    private fun requestLatestMessageAcoustically(messageId: Int) {
        viewModelScope.launch {
            // Small randomized backoff to avoid collision with other receivers
            val backoffDelay = responseManager.calculateSlotDelay()
            delay(backoffDelay)

            isSendingControlPacket = true
            audioReceiver.stop()
            delay(120)

            val requestPacket = PacketEncoder.createRequestPacket(messageId)
            _uiState.value = _uiState.value.copy(
                statusText = "Transmitting acoustic REQUEST tone for Message #$messageId..."
            )
            audioSender.transmitPackets(listOf(requestPacket))
            delay(120)

            isSendingControlPacket = false
            _uiState.value = _uiState.value.copy(
                statusText = "REQUEST sent ✓ Listening for sender's packets..."
            )

            // Resume listening for incoming DATA packets
            activeListeningJob?.cancel()
            activeListeningJob = viewModelScope.launch {
                runListeningSession()
            }
        }
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
        } else if (missing.isNotEmpty() && sequenceManager.validPacketCount >= 1) {
            // Initiate partial reception recovery if packets are missing
            startRecoveryFlow(missing)
        }
    }

    /**
     * Handles missing packet recovery by transmitting an acoustic NACK packet.
     */
    private fun startRecoveryFlow(missing: List<Int>) {
        if (_uiState.value.state == ReceptionState.RECOVERING) return

        val timeline = mutableListOf(
            "Missing detected: " + missing.joinToString { String.format("Packet %02d", it) },
            "Calculating slotted response delay...",
            "Sending acoustic NACK to broadcaster..."
        )

        _uiState.value = _uiState.value.copy(
            state = ReceptionState.RECOVERING,
            statusText = "Recovering message... ${missing.size} packet(s) needed",
            missingSequences = missing,
            recoveryTimeline = timeline,
            recoveryProgressPercent = 30
        )

        viewModelScope.launch {
            val messageId = sequenceManager.activeMessageId ?: 0x101

            // Slotted delay to prevent collision
            val slotDelay = responseManager.calculateSlotDelay()
            delay(slotDelay)

            isSendingControlPacket = true
            audioReceiver.stop()
            delay(120)

            val nackPacket = nackManager.createNack(messageId, missing)
            audioSender.transmitPackets(listOf(nackPacket))
            delay(120)

            isSendingControlPacket = false
            retransmittedCount++

            _uiState.value = _uiState.value.let { state ->
                val tl = state.recoveryTimeline.toMutableList()
                tl.add("Acoustic NACK transmitted ✓")
                tl.add("Listening for sender retransmission...")
                state.copy(
                    recoveryTimeline = tl,
                    recoveryProgressPercent = 60
                )
            }

            // In demo mode without second device: complete simulation
            if (_uiState.value.simulatePacketDrop && retransmittedCount == 1) {
                delay(1200)
                for (seq in missing) {
                    val retransmittedPacket = Packet(
                        type = ProtocolConstants.TYPE_DATA,
                        messageId = messageId,
                        sequenceNumber = seq,
                        totalPackets = sequenceManager.totalPackets,
                        payload = " recovered".toByteArray(Charsets.UTF_8),
                        crc16 = 0,
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
                delay(500)
                completeReception()
            } else {
                // Resume listening for the actual retransmitted packets
                activeListeningJob?.cancel()
                activeListeningJob = viewModelScope.launch {
                    runListeningSession()
                }
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
        audioSender.stop()
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
        audioSender.stop()
    }
}
