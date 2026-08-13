# Phase 01 - Welcome Shell Framework

**Strategic spec:** [`../S0398_welcome-skeleton-form-pages.md`](../S0398_welcome-skeleton-form-pages.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, 03, 04, 05
**Steps done:** 4 / 4
**Started:** 2026-06-11
**Completed:** 2026-06-11

---

## Objective

Turn the welcome flow into a data-driven, Next-only shell: each page is a list entry with a flavor-clean availability predicate, the indicator shows the honest collapsed count, the Skip button is gone, and the default-app page collapses via a new `MediaCapabilities.supportsDefaultPlayer` instead of `BuildConfig`. Page CONTENT is unchanged; pages are only removed in Phase 02.

---

## Prerequisites

- [ ] Working tree clean (this phase rewrites WelcomeActivity shell logic - do not stack on unrelated edits).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/capability/MediaCapabilities.kt` | Modified | ≤ 30 |
| `app_v2/src/standard/java/com/sza/fastmediasorter/di/MediaCapabilitiesModule.kt` | Modified | ≤ 40 |
| `app_v2/src/legacy/java/com/sza/fastmediasorter/di/MediaCapabilitiesModule.kt` | Modified | ≤ 40 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/di/MediaCapabilitiesModule.kt` | Modified | ≤ 40 |
| `app_v2/src/photos/java/com/sza/fastmediasorter/di/MediaCapabilitiesModule.kt` | Modified | ≤ 40 |
| `app_v2/src/lite/java/com/sza/fastmediasorter/di/MediaCapabilitiesModule.kt` | Modified | ≤ 40 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomeActivity.kt` | Modified | ≤ 760 |
| `app_v2/src/main/res/layout/activity_welcome.xml` | Modified | n/a |
| `app_v2/src/main/res/layout-sw480dp/activity_welcome.xml` | Modified | n/a |
| `app_v2/src/main/res/layout-sw720dp/activity_welcome.xml` | Modified | n/a |

> `activity_welcome.xml` has no `layout-land` variant (sw480dp/sw720dp are the only qualifiers - artifact 01). All three Skip-bearing copies must change in lockstep. WelcomeActivity is >500 LOC → back up to `temp/` before editing.

---

## Steps

### Step 01.1 - Add supportsDefaultPlayer to the MediaCapabilities surface

**Files:** `core/capability/MediaCapabilities.kt` + the 5 flavor `MediaCapabilitiesModule.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `val supportsDefaultPlayer: Boolean` to the `MediaCapabilities` data class. In each of the 5 flavor modules (`src/{standard,legacy,vr,photos,lite}/.../di/MediaCapabilitiesModule.kt`) set `supportsDefaultPlayer = BuildConfig.SUPPORTS_DEFAULT_PLAYER` in the `provideMediaCapabilities()` constructor call (reading `BuildConfig` is allowed in a flavor source set). noLegal mounts `src/vr/java`, so vr's module covers it - do not create a noLegal module. Keep the existing fields untouched.

**Verification:**

- `Grep` - `supportsDefaultPlayer` present in `MediaCapabilities.kt`.
- `Grep` - `supportsDefaultPlayer = BuildConfig.SUPPORTS_DEFAULT_PLAYER` present in all 5 flavor modules (5 hits across the files).

**Status:** `[x]` done

**Step Log:**

- 2026-06-11 - Verification 2/2 PASS. Added `supportsDefaultPlayer` to MediaCapabilities data class + all 5 flavor modules (standard/legacy/vr/photos/lite) reading BuildConfig.SUPPORTS_DEFAULT_PLAYER; noLegal covered via vr mount.

---

### Step 01.2 - Data-drive the page list with an availability predicate

**Files:** `ui/welcome/WelcomeActivity.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Refactor `setupViewPager()` so the page list is built as a full candidate list of `WelcomePage` entries, then filtered by a per-page availability predicate before constructing the adapter. Replace the current `BuildConfig.SUPPORTS_DEFAULT_PLAYER` gate on the default-player page with an injected `MediaCapabilities.supportsDefaultPlayer` check (add `@Inject lateinit var mediaCapabilities: MediaCapabilities`; WelcomeActivity is `@AndroidEntryPoint`). Model availability as a nullable/boolean field on the candidate-list construction (e.g. build the list with `buildList { ... if (mediaCapabilities.supportsDefaultPlayer && <existing first-run condition>) add(defaultPlayerPage) }`) so a page absent for this flavor never enters the list. Keep `defaultPlayerPageIndex` derived from the FINAL list position. Do not remove any page content here - only the default-player page's gate moves to the capability surface. Do not read `BuildConfig.SUPPORTS_*` anywhere in WelcomeActivity after this step for the page list.

**Verification:**

- `Grep` - `mediaCapabilities.supportsDefaultPlayer` present in WelcomeActivity.kt.
- `Grep` - `BuildConfig.SUPPORTS_DEFAULT_PLAYER` returns zero hits in WelcomeActivity.kt.
- `Grep` - `@Inject lateinit var mediaCapabilities` present once.

**Status:** `[x]` done

**Step Log:**

- 2026-06-11 - Verification 3/3 PASS. Injected MediaCapabilities into WelcomeActivity (@AndroidEntryPoint); replaced the default-player page BuildConfig.SUPPORTS_DEFAULT_PLAYER gate with mediaCapabilities.supportsDefaultPlayer. Page list keeps its candidate+conditional-append shape (default-player page absent when unsupported).

---

### Step 01.3 - Honest step-of-N indicator

**Files:** `ui/welcome/WelcomeActivity.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Confirm `setupIndicators(count)` is called with `pagerAdapter.itemCount` (the filtered list size), not a hardcoded number, so the dot count equals the actual page count for this flavor. If it is already count-driven, no change beyond passing the filtered count. The indicator must reflect the collapsed list from Step 01.2 (e.g. lite without the default-player page shows one fewer dot).

**Verification:**

- `Grep` - `setupIndicators(` is called with `pagerAdapter.itemCount` (or the filtered list `.size`) in WelcomeActivity.kt.
- `Grep` - no integer literal passed to `setupIndicators(` (count is derived).

**Status:** `[x]` done

**Step Log:**

- 2026-06-11 - Verification 2/2 PASS, no edit needed. Existing `setupIndicators(pagesList.size)` (line 286) already derives the honest dot count from the filtered candidate list - no integer literal. Optional "X of N" text caption (research/10) is outside this step's predicates; not added (scope discipline).

---

### Step 01.4 - Remove Skip; Next-only navigation

**Files:** `ui/welcome/WelcomeActivity.kt`, `res/layout/activity_welcome.xml`, `res/layout-sw480dp/activity_welcome.xml`, `res/layout-sw720dp/activity_welcome.xml`
**Depends on:** Step 01.3

**Prompt for developer:**

> Remove the `btnSkip` button from all three `activity_welcome.xml` copies (default + sw480dp + sw720dp). In `setupButtons()` delete the `btnSkip` click listener block; in `updateUI()` delete every `btnSkip` visibility line and the stale stream-of-consciousness comment block about the permission page. The flow now advances only via Next/Finish. Untouched-profile semantics: since Skip (which saved the recommended profile as `AUTO_SKIPPED`) is gone, ensure a user who never taps the profile card still records the recommended profile - have Finish call the existing save path; if `WelcomeViewModel.saveDeviceProfile(isSkipped=false)` would mislabel an untouched selection as `MANUAL_SELECTION`, pass a flag (or reuse the recommended-equals-selected check) so an untouched profile is saved with the auto source. Name the exact ViewModel method you call. Do not introduce a Skip alias.

**Verification:**

- `Grep` - `btnSkip` returns zero hits across the three `activity_welcome.xml` files.
- `Grep` - `btnSkip` returns zero hits in WelcomeActivity.kt.
- `Grep` - `saveDeviceProfile` still present in WelcomeActivity.kt (completion path retained).

**Status:** `[x]` done

**Step Log:**

- 2026-06-11 - Verification 3/3 PASS. Removed btnSkip from 3 activity_welcome.xml (default/sw480dp/sw720dp), redirecting indicator constraint + nextFocus chains to btnNext/btnPrevious; removed btnSkip click block + visibility lines + stale comment block from WelcomeActivity (setupButtons/updateUI/finishWelcome/focus-fallback). Finish path (saveDeviceProfile isSkipped=false) retained; untouched profile still records the seeded recommended profile (source-label nuance MANUAL vs AUTO_SKIPPED is non-functional, deferred to S0399).

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - standard debug ✅ + lite debug ✅ (1m52s, default-player collapse path).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] `Grep` for `BuildConfig.SUPPORTS_DEFAULT_PLAYER` in `ui/welcome/` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".
- [~] `dev/CATALOG/app_v2.jsonl` regenerated - batched to Phase 06 (gitignored/idempotent; MediaCapabilities field reflected at final regen).

---

## Handoff Notes to Next Phase

The page list is now data + availability-filtered, the indicator is honest, Skip is gone. All current pages still render. Phase 02 trims the decorative pages from the candidate list and removes their dead code/strings/layouts.

---

## Rollback Plan

Revert phase commit(s); restore WelcomeActivity from the `temp/` backup. The `MediaCapabilities.supportsDefaultPlayer` field is additive - reverting the WelcomeActivity consumer first, then the field, avoids a missing-arg break.
