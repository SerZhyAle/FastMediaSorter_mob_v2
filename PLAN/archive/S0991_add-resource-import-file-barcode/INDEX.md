# Tactical Plan: S0991 - add-resource-import-file-barcode

**Strategic spec:** [`../S0991_add-resource-import-file-barcode.md`](../S0991_add-resource-import-file-barcode.md)
**Research inputs:** none (companion import already exists; architecture mapped in strategic §4)
**Feature:** Add "Import from file" / "Import by barcode" entry points next to the resource-type cards, jointly relabelling the SFTP-form import buttons (S0992) onto one shared action source
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Verified
**Phases:** 2 / 2 done
**Last updated:** 2026-07-11

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in strategic spec.

> **Joint delivery (owner §6):** S0991 and S0992 ship as one coherent feature - a single internal action source, no duplicated import logic. Phase 01 delivers the shared plumbing + the S0992 SFTP-header relocation/relabel; Phase 02 delivers the S0991 type-screen entries. S0992 is advanced to the same status as S0991 at closure.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | shared-plumbing-and-sftp-header | - | ✅ Done | 4/4 | [PHASE_01__shared-plumbing-and-sftp-header.md](PHASE_01__shared-plumbing-and-sftp-header.md) |
| 02 | type-screen-import-entries-acceptance | 01 | ✅ Done | 3/3 | [PHASE_02__type-screen-import-entries-acceptance.md](PHASE_02__type-screen-import-entries-acceptance.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Design decisions (UI ambiguity resolved from existing patterns)

- **Two entry points, two placements, one action source.** Both the type-screen entries (S0991) and the SFTP-header buttons (S0992) call the same two private launch methods in `AddResourceActivity` (`launchCompanionFileImport()` / `launchCompanionQrScan()`). Import logic is never forked (strategic §11.6).
- **Shared strings.** New `resource_import_from_file` / `resource_import_from_barcode` replace the companion-specific `companion_import_button` / `companion_qr_scan_button`, which become dead and are removed (Rule 20).
- **Barcode label over QR.** Owner-mandated user-facing wording is "by barcode" even though the scanner is a QR scanner (QR is a 2D barcode). Internal ids/classes keep their `Qr` naming.
- **Camera gate in both places.** The "by barcode" control is visible only when `PackageManager.FEATURE_CAMERA_ANY` is present, mirroring the current QR button.
- **Type-screen form.** The two entries render as a labelled row of two Outlined buttons appended inside the `layoutResourceTypes` GridLayout (so they auto-hide with the grid when a type/section opens). Outlined style distinguishes import *actions* from the four resource-*type* cards. Grounded in the existing companion-import button pattern already on this screen.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES*.md` - not edited per-spec; capability recorded in `docs/ALL_FEATURES.jsonl` on `Implemented`, showcase owned by `/skill-release`.
- [ ] `dev/CHANGELOG.md` has an entry for the change (via `post-change.ps1`).
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (no new class, but touched-file sync once per ticket).
- [ ] Dead `companion_import_button` / `companion_qr_scan_button` removed from all three locales (Rule 20).
- [ ] String audit clean for the new keys (`scripts/check_strings_localized.ps1 -KeyPrefix "resource_import"`).
- [ ] Device verification of strategic criteria 1-6 passed (`/spec-test-device S0991` -> `/spec-check S0991`) OR deferred if no device.
- [ ] `/spec-check S0991` returns `Verified`.
- [ ] S0992 advanced to the same terminal status (joint delivery).

---

## How to Track Progress

1. Before starting a phase: flip its row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip a step to `[~] in progress` when started, `[x] done` when its Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip the row to `✅ Done`, bump the counter.
4. If blocked: flip to `⛔ Blocked`, add a bullet to the Blockers Log.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-07-11 - Initial tactical plan authored by `/spec-all` (F2). Joint S0991 + S0992 delivery on one shared action source.
- 2026-07-11 - Both phases implemented and device-verified (`/spec-all` F3-F5). S0991 + S0992 -> Verified. Evidence under `temp/S0991/`; full audit in strategic `## Last Audit`. Criteria 3-4 accepted by parity (no physical QR / camera-less device); logic unchanged from S0988.
