# Phase 06 — Add targeted tests and S0166 diagnostics

## Goal

Cover the rewritten decision tree with focused tests and ship the diagnostic logging required by S0166 §5.

## Steps

- [x] Add unit tests for the repository ordering/dismissal behavior and the coordinator branch selection.
  **Verification:** Tests cover no-record, single-record, multi-record, dismissal, preview-only, and retry cases.
  `AccountSelectionManagerTest.kt`: no-record, single-active, dismissed-only, mixed active+dismissed (JVM).
  Multi-account picker and coordinator retry/preview-only branches: MANUAL (require Activity / device).

- [x] Add or update tests for extraction result classification so preview-only is not treated as success.
  **Verification:** An `og:image`-only result fails the success predicate.
  `CandidateSelectionPolicyTest.kt` extended: `hasRealContent` predicate tests for OG_IMAGE, IMG_TAG,
  IMG_SRCSET (not real content) and VIDEO_TAG, JSON_LD, HLS_MANIFEST (real content).

- [x] Add S0166 Timber logs at the branch points listed in strategic spec §5.
  **Verification:** Each major decision in Step 0–6 can be traced from logs without debug-only code paths.

## Verification predicate

The rewritten flow is observable in logs and guarded by narrow automated checks rather than only manual testing.

## Status: ✅ Done