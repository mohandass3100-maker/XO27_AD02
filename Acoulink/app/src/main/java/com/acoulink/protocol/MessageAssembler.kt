package com.acoulink.protocol

import java.io.ByteArrayOutputStream
import java.util.regex.Pattern

/**
 * Result of successfully assembling an acoustic message.
 */
data class AssembledMessage(
    val messageId: String,
    val content: String,
    val isUrl: Boolean,
    val packetCount: Int,
    val validPacketCount: Int,
    val retransmittedCount: Int,
    val crcVerified: Boolean
)

/**
 * Reassembles packet byte streams into complete UTF-8 strings and analyzes content types.
 */
object MessageAssembler {
    private val URL_REGEX = Pattern.compile(
        "^(https?://|www\\.)[a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]",
        Pattern.CASE_INSENSITIVE
    )

    /**
     * Checks if the given string contains or starts with a valid URL.
     */
    fun isUrl(text: String): Boolean {
        val trimmed = text.trim()
        return URL_REGEX.matcher(trimmed).find() || trimmed.startsWith("http://") || trimmed.startsWith("https://")
    }

    /**
     * Assembles ordered valid packets into an [AssembledMessage].
     */
    fun assemble(
        packets: List<Packet>,
        retransmittedCount: Int = 0
    ): AssembledMessage? {
        if (packets.isEmpty()) return null

        val sorted = packets.sortedBy { it.sequenceNumber }
        val out = ByteArrayOutputStream()

        for (packet in sorted) {
            out.write(packet.payload)
        }

        val content = try {
            String(out.toByteArray(), Charsets.UTF_8)
        } catch (_: Exception) {
            String(out.toByteArray())
        }

        val first = sorted.first()
        val isUrl = isUrl(content)

        return AssembledMessage(
            messageId = first.formattedMessageId,
            content = content,
            isUrl = isUrl,
            packetCount = first.totalPackets,
            validPacketCount = sorted.size,
            retransmittedCount = retransmittedCount,
            crcVerified = sorted.all { !it.isCorrupted }
        )
    }
}
