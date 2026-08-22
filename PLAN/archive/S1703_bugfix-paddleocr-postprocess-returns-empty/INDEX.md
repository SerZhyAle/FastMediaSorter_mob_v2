# Tactical Plan: S1703 - bugfix-paddleocr-postprocess-returns-empty

**Strategic spec:** [`../S1703_bugfix-paddleocr-postprocess-returns-empty.md`](../S1703_bugfix-paddleocr-postprocess-returns-empty.md)
**Research inputs:** none - the owner's decision of 2026-08-16 replaced the investigation: the engine is withdrawn rather than fixed.
**Feature:** withdrawal of the PaddleOCR engine, its payload and its setting
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 90
**Status:** Not started
**Phases:** 4 / 4 done
**Last updated:** 2026-08-17

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in
> the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | stored-value-compatibility | - | ✅ Done | 2/2 | [PHASE_01__stored-value-compatibility.md](PHASE_01__stored-value-compatibility.md) |
| 02 | remove-engine-code | 01 | ✅ Done | 3/3 | [PHASE_02__remove-engine-code.md](PHASE_02__remove-engine-code.md) |
| 03 | remove-payload-and-dependency | 02 | ✅ Done | 3/3 | [PHASE_03__remove-payload-and-dependency.md](PHASE_03__remove-payload-and-dependency.md) |
| 04 | ui-docs-and-inventory | 03 | ✅ Done | 3/3 | [PHASE_04__ui-docs-and-inventory.md](PHASE_04__ui-docs-and-inventory.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Ordering rationale

The compatibility of what users already have on their devices comes first, before a single class is deleted:
a device whose stored setting says `PADDLE` must keep working the moment the code that understood the word is
gone. Everything after that is subtraction, ordered so the tree compiles at every step - callers before
implementations, implementations before the payload they load, and the user-visible surface last, where its
removal is a decision rather than a leftover.

---

## Measured starting state (2026-08-17)

- 58 files under `app_v2/src` mention Paddle.
- The setting is **a plain String** - `AppSettings.ocrEngineType: String = "TESSERACT"` - not an enum, so a
  stored `PADDLE` cannot fail to deserialise; it can only select an engine that no longer exists. That makes
  phase 01 a normalisation rather than a migration.
- The value travels through the backup path (`BackupData`, `BackupMapper`, `ImportSettingsUseCase`), so an
  old backup can carry `PADDLE` long after the code is gone.
- The payload is delivered on demand: `DeliverableSet`, `DeliverableDescriptorCatalog` and
  `DeliveredNativeLibraryLoader` name the Paddle `.so` and models beside the Tesseract ones.
- `docs/ALL_FEATURES.jsonl` carries two Paddle-related records; the strategic §3 says the engine record moves
  to `removed` rather than being deleted.

---

## Pre-Implementation Blockers

None. The owner's decision of 2026-08-16 is recorded in strategic §3 and replaces the root-cause work.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES*.md` - not edited here; the release skill owns the showcase.
- [x] `docs/ALL_FEATURES.jsonl` - the engine record reads `removed`.
- [x] `dev/CHANGELOG.md` has an entry for every modified file.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated - classes were deleted.
- [x] `docs/settings/settings-manifest.json` and the settings reference regenerated - a setting disappeared.
- [ ] `/spec-check S1703` returns `Verified`.

---

## How to Track Progress

1. Before starting a phase: flip its row to `🚧 In Progress`. Update `Phases: X/4 done`.
2. During a phase: flip a step to `[~] in progress` when started, `[x] done` when its Verification passes.
   Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip the row to `✅ Done`,
   bump the counter.
4. If blocked: flip to `⛔ Blocked`, add a bullet to the Blockers Log.

---

## Blockers Log

- none

---

## Change Log

- 2026-08-17 - Initial tactical plan authored by `/spec-tech` after measuring the removal surface.
