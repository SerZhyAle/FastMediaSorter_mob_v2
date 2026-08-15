**Status:** Archived

# S0414 — Bugfix: Open-in-FMS opens Browse for Downloads `raw:` document URIs

## Problem

From a standalone player, the overflow item "Open in FastMediaSorter" should close the player and
reopen the same file inside the in-app `PlayerActivity`, on the file's position, with folder paging
(browse back/forth). Instead it opens Browse (`MainActivity`).

Observed flow (device log, 2026-06-13):
- A local PDF is opened from another app via the Downloads provider:
  `content://com.android.providers.downloads.documents/document/raw%3A%2Fstorage%2Femulated%2F0%2FDownload%2FFastMediaSorter_Test%2FDocs%2Ftest_doc_romcom.pdf`.
- Overflow → "Open in FMS" runs `StandaloneFileOperationsHandler.openInFms()`.
- `ResolveOpenInFmsTargetUseCase` short-circuits to `NotResolvable` and the handler falls back to
  `launchMainActivity()` (Browse).

## Root cause

The feature chain from S0389 (Phase 02 resolver + Phase 05 Open-in-FMS resolver) is correct, but the
local-path resolver `ResolveLocalPathFromUriUseCase.resolveViaDocumentId` does not handle the
Downloads provider `raw:` document id.

- The document id is `raw:/storage/emulated/0/Download/.../test_doc_romcom.pdf`.
- `split(':', limit = 2)` yields `["raw", "/storage/emulated/0/Download/.../test_doc_romcom.pdf"]`.
- `parts[0]` is `raw`, which is neither `primary` nor a MediaStore volume, so the method returns null.
- `resolveViaDataColumn` also returns null for the Downloads documents provider.
- Result: `NotLocal` → `NotResolvable` → Browse fallback.

The `raw:` form already carries the absolute filesystem path; the resolver only needs to read it.

## Goal

Open-in-FMS on a local file exposed through a Downloads `raw:` document URI resolves to a local path,
so the existing S0389 chain reuses or creates a persistent folder resource and opens the in-app player
at that file. No change to the non-local fallback for genuinely SAF-only / network files.

## Scope

In scope:
- `ResolveLocalPathFromUriUseCase`: resolve the `raw:` document id to its embedded absolute path.

Out of scope:
- Numeric / `msf:` Downloads document ids (legacy `public_downloads` lookup) — left `NotLocal`.
- Secondary volumes (SD card, USB) — unchanged, still `NotLocal`.
- Open-in-FMS routing / resource-creation logic — already correct in S0389.

## Approach

- In `resolveViaDocumentId`, before the primary-volume branch, detect the `raw` document-id prefix and
  return `parts[1]` when it is an absolute path to an existing file.

## Verification

- Build: standard debug (`.\a.ps1 fk` then `.\a.ps1 fc`) green.
- Unit: `ResolveLocalPathFromUriUseCaseTest` gains a `raw:` case; per-class XML report passing.
- Device (BlockNeedUserTest): open a local file from another app via the Downloads provider (a file
  under `/storage/emulated/0/Download/...`), overflow → "Open in FMS". Expect the standalone player
  closes and the in-app player opens on that file inside its folder resource, pageable back/forth — not
  Browse. A persistent folder resource is created for that folder if none exists.

## Last Audit

**Date:** 2026-06-15
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 5 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 0

### Manual / on-device

- [x] Downloads `raw:` document URI → standalone player → "Open in FMS" → in-app PlayerActivity, folder-paged, not Browse — verified on-device 2026-06-15 (emulator-5554)
- [x] `ResolveLocalPathFromUriUseCase` `raw:` branch resolves embedded absolute path — code + unit test `downloads raw document uri resolves to embedded local path`

## Revision History

- **2026-06-15** — by `/spec-test-device` (`sdk_gphone16k_x86_64`, device: emulator-5554, Android 17/SDK 37)
  - Scenario: temp/S0414_mobile_test_scenario_20260615_2241.md · PASS/FAIL/SKIPPED 4/0/0 · Errors in log: 0
  - Downloads `raw:` document URI → standalone PDF player → "Open in FMS" → in-app PlayerActivity (folder-paged), not Browse. `S0414:` resolver tag fired on-device.
