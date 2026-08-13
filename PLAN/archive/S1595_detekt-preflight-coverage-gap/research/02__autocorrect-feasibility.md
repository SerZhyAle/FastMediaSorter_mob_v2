# 02 - Does `--auto-correct` remove the ImportOrdering / line-length failures?

**Strategic §6 item:** 2
**Date:** 2026-08-12
**Verdict:** REJECT as proposed. Line length is not mechanically correctable at all; import
ordering is, but not through the only switch this build exposes.

## Line length cannot be auto-corrected

Two different rules report the same condition and both are live in this configuration:

- `style:MaxLineLength` - detekt's own rule, 2031 baselined findings in `app_v2`. It sits
  outside the `formatting` rule set, so the autocorrect mechanism has no code path to it at all.
- `formatting:MaximumLineLength` - the ktlint-wrapped rule, 495 baselined findings. It is inside
  the mechanism, but the rule only reports; it does not reflow or wrap a line.

Lines shorten only as a side effect of separate wrapping rules inserting breaks at commas, dots
and parens. That is partial and untargeted - a single long identifier, string or URL survives
untouched. Neither rule is overridden in `config/detekt/detekt.yml`, so both run at documented
defaults through `buildUponDefaultConfig`.

Side finding: the two rules fired together on all eight line-length failures in the corpus, and
the shipped preflight recognises only the first of the two names.

## Import ordering is correctable, but the switch is too coarse

`ImportOrdering` is a deterministic ktlint rewrite (pure reordering, no semantic risk) and is
eligible. The problem is the wiring: this build has exactly one surface for the flag - the shared
`formatting:` block in `config/detekt/detekt.yml`, applied identically to both modules through a
single extension in the root build file. There is no per-rule and no per-invocation override.

Flipping it to fix 273 (+5 in `wear`) `ImportOrdering` findings simultaneously arms every other
finding in the same rule set for rewrite on the next run by every agent:

`ArgumentListWrapping` 1992, `MaximumLineLength` 495, `Wrapping` 398, `NoTrailingSpaces` 346,
`MultiLineIfElse` 333, `SpacingBetweenDeclarationsWithComments` 294, `ImportOrdering` 273,
`NoMultipleSpaces` 258, `SpacingBetweenDeclarationsWithAnnotations` 212, `NoUnusedImports` 174,
`NewLineAtEndOfFile` 158, `FinalNewline` 158, `Indentation` 139, and a long tail - about
**5,591 findings, 46% of the module's entire 12,254-entry baseline**.

Each rewritten element re-keys its baseline entry. `docs/DEV_OPS.md` already documents that
mechanism and its consequence: the suppressed finding does not disappear, it lies dormant until
an unrelated change to the same file trips the diff-scoped gate, which then blames that unrelated
ticket. The repository has been bitten by this from mutations far smaller than a bulk rewrite.

## Cost

An autocorrect run is the same whole-module task over the same source set, plus ktlint's
write-back. Strictly additive per run, never cheaper. It also invalidates the gate's own
clean-verdict cache (fingerprinted on every analysed file's length and mtime), which is
documented as hit 56% of the time - so the cost is not one extra run but a forced full run for
every subsequent caller until the tree stabilises.

## What remains viable

A lexical rewrite of the import block, with no gradle involved: zero blast radius on other rules,
no lock contention, no cache invalidation. `detekt-preflight.ps1` already parses the import block
and encodes the layout, and `S1561` measured that layout independently. Deliberately NOT part of
this ticket - it is a separate change with its own validation need, and folding it in here would
mix a diagnosis change with a mutation change.
