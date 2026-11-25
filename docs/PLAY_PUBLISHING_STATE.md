<sub class="doc-stamp">26.11.24 15:19</sub>

# Play publishing state

The single record of what Google Play is currently publishing for `com.sza.fastmediasorter`: what the
store serves, what each track holds, what the Console's `Policy status` page lists, and how the
published builds behave on users' devices (Android vitals, block 4, S2917). Before this
file existed the answer was derived on demand from ticket status notes and from memory - three times
in one week, and once wrongly (S2272, ADR-2).

**The rule every row obeys.** Each row carries its own measurement date and its own source class. A
row with no date is read as **unmeasured**, never as unchanged. Two source classes exist:

- `measured` - written by a script, from the Play Developer API or from an anonymous page fetch.
- `transcribed` - typed in by the owner from a Console screen no API serves.

The four blocks are deliberately kept apart because they disagree in practice. The console can report
a track `completed` while the store still serves a two-week-old build, and a record holding only one
of those two rows reads as green.

---

## 1. Public serve - `measured`

What an anonymous visitor actually receives. Produced by
`pwsh -NoProfile -File scripts/release/read-play-public-serve.ps1 -Json`.

**The listing page carries no version since 2026-10-27 (S4463).** The `Served version` cell reads
`not on the page` while that holds, and the signal is the store's `Updated at (UTC)` stamp: it moves
when the store publishes a new release, and `Updated on` is its date. A release has landed when
`read-play-public-serve.ps1 -RequireUpdatedAfter <the Updated at value recorded below>` exits 0.
Cross-read the `production` row of block 2 for the version that stamp belongs to. If the page serves a
version again, the reader reads it with no change and the cell shows it.

<!-- s2272:measured:public-serve:begin -->

| Served version | Store `Updated on` | Updated at (UTC) | Detected by | Measured (UTC) |
|----------------|--------------------|------------------|-------------|----------------|
| not on the page | Nov 12, 2026 | `2026-11-12T17:53:51Z` | none - the listing page carries no version | 2026-11-24 |

Reader exit code: 0.

<!-- s2272:measured:public-serve:end -->

---

## 2. Track state - `measured`

What each track holds. Produced by
`pwsh -NoProfile -File scripts/release/read-play-tracks.ps1 -Json`.

A track's `completed` status shows the configured rollout. For release lifecycle summaries on a
specific track, Google documents a separate GET outside an edit. For the app-wide review batch and
policy claims, use the Console. For public availability, use the served store page. The table below
comes from the edit-based track reader. See GOOGLE-PLAY section 8B and the
[release lifecycle reference](https://developers.google.com/android-publisher/api-ref/rest/v3/applications.tracks.releases/list).

**One row per release, live-first (S4881).** A track holds several releases at once, so a track may own
several rows here. They are ordered `completed`, `inProgress`, `halted`, `draft`, then any other status,
and by version code descending inside one rank: a track's **first** row is what it currently serves, and
a `draft` row is a release staged on the track and not yet rolled out. Until 2026-11-24 only a track's
first release was rendered, which dropped the live release of any track that also held a draft.

<!-- s2272:measured:tracks:begin -->

| Track | versionName | versionCode | Status | Measured (UTC) |
|-------|-------------|-------------|--------|----------------|
| `production` | `2.61.1121.756` | `261112175` | completed | 2026-11-24 |
| `beta` | - | - | no release | 2026-11-24 |
| `alpha` | - | - | no release | 2026-11-24 |
| `internal` | `2.61.1121.756` | `261112175` | draft | 2026-11-24 |
| `wear:beta` | - | - | no release | 2026-11-24 |
| `wear:internal` | - | - | no release | 2026-11-24 |
| `wear:production` | `2.61.1121.733` | `261112178` | completed | 2026-11-24 |

Reader exit code: 0.

<!-- s2272:measured:tracks:end -->

**Drift 2026-09-25, later the same day: the S3555 republish went up, held.** `production` moved to
`260926004` and `wear:production` to `260926008`, both `2.60.9260.042`, uploaded by
`publish-play-release.ps1` with `changesNotSentForReview` - Play still refuses automatic review while the
2026-09-25 rejection stands. Nothing is in review until the owner sends the batch from `Publishing
overview`; both groups sit in it, and `Save for later` on the watch group is what keeps the phone from
travelling with the watch again (recovery plan, step 1). Pre-flight record:
`PLAN/S3555_hotfix-wear-stopwatch-ongoing-activity/evidence/preflight-2.60.9260.042.md`.

**Drift 2026-09-12 -> 2026-09-25: one joint submission, rejected on the watch.** `production` moved to
`260924231` and `wear:production` to `260924236`, both `2.60.9242.311` - the phone and the watch
travelled in one batch again. Block 1 still serves `2.60.9191.906`, so nothing of that batch reached
users: the watch verdict of 2026-09-25 (block 3) held the phone artifact with it, the failure step 1 of
the recovery plan below exists to prevent.

**Drift 2026-09-11 -> 2026-09-12: the phone release v2.60.9121.346 went up.** `production` moved from
`260902195` to `260912134` / `2.60.9121.346`, uploaded and committed by
`scripts/release/publish-play-release.ps1` on 2026-09-12 and **held**: Play refuses automatic review
for this app, so the uploader committed with changes held and the owner sent them for review from the
Console the same day. Block 1 still serves `2.60.9021.951` - that is the expected shape while a
release is in review, not a disagreement of the kind recorded through 2026-08-31. `wear:production`
and `internal` did not move.

**Drift 2026-09-09 -> 2026-09-10** - kept here, outside the marked region, because
the refresher overwrites what is inside it. Measured 2026-09-10 (S2294): **no row moved.** Every
versionCode, versionName and status is identical to the 2026-09-09 measurement; only the measurement
dates advanced. Two consequences worth stating rather than re-deriving:

- Blocks 1 and 2 now **agree** for the first time in this record: `production` holds `260902195` /
  `2.60.9021.951` and the store serves that same `2.60.9021.951`. The two-week disagreement recorded
  through 2026-08-31 (track ahead of store) is closed.
- `internal` still holds the stale `2.60.6222.324` / `260622232` from 2026-06-22, `completed`. Step 2
  of the recovery plan has not been executed.

**Drift 2026-08-31 -> 2026-09-09**, kept for the chain: `production` went from `2.60.8250.134` /
`260825013` to `2.60.8241.708` / `260824170` (the rejected release off the track, the previous one
back), then to today's `260902195`; `wear:internal` held a draft on 2026-08-31 and has held nothing
since.

---

## 3. Policy status - `transcribed`

<!-- s2272:transcribed:policy-status -->

**No script writes this block.** The Play Developer API exposes tracks, edits and listings; it exposes
no policy or rejection surface at all. This block is transcribed by the owner from
`Policy status` in the Console, so it can only be aged, never refreshed. `refresh-play-publishing-state.ps1`
reports how old it is and changes nothing in it.

**Transcribed:** 2026-08-31, from the owner's screenshot.

`Update rejected` - four rows, all dated 2026-08-31, all Wear:

| Row | Claim | Carrier |
|-----|-------|---------|
| 1 | `Wear App Quality Guidelines: Wear app functionality not working as described` | S2275 |
| 2 | `Wear App Quality Guidelines: Wear app functionality not working as described` (duplicate line) | S2275 |
| 3 | `Wear App Quality Guidelines: Missing app icon in splash screen` | S2274 |
| 4 | `Wear App Quality Guidelines: Watch shapes` | S2273 |

**Transcribed:** 2026-09-09, from the owner's Play verdict letter of 2026-09-08 (quoted verbatim in
S2756 section 0), Routing ID `ZLFS`, versionCode `26090503`.

`Update rejected` - one row, dated 2026-09-08, Wear:

| Row | Claim | Carrier |
|-----|-------|---------|
| 1 | `Issue found: Wear app functionality not working as described` / `The functionality of your app doesn't work as described.` | S2755, S2756 |

This is a **separate verdict on a separate artifact**, not a restatement of the rows above: the
2026-08-31 batch carried versionCode `26082501` (block 2, `wear:production`), this letter carries
`26090503` and its own Routing ID. Both entries therefore stand, each under its own date, exactly as
the rule at the top of this file requires.

**This entry is the letter, not the page.** `Policy status` in the Console has not been re-read
since 2026-08-31, so nothing here says whether the four August rows are still listed there. A letter
reports one submission's verdict; the page reports the app's standing claims. The next screenshot
transcription is what reconciles them - until then, treat the August table as unmeasured rather than
as current.

The claim is carried from both sides, per the owner's decision of 2026-09-08 recorded in S2756: the
watch behaviour by **S2755** (text cut off at large font), the listing text by **S2756** (the store
description now matches what the watch build actually implements).

`App updates with these issues will be rejected`:

- `App must target Android 16 (API level 36) or higher` - `Enforced Aug 31, 2026`, warning sent
  2026-07-21. This has stopped being a warning: it is now a state in which updates are rejected.
  `app_v2/build.gradle.kts` already sets `targetSdk = 36`, so the requirement is met by the code and
  broken by what is still sitting on a track.

**Absent as of this transcription: `All Files Access`.** The `Not a core feature` row behind the
rejections of 2026-08-23 and 2026-08-24 is gone. The build that removed `MANAGE_EXTERNAL_STORAGE`
from the store flavors and `ACCESS_FINE_LOCATION` from the watch (S2012, S2013), plus the accepted
appeal, closed it. Recorded because an absence measured once is worth more than the same question
researched again.

**Transcribed:** 2026-09-25, from the owner's Play verdict letter of 2026-09-24, versionCode
`260924019`.

`Update rejected` - one row, dated 2026-09-24, Wear:

| Row | Claim | Carrier |
|-----|-------|---------|
| 1 | `Play listing description`: the store description names no tile or complication, although the watch build ships a tile | S3529 |

**Transcribed:** 2026-09-26, from the owner's Play verdict letter of 2026-09-25 (received 10:31 -0700,
quoted in `PLAN/S3555_hotfix-wear-stopwatch-ongoing-activity/research/01__why-the-ongoing-activity-never-appeared.md`),
Routing ID `ZLFS`, versionCode `260924236`.

`Update rejected` - one row, dated 2026-09-25, Wear:

| Row | Claim | Carrier |
|-----|-------|---------|
| 1 | `Issue found: Missing ongoing activity` / "When a user has an ongoing activity, your app did not do one or more of the following: Show the ongoing activity indicator on the watch face. Update recent apps with the appropriate app launcher chip for the ongoing activity. Reference the ongoing activity from the tile if the tile is present in the tile carousel." | S3555 |

S3529 carried this same claim beside the listing one and was closed on unit tests alone; this review
refuted its ongoing-activity half. The store build could not hold the notification permission, so the
indicator never existed on a Wear OS 4+ watch. The letter repeats neither the listing claim of
2026-09-24 nor the large-font claim of 2026-09-11. Both artifacts of that submission, phone
`260924231` and watch `260924236` (`2.60.9242.311`), still read `completed` in block 2 - a rejected
release keeps that status, as block 2 explains.

**Transcribed:** 2026-10-18, from the owner's Play verdict letter of 2026-10-18 (06:04 -0700),
Routing ID `ZLFS`. **A different app:** the watch face `com.sza.fastmediasorter.watchface`, versionCode
`261014176` (`2.61.0141.706`, `wear:production`), not the phone and watch app this file otherwise
records.

`Rejected` - one row, dated 2026-10-18, Wear:

| Row | Claim | Carrier |
|-----|-------|---------|
| 1 | `Issue found: Play listing icon` / "Your app icon don't accurately represent the watch face." - area `In-app experience`, screenshot of the launcher icon (the brand waves on a dark dial, no time) | no ticket - icon re-captured from the face 2026-10-18, `play/watchface/README.md` |

The rejected bundle was built 2026-10-14, before the icon redraw of 2026-10-15; that redraw was still
a drawing (thick bright waves the face does not draw), so it was replaced by a capture of the real face
in the launcher icon, the picker preview, the store icon and the icon tile of the feature graphic.

Republished 2026-10-18: bundle `261018159` (`2.61.0181.546`) on the face's `wear:production` and the
three-locale listing committed, both **held** - Play refuses automatic review while the rejection
stands, so nothing is in review until the owner sends it from `Publishing overview`. The face's
`wear:internal` still holds the 2026-09-30 draft `260930119`, which carries the original icon.

**Transcribed:** 2026-11-20, read by the agent from the `Policy status` page itself (S2294), not from a
letter. This is the first reading of the page since 2026-08-31.

- Banner `Update in review`: an update is submitted and the page is refreshed when the review ends.
- `Policy issues` holds one group, `App updates with these issues will be rejected`, with one row:
  `App must target Android 16 (API level 36) or higher` - `Enforced Aug 31, 2026`, `Warning sent Jul 21, 2026`.
- No `Update rejected` group and no Wear row: the August rows and the letters recorded above are no
  longer listed as standing claims.
- `Issue details` of that row, verbatim in substance: "Your highest non-compliant target API level is
  Android 15 (API level 35)". How to fix: target API 36 and "Publish a new version of your app to
  production"; Play then notifies that the app is no longer affected.
- The page names no track and no versionCode. Of all live track releases only `internal` `260622232`
  (2026-06-22, before S1149) can carry API 35, and the `Internal testing` page reads it as
  `Inactive - Latest release: 260622232 (2.60.6222.324)`, `Available to internal testers`, with only
  `Manage release` and `Promote release` offered - no remove control for a completed release.

**Re-read 2026-11-24 (S2294)**, after `production` `2.61.1121.756` / `261112175` published: the
`Update in review` banner is gone and the same single row is still listed - `App must target Android 16
(API level 36) or higher`, `Enforced Aug 31, 2026`, `Warning sent Jul 21, 2026`. A compliant production
release alone did not clear it. `Internal testing` still holds `260622232` (`2.60.6222.324`), `Available
to internal testers`, with `Pause track` and `Create new release` offered.

### Contacts Permission declaration (S4030)

<!-- s4030:transcribed:contacts-declaration -->

**State:** not filed
**Date:** -
**Extension requested:** -

Allowed states: `not filed`, `filed`, `approved`, `rejected`, `extension requested`. The owner writes
the state and its date here right after submitting the form and after reading each Play verdict; the
text that was submitted, the console path and the rollback are in `store_assets/PLAY_CONTACTS_DECLARATION.md`.
The pre-release gate reads this block, so keep the three field lines above in this form.

### dataSync foreground service declaration (S4271)

**State:** rejected
**Date:** 2026-10-29
**Video:** recorded

The Play commit of release 47 (versionCode 261029163) was refused with HTTP 403 because the
`standard` bundle carried `dataSync`. Since 2026-10-29 `fms.dataSyncFgsStandard=off`: the store build
strips the type, and the standard store build never gains a foreground-service type the previous Play
release did not ship - a capability that needs one stays in `noLegal`. The text that was
submitted, the video steps and the rollback are in `store_assets/PLAY_FGS_DECLARATIONS.md`. Allowed
states are the same as for the Contacts declaration above; the owner updates this block after
reading each Play verdict.

### Location permissions declaration (S4501)

<!-- s4501:transcribed:location-declaration -->

**State:** not filed
**Date:** -
**Deadline:** 2027-01-27 (Google's announcement of 2026-10-29)

Allowed states are the same as for the Contacts declaration above. The text to paste, the evidence by
consumer and the points a reviewer can challenge are in `store_assets/PLAY_PERMISSIONS_DECLARATION.md`,
section `ACCESS_FINE_LOCATION declaration (S4501)`; the walk-through is `store_assets/PLAY_CONSOLE_CHECKLIST.md`
section C8. Google's text does not say whether the declaration waits for targetSdk 37, so the text is ready
ahead of the bump (S4502).

### Local Network declaration (S4502)

<!-- s4502:transcribed:local-network-declaration -->

**State:** not filed
**Date:** -
**Deadline:** 2027-08-25 (Google's announcement of 2026-10-29)

Allowed states are the same as for the Contacts declaration above. The text to paste is in
`store_assets/PLAY_LOCAL_NETWORK_DECLARATION.md`.

**Upload order (S4502).** `app_v2/build.gradle.kts` targets 37 since S4502. The first upload of a build
targeting 37 follows the filing of all three declarations above - Contacts, Location and Local Network.
Until then the release stays off Play tracks, or is rebuilt with `targetSdk = 36`.

### Developer account - trader status (S4501)

<!-- s4501:transcribed:trader-status -->

**State:** not recorded
**Date:** -
**Declared as:** -
**Deadline:** the console banner decides (help page: 2027-02-01; announcement: November 2026)

Allowed states: `not recorded`, `declared trader`, `declared non-trader`. This is an account-level
declaration under EEA and UK law, not an app-level form; the owner writes the state, the choice and the
date here right after declaring. Walk-through: `store_assets/PLAY_CONSOLE_CHECKLIST.md` section C8.

---

## 4. Android vitals - `measured`

How the published builds behave on users' devices, read from the Play Developer Reporting API.
Produced by `pwsh -NoProfile -File scripts/release/watch-play-vitals.ps1` (`.\a.ps1 pv`), which is
the only writer of this block and the only script that reads that API.

- The window is the last 28 days the API has data for, in `America/Los_Angeles` dates - the only
  time zone its daily figures exist in - and the rates are the user-perceived 28-day values Google
  judges bad behaviour by.
- The bands are Google's published thresholds, kept in the `PlayVitals` block of
  `scripts/devtest/prerelease.config.psd1` with the date they were read.
- `insufficient data` is a result, not a failure: too few users for a rate to mean anything.
- A red band files a Draft ticket; the ticket's section 0 carries the evidence.
- A run whose read failed writes nothing, so a date in this block is always a date of real data.

<!-- s2272:measured:vitals:begin -->

**Verdict:** `insufficient-data` - measured 2026-11-25 (UTC), window 2026-10-27..2026-11-23 America/Los_Angeles, source api. Rates as the API returns them, read as fraction (S2917 research 6).

| Finding | Scope | Value | Band | Colour | Distinct users |
|---------|-------|-------|------|--------|----------------|
| `userPerceivedCrashRate28dUserWeighted` | all devices | 0 | 0.0109 | insufficient-data | 10 |
| `userPerceivedAnrRate28dUserWeighted` | all devices | 0 | 0.0047 | insufficient-data | 10 |
| `anonRssAndSwapMemoryUsageP90` | 0 RAM bucket x app state row(s) | - | PlayMemory (not compared) | unit-unconfirmed | - |
| `bitmapMemoryUsageP90` | 0 RAM bucket x app state row(s) | - | PlayMemory (not compared) | unit-unconfirmed | - |

Google anomalies in the window: none.

Top error issues by distinct users (9):
- `CRASH` android.os.Parcel.createExceptionOrNull at `java.lang.IllegalArgumentException` - 4 users, 9 reports, last versionCode 261029214 - [console](https://play.google.com/console/developers/8446656778368889827/app/4976011497161631822/vitals/crashes/80bc1efd6a749a1926821f75f2e881d0/details)
  - `Exception java.lang.RuntimeException:`
  - `at android.app.ActivityThread.handleServiceArgs (ActivityThread.java:4720)`
  - `at android.app.ActivityThread.-$$Nest$mhandleServiceArgs (ActivityThread.java)`
  - `at android.app.ActivityThread$H.handleMessage (ActivityThread.java:2192)`
  - `at android.os.Handler.dispatchMessage (Handler.java:106)`
  - `at android.os.Looper.loopOnce (Looper.java:201)`
  - `at android.os.Looper.loop (Looper.java:288)`
  - `at android.app.ActivityThread.main (ActivityThread.java:8010)`
  - `at java.lang.reflect.Method.invoke`
- `CRASH` Unknown Source - v9k.c at `java.lang.IllegalArgumentException` - 4 users, 101 reports, last versionCode 261020235 - [console](https://play.google.com/console/developers/8446656778368889827/app/4976011497161631822/vitals/crashes/9ffdb283b34782c07a5413220051334c/details)
  - `Exception java.lang.RuntimeException:`
  - `at android.app.ActivityThread.handleServiceArgs (ActivityThread.java:4720)`
  - `at android.app.ActivityThread.-$$Nest$mhandleServiceArgs (ActivityThread.java)`
  - `at android.app.ActivityThread$H.handleMessage (ActivityThread.java:2192)`
  - `at android.os.Handler.dispatchMessage (Handler.java:106)`
  - `at android.os.Looper.loopOnce (Looper.java:201)`
  - `at android.os.Looper.loop (Looper.java:288)`
  - `at android.app.ActivityThread.main (ActivityThread.java:8010)`
  - `at java.lang.reflect.Method.invoke`
- `CRASH` com.android.server.am.ActiveServices.setServiceForegroundInnerLocked at `android.os.RemoteException` - 4 users, 49 reports, last versionCode 261029214 - [console](https://play.google.com/console/developers/8446656778368889827/app/4976011497161631822/vitals/crashes/c833ca20a5b60832aa73b7f3b1773204/details)
  - `Exception java.lang.RuntimeException:`
  - `at android.app.ActivityThread.handleServiceArgs (ActivityThread.java:4720)`
  - `at android.app.ActivityThread.-$$Nest$mhandleServiceArgs (ActivityThread.java)`
  - `at android.app.ActivityThread$H.handleMessage (ActivityThread.java:2192)`
  - `at android.os.Handler.dispatchMessage (Handler.java:106)`
  - `at android.os.Looper.loopOnce (Looper.java:201)`
  - `at android.os.Looper.loop (Looper.java:288)`
  - `at android.app.ActivityThread.main (ActivityThread.java:8010)`
  - `at java.lang.reflect.Method.invoke`
- `CRASH` com.sza.fastmediasorter.core.ui.BaseActivity.getBinding at `java.lang.IllegalStateException` - 1 users, 1 reports, last versionCode 261029214 - [console](https://play.google.com/console/developers/8446656778368889827/app/4976011497161631822/vitals/crashes/24ae8c61985ee51e3a1b5cde78a538c3/details)
  - `Exception java.lang.IllegalStateException: Binding is only valid between onCreate and onDestroy`
  - `at com.sza.fastmediasorter.core.ui.BaseActivity.getBinding (BaseActivity.kt:78)`
  - `at com.sza.fastmediasorter.ui.settings.SettingsActivity.refreshCollapseAllButton (SettingsActivity.kt:721)`
  - `at com.sza.fastmediasorter.ui.settings.SettingsActivity.access$refreshCollapseAllButton (SettingsActivity.kt:54)`
  - `at com.sza.fastmediasorter.ui.settings.SettingsActivity$setupViews$4.onPageSelected$lambda$0 (SettingsActivity.java:286)`
  - `at android.os.Handler.handleCallback (Handler.java:1037)`
  - `at android.os.Handler.dispatchMessage (Handler.java:108)`
  - `at android.os.Looper.loopOnce (Looper.java:304)`
  - `at android.os.Looper.loop (Looper.java:430)`
- `APPLICATION_NOT_RESPONDING` java.util.ArrayList.get at `Input dispatching timed out` - 1 users, 1 reports, last versionCode 261029214 - [console](https://play.google.com/console/developers/8446656778368889827/app/4976011497161631822/vitals/crashes/261fa3053a3de847012a114b34686684/details)
  - `"main" tid=1 Runnable`
  - `at java.util.ArrayList.get (ArrayList.java:434)`
  - `at androidx.recyclerview.widget.AdapterHelper.findPositionOffset (AdapterHelper.java:474)`
  - `at androidx.recyclerview.widget.AdapterHelper.canFindInPreLayout (AdapterHelper.java:422)`
  - `at androidx.recyclerview.widget.AdapterHelper.applyUpdate (AdapterHelper.java:194)`
  - `at androidx.recyclerview.widget.AdapterHelper.preProcess (AdapterHelper.java:105)`
  - `at androidx.recyclerview.widget.RecyclerView.processAdapterUpdatesAndSetAnimationFlags (RecyclerView.java:4278)`
  - `at androidx.recyclerview.widget.RecyclerView.dispatchLayoutStep1 (RecyclerView.java:4545)`
  - `at androidx.recyclerview.widget.RecyclerView.dispatchLayout (RecyclerView.java:4346)`
- `APPLICATION_NOT_RESPONDING` com.sza.fastmediasorter.core.util.GmsAvailabilityProbe.evaluate at `Executing service com.sza.fastmediasorter/androidx.work.impl.background.systemjob.SystemJobService` - 1 users, 1 reports, last versionCode 261029214 - [console](https://play.google.com/console/developers/8446656778368889827/app/4976011497161631822/vitals/crashes/ade2a10382cade9f63b2d5810f0c364b/details)
  - `"main" tid=1 Runnable`
  - `#00  pc 0x00000000003716ed  /apex/com.android.art/lib/libart.so (art::DumpNativeStack(std::__1::basic_ostream<char, std::__1::char_traits<char> >&, int, BacktraceMap*, char const*, art::ArtMethod*, void*, bool)+76)`
  - `#01  pc 0x000000000044745f  /apex/com.android.art/lib/libart.so (art::Thread::DumpStack(std::__1::basic_ostream<char, std::__1::char_traits<char> >&, bool, BacktraceMap*, bool) const+214)`
  - `#02  pc 0x000000000045e89d  /apex/com.android.art/lib/libart.so (art::DumpCheckpoint::Run(art::Thread*)+640)`
  - `#03  pc 0x0000000000447fdb  /apex/com.android.art/lib/libart.so (art::Thread::RunCheckpointFunction()+118)`
  - `#04  pc 0x00000000004d70f7  /apex/com.android.art/lib/libart.so (art::JniMethodFastEndWithReference(_jobject*, unsigned int, art::Thread*)+90)`
  - `at java.lang.Class.getPublicFieldRecursive (Native method)`
  - `at java.lang.Class.getField (Class.java:1633)`
  - `at android.os.Parcel.readParcelableCreator (Parcel.java:3419)`
- `CRASH` com.android.server.am.ActiveServices.setServiceForegroundInnerLocked at `android.os.RemoteException` - 1 users, 40 reports, last versionCode 261020235 - [console](https://play.google.com/console/developers/8446656778368889827/app/4976011497161631822/vitals/crashes/b617f727a74914b7d454d84e7ba86fcd/details)
  - `Exception java.lang.RuntimeException:`
  - `at android.app.ActivityThread.handleServiceArgs (ActivityThread.java:4945)`
  - `at android.app.ActivityThread.-$$Nest$mhandleServiceArgs (ActivityThread.java)`
  - `at android.app.ActivityThread$H.handleMessage (ActivityThread.java:2316)`
  - `at android.os.Handler.dispatchMessage (Handler.java:106)`
  - `at android.os.Looper.loopOnce (Looper.java:211)`
  - `at android.os.Looper.loop (Looper.java:300)`
  - `at android.app.ActivityThread.main (ActivityThread.java:8348)`
  - `at java.lang.reflect.Method.invoke`
- `CRASH` qb/115066909 0a177c1de5a73eab8fa56f9e1b073b2be652f2e6f0716c8331c21d42b37c3368 - com.att.iqi.lib.Metric$$ExternalSyntheticBUOutline0.m at `android.os.RemoteException` - 1 users, 2 reports, last versionCode 261029214 - [console](https://play.google.com/console/developers/8446656778368889827/app/4976011497161631822/vitals/crashes/e588f4ab06f5fc3a43a88e10cf4ab3f6/details)
  - `Exception java.lang.RuntimeException:`
  - `at android.net.Uri$$ExternalSyntheticBUOutline0.m (D8$$SyntheticClass)`
  - `at android.app.ActivityThread.handleServiceArgs (ActivityThread.java:6566)`
  - `at android.app.ActivityThread.-$$Nest$mhandleServiceArgs (ActivityThread.java)`
  - `at android.app.ActivityThread$H.handleMessage (ActivityThread.java:3190)`
  - `at android.os.Handler.dispatchMessageImpl (Handler.java:142)`
  - `at android.os.Handler.dispatchMessage (Handler.java:125)`
  - `at android.os.Looper.loopOnce (Looper.java:321)`
  - `at android.os.Looper.loop (Looper.java:422)`
- `APPLICATION_NOT_RESPONDING` com.sza.fastmediasorter.databinding.ActivityMainBinding.inflate at `Input dispatching timed out (No focused window)` - 1 users, 1 reports, last versionCode 261020235 - [console](https://play.google.com/console/developers/8446656778368889827/app/4976011497161631822/vitals/crashes/f1df801772d8e45cfa6044690ae76918/details)
  - `"main" tid=1 Runnable`
  - `at java.nio.ByteBuffer.get (ByteBuffer.java:602)`
  - `at android.icu.impl.ICUBinary.getBytes (ICUBinary.java:672)`
  - `at android.icu.util.CodePointTrie.fromBinary (CodePointTrie.java:248)`
  - `at android.icu.impl.RBBIDataWrapper.get (RBBIDataWrapper.java:373)`
  - `at android.icu.text.RuleBasedBreakIterator.getInstanceFromCompiledRules (RuleBasedBreakIterator.java:132)`
  - `at android.icu.text.RuleBasedBreakIterator.getInstanceFromCompiledRules (RuleBasedBreakIterator.java:108)`
  - `at android.icu.text.BreakIteratorFactory.createBreakInstance (BreakIteratorFactory.java:165)`
  - `at android.icu.text.BreakIteratorFactory.createBreakIterator (BreakIteratorFactory.java:73)`

<!-- s2272:measured:vitals:end -->

Known fixed, awaiting drain: the top `movePosition` crash (issue 79e19874) is archived S3067 - the
`count <= 0` guard landed 2026-09-14 with a unit test, audit Verified. Only published 260902195
predates the fix, so its reports grow until a build carrying the guard ships. Not a re-capture
candidate: S3277 (2026-09-18) already re-filed it once and was archived as a duplicate.

Known fixed, awaiting drain: the `SharedPreferencesWearSettingsMirrorStore` `STAMP_MAP_TYPE` crash
(issue daf4cfa06fbe2ac60ec520ebecc05e00) is archived S3068 - the anonymous `TypeToken` subclass was
rebuilt as `TypeToken.getParameterized(..)` and the weakened R8 keep rules restored on 2026-09-13,
audit Verified, with the `anonymous-typetoken` gate holding the construct out. Published 260912134
shipped 2026-09-12, one day before the fix, so every build now on Play predates it and the counters
keep climbing on 260902195. Not a re-capture candidate: S3278 (2026-09-18) already re-filed it once
and was archived as a duplicate.

---

## Recovery plan

Ordered. Every step names who can execute it, because most of this cannot be done from the
repository: splitting a review batch, removing a track release and submitting for review have no
scripted path, and nothing here can cancel a submission or dismiss a policy row.

### Step 1 - Split the phone and the watch into separate review batches

**Executor:** owner (Play Console).
**Proof:** the phone artifact is sent for review with no watch artifact in the same batch.

Managed publishing groups everything unsent into one batch. On 2026-08-25 the phone and the watch
travelled together, and the watch verdict of 2026-08-31 took the phone release with it. This is first
because every later step depends on it.

**Where the batch is actually formed, measured 2026-09-01.** While a rejection stands on the app,
`edits().commit()` returns HTTP 400 `Changes cannot be sent for review automatically` and only
`changesNotSentForReview=True` succeeds. Every API edit therefore lands in `Publishing overview` as an
unsent change and rides out in whatever batch the owner sends by hand. ADR-1 is not satisfied by
building the two artifacts separately - it is satisfied by there being nothing from the watch among
the unsent changes at the moment the batch is sent. Read that list before sending, every time.

**Cancelling a release does not empty a track; it rolls it back.** After the cancellation of
2026-09-01, `production` did not go empty - it returned to the 2026-08-24 build, which was itself
rejected. "Release cancelled" and "track clean" are different states, so criterion 2 cannot be checked
by the fact that a cancellation happened.

**Executed - the outcome is measured, the sequence is not (2026-09-10, S2294).** The phone artifact
`260902195` reached `production` and passed review on 2026-09-02; the watch artifact `260909148`
reached `wear:production` on 2026-09-09 as its own submission seven days later. Two artifacts on two
dates with two verdicts is what a split batch looks like from the outside, and it is the whole purpose
of this step, so the step is done. What is **not** recorded is the sequence in `Publishing overview`
that produced the split - that is S2294's open question 1, and only an owner transcription answers it.
Absent that, a future split is repeated from the outcome, not from a written procedure.

**Read from the Console 2026-11-20 (S2294).** Once a batch is in review, each row of `Changes in
review` offers only `View change`; the one control over the batch is `Remove changes`, which
withdraws all of it. So a split can only be made before the batch is sent: under managed publishing,
the watch artifact must be absent from `Changes ready to publish` at the moment of `Send for review`.
On 2026-11-20 the batch in review carries both `Production` `2.61.1121.756` and `Production (Wear OS)`
`2.61.1121.733` plus store-listing graphics, so the phone and the watch travel together again in this
submission.

### Step 2 - Clear `App must target Android 16`

**Executor:** owner (Play Console).
**Proof:** the row is absent from `Policy status` on the next transcription into block 3.

Candidate: the stale `internal` release at versionCode `260622232` (`2.60.6222.324`) from 2026-06-22,
still `completed` as of the measurement in block 2. Confirm what the Console attributes the row to
before removing anything, then re-transcribe and check the row is gone. S1991's decision to leave
tracks alone until a verdict arrived is spent - the verdict came, and there is no active review left
to disturb.

**That candidate is now the only one there can be, measured 2026-09-01 (S2294).** `targetSdk = 36` is
set in every module of the tree - `app_v2`, `wear`, `watchface` - and no flavor overrides it; `legacy`
moves `minSdk` only. The 35 -> 36 bump landed with S1149, closed 2026-07-22. The versionCode scheme is
`YYMMDDHHm` (`app_v2/build.gradle.kts:288`), which dates every live track release: `internal`
`260622232` is 2026-06-22, a month **before** the bump, while `production` `260824170` (2026-08-24)
and `wear:production` `26082501` (2026-08-25) both postdate it. `beta`, `alpha`, `wear:beta` and
`wear:internal` hold nothing. So the stale `internal` release is the only artifact on any track built
before `targetSdk` reached 36. This is an exhaustive elimination, not a Console attribution - it says
what the row *can* be raised by, and the step still ends at a re-transcription of block 3.

The owner's working invocation, confirmed by a dry run on 2026-09-01 that wrote nothing: the script
read the track, reported the release as `completed` and refused with exit 1. That refusal is final:
the empty-release update removes drafts only, so `-AllowNonDraft` no longer overrides it, and the
attempts below are why.

**Not executed as of 2026-09-10 (S2294).** `internal` still holds `2.60.6222.324` / `260622232`,
`completed` (block 2, measured 2026-09-10). Nothing about the row has changed since 2026-09-01, and
`Policy status` has not been re-read since 2026-08-31, so whether the Android 16 row is still listed
is unmeasured in both directions.

Tool: `scripts/release/clear-play-track-release.ps1`, which refuses anything that is not a `draft`.
**A successful commit does not finish this step.** Measured 2026-09-01: the
`wear:internal` **draft** vanished from `tracks().list()` immediately, while the `completed` record on
`internal` survived the same successful commit - the change went into `Publishing overview` as an
unsent change and takes effect only when the owner sends that batch. So do not re-transcribe
`Policy status` straight after the commit and conclude the row is still there; the step ends at
publication, not at the API call.

**Attempted and it did NOT take, 2026-09-12 (S2994).** `clear-play-track-release.ps1 -Track
internal -AllowNonDraft -Confirm` returned exit 0 and reported the one `completed` record removed,
but Play refused the automatic review and the script fell back to `changesNotSentForReview`. **A
held commit only queues a change; it does not alter the state later edits are built from, and here
it did not even produce a row.** The owner's `Publishing overview` of 2026-09-12 carries one group,
`Store listings`, and no `Internal testing` group at all, while `tracks().list()` still reports
`260622232`. So this step is not merely unfinished - the API cannot finish it while a rejection
stands. The June artifact is the last one below `targetSdk 36` and it remains on `internal`.

**Re-run 2026-11-19 (S2294), same result.** The repo venv was rebuilt (`.venv`, with
`google-api-python-client` and `google-auth`) and the same invocation again returned exit 0 with
"1 release record(s) removed" (log: `temp/S2294/clear-internal-20261119.log`). The refresher run right
after still reads `internal` = `2.60.6222.324` / `260622232`, `completed` (block 2, 2026-11-19). The
rejection that forced the held commit is gone by now - `production` carries `261112175` - so the API
route has failed under both conditions. Do not run it a third time; the Console is the only route.

Route left: remove the release from the `internal` track in the Console by hand, or accept that the
carrier of `App must target Android 16` is an internal-testing artifact rather than a served one.

**Console read 2026-11-20 (S2294).** The row is still listed (block 3). Its `Issue details` give the
offending level, API 35, and prescribe publishing a compliant version to `production`, not removing
anything; the `Internal testing` page offers no remove control for the completed `260622232`, only
`Manage release`, `Promote release` and `Pause track`. The `production` release `2.61.1121.756`
(`targetSdk 36`) is in review now. Proof of this step is therefore the next `Policy status` reading
after that review publishes: the row gone closes the step; the row still listed leaves `Pause track`
on `Internal testing` or a new `internal` release from current code - each a submission, so the
owner's go for that exact change.
**Console read 2026-11-24 (S2294).** `2.61.1121.756` is published and the row is still listed, so the
step now waits on the owner's go for `Pause track` on `Internal testing` (or a new `internal` release
from current code) - a one-way track change.
The same held-commit behaviour was measured on `wear:production` the same day (step 2a).

### Step 2a - Take the Wear artifact out of the pending batch

**Executor:** owner (Play Console) plus one API call, done.
**Proof:** `Publishing overview` lists no `Production (Wear OS)` row before the batch is submitted.

Release 37 ships without the watch (owner ruling 2026-09-12, carrier S2996). Two things had to go.

The owner discarded the rejected wear release `260909148` in the Console on 2026-09-12. That did
**not** empty the track: `wear:production` rolled back to `26090503` - the artifact of the
2026-09-08 rejection - and `Publishing overview` carried it as a pending `Start full rollout`. This
is the rollback behaviour the plan assumed and is now measured: discarding a release restores the
previous record rather than clearing the track.

`clear-play-track-release.ps1 -Track wear:production -AllowNonDraft -Confirm` reported exit 0 and
"1 release record(s) removed", again committed with `changesNotSentForReview` - **and it did not
take.** A dry run hours later still printed `would clear 1 release record(s)` with the full
`26090503` record, notes included, and the Console still showed the row. Each run added a change to
the batch instead of removing one; the batch grew 21 -> 25.

**What actually removed it: `Save for later` on the group header, in the Console** (owner,
2026-09-12). That control holds a whole pending group out of the submission, it is the only thing
that removes one, and the batch then read `24 changes` with `1 change saved for later`. Every
earlier note in this repository calling it a column label is wrong - it is a live button, and it is
the answer whenever a pending group must not reach review.

### Step 2b - Take the watch out of the store listing

**Executor:** one API call for the text, done; the images need an owner decision first.
**Proof:** the live listing carries no Wear OS bullet, and `wearScreenshots` are gone from it.

Carrier S2996. The listing is the contract a reviewer reads - two of the three rejections quote it
verbatim ("functionality not working **as described**") - so a release that ships no watch must not
promise one.

**Text, done 2026-09-12.** The `Wear OS` bullet was removed from `full_description.txt` in all
thirteen locales (one line each, the file's only occurrence of the string; sources backed up under
`temp/S2996/`). `assert-play-listing-locales.ps1` passes with 0 violations over 13 declared locales,
the longest description landing at 3721 of the 4000 characters Play allows.
`publish-play-listing.ps1 -Mode validate` cannot run at all under enforcement - Play refuses the
automatic review and `validate()` has no way to hold changes, which the script reports and which is
not a finding about the listing. `-Mode commit` then returned `SUCCESS: edit committed, but HELD -
not sent for review`, so the new text waits in the same unsent batch as the two track clears.

**Images stay, and that is the right answer, not a defeat.**
`clear-play-listing-images.ps1 -ImageType wearScreenshots -Confirm` is refused at commit time:

```
HTTP 403 ... "Cannot opt-in to Android Wear without screenshots."
```

While the app is opted in to the Wear OS form factor, the listing must carry at least one watch
screenshot, so the images cannot be deleted without first opting out under `Advanced settings ->
Form factors -> Wear OS -> Manage`. Play ties the form factor to the SCREENSHOTS, not to the
artifact: clearing `wear:production` was accepted on the same day, deleting four images was not.

**The opt-out is not required for release 37, and the precedent is our own.** The phone-only
submission of 2026-09-02 passed review and is what `production` serves today, and it went out with
the Wear OS form factor opted in and all four wear screenshots on the listing - they were staged
2026-08-24 (S1707) and refreshed 2026-09-05 by the wear release flow, so they were live throughout.
What has been rejected three times is the WATCH submission, never the phone one. Removing the
screenshots was therefore always the optional half of this step; the text was the half that
carries the "as described" argument, and the text is done. Owner ruling 2026-09-12: leave the form
factor alone. The opt-out question returns with the watch in release 38 (S2994 section 5.4), where
it belongs.

What does matter for the batch is the Wear PRODUCTION ROW, not the pictures - an artifact in the
batch is what dragged the phone release down in August (block 4, step 1).

Two operational facts from the same run, both worth keeping. **`publish-play-listing.ps1` re-uploads
every non-empty image folder**, `wearScreenshots` included - the en-US pass uploaded 22 images - so
a clear run before a publish is undone by that publish; the clear must always come after. And a
`503` mid-run leaves nothing behind: the edit is never committed, so the retry is safe.

### Step 3 - Submit the phone release from current code

**Executor:** owner (Play Console).
**Proof:** block 1 reports a store update stamp later than the one recorded there today (a served
version above the recorded one, while the page carries a version), checked with
`read-play-public-serve.ps1 -RequireUpdatedAfter <previous>` (`-RequireVersionAbove <previous>` for a
version) rather than by reading the Console.

An ordinary `standard` campaign, with no watch artifact in the batch.

**Executed and proven, 2026-09-10 (S2294).**
`read-play-public-serve.ps1 -RequireVersionAbove 2.60.8151.948` exits 0: the store serves
`2.60.9021.951`, updated Sep 2, 2026, above the `2.60.8151.948` this record carried on 2026-09-01. The
proof is the reader's exit code, not a Console reading, exactly as the step demands.

### Step 4 - Return the watch on its own submission

**Executor:** repository first, then owner (Play Console).
**Proof:** all three carriers closed, S1984's pre-release walk passed on the reviewed Wear emulator
profiles, then a watch-only
campaign.

Every Wear claim recorded in block 3 with its carrier and that carrier's status, checked in the
catalog on 2026-09-09:

- `Watch shapes` (2026-08-31) - **S2273**, `Broken`. Open.
- `Missing app icon in splash screen` (2026-08-31) - **S2274**, `Archived`. Closed.
- `Functionality not working as described` (2026-08-31, both identical rows - one subject stated
  twice) - **S2275**, `Archived`. Closed.
- `Wear app functionality not working as described` (2026-09-08, versionCode `26090503`) -
  **S2755**, `Verified`, and **S2756**, `Verified`. Closed.

No watch submission is attempted before every open carrier above is closed and the pre-release walk
of **S1984** has run on device: a rejection costs a full review cycle. Open as of this check: none.

**Second submission sent, 2026-09-09 - `260909148` / `2.60.9021.951`.** All three 2026-09-08 carriers
were closed first, and release package 36 was closed on the artifact that reached the track (owner
instruction, `PLAN/RELEASE_QUEUE_DONE.md`). The review verdict is not in yet, so this submission is
recorded here and nowhere else - a rejection of it lands as fresh tickets in package 38, never as a
third reopening of 36.

**Third submission rejected, 2026-09-11 - `260909148` / `2.60.9021.951` (S2994).**
Email received 2026-09-11 12:17 -0700, Routing ID `ZLFS`, App Status: Rejected.
Specific issues found:
1. "Your app does not provide text without cut off in default font size as shown/described on the store listing."
2. "texts are cut off when a large font size is selected in your app."

**Wear Quality Guidelines mapping & review fleet findings (S2994):**
- **WO-V1 (User configured font size):** App must conform to font size set by user in system settings without text/control clipping or overlapping.
- **WO-V16 (Watch shapes):** Content must fit physical display area without text/control clipping.
- **WO-V14 (Font size):** Min font size 12sp (essential) / 10sp (non-essential).
- **WO-G2 (Play listing description):** List main features; mention tile/complication if surface included.
- **WO-G5 (Play listing screenshots):** Accurate Wear OS screenshots, 1:1 aspect ratio, no transparent mask/device frame.
- **WO-P5 (Companion app):** Non-standalone app must connect smoothly with companion.

**Precedents & Geometry Rules (S2994):**
- **Geometry over text metrics (spearo-go precedent):** Page scrolling (`ScalingLazyColumn`) does not substitute item layout geometry. Fix padding via viewport-relative insets (e.g. top 10%, bottom 12%, side 8%) rather than fixed dp padding to avoid pushing top/bottom lines under watch edges or pager dots.
- **Non-defects confirmed:** Ellipsis (`TextOverflow.Ellipsis`) is Google-recommended overflow behaviour; typography already uses `MaterialTheme.typography` tokens; no `fontScale` clamp exists.
- **Testing requirements:** Verification on real Galaxy Watch 7 (`SM-L310`) and shape profiles `small-round` (192dp) / `large-round` (227dp) using `wear-ink-clip.ps1` on every frame under all font sizes (1.0..1.24 & adb scale 1.5).

**Strategy for Release 36 / 37 / 38 (Owner ruling 2026-09-12):**
1. Release 36 Wear artifact failed (`260909148`). Phone artifact `2.60.9021.951` succeeded and is live.
2. Release 37 proceeds as **phone-only** (no Wear OS artifact submitted).
3. Release 38 will deliver the full Wear OS fix package after physical Galaxy Watch 7 glass testing (S2757), store listing bullet adjustment (S2996), `BLOOD_PRESSURE` removal from `standard` (S2995), and re-shooting `wearScreenshots`.

**What was traded to send it, named rather than buried.** The precondition "no `BlockNeedUserTest`
left in the package" was waived by the owner on 2026-09-09; 25 of the 46 rows the package shipped went
out unmeasured on glass, S2757 (the full watch retest) is still In Progress and moved to package 38,
and S2770 - content outside the round glass, the same class as the `Watch shapes` claim - was closed
by code audit with no device measurement. That is the configuration that produced the 2026-09-08
rejection. If this submission is rejected on a shape or functionality claim, read this paragraph
before re-deriving why.

The Release 37 phone plan proceeded independently of the Release 38 Wear fixes. For a new release,
check the shared app's review state and follow the cadence below.

---

## Release batches - simultaneous publication without the phone as hostage (S4083)

The phone (`production`) and watch (`wear:production`) share `com.sza.fastmediasorter`. Their active
review and pending Console changes apply to the entire app. The face,
`com.sza.fastmediasorter.watchface`, has a separate edit and review batch.

Apply `GOOGLE-PLAY` rules 28-31 and the [Play publishing procedure](../scripts/release/README.md#google-play-publishing-procedure).
An authorized joint phone and Wear release uses one edit and one guarded commit. For independent
submissions, schedule the Wear release on its own cadence and wait for any active same-app review
to clear. Check Publishing overview immediately before each commit.

When changes are already held, the owner checks the unsent list and uses **Save for later** on an
artifact group outside the authorized review. Run `pwsh -NoProfile -File scripts/release/assert-play-review-batches.ps1 -Send phone -Hold watch -PhoneCode <vc> -WearCode <vc>`
after the publishing edit ends. A result of 1 means the stated plan shares a batch. The script reads
the track configuration. Confirm the app-wide batch in Publishing overview before each authorized
send, then record the disposition in the review batch log.

---

## Signing keys (GOOGLE-PLAY rules 2-3)

Public certificate fingerprints only; no key material is ever written to this file. One row per console
app. The upload key is the release keystore that signs every bundle and APK this repository builds; the
app signing key is the one Play re-signs with, which only the Console's `App integrity` page shows.

| Console app | Upload key SHA-256 | Source of the upload key | App signing key SHA-256 | Source class |
|-------------|--------------------|--------------------------|-------------------------|--------------|
| `com.sza.fastmediasorter` (phone and watch app) | `6A:A6:EA:72:75:2A:E9:44:09:29:F1:E0:BA:F8:F2:AD:13:CC:21:CE:C4:60:6B:F6:22:9D:69:02:06:C4:35:A6` | `keytool -list -v` of `.secrets/fastmediasorter.keystore`, alias `fastmediasorter`, exit 0 on 2026-11-15; equal to the pin in `scripts/release/expected-signing-fingerprint.txt` (`apksigner` of the release APKs, 2026-05-15) | not read | `transcribed` - the owner copies the SHA-256 from `App integrity` > `App signing` |
| `com.sza.fastmediasorter.watchface` | `6A:A6:EA:72:75:2A:E9:44:09:29:F1:E0:BA:F8:F2:AD:13:CC:21:CE:C4:60:6B:F6:22:9D:69:02:06:C4:35:A6` | `scripts/builders/build-watchface-release.ps1` refuses a bundle whose fingerprint differs from the same pin | not read | `transcribed` - same page of the watch face's own console app |

Whether Google manages the app signing key or the upload key is also the app signing key is read from
the same page and is not recorded here yet; until both app signing rows are filled, rung 1 of
`GOOGLE-PLAY` is not claimed for this product. The release-readiness check "Signing fingerprint
confirmation" (`docs/RELEASE_READINESS_STANDARD.md`) compares a release against this table.

## Review batch log (GOOGLE-PLAY rules 16 and 21)

One row per review batch or verdict, with the fact and where it was read. A cell the file does not hold
is `not recorded`, never a guess. The owner appends a row when a batch is sent and when a verdict
arrives; the verdict letters themselves stay in block 3.

| Date | Console app | Event | Changes in the batch | Routing ID | versionCodes named | Carriers | Source |
|------|-------------|-------|----------------------|------------|--------------------|----------|--------|
| 2026-08-25 | `com.sza.fastmediasorter` | phone and watch releases sent together as one batch | not recorded | not recorded | watch `26082501` | not recorded | block 2, block 3 (verdict of 2026-08-31) |
| 2026-08-31 | `com.sza.fastmediasorter` | verdict: `Update rejected`, four Wear rows | not recorded | not recorded | `26082501` | S2273, S2274, S2275 | block 3 |
| 2026-09-02 to 2026-09-12 | `com.sza.fastmediasorter` | batch held while a rejection stood; a track clear queued as one more change | 21, then 25 | not recorded | not recorded | not recorded | `GOOGLE-PLAY` rule 17 (this product's own measurement) |
| 2026-09-08 | `com.sza.fastmediasorter` | verdict: `Update rejected`, one Wear row | not recorded | `ZLFS` | `26090503` | S2755, S2756 | block 3 |
| 2026-09-24 | `com.sza.fastmediasorter` | verdict: `Update rejected`, listing description | not recorded | not recorded | `260924019` | S3529 | block 3 |
| 2026-09-25 | `com.sza.fastmediasorter` | verdict: `Update rejected`, missing ongoing activity | not recorded | `ZLFS` | `260924236` | S3555 | block 3 |
| 2026-10-02 | `com.sza.fastmediasorter` | verdict: third watch-shape rejection of `wear:production` | not recorded | not recorded | `260926008` | not recorded | "Release batches" section above |
| 2026-10-18 | `com.sza.fastmediasorter.watchface` | verdict: `Rejected`, listing icon | not recorded | `ZLFS` | `261014176` | no ticket | block 3 |
| 2026-10-18 | `com.sza.fastmediasorter.watchface` | republished bundle `261018159` and the three-locale listing, both held | not recorded | not recorded | `261018159` | not recorded | block 3 |

| 2026-11-12 | `com.sza.fastmediasorter` | standard-app submission entered review; still open on 2026-11-23 | not recorded | not recorded | not recorded | S4831 | Play Developer Support reply, case 6-7601000042237, 2026-11-23 |

## Pending review hold (S4831)

- **Fact (Play Developer Support, case 6-7601000042237, 2026-11-23):** the latest submission of
  `com.sza.fastmediasorter`, open since 2026-11-12, is under review and was expedited to the Review Team. No
  owner action is needed.
- **Rule:** while a submission is under review, upload and send nothing for this app. Each new submission
  resets the review turnaround, which is counted from the most recent change.
- **Where the status is read:** Play Console, `Publishing overview` (`Changes in review`) and the app's
  status banner. `read-play-tracks.ps1` cannot see it - the edit-based reader reports track releases, not the
  review queue.
- **Release flow:** `.claude/commands/release.md` distribution step checks this section first; an open hold
  stops any upload until the Console status changes.
- **Closing the hold:** when the Console shows the verdict, append the verdict row to the log above and
  delete this section's open state.

## Open measurement - release notes language code (GOOGLE-PLAY rule 26)

- **Question:** `scripts/release/publish-play-release.py` sends the Ukrainian release notes under `uk-UA`,
  while the console's own code is `uk`. Whether Play stores the API's `uk-UA` as `uk` is unmeasured; for a
  listing it does (S1266), for release notes nobody has read it back.
- **Measure:** after a release that carried notes, run
  `pwsh -NoProfile -File scripts/release/read-play-tracks.ps1`; its `notes=` column lists the language
  codes of each release as Play stores them. It needs `.secrets/play-console-key.json`, which was not on the
  workstation of 2026-11-15.
- **Decision:** `uk` stored -> map the folder `uk-UA` to the code `uk` in `get_release_notes()`; `uk-UA`
  stored, alone or beside `uk` -> record the measurement as a correction proposal to the contract.

## How this file is kept current

`scripts/release/refresh-play-publishing-state.ps1` is the **only** writer of blocks 1 and 2, and
`scripts/release/watch-play-vitals.ps1` the only writer of block 4. Anything hand-written between the
`s2272:measured:*` marker comments is overwritten on the next run - edit the readers or the writers
instead. Block 3 is the owner's and no script touches it; the refresher only reports its age.

Refresh at every release and at every Play verdict:

```powershell
pwsh -NoProfile -File scripts/release/refresh-play-publishing-state.ps1
pwsh -NoProfile -File scripts/release/refresh-play-publishing-state.ps1 -Check   # staleness probe, writes nothing
pwsh -NoProfile -File scripts/release/watch-play-vitals.ps1                      # block 4; files a Draft on a red band
pwsh -NoProfile -File scripts/release/watch-play-vitals.ps1 -Check               # block 4 staleness probe, writes nothing
```
