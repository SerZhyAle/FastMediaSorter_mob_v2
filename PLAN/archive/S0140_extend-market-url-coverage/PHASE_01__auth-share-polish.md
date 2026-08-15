# Phase 01 — auth-share-polish

**Strategic spec:** [`../S0140_extend-market-url-coverage.md`](../S0140_extend-market-url-coverage.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation slice
**Blocks:** Phase 02, 04
**Steps done:** 4 / 4
**Started:** 2026-05-10
**Completed:** 2026-05-10

---

## Objective

Accept multiple shared links as one batch download and prevent the WebView auth flow from storing unusable empty sessions.

---

## Prerequisites

- [x] Working tree reviewed.
- [x] Existing share-sheet / auth code path identified.
- [x] No Phase 03 research dependency applies to this slice.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/UrlInTextDetector.kt` | Modified | <= 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinator.kt` | Modified | <= 420 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/ReceiveShareActivity.kt` | Modified | <= 460 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/auth/WebViewAuthDialogFragment.kt` | Modified | <= 280 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/auth/WebViewAuthViewModel.kt` | Modified | <= 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/AuthSessionRepositoryImpl.kt` | Modified | <= 100 |
| `app_v2/src/main/res/layout/dialog_webview_auth.xml` | Modified | <= 80 |
| `app_v2/src/main/res/layout-land/dialog_webview_auth.xml` | Modified | <= 90 |
| `app_v2/src/main/res/values/strings_s0140.xml` | New | <= 40 |
| `app_v2/src/main/res/values-ru/strings_s0140.xml` | New | <= 40 |
| `app_v2/src/main/res/values-uk/strings_s0140.xml` | New | <= 40 |

---

## Steps

### Step 01.1 — Extract all shared URLs and expose a batch coordinator entrypoint

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/UrlInTextDetector.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinator.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Extend the shared-text parser so it returns all de-duplicated `http(s)` URLs in input order, then add a public `LinkAutoDownloadCoordinator` entrypoint that wraps a URL list into the existing batch execution path while preserving the single-item fast path.

**Verification:**

- `Grep` — `fun httpUrls` matches once in `UrlInTextDetector.kt`.
- `Grep` — `suspend fun handleBatch` matches once in `LinkAutoDownloadCoordinator.kt`.
- `Grep` — `OpenResult.Batch\(items = items\)` matches once in `LinkAutoDownloadCoordinator.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-10 — Verification PASS. Files: `UrlInTextDetector.kt`, `LinkAutoDownloadCoordinator.kt`. Dev log recorded.

---

### Step 01.2 — Route shared multi-URL payloads through the existing progress UI

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/ReceiveShareActivity.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> In `ReceiveShareActivity`, detect multiple links in `Intent.EXTRA_TEXT` when no file streams are present. Keep the current auth-offer flow for a single URL, and send multi-link payloads straight to the batch coordinator with the existing progress / result presenter.

**Verification:**

- `Grep` — `val urls = UrlInTextDetector.httpUrls` matches once.
- `Grep` — `processLinkAutoDownloadBatch` matches exactly 2 times (declaration + call).
- `Grep` — `linkAutoDownloadCoordinator.handleBatch` matches once.

**Status:** `[x]` done

**Step Log:**

- 2026-05-10 — Verification PASS. File: `ReceiveShareActivity.kt`. Dev log recorded.

---

### Step 01.3 — Block empty WebView auth saves and explain the required action

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/auth/WebViewAuthDialogFragment.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/auth/WebViewAuthViewModel.kt`, `app_v2/src/main/res/layout/dialog_webview_auth.xml`, `app_v2/src/main/res/layout-land/dialog_webview_auth.xml`, `app_v2/src/main/res/values/strings_s0140.xml`, `app_v2/src/main/res/values-ru/strings_s0140.xml`, `app_v2/src/main/res/values-uk/strings_s0140.xml`
**Depends on:** Step 01.2

**Prompt for developer:**

> Keep `Save authorization` disabled until cookies appear for the target host, show a hint bar explaining the sign-in order, and surface a snackbar instead of dismissing the dialog when the user still tries to save an empty session.

**Verification:**

- `Grep` — `refreshSaveButtonState` matches once in `WebViewAuthDialogFragment.kt`.
- `Grep` — `Snackbar.make` matches once in `WebViewAuthDialogFragment.kt`.
- `Grep` — `s0140_webview_auth_hint` matches in both portrait and landscape layouts.
- `Grep` — `s0140_webview_auth_sign_in_first` exists in EN/RU/UK string files.

**Status:** `[x]` done

**Step Log:**

- 2026-05-10 — Verification PASS. Files: auth dialog + VM + layouts + trilingual strings. `check_strings_localized.ps1 -KeyPrefix "s0140_"` PASS after helper fix. Dev log recorded.

---

### Step 01.4 — No-op empty auth saves and prune stale zero-cookie entries

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/AuthSessionRepositoryImpl.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> Enforce the root fix in `AuthSessionRepositoryImpl`: never persist an empty cookie list, and delete previously stored zero-cookie sessions when rebuilding the observable snapshot so the auth list stops showing useless entries.

**Verification:**

- `Grep` — `skipped empty auth session save` matches once.
- `Grep` — `pruned %d empty auth session` matches once.
- `Grep` — `cookieCount == 0` matches once.

**Status:** `[x]` done

**Step Log:**

- 2026-05-10 — Verification PASS. File: `AuthSessionRepositoryImpl.kt`. Touched-file diagnostics clean. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Touched-file diagnostics report no errors.
- [x] `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "s0140_"` returns 0.
- [x] Dev log entry added for every file in "Files Touched" via `./scripts/add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

Batch progress/result UX already exists in `main/`; Phase 02 can extend discovery coverage without reopening the share/auth UI flow.

---

## Rollback Plan

Revert the Phase 01 commit(s). No schema change and no irreversible data migration beyond deleting zero-cookie auth entries.