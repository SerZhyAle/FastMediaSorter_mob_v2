# S1637 - on-device scenario (2026-08-14 19:55)

**Spec:** `PLAN/S1637_activity-logic-debt-player-hosts.md` · Status `BlockNeedUserTest`
**Flavor / variant:** standard debug
**Device:** RFCR110NBQJ (filled from `adb.ps1 props` below)

## What this run has to prove

The ticket is a dependency-injection refactor of the two largest player hosts. Strategic §11 has three criteria; only the third has a device half:

| Criterion | Device-testable | Why |
|---|---|---|
| §11.1 - neither host declares a domain `@Inject` field | out-of-scope | static source property, proven by grep |
| §11.2 - violation counter and gate at zero | out-of-scope | proven by `assert-activity-logic-not-growing.ps1` |
| §11.3 - build green, **behaviour unchanged**, no `@Suppress` | **partial - this run** | the behaviour half cannot be proven by a compile (strategic §7 rates a real-device playback regression medium) |

Strategic §7 names the risk this run exists to retire: moving dependencies could change initialization order in the largest player host and break playback.

## Probes

Three `Timber.d("S1637: ..")` tags, one per supplier introduced or newly consumed:

| Probe text fragment | File | Flow it marks |
|---|---|---|
| `player dialog helper built` | `PlayerManagerInitializer.kt` | in-app player builds `PlayerDialogHelper` with the edit cluster from `ImageEditFactory` |
| `video player manager built` | `PlayerViewerFactory.kt` | in-app player builds `VideoPlayerManager` with playback-position and track-preference from `PlayerHostFactory` |
| `standalone view manager built` | `PhotoVideoStandaloneActivity.kt` | standalone host builds `StandaloneViewManager` through `StandaloneHostFactory` |

A probe that never fires means that flow was not exercised - a coverage gap, not automatically a FAIL.

## Pre-conditions

- Fresh `standard` debug APK installed and its version matching the build.
- At least one resource with playable media registered (the device is the owner's daily driver, so this holds).

## Scenario steps

| # | Goal | Action | Expected |
|---|---|---|---|
| 01 | App starts | launch `MainActivity` via `adb.ps1 launch` | main screen element tree, no crash |
| 02 | Open a resource | tap the first resource row | browse grid appears |
| 03 | Open the in-app player on an image | tap the first image cell | player screen; probes `player dialog helper built` and (for video) `video player manager built` |
| 04 | Edit cluster reachable | open the image edit dialog | rotate / flip / filter / adjust controls present |
| 05 | Draw overlay | open the draw editor | draw toolbar present; the merge use case now arrives by constructor |
| 06 | Video path | play a video file | playback starts; `video player manager built` probe fires |
| 07 | Standalone host | open a photo/video outside the app (share / view intent) | standalone screen; `standalone view manager built` probe fires |

## Device profile

SM-G996U1 (Galaxy S21+), Android 15, SDK 35, 1080x2400 @ density 450, held in landscape throughout.
Installed `FastMediaSorter_standard_debug_v2.60.8112.319-DEBUG.apk`; the main screen footer read the same string, so the running build is the one under test.

## Run log

| # | Action | Result | Evidence |
|---|---|---|---|
| 01 | launch `MainActivity` | PASS | element tree renders, version footer `v2.60.8112.319-DEBUG` |
| 02 | open resource "Всё видео" | PASS | browse grid, `Всё видео (9 files) • virtual://all_video` |
| 03 | open the first video in the in-app player | PASS | player screen `CAP_20260627_213430_2.mp4 (1/9)`; both player probes fire at 19:53:49 |
| 04 | video actually decodes | PASS | `onRenderedFirstFrame`, `Playback ready`, `MEM_PROBE checkpoint=AFTER_STATE_READY scenario=video`; three files played in sequence as slideshow advanced |
| 05 | open an image in the in-app player | PASS | `1778370091004742068579991955147.jpg (1/246)`, image command row present |
| 06 | open the image edit dialog | PASS | dialog "Редактирование изображения" with rotate ↺90/180/↻90, flip ↔/↕, filters grayscale/sepia/negative, adjustments - all five cluster use cases resolved through `ImageEditFactory` |
| 07 | open the draw overlay | PASS | draw toolbar: tool selector, colour swatches, save, save-and-close, settings, cancel - `PlayerDrawingSaveHelper` built with the merge use case by constructor |
| 08 | open the standalone host by external VIEW intent | PASS | `topResumedActivity=..StandaloneImagePlay..`; third probe fires at 19:55:50 |
| 09 | standalone file info | PASS | `FileInfoDialog` from `createFileOperationsHandler`'s sibling `createFileInfoDialog` - name, size, date, type, EXIF resolution / megapixels / format |
| 10 | in-app Copy/Move destination panels | PASS | `copyToPanel` and `moveToPanel` render with the "Загрузки" destination and the folder picker |

**Not executed on purpose:** no edit use case was *applied*. Rotating or filtering would rewrite the owner's real photographs, and the DI question this run exists to answer is whether the dependencies resolve and the surfaces build - which steps 06 and 07 answer. Executing the transform would test the use case, which this ticket did not touch.

**Explicit component start is refused.** `am start -n ..PhotoVideoStandaloneActivity` returns `SecurityException: not exported`; the host is reached through its `StandaloneImagePlayer` alias. Dropping `-n` and keeping `-p com.sza.fastmediasorter.debug` resolves correctly - use that shape next time.

## Log findings

Captured window 19:53:23 - 19:56 (2.66 MB, 18 601 structured lines).

- **All three probes fired**, so every changed flow was exercised:
  - `19:53:49.412` and `19:54:40.547` `D/PlayerManagerInitializer S1637: player dialog helper built - edit cluster from ImageEditFactory` (once for the video entry, once for the image entry)
  - `19:53:49.427` and `19:54:40.550` `D/PlayerViewerFactory S1637: video player manager built - playback position and track preference from PlayerHostFactory`
  - `19:55:50.714` `D/PhotoVideoStandaloneActivity S1637: standalone view manager built by StandaloneHostFactory`
- All three are `D` level, so they are valid probes, not stray instrumentation.
- **Zero** `FATAL EXCEPTION`, zero `MissingBinding`, zero `UninitializedPropertyAccessException`, zero lateinit-access `IllegalStateException`, and zero stack frames in `com.sza.fastmediasorter` - the failure shape a broken `@Inject` rewiring would produce is absent.
- 178 `E` lines in the window are all platform noise: SurfaceFlinger alpha transitions, WindowManager surface teardown, Samsung services (`pageboostd`, `SemDvfsManager`, honeyboard). The one app-tagged line, `Unable to open libpenguin.so`, is a Samsung vendor library probe and predates this ticket.
- App-side `W` lines are `PlayerMediaLoaderManager` playback traces, informational by design.

## Verdict

PASS 10 / FAIL 0 / SKIPPED 0, 0 app errors. Strategic §11.3's behaviour half is met on hardware: both hosts build every collaborator through the new suppliers and behave as before.

## Recommended follow-ups

- None from this run for S1637.
- Unrelated observation, already parked as **S1659**: in landscape all five resource-type tabs render, but "Облако" gets 280px against 438px for its four neighbours - it is the tab being squeezed, which matches the portrait symptom the owner reported.
