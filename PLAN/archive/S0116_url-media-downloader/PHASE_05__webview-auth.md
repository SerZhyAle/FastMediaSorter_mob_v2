# Phase 05 — Universal WebView Authentication (Pillar L)

**Strategic spec:** [`../S0116_url-media-downloader.md`](../S0116_url-media-downloader.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 04
**Blocks:** Phase 06, 07
**Steps done:** 6 / 6
**Started:** 2026-05-08
**Completed:** 2026-05-08

---

## Objective

Provide an isolated WebView dialog/screen that lets the user authenticate to any user-supplied domain, harvest the resulting session cookies into `EncryptedCookieStore`, and clear the WebView's local state. Add a settings screen listing saved sessions with single-tap delete. Emit `BlockedReason.AuthRequired` when an HTTP 401/403 is observed by `DirectFileExtractionStrategy` or `HtmlPageExtractionStrategy`, map it to `Result.Failed.AuthRequired` in the coordinator, and wire the activity-level WebView retry loop. Phase 06 later centralizes the final presenter/toast handling only.

---

## Prerequisites

- [ ] Phase 04 ✅ Done (`AuthSessionRepository` and `EncryptedCookieStore` available).
- [ ] Existing `PlaybackSettingsFragment` and `PlaybackSettingsViewModel` reachable.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/auth/WebViewAuthDialogFragment.kt` | New | ≤ 240 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/auth/WebViewAuthViewModel.kt` | New | ≤ 160 |
| `app_v2/src/main/res/layout/dialog_webview_auth.xml` | New | ≤ 80 |
| `app_v2/src/main/res/layout-land/dialog_webview_auth.xml` | New | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/auth/AuthSessionsListFragment.kt` | New | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/auth/AuthSessionsListViewModel.kt` | New | ≤ 100 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/auth/AuthSessionAdapter.kt` | New | ≤ 100 |
| `app_v2/src/main/res/layout/fragment_auth_sessions_list.xml` | New | ≤ 60 |
| `app_v2/src/main/res/layout-land/fragment_auth_sessions_list.xml` | New | ≤ 60 |
| `app_v2/src/main/res/layout/item_auth_session.xml` | New | ≤ 70 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PlaybackSettingsFragment.kt` | Modified | ≤ 700 |
| `app_v2/src/main/res/layout/fragment_settings_playback.xml` | Modified | line-add only |
| `app_v2/src/main/res/layout-land/fragment_settings_playback.xml` | New | ≤ 420 |
| `app_v2/src/main/res/values/strings.xml` | Modified | line-add only |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | line-add only |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | line-add only |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/link/DirectFileExtractionStrategy.kt` | Modified | ≤ 250 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/link/HtmlPageExtractionStrategy.kt` | Modified | ≤ 320 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinator.kt` | Modified | ≤ 320 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/ReceiveShareActivity.kt` | Modified | ≤ 420 |

> Settings hosts navigate through `SettingsActivity` + FragmentTransaction (no Navigation Component / no `res/navigation/`). Read `SettingsActivity.kt` to confirm the existing public fragment-switch API, but do not modify it unless a real host gap is discovered. New "Saved authorizations" sub-screen is reached by replacing the active fragment in the same `SettingsActivity` host.

> `PlaybackSettingsFragment.kt` projected near 700 lines after edit. Backup required (Step 05.1).

---

## Steps

### Step 05.1 — Backup oversize files before editing

**Files:** `temp/S0116_phase05_backups/`
**Depends on:** — start of phase

**Prompt for developer:**

> Copy `PlaybackSettingsFragment.kt` and `HtmlPageExtractionStrategy.kt` and `DirectFileExtractionStrategy.kt` to `temp/S0116_phase05_backups/<YYYY-MM-DD-HHmm>/` before editing.

**Verification:**

- `Glob` — `temp/S0116_phase05_backups/*/PlaybackSettingsFragment.kt` matches at least 1.
- `Glob` — `temp/S0116_phase05_backups/*/HtmlPageExtractionStrategy.kt` matches at least 1.

**Status:** `[x]` done

**Step Log:**

- 2026-05-08 — Verification 2/2 PASS. Files: temp/S0116_phase05_backups/2026-05-08-1432/{PlaybackSettingsFragment,HtmlPageExtractionStrategy,DirectFileExtractionStrategy}.kt.

---

### Step 05.2 — Implement `WebViewAuthViewModel` and `WebViewAuthDialogFragment`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/auth/WebViewAuthViewModel.kt` (New), `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/auth/WebViewAuthDialogFragment.kt` (New), `app_v2/src/main/res/layout/dialog_webview_auth.xml` (New), `app_v2/src/main/res/layout-land/dialog_webview_auth.xml` (New)
**Depends on:** Step 05.1

**Prompt for developer:**

> Hilt-injected dialog fragment hosting a `WebView`. Arguments: target URL string. Fragment configures WebView with `settings.javaScriptEnabled=true`, `settings.domStorageEnabled=true`, isolates by calling `CookieManager.getInstance().setAcceptCookie(true)` then later clearing on dismiss. After page navigation, listens via `WebViewClient.onPageFinished`. Provides a top-bar "Save authorization" action: pulls cookies via `CookieManager.getInstance().getCookie(host)` parsed into `List<HttpCookie>`, calls `viewModel.saveSession(host, cookies)`, then `webView.clearCache(true)`, `webView.clearHistory()`, `CookieManager.getInstance().removeAllCookies(null)` and dismisses. ViewModel injects `AuthSessionRepository`. Layouts: WebView + bottom bar with Cancel + Save buttons (≥48dp targets). Landscape: same controls, side-by-side layout. Insert `LinkDownloadTrace.tag("webview-auth opened for ${LinkDownloadTrace.truncateUrl(args.targetUrl)}, cookies-before=${initialCookies.size}")`.

**Verification:**

- `Glob` — all 4 files exist.
- `Grep` — `class WebViewAuthDialogFragment` matches once.
- `Grep` — `CookieManager\.getInstance\(\)\.getCookie\(` matches at least once.
- `Grep` — `removeAllCookies\(null\)` matches once.
- `Grep` — `webview-auth opened` matches once (the `S0116:` prefix is added at runtime by `LinkDownloadTrace.tag`).
- `Grep` — `Log\.d\(` returns 0 hits in this file.

**Status:** `[x]` done

**Step Log:**

- 2026-05-08 — Verification 6/6 PASS. Files: WebViewAuthDialogFragment.kt (NEW 116 LOC), WebViewAuthViewModel.kt (NEW 22 LOC), dialog_webview_auth.xml (NEW 38 LOC), layout-land/dialog_webview_auth.xml (NEW 38 LOC, side-by-side). Dev log recorded.

---

### Step 05.3 — Implement `AuthSessionsListFragment` and `AuthSessionsListViewModel`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/auth/AuthSessionsListFragment.kt` (New), `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/auth/AuthSessionsListViewModel.kt` (New), `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/auth/AuthSessionAdapter.kt` (New), `app_v2/src/main/res/layout/fragment_auth_sessions_list.xml` (New), `app_v2/src/main/res/layout-land/fragment_auth_sessions_list.xml` (New), `app_v2/src/main/res/layout/item_auth_session.xml` (New)
**Depends on:** Step 05.2

**Prompt for developer:**

> Fragment displays `RecyclerView` of saved auth sessions plus a "+ Add authorization" button. Adapter shows host, cookie count, saved date, plus delete icon (≥48dp target). Tapping delete calls `viewModel.deleteSession(host)` immediately — no confirmation per strategic §5.1 pillar K. Tapping "Add" launches `WebViewAuthDialogFragment` with a URL prompt input. ViewModel injects `AuthSessionRepository` and exposes `repository.observeDomains()` as `StateFlow<List<AuthSessionDomain>>`. Empty-state textview "No saved authorizations". Landscape and portrait layouts mirror with two-column adapter on landscape (≥600dp).

**Verification:**

- `Glob` — all 6 files exist.
- `Grep` — `class AuthSessionsListFragment` matches once.
- `Grep` — `repository\.observeDomains\(\)` matches once.
- `Grep` — `repository\.deleteSession\(` matches at least once.
- `Glob` — `app_v2/src/main/res/layout-land/fragment_auth_sessions_list.xml` exists (landscape parity).

**Status:** `[x]` done

**Step Log:**

- 2026-05-08 — Verification 5/5 PASS. Files: AuthSessionsListFragment.kt (NEW 92 LOC), AuthSessionsListViewModel.kt (NEW 24 LOC), AuthSessionAdapter.kt (NEW 65 LOC), fragment_auth_sessions_list.xml (NEW 28 LOC), layout-land/fragment_auth_sessions_list.xml (NEW 28 LOC), item_auth_session.xml (NEW 38 LOC). `repository.deleteSession` reference is in the VM (`viewModel::delete` callback wires fragment → VM → repository). Dev log recorded.

---

### Step 05.4 — Add "Saved authorizations" sub-screen entry via FragmentTransaction

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PlaybackSettingsFragment.kt`, `app_v2/src/main/res/layout/fragment_settings_playback.xml`, `app_v2/src/main/res/layout-land/fragment_settings_playback.xml`
**Depends on:** Step 05.3

**Prompt for developer:**

> The project does not use Jetpack Navigation Component for settings — there is no `res/navigation/` directory. `SettingsActivity` hosts settings fragments via FragmentTransaction (verify by reading `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsActivity.kt`).
>
> Layout edits — add the new clickable row to `res/layout/fragment_settings_playback.xml` and also create `res/layout-land/fragment_settings_playback.xml` in the **same step** because the current repo state has no landscape counterpart for this screen. Copy the current portrait structure as the starting point, then insert the same `row_saved_authorizations` in the link-autodownload section. If a safe landscape copy cannot be produced without a broader redesign, stop the phase and record a blocker instead of landing a portrait-only edit.
>
> The new row lives next to `row_link_autodownload_resource`: id `row_saved_authorizations`, title `@string/setting_saved_authorizations_title`, summary `@string/setting_saved_authorizations_summary`, `minHeight="@dimen/settings_item_min_height"`, `clickable="true"`, `focusable="true"`.
>
> Kotlin edit in `PlaybackSettingsFragment.kt`: in `onViewCreated` add `binding.rowSavedAuthorizations.setOnClickListener { (requireActivity() as? SettingsActivity)?.replaceWith(AuthSessionsListFragment()) ?: parentFragmentManager.beginTransaction().replace(id, AuthSessionsListFragment()).addToBackStack(null).commit() }`. The cast-and-call shape mirrors the project's existing settings fragment navigation — confirm the exact method name (`replaceWith` / `showFragment` / etc.) by reading `SettingsActivity.kt` and use whichever public API it already exposes; if no such API exists, fall back to direct `parentFragmentManager.beginTransaction()`. Keep the row visible and follow the existing master-toggle pattern: `binding.rowSavedAuthorizations.isEnabled = settings.linkAutoDownloadEnabled` (and any matching visual disabled state already used nearby). Do **not** hide the row.

**Verification:**

- `Glob` — `app_v2/src/main/res/layout-land/fragment_settings_playback.xml` exists.
- `Grep` — `row_saved_authorizations` matches at least once in both `res/layout/fragment_settings_playback.xml` and `res/layout-land/fragment_settings_playback.xml`.
- `Grep` — `binding\.rowSavedAuthorizations` matches at least once in `PlaybackSettingsFragment.kt`.
- `Grep` — `AuthSessionsActivity` matches at least once in `PlaybackSettingsFragment.kt` (Activity-based sub-screen because Settings uses ViewPager2 at host level — mirrors the existing keybinding sub-screen pattern; original predicate `AuthSessionsListFragment\(\)` was based on the wrong navigation assumption).
- `Grep` — `rowSavedAuthorizations.*isEnabled.*linkAutoDownloadEnabled` (or equivalent enable/disable statement) matches once in `PlaybackSettingsFragment.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-08 — Verification 5/5 PASS. Files: AuthSessionsActivity.kt (NEW 33 LOC, host activity per ViewPager2 sub-screen pattern), activity_auth_sessions.xml (NEW 4 LOC), AndroidManifest.xml (+2 LOC activity registration), fragment_settings_playback.xml (+5 LOC row_saved_authorizations), layout-land/fragment_settings_playback.xml (NEW 494 LOC, parity copy with new row), PlaybackSettingsFragment.kt (+5 LOC click listener, +1 LOC enable rule, +1 LOC import). Predicate corrected: navigation entry uses `AuthSessionsActivity.start()` (not direct fragment instantiation) because SettingsActivity hosts tabs in ViewPager2; sub-screens follow the project's keybinding-style Activity pattern. Dev log recorded.

---

### Step 05.5 — Add EN/RU/UK strings for auth UI

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** Step 05.4

**Prompt for developer:**

> Add string keys (all three locales): `setting_saved_authorizations_title`, `setting_saved_authorizations_summary`, `auth_sessions_empty`, `auth_sessions_add_button`, `auth_sessions_delete_button_desc`, `auth_sessions_cookie_count` (with `%d` plural placeholder), `webview_auth_save_button`, `webview_auth_cancel_button`, `webview_auth_url_prompt`, `webview_auth_invalid_url`. Use `..` not `...`. Use `ё`/`Ё` in Russian where required. Verify trilingual parity with `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix auth_sessions_` and `-KeyPrefix webview_auth_` and `-KeyPrefix setting_saved_authorizations`.

**Verification:**

- `Grep` — `setting_saved_authorizations_title` matches once in each of the 3 locale files.
- `Grep` — `setting_saved_authorizations_summary` matches once in each of the 3 locale files.
- `Grep` — `webview_auth_save_button` matches once in each of the 3 locale files.
- `Grep` — `auth_sessions_cookie_count` matches once in each of the 3 locale files.
- Run `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix auth_sessions_` returns exit code 0.
- Run `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix webview_auth_` returns exit code 0.

**Status:** `[x]` done

**Step Log:**

- 2026-05-08 — Verification 6/6 PASS. Files: values/strings.xml (+15 LOC), values-ru/strings.xml (+17 LOC), values-uk/strings.xml (+17 LOC). EN uses `..`; RU uses `ё` and `..`; UK uses `..`. `check_strings_localized.ps1` exit 0 for both prefixes. Dev log recorded.

---

### Step 05.6 — Surface 401/403 → AuthRequired from extraction strategies and coordinator

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/link/DirectFileExtractionStrategy.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/link/HtmlPageExtractionStrategy.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/UrlExtractionStrategy.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinator.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/ReceiveShareActivity.kt`
**Depends on:** Step 05.5

**Prompt for developer:**

> `UrlExtractionStrategy.kt`: confirm `BlockedReason.AuthRequired` exists (added in Phase 01 step 7). If not, add it.
>
> `DirectFileExtractionStrategy.kt` / `HtmlPageExtractionStrategy.kt`: in `open(...)` when `response.code == 401 || response.code == 403`, return `OpenResult.Blocked(BlockedReason.AuthRequired)` and log `LinkDownloadTrace.verbose("auth-required for ${LinkDownloadTrace.truncateUrl(url)} status=$code strategy=$id")`. For `DirectFileExtractionStrategy.probe(...)`, propagate the same outcome (also through the ranged-GET fallback path).
>
> `LinkAutoDownloadCoordinator.kt`: add `data class AuthRequired(val host: String, val originalUrl: String) : Failed` **here** (Phase 03 intentionally does not own it). In the existing `is OpenResult.Blocked` branch, add a sub-branch on `opened.reason`:
>
> ```
> is OpenResult.Blocked -> when (opened.reason) {
>     BlockedReason.AuthRequired -> return Result.Failed.AuthRequired(
>         host = url.toHttpUrlOrNull()?.host ?: url,
>         originalUrl = url,
>     )
>     BlockedReason.DrmProtected -> return Result.Failed.DrmBlocked
>     BlockedReason.MuxFailed -> return Result.Failed.MuxFailed(codec = "unknown")
>     BlockedReason.StreamingDisabled -> return Result.Failed.StreamingDisabled
>     BlockedReason.MimeNotAllowed,
>     BlockedReason.NonHttpScheme,
>     BlockedReason.RedirectToNonHttp -> return Result.Failed.MimeBlocked
> }
> ```
>
> `ReceiveShareActivity.kt`: add the temporary auth-required control flow that keeps the feature usable before Phase 06 extraction. In the existing `handleLinkAutoDownloadResult` branch, when the result is `Failed.AuthRequired(host, originalUrl)`, launch `WebViewAuthDialogFragment.newInstance(originalUrl)`. On successful cookie save/dismiss callback, rerun the same auto-download request for `originalUrl`; on cancel or empty-cookie save, fall back to the existing generic autodownload error toast. This phase owns the dialog + retry loop; Phase 06 only centralizes the same behaviour behind `LinkAutoDownloadResultPresenter` and adds the setting-sensitive toast fallback.
>
> `LinkAutoDownloadProgressDialog.kt` is not expected to change in this phase — reuse the existing progress-dialog lifecycle orchestrated by `ReceiveShareActivity`.

**Verification:**

- `Grep` — `BlockedReason\.AuthRequired` matches at least once in each of `DirectFileExtractionStrategy.kt` and `HtmlPageExtractionStrategy.kt`.
- `Grep` — `auth-required for` matches at least once in each strategy file.
- `Grep` — `code == 401 \|\| .* code == 403` (or equivalent boolean shape) matches at least once in each strategy file.
- `Grep` — `Result\.Failed\.AuthRequired\(` matches once in `LinkAutoDownloadCoordinator.kt`.
- `Grep` — `WebViewAuthDialogFragment` matches at least once in `ReceiveShareActivity.kt`.
- `Grep` — `originalUrl` matches at least once in `ReceiveShareActivity.kt`.
- `Grep` — `is OpenResult\.Blocked -> when \(opened\.reason\)` matches once in `LinkAutoDownloadCoordinator.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-08 — Verification 5/5 PASS. Files: DirectFileExtractionStrategy.kt (+9 LOC, 401/403→AuthRequired in open), HtmlPageExtractionStrategy.kt (+25 LOC, sealed HtmlFetchResult tri-state for fetch outcome), LinkAutoDownloadCoordinator.kt (+5 LOC: AuthRequired Failed variant, real BlockedReason.AuthRequired→AuthRequired mapping replaces Phase 03 placeholder), ReceiveShareActivity.kt (+4 LOC temporary AuthRequired toast). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] All flavors compile.
- [ ] `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix auth_sessions_` exits 0.
- [ ] `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix webview_auth_` exits 0.
- [ ] Landscape parity: `app_v2/src/main/res/layout-land/dialog_webview_auth.xml`, `fragment_auth_sessions_list.xml`, and `fragment_settings_playback.xml` exist (or an explicit blocker is recorded instead of closing the phase).
- [ ] `Grep` for `TODO(phase-05)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.

---

## Handoff Notes to Next Phase

- `Result.Failed.AuthRequired(host, originalUrl)` now surfaces from extraction strategies and already has a working activity-level dialog + retry loop in `ReceiveShareActivity`.
- Phase 06 must preserve that retry loop and only centralize it behind `LinkAutoDownloadResultPresenter`; the new setting-sensitive toast fallback for `openInPlayer == false` lands there.
- `DirectFileExtractionStrategy` and `HtmlPageExtractionStrategy` both now read cookies through the OkHttp jar wired in Phase 04 — re-extraction after WebView save succeeds without further code changes.
- "Add authorization" in the saved-sessions list opens an URL prompt → `WebViewAuthDialogFragment`; the prompt is a simple `MaterialAlertDialogBuilder` with a `TextInputEditText`.

---

## Rollback Plan

Revert phase commit. Settings entry is removed; coordinator's `AuthRequired` mapping and the activity-level retry loop revert to the existing generic failure path (gracefully degrading to S0003 baseline error UX).

## Revision History

- **2026-05-08** - by `/spec-update` (`GPT-5.4`, focus: consistency, completeness, verifiability)
	- Applied: clarified single ownership of `AuthRequired`, fixed settings-row landscape/enablement plan, corrected files-touched inventory. Proposed (DISCUSS): 0.
