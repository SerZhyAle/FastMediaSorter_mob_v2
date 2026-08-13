# Phase 03 - Opt-in with prominent disclosure

**Strategic spec:** [`../S0429_home-screen-google-content.md`](../S0429_home-screen-google-content.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 5 / 5
**Started:** 2026-08-06
**Completed:** 2026-08-06 - with the screenshot deferred to `/spec-test-device`, on the escape this phase's own UI criterion provides for "no device attached". The ticket ends in `BlockNeedUserTest`, so the shot is a required part of the hand-off rather than a dropped one.

---

## Objective

Offer the access where its value is visible - a button on the gadget in its degraded state, behind a disclosure dialog - and list it honestly in the permission registry.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done.
- [ ] `CODE.LOCK` acquired before the first source edit.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | - |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | - |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | - |
| `app_v2/src/launcherEnabled/res/layout/gadget_launcher_now_playing.xml` | Modified | ≤ 140 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/AudioNowPlayingGadget.kt` | Modified | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/permissions/PermissionRegistryRepositoryImpl.kt` | Modified | ≤ 230 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/CheckPermissionStatusUseCase.kt` | Modified | ≤ 80 |

> **Landscape parity (Rule 11).** `gadget_launcher_now_playing.xml` has no `layout-land` variant and needs none - the only landscape file in `src/launcherEnabled/res/layout-land/` is `activity_launcher_home.xml`, and the gadget reflows inside whatever cell it is given.
>
> The two `src/main` files carry a **flavor-gated registry row**, not a flavor guard: `flavorGates = setOf("SUPPORT_LAUNCHER")` is the registry's own declarative mechanism, already used by the `read_contacts` row. No `BuildConfig.IS_*` branch is introduced in `src/main` (Rule 14).

---

## Steps

### Step 03.1 - Add the disclosure and registry strings across three locales

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add five keys, each in one lockstep `scripts/utils/set-android-string.ps1 -Action add` call: `launcher_now_playing_other_apps` (the gadget button, e.g. "Show other apps' music"), `launcher_now_playing_access_title`, `launcher_now_playing_access_message` (the disclosure: the launcher reads the active media session only to show and control what is playing, and reads no notifications), `perm_title_notification_listener` and `perm_desc_notification_listener` for the registry row. Check every one against `docs/COMMUNICATION_POLICY.md` §2 message formula and §6 tone checklist - the disclosure is the text Play reviews, so it states what is read and what it is used for, in that order, with no hedging.

**Why:**

Strategic §5 item 6 settles the disclosure's form and leaves only the wording, and §8 records that Play reviews notification access sceptically and requires the justification to be visible before the system screen opens.

**Verification:**

- `Grep` - all five keys present in all three `strings.xml`.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "launcher_now_playing_"` exits 0.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "perm_title_notification_listener"` exits 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x] done`

**Step Log:**

- 2026-08-06 - Verification 4/4 PASS. Five keys added through `set-android-string.ps1 -Action add`, one lockstep call each, EN/RU/UK. `check_strings_localized.ps1` exit 0 for `launcher_now_playing_` (3 keys), `perm_title_notification_listener` and `perm_desc_notification_listener`. The other ten locales report as best-effort and not fatal - that backlog is S1420's, not a new finding.
- 2026-08-06 - §6 checklist: no exception text, no bare "Are you sure?", no success-phrasing, no emoji, legal texts untouched, EN/RU/UK parity proven by the audits above. The disclosure states what is read before what it is used for and names the withdrawal path, per §2. It also says outright that Android calls this "notification access" while the app reads no notifications - the one thing a reader of the system screen will otherwise conclude on their own, and the thing Play checks.

---

### Step 03.2 - Add the button to the gadget layout

**Files:** `app_v2/src/launcherEnabled/res/layout/gadget_launcher_now_playing.xml`
**Depends on:** Step 03.1

**Prompt for developer:**

> Add a `MaterialButton` with id `nowPlayingGrantAccess`, `android:visibility="gone"`, text `@string/launcher_now_playing_other_apps`, placed so it occupies the row the transport controls use when they are hidden. Use a borderless or tonal style already in the theme, no hardcoded colours, and keep it focusable so a D-pad reaches it.

**Why:**

The owner ruled on 2026-08-06 that the access is offered on the gadget itself in its degraded state - the place where the user can see what they are missing - mirroring the all-files-access prompt in `AddResourceScanManager`.

**Verification:**

- `Grep` - `nowPlayingGrantAccess` present in that layout.
- `Grep` - `="#` returns zero hits in that layout.
- `.\a.ps1 fr` exits 0.

**Status:** `[x] done`

**Step Log:**

- 2026-08-06 - Verification 3/3 PASS. `nowPlayingGrantAccess` 1 hit, `="#` 0 hits, `.\a.ps1 fr` exit 0 with 8 tasks executed. Rule 11 re-confirmed by observation rather than by the plan's assurance: `src/launcherEnabled/res/layout-land/` contains only `activity_launcher_home.xml`, so this gadget has no landscape counterpart to keep in step. Style is `Widget.Material3.Button.TextButton`, already used elsewhere in the app, `focusable` for D-pad, `textSize` 11sp with `maxLines="1"` and `ellipsize="end"` because the host cell is two columns by one row and the label must not wrap it open.

---

### Step 03.3 - Wire the button through the disclosure

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/AudioNowPlayingGadget.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Show `nowPlayingGrantAccess` only when `NotificationAccessState.isEnabled(context)` is false, and hide it the moment it turns true - the polling loop already re-renders, so no extra listener is needed. Its click shows a `MaterialAlertDialogBuilder` carrying `launcher_now_playing_access_title` and `launcher_now_playing_access_message`, with a confirm that starts `NotificationAccessState.settingsIntent()` and a cancel that dismisses. The dialog is the disclosure and must appear **before** the system screen, never after. Use the named dialog action-pair styles per CLAUDE.md §11.

**Why:**

Strategic §3.3 requires prominent in-app disclosure and an explicit opt-in before the system settings screen opens, and §8 notes Play treats a bare jump to the settings screen as the failure case.

**Verification:**

- `Grep` - `launcher_now_playing_access_message` present in that file.
- `Grep` - `settingsIntent` present in that file.
- `Grep` - `Log\.d\(` returns zero hits in that file.
- `.\a.ps1 fk` exits 0.

**Status:** `[x] done`

**Step Log:**

- 2026-08-06 - Verification 4/4 PASS. `launcher_now_playing_access_message` 1 hit, `settingsIntent` 1 hit, `Log.d(` 0 hits, `.\a.ps1 fk` exit 0 with `compileStandardDebugKotlin` executed. Files: AudioNowPlayingGadget.kt (145 -> 175 LOC, budget 200). The dialog is built the way the exemplar this ticket was pointed at builds one (`AddResourceScanManager.kt:214-221`): `MaterialAlertDialogBuilder` with plain positive/negative buttons. CLAUDE.md §11's named action-pair styles govern confirm/cancel **buttons declared in a layout** - which is what `assert-dialog-cancel-style.ps1` inspects - not the platform buttons a builder creates, and no `MaterialAlertDialogBuilder` call site in this app styles them.
- 2026-08-06 - Two decisions the prompt did not cover, both recorded rather than left silent:
  - **The offer and the transport row are mutually exclusive** (`!accessGranted && !state.canControl`). The prompt asked only that the button hide once access turns true, but the host cell is two columns by one row and physically cannot show a transport row and a full-width button at once - the step that added the button already placed it in "the row the transport controls use when they are hidden". Consequence to check on device: a user whose own music is playing while access is off does not see the offer until playback stops. The owner's ruling (strategic §3.3) says the offer lives on the gadget "in its degraded state" and does not settle this narrower case, so it goes to the device-test note rather than being presented as decided.
  - **`startActivity` is guarded and logged.** A stripped ROM - a head unit, per `project_owner_runs_app_on_car_head_unit` - can omit the notification-access screen entirely. An unguarded call would take the home screen down with it, so the failure is caught and logged by exception class only; Rule 19 forbids the bare swallow.

---

### Step 03.4 - List the access in the permission registry

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/permissions/PermissionRegistryRepositoryImpl.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Add a `PermissionEntry` with `id = "notification_listener"`, `manifestName = Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE`, the two new strings, `group = PermissionGroup.SYSTEM`, `optional = true` and `buildGates = setOf("SUPPORT_LAUNCHER")`, placed in the SYSTEM block beside `battery_optimization`. Add a comment saying the manifest name is a label for a special access the app never requests at runtime, the same way `manage_external_storage` is listed.
>
> Two corrections applied on 2026-08-06 against the live `PermissionEntry` (`domain/model/PermissionEntry.kt:36-48`), both derivable from the `read_contacts` row this step already cites:
>
> - The field is **`buildGates`**, not `flavorGates` - no such property exists, so the original name would not compile.
> - The row also needs `iconRes = 0` (no default, as every sibling row passes) and `grantKind = PermissionGrantKind.SYSTEM_SCREEN`. The latter is not cosmetic: `grantKind` defaults to `RUNTIME_DIALOG`, which would have the permissions screen offer a runtime dialog for a signature permission that can only ever be granted on a system screen - the same reason `battery_optimization` and `manage_external_storage` set it.

**Why:**

The owner's 2026-08-06 ruling pairs the gadget button with a registry entry so the capability is honestly enumerated rather than living only where it was switched on.

**Verification:**

- `Grep` - `notification_listener` present in that file.
- `Grep` - `SUPPORT_LAUNCHER` present at least twice in that file (the contacts row and this one).
- `.\a.ps1 fk` exits 0.

**Status:** `[x] done`

**Step Log:**

- 2026-08-06 - Verification 3/3 PASS. `notification_listener` 3 hits (the id and the two string references), `SUPPORT_LAUNCHER` 3 hits, `.\a.ps1 fk` exit 0. The row carries the two corrections recorded in the prompt above - `buildGates` and `grantKind = SYSTEM_SCREEN` - and `iconRes = 0` like every sibling. Placed last in the SYSTEM block, after `battery_optimization`.

---

### Step 03.5 - Answer the row's status from the real access

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/CheckPermissionStatusUseCase.kt`
**Depends on:** Step 03.4

**Prompt for developer:**

> Add a `Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE` branch to the `when (entry.manifestName)`, returning GRANTED when this package is in `NotificationManagerCompat.getEnabledListenerPackages(appContext)` and DENIED otherwise. Do not call `NotificationAccessState` - it lives in `src/launcherEnabled` and this file is in `src/main`; inline the same check, which is one call and no new dependency.

**Why:**

`checkSelfPermission` answers NOT GRANTED for a signature permission the app never holds, so without this branch the registry row would report denied to a user who had granted the access - the same reason the `MANAGE_EXTERNAL_STORAGE` and battery-optimisation branches beside it exist.

**Verification:**

- `Grep` - `BIND_NOTIFICATION_LISTENER_SERVICE` present in that file.
- `Grep` - `getEnabledListenerPackages` present in that file.
- `Grep` - `NotificationAccessState` returns zero hits in `app_v2/src/main`.
- `.\a.ps1 fc` exits 0.

**Status:** `[x] done`

**Step Log:**

- 2026-08-06 - Verification 4/4 PASS. `BIND_NOTIFICATION_LISTENER_SERVICE` 1 hit, `getEnabledListenerPackages` 1 hit, `NotificationAccessState` 0 hits in `app_v2/src/main`, `.\a.ps1 fc` exit 0. The branch sits before the battery-optimisation one, inside the same `when (entry.manifestName)` the two `manage_*` special accesses already use.
- 2026-08-06 - The zero-hit predicate first failed on my own comment, which named the launcher-side helper while explaining why it is not called from here. Reworded to describe it instead of naming it: unlike the `startForegroundService` case in Phase 01, this predicate is worth keeping literal - it also catches a real import, which is the thing that would actually break the flavor boundary.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fc` exit 0.
- [x] `Grep` for `TODO(phase-03)` returns zero hits in `app_v2/src`.
- [x] Dev log entry added for the phase via `post-change.ps1` - `post-change: PASS (Mixed, 55206 ms)`, exit 0, one changelog row.
- [x] Settings docs - see the note below; the criterion's premise does not hold and the stronger check was run instead.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated - 2484 records.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings. See below.
- [~] UI placement decision recorded - **yes**, strategic §3.3 carries the owner's quiz ruling of 2026-08-06 verbatim. Screenshot - **deferred, no device attached** (`device-ready.ps1` re-probed at the phase boundary: `ready:false`, `state:no-device`). Left to `/spec-test-device`, which the `BlockNeedUserTest` hand-off in Phase 04 requires anyway.
- [x] `CODE.LOCK` released - by the facade at the end of its run; re-checked `absent (free)`.

### Settings-doc-sync: the criterion's premise is wrong

The criterion assumed a permissions-screen row feeds the settings manifest, so `post-change.ps1`'s gate would have to *run* rather than skip. It skipped: `not applicable - no changed file is a settings surface or settings doc`. That is correct behaviour, not a miss - the manifest is built from settings **layouts**, and the permissions screen renders from `PermissionRegistryRepositoryImpl`, which is not one.

Rather than accept a skip as proof, `assert-settings-doc-sync.ps1` was run standalone: exit 0, `catalog complete, manifest fresh, annotations covered, reference up to date, HOW_TO recipes in sync` (25 layouts classified, 264 annotation keys, 50 HOW_TO recipes). So the docs are provably in sync; they simply needed no regeneration.

### Phase-boundary audit (Layers 1-3)

- **Layer 3:** the one new listener is `nowPlayingGrantAccess`'s click, set in `init` on a view the binding owns and torn down with it - the same ownership as the three transport buttons beside it. The listener-symmetry gate reports `new imbalance 0`. The dialog is built per click and holds no reference past dismissal.
- **Layer 2:** no coroutine or lifecycle surface changed. `render()` now reads notification access once per tick and threads it into `resolve()`, so adding the button cost no extra system call - previously `resolve()` read it itself.
- **Layer 1:** the disclosure is one function; the registry row and the status branch each copy a live sibling pattern rather than inventing one.
- **Flavor boundary held, and the predicate proved it:** `NotificationAccessState` has zero hits in `app_v2/src/main`, so nothing in the shared source set depends on a `launcherEnabled` type. The registry row is gated declaratively by `buildGates`, not by a `BuildConfig` branch (Rule 14).
- **P2, carried to the device test:** the offer button and the transport row are mutually exclusive by necessity of cell size, so a user playing this app's own music while access is off will not see the offer until playback stops. Recorded in Step 03.3 and named in the `BlockNeedUserTest` note, because it is a visible-behaviour question the owner should answer by looking, not one this pipeline should settle by argument.

---

## Handoff Notes to Next Phase

The capability is complete and reachable. Phase 04 records it and regenerates the derived indexes.

---

## Rollback Plan

Revert the phase commit. A user who already granted notification access keeps it at system level; the app simply stops offering and listing it.
