# AcouLink — Project Documentation

## 1. Project Title
**AcouLink — Acoustic One-to-Many Communication**

**Hackathon Problem Statement:** PS02 – Acoustic One-to-Many Communication

## 2. Abstract
AcouLink is an Android-based acoustic communication system designed to transmit short text messages and URLs from one smartphone to multiple nearby Android smartphones without relying on Internet connectivity, Wi-Fi, Bluetooth, GPS, or external communication hardware.

The sender converts digital data into acoustic signals using FSK/BFSK and transmits them through the built-in speaker. Receiver smartphones capture the signal through their microphones, detect and decode the frequencies, validate packets, and reconstruct the original message.

The system also includes packet sequencing, CRC-16 error detection, ACK/NACK mechanisms, selective retransmission for missing packets, and a Dynamic Group mechanism that allows a receiver joining later to request the latest available message automatically.

## 3. Problem Statement
The challenge is to establish one-to-many communication using sound, where one Android phone broadcasts information to multiple nearby Android phones without Internet, mobile data, Wi-Fi, Bluetooth, GPS, or external hardware.

The system should also remain reliable when packets are lost or corrupted and should allow receivers joining later to obtain the latest message automatically.

## 4. Objectives
1. Enable acoustic communication between Android smartphones.
2. Support one sender communicating with multiple receivers.
3. Transmit short text messages and URLs.
4. Avoid dependence on Internet, Wi-Fi, Bluetooth, GPS, or external hardware.
5. Encode data using FSK/BFSK.
6. Detect corrupted packets using CRC-16.
7. Detect missing packets using sequence information.
8. Recover incomplete messages through NACK and selective retransmission.
9. Support late-joining receivers through Dynamic Group.
10. Maintain the latest transmitted message for later retrieval.

## 5. Proposed Solution
### Sender
The sender accepts a text message or URL, converts it to UTF-8 bytes, divides it into packets, adds metadata and CRC-16, modulates the data using FSK/BFSK, generates PCM audio, and plays it through the speaker. The sender also maintains the latest message and packets in a local cache and handles JOIN, REQUEST, ACK, and NACK messages.

### Receiver
The receiver captures sound using AudioRecord, detects frequencies, demodulates FSK/BFSK, decodes packets, validates CRC-16, tracks sequence IDs, detects missing packets, requests missing packets using NACK, reconstructs the message, and sends ACK after successful reception.

## 6. System Architecture
**Sender:** Message Input → Packet Encoder → CRC-16 → FSK/BFSK Modulator → AudioTrack → Speaker

**Acoustic Channel:** Sound Waves Through Air

**Receivers:** Microphone → AudioRecord → FSK/BFSK Demodulator → Goertzel Detection → Packet Decoder → CRC Validation → Message Assembler

## 7. Acoustic Communication Technique
### FSK/BFSK
Binary values are represented by different frequencies:
- Bit `0` → F0
- Bit `1` → F1

### AudioTrack
The sender generates and plays PCM audio representing the modulated data.

### AudioRecord
The receiver captures the acoustic signal through the smartphone microphone.

### Goertzel Detection
Goertzel-based frequency analysis determines the strength of the selected frequencies and identifies transmitted symbols.

## 8. Packet Structure
```text
PREAMBLE
PACKET_TYPE
MESSAGE_ID
PROTOCOL_VERSION
SEQUENCE_ID
TOTAL_SEGMENTS
PAYLOAD_LENGTH
PAYLOAD
CRC16
```

| Field | Purpose |
|---|---|
| PREAMBLE | Synchronization |
| PACKET_TYPE | Identifies DATA, JOIN, REQUEST, ACK, NACK, or BEACON |
| MESSAGE_ID | Identifies the current message |
| PROTOCOL_VERSION | Identifies protocol format |
| SEQUENCE_ID | Identifies packet position |
| TOTAL_SEGMENTS | Total packet count |
| PAYLOAD_LENGTH | Payload size |
| PAYLOAD | Message data |
| CRC16 | Error detection |

## 9. CRC-16 Error Detection
The receiver recalculates CRC-16 for each packet and compares it with the transmitted CRC. Matching packets are accepted; corrupted packets are rejected and can be recovered through the protocol.

## 10. Partial Reception Recovery
If a receiver misses or receives a corrupted packet, sequence tracking identifies the missing sequence ID.

```text
DATA
  ↓
Missing/corrupt packet detected
  ↓
NACK
  ↓
Sender checks cached packets
  ↓
Selective retransmission
  ↓
Receiver validates packet
  ↓
Message reconstructed
  ↓
ACK
```

Only missing packets are retransmitted rather than unnecessarily sending the complete message again.

## 11. Dynamic Group / Late Joining
A receiver joining after the initial broadcast can synchronize with the sender.

```text
New Receiver
     ↓
JOIN
     ↓
BEACON / AVAILABLE
     ↓
REQUEST
     ↓
Latest cached DATA
     ↓
Message reconstruction
     ↓
ACK
```

The sender maintains the latest message and packetized representation in a local cache.

Typical cached information includes:
```text
latestMessage
latestMessageId
latestPackets
latestPacketCount
```

## 12. Communication Protocol
| Packet Type | Purpose |
|---|---|
| JOIN | Receiver announces synchronization request |
| BEACON | Sender indicates latest message availability |
| REQUEST | Receiver requests latest data |
| DATA | Carries message packets |
| ACK | Confirms successful reception |
| NACK | Requests missing/corrupted packets |

## 13. Multiple Receiver Support
The sender broadcasts acoustically, allowing multiple nearby Android receivers to listen to the same transmission. Each receiver independently validates packets, tracks missing data, performs recovery, and reconstructs the message.

## 14. Reliability Mechanisms
- Packet sequencing
- CRC-16 validation
- ACK confirmation
- NACK requests
- Selective retransmission
- Retry and timeout handling
- Latest-message caching
- JOIN/REQUEST handling
- Collision/request handling for multiple receivers

## 15. Application Workflow
### Normal Broadcast
```text
Message Input
 → Packetization
 → CRC
 → FSK/BFSK
 → Speaker
 → Acoustic Channel
 → Microphone
 → Frequency Detection
 → Packet Decoding
 → CRC Validation
 → Message Reconstruction
 → ACK
```

### Partial Reception
```text
Partial Data
 → Missing Packet Detection
 → NACK
 → Selective Retransmission
 → Validation
 → Complete Message
 → ACK
```

### Late Join
```text
JOIN
 → BEACON
 → REQUEST
 → Latest Cached DATA
 → Message Reconstruction
 → ACK
```

## 16. Technologies Used
- Platform: Android
- Language/Stack: Kotlin / Android
- Audio output: AudioTrack
- Audio input: AudioRecord
- Modulation: FSK/BFSK
- Signal detection: Goertzel
- Encoding: UTF-8
- Error detection: CRC-16 / CRC-16-CCITT
- Packetization: Sequence-based protocol
- Storage/cache: Local application storage/repository
- Development: Android Studio

## 17. Key Features
### Core
- Text transmission
- URL transmission
- One-to-many acoustic communication
- Speaker-based transmission
- Microphone-based reception
- FSK/BFSK modulation/demodulation
- Goertzel detection
- Packetization
- CRC-16 validation

### Reliability
- Sequence tracking
- Missing packet detection
- ACK
- NACK
- Selective retransmission
- Retry/timeout handling

### Dynamic Group
- JOIN
- BEACON/AVAILABLE
- REQUEST
- Latest Message Cache
- Automatic late-join synchronization

## 18. Hardware Requirements
No external hardware is required. The system uses smartphone speakers and microphones. Multiple Android smartphones can be used for demonstration.

## 19. Network Restrictions
The core communication channel does not require:
- Internet
- Mobile data
- Wi-Fi
- Bluetooth
- GPS/location services
- External communication hardware

The primary channel is sound through the air.

## 20. How to Run
### Sender
1. Install the APK.
2. Open AcouLink.
3. Select Sender.
4. Enter a short text message or URL.
5. Start transmission.

### Receiver
1. Install the APK on one or more Android phones.
2. Select Receiver.
3. Start listening.
4. Place the receiver within suitable acoustic range.
5. Wait for the message to be decoded.

### Late-Joining Receiver
Start a receiver after the initial broadcast. It should use JOIN → BEACON → REQUEST and receive the latest cached message without requiring manual rebroadcast.

## 21. Demonstration Scenarios
### Normal One-to-Many
One sender transmits to multiple receivers and all receivers reconstruct the same message.

### Partial Reception Recovery
Cause a receiver to miss packets. The receiver should detect the missing data, send NACK, receive selective retransmission, and complete the message.

### Dynamic Group
Add a receiver after the first broadcast. The receiver should automatically synchronize with the latest message.

## 22. Advantages
- Network-independent communication
- Uses existing smartphone hardware
- One-to-many transmission
- Packet-level error detection
- Missing-packet recovery
- Late-join synchronization
- No external communication hardware

## 23. Potential Use Cases
Potential application areas include:
- Emergency/local alerts
- Classrooms and campuses
- Museums and guided information
- Offline events
- Local announcements
- Network-independent short-message distribution

## 24. Limitations
Acoustic communication can be affected by ambient noise, distance, speaker/microphone characteristics, interference, reverberation, device orientation, and simultaneous receiver responses. Reliability mechanisms reduce packet-loss impact but cannot guarantee perfect reception in every environment.

## 25. Future Scope
### Optimize
- Improved synchronization
- Better transmission efficiency
- Increased range and reliability

### Adapt
- Adaptive frequency selection
- Greater noise tolerance
- Intelligent error recovery

### Scale
- Improved collision avoidance
- Group synchronization
- Larger receiver groups

### Deploy
- Emergency/local alerts
- Classroom and campus communication
- Museum/guided information
- Offline event communication

**Future goal:** Make sound-based communication faster, smarter, and more resilient.

## 26. Expected Output / MVP
The MVP demonstrates:
1. One Android sender.
2. Multiple Android receivers.
3. Text/URL transmission.
4. Acoustic FSK/BFSK communication.
5. Packet-based transmission.
6. CRC-16 error detection.
7. Missing packet identification.
8. NACK-based recovery.
9. Selective retransmission.
10. ACK confirmation.
11. Latest Message Cache.
12. Dynamic Group / late-join synchronization.

## 27. Testing Strategy
Test on real Android smartphones:
- Normal sender/receiver communication
- Text and URL transmission
- Packet decoding
- CRC validation
- Missing packet detection
- NACK generation
- Selective retransmission
- Late-join JOIN/BEACON/REQUEST flow
- Multiple receivers
- Retry and timeout behavior

## 28. Security and Privacy
Acoustic transmission can potentially be detected by devices within suitable acoustic range. The system should therefore not be treated as a confidential communication channel unless additional encryption is implemented.

## 29. Team Roles
**J. MOHANDASS — Android Development & UI**

**B. HARISH — Acoustic Communication & Protocol**

**HAITHER ALI.M — Testing & Integration**

## 30. Conclusion
AcouLink demonstrates one-to-many smartphone communication using sound instead of conventional network infrastructure. By combining FSK/BFSK, AudioTrack, AudioRecord, Goertzel detection, packetization, CRC-16, ACK/NACK, selective retransmission, and Dynamic Group synchronization, the project aims to make acoustic communication more practical and resilient.

> **Communication without networks — using sound.**

## 31. Recommended GitHub Structure
```text
XO27_AD02/
├── AcouLink/
│   └── Android source code
├── APK/
│   └── AcouLink-latest.apk
├── Presentation/
│   └── AcouLink-Final.pptx
├── Documentation/
│   └── PROJECT_DOCUMENTATION.md
└── README.md
```
