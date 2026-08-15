# Phase 02 - Docs, catalog, cleanup

**Strategic spec:** [`../S1158_video-stream-program-name-overlay.md`](../S1158_video-stream-program-name-overlay.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** none
**Steps done:** 2 / 2
**Started:** 2026-07-24
**Completed:** 2026-07-24

---

## Objective

Close the ticket mechanically and hand it to the device test.

---

## Prerequisites

- [x] Phase 01 is ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StreamPlaybackHelper.kt` | Modified | ≤ 600 |
| `docs/ALL_FEATURES.jsonl` | Modified | +1 record |

---

## Steps

### Step 02.1 - Device-test probe

**Files:** `StreamPlaybackHelper.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add exactly one `Timber.d("S1158: ...")` probe where the programme name is handed to the callback -
> that is the entry point of the changed flow, and a device log has to answer "did an ICY title arrive
> and get forwarded" before anything about the label is worth debugging.
>
> Keep the line at or below 120 characters (CLAUDE.md Rule 19).

**Verification:**

- `Grep` - `Timber.d("S1158:` matches exactly once across `app_v2/src/**/*.kt`.

**Status:** `[x]` done

---

### Step 02.2 - Inventory, gates, closure

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** Step 02.1

**Prompt for developer:**

> Record the capability. Flavors: read them off the actual gate - strategic §3.2 says the streams
> feature is on where `SUPPORT_STREAMS` is true; confirm against `app_v2/build.gradle.kts` rather than
> copying a sibling record.
>
> No new string resources were added, so the trilingual audit has nothing to check - do not run it for
> show.
>
> Flip the status to `BlockNeedUserTest` **before** the gates (`assert-no-ticket-logs` fails while an
> `S1158:` probe exists under any other status), then run `.\a.ps1 fg` and the diff-scoped detekt gate,
> and close via `close-and-log.ps1` with a note naming what to check on device: play a live video
> channel that carries ICY metadata, confirm the programme name appears under the channel name and
> updates as the programme changes, confirm it disappears when switching to another channel or a local
> file, and confirm both orientations.

**Verification:**

- `pwsh -NoProfile -File scripts/all_features/validate.ps1` - exit 0.
- `.\a.ps1 fg` - exit 0.
- `select.ps1 -Id S1158` - status `BlockNeedUserTest` with a non-empty note.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] `dev/CHANGELOG.md` has an entry for every file this ticket touched.
- [x] Ticket status is `BlockNeedUserTest` with a status note.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Revert phase commits - no data migration, no persisted state.
