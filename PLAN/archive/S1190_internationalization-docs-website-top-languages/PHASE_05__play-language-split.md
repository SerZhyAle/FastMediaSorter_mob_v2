# Phase 05 - Play language split

**Strategic spec:** [`../S1190_internationalization-docs-website-top-languages.md`](../S1190_internationalization-docs-website-top-languages.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** none
**Steps done:** 4 / 4
**Started:** 2026-08-05
**Completed:** 2026-08-05

---

## Objective

Ask Play to deliver the chosen language on demand, and refuse the switch - with a reason - when it cannot.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] `temp/CODE.LOCK` free.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/build.gradle.kts` | Modified | ≤ 5 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/util/LanguageSplitInstaller.kt` | New | ≤ 180 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsViewSetupHelper.kt` | Modified | ≤ 700 |
| `app_v2/src/main/res/values/strings_settings.xml` | Modified | ≤ 5 |

> `LanguageSplitInstaller` goes in `src/main/`: it is not flavor-specific code but a runtime capability that degrades on its own. Channels installed outside Play carry every locale already (strategic ADR-5), and the Play library reports "no split needed" there - no `BuildConfig` guard, no flavor source set (CLAUDE.md Rule 15). Confirm that behaviour in Step 05.2's fallback rather than assuming it.

---

## Steps

### Step 05.1 - Restore the delivery dependency

**Files:** `app_v2/build.gradle.kts`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `com.google.android.play:feature-delivery` (Kotlin extensions variant if the project already uses coroutine wrappers elsewhere) to the app module's dependency block. There is no `gradle/libs.versions.toml` in this repo - dependencies are declared with literal coordinates in `app_v2/build.gradle.kts`, so follow that. This is the library removed together with the deleted `:translate_feature` DFM (S0423); it is being brought back for language splits only - do not reintroduce any dynamic-feature module.

**Verification:**

- `Grep` - `feature-delivery` matches once in `app_v2/build.gradle.kts`.
- `Grep` - `dynamicFeatures` returns zero hits in `app_v2/build.gradle.kts`.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-05 - Verification 3\3 PASS. `com.google.android.play:feature-delivery-ktx:2.1.0` added beside the existing `review-ktx`; one hit, zero `dynamicFeatures`, `fk` exit 0 with the artifact resolved from the network (it was not in the local Gradle cache - only `core-common`, `review` and `review-ktx` were). The ktx variant is the right one by the step's own condition: the project already carries `kotlinx-coroutines-play-services` and `review-ktx`. Version taken from Google's Maven metadata rather than guessed - 2.1.0 is the newest published. Dev log recorded.

---

### Step 05.2 - Request the language

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/util/LanguageSplitInstaller.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> Add `LanguageSplitInstaller`: given a language tag it reports whether that language is already installed, and otherwise suspends until `SplitInstallManager.startInstall(..addLanguage(..))` either completes or fails, returning a typed outcome (already present / installed now / failed with a reason). No blocking waits, no `GlobalScope`; the caller supplies the scope. A failure is an expected outcome, not an exception to swallow - log it at `Timber.i` with the reason and return it.

**Verification:**

- `Glob` - `LanguageSplitInstaller.kt` exists.
- `Grep` - `addLanguage` matches at least once.
- `Grep` - `GlobalScope` returns zero hits in the file.
- `Grep` - `catch (e: Exception) {}` (empty body) returns zero hits in the file.

**Status:** `[x]` done

**Step Log:**

- 2026-08-05 - Verification 4\4 PASS. The class was already present and already compiled - a previous run of this phase wrote it after Step 05.1 and stopped before flipping the step, so this run verified rather than re-authored it. Predicates: file exists; `addLanguage` 1 hit; `GlobalScope` 0 hits; empty `catch (e: Exception) {}` 0 hits. `.\a.ps1 fk` exit 0 with `compileStandardDebugKotlin` UP-TO-DATE, which is what proves the file had already compiled clean rather than merely being syntactically plausible. Both catches call `rethrowIfCancellation()` (`core/util/CoroutineExt.kt:15`) before returning an outcome, so cancellation is not swallowed. `dev/CHANGELOG.md` carried no entry for the file - closed here.

---

### Step 05.3 - Gate the switch on the download

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsViewSetupHelper.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/GeneralSettingsFragment.kt`
**Depends on:** Step 05.2

**Prompt for developer:**

> When the user picks a language, request it through `LanguageSplitInstaller` first and apply it only on success. On failure, keep the previous language and tell the user why in one sentence - what happened plus what they can do (`docs/COMMUNICATION_POLICY.md` §2), never a raw exception message. Do not block the UI thread while the split downloads.

**Verification:**

- `Grep` - `LanguageSplitInstaller` referenced at least once in the helper.
- `Grep` - the language-apply call is inside the success branch of the installer result.
- Strings pass `COMMUNICATION_POLICY` §6 checklist.

**Status:** `[x]` done

**Step Log:**

- 2026-08-05 - Verification 3\3 PASS. `LanguageSplitInstaller` 4 hits in the helper; the apply call sits in the `else ->` arm of a `when` over the outcome, so only `Failed` skips it and any success applies. The message states outcome plus next action and names no exception - `COMMUNICATION_POLICY` §6 clean.
- Two deviations from the step as written, both forced by the code rather than chosen. **(1) `Files Touched` was short one file.** The helper is constructed by hand in `GeneralSettingsFragment.kt:211`, so the installer could not reach it without a constructor parameter and the matching `@Inject lateinit var` in that fragment. Injecting it is not a Hilt graph change - `LanguageSplitInstaller` is already `@Singleton` with an `@Inject constructor`, and the fragment is already `@AndroidEntryPoint` - so this is not Hard Stop #7. The file was added to the step's `Files Touched` rather than edited silently. **(2) The follow-system sentinel needed a guard.** `LocaleHelper.FOLLOW_SYSTEM_LANGUAGE` is the string `"system"`, which `Locale.forLanguageTag` accepts as a well-formed language subtag - so without the guard, choosing "follow the system" would have asked Play for a split named `system`, been refused, and shown the user a download error for a language that needs no download. The step prompt covers picking *a language*; the sentinel is not one. Applying it directly is the only reading that is not a regression, so it was written that way and recorded here rather than guessed silently.
- Ordering note: predicate 3 judges the string that Step 05.4 adds, so the string was written before this step was flipped. The dependency is not violated - 05.4's own predicates are evaluated in its log below.
- `.\a.ps1 fk` exit 0 after both edits. Note this does **not** prove the Hilt graph: a missing binding survives a green Kotlin compile and only surfaces in the kapt/full build, which the Phase Done Criteria run covers.

---

### Step 05.4 - Add the failure string in lockstep

**Files:** `app_v2/src/main/res/values{,-ru,-uk}/strings_settings.xml`
**Depends on:** Step 05.3

**Prompt for developer:**

> Add the download-failure message with `scripts/utils/set-android-string.ps1 -Action add -Key <key> -En -Ru -Uk` in one call so the three strict locales stay in parity. Message states the outcome and the next step; no "operation failed", no exception text.

**Verification:**

- `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action get -Key <key>` lists all three strict locales.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "<key prefix>"` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-05 - Verification 2\2 PASS. Key `language_download_failed` added to `strings_settings.xml` in one `set-android-string.ps1 -Action add -En -Ru -Uk` call, so the three strict locales landed together rather than drifting. `-Action get` lists all three; `check_strings_localized.ps1 -KeyPrefix "language_download_failed"` exit 0, no strict-locale gaps.

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] Project compiles - run `/build`. `.\a.ps1 dq` exit 0, `hiltJavaCompileStandardDebug` executed, APK `v2.60.8041.533-DEBUG` produced. The Hilt run is what matters here: `fk` alone never touches the graph, so a missing binding for the newly injected installer would have passed it.
- [x] `Grep` for `TODO(phase-05)` returns zero hits.
- [x] Dev log entry added via `scripts/post-change.ps1 -ChangeType Mixed`. `post-change: PASS (Mixed)` over all five changed files with `-ScopeToFile`.
- [x] Phase-boundary audit run - coroutine and callback ownership are in scope for this phase (Layer 2).

## Phase-boundary audit - 2026-08-05

Scope: `LanguageSplitInstaller.kt`, `GeneralSettingsViewSetupHelper.kt`, `GeneralSettingsFragment.kt`, `strings_settings.xml` (en/ru/uk).

- **Layer 1 - architecture.** The UI helper calls a `core/util` capability wrapper directly rather than routing through a UseCase. Consistent with the file's own established pattern - it already calls `LocaleHelper` the same way, and `LanguageSplitInstaller` sits in the same category (a wrapper over a platform service, no domain state). Not a layer violation, no action.
- **Layer 2 - coroutines.** The wait runs on `fragment.viewLifecycleOwner.lifecycleScope`, so leaving the settings screen mid-download cancels it instead of resuming onto a dead view. One-shot `suspend` call, not a Flow collection, so `repeatOnLifecycle` does not apply - the `unsafe-collect` gate agrees. Both `catch` blocks call `rethrowIfCancellation()` before returning an outcome, so a cancelled install is not converted into a `Failed` toast; the `swallowed-cancellation` gate reports 0 new occurrences.
- **Layer 3 - ownership.** No new listener or observer registered, so nothing to unregister. `LanguageSplitInstaller` is `@Singleton` over `@ApplicationContext` and holds no view reference.
- **Layer 4 - Room.** Not touched by this phase.
- **UI gate (S1338).** This phase touches `ui/**` and a settings surface, so both halves of the gate were checked. **Placement decision - present.** The strategic spec records the owner's ruling on this exact behaviour, §7 risk table: `Языковой сплит не догружается (нет сети, отказ Play) | .. | Переход не выполняется, причина показана (§11.7); ранее выбранный язык сохраняется`, restated as acceptance criterion §11.7 and sourced in §5 to `Владелец описал сценарий «предложить скачать, отказать при неудаче»`. The phase adds no new control and no new position - only a failure message on an existing row - so there is nothing further to place. **Screenshot - `screenshot deferred (no device)`.** `device-ready.ps1` reports `no-device` this session and this phase's own Done Criteria do not demand a shot, so the deferral is recorded and the phase continues. The visual check belongs to the manual gate that already covers this path: proving the download end to end needs a Play-installed build, not a debug APK.
- `AUDIT-P2: the download has no visible progress.` Between tapping "Restart" and either the restart or the failure toast, a slow fetch shows nothing, which reads as a dead button. Not fixed here: a progress affordance is a placement/visibility decision (CLAUDE.md Rule 10) and this phase carries no owner ruling on where it goes, so guessing it would ship an unreviewed UI. In scope for this ticket, so it is recorded here rather than parked as its own spec.

---

## Handoff Notes to Next Phase

A language the package does not carry can now arrive from Play, and a failed download leaves the user where they were. Proving it end to end needs a Play-installed build and stays on the manual list.

---

## Rollback Plan

Revert the phase commit and drop the dependency; language switching returns to applying immediately, which is correct for channels that carry every locale.
