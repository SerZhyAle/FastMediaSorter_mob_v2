# Tactical Plan: S1010 - write-resource-picker-local-folder

**Strategic spec:** [`../S1010_write-resource-picker-local-folder.md`](../S1010_write-resource-picker-local-folder.md)
**Research inputs:** none - strategic §6 resolved inline during this tactical pass (S1009 already Verified; ground truth confirmed by direct code read, no separate research artifact needed).
**Feature:** "Local Folder" option in settings write-receiver pickers
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 5 / 5 done
**Last updated:** 2026-08-02

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | local-folder-picker-foundation | - | ✅ Done | 2/2 | [PHASE_01__local-folder-picker-foundation.md](PHASE_01__local-folder-picker-foundation.md) |
| 02 | operations-capture-destinations | 01 | ✅ Done | 2/2 | [PHASE_02__operations-capture-destinations.md](PHASE_02__operations-capture-destinations.md) |
| 03 | screenshot-destination | 01 | ✅ Done | 2/2 | [PHASE_03__screenshot-destination.md](PHASE_03__screenshot-destination.md) |
| 04 | video-snapshot-destination | 01 | ✅ Done | 2/2 | [PHASE_04__video-snapshot-destination.md](PHASE_04__video-snapshot-destination.md) |
| 05 | docs-catalog-cleanup | 02, 03, 04 | ✅ Done | 2/2 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. All three strategic §6 research items are Resolved (2026-08-02, this tactical pass) - S1009's hidden-resource
mechanism is Verified and reused as-is; the full 7-setting/3-insertion-point inventory is confirmed against live
code; the write-check reuses `CheckLocalFolderWritableUseCase` unchanged. Phase 01 may start immediately.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - NOT edited per-spec (owned by `/skill-release`); strategic §8
      already names the user-visible capability, recorded via `docs/ALL_FEATURES.jsonl` at the `Implemented`/
      `BlockNeedUserTest` transition, not here.
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new class `LocalFolderDestinationPickerManager`, modified
      `SettingsViewModel`/`DestinationPickerDialog` signatures).
- [ ] `/spec-check S1010` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1010`.

---

## Blockers Log

None yet.

---

## Change Log

- 2026-08-02 - Initial tactical plan authored by `/spec-tech`. Strategic §6/§10 patched inline with resolved
  findings (S1009 mechanism reuse, 7-setting/3-family insertion map, S1354 spun out for the
  `CaptureDestinationPolicy` SAF gap).
