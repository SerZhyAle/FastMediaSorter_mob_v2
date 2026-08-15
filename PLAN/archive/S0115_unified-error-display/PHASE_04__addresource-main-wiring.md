# Phase 04 — AddResource + Main Activity Wiring

**Strategic spec:** [`../S0115_unified-error-display.md`](../S0115_unified-error-display.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 05
**Steps done:** 3 / 3
**Started:** 2026-05-08
**Completed:** 2026-05-08

---

## Objective

Replace error-specific direct `Toast.makeText` calls in `AddResourceConnectionManager` and `MainActivity` with `AppErrorNotifier.show`. Informational toasts (status confirmations, non-error feedback) are intentionally left untouched.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Phase 02 is ✅ Done.
- [ ] Working tree is clean.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceConnectionManager.kt` | Modified | ≤ 520 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt` | Modified | ≤ 1000 |

> `AddResourceConnectionManager.kt` is exactly 500 LOC — create a timestamped backup in `temp/` before editing.
> `MainActivity.kt` is 987 LOC — create a timestamped backup in `temp/` before editing.

---

## Steps

### Step 04.1 — Backup large files before editing

**Files:** _(temp/ backups only)_
**Depends on:** — start of phase

**Prompt for developer:**

> Copy both files to `temp/` with a timestamp suffix before making any edits:
> ```powershell
> $ts = Get-Date -Format "yyyyMMdd_HHmmss"
> Copy-Item "app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceConnectionManager.kt" "temp/AddResourceConnectionManager_$ts.kt.backup"
> Copy-Item "app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt" "temp/MainActivity_$ts.kt.backup"
> ```

**Verification:**

- `Glob` — at least one `temp/AddResourceConnectionManager_*.kt.backup` file exists.
- `Glob` — at least one `temp/MainActivity_*.kt.backup` file exists.

**Status:** `[x] done`

**Step Log:**

- 2026-05-08 — Verification 2/2 PASS. Backups: AddResourceConnectionManager_20260508_102657.kt.backup, MainActivity_20260508_102657.kt.backup.

---

### Step 04.2 — Wire AddResourceConnectionManager to AppErrorNotifier

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceConnectionManager.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> In `AddResourceConnectionManager`, identify `Toast.makeText(...)` calls that surface **error conditions** — connection failures, authentication failures, validation errors (e.g., "invalid server address", "host required", "SSH key required", connection test failures). Replace each such error Toast with:
> ```kotlin
> AppErrorNotifier.show(
>     activity = activity,
>     message = <existing message string>,
>     severity = ErrorSeverity.CRITICAL
> )
> ```
> Do NOT replace toasts that are confirmational/informational (e.g., "signed out" notifications for cloud providers — those are status, not errors). When there is ambiguity about whether a toast is an error, treat it as CRITICAL if it indicates a failed operation.
>
> Also check: if `settings.showDetailedErrors` is already read in this file and an `ErrorDialog` is shown in the `true` branch, leave that branch unchanged and only replace the `else` / non-detailed Toast branch.
>
> Add `Timber.d("S0115: AddResourceConnectionManager error delegating to AppErrorNotifier")` before the first replacement.

**Verification:**

- `Grep` — `AppErrorNotifier.show(` present in `AddResourceConnectionManager.kt`.
- `Grep` — `Timber.d("S0115:` present in `AddResourceConnectionManager.kt`.
- `Grep -n "Log\.d\("` — zero hits in the file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-08 — Verification 3/3 PASS. Files: AddResourceConnectionManager.kt — 8 error Toasts replaced (auth failures: GDrive, OneDrive; validation: invalid_server_address, server_address_required, invalid_host_address, host_required, ssh_key_required; showError else-branch). Confirmational toasts (signed-in, signed-out) left unchanged. Dev log recorded.

---

### Step 04.3 — Wire MainActivity error paths to AppErrorNotifier

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> In `MainActivity`, the private `showError(message, details?)` function currently calls `Toast.makeText(this, message, Toast.LENGTH_LONG).show()` in the `else` branch (when `showDetailedErrors == false`). Replace that Toast call with:
> ```kotlin
> AppErrorNotifier.show(
>     activity = this,
>     message = message,
>     severity = ErrorSeverity.CRITICAL,
>     showDetailedErrors = false
> )
> ```
>
> Leave `showInfo(...)` and any other informational Toast calls in `MainActivity` unchanged — informational toasts (app-update notification, version info, status messages) are not errors and must stay as plain Toast.
>
> Add `Timber.d("S0115: MainActivity.showError delegating to AppErrorNotifier")` before the replacement call.

**Verification:**

- `Grep` — `AppErrorNotifier.show(` present in `MainActivity.kt`.
- `Grep` — `Timber.d("S0115:` present in `MainActivity.kt`.
- `Grep` — `fun showError(` still present in `MainActivity.kt` (function not deleted).
- `Grep -n "Log\.d\("` — zero hits in the file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-08 — Verification 4/4 PASS. Files: MainActivity.kt — AppErrorNotifier+ErrorSeverity imports added, showError else-branch replaced. Build PASS (58s). Zero TODO(phase-04) hits. Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entries added for every file in "Files Touched" and for each backup in `temp/`.
- [ ] `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` run.

---

## Handoff Notes to Next Phase

- `AddResourceConnectionManager` and `MainActivity` error paths now use `AppErrorNotifier`.
- Informational toasts (cloud sign-out confirmations, version update notice, status messages) remain as plain `Toast.makeText` — this is intentional.
- Phase 05 is the final cleanup phase.

---

## Rollback Plan

Restore from `temp/` backups created in Step 04.1. No data migration or schema change involved.
