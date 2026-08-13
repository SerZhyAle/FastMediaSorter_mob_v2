# Phase 04 - Technical gadgets on the desktop

**Strategic spec:** [`../S1178_launcher-system-status-widgets.md`](../S1178_launcher-system-status-widgets.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** Phase 05
**Steps done:** 6 / 6
**Started:** 2026-08-08
**Completed:** 2026-08-08

---

## Objective

Turn the four metrics into four placeable desktop gadgets sharing one layout, one view and one formatter, mounted only where the launcher surface is.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.
- [ ] UI placement is decided in the strategic spec and not open here: §11.1 places each gadget on the launcher desktop through the existing gadget picker, §5.1.4 fixes one layout for all four - large value, caption, optional fill bar - and §3.2 forbids the bar from being the only carrier of the value.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | +40 |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | +40 |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | +40 |
| `app_v2/src/launcherEnabled/res/layout/gadget_launcher_technical.xml` | New | ≤ 90 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/TechnicalGadgetFormatter.kt` | New | ≤ 220 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/TechnicalGadget.kt` | New | ≤ 220 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/di/TechnicalGadgetModule.kt` | New | ≤ 160 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/LauncherGadgetRegistry.kt` | Modified | ≤ 110 |

> Backup / split thresholds: see Constraints (>500 LOC → backup step, >1500 LOC → split via Manager pattern). The three `strings.xml` files are far past 500 lines - step 04.1 carries the backup sub-step for them.
>
> **Flavor placement.** Every Kotlin and layout file above lives under `app_v2/src/launcherEnabled/`, which `app_v2/build.gradle.kts` mounts for `standard` and `noLegal` only. No file in this phase carries a `BuildConfig.IS_*` guard, and no file in `src/main/java/` learns about these gadgets.
>
> **Landscape parity.** `app_v2/src/launcherEnabled/res/layout-land/` contains only `activity_launcher_home.xml`; no gadget layout has a landscape variant, and the desktop grid measures a gadget's cell in both orientations. Landscape variant absent - not needed.

---

## Steps

### Step 04.1 - Add the trilingual strings

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> First copy the three `strings.xml` files to `temp/S1178/` with a timestamped name - each is past the 500-line backup threshold. Then add every key with one `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key <key> -En <en> -Ru <ru> -Uk <uk>` call per key, never by hand-editing the three files:
>
> - `launcher_gadget_network`, `launcher_gadget_battery`, `launcher_gadget_storage`, `launcher_gadget_resources` - picker labels.
> - `launcher_gadget_technical_unknown` - the word shown where a metric is unreadable.
> - `launcher_gadget_battery_remaining_approx` - the remaining-time line marked as an estimate.
> - `launcher_gadget_storage_free` - free-of-total line, two size arguments.
> - `launcher_gadget_resources_uptime` - time since boot.
> - One `*_description` key per gadget for the accessibility announcement, each taking the value and naming what it means.
>
> Check every new string against `docs/COMMUNICATION_POLICY.md` §2 for the message formula of its type and §6 for the tone checklist before running the tool. After the last key, run `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "launcher_gadget_"`.

**Why:**

Strategic §3.2 makes EN/RU/UK coverage mandatory for this feature, and §3.2 accessibility requires every cell to carry a textual alternative naming both the number and its meaning - which is what the `*_description` keys are for.

**Verification:**

- `Grep` - each new key present in all three `strings.xml` files.
- `Grep` - `launcher_gadget_technical_unknown` present in `values/`, `values-ru/` and `values-uk/`.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "launcher_gadget_"` exits 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

**Step Log:**

- 2026-08-08 - Verification 4/4 PASS. Backups of the three `strings.xml` in `temp/S1178/strings-values*-20260808-172350.xml`.
  19 keys added via `set-android-string.ps1 -Action add` (18 planned + `launcher_gadget_battery_remaining`, the
  non-approximate counterpart of the approximate line - a marker needs something to be marked against).
  `check_strings_localized.ps1 -KeyPrefix "launcher_gadget_"` exit 0, 65 keys present in en/ru/uk.
- **Deviation from the prompt, with cause.** The four transport words are NOT new keys: the gadget reuses the
  tray's `launcher_tray_network_*`. One transport vocabulary (`NetworkTransport`) with two sets of words is
  exactly the drift phase 03 deleted the duplicate classifier to prevent.
- **UK apostrophe trap.** `-Action add` escapes a backslash it is handed, so passing `Пам\'ять` wrote `\\'` and
  `mergeStandardDebugResources` failed with "Invalid unicode escape sequence". Fixed by dropping the
  apostrophe from the wording: UK storage is `Сховище пристрою`. Pass raw text to the tool, never pre-escaped.

---

### Step 04.2 - Add the shared technical gadget layout

**Files:** `app_v2/src/launcherEnabled/res/layout/gadget_launcher_technical.xml`
**Depends on:** Step 04.1

**Prompt for developer:**

> Add `gadget_launcher_technical.xml` as a `<merge tools:parentTag="android.widget.FrameLayout">`, following `gadget_launcher_weather.xml`: an `ImageView` for the gadget icon, an autosizing `TextView` for the value, a smaller `TextView` for the caption, and a `com.google.android.material.progressindicator.LinearProgressIndicator` with `android:visibility="gone"` for the optional fill bar. Set every colour from `?attr/colorOnSurface` and `?attr/colorOnSurfaceVariant` - no literal hex anywhere. Set the icon's `contentDescription` to `@null`; the announcement comes from the root view. Set no fixed height - the desktop grid measures the cell.

**Why:**

Strategic ADR-2 and §5.1.4 require one layout behind all four gadgets, because four copies of the same body diverge at the first edit; §3.1.2 states the set must read as one set.

**Verification:**

- `Glob` - `app_v2/src/launcherEnabled/res/layout/gadget_launcher_technical.xml` exists.
- `Grep` - `LinearProgressIndicator` matches exactly once.
- `Grep` - `="#` returns zero hits in that file.
- `Grep` - `layout_height="[0-9]` returns zero hits in that file.

**Status:** `[x]` done

**Step Log:**

- 2026-08-08 - Verification 4/4 PASS. Files: launcherEnabled/res/layout/gadget_launcher_technical.xml (+76 LOC).
  `LinearProgressIndicator` = 1, `="#` = 0, fixed `layout_height` = 0. No landscape variant - the folder holds
  only `activity_launcher_home.xml` and the grid measures a gadget cell in both orientations.

---

### Step 04.3 - Add the formatter

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/TechnicalGadgetFormatter.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> Add `TechnicalGadgetFormatter` turning a `NetworkStatus`, `BatteryStatus`, `StorageStatus` or `MemoryStatus` into a value string, a caption string and an optional fill fraction between 0 and 1. Format every byte count with `android.text.format.Formatter.formatFileSize(context, bytes)` and every duration with `android.text.format.DateUtils`, so units and ordering come from the system locale rather than from concatenation. Render any `MetricValue.Unknown` as `launcher_gadget_technical_unknown` and return a null fill fraction for it. Prefix a battery estimate with the approximate marker whenever `isEstimateApproximate` is true. Omit the card line entirely when `StorageStatus.card` is null.

**Why:**

Strategic §3.2 requires units and size formats to come from the system locale rather than from string concatenation, and §5.1.1 puts formatting in the view layer so the metrics stay values that another surface can render differently.

**Verification:**

- `Glob` - `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/TechnicalGadgetFormatter.kt` exists.
- `Grep` - `class TechnicalGadgetFormatter` matches exactly once.
- `Grep` - `Formatter.formatFileSize` present.
- `Grep` - `launcher_gadget_technical_unknown` present.
- `Grep` - `"KB"`, `"MB"`, `"GB"` each return zero hits in that file.
- `Grep` - `Log\.d\(` returns zero hits in that file.

**Status:** `[x]` done

**Step Log:**

- 2026-08-08 - Verification 6/6 PASS. Files: ui/launcher/gadget/TechnicalGadgetFormatter.kt (+178 LOC).
  `class TechnicalGadgetFormatter` = 1, `Formatter.formatFileSize` = 2, `launcher_gadget_technical_unknown` = 1,
  `"KB"`/`"MB"`/`"GB"` = 0, `Log.d(` = 0. Emits `TechnicalGadgetContent` (value, caption, fill, description);
  the description is what step 04.4 puts on `contentDescription`. `fillOf` returns null unless BOTH total and
  available are `Known` - a known total with an unknown free space would otherwise draw a full device.

---

### Step 04.4 - Add the shared gadget view

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/TechnicalGadget.kt`
**Depends on:** Step 04.3

**Prompt for developer:**

> In `TechnicalGadget.kt` add a private `TechnicalGadgetView` extending `LauncherGadgetView`, inflating `gadget_launcher_technical.xml` through view binding. Override `onActive()` to loop `while (isActive)`: read the injected `DeviceStatusProvider`, render through `TechnicalGadgetFormatter`, then `delay(provider.refreshIntervalMs)` - the same shape `WeatherGadgetView.onActive` uses. Set the root view's `contentDescription` to the formatted description string on every render, and show the fill bar only when the formatter returns a fraction, leaving the numeric value visible in every case. Do not register a receiver, start a job outside `onActive`, or hold a reference to anything with a longer lifetime than the view.

**Why:**

Strategic §2.5 and §11.7 require a gadget to refresh only while its cell is visible and to cost nothing while it is not, and §7 names a subscription outliving a recycled cell as the leak this set is most exposed to.

**Verification:**

- `Grep` - `class TechnicalGadgetView` matches exactly once in `TechnicalGadget.kt`.
- `Grep` - `LauncherGadgetView` present on that class's declaration line.
- `Grep` - `override suspend fun CoroutineScope.onActive` present.
- `Grep` - `refreshIntervalMs` present - the period comes from the provider, not the view.
- `Grep` - `registerReceiver`, `GlobalScope`, `lifecycleScope` each return zero hits in that file.
- `Grep` - `contentDescription` present.
- `Grep` - `Log\.d\(` returns zero hits in that file.

**Status:** `[x]` done

**Step Log:**

- 2026-08-08 - Verification 7/7 PASS. Files: ui/launcher/gadget/TechnicalGadget.kt (+90 LOC, both classes).
  `class TechnicalGadgetView` = 1, `LauncherGadgetView` present, `override suspend fun CoroutineScope.onActive`
  = 1, `refreshIntervalMs` = 1, `registerReceiver`/`GlobalScope`/`lifecycleScope` = 0, `contentDescription` = 1,
  `Log.d(` = 0. No `runCatching` inside the poll loop on purpose - it would swallow the `CancellationException`
  that stops the loop on detach, which is the one mechanism the whole set's power budget rests on.

---

### Step 04.5 - Add the four gadget registrations and their keys

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/TechnicalGadget.kt`, `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/LauncherGadgetRegistry.kt`
**Depends on:** Step 04.4

**Prompt for developer:**

> In `TechnicalGadget.kt` add `class TechnicalGadget` implementing `LauncherGadget`, parameterised by key, `labelRes`, `iconRes` and its `DeviceStatusProvider` - one class instantiated four times, following `HomeWidgetGadget`. Give all four the same `defaultSpanW = 2`, `defaultSpanH = 1`, `minSpanW = 1`, `minSpanH = 1` as `WeatherGadget`; the strategic spec states no sizes, and identical spans are what makes the four read as one set. Set `requiresResourceParam = false` on all four. Add `KEY_NETWORK = "network"`, `KEY_BATTERY = "battery"`, `KEY_STORAGE = "storage"` and `KEY_RESOURCES = "resources"` to `LauncherGadgetRegistry.Companion`, with the existing comment convention that a key is a storage format and is never renamed. Reuse `ic_wifi`, `ic_battery`, `ic_storage` and `ic_speed` - add no drawable.

**Why:**

Strategic §3.1.2 requires the four to look like one set rather than four differently styled cells, and §3.2 fixes the storage contract as gadget keys added to the registry with no migration.

**Verification:**

- `Grep` - `class TechnicalGadget` matches exactly once and its declaration line contains `LauncherGadget`.
- `Grep` - `KEY_NETWORK`, `KEY_BATTERY`, `KEY_STORAGE`, `KEY_RESOURCES` each present in `LauncherGadgetRegistry.kt`.
- `Grep` - `@Entity`, `Migration` each return zero hits across the phase's files - the keys are a text value, not a schema change.
- `Glob` - no new file under `app_v2/src/main/res/drawable/`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-08 - Verification 4/4 PASS. `class TechnicalGadget(` = 1 (the predicate's plain substring also
  matches `TechnicalGadgetView` from step 04.4, so it is judged on the declaration itself); its declaration
  block ends `) : LauncherGadget {` - multi-line because the single-line form exceeds the 120-char limit.
  `KEY_NETWORK`/`KEY_BATTERY`/`KEY_STORAGE`/`KEY_RESOURCES` all in `LauncherGadgetRegistry.kt`; `@Entity` and
  `Migration` = 0 across the phase's files; no new drawable - `ic_wifi`, `ic_battery`, `ic_storage`, `ic_speed`
  all already exist in `src/main/res/drawable/`.

---

### Step 04.6 - Wire the four gadgets into the registry through Hilt

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/di/TechnicalGadgetModule.kt`, `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/LauncherGadgetRegistry.kt`
**Depends on:** Step 04.5

**Prompt for developer:**

> Add `TechnicalGadgetModule` in `ui/launcher/gadget/di/`, `@InstallIn(SingletonComponent::class)`, declaring a `@TechnicalGadgets` qualifier and a `@Provides @Singleton` function returning the four `TechnicalGadget` instances as one `List<LauncherGadget>`, injecting the four use cases - this mirrors `HomeWidgetGadgetModule` and keeps `LauncherGadgetRegistry` under detekt's constructor threshold. Add the qualified parameter to `LauncherGadgetRegistry`'s constructor as `@TechnicalGadgets technical: List<@JvmSuppressWildcards LauncherGadget>` - the `@JvmSuppressWildcards` is required or Dagger fails with `MissingBinding` at `hiltJavaCompile`, after the Kotlin compile has already reported clean - and append it to the `gadgets` list.

**Why:**

Strategic §5.3 requires a fifth technical gadget to arrive as a registration rather than a new view, which only holds if the registry consumes the set as one binding.

**Verification:**

- `Glob` - `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/di/TechnicalGadgetModule.kt` exists.
- `Grep` - `annotation class TechnicalGadgets` matches exactly once.
- `Grep` - `@JvmSuppressWildcards` present in `LauncherGadgetRegistry.kt` on the technical-gadget parameter.
- `Grep` - `technical` appended to the `gadgets` list expression in `LauncherGadgetRegistry.kt`.
- Run `.\a.ps1 fk` and `.\a.ps1 fkn`, then a `standard` debug build - a Kotlin-only check does not prove the Hilt graph.

**Status:** `[x]` done

**Step Log:**

- 2026-08-08 - Verification 5/5 PASS. Files: ui/launcher/gadget/di/TechnicalGadgetModule.kt (+72 LOC),
  LauncherGadgetRegistry.kt (89 -> 101 LOC). `annotation class TechnicalGadgets` = 1, `@JvmSuppressWildcards`
  on the new parameter, `+ technical` appended to the `gadgets` expression.
- Builds: `.\a.ps1 fk` SUCCESSFUL 51s, `.\a.ps1 fkn` SUCCESSFUL 28s, `.\a.ps1 d` SUCCESSFUL 1m26s with
  `hiltJavaCompileStandardDebug` executed - that task is the actual proof of the graph, and the reason a
  Kotlin-only check is not enough here.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles - `fk` 51s, `fkn` 28s, `d` (standard debug APK, Hilt graph) 1m26s, and `Lite` code check
  1m21s to prove the gadgets are absent there. All BUILD SUCCESSFUL, 2026-08-08.
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entry added for the phase via `scripts/post-change.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated by `scripts/post-change.ps1`.
- [x] Phase-boundary audit run - see Audit below.

---

## Phase-boundary audit (2026-08-08)

- **Lifecycle / coroutine trigger.** The only long-lived work is the `while (isActive)` loop inside
  `LauncherGadgetView.onActive`, which the base class cancels on detach and on STOP. The view starts no job of
  its own, registers no receiver and captures nothing outliving it - the loop closes over `provider` and
  `binding`, both view-scoped. Strategic §7's top two risks are structurally absent, not merely handled.
- **No swallowed cancellation.** The poll loop deliberately carries no `runCatching`; each provider already
  contains its own platform-read failures, so a catch here would only be able to swallow the
  `CancellationException` that stops the loop.
- **Main-safety.** Every `read()` implementation switches to `Dispatchers.IO` itself; the view only formats and
  assigns text, on the main thread where it belongs.
- **Flavor isolation.** No `BuildConfig.IS_*` guard anywhere in the phase; absence in `lite`/`photos`/`legacy`
  is proved by the `Lite` compile succeeding with the source set unmounted (strategic §11.10).
- **Accessibility.** `contentDescription` is reassigned on every render and always carries the number plus what
  it means; the fill bar is decoration on top of a value that never leaves the screen (strategic §11.9).
- **Permissions.** Manifest untouched in this phase; the permission count is the same as before phase 03.
- **P0/P1 open:** none.

---

## Handoff Notes to Next Phase

- The four registry keys are persisted inside a cell's `target` column from the first time the user places one - they are a storage format from that moment.
- The gadget set costs nothing on `lite`, `photos` and `legacy` because the source set is not mounted there, not because a flag is off.

---

## Rollback Plan

Revert phase commit(s) - no data migration. A desktop cell holding one of the four keys decodes to an unknown gadget after a revert, which the desktop already renders as a broken cell rather than crashing.
