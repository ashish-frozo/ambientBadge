# Ambient Scribe Troubleshooting Guide

This guide consolidates high-signal recovery steps for operations, support, and QA teams. Each entry lists symptoms, root causes, and verification commands.

## 1. Installation & Environment

| Symptom | Likely Cause | Resolution | Verification |
| --- | --- | --- | --- |
| Gradle sync fails with "android-34 not found" | Android SDK API 34 missing | Install via Android Studio SDK Manager → Android 14 (34) | `./gradlew tasks` succeeds |
| Build fails on `ksp` task | Outdated Kotlin or KSP version cache | Run `./gradlew --stop && ./gradlew clean build` | `./gradlew build` passes |
| `NOTICE.md` out of date warning | Dependencies changed without regenerating SBOM | Run `./gradlew generateNotice` and commit diff | `./gradlew verifyNoticeUpToDate` passes |

## 2. Audio Capture

| Symptom | Likely Cause | Resolution | Verification |
| --- | --- | --- | --- |
| No audio waveform during call | Microphone permission denied or OEM kill | Re-trigger Record Audio permission; consult OEM playbook via in-app link | Observe `AudioTranscriptionPipeline` logs showing non-zero energy |
| Recording stops after screen lock | App not excluded from battery optimization | Use in-app prompt to open Doze exemption, verify granted | `adb shell dumpsys deviceidle whitelist` contains app ID |
| Saved last 30 seconds empty | Ring buffer not initialized (initialize failed) | Check `AudioCapture.initialize()` return; ensure RECORD_AUDIO granted | `adb logcat` shows `AudioCapture` init success |

## 3. Transcription & AI

| Symptom | Likely Cause | Resolution | Verification |
| --- | --- | --- | --- |
| Whisper stalls at start | Model warm-up exceeding tier budget | Confirm device tier, clear thermal throttling; relaunch pipeline | `PerformanceManager` logs `tier=A` or `tier=B` and warm-up timings |
| Encounter note missing prescription | Validator rejected due to formulary or dosage issues | Inspect `PrescriptionValidator` warnings; adjust dict in clinic config | Unit test `PrescriptionValidatorTest` passes |
| "AI throttled" error | `AIResourceManager` triggered thermal/memory guard | Allow device to cool; ensure other heavy apps closed | `adb shell dumpsys thermalservice` shows `status=none` |

## 4. PDF Export & Sharing

| Symptom | Likely Cause | Resolution | Verification |
| --- | --- | --- | --- |
| Export fails with encryption error | Clinic key rotated but not provisioned | Rerun provisioning via `ClinicKeyProvisioningService`; validate Keystore | `AuditLogger` emits `KEY_ROTATED` followed by `EXPORT_OK` |
| QR code unreadable | Template scaling for Tier B fallback | Recreate export using `FailSafeExportManager`; verify printer DPI settings | `PDFIntegrationTest` passes with generated file |
| NOTICE missing dependency | Generated file outdated | Run `./gradlew generateNotice` | `NOTICE.md` includes offending library |

## 5. Security & Compliance

| Symptom | Likely Cause | Resolution | Verification |
| --- | --- | --- | --- |
| Audit chain verification fails | Missing genesis record or key mismatch | Run `AuditGenesisManager` to rebuild chain; validate key rotation window | `AuditVerifier` CLI reports `chain_status: ok` |
| Consent toggle ignored | Encounter flagged `consent_off` but pipeline cached state | Force refresh via `ConsentManager.invalidateCache()`; restart pipeline | Audit log emits `CONSENT_REFRESHED` |
| Crash on startup for specific clinic | Incompatible keystore hardware or missing attestation | Switch to software fallback keys per clinic policy; escalate to security | `KeystoreHazardSuite` unit tests pass |

## 6. Testing & CI

| Symptom | Likely Cause | Resolution | Verification |
| --- | --- | --- | --- |
| CI fails `jacocoCoverageVerification` | Coverage dropped below thresholds | Run targeted tests, expand suites for affected package | `./gradlew test jacocoTestReport` shows ratios ≥ thresholds |
| `StrictModePolicyTest` kills instrumentation | New code executes network on main thread or leaks closables | Wrap calls in coroutines/IO dispatcher or ensure streams closed | Re-run `./gradlew connectedAndroidTest --tests "*StrictModePolicyTest*"` |
| License gate fails | New dependency uses unapproved license | Evaluate license, request approval, or replace dependency | `./gradlew verifyLicenseAllowlist` passes |

Keep this guide updated when new incident patterns emerge. Cross-reference with deployment and security documentation for extended runbooks.
