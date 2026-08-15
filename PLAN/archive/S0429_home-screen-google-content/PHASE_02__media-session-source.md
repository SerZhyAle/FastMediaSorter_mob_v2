# Phase 02 - Read any app's active media session

**Strategic spec:** [`../S0429_home-screen-google-content.md`](../S0429_home-screen-google-content.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 4 / 4
**Started:** 2026-08-06
**Completed:** 2026-08-06

---

## Objective

Add the notification-listener component and a second `NowPlayingSource` reading `MediaSessionManager.getActiveSessions`, chosen automatically when notification access is on.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] `CODE.LOCK` acquired before the first source edit.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/nowplaying/MediaSessionAccessService.kt` | New | ≤ 60 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/nowplaying/NotificationAccessState.kt` | New | ≤ 70 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/nowplaying/ActiveSessionNowPlayingSource.kt` | New | ≤ 150 |
| `app_v2/src/launcherEnabled/AndroidManifest.xml` | Modified | ≤ 80 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/AudioNowPlayingGadget.kt` | Modified | ≤ 170 |

> The listener service is declared in `src/launcherEnabled/AndroidManifest.xml`, the manifest injected only into `standard` and `noLegal` (`build.gradle.kts:1063`). No other flavor ever declares it, which is the Rule 14 placement this feature needs - no `BuildConfig` guard anywhere.

---

## Steps

### Step 02.1 - Declare the notification-listener component

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/nowplaying/MediaSessionAccessService.kt`, `app_v2/src/launcherEnabled/AndroidManifest.xml`

**Depends on:** - start of phase

**Prompt for developer:**

> Add `class MediaSessionAccessService : NotificationListenerService()` with no overridden callbacks - it exists so the system will hand `MediaSessionManager` a component it trusts, and it must not read notifications. Say that in its KDoc. Declare it in `src/launcherEnabled/AndroidManifest.xml` with `android:exported="false"`, `android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"` and the `android.service.notification.NotificationListenerService` intent filter. Do not add `<uses-permission>`: the permission is held by the service declaration, not requested by the app.

**Why:**

Strategic §8 records that `MediaSessionManager.getActiveSessions` refuses to answer without an enabled `NotificationListenerService` component, so the component is the price of a provider-agnostic now-playing block and there is no lighter API.

**Verification:**

- `Glob` - the service file exists.
- `Grep` - `class MediaSessionAccessService : NotificationListenerService()` matches exactly once.
- `Grep` - `BIND_NOTIFICATION_LISTENER_SERVICE` present in `src/launcherEnabled/AndroidManifest.xml`.
- `Grep` - `onNotificationPosted` returns zero hits in the service file.
- `.\a.ps1 fr` exits 0.

**Status:** `[x] done`

**Step Log:**

- 2026-08-06 - Verification 4/4 PASS. `class MediaSessionAccessService : NotificationListenerService()` 1 hit, `onNotificationPosted` 0 hits, `BIND_NOTIFICATION_LISTENER_SERVICE` present in the source-set manifest. `.\a.ps1 fr` exit 0 with `processStandardDebugManifest` *executed* rather than up-to-date, so the merge really saw the new `<service>`. Files: MediaSessionAccessService.kt (new, 13 LOC), src/launcherEnabled/AndroidManifest.xml (+13). The manifest header gained a paragraph saying the listener is declared and never requested - a notification listener inside a media app is exactly what a reviewer stops on, and the file's existing comments are written to answer that class of question.

---

### Step 02.2 - Answer whether notification access is on

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/nowplaying/NotificationAccessState.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add an object with `fun isEnabled(context: Context): Boolean` returning whether this package is in `NotificationManagerCompat.getEnabledListenerPackages(context)`, and `fun settingsIntent(): Intent` returning `Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)`. Read the state fresh on every call - the user can revoke it in system settings while the launcher is running, and a cached answer would leave the gadget claiming an access it no longer has.

**Why:**

Strategic §3.3 makes the access opt-in and revocable, and the gadget has to degrade to the app's own session the moment it goes away rather than showing a stale foreign session.

**Verification:**

- `Glob` - the file exists.
- `Grep` - `getEnabledListenerPackages` present.
- `Grep` - `ACTION_NOTIFICATION_LISTENER_SETTINGS` present.

**Status:** `[x] done`

**Step Log:**

- 2026-08-06 - Verification 3/3 PASS. File exists, `getEnabledListenerPackages` and `ACTION_NOTIFICATION_LISTENER_SETTINGS` each 1 hit. Files: NotificationAccessState.kt (new, 20 LOC). No build predicate on this step; it compiles as part of Step 02.3's `fk`.

---

### Step 02.3 - Read and drive the active session

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/nowplaying/ActiveSessionNowPlayingSource.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Add `class ActiveSessionNowPlayingSource(private val context: Context) : NowPlayingSource`. `read()` asks `MediaSessionManager.getActiveSessions(ComponentName(context, MediaSessionAccessService::class.java))` for the controllers, takes the first whose `playbackState` is playing - falling back to the first controller at all when none is playing - and maps its `metadata` title and artist onto `NowPlayingState`, with `canControl = true`. `send()` calls `transportControls.skipToPrevious()`, `.play()`/`.pause()` by current state, or `.skipToNext()` on that same controller. Wrap the whole `getActiveSessions` call in `runCatching`: the platform throws `SecurityException` when access is revoked between the check and the call, and the correct answer there is an inactive state, not a crash. Log the failure kind through Timber - never the track title, the artist or the package.

**Why:**

Strategic §2 goal 4 asks for a provider-agnostic block covering YT Music, Spotify and any other player, which is exactly what the session list gives without a single Google API or a hardcoded package name.

**Verification:**

- `Glob` - the file exists.
- `Grep` - `getActiveSessions` present exactly once.
- `Grep` - `runCatching` present.
- `Grep` - `com.google.android` and `com.spotify` return zero hits in that file.
- `.\a.ps1 fk` exits 0.

**Status:** `[x] done`

**Step Log:**

- 2026-08-06 - Verification 5/5 PASS. `getActiveSessions` 1 hit, `runCatching` 2 hits (the session query and the transport send), `com.google.android` and `com.spotify` 0 hits. `.\a.ps1 fk` exit 0 with `compileStandardDebugKotlin` executed. Files: ActiveSessionNowPlayingSource.kt (new, 73 LOC). The Timber line names only the exception class, never the track, the artist or the package, and carries no ticket id - permanent logs may not (CLAUDE.md §2).

---

### Step 02.4 - Pick the source on every render

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/AudioNowPlayingGadget.kt`
**Depends on:** Step 02.3

**Prompt for developer:**

> Replace the single `OwnSessionNowPlayingSource` field with a resolver that returns the active-session source when `NotificationAccessState.isEnabled(context)` and the own-session source otherwise, and call it from `render()` and from `send()`. Resolve per call, not once in the constructor - the gadget is long-lived and the user may grant or revoke access while it is on screen. When the active-session source reports nothing playing, fall back to reading the own-session source in the same pass, so the app's own playback never disappears just because another player is idle.

**Why:**

Strategic §3.3 states the refusal case shows the app's own session rather than hiding the gadget, and the same rule reads naturally as the general one: foreign session when there is one, own session otherwise.

**Verification:**

- `Grep` - `NotificationAccessState.isEnabled` present in that file.
- `Grep` - both `ActiveSessionNowPlayingSource` and `OwnSessionNowPlayingSource` present in that file.
- `Grep` - `Log\.d\(` returns zero hits in every file this phase modified.
- `.\a.ps1 fc` exits 0.

**Status:** `[x] done`

**Step Log:**

- 2026-08-06 - Verification 4/4 PASS. `NotificationAccessState.isEnabled` 1 hit, `ActiveSessionNowPlayingSource` 2 hits, `OwnSessionNowPlayingSource` 2 hits, `Log.d(` 0 hits across all five files this phase touched. `.\a.ps1 fc` exit 0 with `compileStandardDebugKotlin` executed. Files: AudioNowPlayingGadget.kt (120 -> 145 LOC, budget 170). `resolve()` returns the source *and* the state it just produced, so `render()` does not read twice and `send()` reaches whichever source is answering at click time - the pair is what makes "fall back in the same pass" and "resolve per call" hold together. The `onActive` KDoc, flagged P3 at the Phase 01 boundary, was rewritten here as planned: both sources are pull-only, which is the reason the loop exists.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fc` exit 0.
- [x] `Grep` for `TODO(phase-02)` returns zero hits in `app_v2/src`.
- [x] Dev log entry added for the phase via `post-change.ps1` - `post-change: PASS (Mixed, 66524 ms)`, exit 0.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated - 2484 records, up from 2481.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings. See below.
- [x] `CODE.LOCK` released - by the facade at the end of its run; a sibling session took it 4 seconds later.

### Phase-boundary audit (Layers 1-3)

- **Layer 3, the finding this phase was told to look for, and it is clean.** No `MediaController` is ever retained: `activeController()` fetches the list, picks one, uses it and drops it inside a single call, and no `MediaController.Callback` is registered anywhere. There is therefore no registration to balance and no live system handle outliving the view - the listener-symmetry gate agrees (`new imbalance 0`). The polling design is what buys this: a push design would have needed exactly the retained callback this avoids.
- **Layer 2:** no coroutine, dispatcher or lifecycle surface changed. The loop, its interval and `onActive` are untouched from Phase 01, and both new sources are synchronous and stateless.
- **Layer 1:** `resolve()` is the only new decision, it is one expression deep, and the two sources are interchangeable behind the seam Phase 01 built.
- **P2, recorded and deliberately not fixed here:** a render tick now costs two binder round trips instead of one SharedPreferences read - `getEnabledListenerPackages` (a `Settings.Secure` read) and, when access is on, `getActiveSessions`. At one tick per two seconds, bounded to a gadget that is both attached and STARTED, this is affordable, and the alternative - subscribing to a `MediaController.Callback` - trades it for the retained system handle the Layer 3 note above says the design deliberately does not hold. Revisit only if a device test shows home-screen jank.
- **Rule 14 holds by placement:** all five files live in `src/launcherEnabled`, which only `standard` and `noLegal` mount. No `BuildConfig` guard was introduced, and the flavor-flag gate reports `new occurrences 0`.

---

## Handoff Notes to Next Phase

The capability works end to end for a user who has already granted notification access in system settings. Nothing in the app offers that access yet - Phase 03 adds the only two places it can be turned on.

---

## Rollback Plan

Revert the phase commit. The manifest gains a `<service>`; reverting removes it, and an access the user granted simply stops being used.
