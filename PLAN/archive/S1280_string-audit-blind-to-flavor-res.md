# S1280 - The string localization audit reports success when it found nothing

**Status:** Archived
**Priority:** 40

## 0. Raw capture

Found while closing S1223 on 2026-07-29. That ticket added 19 keys to
`app_v2/src/vr/res/values/strings.xml` and its `-ru` / `-uk` siblings, then ran the audit the rules
require after any `strings.xml` change:

```text
pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "vr_legend"
No keys matching 'vr_legend*' found in any strict locale.
exit=0
```

Exit 0 with the keys present in all three locales. The audit did not find them, did not say it could
not find them, and reported success.

## 1. Why this matters

`check_strings_localized.ps1` is the gate that catches a key added to EN and forgotten in RU or UK.
CLAUDE.md makes it mandatory after any `strings.xml` edit and treats exit 1 as "fix first".

The first reading of this ticket blamed the scan root, and that was only half right. The script has
carried a `-SourceSet` parameter all along, and `post-change.ps1` already derives it from the edited
path, so the mandatory closure path was never blind. Two real holes remain:

- **A manual invocation silently audits the wrong source set.** Omitting `-SourceSet` audits `main`.
  A `vr`-owned prefix matches nothing there, and "nothing matched" exited 0. That is the capture
  above: a false green, which is worse than no gate, because the exit code claims the check ran and
  was happy.
- **A full sweep covers `main` only.** Running with no `-KeyPrefix` audits every key of one source
  set. Flavor-owned strings had no parity gate in that mode at all.

## 2. Decisions

Both open questions are settled by measurement rather than judgement.

- **Widen the scan, or add a separate flavor pass?** Widen the existing script, behind an opt-in
  `-AllSourceSets` switch. The default stays `main`, so every existing caller keeps its meaning, and
  `post-change.ps1` keeps its path-derived source set. One script stays the single definition of
  what parity means.
- **Should "no keys matched" stay exit 0?** No. It is either a mistyped prefix or the wrong source
  set, and both are defects in the invocation. New exit code 3, distinct from a parity failure so a
  caller can tell them apart.
- **Does widening surface a backlog that must be sized before the gate can block?** No. Measured
  2026-07-29: only four source sets under `app_v2/src` own a `strings*.xml`, and two of them are
  already clean.

## 3. Measurement (2026-07-29)

Source sets owning strings resources, and their strict-locale parity before any fix:

- `main` - 4427 keys, zero gaps.
- `vr` - 53 keys, zero gaps.
- `noLegal` - 17 keys, zero gaps.
- `debug` - 27 keys missing from RU and UK.

`lite`, `photos`, `legacy` and the capability source sets own no strings at all, so the ticket's
original worry about "four or five flavors at once" does not arise.

The 27 `debug` findings are not a translation backlog. They are the debug menu in
`src/debug/res/values/strings_debug.xml`, deliberately English-only and never shipped - the same 27
that `app_v2/build.gradle.kts` names in its `MissingTranslation` comment. Two tools that scan
independently agree on the count, which is what makes the reading trustworthy. They were simply
never marked `translatable="false"`, the attribute both this audit and lint honour.

Marking them therefore has a second effect: it removes the stated precondition for re-enabling
lint's `MissingTranslation`, which S1195 owns.

## 4. Criteria

1. A prefix that matches nothing in the audited source sets exits non-zero and says which source
   sets were searched.
2. `-AllSourceSets` audits every source set that owns a `strings*.xml`, and reports per-source-set
   plus a summary.
3. Under `-AllSourceSets`, a prefix that matches in at least one source set is a pass; only matching
   nowhere is an error.
4. Default behaviour is unchanged - no `-SourceSet`, no `-AllSourceSets` still audits `main` and
   still exits 0 on today's tree.
5. `-AllSourceSets` with no prefix exits 0 on the whole module.
6. The exit codes the script can return are listed in its header, per CLAUDE.md section 7.

## 5. Not in scope

- Re-enabling lint's `MissingTranslation` - S1195 owns that, and owns the `build.gradle.kts` edit.
- Making `-AllSourceSets` the default for `post-change.ps1`. The per-change audit is scoped to the
  edited file on purpose; the sweep is for release and audit runs.

## Last Audit

**Audited:** 2026-07-29, mechanical. No device or build involved - the acceptance is entirely exit
codes, so every criterion is demonstrated rather than argued.

Changed:

- `scripts/check_strings_localized.ps1` - per-source-set audit extracted into a function, new
  `-AllSourceSets` switch, new exit code 3, header exit contract updated.
- `app_v2/src/debug/res/values/strings_debug.xml` - all 27 keys marked `translatable="false"`, with
  a header comment stating why a new key must carry it too.

Verification matrix, each row an actual run:

- no arguments (`main`, every key) - expected exit 0 | actual exit 0, 4427 keys, zero gaps.
- `-SourceSet debug` (what `post-change.ps1` now invokes for a debug strings edit) - expected exit 0
  | actual exit 0, "no translatable strings to check".
- `-KeyPrefix vr_legend` with no source set, the original capture - expected non-zero | actual exit
  3, naming the source set searched.
- `-KeyPrefix vr_legend -AllSourceSets` - expected exit 0 | actual exit 0, 18 keys matched in `vr`.
- `-KeyPrefix zzz_not_a_key -AllSourceSets` - expected exit 3 | actual exit 3, listing all four
  source sets searched.
- `-SourceSet nosuchflavor` - expected exit 1 | actual exit 1, "Resource dir not found".
- `-AllSourceSets`, every key - expected exit 0 | actual exit 0 across debug, main, noLegal, vr.

One regression was introduced and caught before closure: with every `debug` key marked
non-translatable, an unprefixed audit of that source set matched nothing and exit 3 turned a correct
state into a red gate - which `post-change.ps1` would have hit on the very next debug strings edit.
Exit 3 is now conditional on the resolved pattern being narrower than `*`; under `*` an empty result
is reported as the legitimate state it is. The condition is on the pattern rather than on
`-KeyPrefix` being absent, because `post-change.ps1` passes `-KeyPrefix '*'` explicitly - testing the
parameter instead of the pattern reproduced the same red gate one layer down, which is how the first
attempt failed.

## 6. Related

- **S1223** - the ticket that hit it; its `vr_legend_*` keys were verified by hand-counting the three
  files instead, 18 + 1 in each.
- **S1195** - lint is red and unreadable; its step to re-enable `MissingTranslation` depends on the
  debug strings being marked here.
- **S1190** - internationalization and website localization, a different surface.
