package com.acoulink.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.acoulink.audio.AudioConfig
import com.acoulink.audio.AudioReceiver
import com.acoulink.audio.AudioSender
import com.acoulink.audio.ReceiverEventListener
import com.acoulink.audio.ReceiverState
import com.acoulink.audio.TransmissionProgressListener
import com.acoulink.data.LatestMessageRepository
import com.acoulink.data.MessageDirection
import com.acoulink.data.MessageEntity
import com.acoulink.data.MessageRepository
import com.acoulink.protocol.MessageAssembler
import com.acoulink.protocol.Packet
import com.acoulink.protocol.PacketEncoder
import com.acoulink.protocol.ProtocolConstants
import com.acoulink.recovery.NackManager
import com.acoulink.recovery.RetransmissionManager
import com.acoulink.ui.components.PacketVisualState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

enum class TransmissionState {
    IDLE,
    BROADCASTING,
    WAITING_ACK,
    RETRANSMITTING,
    BEACON_ACTIVE,
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
    // Surprise Challenge 2 - Dynamic Group & Beacon
    val latestMessageId: Int? = null,
    val isBeaconActive: Boolean = false,
    val isLatestCached: Boolean = false,
    val detectedNewReceivers: Int = 0,
    // Demo simulator option
    val simulateMissingPacketOnReceiver: Boolean = false,
    val simulatePacketDropSeq: Int = 3
)

class SendViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MessageRepository.getInstance(application)
    private val latestMessageRepo = LatestMessageRepository.getInstance(application)
    private val audioConfig = AudioConfig()
    private val audioSender = AudioSender(audioConfig)
    private val audioReceiver = AudioReceiver(audioConfig)
    private val retransmissionManager = RetransmissionManager()
    private val nackManager = NackManager()

    private val _uiState = MutableStateFlow(SendUiState())
    val uiState = _uiState.asStateFlow()

    private var activeTransmissionJob: Job? = null
    private var beaconAndRequestListenerJob: Job? = null
    private var generatedPackets: List<Packet> = emptyList()

    init {
        // Check if there is an existing cached broadcast from previous run
        latestMessageRepo.getLatestBroadcast()?.let { cached ->
            _uiState.value = _uiState.value.copy(
                latestMessageId = cached.messageId,
                isLatestCached = true
            )
        }
    }

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

        // Generate sequential or unique Message ID
        val messageId = latestMessageRepo.getLatestBroadcast()?.let {
            if (it.messageId < 9999) it.messageId + 1 else 101
        } ?: Random.nextInt(101, 199)

        generatedPackets = PacketEncoder.createDataPackets(
            message = text,
            messageId = messageId
        )
        retransmissionManager.initialize(generatedPackets)

        // Cache message for Dynamic Group (Surprise Challenge 2)
        latestMessageRepo.storeLatestBroadcast(
            messageId = messageId,
            content = text,
            isUrl = _uiState.value.isUrl,
            packets = generatedPackets
        )

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
            statusMessage = "Broadcasting acoustic packets (Message #$messageId)...",
            packetList = initialPacketItems,
            latestMessageId = messageId,
            isLatestCached = true,
            errorMessage = null
        )

        activeTransmissionJob?.cancel()
        beaconAndRequestListenerJob?.cancel()

        activeTransmissionJob = viewModelScope.launch {
            audioSender.transmitPackets(
                packets = generatedPackets,
                listener = object : TransmissionProgressListener {
                    override fun onPacketTransmitting(currentPacket: Int, totalPackets: Int, packet: Packet) {
                        _uiState.value = _uiState.value.let { state ->
                            val updatedList = state.packetList.map { item ->
                                if (item.sequenceNumber == currentPacket) {
                                    item.copy(visualState = PacketVisualState.TRANSMITTING)
                                } else item
                            }
                            state.copy(
                                currentPacketIndex = currentPacket,
                                statusMessage = "Broadcasting segment $currentPacket of $totalPackets...",
                                packetList = updatedList
                            )
                        }
                    }

                    override fun onPacketTransmitted(currentPacket: Int, totalPackets: Int, packet: Packet) {
                        _uiState.value = _uiState.value.let { state ->
                            val updatedList = state.packetList.map { item ->
                                if (item.sequenceNumber == currentPacket) {
                                    item.copy(visualState = PacketVisualState.CONFIRMED)
                                } else item
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
                        viewModelScope.launch {
                            finishTransmissionAndStartBeacon(messageId)
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

    /**
     * Completes the primary broadcast, saves message to history with consent,
     * and transitions into the autonomous Beacon & Request listener loop.
     */
    private suspend fun finishTransmissionAndStartBeacon(messageId: Int) {
        val text = _uiState.value.inputText.trim()
        val firstPacket = generatedPackets.firstOrNull()

        // Auto-save sent message if local history storage is enabled
        val entity = MessageEntity(
            messageId = firstPacket?.formattedMessageId ?: String.format("AC%04X", messageId),
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
            state = TransmissionState.BEACON_ACTIVE,
            isBeaconActive = true,
            statusMessage = "Broadcast complete • Beacon ACTIVE • Waiting for new receivers..."
        )

        startBeaconAndRequestListenerLoop(messageId)
    }

    /**
     * Surprise Challenge 2 Engine:
     * Periodically transmits an acoustic BEACON packet, and between beacons, listens on the
     * microphone for incoming acoustic REQUEST or NACK packets to automatically retransmit.
     */
    private fun startBeaconAndRequestListenerLoop(messageId: Int) {
        beaconAndRequestListenerJob?.cancel()
        beaconAndRequestListenerJob = viewModelScope.launch {
            val beaconPacket = PacketEncoder.createBeaconPacket(
                messageId = messageId,
                totalPackets = generatedPackets.size
            )

            while (isActive && _uiState.value.isBeaconActive) {
                // 1. Transmit acoustic BEACON tone over speaker
                _uiState.value = _uiState.value.copy(
                    statusMessage = "Transmitting acoustic BEACON (#$messageId)..."
                )
                audioReceiver.stop()
                delay(120)
                audioSender.transmitPackets(listOf(beaconPacket))
                delay(120)

                _uiState.value = _uiState.value.copy(
                    statusMessage = "Beacon active • Listening for receiver requests..."
                )

                // 2. Listen on microphone for incoming acoustic REQUEST or NACK packets
                val listenWindowJob = launch {
                    audioReceiver.startListening(object : ReceiverEventListener {
                        override fun onStateChanged(state: ReceiverState, signalQualityPercent: Int) = Unit

                        override fun onPacketDecoded(packet: Packet) {
                            if (packet.isCorrupted) return

                            // Case A: New receiver requests the latest message!
                            if (packet.isRequest && packet.messageId == messageId) {
                                viewModelScope.launch {
                                    handleAutonomousRetransmit(messageId, emptyList())
                                }
                            }
                            // Case B: Receiver sent acoustic NACK requesting specific missing packets
                            else if (packet.isNack && packet.messageId == messageId) {
                                val missingSeqs = nackManager.extractMissingSequences(packet)
                                if (missingSeqs.isNotEmpty()) {
                                    viewModelScope.launch {
                                        handleAutonomousRetransmit(messageId, missingSeqs)
                                    }
                                }
                            }
                        }

                        override fun onError(message: String) = Unit
                    })
                }

                // Check demo simulator option for testing
                if (_uiState.value.simulateMissingPacketOnReceiver && _uiState.value.retransmittedCount == 0) {
                    delay(1500)
                    listenWindowJob.cancel()
                    audioReceiver.stop()
                    handleAutonomousRetransmit(messageId, listOf(_uiState.value.simulatePacketDropSeq))
                } else {
                    // Listen for the duration of the beacon interval
                    delay(ProtocolConstants.BEACON_INTERVAL_MS)
                    listenWindowJob.cancel()
                    audioReceiver.stop()
                }
            }
        }
    }

    /**
     * Automatically retransmits cached message packets without human intervention.
     */
    private suspend fun handleAutonomousRetransmit(messageId: Int, specificSequences: List<Int>) {
        val cached = latestMessageRepo.getLatestBroadcast() ?: return
        if (cached.messageId != messageId) return

        val packetsToSend = if (specificSequences.isEmpty()) {
            cached.packets // Resend all packets for new receiver
        } else {
            retransmissionManager.getPacketsForRetransmission(specificSequences)
        }

        if (packetsToSend.isEmpty()) return

        audioReceiver.stop()
        delay(150)

        val isNewReceiver = specificSequences.isEmpty()
        _uiState.value = _uiState.value.let { state ->
            state.copy(
                state = TransmissionState.RETRANSMITTING,
                detectedNewReceivers = if (isNewReceiver) state.detectedNewReceivers + 1 else state.detectedNewReceivers,
                retransmittedCount = state.retransmittedCount + packetsToSend.size,
                statusMessage = if (isNewReceiver) {
                    "New receiver detected! Automatically transmitting latest message..."
                } else {
                    "NACK received! Retransmitting segment(s) ${specificSequences.joinToString()}..."
                }
            )
        }

        audioSender.transmitPackets(packetsToSend)
        delay(200)

        _uiState.value = _uiState.value.copy(
            state = TransmissionState.BEACON_ACTIVE,
            statusMessage = "Retransmission complete • Resuming acoustic beacon..."
        )
    }

    fun stopBroadcast() {
        activeTransmissionJob?.cancel()
        beaconAndRequestListenerJob?.cancel()
        audioSender.stop()
        audioReceiver.stop()
        latestMessageRepo.setBeaconActive(false)
        _uiState.value = _uiState.value.copy(
            state = TransmissionState.STOPPED,
            isBeaconActive = false,
            statusMessage = "Broadcast and beacon stopped by user"
        )
    }

    fun reset() {
        stopBroadcast()
        _uiState.value = SendUiState()
    }

    override fun onCleared() {
        super.onCleared()
        audioSender.stop()
        audioReceiver.stop()
        latestMessageRepo.setBeaconActive(false)
    }
}
