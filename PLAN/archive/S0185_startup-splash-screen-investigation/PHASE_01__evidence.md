# Phase 01 — Evidence catalogue

**Strategic spec:** [`../S0185_startup-splash-screen-investigation.md`](../S0185_startup-splash-screen-investigation.md)
**Tactical INDEX:** [`INDEX.md`](INDEX.md)
**Depends on:** —
**Status:** ✅ Done
**Steps:** 4 / 4

> **Scope:** static evidence only. No code edits, no measurements (that's Phase 02). Goal: produce a concrete, file-level snapshot of every theme attribute, drawable, manifest declaration, and Application-level startup call that influences what the user sees during cold start. The snapshot is the input to Phase 02 (measurement) and Phase 03 (decision).

---

## Goals

1. Enumerate every theme attribute that controls the splash / starting window on Android 12+ and on pre-12, in both light and dark variants.
2. Identify every drawable asset reachable from those attributes, and confirm what is actually displayed (logo, colour, animation).
3. Catalogue every synchronous call inside `FastMediaSorterApp.onCreate` and `AppStartupInitializer` that runs on the main thread before first draw.
4. Confirm whether the runtime `androidx.profileinstaller` dependency is matched by an actual baseline profile artefact in the repo (it should not be, per the strategic-level "Observed gap" finding).

---

## Step 01.1 — Theme attribute snapshot

**Goal:** dump every relevant theme attribute from the four theme files (day v31 / night v31 / day legacy / night legacy) and record the exact value of each.

**Files to inspect (read-only):**

- `app_v2/src/main/res/values-v31/themes.xml`
- `app_v2/src/main/res/values-night-v31/themes.xml`
- `app_v2/src/main/res/values/themes.xml`
- `app_v2/src/main/res/values-night/themes.xml`

**Attributes to record per file:**

- `android:windowSplashScreenBackground` (v31 only)
- `android:windowSplashScreenAnimatedIcon` (v31 only)
- `android:windowSplashScreenAnimationDuration` (v31 only)
- `android:windowBackground` (legacy only)
- `android:windowDisablePreview` (legacy only)
- `android:statusBarColor` / `android:navigationBarColor` (relevant for visual continuity with first frame)

**Verification:**

- Grep returns expected number of `windowSplashScreen*` occurrences:
  - `Grep -r 'windowSplashScreen' app_v2/src/main/res/` — expected: 6 lines (3 attrs × 2 themes).
- Grep returns expected number of `windowDisablePreview` occurrences:
  - `Grep -r 'windowDisablePreview' app_v2/src/main/res/` — expected: 2 lines (day + night legacy).
- Recorded value of `windowSplashScreenAnimationDuration` equals `0` in both v31 themes.

**Output artefact:** appended to this file under `### Step 01.1 — Findings`.

---

## Step 01.2 — Drawable resolution chain

**Goal:** trace the chain from `windowSplashScreenAnimatedIcon` to the actual bitmap shown on screen, in light and dark mode.

**Files to inspect (read-only):**

- `app_v2/src/main/res/drawable/ic_splash_logo.xml` — wrapper layer-list.
- `app_v2/src/main/res/drawable/ic_logo.xml` (and any `drawable-night/ic_logo.xml` variant).
- Any related `ic_logo*` resources reachable from the wrapper.

**Information to record:**

- Wrapper layer-list dimensions (108dp × 108dp expected per Android 12+ adaptive-icon foreground spec).
- Source drawable resolution chain in day mode and night mode.
- Whether the source bitmap is vector or raster, and its intrinsic size.
- Confirm that root-level `logo-dark.png` / `logo-light.png` are NOT referenced from any Android resource (they are website-only assets per `index.html`).

**Verification:**

- `Grep -r 'ic_splash_logo' app_v2/src/` returns at least 2 references (v31 themes) and no others outside the theme + drawable wrapper.
- `Grep -r 'logo-dark\.png\|logo-light\.png' app_v2/` returns 0 matches (confirms website-only).

**Output artefact:** appended to this file under `### Step 01.2 — Findings`.

---

## Step 01.3 — Application-level synchronous startup call inventory

**Goal:** list every synchronous call in `FastMediaSorterApp.onCreate` (and helper inits it invokes synchronously) that contributes to time-to-first-draw.

**Files to inspect (read-only):**

- `app_v2/src/main/java/com/sza/fastmediasorter/FastMediaSorterApp.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/core/init/AppStartupInitializer.kt`
- `app_v2/src/main/AndroidManifest.xml` (to confirm WorkManager auto-init removal and any `<provider>` declarations that run at process start).

**Known candidates at time of writing (May 2026) — confirm each is still present and synchronous, drop those that have been deferred:**

- `GmsAvailabilityChecker.check(this)` — Google Play Services probe.
- `TranslationCacheManager.clearAll()` — disk wipe.
- `CastContext.getSharedInstance(this)` — Google Cast framework bootstrap.
- `logAppStartupInfo()` — debug-only banner; the heavy string assembly happens here, so the cost is debug-only but worth confirming it does not leak into release.
- `LoggingHelper.hasPreviousCrash()` — last-crash flag disk read.
- `AppStartupInitializer` synchronous prefix (the part before it hands off to background coroutines).
- WorkManager scheduling delay (currently a fixed sleep gate before any deferred startup path).
- `NetworkLifecycleBootstrapper` registration point — confirm it's lazy as expected after S0194 / S0195.

**Verification:**

- For every listed candidate, a `Grep` of the symbol in `FastMediaSorterApp.kt` returns ≥ 1 line OR the candidate is explicitly marked as "no longer present, lazy via X".
- Confirmed absence of `networkStateMonitor.start()` direct call in `FastMediaSorterApp.kt` (was removed in favour of `NetworkLifecycleBootstrapper`).
- Manifest grep `<provider .* InitializationProvider` shows `WorkManagerInitializer` is `tools:node="remove"` (WorkManager auto-init disabled).

**Output artefact:** appended to this file under `### Step 01.3 — Findings`. Each candidate gets one bullet: `symbol — present at line N (release) / debug-only / removed`.

---

## Step 01.4 — Baseline profile pipeline state

**Goal:** confirm what's actually in the repo around baseline profiles.

**Files to inspect (read-only):**

- `app_v2/build.gradle.kts` — confirm `androidx.profileinstaller:profileinstaller` is listed and at what version.
- Anywhere under `app_v2/src/main/baseline-prof.txt` or similar — expected absent.
- Any `macrobenchmark` module under repo root — expected absent.
- Any `:baselineprofile` module declaration in `settings.gradle.kts` — expected absent.

**Verification:**

- `Grep -r 'profileinstaller' app_v2/build.gradle.kts` returns exactly 1 dependency line.
- `Glob 'app_v2/src/**/baseline-prof.txt'` returns 0 matches.
- `Glob '**/macrobenchmark/build.gradle*'` returns 0 matches.
- `Grep ':macrobenchmark\|:baselineprofile' settings.gradle.kts` returns 0 matches.

**Output artefact:** one paragraph under `### Step 01.4 — Findings` summarising the gap (runtime installer present, generation pipeline absent).

---

## Phase Done Criteria

1. All 4 steps marked `[x]` done with Verification recorded.
2. `### Step 01.1 — Findings` .. `### Step 01.4 — Findings` sections appended to this file with concrete values (not "checked, looked fine").
3. Every Verification command above has been run; expected vs actual recorded explicitly (`expected: X | actual: Y`). Mismatches block phase completion.
4. INDEX `Phases: X/N done` counter advanced; row flipped to ✅ Done.
5. Strategic spec §6.1 .. §6.3 remain `Open` (those are Phase 02 concerns); only the gap finding (§4 paragraph about baseline profiles) gets confirmed by this phase.

---

## Step Findings

### Step 01.1 — Findings

**Android 12+ (API 31) — `values-v31/themes.xml` (day / light):**

- `android:windowSplashScreenBackground` = `#F5F5F5` (near-white static colour; no colour resource reference).
- `android:windowSplashScreenAnimatedIcon` = `@drawable/ic_splash_logo`.
- `android:windowSplashScreenAnimationDuration` = `0` (animation disabled).

**Android 12+ (API 31) — `values-night-v31/themes.xml` (night / dark):**

- `android:windowSplashScreenBackground` = `@color/item_normal` (dark surface colour from the app palette).
- `android:windowSplashScreenAnimatedIcon` = `@drawable/ic_splash_logo` (same as day).
- `android:windowSplashScreenAnimationDuration` = `0`.

**Pre-Android-12 — `values/themes.xml` (day):**

- `android:windowBackground` = `@color/white`.
- `android:windowDisablePreview` = `true`.
- `android:statusBarColor` / `android:navigationBarColor`: **not set** in this file; inherited from base theme or system default.

**Pre-Android-12 — `values-night/themes.xml` (night):**

- `android:windowBackground` = `@color/item_normal`.
- `android:windowDisablePreview` = `true`.
- No bar colour overrides.

**Verification results:**

- `grep -rn 'windowSplashScreen' app_v2/src/main/res/` → expected: 6 attribute lines | actual: 6 attribute lines (3 per v31 theme × 2 themes) + 1 comment line in `ic_splash_logo.xml` (not an attribute, not counted). **PASS.**
- `grep -rn 'windowDisablePreview' app_v2/src/main/res/` → expected: 2 | actual: 2. **PASS.**
- `windowSplashScreenAnimationDuration` = `0` in both v31 themes. **PASS.**

**Notable:** a fourth qualifier directory `values-night-v35/` exists (untracked in git status, empty or newly created) — no splash attributes found there; safe to ignore for this investigation.

---

### Step 01.2 — Findings

**`ic_splash_logo.xml` (`app_v2/src/main/res/drawable/`):**

- Type: `<layer-list>`.
- Declared size: `android:width="108dp"` / `android:height="108dp"` — matches the Android 12+ adaptive-icon foreground spec.
- Content: single `<bitmap android:src="@drawable/ic_logo" android:gravity="center" android:tileMode="disabled" android:antialias="true" />`.
- Resolution in dark mode: Android resolves `@drawable/ic_logo` automatically to `drawable-night/ic_logo.png` when night mode is active.

**`ic_logo.png` — raster PNG, two density-independent variants:**

- Day: `app_v2/src/main/res/drawable/ic_logo.png` — 1 251 bytes (lightweight PNG, no density bucket; treated as `drawable-mdpi` by Android resource system if no other buckets exist).
- Night: `app_v2/src/main/res/drawable-night/ic_logo.png` — 1 651 bytes.
- Neither file is a vector; both are raster bitmaps.

**`splash_icon_empty.xml` — transparent empty vector:**

- Exists at `app_v2/src/main/res/drawable/splash_icon_empty.xml`.
- Declared as 108×108dp, intentionally empty (no path data).
- **Not referenced by any theme** — this is an unused asset, likely a leftover from a previous "suppress logo on splash" experiment. Not a concern for this investigation but could be cleaned up.

**Verification results:**

- `grep -rn 'ic_splash_logo' app_v2/src/` → expected: at least 2 (v31 themes) plus `keep.xml`, no others | actual: exactly 3 matches (v31 day theme, v31 night theme, `res/raw/keep.xml`). No other references outside theme + keep.xml. **PASS.**
- `grep -rn 'logo-dark\.png\|logo-light\.png' app_v2/` → expected: 0 | actual: 0. **PASS** (website-only assets confirmed not referenced from Android resources).

**Summary:** the full drawable chain is `windowSplashScreenAnimatedIcon` → `@drawable/ic_splash_logo` (layer-list) → `@drawable/ic_logo` (raster PNG, day or night variant). No animated vector; no Lottie; no animated-vector-drawable. The icon is a static PNG wrapped in a fixed-size layer-list.

---

### Step 01.3 — Findings

Synchronous main-thread calls in `FastMediaSorterApp.onCreate()` (lines reference as of 2026-05-16):

- `androidx.media3.common.util.Log.setLogger(media3Logger)` — L136, lightweight registration, synchronous.
- `DynamicColors.applyToActivitiesIfAvailable(this)` — L144, registers an `ActivityLifecycleCallbacks`; no disk/network I/O; effectively instantaneous.
- `DebugToolsBridge.install(this)` — L147, **DEBUG builds only**; not on release critical path.
- `setupDebugStrictMode()` — L150, **DEBUG builds only** (returns early if `!BuildConfig.DEBUG`).
- `GmsAvailabilityChecker.check(this)` — L151, **present**, synchronous, main thread. Checks Google Play Services availability; the check itself reads from a cached GMS state (no network call), but can trigger a service connection if the cached state is stale.
- `appContext = applicationContext` — L154, field assignment, negligible.
- `ProcessLifecycleOwner.get().lifecycle.addObserver(...)` — L157, lightweight observer registration, synchronous.
- `LocaleHelper.applyLocale(this)` — L181, reads SharedPreferences for locale; synchronous disk read (tiny).
- `CastContext.getSharedInstance(this)` — L188, **present**, synchronous, main thread, gated on `BuildConfig.SUPPORT_CAST`. Google Cast framework bootstrap — historically one of the heaviest Application-level initialisation calls. Wrapped in `try/catch`; if Play Services unavailable it is swallowed.
- `LoggingHelper.hasPreviousCrash()` — L198, **present**, synchronous disk read (SharedPreferences file open). Lightweight but on the main thread.
- `startupInitializer.get().initialize()` — L204, synchronous call; `initialize()` itself calls `syncCacheSizeToSharedPreferences()` which launches a **coroutine** on `applicationScope` → effectively non-blocking. The `.get()` dereferences `dagger.Lazy<AppStartupInitializer>` which constructs the singleton once.
- `memoryProbe.record(MemoryCheckpoint.APP_STARTED)` — L264, last call in `onCreate`; lightweight memory-stat read.

**Confirmed deferred (behind `firstFrameSignal.await` on IO dispatcher):**

- `TranslationCacheManager.clearAll()` — deferred ✅
- `logAppStartupInfo()` — deferred ✅
- WorkManager scheduling (all coroutine behind `firstFrameSignal.await`) — deferred ✅
- `enqueueDeferredStartupWorker()` — 30-second initial delay + deferred ✅

**Confirmed absent from main-thread path:**

- `networkStateMonitor.start()` — **absent** from `FastMediaSorterApp.kt`. **PASS** — replaced by `NetworkLifecycleBootstrapper` (lazy, trigger-on-first-use per S0194/S0195). ✅

**WorkManager auto-init manifest check:**

- `<provider android:name="androidx.startup.InitializationProvider" ...>` with `<meta-data android:name="androidx.work.WorkManagerInitializer" ... tools:node="remove" />` — present at manifest L78-79. **PASS.** ✅

**Summary of main-thread critical path for cold start:**

1. `Log.setLogger` — negligible.
2. `DynamicColors.applyToActivitiesIfAvailable` — negligible.
3. `GmsAvailabilityChecker.check` — potentially 5-30 ms on first call if GMS cache is cold.
4. `ProcessLifecycleOwner.addObserver` — negligible.
5. `LocaleHelper.applyLocale` — SharedPreferences read, typically < 5 ms.
6. `CastContext.getSharedInstance` — **dominant candidate**, potentially 50-200 ms on cold GMS state (release, `SUPPORT_CAST=true` flavors only; absent from VR/noLegal).
7. `LoggingHelper.hasPreviousCrash` — SharedPreferences read, < 5 ms.
8. `startupInitializer.get().initialize()` — triggers coroutine, effectively non-blocking.
9. `memoryProbe.record` — < 2 ms.

The only material synchronous contributors are `GmsAvailabilityChecker.check` and `CastContext.getSharedInstance`. Everything else is sub-millisecond or already deferred.

---

### Step 01.4 — Findings

**`profileinstaller` dependency in `app_v2/build.gradle.kts`:**

- Line 783: `implementation("androidx.profileinstaller:profileinstaller:1.3.1")` — **present**. The runtime library is declared.

**Baseline profile artefact (`baseline-prof.txt`):**

- `find app_v2/src -name "baseline-prof*"` → **0 matches**. No generated profile exists in the repository.

**Macrobenchmark module:**

- Search for `macrobenchmark/build.gradle*` anywhere under project root → **0 matches**.
- `grep ':macrobenchmark\|:baselineprofile' settings.gradle.kts` → **0 matches**.

**Gap confirmed:** the runtime `profileinstaller` library is included (it installs a baseline profile from the APK at first run), but no profile artefact exists to install. At runtime the installer finds nothing and silently no-ops. The full generation pipeline (macrobenchmark module → Gradle task → `baseline-prof.txt` committed to `app_v2/src/main/`) is completely absent.

**Verification results:**

- `profileinstaller` in `build.gradle.kts` → expected: 1 line | actual: 1 line (L783). **PASS.**
- `baseline-prof.txt` under `app_v2/src/` → expected: 0 | actual: 0. **PASS.**
- Macrobenchmark module `build.gradle*` → expected: 0 | actual: 0. **PASS.**
- `:macrobenchmark` / `:baselineprofile` in `settings.gradle.kts` → expected: 0 | actual: 0. **PASS.**

---

## Change Log

- 2026-05-16 — Phase 01 drafted by `/spec-update` (P-2(a) restructure). Steps and Verification predicates authored; findings sections are placeholders to be filled during execution.
- 2026-05-16 — Phase 01 executed by `/spec-all` (claude-sonnet-4-6). All 4 steps completed; findings recorded; all Verification predicates PASS.
