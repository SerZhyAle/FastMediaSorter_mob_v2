# Phase 05 — Preview-Only No-Login Notification

**Strategic spec:** [`../S0211_webview-auth-account-dedup-and-loop-prevention.md`](../S0211_webview-auth-account-dedup-and-loop-prevention.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** —
**Blocks:** —
**Steps done:** 3 / 3
**Started:** 2026-05-15
**Completed:** 2026-05-15

---

## Objective

Replace the misleading "Ошибка загрузки" / "Download failed" notification text shown by `LinkDownloadWorker` when the share-link path returned `SocialPreviewOnly` with a valid existing session, with a dedicated string: "Couldn't extract page content. No sign-in needed." (EN/RU/UK). The `isDismissedHost=true` branch keeps the existing failed wording (it is a different scenario — user explicitly opted out).

---

## Prerequisites

- [ ] Working tree clean or on the active DEBUG branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | ≤ +1 line |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | ≤ +1 line |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | ≤ +1 line |
| `app_v2/src/main/java/com/sza/fastmediasorter/worker/LinkDownloadWorker.kt` | Modified | ≤ 360 |

---

## Steps

### Step 05.1 — Add new trilingual string `link_download_notif_text_preview_only_signed_in`

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** —

**Prompt for developer:**

> Add a single new string in all three locale files, next to the existing `link_download_notif_text_failed` (search for that key first; place the new key directly after it). Suggested wording per `docs/COMMUNICATION_POLICY.md` §2 (failure message: state-what-happened + reassurance) and §6 (no blame, no CTA, neutral tone):
>
> - `values/strings.xml` (EN):
>   ```xml
>   <string name="link_download_notif_text_preview_only_signed_in">Couldn\'t extract page content. No sign-in needed.</string>
>   ```
>
> - `values-ru/strings.xml` (RU):
>   ```xml
>   <string name="link_download_notif_text_preview_only_signed_in">Не удалось извлечь содержимое страницы. Вход в аккаунт не требуется.</string>
>   ```
>
> - `values-uk/strings.xml` (UK):
>   ```xml
>   <string name="link_download_notif_text_preview_only_signed_in">Не вдалося отримати вміст сторінки. Вхід в акаунт не потрібен.</string>
>   ```
>
> Russian text: `..` allowed as ellipsis style, but here there is no trailing ellipsis — both sentences end in a period. Verify Russian uses `ё` correctly (none of the words in this string require `ё`).
>
> Tone-checklist (`docs/COMMUNICATION_POLICY.md` §6) — verify each:
> - Friendly neutral tone: ✓ (statement of fact, no blame).
> - No imperative CTA: ✓ ("No sign-in needed" is reassurance, not an ask).
> - No alarm words: ✓ ("Couldn't extract" replaces "Error").
> - Concise: ✓ (≤ 80 chars per locale).
> - Localizable without placeholders: ✓.

**Verification:**

- `Grep -n "link_download_notif_text_preview_only_signed_in" values/strings.xml` — exactly one match.
- `Grep -n "link_download_notif_text_preview_only_signed_in" values-ru/strings.xml` — exactly one match.
- `Grep -n "link_download_notif_text_preview_only_signed_in" values-uk/strings.xml` — exactly one match.
- Run `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "link_download_notif_text_preview"` — exit code 0.

**Status:** `[x]` done

**Step Log:**

- 2026-05-15 — Verification 4/4 PASS. Used `scripts/utils/set-android-string.ps1 -CreateIfMissing` for all three locales. `check_strings_localized.ps1` exit 0. Tone-check PASS (statement of fact, no CTA, no blame, ≤80 chars). Dev log recorded.

---

### Step 05.2 — Switch worker notification text for `hadExistingSession=true`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/worker/LinkDownloadWorker.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> Modify the `SocialPreviewOnly` arm of `postResultNotification` (currently around lines 236–259). Split the existing single branch into two:
>
> ```kotlin
> is LinkAutoDownloadCoordinator.Result.Failed.SocialPreviewOnly -> {
>     when {
>         result.hadExistingSession -> {
>             // S0211: valid session but extractor cannot parse this page. Honest notice,
>             // no Sign-In CTA — re-authenticating would not change the outcome.
>             builder
>                 .setContentTitle(context.getString(R.string.link_download_notif_title_done))
>                 .setContentText(
>                     context.getString(R.string.link_download_notif_text_preview_only_signed_in),
>                 )
>         }
>         isDismissedHost -> {
>             // User explicitly opted out of re-auth for this host — quiet "failed" notice.
>             builder
>                 .setContentTitle(context.getString(R.string.link_download_notif_title_done))
>                 .setContentText(context.getString(R.string.link_download_notif_text_failed))
>         }
>         else -> {
>             // No session yet — heads-up sign-in notification with re-auth CTA.
>             builder
>                 .setContentTitle(context.getString(R.string.link_download_notif_title_sign_in_needed))
>                 .setContentText(
>                     context.getString(R.string.link_download_notif_text_sign_in_needed, result.host),
>                 )
>                 .addAction(buildSignInAction(result.originalUrl))
>         }
>     }
> }
> ```
>
> Note the ordering: `hadExistingSession` is checked FIRST. If a user with a valid session has previously "dismissed" the host, we still prefer the honest "couldn't extract" message — re-prompting them is pointless when the session is valid.
>
> Insert `Timber.d("S0211: LinkDownloadWorker notify preview-only-signed-in host=%s", result.host)` inside the new `hadExistingSession -> { … }` block (debug verification tag, per CLAUDE.md). Removed by `/spec-check` on Verified.
>
> Update the inline comment that previously referenced the duplicate-account loop (lines 237–246 of the old version) — keep the historical reference but trim it now that the loop is closed end-to-end:
>
> ```kotlin
> // S0211: worker cannot show dialogs. Three branches:
> // - extractor already ran with a valid stored session (hadExistingSession) — honest
> //   notice, no Sign-In CTA. Mirrors LinkAutoDownloadResultPresenter.presentSocialPreviewOnly.
> // - "don't ask" recorded for this host — quiet failure notification.
> // - no session yet — heads-up sign-in notification with re-auth CTA.
> ```

**Verification:**

- `Grep -n "link_download_notif_text_preview_only_signed_in" LinkDownloadWorker.kt` — exactly one match.
- `Grep -n "Timber.d\(\"S0211:" LinkDownloadWorker.kt` — exactly one match.
- `Grep -n "isDismissedHost \|\| result.hadExistingSession" LinkDownloadWorker.kt` — zero matches (the merged condition has been split).
- File still ≤ 360 LOC after edit.

**Status:** `[x]` done

**Step Log:**

- 2026-05-15 — Verification 4/4 PASS. Files: LinkDownloadWorker.kt (LOC 334, +~6 net). Branch split: hadExistingSession (signed-in honest notice) → isDismissedHost (quiet fail) → else (sign-in CTA). Dev log recorded.

---

### Step 05.3 — Compile + locale audit

**Files:** —
**Depends on:** Steps 05.1, 05.2

**Prompt for developer:**

> Run `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "link_download_notif_text_preview"` — exit 0.
>
> Run `/build` for `standardDebug`. Record `expected: BUILD SUCCESSFUL | actual: <result>` in chat.

**Verification:**

- `check_strings_localized.ps1` exit code 0.
- `expected: BUILD SUCCESSFUL | actual: <result>` recorded.

**Status:** `[x]` done

**Step Log:**

- 2026-05-15 — Locale audit exit 0; `expected: BUILD SUCCESSFUL | actual: BUILD SUCCESSFUL — PASS`.

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] Project compiles — `standardDebug` PASS.
- [ ] `scripts/check_strings_localized.ps1 -KeyPrefix "link_download_notif_text_preview"` PASS.
- [ ] `Grep` for `TODO(phase-05)` returns zero hits.
- [ ] Dev log entry added for every changed file via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

Phase 06 runs catalogue + dev log + functionality log finalisation.

---

## Rollback Plan

Revert phase commit — removes the new string and reverts the worker arm to the merged condition. Other locales unaffected.
