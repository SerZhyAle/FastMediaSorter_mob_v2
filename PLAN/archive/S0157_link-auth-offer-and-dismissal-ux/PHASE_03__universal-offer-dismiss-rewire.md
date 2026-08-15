# Phase 03 — universal-offer-dismiss-rewire

**Strategic spec:** [`../S0157_link-auth-offer-and-dismissal-ux.md`](../S0157_link-auth-offer-and-dismissal-ux.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04, Phase 06
**Steps done:** 6 / 6
**Started:** —
**Completed:** 2026-05-11

---

## Objective

Replace the binary dismiss store with the new repository API. Remove the `KnownAuthResources` guard so any http(s) host triggers the auth offer. Upgrade the offer dialog from 2-button to 3-button in both `ReceiveShareActivity` (proactive) and `LinkAutoDownloadResultPresenter` (reactive). Delete `AuthOfferDismissalStore`.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.
- [ ] `ReceiveShareActivity.kt` and `LinkAutoDownloadResultPresenter.kt` read before editing.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings_s0157.xml` | New | ≤ 30 |
| `app_v2/src/main/res/values-ru/strings_s0157.xml` | New | ≤ 30 |
| `app_v2/src/main/res/values-uk/strings_s0157.xml` | New | ≤ 30 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/ReceiveShareActivity.kt` | Modified | ≤ 490 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/LinkAutoDownloadResultPresenter.kt` | Modified | ≤ 230 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/link/auth/AuthOfferDismissalStore.kt` | Delete | — |

---

## Steps

### Step 03.1 — Add S0157 string resources (EN / RU / UK)

**Files:** `values/strings_s0157.xml`, `values-ru/strings_s0157.xml`, `values-uk/strings_s0157.xml`
**Depends on:** — start of phase

**Prompt for developer:**

> Create three new string resource files (one per locale). All new keys must pass `docs/COMMUNICATION_POLICY.md` §2 (short, action-verb CTAs) and the §6 tone checklist (direct, non-condescending, no double negatives). Strings needed:
>
> **EN (`values/strings_s0157.xml`):**
> ```xml
> <?xml version="1.0" encoding="utf-8"?>
> <resources>
>     <!-- S0157: 3-button auth offer dialog — third "permanent skip" button -->
>     <string name="s0157_auth_offer_dismiss_always">Don\'t ask again</string>
>     <!-- S0157: account name pre-fill default when no hint is available -->
>     <string name="s0157_account_default_name">Default account</string>
>     <!-- S0157: label for a dismissed record in the auth settings list -->
>     <string name="s0157_dismissed_label">Not authorized (you declined)</string>
>     <!-- S0157: last-used date placeholder when the account was never used -->
>     <string name="s0157_last_used_never">not yet used</string>
> </resources>
> ```
>
> **RU (`values-ru/strings_s0157.xml`):**
> ```xml
> <?xml version="1.0" encoding="utf-8"?>
> <resources>
>     <string name="s0157_auth_offer_dismiss_always">Не спрашивать</string>
>     <string name="s0157_account_default_name">Аккаунт по умолчанию</string>
>     <string name="s0157_dismissed_label">Не авторизован (вы отказались)</string>
>     <string name="s0157_last_used_never">ещё не использовалась</string>
> </resources>
> ```
>
> **UK (`values-uk/strings_s0157.xml`):**
> ```xml
> <?xml version="1.0" encoding="utf-8"?>
> <resources>
>     <string name="s0157_auth_offer_dismiss_always">Не питати</string>
>     <string name="s0157_account_default_name">Акаунт за замовчуванням</string>
>     <string name="s0157_dismissed_label">Не авторизовано (ви відмовились)</string>
>     <string name="s0157_last_used_never">ще не використовувалась</string>
> </resources>
> ```

**Verification:**

- `Glob` — `app_v2/src/main/res/values/strings_s0157.xml` exists.
- `Glob` — `app_v2/src/main/res/values-ru/strings_s0157.xml` exists.
- `Glob` — `app_v2/src/main/res/values-uk/strings_s0157.xml` exists.
- `Grep` — `s0157_auth_offer_dismiss_always` present in all three files.
- `Grep` — `s0157_dismissed_label` present in all three files.
- Strings pass `COMMUNICATION_POLICY.md` §6 checklist: direct phrasing, no double negatives ("Don't ask again" is idiomatic; RU/UK equivalents are one-word imperatives ✓).

**Status:** `[x] done`

**Step Log:**

- 2026-05-11 — Verification 5/5 PASS. All three locale files created; 5 keys × 3 locales = 15 strings, `check_strings_localized.ps1` exit 0. COMM_POLICY §6 ✓. Files: strings_s0157.xml ×3.

---

### Step 03.2 — `ReceiveShareActivity`: universal host routing in `maybeOfferAuthThenDownload()`

**Files:** `ui/share/ReceiveShareActivity.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Rewrite `maybeOfferAuthThenDownload(url: String)`:
>
> 1. Parse `host` from the URL. If blank, fall through to `processLinkAutoDownload(url, null)`.
> 2. Check `authSessionRepository.isDismissedForHost(host)` — if true, skip the offer and proceed without auth.
> 3. Otherwise, look up the known resource: `val resource = KnownAuthResources.matchHost(host)`.
> 4. Launch `accountSelectionManager.selectAccount(host = host, ...)` — the manager already handles "no accounts" via `onNoneAvailable`. In `onNoneAvailable`, call `offerAuthThenDownload(url, host, resource)` (passing `resource` which may be null for unknown hosts).
>
> The `isDismissedForHost()` call is a suspend function — keep the `lifecycleScope.launch { ... }` wrapper. Remove the early-return `if (resource == null) { processLinkAutoDownload(url, null); return }` guard.

**Verification:**

- `Grep` — `isDismissedForHost(host)` called in `maybeOfferAuthThenDownload` body.
- `Grep` — early-return `if (resource == null)` block removed (no match for the old pattern `resource == null.*processLinkAutoDownload`).

**Status:** `[x] done`

**Step Log:**

- 2026-05-11 — Verification 2/2 PASS. `isDismissedForHost(host)` called in `maybeOfferAuthThenDownload`; old null-guard removed. Files: ReceiveShareActivity.kt (~0 LOC delta).

---

### Step 03.3 — `ReceiveShareActivity`: 3-button dialog in `offerAuthThenDownload()`

**Files:** `ui/share/ReceiveShareActivity.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Rewrite `offerAuthThenDownload(url: String, host: String, resource: KnownAuthResource?)`. Remove the old `isDismissed` check (now done upstream in Step 03.2). Build a 3-button `MaterialAlertDialogBuilder`:
>
> - Title: `getString(R.string.auth_offer_dialog_title, resource?.displayName ?: host)` — falls back to bare hostname for unknown resources.
> - Message: `getString(R.string.auth_offer_dialog_message, resource?.displayName ?: host)`.
> - Positive button (`R.string.auth_offer_dialog_add`) → launch `WebViewAuthDialogFragment` with the login URL. For known resources: `resource.loginUrl`. For unknown hosts: use `url` itself. After dialog result, call `processLinkAutoDownload(url, null)`.
> - Neutral button (`R.string.auth_offer_dialog_skip`) → `processLinkAutoDownload(url, null)` immediately (no dismissal recorded — "skip for now").
> - Negative button (`R.string.s0157_auth_offer_dismiss_always`) → `lifecycleScope.launch { authSessionRepository.markDismissed(host) }` then `processLinkAutoDownload(url, null)`.
>
> Use `setNeutralButton` for "Skip for now" and `setNegativeButton` for "Don't ask again" to keep the visual order: Add (primary) · Skip (neutral) · Don't ask (negative, typically on the far left).

**Verification:**

- `Grep` — `s0157_auth_offer_dismiss_always` referenced in `ReceiveShareActivity.kt`.
- `Grep` — `markDismissed(host)` called from `offerAuthThenDownload` body.
- `Grep` — `setNeutralButton` used in `offerAuthThenDownload`.
- `Grep` — `authOfferDismissalStore` does NOT appear in `ReceiveShareActivity.kt` (all old usages removed).
- `Grep` — `Log\.d\(` returns zero hits in `ReceiveShareActivity.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-11 — Verification 5/5 PASS. `s0157_auth_offer_dismiss_always`, `markDismissed(host)`, `setNeutralButton` present; `authOfferDismissalStore` absent; `Log.d(` 0 hits. Files: ReceiveShareActivity.kt (-2 lines net).

---

### Step 03.4 — `LinkAutoDownloadResultPresenter`: swap `AuthOfferDismissalStore` for `AuthSessionRepository`

**Files:** `ui/share/LinkAutoDownloadResultPresenter.kt`
**Depends on:** Step 03.3

**Prompt for developer:**

> In `LinkAutoDownloadResultPresenter`:
>
> 1. Remove `private val authOfferDismissalStore: AuthOfferDismissalStore` from the constructor.
> 2. Add `private val authSessionRepository: AuthSessionRepository` to the constructor (import `com.sza.fastmediasorter.domain.repository.AuthSessionRepository`).
> 3. The `@Singleton` class is already in the DI graph via Hilt — `AuthSessionRepository` is already bound in `LinkDownloadModule`.

**Verification:**

- `Grep` — `authOfferDismissalStore` does NOT appear in `LinkAutoDownloadResultPresenter.kt`.
- `Grep` — `private val authSessionRepository: AuthSessionRepository` in constructor.

**Status:** `[x] done`

**Step Log:**

- 2026-05-11 — Constructor swap done (`authOfferDismissalStore` removed, `authSessionRepository` added). Body usages addressed in Step 03.5 (prerequisite for compile). Files: LinkAutoDownloadResultPresenter.kt (-1 line net).

---

### Step 03.5 — `LinkAutoDownloadResultPresenter`: 3-button reactive reauth dialog

**Files:** `ui/share/LinkAutoDownloadResultPresenter.kt`
**Depends on:** Step 03.4

**Prompt for developer:**

> In `presentSocialPreviewOnly()`:
>
> 1. Replace the `isDismissed` check (old: `authOfferDismissalStore.isDismissed(...)`) with a `runBlocking { authSessionRepository.isDismissedForHost(result.host) }`. Since `present()` is already a suspend function called from a coroutine, you may use `val isDismissed = authSessionRepository.isDismissedForHost(result.host)` directly (it's already inside a coroutine context via `lifecycleScope.launch` in the caller). Make `presentSocialPreviewOnly` a suspend function to allow the `isDismissed` await.
> 2. Add a neutral button `R.string.auth_offer_dialog_skip` (skip for now — no dismissal) alongside the existing skip logic.
> 3. Rename the existing negative button to use `R.string.s0157_auth_offer_dismiss_always` and wire it to `authSessionRepository.markDismissed(result.host)` (in a `lifecycleScope.launch { }` block on the `hostActivity`).
> 4. Remove any `@Suppress("DEPRECATION")` annotations that existed for the old dismissal-store calls.

**Verification:**

- `Grep` — `isDismissedForHost(result.host)` present in `LinkAutoDownloadResultPresenter.kt`.
- `Grep` — `markDismissed(result.host)` present.
- `Grep` — `s0157_auth_offer_dismiss_always` present.
- `Grep` — `authOfferDismissalStore` does NOT appear anywhere in `LinkAutoDownloadResultPresenter.kt`.
- `Grep` — `Log\.d\(` returns zero hits.

**Status:** `[x] done`

**Step Log:**

- 2026-05-11 — Verification 5/5 PASS. `isDismissedForHost(result.host)` and `markDismissed(result.host)` present; `s0157_auth_offer_dismiss_always` present; `authOfferDismissalStore` 0 hits; `Log.d(` 0 hits. `presentSocialPreviewOnly` made `suspend`, `@Suppress("DEPRECATION")` annotations removed, neutral + negative buttons rewired. Files: LinkAutoDownloadResultPresenter.kt (-12 lines net).

---

### Step 03.6 — Delete `AuthOfferDismissalStore.kt`

**Files:** `data/link/auth/AuthOfferDismissalStore.kt`
**Depends on:** Step 03.5

**Prompt for developer:**

> Delete `app_v2/src/main/java/com/sza/fastmediasorter/data/link/auth/AuthOfferDismissalStore.kt`. Confirm there are no remaining import references to this class by running `Grep` across `app_v2/src/`. The `"link_download_auth_offer"` SharedPreferences file will persist on device as orphaned data — this is acceptable (no sensitive content, cleared on uninstall).

**Verification:**

- `Glob` — `data/link/auth/AuthOfferDismissalStore.kt` does NOT exist.
- `Grep` — `AuthOfferDismissalStore` does NOT appear in any `.kt` file under `app_v2/src/`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-11 — Verification 2/2 PASS. File deleted; `AuthOfferDismissalStore` 0 hits in any .kt under app_v2/src/.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles — run `/build`.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] String locale audit: `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "s0157_"` exits code 0.
- [x] Dev log entries added for all files in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

- `AuthOfferDismissalStore` is gone; `markDismissed` / `isDismissedForHost` live in `AuthSessionRepository`.
- `ReceiveShareActivity` and `LinkAutoDownloadResultPresenter` both use the new 3-button pattern.
- All http(s) hosts now trigger the auth offer if no session and no dismissal exist.
- Strings `s0157_auth_offer_dismiss_always`, `s0157_account_default_name`, `s0157_dismissed_label`, `s0157_last_used_never` are available for Phase 04 and 05.

---

## Rollback Plan

Revert phase commit(s). `AuthOfferDismissalStore.kt` can be restored from git history. No data migration changes — the dismissed records in `EncryptedCookieStore` from Phase 01/02 are self-consistent.
