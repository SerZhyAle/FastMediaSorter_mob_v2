# Phase 04 — share-flow-integration

**Strategic spec:** [`../S0155_link-auth-multi-account.md`](../S0155_link-auth-multi-account.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 03 (account-naming)
**Blocks:** Phase 05
**Steps done:** 0 / 5
**Started:** —
**Completed:** —

---

## Objective

Wire account selection into the download pipeline: update `AuthOfferDismissalStore` to per-account keying; create `AccountSelectionManager` helper that shows the picker dialog; update `ReceiveShareActivity.maybeOfferAuthThenDownload()` for 0/1/≥2 account logic; update `LinkAutoDownloadCoordinator.handle()` to accept and apply the selected account (set session context, enrich `SocialPreviewOnly` with account display name).

---

## Prerequisites

- [ ] Phase 03 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/link/auth/AuthOfferDismissalStore.kt` | Modified | ≤ 75 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/helpers/AccountSelectionManager.kt` | New | ≤ 130 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/ReceiveShareActivity.kt` | Modified | ≤ 470 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinator.kt` | Modified | ≤ 500 |

> `ReceiveShareActivity.kt` is 431 LOC; additions in this phase should stay under 470. If it would exceed 500, extract picker delegation fully into `AccountSelectionManager`. `LinkAutoDownloadCoordinator.kt` is 448 LOC — additions will approach 500; keep the `handle()` signature change minimal and move per-account cookie loading to a private helper.

---

## Steps

### Step 04.1 — Update AuthOfferDismissalStore for per-account keying

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/link/auth/AuthOfferDismissalStore.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Extend `AuthOfferDismissalStore` to support per-(host, accountId) dismissal while preserving backward compat with old per-host dismissals.
>
> Add a second SharedPreferences key `KEY_ACCOUNT_DISMISSALS` (a `Set<String>`) where each element is `"<host>::<accountId>"`.
>
> New API:
> - `fun isDismissed(host: String, accountId: String): Boolean` — returns true if `"<host>::<accountId>"` is in the account set OR if `host` is in the legacy host set (old `KEY_HOSTS`).
> - `fun markDismissed(host: String, accountId: String)` — adds `"<host>::<accountId>"` to the account set; does NOT touch the legacy host set.
>
> Keep the old `isDismissed(host: String)` and `markDismissed(host: String)` with `@Deprecated` so callers compile until Phase 05 updates them. Old methods remain functionally unchanged — they operate only on `KEY_HOSTS`.

**Verification:**

- `Grep` — `fun isDismissed(host: String, accountId: String)` present in `AuthOfferDismissalStore.kt`.
- `Grep` — `fun markDismissed(host: String, accountId: String)` present.
- `Grep` — `KEY_ACCOUNT_DISMISSALS` constant present.
- `Grep` — `Log\.d\(` returns zero hits in this file.

**Status:** `[ ]` not done

---

### Step 04.2 — Create AccountSelectionManager helper

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/helpers/AccountSelectionManager.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Create `AccountSelectionManager` as a plain class (NOT Hilt-injected — instantiated by `ReceiveShareActivity` with the required deps passed at construction). It owns the account picker dialog and the 0/1/≥2 account selection logic.
>
> ```kotlin
> package com.sza.fastmediasorter.ui.share.helpers
>
> import androidx.appcompat.app.AppCompatActivity
> import com.google.android.material.dialog.MaterialAlertDialogBuilder
> import com.sza.fastmediasorter.domain.repository.AuthAccountDomain
> import com.sza.fastmediasorter.domain.repository.AuthSessionRepository
> import timber.log.Timber
>
> class AccountSelectionManager(
>     private val repository: AuthSessionRepository,
> ) {
>     /**
>      * Determine which account to use for [host].
>      * - 0 accounts → returns null (caller shows auth offer).
>      * - 1 account → returns it silently; logs "single account selected".
>      * - ≥2 accounts → shows picker dialog; invokes [onSelected] with chosen account
>      *                  or [onCancelled] if user dismisses.
>      */
>     suspend fun selectAccount(
>         host: String,
>         activity: AppCompatActivity,
>         onSelected: (AuthAccountDomain) -> Unit,
>         onNoneAvailable: () -> Unit,
>         onCancelled: () -> Unit,
>     ) {
>         val accounts = repository.listAccountsForHost(host)
>         Timber.i("AccountSelectionManager: host=%s accounts=%d", host, accounts.size)
>         when {
>             accounts.isEmpty() -> onNoneAvailable()
>             accounts.size == 1 -> {
>                 Timber.i("AccountSelectionManager: single account selected host=%s accountId=%s", host, accounts[0].accountId)
>                 onSelected(accounts[0])
>             }
>             else -> showPicker(host, accounts, activity, onSelected, onCancelled)
>         }
>     }
>
>     private fun showPicker(
>         host: String,
>         accounts: List<AuthAccountDomain>,
>         activity: AppCompatActivity,
>         onSelected: (AuthAccountDomain) -> Unit,
>         onCancelled: () -> Unit,
>     ) {
>         val defaultIndex = accounts.indexOfFirst { it.lastUsedAt != null && it == accounts.maxByOrNull { a -> a.lastUsedAt ?: java.time.Instant.MIN } }
>             .takeIf { it >= 0 } ?: 0
>         val labels = accounts.mapIndexed { i, acc ->
>             if (i == defaultIndex) "${acc.displayName} ${activity.getString(com.sza.fastmediasorter.R.string.s0155_pick_account_last_used)}"
>             else acc.displayName
>         }.toTypedArray()
>         var selectedIndex = defaultIndex
>         MaterialAlertDialogBuilder(activity)
>             .setTitle(activity.getString(com.sza.fastmediasorter.R.string.s0155_pick_account_title, host))
>             .setSingleChoiceItems(labels, defaultIndex) { _, which -> selectedIndex = which }
>             .setPositiveButton(android.R.string.ok) { _, _ ->
>                 val chosen = accounts[selectedIndex]
>                 Timber.i("AccountSelectionManager: user selected host=%s accountId=%s", host, chosen.accountId)
>                 onSelected(chosen)
>             }
>             .setNegativeButton(android.R.string.cancel) { _, _ -> onCancelled() }
>             .setOnCancelListener { onCancelled() }
>             .show()
>     }
> }
> ```

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/helpers/AccountSelectionManager.kt` exists.
- `Grep` — `class AccountSelectionManager` present exactly once.
- `Grep` — `suspend fun selectAccount(` present.
- `Grep` — `fun showPicker(` present.
- `Grep` — `Log\.d\(` returns zero hits in this file.

**Status:** `[ ]` not done

---

### Step 04.3 — Update ReceiveShareActivity for account-aware download

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/ReceiveShareActivity.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> Inject `AccountSelectionManager` by instantiating it in `onCreate` (pass `authSessionRepository`). Replace the body of `maybeOfferAuthThenDownload(url: String)`:
>
> New flow:
> 1. Resolve host from URL: `val resource = KnownAuthResources.matchHost(Uri.parse(url).host)`. If null → `processLinkAutoDownload(url, accountId = null)`.
> 2. Call `accountSelectionManager.selectAccount(resource.host, this, onSelected = { account → processLinkAutoDownload(url, accountId = account.accountId) }, onNoneAvailable = { /* 0 accounts: show auth offer dialog as before */ offerAuthThenDownload(url, resource) }, onCancelled = { cleanupAndFinish() })`.
>
> Extract the existing `MaterialAlertDialogBuilder` auth-offer dialog (when no session exists) into a private `fun offerAuthThenDownload(url: String, resource: KnownAuthResource)` to keep `maybeOfferAuthThenDownload` readable.
>
> Update `processLinkAutoDownload(url: String)` signature to `processLinkAutoDownload(url: String, accountId: String?)`. Pass `accountId` to `linkAutoDownloadCoordinator.handle(url, callbacks, accountId = accountId)` (see Step 04.4 for coordinator changes).
>
> Log at the start of `processLinkAutoDownload`:
> `Timber.i("ReceiveShareActivity: link auto-download enter url=%s accountId=%s", url, accountId)`
>
> Keep `processLinkAutoDownloadBatch` unchanged (batch mode does not support per-account selection in this iteration).

**Verification:**

- `Grep` — `accountSelectionManager.selectAccount(` present in `ReceiveShareActivity.kt`.
- `Grep` — `fun offerAuthThenDownload(` present.
- `Grep` — `processLinkAutoDownload(url, accountId` present (new call site).
- `Grep` — `Log\.d\(` returns zero hits in this file.

**Status:** `[ ]` not done

---

### Step 04.4 — Update LinkAutoDownloadCoordinator for account context

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinator.kt`
**Depends on:** Step 04.3

**Prompt for developer:**

> 1. Inject `LinkDownloadSessionContext` into `LinkAutoDownloadCoordinator` constructor (via Hilt field injection or constructor injection — prefer constructor injection since it's `@Singleton`).
>
> 2. Inject `AuthSessionRepository` (already injected as `authSessionRepository`).
>
> 3. Change `handle(url: String, callbacks: Callbacks)` to `handle(url: String, callbacks: Callbacks, accountId: String? = null)`.
>
> 4. Before calling `handleUrl(...)`, resolve account cookies and set the session context:
>
> ```kotlin
> private suspend fun applySessionContext(url: String, accountId: String?) {
>     if (accountId == null) return
>     val host = url.toHttpUrlOrNull()?.host ?: return
>     val resource = KnownAuthResources.matchHost(host) ?: return
>     val accounts = authSessionRepository.listAccountsForHost(resource.host)
>     val account = accounts.firstOrNull { it.accountId == accountId } ?: return
>     val cookies = withContext(Dispatchers.IO) {
>         // Load via the store; sessionContext is set for OkHttp/WebView injection.
>         // EncryptedCookieStore access is via the repository; add a loadCookiesForAccount
>         // method to AuthSessionRepository or access the store directly via DI.
>     }
>     // Actually: inject EncryptedCookieStore directly (it's already a singleton) and call
>     // store.loadForAccount(resource.host, accountId).
>     sessionContext.set(resource.host, cookies)
>     Timber.i("LinkAutoDownloadCoordinator: session context set host=%s accountId=%s cookies=%d",
>         resource.host, accountId, cookies.size)
> }
> ```
>
> Then in `handle()`:
> ```kotlin
> suspend fun handle(url: String, callbacks: Callbacks, accountId: String? = null): Result {
>     ...
>     applySessionContext(url, accountId)
>     return try {
>         handleUrl(url = url, settings = settings, callbacks = callbacks, accountId = accountId)
>     } finally {
>         sessionContext.clear()
>     }
> }
> ```
>
> 5. Pass `accountId` through to `handleUrl()` and use it when building `SocialPreviewOnly`:
>
> Current `SocialPreviewOnly` is:
> ```kotlin
> data class SocialPreviewOnly(val host: String, val originalUrl: String, val hadExistingSession: Boolean) : Failed
> ```
>
> Extend to:
> ```kotlin
> data class SocialPreviewOnly(
>     val host: String,
>     val originalUrl: String,
>     val hadExistingSession: Boolean,
>     val accountId: String?,           // null when no specific account was selected
>     val accountDisplayName: String?,  // null when no specific account was selected
> ) : Failed
> ```
>
> When building `SocialPreviewOnly` in `handleUrl()`:
> - `hadExistingSession` remains as-is (check via `authSessionRepository.hasAnySession(previewHost)` — use the new non-deprecated method).
> - `accountId` = the `accountId` param passed to `handleUrl`.
> - `accountDisplayName` = look up from `authSessionRepository.listAccountsForHost(previewHost).firstOrNull { it.accountId == accountId }?.displayName`.
>
> 6. Log the account selection in the pipeline:
> `Timber.i("LinkAutoDownloadCoordinator: account context host=%s accountId=%s reason=%s", host, accountId ?: "none", if (accountId != null) "explicit" else "default")`
>
> Note: inject `EncryptedCookieStore` directly into `LinkAutoDownloadCoordinator` for `loadForAccount` access (it's `@Singleton` and already used in adjacent files). Add it to the constructor.

**Verification:**

- `Grep` — `fun handle(url: String, callbacks: Callbacks, accountId: String? = null)` present in `LinkAutoDownloadCoordinator.kt`.
- `Grep` — `sessionContext.set(` present.
- `Grep` — `sessionContext.clear()` present.
- `Grep` — `val accountId: String?` present inside `SocialPreviewOnly` data class.
- `Grep` — `val accountDisplayName: String?` present inside `SocialPreviewOnly` data class.
- `Grep` — `hasAnySession(` present (old `hasSession` call replaced).
- `Grep` — `Log\.d\(` returns zero hits in this file.

**Status:** `[ ]` not done

---

### Step 04.5 — Dev log for Phase 04 files

**Files:** `dev/CHANGELOG.md` (via script)
**Depends on:** Step 04.4

**Prompt for developer:**

```powershell
.\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/data/link/auth/AuthOfferDismissalStore.kt" "S0155 Phase 04" "Add per-(host, accountId) dismissal storage alongside legacy host-level store"
.\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/share/helpers/AccountSelectionManager.kt" "S0155 Phase 04" "New helper: account picker dialog for ≥2 accounts on a host"
.\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/share/ReceiveShareActivity.kt" "S0155 Phase 04" "Account-aware download flow: 0/1/≥2 account selection before pipeline"
.\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinator.kt" "S0155 Phase 04" "Accept accountId, set session context, enrich SocialPreviewOnly with account info"
```

**Verification:**

- `Grep` — `S0155 Phase 04` matches at least 4 lines in `dev/CHANGELOG.md`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entries added for all files in "Files Touched".
- [ ] `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` run.

---

## Handoff Notes to Next Phase

- `SocialPreviewOnly` now carries `accountId` and `accountDisplayName`.
- `AuthOfferDismissalStore` has the per-account API ready for Phase 05.
- Phase 05 updates `LinkAutoDownloadResultPresenter` to use the named re-auth dialog.

---

## Rollback Plan

Revert phase commit(s). `AuthOfferDismissalStore` change is additive — old host-level dismissals untouched. No data migration risk.
