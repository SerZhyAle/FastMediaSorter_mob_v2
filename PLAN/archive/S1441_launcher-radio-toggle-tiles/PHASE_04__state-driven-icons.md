# Phase 04 - State-driven tile icons

**Strategic spec:** [`../S1441_launcher-radio-toggle-tiles.md`](../S1441_launcher-radio-toggle-tiles.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** Phase 05
**Steps done:** 3 / 3
**Started:** 2026-08-08
**Completed:** 2026-08-08

---

## Objective

Make the Wi-Fi and Bluetooth tiles draw a different main icon when the radio is on and when it is off, and make that icon follow a state change that happened anywhere - inside the app or outside it.

---

## Prerequisites

- [ ] Phase 03 is ✅ Done - the tap path already toggles.
- [ ] Working tree is clean or on a feature branch.

**Placement decision on record (S1338).** Strategic §6.3, owner ruling of 2026-08-07, verbatim: «Решением владельца 2026-08-07: сменой основного значка клетки. Значок 44dp меняется между состояниями "включено" и "выключено"». The rejected alternatives - an 18dp badge in `cellModeBadge` and a caption suffix - are recorded there too, so this phase adds no new view and no new string.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/drawable/ic_wifi_off.xml` | New | ≤ 20 |
| `app_v2/src/main/res/drawable/ic_bluetooth_off.xml` | New | ≤ 20 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/ResolveLauncherCommandLabelUseCase.kt` | Modified | ≤ 25 added |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/ResolveLauncherDesktopUseCase.kt` | Modified | ≤ 30 added |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/panel/ResolveAppLaunchPanelTilesUseCase.kt` | Modified | ≤ 20 added |

> No layout file changes: the decision reuses the existing 44dp `cellIcon`, so `LauncherCellViewBinder.bindShortcut` keeps setting one drawable and needs no edit. Landscape parity therefore does not apply - no `res/layout*` file is touched.

---

## Steps

### Step 04.1 - Add the two off-state vectors

**Files:** `app_v2/src/main/res/drawable/ic_wifi_off.xml`, `app_v2/src/main/res/drawable/ic_bluetooth_off.xml`

**Depends on:** - start of phase

**Prompt for developer:**

> Add two 24dp vector drawables matching the existing `ic_wifi.xml` and `ic_bluetooth.xml` in viewport, tint
> attribute and path style - the standard Material "wifi off" and "bluetooth disabled" glyphs, i.e. the same
> glyph crossed by a slash. Keep `android:tint` exactly as the on-state files declare it so the pair reads as
> one family, and do not hardcode a colour.

**Why:**

Strategic §11.8 requires the state to be readable from the shape alone - a greyscale screenshot of the tile must
still answer whether the radio is on - which a colour change or a tint swap would not satisfy.

**Verification:**

- `Glob` - both files exist.
- `Grep` - neither file contains a literal `#` colour outside the `android:tint` attribute the on-state files use.
- `Grep` - `android:viewportWidth` value matches the corresponding on-state file.
- `.\a.ps1 fr` exits 0.

**Status:** `[x] done`

**Step Log:**

- 2026-08-08 - Verification 4\4 PASS. Files: app_v2/src/main/res/drawable/ic_wifi_off.xml (+10 LOC), app_v2/src/main/res/drawable/ic_bluetooth_off.xml (+10 LOC). `.\a.ps1 fr` exit 0. Dev log recorded.

---

### Step 04.2 - Resolve the desktop icon from the radio state

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/ResolveLauncherCommandLabelUseCase.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/ResolveLauncherDesktopUseCase.kt`

**Depends on:** Step 04.1

**Prompt for developer:**

> Give `ResolveLauncherCommandLabelUseCase.osVisual` an extra parameter carrying the two radio states
> (a small `data class RadioStates(val wifi: Boolean?, val bluetooth: Boolean?)` next to the use case, defaulted
> to both `null`), and return `ic_wifi_off` / `ic_bluetooth_off` when the matching state is `false`. A `null`
> state keeps today's icon - unknown is not off, the same rule `LauncherTrayBluetoothMonitor` already follows.
>
> In `ResolveLauncherDesktopUseCase`, `combine` the existing `observeCells(orientation)` flow with
> `radioControl.state(RadioKind.WIFI)` and `radioControl.state(RadioKind.BLUETOOTH)` and pass the pair into the
> resolver. A state change then emits a `LauncherCellUi` whose `iconRes` differs, which is what carries it past
> `LauncherCellViewBinder`'s `lastBound` equality guard - do not touch that guard.

**Why:**

Strategic §11.5 requires the tile to reflect a change made outside the app as well as inside it, and the desktop
flow currently re-emits only when the persisted cell list changes, so without this combine the icon would update
only after an unrelated database write.

**Verification:**

- `Grep` - `RadioStates` declared exactly once and used in `osVisual`'s signature.
- `Grep` - `ic_wifi_off` and `ic_bluetooth_off` each referenced exactly once in `ResolveLauncherCommandLabelUseCase.kt`.
- `Grep` - `combine(` present in `ResolveLauncherDesktopUseCase.kt` with both `RadioKind.WIFI` and `RadioKind.BLUETOOTH`.
- `Grep` - `lastBound` unchanged in `LauncherCellViewBinder.kt` (file not in this step's scope; the grep proves it was not edited).
- `.\a.ps1 fk` exits 0.

**Status:** `[x] done`

**Step Log:**

- 2026-08-08 - Verification 5\5 PASS. Files: ResolveLauncherCommandLabelUseCase.kt (+20 LOC, `RadioStates` + `offStateIconRes`), ResolveLauncherDesktopUseCase.kt (+14 LOC, three-way `combine`). `.\a.ps1 fk` exit 0. `lastBound` confirmed intact at `app_v2/src/launcherEnabled/.../grid/LauncherCellViewBinder.kt:46` - the step's path omitted the `launcherEnabled` source set, file untouched either way. Dev log recorded.

---

### Step 04.3 - Same icon rule for the app-launch panel

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/panel/ResolveAppLaunchPanelTilesUseCase.kt`

**Depends on:** Step 04.2

**Prompt for developer:**

> Apply the same rule where the panel resolves an `OsShortcut` tile's `iconRes`, reading the state through
> `RadioControlContract`. On a flavor without the network monitor the no-op emits `null`, so the panel keeps
> today's static icon with no branch of its own.
>
> Leave `OsShortcutPickerDialogFragment` alone: it lists targets for assignment, where the current radio state is
> not information the user is choosing by.

**Why:**

Strategic §11.6 requires the surfaces that show these targets to behave alike, and §3.4 requires builds without
the launcher to keep working, which the no-op's `null` state delivers without a flavor guard.

**Verification:**

- `Grep` - `RadioControlContract` appears in `ResolveAppLaunchPanelTilesUseCase.kt`.
- `Grep` - `OsShortcutPickerDialogFragment.kt` does not reference `RadioControlContract`.
- `.\a.ps1 fk` exits 0 and `.\a.ps1 fkn` exits 0.

**Status:** `[x] done`

**Step Log:**

- 2026-08-08 - Verification 3\3 PASS. Files: ResolveAppLaunchPanelTilesUseCase.kt (+18 LOC, `osIconRes`). `.\a.ps1 fk` exit 0, `.\a.ps1 fkn` exit 0. `OsShortcutPickerDialogFragment.kt` clean of `RadioControlContract` (grep exit 1). Deviation from the prompt's wording, recorded because it is a design choice: the state is read with `state(kind).first()` inside the existing `resolveInternalRoute` rather than combined into `invoke()`'s flow. Threading a `RadioStates` parameter would have changed the signatures of `resolveTile` and `resolveInternalRoute`, both of which carry `ReturnCount` entries in `config/detekt/baseline-app_v2.xml` keyed to their exact signature - the change would have resurfaced two baselined findings for no behavioural gain, since the panel resolves on open and closes on the first tap. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 dq` exit 0, 2026-08-08. `hiltJavaCompileStandardDebug` green, which is what proves the new `RadioControlContract` injection into `ResolveAppLaunchPanelTilesUseCase` resolves - `fk` alone never checks the Hilt graph.
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `post-change.ps1` (three batched calls, all five files named).
- [x] `screenshot deferred (no device)` - `device-ready.ps1` reports `no-device` for this session.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

## Phase-boundary audit (2026-08-08)

- Layer 1 - architecture. Both new members are private helpers on existing domain use cases; no UI logic, no layer inversion, no naming deviation. The two drawables carry no literal colour.
- Layer 2 - coroutines and flows. `ResolveLauncherDesktopUseCase.invoke` now combines three cold flows and stays `flowOn(Dispatchers.IO)`; nothing was moved onto the main thread. `osIconRes` uses `state(kind).first()`, which terminates because `RadioStateReader.state` emits at subscription time and `NoOpRadioControlContract.state` is `flowOf(null)` - neither can suspend indefinitely.
- Layer 3 - listener ownership. The `ContentObserver` is registered inside `callbackFlow` and unregistered in its `awaitClose`, so both consumers own it symmetrically. The desktop chain terminates at `stateIn(viewModelScope, WhileSubscribed(..))` in `LauncherHomeViewModel`, so the combined flow cannot outlive the desktop screen - the criterion this phase names explicitly. The panel's `first()` cancels the flow at once, unregistering immediately.
- Layer 4 - Room. Not applicable, no persistence surface touched.
- One deviation from the step prompt is recorded in Step 04.3's log, with its reason. No P0/P1 findings.

---

## Handoff Notes to Next Phase

Behaviour and appearance are complete. Phase 05 records the capability and regenerates the indexes.

---

## Rollback Plan

Revert the phase commit - two drawables and three resolver edits, no persisted data and no layout change.
