# Phase 05 — Settings tab extension contract + `SettingsPagerAdapter` refactor

**Strategic spec:** [`../S0245_vr-settings-scaffold-stage0.md`](../S0245_vr-settings-scaffold-stage0.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 02
**Blocks:** Phase 06
**Steps done:** 0 / 4
**Started:** —
**Completed:** —

---

## Objective

Introduce a flavor-agnostic plugin point for extra Settings tabs (`SettingsTabExtension`), refactor `SettingsPagerAdapter` to consume an injected `Set<SettingsTabExtension>` (initially empty for phone flavors, populated by `vr` flavor in Phase 06), and update `SettingsActivity` to pass the set through and resolve tab titles dynamically. No `BuildConfig.SUPPORT_VR_PLAYER` branch in `src/main/` — flavor isolation per CLAUDE.md Rule 15.

---

## Prerequisites

- [ ] Phase 02 ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsTabExtension.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/di/SettingsTabExtensionModule.kt` | New | ≤ 40 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsPagerAdapter.kt` | Modified | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsActivity.kt` | Modified | ≤ 500 |

> `SettingsActivity.kt` is currently 433 LOC; this phase adds ~10 LOC → projected ~443 LOC, still under the 500 LOC backup threshold. No backup required.

---

## Steps

### Step 05.1 — Author `SettingsTabExtension` interface

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsTabExtension.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Plugin contract for adding a new Settings tab from a flavor source set. Exposes the string resource for the tab title, a creator lambda for the Fragment, and a synchronous `isVisible` predicate. Visibility is evaluated at adapter construction time — the adapter caches the result. For dynamic visibility (e.g. master toggle changes), Phase 06 instructs the implementation to read the current `XrDetectionState` value via `runBlocking { facade.state().first() }` in `isVisible`. This is acceptable in Settings because the adapter is rebuilt when the user re-enters the screen.
>
> ```kotlin
> package com.sza.fastmediasorter.ui.settings
>
> import androidx.annotation.StringRes
> import androidx.fragment.app.Fragment
>
> /**
>  * Plugin contract for flavor-supplied Settings tabs (S0245).
>  *
>  * Implementations live in flavor source sets (e.g. `src/vr/java/`) and bind via
>  * `@IntoSet` into the `Set<SettingsTabExtension>` multibinding. `SettingsPagerAdapter`
>  * injects the set, filters by [isVisible], sorts by [order], and appends to the static
>  * 4 main tabs.
>  *
>  * Phone-only flavors (standard, lite, photos, legacy) provide no implementations — the
>  * multibinding stays empty, so the static 4 tabs remain.
>  */
> interface SettingsTabExtension {
>     /** Display order. Lower values appear first. Existing static tabs occupy 0..3. */
>     val order: Int
>
>     /** Title resource for the TabLayout entry. */
>     @get:StringRes
>     val tabTitleResId: Int
>
>     /** `true` if the tab should be shown at adapter construction time. */
>     val isVisible: Boolean
>
>     /** Fresh Fragment instance for this tab. Called by `FragmentStateAdapter`. */
>     fun createFragment(): Fragment
> }
> ```

**Verification:**

- `Glob` — `SettingsTabExtension.kt` exists.
- `Grep` — `interface SettingsTabExtension` matches once.
- `Grep` — `fun createFragment\(\): Fragment` matches once.

**Status:** `[ ]` not done

---

### Step 05.2 — Declare `Set<SettingsTabExtension>` multibinding in src/main

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/di/SettingsTabExtensionModule.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> Declare an empty multibinding so the `Set<SettingsTabExtension>` is always resolvable, even on flavors that contribute no entries. Use Hilt's `@Multibinds` on an abstract method.
>
> ```kotlin
> package com.sza.fastmediasorter.ui.settings.di
>
> import com.sza.fastmediasorter.ui.settings.SettingsTabExtension
> import dagger.Module
> import dagger.hilt.InstallIn
> import dagger.hilt.components.SingletonComponent
> import dagger.multibindings.Multibinds
>
> /**
>  * Declares the `Set<SettingsTabExtension>` multibinding (S0245).
>  *
>  * Flavor modules add entries via `@Binds @IntoSet`. Phone-only flavors contribute zero
>  * entries, so the set resolves as empty — `SettingsPagerAdapter` shows only the static
>  * 4 tabs.
>  */
> @Module
> @InstallIn(SingletonComponent::class)
> abstract class SettingsTabExtensionModule {
>     @Multibinds
>     abstract fun settingsTabExtensions(): Set<@JvmSuppressWildcards SettingsTabExtension>
> }
> ```

**Verification:**

- `Glob` — `SettingsTabExtensionModule.kt` exists.
- `Grep` — `@Multibinds` matches once.
- `Grep` — `Set<@JvmSuppressWildcards SettingsTabExtension>` matches once.

**Status:** `[ ]` not done

---

### Step 05.3 — Refactor `SettingsPagerAdapter` to consume injected extensions

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsPagerAdapter.kt`
**Depends on:** Steps 05.1 / 05.2

**Prompt for developer:**

> Modify the adapter to accept a `Set<SettingsTabExtension>` in its constructor. Internally combine the 4 static fragments with the filtered/sorted extensions into one list. Expose a `getTabTitleResId(position: Int): Int` helper for `SettingsActivity` to use in the `TabLayoutMediator`. Replace the existing static `getItemCount() = 4` / `createFragment(position)` logic.
>
> ```kotlin
> package com.sza.fastmediasorter.ui.settings
>
> import androidx.annotation.StringRes
> import androidx.fragment.app.Fragment
> import androidx.fragment.app.FragmentActivity
> import androidx.viewpager2.adapter.FragmentStateAdapter
> import com.sza.fastmediasorter.R
> import com.sza.fastmediasorter.ui.settings.fragments.GeneralSettingsFragment
> import com.sza.fastmediasorter.ui.settings.fragments.MediaSettingsFragment
> import com.sza.fastmediasorter.ui.settings.fragments.OperationsSettingsFragment
> import com.sza.fastmediasorter.ui.settings.fragments.PlaybackSettingsFragment
>
> /**
>  * Pager adapter for [SettingsActivity] (S0245 refactor).
>  *
>  * Combines the static 4 tabs (General / Media / Playback / Operations) with any
>  * flavor-supplied [SettingsTabExtension] entries that report `isVisible == true`. Phone
>  * flavors contribute no extensions, so behaviour is unchanged for them.
>  */
> class SettingsPagerAdapter(
>     activity: FragmentActivity,
>     extensions: Set<SettingsTabExtension>,
> ) : FragmentStateAdapter(activity) {
>
>     private val entries: List<TabEntry> = buildList {
>         add(TabEntry(R.string.settings_tab_general, ::GeneralSettingsFragment))
>         add(TabEntry(R.string.settings_tab_media, ::MediaSettingsFragment))
>         add(TabEntry(R.string.settings_tab_playback, ::PlaybackSettingsFragment))
>         add(TabEntry(R.string.settings_tab_operations, ::OperationsSettingsFragment))
>         extensions
>             .filter { it.isVisible }
>             .sortedBy { it.order }
>             .forEach { add(TabEntry(it.tabTitleResId, it::createFragment)) }
>     }
>
>     override fun getItemCount(): Int = entries.size
>
>     override fun createFragment(position: Int): Fragment = entries[position].fragmentCreator()
>
>     @StringRes
>     fun getTabTitleResId(position: Int): Int = entries[position].titleResId
>
>     private data class TabEntry(
>         @StringRes val titleResId: Int,
>         val fragmentCreator: () -> Fragment,
>     )
> }
> ```

**Verification:**

- `Grep` — `class SettingsPagerAdapter\(` matches once.
- `Grep` — `extensions: Set<SettingsTabExtension>` matches once.
- `Grep` — `fun getItemCount\(\): Int = entries.size` matches once.
- `Grep` — Old static signature `getItemCount\(\): Int = 4` returns zero hits.

**Status:** `[ ]` not done

---

### Step 05.4 — Wire `SettingsActivity` to inject the extension set and rebuild tab titles

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsActivity.kt`
**Depends on:** Step 05.3

**Prompt for developer:**

> 1. Add `@Inject lateinit var settingsTabExtensions: Set<@JvmSuppressWildcards SettingsTabExtension>` near the existing `viewModel` declaration.
> 2. Replace `val adapter = SettingsPagerAdapter(this)` with `val adapter = SettingsPagerAdapter(this, settingsTabExtensions)`.
> 3. Replace the hard-coded `TabLayoutMediator` `when (position)` block with `tab.text = getString(adapter.getTabTitleResId(position))`. Drop the `else -> ""` branch — out-of-range positions cannot occur because TabLayoutMediator uses adapter.itemCount.
> 4. Strings tab title check (COMMUNICATION_POLICY §6 tone checklist): titles for existing 4 tabs are unchanged. The 5th VR title is supplied by Phase 06.
> 5. Strings pass COMMUNICATION_POLICY §6 checklist (no new user-visible strings in this step — only structural).
>
> Keep all other logic in `setupViews()` untouched, including the `EXTRA_INITIAL_TAB` handling (it already uses `adapter.itemCount` for bounds-checking, which automatically accommodates the new dynamic count).

**Verification:**

- `Grep` — `@Inject lateinit var settingsTabExtensions: Set<@JvmSuppressWildcards SettingsTabExtension>` matches once.
- `Grep` — `SettingsPagerAdapter\(this, settingsTabExtensions\)` matches once.
- `Grep` — `tab.text = getString\(adapter.getTabTitleResId\(position\)\)` matches once.
- `Grep` — The old `when \(position\) \{` block targeting `settings_tab_general` is absent.
- Strings pass COMMUNICATION_POLICY §6 checklist (no new strings in this step).
- Build `assembleStandardDebug` passes — `Set<SettingsTabExtension>` resolves as empty.
- Build `assembleVrDebug` passes (with Phase 06 extension binding pending — for this phase, verify only that the empty-set path compiles).

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] `assembleStandardDebug` passes; phone tab count remains 4.
- [ ] `assembleVrDebug` passes; tab count is still 4 until Phase 06 binds the VR extension.
- [ ] `Grep` for `TODO(phase-05)` returns zero hits.
- [ ] Dev log entry added for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new interfaces in main).

---

## Handoff Notes to Next Phase

`SettingsPagerAdapter` now accepts dynamic extras. Phase 06 contributes the `vr` flavor's `VrSettingsTabExtension` and the actual `VrSettingsFragment`.

---

## Rollback Plan

Revert the two `.kt` edits and delete the two new files. Phone behaviour returns to the original 4-tab static layout.
