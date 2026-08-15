---
name: wear-data-layer-applicationid-mismatch
description: Wear Data Layer needs an identical applicationId phone/watch - the project shipped mismatched ids so nothing was ever delivered; fixed in S1681 on 2026-08-15, do not let it drift back
metadata:
  type: project
---

**The watch app's `applicationId` must stay identical to app_v2's (`com.sza.fastmediasorter`), and the `namespace` must keep its `.wear` segment.** Fixed 2026-08-15 in S1681 (Verified); before that the watch shipped `com.sza.fastmediasorter.wear` and **no Data Layer payload had ever been deliverable in either direction** - not sources, not settings, not playback commands.

**Why:** Play Services routes by an `AppKey` = (package name, signing certificate) and requires **both** to match across devices ([Data Layer overview](https://developer.android.com/training/wearables/data/overview)). The drop happens inside `WearableService`, below the app, so it is invisible from both sides: the watch logs a sent message, the phone app is simply never called, and the phone UI still reports success. The only tell is a phone-side Play Services warning, not an app log:
`W/WearableService: Failed to deliver message to AppKey[..]; Event[onMessageReceived, action=/fms/network_sources/request ..]`

**How to apply:**
- Never "tidy" the watch `applicationId` back to something `.wear`-suffixed. The comment in `wear/build.gradle.kts` says so; this memory exists because the failure mode is silent and would be re-diagnosed from scratch.
- Debug watch build now installs as `com.sza.fastmediasorter.debug` - the same id as the phone debug build. That is correct, not a bug; it only collides if both were installed on one device.
- A watch-sync symptom is worth diagnosing at the payload/path/serialization level only after confirming delivery happens at all. Grep the **phone** log for `WearableService.*Failed to deliver` first - one line settles it.
- Working round trip looks like this across the two logs: watch `Sync request sent to node <id>` -> phone `message received /fms/network_sources/request` -> phone `putDataItem: /fms/network_sources/push` -> watch `handlePush: import done` -> phone `Watch ack received`.
- **Open follow-ups:** S1682 (phone reports success before any ack - `WearSyncViewModel.startPush` always beats its own ack collector, so the green check means "bytes accepted locally"). S1631 was untestable while delivery was dead and needs re-testing on a release pair. A shared `applicationId` also means Play needs distinct `versionCode`s for the phone and watch artifacts - unverified before the first Wear submission.

Related device facts: [[test-device-galaxy-s21]] (Galaxy Watch 7 entry - Wi-Fi-only adb, packages, signers).
