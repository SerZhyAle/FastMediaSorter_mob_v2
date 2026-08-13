# 01 - Can a lexical scan reproduce detekt's size rules?

**Strategic §6 item:** 1
**Date:** 2026-08-12
**Verdict:** NO - the approach named in the capture is not viable.

## Question

The capture proposes extending the lexical preflight to `LargeClass`, `LongMethod` and
`ComplexMethod`. That only works if a lexical line count agrees with what detekt itself
measures. This measures whether it does.

## Method

The frozen baselines are the calibration corpus. Every `<ID>LargeClass:File.kt$Name : Super</ID>`
entry is a class detekt itself measured as over threshold, so:

- agreement on baselined classes = recall,
- classes the scan flags that detekt never flagged = the false-positive budget.

Probe walks masked brace depth (string literals, line comments, block comments and raw strings
blanked so a brace inside them never moves the depth) from each `class`/`object`/`interface`
declaration to its matching close brace, and counts the body under three candidate metrics:

- `Raw` - every line between the braces.
- `NoBlank` - non-blank lines.
- `Lloc` - non-blank, non-comment, and not a line holding only brackets/punctuation.

Scripts: `temp/S1595/probe-largeclass.ps1`, `temp/S1595/probe-lloc.ps1`.
Corpus: `app_v2/src` + `wear/src`, both committed baselines. 27 baselined `LargeClass` entries.

## Result

At the configured threshold of 600:

| metric | baselined hit | unbaselined flagged | min(baselined) | max(unbaselined) | separable |
|---|---:|---:|---:|---:|---|
| Raw | 24 / 27 | 50 | 713 | 954 | no |
| NoBlank | 24 / 27 | 30 | - | 858 | no |
| Lloc | 15 / 27 | 1 | 516 | 692 | no |

Every metric overlaps. Under `Raw` the smallest class detekt flagged is 713 lines while the
largest it did not flag is 954 - the ordering is inverted across a 240-line band, so no cut
point exists. `Lloc` narrows the false-positive count from 50 to 1 but pushes the true-positive
floor down to 516, so the bands still cross.

Three baselined entries scored a body of 1 line: the probe's brace walker fails on declarations
whose constructor parameter list spans lines. That is a probe limitation, not data, and it is
why recall is quoted as "of 27" rather than corrected upward.

## Why this closes the approach

A preflight's whole value is that a clean verdict is trusted. `detekt-preflight.ps1`'s own header
states the asymmetry: a false positive costs an edit, a miss only costs the round-trip the gate
was going to pay anyway. Fifty false positives against twenty-seven real findings inverts that -
it produces a step that must be ignored to keep working, which is worse than no step.

The failure is not "the metric needs tuning". detekt's threshold is not applied to any line count
this scan can compute, and the ratio between raw lines and whatever detekt counts varies per file
with comment and blank density. Tuning a constant cannot fix a varying ratio.

## Consequence for the plan

Feeds ADR-1: stop approximating the analyser, run it. See `05__scoped-runner-cost-and-proof.md`.
