# Phase 04 - Settings pin picker

**Strategic spec:** [`../S0348_home-widget-icon-refresh.md`](../S0348_home-widget-icon-refresh.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02, Phase 03
**Blocks:** Phase 05
**Steps done:** 6 / 6
**Started:** 2026-06-04
**Completed:** 2026-06-04

---

## Objective

Add a "Add widget to home screen.." action in General settings that opens an in-app picker of currently-available widgets (`HomeWidgetCatalog.availableEntries()`) and pins the chosen one via the system `requestPinAppWidget` flow, with an explicit fallback when pinning is unsupported. Extract the pinning primitives shared with `ResourceEditorFragment` into a reusable `HomeWidgetPinner`.

> **UI placement assumption (delegated by strategic §3.3):** the action lives in **General settings** (the existing hub that already hosts backup/restore, permissions, logs), rendered as a standard settings button row, delegated to a new `GeneralSettings*Helper`. The picker is a `MaterialAlertDialog` list. Documented here so `/spec-dev` does not re-decide.

---

## Prerequisites

- [ ] Phase 03 is ✅ Done - `HomeWidgetCatalog` exists and is injectable.
- [ ] Phase 02 is ✅ Done - Camera-OCR is a `1x1` provider.
- [ ] Read `docs/COMMUNICATION_POLICY.md` §2 + §6 before authoring the new strings.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/widget/registry/HomeWidgetPinner.kt` | New | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsWidgetHelper.kt` | New | ≤ 180 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/GeneralSettingsFragment.kt` | Modified | ≤ 900 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/resourceeditor/ResourceEditorFragment.kt` | Modified | ≤ 1400 |
| `app_v2/src/main/res/layout/fragment_settings_general.xml` | Modified | +1 row |
| `app_v2/src/main/res/layout-land/fragment_settings_general.xml` | Modified | +1 row |
| `app_v2/src/main/res/values/strings_widget.xml` | Modified | +2 keys |
| `app_v2/src/main/res/values-ru/strings_widget.xml` | Modified | +2 keys |
| `app_v2/src/main/res/values-uk/strings_widget.xml` | Modified | +2 keys |

> `ResourceEditorFragment.kt` is already large - confirm post-edit line count stays < 1500. If a delegation edit pushes it over, extract its pin logic fully into `HomeWidgetPinner` rather than growing the fragment. Create a timestamped backup in `temp/` before editing (file > 500 LOC).
> Landscape parity: `fragment_settings_general.xml` HAS a `layout-land` counterpart - the button row MUST be added to both (Rule 12).

---

## Steps

### Step 04.1 - Extract `HomeWidgetPinner`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/widget/registry/HomeWidgetPinner.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `@Singleton class HomeWidgetPinner @Inject constructor(@ApplicationContext private val context: Context)` encapsulating the pin primitives currently inline in `ResourceEditorFragment.pinWidgetForCurrentResource()`:
> - `fun isSupported(): Boolean` = `Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported`.
> - `fun isKeyguardLocked(): Boolean` via `KeyguardManager`.
> - `fun requestPin(component: ComponentName, successCallback: PendingIntent?): Boolean` - calls `AppWidgetManager.getInstance(context).requestPinAppWidget(component, null, successCallback)` and returns its result; returns `false` early if `!isSupported()`.
> Keep the `Build.VERSION.SDK_INT >= O` guard because `legacy` has `minSdk 23` (strategic §6.1). Timber only - no `Log.d`.

**Verification:**

- `Glob` - `HomeWidgetPinner.kt` exists.
- `Grep -n "class HomeWidgetPinner"` matches once.
- `Grep -n "isRequestPinAppWidgetSupported"` and `Grep -n "requestPinAppWidget"` present; `Grep -n "Build.VERSION_CODES.O"` present (inlined twice for legacy lint-safety).
- `Grep -n "Log\.d\("` returns zero hits.
- Build: `.\a.ps1 dq` compiles.

**Status:** `[x]` done

**Step Log:**

- 2026-06-04 - Verification PASS (structural): HomeWidgetPinner.kt created; `class HomeWidgetPinner`, `isRequestPinAppWidgetSupported`, `requestPinAppWidget`, inline `Build.VERSION_CODES.O` guard in both methods; `Log.d` 0. Build at phase gate. File: widget/registry/HomeWidgetPinner.kt.

---

### Step 04.2 - Route `ResourceEditorFragment` through `HomeWidgetPinner`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/resourceeditor/ResourceEditorFragment.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Inject `HomeWidgetPinner` into `ResourceEditorFragment` and make `pinWidgetForCurrentResource()` delegate the SDK/support check to `pinner.isSupported()` (toast `R.string.widget_pin_not_supported` on false), the keyguard check to `pinner.isKeyguardLocked()` (keep the existing unlock dialog), and the actual pin call to `pinner.requestPin(...)` with the existing `ResourceLaunchWidgetProvider` component and `ACTION_WIDGET_PINNED` success `PendingIntent`. Do not change observable behaviour (same toasts, same dialog, same callback). Remove the now-duplicated inline `isRequestPinAppWidgetSupported` / `requestPinAppWidget` calls from the fragment.

**Verification:**

- `Grep -n "HomeWidgetPinner"` in `ResourceEditorFragment.kt` - expected: injected and used | actual: <fill in>.
- `Grep -n "requestPinAppWidget"` in `ResourceEditorFragment.kt` returns zero hits (call moved into pinner).
- Post-edit LOC of `ResourceEditorFragment.kt` < 1500 (`expected: <1500 | actual: 875`).
- Build: `.\a.ps1 dq` compiles.

**Status:** `[x]` done

**Step Log:**

- 2026-06-04 - Verification PASS (structural): homeWidgetPinner injected (line 57) + used (isSupported/isKeyguardLocked/requestPin); `requestPinAppWidget` 0 hits in fragment; removed orphaned Build/KeyguardManager imports, added Inject + HomeWidgetPinner imports; LOC 875 < 1500. Build at phase gate. File: ResourceEditorFragment.kt.

---

### Step 04.3 - Add picker + button strings (trilingual)

**Files:** `strings_widget.xml` (EN/RU/UK)
**Depends on:** - independent

**Prompt for developer:**

> Add two keys across EN/RU/UK in lockstep using `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add` (one parity-enforced call per key, `-En -Ru -Uk`):
> - `settings_add_home_widget_title` - EN "Add widget to home screen.." / RU "Добавить виджет на домашний экран.." / UK "Додати віджет на головний екран.."
> - `widget_picker_dialog_title` - EN "Choose a widget" / RU "Выберите виджет" / UK "Виберіть віджет"
> Author style: two-dot ellipsis `..`, correct `ё`. Validate against `docs/COMMUNICATION_POLICY.md` §2 (action/CTA formula) and §6 (tone checklist) before committing.

**Verification:**

- `Grep -n "settings_add_home_widget_title"` matches in all three `strings_widget.xml` locales.
- `Grep -n "widget_picker_dialog_title"` matches in all three locales.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "settings_add_home_widget"` exits 0.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "widget_picker_dialog"` exits 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

**Step Log:**

- 2026-06-04 - Verification PASS: settings_add_home_widget_title + widget_picker_dialog_title added to strings_widget.xml EN/RU/UK via set-android-string.ps1; check_strings_localized exit 0 for both prefixes; CTA/title pass COMMUNICATION_POLICY §2/§6. Author style `..` applied.

---

### Step 04.4 - Add the settings button row (portrait + landscape)

**Files:** `res/layout/fragment_settings_general.xml`, `res/layout-land/fragment_settings_general.xml`
**Depends on:** Step 04.3

**Prompt for developer:**

> Add a settings button row with `android:id="@+id/buttonAddHomeWidget"` and `android:text="@string/settings_add_home_widget_title"`, styled to match the surrounding General-settings button rows (same widget type, colours, typography, spacing, icon style). Add the identical row to the `layout-land` variant in the analogous position. Ensure full input coverage (Rule 17): `android:focusable="true"`, `android:clickable="true"`, a logical `nextFocus*` chain consistent with neighbouring rows, and a visible hover/focus state from the shared style. Keep the row inside system-bar safe bounds (Rule 18) - the General settings scroll container already applies system-bar insets, so place the row within it.

**Verification:**

- `Grep -n "buttonAddHomeWidget"` matches in BOTH `res/layout/fragment_settings_general.xml` and `res/layout-land/fragment_settings_general.xml`.
- `Grep -n "settings_add_home_widget_title"` matches in both layout files.
- Build: `.\a.ps1 dq` compiles (binding regenerates `buttonAddHomeWidget`).

**Status:** `[x]` done

**Step Log:**

- 2026-06-04 - Verification PASS (structural): `buttonAddHomeWidget` + `settings_add_home_widget_title` present in BOTH portrait and landscape `fragment_settings_general.xml`; placed in the device-integration card next to Wear Companion; Tonal style, focusable+clickable, ic_add icon. Build at phase gate.

---

### Step 04.5 - Implement `GeneralSettingsWidgetHelper`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsWidgetHelper.kt`
**Depends on:** Step 04.1, Step 04.4

**Prompt for developer:**

> Create `GeneralSettingsWidgetHelper(binding, fragment, catalog: HomeWidgetCatalog, pinner: HomeWidgetPinner)` following the existing `GeneralSettings*Helper` pattern (constructor-injected collaborators, no business logic in the fragment). On `binding.buttonAddHomeWidget` click:
> 1. If `!pinner.isSupported()` - toast `R.string.widget_pin_not_supported` and return (explicit fallback, strategic §6.2.6).
> 2. Otherwise launch the fragment lifecycle scope, call `catalog.availableEntries()`, and show a `MaterialAlertDialogBuilder` titled `R.string.widget_picker_dialog_title` listing each entry by its `labelRes` + `iconRes`.
> 3. On selection, call `pinner.requestPin(entry.component(context), null)`; if it returns false, toast `R.string.widget_pin_not_supported`.
> Hide unavailable widgets by construction (the catalog already filters). Timber only.

**Verification:**

- `Glob` - `GeneralSettingsWidgetHelper.kt` exists.
- `Grep -n "class GeneralSettingsWidgetHelper"` matches once.
- `Grep -n "availableEntries"`, `Grep -n "requestPin"`, `Grep -n "widget_pin_not_supported"` each match.
- `Grep -n "Log\.d\("` returns zero hits.
- Build: `.\a.ps1 dq` compiles.

**Status:** `[x]` done

**Step Log:**

- 2026-06-04 - Verification PASS (structural): GeneralSettingsWidgetHelper.kt created; class + availableEntries + requestPin + widget_pin_not_supported fallback present; `Log.d` 0; MaterialAlertDialog picker over labels. Build at phase gate. File: ui/settings/helpers/GeneralSettingsWidgetHelper.kt.

---

### Step 04.6 - Wire the helper into `GeneralSettingsFragment`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/GeneralSettingsFragment.kt`
**Depends on:** Step 04.5

**Prompt for developer:**

> Inject `HomeWidgetCatalog` and `HomeWidgetPinner` (`@Inject lateinit var`), add a `private val widgetHelper by lazy { GeneralSettingsWidgetHelper(binding, this, homeWidgetCatalog, homeWidgetPinner) }` mirroring the other lazy helpers, and call its setup (click binding) from `onViewCreated` alongside the existing helper wiring. No business logic added to the fragment itself (Rule 3 / UI layer discipline).

**Verification:**

- `Grep -n "GeneralSettingsWidgetHelper"` in `GeneralSettingsFragment.kt` - expected: declared + set up | actual: <fill in>.
- `Grep -n "homeWidgetCatalog|HomeWidgetCatalog"` matches (injected).
- Post-edit LOC of `GeneralSettingsFragment.kt` < 1500.
- Build: `.\a.ps1 dq` compiles.

**Status:** `[x]` done

**Step Log:**

- 2026-06-04 - Verification PASS (structural): GeneralSettingsFragment injects homeWidgetCatalog + homeWidgetPinner, lazy widgetHelper, `widgetHelper.setup()` in onViewCreated; imports added; LOC 263 < 1500. Build at phase gate. File: GeneralSettingsFragment.kt.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 dq` BUILD SUCCESSFUL 1m26s.
- [x] `Grep -n "requestPinAppWidget"` across `src/main` matches only inside `HomeWidgetPinner.kt`.
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] `check_strings_localized.ps1` exit 0 for `settings_add_home_widget` and `widget_picker_dialog`.
- [x] Dev log entry added for every file in "Files Touched".
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (1618 records).

---

## Handoff Notes to Next Phase

Settings → "Add widget to home screen.." → picker (available-only) → system pin → fallback toast is complete. Phase 05 updates FEATURES trilingual, functionality log, catalog roles, and carries the Camera-OCR re-add manual-verification note.

> **Device-test note for `/spec-dev` → `BlockNeedUserTest`:** insert one `Timber.d("S0348: <entry>")` tag at each new flow entry - the settings button click (`GeneralSettingsWidgetHelper`) and the picker selection / pin request. These tags are removed when the spec leaves `BlockNeedUserTest`.

---

## Rollback Plan

Revert phase commit(s): remove the helper, pinner, button rows, and strings; restore `ResourceEditorFragment`'s inline pin logic. No data migration; no schema change.
