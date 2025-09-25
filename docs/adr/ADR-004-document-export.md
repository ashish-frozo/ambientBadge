# ADR-004: Document Export and Verification

- Status: Accepted
- Date: 2024-12-07
- Owners: Clinical Documentation Squad

## Context
Clinics require digitally signed PDFs, QR verification, and offline handover workflows. Options included server-side PDF rendering, third-party SDKs, or an in-app stack using iText. We also needed consistent provenance stamping and the ability to operate without backend connectivity.

## Decision
We implemented an in-app export pipeline:
- Encounter notes flow from `AIService` into `PDFGenerator`, templated by clinic assets in `DocumentTemplateManager`.
- `PDFExportManager` orchestrates encryption, QR embedding via `QRCodeGenerator`, and dual-write to scoped storage plus secure cache.
- `FailSafeExportManager` retains a minimal fallback PDF with SOAP summary if rich export fails.
- Provenance metadata (model versions, device tier, hash) is embedded in both PDF metadata and QR payload, aligning with PT-12 roadmap.
- `ScopedStorageManager` ensures exports respect user consent and clinic storage policies.

## Consequences
- ✅ Offline-first exports with deterministic formatting and QR verification path for clinics.
- ✅ Security alignment: PDFs encrypted at rest, audit events emitted for every export.
- ⚠️ Maintenance: iText updates require license scrutiny; NOTICE generation guards this risk.
- ⚠️ Size/performance: Rendering complex templates on Tier B devices pushes thermal limits; mitigated via tier-aware rasterization.
- 🚀 Future work: Upgrade to signed PAdES packages once backend services become available.
