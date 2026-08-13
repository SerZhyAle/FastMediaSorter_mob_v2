# Phase 04 - Welcome trigger UI

**Strategic spec:** [`../S0409_welcome-enable-all.md`](../S0409_welcome-enable-all.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** Phase 05
**Steps done:** 3 / 3
**Started:** 2026-06-12
**Completed:** 2026-06-12

---

## Objective

Add the page-0 "Включить всё" button beside Next, localise its text, and wire it to the orchestrator -
visible only on the first page, focusable for D-pad/TV, with rotation-safe state delegated to the manager.

---

## Prerequisites

- [ ] Phase 03 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | - |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | - |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | - |
| `app_v2/src/main/res/layout/activity_welcome.xml` | Modified | ≤ 40 |
| `app_v2/src/main/res/layout-sw480dp/activity_welcome.xml` | Modified | ≤ 40 |
| `app_v2/src/main/res/layout-sw720dp/activity_welcome.xml` | Modified | ≤ 40 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomeActivity.kt` | Modified | ≤ 760 |

> Landscape parity: `app_v2/src/main/res/layout-land/activity_welcome.xml` does not exist - the activity
> shell is portrait-only (only the page fragments have land variants). No landscape edit needed.
>
> Width-qualified parity: `activity_welcome.xml` also has `layout-sw480dp/` and `layout-sw720dp/`
> variants. The new `@id/btnEnableAll` MUST be added to ALL THREE or ViewBinding generates a nullable
> field (compile error on `binding.btnEnableAll.<call>`). All three are listed above.

---

## Steps

### Step 04.1 - Add the trilingual strings

**Files:** `app_v2/src/main/res/values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add two keys in EN/RU/UK lockstep with a single call:
> `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key welcome_enable_all
> -En "Enable all" -Ru "Включить всё" -Uk "Увімкнути все"` and a second `add` for
> `welcome_enable_all_content_description` (`-En "Enable everything and finish setup"
> -Ru "Включить всё и завершить настройку" -Uk "Увімкнути все та завершити налаштування"`). Verify the
> button label and content description satisfy `docs/COMMUNICATION_POLICY.md` §2 (action label formula)
> and the §6 tone checklist before committing.

**Verification:**

- `Grep` - `name="welcome_enable_all"` present in all three `strings.xml`.
- `Grep` - `name="welcome_enable_all_content_description"` present in all three.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "welcome_enable_all"` exits 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x] done`

**Step Log:**

- 2026-06-12 - Verification PASS. welcome_enable_all + welcome_enable_all_content_description added EN/RU/UK via set-android-string.ps1 (UTF-8 script, no mojibake, ё preserved). check_strings_localized exit 0. Tone checklist OK (clear action label + descriptive content desc).

---

### Step 04.2 - Add the button to the bottom nav

**Files:** `app_v2/src/main/res/layout/activity_welcome.xml`
**Depends on:** Step 04.1

**Prompt for developer:**

> In `layoutBottomNav`, add a `MaterialButton` `@+id/btnEnableAll` between `layoutIndicator` and
> `btnNext`, styled as a secondary/text button (not the same filled primary as Next) so the page-0
> hierarchy stays Next-primary. Text `@string/welcome_enable_all`, `contentDescription`
> `@string/welcome_enable_all_content_description`, `android:visibility="gone"` by default (the Activity
> shows it on page 0 only). Set `focusable`/`clickable` true and `foreground="@drawable/focus_button_background"`
> like the sibling buttons. Re-point `layoutIndicator`'s `app:layout_constraintEnd_toStartOf` to
> `@id/btnEnableAll`, and constrain `btnEnableAll` `End_toStartOf="@id/btnNext"`. Add D-pad focus order:
> `btnEnableAll` `nextFocusLeft=@id/btnPrevious` / `nextFocusRight=@id/btnNext` / `nextFocusUp=@id/viewPager`;
> update `btnNext` and `btnFinish` `nextFocusLeft` to `@id/btnEnableAll`. Use only `?attr/`/`@color/`
> colour references - no hardcoded hex.

**Verification:**

- `Grep` - `@+id/btnEnableAll` present once in `activity_welcome.xml`.
- `Grep` - `app:layout_constraintEnd_toStartOf="@id/btnEnableAll"` present (indicator re-pointed).
- `Grep -n "#"` returns no hardcoded hex colour attribute in the added button block.

**Status:** `[x] done`

**Step Log:**

- 2026-06-12 - Verification 3/3 PASS. activity_welcome.xml: btnEnableAll (OutlinedButton, gone by default) inserted between indicator and Next; indicator end re-pointed; btnNext nextFocusLeft -> btnEnableAll. btnFinish nextFocusLeft kept as btnPrevious (Finish never coexists with a visible Enable-all). No layout-land/activity_welcome.xml variant - portrait-only shell. No hardcoded hex (?attr/colorPrimary).
- 2026-06-12 - Build surfaced nullable `MaterialButton?` binding: btnEnableAll was missing from the `layout-sw480dp/` and `layout-sw720dp/` variants of activity_welcome.xml. Added it to both (variant-native multi-line style, gone by default). Field is now non-null.

---

### Step 04.3 - Wire the button and the orchestrator in `WelcomeActivity`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomeActivity.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> Field-inject `WelcomeEnableAllManager` (mirror the `permissionsManager` `@Inject lateinit var`). In
> `setupViews()` call `enableAllManager.attach(this)` after `permissionsManager.attach(this)`. In
> `onCreate`/`onSaveInstanceState` delegate to `enableAllManager.onRestoreInstanceState(savedInstanceState)`
> / `enableAllManager.onSaveInstanceState(outState)` alongside the existing permissions delegation. In
> `setupButtons()` wire `binding.btnEnableAll.setOnClickListener` to
> `enableAllManager.start(permissionsManager, applyProfileOther = { viewModel.onProfileSelected(
> DeviceProfileType.OTHER); viewModel.saveDeviceProfile(isSkipped = false) }, onFinished = {
> completeWelcomeFlow() })`. In `updateUI()` set `binding.btnEnableAll.visibility` to `VISIBLE` only when
> `isFirstPage`, else `GONE`. Include `btnEnableAll` in `focusBar()`/`sliderFocusables()` traversal so
> D-pad reaches it on page 0.

**Verification:**

- `Grep` - `lateinit var enableAllManager` present.
- `Grep` - `enableAllManager.attach(this` present.
- `Grep` - `enableAllManager.start` present (trailing-lambda call site).
- `Grep` - `btnEnableAll.visibility` present in `updateUI` area.
- `Grep` - `enableAllManager.onSaveInstanceState` and `enableAllManager.onRestoreInstanceState` present.
- `.\a.ps1 fk` compiles.

**Status:** `[x] done`

**Step Log:**

- 2026-06-12 - Verification 6/6 PASS. WelcomeActivity: injected enableAllManager; attach (re-wired each setupViews, onFinished=completeWelcomeFlow); save/restore delegation; btnEnableAll click -> start{OTHER profile + save}; visibility VISIBLE only on first page. Post-change Kotlin PASS (ticket-log gate clean pre-tag).

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "welcome_enable_all"` exits 0.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

The feature is fully wired end-to-end behind the page-0 button. Phase 05 regenerates the catalog, adds
the FEATURES trilingual entry, and closes the dev log. The ticket then enters BlockNeedUserTest - the
`Timber.d("S0409:` probe from Phase 03 must stay until device verification passes.

---

## Rollback Plan

Revert phase commit(s) - layout + Activity wiring + strings; no data migration. The orchestrator and
seams from earlier phases become dormant (nothing else references them).
