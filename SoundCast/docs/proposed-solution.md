# Proposed Solution

SoundCast will use Frequency Shift Keying (FSK) for the first prototype.

Two frequencies represent binary values:

- Frequency A → binary `0`
- Frequency B → binary `1`

The sender converts a simple message into bits, generates sine-wave tones for those bits, and plays the PCM audio through `AudioTrack`.

The receiver captures microphone samples with `AudioRecord`, analyzes the samples with `FrequencyDetector`, identifies which frequency is present, converts the detected frequencies back into binary values, and displays the decoded message.

This checkpoint deliberately keeps the protocol simple and does not include synchronization, error correction, acknowledgements, retransmission, packetization, or persistence.
