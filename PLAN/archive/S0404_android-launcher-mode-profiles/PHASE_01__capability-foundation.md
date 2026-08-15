# Phase 01 - Capability Foundation

**Strategic spec:** [`../S0404_android-launcher-mode-profiles.md`](../S0404_android-launcher-mode-profiles.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, 04, 05, 08
**Steps done:** 6 / 6
**Started:** 2026-07-17
**Completed:** 2026-07-17

---

## Objective

Introduce the launcher-mode capability seam (contract in `src/main`, enabled/disabled source sets, Hilt bindings), a disabled-by-default `LauncherHomeActivity` skeleton with the HOME intent-filter, the role manager, and the mode icon. No desktop UI, no persistence yet.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch; `scripts/utils/lock-status.ps1 -Name Build` shows no live build.
- [ ] `scripts/utils/enter-code-lock.ps1 -Reason "S0404 phase 01"` acquired before the first source edit.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/build.gradle.kts` | Modified (backup to `temp/S0404/` first - >500 LOC) | +30 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/launcher/LauncherModeContract.kt` | New | ≤ 40 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/launcher/LauncherRoleManager.kt` | New | ≤ 150 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/launcher/LauncherModeContractImpl.kt` | New | ≤ 40 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/di/LauncherModeModule.kt` | New | ≤ 30 |
| `app_v2/src/launcherDisabled/java/com/sza/fastmediasorter/launcher/LauncherModeContractImpl.kt` | New | ≤ 25 |
| `app_v2/src/launcherDisabled/java/com/sza/fastmediasorter/di/LauncherModeModule.kt` | New | ≤ 30 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeActivity.kt` | New (skeleton) | ≤ 80 |
| `app_v2/src/launcherEnabled/res/layout/activity_launcher_home.xml` | New (skeleton) | ≤ 40 |
| `app_v2/src/launcherEnabled/res/layout-land/activity_launcher_home.xml` | New (skeleton) | ≤ 40 |
| `app_v2/src/launcherEnabled/AndroidManifest.xml` | New | ≤ 30 |
| `app_v2/src/main/res/drawable/ic_launcher_mode.xml` | New | ≤ 30 |

> Flavor placement per `dev/FLAVOR_DEVELOPMENT_RULES.md`: contract in `src/main`, real impl in `src/launcherEnabled`, no-op in `src/launcherDisabled`, Hilt module duplicated per source set under the SAME package+file name so exactly one compiles per variant (existing precedent: `src/vr/.../di/` vs `src/vrStub/.../di/`). No `BuildConfig.IS_*` anywhere in `src/main`.

---

## Steps

### Step 01.1 - Mount launcherEnabled / launcherDisabled source sets

**Files:** `app_v2/build.gradle.kts`
**Depends on:** - start of phase

**Prompt for developer:**

> Back up `app_v2/build.gradle.kts` to `temp/S0404/` (timestamped) first. In the `sourceSets { }` block (~line 559), mirror the existing `src/screenCapture` pattern: add `kotlin.directories.add("src/launcherEnabled/java")` and `res.directories.add("src/launcherEnabled/res")` to `getByName("standard")` and `getByName("noLegal")`; add `kotlin.directories.add("src/launcherDisabled/java")` to `getByName("lite")`, `getByName("photos")`, `getByName("legacy")`, and `getByName("vr")`. In the per-variant loop that already calls `variant.sources.manifests.addStaticManifestFile(...)` (~line 990, see the `castFlavors` example), add: `val launcherFlavors = setOf("standard", "noLegal")` and inject `addStaticManifestFile("src/launcherEnabled/AndroidManifest.xml")` for those flavors. Do NOT use `manifest.srcFile` (it replaces instead of adds - see the S0183 comment at ~line 993). Add a `// S0404:` comment on each block explaining the mount.

**Verification:**

- `Grep` - `src/launcherEnabled/java` appears exactly 2 times in `app_v2/build.gradle.kts`; `src/launcherDisabled/java` exactly 4 times.
- `Grep` - `addStaticManifestFile("src/launcherEnabled/AndroidManifest.xml")` present.

**Status:** `[x]` done

**Step Log:**

- 2026-07-17 - Verification 3/3 PASS (launcherEnabled 2, launcherDisabled 4, manifest injection at line 1042). Files: app_v2/build.gradle.kts (+18 LOC). Backup: temp/S0404/build.gradle.kts.20260717_103012.bak. Dev log batched at phase close.

---

### Step 01.2 - Contract interface + both implementations + Hilt modules

**Files:** `domain/launcher/LauncherModeContract.kt` (main), `launcher/LauncherModeContractImpl.kt` + `di/LauncherModeModule.kt` (in BOTH `src/launcherEnabled` and `src/launcherDisabled`)
**Depends on:** Step 01.1

**Prompt for developer:**

> In `src/main/.../domain/launcher/LauncherModeContract.kt` declare:
> ```kotlin
> interface LauncherModeContract {
>     /** True when this build compiles the launcher-mode surface (standard / noLegal). */
>     val isAvailableInBuild: Boolean
>     /** ComponentName of the HOME-filter activity, or null when unavailable in this build. */
>     fun homeComponent(context: Context): ComponentName?
> }
> ```
> In `src/launcherEnabled/.../launcher/LauncherModeContractImpl.kt`: `isAvailableInBuild = true`, `homeComponent` returns `ComponentName(context, LauncherHomeActivity::class.java)`. In `src/launcherDisabled/.../launcher/LauncherModeContractImpl.kt`: `false` / `null`. In each source set create `di/LauncherModeModule.kt` - `@Module @InstallIn(SingletonComponent::class)` with a `@Provides @Singleton fun provideLauncherModeContract(): LauncherModeContract = LauncherModeContractImpl()`. Identical package `com.sza.fastmediasorter.di` and file name in both source sets.

**Verification:**

- `Grep` - `interface LauncherModeContract` matches once (src/main).
- `Grep` - `class LauncherModeContractImpl` matches once in `src/launcherEnabled` and once in `src/launcherDisabled`.
- `Grep` - `provideLauncherModeContract` matches once per source set.

**Status:** `[x]` done

**Step Log:**

- 2026-07-17 - Verification 3/3 PASS. Files: domain/launcher/LauncherModeContract.kt (new, 17 LOC), launcherEnabled + launcherDisabled LauncherModeContractImpl.kt / di/LauncherModeModule.kt (4 new files). Hilt scope explicit: @Provides @Singleton in SingletonComponent, one module per source set.

---

### Step 01.3 - LauncherHomeActivity skeleton + layouts

**Files:** `src/launcherEnabled/.../ui/launcher/LauncherHomeActivity.kt`, `src/launcherEnabled/res/layout/activity_launcher_home.xml`, `src/launcherEnabled/res/layout-land/activity_launcher_home.xml`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create `LauncherHomeActivity` as an `@AndroidEntryPoint` activity extending the same `BaseActivity<ActivityLauncherHomeBinding>` pattern used by `ui/main/MainActivity.kt` (view binding, `setupViews()` posted by BaseActivity - do restore work in `onResumeWithViews`, not `onCreate`). Skeleton behavior only: inflate the layout, suppress Back (a HOME surface never finishes on Back - override the back callback to a no-op via `onBackPressedDispatcher.addCallback`), keep UI inside `systemBars` + `displayCutout` insets (Rule 17). Layout: a root `ConstraintLayout` with a full-size empty `RecyclerView` `@+id/launcherGrid` and a bottom 56dp horizontal `LinearLayout` `@+id/launcherTaskbar` placeholder. Both orientation files must carry the same view ids (Rule 11); landscape may differ only in paddings. No hex colors - `?attr`/`@color` only (Rule 19).

**Verification:**

- `Grep` - `class LauncherHomeActivity` matches once; `onBackPressedDispatcher.addCallback` present in it.
- `Grep` - `launcherGrid` present in BOTH `layout/activity_launcher_home.xml` and `layout-land/activity_launcher_home.xml`.
- `Grep` - `="#` returns zero hits in both layout files.

**Status:** `[x]` done

**Step Log:**

- 2026-07-17 - Verification 3/3 PASS (class 1x + back callback; launcherGrid in both orientations; 0 hex colors). Files: ui/launcher/LauncherHomeActivity.kt (new, 45 LOC), res/layout + res/layout-land activity_launcher_home.xml (new). Note for Phase 04: BaseActivity already owns `onConfigurationChanged` and posts to the `onLayoutConfigurationChanged(newConfig)` hook - Phase 04 must override THAT, not `onConfigurationChanged` (its Grep predicate needs adjusting to `onLayoutConfigurationChanged`).

---

### Step 01.4 - HOME manifest declaration, disabled by default

**Files:** `src/launcherEnabled/AndroidManifest.xml`
**Depends on:** Step 01.3

**Prompt for developer:**

> Create the overlay manifest declaring `LauncherHomeActivity` with: `android:exported="true"`, `android:enabled="false"` (ADR-2: component off until the user enables the mode - strategic goal 9), `android:launchMode="singleTask"`, `android:stateNotNeeded="true"`, `android:excludeFromRecents="true"`, `android:configChanges="orientation|screenSize|screenLayout|keyboardHidden"` (recompute layout in `onConfigurationChanged` later - no recreate, precedent S0918 streams pattern), and an intent-filter with `action MAIN` + `category HOME` + `category DEFAULT` (research 01). No new permissions, no `<queries>` (already in main manifest per S0623).

**Verification:**

- `Grep` - `android.intent.category.HOME` matches once in `src/launcherEnabled/AndroidManifest.xml`.
- `Grep` - `android:enabled="false"` present in the same `<activity>` element.
- `Grep` - `QUERY_ALL_PACKAGES` returns zero hits across `app_v2/src/**/AndroidManifest.xml`.

**Status:** `[x]` done

**Step Log:**

- 2026-07-17 - Verification 3/3 PASS (HOME category 1x, enabled="false" line 18, QUERY_ALL_PACKAGES 0 files). Files: src/launcherEnabled/AndroidManifest.xml (new, 31 LOC).

---

### Step 01.5 - LauncherRoleManager (enable / disable / role request)

**Files:** `src/main/.../core/launcher/LauncherRoleManager.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Create `@Singleton class LauncherRoleManager @Inject constructor(@ApplicationContext context: Context, contract: LauncherModeContract)` with:
> - `fun isModeEnabled(): Boolean` - component state of `contract.homeComponent()` == `COMPONENT_ENABLED_STATE_ENABLED` (false when component null).
> - `fun isHomeRoleHeld(): Boolean` - API 29+: `RoleManager.isRoleHeld(RoleManager.ROLE_HOME)`; below 29: resolve `Intent(ACTION_MAIN).addCategory(CATEGORY_HOME)` default and compare package.
> - `fun enableMode(activity: Activity)` - `packageManager.setComponentEnabledSetting(component, COMPONENT_ENABLED_STATE_ENABLED, DONT_KILL_APP)`; then API 29+: start `RoleManager.createRequestRoleIntent(ROLE_HOME)` via `activity.startActivityForResult`-free launcher (plain `startActivity` is not valid for role intents - use `ActivityResultLauncher` passed in by the caller, signature `enableMode(activity: Activity, roleLauncher: ActivityResultLauncher<Intent>?)`); below 29 or when the role intent is unavailable: start `Intent(Settings.ACTION_HOME_SETTINGS)` guarded by `resolveActivity` (fallback: general `Settings.ACTION_SETTINGS`), per research 01/12.
> - `fun disableMode()` - `setComponentEnabledSetting(component, COMPONENT_ENABLED_STATE_DISABLED, DONT_KILL_APP)` (the system reverts to the previous launcher - risk 2 mitigation).
> - `fun openHomeChooser(activity: Activity)` - opens `ACTION_HOME_SETTINGS` with the same resolve fallback ("stop being the launcher" path, research 01 "Снятие роли").
> All logging Timber, level `i` for expected fallbacks (no `Sxxxx` prefixes). Use `PackageManagerCompat` helpers from `util/PackageManagerCompat.kt` for any PM query overload (Rule 21). Keep every log line ≤120 chars (detekt).

**Verification:**

- `Grep` - `class LauncherRoleManager` matches once; `ACTION_HOME_SETTINGS`, `setComponentEnabledSetting`, `ROLE_HOME` all present in it.
- `Grep` - `Log\.d\(` zero hits in the new file.

**Status:** `[x]` done

**Step Log:**

- 2026-07-17 - Verification 2/2 PASS (class 1x; ACTION_HOME_SETTINGS/setComponentEnabledSetting/ROLE_HOME present; Log.d 0). Detekt pre-check: 0 lines >120 chars. Files: core/launcher/LauncherRoleManager.kt (new, 113 LOC). Uses resolveActivityCompat (Rule 21); role dialog gated on isRoleAvailable with system-settings fallback.

---

### Step 01.6 - Mode icon + compile gate

**Files:** `app_v2/src/main/res/drawable/ic_launcher_mode.xml`
**Depends on:** Step 01.3

**Prompt for developer:**

> Add vector drawable `ic_launcher_mode.xml` (24dp, `android:tint="?attr/colorControlNormal"`): a monitor/desktop-grid glyph - reuse the material "dashboard"/"grid_view" geometry style consistent with existing `ic_*` assets (owner request 2026-07-17: a dedicated launcher-mode icon for the settings group, Welcome toggle and menu entries). Then run the phase compile gate: `.\a.ps1 fk` (standard) and `.\a.ps1 fkn` (noLegal) must pass, plus `.\gradlew.bat :app_v2:compileLiteDebugKotlin` (proves the launcherDisabled no-op path) - acquire `temp/BUILD.LOCK` via the a.ps1 wrappers, never run two gradle invocations at once (Rule 23).

**Verification:**

- `Glob` - `app_v2/src/main/res/drawable/ic_launcher_mode.xml` exists.
- `.\a.ps1 fk` → BUILD SUCCESSFUL; `.\a.ps1 fkn` → BUILD SUCCESSFUL; `compileLiteDebugKotlin` → BUILD SUCCESSFUL (record exit codes).

**Status:** `[x]` done

**Step Log:**

- 2026-07-17 - Verification 2/2 PASS. Icon: res/drawable/ic_launcher_mode.xml (monitor + 2x2 desktop grid, `?attr/colorControlNormal` tint). Builds: `.\a.ps1 fk` expected BUILD SUCCESSFUL | actual BUILD SUCCESSFUL (43s); `check-standard-fast.ps1 -Flavor Lite` expected BUILD SUCCESSFUL | actual BUILD SUCCESSFUL (2m25s, proves the launcherDisabled no-op path); `.\a.ps1 fkn` expected BUILD SUCCESSFUL | actual BUILD SUCCESSFUL (54s).
- 2026-07-17 - Tooling fix (CLAUDE.md Rule 13): `scripts/builders/check-standard-fast.ps1` accepted only Standard/NoLegal, so the capability-gated flavors had no lock-respecting fast check. Extended `-Flavor` ValidateSet with Lite/Photos/Legacy and flipped the Chaquopy/configuration-cache branch from `-eq "Standard"` to `-ne "NoLegal"` (only noLegal bundles Python). Verified by the Lite run above.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles on standard + noLegal + lite (commands in Step 01.6).
- [ ] **DEFERRED-DEVICE** - Installing a debug build and pressing Home does NOT offer FastMediaSorter (component disabled) - quick `adb.ps1` sanity check. No device online on 2026-07-17 (`adb.ps1 devices` → exit 2). The property is statically proven by `android:enabled="false"` (Step 01.4 verification) and is re-checked in the ticket's BlockNeedUserTest device pass (Phase 10 step 10.4). Not a code blocker.
- [x] Dev log entries added (batch via `scripts/post-change.ps1 ... -ScopeToFile` or `close-and-log.ps1 -DevLogs`); `scripts/utils/exit-code-lock.ps1` released (auto-released by the post-change closure; `lock-status.ps1 -Name Code` → absent).
- [x] `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` run once for the phase (2179 records).

---

## Handoff Notes to Next Phase

- Contract seam ready: inject `LauncherModeContract` anywhere in `src/main`; never reference `LauncherHomeActivity` outside `src/launcherEnabled`.
- For on-device testing before Phase 08 exists, enable the component manually: `scripts/devtest/adb.ps1 shell -Cmd "pm enable com.sza.fastmediasorter.debug/com.sza.fastmediasorter.ui.launcher.LauncherHomeActivity"` then press Home.
- `LauncherRoleManager` is UI-agnostic; Phase 08 wires it to Settings and Welcome.

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing surface changed (component ships disabled).
