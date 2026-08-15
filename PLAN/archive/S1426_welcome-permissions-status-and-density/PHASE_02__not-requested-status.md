# Phase 02 - Not-requested status

**Strategic spec:** [`../S1426_welcome-permissions-status-and-density.md`](../S1426_welcome-permissions-status-and-density.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 04
**Steps done:** 4 / 4
**Started:** -
**Completed:** -

---

## Objective

Add a fifth permission state meaning "never requested" and make the status use case produce it from the marker, so a fresh install stops reporting a blocked permission.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/PermissionEntry.kt` | Modified | ≤ 35 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/CheckPermissionStatusUseCase.kt` | Modified | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PermissionRowAdapter.kt` | Modified | ≤ 130 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/permissions/PermissionDenialHandler.kt` | Modified | ≤ 55 |
| `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/CheckPermissionStatusUseCaseTest.kt` | New | ≤ 140 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PermissionsManagementFragment.kt` | Modified | ≤ 290 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/helpers/WelcomePermissionsManager.kt` | Modified | ≤ 360 |

> Planning correction, recorded during implementation: the two host files were missing from this list. Both
> carry their own exhaustive `when` over `PermissionStatus`, so neither compiles until the new value is
> handled there too. Step 02.3 covers them; Phase 03 rewrites those same branches wholesale.

---

## Steps

### Step 02.1 - Add the state

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/PermissionEntry.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `NOT_YET_REQUESTED` to the `PermissionStatus` enum, ordered before `DENIED`. Add a KDoc line on the enum stating that `NOT_YET_REQUESTED` and `PERMANENTLY_DENIED` both correspond to the same platform answer and are told apart only by the request marker.

**Why:**

Strategic §5.1 requires a value distinct from both "denied" and "blocked", because the absence of a marker on a fresh install must not be reported as a blocked permission.

**Verification:**

- `Grep` - `NOT_YET_REQUESTED` present in `PermissionEntry.kt`.
- `Grep` - the enum still declares `GRANTED`, `DENIED`, `PERMANENTLY_DENIED`, `NOT_APPLICABLE`.

**Status:** `[x]` done

---

### Step 02.2 - Produce it from the marker

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/CheckPermissionStatusUseCase.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Inject `PermissionRequestMarkerRepository` into `CheckPermissionStatusUseCase`. In the plain-runtime-permission branch, when the permission is not granted, return `NOT_YET_REQUESTED` if the marker says the request was never fired. Only when the marker says it was fired may the existing `shouldShowRequestPermissionRationale == false` reading produce `PERMANENTLY_DENIED`; otherwise return `DENIED`. Leave the three special-permission branches untouched. Replace the stale comment that says the two cases are indistinguishable with one sentence naming the marker as what now distinguishes them.

**Why:**

Strategic §1 states that the current logic reads the platform's identical answer to "never asked" and "denied forever" as a denial, which is what makes a first-run screen report a false fact.

**Verification:**

- `Grep` - `PermissionRequestMarkerRepository` present in the constructor of `CheckPermissionStatusUseCase`.
- `Grep` - `NOT_YET_REQUESTED` present in that file.
- `Grep` - `indistinguishable` returns zero hits in that file.
- `Grep` - `hasAllFilesAccessPermission`, `hasManageMediaPermission` and `isIgnoringBatteryOptimizations` still present.

**Status:** `[x]` done

---

### Step 02.3 - Make every exhaustive branch handle the new state

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PermissionRowAdapter.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/permissions/PermissionDenialHandler.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Extend every `when (status)` over `PermissionStatus` so the new value is handled explicitly rather than by an else branch. In the adapter, `NOT_YET_REQUESTED` takes the same button label as `DENIED` for now - Phase 04 replaces this rendering wholesale. In `PermissionDenialHandler`, `NOT_YET_REQUESTED` must not offer the "open settings" route, because a system dialog is still available.

**Why:**

Strategic §2 goal 2 requires the button to route to settings only once the system will no longer show a request, and an unhandled new state would silently keep the old routing.

**Verification:**

- `.\a.ps1 fk` exits 0, which proves no `when` over the enum is left non-exhaustive.
- `Grep` - `NOT_YET_REQUESTED` present in both files.

**Status:** `[x]` done

---

### Step 02.4 - Cover the status matrix with tests

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/CheckPermissionStatusUseCaseTest.kt`
**Depends on:** Step 02.3

**Prompt for developer:**

> Add a Robolectric test class covering: a granted permission reports `GRANTED`; an ungranted permission with no marker reports `NOT_YET_REQUESTED` regardless of what the rationale call answers; an ungranted permission with a marker and rationale true reports `DENIED`; an ungranted permission with a marker and rationale false reports `PERMANENTLY_DENIED`; each of the three special permissions never reports `PERMANENTLY_DENIED`. Use a fake marker repository rather than the real preferences file.

**Why:**

This matrix is the whole behavioural claim of the ticket, and the research artifact records that the status use case has no test today.

**Verification:**

- `Glob` - the test file exists.
- `Grep` - all five `PermissionStatus` values appear in that file.
- `.\a.ps1 fu` reports this class passing.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

The status a row carries is now honest, but nothing writes the marker yet, so every permission stays `NOT_YET_REQUESTED` until Phase 03 fires the write. Do not test the end state before Phase 03 lands.

---

## Rollback Plan

Revert the phase commit. The marker store from Phase 01 becomes unread again, which is harmless.
