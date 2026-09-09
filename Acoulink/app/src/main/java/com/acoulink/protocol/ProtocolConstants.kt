package com.acoulink.protocol

/**
 * Global constants defining the AcouLink acoustic communication protocol.
 */
object ProtocolConstants {
    // Protocol Identification
    const val PROTOCOL_VERSION: Byte = 0x01
    
    // Magic Synchronization Header: 0xAA, 0x55, 0x7E
    val PREAMBLE_SYNC_BYTES = byteArrayOf(0xAA.toByte(), 0x55.toByte(), 0x7E.toByte())
    
    // Packet Types
    const val TYPE_DATA: Byte = 0x01
    const val TYPE_ACK: Byte = 0x02
    const val TYPE_NACK: Byte = 0x03
    const val TYPE_BEACON: Byte = 0x04
    const val TYPE_REQUEST: Byte = 0x05

    // Framing Constraints
    const val HEADER_SIZE_BYTES = 7 // [Magic(3) + Type(1) + MsgId(2) + Seq(1) + Total(1) + Len(1)]
    const val CRC_SIZE_BYTES = 2
    const val DEFAULT_MAX_PAYLOAD_SIZE = 24 // Bytes per packet
    const val MAX_MESSAGE_CHARACTERS = 512

    // Timing & Recovery Constraints
    const val DEFAULT_MAX_RETRIES = 3
    const val RESPONSE_SLOT_DURATION_MS = 300L
    const val ACK_WAIT_TIMEOUT_MS = 2500L
    const val GUARD_INTERVAL_MS = 40L // Silence between packet transmissions
    const val BEACON_INTERVAL_MS = 7000L // Acoustic beacon advertising interval
    const val DISCOVERY_WINDOW_MS = 15000L // Receiver beacon scanning timeout
}
