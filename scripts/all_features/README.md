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

## Shape 1 and shape 3 combine when the store watch build lacks the watch half (S3264)

The three shapes above split on "does the phone participate". They do not answer a fourth case the
Wear store boundary created: a phone-bridge capability whose **watch** half sits on a route,
component or permission `wear/config/store-boundary-policy.json` confines to the sideload artifact.
Every phone-watch exchange runs through `.data.wear.WatchWearListenerService`, which the store watch
build does not carry, so a capability like "watch colour scheme set from the phone" does not exist
end to end there whatever the phone build offers.

Such a record keeps its `SUPPORT_WEAR_COMPANION` gate **and** declares `wearFlavors: ["noLegal"]`.
The two fields answer about different modules and `validate.ps1` accepts both at once - what it
refuses is `wearFlavors` naming every watch variant, which is what absence already says. Which
records are in this position is declared in `scripts/quality/allfeatures-wear-boundary-map.json` and
held by check 6 of `scripts/quality/assert-allfeatures-sync.ps1`.

## `wearFlavors` has its own writer, because the sanctioned ones destroy it (S3264)

`add.ps1` has no parameter for the field, and `patch.ps1` rebuilds the record from an `[ordered]` of
the parameters it was given - so patching any field of a record that carried `wearFlavors` drops the
key, exactly as S3209 measured for `gate` one field over. Both are canon forwarders whose bodies
this repository does not own (S2402).

Write it with `scripts/all_features/set-wear-flavors.ps1 -Ids <id[,id..]> -WearFlavors noLegal`,
which rebuilds from the record's own property order rather than from a known field list, and
`-Clear` to go back to "every watch build". A strip by some later `patch.ps1` call is caught by
check 6 inside the closure that did it, for any record the boundary map names.

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

## A watch record's reach is now checked against the companion row (S2933)

The warning above stopped being advice on 2026-09-11. `wear.settings-sync-timeout` (S2916) and
`wear.watch-settings-report-carries-panel-auto-hide` (S2923) both declared
`[standard, noLegal, lite, photos, legacy]` - byte-identical to the `SUPPORT_CAST` row, so the
ratchet counted them explained - while describing phone-to-watch settings sync, which lives behind
`SUPPORT_WEAR_COMPANION` and reaches two flavors, not five.

Check 4 of `scripts/quality/assert-allfeatures-sync.ps1` now refuses that. A record whose subject is
declared in `scripts/quality/allfeatures-subject-reach.json` - today one subject, the watch, matched
by `area` = `Wear OS` or an `id` starting `wear.` / `wear-` - must declare either that subject's flag
row, read live out of `docs/FLAVOR_MATRIX.md`, or the full flavor dimension. Eight records were in
neither shape when the check landed; all eight were corrected rather than excused, so there is no
baseline file and a finding here is always real.

**The check keys off the set, never off the `gate` field**, and that is forced rather than chosen:
`close-and-log.ps1` is a canon facade (S2402) with no `-FeatGate` parameter, so a record written
through the sanctioned closure path cannot carry a gate at all. A rule demanding one would be
unsatisfiable - the `hooks.postClose` check judges the whole file inside the very call that wrote the
record, so the close would fail before anyone could add the field. `-FeatFlavors` is free, so a
wrong set is always fixable in one call.

A record that **does** name a gate is skipped here and left to `validate.ps1`, which compares its set
against the named row - a stronger claim than this check makes. That is the in-band exit for a watch
capability living behind some other flag.

What it cannot see: a record that picked the wrong one of two legal shapes. `wear.blood-pressure`
declares the companion row while its sensor is compiled only into the watch's `noLegal` variant, so
shape 3 is the truthful one - but both sets are legal and only the capability's meaning separates
them. The check narrows the error to a choice between legal shapes; it does not make the choice.

Why the general form of this rule is not here: the ratchet's blind spot is not specific to the watch.
Measured 2026-09-11, **491 of 1085 records** are explained only by coinciding with some flag's row,
and a set often equals several rows at once - `[legacy, noLegal, standard, vr]` is `SUPPORT_STREAMS`,
`ENABLE_TRANSLATION` and `SUPPORT_MIC_RECORDING` together - so no rule can derive which flag produced
a set. Closing the class needs `gate` on the write path, which is the canon change above.

## A closure that lands on an existing record strips its gate (S3209)

The paragraph above says the closure path cannot **write** a gate. Measured 2026-09-17, it also
**destroys** one. `add.ps1` upserts the whole record - it rebuilds an `[ordered]` from the parameters
it was given and replaces the line with the matching `id` - so an absent `-Gate` means "a record with
no gate", not "leave the gate alone". Closing S3208 with `-FuncOp CHANGE -FeatName "Start panel rows"`
matched `launcher.start-panel-rows`, written by S3131, and dropped `"gate":"SUPPORT_LAUNCHER"` from it.

Nothing refused. `validate.ps1` returned PASS both before and after: the schema does not require the
field, and the `flavors` set was untouched, so check 4 saw a correct reach too. The loss was found by
a human comparing the record against its previous text, and repaired with
`patch.ps1 -Id launcher.start-panel-rows -Gate SUPPORT_LAUNCHER -Description ..`.

Check 5 of `scripts/quality/assert-allfeatures-sync.ps1` now holds the field. Its baseline,
`scripts/quality/allfeatures-gate-baseline.txt`, is one `<id> <gate>` row per gated record - 141 of
1177 when it landed - and a record that lost its flag, carries a different one, or has left the ledger
fails the check inside the same closure that did it. The refusal prints the `patch.ps1` repair line;
an intentional re-gate is `-UpdateBaseline`, which rewrites this baseline and the count baseline
together. Unlike check 4 this one reads the `gate` field directly, which it can afford to: it never
demands the field be present, only that a field already there stays there.

## The author's own session judges the record now (S2927)

`scripts/quality/assert-allfeatures-sync.ps1 -Gate` is declared in `hooks.postClose` in
`.sza-profile.json`, so `close-and-log.ps1` runs it immediately after the `all-features` step that
wrote the record - in the same call, in every runtime, not only under Claude Code. That script is the
one that carries all three checks at once: `validate.ps1`'s schema and flavors ratchet, the
record-count ratchet, and the `area` vocabulary.

This exists because `all-features-gate` in `post-change.ps1` fires only when the changed set contains
`docs/ALL_FEATURES.jsonl`, and the one path that writes the ledger never puts it there: the file is
written by a script, so nobody names it in `-Files`. The session that **wrote** the record therefore
never judged it, and the FAIL landed on whichever session next touched the ledger by hand - S2531,
S2461, S2868 and S2927 all paid that bill, twice over two separate checks.

It is detection inside the same call, not a refusal before the write: the record is already in the
file when the check runs, and a failure surfaces as a `FAILED` step in the `close-and-log` report. A
refusal at the moment of typing belongs in `add.ps1`, which is a canon forwarder this repository does
not own - the same boundary the section below draws for the baseline file.

The hook is only as usable as the ledger is clean, because it judges the whole file rather than the
row just written: it could not be wired until S2928 cleared the eight records whose `area` sat outside
the closed vocabulary, since a permanently red hook would have failed every close in the repository.

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
`scripts/quality/assert-unreferenced-strings-baseline.txt` and
`scripts/quality/blockneedusertest-probe-baseline.txt` already use - a named row per exemption with
the reason it was granted - is a change to the harness in the canon repository, not to anything here;
editing the local forwarder achieves nothing and is overwritten by the next plugin update.
(`compose-island-baseline.txt`, named here until S2933 measured it, is a bare count like this one.)
