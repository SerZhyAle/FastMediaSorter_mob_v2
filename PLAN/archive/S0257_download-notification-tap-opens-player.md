# S0257 - Download Notification Tap Opens Player

- Ticket: `S0257`
- Status: BlockNeedUserTest
- Priority: 60
- Roadmap: ad-hoc
- Origin: user-reported UX gap on link-share auto-download notifications

## Goal

Уведомления «Загружено» / «Сохранено», которые `LinkDownloadWorker` показывает после завершения скачивания по входящей ссылке, сейчас никуда не ведут — клик по ним только убирает уведомление из шторки. Нужно сделать само тело уведомления кликабельным: тап открывает `StandalonePlayerActivity` на скачанном файле. Если в рамках одного share-batch было загружено несколько файлов, открывается первый успешный.

## Affected Surface

- `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinator.kt` - `Result.BatchSummary` carries the first successful save URI; `openInPlayerUri` is always populated for `Saved` / `FellBackToDownloads` (no longer gated on `linkAutoDownloadOpenInPlayer`).
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/LinkAutoDownloadResultPresenter.kt` - foreground auto-open keeps obeying `linkAutoDownloadOpenInPlayer` (gate moved from coordinator into presenter).
- `app_v2/src/main/java/com/sza/fastmediasorter/worker/LinkDownloadWorker.kt` - result notification gets `setContentIntent(...)` for Saved / FellBackToDownloads / BatchCompleted with the saved file URI.

## Non-Goals

- No change to the progress notification (`NOTIF_ID_PROGRESS`) or to the existing Sign-In / Open-URL actions on `SocialPreviewOnly` and `UnsupportedYouTubeCommunityPost`.
- No change to the toast / dialog UX path that fires when MainActivity is foreground and `notificationShown == false`.
- No change to `linkAutoDownloadOpenInPlayer` semantics from the user's point of view - the setting still controls whether the player opens automatically; an explicit notification tap is always honoured.
- No new strings, no manifest change, no Room migration, no Hilt scope change.

## Phase 01 - Carry first saved URI through `BatchSummary`

### Step 01.01 - Add `firstSavedUri` to `BatchSummary`

Edit `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinator.kt`:

- Add field `val firstSavedUri: Uri? = null` to `data class BatchSummary` (after `failures`). Default `null` to keep existing call sites compatible.

Verification (PowerShell, repo root):

```powershell
Select-String -Path 'app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinator.kt' -Pattern 'firstSavedUri'
```

Expected: at least one line matching `val firstSavedUri: Uri? = null` inside `BatchSummary`.

### Step 01.02 - Capture first success URI inside `runBatch`

In the same file, inside `runBatch(...)`:

- Introduce a local `var firstSavedUri: Uri? = null` before the loop.
- Inside the `when (val itemResult = handleUrl(...))` block, on the success branch (`is Result.Saved` and `is Result.FellBackToDownloads`):
  - Cast / smart-cast to read `openInPlayerUri`.
  - If `firstSavedUri == null` and the item URI is non-null, assign it.
- Pass `firstSavedUri = firstSavedUri` into `BatchSummary(...)` at the bottom.

Note: this step relies on Step 02.01 (`openInPlayerUri` always populated). Order in this phase first, that one second.

Verification:

```powershell
Select-String -Path 'app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinator.kt' -Pattern 'firstSavedUri'
```

Expected: at least three hits - field declaration, local var assignment inside `runBatch`, and field write at `BatchSummary(...)` construction.

## Phase 02 - Always populate `openInPlayerUri`, move gate into presenter

### Step 02.01 - Drop the `takeIf { openInPlayer }` filter on `openInPlayerUri`

Edit `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinator.kt`:

- For all four `openInPlayerUri = writeResult.destinationUri.takeIf { openInPlayer }` sites (currently lines 345, 361, 518, 527), replace with `openInPlayerUri = writeResult.destinationUri`.

Rationale: the URI is now reused for the notification's content intent. Whether to auto-open in foreground is decided by the presenter, not by nullifying the URI at source.

Verification:

```powershell
Select-String -Path 'app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinator.kt' -Pattern "openInPlayerUri\s*=\s*writeResult\.destinationUri\.takeIf"
```

Expected: 0 matches (all four occurrences removed).

### Step 02.02 - Keep presenter gate on `linkAutoDownloadOpenInPlayer`

Edit `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/LinkAutoDownloadResultPresenter.kt`:

- Confirm the two existing branches (`is Result.Saved` and `is Result.FellBackToDownloads`) still gate with `if (openInPlayer && result.openInPlayerUri != null)`. The URI being non-null now reflects a real saved file rather than a feature-flag remnant - no semantic change for the foreground UX.

Verification:

```powershell
Select-String -Path 'app_v2/src/main/java/com/sza/fastmediasorter/ui/share/LinkAutoDownloadResultPresenter.kt' -Pattern 'openInPlayer && result\.openInPlayerUri != null'
```

Expected: exactly 2 matches.

## Phase 03 - Wire the content intent on the result notification

### Step 03.01 - Build a player PendingIntent helper inside the worker

Edit `app_v2/src/main/java/com/sza/fastmediasorter/worker/LinkDownloadWorker.kt`:

- Add a private helper:

```kotlin
private fun buildOpenInPlayerPendingIntent(uri: Uri, originalUrl: String): PendingIntent {
    val intent = Intent(context, StandalonePlayerActivity::class.java)
        .setData(uri)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
    return PendingIntent.getActivity(
        context,
        20_000 + Math.floorMod(originalUrl.hashCode(), 10_000),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
```

Request-code base `20_000` avoids collisions with `buildSignInAction` (range starts at `0`) and `buildOpenUrlAction` (range starts at `10_000`).

`FLAG_ACTIVITY_NEW_TASK` is mandatory because the worker fires the PendingIntent from a non-Activity context. `FLAG_GRANT_READ_URI_PERMISSION` mirrors the existing in-process launch in `LinkAutoDownloadResultPresenter.launchPlayer`.

Import the activity at the top:

```kotlin
import com.sza.fastmediasorter.ui.player.StandalonePlayerActivity
```

Verification:

```powershell
Select-String -Path 'app_v2/src/main/java/com/sza/fastmediasorter/worker/LinkDownloadWorker.kt' -Pattern 'buildOpenInPlayerPendingIntent'
```

Expected: at least 1 match (declaration).

### Step 03.02 - Attach `setContentIntent` for Saved / FellBackToDownloads / BatchCompleted

In the same file, inside `postResultNotification(...)`:

- For each of the three success branches in the `when (result)` block, after building the title/text but before the `when` block ends, attach a content intent when a URI is available:
  - `is Result.Saved` → `result.openInPlayerUri?.let { builder.setContentIntent(buildOpenInPlayerPendingIntent(it, originalUrl)) }`.
  - `is Result.FellBackToDownloads` → same pattern with `result.openInPlayerUri`.
  - `is Result.BatchCompleted` → `result.summary.firstSavedUri?.let { builder.setContentIntent(buildOpenInPlayerPendingIntent(it, originalUrl)) }`.
- Failure branches stay untouched - they have no playable file.

`.setAutoCancel(true)` is already on the builder, so the notification dismisses itself on tap.

Verification:

```powershell
Select-String -Path 'app_v2/src/main/java/com/sza/fastmediasorter/worker/LinkDownloadWorker.kt' -Pattern 'setContentIntent\(buildOpenInPlayerPendingIntent'
```

Expected: exactly 3 matches (one per success branch).

## Phase 04 - Build + observability

### Step 04.01 - Build gate (standardDebug) - DEFERRED

Status: `[DEFERRED - pre-existing branch breakage]`.

`./a.ps1 dq` on `DEBUG-v004` fails with ~6029 unresolved-reference errors across ~130 unrelated `.kt` files (the in-flight RevisedSettings removal + layout-binding refactor). The errors do NOT touch the three files modified by S0257 (`LinkAutoDownloadCoordinator.kt`, `LinkDownloadWorker.kt`, `LinkAutoDownloadResultPresenter.kt`) - verified by `Select-String` against the build log: 0 hits on any S0257-touched path.

Resolution path: once the branch's pre-existing breakage is resolved (out of scope for this ticket), re-run `./a.ps1 dq` and expect `BUILD SUCCESSFUL`.

### Step 04.02 - Catalogue sync

```powershell
pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2
```

Verification: exit code 0. Updated `dev/CATALOG/app_v2.jsonl` and `dev/CATALOG/app_v2.md` will be committed alongside the code.

### Step 04.03 - Dev changelog

```powershell
./scripts/add_to_dev_log.ps1 'app_v2/src/main/java/com/sza/fastmediasorter/worker/LinkDownloadWorker.kt' 'S0257' 'Result notification (Saved/FellBackToDownloads/BatchCompleted) is now clickable - tap opens StandalonePlayerActivity on the saved file (first one for batches).'
```

Verification: exit code 0; new bullet appended to `dev/CHANGELOG.md`.

### Step 04.04 - Functionality log

```powershell
./scripts/add_to_functionality_log.ps1 -Id S0257 -Op FIX -Description 'Auto-download result notifications are now tappable: opens the saved file in the standalone player (first item for batch shares).'
```

Verification: exit code 0; new line in `dev/FUNCTIONALITY.log`.

## Last Audit

**Date:** 2026-05-21
**Mode:** full
**Flags:** -
**Outcome:** BlockNeedUserTest (no flip — Manual tap gate still open)
**Counts:** PASS 1 · WARN 0 · FAIL 0 · MANUAL 1 · EXEMPT 0

Log `logs/fastmediasorter_20260521_141313.log` shows `Timber.d("S0257: building open-in-player PendingIntent for uri=content://media/external/downloads/...")` firing twice — once per batch:
- line 946 (`uri=content://media/external/downloads/17498`, after the 12-item batch completes via `LinkDownloadNotification set total=12 success=12`)
- line 2801 (`uri=content://media/external/downloads/17499`, after the 50-item batch completes via `LinkDownloadNotification set total=50 success=50 label=The Hits: '80s`)

This confirms the worker path: success branches in `postResultNotification` (`Saved` / `FellBackToDownloads` / `BatchCompleted`) DO build the player PendingIntent via `buildOpenInPlayerPendingIntent`, and the saved-file URI propagates correctly (via `firstSavedUri` in the BatchCompleted case). The `setContentIntent(...)` wiring on the notification builder is the only path required by Phase 03; the helper request-code base (20_000 + hash) avoids collision with `buildSignInAction`/`buildOpenUrlAction`.

What the log does NOT exercise: the actual tap on the notification body. The PendingIntent is built but never resolved by the system. This is the only remaining gate before Verified.

### Manual / on-device

- [ ] Share a single Threads / YouTube reel into the app while it is backgrounded → wait for «Загружено» / «Сохранено» notification → tap the notification body (not the action button) → confirm `StandalonePlayerActivity` opens on the saved file.
- [ ] Share multiple URLs in one batch → wait for «Завершено» batch notification (`total=N success=M`) → tap → confirm first successful file opens.
- [ ] Toggle `linkAutoDownloadOpenInPlayer = false` in Settings → re-run the share-tap flow → confirm the notification tap still opens the player (the toggle gates only the foreground auto-open path, never the explicit tap).

### Code path coverage

- Phase 01 (carry `firstSavedUri` through `BatchSummary`) — covered by line 2801 (batch URI present).
- Phase 02 (drop `takeIf { openInPlayer }` in coordinator) — covered by line 946 (`Saved` URI present even though completion was background-only; presenter gate not in play here).
- Phase 03 (worker `buildOpenInPlayerPendingIntent` + `setContentIntent`) — directly proven by `Timber.d("S0257: building open-in-player PendingIntent for uri=…")` × 2.

## Manual / On-Device

Verification of the actual tap behaviour is device-only and feeds the `BlockNeedUserTest → Verified` transition:

- Share a single Instagram / Threads / YouTube reel into the app while the app is backgrounded → wait for «Загружено» notification → tap the notification body (not the actions) → `StandalonePlayerActivity` opens on the saved file.
- Share multiple URLs in one batch → wait for «Завершено» batch notification → tap → first successful file opens.
- Verify nothing changes when `linkAutoDownloadOpenInPlayer` is disabled in settings: foreground completion still suppresses auto-open (existing behaviour), but the notification tap still opens the player (this is intentional - the tap is an explicit user choice).
