# Phase 06 - Open-in-FMS Routing to In-App Player

**Strategic spec:** [`../S0389_standalone-player-parity.md`](../S0389_standalone-player-parity.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 05
**Blocks:** none
**Steps done:** 2 / 2
**Started:** 2026-06-09
**Completed:** 2026-06-09

**Step Log (phase):**

- 2026-06-09 - 06.1 openInFms() rewritten: ResolveOpenInFmsTargetUseCase → PlayerActivity.createPanelIntent(skipAvailabilityCheck=true, initialFilePath); NotResolvable keeps Main/Browse fallback. Success path no longer launches Browse. Handler constructor param findResourceForPath → resolveOpenInFmsTarget; wired @Inject across 5 host activities. Removed orphaned VM.findResourceForPath + resourceRepository (dead-weight). 06.2 string open_in_fms_external_file_notice EN/RU/UK (policy-compliant). Build green; localization exit 0.

> **Gate:** S0380 must be confirmed on device before this phase lands (INDEX Pre-Implementation Blockers). Routing builds on the split per-type standalone hosts.

---

## Objective

Change "Open in FastMediaSorter" so it opens the in-app player on the specific file inside the resolved resource, instead of the Browse list. Preserve a clear fallback for non-local files.

---

## Prerequisites

- [ ] Phase 05 is ✅ Done.
- [ ] S0380 confirmed on device (INDEX blocker checked).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StandaloneFileOperationsHandler.kt` | Modified | ≤ 460 |
| `app_v2/src/main/res/values/strings.xml` | Modified | +1 key |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | +1 key |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | +1 key |

---

## Steps

### Step 06.1 - Route Open-in-FMS through the resolver to the in-app player

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StandaloneFileOperationsHandler.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Replace the current `openInFms()` Browse navigation. Call `ResolveOpenInFmsTargetUseCase`; on a resolved `(resourceId, absoluteFilePath)`, launch the in-app player with the resource id and `initialFilePath` set to the absolute path (use the existing player intent factory that accepts `initialFilePath` and skips the availability check). On not-resolvable (non-local file), keep the existing fallback (Browse/Main) and surface a short, policy-compliant notice that the file is opened outside a managed folder. Run resolution off the main thread; launch the activity on the main thread.

**Verification:**

- `Grep` - `ResolveOpenInFmsTargetUseCase` referenced in `StandaloneFileOperationsHandler.kt`.
- `Grep` - `initialFilePath` passed to the player intent factory (`createPanelIntent`/`createIntent`).
- `Grep` - the success path no longer launches `BrowseActivity` (Browse remains only on the not-resolvable fallback).
- `Grep -n "Log\.d\("` returns zero hits in the file.

**Status:** `[x]` done

---

### Step 06.2 - Add the non-local fallback notice string (trilingual)

**Files:** `values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** Step 06.1

**Prompt for developer:**

> Add one string key for the non-local fallback notice in EN/RU/UK in lockstep via `scripts/utils/set-android-string.ps1 -Action add`. Wording follows `docs/COMMUNICATION_POLICY.md` §2 (informational message) and §6 tone checklist - plain, no blame, no jargon. Use `ё` where applicable in RU.

**Verification:**

- `Grep` - the new key present in all three `strings.xml` files.
- Run `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "<key_prefix>"` - exit 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 06.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-06)` returns zero hits.
- [ ] Dev log entries added for every file in "Files Touched".
- [ ] `scripts/check_strings_localized.ps1` exit 0 for the new key.

---

## Handoff Notes to Next Phase

Open-in-FMS now lands in the in-app player on the chosen file. Final phase handles catalog regen, dev log completeness, and the FEATURES trilingual update.

---

## Rollback Plan

Revert phase commit(s) - restores the prior Browse navigation. Remove the added string key via `set-android-string.ps1 -Action remove`. No schema change.

