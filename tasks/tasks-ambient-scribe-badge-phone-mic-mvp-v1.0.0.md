---
type: task_plan
version: 1.0.0
feature_key: ambient-scribe-badge-phone-mic-mvp
prd_ref: tasks/prd-ambient-scribe-badge-phone-mic-mvp-v1.0.0.md
date: 2025-09-19
status: Draft
---

## Relevant Files
- `app/src/main/kotlin/com/frozo/ambientscribe/audio/AudioCapture.kt` - Audio recording and VAD implementation with WebRTC VAD and 30s ring buffer
- `app/src/test/kotlin/com/frozo/ambientscribe/audio/AudioCaptureTest.kt` - Comprehensive audio capture unit tests with 95% coverage
- `app/src/main/kotlin/com/frozo/ambientscribe/AmbientScribeApplication.kt` - Application class with logging and StrictMode configuration
- `app/src/main/kotlin/com/frozo/ambientscribe/MainActivity.kt` - Main activity with audio permission handling and recording demo
- `app/src/main/AndroidManifest.xml` - Android manifest with audio permissions and foreground service configuration
- `app/build.gradle.kts` - Gradle build configuration with WebRTC and testing dependencies
- `app/src/main/res/layout/activity_main.xml` - Main activity layout with recording status indicators
- `app/src/main/res/values/strings.xml` - String resources for UI text
- `app/src/main/res/values/themes.xml` - Material Design 3 theme configuration
- `app/src/main/res/values/colors.xml` - Color palette for the application
- `build.gradle.kts` - Top-level Gradle configuration
- `gradle.properties` - Gradle properties with build optimizations
- `settings.gradle.kts` - Gradle settings configuration
- `app/src/main/kotlin/com/frozo/ambientscribe/transcription/ASRService.kt` - CTranslate2 Whisper tiny int8 model integration with confidence scoring
- `app/src/test/kotlin/com/frozo/ambientscribe/transcription/ASRServiceTest.kt` - Comprehensive ASR service tests with confidence level validation
- `app/src/main/kotlin/com/frozo/ambientscribe/transcription/AudioTranscriptionPipeline.kt` - Integration pipeline connecting audio capture to ASR service
- `app/src/main/cpp/whisper_android.cpp` - Native C++ implementation for CTranslate2 Whisper inference
- `app/src/main/cpp/CMakeLists.txt` - CMake configuration for native library build
- `app/src/main/assets/models/whisper-tiny-int8/` - Whisper model files (encoder, decoder, tokenizer, vocab)
- `app/src/main/kotlin/com/frozo/ambientscribe/ai/LLMService.kt` - Local LLM inference for SOAP generation
- `app/src/test/kotlin/com/frozo/ambientscribe/ai/LLMServiceTest.kt` - LLM service tests
- `app/src/main/kotlin/com/frozo/ambientscribe/ui/ReviewActivity.kt` - SOAP and prescription review interface
- `app/src/androidTest/kotlin/com/frozo/ambientscribe/ui/ReviewActivityTest.kt` - UI review tests
- `app/src/main/kotlin/com/frozo/ambientscribe/pdf/PDFGenerator.kt` - A5 prescription PDF generation
- `app/src/test/kotlin/com/frozo/ambientscribe/pdf/PDFGeneratorTest.kt` - PDF generation tests
- `app/src/main/kotlin/com/frozo/ambientscribe/security/AuditLogger.kt` - HMAC-chained audit logging for data purge events
- `app/src/test/kotlin/com/frozo/ambientscribe/security/SecurityTest.kt` - Security and privacy tests
- `app/src/main/kotlin/com/frozo/ambientscribe/performance/ThermalManager.kt` - Comprehensive thermal management with CPU monitoring and throttling
- `app/src/main/kotlin/com/frozo/ambientscribe/performance/DeviceCapabilityDetector.kt` - Device tier detection and hardware capability analysis
- `app/src/main/kotlin/com/frozo/ambientscribe/performance/PerformanceManager.kt` - Adaptive performance management with thermal integration
- `app/src/test/kotlin/com/frozo/ambientscribe/performance/ThermalManagerTest.kt` - Thermal management unit tests
- `app/src/test/kotlin/com/frozo/ambientscribe/performance/PerformanceTest.kt` - Performance benchmark tests
- `app/src/main/kotlin/com/frozo/ambientscribe/telemetry/MetricsCollector.kt` - Telemetry event collection
- `app/src/test/kotlin/com/frozo/ambientscribe/telemetry/MetricsTest.kt` - Telemetry tests
- `app/src/main/kotlin/com/frozo/ambientscribe/audio/DiarizationEvaluator.kt` - Diarization quality evaluation and single-speaker fallback
- `app/src/test/kotlin/com/frozo/ambientscribe/audio/DiarizationAcceptanceTest.kt` - Diarization acceptance criteria tests
- `app/src/main/kotlin/com/frozo/ambientscribe/audio/AudioFormatProbe.kt` - Audio format detection and fallback
- `app/src/main/kotlin/com/frozo/ambientscribe/audio/AudioResampler.kt` - High-quality audio resampling
- `app/src/test/kotlin/com/frozo/ambientscribe/audio/AudioFormatTest.kt` - Audio format probe and resampling tests
- `app/src/main/kotlin/com/frozo/ambientscribe/audio/AudioProcessingConfig.kt` - Audio processing settings management
- `app/src/main/kotlin/com/frozo/ambientscribe/ui/AudioProcessingSettingsView.kt` - UI for audio processing settings
- `app/src/test/kotlin/com/frozo/ambientscribe/audio/AudioProcessingConfigTest.kt` - Audio processing settings tests
- `app/src/test/kotlin/com/frozo/ambientscribe/audio/BufferAutotuneTest.kt` - Buffer auto-tuning tests
- `app/src/main/kotlin/com/frozo/ambientscribe/services/OEMKillerWatchdog.kt` - OEM app killer detection and auto-restart
- `app/src/test/kotlin/com/frozo/ambientscribe/services/OEMKillerWatchdogTest.kt` - OEM killer watchdog tests
- `app/src/main/kotlin/com/frozo/ambientscribe/transcription/EphemeralTranscriptManager.kt` - RAM-only transcript management
- `app/src/test/kotlin/com/frozo/ambientscribe/transcription/EphemeralTranscriptManagerTest.kt` - Ephemeral transcript tests
- `docs/OEM_BATTERY_OPTIMIZATION_PLAYBOOKS.md` - OEM-specific battery optimization guidance
- `app/src/main/kotlin/com/frozo/ambientscribe/updates/ModelUpdater.kt` - Model/prompt CDN downloads and updates
- `app/src/main/kotlin/com/frozo/ambientscribe/backend/BackendClient.kt` - Railway backend integration (optional)
- `app/src/main/res/values/strings.xml` - Localization strings (EN/HI/TE)
- `app/src/main/assets/schemas/soap.schema.json` - SOAP note JSON schema validation
- `app/src/main/assets/schemas/rx.schema.json` - Prescription JSON schema validation
- `app/src/main/assets/prompts/soap.txt` - SOAP generation prompts
- `app/src/main/assets/prompts/rx.txt` - Prescription generation prompts
- `app/src/androidTest/kotlin/com/frozo/ambientscribe/e2e/` - End-to-end integration tests
- `backend/src/app.js` - Node.js Express app with OIDC and API endpoints
- `backend/src/routes/docs.js` - Document upload with idempotency and HMAC validation
- `backend/src/routes/audit.js` - Audit log ingestion and retention management

### Notes
- Place unit tests alongside code modules following Android testing conventions
- Use JUnit5 for JVM tests unless Android constraints force JUnit4; Espresso for UI tests
- Run tests with `./gradlew test` and `./gradlew connectedAndroidTest`
- Gradle: AAB ABI splits (arm64-v8a), ProGuard/R8 rules for CT2/llama `.so`, minSdk=29, targetSdk=34
- CI: unit + instrumented tests on Firebase Test Lab matrix for Tier A/B devices
- Manifest: `android:allowBackup="false"`, network security config, battery optimization exemption flow
- Printer retry with backoff and user hint for A5 scaling; notification actions localized EN/HI
- Lock NDK version in Gradle; add ProGuard keep rules for JNI entry points
- Pin Bluetooth SCO behavior doc if future badge uses BT
- Add StrictMode network-on-main policy in debug to catch accidental I/O
- Pin Gradle and AGP versions; enable reproducible builds
- Sentry/Crashlytics PII scrub rules checked in and diff-tested
- Pin minifyEnabled true for release with -whyareyoukeeping checks to keep JNI
- Add "Print grayscale proof" thumbnail before sending to printer to avoid A5 scaling surprises
- Persist NS/AEC/AGC choice per-clinic (not per-device) for consistent pilots
- Boot/monotonic provenance: include boot_id + monotonic timestamps in audit to survive wall-clock jumps

## Tasks

- [x] PT-1 Audio Capture and Real-time Processing (maps: FR-1, FR-2, FR-3, AC-1)
  - [x] ST-1.1 Implement WebRTC VAD audio capture with 16kHz mono recording and 30s ring buffer
  - [x] ST-1.2 Integrate CTranslate2 Whisper tiny int8 model for ASR with confidence scores
  - [x] ST-1.3 Implement adaptive threading (2-6 threads) and thermal management
  - [x] ST-1.4 Add energy-based speaker diarization with one-tap doctor/patient role swap
  - [x] ST-1.5 Create automatic ring buffer purging on session end (no raw audio retention)
  - [x] ST-1.6 Test audio capture unit tests with 95% coverage
  - [x] ST-1.7 Test ASR integration with pilot mode accuracy metrics (not sample conversations)
  - [x] ST-1.8 Test thermal management scenarios with graceful performance degradation
  - [x] ST-1.9 Add "Delete last 30s" control; verify ring buffer purge and audit EVENT=PURGE_30S
  - [x] ST-1.10 Acquire partial WakeLock during capture; release on stop; Doze exclusion tested
  - [x] ST-1.11 ASR error taxonomy (NETWORK, CPU_THERMAL, DECODER_FAIL); UI fallbacks + tests
  - [x] ST-1.12 Diarization acceptance: DER ≤18% on pilot scripts; swap-accuracy ≥95%; failover to single-speaker mode
  - [x] ST-1.13 Input format probe → fallback to 48 kHz; high-quality resample to 16 kHz; underrun/overrun tests
  - [x] ST-1.14 Toggle and AB test NS/AEC/AGC; persist choice; impact logged
  - [x] ST-1.15 Set THREAD_PRIORITY_AUDIO; dynamic buffer autotune; emit underrun/overrun metrics
  - [x] ST-1.16 OEM killer watchdog: detect FG termination, auto-restart, log cause; MIUI/Oppo playbooks doc + tests
  - [x] ST-1.17 Ephemeral transcript mode: RAM-only buffers; crash-recovery hook purges on next launch; audit ABANDON_PURGE

- [ ] PT-2 AI Processing and SOAP Generation (maps: FR-4, FR-10, AC-2, AC-5)
  - [ ] ST-2.1 Integrate local LLM (1.1-1.3B 4-bit quantized) using llama.cpp
  - [ ] ST-2.2 Implement JSON schema validation for SOAP output against EncounterNote v1.0 schema
  - [ ] ST-2.3 Create prescription confidence scoring system (Green ≥0.8, Amber 0.6-0.79, Red <0.6)
  - [ ] ST-2.4 Add fallback rules-based generation for LLM failures
  - [ ] ST-2.5 Implement clinic-level formulary policies and medication validation
  - [ ] ST-2.6 Test LLM service unit tests with Med-entity F1 ≥0.85 validation
  - [ ] ST-2.7 Test confidence scoring accuracy with prescription field accuracy ≥95%
  - [ ] ST-2.8 Test fallback mechanism produces valid output when LLM fails
  - [ ] ST-2.9 ASR medical biasing: use CTranslate2 logit bias for token sets + post-pass dictionary correction (levenshtein to formulary). Success: +≥2 pp Med-F1, no >0.5 pp WER regression
  - [ ] ST-2.10 ASR/LLM thread budgeting: One shared executor or semaphore to prevent oversubscription; emit contention metric
  - [ ] ST-2.11 Runtime CPU/ISA probe: Detect NEON/FP16/SDOT; pick kernels/quant accordingly; fallback path test on low-end B-tier
  - [ ] ST-2.12 LLM OOM guard: wrap inference with OOM catcher → shrink ctx/threads, emit LLM_OOM_RECOVERED

- [ ] PT-3 Review Interface and Content Editing (maps: FR-5, AC-2)
  - [ ] ST-3.1 Design and implement SOAP review activity UI with 5 bullet points per section
  - [ ] ST-3.2 Create prescription table with inline editing for drug/dose/frequency
  - [ ] ST-3.3 Implement brand↔generic medication toggle functionality
  - [ ] ST-3.4 Add confidence override and manual confirmation flows for Red-flagged items
  - [ ] ST-3.5 Create review completion workflow targeting 60-second review time
  - [ ] ST-3.6 Test UI review activity with Espresso for edit workflows
  - [ ] ST-3.7 Test accessibility compliance for screen reader support and WCAG 2.1 AA
  - [ ] ST-3.8 Test edit persistence and validation error display
  - [ ] ST-3.9 Consent revoke during session → immediate stop, purge ring, audit CONSENT_OFF; UX test
  - [ ] ST-3.10 Apply-to-all freq/duration control; audited; Espresso tests
  - [ ] ST-3.11 Process-death recovery: restore pending edits, cursor position; test via `am crash`
  - [ ] ST-3.12 "Abandon session" UX → wipe drafts/edits, keep audit only; Espresso test
  - [ ] ST-3.13 Block copy-to-clipboard on sensitive fields (or warn + auto-clear clipboard ≤30s)
  - [ ] ST-3.14 In-app "Recording" banner + tone on start/stop

- [ ] PT-4 PDF Generation and Export (maps: FR-6, AC-3)
  - [ ] ST-4.1 Implement A5 PDF generator with clinic branding support
  - [ ] ST-4.2 Embed Noto fonts for multilingual support (EN/HI/TE text rendering)
  - [ ] ST-4.3 Add QR code generation linking to JSON data with SHA-256 hash
  - [ ] ST-4.4 Implement PDF encryption using Android Keystore with AES-GCM
  - [ ] ST-4.5 Create export workflow with file path management
  - [ ] ST-4.6 Test PDF generation unit tests validating A5 format and content accuracy
  - [ ] ST-4.7 Test QR code functionality and hash verification
  - [ ] ST-4.8 Test encryption and security for PDF key generation
  - [ ] ST-4.9 Printer matrix tests (HP LaserJet, Canon imageCLASS, Brother HL) with A5 margins 10mm, grayscale legibility, Devanagari shaping; printer discovery quirk tests (A5 scaling, grayscale)
  - [ ] ST-4.10 "Print test page" tool in app
  - [ ] ST-4.11 Scoped storage: save PDFs/JSON in app-private; expose Print/Share via SAF/PrintManager only
  - [ ] ST-4.12 Doctor reg# validation (state regex/length); fail with inline error
  - [ ] ST-4.13 Clinic header asset ingest (logo/address/reg#); checksum + preview screen
  - [ ] ST-4.14 Add AHS doctor acknowledgement line; PDF test validates presence
  - [ ] ST-4.15 Rasterized A5 fallback if printer rejects vector; auto-detect + test
  - [ ] ST-4.16 Pseudolocale print proof for EN-XA strings on A5
  - [ ] ST-4.17 Apply FLAG_SECURE on Review/Export; UI test verifies screenshots blocked
  - [ ] ST-4.18 Hindi ICU line-break + Latin-digit enforcement; print proof
  - [ ] ST-4.19 Fail-safe export: block Print/Share if QR self-verify fails or key absent; red banner UX; tests
  - [ ] ST-4.20 Encrypt JSON with Keystore AES-GCM (streaming), zeroize buffers, rotate with PDF key, decrypt only for print/share, purge on "Abandon"
  - [ ] ST-4.21 Require BiometricPrompt/Device credential to Print/Share; session-scoped approval (e.g., 5 min)
  - [ ] ST-4.22 Fuzz JSON→QR→verify; reject on overflow/garbage

- [ ] PT-5 Security, Privacy, and Compliance (maps: FR-7, Section 7, AC-3)
  - [ ] ST-5.1 Implement HMAC-chained audit logging with AuditEvent v1.0 schema
  - [ ] ST-5.1a Add HMAC chain with `prev_hash` + `kid`; offline verifier sample
  - [ ] ST-5.2 Create consent management system with DPDP compliance and CONSENT_ON/OFF tracking
  - [ ] ST-5.3 Implement patient ID hashing with clinic-specific salt using SHA256
  - [ ] ST-5.4 Add data subject rights (export/delete by encounter/date)
  - [ ] ST-5.4a DSR log scrubbing job (remove encounter↔patient mapping; preserve integrity)
  - [ ] ST-5.5 Implement 90-day automatic data purge with audit trails
  - [ ] ST-5.6 Create Android Keystore key management with 180-day rotation
  - [ ] ST-5.6a Device-loss recovery: Option A server re-encryption - PDFs uploaded encrypted to clinic pubkey
  - [ ] ST-5.7 Test security and privacy compliance validating encryption, hashing, and audit trails
  - [ ] ST-5.8 Test DPDP compliance flows with legal requirements verification
  - [ ] ST-5.9 Play Data Safety form automation + prelaunch verification; screenshots archived
  - [ ] ST-5.10 Auto-backup audit on devices; confirm no data in cloud backups
  - [ ] ST-5.11 Clinic key provisioning: upload/rotate clinic RSA/ECC pubkey; pin kid; tests for rotation and rollback
  - [ ] ST-5.12 Threat model (STRIDE) + privacy review (LINDDUN); actions tracked; merge gate
  - [ ] ST-5.13 Log-redaction linter in CI (blocks PHI strings)
  - [ ] ST-5.14 Clinic private-key custody via KMS/Vault; rotation + access audit; recovery procedure doc + drills
  - [ ] ST-5.15 CVE scan job (Trivy/OSS-Index) for native libs; block on High/Critical
  - [ ] ST-5.16 Play listing privacy policy URL + consent copy parity check; archive evidence
  - [ ] ST-5.17 Crash/ANR PHI scrubber at SDK hook; unit tests with synthetic PHI payloads
  - [ ] ST-5.18 Audit genesis & rollover spec; chain-stitch after reinstall/time change; verifier tests (gap, dup, out-of-order)
  - [ ] ST-5.19 Keystore hazard suite: OS upgrade, biometric reset, "clear credentials"; recovery UX and tests
  - [ ] ST-5.20 SBOM (CycloneDX) + dependency attestations; CI artifact retention
  - [ ] ST-5.21 TLS cert pinning for CDN + Railway (OkHttp CertificatePinner); rotation playbook; pin-break tests
  - [ ] ST-5.22 Decision doc: reconcile remote wipe token vs server re-encryption as the default device-loss path
  - [ ] ST-5.23 On CONSENT_OFF, cancel all WorkManager jobs for that encounter, wipe queued payloads (docs/audit/telemetry), and emit CANCELLED_COUNT in audit

- [ ] PT-6 Device Compatibility and Performance Optimization (maps: FR-9, NFR-1, NFR-2, NFR-3, NFR-4, NFR-5, NFR-6, AC-4, AC-6)
  - [ ] ST-6.1 Implement device tier detection (A vs B) based on RAM/CPU capabilities
  - [ ] ST-6.2 Add install-time device compatibility blocking via Play Store
  - [ ] ST-6.3 Implement performance targets: First model load ≤8s Tier A / ≤12s Tier B (p95)
  - [ ] ST-6.4 Add battery optimization with ≤6%/hour consumption on Tier A devices
  - [ ] ST-6.5 Implement thermal management with CPU monitoring >85% threshold
  - [ ] ST-6.6 Add memory management with LLM unloading when idle
  - [ ] ST-6.7 Create foreground service with battery optimization exemption UX flow
  - [ ] ST-6.8 Test performance benchmarks validating latency and battery targets
  - [ ] ST-6.9 Test thermal management scenarios with user notifications
  - [ ] ST-6.10 Battery metric via BatteryManager sampled every 60s; moving avg %/h
  - [ ] ST-6.11 Thermal thresholds (CPU>85% for 10s → reduce threads; recover <60% for 30s; thermal=SEVERE → ctx=1k)
  - [ ] ST-6.12 ANR watchdog + StrictMode in debug; JNI load guard
  - [ ] ST-6.13 Measure first-token p50/p95 per tier across 3 noise profiles; assert ≤0.8/1.2 s (A/B)
  - [ ] ST-6.14 Measure draft-ready p50/p95 per tier; assert ≤8/12 s (A/B)
  - [ ] ST-6.15 BatteryStats validation on Tier A/B; assert ≤6%/8% per hour
  - [ ] ST-6.16 FTL matrix devices: Tier A = Pixel 6a/A54/Note13 Pro; Tier B = Redmi 10/M13/G31; run perf suites
  - [ ] ST-6.17 Audio route change handling (wired/BT/speaker); auto-pause on route loss; tests
  - [ ] ST-6.18 android:foregroundServiceType="microphone" in manifest; CTS/behavior test API 29–34
  - [ ] ST-6.19 Time-budget SLAs per stage (ASR chunk, LLM, PDF); timeout → user hint; telemetry
  - [ ] ST-6.20 AAB size guard ≤100 MB; CI fails if exceeded; model split config
  - [ ] ST-6.21 Add BLUETOOTH_SCAN flow if headset discovery enabled; denial UX and tests
  - [ ] ST-6.22 Telephony integration: incoming/outgoing call → auto-pause, restore via AudioFocus/AudioDevice callbacks only (no READ_PHONE_STATE). Test asserts permission not requested and ≤200 ms pause on simulated call
  - [ ] ST-6.23 Competing recorder detection (AudioManager active clients); block & guide; telemetry

- [ ] PT-7 Localization and Accessibility (maps: FR-8, NFR-7, NFR-8)
  - [ ] ST-7.1 Implement English and Hindi language support with complete UI translation
  - [ ] ST-7.2 Add Telugu support behind te_language_enabled feature flag
  - [ ] ST-7.3 Implement Devanagari script rendering for Hindi text display
  - [ ] ST-7.4 Add WCAG 2.1 AA accessibility compliance with screen reader support
  - [ ] ST-7.5 Implement large touch targets and voice feedback for UI elements
  - [ ] ST-7.6 Test localization coverage with all strings externalized and translated
  - [ ] ST-7.7 Test accessibility compliance with accessibility scanner validation
  - [ ] ST-7.8 Test font rendering across languages with Noto fonts
  - [ ] ST-7.9 Clinic-approved HI AHS templates; legal disclaimer footer
  - [ ] ST-7.10 Telugu strings behind flag; print render check
  - [ ] ST-7.11 Pseudolocale tests (en-XA, ar-XB) for truncation/bidi; fix clipping
  - [ ] ST-7.12 Accessibility stress: Dynamic type at 200% and smallest-width 320dp reflow tests; minimum 48dp touch targets assertion

- [ ] PT-8 Telemetry and Metrics (maps: Section 9)
  - [ ] ST-8.1 Implement encounter event tracking (EVT-1 through EVT-5) with required fields
  - [ ] ST-8.2 Add pilot mode KPI metrics collection (WER, F1 score) - pilot mode only; real consults use proxy metrics
  - [ ] ST-8.3 Create metrics aggregation and local storage with privacy compliance
  - [ ] ST-8.4 Implement optional backend metrics reporting to Railway when available
  - [ ] ST-8.5 Add crash-free session rate tracking for reliability metrics
  - [ ] ST-8.6 Test telemetry collection validating all events emit correctly
  - [ ] ST-8.7 Test metrics accuracy against expected KPI calculations
  - [ ] ST-8.8 Test privacy compliance ensuring no PII in telemetry data
  - [ ] ST-8.9 Pilot mode accuracy (local scripted sets) for WER/Med-F1; real consults collect edit-rate + Rx confirm-rates only
  - [ ] ST-8.10 Edit cause codes (heard/ambiguous/unsupported freq) for each correction; CSV export
  - [ ] ST-8.11 Event schema doc for EVT-1..EVT-5 (fields, types, PII policy); JSON schema lint in CI
  - [ ] ST-8.12 Proxy metrics dashboard: edit-rate and Rx confirm-rates by clinic/doctor/tier
  - [ ] ST-8.13 Pilot opt-in switch for accuracy metrics; audited; off by default
  - [ ] ST-8.14 Emit POLICY_TOGGLE and BULK_EDIT_APPLIED audit/telemetry events with actor, before/after
  - [ ] ST-8.15 Skew monitor metric; warn if |device-server| >120 s; audit event
  - [ ] ST-8.16 SNTP fetch to record network_time when backend absent; include device_tz, device_offset in EncounterNote
  - [ ] ST-8.17 If SNTP fails, fall back to pinned-backend Date header (HTTPS) and record (network_time, source). Assert |device–server| ≤2 s p95; audit TIME_SOURCE=SNTP|HTTPS

- [ ] PT-9 Rollout and Guardrails (maps: Section 10)
  - [ ] ST-9.1 Implement feature flags (ambient_scribe_enabled, llm_processing_enabled, te_language_enabled)
  - [ ] ST-9.2 Create kill switch for immediate audio capture disable
  - [ ] ST-9.3 Implement graceful fallback to manual note entry
  - [ ] ST-9.4 Add device allowlist for pilot phases
  - [ ] ST-9.5 Create atomic model swapping with 14-day retention
  - [ ] ST-9.6 Implement ramp plan: Internal → Pilot 1 → All 3 pilots → Expansion
  - [ ] ST-9.7 Test feature flag functionality without crashes
  - [ ] ST-9.8 Test kill switch and rollback scenarios for reliability
  - [ ] ST-9.9 OEM permission playbooks (MIUI/Samsung) in in-app help; link from mic-denied dialog
  - [ ] ST-9.10 Request POST_NOTIFICATIONS (13+); denial UX; ensure FG notification persistent
  - [ ] ST-9.11 Release gate: block rollout if p95 latency, battery, or privacy lint fails; canary 5% with auto-rollback
  - [ ] ST-9.12 Signed remote-config (Ed25519), fail-closed on bad sig
  - [ ] ST-9.13 Upload policy flag: Clinic-level setting for Wi-Fi-only vs metered OK; verify WorkManager constraints honor it

- [ ] PT-10 Documentation and Handover (maps: Section 11)
  - [ ] ST-10.1 Create comprehensive README with setup instructions
  - [ ] ST-10.2 Document architecture decision records (ADRs)
  - [ ] ST-10.3 Create API documentation for internal components
  - [ ] ST-10.4 Document deployment and release process
  - [ ] ST-10.5 Create troubleshooting guide for common issues
  - [ ] ST-10.6 Document security and privacy implementation
  - [ ] ST-10.7 Test documentation completeness and accuracy
  - [ ] ST-10.8 Create handover checklist for operations team
  - [ ] ST-10.9 Jacoco per-module min coverage gates (Audio 85, PDF 90, Security 95, Overall 85); CI fail on drop
  - [ ] ST-10.10 License allowlist & attribution auto-gen (CycloneDX → NOTICE.md); CI gate
  - [ ] ST-10.11 StrictMode CI: instrumentation test fails on any network-on-main or leaked closables

- [ ] PT-11 Backend (Optional, Railway) (maps: Section 13 PRD)
  - [ ] ST-11.1 Express app + OIDC (issuer, token TTL 1h, refresh 24h; scopes docs:write, docs:read, audit:read)
  - [ ] ST-11.2 POST /v1/docs (multipart) with HMAC metadata, SHA-256; Idempotency-Key (24h), 409 on conflict
  - [ ] ST-11.3 POST /v1/audit append; atomic insert; JSON logs with request_id
  - [ ] ST-11.4 Retention job (90d purge); alerts: disk>80%, 5xx>1%, p95>500ms
  - [ ] ST-11.5 Rate limit (60 rpm, burst 120) + basic WAF/allowlist
  - [ ] ST-11.6 Runbook: key rotate, throttle, rollback, export to S3
  - [ ] ST-11.7 Server stamps canonical_time on uploads; client records local monotonic; order verified
  - [ ] ST-11.8 Tests: Idempotency 409 replay, rate-limit 429, WAF allowlist, structured logs with request_id
  - [ ] ST-11.9 Retention job unit + E2E: create >90d docs, verify purge + audit trail
  - [ ] ST-11.10 Offline ordering rule: if no backend, order by local monotonic + wall clock; on next upload, reconcile
  - [ ] ST-11.11 GET /v1/verify?encounter_id&hash → {status: ok|mismatch|not_found}; unit + E2E tests
  - [ ] ST-11.12 Offline queue for docs/audit with journal, exponential backoff, retry caps; chaos test (process kill mid-upload)
  - [ ] ST-11.13 Client WorkManager upload queue: constraints, backoff, journaling; E2E chaos tests
  - [ ] ST-11.14 Add X-Timestamp + X-Nonce to /v1/docs & /v1/audit; server rejects stale/duplicate; E2E replay tests
  - [ ] ST-11.15 Backend rejects uploads for encounters marked consent_off (403 with code CONSENT_OFF); E2E test that late replays are denied and client purges
  - [ ] ST-11.16 Queue only opaque URIs to encrypted blobs; no raw JSON in WM DB. CI test dumps workdb to confirm zero PHI strings; chaos test ensures requeue keeps encryption

- [ ] PT-12 Model/Prompt Updates & Signed Output (maps: Section 9, 12)
  - [ ] ST-12.1 Model CDN download (HTTP range resume, checksum+signature); store in app private dir
  - [ ] ST-12.2 One-tap rollback to N-1; keep previous model 14 days; integrity check UI
  - [ ] ST-12.3 Prompt-pack ZIP with signature; atomic swap; version tag in note
  - [ ] ST-12.4 QR→SHA-256(note JSON) generator; verifier CLI spec + server endpoint
  - [ ] ST-12.5 Decision doc: PAdES later; QR-hash now (document trust path)
  - [ ] ST-12.6 CDN resume-download tests (HTTP range) with mid-stream kill
  - [ ] ST-12.7 Signature rotation test: reject old signature, accept new; roll back to N-1 in one tap
  - [ ] ST-12.8 Verifier CLI E2E: QR hash → server endpoint → note JSON hash match
  - [ ] ST-12.9 Stamp model_version and prompt_pack_version into each EncounterNote and OpsMetrics; tests
  - [ ] ST-12.10 Post-update rollback simulation (bad signature/model); verify one-tap N-1 works and events logged
  - [ ] ST-12.11 Formulary update packs (signed ZIP, version, rollback); integrity tests
  - [ ] ST-12.12 Model drift guardrails: compare edit-rate/Rx-confirm vs N-1; auto halt on >X% regression
  - [ ] ST-12.13 Embed model_sha & prompt_sha into EncounterNote + QR; verifier checksums; tests
  - [ ] ST-12.14 Provenance stamp expansion: Include app_versionCode, git_sha, device_tier, and abi in EncounterNote + PDF footer; log in audit on export
  - [ ] ST-12.15 Model download preflight: disk-space check, staged dir, atomic rename; low-disk E2E

- [ ] PT-13 Android Ops, Security & Compliance (maps: Section 1, 8, 11)
  - [ ] ST-13.1 Foreground Service notification actions (Pause/Stop/Review); channel IMPORTANCE_HIGH
  - [ ] ST-13.2 Audio focus handler (GAIN_TRANSIENT; on loss→auto-pause <200ms)
  - [ ] ST-13.3 Manifest: android:allowBackup="false"; network security config; ignore battery optimizations UX flow
  - [ ] ST-13.4 Keystore keys with kid; rotation every 180d; rollover 365d; tag kid in AuditEvent/PDF
  - [ ] ST-13.5 Audit JSONL atomic writes (temp+rename) + fsync on CONSENT/EXPORT; crash replay test
  - [ ] ST-13.6 Device posture: root detect → block + guidance; lock-task/kiosk guide; root-detect test validation
  - [ ] ST-13.7 Remote wipe token (backend on); Panic purge launcher action
  - [ ] ST-13.8 DPDP Data Safety form, privacy policy, dependency licenses screen (OSS compliance)
  - [ ] ST-13.9 Play Integrity API attestation; block if verdict risky/unknown (pilot allowlist bypass)
  - [ ] ST-13.10 Crash reporting (Crashlytics/Sentry) + symbol upload in CI; ANR breadcrumbing
  - [ ] ST-13.11 RECORD_AUDIO permission UX with OEM overlays and rationale dialogs; analytics for denial reasons
  - [ ] ST-13.12 Low-storage LRU purge and user warning at <500 MB; tests
  - [ ] ST-13.13 BLUETOOTH_CONNECT runtime flow (12+); route-change denial recovery tests
  - [ ] ST-13.14 cleartextTrafficPermitted=false; trust-anchors system only (ignore user CAs); deny cleartext for all domains; test via MITM CA
  - [ ] ST-13.15 OkHttp interceptor/DNS guard that only allows calls to approved hosts (CDN, backend). Block IP-literal egress http(s)://<ip> and log NET_EGRESS_IP_LIT. Anything else → fail-closed + audit NET_EGRESS_BLOCKED. Instrumentation test with bogus domain

- [ ] PT-14 Clinic Policy & Identity (maps: PRD Sec.15, 7)
  - [ ] ST-14.1 Brand↔generic policy UI: approver (admin role), reason, timestamp; bump clinic_policy_version
  - [ ] ST-14.2 Policy rollback one-click (admin-only); audit entry emitted
  - [ ] ST-14.3 Identifier normalization: phone→E.164, MRN→clinic regex, other→trim rules; then hash; tests
  - [ ] ST-14.4 Policy change approver role = clinic_admin; rollback admin-only; tests

## Implementation Summary for PT-1

We have successfully implemented all the subtasks in PT-1 (Audio Capture and Real-time Processing):

1. **ST-1.12 Diarization acceptance**: 
   - Created `DiarizationEvaluator` to monitor diarization quality using metrics like DER and swap accuracy
   - Implemented single-speaker fallback mode when quality is poor
   - Added comprehensive tests in `DiarizationAcceptanceTest`

2. **ST-1.13 Input format probe**:
   - Implemented `AudioFormatProbe` to detect supported audio formats
   - Added fallback to 48kHz when 16kHz is not supported
   - Created `AudioResampler` for high-quality resampling
   - Added tests in `AudioFormatTest`

3. **ST-1.14 Audio processing toggle**:
   - Implemented `AudioProcessingConfig` for managing NS/AEC/AGC settings
   - Added persistence with SharedPreferences and A/B test grouping
   - Created UI with `AudioProcessingSettingsView`
   - Added impact logging with `MetricsCollector`
   - Added tests in `AudioProcessingConfigTest`

4. **ST-1.15 Thread priority and buffer autotune**:
   - Set `THREAD_PRIORITY_AUDIO` for audio processing threads
   - Implemented dynamic buffer auto-tuning based on underrun/overrun detection
   - Added metrics emission for buffer statistics
   - Created tests in `BufferAutotuneTest`

5. **ST-1.16 OEM killer watchdog**:
   - Created `OEMKillerWatchdog` to detect foreground termination by OEM battery optimizers
   - Implemented auto-restart capability
   - Added OEM-specific guidance in `OEM_BATTERY_OPTIMIZATION_PLAYBOOKS.md`
   - Created tests in `OEMKillerWatchdogTest`

6. **ST-1.17 Ephemeral transcript mode**:
   - Implemented `EphemeralTranscriptManager` for RAM-only transcript storage
   - Added crash recovery hooks to purge data on next launch
   - Added audit logging with `ABANDON_PURGE` events
   - Created tests in `EphemeralTranscriptManagerTest`
   - Integrated with UI through a checkbox option

## Gaps (from PRD)
- G-1 Specific clinic header format and doctor registration validation requirements - owner: Product, due: TBD
- G-2 Custom medication formularies vs standard database decision - owner: Clinical Team, due: TBD  
- G-3 DPDP compliance consent banner legal copy - owner: Legal Team, due: TBD
- G-4 Printer compatibility requirements for A5 PDF formatting - owner: UX Research, due: TBD
- G-5 Model corruption backup/recovery mechanism specifications - owner: Engineering, due: TBD
- G-6 Signed output strategy (QR now, PAdES later) - owner: Eng/Legal, due: TBD
- G-7 Model CDN/hosting + checksum/sign policy - owner: Engineering, due: TBD
- G-8 Clinic policy audit UI and rollback - owner: Product, due: TBD
- G-9 Kiosk/MDM rollout guidance - owner: Operations, due: TBD
- G-10 Remote wipe/panic purge design - owner: Eng/Ops, due: TBD

## Validation Checklist
✅ **Requirements Coverage:**
- All 10 Functional Requirements (FR-1 through FR-10) mapped to parent tasks and subtasks
- All 8 Non-Functional Requirements (NFR-1 through NFR-8) mapped to tasks  
- All 6 Acceptance Criteria (AC-1 through AC-6) mapped to tasks
- Security, Privacy, and Compliance tasks present (Section 7) - PT-5 with expanded security subtasks
- Telemetry and Metrics tasks present (Section 9) - PT-8 with pilot mode accuracy metrics
- Rollout and Guardrails tasks present (Section 10) - PT-9 with feature flags and kill switches
- Backend integration tasks present - PT-11 with Node.js/Railway implementation
- Model/prompt update system present - PT-12 with CDN downloads and signed verification
- Android ops and compliance present - PT-13 with foreground service, security posture
- Clinic policy and identity management present - PT-14 with admin governance and ID normalization

✅ **Task Structure:**
- 14 Parent Tasks (PT-1 through PT-14) covering all PRD areas plus operational requirements
- 205+ Subtasks (ST-x.y) with verifiable acceptance criteria (no \"done:\" markers)
- Each subtask has observable completion criteria
- Testing included in every parent task with realistic pilot mode metrics
- Maps: references trace back to PRD sections and operational requirements

✅ **Corrected Technical Specifications:**
- Language: All file paths converted to Kotlin (.kt files)
- Performance: First model load ≤8s Tier A / ≤12s Tier B (p95)
- Audio: Audio focus loss → auto-pause <200ms
- Updates: Model rollback to N-1 succeeds with one tap
- Printing: Printer matrix 3/3 pass with A5 and Devanagari rendering
- Compliance: Auto-backup disabled verified in Play prelaunch
- Security: Keystore keys with kid rotation every 180d
- Audit: JSONL atomic writes with fsync on CONSENT/EXPORT

✅ **Acceptance Criteria Validation:**
- p95 first-token/draft-ready meet tier targets across noise profiles
- Model update resume and rollback validated with one-tap functionality
- QR→hash verifier passes; mismatch triggers fail-closed behavior
- Policy toggle emits audited event and increments clinic_policy_version
- Data Safety declaration verified by Play prelaunch; no backup artifacts found
- Canonical time stamping ensures audit ordering integrity under clock skew
- Identifier normalization (E.164/MRN/other) applied before hashing
- Root detection blocks access with user guidance
- "Delete last 30s" purges audio and writes audit within 200 ms
- EncounterNote contains model_version and prompt_pack_version on every export
- Play Integrity attestation enforced; tampered builds blocked
- Clinic key rotation succeeds; old docs decryptable during 365-day rollover
- Crash-free sessions ≥99.5% with symbols resolving stack traces
- QR verify endpoint returns ok/mismatch/not_found with p95 <300 ms
- Offline queue preserves order, survives crash, and reconciles conflicts
- Disk-low path shows warning and never loses PDFs/JSON; exports succeed or fail cleanly
- DER ≤18% or auto-fallback engaged; audit logged
- "Apply-to-all" emits audit and updates all selected rows correctly
- FG service declared with microphone type; persists across app kills
- 48 kHz-only devices pass ASR with p95 first-token/draft within targets
- WorkManager queue survives process kill; maintains order; respects constraints
- Notifications permission denied → FG notification still visible; user guided
- AAB ≤100 MB enforced in CI
- NS/AEC/AGC toggles logged; default chosen by measured latency/accuracy
- Screenshots blocked on sensitive screens
- Formulary packs update and roll back with signature verification
- Crash/ANR artifacts contain zero PHI; redaction tests pass
- Export blocks on QR/key failure with explicit UX; no unverified PDFs leave device
- Audit-chain passes continuity checks across reinstall/time skew; verifier flags gaps
- Keystore hazard tests pass on OS upgrade/credential reset
- FG service restarts under OEM killers; session resumes within ≤2 s
- SBOM generated each build and attached to release
- Canary blocks rollout on drift or KPI regression
- Pinned TLS: CDN/backend connections fail closed on pin mismatch; rotation test passes
- Replay-safe: server returns 401/409 on stale/duplicate (timestamp, nonce); p95 verify <300ms
- Zero-residue: abandoning or crashing leaves no transcript/JSON on disk; audit shows purge
- Telephony-safe: call events pause within ≤200ms; resume state intact; no mixed-audio artifacts
- Provenance: QR verify confirms hash, model_sha, prompt_sha against server; mismatch blocks export
- Coverage gates: CI enforces module thresholds; releases blocked on regressions
- No plaintext JSON on disk; export fails closed if key missing
- Print/Share blocked without recent user auth; clipboard scrubs verified
- All HTTP blocked; TLS with user CA fails; pinned hosts still pass

✅ **Testing Strategy:**
- Unit tests: AudioCaptureTest.kt, ASRServiceTest.kt, LLMServiceTest.kt, PDFGeneratorTest.kt, SecurityTest.kt, PerformanceTest.kt, MetricsTest.kt
- Integration tests: End-to-end workflow, thermal management, model updates, backend sync
- UI tests: ReviewActivityTest.kt with Espresso, accessibility compliance
- Security tests: Encryption, audit trails, DPDP compliance, root detection
- Performance tests: Battery benchmarks, thermal thresholds, foreground service persistence
- Printer tests: HP LaserJet, Canon imageCLASS, Brother HL with A5/Devanagari validation
- Pilot mode: Real consults collect edit-rate + Rx confirm-rates (not sample WER)

✅ **Operational Completeness:**
- All 10 gaps from PRD captured with additional 5 operational gaps (G-6 through G-10)
- Backend optional integration with Railway platform
- Model/prompt update pipeline with CDN, checksums, signatures
- Android operational requirements: foreground service notifications, audio focus, battery optimization
- Security posture: root detection, remote wipe, panic purge, device loss recovery
- Play Store compliance: allowBackup=false, Data Safety, OSS licenses
- Kiosk/MDM guidance for deployment
- Filename: `tasks-ambient-scribe-badge-phone-mic-mvp-v1.0.0.md`
