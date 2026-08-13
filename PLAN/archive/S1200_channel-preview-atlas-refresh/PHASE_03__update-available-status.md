# Phase 03 - Update-available status

**Strategic spec:** [`../S1200_channel-preview-atlas-refresh.md`](../S1200_channel-preview-atlas-refresh.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 5 / 5
**Started:** -
**Completed:** 2026-07-26

---

## Objective

Add the third state to `ExtensionStatus`, emit it for a stale set, and render it as an offer to update rather than as "installed".

---

## Prerequisites

- [ ] Phase 02 is ✅ Done (`isStale` answers).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/delivery/DeliverableInventory.kt` | Modified | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/delivery/DeliverableInventoryImpl.kt` | Modified | ≤ 350 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/delivery/ExtensionsManagerFragment.kt` | Modified | ≤ 300 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/delivery/ExtensionsManagerViewModel.kt` | Modified | ≤ 120 |
| `app_v2/src/main/res/values/strings.xml` | Modified | - |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | - |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | - |

---

## Steps

### Step 03.1 - Add the status

**Files:** `domain/delivery/DeliverableInventory.kt`

**Prompt for developer:**

> Add `object UpdateAvailable : ExtensionStatus()` with a KDoc line: the payload is present but was installed against different pins than this build carries (S1200). Expect the build to break wherever the sealed class is matched exhaustively - that is the intended compile-time contract, closed in Step 03.3.

**Verification:**

- `Grep` - `UpdateAvailable` present in `DeliverableInventory.kt`.

**Status:** `[x]` done

---

### Step 03.2 - Emit it for a stale set

**Files:** `data/delivery/DeliverableInventoryImpl.kt`

**Prompt for developer:**

> In `moduleStatusFlow(set)`, where the payload-present branch currently yields `Installed`, yield `UpdateAvailable` instead when `repository.isStale(set, descriptors[set]?.stamp ?: return@map Installed)` - a set with no descriptor in this flavor cannot be compared, so it stays `Installed`. Leave the bundled branch untouched (it returns `Installed` before reaching here). Do not touch the language-data or catalog branches: they have no pinned descriptor.

**Verification:**

- `Grep` - `isStale` called exactly once in `DeliverableInventoryImpl.kt`.
- `Grep` - the bundled early-return (`bundled.contains(set)`) still precedes the new branch.
- `.\a.ps1 fk` compiles.

**Status:** `[x]` done

---

### Step 03.3 - Render it

**Files:** `ui/delivery/ExtensionsManagerFragment.kt`, `ui/delivery/ExtensionsManagerViewModel.kt`

**Prompt for developer:**

> In the Fragment's status `when`, add an `UpdateAvailable` branch: same affordance as `NotInstalled` (an actionable download button) but labelled with the new update string and keeping the row readable as already-usable - the old payload still works, this is an offer, not a warning. In the ViewModel, treat `UpdateAvailable` the same as `NotInstalled` at the "may start a download" check, and the same as `Installed` at the "may uninstall" check (a stale payload is still on disk and still deletable).

**Verification:**

- `Grep` - `UpdateAvailable` handled in both `ExtensionsManagerFragment.kt` and `ExtensionsManagerViewModel.kt`.
- `Grep` - the ViewModel's download guard admits `UpdateAvailable`; the uninstall guard admits it too.
- `.\a.ps1 fk` compiles (proves the sealed `when` is exhaustive again).

**Status:** `[x]` done

---

### Step 03.4 - Add the trilingual string

**Files:** `res/values/strings.xml`, `res/values-ru/strings.xml`, `res/values-uk/strings.xml`

**Prompt for developer:**

> Add `ext_status_update_available` across EN/RU/UK in one lockstep call: `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key ext_status_update_available -En .. -Ru .. -Uk ..`. Wording states what the user gets, not the mechanism - no "pins", "stamp", "hash", "payload" in any locale. Check against `docs/COMMUNICATION_POLICY.md` §2 formula and §6 tone checklist; house style `..` and `ё`.

**Verification:**

- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "ext_status_update_available"` exits 0.
- Strings pass `COMMUNICATION_POLICY` §6 - no internal vocabulary in any locale.

**Status:** `[x]` done

---

### Step 03.5 - Let the download past the installed-payload guard

**Files:** `domain/delivery/DeliverableDownloadRunner.kt`, `data/delivery/DeliverableDownloadRunnerImpl.kt`, `data/delivery/DeliverableInventoryImpl.kt`

**Added mid-implementation.** Device run showed the offer appearing and the tap doing nothing: `DeliverableDownloadRunnerImpl.enqueue` returns early on `isInstalledBlocking(set)`, so a stale payload could never be re-fetched. Same class of bug as the silenced offer, one layer down - and invisible to reasoning, because both the status and the offer were provably correct by then.

**Prompt for developer:**

> Add `force: Boolean = false` to `DeliverableDownloadRunner.enqueue` and skip the installed-payload guard when it is set. In `DeliverableInventoryImpl.download`, pass `force = isStale(item.set)`. While here, make `progressOf` prefer an unfinished `WorkInfo` over a finished one: a re-download runs under the same unique work name, and reading the previous finished entry would report the old outcome for the new attempt.
>
> Then close the hole the first fix opens. `progressOf` reports `Installed` when there is no `WorkInfo` but a payload exists - harmless while a download over an installed payload was impossible, a lie once it is possible: in the gap between `enqueue` and WorkManager registering the request the caller sees `Installed` and stamps the OLD payload as current, so the update is recorded as done and never offered again. This is not hypothetical; it happened on the first device run and falsely stamped the preview atlas. Track forced sets in the runner and report `Queued` for them until a `WorkInfo` appears, clearing the mark when a finished one does.

**Verification:**

- `Grep` - `enqueue(` carries `force` in interface, impl and call site.
- `Grep` - the guard reads `!force && repository.isInstalledBlocking(set)`.
- `Grep` - `progressOf` picks `firstOrNull { !it.state.isFinished }` before falling back.
- `Grep` - the no-WorkInfo branch checks `forced` before `isInstalledBlocking`.
- Device: tapping the offer starts a real download and the payload bytes on disk change.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] Phase-boundary audit - focus: no `when` over `ExtensionStatus` left with an implicit else that would swallow the new state; the status flow does not read the DataStore on the main thread more often than before.

---

## Handoff Notes to Next Phase

- The Extensions Manager now shows the state; Phase 04 makes the post-import offer respect it.

---

## Rollback Plan

Revert the phase commit(s). Removing the sealed-class member re-breaks the two `when` blocks, so revert them together.
