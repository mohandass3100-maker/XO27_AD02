# SoundCast

## Project Name
SoundCast

## Problem
Enable nearby Android phones to exchange short text messages or URLs without Wi-Fi, Bluetooth, internet, or a backend.

## Proposed Solution
SoundCast uses acoustic communication. The sender converts information into simple FSK audio tones and plays them through the phone speaker. A receiver uses the microphone to capture the sound, detects the transmitted frequency, and decodes the basic signal.

## Technology Used
- Android
- Kotlin
- Android Studio
- Native Android audio APIs
- AudioRecord for microphone input
- AudioTrack for acoustic tone output
- FSK (Frequency Shift Keying)

## Checkpoint 1 Objective
Build the first simple prototype:
1. Enter a short message on Phone A.
2. Convert the message into a basic two-frequency acoustic signal.
3. Play the signal through the speaker.
4. Capture it on Phone B.
5. Detect the two frequencies.
6. Decode and display the basic message.

## Current Status
Checkpoint 1 project structure created. Advanced communication features are intentionally not implemented yet.

## Not Included in Checkpoint 1
- CRC
- Packetization
- Message IDs
- Preamble synchronization
- ACK
- Retransmission
- Multiple receiver dashboard
- Database / Room
- Notifications
- Cloud services
- Internet APIs
- Authentication
- Wi-Fi / Bluetooth / location services
