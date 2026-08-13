# Phase 01 - Indicator view and its resources

**Strategic spec:** [`../S1398_hairline-background-progress-bar.md`](../S1398_hairline-background-progress-bar.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 4 / 4
**Started:** 2026-08-10
**Completed:** 2026-08-10

---

## Objective

Introduce the state model and the self-configuring 1dp progress view, plus the dimension and the trilingual screen-reader description they need. Nothing observes the view and nothing attaches it yet.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/dimens.xml` | Modified | +2 |
| `app_v2/src/main/res/values/strings.xml` | Modified | +1 |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | +1 |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | +1 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/backgroundop/BackgroundOperationBarState.kt` | New | ≤ 40 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/backgroundop/BackgroundOperationBarView.kt` | New | ≤ 120 |

> No `res/layout*` file is touched in this phase or in any later one: the bar is attached programmatically over `android.R.id.content`, so CLAUDE.md Rule 11 landscape parity has nothing to mirror. Landscape behaviour is still verified on device in Phase 03.

---

## Steps

### Step 01.1 - Add the bar height dimension

**Files:** `app_v2/src/main/res/values/dimens.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `background_operation_bar_height` with the value `1dp` to `app_v2/src/main/res/values/dimens.xml`, placed next to the other progress-related dimensions. Do not reuse or edit `player_progress_bar_height_large` - that 6dp value belongs to the player and stays untouched.

**Why:**

Strategic §6.7 records the owner's ruling of 1dp, and §6.9 established that no thin-indicator style exists in resources, so the size has to be introduced rather than borrowed. Naming it as a dimension rather than hardcoding it in Kotlin is what keeps the detekt `MagicNumber` rule satisfied in step 01.4.

**Verification:**

- `Grep` - `background_operation_bar_height` matches exactly once in `app_v2/src/main/res/values/dimens.xml`.
- `Grep` - `>1dp<` present on that same line.
- `Grep` - `player_progress_bar_height_large` still matches in `dimens.xml`, unchanged.

**Status:** `[x] done`

**Step Log:**

- 2026-08-10 - Verification 3/3 PASS. Files: app_v2/src/main/res/values/dimens.xml (+1 LOC, `background_operation_bar_height` = 1dp at line 156). `player_progress_bar_height_large` still 6dp at line 282, untouched. Dev log recorded.

---

### Step 01.2 - Add the trilingual screen-reader description

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add the key `background_operation_bar_description` across all three locales in one call:
>
> ```powershell
> pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key background_operation_bar_description -En "Background operation in progress" -Ru "Фоновая операция выполняется" -Uk "Фонова операція виконується"
> ```
>
> Check the wording against `docs/COMMUNICATION_POLICY.md` §2 (message formula for a status message) and §6 (tone checklist) before running. Then audit the key:
>
> ```powershell
> pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "background_operation_bar"
> ```

**Why:**

Strategic §3.2 requires EN/RU/UK for the screen-reader description, and §3.3 Accessibility makes the description mandatory because the bar is a colour-and-length-only indicator that must not be the sole carrier of the information.

**Verification:**

- `Grep` - `background_operation_bar_description` matches exactly once in each of the three `strings.xml` files.
- `check_strings_localized.ps1 -KeyPrefix "background_operation_bar"` exits 0.
- Strings pass `COMMUNICATION_POLICY` §6 checklist.

**Status:** `[x] done`

**Step Log:**

- 2026-08-10 - Verification 3/3 PASS. Files: values/strings.xml, values-ru/strings.xml, values-uk/strings.xml (+1 line each) via `set-android-string.ps1 -Action add`. `check_strings_localized.ps1 -KeyPrefix "background_operation_bar"` exit 0, all 1 key present in en/ru/uk; 10 best-effort locales reported untranslated, non-fatal by the gate's own contract. COMMUNICATION_POLICY §6: neutral status wording, no raw exception text, no false-success phrasing. Dev log recorded.

---

### Step 01.3 - Add the bar state model

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/backgroundop/BackgroundOperationBarState.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create the package `ui/common/backgroundop/` and add `BackgroundOperationBarState` as a sealed interface with exactly three members: `data object Hidden`, `data object Indeterminate`, and `data class Determinate(val percent: Int)`. Add a KDoc line on `Indeterminate` stating that it is the state used while the operation total is still unknown.

**Why:**

Strategic §6.5 fixes three distinct renderings - hidden, indeterminate while the total is unknown, determinate otherwise - and modelling them as one sealed type is what stops Phase 03 from re-deriving "is it visible" from a nullable percent, where `null` would be ambiguous between "hidden" and "unknown total".

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/backgroundop/BackgroundOperationBarState.kt` exists.
- `Grep` - `sealed interface BackgroundOperationBarState` matches exactly once.
- `Grep` - `data object Hidden`, `data object Indeterminate` and `data class Determinate(val percent: Int)` each match exactly once.

**Status:** `[x] done`

**Step Log:**

- 2026-08-10 - Verification 5/5 PASS. Files: ui/common/backgroundop/BackgroundOperationBarState.kt (New, 19 LOC). Sealed interface with the three declared members, each matching exactly once. Dev log recorded.

---

### Step 01.4 - Add the bar view

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/backgroundop/BackgroundOperationBarView.kt`
**Depends on:** Step 01.1, Step 01.3

**Prompt for developer:**

> Add `BackgroundOperationBarView` extending `com.google.android.material.progressindicator.LinearProgressIndicator`, with a `@JvmOverloads constructor(context: Context, attrs: AttributeSet? = null)`. In `init`, configure it once:
>
> - `trackThickness` from `R.dimen.background_operation_bar_height`;
> - `setIndicatorColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary, Color.TRANSPARENT))`, following the resolution pattern already used in `ui/main/helpers/VersionOverlayManager.kt`;
> - `trackCornerRadius = 0`, and the track colour set to transparent so nothing is painted while the operation is idle;
> - `isClickable = false`, `isFocusable = false`, `isFocusableInTouchMode = false`;
> - `contentDescription` from `R.string.background_operation_bar_description`;
> - `max = PERCENT_MAX` with `PERCENT_MAX` a private const of 100.
>
> Add one public method `render(state: BackgroundOperationBarState)` that maps `Hidden` to `isVisible = false`, `Indeterminate` to visible plus `isIndeterminate = true`, and `Determinate` to visible plus `isIndeterminate = false` and `setProgressCompat(state.percent, true)`. Switching `isIndeterminate` requires the view to be detached from the animation, so set `isIndeterminate` before assigning the progress value, never after.

**Why:**

Strategic §6.8 rules the bar fully passive, and the explicit `isClickable`/`isFocusable` flags are what make criterion §11.7 - elements at the bottom edge stay tappable - hold, because a non-clickable child returns false from `onTouchEvent` and lets the touch fall through to the view beneath it. §6.7 fixes the 1dp thickness and the theme accent colour, and taking the colour from the theme attribute rather than a literal is what lets it follow the light and dark schemes.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/backgroundop/BackgroundOperationBarView.kt` exists.
- `Grep` - `class BackgroundOperationBarView` matches exactly once.
- `Grep` - `fun render(state: BackgroundOperationBarState)` present.
- `Grep` - `isClickable = false` and `isFocusable = false` each present.
- `Grep` - `R.dimen.background_operation_bar_height` and `R.string.background_operation_bar_description` each present.
- `Grep` - `Log\.d\(` returns zero hits in the file.
- `Grep` - `="#` returns zero hits in the file - no hardcoded colour.

**Status:** `[x] done`

**Step Log:**

- 2026-08-10 - Verification 8/8 PASS. Files: ui/common/backgroundop/BackgroundOperationBarView.kt (New, 63 LOC). No `Log.d(`, no hex colour, no line over 120 chars. Note on the prompt's ordering instruction: it was written against a remembered `IllegalStateException` guard in `BaseProgressIndicator.setIndeterminate`, which `javap` on material-1.14.0 shows does not exist in this version - the class carries no `IllegalStateException` at all. The prescribed order (mode before value) is kept because it is correct regardless; no hide-before-switch dance was added, and no comment claims a constraint that could not be verified. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fc` (code + resources, the right target for a phase adding both) exit 0. First run failed on `com.google.android.material.R.attr.colorPrimary` being unresolved; `colorPrimary` is an appcompat attribute, and the project already resolves it as `androidx.appcompat.R.attr.colorPrimary` in `CameraCaptureResultManager` and `CropOverlayView`. Fixed, re-run exit 0. Build retries used: 1 of 3.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for the phase via `post-change.ps1` - one row for the six-file set.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated as part of the closure (2761 records); roles for the new classes are set in Phase 04.
- [x] Phase-boundary audit run - no P0/P1 findings. Layer 1 applies: both files sit in `ui/`, carry no business logic, and are 19 and 63 LOC. Layers 2-4 do not apply - the phase introduces no lifecycle, coroutine, listener or Room surface. `assert-detekt` scoped: PASS, none of the three project-wide new findings is in this set.
- [x] UI-phase screenshot gate (S1338): placement decision recorded - strategic §3.3 UI placement contract with `Owner sign-off: 2026-08-10`. Screenshot deferred with reason: this phase attaches the view to nothing, so no screen renders differently yet. The shot is owed by Phase 03, which is where the bar first appears.

---

## Handoff Notes to Next Phase

`BackgroundOperationBarState` is the only vocabulary later phases use to describe the bar; Phase 02 produces it and Phase 03 consumes it, and neither may re-derive a percent of its own. The view configures itself entirely in `init`, so an attach site never sets thickness, colour or accessibility flags.

---

## Rollback Plan

Revert phase commit(s) - two new files plus four additive resource lines, no data migration and no user-facing surface yet.
