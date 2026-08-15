# S1328 - StreamInlineAudioManager sits exactly on the LongParameterList threshold

**Status:** Archived
**Tactical plan:** `PLAN/S1328_streaminlineaudiomanager-detekt-debt/INDEX.md`
**Priority:** 35

## 0. Raw capture

Not an owner report. Surfaced 2026-07-31 while closing **S1219**, which added one probe line to this file and so pulled it into the diff-scoped detekt gate. The finding predates that edit and is unrelated to it.

Verbatim from the gate:

```
LongParameterList - 10/10 - [StreamInlineAudioManager] at
app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamInlineAudioManager.kt:43:31
```

`config/detekt/detekt.yml` sets `constructorThreshold: 10`, so the class is exactly at the cap - the next dependency anyone adds turns this into a hard gate failure for whoever happens to be touching the file that day.

## 1. Why this is its own ticket

Three conditions from CLAUDE.md 3.1 all hold: unrelated to S1219 (a layout change), not a one-liner (the remedy is a parameter object or a split, in a class with ten collaborators), and it needs its own read of what belongs together.

It is also not in `config/detekt/baseline-app_v2.xml`, which has four other entries for this file but not this one. So it is live debt, not accepted debt - it just never surfaced because the scoped gate only reports findings in files a change actually touches.

**Superseded 2026-08-02** - the paragraph above described the tree on 2026-07-31 and no longer does. See §1.1.

### 1.1 The finding was frozen into the baseline on 2026-08-02, by nobody's decision

`config/detekt/baseline-app_v2.xml` was rewritten at **2026-08-02 15:03:18** - 12286 lines, down from
the 12656 this ticket measured on 2026-07-31 - and no dev-log row records it. Neither
`StreamInlineAudioManager.kt` nor `StreamsActivity.kt` has been edited since 2026-07-31, so the
findings did not move; the baseline did.

Verified against the working tree on 2026-08-02, not inferred:

- `baseline-app_v2.xml:3484` now carries
  `LongParameterList:StreamInlineAudioManager.kt$StreamInlineAudioManager$( lifecycleOwner: .. )` -
  this ticket's finding.
- `baseline-app_v2.xml:11620` now carries
  `TooManyFunctions:StreamsActivity.kt$StreamsActivity : BaseActivity` - S1198's finding, the one
  §2.1 built its whole blocker on.
- `assert-detekt.ps1 -Module app_v2 -Gate -ChangedFiles StreamsActivity.kt` exits **0**,
  `PASS (no new findings; baselines hold)`. The same gate over both files exits **0** as well.

Two consequences, both of which rewrite this ticket's shape:

- **The cross-ticket blocker in §2.1 is gone.** Touching `StreamsActivity.kt` no longer fails the
  gate, so S1328 does not need S1198 to land first and the release order needs no change.
- **The freeze buys nothing.** A `LongParameterList` baseline id embeds the entire parameter list as
  its signature, so the first dependency anyone adds re-keys it and the finding resurfaces for
  whoever happens to touch the file that day - which is exactly the failure mode §0 opened this
  ticket to prevent. The entry is a dead man's switch, not protection.

The regeneration itself - an unlogged wholesale rewrite that absorbed at least two tickets' live debt
- is out of this ticket's scope and parked as **S1356**.

## 2. Shape of the remedy - not yet decided

The ten constructor parameters split roughly into view handles, a playback controller, and a bundle of callbacks back to the Activity. Candidate directions, to be weighed rather than assumed:

- Group the three view handles into one holder, which is the smallest change and buys one slot.
- Group the callbacks into a single listener interface, which buys the most slots but changes how the Activity wires up.
- Leave the count and add the finding to the baseline, accepting it deliberately - the honest option if the class is genuinely cohesive and the split would only move parameters around.

## 2.1 The blocker found while planning, 2026-07-31

The remedy cannot be confined to this file. Both call sites pass **named** arguments bound to the
parameter names, so there is no constructor shape that leaves callers untouched - and one of those
callers is `StreamsActivity`, which carries six live detekt findings of its own, including
`TooManyFunctions 42/40`.

The gate is **per file, not per line**: it flags any non-baselined finding in a changed file. Touching
`StreamsActivity` therefore drags in all six, and those belong to **S1198**.

**Owner decision 2026-07-31: close S1198 first.** S1328 waits on it rather than absorbing its scope,
so the debt is paid once, in the ticket that owns it. Consequence for the plan: S1198 must move ahead
of S1328 in the release order - it currently sits in package 34 while S1328 sits in package 30, which
is backwards for this dependency.

**Void since 2026-08-02, see §1.1.** `TooManyFunctions 42/40` is baselined, the gate passes on
`StreamsActivity.kt`, and S1328 no longer depends on S1198 in either direction. No release reorder.
One technical residue survives and belongs to the tactical plan, not here: the `ImportOrdering`
baseline entry for `StreamsActivity.kt` keys on the full import list, so Phase 02 adding two imports
re-keys it and resurfaces the finding unless Phase 01 fixes the drifted import pair first.

## 3. Related

- **S1219** - the layout ticket whose closure surfaced this; nothing about the panel layout depends on it.
- **S1198**, **S1247**, **S1311**, **S1314** - the existing per-class detekt-debt family this joins.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1198 (`streamsactivity-detekt-debt`, Draft) - **no longer a blocker** since
  2026-08-02, its `TooManyFunctions` finding is baselined and `StreamsActivity.kt` passes the gate
  (§1.1); the two tickets are now independent in both directions. S1219 (`BlockNeedUserTest`) -
  surfaced this, nothing depends on it. S1247, S1311, S1314, S1329, S1334 - the per-class
  detekt-debt family, no file overlap with this one. S1356 (Draft) - the unlogged baseline
  regeneration that froze this finding, spun off from the 2026-08-02 quiz.
- **Ordering decision (owner, 2026-07-31, void since 2026-08-02):** S1198 closes first. Superseded -
  the dependency it protected against no longer exists, so S1328 runs on its own schedule and the
  release order stands as it is.
- **Remedy shape:** two holders - view handles and callbacks - leaving four constructor parameters
  against a threshold of ten. The callback holder is a `data class` of lambdas with no-op defaults,
  not a listener interface: the second call site deliberately omits three of the five callbacks, and
  an interface would force empty overrides there. Precedent for the holder shape is
  `ui/player/VideoPlayerDependencies.kt`.
- **Constraint:** no `@Suppress`, and no baseline edit **other than** deleting the
  `LongParameterList` entry the 2026-08-02 regeneration added for this constructor. After the
  refactor that entry is dead - its signature spells out the old ten-parameter list - and leaving a
  dead entry behind is the exact debt S1334 built `scripts/quality/audit-detekt-baseline-drift.ps1`
  to catch. Prune it the way S1350 and S1351 pruned theirs. Four `Timber.d("Sxxxx: ..")` probes live
  in the target file and belong to tickets still in `BlockNeedUserTest` - all four must survive the
  refactor.
- **Measurement note, re-verified 2026-08-02:** the constructor still declares ten parameters, so the
  `10/10` count holds. Everything about its detekt status does not - see §1.1. Current numbers:
  `baseline-app_v2.xml` is 12286 lines with **4** entries for `StreamInlineAudioManager.kt` (three
  `SpacingBetweenDeclarationsWithComments` plus the new `LongParameterList`) and **12** for
  `StreamsActivity.kt`. The strategic spec's original "four other baseline entries" was wrong when
  written - there were three - and is now accidentally right for the wrong reason.
- **Flavors:** all - the file is in `src/main` and carries no flavor gate.
- **Localization:** no new strings.

### Quiz decisions (2026-08-02)

Both blockers the status note carried were re-researched against the working tree before being put
to the owner, and only one survived as a real question.

- Fate of the ticket now that the finding is baselined rather than live → **fix it anyway, and prune
  the dead baseline entry as part of the fix**. The freeze protects nothing: the entry's signature is
  the ten-parameter list itself, so the next added dependency re-keys it and the finding lands on
  whoever touches the file. The alternatives offered were closing S1328 as won't-fix and deferring it
  to a later release package; both were declined.
- Cross-ticket entanglement with S1198, options (a) land S1198 first / (b) absorb a 3-function
  extraction / (c) won't-fix → **not asked, the premise is void**. `TooManyFunctions` is baselined and
  the gate passes on `StreamsActivity.kt` (§1.1), so none of the three routes is needed.
- Holder-grouping shape, two holders -> 4 parameters vs views-only -> 8 → **not asked, the ticket's
  own goal decides it**. Views-only leaves the constructor at 8 against a threshold of 10, which is
  the same "one dependency away from a gate failure" position §0 opened the ticket to escape. Two
  holders it is, on the `ui/player/VideoPlayerDependencies.kt` precedent.

## 4. Repro record

A refactor rather than a bugfix, so there is no failing observation to reproduce. What has to be shown
instead is that nothing broke on the way through.

**Before, re-measured 2026-08-03 against the live tree**, because a detekt-debt ticket's premise rots:
`config/detekt/baseline-app_v2.xml` still 12286 lines, still carrying
`LongParameterList:StreamInlineAudioManager.kt$..` at line 3484, gate exit 0. The constructor still
declared ten parameters. So §1.1's account held, and the owner's "fix it anyway and prune the dead
entry" decision still applied.

**After, 2026-08-03 on `emulator-5554` (Android 15).** An AUDIO channel played through the reworked
constructor: the probe reported the holder-supplied view id, the mini-control appeared with the station
title, the row gained its `Now playing` label and its green status dot, and the stop button hid the
control and cleared the label. Four of the five callbacks and all three view handles were observed
firing through the holders; `onPlaybackStateChanged` and `onNowPlayingChanged` had no observable in the
window. Zero app-side errors in 9464 log lines. Full run:
`temp/S1328/mobile_test_scenario_20260803_0140.md`.

## Last Audit

**Date:** 2026-08-03
**Mode:** full
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 21 · WARN 0 · FAIL 0 · MANUAL 2 · EXEMPT 1

The contract holds end to end. The constructor declares four parameters against a threshold of ten;
both holders are `data class` with a no-op default on every callback field; both call sites build their
own holders and the second still omits three callbacks; the `Player.Listener`
`onPlaybackStateChanged(playbackState: Int)` override survived the rename while the three two-argument
sites became `callbacks.onPlaybackStateChanged`. All four inherited probes (`S1142` x2, `S1219`,
`S0896`) are intact. Zero `@Suppress` and zero `Log.d(` across the four files. The baseline lost
exactly one line by hand - 12286 -> 12285, four entries for this file down to three - and
`audit-detekt-baseline-drift.ps1` reports no dead entry for it. Phase 01's import reorder holds at 61
imports with the pair in ordinal order. Line budgets: 394 / 38 / 1212 / 98, all inside their phase
budgets and far inside Rule 2. Closure: `a.ps1 fk` exit 0, the gate over all four files exit 0,
`post-change` clean PASS with `listener-symmetry new imbalance 0`, catalog regenerated with both holder
records carrying `role` and `status=new`, `docs/ALL_FEATURES.jsonl` untouched. Device run on
emulator-5554: 8 PASS, 0 FAIL, zero app-side errors in 9464 log lines.

EXEMPT: `docs/FEATURES*.md` - a constructor shape is not a shippable capability.

**Parked during this audit:** `S1373` - `BuildConfig.ENABLE_PERSISTENT_AUDIO_PLAYBACK` is read directly
in `src/main` at two sites this ticket happened to touch, which Rule 14 forbids. Pre-existing, inside
the flavor-flag gate's ratchet, and out of this ticket's scope.

### Manual / on-device

- [ ] The second host - the main-screen streams panel - was never exercised: the panel is not enabled
      on the test AVD. It supplies two of five callbacks and leans on the new no-op defaults.
- [ ] `onNowPlayingChanged` and `onPlaybackStateChanged` ran without an observable in the test window:
      the station sent no ICY metadata, and the resume-point write has no UI. Both are type-checked at
      their call sites, so the risk is coverage, not correctness.

## Revision History

- **2026-08-03** - by `/spec-test-device` (`sdk_gphone64_x86_64`, device: emulator-5554, Android 15)
  - Scenario: `temp/S1328/mobile_test_scenario_20260803_0140.md` · PASS/FAIL/SKIPPED 8/0/2 (+2 inconclusive) · Errors in log: 0
