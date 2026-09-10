# ALL_FEATURES inventory - schema notes

`docs/ALL_FEATURES.jsonl` is the EN-only developer inventory of shipped capabilities. Records are
written by `add.ps1` and judged by `validate.ps1`; the field-by-field contract lives in
`docs/ALL_FEATURES.schema.json`. This file covers the one thing the schema cannot state in a
`$comment` next to a single field: which combination of fields a **watch** capability may use.

## `-Area` takes a value from a closed list

Since S2842 `area` is an enum in the schema, not a free string. Pick an existing one - `add.ps1
-ListAreas` prints what the ledger is using - and when a genuinely new subject arrives, add it to
`area.enum` in `docs/ALL_FEATURES.schema.json`, which is the only place the list exists. Nothing
here or in `CLAUDE.md` repeats the values, so the list cannot go stale in a second copy.

`add.ps1` itself accepts anything and exits 0: it is a canon forwarder and this repository does not
own its body (S2402). The refusal comes from `scripts/quality/assert-allfeatures-sync.ps1`, which
runs at ticket closure whenever the ledger is in the changed set - so an unknown area is caught, but
later than you typed it.

## The three legal shapes of a watch record

A watch record's `flavors` field is about the **phone**, always. It answers "which phone build gates
this", never "which watch build has this". That is why three shapes exist rather than two.

**1. Phone-bridge** - the phone participates: it pushes, stores, answers or originates something.

- `gate` = `SUPPORT_WEAR_COMPANION`
- `flavors` = that flag's row, **read out of `docs/FLAVOR_MATRIX.md` at the moment you write the
  record**
- no `wearFlavors`

**2. Watch-standalone, every watch build** - the capability lives entirely in the `wear` module and
both watch variants have it.

- no `gate`
- `flavors` = the full flavor list
- no `wearFlavors` - its absence is the assertion "every watch build"

**3. Watch-standalone, one watch build** - the capability exists only in the watch's `standard` or
only in its `noLegal` variant (S2090).

- no `gate`
- `flavors` = the full flavor list - no phone build excludes it, so a narrower set here would assert a phone
  exclusion that does not exist
- `wearFlavors` = the one variant. Naming both is refused: it claims exactly what absence claims.

## Never hardcode the companion row

Shape 1's `flavors` value is not a constant. As of 2026-08-23 (S1951) the `SUPPORT_WEAR_COMPANION`
row is `standard, noLegal` - `legacy` left it, because that flavor carries `applicationIdSuffix =
".legacy"` and Play Services routes the Wear Data Layer by the phone's applicationId, so a legacy
phone can never reach the watch app.

Read the row out of `docs/FLAVOR_MATRIX.md`, which is generated from `productFlavors`. When S1951
moved that row, thirty records stopped matching it in one step - twenty genuinely about the watch and
ten not about the watch at all. A flavor set that is right only by coincidence looks identical to one
that is right on purpose, until the row moves.

## Why the ratchet cares

`validate.ps1` counts a record as **unexplained** unless its `flavors` set equals the full flavor list or
exactly matches some flag's row (S1934). An unexplained set raises the count in
`unexplained-flavors-baseline.txt` and fails `all-features-gate` in `post-change.ps1`.

Read the **exit code**, not the last line: the ratchet verdict prints at the top of the output, so a
`| tail` misses it entirely.

`wearFlavors` deliberately takes no part in the ratchet. It is an added axis, not a reinterpretation
of `flavors` - which is why shape 3 keeps the full flavor list rather than narrowing the field that the
ratchet reads.

## The third shape the rule does not model: flavor identity (S2839)

Some records name a capability whose reach IS a flavor identity rather than a flag's row, and the
ratchet has no way to explain them. Three carry that shape today:

- `distribution.fdroid-foss-variant` - `[foss]`
- `file-operations.local-copy-and-move-photos-flavor` - `[photos]`
- `file-operations.local-only-file-copy-and-move-lite-flavor` - `[lite]`

No flag row in `docs/FLAVOR_MATRIX.md` equals a single subtractive flavor. `foss` is the clearest
case: it switches `SUPPORT_STREAMS`, `SUPPORT_CLOUD`, `SUPPORT_CAST`, `ENABLE_TRANSLATION` and
`SUPPORT_MIC_RECORDING` off and turns nothing of its own on, so nothing in the matrix can ever
match `[foss]`. Such a record is therefore permanently unexplained while being perfectly truthful,
and it lands in the baseline rather than in a gate.

Two repairs look right and are not. **Widening the set to the full flavor list** trades a permanently
red counter for a false claim that every build carries the capability. **Inventing a flag** such as
`IS_FOSS_FLAVOR` for the counter's sake creates a gate that gates nothing, and every later reader has
to discover that by hand. Raise the baseline instead and say which record raised it.

## The baseline names no record, and that is canon-side (S2839)

`unexplained-flavors-baseline.txt` holds a bare integer, parsed whole by
`tools/harness/all_features/validate.ps1` - the canon-shipped harness that
`scripts/all_features/validate.ps1` forwards to (S2402). A comment or a label in the file fails the
run with `baseline is not an integer`.

So a raised count says only that the total grew, never whose record grew it, and the FAIL lands on
whichever session's change set happens to contain `docs/ALL_FEATURES.jsonl` - which is how S2531 paid
for a record it did not add. Turning the file into an identity-carrying list, the shape
`scripts/quality/compose-island-baseline.txt` already uses, is a change to the harness in the canon
repository, not to anything here; editing the local forwarder achieves nothing and is overwritten by
the next plugin update.
