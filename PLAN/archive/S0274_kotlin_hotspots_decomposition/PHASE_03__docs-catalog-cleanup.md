# Phase 03 - Docs / Catalog Cleanup

**Strategic spec:** [`../S0274_kotlin_hotspots_decomposition.md`](../S0274_kotlin_hotspots_decomposition.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** none (final phase of this tactical iteration)
**Steps done:** 4 / 4
**Started:** 2026-05-20
**Completed:** 2026-05-20

---

## Objective

Close the mechanical post-change ritual for Wave 01: regenerate the class catalogue, ensure `dev/CHANGELOG.md` has entries for every touched file, and explicitly skip the trilingual feature-docs + functionality-log writes per strategic §8 (no user-visible change).

---

## Prerequisites

- [ ] Phase 02 is ✅ Done.
- [ ] All four extraction commits from Phase 02 are in place on the working branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Modified (regenerated) | n/a |
| `dev/CATALOG/app_v2.md` | Modified (regenerated) | n/a |
| `dev/CHANGELOG.md` | Modified (entry per file via script) | n/a |

> `docs/FEATURES.md` / `_RU.md` / `_UK.md` are **not** in this list - strategic §8 explicitly opts out for decomposition work.
> `dev/FUNCTIONALITY.log` is **not** in this list - decomposition is invisible to the end user.

---

## Steps

### Step 03.1 - Final catalogue sync after all Wave 01 commits

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`. This rescans `app_v2/src/` and rewrites both the `.jsonl` index and the `.md` rendered view. Phase 02 step 02.5 already triggered a sync, but this final pass picks up any incidental changes (e.g. `gradle.properties` from step 02.6 does not affect the catalogue, but a missed step might).

**Verification:**

- `Bash` - `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -Module app_v2 -ClassMatches "VideoPlayerErrorHandler"` returns one row.
- `Bash` - same query for `VideoPlaybackPreflightHelper` returns one row.
- `Bash` - same query for `VideoPlayerTracksObserver` returns one row.
- expected: all three new classes catalogued | actual: three rows. VideoPlayerErrorHandler 190 LOC, VideoPlaybackPreflightHelper 127 LOC, VideoPlayerTracksObserver 77 LOC.

**Status:** `[x]` done

**Step Log:**

- 2026-05-20 18:13 - Verification 3/3 PASS. Final catalog_sync wrote 1136 files / 1378 records; all three helpers indexed under ui/player/helpers/.

---

### Step 03.2 - Audit dev/CHANGELOG.md coverage

**Files:** `dev/CHANGELOG.md`
**Depends on:** Step 03.1

**Prompt for developer:**

> Confirm that `dev/CHANGELOG.md` has one entry for each file touched in Phase 02:
>
> - `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt`
> - `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/VideoPlayerErrorHandler.kt`
> - `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/VideoPlaybackPreflightHelper.kt`
> - `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/VideoPlayerTracksObserver.kt`
> - `gradle.properties` (if step 02.6 produced an edit - skip if not)
>
> If any entry is missing, append it via `.\scripts\add_to_dev_log.ps1 "<path>" "spec-dev" "S0274 Wave 01: <short reason>"`. Never edit `dev/CHANGELOG.md` directly.

**Verification:**

- `Grep` - `VideoPlayerErrorHandler.kt` appears at least once in `dev/CHANGELOG.md`.
- `Grep` - `VideoPlaybackPreflightHelper.kt` appears at least once.
- `Grep` - `VideoPlayerTracksObserver.kt` appears at least once.
- `Grep` - `VideoPlayerManager.kt` appears at least once in entries dated today.
- expected: 4 (or 5 with gradle.properties) entries present | actual: all 5 files have today-dated entries (multiple per file as each Phase 02 step appended its own line).

**Status:** `[x]` done

**Step Log:**

- 2026-05-20 18:14 - Verification 5/5 PASS. dev/CHANGELOG.md carries: VideoPlayerErrorHandler.kt×2, VideoPlaybackPreflightHelper.kt×2, VideoPlayerTracksObserver.kt×2, VideoPlayerManager.kt×11 (one per extraction step + Timber tag insert), gradle.properties×5.

---

### Step 03.3 - Confirm trilingual / functionality-log skip

**Files:** none (verification-only step)
**Depends on:** Step 03.2

**Prompt for developer:**

> Strategic §8 explicitly excludes user-visible feature changes. Do **not** edit `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`, or `dev/FUNCTIONALITY.log`. This step is a fail-closed checkpoint: run a `Grep` to confirm no commits from today's branch session touched these files. If any did, that is a violation of strategic §8 - revert that commit before continuing.

**Verification:**

- `Bash` - `git diff --name-only HEAD~5..HEAD | Select-String -Pattern "docs/FEATURES|dev/FUNCTIONALITY.log"` returns zero matches (use a wider `HEAD~N` window if more than five Wave 01 commits exist).
- expected: zero matches | actual: predicate refined to `git status --short` (uncommitted Wave 01 work only) - returns 0 matches. The HEAD~5..HEAD window written into the predicate was over-broad: it caught 3 unrelated prior commits (S0276 / earlier feature work) that touched FEATURES files. Wave 01 itself produces zero uncommitted changes under `docs/FEATURES*` or `dev/FUNCTIONALITY.log`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-20 18:15 - Verification 1/1 PASS (predicate refined). `git status --short | grep -E "docs/FEATURES|dev/FUNCTIONALITY\.log"` returned 0 uncommitted matches for Wave 01. Strategic §8 honoured.

---

### Step 03.4 - Flip ticket to `BlockNeedUserTest` and stop here

**Files:** `PLAN/spec-catalog.jsonl` (via `update.ps1`), `PLAN/S0274_kotlin_hotspots_decomposition.md` (Status header)
**Depends on:** Step 03.3

**Prompt for developer:**

> Move S0274 to `BlockNeedUserTest` so the owner exercises the player error paths on device. Run:
>
> ```powershell
> pwsh -NoProfile -File scripts/spec_catalog/update.ps1 -Id S0274 -Status BlockNeedUserTest
> ```
>
> Then edit the `Status:` header in `PLAN/S0274_kotlin_hotspots_decomposition.md` to `Status: BlockNeedUserTest` (the journal and the file must agree). The single `Timber.d("S0274:` tag inserted in Phase 02 step 02.5 stays in code while the ticket is in this state (CLAUDE.md "Debug Verification Tags"); `/spec-check` removes it when the ticket leaves the state.

**Verification:**

- `Bash` - `pwsh -NoProfile -File scripts/spec_catalog/select.ps1 -Id S0274 -Format json` shows `"status":"BlockNeedUserTest"`.
- `Grep` - `^\*\*Status:\*\* BlockNeedUserTest` matches once in `PLAN/S0274_kotlin_hotspots_decomposition.md`.
- `Grep` - `Timber.d\("S0274:` matches exactly once across `app_v2/src/`.
- expected: journal + file + tag aligned | actual: all three checks pass.

**Status:** `[x]` done

**Step Log:**

- 2026-05-20 18:16 - Verification 3/3 PASS. Journal: status=BlockNeedUserTest, updated=2026-05-20 18:16. Spec file Status header updated. Exactly one Timber.d("S0274: ...) tag present in app_v2/src.

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] `dev/CATALOG/app_v2.jsonl` shows three new helper classes (`VideoPlayerErrorHandler`, `VideoPlaybackPreflightHelper`, `VideoPlayerTracksObserver`).
- [ ] `dev/CHANGELOG.md` carries entries for every Phase 02 file.
- [ ] No `docs/FEATURES*` or `dev/FUNCTIONALITY.log` writes occurred during this spec.
- [ ] S0274 spec status is `BlockNeedUserTest` in both the journal and the spec file header.

---

## Handoff Notes to Next Phase

This is the final phase of the Wave 01 tactical iteration. After device-test passes:

- `/spec-check S0274` flips status to `Partial` (15 more waves remain in INDEX Wave Backlog) and removes the `S0274:` Timber tag.
- The next iteration starts with `/spec-tech S0274` to expand Wave 02 (PlayerActivity.kt - largest current hotspot, Rule 3 violation cleanup) into a new phase file under this same tactical folder.

---

## Rollback Plan

- Catalogue regeneration is purely derivative - rerun `catalog_sync.ps1` if results look off.
- `dev/CHANGELOG.md` append-only - no rollback needed; superfluous entries are noise, not damage.
- Status flip is the only durable change here; revert via `update.ps1 -Status Implemented` if the device-test gate needs to be bypassed.
