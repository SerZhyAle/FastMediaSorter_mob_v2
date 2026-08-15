# Phase 03 — account-naming

**Strategic spec:** [`../S0155_link-auth-multi-account.md`](../S0155_link-auth-multi-account.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 02 (session-context)
**Blocks:** Phase 04, Phase 06
**Steps done:** 0 / 4
**Started:** —
**Completed:** —

---

## Objective

After the user completes WebView login and taps "Save authorization", show a "Name this account" dialog with a best-effort cookie-derived hint. The confirmed name is stored as `displayName`; the stable `accountId` is a UUID generated at save time. `WebViewAuthViewModel` is updated to the new `(host, accountId, displayName, cookies)` signature.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/link/auth/AccountNameHintExtractor.kt` | New | ≤ 70 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/auth/WebViewAuthViewModel.kt` | Modified | ≤ 55 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/auth/WebViewAuthDialogFragment.kt` | Modified | ≤ 280 |
| `app_v2/src/main/res/values/strings_s0155.xml` | New | ≤ 30 lines |
| `app_v2/src/main/res/values-ru/strings_s0155.xml` | New | ≤ 30 lines |
| `app_v2/src/main/res/values-uk/strings_s0155.xml` | New | ≤ 30 lines |

---

## Steps

### Step 03.1 — Create AccountNameHintExtractor

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/link/auth/AccountNameHintExtractor.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create `AccountNameHintExtractor` as a plain `object` (no DI — pure function). It attempts to extract a human-readable account name hint from a list of `HttpCookie` objects.
>
> Logic (in order, return first non-blank match):
> 1. Cookie named `username` (any host) — value is often the plaintext username.
> 2. Cookie named `ds_user` (Instagram/Threads) — value is often the plaintext username.
> 3. Cookie named `twid` (X/Twitter) — value is `u=<id>`, strip `u=` prefix; result is a numeric ID, not ideal but better than nothing.
> 4. Cookie named `reddit_session` or `reddit_user` — not useful for username extraction.
> 5. If none found, return `null`.
>
> Apply `@JvmStatic` if it helps test-friendliness, but `object` is fine for production use.
>
> ```kotlin
> object AccountNameHintExtractor {
>     fun extract(cookies: List<java.net.HttpCookie>): String? {
>         val byName = cookies.associateBy { it.name.lowercase() }
>         return listOf("username", "ds_user")
>             .firstNotNullOfOrNull { byName[it]?.value?.takeIf { v -> v.isNotBlank() } }
>             ?: byName["twid"]?.value?.removePrefix("u=")?.takeIf { it.isNotBlank() }
>     }
> }
> ```

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/data/link/auth/AccountNameHintExtractor.kt` exists.
- `Grep` — `object AccountNameHintExtractor` present exactly once.
- `Grep` — `fun extract(cookies: List<` present.
- `Grep` — `Log\.d\(` returns zero hits in this file.

**Status:** `[ ]` not done

---

### Step 03.2 — Update WebViewAuthViewModel for multi-account save

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/auth/WebViewAuthViewModel.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Add a new `saveSession(host: String, accountId: String, displayName: String, cookies: List<HttpCookie>)` method that delegates to the new `AuthSessionRepository.saveSession(host, accountId, displayName, cookies)`. Keep the old `saveSession(domain, cookies)` method as deprecated (it still delegates to the old `repository.saveSession(domain, cookies)` for backward compat until no callers remain). Import `java.util.UUID` is not needed here — `accountId` is generated in the Fragment (caller).

**Verification:**

- `Grep` — `fun saveSession(host: String, accountId: String, displayName: String` present in `WebViewAuthViewModel.kt`.
- `Grep` — `Log\.d\(` returns zero hits in this file.

**Status:** `[ ]` not done

---

### Step 03.3 — Add account-naming strings (EN / RU / UK)

**Files:** `app_v2/src/main/res/values/strings_s0155.xml`, `values-ru/strings_s0155.xml`, `values-uk/strings_s0155.xml`
**Depends on:** Step 03.2

**Prompt for developer:**

> Create three new strings files for S0155. Apply `docs/COMMUNICATION_POLICY.md` §2 (message formula) and §6 (tone checklist) — calm, informational, no exclamation marks, no alarming language.
>
> **`values/strings_s0155.xml`** (EN):
> ```xml
> <?xml version="1.0" encoding="utf-8"?>
> <resources>
>     <!-- Account-naming dialog shown after WebView login -->
>     <string name="s0155_name_account_title">Name this account</string>
>     <string name="s0155_name_account_hint">e.g. @myusername</string>
>     <string name="s0155_name_account_positive">Save</string>
>     <!-- Account picker shown when ≥2 accounts exist for a host -->
>     <string name="s0155_pick_account_title">Choose account for %1$s</string>
>     <string name="s0155_pick_account_last_used">(last used)</string>
>     <!-- Named re-auth dialogs -->
>     <string name="s0155_reauth_title">Sign in again as %1$s?</string>
>     <string name="s0155_reauth_message">The saved sign-in for %1$s may have expired. Signing in again lets the app fetch the actual content.</string>
>     <string name="s0155_reauth_positive">Sign in again</string>
>     <!-- Settings: per-account operations -->
>     <string name="s0155_add_account_label">Add another account</string>
>     <string name="s0155_rename_account_title">Rename account</string>
>     <string name="s0155_delete_account_confirm">Delete sign-in for %1$s?</string>
>     <string name="s0155_relogin_account_label">Sign in again</string>
>     <!-- Migrated legacy account display name -->
>     <string name="s0155_account_default_name">Account 1</string>
> </resources>
> ```
>
> **`values-ru/strings_s0155.xml`** (RU):
> ```xml
> <?xml version="1.0" encoding="utf-8"?>
> <resources>
>     <string name="s0155_name_account_title">Назовите аккаунт</string>
>     <string name="s0155_name_account_hint">например, @myusername</string>
>     <string name="s0155_name_account_positive">Сохранить</string>
>     <string name="s0155_pick_account_title">Выберите аккаунт для %1$s</string>
>     <string name="s0155_pick_account_last_used">(последний использованный)</string>
>     <string name="s0155_reauth_title">Войти снова как %1$s?</string>
>     <string name="s0155_reauth_message">Сохранённый вход для %1$s мог устареть. Повторный вход позволит загрузить реальный контент.</string>
>     <string name="s0155_reauth_positive">Войти снова</string>
>     <string name="s0155_add_account_label">Добавить ещё один аккаунт</string>
>     <string name="s0155_rename_account_title">Переименовать аккаунт</string>
>     <string name="s0155_delete_account_confirm">Удалить вход для %1$s?</string>
>     <string name="s0155_relogin_account_label">Войти снова</string>
>     <string name="s0155_account_default_name">Аккаунт 1</string>
> </resources>
> ```
>
> **`values-uk/strings_s0155.xml`** (UK):
> ```xml
> <?xml version="1.0" encoding="utf-8"?>
> <resources>
>     <string name="s0155_name_account_title">Назвіть акаунт</string>
>     <string name="s0155_name_account_hint">наприклад, @myusername</string>
>     <string name="s0155_name_account_positive">Зберегти</string>
>     <string name="s0155_pick_account_title">Виберіть акаунт для %1$s</string>
>     <string name="s0155_pick_account_last_used">(останній використаний)</string>
>     <string name="s0155_reauth_title">Увійти знову як %1$s?</string>
>     <string name="s0155_reauth_message">Збережений вхід для %1$s міг застаріти. Повторний вхід дозволить завантажити реальний контент.</string>
>     <string name="s0155_reauth_positive">Увійти знову</string>
>     <string name="s0155_add_account_label">Додати ще один акаунт</string>
>     <string name="s0155_rename_account_title">Перейменувати акаунт</string>
>     <string name="s0155_delete_account_confirm">Видалити вхід для %1$s?</string>
>     <string name="s0155_relogin_account_label">Увійти знову</string>
>     <string name="s0155_account_default_name">Акаунт 1</string>
> </resources>
> ```
>
> Tone checklist (COMMUNICATION_POLICY §6):
> - No exclamation marks — ✓
> - No alarming language ("error", "failed", "broken") — ✓
> - Action-first labels ("Save", "Sign in again") — ✓
> - %1$s format placeholders are account name, not bare host — ✓

**Verification:**

- `Glob` — `app_v2/src/main/res/values/strings_s0155.xml` exists.
- `Glob` — `app_v2/src/main/res/values-ru/strings_s0155.xml` exists.
- `Glob` — `app_v2/src/main/res/values-uk/strings_s0155.xml` exists.
- `Grep` — `s0155_name_account_title` matches in all three files.
- `Grep` — `s0155_account_default_name` matches in all three files.
- `Grep` — `s0155_pick_account_title` matches in all three files.
- `Grep` — `s0155_reauth_title` matches in all three files.
- Strings pass COMMUNICATION_POLICY §6 checklist (no alarming language, action-first labels, calm tone).

**Status:** `[ ]` not done

---

### Step 03.4 — Update WebViewAuthDialogFragment: post-login account-naming dialog

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/auth/WebViewAuthDialogFragment.kt`
**Depends on:** Step 03.3

**Prompt for developer:**

> In `harvestAndDismiss()`, after collecting cookies and before calling `viewModel.saveSession(...)`:
>
> 1. Call `AccountNameHintExtractor.extract(cookies)` to get a hint string (or null).
> 2. Show a `MaterialAlertDialogBuilder` dialog:
>    - Title: `getString(R.string.s0155_name_account_title)`.
>    - View: a `TextInputEditText` with text pre-filled with the hint (if non-null, else empty).
>    - Hint text: `getString(R.string.s0155_name_account_hint)`.
>    - Positive button: `getString(R.string.s0155_name_account_positive)`.
>    - On positive: read the text. If blank, use the hint (if available) or fall back to `getString(R.string.s0155_account_default_name)`.
>    - Generate `accountId = java.util.UUID.randomUUID().toString()`.
>    - Call `viewModel.saveSession(targetHost, accountId, displayName, cookies)`.
>    - Then continue to the WebView state scrub + `emitResultAndDismiss(saved = true)`.
>    - On dismiss/cancel (negative button): do NOT save the session; call `emitResultAndDismiss(saved = false)`.
>
> Remove the direct call to `viewModel.saveSession(targetHost, cookies)` that currently exists (replaced by the dialog flow above).
>
> Note: the dialog is shown synchronously inside the coroutine context of the button click — use `MaterialAlertDialogBuilder.show()` (non-blocking), and move the WebView scrub + emit inside the positive callback.
>
> Existing WebView scrub block (cookie wipe, cache clear) must still execute after the account-naming positive confirmation.

**Verification:**

- `Grep` — `AccountNameHintExtractor.extract(` present in `WebViewAuthDialogFragment.kt`.
- `Grep` — `R.string.s0155_name_account_title` present.
- `Grep` — `UUID.randomUUID()` present.
- `Grep` — `viewModel.saveSession(targetHost, accountId` present (new four-arg signature).
- `Grep` — `viewModel.saveSession(targetHost, cookies)` returns zero hits (old two-arg call removed).
- `Grep` — `Log\.d\(` returns zero hits in this file.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entries added for all files in "Files Touched" via `add_to_dev_log.ps1`.
- [ ] String locale audit: `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "s0155_"` — exit code 0.
- [ ] `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` run.

---

## Handoff Notes to Next Phase

- After this phase: any new session saved through the WebView auth dialog has a UUID `accountId` and user-confirmed `displayName`.
- `AccountNameHintExtractor` is available for use in the share flow (Phase 04 does not need it directly).
- Strings `s0155_pick_account_*`, `s0155_reauth_*`, `s0155_add_account_*`, `s0155_rename_account_*`, `s0155_delete_account_*` are ready for use in Phases 04-06.

---

## Rollback Plan

Revert phase commit(s). No data written yet (Phase 01 migration only triggers on first app launch after Phase 01 lands).
