# Phase 02 - PlayerReleaseDetector

**Strategic spec:** [`../S1195_lint-strict-but-never-run-72-unbaselined-errors.md`](../S1195_lint-strict-but-never-run-72-unbaselined-errors.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 07
**Steps done:** 4 / 4

---

## Objective

Rebuild `PlayerNotReleased` around **ownership** instead of the substring `Player`. Today all 6 findings are false positives and the rule proves nothing: it flags an `object` with zero properties, a settings-migration class with no player, two DTOs, the `Application` class, and a nested data class holding a `Future`.

CLAUDE.md §13 states the contract as "one owner per `ExoPlayer`". The tractable lint approximation: a class must call `release()` on a player instance **it constructs**. A class that receives a player, wraps one, or merely names one is not an owner and has nothing to release.

---

## Prerequisites

- [x] Phase 01 done - `.\a.ps1 flr` is a working red/green loop.
- [x] `temp/CODE.LOCK` acquired.
- [x] Read `PlayerReleaseDetector.kt` in full (53 LOC) before editing.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `lint-rules/src/main/java/com/sza/fastmediasorter/lint/PlayerReleaseDetector.kt` | Modified | ≤ 170 |
| `lint-rules/src/test/java/com/sza/fastmediasorter/lint/CustomLintRulesTest.kt` | Modified | ≤ 600 |

---

## Three defects to close

1. **Type match is a bare substring** (`PlayerReleaseDetector.kt:16`). `typeName.contains("Player")` matches `PlayerRepository`, `VideoPlayerDependencies`, `DefaultPlayerSettingsManager`, and - because Kotlin light classes expose synthetic `INSTANCE` and `Companion` fields typed with the enclosing class's FQN - any class whose own name contains `Player`. That is how a zero-property `object` and a companion-only migration class get flagged.
2. **`hasReleaseCall` proves nothing** (`PlayerReleaseDetector.kt:21-29`). Any call named `release()` anywhere in the class satisfies it - `wakeLock.release()`, a semaphore, an image reader.
3. **Ownership versus borrowing is not modelled at all**, and nested classes are evaluated in isolation from their owner.

---

## Steps

### Step 02.1 - Capture today's false positives as failing tests

**Files:** `lint-rules/src/test/java/com/sza/fastmediasorter/lint/CustomLintRulesTest.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Before touching the detector, add test cases reproducing each false-positive shape found in the codebase, each asserting `expectClean()`. Write them so they fail now and pass after Step 02.2 - that failure is the evidence the fix is real.
>
> Shapes to cover, one test file each, keeping the existing `.allowMissingSdk()` style:
> - an `object` with no properties whose own name contains `Player` (models `PlayerTextureFrameCapture`);
> - a class with a `private companion object` and no player field, whose own name contains `Player` (models `S0981OpenInPlayerDefaultOff`);
> - a `data class` DTO carrying a player-typed property it does not construct (models `VideoPlayerHostDependencies`);
> - a class holding a `Lazy<SomethingWithPlayerInTheName>` (models `FastMediaSorterApp`);
> - a nested `data class` holding a `ListenableFuture<MediaController>` inside an outer class that does construct and release a controller (models `AudioServiceController`) - assert clean for both.
>
> Do not delete the existing `testPlayerReleaseDetector`; Step 02.3 rewrites it.

**Verification:**

- `.\a.ps1 flr` fails, and the failure names the newly added tests, not the pre-existing ones.
- `Grep` - 5 new `expectClean()` assertions in `CustomLintRulesTest.kt`.

**Status:** `[x]` done

---

### Step 02.2 - Rewrite ownership detection

**Files:** `lint-rules/src/main/java/com/sza/fastmediasorter/lint/PlayerReleaseDetector.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Replace the field-substring scan with construction-site detection.
>
> Define the owned media types by fully-qualified name, in a companion `private val OWNED_PLAYER_TYPES`: `androidx.media3.common.Player`, `androidx.media3.exoplayer.ExoPlayer`, `android.media.MediaPlayer`, `androidx.media3.session.MediaController`. Resolve a candidate type through `PsiType` and its supertypes so a subclass of `ExoPlayer` still matches; never compare raw strings with `contains`.
>
> A class is an **owner** when its body constructs one of those types: an `ExoPlayer.Builder(..).build()` chain, a `MediaPlayer()` constructor call, or a `MediaController.Builder(..).buildAsync()` chain. Nothing else makes a class an owner - a constructor parameter, a `Lazy<..>`, a DTO property, a function parameter and a synthetic `INSTANCE` or `Companion` field are all borrowing.
>
> When the class is an owner, require a `release()` call **whose receiver resolves to the constructed instance** (the property or local it was assigned to). Drop the current "any method named release" walk. Keep the report on the constructing element, not on the `UClass`, so the location stops landing on the class KDoc.
>
> Skip synthetic fields explicitly - guard on `field.sourcePsi != null` or the equivalent light-element check - so `INSTANCE` and `Companion` can never contribute.
>
> Update `ISSUE.explanation` to state the ownership contract in the same words as CLAUDE.md §13, since the message is what a developer reads when deciding whether to believe the rule.

**Verification:**

- `.\a.ps1 flr` green - every test from Step 02.1 passes.
- `Grep` - `contains("Player")` no longer present in `PlayerReleaseDetector.kt`.
- `Grep` - `androidx.media3.exoplayer.ExoPlayer` present as a literal FQN constant.

**Status:** `[x]` done

---

### Step 02.3 - Prove the rule still catches a real leak

**Files:** `lint-rules/src/test/java/com/sza/fastmediasorter/lint/CustomLintRulesTest.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Rewrite `testPlayerReleaseDetector` so the positive case is an actual owner: a class that builds an `ExoPlayer` via `ExoPlayer.Builder(context).build()`, assigns it to a property, and never calls `release()` - assert exactly one `PlayerNotReleased` error on the construction site. Add a companion negative: the same class with a `release()` call on that property in `onDestroy` - assert `expectClean()`. Add a third: a class that calls `wakeLock.release()` but never releases the player it built - assert the error still fires, closing defect 2.

**Verification:**

- `.\a.ps1 flr` green.
- `Grep` - `CustomLintRulesTest.kt` contains both an `expect(..PlayerNotReleased..)` and an `expectClean()` for the owner shape.

**Status:** `[x]` done

---

### Step 02.4 - Re-measure against the real codebase

**Files:** none - measurement only
**Depends on:** Step 02.3

**Prompt for developer:**

> Run a full `:app_v2:lintStandardDebug` under `temp/BUILD.LOCK`, redirect output to `temp/S1195/phase02-lint.log`, and parse `app_v2/build/reports/lint-results.xml` for `PlayerNotReleased`. Expected: 0 live findings. For any finding that survives, read the class and record in this file whether it is a true positive to fix or a residual false positive to close in a follow-up step - do not baseline it here. Also record the new `LintBaselineFixed` count for `PlayerNotReleased`, which Phase 07 will consume.

**Verification:**

- `expected: 0 live PlayerNotReleased | actual: <N>` recorded in this file with the log path.
- Any surviving finding is classified in writing before the phase closes.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] `.\a.ps1 flr` green, cited.
- [x] `PlayerNotReleased` live findings measured, and each survivor classified.
- [x] No `@Suppress` added to any method that already carries a baselined detekt finding (S0826 signature-shift trap).
- [x] `pwsh -NoProfile -File scripts/post-change.ps1 -ChangeType Kotlin -ScopeToFile ..` closure run.
- [x] Dev log entry added.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Red-then-green is on the record, which is the point of ADR-1:

- Against the OLD detector the 6 new cases failed: `expected: 6 failing | actual: 6 failing` of 8 (`temp/S1195/phase02-red.log`). The two that passed were the owner cases the old rule happened to get right.
- Against the NEW detector: `expected: 8/8 | actual: 8/8` (`temp/S1195/phase02-green.log`).

The rewrite keys on **ownership by construction**, not on field types at all, which is why all three plan-listed defects close at once:

- The substring match is gone - `OWNED_PLAYER_TYPES` holds four literal FQNs and resolution walks supertypes.
- `hasReleaseCall` is replaced by receiver matching, so the `wakeLock.release()` decoy no longer silences the rule (its own test case).
- Nested-class scoping needs no special handling: a nested DTO constructs nothing, so it is clean by construction rather than by exception.

One correction to the plan's Step 02.2: it asks to "skip synthetic fields explicitly, guard on `field.sourcePsi != null`". The rewritten detector never iterates `node.fields`, so synthetic `INSTANCE` / `Companion` fields cannot contribute by construction and no guard exists to write. The synthetic-field guard IS present in `UiContextLeakDetector`, which does still walk fields.

### Step 02.4 measurement

`expected: 0 live PlayerNotReleased | actual: 6`. Log `temp/S1195/phase04-lint-final.log`. **None of the original 6 findings remain** - the count coincidence is misleading, it is an entirely different set of files.

Measuring against real code did what the unit tests could not: it exposed two false-positive patterns the tests had never modelled. Both were fixed inside this phase and both now have their own test case:

- **A factory returning its own product.** `PlayerSetupHelper.createPlayer(): ExoPlayer` builds a player into a local and returns it; the caller becomes the owner. Closed by `isHandedToCaller`, which checks the enclosing function's return type.
- **Release through a differently-named local.** `BackgroundMusicManager` assigns `musicPlayer` at build time and frees it via a `player` alias, so name matching alone missed it. Closed by also matching a `release()` whose receiver's TYPE resolves to an owned player type - which does not reopen the wakeLock hole, since a WakeLock is not player-typed.

That took live findings from 14 to 6 (intermediate run `temp/S1195/phase04-lint.log`, 21:27).

**All 6 survivors are residual false positives**, classified by reading the class. Two distinct mechanisms, both recorded for Phase 07 rather than fixed here:

- `CloudPlaybackHelper:72`, `FtpPlaybackHelper:107`, `SftpPlaybackHelper:100`, `SmbPlaybackHelper:121`, `StreamPlaybackHelper:95` - all five are **extension functions on `VideoPlayerManager`** that assign its `exoPlayer` property. The owner's `release()` lives in `VideoPlayerManager.kt`, a different file, and the detector runs at `Scope.JAVA_FILE_SCOPE`. The construction is visible, the release is not.
- `NowPlayingViewModel:110` - does release, at line 148, via `controllerFuture?.let { MediaController.releaseFuture(it) }`. Two things defeat the match: the release target is the implicit lambda parameter `it`, and the value released is a `ListenableFuture<MediaController>` rather than a `MediaController`.

Phase 07 has a genuine choice to make here, and it should be made explicitly rather than by baselining: either teach the rule to unwrap `ListenableFuture<T>` and resolve implicit lambda parameters and widen its scope beyond one file, or accept that ownership spanning files is out of reach for a file-scoped detector and baseline these six with this reasoning recorded. **None baselined here.**

The 148 existing baseline entries were written against the substring detector and should be assumed meaningless.

---

## Rollback Plan

Revert the detector and test file together. The detector is not shipped in the APK, so a revert cannot affect runtime behaviour - only what lint reports.
