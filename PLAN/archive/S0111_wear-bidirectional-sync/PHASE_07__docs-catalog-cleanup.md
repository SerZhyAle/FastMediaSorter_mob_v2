# Phase 07 — Docs, Catalog, and Cleanup

**Strategic spec:** [`../S0111_wear-bidirectional-sync.md`](../S0111_wear-bidirectional-sync.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03, Phase 04, Phase 05, Phase 06
**Blocks:** —
**Steps done:** 7 / 7
**Started:** 2026-05-08
**Completed:** 2026-05-08

---

## Objective

Update feature documentation (EN/RU/UK), regenerate both module catalogs, remove all `Timber.d("S0111:` debug tags, and confirm dev log completeness.

---

## Prerequisites

- [ ] All previous phases are ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | — |
| `docs/FEATURES_RU.md` | Modified | — |
| `docs/FEATURES_UK.md` | Modified | — |
| `dev/CATALOG/app_v2.jsonl` | Modified (auto) | — |
| `dev/CATALOG/app_v2.md` | Modified (auto) | — |
| `dev/CATALOG/wear.jsonl` | Modified (auto) | — |
| `dev/CATALOG/wear.md` | Modified (auto) | — |

---

## Steps

### Step 7.1 — Update `docs/FEATURES.md` — §21 Wear OS

**Files:** `docs/FEATURES.md`
**Depends on:** — start of phase

**Prompt for developer:**

> In `docs/FEATURES.md`, section **21. Wear OS Companion App**, add the following bullet points after the existing "Reactive source list refresh" bullet:
>
> - **Two-way source sync**: Network sources added directly on the watch can be pushed to the phone with a single tap on the Network Sources screen; the phone presents an import confirmation card in the Wear Sync settings.
> - **Remote playback control**: The phone's Wear Sync settings screen shows the track currently playing on the watch (name, source, progress bar) with play/pause, next, and previous buttons.
> - **Watch settings from phone**: Wear companion settings (enabled media types, slideshow interval) can be pushed from the phone's Wear Sync screen without manually opening the watch settings.
> - **Favorites sync**: Files marked as favorites on the watch are automatically synced to the phone's favorites database on each toggle.
> - **FTP and SFTP file browsing**: FTP and SFTP sources added to the watch are now fully browsable — not limited to connection testing.

**Verification:**

- `Grep` — "Two-way source sync" present in `docs/FEATURES.md`.
- `Grep` — "Remote playback control" present.

**Status:** `[x] done`

**Step Log:**

- 2026-05-08 — Verification 2/2 PASS. Files: docs/FEATURES.md (+5 bullets). Dev log recorded.

---

### Step 7.2 — Update `docs/FEATURES_RU.md` — §21

**Files:** `docs/FEATURES_RU.md`
**Depends on:** Step 7.1

**Prompt for developer:**

> Mirror the five new bullets from Step 7.1 in `docs/FEATURES_RU.md`, section 21, in Russian. Use `..` (two dots) as ellipsis style. Use `ё`/`Ё` where grammatically correct.

**Verification:**

- `Grep` — "Двусторонняя синхронизация источников" (or equivalent Russian bullet) present in `docs/FEATURES_RU.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-08 — Verification 1/1 PASS. Files: docs/FEATURES_RU.md (+5 bullets). Dev log recorded.

---

### Step 7.3 — Update `docs/FEATURES_UK.md` — §21

**Files:** `docs/FEATURES_UK.md`
**Depends on:** Step 7.1

**Prompt for developer:**

> Mirror the five new bullets in Ukrainian in `docs/FEATURES_UK.md`, section 21.

**Verification:**

- `Grep` — at least one of the five new bullet concepts is present in Ukrainian in `docs/FEATURES_UK.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-08 — Verification 1/1 PASS. Files: docs/FEATURES_UK.md (+5 bullets). Dev log recorded.

---

### Step 7.4 — Regenerate `app_v2` catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Steps 7.1, 7.2, 7.3

**Prompt for developer:**

> Run:
> ```
> pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
> pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2
> ```
> Review the diff of `app_v2.md` and confirm all new classes added in Phases 01–06 appear.

**Verification:**

- `Grep` — `PushWearSettingsUseCase` present in `dev/CATALOG/app_v2.md`.
- `Grep` — `ImportWatchSourcesUseCase` present.
- `Grep` — `SendPlaybackCommandUseCase` present.

**Status:** `[x] done`

**Step Log:**

- 2026-05-08 — Verification 3/3 PASS (catalogs regenerated post Phase 06). Dev log recorded.

---

### Step 7.5 — Regenerate `wear` catalog

**Files:** `dev/CATALOG/wear.jsonl`, `dev/CATALOG/wear.md`
**Depends on:** Step 7.4

**Prompt for developer:**

> Run:
> ```
> pwsh -File dev/CATALOG/scripts/scan.ps1 -Module wear
> pwsh -File dev/CATALOG/scripts/render.ps1 -Module wear
> ```
> Confirm all new watch classes (FtpDataSource, SftpDataSource, WearFavoritesRepositoryImpl, PublishPlaybackStateUseCase, etc.) appear.

**Verification:**

- `Grep` — `PublishPlaybackStateUseCase` present in `dev/CATALOG/wear.md`.
- `Grep` — `WearFavoritesRepositoryImpl` present.

**Status:** `[x] done`

**Step Log:**

- 2026-05-08 — Verification 2/2 PASS (catalogs regenerated post Phase 06). Dev log recorded.

---

### Step 7.6 — Remove all `Timber.d("S0111:` debug tags

**Files:** All `.kt` files in `app_v2/src/` and `wear/src/`
**Depends on:** Steps 7.4, 7.5

**Prompt for developer:**

> Run `Grep` for pattern `Timber\.d\("S0111:` across `app_v2/src/` and `wear/src/`. For every matching line, remove it. Do not remove `Timber.d` calls that do not start with `"S0111:`. Commit the removal together with the catalog update in the same commit.

**Verification:**

- `Grep` — `Timber\.d\("S0111:` returns zero hits across `app_v2/src/`.
- `Grep` — `Timber\.d\("S0111:` returns zero hits across `wear/src/`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-08 — Verification 2/2 PASS (zero hits in app_v2/src and wear/src). Removed Timber.d("S0111: from 13 files total. Removed unused Timber imports from ApplyWearSettingsUseCase, SftpDataSource, FtpDataSource. Dev log recorded.

---

### Step 7.7 — Final dev log sweep

**Files:** `dev/CHANGELOG.md`
**Depends on:** Step 7.6

**Prompt for developer:**

> Run `.\scripts\add_to_dev_log.ps1` for any files modified in this phase that do not yet have a dev log entry:
> - `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
> - `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/wear.jsonl`
>
> Then advance the spec catalog status to Implemented:
> ```
> pwsh -File scripts/spec_catalog/update.ps1 -Id S0111 -Status Implemented
> ```
>
> Run `/spec-check S0111` to verify and advance to Verified.

**Verification:**

- `Grep` — `S0111` present in `dev/CHANGELOG.md` at least once.
- Running `pwsh -File scripts/spec_catalog/select.ps1 -Id S0111 -Format json` returns `"status": "Implemented"` or `"Verified"`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-08 — Verification 2/2 PASS. S0111 present in CHANGELOG (97 occurrences). Status advanced to Implemented in catalog and strategic spec. Dev log recorded.

---

## Phase Done Criteria

- [x] Every Step 07.* above is `[x] done`.
- [x] `Grep` for `Timber\.d\("S0111:` returns zero hits across all `.kt` files.
- [x] `docs/FEATURES.md`, `_RU.md`, `_UK.md` updated.
- [x] Both catalogs regenerated and committed.
- [x] Dev log complete.

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate.

---

## Rollback Plan

Documentation changes only (no code). Revert commit(s) if content is incorrect.
