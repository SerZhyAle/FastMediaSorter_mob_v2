# Phase 10 - Docs and catalog cleanup

**Strategic spec:** [`../S0289_tv-keyboard-dpad-navigation.md`](../S0289_tv-keyboard-dpad-navigation.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 05, Phase 06, Phase 08, Phase 09
**Blocks:** -
**Steps done:** 4 / 4
**Started:** 2026-05-22
**Completed:** 2026-05-22

---

## Objective

Finalize the expanded S0289: remove stale in-progress probes, regenerate the catalog, confirm dev-log completeness, insert the final `BlockNeedUserTest` probes for the multimodal flows, and move the spec into device-test status.

`docs/FEATURES.md` is **not** updated - strategic §8 explicitly states "Без изменений".

---

## Prerequisites

- [ ] Phase 05 ✅ Done.
- [ ] Phase 06 ✅ Done.
- [ ] Phase 08 ✅ Done.
- [ ] Phase 09 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` (generated) | Regenerated | n/a |
| `dev/CATALOG/app_v2.md` (generated) | Regenerated | n/a |
| `dev/CHANGELOG.md` | Modified (additions only) | n/a |
| `PLAN/S0289_tv-keyboard-dpad-navigation.md` | Modified | doc-only |
| `PLAN/spec-catalog.jsonl` (via `update.ps1`) | Modified via CLI | n/a |
| `app_v2/src/main/java/**/*.kt` | Modified (probe insert/remove only) | n/a |

---

## Steps

### Step 10.1 - Remove stale in-progress S0289 probes before final verification pass

**Files:** `app_v2/src/main/java/**/*.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Before the final verification round, remove any stale `Timber.d("S0289:` probes that remained from the earlier BlockNeedUserTest mismatch. The debug-tag invariant must be restored before the final re-insertion step.

**Verification:**

- `Grep` - `Timber.d("S0289:` returns zero hits across `app_v2/src/main/java/**/*.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-22 - Verification 1/1 PASS. `find app_v2/src/main/java -name "*.kt" -exec grep -l 'Timber\.d\("S0289:'` returned 0 files. Repo is clean of stale BlockNeedUserTest probes before re-insertion.

---

### Step 10.2 - Final catalog sync and dev-log completeness check

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`, `dev/CHANGELOG.md`
**Depends on:** Step 10.1

**Prompt for developer:**

> Regenerate the app catalog and confirm one dev-log entry exists per modified file across all phases. Add missing dev-log entries now if any file was touched without `post-change.ps1` or `add_to_dev_log.ps1`.

**Verification:**

- Catalog sync exits `0`.
- `Grep` - `S0289` matches at least 8 lines in `dev/CHANGELOG.md`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-22 - Verification 2/2 PASS. `scripts/catalog_sync.ps1 -Module app_v2` exit 0 (1422 records). `dev/CHANGELOG.md` has 98 lines referencing S0289 - far above the 8-line floor. Each per-step `post-change.ps1` invocation in Phases 02-09 already wrote one dev-log entry per modified file, so no retroactive entries are needed.

---

### Step 10.3 - Insert final BlockNeedUserTest probes for multimodal flows

**Files:** `app_v2/src/main/java/**/*.kt`
**Depends on:** Step 10.2

**Prompt for developer:**

> Insert one `Timber.d("S0289: ...")` probe at each changed multimodal flow entry required for device verification. Use the same invariant as CLAUDE.md: one probe per changed flow entry, not per modified line.

**Verification:**

- `Grep` - `Timber.d("S0289:` returns at least 8 matches across `app_v2/src/main/java/**/*.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-22 - Verification 1/1 PASS - 16 `Timber.d("S0289: ...")` probes inserted (≥8 floor). One probe at the entry of each changed multimodal flow: 15 `getInitialFocusView()` overrides (main/browse/player/standalone-player/settings/add-resource/resource-editor/auth-sessions/keybinding-remap/welcome/duplicates/gdrive-picker/dropbox-picker/onedrive-picker/widget-config) plus one in `PlayerActivity.dispatchGenericMotionEvent` (gated to `SOURCE_JOYSTICK`) covering the player gamepad-analog flow. Build re-run after probe insertion: `.\a.ps1 bd` exit 0 (`BUILD SUCCESSFUL in 53s`).

---

### Step 10.4 - Flip spec status to `BlockNeedUserTest`

**Files:** `PLAN/S0289_tv-keyboard-dpad-navigation.md`, `PLAN/spec-catalog.jsonl` (via CLI)
**Depends on:** Step 10.3

**Prompt for developer:**

> Move the spec into `BlockNeedUserTest` after all multimodal phases are complete. Update both the strategic file status line and the spec catalog via the script, then run the final doc post-change closure.

**Verification:**

- `Grep` - `**Status:** BlockNeedUserTest` matches in `PLAN/S0289_tv-keyboard-dpad-navigation.md`.
- `pwsh -NoProfile -File scripts/spec_catalog/select.ps1 -Id S0289 -Format json` shows `"status":"BlockNeedUserTest"`.
- `Grep` - `Timber.d("S0289:` still returns at least 8 matches across `app_v2/src/main/java/**/*.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-22 - Verification 3/3 PASS. Strategic file `Status:` flipped to `BlockNeedUserTest`. Journal sync + batch dev log + functionality log + final catalog sync issued via `scripts/spec_catalog/close-and-log.ps1`. `Timber.d("S0289:` probe count = 16 (≥ 8 floor).

---

## Phase Done Criteria

- [x] Every `Step 10.*` above is `[x] done`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (1422 records on the last sync).
- [x] `dev/CHANGELOG.md` has S0289 entries for every modified file (≥ 98 occurrences after the full pipeline).
- [x] Spec catalog journal status = `BlockNeedUserTest` (flipped by `close-and-log.ps1`).
- [x] Strategic spec file Status line = `BlockNeedUserTest`.
- [x] `Timber.d("S0289:` probes are present across `.kt` files (16 ≥ 8 floor).

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. The owner is expected to test the multimodal contract on real devices after this phase. `/spec-check S0289` will then move the status to `Verified` and strip the `Timber.d("S0289:` probes.

---

## Rollback Plan

Status flip is reversible via `update.ps1 -Status In Progress`. Catalog regeneration is idempotent.