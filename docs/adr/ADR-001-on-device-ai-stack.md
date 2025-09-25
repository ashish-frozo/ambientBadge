# ADR-001: On-Device AI Stack

- Status: Accepted
- Date: 2024-12-01
- Owners: AI Platform Squad

## Context
Ambient Scribe must transcribe audio, extract medical entities, and draft encounter notes without depending on cloud inference. Clinics require offline operation, DPDP compliance forbids raw audio egress, and latency targets mandate sub-10-second turnaround on Tier B Android devices. We evaluated cloud-hosted models, hybrid inference, and full on-device execution.

## Decision
We adopted an all on-device AI architecture built around:
- Whisper-small medical fine-tune running through `ASRService` with device-tier optimizations.
- A lightweight local LLM orchestrated by `AIService` and `LLMService` using quantized weights loaded via `AIResourceManager`.
- Contextual post-processing by `MedicalEntityExtractor`, `PrescriptionValidator`, and formulary lookups to ensure clinical accuracy without network access.
- Thermal and memory guards from `PerformanceManager` to gate concurrent model usage.

This stack ships with each build, keeps PHI local, and is managed by the telemetry layer for pilot-mode metric capture.

## Consequences
- ✅ Compliance: No PHI leaves the handset; simplifies consent and DPDP posture.
- ✅ Resilience: Works in poor connectivity environments while maintaining latency SLAs.
- ⚠️ App size: Models increase APK/ABB footprint; we mitigate via split installs and delta updates.
- ⚠️ Device variability: Tier B hardware needs aggressive scheduling; `TimeBudgetManager` and throttling logic become critical surfaces.
- 🚀 Future work: Modular model delivery (PT-12) will let us hot-swap updated weights without full app releases.
