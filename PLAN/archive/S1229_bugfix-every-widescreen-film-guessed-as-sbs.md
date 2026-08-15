# S1229 - The best-guess stereo pass classified every widescreen film as side-by-side 3D

**Status:** Archived
**Priority:** 80

## 0. Raw capture

Owner, 2026-07-27, Quest 3 session:

> "обчычные фильмм автоопределяенткак SBS стереоэ"

## 1. Problem

With the "best guess on ambiguity" option enabled, ordinary 2D films were classified `SBS_FULL` and played as side-by-side stereo.

From `temp/scratch/vr_session_20260727-2224.log`:

```
Приключения Паддингтона 2.2017.UHD.Blu-Ray.Remux.2160p.mkv -> SBS_FULL  source=ambiguity-best-guess
КИНО — Спокойная ночь Live 2021 [..].webm                  -> SBS_FULL  source=ambiguity-best-guess
Antonio mallorca _ FPOV.mp4                                -> SBS_FULL  source=ambiguity-best-guess
```

## 2. Root cause

`ui/player/StereoDetector.kt`, `aggressiveDimensionGuess` - the last-resort pass that runs when metadata, filename and Matroska tags have all declined:

```kotlin
aspect >= 1.6f && width >= 1024 -> StereoMode.SBS_FULL
```

A standard widescreen frame is 16:9 = **1.78**. Every ordinary film cleared the threshold. This was not a heuristic with a false-positive rate; it was "essentially all video is stereo".

The same file already carries the correct arithmetic 300 lines above, in the conservative detector: `SBS_AR_MIN = 3.2f`, commented "Flat SBS: width / height ≈ 32:9". The aggressive pass contradicted its own file.

The over-under rules were wrong in both directions at once:

- `aspect <= 0.7f -> OU` caught portrait phone video (9:16 = 0.5625), which is not stereo.
- `aspect in 0.9f..1.1f -> OU` **missed the most common real case**: full OU of a 16:9 source is 0.89, just under the band. The window had a hole exactly where the real-world value lives.

## 3. Change

Bands replaced with values derived from the geometry, as named constants with the arithmetic in the comment:

- `GUESS_SBS_AR_MIN = 2.5f` - full SBS is two frames side by side, so twice the source aspect: 2.67 for 4:3, 3.55 for 16:9. The widest ordinary cinema aspect is 2.39, so 2.5 separates them.
- `GUESS_OU_AR_MIN = 0.62f`, `GUESS_OU_AR_MAX = 1.25f` - full OU stacks two frames, so half the source aspect: 0.67 for 4:3, 0.89 for 16:9, 1.20 for 2.39:1. Portrait phone video at 0.5625 falls below the band.
- The two disjoint OU branches collapse into one continuous band, closing the hole.

Only the `ambiguityBestGuess` path changed. The conservative cascade - metadata, filename tokens, Matroska tag, the strict aspect-ratio heuristic - is untouched, so nothing that previously resolved from a real signal changes.

## 4. Test debt

`StereoDetectorTest` covers `detectFromDimensions`, `detectFromFilename`, `detectFromFormat` and the Matroska tags, but has **no case at all** for `ambiguityBestGuess` / `aggressiveDimensionGuess` - which is how a threshold this wrong survived. Regression cases owed:

- 1920x1080 with best-guess ON stays MONO/UNKNOWN, not SBS_FULL.
- 3840x1080 (true full SBS) still resolves SBS_FULL.
- 1920x2160 (true full OU of 16:9) resolves OU - the case the old band missed.
- 1080x1920 (portrait phone) stays MONO.

**Paid, 2026-07-28.** All four, plus two that test the chosen constants rather than the reported symptom:

- 2560x1080 (2.37, the widest ordinary cinema framing) stays undecided - this is the case `GUESS_SBS_AR_MIN = 2.5` was picked to sit above, so it is the one that fails first if anyone lowers the constant.
- 640x720 (0.89, inside the OU band by aspect but too small to be packed stereo) stays undecided, covering `GUESS_MIN_WIDTH`.

Isolating the pass needed a config with every conservative source off (`trustAspectRatio` in particular): with the strict heuristic ON, `detectForVideo` returns at the aspect-ratio branch and the guess never runs. That is recorded in a KDoc on the test helper, because it is the kind of thing that silently turns these six tests into tests of a different code path.

Result: `StereoDetectorTest` 73 tests, 0 failures, 0 errors. The 10 skips are the pre-existing reflective Matroska cases (`assumeTrue` on `Format.Builder.setCustomData`), untouched here.

## 5. Verification

- Build: `.\a.ps1 nd` - BUILD SUCCESSFUL, APK `v2.60.7272.259`, installed on Quest 3.
- Device check owed: open an ordinary 16:9 film with the best-guess option ON and confirm it plays flat; confirm a real SBS file still resolves SBS.

## Last Audit

2026-07-28, static (no device attached this session).

**Stale documentation, fixed here.** The KDoc on `aggressiveDimensionGuess` still spelled out the exact thresholds this ticket removed - "aspect ≥ 1.6 → SBS_FULL", "aspect ≤ 0.7 → OU", "0.9 ≤ aspect ≤ 1.1 → OU". A reader trusting the comment would have concluded the bug was still present. It also claimed the function runs "only when the user has explicitly tapped the VR-toolbar icon", while `detectForImage` and `detectForVideo` both reach it via `config.ambiguityBestGuess` - which is the path the bug was reported on. Rewritten to reference the named constants instead of restating numbers, so the next constant change cannot desynchronise it again.

This was in scope, not a drive-by: it is unfinished work from this ticket's own change, and per the repo's comment discipline an existing comment is a requirement, not decoration.

**Not changed:** the constants, the conservative cascade, and the `detectForImage` best-guess branch. The only behavioural edit in this pass is the added debug probe.

**Still owed:** the device confirmation in section 5. The unit cases prove the classification arithmetic; they cannot prove the player renders the result flat end to end, and the bug was reported from a headset. Status therefore goes to `BlockNeedUserTest` rather than `Verified` - `Timber.d("S1229: best-guess %dx%d -> %s")` in `detectForVideo` reports every guess including the undecided ones, which is exactly the line to look for when opening an ordinary film.

## 6. Related

- **S1217** - the opposite complaint from the same session: a real VR file with no filename token renders flat and cannot be corrected by hand. The two tickets bracket the same weakness - detection rests almost entirely on the filename.
