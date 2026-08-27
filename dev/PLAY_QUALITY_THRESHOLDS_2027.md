# Google Play technical quality thresholds - February 2027 (S2100)

Google Play begins enforcing quality thresholds on three metrics from **February 2027**. Exceeding a
threshold costs Play visibility and restricts publishing, so the exposure is to the delivery channel,
not to the app's behaviour.

This file is the single home for both halves of the evidence: the numbers measured locally on
devices, and the numbers readable only from Play Console after a bundle upload. Both halves belong
together because neither answers the question on its own.

Ticket: S2100. Neighbouring ticket on the DEX metric: S1157.

---

## 1. The threshold grid

Source: <https://support.google.com/googleplay/android-developer/answer/17492799>
Read on: **2026-08-27**

Google states these thresholds will change over time. This section is the only thing that needs
editing when they do; the machine-readable copy lives in the `PlayMemory` block of
`scripts/devtest/prerelease.config.psd1`.

### 1.1 Dynamic memory - anonymous RSS + swap

Private process memory, active and compressed. **Code and assets are not counted.** Judged at the
90th percentile, per device RAM bucket and per process state. Apps table (the games table is more
generous and does not apply here):

| Physical RAM | Foreground | User-perceived services | Background |
|---|---|---|---|
| 4 GB | 2 GB | 1 GB | 1 GB |
| 6 GB | 2.25 GB | 1.25 GB | 1.25 GB |
| 8 GB | 2.25 GB | 1.5 GB | 1.5 GB |
| 12 GB | 3.25 GB | 1.75 GB | 1.75 GB |
| 16 GB | 4.25 GB | 2 GB | 2 GB |

### 1.2 Bitmap memory

- User-perceived services: **200 MB**
- Background: **200 MB**
- Cached: **400 MB**

Play declares **no foreground threshold** - bitmaps in foreground are acceptable by its own
statement. An absent row means "not judged", never "unlimited", and a foreground limit must not be
invented for symmetry.

### 1.3 Optimized DEX code

Minimum **25 %** optimization, shrinking and obfuscation (R8 or equivalent). The rule applies only
to **apps whose DEX exceeds 10 MB** (games: 50 MB). See the verdict in section 4.

---

## 2. Local measurements

Taken with `scripts/devtest/prerelease-measure.ps1`, checkpoints `play-anon-memory` and
`play-bitmap-memory`. Every row carries the process state the sample was actually attributed to,
read from `oom_score_adj` - not the state the operator believed the app was in.

Reproduce a row with:

```powershell
pwsh -NoProfile -File scripts/devtest/prerelease-measure.ps1 -DeviceId <serial> -Checkpoint play-anon-memory -Json
pwsh -NoProfile -File scripts/devtest/prerelease-measure.ps1 -DeviceId <serial> -Checkpoint play-bitmap-memory -Json
```

All rows below were taken with the app populated: `BrowseActivity` open on a 251-file local folder,
scrolled several screens so thumbnails had actually decoded. An idle app measures nothing useful.

| Date | Device | RAM / bucket | App tier | State | Anon+swap kB | Limit kB | Verdict | Bitmap upper bound kB | Limit kB | Verdict |
|---|---|---|---|---|---|---|---|---|---|---|
| 2026-08-27 | SM-G996U1 | 7.19 GB / 8 GB | LOW | foreground | 133,376 | 2,359,296 | **pass** (5.7 %) | 51,116 | none declared | not judged |
| 2026-08-27 | SM-G996U1 | 7.19 GB / 8 GB | LOW | perceptible | 157,616 | 1,572,864 | **pass** (10.0 %) | 86,560 | 204,800 | **pass** (42.3 %) |
| 2026-08-27 | SM-G996U1 | 7.19 GB / 8 GB | LOW | background | not reached - see 2.3 | | | | | |
| 2026-08-27 | SM-G996U1 | 7.19 GB / 8 GB | LOW | cached | not reached - see 2.3 | | | | | |

**The bitmap figure is an upper bound, not the bitmap total.** From API 26 bitmap pixels are
allocated in the native heap alongside every other native allocation, and no shell-visible source
separates them. A figure over a threshold therefore does not prove the threshold was exceeded - and
conversely, the 42.3 % above is a ceiling, so the true bitmap share is lower still.

**Note the app tier and the Play bucket disagree, and both are correct.** The device carries 7.19 GB
of RAM, which puts it in Play's 8 GB bucket, but its Java heap is capped at 512 MB, which puts it in
the project's own `LOW` tier. The two classifications answer different questions and neither
substitutes for the other; a row recording only one of them is ambiguous.

Rows for the remaining memory tiers are still outstanding - see section 6.

### 2.3 The process did not reach background or cached

On this device the app never leaves visible state. After `HOME`, and again after launching Settings,
Chrome, Camera, Notes, YouTube and Gallery in sequence, `oom_score_adj` stayed at **100**
(`VISIBLE_APP_ADJ`) and never fell toward `CACHED_APP_MIN_ADJ` (900).

The cause is the app's own Quick Settings tile: `dumpsys activity processes` shows a live
`ConnectionRecord ... AppLaunchPanelTileService ... FGSA`, and a bound tile service holds the
process at visible adj regardless of memory pressure elsewhere.

Two consequences, and they pull in opposite directions:

- **Stricter than expected.** Play will judge this configuration in the perceptible bucket - 1.5 GB
  memory and a 200 MB bitmap limit - rather than in the more generous foreground bucket. Both are
  passed, with the tightest figure at 42.3 % of its limit.
- **The ticket's premise is narrower than it looked.** S2100 was opened on the concern that bitmaps
  *remain resident in background and cached*. On this configuration the process is not in those
  states at all, so that specific exposure does not arise here.

This is one device with the tile bound. A device where the tile was never added should reach cached
normally, so the observation is configuration-dependent and must not be generalised into "the app
never caches". Carried as an open item in section 6.

### 2.1 Image-loader pool sizes

The LRU memory cache is configured explicitly (8 / 16 / 24 MB by tier). The bitmap pool and array
pool are not configured anywhere in this project and keep the library's screen-derived defaults, so
the pool is the only bitmap-holding structure large enough to plausibly approach the 200 MB
background threshold.

| Date | Device | Tier | Configured memory cache MB | Default memory cache MB | Bitmap pool MB | Array pool MB |
|---|---|---|---|---|---|---|
| 2026-08-27 | SM-G996U1 | LOW | 8 | 19 | **9** | 4 |

Read from the `GlideAppModule: defaultSizes` startup line. **The pool is 9 MB.** The suspicion that
motivated this measurement - that an unconfigured, screen-derived bitmap pool might be the structure
large enough to approach the 200 MB background threshold - is refuted on this device. Every
loader-owned bitmap structure together comes to roughly 21 MB, about a tenth of the threshold.

The pool is derived from screen size and from the low-memory flag, so a large, dense, non-low-RAM
screen will produce a bigger number than this. Re-read the line on each tier rather than assuming
9 MB everywhere.

### 2.2 Return-from-background cost

The price side of any decision to release bitmap memory in the background. Measured as the time to
reopen an already-browsed folder after the app returns from background.

| Date | Device | Behaviour | Reopen ms | Method |
|---|---|---|---|---|
| 2026-08-27 | SM-G996U1 | before change | **233** | `ActivityTaskManager: Displayed` marker, warm resume |

The process kept the same pid across the resume, so this is a warm return with the memory cache
intact - which is exactly the behaviour any background-release change would trade away. It is the
baseline an "after" figure is compared against, and it is measured against **unmodified** behaviour.

---

## 3. Play Console figures

Readable only after a bundle upload. No local check substitutes for these. Filled by the release
operator - see section F of `store_assets/PLAY_CONSOLE_CHECKLIST.md`.

| Date | Release | DEX optimization % | OOM kills | Memory percentile alerts | Bad-behaviour alerts fired |
|---|---|---|---|---|---|
| | | | | | |

---

## 4. Verdict - DEX

**The 25 % rule applies to this app.**

The rule is gated on DEX size, and this app is over the gate. Measured 2026-08-27 on
`app_v2/build/outputs/apk/standard/release/FastMediaSorter_standard_v2.60.8241.413.apk`: 3 dex files
totalling **27,711,804 bytes (26.43 MB)**, against a **10 MB** gate.

Reproduce with:

```bash
unzip -l <release-apk> | grep -E "classes.*\.dex" | awk '{s+=$1} END {print s}'
```

The figure is a property of one built artifact and drifts with every release, so re-measure rather
than quoting this number.

What remains open is the **achieved percentage**, which is readable only from the DEX code
optimization insights on a bundle upload. **S1157 owns that question** - it is already in
`BlockExternal` waiting on exactly this measurement, and duplicating it here would give one question
two owners.

R8 is enabled on release (`isMinifyEnabled`, `isShrinkResources`), with
`proguard-android-optimize.txt` plus the project's own rules. Narrowing those keep rules is S1157's
scope, explicitly a non-goal of S2100.

---

## 5. Verdict - split bundles

**All three configuration splits are already active, and no local change is indicated.**

Evidence, gathered 2026-08-27:

- `app_v2/build.gradle.kts` declares no `bundle { }` block, so the ABI, density and language splits
  all keep their defaults, which are enabled. An app bundle has therefore always been sliced per ABI.
- S1972 additionally made the ABI split explicit for APK builds, gated behind `-Pfms.abiSplits`.
- A Play dynamic feature module exists for ML Kit translation.
- Heavy native payloads are delivered on demand through a project-owned mechanism hosted on GitHub
  releases rather than through Play - see `delivery/INVENTORY.md`.

**Residual doubt, and it is not closed by the above.** It is not established whether Play's "limited
split-bundle usage" alert counts only its own feature modules. If it does, the project's own
on-demand delivery scheme is invisible to that alert, and the alert could fire against a delivery
setup that is in fact already optimal. Recording the evidence without this caveat would let a future
reader mistake "our configuration is correct" for "the alert will not fire".

Answerable only by observing whether the alert actually fires. Read it with the section 3 figures
after the next release.

---

## 6. Verdict - memory and bitmaps (2026-08-27)

**No measured figure exceeds any threshold, on any state reached.** The margins are not narrow.

| Metric | State | Measured | Threshold | Share of limit |
|---|---|---|---|---|
| Anonymous RSS + swap | foreground | 133,376 kB | 2,359,296 kB | 5.7 % |
| Anonymous RSS + swap | perceptible | 157,616 kB | 1,572,864 kB | 10.0 % |
| Bitmap (upper bound) | perceptible | 86,560 kB | 204,800 kB | 42.3 % |

The tightest figure is the bitmap one at 42.3 %, and that number is a ceiling that includes every
non-bitmap native allocation in the process, so the real share is lower.

**What this means for the change S2100 was opened to make.** The ticket's premise was that the
memory-pressure handler deliberately preserves image memory in the background and that this would
collide with Play's rule. Two measurements narrow that considerably:

1. The loader's bitmap structures total roughly 21 MB (8 MB configured cache + 9 MB pool + 4 MB
   array pool). Releasing all of them frees about a tenth of the threshold that is already passed.
2. On this configuration the process does not enter background or cached at all (section 2.3), so
   the state the rule targets is not one this device reaches.

There is still a defect to fix, and it is a structural one rather than a memory one: two of the
three background levels in the pressure handler reach a branch that performs no action. That is
worth correcting because a level that silently does nothing is a trap for the next reader, not
because the measurements demand it.

**Open and carried:**

- Remaining memory tiers (`STANDARD`, and a non-low-RAM `HIGH` device where the pool default will be
  larger than the 9 MB measured here) - unmeasured.
- Whether a device without the Quick Settings tile bound reaches cached, and what the figures are
  there - unmeasured, and it is the only path to the 400 MB cached row.
- Both remain owned by S2100.

**Stop.** Per S2100 strategic §9 ADR-1 this report ends the measurement pass and does not proceed to
the handler change. No numeric cache limit is proposed here, deliberately: ADR-2 rules that a
threshold named alongside its own measurement is the same guess with a number attached.
