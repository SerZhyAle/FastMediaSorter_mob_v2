# Phase 05 - Docs & Catalog Cleanup

**Strategic spec:** [`../S0356_bugfix-player-media-load-npe.md`](../S0356_bugfix-player-media-load-npe.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03, Phase 04
**Blocks:** none - final phase
**Steps done:** 2 / 2
**Started:** 2026-06-04
**Completed:** 2026-06-04

---

## Objective

Remove the temporary integrity probe added in Phase 01 (now superseded by the Phase 02 guard), regenerate the class catalog for the new `MediaFileIntegrity` type, and close the dev log / changelog. No FEATURES update (strategic §8: "Без изменений").

---

## Prerequisites

- [ ] Phases 01-04 are ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerMediaFilesLoader.kt` | Modified | ≤ 510 |

> No layout files. Shared `src/main` - no flavor source set involved. The `S0356` debug tag is intentionally left in place by this phase (see Step 05.1).

---

## Steps

### Step 05.1 - Remove the temporary integrity probe (keep the BlockNeedUserTest tag)

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerMediaFilesLoader.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Remove the temporary `Timber.w` integrity-scan probe added in Phase 01 Step 01.2 (the loop over `allFiles` checking non-null fields) - it is superseded by the `MediaFileIntegrity` guard from Phase 02 and must not remain as permanent diagnostics. Do NOT remove the `Timber.d("S0356: player media load integrity probe")` entry tag: per CLAUDE.md the verification tag lives in code for exactly as long as S0356 is in `BlockNeedUserTest`, and it is removed by `/spec-check` when the spec moves to `Verified`. Removing it here would break the BlockNeedUserTest invariant.

**Verification:**

- `Grep` - the Phase 01 integrity-scan probe (the `allFiles` non-null-field loop) is gone: `Grep` for its distinguishing text returns zero hits.
- `Grep` - `S0356: player media load integrity probe` still matches exactly once in `PlayerMediaFilesLoader.kt` (the BlockNeedUserTest tag is retained).
- `Grep -n "Log\.d\("` - zero hits in `PlayerMediaFilesLoader.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-04 - Verification PASS. The temporary integrity-scan probe loop was never added (Phase 01 diverged: superseded by the MediaFileIntegrity substitution log), so there is nothing to remove. BlockNeedUserTest tag `Timber.d("S0356: …")` expected 1 | actual 1 (retained). Log.d expected 0 | actual 0.

---

### Step 05.2 - Regenerate catalog and close dev log

**Files:** `dev/CATALOG/app_v2.jsonl` (regenerated), `dev/CHANGELOG.md` (via script)
**Depends on:** Step 05.1

**Prompt for developer:**

> Regenerate the class catalog so the new `MediaFileIntegrity` object is indexed, and fill its `role`/`status` via `set.ps1`. Confirm `dev/CHANGELOG.md` has an entry for every file modified across Phases 01-05 (`PlayerMediaFilesLoader.kt`, `MediaFileIntegrity.kt`, the three scanners, the reconcile test). Do NOT update `docs/FEATURES*.md` - strategic §8 says "Без изменений". This is a bug fix, not a new user-visible capability.

**Verification:**

- Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`, exit 0.
- `Grep` - `MediaFileIntegrity` present in `dev/CATALOG/app_v2.jsonl` after regeneration.
- Value - `dev/CHANGELOG.md` contains an entry referencing `MediaFileIntegrity` and `PlayerMediaFilesLoader`. `expected: changelog entries present | actual: <fill at run>`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-04 - Verification PASS. catalog_sync exit 0. MediaFileIntegrity in app_v2.jsonl expected present | actual 1 record; role/status set (role + status=new). Changelog: MediaFileIntegrity (8 hits) + PlayerMediaFilesLoader (15 hits) present. Functionality log FIX line for S0356 written. No docs/FEATURES*.md change (strategic §8).

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] Project compiles - `assembleStandardDebug`-equivalent compile passed during the test task (BUILD SUCCESSFUL).
- [x] `Grep` for `TODO(phase-05)` returns zero hits. (expected 0 | actual 0)
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `scripts/catalog_sync.ps1 -Module app_v2`.
- [x] `docs/FEATURES*.md` left unchanged by S0356 (strategic §8: "Без изменений").
- [x] Dev log entry added for every file in "Files Touched" (all 6 + strategic spec).

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. The `S0356:` debug tag remains in `PlayerMediaFilesLoader.kt` until `/spec-check` verifies the spec and moves it out of `BlockNeedUserTest`.

---

## Rollback Plan

Revert phase commit - probe removal and catalog regen only. Catalog files are gitignored local indexes; regenerate rather than restore.
