# SoundCast – Offline Acoustic Communication System

## Team Details

| Details | Information |
|---|---|
| **Team Name** | ByteForce |
| **Team Leader** | Mohandass J |
| **Register Number** | 25103119 |
| **College** | Manakula Vinayagar Institute of Technology |
| **Domain** | Android App Development |

---

## 1. Problem Statement

In crowded environments such as classrooms, examination halls, conferences, railway stations, hospitals, and public events, organizers may need to quickly share the same information with multiple nearby smartphones.

Traditional communication methods such as Internet messaging, Wi-Fi, and Bluetooth may not always be available or suitable.

The challenge is to provide a way to broadcast short information such as **text messages or URLs** from one smartphone to multiple nearby Android devices without depending on:

- Internet connectivity
- Wi-Fi
- Bluetooth
- Location services
- Network infrastructure
- External hardware

The system should also reliably decode the transmitted information and allow receivers to access previously received messages.

### Target Users

- Event organizers
- Teachers and educational institutions
- Examination authorities
- Conference organizers
- Railway and transport authorities
- Hospitals
- Public information systems
- Emergency announcement systems

### Importance

The proposed solution provides an alternative communication method for environments where Internet or network infrastructure may be unavailable, restricted, overloaded, or inconvenient.

---

# 2. Proposed Solution

We propose **SoundCast**, an Android-based offline acoustic communication application.

The system allows an **Admin/Sender** to enter a short message or URL and broadcast it as an encoded audio signal through the smartphone speaker.

Nearby Android devices running the **Receiver** application use their microphones to detect the audio signal, decode it, verify its correctness, and display the original message.

The received information is also stored locally so that users can access it later through the **History** section.

### Basic Concept

```text
                ADMIN / SENDER
                       |
                       v
                Enter Message / URL
                       |
                       v
                  Encode Data
                       |
                       v
                 Packetization
                       |
                       v
                   CRC Check
                       |
                       v
                 FSK Modulation
                       |
                       v
                    Speaker
                       |
                 Sound Waves
                       |
        +--------------+--------------+
        |              |              |
        v              v              v
   Receiver 1     Receiver 2     Receiver 3
        |              |              |
   Microphone      Microphone      Microphone
        |              |              |
        +--------------+--------------+
                       |
                       v
                  Decode Audio
                       |
                       v
                  Verify Data
                       |
                       v
                  Display Text
                       |
                       v
                  Save History
```

---

## 3. Key Features

- **Offline communication** – Works without Internet or cellular data.
- **Acoustic transmission** – Uses the smartphone speaker and microphone.
- **One-to-many broadcasting** – One sender can transmit to multiple nearby receivers.
- **Text and URL support** – Suitable for short messages and web addresses.
- **FSK modulation** – Represents digital data using different audio frequencies.
- **Packetization** – Organizes transmitted data into structured packets.
- **CRC verification** – Helps detect corrupted or incorrectly received data.
- **Local history** – Stores previously received messages for later access.
- **No external hardware** – Uses built-in smartphone audio components.
- **Privacy-friendly architecture** – Communication can occur locally without sending the message through an online server.

---

## 4. System Architecture

```text
                     SOUNDCAST SYSTEM

              +-----------------------+
              |    Admin / Sender     |
              +-----------+-----------+
                          |
                          v
              +-----------------------+
              | Message / URL Input  |
              +-----------+-----------+
                          |
                          v
              +-----------------------+
              | Data Encoding        |
              +-----------+-----------+
                          |
                          v
              +-----------------------+
              | Packetization        |
              +-----------+-----------+
                          |
                          v
              +-----------------------+
              | CRC Generation       |
              +-----------+-----------+
                          |
                          v
              +-----------------------+
              | FSK Modulator        |
              +-----------+-----------+
                          |
                          v
              +-----------------------+
              | Smartphone Speaker   |
              +-----------+-----------+
                          |
                     SOUND WAVES
                          |
        +-----------------+-----------------+
        |                 |                 |
        v                 v                 v
 +-------------+   +-------------+   +-------------+
 | Receiver 1  |   | Receiver 2  |   | Receiver 3  |
 | Microphone  |   | Microphone  |   | Microphone  |
 +------+------+   +------+------+   +------+------+
        |                 |                 |
        +-----------------+-----------------+
                          |
                          v
              +-----------------------+
              | Audio Signal Detection|
              +-----------+-----------+
                          |
                          v
              +-----------------------+
              | FSK Demodulation     |
              +-----------+-----------+
                          |
                          v
              +-----------------------+
              | Packet Reconstruction|
              +-----------+-----------+
                          |
                          v
              +-----------------------+
              | CRC Verification     |
              +-----------+-----------+
                          |
                          v
              +-----------------------+
              | Display Message      |
              +-----------+-----------+
                          |
                          v
              +-----------------------+
              | Local History Storage|
              +-----------------------+
```

---

## 5. Working Principle

### Sender Side

1. The administrator opens the SoundCast sender interface.
2. The administrator enters a short text message or URL.
3. The application converts the input into a digital data format.
4. The data is divided into packets.
5. A CRC value is generated for error detection.
6. The packet data is converted into audio using FSK modulation.
7. The smartphone speaker transmits the resulting sound signal.

### Receiver Side

1. The receiver opens the SoundCast receiver interface.
2. The smartphone microphone continuously listens for the acoustic signal.
3. The application detects the expected signal pattern.
4. FSK demodulation converts the audio signal back into digital data.
5. The packets are reconstructed.
6. The CRC value is checked to identify transmission errors.
7. If verification succeeds, the original message or URL is displayed.
8. The received information is stored locally in the History section.

---

## 6. FSK Modulation

**Frequency Shift Keying (FSK)** is used to represent digital information using different audio frequencies.

For example:

```text
Binary 0  --->  Frequency F0
Binary 1  --->  Frequency F1
```

During transmission, the application changes between the selected frequencies according to the binary data.

At the receiver:

```text
Audio Frequency
       |
       v
Frequency Detection
       |
       v
F0 ---> 0
F1 ---> 1
       |
       v
Binary Data
```

The exact frequencies and timing parameters can be selected during implementation based on smartphone speaker/microphone characteristics and the desired communication range.

---

## 7. Packet Structure

A simple packet can be designed as:

```text
+---------+---------+---------+---------+---------+
|  SYNC   | HEADER  | LENGTH  |  DATA   |   CRC   |
+---------+---------+---------+---------+---------+
```

### Fields

- **SYNC** – Helps the receiver identify the beginning of a transmission.
- **HEADER** – Contains packet/control information.
- **LENGTH** – Indicates the size of the data field.
- **DATA** – Contains the encoded message or URL.
- **CRC** – Used to detect transmission errors.

This structure helps the receiver identify, reconstruct, and validate the transmitted information.

---

## 8. Error Detection Using CRC

Acoustic communication can be affected by:

- Background noise
- People talking
- Echoes
- Distance
- Speaker/microphone limitations
- Other nearby sounds

To improve reliability, SoundCast uses **Cyclic Redundancy Check (CRC)**.

The sender calculates a CRC value from the transmitted data and adds it to the packet.

The receiver calculates the CRC again after decoding.

```text
Sender:
Data ---> CRC Calculation ---> Data + CRC

Receiver:
Received Data ---> CRC Calculation
                         |
              +----------+----------+
              |                     |
           Match                  Mismatch
              |                     |
              v                     v
         Accept Data          Reject / Retry
```

A matching CRC indicates that the received packet passed the selected error-detection check.

---

## 9. One-to-Many Communication

One of the main advantages of SoundCast is that the same acoustic signal can be received by several nearby smartphones.

```text
                 Sender
                   |
                Speaker
                   |
               Sound Wave
        ___________|___________
       /           |           \
      v            v            v
 Receiver 1   Receiver 2   Receiver 3
```

Unlike a point-to-point communication method, the sender does not need to establish a separate connection with each receiver.

---

## 10. History Module

The receiver application stores successfully verified messages locally.

Example:

```text
+----------------------------------+
|           MESSAGE HISTORY        |
+----------------------------------+
| 1. Exam Hall - Room 204         |
|    Received: 10:15 AM           |
+----------------------------------+
| 2. Event Website URL             |
|    Received: 11:20 AM           |
+----------------------------------+
| 3. Conference Announcement       |
|    Received: 01:05 PM           |
+----------------------------------+
```

The History module allows users to review previously received information without requiring another transmission.

---

## 11. Suggested Android Components

The application can be implemented using standard Android development technologies.

### Sender

- Android UI for message/URL input
- Data encoder
- Packet builder
- CRC generator
- FSK audio generator
- Android audio playback

### Receiver

- Android microphone/audio capture
- Signal detection
- FSK demodulator
- Packet parser
- CRC verification
- Message/URL display
- Local database or persistent storage for History

### Possible Technology Stack

- **Language:** Kotlin or Java
- **Platform:** Android
- **Audio Input:** Android microphone APIs
- **Audio Output:** Android speaker/audio APIs
- **Storage:** SQLite/Room or another local persistence mechanism
- **UI:** Android Views or Jetpack Compose

---

## 12. Advantages

1. Does not require Internet connectivity.
2. Does not require Wi-Fi or Bluetooth.
3. Does not require location services.
4. Does not require network infrastructure.
5. Uses hardware already available in smartphones.
6. Supports one-to-many broadcasting.
7. Can be useful in network-constrained environments.
8. Received information can be stored locally.
9. Suitable for short messages and URLs.
10. Can be developed as a low-cost Android solution.

---

## 13. Limitations

- Acoustic communication has a limited practical range.
- Background noise can affect reception.
- Walls and physical obstacles may reduce reliability.
- Echoes can interfere with signal detection.
- Smartphone speaker and microphone hardware varies between devices.
- Long messages require more transmission time.
- Transmission reliability depends on frequency selection, timing, and environmental conditions.

These limitations can be addressed through signal design, error detection, synchronization, repeated transmission, and testing under different environmental conditions.

---

## 14. Use Cases

### Classroom

A teacher can broadcast a short announcement or resource URL to nearby students.

### Examination Hall

Authorized staff can transmit short instructions or announcements without depending on Internet access.

### Conferences

Organizers can broadcast an event webpage, schedule URL, or short announcement.

### Railway / Transport

Short platform or service information could be broadcast to nearby compatible devices.

### Hospitals

Authorized staff could transmit short local instructions or information in areas with poor network connectivity.

### Public Events

Organizers can distribute short announcements or information URLs to nearby participants.

### Emergency Information

A suitable acoustic broadcast system could provide short local announcements when conventional connectivity is unavailable.

---

## 15. Security and Reliability Considerations

SoundCast should be designed primarily for controlled, short-range information broadcasting.

Possible improvements include:

- Packet sequence numbers
- Message identifiers
- CRC error detection
- Repeated transmission
- Start/end synchronization markers
- Duplicate-message detection
- Optional application-level authentication
- Expiration time for old messages
- Maximum message length
- Local-only storage

For sensitive information, additional authentication and encryption should be considered rather than relying on acoustic transmission alone.

---

## 16. Future Enhancements

Future versions can include:

- Adaptive frequency selection
- Better noise filtering
- Automatic gain control
- More robust modulation schemes
- Reed-Solomon or other forward error correction
- Automatic retransmission
- Message prioritization
- Sender/receiver authentication
- Encrypted messages
- QR-code fallback
- Multi-language support
- Android accessibility support
- Improved range and reliability through optimized signal processing

---

## 17. Expected Outcome

The expected result is an Android application capable of transmitting short text messages or URLs acoustically from one sender to multiple nearby Android receivers without Internet, Wi-Fi, Bluetooth, location services, network infrastructure, or external hardware.

The receiver should be able to:

1. Detect the transmitted acoustic signal.
2. Decode the FSK-modulated data.
3. Reconstruct the packet.
4. Verify the data using CRC.
5. Display the original message or URL.
6. Store successfully received information in local History.

---

## 18. Project Summary

**SoundCast** is an offline acoustic communication concept that transforms smartphones into a short-range broadcast communication system.

The project combines:

**Data Encoding + Packetization + CRC + FSK Modulation + Acoustic Transmission + Signal Detection + Demodulation + Verification + Local History**

The proposed system is intended to provide a simple alternative for short-range information broadcasting when conventional network-based communication is unavailable or unsuitable.
