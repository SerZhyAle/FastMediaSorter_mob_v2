# Phase 04 - Crop user feedback

**Strategic spec:** [`../S1558_cast-single-eye-crop.md`](../S1558_cast-single-eye-crop.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 03
**Blocks:** Phase 05
**Steps done:** 2 / 2
**Started:** -
**Completed:** -

---

## Objective

Tell the user why the cast is not starting instantly, and tell them when the crop was skipped, reusing the Toast surface the same method already uses for every other Cast wait and refusal.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.
- [ ] `temp/CODE.LOCK` acquired via `scripts/utils/enter-code-lock.ps1 -Reason "S1558 phase 04"`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | +2 keys |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | +2 keys |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | +2 keys |
| `app_v2/src/castEnabled/java/com/sza/fastmediasorter/core/cast/CastMediaManagerImpl.kt` | Modified | ≤ 480 |

> Backup / split thresholds: see Constraints (>500 LOC → backup step, >1500 LOC → split via Manager pattern). No file in this phase crosses either.
>
> **No layout file is touched and no `ui/**` class is touched.** The surface is `Toast`, which the same method already uses for `cast_preparing`, `cast_video_too_large`, `cast_error_file`, `cast_no_wifi` and `cast_stream_unsupported_protocol`; no placement decision is being taken, so CLAUDE.md Rule 10 does not fire. A richer progress surface would be a new placement decision and is deliberately out of scope for this ticket.

---

## Steps

### Step 04.1 - Add the two strings across all three locales

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `cast_cropping_stereo` ("preparing the single-eye copy" sense) and `cast_crop_skipped_long` ("video too long to crop, casting it whole" sense) with one lockstep call each: `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key <key> -En "<en>" -Ru "<ru>" -Uk "<uk>"`. Do not hand-edit the three files. Check both texts against `docs/COMMUNICATION_POLICY.md` §2 for the message formula of their type and §6 for tone; `cast_crop_skipped_long` states what happened and what the user gets instead, never an apology or a raw limit value.

**Why:**

Strategic §5 requires progress to be shown while the pass runs because the wait is proportional to the clip length, and §3.2's duration ceiling means some casts silently keep both eyes, which the user has to be told about or the flag looks broken.

**Verification:**

- `Grep` - `cast_cropping_stereo` present in all three `strings.xml` files.
- `Grep` - `cast_crop_skipped_long` present in all three `strings.xml` files.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "cast_"` exits 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - Cast crop feedback validated: scoped detekt, cast string parity, and a.ps1 fc passed.

---

### Step 04.2 - Show the two Toasts on the crop path

**Files:** `app_v2/src/castEnabled/java/com/sza/fastmediasorter/core/cast/CastMediaManagerImpl.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Show `cast_cropping_stereo` with `Toast.LENGTH_SHORT` on `Dispatchers.Main` immediately before awaiting the transcoder, mirroring how `cast_preparing` is shown before `downloadToTemp`. Show `cast_crop_skipped_long` with `Toast.LENGTH_LONG` only when the transcoder returned `null` because the duration ceiling was exceeded - not on export failure, where the user gains nothing from being told. Return a distinguishable result from the transcoder for that one case rather than inferring it from a second duration read.

**Why:**

Strategic §5 requires a failed pass to degrade to casting the original without breaking the session, so the export-failure path stays quiet while the deliberate skip is announced, since only the second one is a decision the user could act on by trimming or by casting a shorter clip.

**Verification:**

- `Grep` - `R.string.cast_cropping_stereo` present in `CastMediaManagerImpl.kt`.
- `Grep` - `R.string.cast_crop_skipped_long` present in `CastMediaManagerImpl.kt`.
- `Grep` - `Toast.LENGTH_LONG` on the skipped-long line.
- `Grep` - `Log\.d\(` returns zero hits in the modified file.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - Cast crop feedback validated: scoped detekt, cast string parity, and a.ps1 fc passed.

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] If public API changed: `dev/CATALOG/<module>.jsonl` regenerated via `pwsh -NoProfile -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md` "Phase-boundary audits"; see `/spec-dev` "Phase-boundary audit" step).

---

## Handoff Notes to Next Phase

Every code change the ticket needs is now in the tree. Phase 05 writes nothing to `app_v2/src/` beyond a catalog regeneration and closes the documentation surfaces strategic §8 names.

---

## Rollback Plan

Revert phase commit(s) - two string keys and two Toast calls, no persisted state and no data migration.
