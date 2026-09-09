# Checkpoint 1

## Goal

Demonstrate the simplest end-to-end acoustic communication flow.

### Phone A — Sender
1. Open the Sender screen.
2. Enter a short message such as `HELLO SOUNDCAST`.
3. Press Broadcast.
4. Generate two-frequency FSK tones.
5. Play the generated PCM signal through the speaker.

### Phone B — Receiver
1. Open the Receiver screen.
2. Request microphone permission.
3. Start listening.
4. Capture microphone samples.
5. Detect which of the two FSK frequencies is present.
6. Convert the detected frequencies into binary values.
7. Decode and display the received message.

## Scope

Only the basic two-frequency prototype is included.

## Deferred Features

CRC, packetization, message IDs, preamble synchronization, ACK, retransmission, receiver dashboards, database/history, notifications, cloud services, internet APIs, authentication, Wi-Fi, Bluetooth, and location services are outside this checkpoint.
