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

### 1.4 Which form factors the memory thresholds cover (S2449)

Source: the same requirements page as section 1.
Read on: **2026-09-03**

Google scopes the two memory metrics by form factor, in its own words:

> This requirement only applies to mobile and tablet form factors.

> For Memory usage (Anonymous RSS + Swap) and Bitmap memory usage metric, the mobile and tablets
> form factor is in scope.

**The watch is therefore outside these thresholds entirely.** The question S2100 left open - whether
the `wear` module is judged under the phone's thresholds or under its own - does not arise: there is
no watch row to judge. Two consequences for the release operator:

- Android vitals will show **no Wear breakdown** under dynamic memory or bitmap memory. Its absence
  is the documented scope, not a missing filter or an unpopulated report.
- The premise that the watch rides inside the phone bundle is wrong independently of the scope
  question. `:wear` builds its own bundle and publishes to the `wear:production` track on its own
  cadence (`/skill-release-wear`), sharing only the `applicationId` with `:app_v2`.

Battery is the metric Android vitals does judge per watch model; it is a different metric with
different thresholds and is not covered by this document.

Re-read this subsection whenever section 1's numbers are re-read - Google states the thresholds
change over time, and a scope line goes stale as silently as a number does.

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

All rows below were taken with the app populated: `BrowseActivity` open on a folder of 134 to 251
files, scrolled several screens so thumbnails had actually decoded. An idle app measures nothing
useful. The exact folder differs per pass and each pass names its own below, because file count moves
the numbers.

| Date | Device | RAM / bucket | App tier | State | Anon+swap kB | Limit kB | Verdict | Bitmap upper bound kB | Limit kB | Verdict |
|---|---|---|---|---|---|---|---|---|---|---|
| 2026-08-27 | SM-G996U1 | 7.19 GB / 8 GB | LOW | foreground | 133,376 | 2,359,296 | **pass** (5.7 %) | 51,116 | none declared | not judged |
| 2026-08-27 | SM-G996U1 | 7.19 GB / 8 GB | LOW | perceptible | 157,616 | 1,572,864 | **pass** (10.0 %) | 86,560 | 204,800 | **pass** (42.3 %) |
| 2026-08-27 | SM-G996U1 | 7.19 GB / 8 GB | LOW | background | not reached - see 2.3 | | | | | |
| 2026-08-27 | SM-G996U1 | 7.19 GB / 8 GB | LOW | cached | not reached - see 2.3 | | | | | |
| 2026-09-03 | SM-G996U1 | 7.19 GB / 8 GB | LOW | foreground | 317,528 | 2,359,296 | **pass** (13.5 %) | 93,472 | none declared | not judged |
| 2026-09-03 | SM-G996U1 | 7.19 GB / 8 GB | LOW | background | 258,416 | 1,572,864 | **pass** (16.4 %) | 51,416 | 204,800 | **pass** (25.1 %) |
| 2026-09-03 | SM-G996U1 | 7.19 GB / 8 GB | LOW | cached | not reached - see 2.3 | | | | | |
| 2026-09-03 | Pixel_6 AVD | 3.82 GB / 4 GB | STANDARD | foreground | 271,332 | 2,097,152 | **pass** (12.9 %) | 52,184 | none declared | not judged |
| 2026-09-03 | Pixel_6 AVD | 3.82 GB / 4 GB | STANDARD | background | 234,912 | 1,048,576 | **pass** (22.4 %) | 38,924 | 204,800 | **pass** (19.0 %) |
| 2026-09-03 | Pixel_6 AVD | 3.82 GB / 4 GB | STANDARD | cached | 231,044 | 1,048,576 | **pass** (22.0 %) | 37,300 | 409,600 | **pass** (9.1 %) |
| 2026-09-03 | Pixel_10_Pro_Fold AVD | 7.75 GB / 8 GB | HIGH | foreground | 294,940 | 2,359,296 | **pass** (12.5 %) | 69,184 | none declared | not judged |
| 2026-09-03 | Pixel_10_Pro_Fold AVD | 7.75 GB / 8 GB | HIGH | background | 254,276 | 1,572,864 | **pass** (16.2 %) | 42,256 | 204,800 | **pass** (20.6 %) |
| 2026-09-03 | Pixel_10_Pro_Fold AVD | 7.75 GB / 8 GB | HIGH | cached | 276,612 | 1,572,864 | **pass** (17.6 %) | 5,840 | 409,600 | **pass** (1.4 %) - see 2.4 |

The three `SM-G996U1` rows dated 2026-09-03 are the **after** set: same device, same script, taken on the debug build that
carries the Phase 04 pressure-handler correction. Two differences from the 2026-08-27 set are
recorded rather than smoothed over, because both move the numbers:

- The folder browsed was `Camera Photos`, 134 files, not the 251-file folder of the first pass.
- The install was fresh, so the app had just completed its onboarding wizard when the foreground
  sample was taken. That is why the foreground anonymous figure is higher than in August while the
  background figure below it is lower - the two rows are not a before/after pair with each other.

**The process reached `background` this time.** In August it never left visible state because the
app's own Quick Settings tile was bound (section 2.3). On the fresh install the tile was never
added, the process fell to `background` after a single `HOME`, and the 200 MB bitmap row became
reachable. Section 2.3's caveat therefore holds as written: the pinning is configuration-dependent,
not a property of the app.

**The bitmap figure is an upper bound, not the bitmap total.** From API 26 bitmap pixels are
allocated in the native heap alongside every other native allocation, and no shell-visible source
separates them. A figure over a threshold therefore does not prove the threshold was exceeded - and
conversely, the 42.3 % above is a ceiling, so the true bitmap share is lower still.

**Note the app tier and the Play bucket disagree, and both are correct.** The device carries 7.19 GB
of RAM, which puts it in Play's 8 GB bucket, but its Java heap is capped at 512 MB, which puts it in
the project's own `LOW` tier. The two classifications answer different questions and neither
substitutes for the other; a row recording only one of them is ambiguous.

**The six AVD rows are S2448, and they complete the tier set.** `STANDARD` and `HIGH` had never been
measured, and the `cached` state - the only one Play judges against the 400 MB bitmap limit - had
never been sampled on any device. Both AVDs were populated the way the phone rows were: the
`All Images` virtual folder at 182 and 189 files respectively, scrolled several screens so thumbnails
decoded, on the same `standard` debug build. How each AVD was picked is section 2.5.

**These are emulator rows and the device column says so.** They sit in the same table because they
were taken by the same script and carry the same process-state attribution, but the bitmap upper
bound is reached differently under a host GPU, so an emulator row does not substitute for a phone row
of the same tier.

Every memory tier now carries rows - see the cross-tier verdict in section 8.

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
| 2026-09-03 | Pixel_6 AVD (1080x2400 @420) | STANDARD | 16 | 18 | **9** | 4 |
| 2026-09-03 | Pixel_10_Pro_Fold AVD (2076x2152 @390) | HIGH | 24 | 34 | **17** | 4 |

**The prediction below is confirmed, and its size is now known.** The pool doubles on the larger
screen - 9 MB to 17 MB - and it tracks screen area rather than the tier name: the `STANDARD` AVD has
the same 1080x2400 panel as the phone and reads the same 9 MB. Even at 17 MB the whole set of
loader-owned bitmap structures comes to 45 MB on `HIGH` (24 configured cache + 17 pool + 4 array
pool), against a 200 MB background threshold - still under a quarter of the limit at its largest
measured size.

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
| 2026-09-03 | SM-G996U1 | after change, folder reopened following a background pass | **108** | `Displayed BrowseActivity` marker |
| 2026-09-03 | SM-G996U1 | after change, folder reopened with no background pass (cache intact) | **95** | `Displayed BrowseActivity` marker |

The process kept the same pid across the resume, so this is a warm return with the memory cache
intact - which is exactly the behaviour any background-release change would trade away. It is the
baseline an "after" figure is compared against, and it is measured against **unmodified** behaviour.

**Read the two 2026-09-03 rows against each other, not against the 233 ms above.** The August figure
timed a warm *activity resume*; on the corrected build the same gesture produced no `Displayed`
marker at all, because the platform brought the existing activity to front without redisplaying it
(`START .. result code=2`). So the after figure was taken by a different action - reopening the
folder from the main screen - and the pair below it was taken by that same action with the cache
left intact, which is the only honest control for it.

**The measured cost of releasing bitmap memory in the background is +13 ms (95 -> 108, +13.7 %).**
That is the whole price side of the owner's decision, and it is one gesture on one device.

### 2.4 The cached row, and why its bitmap figure is the least trustworthy of the set (S2448)

The `cached` state was reached on both AVDs by sending the app HOME and then launching five or six
other apps until `oom_score_adj` read 930. Neither AVD has the app's Quick Settings tile bound, which
is exactly the configuration section 2.3 names as the missing one - so the state the owner's phone
never enters is reachable, and the 400 MB bitmap limit is now measured rather than assumed.

**On the `HIGH` AVD the process was largely swapped out by the time it was sampled**, and the two
metrics disagree about it in opposite directions:

- Anonymous memory is unaffected as a verdict. Play counts anonymous RSS **plus swap**, and the
  script reads both: 40,784 kB resident with 235,828 kB swapped, totalling 276,612 kB. Compression
  moves the memory, it does not remove it from the metric.
- The bitmap figure collapses to 5,840 kB, an eighth of the same process's background reading. That
  number is `dumpsys meminfo` native-heap **Rss**, which counts only what is still resident, so on a
  compressed process it is a floor, not the upper bound the rest of this report describes.

So the cached bitmap row proves the threshold is not exceeded - a compressed process cannot be
holding 400 MB resident - but it must not be read as "the app holds 5.8 MB of bitmaps when cached".
Compare cached bitmap figures with background ones only on a process that was not swapped, and read
the swap column of the anonymous record to tell which case a row belongs to.

### 2.5 How to get a device of a given tier (S2448)

The project tier is not chosen in the app; `MemoryTier.classify` derives it, and the **Java heap
limit dominates** - anything at or below 512 MB is `LOW` whatever the physical RAM. Because this app
declares `android:largeHeap="true"`, the figure `Runtime.getRuntime().maxMemory()` returns follows
`dalvik.vm.heapsize`, **not** `dalvik.vm.heapgrowthlimit`. That single fact is what makes the tiers
reachable locally: every stock AVD image measured here reports `heapgrowthlimit=192m` but
`heapsize=576m`, so an AVD lands in `LOW` only if its RAM is too small, never because of its heap.

| Wanted tier | Requirement | AVD that satisfies it as shipped | Observed |
|---|---|---|---|
| `LOW` | heap <= 512 MB or RAM < 3 GB or low-RAM flag | Pixel_9 (2048 MB) | not measured here - the phone rows cover this tier |
| `STANDARD` | RAM 3..6 GB, heap > 512 MB | **Pixel_6** (4096 MB, API 33) | `tier=STANDARD, totalRAM=3.82GB, heapMax=576MB` |
| `HIGH` | RAM >= 6 GB, heap > 512 MB | **Pixel_10_Pro_Fold** (8192 MB, API 37) | `tier=HIGH, totalRAM=7.75GB, heapMax=576MB` |

No AVD had to be created or reconfigured. Confirm the tier from the app's own
`MemoryTier.detect` logcat line before measuring - the line prints all three inputs and the verdict,
and a row signed with a tier computed from `config.ini` is a measurement of something unknown.

Note `hw.ramSize` in `config.ini` is not what the classifier sees: 4096 MB reads as 3.82 GB and
8192 MB as 7.75 GB, because the kernel keeps the difference. A RAM figure aimed at a boundary must
clear it with margin - 6144 MB would report about 5.7 GB and land in `STANDARD`, not `HIGH`.

---

## 3. Play Console figures

Readable only after a bundle upload. No local check substitutes for these. Filled by the release
operator - see section F of `store_assets/PLAY_CONSOLE_CHECKLIST.md`.

| Date | Release | DEX optimization % | OOM kills | Memory percentile alerts | Bad-behaviour alerts fired |
|---|---|---|---|---|---|
| | | | | | |

### 3.1 The split-bundle alert - what this project can and cannot produce (S2449)

Read from the tree on **2026-09-03**. Half of this question is answered by the repository and needs
no upload; only the other half is a console observation, and separating them is the point of this
subsection.

**Answered from the tree - this project declares no feature module.** No module applies the
`com.android.dynamic-feature` plugin; `settings.gradle.kts` carries `:app_v2`, `:wear`,
`:lint-rules`, `:benchmark` and `:watchface`, none of which is one. The single feature module that
ever existed was deleted with S0423 and stays deleted. So Play Feature Delivery's own on-demand
module mechanism is not merely under-used here - it is absent.

**Answered from the tree - the configuration splits are on, by default and not by declaration.**
`app_v2/build.gradle.kts` carries no `bundle { }` block, so `abi`, `language` and `density` splits
keep AGP's enabled-by-default state and the AAB has always been sliced by all three. The
`android.splits` block that file does carry is ignored when building a bundle; it shapes the
direct-download APK channel only.

**Answered from the tree - the one on-demand scheme here is language splits.** `LanguageSplitInstaller`
requests a language through `SplitInstallManager` when the user picks a locale the install does not
carry. That is Play Feature Delivery's configuration-split half, driven at runtime, and it is the
delivery behaviour any alert about this app would have to be talking about.

**Left for the console - whether the alert fires at all.** Google publishes no statement of what
"limited split-bundle usage" counts, so whether it looks only at feature modules or also at
configuration splits cannot be settled from documentation. If it fires, record it in the table above
as an observation and **do not treat it as a defect to fix**: with no feature module to add and the
configuration splits already on, there is no delivery change left that would satisfy it. If it never
fires across a release, that is the answer too, and it closes S2449's residual.

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

**Open and carried:** both items below were closed on 2026-09-03 by S2448 - see section 8 for the
figures and the verdict across every tier.

- ~~Remaining memory tiers (`STANDARD`, and a non-low-RAM `HIGH` device where the pool default will be
  larger than the 9 MB measured here) - unmeasured.~~ Measured; the pool is 17 MB on `HIGH`
  (section 2.1).
- ~~Whether a device without the Quick Settings tile bound reaches cached, and what the figures are
  there - unmeasured, and it is the only path to the 400 MB cached row.~~ Reached on both AVDs at
  `oom_score_adj` 930; the 400 MB row is measured (sections 2 and 2.4).

**Stop.** Per S2100 strategic §9 ADR-1 this report ends the measurement pass and does not proceed to
the handler change. No numeric cache limit is proposed here, deliberately: ADR-2 rules that a
threshold named alongside its own measurement is the same guess with a number attached.

---

## 7. Verdict - after the handler correction (2026-09-03)

The owner's decision on the section 6 report, taken 2026-09-02 through `/spec-quiz`, was to fix the
structural defect only: make every background level invoke the standard release, with no aggressive
purge and no cache resizing. That is what the build measured here carries.

**What the correction changed, observed rather than argued:**

- `TRIM_MEMORY_BACKGROUND` (40) and `TRIM_MEMORY_MODERATE` (60) now reach code. Both were driven with
  `am send-trim-memory` and both logged the handler entry; before the change they arrived at a branch
  whose whole body was guarded by a test for `UI_HIDDEN` and performed nothing.
- Bitmap upper bound fell from 93,472 kB in foreground to 51,416 kB after a single `HOME`, and to
  50,036 kB after the two forced levels above - a release of roughly 42 MB, 45 % of the foreground
  figure.
- The image **disk** cache survived: 165 entries / 2,516 kB before the background pass and 165
  entries / 2,516 kB after it, read from `cache/image_cache` on the device. This is the observation
  strategic §11.7 asks for, and it is a directory listing rather than a reading of the code.

**What it cost:** +13 ms on reopening an already-browsed folder (section 2.2).

**What it did not change:** no threshold was near being exceeded before the fix and none is now. The
tightest figure on the after set is the background bitmap bound at 25.1 % of its 200 MB limit. This
correction is a structural one - a level that silently did nothing is a trap for the next reader -
and describing it as a memory optimisation would overstate it.

---

## 8. Verdict - every tier measured (2026-09-03, S2448)

**Nine rows across three tiers and four process states, and no figure reaches half of any
threshold.** The tier set is complete: `LOW` on the owner's phone, `STANDARD` and `HIGH` on AVDs,
each in foreground, background and - for the two AVDs - cached.

| Metric | Where the tightest figure falls | Measured | Threshold | Share |
|---|---|---|---|---|
| Anonymous RSS + swap | `STANDARD`, background | 234,912 kB | 1,048,576 kB | 22.4 % |
| Bitmap (upper bound) | `LOW` (SM-G996U1), perceptible | 86,560 kB | 204,800 kB | 42.3 % |
| Bitmap (upper bound) | `HIGH`, background | 42,256 kB | 204,800 kB | 20.6 % |
| Bitmap, cached | `STANDARD`, cached | 37,300 kB | 409,600 kB | 9.1 % |

Three things the added tiers changed, none of them the verdict:

1. **The tightest anonymous figure is not on the biggest device, it is on the smallest bucket.** The
   app's footprint barely moves between tiers - 231 to 295 MB across every row - while Play's limit
   scales with the device's RAM, so the share of the limit rises as the device shrinks. The exposure
   to watch is a 4 GB device, not an 8 GB one, and a 4 GB row now exists.
2. **The bitmap pool does grow with the screen** - 9 MB to 17 MB, section 2.1 - which was the open
   suspicion. It grew by 8 MB against a 200 MB threshold, so the growth is real and irrelevant.
3. **The 400 MB cached row exists at last**, at 9.1 % of its limit on the tier where it is readable
   without swap distortion. Section 2.4 records why the `HIGH` cached bitmap figure is a floor rather
   than a ceiling and must not be quoted as the app's cached bitmap total.

**What is still not measured, and it is deliberate.** No 12 GB or 16 GB bucket row exists. Both
buckets carry the most generous limits in Play's table and the app's footprint does not scale with
device RAM, so a row there would be the least informative one obtainable - section 2.5 records the
recipe if a future reader wants it anyway.
