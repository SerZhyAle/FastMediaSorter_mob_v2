# Phase 07 - Docs catalog cleanup

**Strategic spec:** [`../S1170_launcher-desktop-app-widgets.md`](../S1170_launcher-desktop-app-widgets.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01, Phase 02, Phase 03, Phase 04, Phase 05, Phase 06
**Blocks:** none - final phase
**Steps done:** 0 / 3
**Started:** -
**Completed:** -

---

## Objective

Land the device-verification probes, register the capability, and regenerate the class catalog for the types this plan added.

---

## Prerequisites

- [ ] Phases 01-06 are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/LauncherGadgetRegistry.kt` | Modified | ≤ 130 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/HomeWidgetSettingsHelper.kt` | Modified | ≤ 225 |
| `docs/ALL_FEATURES.jsonl` | Modified | n/a - append via CLI |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | n/a - generated index |

---

## Steps

### Step 07.1 - Add the device-verification probes

**Files:** `LauncherGadgetRegistry.kt`, `HomeWidgetSettingsHelper.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> The ticket's remaining gate is on-device observation, so it enters `BlockNeedUserTest` and needs its probes (CLAUDE.md "Debug Verification Tags"). Add exactly two `Timber.d("S1170: ..")` lines, one per changed flow entry: one where the placement path reports what key was placed and into which row/column, one where the registry resolves a home-widget key to a gadget. Each on a single line at or under 120 characters - a probe split across two lines does not match `Timber.d("S1170:` and both the gate and the later removal grep would miss it. Do not tag `createView`; it runs per cell per bind.

**Verification:**

- `Grep` - `Timber.d("S1170:` matches exactly twice across the repo.
- `Grep` - zero `S1170` hits inside any `Timber.i`/`Timber.w`/`Timber.e` call.

**Status:** `[ ]` not done

---

### Step 07.2 - Record the capability

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** Step 07.1

**Prompt for developer:**

> Append an `ADD` record through `scripts/all_features/add.ps1` for placing the app's own home-screen widgets on the launcher desktop. Read the flavor list off the actual gate, not a sibling record: the launcher desktop ships from `src/launcherEnabled`, which `app_v2/build.gradle.kts` mounts for `standard` and `noLegal` with no gradle property of its own, so the record ships as `standard,noLegal`. Verify that by reading the source-set block rather than trusting this sentence.

**Verification:**

- `Grep` - `S1170` present in `docs/ALL_FEATURES.jsonl`.
- `pwsh -NoProfile -File scripts/all_features/validate.ps1` exits 0.
- The record's `flavors` field matches the source-set mount read from `app_v2/build.gradle.kts`.

**Status:** `[ ]` not done

---

### Step 07.3 - Regenerate the catalog and close out

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** Step 07.2

**Prompt for developer:**

> Run `scripts/catalog_sync.ps1 -Module app_v2` once for the ticket, then set `role` and `status` on the new public types via `dev/CATALOG/scripts/set.ps1` - at minimum `HomeWidgetGadget`, `FavoritesGadget`, `ScheduledTasksGadget`, `RandomPhotoFrameGadget`, `QuickCaptureGadget`, `AudioNowPlayingGadget`. These live only in `launcherEnabled`, so declare `-NoFlavors "lite,photos,legacy"` to make that isolation searchable. Route the remaining mechanical closure through `scripts/post-change.ps1 -ChangeType Mixed -ScopeToFile`.

**Verification:**

- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -Module app_v2 -ClassMatches "*Gadget*"` returns the new types with non-empty `role`.
- `dev/CHANGELOG.md` carries an entry for this ticket.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 07.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (this build validates code and probes in one pass).
- [ ] `Grep` for `TODO(phase-07)` returns zero hits.
- [ ] Status advanced to `BlockNeedUserTest` with a `-StatusNote` carrying the strategic §5 check: launcher mode on, add each widget from Settings, cell looks and behaves like its Android-home twin, survives a restart.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Final phase - see [INDEX.md](INDEX.md) Completion Gate. The `S1170:` probes stay until the ticket leaves `BlockNeedUserTest`; whoever flips it out deletes both lines in the same change.

---

## Rollback Plan

Revert the phase commit and remove the `ALL_FEATURES` record with its tooling; the catalog index is gitignored and regenerates.
