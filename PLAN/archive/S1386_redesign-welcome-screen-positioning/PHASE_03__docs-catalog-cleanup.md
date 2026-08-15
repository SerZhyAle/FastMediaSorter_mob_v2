# Phase 03 - Docs, catalog, cleanup

**Strategic spec:** [`../S1386_redesign-welcome-screen-positioning.md`](../S1386_redesign-welcome-screen-positioning.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** none - final phase
**Steps done:** 3 / 3
**Started:** 2026-08-04
**Completed:** 2026-08-04

---

## Objective

Record the delivered capability change, add the device-test probe the validation level requires, and close the ticket through the mechanical closure facade.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/ALL_FEATURES.jsonl` | Modified | ≤ 1 added record |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/helpers/WelcomeFeatureCards.kt` | Modified | ≤ 150 |
| `dev/CHANGELOG.md` | Modified (via script only) | - |

---

## Steps

### Step 03.1 - Record the capability change in the feature inventory

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one record with `pwsh -NoProfile -File scripts/all_features/add.ps1` describing what the first wizard page now tells the user: it names the app's roles - file manager, player of photos, video, music, GIFs, documents and text, reader of local, network and cloud sources, and one-tap sorting - with the protocol and service names kept in each tile's second line. Set the spec field to `S1386`. Write it in English. Then run `pwsh -NoProfile -File scripts/all_features/validate.ps1`.

**Why:**

Strategic §8 states this ticket changes what the user is told on the first screen, and CLAUDE.md §11 makes `docs/ALL_FEATURES.jsonl` the inventory from which `/skill-release` later builds the public showcase, so an unrecorded change never reaches the release notes.

**Verification:**

- `Grep` - `S1386` matches at least once in `docs/ALL_FEATURES.jsonl`.
- `scripts/all_features/validate.ps1` exits 0.

**Status:** `[x]` done

---

### Step 03.2 - Add the device-test probe

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/helpers/WelcomeFeatureCards.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Add one `Timber.d("S1386: welcome role showcase built, cards=<n>")` at the end of `build`, where `<n>` is the produced list size, so a device run proves which set the build actually rendered. One probe for this flow entry, not one per card.

**Why:**

Strategic §3.3 sets the validation level at an on-device render check in portrait and landscape plus a check on `lite`, where part of the roles are worded differently, and the probe is what makes the rendered set readable from logcat instead of guessed from a screenshot.

**Verification:**

- `Grep` - `Timber.d("S1386:` matches exactly once across `app_v2/src`.
- `Grep` - `Log\.d\(` returns zero hits in `WelcomeFeatureCards.kt`.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

---

### Step 03.3 - Close through the facade

**Files:** `dev/CHANGELOG.md`, `dev/CATALOG/app_v2.jsonl`
**Depends on:** Step 03.2

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/post-change.ps1 -Files "app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/helpers/WelcomeFeatureCards.kt,app_v2/src/main/res/values/strings_setup.xml,app_v2/src/main/res/values-ru/strings_setup.xml,app_v2/src/main/res/values-uk/strings_setup.xml,app_v2/src/main/res/values-sw320dp-land/integers.xml" -Target "S1386" -Description "Welcome first page states the app roles instead of a sample of capabilities" -ChangeType Mixed -Module app_v2 -ScopeToFile` and read its verdict. Before closing, grep `docs/` for any page that enumerates the old six showcase tiles by name and update it if one exists.

**Why:**

CLAUDE.md §12 routes mechanical closure through this facade, and `-ScopeToFile` is what judges the scoped gates against this ticket's own file set on a tree that always carries other tickets' work in flight.

**Verification:**

- `post-change.ps1` prints `post-change: PASS` or `PASS WITH ADVISORIES` and exits 0.
- `Grep` - `Photos & Video`, `Local Folders`, `Network Sources`, `Cloud Storage`, `Smart Sorting` and `Slideshow` do not appear together as a showcase list in any file under `docs/`.

**Status:** `[x]` done

---

## Step Log

- 2026-08-04 17:02 - Step 03.1 done. `setup-onboarding.welcome-role-pitch` added to `docs/ALL_FEATURES.jsonl`; `validate.ps1` PASS, 639 records.
- 2026-08-04 17:03 - Step 03.2 done. One probe at the end of `build`; `Timber.d("S1386:` = 1 across `app_v2/src`, `Log.d(` = 0, `.\a.ps1 fk` exit 0.
- 2026-08-04 17:04 - Single build for code + probe: `.\a.ps1 d` exit 0, APK `FastMediaSorter_standard_debug_v2.60.8041.533-DEBUG.apk`. On-device probe output: `S1386: welcome role showcase built, cards=4`.
- 2026-08-04 17:09 - Docs sweep: no page under `docs/` enumerates the six former showcase tiles; the `Cloud Storage` and `Network Sources` hits in HOW_TO, QUICK_START and SMB_SETUP_GUIDE name the resource-add dialog and a settings section, not the welcome pitch. No doc edit needed.
- 2026-08-04 17:09 - Step 03.3: first closure run FAILED on `ticket-log-audit` (probe present while the ticket was still `In Progress`). Status flipped to `BlockNeedUserTest` first, then closure re-run.
- 2026-08-04 17:10 - Step 03.3 done. `post-change.ps1 -ChangeType Mixed -ScopeToFile` printed `PASS WITH ADVISORIES (1)`; the advisory was the feature-inventory registry asking whether `docs/ALL_FEATURES.schema.json` needed the same edit. It does not - the new record uses existing fields only - acknowledged via `-RegistryAck 'feature-inventory'`, which then printed `post-change: PASS`.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 d` exit 0 (the single build that validated code plus probe).
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.
- [x] Phase-boundary audit run - skipped by rule: this phase's file delta is the inventory record plus the temporary probe, no product logic.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. The `Timber.d("S1386:` probe stays in the source until the ticket leaves `BlockNeedUserTest`; whoever flips it to `Verified` deletes the line.

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing surface changed beyond the first-run page text.
