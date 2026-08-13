# Phase 06 — VR Settings Fragment + tab extension binding

**Strategic spec:** [`../S0245_vr-settings-scaffold-stage0.md`](../S0245_vr-settings-scaffold-stage0.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 04, Phase 05
**Blocks:** Phase 07
**Steps done:** 0 / 7
**Started:** —
**Completed:** —

---

## Objective

Land the `VrSettingsFragment` UI in `src/vr/`, its layout + trilingual strings, and the Hilt-multibinds entry `VrSettingsTabExtension` so the `vr` and `noLegal` flavors gain the 5th Settings tab. Visibility on Stage 0 is gated by `XrDetectionFacade` reporting a non-`NONE` state (i.e. real Quest / Android XR device or master toggle is at default-ON).

---

## Prerequisites

- [ ] Phase 04 ✅ Done — real XR contracts bound for vr/noLegal.
- [ ] Phase 05 ✅ Done — adapter consumes injected extensions.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/res/layout/fragment_vr_settings.xml` | New | ≤ 80 |
| `app_v2/src/vr/res/values/strings.xml` | New | ≤ 40 |
| `app_v2/src/vr/res/values-ru/strings.xml` | New | ≤ 40 |
| `app_v2/src/vr/res/values-uk/strings.xml` | New | ≤ 40 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/ui/settings/vr/VrSettingsFragment.kt` | New | ≤ 120 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/ui/settings/vr/VrSettingsTabExtension.kt` | New | ≤ 80 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/ui/settings/vr/di/VrSettingsExtensionModule.kt` | New | ≤ 40 |

> No landscape layout counterpart created on Stage 0 — the single-toggle Fragment uses a vertical `LinearLayout` that adapts to both orientations. Reassess in Stage 1 when placeholder controls expand.

---

## Steps

### Step 06.1 — Author EN strings for VR Settings

**Files:** `app_v2/src/vr/res/values/strings.xml`
**Depends on:** — start of phase

**Prompt for developer:**

> Add the canonical English strings for the VR tab. Strings pass COMMUNICATION_POLICY §6 checklist (preference-toggle formula: short verb + object; summary explains scope without imperatives).
>
> ```xml
> <?xml version="1.0" encoding="utf-8"?>
> <resources>
>     <!-- S0245 Stage 0: VR settings scaffold strings -->
>     <string name="settings_tab_vr">VR</string>
>     <string name="vr_settings_master_toggle_label">Enable 3D VR</string>
>     <string name="vr_settings_master_toggle_summary">Show VR features in this app</string>
>     <string name="vr_settings_placeholder_summary">More VR settings will appear here as features are added.</string>
> </resources>
> ```

**Verification:**

- `Glob` — `app_v2/src/vr/res/values/strings.xml` exists.
- `Grep` — `<string name=\"settings_tab_vr\">VR</string>` matches once.
- Strings pass COMMUNICATION_POLICY §6 checklist (no marketing fluff, no exclamation, scope-stating summary).

**Status:** `[ ]` not done

---

### Step 06.2 — Author RU strings mirror

**Files:** `app_v2/src/vr/res/values-ru/strings.xml`
**Depends on:** Step 06.1

**Prompt for developer:**

> Trilingual mirror per project convention (`ё` and `..` style rules from CLAUDE.md). Same string keys as EN.
>
> ```xml
> <?xml version="1.0" encoding="utf-8"?>
> <resources>
>     <string name="settings_tab_vr">VR</string>
>     <string name="vr_settings_master_toggle_label">Включить 3D VR</string>
>     <string name="vr_settings_master_toggle_summary">Показывать VR-функции в этом приложении</string>
>     <string name="vr_settings_placeholder_summary">Дополнительные VR-настройки появятся по мере добавления функций.</string>
> </resources>
> ```

**Verification:**

- `Glob` — `app_v2/src/vr/res/values-ru/strings.xml` exists.
- `Grep` — `Включить 3D VR` matches once.
- `Grep` — `…` ellipsis returns zero hits in this file (style rule `..`).

**Status:** `[ ]` not done

---

### Step 06.3 — Author UK strings mirror

**Files:** `app_v2/src/vr/res/values-uk/strings.xml`
**Depends on:** Step 06.1

**Prompt for developer:**

> Ukrainian mirror. Same keys.
>
> ```xml
> <?xml version="1.0" encoding="utf-8"?>
> <resources>
>     <string name="settings_tab_vr">VR</string>
>     <string name="vr_settings_master_toggle_label">Увімкнути 3D VR</string>
>     <string name="vr_settings_master_toggle_summary">Показувати VR-функції у цьому застосунку</string>
>     <string name="vr_settings_placeholder_summary">Додаткові VR-налаштування з\'являться, коли будуть додані функції.</string>
> </resources>
> ```

**Verification:**

- `Glob` — `app_v2/src/vr/res/values-uk/strings.xml` exists.
- `Grep` — `Увімкнути 3D VR` matches once.
- After all three string files exist: run `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "vr_settings_"` exit code 0.

**Status:** `[ ]` not done

---

### Step 06.4 — Author `fragment_vr_settings.xml` layout

**Files:** `app_v2/src/vr/res/layout/fragment_vr_settings.xml`
**Depends on:** Step 06.1

**Prompt for developer:**

> Single-screen layout: outer `ScrollView` → vertical `LinearLayout` → master toggle row (`SwitchMaterial` aligned right, label + summary `TextView`s on left) → divider → placeholder summary `TextView` for "more settings will appear". Match the project visual design tokens (`@dimen/section_padding_horizontal`, `@dimen/section_padding_vertical`, `?attr/colorOnSurface`). All controls must be `focusable="true"` per CLAUDE.md Rule 17 (D-pad / keyboard / mouse coverage).
>
> Skeleton:
>
> ```xml
> <?xml version="1.0" encoding="utf-8"?>
> <ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
>     xmlns:app="http://schemas.android.com/apk/res-auto"
>     xmlns:tools="http://schemas.android.com/tools"
>     android:layout_width="match_parent"
>     android:layout_height="match_parent">
>
>     <LinearLayout
>         android:layout_width="match_parent"
>         android:layout_height="wrap_content"
>         android:orientation="vertical"
>         android:padding="@dimen/section_padding_horizontal">
>
>         <!-- Master toggle row -->
>         <LinearLayout
>             android:id="@+id/masterToggleRow"
>             android:layout_width="match_parent"
>             android:layout_height="wrap_content"
>             android:orientation="horizontal"
>             android:gravity="center_vertical"
>             android:focusable="true"
>             android:clickable="true"
>             android:background="?attr/selectableItemBackground"
>             android:padding="@dimen/section_padding_vertical">
>
>             <LinearLayout
>                 android:layout_width="0dp"
>                 android:layout_height="wrap_content"
>                 android:layout_weight="1"
>                 android:orientation="vertical">
>
>                 <TextView
>                     android:id="@+id/masterToggleLabel"
>                     android:layout_width="wrap_content"
>                     android:layout_height="wrap_content"
>                     android:text="@string/vr_settings_master_toggle_label"
>                     android:textAppearance="?attr/textAppearanceTitleMedium" />
>
>                 <TextView
>                     android:id="@+id/masterToggleSummary"
>                     android:layout_width="wrap_content"
>                     android:layout_height="wrap_content"
>                     android:text="@string/vr_settings_master_toggle_summary"
>                     android:textAppearance="?attr/textAppearanceBodySmall" />
>             </LinearLayout>
>
>             <com.google.android.material.materialswitch.MaterialSwitch
>                 android:id="@+id/masterToggleSwitch"
>                 android:layout_width="wrap_content"
>                 android:layout_height="wrap_content"
>                 android:focusable="true"
>                 android:clickable="true"
>                 tools:checked="true" />
>         </LinearLayout>
>
>         <View
>             android:layout_width="match_parent"
>             android:layout_height="1dp"
>             android:layout_marginTop="@dimen/section_padding_vertical"
>             android:layout_marginBottom="@dimen/section_padding_vertical"
>             android:background="?android:attr/listDivider" />
>
>         <TextView
>             android:id="@+id/placeholderSummary"
>             android:layout_width="match_parent"
>             android:layout_height="wrap_content"
>             android:text="@string/vr_settings_placeholder_summary"
>             android:textAppearance="?attr/textAppearanceBodySmall"
>             android:textColor="?android:attr/textColorSecondary" />
>     </LinearLayout>
> </ScrollView>
> ```
>
> If `@dimen/section_padding_horizontal` / `@dimen/section_padding_vertical` are missing from main resources, substitute with the closest existing dimens (`@dimen/settings_section_padding` is one candidate — verify via `Grep` in `app_v2/src/main/res/values/dimens.xml`). Use existing tokens; do not introduce new dimens in this step.

**Verification:**

- `Glob` — `app_v2/src/vr/res/layout/fragment_vr_settings.xml` exists.
- `Grep` — `@string/vr_settings_master_toggle_label` matches once.
- `Grep` — `MaterialSwitch` matches once.
- `Grep` — `android:focusable=\"true\"` matches at least 2 times (row + switch).

**Status:** `[ ]` not done

---

### Step 06.5 — Author `VrSettingsFragment`

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/ui/settings/vr/VrSettingsFragment.kt`
**Depends on:** Steps 06.2 / 06.3 / 06.4 and Phase 04

**Prompt for developer:**

> `@AndroidEntryPoint` Fragment binding the master toggle to `MasterTogglePreferences`. Reads current value in `viewLifecycleOwner.lifecycleScope.launch { repeatOnLifecycle(STARTED) { … } }`. Persists changes via `MasterTogglePreferences.setEnabled`. Row click toggles the switch (for D-pad / mouse parity).
>
> ```kotlin
> package com.sza.fastmediasorter.ui.settings.vr
>
> import android.os.Bundle
> import android.view.LayoutInflater
> import android.view.View
> import android.view.ViewGroup
> import androidx.fragment.app.Fragment
> import androidx.lifecycle.Lifecycle
> import androidx.lifecycle.lifecycleScope
> import androidx.lifecycle.repeatOnLifecycle
> import com.google.android.material.materialswitch.MaterialSwitch
> import com.sza.fastmediasorter.R
> import com.sza.fastmediasorter.core.xr.MasterTogglePreferences
> import dagger.hilt.android.AndroidEntryPoint
> import kotlinx.coroutines.flow.first
> import kotlinx.coroutines.flow.onEach
> import kotlinx.coroutines.launch
> import timber.log.Timber
> import javax.inject.Inject
>
> /**
>  * Stage 0 VR Settings tab body (S0245).
>  *
>  * Surfaces the single master toggle that gates VR feature visibility. Placeholder section
>  * below the toggle hints at future Stage 1+ controls but does nothing on Stage 0.
>  */
> @AndroidEntryPoint
> class VrSettingsFragment : Fragment() {
>
>     @Inject lateinit var preferences: MasterTogglePreferences
>
>     override fun onCreateView(
>         inflater: LayoutInflater,
>         container: ViewGroup?,
>         savedInstanceState: Bundle?,
>     ): View = inflater.inflate(R.layout.fragment_vr_settings, container, false)
>
>     override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
>         super.onViewCreated(view, savedInstanceState)
>         val switch = view.findViewById<MaterialSwitch>(R.id.masterToggleSwitch)
>         val row = view.findViewById<View>(R.id.masterToggleRow)
>
>         row.setOnClickListener { switch.toggle() }
>
>         switch.setOnCheckedChangeListener { _, isChecked ->
>             viewLifecycleOwner.lifecycleScope.launch {
>                 preferences.setEnabled(isChecked)
>                 Timber.d("VrSettingsFragment: master toggle changed -> $isChecked")
>             }
>         }
>
>         viewLifecycleOwner.lifecycleScope.launch {
>             viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
>                 preferences.enabled
>                     .onEach { switch.isChecked = it }
>                     .collect()
>             }
>         }
>     }
> }
> ```
>
> The `onEach { … }.collect()` form is intentional — `MaterialSwitch.setOnCheckedChangeListener` is already wired, so the flow only updates the UI when the value changes from the outside (default value on first read, or programmatic change). The listener guards against feedback loops because setting `isChecked` to the same value is a no-op.
>
> Strings pass COMMUNICATION_POLICY §6 checklist (no new user-visible strings here — code references existing string keys).

**Verification:**

- `Glob` — `VrSettingsFragment.kt` exists at the path above.
- `Grep` — `class VrSettingsFragment` matches once.
- `Grep` — `@AndroidEntryPoint` matches once in this file.
- `Grep` — `MasterTogglePreferences` matches at least twice (import + injection).
- No `Log.d(` in the file — only `Timber.d(`.

**Status:** `[ ]` not done

---

### Step 06.6 — Author `VrSettingsTabExtension`

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/ui/settings/vr/VrSettingsTabExtension.kt`
**Depends on:** Step 06.5

**Prompt for developer:**

> Implements `SettingsTabExtension`. `isVisible` is evaluated synchronously by sampling the latest `XrDetectionState`; for Stage 0 the simpler rule is: visible iff `XrEnvironmentDetector.detect() != NONE`. This avoids `runBlocking` on the main thread for the first adapter render. The master-toggle state controls VR feature visibility everywhere else — the tab itself can stay visible so the user can re-enable VR from Settings.
>
> ```kotlin
> package com.sza.fastmediasorter.ui.settings.vr
>
> import androidx.fragment.app.Fragment
> import com.sza.fastmediasorter.R
> import com.sza.fastmediasorter.core.xr.XrEnvironment
> import com.sza.fastmediasorter.core.xr.XrEnvironmentDetector
> import com.sza.fastmediasorter.ui.settings.SettingsTabExtension
> import javax.inject.Inject
> import javax.inject.Singleton
>
> /**
>  * Adds the 5th "VR" Settings tab on flavors that ship the real XR contracts (vr / noLegal).
>  * Visibility is gated by [XrEnvironmentDetector] — the tab appears only on XR-capable
>  * devices (Quest / Android XR) regardless of the user's master-toggle state.
>  */
> @Singleton
> class VrSettingsTabExtension @Inject constructor(
>     private val detector: XrEnvironmentDetector,
> ) : SettingsTabExtension {
>     override val order: Int = 100  // After the static 0..3 tabs.
>     override val tabTitleResId: Int = R.string.settings_tab_vr
>     override val isVisible: Boolean
>         get() = detector.detect() != XrEnvironment.NONE
>
>     override fun createFragment(): Fragment = VrSettingsFragment()
> }
> ```

**Verification:**

- `Glob` — `VrSettingsTabExtension.kt` exists.
- `Grep` — `class VrSettingsTabExtension` matches once.
- `Grep` — `detector.detect\(\) != XrEnvironment.NONE` matches once.

**Status:** `[ ]` not done

---

### Step 06.7 — Bind `VrSettingsTabExtension` `@IntoSet`

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/ui/settings/vr/di/VrSettingsExtensionModule.kt`
**Depends on:** Step 06.6

**Prompt for developer:**

> Hilt module that contributes `VrSettingsTabExtension` to the `Set<SettingsTabExtension>` multibinding. Installed in `SingletonComponent`.
>
> ```kotlin
> package com.sza.fastmediasorter.ui.settings.vr.di
>
> import com.sza.fastmediasorter.ui.settings.SettingsTabExtension
> import com.sza.fastmediasorter.ui.settings.vr.VrSettingsTabExtension
> import dagger.Binds
> import dagger.Module
> import dagger.hilt.InstallIn
> import dagger.hilt.components.SingletonComponent
> import dagger.multibindings.IntoSet
>
> /**
>  * Adds the VR Settings tab to the `Set<SettingsTabExtension>` multibinding. Mounted only
>  * in the `vr` source set — `noLegal` picks it up via the source-set inheritance configured
>  * in `app_v2/build.gradle.kts` (S0245 Phase 01).
>  */
> @Module
> @InstallIn(SingletonComponent::class)
> abstract class VrSettingsExtensionModule {
>     @Binds
>     @IntoSet
>     abstract fun bindVrSettingsTabExtension(
>         impl: VrSettingsTabExtension
>     ): SettingsTabExtension
> }
> ```

**Verification:**

- `Glob` — `VrSettingsExtensionModule.kt` exists.
- `Grep` — `@IntoSet` matches once.
- `Grep` — `abstract class VrSettingsExtensionModule` matches once.
- Build `assembleVrDebug` passes — `Set<SettingsTabExtension>` resolves with one entry; tab count becomes 5 when device is XR-capable.
- Build `assembleNoLegalDebug` passes — same module is mounted via source-set inheritance.
- Build `assembleStandardDebug` passes — module is not on classpath; tab count remains 4.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 06.*` above is `[x] done`.
- [ ] Build `assembleVrDebug` passes; APK contains `VrSettingsFragment.class` (verifiable via `unzip -l … | grep VrSettingsFragment`).
- [ ] Build `assembleStandardDebug` passes; APK does NOT contain `VrSettingsFragment.class`.
- [ ] Build `assembleNoLegalDebug` passes; APK contains `VrSettingsFragment.class` (inheritance from `src/vr/java/`).
- [ ] `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "vr_settings_"` exits 0.
- [ ] `Grep` for `TODO(phase-06)` returns zero hits.
- [ ] Dev log entry added for every new file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated and the new VR-flavor classes are catalogued with `-NoFlavors "standard,lite,photos,legacy"` (Phase 07 sub-step).

---

## Handoff Notes to Next Phase

Functional Stage 0 is complete: VR tab visible on Quest 3 / Android XR, master toggle persists across launches, phone APKs unchanged. Phase 07 is doc / catalog / Timber-tag hygiene.

---

## Rollback Plan

Delete all new files in `src/vr/java/com/sza/fastmediasorter/ui/settings/vr/` and the three `strings.xml` mirrors. Phase 05 leaves `SettingsPagerAdapter` accepting an empty set — phone flavors continue to work, vr flavor temporarily reverts to 4 tabs.
