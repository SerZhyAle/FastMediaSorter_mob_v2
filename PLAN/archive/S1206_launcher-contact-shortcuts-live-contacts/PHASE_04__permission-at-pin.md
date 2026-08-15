# Phase 04 - Permission at pin

**Strategic spec:** [`../S1206_launcher-contact-shortcuts-live-contacts.md`](../S1206_launcher-contact-shortcuts-live-contacts.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03 (compile-time) - Phase 02 for the grant to change anything the user can see
**Blocks:** Phase 05
**Steps done:** 2 / 2
**Started:** 2026-08-08
**Completed:** 2026-08-08

---

## Objective

Ask for `READ_CONTACTS` at the moment the user pins a contact, explain why first, and pin the cell
whatever they answer.

---

## Prerequisites

- [x] Phase 03 is ✅ Done. Phase 02 should also be Done, or the grant changes nothing visible.
- [x] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherContactPickManager.kt` | Modified | ≤ 300 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/permissions/PermissionAskability.kt` | New | ≤ 70 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/permissions/PermissionAskabilityEntryPoint.kt` | New | ≤ 25 |

> **Second file added during implementation (2026-08-08).** Step 04.2 named
> `shouldShowRequestPermissionRationale` as the way to tell a permanently denied permission from a
> never-requested one, but that platform call answers "no rationale" in *both* cases - which
> `PermissionStatus`'s own KDoc states, and which is why `CheckPermissionStatusUseCase` consults the
> request marker as well. Using the platform call alone would have skipped the explanation on the very
> first pin. The reading is therefore expressed once, as `Activity.canRequestPermission` plus
> `Context.markPermissionRequested`, in the same package and by the same Hilt entry-point idiom as
> `permissionRationale` beside it. The marker write is the second half of the correction: the marker is
> shared with the settings list, so a request that fired without recording it would leave every reader
> believing the user was never asked.
>
> The Hilt entry point sits in its own file because detekt's `MatchingDeclarationName` refuses a file
> whose single top-level declaration is named differently - which is also why `PermissionRationaleText`
> and `PermissionRationaleEntryPoint` are two files beside it rather than one.

> The file is 213 LOC, so no backup step is required.
>
> **Flavor placement.** The file already lives in `src/launcherEnabled`, which strategic §3.3 names as the
> feature's home ("там же, где домашняя поверхность лаунчера"); no `src/main` counterpart is needed because
> the ask is part of the pin flow, and the pin flow only exists where the launcher does.
>
> **No Activity change.** `LauncherContactPickManager` already receives the `FragmentActivity` and registers
> its own activity-result contract in a field initialiser, so the new contract registers beside the existing
> one and `LauncherHomeActivity` is not touched (CLAUDE.md Rule 3).

---

## Steps

### Step 04.1 - Explain, then ask, before the picker opens

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherContactPickManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Register a second contract in a field initialiser:
> `activity.registerForActivityResult(ActivityResultContracts.RequestPermission())`, whose callback runs a
> held `pendingPick: (() -> Unit)?` and clears it, mirroring `pendingAction` beside it and
> `LauncherSensorPermissionManager.pendingPlacement`.
>
> In `start(action)`, before the existing `when`, short-circuit to the current behaviour when
> `READ_CONTACTS` is already granted. Otherwise show a `MaterialAlertDialogBuilder` whose message is
> `activity.permissionRationale(Manifest.permission.READ_CONTACTS, PermissionTask.CONTACT_CELL_PINNING)`,
> mirroring the pattern in `MainScreenRecordingManager` and `BrowseEventHandler`. Its confirm action stores
> the rest of `start` as `pendingPick` and launches the request; its cancel action runs the rest of `start`
> directly. Apply the named dialog button styles per CLAUDE.md §11.
>
> A refusal must still pin the cell. Do not gate any existing branch of `start` on the answer.

**Why:**

Strategic §3.3 fixes the moment of the request - "при закреплении контакта на стол" - and rules out a
switch of its own, and §3 requires the user be told why the launcher wants contacts before being asked.
Pinning regardless of the answer is what §3 means by the refusal scenario being "не деградация, а текущее
поведение": without the permission the cell simply keeps the snapshot behaviour S1176 already ships.

**Verification:**

- `Grep` - `ActivityResultContracts.RequestPermission` present.
- `Grep` - `PermissionTask.CONTACT_CELL_PINNING` present.
- `Grep` - `permissionRationale` present.
- `Grep` - `READ_CONTACTS` present.
- Read the file - every branch of `start` is still reachable when the request is refused.
- `Grep` - `Log\.d\(` returns zero hits in this file.

**Status:** `[x]` done

---

### Step 04.2 - Do not ask a user who already answered no

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherContactPickManager.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Skip the dialog and the request when the permission is permanently denied - the system reports no
> rationale and `ActivityResultContracts.RequestPermission` would return instantly without showing
> anything. Proceed straight to the existing pick flow in that case. Use
> `ActivityCompat.shouldShowRequestPermissionRationale` together with the granted check to tell the
> two "not granted" cases apart, as `PermissionStatus`'s KDoc describes.

**Why:**

Strategic §3.3 designates the existing Settings > Permissions > Contacts row as the feature's only switch,
so a user who turned it off there must not be re-prompted by the pin flow every time they add a cell -
that would make the pin flow a second, louder switch beside the one the owner chose.

**Verification:**

- `Grep` - `shouldShowRequestPermissionRationale` present.
- Read the file - the permanently-denied path calls the pick flow without showing the dialog.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` exit 0, `Fast check passed` (2026-08-08).
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entry added - batched for the whole ticket at Phase 05 closure.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings. The permission contract registers in a field initialiser beside the existing picker one, so it is never registered after `onStart`; every exit from the dialog - confirm, decline, back, tap outside - reaches the pick, so no path loses the cell the user asked for; the dialog inherits the S0538 confirm/cancel button styles from the app theme, as every `MaterialAlertDialogBuilder` here does.

---

## Handoff Notes to Next Phase

Granting at pin time is followed by a cell insert, which is a desktop database write, which re-emits
`observeCells` and re-resolves every cell on the desktop - so already-pinned snapshot cells convert to live
data at that moment without any explicit invalidation, which is what strategic §3.3 asks for.

---

## Rollback Plan

Revert phase commit(s) - the pin flow returns to never asking. Live data still works for a user who granted
the permission from Settings.
