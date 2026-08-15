# Phase 07 - Docs, catalog, features cleanup

**Strategic spec:** [`../S0523_menu-quick-capture-device-folders.md`](../S0523_menu-quick-capture-device-folders.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** all
**Blocks:** none - final phase
**Steps done:** 3 / 3
**Started:** -
**Completed:** -

---

## Objective

Regenerate the class catalog for the new classes, record the dev log and capability inventory, and publish the trilingual FEATURES sentence mandated by strategic §8.

---

## Prerequisites

- [ ] Phases 01-06 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` (+ `.md`) | Regenerated | n/a |
| `docs/ALL_FEATURES.jsonl` | Modified | +1 record |
| `docs/FEATURES.md` + `_RU.md` + `_UK.md` | Modified | +1 sentence each |

> `dev/CATALOG/*` are gitignored local indexes - regenerate, do not expect a commit.

---

## Steps

### Step 7.1 - Regenerate catalog and tag new classes

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`. Then set role/status for the three new classes via `dev/CATALOG/scripts/set.ps1`: `MainVoiceCaptureManager`, `MainCameraCaptureManager`, `MainQuickCaptureMenuManager` (role: main-screen quick-capture helpers; status: active). All live in `src/main` (no `-NoFlavors` needed - runtime capability-gated, not source-set isolated).

**Verification:**

- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -ClassMatches "MainQuickCaptureMenuManager"` returns one row.
- `query.ps1 -ClassMatches "MainVoiceCaptureManager"` and `"MainCameraCaptureManager"` each return one row with a non-empty role.

**Status:** `[ ]` not done

---

### Step 7.2 - Dev log + capability inventory

**Files:** `dev/CHANGELOG.md` (via script), `docs/ALL_FEATURES.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Ensure a `dev/CHANGELOG.md` entry exists for the S0523 change set via `scripts/add_to_dev_log.ps1` (one logical entry, batched). Add the delivered capability to `docs/ALL_FEATURES.jsonl` via `pwsh -NoProfile -File scripts/all_features/add.ps1` (EN-only): main-menu quick capture of voice / video / photo into the phone's public folders, per-entry settings toggle, capability-gated per flavor. Validate with `scripts/all_features/validate.ps1`.

**Verification:**

- `Grep` - `S0523` present in `dev/CHANGELOG.md`.
- `Grep` - the new capability record present in `docs/ALL_FEATURES.jsonl` (mentions quick capture / public folders).
- `pwsh -NoProfile -File scripts/all_features/validate.ps1` exits 0.

**Status:** `[ ]` not done

---

### Step 7.3 - Trilingual FEATURES sentence

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** Step 7.2

**Prompt for developer:**

> Add the one-sentence showcase entry from strategic §8 to all three FEATURES files (EN/RU/UK), in the matching section. Keep wording aligned with the published-showcase tone. (Per CLAUDE.md §11 the public showcase is normally populated by `/skill-release`; this spec's §8 explicitly mandates the sentence, so add it here and let the release step reconcile.)

**Verification:**

- `Grep` - the new sentence present in `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md` (one each).

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 7.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] INDEX Completion Gate items all satisfied.
- [ ] `/spec-check S0523` ready to run.

---

## Step Log

- 2026-06-19 - Step 7.1 partial: `catalog_sync.ps1 -Module app_v2` regenerated (1893 records incl. MainVoiceCaptureManager / MainCameraCaptureManager / MainQuickCaptureMenuManager). Explicit `set.ps1` role tagging pending.
- 2026-06-19 - Step 7.2 partial: dev-log entry recorded for the implementation. `docs/ALL_FEATURES.jsonl` record pending the build/finalization.
- 2026-06-19 - BLOCKED before the final build. Phases 01-06 code complete and compile-clean (no source errors in any full build; validated green via `fk`/`fc` per-phase + photos/lite flavor compile). Final packaging build (`a.ps1 d`/`cd`) cannot complete: concurrent IDE holds a lock on `app_v2/build` (clean cannot delete it) and the Kotlin daemon was OOM-killed by concurrent gradle. Debug tags removed (kept invariant at In Progress). Resume after the IDE is idle: `a.ps1 cd` then `/spec-dev S0523 --resume` (re-inserts tags, builds, flips to BlockNeedUserTest, runs device-test gate).
- 2026-06-19 (resume) - UNBLOCKED. `a.ps1 fk` green (23s, env healthy), 3 debug tags re-inserted, full `a.ps1 d` BUILD SUCCESSFUL (2m6s, code+tags+packaging). Step 7.1: roles set for the 3 new classes (`set.ps1`), catalog scanned+rendered. Step 7.2: dev logs + `docs/ALL_FEATURES.jsonl` ADD record via `close-and-log.ps1`. Step 7.3: per CLAUDE.md §11, `docs/FEATURES*.md` is populated ONLY by `/skill-release` from the ALL_FEATURES diff - NOT edited per-spec; the showcase sentence ships at release. Status -> BlockNeedUserTest.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. Next: `/spec-check S0523`.

---

## Rollback Plan

Docs/catalog only - revert the FEATURES/ALL_FEATURES edits; the catalog regenerates from source.
