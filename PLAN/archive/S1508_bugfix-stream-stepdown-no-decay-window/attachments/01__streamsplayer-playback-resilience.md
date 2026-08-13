# Playback resilience: how StreamsPlayer fights a bad source

**Audience:** maintainers of this repository.
**Scope:** everything the product does to keep a picture on screen when the source, not the machine, is
the problem. It is a map of shipped behaviour with the constants and the file for each rule, so a
complaint about "freezing" can be routed to the layer that owns it.

For the outward-facing version of these findings - written for the FastMediaSorter (Android) developer
against the same stream bank - see [stream-playback-recommendations.md](stream-playback-recommendations.md).
That document argues *what* to do; this one records *what we did*.

---

## 0. The one thing to know first

**Most "freezes" on this stream bank are not one problem, and bigger buffers fix none of them.** Four
distinct failures were measured on real channels, and each has its own layer:

| Symptom | Real cause | Layer that owns it |
|---|---|---|
| Picture stops, audio stops, nothing arrives | source stopped sending | §3 freeze detection → §4 recovery |
| Picture stops but bytes keep arriving | decoder/clock fault | §2 open-time settings |
| Buffer empties every 10-60 s, forever | the chosen rendition is undeliverable | §5 quality ceiling |
| Nothing plays at all, ever | the channel or the network is down | §4 recovery → verdict |

Reconnecting to grow a buffer made things *worse* in every early measurement. The rule that follows from
that runs through the whole design: **decide from what was observed, not from what was declared.**

---

## 1. The measurement layer

Everything below is driven by five observations the player already makes. No layer adds a timer or a poll
of its own; each hangs off signals that were already there.

| Observation | Where it comes from |
|---|---|
| Buffering reached 100 % after playback was live → **stall**; buffering left → **resume** | `PlayerWindow.UpdateBuffering` |
| Displayed pictures and demuxed input bytes, monotonic totals | `IVideoBackend.ReadProgressCounters()` |
| Lost / corrupted picture counters | `IVideoBackend.ReadLossCounters()` |
| Media position, engine playing state | `IVideoBackend` |
| The engine's own error and end-of-stream events | `PlayerWindow` handlers → `PlaybackFailureSignal` |

Sampled on **one** two-second tick (`PlayerWindow.StatsSampleInterval`), which also writes the `STATS`
log line.

Two conventions make these rules testable and are worth preserving:

- **Time is a parameter, never ambient.** Every Core rule takes `now` from a monotonic session stopwatch
  (`PlayerWindow.HealthNow` = `_sessionClock.Elapsed`). A wall-clock change can neither expire a threshold
  early nor hang it forever, and a test can drive ten minutes in ten lines.
- **Null means "no evidence", never "fine".** A backend that reports no counters must not silently disarm
  a watchdog; it falls back to a weaker signal and says so.

Consequence: **every rule in this document lives in `StreamsPlayer.Core` and is unit-tested without a
window, a network, or a media engine.** The App only forwards observations and applies answers.

---

## 2. Open-time settings: avoiding the failure instead of recovering from it

`LibVlcVideoBackend` — instance options, fixed for every stream this backend ever plays:

```
--rtsp-tcp                 RTSP over TCP; UDP loss on public relays was unrecoverable
--clock-jitter=1000        tolerate broken PCR/PTS instead of stalling the clock on them
--avcodec-hw=none          software decode, always
--no-video-title-show --no-osd --no-snapshot-preview
```

Per-media options:

```
:network-caching=<ms> :live-caching=<ms>       15 000 live, 4 000 on a re-open
:rtsp-tcp                                       when the channel is RTSP
:adaptive-maxwidth=<w> :adaptive-maxheight=<h>  the SP-0071 ceiling (§5)
```

Three decisions here are load-bearing and have each already been re-litigated once:

- **`--avcodec-hw=none` is instance-wide, so it overrides `Play`'s per-stream `softwareDecode`
  argument.** Only `FlyleafVideoBackend` actually honours that argument today. Hardware decode caused GPU
  surface starvation, which was the original freeze. Do not "clean this up" without re-measuring.
- **`--clock-jitter=1000`, and `--no-ts-trust-pcr` was tried and reverted** — it removes the clock
  reference entirely and deadlocks the video output at 0 fps.
- **A smaller buffer on re-opens (4 s vs 15 s).** Flapping sources hit `EndReached` every ~20 s; refilling
  15 s of buffer on each one showed the spinner more than the picture.

---

## 3. Freeze detection — SP-0070

`src/StreamsPlayer.Core/PlaybackFreezeDetector.cs`. A stream can stop without the engine reporting
anything at all; nothing below §4 would ever fire.

**The rule is deliberately conservative: a freeze is _both_ signals at once** — nothing new reaches the
screen **and** nothing new arrives from the source, for `FreezeAfter` = **9 s**. Either alone is ordinary:
a rebuffering stream still receives bytes, a briefly-behind decoder still displays pictures.

Two fallbacks matter as much as the rule:

- Until displayed pictures have grown **at least once on this leg**, there is no picture signal to lose
  (audio-only, or the seconds before the first frame). Until then it judges by media time advancing
  `PositionProgressMilliseconds` = 500 ms.
- A backend reporting no counters falls back the same way. `null` is "this engine has no telemetry".

Input bytes are read from the **demuxer**, not the access layer: on HLS and DASH the segments are fetched
below the access layer, so the access-side count stays frozen for an entire healthy session.

Counters are differenced per media open and reset on each (`Reset()`), or the restart would read as either
a freeze or a burst of progress that never happened. A detected freeze reports **once**, so the next tick
does not fire a second recovery on the same event.

Second, cruder watchdog, still in `PlayerWindow`: buffering for **> 15 s** with media position advancing
< 500 ms → `WATCHDOG kind=stuck_buffer`. Different failure (a buffer that never fills, rather than a
picture that stopped), so it is a separate branch.

---

## 4. Recovery — SP-0015, tightened by SP-0041

`src/StreamsPlayer.Core/LivePlaybackRecoveryPolicy.cs` decides; `PlayerWindow.RecoverAsync` executes.
`PlaybackRecoveryClassifier` turns an engine event into a `RecoveryTrigger` first. The budgets and the
backoff schedule are a written contract, not an implementation choice - `docs/specifications/streams.txt`,
Part D (and Part F for backend adaptation). Change the table below only by changing that first.

| Trigger | Budget | Backoff before attempt _n_ |
|---|---|---|
| `BehindLiveWindow` | 3 | _n_ s (1, 2, 3) |
| `Transient` | 4 | 2ⁿ s (2, 4, 8, 16) |
| `Stall` | 3 | 1 s |
| `StreamEnded` | 4 | 1 s |
| `HardFail` | — | no reconnect; straight to the verdict |

Budgets are **per trigger**, so a stream that stalls three times and then ends still has its
stream-ended budget. Sustained live restores the whole budget (`_recovery.NotifyLive()` on first live).
Exhausting a budget hands off to `PlaybackFailureDialog` — the terminal verdict, with Retry.

Before spending the ladder on an unreachable host, SP-0041 establishes *what* is unreachable — the
channel's host or the network itself — via `StreamTransmissionProbe`, so the app does not offer to delete
a channel because the user's own Wi-Fi is down.

The backoff is cancellable and the "Reconnecting" label stays visible through it: ordinary buffering and
reconnection must never look the same to the user.

Audio has its own parallel path in `MainWindow` (`AUDIO RECOVER`), same policy type.

---

## 5. Adaptive quality ceiling — SP-0071

The newest layer and the one with the most field evidence. `StreamQualityLadder`,
`AdaptiveQualityGovernor`, `QualityMemory` in Core; `StreamQualityLadderProbe`, `PlayerWindow.Quality.cs`
in App.

### The problem it solves

The reference channel offers three renditions and throttles per connection. Measured on one machine in one
hour, per 4 s of media:

| Rendition | Fetch time for 4 s of media | Verdict |
|---|---|---|
| 2096k / 1024x576 | 6.1 - 13.5 s | undeliverable |
| 796k / 640x360 | 2.0 - 5.0 s | borderline |
| 446k / 426x240 | 1.8 - 2.6 s | has margin |

The player sat on the top one. The buffer empties regardless of its size: a second of air is consumed per
second and delivered per two or three. **No buffer size fixes an under-delivered rendition** — only a
smaller rendition does.

### Why starvation, not a speed reading

Both cheaper measurements were tried against the evidence and rejected:

- **Delivered bytes vs the rung's declared `BANDWIDTH`** — `BANDWIDTH` is the *peak*, so a perfectly
  healthy rung reads 10-30 % under its own declaration. That is a false downgrade of a working stream.
- **An instantaneous rate sample** — this source bursts: `in_kbps` swung 0 → 2165 between two samples.

The buffer running dry *is* the measurement. It says delivery lost to real-time consumption over that
interval, whatever any number claims.

### The rule

- **Down:** two starvations inside `StarvationWindow` = **120 s** → step down exactly one rung. A
  starvation is a `PLAYBACK STALL` after live, or a caught freeze from §3. Two, not one, so a single
  hiccup never costs quality — which is also what keeps a healthy stream from ever reaching the rule.
- **Up is a trial, not a deduction.** On a lower rung the player is not saturating the link, so nothing it
  can measure proves the higher rung is deliverable now. The probe is a real switch.
- **The wait belongs to the rung, not to the player.** Base `FirstProbeAfter` = **5 min**, doubled once
  per recorded failure *of that rung*, capped at `MaximumProbeWait` = **1 h**. A probe is a success once
  it survives one full starvation window, and success forgives **only its own** rung.
- **A rung's record outlives the window.** `quality-memory.json`, keyed by normalized URL and by rung
  **bandwidth** (a source may re-encode), expires after **7 days**, capped at **200** sources.
- Every change is a re-open, because libvlc fixes adaptive options at media-open time. A downgrade
  triggered by a freeze rides the recovery re-open that was coming anyway and costs nothing extra.

Deliberate limits, all logged rather than silent: HLS `.m3u8` only; every variant must declare both
`BANDWIDTH` and `RESOLUTION`; fewer than two rungs disables the rule; video only.

### What it measured, on the same channel and hour

| | before the rule | 60 s probe base | 5 min base, per-rung memory |
|---|---|---|---|
| session | 656 s | 656 s | 417 s |
| legs | — | 10 | 4 |
| stalls | — | 11 | 4 |
| black screen | — | 108.9 s (16.6 %) | 36.0 s (8.6 %) |

At the working rung the delivered rate sat at a steady 636-966 kbps at 24-28 fps for 290 s with no stall,
while the uncapped top rung swung 234-1131 kbps and then reported **0.0 kbps for sixteen consecutive
seconds**. That contrast is the whole feature.

Full evidence: `PLAN/DONE/SP-0071_adaptive_quality_ceiling/05_validation.md`.

---

## 6. What the user is told

- **The buffer stripe reports signal health by colour** (SP-0045, `SignalHealthMonitor`): green after
  `CleanInterval` = **60 s** undisturbed; any stall, caught freeze, recovery or failure disturbs it; loss
  counters growing by `LossThreshold` = **5** within one sample counts as trouble. The 60 s interval is an
  anti-flicker constraint, not a comfort setting: a stream dipping once a minute must read steadily
  yellow rather than strobe green between dips.
- **"Reconnecting" is shown only for an actual reconnect** — never for ordinary buffering, and never for
  a quality change, which is why `ApplyQualityDecision` deliberately does not go through `RecoverAsync`.
- **The terminal verdict** is `PlaybackFailureDialog`, with Retry and (only when the channel itself is at
  fault) an offer to hide or delete it.
- **Not yet shipped:** a status line over the video during freezes and re-opens — `PLAN/SP-0072_playback_interruption_notice.md`,
  Status Draft. Today a quality re-open is a silent black screen of 3-18 s, which the owner's field report
  described as "экран много раз моргает". Nothing tells the user the player is working on it.

---

## 7. The log is the contract

`%LOCALAPPDATA%\StreamsPlayer\Current.log`, retired to `Session-<yyyyMMdd-HHmm>.log` on launch, last ten
kept. **A quality or freeze complaint is unreadable without it**, which is why each layer logs its
non-application as loudly as its application: "the rule did not apply" and "the rule never ran" must never
look the same in an archive.

| Event | Says |
|---|---|
| `PLAYBACK OPEN` | `reason=` (initial/quality/recover/retry), `cache_ms=`, `engine=`, `ceiling=` |
| `PLAYBACK LIVE` | `ttff_ms=` — the black-screen cost of that leg |
| `PLAYBACK STALL` / `RESUME` | buffer emptied / refilled after live |
| `PLAYBACK WATCHDOG` | `kind=frozen` or `kind=stuck_buffer` |
| `PLAYBACK RECOVER` | `trigger=`, `action=`, `attempt=`, budget, delay |
| `PLAYBACK QUALITY` | `action=ladder\|down\|up\|hold\|memory`, `from=`, `to=`, `ceiling=`, `starvations=`, `memory=` |
| `PLAYBACK CLOSE` / `SESSION` | `legs=`, `reconnects=`, `stalls=`, `outcome=` |
| `STATS` | every 2 s: `in_kbps`, `disp_fps`, `lost_pics`, `corrupted`, `discont` |

**How to read a session in one pass:** sum `ttff_ms` over legs → total black screen; `legs` minus
`reconnects` → how many interruptions were quality changes rather than failures; `memory=` on the ladder
line → whether this session started knowing anything.

Known noise: `direct3d11 | SetThumbNailClip failed: 0x800706f4`, about six lines per open. It is the video
output adjusting the taskbar preview clip. It is not a freeze and not a re-captured thumbnail — `THUMB
TAKEN` appears once per session.

---

## 8. Deliberately not done

| Not done | Why |
|---|---|
| A user-facing quality setting or per-channel quality preference | The user must not have to know what a rendition is. The failure *record* is not a preference. |
| Applying a remembered ceiling at open time | Faster on a permanently bad source, wrong on one that was fixed - and the ladder is not known until the playlist is fetched, which happens after live so the first open is never slowed. Candidate, not shipped. |
| A seamless probe (fetching a segment of the higher rung and timing it) | Needs a second fetcher and steals bandwidth from the very link under test - it can cause the stall it is measuring. |
| A settle window after a leg starts | Looks obviously right, is wrong here: starvation is only reported after buffering reached 100 %, and the top rung emptied a *full* buffer 5-16 s after live. Any window long enough would have disabled the feature on the source that motivated it. |
| Bigger buffers | Measured, repeatedly, as no help. The failures are decode, clock and delivery-rate faults. |
| DASH ladders | Needs an MPD reader; a stream whose ladder cannot be read is left exactly as it is today, and says so (`reason=not_hls`). |
| Automatic background catalog downloads | Product rule, unrelated to playback but frequently proposed alongside it. |

---

## 9. File map

```
Core (platform-neutral, all unit-tested)
  PlaybackFreezeDetector.cs      §3  is this stream frozen
  LivePlaybackRecoveryPolicy.cs  §4  reconnect or give up, and after how long
  PlaybackRecoveryClassifier.cs  §4  engine event -> RecoveryTrigger
  PlaybackFailureSignal.cs           the input record
  SignalHealthMonitor.cs         §6  green / yellow / red
  StreamQualityLadder.cs         §5  HLS master playlist -> rungs
  AdaptiveQualityGovernor.cs     §5  which rung, and when to try higher
  QualityMemory.cs               §5  the record that outlives the window
  QualityMemoryStore.cs          §5  quality-memory.json

App (forwards observations, applies answers, owns all I/O)
  PlayerWindow.xaml.cs           the five observations, StartMedia, RecoverAsync
  PlayerWindow.Health.cs         §6  paints the stripe
  PlayerWindow.Quality.cs        §5  feeds the governor, logs it, re-opens
  StreamQualityLadderProbe.cs    §5  fetches the master playlist (5 s deadline)
  StreamTransmissionProbe.cs     §4  is it the channel or the network
  QualityMemoryFile.cs           §5  the one gate over the memory file
  LibVlcVideoBackend.cs          §2  engine options and the ceiling
  FlyleafVideoBackend.cs         §2  the opt-in engine
```

## 10. Every number in one place

| Constant | Value | Where |
|---|---|---|
| Live buffer | 15 000 ms | `PlayerWindow.LiveCacheMilliseconds` |
| Re-open buffer | 4 000 ms | `PlayerWindow.ReconnectCacheMilliseconds` |
| Observation tick | 2 s | `PlayerWindow.StatsSampleInterval` |
| Clock jitter tolerance | 1000 ms | `LibVlcVideoBackend.ClockJitterMilliseconds` |
| Freeze threshold | 9 s | `PlaybackFreezeDetector.FreezeAfter` |
| Media-time progress | 500 ms | `PlaybackFreezeDetector.PositionProgressMilliseconds` |
| Stuck-buffer threshold | 15 s | `PlayerWindow.StatsTimer_Tick` |
| Recovery budgets | 3 / 4 / 3 / 4 | `LivePlaybackRecoveryPolicy` |
| Health clean interval | 60 s | `SignalHealthMonitor.CleanInterval` |
| Health loss threshold | 5 per sample | `SignalHealthMonitor.LossThreshold` |
| Starvation window | 120 s | `AdaptiveQualityGovernor.StarvationWindow` |
| Starvations before step down | 2 | `AdaptiveQualityGovernor.StarvationsBeforeStepDown` |
| First probe after | 5 min | `AdaptiveQualityGovernor.FirstProbeAfter` |
| Maximum probe wait | 1 h | `AdaptiveQualityGovernor.MaximumProbeWait` |
| Quality memory retention | 7 days | `QualityMemory.Retention` |
| Quality memory cap | 200 sources | `QualityMemory.MaxSources` |
| Ladder fetch deadline | 5 s | `StreamQualityLadderProbe.Deadline` |

## 11. Tickets

| Ticket | Subject | Status as written in its header |
|---|---|---|
| `DONE/SP-0012` | buffered video backend for unreliable live streams | Verified |
| `DONE/SP-0015` | the bounded recovery ladder | Verified |
| `DONE/SP-0026` | selectable media backend | Verified |
| `DONE/SP-0041` | shorter recovery, connectivity-aware verdict | **Tactical** (in `DONE/`, header not updated) |
| `DONE/SP-0045` | the signal-health stripe | **BlockNeedUserTest** (in `DONE/`, header not updated) |
| `DONE/SP-0070` | silent freeze detection | Verified |
| `DONE/SP-0071` | adaptive quality ceiling | **Implemented** (in `DONE/`, not yet audited) |
| `SP-0072` | telling the user during an interruption | Draft, not started |

Three of those headers disagree with the folder they sit in. Status comes from the header, never from the
path — recorded here so the disagreement is visible rather than inherited.
