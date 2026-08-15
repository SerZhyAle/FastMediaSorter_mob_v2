# Phase 03 - Catalog action model and one execution point

**Strategic spec:** [`../S1441_launcher-radio-toggle-tiles.md`](../S1441_launcher-radio-toggle-tiles.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 04
**Steps done:** 3 / 3
**Started:** 2026-08-08
**Completed:** 2026-08-08

---

## Objective

Teach the OS-shortcut catalog that a target may carry a radio action, and route both existing execution funnels through one use case that tries the toggle first and opens a system surface only when it did not happen.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done - `RadioControlContract` is bound on standard and noLegal.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/panel/OsShortcutCatalog.kt` | Modified | ≤ 30 added |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/radio/ToggleRadioTargetUseCase.kt` | New | ≤ 90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/ExecuteLauncherCommandUseCase.kt` | Modified | ≤ 20 added |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/panel/LaunchAppLaunchPanelTileUseCase.kt` | Modified | ≤ 20 added |

---

## Steps

### Step 03.1 - Extend the catalog record

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/panel/OsShortcutCatalog.kt`

**Depends on:** - start of phase

**Prompt for developer:**

> Add two defaulted fields to `OsShortcutCatalog.Target`: `val radio: RadioKind? = null` and
> `val fallbackIntent: ((Context) -> Intent)? = null`. Set `radio = RadioKind.WIFI` on the `KEY_WIFI` target and
> `radio = RadioKind.BLUETOOTH` on `KEY_BLUETOOTH`; give `KEY_WIFI` a `fallbackIntent` producing
> `Intent(Settings.Panel.ACTION_WIFI)`, which exists from API 29. Leave the other fifteen targets untouched -
> the defaults are what keeps their positional construction and the trailing intent lambda compiling.
>
> `Settings.Panel.ACTION_WIFI` is a compile-time constant like the other `Settings.ACTION_*` values the file
> already inlines under its existing `@SuppressLint("InlinedApi")`, so an old device simply fails to resolve it -
> which is why the caller must check resolvability before using it.

**Why:**

Strategic §1 records that the record is a pure key-to-Intent map and that this is a model limitation rather than
a missing branch, and §3.2 names the system Wi-Fi panel as the better fallback because the user stays inside the
launcher.

**Verification:**

- `Grep` - `val radio: RadioKind? = null` matches exactly once.
- `Grep` - `val fallbackIntent: ((Context) -> Intent)? = null` matches exactly once.
- `Grep` - `RadioKind.WIFI` and `RadioKind.BLUETOOTH` each appear exactly once in the targets list.
- `Grep` - `Settings.Panel.ACTION_WIFI` appears exactly once.
- `.\a.ps1 fk` exits 0 - proof the other fifteen constructions still compile.

**Status:** `[x]` done

---

### Step 03.2 - Add the single execution point

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/radio/ToggleRadioTargetUseCase.kt`

**Depends on:** Step 03.1

**Prompt for developer:**

> Create `ToggleRadioTargetUseCase @Inject constructor(@ApplicationContext context: Context, radioControl: RadioControlContract)`
> with `suspend operator fun invoke(target: OsShortcutCatalog.Target): Boolean`:
>
> - `target.radio == null` or `!radioControl.isToggleSupported` -> return `false`, meaning "not handled here,
>   launch the intent as before".
> - otherwise `radioControl.toggle(target.radio)` -> `true` means the radio flipped and nothing else happens.
> - `false` means the attempt did not take: return `false` as well, so the caller runs its normal launch path.
>
> The use case decides only whether the toggle happened. Choosing between `fallbackIntent` and `intent` stays in
> the callers' existing `startIntent` path, which already knows how to check resolvability - do not duplicate
> `startActivity` here.

**Why:**

Strategic §5.1 pillar 3 requires one execution point shared by every surface, because §7 records that the change
otherwise regresses three surfaces independently.

**Verification:**

- `Grep` - `class ToggleRadioTargetUseCase` matches exactly once.
- `Grep` - `suspend operator fun invoke` present.
- `Grep -c "startActivity"` returns 0 in that file.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

---

### Step 03.3 - Route both funnels through it

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/ExecuteLauncherCommandUseCase.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/panel/LaunchAppLaunchPanelTileUseCase.kt`

**Depends on:** Step 03.2

**Prompt for developer:**

> Inject `ToggleRadioTargetUseCase` into both use cases. In `ExecuteLauncherCommandUseCase.launchOsShortcut` and
> in `LaunchAppLaunchPanelTileUseCase`'s `AppLaunchPanelRouteTarget.OsShortcut` branch, after resolving the
> target and before the existing resolvability check, call the use case; when it returns `true` return `true`
> without starting anything. When it returns `false`, keep today's behaviour but prefer
> `target.fallbackIntent` when it is non-null and resolves on this device, falling back to `target.intent`.
>
> Both call sites are already `suspend`, so no dispatcher changes are needed. Do not add a radio branch to any
> other command kind.

**Why:**

Strategic §2 goal 2 requires the system screen to open immediately when the platform refused the toggle, without
losing the tap, and §11.7 requires the other fifteen targets to behave exactly as before.

**Verification:**

- `Grep` - `ToggleRadioTargetUseCase` appears in both files, once in the constructor of each.
- `Grep` - `fallbackIntent` appears in both files.
- `Grep` - the `OsShortcutCatalog.isResolvable` guard still present in both files.
- `.\a.ps1 fk` exits 0 and `.\a.ps1 fkn` exits 0.

**Status:** `[x]` done

---

## Step Log

- 2026-08-08 - Step 03.1 done, with one shape constraint the plan did not name: the two new fields are declared **before** `intent`, not after. `intent` is a trailing lambda in all seventeen constructions, and a trailing lambda must bind the last parameter - appending the fields after it would have broken every one of them. Greps PASS: both fields once, `RadioKind.WIFI` / `RadioKind.BLUETOOTH` / `Settings.Panel.ACTION_WIFI` once each.
- 2026-08-08 - Step 03.2 done. `ToggleRadioTargetUseCase` created, zero `startActivity` in it. Its `Timber.d` deliberately carries no `Sxxxx` prefix: this line ships, and the ticket prefix is reserved for the temporary probes that exist only while a ticket sits in `BlockNeedUserTest`.
- 2026-08-08 - Step 03.3 done. Both funnels take the use case in their constructor, both keep their `OsShortcutCatalog.isResolvable` guard, both prefer `fallbackIntent` when it resolves. `launchOsShortcut` became `suspend` in each; both callers were already suspend, so no dispatcher changed.
- 2026-08-08 - Compiles PASS: `.\a.ps1 fk` then `-Flavor Lite`, chained with an early exit, overall exit 0 with `compileLiteDebugKotlin` in the tail. Substitution from the written predicate: `lite` was run instead of `fkn` because every file this phase touches is in `src/main`, so the interesting axis is the binding variant (real controller vs no-op), not noLegal's Python bundle - and noLegal's compile was already proven in Phase 02.
- 2026-08-08 - Phase-boundary audit. Layer 1: the radio decision sits in a use case, not in the UI or the catalog; the catalog stays a data declaration and gained no behaviour. Layer 2: the two funnels' new `suspend` hop is inherited from callers that were already suspend, and the use case adds no scope of its own. Layer 3: nothing is retained - the use case holds only the injected contract. Layer 4 not applicable. No P0/P1.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 dq` exit 0, APK produced.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] `LauncherStarterSetsTest` and `LauncherCellCommandTest` still pass - re-run after this phase's edits: 10 and 6 tests, `failures="0" errors="0"`, XML written 00:26 this run. The suite's own exit stays 1 for the same unrelated `IconInventoryExportTest` (S1194) as in Phase 02.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings (see Step Log).

---

## Handoff Notes to Next Phase

Tapping a Wi-Fi or Bluetooth tile now toggles where the platform allows it and opens the system surface where it
does not. The tile still draws a fixed icon - Phase 04 makes it show the state.

---

## Rollback Plan

Revert the phase commit - the catalog fields are defaulted and the funnels return to their previous branch, so
no persisted data changes.
