---
type: prd
version: 1.0.0
feature_key: ambient-scribe-badge-phone-mic-mvp
status: Draft
owner: Ashish
date: 2025-09-18
---

# 1. Overview
- **Summary**: Android app for offline medical transcription using phone microphone. Captures doctor-patient conversations, generates SOAP notes and prescriptions using local AI models, outputs PDFs with consent management and audit trails. Target: fast OPD workflow with <1 min turnaround time.
- **Problem**: Manual documentation slows down OPD consultations, reduces patient interaction time, and creates administrative overhead for doctors.
- **Goal**: Reduce documentation time by ≥60 minutes/day or enable ≥6 additional consultations/day with ≥95% prescription accuracy.

# 2. Goals (SMART)
- **G1**: Achieve WER ≤18%, Med-entity F1 ≥0.85, and Rx field accuracy ≥95% within 3 months of pilot deployment
- **G2**: Process 2-minute conversations to draft completion in ≤10 seconds on Tier A devices (≥6GB RAM) with ≤6% battery consumption per hour
- **G3**: Deploy successfully to 3 pilot clinics with 10 doctors achieving ≥70% D7 retention and crash-free rate ≥99.5%
- **G4**: Maintain complete offline functionality with no raw audio retention and DPDP-compliant consent management

# 3. Non-Goals (Out of Scope)
- **NG1**: Badge hardware integration, Bluetooth connectivity, or dedicated recording devices
- **NG2**: WhatsApp integration, deep EHR integration, or cloud-based AI models
- **NG3**: iOS application or cross-platform deployment
- **NG4**: Real-time collaboration features or multi-doctor workflows
- **NG5**: Advanced analytics, reporting dashboards, or business intelligence features

# 4. User Stories
- **US1**: As a doctor, I want to start recording with one tap so that I can focus on patient interaction without manual note-taking.
- **US2**: As a doctor, I want to review and edit AI-generated SOAP notes and prescriptions within 60 seconds so that I can maintain clinical accuracy.
- **US3**: As a doctor, I want to generate A5 prescription PDFs with clinic branding so that patients receive professional documentation.
- **US4**: As a patient, I want to receive clear prescription and health summary PDFs so that I understand my treatment plan.
- **US5**: As a clinic administrator, I want audit trails of all encounters so that I can ensure consent compliance and data governance.
- **US6**: As a doctor, I want the app to work completely offline so that I'm not dependent on internet connectivity during consultations.

# 5. Functional Requirements
- **FR-1**: The system MUST capture 16 kHz mono audio with WebRTC VAD and maintain a 30-second ring buffer with automatic purging on session end.
- **FR-2**: The system MUST perform real-time ASR using CTranslate2 Whisper tiny int8 with adaptive threading (2-6 threads) and thermal management.
- **FR-3**: The system MUST implement speaker diarization using energy-based turn segmentation with one-tap doctor/patient role swapping.
- **FR-4**: The system MUST generate SOAP notes and prescriptions using local LLM (1.1-1.3B 4-bit) with JSON schema validation and fallback to rules-based generation.
- **FR-5**: The system MUST provide a review interface with inline editing for SOAP bullets and prescription table with brand↔generic toggle.
- **FR-6**: The system MUST generate A5 PDF prescriptions with clinic headers, QR codes linking to JSON, and embedded Noto fonts for multilingual support.
- **FR-7**: The system MUST implement HMAC-chained audit logging with consent tracking (CONSENT_ON/OFF, EXPORT events) and atomic JSONL append.
- **FR-8**: The system MUST support English and Hindi languages at launch with Telugu available behind feature flag.
- **FR-9**: The system MUST enforce device requirements (Android 10+, API 29+, 64-bit ARM, ≥4GB RAM, ≥2GB storage) with install-time blocking.
- **FR-10**: The system MUST implement prescription confidence scoring (Green ≥0.8 auto-accept, Amber 0.6-0.79 review, Red <0.6 confirm) with clinic-level formulary policies.

# 6. Non-Functional Requirements
- **NFR-1**: Performance: First token generation ≤1.0s, complete draft ready ≤10s for 2-minute conversations on Tier A devices.
- **NFR-2**: Performance: Tier B devices (4GB RAM) - first token ≤1.2s, draft ≤12s with degraded LLM context (≤1.5k tokens).
- **NFR-3**: Battery: Power consumption ≤6%/hour on Tier A devices, ≤8%/hour on Tier B devices during active recording.
- **NFR-4**: Reliability: Crash-free sessions ≥99.5%, export success rate ≥99%, foreground service persistence with battery optimization exemption.
- **NFR-5**: Storage: Model assets ≤1.2GB, runtime memory management with LLM unloading when idle, automatic cleanup of temporary files.
- **NFR-6**: Thermal Management: Automatic thread reduction when CPU >85% for 10s, context capping on SEVERE thermal state, user notification banner.
- **NFR-7**: Localization: Support for English/Hindi with Devanagari script rendering, Telugu behind feature flag with proper font embedding.
- **NFR-8**: Accessibility: WCAG 2.1 AA compliance for UI elements, screen reader support, high contrast mode compatibility.

# 7. Security, Privacy, Compliance
- **Data Policy**: No raw audio retention - purge ring buffer on session end, encrypt PDFs/JSON with AES-GCM using Android Keystore, hash patient identifiers with clinic-specific salt.
- **Compliance**: DPDP (Data Protection and Digital Privacy) Act compliance with explicit consent banners, data subject rights (export/delete by encounter/date), opt-in pilot mode.
- **Legal Basis**: Explicit consent for audio processing with clear banner copy, doctor acknowledgment on health summaries, clinic-approved consent language.
- **Threats & Mitigations**: 
  - AuthN/AuthZ: OIDC with 1h access tokens, 24h refresh tokens, scoped permissions (docs:write, docs:read, audit:read)
  - Rate Limiting: 60 requests/minute with 120 burst capacity on backend endpoints
  - Audit Trails: HMAC-chained JSONL with cryptographic integrity verification, tamper-evident logging
  - Data Minimization: Patient ID hashing, PHI redaction in logs, automatic 90-day retention with purge jobs
  - Key Management: Android Keystore non-exportable keys, 180-day rotation policy, 365-day retention for verification

# 8. Acceptance Criteria (Gherkin)
- **AC-1** (maps: FR-1, FR-2)
  **Scenario**: Complete offline transcription workflow
  **Given** a doctor opens the app on a compliant Android device
  **When** they tap start, speak for 2 minutes, and tap stop
  **Then** ASR transcription completes within 10 seconds with confidence scores displayed

- **AC-2** (maps: FR-4, FR-5)
  **Scenario**: SOAP note generation and review
  **Given** transcription is complete with speaker diarization
  **When** the LLM processes the conversation
  **Then** structured SOAP notes appear with 5 bullet points per section and confidence badges

- **AC-3** (maps: FR-6, FR-7)
  **Scenario**: PDF generation and audit logging
  **Given** a doctor reviews and approves the generated content
  **When** they tap "Export" to generate prescription PDF
  **Then** A5 PDF is created with clinic branding, QR code, and audit event logged with HMAC signature

- **AC-4** (maps: FR-9)
  **Scenario**: Device compatibility enforcement
  **Given** a user attempts to install on incompatible device
  **When** the device has <4GB RAM or 32-bit architecture
  **Then** Play Store blocks installation with compatibility message

- **AC-5** (maps: FR-10, NFR-3)
  **Scenario**: Prescription confidence scoring and power management
  **Given** LLM generates prescription with confidence scores
  **When** medications have confidence <0.6 (Red zone)
  **Then** system requires manual confirmation and battery consumption remains <6%/hour

- **AC-6** (maps: NFR-4, NFR-6)
  **Scenario**: Thermal and reliability management
  **Given** device reaches SEVERE thermal state during processing
  **When** CPU temperature exceeds safety thresholds
  **Then** system caps LLM context to 1k tokens, shows optimization banner, and maintains foreground service

# 9. Telemetry & Metrics
- **Events**:
  - **EVT-1**: encounter_start {encounter_id, clinic_id, device_tier, timestamp}
  - **EVT-2**: transcription_complete {encounter_id, wer_estimate, processing_time_ms, model_version}
  - **EVT-3**: review_complete {encounter_id, edit_rate_percent, review_duration_s, confidence_overrides}
  - **EVT-4**: export_success {encounter_id, pdf_size_kb, export_duration_ms, battery_level_percent}
  - **EVT-5**: thermal_event {encounter_id, thermal_state, mitigation_action, cpu_usage_percent}
- **KPIs**: 
  - Activation: Daily/weekly active doctors per clinic
  - Quality: WER, Med-entity F1 score, prescription field accuracy, edit rate
  - Performance: First token latency p95, draft completion time p95, battery consumption rate
  - Reliability: Crash-free session rate, export success rate, foreground service uptime

# 10. Rollout & Guardrails
- **Feature Flag**: ambient_scribe_enabled (clinic-level), llm_processing_enabled, te_language_enabled
- **Ramp**: Internal testing (2 weeks) → Pilot clinic 1 (1 week) → All 3 pilot clinics (2 weeks) → Gradual expansion
- **Kill Switch**: Immediate disable of audio capture, fallback to manual note entry, graceful session termination with data preservation
- **Rollback Plan**: Previous model versions retained for 14 days, atomic model swapping, device allowlist for pilot phases

# 11. Dependencies & Integration Points
- **Android Platform**: API 29+ (Android 10), 64-bit ARM architecture, Android Keystore for cryptographic operations
- **CTranslate2**: Whisper tiny int8 model inference, thread pool management, memory optimization
- **llama.cpp**: Local LLM inference (1.1-1.3B models), 4-bit quantization, context management
- **PDF Generation**: Custom PDF kit with Noto font embedding, A5 page formatting, QR code integration
- **Optional Backend**: Node.js on Railway platform (SLA: 99.9% uptime, p95 <300ms) for signed PDF storage and audit search
- **Model Distribution**: Checksum-verified downloads, delta updates, integrity validation, fallback mechanisms

# 12. Risks & Mitigations
- **R-1**: Low-tier device performance degradation → Implement adaptive scheduling, degraded LLM modes, and clear performance tier messaging
- **R-2**: High ambient noise affecting transcription accuracy → Add placement hints, optional denoising, must-confirm prompts for low confidence
- **R-3**: Railway platform limitations for optional backend → Implement 90-day purge policies, monitoring alerts, S3/R2 migration plan
- **R-4**: Legal compliance issues with medical documentation → Doctor acknowledgment on health summaries, clinic-approved consent copy, audit trail verification
- **R-5**: CTranslate2 ABI compatibility issues → Prebuilt libraries per ABI, integrity checks, graceful fallback to rules-based generation
- **R-6**: Battery drain on extended usage → Foreground service optimization, thermal guards, adaptive processing based on device capabilities

# 13. Design Considerations
- **Mobile UI States**: Home (Ready/Recording/Processing status chips), Recording (timer, confidence bar, pause/stop), Review (SOAP bullets with badges, Rx table with inline edits), Export (print/save options, file path display)
- **Accessibility**: High contrast mode support, screen reader compatibility, large touch targets, voice feedback for status changes
- **Responsive Design**: Portrait-optimized layout, keyboard-friendly editing interface, clear visual hierarchy for clinical workflow
- **Error States**: Microphone permission denied, storage full, background kill recovery, model loading failures with recovery steps
- **Offline-First**: Complete functionality without network, local asset storage, graceful degradation for optional cloud features

# 14. Assumptions
- **A-1**: Doctors have basic Android device familiarity and will follow device placement guidelines for optimal audio capture
- **A-2**: Clinic environments will have moderate noise levels (≤75 dBA) suitable for phone microphone capture
- **A-3**: Prescription formats can be standardized across target clinics with common medication databases
- **A-4**: DPDP compliance requirements are satisfied with explicit consent banners and local processing
- **A-5**: 4GB RAM devices can handle degraded LLM performance with acceptable user experience
- **A-6**: Railway platform provides sufficient reliability for optional backend services during pilot phase

# 15. Open Questions
- **Q-1**: What specific clinic header format and doctor registration number validation is required for prescription PDFs?
- **Q-2**: Should the app support custom medication formularies per clinic or use a standard database?
- **Q-3**: What is the exact consent banner copy that needs legal approval for DPDP compliance?
- **Q-4**: Are there specific printer compatibility requirements for A5 PDF scaling and formatting?
- **Q-5**: What backup/recovery mechanism is needed if the local model files become corrupted?

# 16. Appendix

## Data Schemas

### EncounterNote v1.0
```json
{
  "schema_version": "1.0",
  "clinic_policy_version": "1.0", 
  "model_version": "whisper-tiny-int8@ct2-1",
  "prompt_pack_version": "p1",
  "device_tier": "A|B",
  "encounter_id": "uuid",
  "clinic_id": "string",
  "doctor_id": "string", 
  "patient_id_type": "phone|MRN|other",
  "patient_ref": "hash:v1:salt32:SHA256",
  "ts": "ISO8601",
  "soap": {
    "subjective": [{"text": "", "conf": 0.0}],
    "objective": [{"text": "", "conf": 0.0}], 
    "assessment": [{"text": "", "conf": 0.0}],
    "plan": [{"text": "", "conf": 0.0}]
  },
  "rx": [{"drug": "", "strength": "", "form": "", "route": "", "dose": "", "freq": "", "duration": "", "notes": "", "confidence": 0.0}],
  "red_flags": [],
  "hash": "sha256:...",
  "pdf_uri": "app://..."
}
```

### AuditEvent v1.0
```json
{
  "encounter_id": "uuid",
  "kid": "kid-2025Q3", 
  "prev_hash": "sha256:...",
  "event": "CONSENT_ON|CONSENT_OFF|EXPORT|ERROR",
  "ts": "ISO8601",
  "actor": "app|doctor|admin",
  "meta": {}
}
```

## Device Tier Specifications
- **Tier A**: Pixel 6a, Samsung A54, Redmi Note 13 Pro (≥6-8GB RAM) - Full features, optimal performance
- **Tier B**: Redmi 10, Samsung M13, Moto G31 (4GB RAM) - Degraded LLM context, extended processing times

## Success Criteria Gates
- **Accuracy**: WER ≤18%, Med-entity F1 ≥0.85, Rx field accuracy ≥95%
- **Speed**: First token ≤1.0s, draft ready ≤10s (2-min dialog), edit-rate ≤20%
- **Reliability**: Crash-free ≥99.5%, export success ≥99%
- **Pilot Metrics**: 3 clinics, 10 doctors, D7 ≥70%, ≥60 min/day saved or ≥6 extra consults/day
