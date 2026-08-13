# Phase 03 - Docs / catalog cleanup

**Strategic spec:** [`../S0413_bugfix-audio-graceful-skip.md`](../S0413_bugfix-audio-graceful-skip.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** none - final phase
**Steps done:** 2 / 2
**Completed:** 2026-06-13
**Started:** -
**Completed:** -

---

## Objective

Insert the `BlockNeedUserTest` debug verification tag at the changed flow entry, regenerate the class catalog, and finalize dev-log entries.

---

## Prerequisites

- [ ] Phase 01 and Phase 02 are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/AudioPlaybackService.kt` | Modified | +1 line |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | - |

---

## Steps

### Step 03.1 - Insert BlockNeedUserTest debug tag

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/AudioPlaybackService.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> The ticket enters `BlockNeedUserTest` after this phase. Insert exactly one `Timber.d("S0413: <description>")` at the entry of the changed flow - the skip branch inside `onPlayerError` (e.g. `Timber.d("S0413: skippable audio source error - advancing to next track")`). One tag for the changed flow, not per line. Do not add the tag to the fatal/stop branch unless it is a separate changed entry point.

**Verification:**

- `Grep` - `Timber.d("S0413:` matches exactly once in the file.

**Status:** `[x]` done

**Step Log:**

- 2026-06-13 - Verification 1/1 PASS. Inserted `Timber.d("S0413: skippable audio source error - advancing to next track")` at skip-branch entry. Final compile (`a.ps1 fk`) green.

---

### Step 03.2 - Regenerate catalog and finalize dev log

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** Step 03.1

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` to rescan and re-render the catalog. Ensure every file touched across Phases 01-03 has a `dev/CHANGELOG.md` entry via `.\scripts\add_to_dev_log.ps1`. Do not edit `docs/FEATURES*.md` (strategic §8 = "Без изменений").

**Verification:**

- `catalog_sync.ps1 -Module app_v2` exits 0.
- `Grep` in `dev/CHANGELOG.md` - entries present for `AudioPlaybackService.kt` and the three `strings.xml` files.

**Status:** `[x]` done

**Step Log:**

- 2026-06-13 - Verification 2/2 PASS. `catalog_sync.ps1 -Module app_v2` exit 0 (1783 records). CHANGELOG has entries for all touched files. FEATURES untouched (§8 = no change).

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] `Timber.d("S0413:` exists exactly once (BlockNeedUserTest invariant).
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] Project compiles - run `.\a.ps1 fk`.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. Ticket is ready to advance to `BlockNeedUserTest` for on-device verification (playlist with one deliberately corrupt/unrecognized audio file).

---

## Rollback Plan

Remove the debug tag line; catalog regen is idempotent - no rollback needed.
