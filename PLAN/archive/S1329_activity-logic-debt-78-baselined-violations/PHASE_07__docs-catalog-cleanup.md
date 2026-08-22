# Phase 07 - Docs and catalog cleanup

**Strategic spec:** [`../S1329_activity-logic-debt-78-baselined-violations.md`](../S1329_activity-logic-debt-78-baselined-violations.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** all
**Steps done:** 4 / 4
**Started:** 2026-08-14
**Completed:** 2026-08-14

---

## Objective

Close the ticket mechanically: regenerate the class catalog, batch the dev log, record the enforcement state
in the architecture doc, and open the follow-up ticket for the 32 deferred violations.

---

## Prerequisites

- [ ] Phases 01-06 are ✅ Done.
- [x] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` + `.md` | Regenerated (gitignored - not committed) | n/a |
| `dev/CHANGELOG.md` | Appended via script - never hand-edited | n/a |
| `docs/ARCHITECTURE.md` | Modified | ≤ +15 |
| `PLAN/Sxxxx_<follow-up-slug>.md` | New (via `/spec-draft`) | n/a |

---

## Steps

### Step 07.1 - Regenerate the class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` once for the whole ticket. Then set
> `role` and `status` for every class this ticket introduced, via `dev/CATALOG/scripts/set.ps1`. Ten classes,
> from Phase 01: `KeepScreenAwakeManager`, `CameraLaunchWidgetManagerFactory`,
> `CameraQuickCaptureLaunchManagerFactory`, `CameraOcrFlowManagerFactory`, `ScreenCaptureConsentManager`;
> and from the re-planned Phases 02-05: `StandaloneHostFactory`, `ReceiveShareViewModel`,
> `ReceiveShareUiFactory`, `BrowseHostFactory`, `MainHelperFactory`.
> `ScreenCaptureConsentManager` lives in the `screenCapture` source set - give it the matching flavor hint
> so its restricted availability is searchable.
>
> These catalog files are gitignored local indexes - regenerate them, do not commit them.

**Verification:**

- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -ClassMatches "*ReceiveShareViewModel*"` returns exactly one record with a non-empty `role`.
- The same query for each of the other nine new classes returns exactly one record with a non-empty `role`.
- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -ClassMatches "*ScreenCaptureConsentManager*"` shows the `screenCapture` source set.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - catalog_sync ran with each phase-05 closure; roles and status=new set for the five classes that still had none - StandaloneHostFactory, ReceiveShareViewModel, ReceiveShareUiFactory, BrowseHostFactory, MainHelperFactory. The other five already carried S1329 roles from phase 01. All ten verified non-empty by reading dev/CATALOG/app_v2.jsonl. ScreenCaptureConsentManager got its flavor hint as noFlavors=[lite,photos,legacy,vr], which is how the catalog encodes restricted availability - build.gradle.kts mounts src/screenCapture for noLegal and for standard while fms.screenCapture is on.

---

### Step 07.2 - Batch the dev log

**Files:** `dev/CHANGELOG.md` (via script)
**Depends on:** Step 07.1

**Prompt for developer:**

> Add one dev-log entry per logical change, not per file, using
> `pwsh -NoProfile -File scripts/close-and-log.ps1 -DevLogs` with the batch of phase-level entries. Never edit
> `dev/CHANGELOG.md` by hand.
>
> Do **not** add a `docs/ALL_FEATURES.jsonl` record: S1329 ships no user-visible capability, and strategic §8
> says "Без изменений в docs/FEATURES". A refactor that changes no observable behavior has nothing to record
> in the capability inventory.

**Verification:**

- `Grep` - `S1329` matches at least once in `dev/CHANGELOG.md`.
- `Grep` - `S1329` returns zero hits in `docs/ALL_FEATURES.jsonl` (deliberately not recorded).
- `pwsh -NoProfile -File scripts/all_features/validate.ps1` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - Dev log already carries 21 S1329 rows, all written by post-change.ps1 rather than by hand. ALL_FEATURES deliberately untouched: zero S1329 records, and validate.ps1 exits 0 over 696 records - the ticket ships no user-visible capability, per strategic section 8.

---

### Step 07.3 - Record the Rule 3 enforcement state in the architecture doc

**Files:** `docs/ARCHITECTURE.md`
**Depends on:** Step 07.2

**Prompt for developer:**

> In the section describing layer discipline, state that Rule 3 is now mechanically enforced by
> `scripts/quality/assert-activity-logic-not-growing.ps1` against a committed count baseline, that the
> remaining debt is 32 violations confined to `PlayerActivity` and `PhotoVideoStandaloneActivity`, and that
> the sanctioned fixes are: move the dependency into the host's ViewModel, or into an `@Inject`-constructed
> factory for a manually built Manager (the pattern used by
> `app_v2/src/main/java/com/sza/fastmediasorter/widget/PhotoCaptureLaunchManagerFactory.kt`). Name
> `BaseActivity.appSettings` as the sanctioned way for a subclass to read settings without a repository
> reference. Keep it to a short paragraph - no table, no pseudographics.
>
> Re-run the document-registry loop for the `architecture` product area before and after this edit, and run
> `validate.ps1` plus `generate.ps1 -Check` since a registered document changed.

**Verification:**

- `Grep` - `assert-activity-logic-not-growing` matches at least once in `docs/ARCHITECTURE.md`.
- `Grep` - `appSettings` matches at least once in `docs/ARCHITECTURE.md`.
- `pwsh -NoProfile -File scripts/document_registry/validate.ps1` exits 0.
- `pwsh -NoProfile -File scripts/document_registry/generate.ps1 -Check` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - docs/ARCHITECTURE.md gained a short Rule 3 enforcement paragraph naming the gate, the remaining 32 in the two deferred files, the ViewModel and factory fix shapes with PhotoCaptureLaunchManagerFactory as the live example (path verified), and BaseActivity.appSettings. Registry loop run for the architecture area before and after; validate.ps1 and generate.ps1 -Check both exit 0. The eight registry siblings were read for stale Rule 3 claims - only dev/TECH_REQUIREMENTS.md line 278 mentions the rule and its wording is still correct, so no sibling edit was needed; acknowledged with -RegistryAck architecture.

---

### Step 07.4 - Open the follow-up ticket for the deferred 32

**Files:** `PLAN/Sxxxx_<follow-up-slug>.md` (New, id from `next-id.ps1`)
**Depends on:** Step 07.3

**Prompt for developer:**

> Obtain a fresh id with `pwsh -NoProfile -File scripts/spec_catalog/next-id.ps1`, then run `/spec-draft` to
> park the remainder: the 32 `ActivityLogicViolation` entries in `PlayerActivity.kt` (20) and
> `PhotoVideoStandaloneActivity.kt` (12). Record in §0 that the two files are bundled deliberately because 15
> of the 32 are one shared image/GIF edit cluster (rotate, flip, network edit, filter, adjust, merge overlay,
> plus three GIF use cases in the player only) that should become a single facade rather than being built
> twice. Note that `PhotoVideoStandaloneActivity` builds five of the same managers S1329 moved behind
> `StandaloneHostFactory` - the follow-up injects that factory and must not re-add the six dependencies
> anywhere. It also binds `StandalonePlayerViewModel`, which S1329 extended only with the lyrics lookup
> (`SearchLyricsUseCase`), not with the six-dependency surface the original plan proposed; strategic §9 ADR-1
> records why that surface was refuted. Check `PhotoVideoStandaloneActivity` for a `Timber.d("S0995: ..")`
> probe at the time the follow-up is drafted: `S0995` was `Archived` as of 2026-08-13, so any such tag is a
> stale-tag defect to remove rather than preserve - verify the ticket's live status before deciding.
>
> Dedup-check by symptom with `scripts/spec_catalog/search.ps1` first. Reference S1329 as the origin.

**Verification:**

- `pwsh -NoProfile -File scripts/spec_catalog/select.ps1 -Id <new-id> -Format json` returns status `Draft`.
- `Grep` - `S1329` matches at least once in the new spec file.
- `Grep` - `S0995` matches at least once in the new spec file.
- `Grep` - the new ticket id matches at least once in `PLAN/RELEASE_QUEUE.md`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - S1637 activity-logic-debt-player-hosts created as Draft, tier 4, priority 45. Dedup search over the catalog found no ticket covering the deferred 32. The spec records the bundling reason, the StandaloneHostFactory reuse constraint, the refuted ViewModel surface, and the S0995 check - which came back clean: S0995 is Archived and grep finds no S0995 probe tag in app_v2/src, so there is no stale probe to remove. Verified: select.ps1 reads Draft, S1329 x16 and S0995 x1 in the spec, S1637 present in PLAN/RELEASE_QUEUE.md via the catalog reconcile.
- 2026-08-14 - Prerequisite left unticked on purpose: phase 06 is not fully done - its step 06.2 is deferred on S1636. Phase 07 was executed anyway because none of its four steps touch the lint baseline, and holding the catalog roles, the dev log, the architecture note and the follow-up ticket behind an unrelated toolchain defect would have bought nothing.

---

## Phase Done Criteria

- [x] Every `Step 07.*` above is `[x] done`.
- [ ] `pwsh -NoProfile -File scripts/quality/assert-fast-gates.ps1` exits 0.
- [x] `docs/FEATURES*.md` untouched - strategic §8 says no FEATURES change.
- [x] `Grep` for `TODO(phase-07)` returns zero hits.
- [x] Document-registry loop run for the `architecture` product area, affected and unchanged records stated.
- [ ] `/spec-check S1329` returns `Verified`.

---

## Handoff Notes to Next Phase

Final phase - see `INDEX.md` Completion Gate.

---

## Rollback Plan

Revert the phase commit(s). Catalog files are gitignored local indexes and regenerate from source. The
follow-up spec can be archived with `/spec-arc` if the ticket is reopened rather than split.
