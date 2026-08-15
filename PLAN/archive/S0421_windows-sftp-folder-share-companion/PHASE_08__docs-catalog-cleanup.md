# Phase 08 - Docs, Catalog, Cleanup

**Strategic spec:** [`../S0421_windows-sftp-folder-share-companion.md`](../S0421_windows-sftp-folder-share-companion.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done (08.1 adapted to CLAUDE.md §11 - see notes)
**Depends on:** all prior phases
**Blocks:** none - final phase
**Steps done:** 5 / 5
**Started:** 2026-07-10
**Completed:** 2026-07-10

---

## Objective

Land the user-facing docs and inventory for the Level A capability: FEATURES trilingual (strategic §8 mandates it), feature inventory record, catalog regen for the new Android classes, and HOW_TO entry for the import flow.

---

## Prerequisites

- [ ] Phases 01-07 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` + `docs/FEATURES_RU.md` + `docs/FEATURES_UK.md` | Modified | ≤ 500 |
| `docs/HOW_TO.md` (+ `_RU` / `_UK`) | Modified | ≤ 500 |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | n/a |
| `docs/ALL_FEATURES.jsonl` | Modified | n/a |

---

## Steps

### Step 08.1 - FEATURES trilingual

**Files:** `docs/FEATURES.md`, `_RU.md`, `_UK.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Add the strategic §8 sentence (EN/RU/UK provided in the spec) describing the Windows companion + one-action import. Match existing FEATURES formatting. Docs prose style: `..` not `...`, plain hyphen, Russian Ё where correct.

**Verification:**

- `Grep` - companion sentence present in all three FEATURES files.

**Status:** `[x]` done

---

### Step 08.2 - HOW_TO import entry

**Files:** `docs/HOW_TO.md`, `_RU.md`, `_UK.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a short HOW_TO section: install the Windows companion, pick a folder, scan the QR (or open `.fmscfg`) on the phone, done. Any "Settings > .." path with U+2192 must resolve in the settings manifest (S0558 gate). Trilingual parity.

**Verification:**

- `Grep` - HOW_TO import section present in all three locales.
- `pwsh -NoProfile -File scripts/quality/assert-howto-settings-paths.ps1` exits 0.

**Status:** `[x]` done

---

### Step 08.3 - Feature inventory record

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** Phase 07

**Prompt for developer:**

> Record the shipped capability via `scripts/all_features/add.ps1` (EN-only), e.g. id `resource.companion-sftp-import`, area `resource`, flavors matching where SFTP import ships, `-Spec S0421`. Do not hand-edit the JSONL.

**Verification:**

- `Grep` - a record with `"spec":"S0421"` present in `docs/ALL_FEATURES.jsonl`.
- `pwsh -NoProfile -File scripts/all_features/validate.ps1` exits 0.

**Status:** `[x]` done

---

### Step 08.4 - Catalog regen (Android classes)

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** Phase 07

**Prompt for developer:**

> Regenerate the Android class catalog so the new companion-import classes are indexed: `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`. Set `role`/`status` on the new classes via `set.ps1`. The Go subproject is not catalogued (not an Android module).

**Verification:**

- `Grep` - `ImportCompanionConfigUseCase` present in `dev/CATALOG/app_v2.jsonl` after sync.

**Status:** `[x]` done

---

### Step 08.5 - Spec closure

**Files:** - (spec status via tooling)
**Depends on:** Step 08.1-08.4

**Prompt for developer:**

> Run `/spec-check S0421`. Level A is the shipped slice; Level B remains a documented future phase (not a Verified gap). If desktop+device end-to-end verification is still pending hardware, `/spec-check` may set `BlockNeedUserTest` with a note describing the desktop-install + phone-import walkthrough.

**Verification:**

- `/spec-check S0421` runs and sets a terminal status (`Verified` or justified `BlockNeedUserTest`).

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 08.*` is `[x] done` (08.1 adapted, see notes).
- [x] HOW_TO trilingual parity; S0558 gate green (`howto-settings-paths: OK - 49 recipes .. locales in parity`).
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (2122 records); ALL_FEATURES record present (`network-cloud.companion-sftp-import`, validate PASS: 501 records).
- [x] Dev log: batched via `post-change.ps1` (Phase 07) + `close-and-log.ps1 -DevLogs` at ticket closure.

**Execution notes (2026-07-10, /spec-all):**

- **08.1 adapted (spec self-correction):** this phase predates the CLAUDE.md §11 rule that `docs/FEATURES*.md` is the curated showcase populated ONLY by `/skill-release` from the ALL_FEATURES diff - never edited per-spec. The strategic §8 trilingual sentence stays in the spec; `/skill-release` picks the capability up from the `network-cloud.companion-sftp-import` ALL_FEATURES record at the next release. No direct FEATURES edit made.
- 08.2: "Import a Windows Companion Share" section + TOC entry (renumbered) added to `docs/HOW_TO.md` + `_RU` + `_UK` after the SFTP/FTP section; no `Settings >` path used, S0558 gate green.
- 08.3: flavors `standard,photos,legacy,vr,noLegal` - mirrors the existing `sources-storage.sftp-network-source` record (SFTP absent in lite).
- 08.4: new classes tagged `status=new` with roles via `set.ps1` (CompanionConfigParser/Dto, ImportCompanionConfigUseCase, AddResourceCompanionCoordinator).
- 08.5: ticket set to `BlockNeedUserTest` (desktop walkthrough note in header); device-test gate probed per /spec-all - see final report.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Docs-only revert; no code/data impact.
