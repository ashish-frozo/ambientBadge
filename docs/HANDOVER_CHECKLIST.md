# Operations Handover Checklist

Use this checklist before transferring ownership of Ambient Scribe to operations or customer success teams. All items must be ✅ prior to production rollout.

## 1. Build & Signing
- [ ] Release artifacts (`.aab`, `.apk`) generated via `./gradlew bundleRelease assembleRelease`.
- [ ] SHA-256 checksums recorded and stored in release tracker.
- [ ] Release signed with clinic-approved keystore; credentials archived in secret manager.

## 2. Documentation
- [ ] README updated with current version, prerequisites, and run commands.
- [ ] ADRs reflect latest architectural decisions (`docs/adr/`).
- [ ] API reference and troubleshooting guide distributed to support teams.
- [ ] Deployment readme (`docs/DEPLOYMENT_READINESS.md`) acknowledged by ops lead.

## 3. Security & Compliance
- [ ] `verifyLicenseAllowlist` and `verifyNoticeUpToDate` tasks pass.
- [ ] Audit genesis file rotated within SLA; `AuditVerifier` output attached.
- [ ] Consent policies validated for each clinic; sample encounter exported and purged.
- [ ] Threat and privacy reviews (STRIDE, LINDDUN) signed off.

## 4. Testing & Quality Gates
- [ ] `./gradlew test jacocoTestReport jacocoCoverageVerification` run with thresholds met.
- [ ] `./gradlew connectedAndroidTest --tests "*StrictModePolicyTest*"` passes on target devices.
- [ ] Performance regression suite executed on Tier A and Tier B device representatives.
- [ ] Accessibility checks reviewed (TalkBack, dynamic type, contrast).

## 5. Monitoring & Telemetry
- [ ] Metrics endpoints configured; proxy metrics dashboard shared with operations.
- [ ] Pilot-mode metrics toggle verified (default OFF).
- [ ] Time skew alerts integrated with monitoring system.

## 6. Rollout & Support
- [ ] Feature flags and kill switches validated in staging.
- [ ] OEM battery optimization guides accessible from in-app help.
- [ ] Support escalation matrix documented with contact windows.
- [ ] Incident response playbook (including remote wipe and data purge) rehearsed.

## Sign-off
- [ ] Engineering Lead
- [ ] Security Lead
- [ ] Operations Lead
- [ ] Customer Success Lead

Retain the completed checklist in the release documentation archive.
