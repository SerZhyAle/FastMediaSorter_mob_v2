# S1249 - S1229's stereo bands broke the user-initiated portrait guess

**Status:** Archived
**Priority:** 70

## 0. Raw capture

Surfaced 2026-07-28 by S1244, the moment the unit suite stopped truncating and finally reached the `ui.player` package.

```
StereoDetectorUserInitiatedTest > userInitiated tall 1024x2048 with no tokens returns OU FAILED
```

## 1. What broke

`StereoDetector.aggressiveDimensionGuess` serves **two callers with opposite priors**, and S1229 tuned it for only one of them.

- **Passive best-guess** (`config.ambiguityBestGuess`): the user turned on "guess when ambiguous" and is now opening an arbitrary library. A false positive here plays an ordinary film as side-by-side 3D - the bug S1229 fixed.
- **User-initiated** (`userInitiated = true`): the user explicitly tapped the VR/stereo control **on this image**. The function's own KDoc puts it at "95% intent is stereo". A false negative here ignores an explicit instruction.

S1229 narrowed the over-under band to `0.62..1.25`, derived from the arithmetic of packed stereo, and deliberately excluded portrait phone video at 0.5625. That is correct for the passive caller.

`1024x2048` has aspect **0.5**. Under the old `aspect <= 0.7 -> OU` rule an explicit tap on a tall image guessed OU; under the new band it returns MONO. The test asserting the old behaviour has been failing ever since, unseen.

## 2. Neither side is simply wrong

This is not "S1229 was a mistake". Its bands are right for the passive path and the reasoning behind them is sound. It is a shared-helper problem: one function, one set of thresholds, two callers whose acceptable error is in opposite directions.

The likely shape of the fix is to stop sharing the thresholds - keep the strict arithmetic bands for the passive guess, and give the user-initiated path a wider bias that honours the explicit tap. Confirm that reading against `detectForImage` before implementing; do not decide it from this capture.

**What must not happen:** editing the failing assertion to expect MONO. That would encode "an explicit user tap is ignored for tall images" as intended behaviour, which nobody has decided.

## 3. Why it shipped

S1229 was not careless. It added six regression cases, they passed, and the full suite was run. The suite went red for an unrelated assertion and the run never reached `ui.player` at all, because the test worker died of OOM around `data.remote.ftp` (**S1244**). Every individual step looked done.

## Goal

Явный тап по стереоконтролю не должен молча игнорироваться на вытянутых изображениях. Пороги, подобранные под пассивную догадку по произвольной библиотеке, не обязаны управлять случаем, когда пользователь ткнул в конкретный кадр.

## Phase 1 - Split the OU floor, keep everything else shared

- [x] `aggressiveDimensionGuess` takes a `userInitiated` flag and selects the OU lower bound from it; the passive band is untouched.
- [x] `GUESS_OU_AR_MIN_TAP = 0f` - no floor on the tap path, which is what the rule was before S1229 (`aspect <= 0.7 -> OU`, nothing below it).
- [x] The call site in `detectForImage` (342) forwards the flag it already has.
- **Verification:** `check-standard-fast.ps1 -Mode Unit -Tests "*StereoDetector*"` - PASS, `BUILD SUCCESSFUL in 49s`. Read off the JUnit XML rather than the summary line: `StereoDetectorUserInitiatedTest` 7/7, `StereoDetectorTest` 73/73 (10 skipped), `StereoDetectorPhotoSphereTest` 3/3, zero failures and zero errors across all three.

The 73-case `StereoDetectorTest` is the load-bearing half of that result. It covers the passive cascade S1229 tuned, so its staying green is what shows the split did not hand the passive path back its old false positives.

No `ALL_FEATURES` record added. Neither S1229 nor this ticket has one - the guess bands are internal detection tuning that was never recorded as a shipped capability, and this restores intended behaviour rather than adding any.

Reading confirmed against `detectForImage` before implementing, as section 2 required. The function has exactly two callers and they are already distinguishable at the call site: `detectForVideo:305` passes only the passive `ambiguityBestGuess` case, and `detectForImage:341` branches on `userInitiated || config.ambiguityBestGuess` and then *already* treats the two differently in its return handling. The flag was available at the call site; only the thresholds were shared.

**Only the lower OU bound moves.** Three things were deliberately left alone:

- `GUESS_OU_AR_MAX` stays shared at 1.25. It is what keeps an ordinary 4:3 DSLR frame (1.33) MONO, and case 4 of the test asserts exactly that for the user-initiated path.
- `GUESS_SBS_AR_MIN` stays shared at 2.5. Lowering it for the tap path would let a 16:9 image (1.78) become SBS_FULL, which no test asks for and nobody has decided.
- The `GUESS_MIN_WIDTH` floor stays. The failing case (1024x2048) sits exactly on it.

The failing assertion was **not** edited, per section 2's prohibition.

## 4. Related

- **S1229** - introduced the bands; currently `BlockNeedUserTest` for its own device check. Its fix stands; this is the collateral.
- **S1244** - the truncated suite that hid this, fixed the same day.
