# S1544 evidence - fixer coverage parity (tactical step 04.2)

Verdict extract. The raw capture ran to 197 MB because `fix-yo.ps1` emitted its entire transformed
corpus to stdout on a dry run - one more reason it is gone - so only the flagged-file lines and the
conclusion are kept here.

**Measured:** 2026-08-14, before the five scripts were deleted.

## What each old script flagged

```text
=== fix-ellipsis.ps1 (dry run) ===
[MD ] docs\FEATURES_noLegal_RU.md - replaced 1 occurrence(s)
[MD ] docs\FEATURES_noLegal_UK.md - replaced 1 occurrence(s)
[MD ] docs\FEATURES_noLegal.md    - replaced 1 occurrence(s)

=== fix-ellipsis-docs.ps1 -DryRun ===
[dry-run] docs\FEATURES_noLegal_RU.md - ~1 occurrence(s)
[dry-run] docs\FEATURES_noLegal_UK.md - ~1 occurrence(s)
[dry-run] docs\FEATURES_noLegal.md    - ~1 occurrence(s)

=== fix-ellipsis-strings.ps1 -DryRun ===
[no change] app_v2\src\main\res\values\strings.xml
[no change] app_v2\src\main\res\values-ru\strings.xml
[no change] app_v2\src\main\res\values-uk\strings.xml

=== fix-yo.ps1 (dry run) ===
[XML] app_v2\src\main\res\values-ru\strings.xml - 0 string(s)
[MD ] docs\FAQ_RU.md        - 2 line(s)
[MD ] docs\LIMITATIONS_RU.md - 1 line(s)

=== fix-yo-letter.ps1 ===
No default target set: -Paths defaults to empty, so a bare invocation processes nothing.
Its dictionary was its only real contribution.
```

## What the new fixer flagged

```text
=== fix-house-style.ps1 (dry run, Prose) ===
pending docs\FAQ_RU.md               - 1 value(s) [yo]
pending docs\FEATURES_noLegal_RU.md  - 1 value(s) [ellipsis, long-dash]
pending docs\FEATURES_noLegal_UK.md  - 1 value(s) [ellipsis, long-dash]
pending docs\FEATURES_noLegal.md     - 1 value(s) [ellipsis, long-dash]
pending docs\LIMITATIONS_RU.md       - 1 value(s) [yo]
pending docs\QUICK_START_RU.md       - 1 value(s) [yo]
pending docs\README_RU.md            - 1 value(s) [yo]
pending docs\TROUBLESHOOTING_RU.md   - 1 value(s) [yo]

=== fix-house-style.ps1 (dry run, ResourceValue) ===
304 files scanned, 94 files pending across every locale and source set.
```

## Conclusion

The old set is a **strict subset** of the new set. No coverage lost; coverage gained on three axes:

1. the long dash, which none of the five ever touched - and it was the larger violation class;
2. the merged yo dictionary, reachable from a default run for the first time, which is why
   `QUICK_START_RU`, `README_RU` and `TROUBLESHOOTING_RU` appear only on the new side;
3. every locale and source set, not only `main` and `en`/`ru`/`uk`.

`docs/settings/` holds no `.md` file, so the non-recursive walk of `fix-ellipsis-docs.ps1` lost
nothing in practice; the new fixer recurses regardless.

## Reproducing the new side

```powershell
pwsh -NoProfile -File scripts/utils/fix-house-style.ps1 -Area Prose
pwsh -NoProfile -File scripts/utils/fix-house-style.ps1 -Area ResourceValue
```

Expected after S1544 landed: both report `clean - nothing to change` for the resource area and the
`FEATURES_noLegal*` trio plus the five `_RU` files for prose, which are deliberately left alone
(gitignored showcases, and prose carries no gate - strategic ADR-4).
