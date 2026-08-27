# ALL_FEATURES inventory - schema notes

`docs/ALL_FEATURES.jsonl` is the EN-only developer inventory of shipped capabilities. Records are
written by `add.ps1` and judged by `validate.ps1`; the field-by-field contract lives in
`docs/ALL_FEATURES.schema.json`. This file covers the one thing the schema cannot state in a
`$comment` next to a single field: which combination of fields a **watch** capability may use.

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
- `flavors` = the full six
- no `wearFlavors` - its absence is the assertion "every watch build"

**3. Watch-standalone, one watch build** - the capability exists only in the watch's `standard` or
only in its `noLegal` variant (S2090).

- no `gate`
- `flavors` = the full six - no phone build excludes it, so a narrower set here would assert a phone
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

`validate.ps1` counts a record as **unexplained** unless its `flavors` set equals the full six or
exactly matches some flag's row (S1934). An unexplained set raises the count in
`unexplained-flavors-baseline.txt` and fails `all-features-gate` in `post-change.ps1`.

Read the **exit code**, not the last line: the ratchet verdict prints at the top of the output, so a
`| tail` misses it entirely.

`wearFlavors` deliberately takes no part in the ratchet. It is an added axis, not a reinterpretation
of `flavors` - which is why shape 3 keeps the full six rather than narrowing the field that the
ratchet reads.
