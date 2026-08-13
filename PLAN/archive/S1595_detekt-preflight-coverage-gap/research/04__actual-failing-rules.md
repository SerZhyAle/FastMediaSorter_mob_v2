# 04 - Which rules actually fail the gate

**Strategic §6 item:** 4
**Date:** 2026-08-12
**Verdict:** The shipped three rules cover 13.9% of failures outright, and the three additions
named in the capture are the wrong three.

## Method

Source: session transcripts, window 2026-07-20 .. 2026-08-13, 312 files. Deduped by record
`uuid` plus occurrence index - `requestId` is absent on these records, because gate verdicts
arrive inside tool-result records rather than assistant messages.

Two extraction traps, worth not re-discovering: each tool result is stored twice (once under the
message content, once under the result payload), so naive extraction doubles every count; and the
transcripts are CRLF, so an unanchored multiline regex silently fails against the trailing `\r`.

## Attribution ceiling

210 distinct `[detekt-gate] FAIL` verdicts. The gate only began printing `file:line:col - RuleId -
message` on 2026-08-01, so attribution splits sharply by month:

- July: 55 verdicts, **0** named a rule.
- August: 155 verdicts, **104** named a rule (67%).

The honest forward-looking denominator is the August pair, not the 210 total. 95 verdicts could
not be attributed; about 21 of those are infrastructure failures - build-lock refusals and a lock
queue crash - that no preflight can ever prevent.

## Ranking

Distinct failures attributable to each rule, inline evidence only:

- `ImportOrdering` 25 - spread over 27 different files, no hotspot
- `ReturnCount` 22
- `LargeClass` 16 - 12 of them one file
- `ArgumentListWrapping` 15
- `LongMethod` 12
- `LongParameterList` 10
- `MagicNumber` 9
- `CyclomaticComplexMethod` 9
- `TooGenericExceptionCaught` 8
- `MaxLineLength` 8 / `MaximumLineLength` 8 - the same eight long lines counted by two rules
- `SpacingBetweenDeclarationsWithComments` 7, `TooManyFunctions` 7, `Wrapping` 5, then a tail

Never caused a failure: `ComplexMethod`, `NestedBlockDepth`, `SwallowedException`,
`SpreadOperator`, `UnusedPrivateMember`, `NoWildcardImports`, `EmptyFunctionBlock`.

## The two numbers that decide the design

- The shipped preflight's three rules fire on **41 of 115** attributed failures (35.7%), but only
  **16 (13.9%)** have all their findings inside that set. In the other 86% the expensive
  round-trip still happens, so the current step saves diagnosis, not time.
- Greedy set cover over the same corpus: nine hand-listed rules reach only **48.1%** of all
  verdicts. Enumerating rules by hand does not converge.

## Against the capture

The capture proposes `LargeClass`, `LongMethod`, `ComplexMethod`. Measured:

- `ComplexMethod` caused **zero** failures - it is superseded by `CyclomaticComplexMethod`.
- The two largest uncovered rules, `ReturnCount` (22) and `ArgumentListWrapping` (15), are both
  bigger than `LongMethod` (12) and neither is named in the capture.

This is not a mistake in the capture. It is the property that makes the whole approach unstable:
the correct list is only knowable after the fact, and it changes week to week. Feeds ADR-1.
