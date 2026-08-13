# S1599 research 01 - Anatomy of a zero-hit Grep

**Date:** 2026-08-12
**Window:** 2026-08-05 .. 2026-08-12, 965 transcript files, dedup by `tool_use.id`
**Scripts:** `temp/S1599/dig-grep.py`, `temp/S1599/dig-grep2.py`
**Raw output:** `temp/S1599/dig-grep.txt`, `temp/S1599/dig-grep2.txt`

## 0. The captured numbers were produced by a broken instrument

`temp/scratch/week-audit/dig2.py` counts `grep_runs[pattern] += 1` on **every** `Grep`
call, then prints that counter under the heading `top grep patterns` inside the
zero-hit section. The list of "typical zero-hit patterns" carried into S1599 section 0
(`permission`, `screenshotGesture`, `SUPPORT_LAUNCHER`, `launcherEnabled`, ..) is
therefore the list of the week's **most frequent patterns overall**, not of the ones
that missed. Every conclusion drawn from that list is unsupported.

Re-measured with the zero-hit branch recording its own counter:

- `Grep` total **4,297**, zero-hit **651** = **15.2%** (capture said 4,264 / 544 / 12.8%).
- The real top zero-hit patterns are a different set, headed by `Implementation State`
  (7), `Launcher` (7), `BuildConfig\.` (7), `TODO\(phase-0N\)` (19 across six variants).

## 1. Where the misses actually come from

Zero-hit calls by **scope**:

- **611 (93.9%) carried an explicit `path`.**
- 33 (5.1%) `path` + `glob`.
- 7 (1.1%) `glob` only.
- **0 were unscoped.** A repo-wide `Grep` in this corpus never returned nothing.

Zero-hit calls by **pattern kind**:

- regex / alternation **493 (75.7%)**
- member-shaped identifier 50 (7.7%)
- type-shaped identifier 48 (7.4%)
- literal phrase 43 (6.6%)
- document-structure heading 17 (2.6%)

## 2. What the three directions in section 0 are worth

1. **Multi-pattern per call** - already the dominant shape. Three quarters of the
   *failing* calls are alternations; the model is not issuing naive single-token
   series. Refuted as the fix.
2. **Document-structure navigation** - 17 of 651 calls, **2.6%**. Real but marginal.
3. **A missing catalog field** - the class catalog holds `path, class, layer, loc,
   last, status, role`; it has no member index. The recoveries confirm the misses are
   member-level (`fun refresh`, `sendEvent`, `_events`, `.actionKey`), not type-level.
   But `query.ps1` -> `Grep` is 73 pairs of 3,944, so the ceiling is ~1.8%.

## 3. What a zero-hit actually costs

Each zero-hit classified by what the session did next (lookahead of 4 `Grep` calls):

- **430 (66.1%) abandoned** - no related follow-up search at all.
- **176 (27.0%) recovered** by a later `Grep`.
- 45 (6.9%) **absence checks** where zero *is* the expected verdict - probe-tag
  sweeps (`Timber\.d\("S1410:`), banned-API sweeps (`Log\.d\(`, `="#`). These are not
  waste and must be excluded from any success metric.

Shape of the 176 recoveries:

- related pattern, same scope **121 (68.8%)** - the name was guessed wrong.
- related pattern, **wider** scope **42 (23.9%)** - the place was guessed wrong.
- same pattern, different scope 11 (6.2%).
- same pattern, wider scope 2 (1.1%).

## 4. The finding that reframes the ticket

The 430 abandoned calls are the expensive half, and they are expensive for a reason
the capture did not consider. A `Grep` scoped to a path that does not contain the
symbol returns the same "No matches found" as a symbol that does not exist. **The
model cannot tell those two apart, and in 66% of cases it does not ask again** - it
proceeds on the conclusion "this does not exist". That is a correctness failure, not
a token failure, and it is invisible in any turn-count metric.

The widen-recoveries show the mechanism directly:

- `LauncherHomeActivity|CONFIRM_PIN|HOME` under `app_v2/src/main/AndroidManifest.xml`
  -> 0; `LauncherHomeActivity` repo-wide -> hits.
- `READ_PHONE_STATE|ACTIVITY_RECOGNITION` under
  `app_v2/src/main/java/com/sza/fastmediasorter/data/permissions` -> 0;
  `READ_PHONE_STATE` under `app_v2/src` -> hits.
- `accompanist.permissions` under `app_v2` -> 0; `accompanist` repo-wide -> hits.

In each case the pattern was serviceable and the path was one or two directories too
deep. 42 sessions caught it; the rest did not look.

## 5. Which hook shape this needs

Per the verified harness contract:

- `PreToolUse` can rewrite `tool_input`, but cannot know in advance whether a pattern
  will hit, so it cannot decide to widen a path.
- `PostToolUse` **cannot rewrite the tool result**, but **can attach
  `additionalContext`** that the model sees next to that result.

So the only shape that fits is `PostToolUse` on `Grep`, firing only on a zero result
that carried a `path`, re-running the same pattern once at the repository root and
reporting the count and the top files. It cannot mislead by construction: if the
widened run also returns zero, the hook says nothing and the original verdict stands.

Perimeter: ~611 fires per week, one bounded `rg` each. The pre-filter must key on the
`Grep` tool name plus a non-empty `path`, or the hook pays a pwsh start on all 4,297
calls.

## 6. Open

- Should the absence-check class (6.9%) be suppressed from the hook, or is a
  "0 here, 0 repo-wide" confirmation actively useful? Cheap either way.
- The member-index question (direction 3) is a separate, larger ticket if the ~1.8%
  ceiling is judged worth it. Not part of this one.
