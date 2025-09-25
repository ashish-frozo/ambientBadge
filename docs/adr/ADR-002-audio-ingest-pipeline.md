# ADR-002: Audio Ingest Pipeline

- Status: Accepted
- Date: 2024-12-03
- Owners: Audio Platform Squad

## Context
The product must ingest bidirectional phone audio with diarization, preserve the last 30 seconds for deletion, and remain robust against OEM background restrictions. We compared using `MediaRecorder` in call-audio mode, `AudioRecord` with VOICE_RECOGNITION source, and OS-level call recording APIs (where available). We also needed pluggable post-processing for NS/AEC/AGC and diarization hooks.

## Decision
We standardized on `AudioRecord` in VOICE_RECOGNITION mode wrapped by `AudioCapture` and `SpeakerDiarization`:
- `AudioCapture` provides a double-sized ring buffer, energy-level VAD, and a Flow-based streaming API consumed by `AudioTranscriptionPipeline`.
- Thread priority is locked to `THREAD_PRIORITY_AUDIO` and buffers autotune based on underruns, meeting the 120 ms jitter target.
- OEM-kill resilience is handled by `OEMKillerWatchdog` and wake locks validated in `WakeLockTest`.
- Diarization integrates locally via `SpeakerDiarization` with a fallback single-speaker mode controlled by acceptance metrics.

## Consequences
- ✅ Deterministic latency with Flow-based streaming and minimal JNI crossings.
- ✅ Flexible post-processing; `AudioProcessingConfig` toggles NS/AEC/AGC parameters per clinic.
- ⚠️ Device coverage: Some OEMs block VOICE_RECOGNITION across call audio; fallback documentation landed in troubleshooting guides.
- ⚠️ Complexity: Managing ring buffers and 30-second retention adds maintenance overhead but satisfies deletion SLAs.
- 🚀 Future work: Evaluate hardware AAudio path on Android 13+ for lower latency once API coverage stabilizes.
