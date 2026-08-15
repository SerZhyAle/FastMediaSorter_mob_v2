# Phase 05 - Docs Catalog Cleanup

**Strategic spec:** [`../S0296_vr-immerse-video-playback.md`](../S0296_vr-immerse-video-playback.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 04
**Blocks:** final `/spec-check`
**Steps done:** 4 / 4
**Started:** 2026-05-30
**Completed:** 2026-05-30

---

## Objective

Close documentation, catalog, build and device-verification handoff for the S0296 VIDEO cinema MVP.

---

## Prerequisites

- [x] Phase 04 is ✅ Done.
- [x] No S0296 implementation step remains `[ ]` or `[~]`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | change <= 20 |
| `docs/FEATURES_RU.md` | Modified | change <= 20 |
| `docs/FEATURES_UK.md` | Modified | change <= 20 |
| `dev/CATALOG/app_v2.jsonl` | Generated | generated |
| `dev/CATALOG/app_v2.md` | Generated | generated |
| `PLAN/S0296_vr-immerse-video-playback/INDEX.md` | Modified | change <= 40 |
| `PLAN/S0296_vr-immerse-video-playback.md` | Modified | change <= 40 |

---

## Steps

### Step 05.1 - Add public feature bullets

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Add exactly one VR Edition bullet to each public FEATURES file using the approved strategic §6 text. Do not update `docs/FEATURES_noLegal*.md`. Do not mention SBS, OU, VR180, VR360, streaming, cloud, DRM or subtitles.

**Verification:**

- `Grep` - `Watch local or prepared video in immersive VR cinema mode on a flat screen inside the VR scene.` exists exactly once in `docs/FEATURES.md`.
- `Grep` - `Просмотр локального или подготовленного видео в режиме VR-кинозала на плоском экране внутри immersive-сцены.` exists exactly once in `docs/FEATURES_RU.md`.
- `Grep` - `Перегляд локального або підготовленого відео в режимі VR-кінотеатру на плоскому екрані всередині immersive-сцени.` exists exactly once in `docs/FEATURES_UK.md`.
- `Grep` - the S0296 bullet returns zero hits in `docs/FEATURES_noLegal*.md`.

**Status:** `[x]` done

---

### Step 05.2 - Refresh catalog after Kotlin API changes

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Step 05.1

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` after all Kotlin changes. Do not hand-edit generated catalog files.

**Verification:**

- `Command` - `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` exits 0.
- `Grep` - `PlayerStateSnapshot` exists in `dev/CATALOG/app_v2.jsonl`.
- `Grep` - `PlayerVrLaunchManager` exists in `dev/CATALOG/app_v2.jsonl`.

**Status:** `[x]` done

---

### Step 05.3 - Run noLegal debug build

**Files:** build outputs under `temp/` or normal Gradle output
**Depends on:** Step 05.2

**Prompt for developer:**

> Run the noLegal debug build through the project build wrapper: `pwsh -NoProfile -File scripts/builders/build-nolegal-debug.ps1`. If it fails, inspect the failure block with the project build-log tooling and leave this step `[~] in progress` with the failing command and excerpt.

**Verification:**

- `Command` - `pwsh -NoProfile -File scripts/builders/build-nolegal-debug.ps1` exits 0.
- `Grep` - `Log.d(` returns zero hits in modified Kotlin files.
- `Grep` - `Timber.i(.*S0296|Timber.w(.*S0296|Timber.e(.*S0296` returns zero hits in `app_v2/src/**/*.kt`.

**Status:** `[x]` done

---

### Step 05.4 - Prepare on-device verification handoff

**Files:** `PLAN/S0296_vr-immerse-video-playback/INDEX.md`, `PLAN/S0296_vr-immerse-video-playback.md`
**Depends on:** Step 05.3

**Prompt for developer:**

> Add a closure note to the tactical INDEX and strategic spec recording the noLegal build command, the intended Quest 3 manual checks, and the current cold-start measurement placeholder `pending device verification`. Do not mark the strategic spec `Verified`; `/spec-dev` should move it to `BlockNeedUserTest` if all phases are done and on-device acceptance is still required.

**Verification:**

- `Grep` - `pending device verification` exists exactly once in `PLAN/S0296_vr-immerse-video-playback.md`.
- `Grep` - `Quest 3 manual checks` exists in `PLAN/S0296_vr-immerse-video-playback/INDEX.md`.
- `Grep` - `pwsh -NoProfile -File scripts/builders/build-nolegal-debug.ps1` exists in `PLAN/S0296_vr-immerse-video-playback/INDEX.md`.
- `Grep` - `**Status:** Verified` returns zero hits in `PLAN/S0296_vr-immerse-video-playback.md`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] noLegal debug build exits 0.
- [x] Dev log entry added for every modified docs/spec file via `pwsh -NoProfile -File scripts/post-change.ps1`.
- [x] Strategic spec is ready for `/spec-dev` final transition to `BlockNeedUserTest`.

---

## Handoff Notes to Next Phase

Static closure is complete. Device acceptance still needs the Quest 3 manual checks listed in `INDEX.md`.

---

## Rollback Plan

Revert phase commit(s). Generated catalog files can be regenerated from source after rollback.
