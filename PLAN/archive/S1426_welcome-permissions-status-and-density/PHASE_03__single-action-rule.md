# Phase 03 - Single action rule

**Strategic spec:** [`../S1426_welcome-permissions-status-and-density.md`](../S1426_welcome-permissions-status-and-density.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 5 / 5
**Started:** 2026-08-06
**Completed:** 2026-08-06

---

## Objective

Move the "system dialog or settings screen" decision into one shared place used by both hosts, write the request marker on every path that fires a system request, and restate both bulk-request filters in terms of the new state.

---

## Prerequisites

- [x] Phase 02 is ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ResolvePermissionActionUseCase.kt` | New | ≤ 70 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PermissionsManagementFragment.kt` | Modified | ≤ 290 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/helpers/WelcomePermissionsManager.kt` | Modified | ≤ 360 |
| `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/ResolvePermissionActionUseCaseTest.kt` | New | ≤ 110 |

---

## Steps

### Step 03.1 - Extract the action decision

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ResolvePermissionActionUseCase.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `ResolvePermissionActionUseCase` returning a sealed action for an entry and its status: request the permission through the system dialog, open the entry's dedicated system settings screen, open the app settings page, or do nothing. Rule: an entry whose manifest name is one of the three special-grant permissions always resolves to its dedicated system screen; every other entry resolves to the system dialog while the status is `NOT_YET_REQUESTED` or `DENIED`, to app settings on `PERMANENTLY_DENIED`, to app settings on `GRANTED`, and to nothing on `NOT_APPLICABLE`. Keep the three special-grant manifest names in this one place instead of duplicating the set in both hosts.

**Why:**

Strategic §5.1 states the action is chosen by one criterion - whether a system request is still possible - and the research artifact records that both hosts currently reimplement that decision independently, including the special-grant set.

**Verification:**

- `Glob` - the use case file exists.
- `Grep` - `MANAGE_EXTERNAL_STORAGE`, `MANAGE_MEDIA` and `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` each appear exactly once in `app_v2/src/main` outside the registry and `PermissionHelper`.
- `Grep` - all five `PermissionStatus` values appear in the new file.

**Status:** `[x]` done

---

### Step 03.2 - Route the settings host through it

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PermissionsManagementFragment.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Replace the fragment's own `when (status)` click routing and its local `specialGrantPermissions` set with a call to `ResolvePermissionActionUseCase`, dispatching on the returned action. Behaviour must not change for the three states that existed before this ticket.

**Why:**

Strategic §2 goal 5 requires the settings screen to change together with onboarding, and it can only stay in step if both read the same rule.

**Verification:**

- `Grep` - `specialGrantPermissions` returns zero hits in this file.
- `Grep` - `ResolvePermissionActionUseCase` present.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

---

### Step 03.3 - Route the onboarding host through it

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/helpers/WelcomePermissionsManager.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Do the same in the welcome manager: drop its own routing branch and its own copy of the special-grant set, dispatch on the resolved action instead.

**Why:**

Strategic §1 states the onboarding page sends the user to system settings instead of showing a one-tap request, which is the defect this routing change removes.

**Verification:**

- `Grep` - `specialGrantPermissions` returns zero hits in this file.
- `Grep` - `ResolvePermissionActionUseCase` present.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

---

### Step 03.4 - Write the marker wherever a request is fired

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PermissionsManagementFragment.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/helpers/WelcomePermissionsManager.kt`
**Depends on:** Step 03.2, Step 03.3

**Prompt for developer:**

> Call `markRequested` immediately before every launch of a system permission request in both hosts - the single-permission launcher and the bulk grant-all launcher alike, marking every permission included in a bulk request. Then restate both bulk filters in terms of the new state: include `NOT_YET_REQUESTED` and `DENIED`, exclude `PERMANENTLY_DENIED`, and delete the welcome-side workaround comment and its inclusion of permanently denied entries, which existed only because the two cases could not be told apart. Apply the same filter to the grant-all button's visibility check in both hosts so they stop diverging.

**Why:**

Strategic §7 names "the marker is not written on one of the request paths" as the highest-consequence risk, because a missed write leaves the row permanently claiming it was never asked and makes the grant button stop changing anything.

**Verification:**

- `Grep` - `markRequested` appears in both hosts.
- `Grep` - `PERMANENTLY_DENIED` returns zero hits inside the bulk-request filters of both hosts.
- `Grep` - the welcome workaround comment about the first-time dialog is gone.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

---

### Step 03.5 - Cover the action rule with tests

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/ResolvePermissionActionUseCaseTest.kt`
**Depends on:** Step 03.4

**Prompt for developer:**

> Add a unit test asserting the resolved action for every pairing of the five states with a plain permission and with each of the three special-grant permissions, including that `NOT_YET_REQUESTED` resolves to the system dialog and never to settings.

**Why:**

Strategic §11 criterion 2 states that tapping an unrequested permission must open the system dialog rather than a settings screen, and this is the cheapest place to hold that guarantee.

**Verification:**

- `Glob` - the test file exists.
- `Grep` - `NOT_YET_REQUESTED` present in that file.
- `.\a.ps1 fu` reports this class passing.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Status is honest and the button behaves correctly, but the row still renders the old three-block layout with its separate status line. The button's label per state is now meaningful, which is what Phase 04 renders.

---

## Rollback Plan

Revert the phase commit. Markers already written on a device stay, and reverting only restores the previous routing; no data becomes invalid.
