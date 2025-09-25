# Ambient Scribe Internal API Reference

This reference enumerates primary Kotlin/Java entry points that form the production runtime. It is intended for engineering teams extending the MVP and for operations teams triaging incidents. All paths are relative to `app/src/main/kotlin/com/frozo/ambientscribe` unless noted.

## Application Lifecycle

| Component | Type | Responsibility | Key Interactions |
| --- | --- | --- | --- |
| `AmbientScribeApplication` | `Application` | Bootstraps logging, metrics collection, and the OEM watchdog. | Provides `OEMKillerWatchdog` to `MainActivity`, primes `MetricsCollector`.
| `MainActivity` | `AppCompatActivity` | Foreground capture controls, permission UX, and pipeline orchestration. | Starts/stops `AudioTranscriptionPipeline`, binds UI widgets, invokes `DozeExclusionManager`.
| `services/OEMKillerWatchdog` | Service helper | Detects OEM process kills and schedules restarts with guidance. | Consumed by application + UI; emits telemetry.
| `services/DozeExclusionManager` | Manager | Requests battery optimization exemptions and surfaces OEM guidance flows. | Called from `MainActivity` before long-running capture.

## Audio & Transcription APIs

| Component | Responsibility | Public Surface |
| --- | --- | --- |
| `audio/AudioCapture` | Configures `AudioRecord`, exposes `Flow<AudioData>` with ring buffer persistence and energy analytics. | `initialize()`, `startRecording()`, `getAudioFlow()`, `stopRecording()`, `saveLast30Seconds()`.
| `audio/SpeakerDiarization` | Handles diarization model loading and speaker label assignment. | `initialize()`, `assignSpeaker(short[])`, `reset()`.
| `audio/AudioProcessingConfig` | Persists NS/AEC/AGC toggles per clinic and surfaces metrics. | `setNoiseSuppressionEnabled()`, `exportSnapshot()`.
| `transcription/AudioTranscriptionPipeline` | Coordinates audio capture, ASR, diarization, ephemeral transcript management, and accuracy scoring. | `initialize()`, `startTranscription(ephemeralMode)`, `stopTranscription()`, `transcriptionResults()`, `errors()`.
| `transcription/ASRService` | Executes Whisper inference, streaming partials with confidence scoring. | `initialize()`, `transcribe(samples, energyLevel)`, `shutdown()`.
| `transcription/EphemeralTranscriptManager` | Maintains RAM-only transcript buffers for ephemeral mode. | `startEphemeralSession()`, `appendSegment()`, `flush()`.

## AI & Clinical Intelligence

| Component | Responsibility | Public Surface |
| --- | --- | --- |
| `ai/AIService` | Orchestrates LLM invocation, schema validation, prescription checks, formulary advice, and resource throttling. | `initialize()`, `generateEncounterNote(audioFile, speakerTurns, totalDuration)`, `confidenceLevel(note)`.
| `ai/LLMService` | Wraps local LLM weights and prompt templates; returns typed `EncounterNote`. | `initialize(context)`, `generateEncounterNote(audioFile)`.
| `ai/MedicalEntityExtractor` | Extracts medical entities and flags conflicts in transcripts. | `extractEntities(transcript)`, `validateConsistency(entities)`.
| `ai/PrescriptionValidator` | Validates dosage, frequency, duration, and instruction data anchored to clinic policies. | `validateMedication(medication)`, `validatePrescription(prescription)`.
| `ai/FormularyService` | Maintains clinic formulary cache with generic substitution logic. | `isInFormulary(drugName)`, `suggestGenericAlternative(drugName)`.

## Document & Export APIs

| Component | Responsibility | Public Surface |
| --- | --- | --- |
| `pdf/PDFGenerator` | Renders encounter notes into PDF via iText templates. | `generate(note, outputStream, options)`.
| `pdf/PDFExportManager` | Coordinates PDF creation, encryption, QR embedding, and storage. | `export(note, metadata)`, `share(intentContext, uri)`.
| `pdf/ScopedStorageManager` | Provides scoped storage URIs with retention policies and purge hooks. | `createExportUri(clinicId, encounterId)`, `purgeExpired()`.
| `pdf/FailSafeExportManager` | Produces fallback PDFs when the primary path fails. | `createFallback(note, destination)`.
| `pdf/QRCodeGenerator` | Encodes encounter hashes and provenance fields as QR bitmaps/PDF overlays. | `generate(payload)`, `draw(canvas, rect)`.

## Security & Compliance APIs

| Component | Responsibility | Public Surface |
| --- | --- | --- |
| `security/SecurityManager` | Central coordinator for keystore, audit logging, consent, and purge policies. | `bootstrap(context)`, `enforceConsent(encounterId)`, `recordAudit(event)`.
| `security/KeystoreKeyManager` | Manages hardware-backed encryption keys with rotation windows. | `ensureKeys(seed)`, `rotateKeysIfDue(clock)`, `getActiveKey()`.
| `security/AuditLogger` | Appends HMAC-chained audit entries, emits metrics. | `log(event)`, `verifyChain()`.
| `security/ConsentManager` | Tracks consent lifecycle with audit hooks. | `enableConsent(patientId)`, `disableConsent(patientId)`, `isConsentActive(encounterId)`.
| `security/DataSubjectRightsService` | Handles export/delete requests with audit compliance. | `export(request)`, `delete(request)`.
| `security/PDFEncryptionService` | Encrypts PDFs using per-clinic credentials. | `encrypt(inputStream, outputStream, clinicKey)`.

## Performance & Telemetry APIs

| Component | Responsibility | Public Surface |
| --- | --- | --- |
| `performance/PerformanceManager` | Monitors latency, thermal state, and orchestrates throttling. | `initialize()`, `startSession(sessionId)`, `evaluateBudget(step)`.
| `performance/DeviceTierDetector` | Classifies hardware capabilities into Tier A/B. | `detectTier()`, `deviceProfile()`.
| `performance/ThermalManager` | Observes thermal signals, applies load shedding. | `registerListener(listener)`, `shouldThrottle()`.
| `telemetry/MetricsCollector` | Aggregates structured metrics and forwards to backend/persistent storage. | `record(event)`, `flush()`, `setPilotModeEnabled(enabled)`.
| `telemetry/TimeSkewMonitor` | Validates device-vs-network clock skew and emits alerts. | `measureSkew()`, `recordSource(source)`.

## User Interface Components

| Component | Responsibility | Public Surface |
| --- | --- | --- |
| `ui/SpeakerRoleView` | Visual role assignment for diarized speakers, supports manual swap. | `setOnSpeakerSwapListener { }`, `bind(roleAssignments)`.
| `ui/AudioProcessingSettingsView` | Toggle advanced audio settings tied to `AudioProcessingConfig`. | `setAudioProcessingConfig(config, lifecycleOwner)`.
| `ui/ASRErrorView` | Displays recoverable errors with retry wiring into pipeline. | `setOnRetryListener { }`, `showError(error)`.

## Background & System Services

| Component | Responsibility | Public Surface |
| --- | --- | --- |
| `services/DozeExclusionService` | Foreground service to keep capture alive while requesting Doze exceptions. | `start(context)`, `stop(context)`.
| `service/ForegroundCaptureService` | (Future) placeholder for persistent capture notification channel. | Coordinates FG notifications (see PT-13 roadmap).
| `testing/TestDataFactory` | Supplies fixtures across test suites. | `createEncounterNote()`, `createAudioFrame()`.

## Cross-Cutting Utilities

| Component | Responsibility | Public Surface |
| --- | --- | --- |
| `compatibility/DeviceCompatibilityMatrix` | Tracks validated devices and firmware quirks. | `isSupported(model)`, `guidance(model)`.
| `rollout/FeatureFlagManager` | Reads signed remote-config flags with fail-closed defaults. | `isEnabled(flag)`, `refreshIfStale()`.
| `debug/StrictModeConfigurator` | (PT-13) Centralizes StrictMode policies for developer builds. | `installForDebug()`.

## Event & Data Models

| Component | Responsibility |
| --- | --- |
| `ai/LLMService.kt` inner data classes | `EncounterNote`, `SoapSection`, `Prescription`, `Medication` typed outputs from LLM.
| `telemetry/TelemetryEvent` | Canonical schema for metrics and audit correlation.
| `security/AuditEvent` | HMAC-chainable audit payload with `prev_hash`, `kid`, and metadata.

## Testing Surfaces

| Test | Purpose |
| --- | --- |
| `app/src/test/.../MedicalEntityExtractorTest.kt` | Validates entity extraction heuristics across multi-language transcripts.
| `app/src/test/.../PrescriptionValidatorTest.kt` | Ensures dosing validation and formulary enforcement.
| `app/src/test/.../WakeLockTest.kt` | Verifies wake lock acquisition/release semantics in audio capture.
| `app/src/test/.../PerformanceTest.kt` | Confirms latency budgets and tier classification logic.
| `app/src/androidTest/.../StrictModePolicyTest.kt` | Fails CI on main-thread network or leaked closables.
| `app/src/androidTest/.../PDFIntegrationTest.kt` | End-to-end PDF export validation including QR payload.

Refer to module-level documentation in `docs/` for deeper design discussions and testing evidence.
