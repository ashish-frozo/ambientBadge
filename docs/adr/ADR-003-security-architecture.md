# ADR-003: Security and Privacy Architecture

- Status: Accepted
- Date: 2024-12-05
- Owners: Security Guild

## Context
Ambient Scribe handles PHI and must satisfy HIPAA, DPDP, and clinic SLAs. We evaluated lighter-weight approaches (basic AES storage, ad-hoc logs) versus a full keystore-backed design with audit chaining, consent enforcement, and aggressive data minimization. The risk model includes compromised devices, OEM background purges, and operator misuse.

## Decision
We implemented a layered security model:
- Hardware-backed keys managed by `KeystoreKeyManager`; symmetric data keys rotated every 180 days with kid tagging.
- Encrypted storage and export guarded by `JSONEncryptionService`, `PDFEncryptionService`, and `ScopedStorageManager`.
- HMAC-chained audit logging with quarterly key rotation via `AuditLogger`, `AuditGenesisManager`, and verifiable trails from `AuditVerifier`.
- Consent and DSR workflows enforced through `ConsentManager` and `DataSubjectRightsService`, preventing uploads when consent is off.
- TLS pinning (`TLSCertificatePinner`) and network allowlists to block MITM and IP literal egress.
- Privacy-by-design review documented in LINDDUN threat model and cross-checked in `PT5_SECURITY_IMPLEMENTATION_SUMMARY.md`.

## Consequences
- ✅ Meets regulatory requirements with verifiable auditability and purge automation.
- ✅ Clear handover to operations via audit tooling and consent dashboards.
- ⚠️ Operational overhead: Key rotation ceremonies and genesis file management require playbooks covered in deployment docs.
- ⚠️ Complexity in testing: High coverage targets (95%+) mandate extensive Robolectric and instrumentation suites.
- 🚀 Future work: Integrate attestation (Play Integrity) gating from PT-13 and signed document provenance from PT-12.
