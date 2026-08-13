# S1431 research 01 - current surfaces (taskbar tray, top strip, recents, settings)

Date: 2026-08-09. Read-only survey of the code S1431 must extend. Every claim carries file:line.
The launcher lives in `app_v2/src/launcherEnabled/`, never `src/main`.

## 1. Taskbar tray (S1415)

- Renderer: `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherTrayManager.kt`
  (~387 LOC). Constructor at `:48-52`:
  `class LauncherTrayManager(private val lifecycleOwner: LifecycleOwner, private val binding: LauncherTaskbarBinding, private val onRequestPhoneStatePermission: () -> Unit = {})`.
- **The coupling to fix:** the renderer is hard-typed to `LauncherTaskbarBinding` and reaches six views
  by name - `trayClock`, `trayBluetooth`, `traySim1`, `traySim2`, `trayNetwork`, `trayBatteryLevel`
  (`launcher_taskbar.xml:117-174`) - inside `apply()` / `applyBluetooth()` / `applySim()` /
  `renderBattery()`. It cannot render into another container without a placement seam.
- Registry (already placement-agnostic, no View/Binding dependency):
  `ui/launcher/tray/LauncherTrayIndicator.kt` - enum `CLOCK, BLUETOOTH, SIM1, SIM2, NETWORK, BATTERY`
  (`:14-21`) plus `LauncherTrayComposition.from(settings: AppSettings)` (`:43-50`).
  Its KDoc (`:5-13`) already states verbatim that **S1431 reuses the same registry** so the two
  placements cannot offer different sets. This is the invariant behind strategic goal 4.
- State sources, all view-agnostic and reusable as-is:
  `LauncherTrayBluetoothMonitor.state(): Flow<Boolean?>` (`tray/LauncherTrayBluetoothMonitor.kt:30`),
  `LauncherTraySimSignalMonitor` (`tray/LauncherTraySimSignalMonitor.kt`), `GetNetworkStatusUseCase.classify`,
  and a battery `ACTION_BATTERY_CHANGED` receiver registered at `LauncherTrayManager.kt:251-257`.
- **Latent defect, worth noting for the plan:** indicator order is duplicated in two unsynchronised
  places - the enum declaration order and the XML child order. Nothing enforces they agree. A single
  ordered render driven by the registry removes the duplication rather than adding a third copy.

## 2. The clock

- Taskbar `trayClock` (`launcher_taskbar.xml:117-124`) is a bare `android.widget.TextClock` with no
  format override - locale short time, **no seconds**.
- The one existing seconds clock: `gadget_launcher_clock.xml:24-40` (`gadgetClockTime`) sets
  `android:format12Hour="h:mm:ss"` / `android:format24Hour="H:mm:ss"`, used by
  `ui/launcher/gadget/ClockGadget.kt:23-38`.
- No manual ticker, `Handler`, or `ACTION_TIME_TICK` receiver exists anywhere in `launcherEnabled`.
- **Verified against the platform, not assumed:** `javap` on
  `platforms/android-36/android.jar` shows `android.widget.TextClock` declares
  `public void onVisibilityAggregated(boolean)` alongside `onAttachedToWindow` / `onDetachedFromWindow`.
  That is the AOSP hook that starts and stops the per-second ticker by aggregate visibility, which
  includes window visibility - so a seconds clock stops when the launcher leaves the screen with no
  project code. Strategic risk row "clock keeps ticking" is closed by this; do not add a custom ticker.

## 3. Top strip (S1421) - the band S1431 must share

- Manager: `ui/launcher/helpers/LauncherStatusStripManager.kt` (205 LOC).
- Layout: `res/layout/launcher_status_strip.xml` (27 lines). Root `FrameLayout` (`:8-11`,
  `visibility="gone"`, height `@dimen/launcher_status_strip_height` = 28dp, `values/dimens.xml:13`),
  containing `launcherStatusStripContent` (`:14-17`), containing the single custom child
  `LauncherSignalRowView` id `launcherSignalRow` (`:19-22`).
- **There is no left/right container.** The split is computed per-index at runtime in
  `LauncherSignalRowView.onLayout` (`signal/LauncherSignalRowView.kt:93-111`) from `startGroupCount`,
  recomputed in `rebuild()` (`:143`) as `minOf(startCapacity, (childCount + 1) / 2)` - it balances
  homogeneous 24dp chips evenly around the gap. It has **no notion of role**, so it cannot keep a clock
  pinned far-left as chips come and go. This is exactly what strategic ADR-3 replaces.
- Cutout gap is computed, not a view: `_cutoutBounds` (`LauncherStatusStripManager.kt:50-60`) from
  `WindowInsetsCompat.displayCutout.boundingRects` via `topCutoutBounds()` (`:135-146`), pushed into
  `LauncherSignalRowView.setCutoutBounds()` (`:69-75`), consumed by `localCutoutGap()` (`:162-172`).
  Already exposed publicly as `cutoutBounds: StateFlow<Rect>` (`:60`) - reuse it, do not recompute.
- Visibility is driven solely by `replaceSystemStatusArea` (`:79-88`, `binding?.root?.isVisible = replace`).
  Height never varies with content - `:28-29` records that as an owner ruling.
- **Ownership rule to respect:** `launcher_status_strip.xml:13` - "Owned exclusively by
  LauncherStatusStripManager (S1421 ADR-2). Nothing else may add a child." S1431 extends that manager's
  contract; it must not become a second content provider.

## 4. Settings

- Persisted flag: `AppSettings.launcherReplaceSystemStatusArea` (`domain/model/AppSettings.kt:366`,
  default false); DataStore key `launcher_replace_system_status_area`
  (`data/repository/SettingsRepositoryImpl.kt:225`, write at `:821`).
- Six S1415 toggles: `AppSettings.kt:371-376`, keys `SettingsRepositoryImpl.kt:219-224`.
- Dialog: `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/LauncherSettingsDialogFragment.kt`
  (299 LOC). S1422 collapsible sections registered at `:100-111`: `launcher__taskbar`,
  `launcher__top_bar` (`defaultExpanded = true`), `launcher__desktop`, `launcher__system`.
- Placement for the new switch: the `launcher__top_bar` group, immediately after
  `rowLauncherReplaceStatusArea` (`res/layout/dialog_launcher_settings.xml:144-168`) - that is where the
  gating setting already lives. The six indicator toggles stay in `containerLauncherTaskbar`
  (`:94-140`); they govern the indicator set, not the surface, per the registry KDoc.
- Row declaration shape to copy (`dialog_launcher_settings.xml:161-166`):
  ```xml
  <com.sza.fastmediasorter.ui.common.widget.SettingsToggleRow
      android:id="@+id/rowLauncherReplaceStatusArea"
      android:layout_width="match_parent"
      android:layout_height="wrap_content"
      android:layout_marginBottom="@dimen/margin_small"
      app:str_title="@string/launcher_settings_replace_status_area_title" />
  ```
- Wiring shape (`LauncherSettingsDialogFragment.kt:127-132`): `setOnCheckedChangeListener` guarded by
  `isUpdatingFromSettings`, then `viewModel.updateSettings(viewModel.settings.value.copy(...))`;
  mirrored read at `:235`.
- Rule 22 applies: a new settings row means regenerating the settings manifest and reference and
  updating its annotation.

## 5. Taskbar recents

- `ui/launcher/helpers/LauncherTaskbarManager.kt` (79 LOC) owns `taskbarRecents`, `taskbarPinned` and
  `trayContainer` visibility (`:66-70`).
- **The cap to replace:** `LauncherHomeViewModel.kt:112` -
  `val recentIcons: Flow<List<LauncherTaskbarIcon>> = taskbarDependencies.queryRecentCommands(RECENTS_LIMIT)`,
  with `RECENTS_LIMIT = 6` hardcoded at `:662-667`. Never width-measured. Fed to
  `QueryRecentLauncherCommandsUseCase.invoke(limit: Int)` (`:36`).
- Width already reflows without help: `taskbarStrips` (`launcher_taskbar.xml:71-104`) carries
  `layout_weight="1"` and its comment (`:66-70`) records that the block absorbs the slack when
  `trayContainer` shrinks or hides. So freeing the tray already widens the recents row - only the query
  limit blocks more icons from arriving.
- **`trayContainer` visibility today is driven by an independent master switch**
  `launcherTaskbarShowTray` (`LauncherTaskbarManager.kt:66-70`, `LauncherHomeViewModel.kt:184-190`),
  not by `replaceSystemStatusArea`. Strategic ADR-5 subordinates it without overwriting its stored value.

## 6. Flavors

- `app_v2/build.gradle.kts`: `SUPPORT_LAUNCHER` default `false` (`:270`), `true` only in `standard`
  (`:338`) and `noLegal` (`:413`).
- Source sets: `standard` (`:655-658`) and `noLegal` (`:687-689`) mount `src/launcherEnabled/java` +
  `res`; `legacy` (`:705`), `vr` (`:722`), `photos` (`:738`), `lite` (`:752`) mount
  `src/launcherDisabled/java`. Manifest injection gated by `val launcherFlavors = setOf("standard", "noLegal")`
  (`:1135`).
- Capability seam: `launcher/LauncherModeContractImpl.kt` - `isAvailableInBuild` true only in the
  `launcherEnabled` copy. `LauncherSettingsDialogFragment.kt:89-92` dismisses defensively.
- minSdk 26 for both launcher-carrying flavors; the minSdk 23 override (`:476`) is `legacy`-only, which
  never mounts the launcher.

## 7. Layout variants (exhaustive)

Qualifier dirs under `app_v2/src/launcherEnabled/res/`: `drawable`, `layout`, `layout-land`, `values`.
No `sw*`, no `night`, no `values-land`.

- Home screen: `layout/activity_launcher_home.xml` **and** `layout-land/activity_launcher_home.xml` -
  both must change together (Rule 11); both `<include>` the strip at `:38-45`.
- Strip: `layout/launcher_status_strip.xml` only - deliberately single-file, included identically by
  both orientations, same pattern as `launcher_taskbar.xml` (its comment at `:2-4`).
- Taskbar: `layout/launcher_taskbar.xml` only, same reason.

So a strip-internal change is one file; a geometry change touches the two `activity_launcher_home.xml`.

## 8. Tests

Zero. Grepped `LauncherTrayComposition|LauncherTrayIndicator|LauncherTrayManager|LauncherStatusStripManager|LauncherSignalRowView|LauncherSignalRegistry|LauncherTaskbarManager`
across every `test*` source set (`test`, `testCloudEnabled`, `testDocumentsEnabled`, `testNetworkMonitor`,
`testNoLegal`, `testStandard`, `testStreamingEnabled`, `testVr`) and `androidTest` - no matches. There is
no `testLauncherEnabled` source set at all. Pure-logic pieces this ticket introduces (the width-to-count
rule, the zone assignment) are the testable seams; S1498 already tracks the broader launcher test gap.
