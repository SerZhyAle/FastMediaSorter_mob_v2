# S0161 · Background Link Download Queue

**Status:** Implemented  
**Priority:** 65  
**Created:** 2026-05-11  
**Updated:** 2026-05-11

---

## Problem

When the user shares a URL (e.g. from Instagram) to FastMediaSorter, `ReceiveShareActivity`
shows an account-picker / auth dialog **before** starting the download, even when saved
cookies are available. The user had to wait for the picker and then the entire download
before returning to the source app.

Additionally, on `SocialPreviewOnly` results (cookies expired / session lost), the auth
dialog was not always shown — the worker path had no way to open dialogs.

## Goal

1. **Silently use the best saved account** (by `lastUsedAt`) without showing an account picker.
2. **Show a blocking progress dialog** during download — the user sees progress and can cancel.
3. **On `SocialPreviewOnly` (not dismissed):** show auth dialog in-Activity with WebView retry.
4. **On dismiss / already-dismissed host:** toast, no dialog.
5. **Batch URLs:** still use `WorkManager` + `LinkDownloadWorker` (unchanged).

## Architecture

```
ReceiveShareActivity
  onShare(url)
    └── enqueueLinkDownloadSilent(url)
          ├── silently pick best accountId from authSessionRepository.listAccountsForHost()
          │     (filter !isDismissed, max by lastUsedAt)
          └── processLinkAutoDownload(url, accountId)
                ├── show LinkAutoDownloadProgressDialog
                ├── coordinator.handle(url, callbacks, accountId)
                ├── resultPresenter.present(result, activity, onAuthRetryRequested)
                │     ├── Saved / FellBackToDownloads → toast (filename) → finish
                │     ├── SocialPreviewOnly (not dismissed)
                │     │     → MaterialAlertDialog (Login / Skip / Don't ask)
                │     │       → Login → WebViewAuthDialogFragment
                │     │           → onAuthRetryRequested → processLinkAutoDownload(retry=true)
                │     ├── SocialPreviewOnly (dismissed) → toast → finish
                │     └── Failed.* → toast → finish
                └── cleanupAndFinish()  ← only when no auth dialog pending
```

### `EXTRA_REAUTH_URL` flow (notification «Sign in» button from batch worker)

```
ReceiveShareActivity.onCreate(EXTRA_REAUTH_URL)
  └── handleReAuthFromNotification(url)
        ├── isDismissedForHost → processLinkAutoDownload(url, null)
        └── else              → offerAuthThenDownload(url, host, resource)
```

## Affected Files

| File | Change |
|------|--------|
| `ui/share/ReceiveShareActivity.kt` | `enqueueLinkDownloadSilent()` (silent cookie lookup); `processLinkAutoDownload()` — blocking dialog + coordinator + resultPresenter; `handleReAuthFromNotification()` |
| `worker/LinkDownloadWorker.kt` | Batch-only worker; removed `setForeground()`; injected `AuthSessionRepository`; `IMPORTANCE_HIGH` notification channel; `isDismissedForHost` check before sign-in notification |
| `res/values*/strings.xml` | Notification channel + message strings (10 keys, 3 locales) |
| `docs/FEATURES*.md` | Feature entry (EN/RU/UK) |

## Decisions

- **Blocking dialog for single URL** — gives users real-time download progress and a cancel button; consistent with the existing UX for other download types in the app.
- **Silent cookie lookup** — no account picker before download; picks `lastUsedAt` account first, skips dismissed records.
- **Auth dialog only on failure** — `SocialPreviewOnly` result triggers the auth flow inside the Activity, which already has `LinkAutoDownloadResultPresenter` with retry logic.
- **WorkManager only for batch** — batch downloads still use `LinkDownloadWorker` with `IMPORTANCE_HIGH` notifications.
- **`LinkAutoDownloadProgressDialog`** — re-enabled for single-URL path.
- **`resultPresenter`** — re-injected into `ReceiveShareActivity` for single-URL result UX.
- **`setForeground()` removed from worker** — conflicts with `setExpedited()` on Android 12+ (WakeLock SecurityException); expedited work is sufficient for the batch case.

## Status History

| Date | Status | Note |
|------|--------|------|
| 2026-05-11 | Draft | Spec created |
| 2026-05-11 | In Progress | WorkManager-only implementation built |
| 2026-05-11 | In Progress | Revised: reverted to blocking dialog + silent cookie lookup |
| 2026-05-11 | Implemented | Blocking dialog + silent lookup + EXTRA_REAUTH_URL handler complete; BUILD OK |


## Phases

- [x] Phase 01 — Create `LinkDownloadWorker`
- [x] Phase 02 — Modify `ReceiveShareActivity`
- [x] Phase 03 — String resources (EN/RU/UK)
- [x] Phase 04 — FEATURES docs update
- [x] Phase 05 — Revision: silent cookie lookup + blocking dialog (revert WorkManager-only UX)
- [x] Phase 06 — Worker: IMPORTANCE_HIGH channel, AuthSessionRepository inject, isDismissedForHost check, remove setForeground (SecurityException fix)

## Last Audit

**Date:** 2026-05-11
**Status:** Implemented

### Build: PASS — `assembleStandardDebug` BUILD SUCCESSFUL

### On-device test (2026-05-11 log `203620.log`):
- Single URL share from Instagram: silent cookie lookup → progress dialog shown → `FellBackToDownloads` → `LinkDownloadWriter: saved .mp4` ✓
- `SocialPreviewOnly`: cookies present but Instagram returns preview page → auth dialog expected (needs re-test with revised UX)
- `setForeground SecurityException` — **fixed** (removed `setForeground()` from worker)

### Pending re-test:
- SocialPreviewOnly → auth dialog appears in-activity and retry download works
- `EXTRA_REAUTH_URL` (batch sign-in button) → WebView auth dialog shown
