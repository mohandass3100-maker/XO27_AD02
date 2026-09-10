# AcouLink — Architecture & Design Documentation

## 1. Document Purpose

This document describes the architecture and design of the AcouLink Android application, including the acoustic communication pipeline, sender and receiver components, packet protocol, reliability mechanisms, Dynamic Group synchronization, data/cache design, and major application flows.

---

## 2. System Overview

AcouLink implements one-to-many communication using smartphone speakers and microphones as the communication medium.

```text
                 ┌─────────────────────┐
                 │     SENDER PHONE    │
                 │                     │
                 │ Message / URL       │
                 │        ↓            │
                 │ Packet Encoder      │
                 │        ↓            │
                 │ CRC-16              │
                 │        ↓            │
                 │ FSK/BFSK Modulator  │
                 │        ↓            │
                 │ AudioTrack          │
                 └──────────┬──────────┘
                            │
                            │ Acoustic Sound
                            ▼
                  ┌──────────────────┐
                  │   AIR / SOUND    │
                  │     CHANNEL      │
                  └────────┬─────────┘
                           │
             ┌─────────────┼─────────────┐
             ▼             ▼             ▼
      ┌────────────┐ ┌────────────┐ ┌────────────┐
      │ RECEIVER 1 │ │ RECEIVER 2 │ │ RECEIVER N │
      │ Microphone │ │ Microphone │ │ Microphone │
      │ AudioRecord│ │ AudioRecord│ │ AudioRecord│
      │ Goertzel   │ │ Goertzel   │ │ Goertzel   │
      │ Decoder    │ │ Decoder    │ │ Decoder    │
      │ Assembler  │ │ Assembler  │ │ Assembler  │
      └────────────┘ └────────────┘ └────────────┘
```

The acoustic channel also carries control packets such as JOIN, BEACON, REQUEST, ACK, and NACK.

---

## 3. Design Goals

- One sender to multiple receivers.
- Acoustic communication through built-in smartphone speakers and microphones.
- No Internet, Wi-Fi, or Bluetooth dependency for the communication channel.
- Packet-based communication.
- CRC-16 error detection.
- Sequence-based missing-packet detection.
- NACK-based selective retransmission.
- Late-join synchronization using Dynamic Group.
- Latest-message caching.
- Controlled retries and request handling.
- Separation of UI, application state, protocol, audio, recovery, and data responsibilities.

---

## 4. Layered Architecture

```text
┌──────────────────────────────────────────────┐
│                 UI LAYER                     │
│ Compose Screens / User Interaction           │
└───────────────────────┬──────────────────────┘
                        ↓
┌──────────────────────────────────────────────┐
│              VIEWMODEL LAYER                 │
│ SendViewModel / ReceiveViewModel             │
│ Workflow + Application State                 │
└───────────────┬────────────────┬─────────────┘
                ↓                ↓
┌──────────────────────┐ ┌────────────────────┐
│ PROTOCOL / RECOVERY  │ │ DATA LAYER         │
│ Packets / CRC        │ │ Cache / History    │
│ ACK / NACK / Retry   │ │ Local Persistence  │
└────────────┬─────────┘ └────────────────────┘
             ↓
┌──────────────────────────────────────────────┐
│                  AUDIO LAYER                 │
│ AudioTrack / AudioRecord                    │
│ FSK/BFSK / Goertzel                         │
└──────────────────────┬───────────────────────┘
                       ↓
┌──────────────────────────────────────────────┐
│              ACOUSTIC CHANNEL                │
│                  Sound / Air                 │
└──────────────────────────────────────────────┘
```

---

## 5. Major Components

### 5.1 UI Layer

The application contains screens for the major user workflows, including:

- Home
- Send
- Receive
- Transmission
- Receiving
- Recovery
- Message Received
- History
- Diagnostics
- Settings

The UI layer is responsible for user interaction and presenting application state.

### 5.2 ViewModel Layer

Important application-state components include:

- `HomeViewModel`
- `SendViewModel`
- `ReceiveViewModel`
- `HistoryViewModel`
- `SettingsViewModel`

The ViewModels coordinate UI actions with communication and data components.

### 5.3 Audio Layer

The audio layer contains components such as:

- `AudioSender`
- `AudioReceiver`
- `FSKModulator`
- `FSKDemodulator`
- `SignalDetector`
- `AudioConfig`

Responsibilities include audio generation, microphone capture, modulation, demodulation, synchronization, and frequency detection.

### 5.4 Protocol Layer

The protocol layer contains components such as:

- `Packet`
- `PacketEncoder`
- `PacketDecoder`
- `MessageAssembler`
- `SequenceManager`
- Protocol constants/types

Responsibilities include framing, packet serialization, packet decoding, sequence tracking, CRC handling, and message reconstruction.

### 5.5 Recovery Layer

The recovery layer contains components such as:

- `AckManager`
- `NackManager`
- `RetransmissionManager`
- Receiver response handling

Responsibilities include ACK/NACK creation, missing-packet recovery, retransmission, retries, and related control flow.

### 5.6 Data Layer

The data layer contains components such as:

- `LatestMessageRepository`
- `MessageRepository`
- `MessageDao`
- `SQLiteMessageDao`
- Message entities/models

The data layer supports latest-message caching and local message persistence/history.

---

# 6. Sender Architecture

```text
User Message / URL
       ↓
SendViewModel
       ↓
Message Processing
       ↓
PacketEncoder
       ↓
Packetization + CRC-16
       ↓
Latest Message Cache
       ↓
FSKModulator
       ↓
AudioSender
       ↓
AudioTrack
       ↓
Speaker
       ↓
Acoustic Channel
```

### Sender responsibilities

1. Accept text or URL.
2. Create a message ID.
3. Divide the message into packets.
4. Add sequence and total-packet information.
5. Calculate CRC-16.
6. Encode packets into an acoustic representation.
7. Modulate the data using FSK/BFSK.
8. Generate PCM audio.
9. Transmit through AudioTrack.
10. Preserve the latest message/packets for recovery and late joining.
11. Respond to receiver control packets.

---

# 7. Receiver Architecture

```text
Acoustic Channel
       ↓
Microphone
       ↓
AudioRecord
       ↓
SignalDetector
       ↓
Preamble / Synchronization
       ↓
FSKDemodulator
       ↓
Decoded Bytes
       ↓
PacketDecoder
       ↓
CRC Validation
       ↓
SequenceManager
       ↓
MessageAssembler
       ↓
Complete Message
```

### Receiver responsibilities

1. Capture microphone samples.
2. Detect acoustic activity.
3. Synchronize to packet preambles.
4. Demodulate FSK/BFSK symbols.
5. Decode packet frames.
6. Validate CRC-16.
7. Track packet sequence numbers.
8. Identify missing/corrupt packets.
9. Request recovery through NACK.
10. Reconstruct the original message.
11. Send ACK when complete.
12. Support JOIN/REQUEST synchronization for late receivers.

---

# 8. Acoustic Signal Processing

## 8.1 Modulation

The system uses FSK/BFSK:

```text
Binary Data
     ↓
Bit 0 → F0
Bit 1 → F1
     ↓
Audio waveform
```

## 8.2 Transmission

```text
FSK/BFSK symbols
       ↓
PCM samples
       ↓
AudioTrack
       ↓
Speaker
```

## 8.3 Reception

```text
Microphone
    ↓
AudioRecord
    ↓
Audio samples
    ↓
Frequency analysis
    ↓
F0 / F1 decision
    ↓
Bits
```

## 8.4 Goertzel Detection

Goertzel-based frequency analysis is used to evaluate the selected frequencies and support acoustic signal detection/decoding.

---

# 9. Packet Frame Design

The packet frame is organized as:

```text
┌──────────┬──────┬─────────┬─────┬─────┬─────┬─────────┬────────┐
│ PREAMBLE │ TYPE │ MSG ID  │ SEQ │TOTAL│ LEN │ PAYLOAD │ CRC16  │
└──────────┴──────┴─────────┴─────┴─────┴─────┴─────────┴────────┘
```

The implementation uses a synchronization preamble followed by packet metadata, payload, and CRC.

Conceptual serialized structure:

```text
PREAMBLE
TYPE
MESSAGE_ID
SEQUENCE
TOTAL_SEGMENTS
PAYLOAD_LENGTH
PAYLOAD
CRC16
```

### Design purpose

- Preamble → synchronization
- Type → control/data identification
- Message ID → message association
- Sequence → ordering and loss detection
- Total → completion detection
- Length → frame parsing
- Payload → actual content
- CRC16 → integrity verification

---

# 10. Packet Types

```text
JOIN
BEACON
REQUEST
DATA
ACK
NACK
```

### JOIN

Sent by a receiver requesting synchronization with the current sender/group.

### BEACON

Sent by the sender to indicate that a latest message is available.

### REQUEST

Sent by a receiver to request the available/latest message or required data.

### DATA

Carries message content.

### ACK

Confirms successful reception.

### NACK

Identifies missing or corrupted packets that should be retransmitted.

---

# 11. CRC-16 Design

CRC-16 is applied at packet level.

```text
Sender:
Header + Payload
       ↓
CRC-16 calculation
       ↓
CRC appended
       ↓
Transmit

Receiver:
Receive frame
       ↓
Separate CRC
       ↓
Recalculate CRC
       ↓
Compare
     ↙     ↘
 MATCH   MISMATCH
   ↓         ↓
Accept    Reject / Recover
```

A CRC mismatch prevents corrupted data from being accepted as a valid packet.

---

# 12. Sequence and Message Assembly Design

Packets belonging to a message contain sequence information.

Example:

```text
Expected:
1  2  3  4  5

Received:
1  2     4  5
      ↑
    Missing
```

The receiver can identify packet 3 as missing.

`MessageAssembler` then reconstructs the original message in sequence order once all required packets are available.

---

# 13. Partial Reception Recovery

Partial reception is handled without requiring the sender to restart the complete transmission.

```text
             DATA BROADCAST
                    ↓
          Receiver collects packets
                    ↓
              CRC validation
                    ↓
             Sequence tracking
                    ↓
          Complete message?
             ↙             ↘
           YES              NO
            ↓                ↓
          ACK          Find missing packets
                              ↓
                            NACK
                              ↓
                       Sender receives NACK
                              ↓
                    RetransmissionManager
                              ↓
                    Cached packet lookup
                              ↓
                 Retransmit requested data
                              ↓
                         Receiver
                              ↓
                         CRC check
                              ↓
                     Message complete
                              ↓
                            ACK
```

### Selective retransmission

If packets 3 and 7 are missing:

```text
NACK → [3, 7]
```

The sender should retrieve those packets from its cached transmission data rather than rebuilding the entire message transmission.

---

# 14. NACK Design

`NackManager` is responsible for NACK-related operations.

Conceptual NACK:

```text
TYPE       = NACK
MESSAGE_ID = current message
MISSING    = [sequence IDs]
```

The receiver uses missing sequence information to identify which packets need retransmission.

---

# 15. Retransmission Design

`RetransmissionManager` maintains/retrieves transmitted packet data required for recovery.

Conceptual flow:

```text
NACK
 ↓
Extract message ID
 ↓
Extract missing sequence IDs
 ↓
Lookup cached packets
 ↓
Transmit only requested packets
 ↓
Receiver validates
 ↓
Receiver assembles message
```

Controlled retry/timeout behavior prevents an endless recovery loop.

---

# 16. Dynamic Group Architecture

Dynamic Group allows a receiver to join after the initial broadcast.

```text
┌────────────────┐
│ Late Receiver  │
└───────┬────────┘
        │ JOIN
        ▼
┌────────────────┐
│     Sender     │
│ detects JOIN   │
└───────┬────────┘
        │ BEACON
        ▼
┌────────────────┐
│ Late Receiver  │
│ sends REQUEST  │
└───────┬────────┘
        │ REQUEST
        ▼
┌────────────────┐
│ Sender Cache   │
│ Latest Message │
└───────┬────────┘
        │ DATA
        ▼
┌────────────────┐
│ Late Receiver  │
│ reconstructs   │
└───────┬────────┘
        │ ACK
        ▼
     COMPLETE
```

### Key design principle

The sender does not need to manually restart the broadcast for the late receiver. The latest message remains available through the sender's local cache.

---

# 17. Latest Message Cache

The sender maintains the latest message and its packet representation.

Conceptual data:

```text
latestMessage
latestMessageId
latestPackets
latestPacketCount
```

The cache serves two major purposes:

1. **Partial Reception Recovery**
   - Retrieve missing packets for selective retransmission.

2. **Dynamic Group**
   - Provide the latest message to a receiver joining later.

---

# 18. Dynamic Group State Flow

```text
IDLE
 ↓
JOIN SENT
 ↓
WAIT FOR BEACON
 ↓
BEACON RECEIVED
 ↓
REQUEST SENT
 ↓
RECEIVING LATEST DATA
 ↓
CHECKING
 ↓
COMPLETE
```

If data is incomplete:

```text
RECEIVING
    ↓
MISSING DETECTED
    ↓
NACK
    ↓
RECOVERY
    ↓
COMPLETE
```

---

# 19. ACK Architecture

ACK confirms that the receiver has successfully completed the required message reception.

```text
DATA
 ↓
Receiver validates packets
 ↓
All required packets present
 ↓
Message assembled
 ↓
ACK generated
 ↓
Acoustic ACK transmission
```

ACK is therefore a completion signal rather than merely a packet-received indication.

---

# 20. Multi-Receiver Design

The sender uses an acoustic broadcast medium.

```text
                  SENDER
                    │
                    │
             Acoustic Broadcast
                    │
       ┌────────────┼────────────┐
       ↓            ↓            ↓
  Receiver 1   Receiver 2   Receiver N
```

Each receiver maintains its own:

- Reception state
- Packet set
- Missing sequence set
- Message assembly
- Recovery state

This allows receivers to succeed or recover independently.

---

# 21. Control Message Collision Consideration

Multiple receivers can potentially respond to the same sender.

The architecture therefore requires controlled request/response behavior, including:

- Timing control
- Retry handling
- Request windows
- Appropriate delays/backoff where implemented
- Avoiding unnecessary repeated control transmissions

The acoustic broadcast remains shared while each receiver maintains its own local reception state.

---

# 22. End-to-End Data Flow

## Normal Transmission

```text
User
 ↓
SendViewModel
 ↓
PacketEncoder
 ↓
CRC-16
 ↓
FSKModulator
 ↓
AudioSender
 ↓
AudioTrack
 ↓
Speaker
 ↓
Sound
 ↓
Receiver Microphone
 ↓
AudioRecord
 ↓
SignalDetector / Goertzel
 ↓
FSKDemodulator
 ↓
PacketDecoder
 ↓
CRC validation
 ↓
SequenceManager
 ↓
MessageAssembler
 ↓
Received Message
```

---

# 23. Partial Reception Sequence

```text
Sender                    Receiver

DATA 1 -----------------> 1 ✓
DATA 2 -----------------> 2 ✓
DATA 3 -----------------> 3 ✗
DATA 4 -----------------> 4 ✓
DATA 5 -----------------> 5 ✓

                           Detect missing 3
                               |
                               ↓
NACK <-------------------- [3]
   |
   ↓
Lookup cached packet 3
   |
   ↓
DATA 3 -----------------> 3 ✓
                           |
                           ↓
                    Reconstruct message
                           |
                           ↓
ACK <----------------------
```

---

# 24. Dynamic Group Sequence

```text
Late Receiver             Sender

JOIN -------------------->
                          Detect JOIN
                          Check latest cache
BEACON <------------------
                          |
REQUEST ----------------->
                          |
Latest DATA <-------------
                          |
Validate + Assemble
                          |
ACK --------------------->
```

---

# 25. Data Architecture

The project includes local repository/data components for message handling.

Conceptual relationship:

```text
UI / ViewModel
      ↓
Repository
      ↓
DAO
      ↓
Local Database / Storage
```

The latest-message repository provides fast access to the sender's latest cached communication state, while message repositories/database components support persistent local message handling/history where used.

---

# 26. Android Permission and Resource Design

The communication architecture depends on Android audio resources:

### Sender
- Speaker/audio output

### Receiver
- Microphone/audio input

The application should request and manage microphone permission when required by Android.

The audio resources should be released when communication stops or the relevant screen/lifecycle ends.

---

# 27. Error Handling Design

Major error categories include:

### Invalid packet
Handled through packet parsing and validation.

### CRC failure
Packet is rejected and can be treated as corrupted/missing for recovery.

### Missing packet
Detected from sequence information and requested through NACK.

### Timeout
Receiver/sender exits or retries according to communication timeout logic.

### Incomplete message
Message assembly remains incomplete until required packet data is recovered.

### No acoustic signal
Receiver continues listening or follows its configured retry/timeout behavior.

---

# 28. Reliability Design Summary

```text
              RELIABLE ACOUSTIC DELIVERY
                        │
       ┌────────────────┼────────────────┐
       ↓                ↓                ↓
   Synchronize      Validate         Recover
   Preamble         CRC-16           NACK
       │                │                │
       ↓                ↓                ↓
   Decode bits      Reject bad       Retransmit
       │             packets          missing
       └────────────────┬───────────────┘
                        ↓
                  Reconstruct
                        ↓
                       ACK
```

---

# 29. Design Principles

The architecture follows these principles:

1. **Acoustic-first communication** — sound is the communication medium.
2. **Packetization** — messages are divided into manageable units.
3. **Validation before assembly** — CRC is checked before accepting packet data.
4. **Selective recovery** — only missing/corrupt data needs retransmission.
5. **Cached latest state** — the sender can serve late receivers.
6. **Independent receivers** — each receiver maintains its own state.
7. **Separation of concerns** — UI, state, protocol, audio, recovery, and data responsibilities are separated.
8. **No dependence on conventional wireless networking** for the core communication path.

---

# 30. Architecture Summary

The complete AcouLink architecture can be summarized as:

```text
                 ACOULINK
                    │
       ┌────────────┴────────────┐
       │                         │
    SENDER                    RECEIVERS
       │                         │
 Message/URL                 Microphone
       ↓                         ↓
 Packet Encoder              AudioRecord
       ↓                         ↓
 CRC-16                    Signal Detection
       ↓                         ↓
 FSK/BFSK                  Goertzel / FSK
       ↓                         ↓
 AudioTrack                 Packet Decoder
       ↓                         ↓
 Speaker                    CRC-16 Check
       │                         ↓
       │                    Sequence Manager
       │                         ↓
       │                   Message Assembler
       │                         │
       └──── Acoustic ───────────┘
                    │
             Control Protocol
                    │
      JOIN / BEACON / REQUEST
             ACK / NACK
                    │
             Recovery + Cache
```

---

# 31. Architecture Conclusion

AcouLink combines Android audio APIs, acoustic FSK/BFSK communication, signal detection, packet framing, CRC-16 validation, sequence tracking, ACK/NACK reliability, selective retransmission, and Dynamic Group synchronization into a single one-to-many communication architecture.

The central design is:

**One Sender → Acoustic Signal → Multiple Receivers**

The reliability layer extends this with:

**Missing Packet → NACK → Selective Retransmission → ACK**

The Dynamic Group layer extends it further with:

**Late Receiver → JOIN → BEACON → REQUEST → Latest Cached DATA**

This architecture enables AcouLink to demonstrate network-independent acoustic communication while providing mechanisms for incomplete reception and late receiver synchronization.
