# Phase 03 - Continuability signal and exit behaviour

**Strategic spec:** [`../S0529_network-audio-always-continue.md`](../S0529_network-audio-always-continue.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 02
**Blocks:** -
**Steps done:** 0 / 4
**Started:** -
**Completed:** -

---

## Objective

Make the "audio is continuable in background" signal reflect service streaming (not local cache), honour ALWAYS_CONTINUE on exit for streamed network/cloud audio, and add a short wait-then-handoff with an explicit message when a track is still connecting.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done.
- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerMediaLoaderManager.kt` | Modified | ≤ 790 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerLifecycleManager.kt` | Modified | ≤ 560 |
| `app_v2/src/main/res/values/strings.xml` | Modified | ≤ +3 |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | ≤ +3 |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | ≤ +3 |

---

## Steps

### Step 03.1 - Continuability reflects streaming, not local cache

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerMediaLoaderManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> `isServiceAudioActive` currently requires the controller connected + persistent setting + current file is AUDIO. Confirm it stays true for streamed network/cloud audio (the service now holds the track), and that it no longer implicitly assumes a local cache path. Keep it the single source of truth consumed by exit logic and the now-playing indicator.

**Verification:**

- `Grep` - `isServiceAudioActive` still declared once in `PlayerMediaLoaderManager.kt`.
- `Grep` - no condition in that getter requires a `file://`/local cache path.

**Status:** `[ ]` not done

---

### Step 03.2 - Honour ALWAYS_CONTINUE for streamed audio on exit

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerLifecycleManager.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> `exitPlayerWithAudioCheck` short-circuits to `doFinish` when `isServiceAudioActive` is false. With Phase 02 streaming, network/cloud audio is now service-active, so the existing `ALWAYS_CONTINUE` / `ASK` / `ALWAYS_STOP` branch applies uniformly. Verify no separate code path bypasses the preference for network/cloud sources.

**Verification:**

- `Grep` - the `when (.. backgroundAudioExitBehavior)` branch present and reachable for network/cloud audio.
- `Grep -n "Log\.d\("` - zero hits in `PlayerLifecycleManager.kt`.

**Status:** `[ ]` not done

---

### Step 03.3 - Short wait-then-handoff on exit while connecting

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerLifecycleManager.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> When exit is requested while the chosen track is still connecting/buffering (not yet service-active), wait a short bounded window for readiness, then hand off to the service and finish. If the window elapses without readiness, finish the screen with an explicit user message (next step's string) rather than a silent stop. The exit must never block indefinitely.

**Verification:**

- `Grep` - a bounded wait/handoff path present in `PlayerLifecycleManager.kt` (no unbounded loop).
- `Grep` - the new string resource id referenced from this file.

**Status:** `[ ]` not done

---

### Step 03.4 - Add the trilingual "couldn't keep playing" message

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** Step 03.3

**Prompt for developer:**

> Add one string key (e.g. `audio_background_handoff_failed`) in EN/RU/UK in lockstep via `scripts/utils/set-android-string.ps1 -Action add -Key audio_background_handoff_failed -En "<en>" -Ru "<ru>" -Uk "<uk>"`. Wording must pass `docs/COMMUNICATION_POLICY.md` §2 (message formula) and §6 (tone checklist): state plainly that the track couldn't continue in background, no blame, no emoji.

**Verification:**

- `Grep` - `audio_background_handoff_failed` present in all three `strings.xml` files.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "audio_background_handoff_failed"` exits 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

Exit now honours ALWAYS_CONTINUE for all sources. Phase 04 ensures the next network track is ready (via service streaming) so transitions don't reintroduce a startup pause.

---

## Rollback Plan

Revert phase commit(s). String additions are additive; reverting the exit-logic change restores the prior immediate-finish behaviour. No data migration.
