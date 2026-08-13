# Phase 03 — social-auth-ux

**Strategic spec:** [`../S0151_instagram-threads-link-extraction-and-auth.md`](../S0151_instagram-threads-link-extraction-and-auth.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 4 / 4
**Started:** —
**Completed:** —

---

## Objective

Surface `Result.Failed.SocialPreviewOnly` to the user as an actionable dialog ("sign in" or "sign in again") followed by a retry, with fallback to a "content unavailable" toast when the user declines or when the dismissal store is set. Make the re-auth dialog text explanatory — the user sees WHY they're being asked to sign in again (strategic §3.1.5, §6.4 Resolved). Add all required trilingual strings. The root cause of the "annoying repeat prompt" symptom is delegated to **S0155 (link-auth-multi-account)**.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done (`Result.Failed.SocialPreviewOnly` defined in coordinator).
- [ ] `docs/COMMUNICATION_POLICY.md` §2 and §6 reviewed for the new strings.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | — |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | — |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | — |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/LinkAutoDownloadResultPresenter.kt` | Modified | ≤ 250 |

---

## Steps

### Step 03.1 — Add trilingual strings for S0151 UX

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** — start of phase

**Prompt for developer:**

> Add the following string keys to all three `strings.xml` files. Insert them after the existing `s0116_toast_auth_required` entry. English values below; RU/UK translations follow immediately.
>
> **English (`values/strings.xml`):**
> ```xml
> <string name="s0151_toast_content_unavailable">Couldn\'t get the content at this link</string>
> <string name="s0151_dialog_auth_title">Sign in to %1$s?</string>
> <string name="s0151_dialog_reauth_title">Refresh sign-in for %1$s?</string>
> <string name="s0151_dialog_message">The real content from this link needs a sign-in — the preview image was skipped.</string>
> <string name="s0151_dialog_reauth_message">The saved sign-in for %1$s may have expired. Signing in again lets the app fetch the actual content.</string>
> <string name="s0151_dialog_reauth_positive">Sign in again</string>
> ```
>
> **Russian (`values-ru/strings.xml`):**
> ```xml
> <string name="s0151_toast_content_unavailable">Не удалось получить контент по этой ссылке</string>
> <string name="s0151_dialog_auth_title">Войти в %1$s?</string>
> <string name="s0151_dialog_reauth_title">Обновить вход в %1$s?</string>
> <string name="s0151_dialog_message">Настоящий контент по этой ссылке требует входа — превью-картинка пропущена.</string>
> <string name="s0151_dialog_reauth_message">Сохранённый вход в %1$s мог устареть. Войдите заново, чтобы приложение смогло получить реальный контент.</string>
> <string name="s0151_dialog_reauth_positive">Войти заново</string>
> ```
>
> **Ukrainian (`values-uk/strings.xml`):**
> ```xml
> <string name="s0151_toast_content_unavailable">Не вдалося отримати контент за цим посиланням</string>
> <string name="s0151_dialog_auth_title">Увійти в %1$s?</string>
> <string name="s0151_dialog_reauth_title">Оновити вхід у %1$s?</string>
> <string name="s0151_dialog_message">Справжній контент за цим посиланням потребує входу — прев\'ю-картинку пропущено.</string>
> <string name="s0151_dialog_reauth_message">Збережений вхід у %1$s міг застаріти. Увійдіть знову, щоб застосунок міг отримати реальний контент.</string>
> <string name="s0151_dialog_reauth_positive">Увійти знову</string>
> ```
>
> **Tone gate (COMMUNICATION_POLICY §6):** strings must pass before commit — friendly explanation, not alarmist; no exclamation marks; no "error" wording; user understands what happened and what to do next.
>
> After adding keys, run the locale audit:
> `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "s0151_"`. Exit code 1 = missing key, must fix before committing.

**Verification:**

- `Grep` — `s0151_toast_content_unavailable` matches in all three `strings.xml` files.
- `Grep` — `s0151_dialog_reauth_title` matches in all three `strings.xml` files.
- `Grep` — `s0151_dialog_reauth_positive` matches in all three `strings.xml` files.
- Locale audit script exits with code 0.
- Strings pass COMMUNICATION_POLICY §6 checklist (friendly, no alarmist wording, action-oriented positive button).

**Status:** `[ ]` not done

---

### Step 03.2 — Handle `SocialPreviewOnly` in `LinkAutoDownloadResultPresenter`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/LinkAutoDownloadResultPresenter.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Make the following changes to `LinkAutoDownloadResultPresenter`:
>
> **1. Add constructor parameters:**
> ```kotlin
> @Singleton
> class LinkAutoDownloadResultPresenter @Inject constructor(
>     @ApplicationContext private val appContext: Context,
>     private val settings: SettingsRepository,
>     private val authOfferDismissalStore: com.sza.fastmediasorter.data.link.auth.AuthOfferDismissalStore,
> )
> ```
> `AuthOfferDismissalStore` is already `@Singleton` in the DI graph — no new `@Module` entry needed.
>
> **2. Add imports:**
> ```kotlin
> import com.google.android.material.dialog.MaterialAlertDialogBuilder
> import com.sza.fastmediasorter.data.link.auth.AuthOfferDismissalStore
> import com.sza.fastmediasorter.data.link.auth.KnownAuthResources
> import androidx.lifecycle.lifecycleScope
> import kotlinx.coroutines.launch
> ```
>
> **3. Add branch to `present()` `when` block:**
> ```kotlin
> is LinkAutoDownloadCoordinator.Result.Failed.SocialPreviewOnly ->
>     presentSocialPreviewOnly(result, hostActivity, onAuthRetryRequested)
> ```
>
> **4. Add `renderFailureReason()` branch** (inside the existing `when` expression):
> ```kotlin
> is LinkAutoDownloadCoordinator.Result.Failed.SocialPreviewOnly ->
>     appContext.getString(R.string.s0151_toast_content_unavailable)
> ```
>
> **5. Add private `presentSocialPreviewOnly()` method:**
> ```kotlin
> private fun presentSocialPreviewOnly(
>     result: LinkAutoDownloadCoordinator.Result.Failed.SocialPreviewOnly,
>     hostActivity: AppCompatActivity,
>     onAuthRetryRequested: suspend (originalUrl: String) -> Unit,
> ) {
>     if (authOfferDismissalStore.isDismissed(result.host)) {
>         toast(R.string.s0151_toast_content_unavailable)
>         return
>     }
>     val loginUrl = KnownAuthResources.matchHost(result.host)?.loginUrl ?: result.originalUrl
>     val titleRes = if (result.hadExistingSession)
>         R.string.s0151_dialog_reauth_title else R.string.s0151_dialog_auth_title
>     val msgRes = if (result.hadExistingSession)
>         R.string.s0151_dialog_reauth_message else R.string.s0151_dialog_message
>     val positiveLabel = appContext.getString(
>         if (result.hadExistingSession) R.string.s0151_dialog_reauth_positive
>         else R.string.auth_offer_dialog_add,
>     )
>     MaterialAlertDialogBuilder(hostActivity)
>         .setTitle(appContext.getString(titleRes, result.host))
>         .setMessage(appContext.getString(msgRes, result.host))
>         .setCancelable(false)
>         .setPositiveButton(positiveLabel) { _, _ ->
>             hostActivity.supportFragmentManager.setFragmentResultListener(
>                 WebViewAuthDialogFragment.RESULT_KEY,
>                 hostActivity,
>             ) { _, _ ->
>                 hostActivity.supportFragmentManager
>                     .clearFragmentResultListener(WebViewAuthDialogFragment.RESULT_KEY)
>                 hostActivity.lifecycleScope.launch {
>                     runCatching { onAuthRetryRequested(result.originalUrl) }
>                 }
>             }
>             runCatching {
>                 WebViewAuthDialogFragment.newInstance(loginUrl)
>                     .show(hostActivity.supportFragmentManager, "s0151_webview_reauth")
>             }.onFailure {
>                 Timber.w(it, "S0151: reauth WebView launch failed")
>                 toast(R.string.s0151_toast_content_unavailable)
>             }
>         }
>         .setNegativeButton(R.string.auth_offer_dialog_skip) { _, _ ->
>             authOfferDismissalStore.markDismissed(result.host)
>             toast(R.string.s0151_toast_content_unavailable)
>         }
>         .show()
> }
> ```
>
> Note: `s0151_dialog_message` takes no format arg for the no-session case; `s0151_dialog_reauth_message` takes `%1$s` (host). Confirm the correct `getString` overload is used per string.

**Verification:**

- `Grep` — `presentSocialPreviewOnly` matches in `LinkAutoDownloadResultPresenter.kt`.
- `Grep` — `AuthOfferDismissalStore` matches as constructor parameter in `LinkAutoDownloadResultPresenter.kt`.
- `Grep` — `s0151_toast_content_unavailable` matches in `LinkAutoDownloadResultPresenter.kt`.
- `Grep` — `s0151_webview_reauth` tag matches in `LinkAutoDownloadResultPresenter.kt`.
- `Grep` — `Log\.d\(` returns zero hits in `LinkAutoDownloadResultPresenter.kt`.

**Status:** `[ ]` not done

---

### Step 03.3 — Build and insert debug verification tags

**Files:** (build only — no new source edits beyond inserting Timber tags)
**Depends on:** Steps 03.1, 03.2

**Prompt for developer:**

> Run `/build` (standard debug flavor). The build must succeed with zero compile errors.
>
> Then insert debug verification tags per CLAUDE.md §"Debug Verification Tags" — the ticket is about to enter `BlockNeedUserTest`, so one `Timber.d("S0151: ...")` tag per changed flow entry:
>
> - In `KnownAuthResources.isVideoFirstHost()` entry point — not applicable (pure predicate, not a flow entry).
> - In `HtmlPageExtractionStrategy.open()`, just before `return OpenResult.SocialPreviewOnly(host = host)`:
>   ```kotlin
>   Timber.d("S0151: html-strategy social-preview-only host=$host")
>   ```
> - In `InvisibleWebViewExtractionStrategy.open()`, just before `return OpenResult.SocialPreviewOnly(host = host)`:
>   ```kotlin
>   Timber.d("S0151: dynamic-strategy social-preview-only host=$host")
>   ```
> - In `LinkAutoDownloadCoordinator.handleUrl()`, just before `return Result.Failed.SocialPreviewOnly(...)`:
>   ```kotlin
>   Timber.d("S0151: coordinator returning SocialPreviewOnly host=$previewHost hadSession=$hadSession")
>   ```
> - In `LinkAutoDownloadResultPresenter.presentSocialPreviewOnly()`, entry:
>   ```kotlin
>   Timber.d("S0151: presenter SocialPreviewOnly host=${result.host} hadExistingSession=${result.hadExistingSession}")
>   ```
>
> These tags are removed when the spec leaves `BlockNeedUserTest` (by `/spec-check` or `/spec-update`).

**Verification:**

- `/build` returns success (exit code 0).
- `Grep "Timber.d(\"S0151:"` — matches exactly 4 lines across `.kt` files (html strategy, dynamic strategy, coordinator, presenter).
- `Grep "Log\.d\("` — returns zero hits across all modified files.

**Status:** `[ ]` not done

---

### Step 03.4 — Revise the re-auth dialog wording to be explanatory

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** Step 03.1

**Context:** strategic §3.1.5 (rewritten 2026-05-11), §6.4 Resolved, §11.7. The owner's field-incident complaint was that the re-auth offer feels like an unexplained repeat right after a successful sign-in. §6.4 Resolved: no "freshness" heuristic — instead, make the dialog text explain WHY the user is being asked to sign in again (saved session was applied but the page returned only a preview; it may have expired, or this particular post needs a different account — the latter foreshadows S0155). The root-cause fix lives in S0155 (per-host multiple accounts); this step is the temporary, in-S0151 wording fix.

**Prompt for developer:**

> Replace the value of `s0151_dialog_reauth_message` in all three locale files with the more explanatory text below. Other S0151 strings (added in Step 03.1) stay as-is. No new keys.
>
> **English (`values/strings.xml`):**
> ```xml
> <string name="s0151_dialog_reauth_message">We applied your saved sign-in for %1$s, but the page returned only a preview. The session may have expired, or this post needs a different account. Sign in again?</string>
> ```
>
> **Russian (`values-ru/strings.xml`):**
> ```xml
> <string name="s0151_dialog_reauth_message">Применили сохранённый вход в %1$s, но страница вернула только превью. Возможно, сессия устарела, либо этот пост требует другого аккаунта. Войти заново?</string>
> ```
>
> **Ukrainian (`values-uk/strings.xml`):**
> ```xml
> <string name="s0151_dialog_reauth_message">Застосували збережений вхід у %1$s, але сторінка повернула лише прев\'ю. Можливо, сесія застаріла, або цей пост потребує іншого облікового запису. Увійти знову?</string>
> ```
>
> **Tone gate (COMMUNICATION_POLICY §6):** the new wording must pass before commit — explains what happened ("we tried, this is what came back"), names two plausible reasons (session expired / different account needed), ends with an action question. No alarmist wording, no "error".
>
> After updating, run the locale audit:
> `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "s0151_"`. Exit code 1 = missing key, must fix before committing.
>
> No `.kt` changes in this step. Re-run `/build` (standard debug) — it must still succeed with zero compile errors.

**Verification:**

- `Grep` — `s0151_dialog_reauth_message` matches in all three `strings.xml` files; the matching line contains the substring `different account` (EN), `другого аккаунта` (RU), or `іншого облікового запису` (UK) respectively.
- Locale audit script exits with code 0.
- `Grep "isSessionRecentlyApplied"` — returns zero hits across `.kt` (this step's earlier wording is fully obsolete; the helper must not be added).
- `/build` returns success (exit code 0).
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every step above is `[x] done`.
- [ ] Project compiles — `/build` success confirmed in step 03.3.
- [ ] Locale audit `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "s0151_"` exits 0.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entries added for all files in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] Spec status advanced: `pwsh -File scripts/spec_catalog/update.ps1 -Id S0151 -Status BlockNeedUserTest`.

---

## Handoff Notes to Next Phase

- Ticket moves to `BlockNeedUserTest`. On-device testing verifies:
  - Sharing an Instagram reel/post link → only preview extracted → `Timber.d("S0151: html-strategy social-preview-only ...")` appears in logcat → auth dialog shown.
  - Sharing a `threads.com` link → recognized as Threads → proactive auth offer fires.
  - §11.7 — when the re-auth dialog DOES appear (saved session present + extraction failed), its text reads as an explanation (mentions "we applied your saved sign-in", "the page returned only a preview", names two plausible reasons), not as a blank repeat. No "freshness" heuristic is implemented — the symptom of an annoying right-after-login repeat is owned by S0155.
  - §11.8 — after the user declines the (re)login offer, subsequent shares of links from the same host still show the "content unavailable" toast (not a silent no-op).
  - Strategic §6.1 research item answered: does `dynamic` strategy with saved cookies yield real video URLs?
- Catalog regen is deferred to Phase 04 (final cleanup).
- Remove `Timber.d("S0151:` tags and advance to `Verified` via `/spec-check S0151` after user test passes.

---

## Rollback Plan

Revert phase commit(s). No data migration. No Room schema change. String keys added but unused if Phase 02 is reverted too.
