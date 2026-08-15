# Спецификация (compact bugfix): S1681 - Отправка ресурсов с телефона на часы не доходит

**Ticket:** S1681
**Status:** Archived
**Priority:** 90
**Date:** 2026-08-15
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-15

**Текст:**

ещё добавь, что синхронизация не работает - я имею ввиду не отправляются ресурсы с телефона на часы - телеонн показывает галочку "ок" - часы ждут "приходаа" потом выпадают по таймауту

**Захвачено во время:** установки wear-сборки на часы владельца (не тикет)

---

## 1. Проблема / симптом

Owner report, 2026-08-15: the phone reports the network-source push as successful (an "OK" check mark in the UI) while the watch sits waiting for the payload and eventually times out. Nothing arrives.

Environment at the time of the report, partly unconfirmed:

- Watch: Galaxy Watch 7 (SM-L310), Android 16 / API 36, carrying the **debug** wear build `com.sza.fastmediasorter.wear.debug`, installed the same day from `DOWNLOADS/FastMediaSorter_wear_debug.apk`.
- Phone: **not yet inspected.** The phone attached during this session (Galaxy S21+, SM-G996U1) is not the phone paired with the watch - the owner said so and was about to attach the right one. The S21+ carries both `com.sza.fastmediasorter` (release, versionName 2.60.8042.332) and `com.sza.fastmediasorter.debug` (2.60.8112.319-DEBUG); which build and which flavor the *paired* phone runs is unknown and must be established first.

What the code review of 2026-08-15 already rules out and rules in - all of it read, none of it executed against the failing pair:

- **Paths match, so a path typo is not the cause.** The phone publishes the data item at `/fms/network_sources/push` (`SendResourcesToWatchUseCase`, local const), and the watch registers `pathPrefix="/fms/network_sources"` in its manifest and matches `PATH_PUSH = "/fms/network_sources/push"` in `WatchWearListenerService`. The watch's "waiting" state is its own `/fms/network_sources/request` message going the other way.
- **A repeated identical payload is not the cause.** The Data Layer suppresses a `putDataItem` whose bytes are unchanged; here `WearSyncPayload.sentAt = System.currentTimeMillis()` differs on every push, so each write is genuinely new.
- **The phone's "OK" does not mean "the watch got it".** `SendResourcesToWatchUseCase` returns success right after `putDataItem` returns; the only delivery-related precondition it checks is `getConnectedNodes()` being non-empty, which reports a paired *device*, not a matching app on the other side. So a green check mark on the phone is consistent with the watch never receiving anything, and the UI is arguably overstating what it knows.
- **Leading hypothesis - a mixed release/debug pair cannot talk.** The Wear Data Layer only delivers between apps signed with the same key. The watch now runs a debug-signed build; if the paired phone is driving the release app from Play, delivery is dropped silently, `getConnectedNodes()` still returns the watch node, and the observed symptom follows exactly. First thing to test: drive the push from the phone's `com.sza.fastmediasorter.debug` build instead of the release one.
- **Second hypothesis - the phone flavor mounts the no-op bridge.** `wearStub/NoOpWearableDataLayerRepository` implements `putDataItem`/`sendMessage` as `Unit` and `getConnectedNodes` as `emptyList()`. It is mounted for `lite`, `photos` and `vr`; the real `wearGms` bridge is mounted only for `standard`, `noLegal` and `legacy` (`app_v2/build.gradle.kts`, `wearFlavors`). On a stub flavor the send is inert - though `getConnectedNodes()` returning empty would surface as "No watch connected" rather than an OK check mark, so this fits the report less well than the signing hypothesis and should be confirmed or dismissed by reading the phone's flavor.

Diagnostics to capture on the next attempt: phone-side logcat around `SendResourcesToWatchUseCase` (`Sent N resources to watch`, and the `S1631:` probe line), and watch-side logcat filtered on `WatchWearListenerService` to see whether `onDataChanged` fires at all.

### Measurement session 2026-08-15, watch + Galaxy S21+ attached together

Signing certificates read with `dumpsys package`, which settles the mixed-pair question structurally:

- phone release `com.sza.fastmediasorter` 2.60.8122.034 -> signer **8052f1a1**
- phone debug `com.sza.fastmediasorter.debug` 2.60.8112.319-DEBUG -> signer **271dbbd8**
- watch debug `com.sza.fastmediasorter.wear.debug` 2.60.6141.930-DEBUG -> signer **271dbbd8**

So the watch build pairs with the phone's DEBUG app and cannot exchange Data Layer traffic with the phone's RELEASE app. Driving the push from the release app is expected to fail silently. Both phone apps do register `PhoneWearListenerService` for `DATA_CHANGED`/`MESSAGE_RECEIVED`, so the `wearStub` hypothesis above is **dismissed**: the paired phone runs a Wear-capable flavor in both build types.

**The attempted reproduction was invalid, and that itself is the finding of the session.** Driving "Синхр. с телефоном" on the watch produced `W/NetworkSourcesViewModel$requestSyncFromPhone: no connected nodes`, and nothing at all reached the phone log. The reason is not the app: `dumpsys bluetooth_manager` shows the watch with an EMPTY bonded-device list and `ConnectionState: STATE_DISCONNECTED`, and the phone's bonded list contains only two audio devices and no watch. **These two devices are not paired with each other**, so no Data Layer link can exist in either direction. Whatever the owner observed originally happened on a genuinely paired pair; re-measure there before concluding anything about delivery.

### A defect that holds regardless of pairing: the phone's "OK" cannot mean delivery

`WearSyncViewModel.startPush` sets `WearSyncUiState.Success` inside `onSuccess` of the use case, guarded only by `if (_uiState.value is Sending)`. Its own comment says success is meant to arrive over the ack flow, with this as a fallback "if ack not received" - but the fallback runs the instant the local `putDataItem` returns, which is always before any watch ack could travel back. So:

- the ack collector in `init` (`WearSyncEvents.ackFlow`) tests `current is Sending` and by then the state is already `Success`, which makes the ack-driven success branch **unreachable in practice** - dead code, and with it the only real delivery confirmation the feature has;
- the green check mark therefore reports "the Data Layer accepted the bytes", never "the watch applied them", which is exactly the misleading signal in the report.

This part needs no paired hardware to confirm - it is readable in the source - but a fix must decide what the UI should show while waiting for an ack that may never come, which is an owner-facing question rather than a mechanical change. It is therefore **not** fixed here and is carried by its own ticket: `Carrier: S1682`.

---

## 2. Корневая причина

**The phone app and the watch app do not share an `applicationId`, and the Wear Data Layer refuses to deliver between apps whose package names differ.** Confirmed on hardware, with the devices genuinely paired, 2026-08-15.

Measured chain, end to end:

1. The devices were paired for this run (watch bonded to "Serhii's S21+", `ConnectionState: STATE_CONNECTED`; phone bonded to "Galaxy Watch7 (8CRZ)"). The earlier unpaired session is superseded.
2. Watch side, after pressing "Синхр. с телефоном": `D/NetworkSourcesViewModel$requestSyncFromPhone: Sync request sent to node cb9ec331`. The watch found the phone node and sent the message.
3. Phone side, same instant, from Play Services rather than from the app:
   `W/WearableService: Failed to deliver message to AppKey[<hidden#ad294bc0>,e8f8b98216ed136a5e82d326fab25f948101f974]; Event[onMessageReceived, action=/fms/network_sources/request, dataSize=0, source=2f75d419]`
4. The phone app process was alive throughout (pid 23160) and logged **nothing** - it was never handed the message.

Delivery therefore fails inside Play Services, one layer below the app, and neither side's code can observe it. The `AppKey` in that warning is the (package name, signing certificate) pair Play Services looks for on the receiving device:

- Signing certificates **match** and are not the problem: phone debug and watch debug both report signer `271dbbd8` via `dumpsys package`.
- Package names **differ and cannot match by construction**: phone `com.sza.fastmediasorter` (`.debug` in debug), watch `com.sza.fastmediasorter.wear` (`applicationId` in `wear/build.gradle.kts`, plus the same `.debug` suffix). The `.wear` segment guarantees the pair never lines up in any build type.

Android's Data Layer contract requires both halves: "The package name must match across devices, and the signature of the package must match across devices" ([Overview of Data Layer API](https://developer.android.com/training/wearables/data/overview)). The project satisfies the signature half and violates the package-name half, so **no Data Layer traffic has ever been deliverable in either direction** - not the source push, not the settings push, not playback commands.

Consequences for neighbouring work:

- S1631 (Gson field names surviving R8) is about a payload that arrives and deserializes wrong. Nothing arrives, so that ticket cannot be verified on device until this one is fixed, and its `BlockNeedUserTest` state is currently untestable rather than merely untested.
- The unreachable ack branch recorded in §1 is a second, independent defect. Fixing the package identity would make an ack physically possible for the first time, which is exactly when the dead branch starts to matter.

**Открытый вопрос владельцу, не решать самостоятельно:** the fix is to give the watch app the phone's `applicationId`. If the watch app has ever been published under `com.sza.fastmediasorter.wear`, changing it is a frozen-anchor change that orphans existing installs, so the owner must rule on whether that identity was ever shipped before anything is renamed.

---

## 3. Исправление

Owner ruling 2026-08-15: the watch app has never been published, so its install identity is free to change - no frozen-anchor concern, no orphaned installs.

`wear/build.gradle.kts`, `defaultConfig.applicationId`: `com.sza.fastmediasorter.wear` -> `com.sza.fastmediasorter`, matching app_v2. One line, plus a comment recording why it must never drift back, because the failure it causes is invisible from both sides and would be re-diagnosed from scratch.

Deliberately unchanged: `namespace` keeps the `.wear` segment. It is the code package (R, BuildConfig, class names) and has nothing to do with Data Layer routing; renaming it would churn every import for no benefit. The debug suffix `.debug` is untouched, so the debug watch build installs as `com.sza.fastmediasorter.debug` - the same id the phone's debug build uses, which is correct and only ever collides if both were installed on one device.

Documentation carrying the old install id, corrected in the same change: `docs/WEAR_OS_SETUP.md`, `docs/WEAR_OS_QUICK_START.md` (the `am start` component in both), `docs/WEAR_OS_BUILD_CONFIG.md`, `docs/WEAR_OS_STATUS.md`. The latter two also stated a stale SDK grid (Min 30 / Target 35 against the real 28 / 36) and a `1.0.0-MVP` version; corrected, and the version line replaced by a pointer to its generator so it cannot rot again.

Not fixed here, carried: `Carrier: S1682` - the phone's success indicator fires before any ack and needs an owner decision on what to show while waiting.

Worth knowing for the release, not addressed here: a shared `applicationId` means Play treats the phone APK and the watch APK as one app, so their `versionCode` values must stay distinct across the pair. `wear/build.gradle.kts` currently generates its own from the same base as app_v2 minus the minute digit, which does not guarantee that. Verify before the first Wear submission.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1631 (bugfix-wear-payload-gson-obfuscation, BlockNeedUserTest) - a different mechanism: a payload that arrives and deserializes wrong under R8. It was untestable while nothing arrived; this fix unblocks it, and it should be re-tested on a release pair before anyone trusts its state. S1682 - carries the false-success indicator split out of this ticket. S1678, S1679 - same device session, unrelated mechanisms.

---

## 4. Проверка

Verified on hardware 2026-08-15: Galaxy Watch 7 (SM-L310, Android 16) paired with Galaxy S21+ (SM-G996U1), both running the debug build.

Preconditions confirmed before the run, since the previous attempt failed on both of them:

- devices genuinely paired - watch bonded to "Serhii's S21+", `ConnectionState: STATE_CONNECTED`;
- identity now matches on both halves - package `com.sza.fastmediasorter.debug` and signer `271dbbd8` on phone and watch alike (`dumpsys package`).

Scenario: watch -> Сетевое хранилище -> "Синхр. с телефоном". Both logs, same second:

- watch: `NetworkSourcesViewModel$requestSyncFromPhone: Sync request sent to node cb9ec331`
- phone: `PhoneWearListenerService: message received /fms/network_sources/request`
- phone: `Watch requested sync - sending resources`
- phone: `WearableDataLayerRepositoryImpl: putDataItem: /fms/network_sources/push (73 bytes)`
- watch: `WatchWearListenerService$handlePush: import done ImportResult(added=0, updated=0, skipped=0)`
- phone: `PhoneWearListenerService: message received /fms/network_sources/ack`
- phone: `Watch ack received: {"added":0,"updated":0}`

Round trip complete in both directions, including the ack the watch had never been able to send before. The `WearableService: Failed to deliver message to AppKey[..]` warning that dominated the failing run does not appear at all.

`added=0` is expected and not a defect: the phone's debug app has no network sources configured, so there was nothing to send. A payload-carrying run - one source configured on the phone, pushed and listed on the watch - is the natural next check and belongs to whoever tests S1631.
