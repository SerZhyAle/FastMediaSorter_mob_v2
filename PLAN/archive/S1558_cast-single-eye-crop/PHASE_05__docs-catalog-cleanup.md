# Phase 05 - Docs and catalog cleanup

**Strategic spec:** [`../S1558_cast-single-eye-crop.md`](../S1558_cast-single-eye-crop.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** all
**Blocks:** none - final phase
**Steps done:** 4 / 4
**Started:** 2026-08-14
**Completed:** 2026-08-14

---

## Objective

Register the delivered capability, refresh the class catalog, and close the documentation surfaces strategic §8 names.

---

## Prerequisites

- [x] All phases in "Depends on" are ✅ Done.
- [x] Strategic §6 research items blocking this phase are Resolved.
- [x] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/ALL_FEATURES.jsonl` | Modified | +1 record |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | n/a |
| `docs/ARCHITECTURE.md` | Modified | ≤ +12 lines |
| `dev/CHANGELOG.md` | Appended via script | n/a |

> Backup / split thresholds: see Constraints (>500 LOC → backup step, >1500 LOC → split via Manager pattern). No source file is edited in this phase.
>
> `docs/FEATURES*.md` is **not** edited here - CLAUDE.md §11 makes it `/skill-release`-owned and populated from the `ALL_FEATURES` diff. Strategic §8 names the user-facing text; step 05.1 is where that text enters the pipeline.

---

## Steps

### Step 05.1 - Record the capability in the feature inventory

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one record via `pwsh -NoProfile -File scripts/all_features/add.ps1` describing that a stereo video cast to Chromecast with the single-eye panel flag on is now sent cropped to one eye. Name the two boundaries in the record: casts of clips over the duration ceiling keep both eyes, and live streams are unaffected. English only, `spec` field set to `S1558`. Read the flavor reach off the source-set mount rather than assuming - the capability is present in every flavor that mounts `src/castEnabled`, and absent where `castDisabled` is mounted.

**Why:**

Strategic §8 states the delivered capability is user-facing and its text must name the wait and the live-stream exclusion honestly, and CLAUDE.md §11 makes this inventory the only per-spec entry point for the public showcase.

**Verification:**

- `Grep` - `S1558` present in `docs/ALL_FEATURES.jsonl`.
- `pwsh -NoProfile -File scripts/all_features/validate.ps1` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - ALL_FEATURES gained video-player.single-eye-cast-for-stereo-video, spec S1558, flavors standard,noLegal,lite,photos,legacy - read off the sourceSets mounts (vr is the only flavor mounting castDisabled) and cross-checked against the SUPPORT_CAST row of docs/FLAVOR_MATRIX.md. The description names both boundaries: the wait before the session starts and clips over the ceiling keeping both eyes, plus live streams never cropped. validate.ps1 exits 0 over 697 records.

---

### Step 05.2 - Regenerate the class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** Step 05.1

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` once for the whole ticket. Then set the role and status of the two new classes with `dev/CATALOG/scripts/set.ps1`. `CastStereoCropTranscoder` lives in `src/castEnabled` and is absent from flavors mounting `castDisabled` - declare that with `-NoFlavors` naming those flavors, read off the `sourceSets` block in `app_v2/build.gradle.kts` rather than from memory.

**Why:**

CLAUDE.md requires new classes to carry `role` and `status` in the catalog, and a flavor-only class that does not declare its absence reads in the catalog as if every flavor had it.

**Verification:**

- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -ClassMatches "CastStereoCropTranscoder"` returns one record.
- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -ClassMatches "CastStereoCrop"` returns a record whose `role` is not `-`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - catalog_sync could not see the class at all: dev/CATALOG/scripts/scan.ps1 never listed src/castEnabled or src/castDisabled among its source roots, so every Cast seam implementation was invisible while catalog-sync still reported PASS - the same defect its own S0404 comment describes for the launcher pair. Fixed the script inside this step (Rule 13), rebuilt with -Force (2274 files, 2843 records), then set roles: CastStereoCropTranscoder with status=new and noFlavors=[vr], CastStereoCrop with status=new. Both queries return one record with a non-empty role.

---

### Step 05.3 - Document the Cast crop path in the architecture doc

**Files:** `docs/ARCHITECTURE.md`
**Depends on:** Step 05.2

**Prompt for developer:**

> Add a short subsection to the Cast section describing the path: the player resolves the crop, the seam carries it as `CastStereoCrop`, the `castEnabled` transcoder produces a half-frame copy in the cache, and the proxy serves that copy. State that the geometry mirrors `PanelStereoCropApplier` and that live streams bypass the whole path. Do not restate the duration ceiling's value - name the constant.

**Why:**

The registry lists `docs/ARCHITECTURE.md` under the `architecture` trigger for the `player` area, and this change introduces a second producer of media bytes on the Cast path, which the existing text describes as a pass-through proxy that transforms nothing.

**Verification:**

- `Grep` - `CastStereoCrop` present in `docs/ARCHITECTURE.md`.
- `pwsh -NoProfile -File scripts/document_registry/validate.ps1` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - docs/ARCHITECTURE.md had no Cast section at all - the step assumed one existed and described it as a pass-through proxy, so the subsection was created rather than extended. New section Cast (Chromecast) Path: the seam and its flavor scope, the crop resolved by the player and carried as CastStereoCrop so the Cast path never re-detects the mode, the geometry mirroring PanelStereoCropApplier, the transcoder writing the half-frame copy the proxy serves, the ceiling named as CastStereoCropTranscoder.MAX_CROP_DURATION_MS without restating its value, and live streams bypassing the path. CastStereoCrop appears twice; document_registry validate.ps1 and generate.ps1 -Check both exit 0.

---

### Step 05.4 - Close the ticket through the facade

**Files:** `dev/CHANGELOG.md`
**Depends on:** Step 05.3

**Prompt for developer:**

> Run `scripts/post-change.ps1` naming the whole changed set with `-Files` and `-ScopeToFile`, `-ChangeType Mixed`, `-Module app_v2`. Read the verdict: only a bare `post-change: PASS` is clean, and `PASS WITH ADVISORIES` requires reading each advisory before continuing. Then set the status through the catalog CLI, with a `-StatusNote` naming what the owner must check on the device.

**Why:**

CLAUDE.md §12 routes mechanical closure through the facade so the gates run before the changelog row is written, and this ticket's real proof is a Chromecast showing one eye, which only a device run can supply.

**Verification:**

- `scripts/post-change.ps1` exits 0 and prints `post-change: PASS` (or `PASS WITH ADVISORIES` with each advisory read and addressed).
- `pwsh -NoProfile -File scripts/spec_catalog/select.ps1 -Id S1558 -Format json` shows the new status and a non-empty `statusNote`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - Closure ran clean on the third attempt: the first two returned PASS WITH ADVISORIES because the document-registry gate wanted both changed registered documents acknowledged, and -RegistryAck takes one CSV string rather than two arguments. Final verdict is a bare post-change: PASS over the six-file set. Status is BlockNeedUserTest with a note naming the four device checks.
- 2026-08-14 - Phase-boundary audit (Layers 1-3). The phase is documentation plus three probe lines, so the surface is small. Layer 1: the probes carry no logic and no state; the PlayerActivity one reads the backing field into a local and passes the same value on, which preserves the S1558 rule that casting an image must not instantiate VideoPlayerManager. Layer 2: no coroutine, lifecycle or dispatcher change - the transcoder probe sits after the existing withContext(IO) duration read and logs on the caller thread. Layer 3: no listener, no resource ownership change. The permanent CastMediaManager log already covers the same decision without a ticket id, so the probes add device-greppable entries without becoming permanent ticket logs. No P0/P1 findings. dev/CATALOG/scripts/scan.ps1 also changed in this phase: two source roots added, no behaviour change to any scanned record beyond the classes that were previously invisible.

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-05)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] If public API changed: `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md` "Phase-boundary audits"; see `/spec-dev` "Phase-boundary audit" step).

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Revert phase commit(s) - documentation and generated indexes only; `dev/CATALOG/app_v2.jsonl` is gitignored and regenerable.
