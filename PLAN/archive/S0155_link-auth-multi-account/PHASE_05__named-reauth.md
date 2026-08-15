# Phase 05 — named-reauth

**Strategic spec:** [`../S0155_link-auth-multi-account.md`](../S0155_link-auth-multi-account.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 04 (share-flow-integration)
**Blocks:** Phase 06
**Steps done:** 0 / 3
**Started:** —
**Completed:** —

---

## Objective

Update `LinkAutoDownloadResultPresenter` to use the enriched `SocialPreviewOnly.accountDisplayName` in the re-auth dialog (showing "Sign in again as @username?" instead of the generic title). Also update `AuthOfferDismissalStore` call sites in the presenter to use the per-account API from Phase 04.

---

## Prerequisites

- [ ] Phase 04 is ✅ Done.
- [ ] `SocialPreviewOnly` data class has `accountId: String?` and `accountDisplayName: String?` fields.
- [ ] `AuthOfferDismissalStore` has `isDismissed(host, accountId)` and `markDismissed(host, accountId)`.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/LinkAutoDownloadResultPresenter.kt` | Modified | ≤ 260 |

---

## Steps

### Step 05.1 — Update presentSocialPreviewOnly for named account

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/LinkAutoDownloadResultPresenter.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Rewrite `presentSocialPreviewOnly(result, hostActivity, onAuthRetryRequested)` to use account context:
>
> 1. **Dismissal check:** call `authOfferDismissalStore.isDismissed(result.host, result.accountId ?: "")` if `accountId` is non-null; otherwise fall back to the legacy `isDismissed(result.host)` check. If dismissed, show `R.string.s0151_toast_content_unavailable` as before.
>
> 2. **Dialog title and message:** if `result.accountDisplayName != null`:
>    - Title: `getString(R.string.s0155_reauth_title, result.accountDisplayName)` — "Sign in again as @username?"
>    - Message: `getString(R.string.s0155_reauth_message, result.accountDisplayName)`
>    - Positive button label: `getString(R.string.s0155_reauth_positive)` — "Sign in again"
>    - Otherwise fall back to the existing `s0151_dialog_reauth_title` / `s0151_dialog_reauth_message` / `s0151_dialog_reauth_positive` strings (backward compat for cases without an account).
>
> 3. **Negative button (dismiss):** call `authOfferDismissalStore.markDismissed(result.host, result.accountId ?: "")` when `accountId` is non-null; otherwise call legacy `markDismissed(result.host)`.
>
> 4. **Positive button (re-auth):** behaviour unchanged — open `WebViewAuthDialogFragment` for `loginUrl`, wait for fragment result, invoke `onAuthRetryRequested`. After re-auth, the new account-naming dialog (Phase 03) will fire, saving the refreshed session under the same or a new account.
>
> 5. Log the chosen path:
> `Timber.i("LinkAutoDownloadResultPresenter: reauth accountId=%s accountName=%s", result.accountId, result.accountDisplayName)`
>
> Also update `renderFailureReason()` — the `SocialPreviewOnly` case is unchanged (still returns `s0151_toast_content_unavailable`).

**Verification:**

- `Grep` — `R.string.s0155_reauth_title` present in `LinkAutoDownloadResultPresenter.kt`.
- `Grep` — `result.accountDisplayName` present.
- `Grep` — `isDismissed(result.host, result.accountId` present.
- `Grep` — `markDismissed(result.host, result.accountId` present.
- `Grep` — `Log\.d\(` returns zero hits in this file.

**Status:** `[ ]` not done

---

### Step 05.2 — Mark lastUsed on successful download

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinator.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> In `LinkAutoDownloadCoordinator.handle()`, after a successful `Result.Saved` or `Result.FellBackToDownloads` outcome (returned from `handleUrl()`), call `authSessionRepository.markLastUsed(host, accountId)` if `accountId` is non-null.
>
> Determine `host` from the URL's canonical form (via `KnownAuthResources.matchHost`). If the canonical host is null, skip `markLastUsed`.
>
> ```kotlin
> // Inside handle(), after result = handleUrl(...):
> if (accountId != null && (result is Result.Saved || result is Result.FellBackToDownloads)) {
>     val canonicalHost = url.toHttpUrlOrNull()?.host
>         ?.let { KnownAuthResources.matchHost(it)?.host }
>     if (canonicalHost != null) {
>         runCatching { authSessionRepository.markLastUsed(canonicalHost, accountId) }
>         Timber.i("LinkAutoDownloadCoordinator: markLastUsed host=%s accountId=%s", canonicalHost, accountId)
>     }
> }
> ```

**Verification:**

- `Grep` — `markLastUsed(canonicalHost, accountId)` present in `LinkAutoDownloadCoordinator.kt`.
- `Grep` — `Log\.d\(` returns zero hits in this file.

**Status:** `[ ]` not done

---

### Step 05.3 — Dev log for Phase 05 files

**Files:** `dev/CHANGELOG.md` (via script)
**Depends on:** Step 05.2

```powershell
.\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/share/LinkAutoDownloadResultPresenter.kt" "S0155 Phase 05" "Named re-auth dialog showing accountDisplayName; per-account dismissal"
.\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinator.kt" "S0155 Phase 05" "markLastUsed after successful download"
```

**Verification:**

- `Grep` — `S0155 Phase 05` matches at least 2 lines in `dev/CHANGELOG.md`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-05)` returns zero hits.
- [ ] Dev log entries added for all files in "Files Touched".
- [ ] `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` run.

---

## Handoff Notes to Next Phase

- Named re-auth is complete: `presentSocialPreviewOnly` uses `accountDisplayName` when available.
- `markLastUsed` is called after successful downloads so the default-account selection heuristic works.
- Phase 06 updates the Settings UI to display the multi-account list with per-account operations.

---

## Rollback Plan

Revert phase commit(s). Purely UI and coordinator logic — no data storage change.
