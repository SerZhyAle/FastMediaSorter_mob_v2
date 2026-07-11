---
name: link-download-present-suppressed-for-success
description: Link auto-download presenter open-in-player + any present()-only probes are dead for successful downloads (worker suppression)
metadata:
  type: project
---

`LinkAutoDownloadResultPresenter.present()` never runs for a successful download (Saved / FellBackToDownloads / BatchCompleted).

**Why:** `LinkDownloadWorker` is the only result producer and always publishes to `ShareDownloadResultBus` with `notificationShown = true` (LinkDownloadWorker.kt ~139). `MainActivity`'s bus collector guards `if (pending.notificationShown && (isSuccess || isAuthGated)) return` (MainActivity.kt ~299), so it early-returns for every success. The real user-facing open-in-player is the result-notification content-intent (`buildOpenInPlayerPendingIntent`), which per S0257 opens on tap regardless of the `linkAutoDownloadOpenInPlayer` setting.

**How to apply:** Any fix or verification that depends on `present()` running for a successful download - the setting-gated auto-open, or `Timber.d("Sxxxx: ..")` probes placed inside `present()` - is unreachable in the normal share flow and cannot be exercised on-device (e.g. S0980's probe-based device contract was INCONCLUSIVE for exactly this reason). Put probes/verification on the writer (`LinkDownloadWriter`, feeds `destinationUri`) and the notification content-intent, not the presenter. When writing a link-download UX spec, decide first whether the presenter path is even live before specing behavior there.

**UPDATE 2026-07-11 (S0981, code-in-tree, BlockNeedUserTest - not yet device-verified):** the suppression was fixed to variant A. `MainActivity` collector no longer early-returns for success (only `SocialPreviewOnly`/`isAuthGated` still suppressed); it passes `notificationShown` into `present()` and calls `shareResultBus.clearReplayCache()` after handling (consume-once - `replay = 1` would otherwise re-open the player on every return/recreation). `present()` gained `notificationShown: Boolean`; success branches now `launchPlayer()` when `openInPlayer && uri != null` and gate only the toast-fallback behind `!notificationShown`. So the presenter open-in-player path AND its `S0980:` probes are now REACHABLE in the foreground share flow. Decision was resolved from the setting contract (`link_autodownload_open_in_player_summary` promises automatic open), not an owner question. Re-read live code before assuming the branch is still dead.
