# Phase 05 - Video Frame & Internet Download Alignment

**Strategic spec:** [`../S0522_fallback-save-unavailable-resource.md`](../S0522_fallback-save-unavailable-resource.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** none
**Steps done:** 3 / 3
**Started:** 2026-06-19
**Completed:** 2026-06-19

---

## Objective

Align the two flows that already fall back ad-hoc (video frame grab, internet download) onto the shared vocabulary and surfaces: reachability pre-check, the shared `SaveFallbackReason` enum, and notification (foreground for the in-player frame, background system notification for the worker-driven download).

---

## Prerequisites

- [ ] Phase 01, 02 are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/SaveVideoFrameManager.kt` | Modified | ≤ 320 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/link/LinkDownloadWriter.kt` | Modified | ≤ 270 |
| `app_v2/src/main/java/com/sza/fastmediasorter/worker/LinkDownloadWorker.kt` | Modified | ≤ 500 |

---

## Steps

### Step 05.1 - Video frame: reachability pre-check + foreground notification

**Files:** `ui/player/helpers/SaveVideoFrameManager.kt`
**Depends on:** Phase 01 Step 01.1, Phase 02 Step 02.2

**Prompt for developer:**

> Inject `NetworkStateMonitor` and `SaveFallbackNotifier`. In `saveCurrentFrame()`/`trySaveToResource(..)`, when the configured snapshot resource is a network resource and `!networkStateMonitor.canReach(type)`, skip the network attempt and go straight to the existing `saveToDownloads(..)` path, marking the reason `ResourceUnavailable`. Keep the existing write-time `saveToDownloads` fallback as the safety net (reason `ResourceWriteFailed`). When a fallback by unavailability/write-failure occurred, call `saveFallbackNotifier.notify(reason, folderLabel = "Downloads", resourceName, background = false)`. Do not notify when there was simply no configured resource.

**Verification:**

- `Grep` - `canReach(` present in `SaveVideoFrameManager.kt`.
- `Grep` - `saveFallbackNotifier` `.notify(` present with `background = false`.
- `Grep -n "Log\.d\("` - zero hits in the file.

**Status:** `[x] done`

**Step Log:**

- 2026-06-19 - PASS. canReach pre-check + write-time fallback reason; foreground notify on Downloads redirect.

---

### Step 05.2 - Internet download: adopt shared reason + reachability pre-check

**Files:** `data/link/LinkDownloadWriter.kt`
**Depends on:** Phase 01 Step 01.1, 01.2

**Prompt for developer:**

> Replace the local `enum class FallbackReason { .. }` with the shared `com.sza.fastmediasorter.domain.model.SaveFallbackReason`, updating all `WriteResult.FellBackToDownloads` references accordingly. Inject `NetworkStateMonitor`. Before streaming into a network resource, if the resolved `resource.type.isNetworkResource` and `!networkStateMonitor.canReach(resource.type)`, skip the copy attempt and go straight to `saveToDownloads(..)` with reason `ResourceUnavailable`. Keep the existing write-time fallbacks, but map them to the shared reasons (`ResourceWriteFailed` for copy `Failure`, `ResourceUnavailable` for auth/unreachable). Do not add notification here - the worker (Step 05.3) owns the background surface.

**Verification:**

- `Grep` - `enum class FallbackReason` returns zero hits in `LinkDownloadWriter.kt` (removed).
- `Grep` - `SaveFallbackReason` referenced in the file.
- `Grep` - `canReach(` present.

**Status:** `[x] done`

**Step Log:**

- 2026-06-19 - PASS. Removed local FallbackReason enum -> shared SaveFallbackReason; coordinator mapping updated; reachability pre-check added.

---

### Step 05.3 - Internet download: background notification on unavailability fallback

**Files:** `worker/LinkDownloadWorker.kt`
**Depends on:** Step 05.2, Phase 02 Step 02.2

**Prompt for developer:**

> The worker already posts a rich completion notification per `LinkAutoDownloadCoordinator.Result`. Rather than emitting a second (redundant) notification, make the existing `FellBackToDownloads` branch reason-aware: when `result.reason` is `ResourceUnavailable` or `ResourceWriteFailed`, set the content text to a localized "saved to Downloads because the destination was unavailable" string; keep the neutral saved-text for `NoResourceConfigured`. Add the trilingual string `link_download_notif_text_saved_fallback` (EN/RU/UK).

**Verification:**

- `Grep` - `link_download_notif_text_saved_fallback` referenced in `LinkDownloadWorker.kt` and present in all three `strings.xml`.
- `Grep` - `FallbackReason.ResourceUnavailable` referenced (the reason-aware branch).
- `Grep -n "Log\.d\("` - zero hits in the file.

**Status:** `[x] done`

**Step Log:**

- 2026-06-19 - PASS (approach adjusted: reason-aware text on the worker's existing notification instead of a redundant second notification). Trilingual string added; audit exit 0.

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (`.\a.ps1 fc`).
- [ ] `Grep` for `TODO(phase-05)` returns zero hits.
- [ ] No `enum class FallbackReason` remains anywhere - shared `SaveFallbackReason` is the single vocabulary (`Grep` across `app_v2/src`).
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

All six save flows now share one reachability source, one fallback-reason vocabulary, one local-write path, and one notification surface. Final phase regenerates the catalog and records the capability.

---

## Rollback Plan

Revert phase commit(s). The enum swap is the only cross-file change - if reverted, restore `LinkDownloadWriter`'s local enum together with the worker change.
