# Phase 03 — Chrome Custom Tabs Routing for Google Domains

**Strategic spec:** [`../S0200_google-account-central-binding.md`](../S0200_google-account-central-binding.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 06
**Steps done:** 6 / 6
**Started:** 2026-05-16
**Completed:** 2026-05-16

---

## Objective

Introduce host-based routing for the in-app browser. Google domains (`google.com`, `accounts.google.com`, `youtube.com`, `music.youtube.com`, plus subdomains) are launched via `CustomTabsIntent`; non-Google sources continue through `WebViewAuthDialogFragment`. Add a `<queries>` manifest entry so `CustomTabsClient.getPackageName(..)` resolves on API 30+. Provide a `CctAvailabilityChecker` and a refusal dialog when no CCT-capable browser is installed.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (`androidx.browser:browser:1.8.0` on classpath).
- [ ] `WebViewAuthDialogFragment` is unchanged from current state (verified — no in-flight changes to file).
- [ ] `KnownAuthResources.kt` is unchanged.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/AndroidManifest.xml` | Modified | ≤ 250 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/browser/GoogleDomainMatcher.kt` | New | ≤ 90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/browser/CctAvailabilityChecker.kt` | New | ≤ 140 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/browser/GoogleDomainBrowserLauncher.kt` | New | ≤ 180 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/browser/CctUnavailableException.kt` | New | ≤ 25 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/auth/WebViewAuthDialogFragment.kt` | Modified | ≤ 360 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/LinkAutoDownloadResultPresenter.kt` | Modified | ≤ 320 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/auth/AuthSessionsListFragment.kt` | Modified | ≤ 230 |
| `app_v2/src/main/res/values/strings_s0200.xml` | New | ≤ 30 |
| `app_v2/src/main/res/values-ru/strings_s0200.xml` | New | ≤ 30 |
| `app_v2/src/main/res/values-uk/strings_s0200.xml` | New | ≤ 30 |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/browser/GoogleDomainMatcherTest.kt` | New | ≤ 160 |

> No file exceeds 500 LOC after edits. `WebViewAuthDialogFragment.kt` is currently 320; the edits add a ~30 line "Google host gate" → ≤ 360 projected.

---

## Steps

### Step 03.1 — Add CCT package-visibility `<queries>` to manifest

**Files:** `app_v2/src/main/AndroidManifest.xml`
**Depends on:** —

**Prompt for developer:**

> Inside the existing `<queries>` block (manifest currently has only `CREATE_DOCUMENT`, `IMAGE_CAPTURE`, `VIDEO_CAPTURE` per research), add the CCT browser query:
>
> ```xml
> <queries>
>     <!-- existing entries — DO NOT REMOVE -->
>
>     <!-- S0200 — Chrome Custom Tabs resolution on API 30+ -->
>     <intent>
>         <action android:name="android.intent.action.VIEW" />
>         <category android:name="android.intent.category.BROWSABLE" />
>         <data android:scheme="https" />
>     </intent>
>     <intent>
>         <action android:name="android.support.customtabs.action.CustomTabsService" />
>     </intent>
> </queries>
> ```
>
> Both intents are needed: the first lets `PackageManager` enumerate browsers; the second lets `CustomTabsClient.getPackageName` filter to CCT-capable ones.

**Verification:**

- `Grep -n "android.support.customtabs.action.CustomTabsService" app_v2/src/main/AndroidManifest.xml` matches exactly once.
- `Grep -n "<action android:name=\"android.intent.action.VIEW\""` matches at least once inside the `<queries>` block (use line context to verify it's inside `<queries>`, not inside an activity intent-filter).
- Build closure: `/build` → `standardDebug`. **PASS** (1m 22s after Steps 03.1-03.4 + 03.6).

**Status:** `[x]` done

**Step Log:**

- 2026-05-16 — Verification 2/2 PASS. Added two `<intent>` queries (ACTION_VIEW https-BROWSABLE + CustomTabsService action) to existing `<queries>` block in `app_v2/src/main/AndroidManifest.xml`. Dev log recorded.

---

### Step 03.2 — Implement `GoogleDomainMatcher`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/browser/GoogleDomainMatcher.kt`
**Depends on:** —

**Prompt for developer:**

> Pure object — no Hilt scope, no DI needed (matches sibling style of `KnownAuthResources`).
>
> ```kotlin
> /**
>  * Determines whether a URL host falls under Google's OAuth-only domain set.
>  *
>  * Per strategic ADR-4 (S0200), Google domain auth flows MUST route through Chrome Custom Tabs
>  * and never through in-app WebView. This matcher is the single source of truth for that decision.
>  *
>  * Match rule: exact host or `*.host` for every entry in [GOOGLE_AUTH_DOMAINS].
>  */
> object GoogleDomainMatcher {
>     private val GOOGLE_AUTH_DOMAINS = setOf(
>         "google.com",
>         "accounts.google.com",
>         "youtube.com",
>         "music.youtube.com"
>     )
>
>     fun isGoogleAuthHost(uri: Uri?): Boolean {
>         val host = uri?.host?.lowercase() ?: return false
>         return GOOGLE_AUTH_DOMAINS.any { d -> host == d || host.endsWith(".$d") }
>     }
>
>     fun isGoogleAuthUrl(url: String?): Boolean {
>         if (url.isNullOrBlank()) return false
>         return isGoogleAuthHost(runCatching { Uri.parse(url) }.getOrNull())
>     }
> }
> ```

**Verification:**

- `Grep -n "object GoogleDomainMatcher"` matches exactly once.
- `Grep -n "fun isGoogleAuthHost"` matches exactly once.
- Build closure: `/build` → `standardDebug`. **PASS** (1m 22s post-batch).

**Status:** `[x]` done

**Step Log:**

- 2026-05-16 — Verification 2/2 PASS. `GoogleDomainMatcher.kt` (~30 LOC) — single source of truth for Google-domain host predicate. Dev log recorded.

---

### Step 03.3 — Implement `CctAvailabilityChecker` and `CctUnavailableException`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/browser/CctAvailabilityChecker.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/browser/CctUnavailableException.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Create `CctUnavailableException.kt`:
>
> ```kotlin
> class CctUnavailableException(message: String) : Exception(message)
> ```
>
> Create `CctAvailabilityChecker.kt`:
>
> ```kotlin
> @Singleton
> class CctAvailabilityChecker @Inject constructor(
>     @ApplicationContext private val context: Context
> ) {
>     /**
>      * Returns the package name of a Chrome Custom Tabs-capable browser, or null when none is installed.
>      * Uses [CustomTabsClient.getPackageName] with `ignoreDefault=true` — picks ANY CCT-capable browser,
>      * not just the default. Strategic §3.2 hard requirement: any CCT-capable browser unlocks the feature.
>      */
>     fun resolveCctPackage(): String? {
>         val activityIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/"))
>         val pm = context.packageManager
>         val browsers = pm.queryIntentActivities(activityIntent, 0)
>             .map { it.activityInfo.packageName }
>             .distinct()
>         return CustomTabsClient.getPackageName(context, browsers, /* ignoreDefault = */ true)
>     }
>
>     fun isAvailable(): Boolean = resolveCctPackage() != null
> }
> ```
>
> Add a Timber.w when `resolveCctPackage` returns null — useful for diagnosing user reports.

**Verification:**

- `Grep -n "@Singleton" app_v2/src/main/java/com/sza/fastmediasorter/data/browser/CctAvailabilityChecker.kt` matches exactly once.
- `Grep -n "CustomTabsClient.getPackageName"` matches exactly once.
- `Grep -n "class CctUnavailableException"` matches exactly once.
- Build closure: `/build` → `standardDebug`. **PASS** (1m 22s post-batch).

**Status:** `[x]` done

**Step Log:**

- 2026-05-16 — Verification 3/3 PASS. `@Singleton`, `CustomTabsClient.getPackageName` with `ignoreDefault=true`, Timber warning when null. Files: `CctAvailabilityChecker.kt` (~40 LOC), `CctUnavailableException.kt` (~8 LOC). Dev log recorded.

---

### Step 03.4 — Implement `GoogleDomainBrowserLauncher`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/browser/GoogleDomainBrowserLauncher.kt`
**Depends on:** Step 03.3

**Prompt for developer:**

> ```kotlin
> @Singleton
> class GoogleDomainBrowserLauncher @Inject constructor(
>     @ApplicationContext private val appContext: Context,
>     private val cctChecker: CctAvailabilityChecker
> ) {
>     /**
>      * Launches [url] in Chrome Custom Tabs.
>      * @throws CctUnavailableException when no CCT-capable browser is installed.
>      */
>     fun launch(activityContext: Context, url: String) {
>         val cctPackage = cctChecker.resolveCctPackage()
>             ?: throw CctUnavailableException("No CCT-capable browser installed on device")
>         val intent = CustomTabsIntent.Builder()
>             .setShowTitle(true)
>             .setUrlBarHidingEnabled(false)
>             .build()
>         intent.intent.`package` = cctPackage
>         intent.launchUrl(activityContext, Uri.parse(url))
>     }
>
>     /**
>      * Routes [url] based on host: Google-domain → CCT (or throw), else → callback for WebView fallback.
>      */
>     fun routeAuthUrl(activityContext: Context, url: String, onWebViewFallback: (String) -> Unit) {
>         if (GoogleDomainMatcher.isGoogleAuthUrl(url)) {
>             launch(activityContext, url) // throws CctUnavailableException — caller handles
>         } else {
>             onWebViewFallback(url)
>         }
>     }
> }
> ```

**Verification:**

- `Grep -n "class GoogleDomainBrowserLauncher"` matches exactly once.
- `Grep -n "fun routeAuthUrl"` matches exactly once.
- `Grep -n "CustomTabsIntent.Builder"` matches exactly once.
- Build closure: `/build` → `standardDebug`. **PASS** (1m 22s post-batch).

**Status:** `[x]` done

**Step Log:**

- 2026-05-16 — Verification 3/3 PASS. `GoogleDomainBrowserLauncher.kt` (~50 LOC) — single host-route entry with WebView fallback lambda; throws `CctUnavailableException` from launch(). Dev log recorded.

---

### Step 03.5 — Route Google-domain URLs out of WebView in three call sites

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/auth/WebViewAuthDialogFragment.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/LinkAutoDownloadResultPresenter.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/auth/AuthSessionsListFragment.kt`
**Depends on:** Step 03.4

**Prompt for developer:**

> Wrap each `.newInstance(loginUrl).show(fm, TAG)` call site with a `GoogleDomainBrowserLauncher.routeAuthUrl(...)`. Pattern:
>
> ```kotlin
> // Before:
> WebViewAuthDialogFragment.newInstance(loginUrl, harvestMode).show(parentFragmentManager, TAG)
>
> // After:
> try {
>     googleDomainBrowserLauncher.routeAuthUrl(requireContext(), loginUrl) { fallbackUrl ->
>         WebViewAuthDialogFragment.newInstance(fallbackUrl, harvestMode).show(parentFragmentManager, TAG)
>     }
> } catch (e: CctUnavailableException) {
>     showCctUnavailableDialog()
> }
> ```
>
> `googleDomainBrowserLauncher` is `@Inject`-ed into each Fragment / Presenter.
>
> Harvest mode caveat: when `harvestMode = true` AND the URL is Google-domain, CCT cannot deliver cookies back to the host — the previous WebView contract is broken. Per the existing `KnownAuthResources` setup, YouTube's `loginUrl` IS `accounts.google.com/ServiceLogin?service=youtube`. For harvest mode, fall back to the WebView path UNCHANGED (do NOT call CCT). Implement with an explicit `harvestMode` skip:
>
> ```kotlin
> if (harvestMode) {
>     WebViewAuthDialogFragment.newInstance(loginUrl, harvestMode).show(parentFragmentManager, TAG)
>     return
> }
> // else: route via GoogleDomainBrowserLauncher
> ```
>
> This preserves YouTube media-URL extraction while still routing pure account sign-in to CCT.
>
> Add `showCctUnavailableDialog()` as a private helper in each Fragment, surfaced via `MaterialAlertDialog`:
> - Title: `R.string.s0200_cct_unavailable_title`
> - Message: `R.string.s0200_cct_unavailable_message`
> - Positive button: `R.string.s0200_cct_unavailable_retry` (re-evaluates on click via `onResume`)
> - Negative button: `R.string.cancel` (existing)

**Verification:**

- `Grep -n "googleDomainBrowserLauncher.routeAuthUrl" app_v2/src/main/java/com/sza/fastmediasorter/ui/share/auth/WebViewAuthDialogFragment.kt`: zero hits (the dialog itself does NOT self-route — only callers do).
- `Grep -rn "googleDomainBrowserLauncher.routeAuthUrl" app_v2/src/main/java/com/sza/fastmediasorter/ui/`: at least 3 matches (`LinkAutoDownloadResultPresenter`, `AuthSessionsListFragment`, and any other call site discovered).
- `Grep -rn "WebViewAuthDialogFragment.newInstance" app_v2/src/main/java/com/sza/fastmediasorter/ui/`: every match either has a preceding `if (harvestMode)` guard OR is inside the `onWebViewFallback` lambda.
- Build closure: `/build` → `standardDebug`. **PASS** (2m 46s).

**Status:** `[x]` done

**Step Log:**

- 2026-05-16 — Verification PASS. Three files wired: `AuthSessionsListFragment.kt` (4 call sites → helper `openAuthWebView`), `LinkAutoDownloadResultPresenter.kt` (2 call sites → helper `openAuthFlow` + ctor injection of launcher/checker), `ReceiveShareActivity.kt` (1 call site → helper `openAuthFlow`). Each gained @Inject for `GoogleDomainBrowserLauncher` and `CctAvailabilityChecker`, plus a local `showCctUnavailableDialog` (will be consolidated into shared `CctRefusalDialog` in Phase 06 Step 06.1). Build standardDebug PASS. Dev log recorded.

---

### Step 03.6 — Add EN/RU/UK strings + unit test for `GoogleDomainMatcher`

**Files:** `app_v2/src/main/res/values/strings_s0200.xml`, `app_v2/src/main/res/values-ru/strings_s0200.xml`, `app_v2/src/main/res/values-uk/strings_s0200.xml`, `app_v2/src/test/java/com/sza/fastmediasorter/data/browser/GoogleDomainMatcherTest.kt`
**Depends on:** Step 03.5

**Prompt for developer:**

> Create the three strings files. ONLY include keys needed by Phase 03 — later phases extend the same file. Pass `docs/COMMUNICATION_POLICY.md` §6 tone checklist before finalising.
>
> EN (`values/strings_s0200.xml`):
>
> ```xml
> <?xml version="1.0" encoding="utf-8"?>
> <resources>
>     <!-- S0200 — CCT unavailable refusal -->
>     <string name="s0200_cct_unavailable_title">Browser needed</string>
>     <string name="s0200_cct_unavailable_message">A browser that supports Chrome Custom Tabs is required to sign in to Google. Install a supported browser (Chrome, Firefox, Brave..) and try again. Other app features keep working.</string>
>     <string name="s0200_cct_unavailable_retry">Try again</string>
> </resources>
> ```
>
> RU (`values-ru/strings_s0200.xml`):
>
> ```xml
> <?xml version="1.0" encoding="utf-8"?>
> <resources>
>     <!-- S0200 — отказ из-за отсутствия CCT -->
>     <string name="s0200_cct_unavailable_title">Нужен браузер</string>
>     <string name="s0200_cct_unavailable_message">Чтобы войти в Google, нужен браузер с поддержкой Chrome Custom Tabs. Установите подходящий браузер (Chrome, Firefox, Brave..) и попробуйте ещё раз. Остальные функции приложения работают.</string>
>     <string name="s0200_cct_unavailable_retry">Попробовать ещё раз</string>
> </resources>
> ```
>
> UK (`values-uk/strings_s0200.xml`):
>
> ```xml
> <?xml version="1.0" encoding="utf-8"?>
> <resources>
>     <!-- S0200 — відмова через відсутність CCT -->
>     <string name="s0200_cct_unavailable_title">Потрібен браузер</string>
>     <string name="s0200_cct_unavailable_message">Щоб увійти в Google, потрібен браузер із підтримкою Chrome Custom Tabs. Встановіть сумісний браузер (Chrome, Firefox, Brave..) і спробуйте ще раз. Інші функції застосунку працюють.</string>
>     <string name="s0200_cct_unavailable_retry">Спробувати ще раз</string>
> </resources>
> ```
>
> Tone checklist applied: §2.3 dialog-pattern (what happened → what to try → optional next step); §6 no raw error codes; §6 every error has next step; `..` instead of `...`; `ё` in Russian where required.
>
> Create the test file `GoogleDomainMatcherTest.kt`. Cover:
> - `accounts.google.com` → true.
> - `myaccount.google.com` → true (subdomain).
> - `googleusercontent.com` → false (NOT a Google auth domain).
> - `youtube.com` → true.
> - `music.youtube.com` → true.
> - `youtu.be` → false (short-link host is NOT in scope).
> - empty / invalid URL → false.
> - null URL → false.

**Verification:**

- `Glob` — `values/strings_s0200.xml`, `values-ru/strings_s0200.xml`, `values-uk/strings_s0200.xml` all exist.
- `Grep -n "s0200_cct_unavailable_title" app_v2/src/main/res/values/strings_s0200.xml` matches exactly once.
- `Grep -n "s0200_cct_unavailable_title" app_v2/src/main/res/values-ru/strings_s0200.xml` matches exactly once.
- `Grep -n "s0200_cct_unavailable_title" app_v2/src/main/res/values-uk/strings_s0200.xml` matches exactly once.
- Locale audit closure: `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "s0200_cct_unavailable_"` exits 0. Expected exit 0. Actual: paste.
- Test run closure: `./gradlew :app_v2:testStandardDebugUnitTest --tests "*GoogleDomainMatcherTest*"` exits 0. **DEFERRED** — same project blocker as Phase 02 Step 02.5 (pre-existing broken sibling tests in `CloudFileOperationHandlerTest.kt` and `AtomicFileOperationStrategyTest.kt` prevent test-source-set compile). Test source verified by inspection (8 cases covering host/subdomain/short-link/empty/null).
- Strings pass COMMUNICATION_POLICY §6 checklist (manual review). **PASS**: §2.3 dialog formula (title + what to try + retry); no raw error codes; every error has next step; `..` not `...`; `ё` in Russian.

**Status:** `[x]` done (test run deferred)

**Step Log:**

- 2026-05-16 — Verification 6/7 PASS (3 strings files exist, key parity across EN/RU/UK confirmed via `check_strings_localized.ps1` exit 0). Test source exists with 8 `@Test` cases. Test run deferred per project-wide pre-existing test breakage. Strings tone-checked. Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles for `standardDebug` and `liteDebug` — run `/build`.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] `check_strings_localized.ps1 -KeyPrefix "s0200_cct"` exits 0.

---

## Handoff Notes to Next Phase

After Phase 03:
- Any caller that wants to open a Google-domain URL must inject `GoogleDomainBrowserLauncher` and call `routeAuthUrl(...)`.
- `WebViewAuthDialogFragment` itself is unchanged in functionality — it ONLY accepts URLs the caller passed. The Google-domain gate lives at every call site.
- Harvest mode (YouTube media-URL extraction) still uses WebView — intentional, see Step 03.5.
- The refusal UX dialog is implemented at three call sites with identical strings — Phase 06 will extract a shared helper into `helpers/CctRefusalDialog.kt` once the Settings card adds the fourth call site.

---

## Rollback Plan

Revert the phase commit. The WebView path is unchanged where the route wasn't applied; the routing wrapper just stops being called. CCT-related files are dead-code if the bindings are removed.
