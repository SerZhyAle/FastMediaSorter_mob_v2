# Phase 04 - Offer on stale

**Strategic spec:** [`../S1200_channel-preview-atlas-refresh.md`](../S1200_channel-preview-atlas-refresh.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** Phase 05
**Steps done:** 2 / 2
**Started:** -
**Completed:** 2026-07-26

---

## Objective

Make the post-import offer fire for a stale atlas - the exact case the owner hit, where "already installed" silenced it.

---

## Prerequisites

- [ ] Phase 03 is ✅ Done (`UpdateAvailable` is emitted).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamAtlasPromptManager.kt` | Modified | ≤ 110 |
| `app_v2/src/main/res/values/strings.xml` | Modified | - |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | - |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | - |

---

## Steps

### Step 04.1 - Stop treating "installed" as "nothing to ask"

**Files:** `ui/streams/helpers/StreamAtlasPromptManager.kt`

**Prompt for developer:**

> The skip condition currently reads `status is Installed || status is Downloading`. Narrow it: only `Installed` and `Downloading` still skip, but `UpdateAvailable` must fall through to the offer. Keep the `onNothingToOffer` cascade intact - a set that is genuinely up to date still hands off to the next payload's offer. When the status is `UpdateAvailable`, show the update message from Step 04.2 instead of the first-install one; the action label is unchanged.

**Verification:**

- `Grep` - the skip condition no longer swallows `UpdateAvailable`.
- `Grep` - `onNothingToOffer` is still invoked on the `Installed` path (the cascade must not regress).
- `.\a.ps1 fk` compiles.

**Status:** `[x]` done

---

### Step 04.2 - Add the update-offer string

**Files:** `res/values/strings.xml`, `res/values-ru/strings.xml`, `res/values-uk/strings.xml`

**Prompt for developer:**

> Add `streams_atlas_update_prompt_message` across EN/RU/UK in one lockstep `set-android-string.ps1 -Action add` call. It says a newer set of pictures is available for the channels the user just refreshed - not that anything is broken, and without naming atlases, pins or payloads. Check against `docs/COMMUNICATION_POLICY.md` §2 and §6; house style `..` and `ё`.

**Verification:**

- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "streams_atlas_update_prompt"` exits 0.
- `Grep` - the new key is referenced from `StreamAtlasPromptManager.kt`.
- Strings pass `COMMUNICATION_POLICY` §6.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] Phase-boundary audit - focus: still at most one Snackbar on screen; the offered-latch still resets on non-action dismissal.

---

## Handoff Notes to Next Phase

- Behaviour complete; Phase 05 records the capability and regenerates indexes.

---

## Rollback Plan

Revert the phase commit(s) - restores the previous "installed silences the offer" behaviour.
