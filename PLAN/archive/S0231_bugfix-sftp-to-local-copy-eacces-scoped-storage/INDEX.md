# Tactical Plan: S0231 — bugfix-sftp-to-local-copy-eacces-scoped-storage

**Strategic spec:** [`../S0231_bugfix-sftp-to-local-copy-eacces-scoped-storage.md`](../S0231_bugfix-sftp-to-local-copy-eacces-scoped-storage.md)
**Feature:** Network → local public collection copy under scoped storage
**Tier:** 3 — Moderate (ad-hoc)
**Priority:** 70
**Status:** BlockNeedUserTest (Implemented; awaiting on-device verification)
**Phases:** 5 / 5 done
**Last updated:** 2026-05-17

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | foundation-writer-classifier | — | ✅ Done | 6/6 | [PHASE_01__foundation-writer-classifier.md](PHASE_01__foundation-writer-classifier.md) |
| 02 | wire-writer-into-network-strategies | 01 | ✅ Done | 6/6 | [PHASE_02__wire-writer-into-network-strategies.md](PHASE_02__wire-writer-into-network-strategies.md) |
| 03 | atomic-strategy-public-collection-short-circuit | 02 | ✅ Done | 3/3 | [PHASE_03__atomic-strategy-short-circuit.md](PHASE_03__atomic-strategy-short-circuit.md) |
| 04 | error-category-and-trilingual-strings | 01 | ✅ Done | 4/4 | [PHASE_04__error-category-and-strings.md](PHASE_04__error-category-and-strings.md) |
| 05 | docs-catalog-cleanup | all | ✅ Done | 4/4 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

All §6 strategic research items were resolved during tactical authoring (see strategic spec §6 — each item now carries `Status: Resolved` and references the phase that implements the decision). No external blockers.

---

## Resolved Research Items (summary)

1. **Public collection boundary** → leading path under `Environment.DIRECTORY_{MUSIC, MOVIES, PICTURES, DCIM, DOWNLOADS, PODCASTS, RINGTONES, NOTIFICATIONS, ALARMS, AUDIOBOOKS}`, subdirs supported via `RELATIVE_PATH` (API 29+). Implemented in Phase 01.
2. **Non-public destinations** → write directly via `FileOutputStream`; on EACCES raise structured `LocalDestinationPermissionDenied` with a localized message offering Downloads / public collection. No SAF picker in this iteration. Implemented in Phase 04.
3. **Non-media file types** → physical path wins over MIME for collection selection; fallback to `MediaStore.Files` if Audio/Video/Images rejects the MIME. MIME via `MimeTypeMap`. Implemented in Phase 01.
4. **Name collision** → handled above MediaStore via existing `overwrite` flag; pre-insert query, delete-on-overwrite, fail with `FileExistsException` otherwise. MediaStore auto-suffix `(1)` never used. Implemented in Phase 01.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` — skipped per strategic §8 ("Без изменений").
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated — new classes have role+status filled via `set.ps1`.
- [ ] `dev/FUNCTIONALITY.log` has one FIX line for S0231.
- [ ] `/spec-check S0231` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0231`.

---

## Blockers Log

- 2026-05-17 — Initial plan: no blockers; all §6 research items resolved during tactical authoring.

---

## Change Log

- 2026-05-17 — Initial tactical plan authored by `/spec-tech`.
