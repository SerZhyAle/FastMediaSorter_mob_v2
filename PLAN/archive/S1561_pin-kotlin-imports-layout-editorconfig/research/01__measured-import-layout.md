# Research 01 - The import layout, established by measurement

**Spec:** S1561 - pin-kotlin-imports-layout-editorconfig
**Date:** 2026-08-11
**Question:** what layout does the current toolchain actually require, and does writing it down change anything?

---

## Toolchain in force

- detekt `1.23.8` (`build.gradle.kts:34`), with `detekt-formatting:1.23.8` on the `detektPlugins`
  configuration (`build.gradle.kts:59`), so ktlint runs inside detekt.
- `config/detekt/detekt.yml`: `formatting.android: true`, `formatting.autoCorrect: false`.
- No `.editorconfig` existed anywhere in the repository before this ticket - confirmed by direct check, the
  only prior matches being inside `scripts/mcp/*/node_modules/`.

## The value

```text
ij_kotlin_imports_layout = *,java.**,javax.**,kotlin.**,^
```

Read as: everything else first in lexicographic order, then `java.*`, then `javax.*`, then `kotlin.*`, then
aliased imports.

## Why this is measured rather than asserted

Reading a ruleset's documented default proves what the documentation says, not what this build does. The
decisive test is a before/after comparison on a full run.

Method: run `scripts/quality/assert-detekt.ps1 -Module app_v2 -NoCache` with the file present, move the file
aside, run again, and compare `app_v2/build/reports/detekt/detekt.txt` both times.

Result - the two runs produced the same finding set:

- 7 x `ArgumentListWrapping`
- 2 x `ReturnCount`
- 1 x `ImportOrdering`
- 1 x `MaxLineLength`
- 1 x `SpacingBetweenDeclarationsWithComments`
- 1 x `UnusedPrivateMember`

Zero delta. The pinned value is therefore identical to what the toolchain already enforces, which is exactly
the property strategic criterion 2 asks for.

`wear` was checked separately: `assert-detekt: PASS [wear] (no new findings; baselines hold)` with the file in
place.

## Note on the surviving ImportOrdering finding

The single `ImportOrdering` finding is in `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/
BrowseDeleteManager.kt:3`, where `domain.repository.SettingsRepository` sits between `domain.usecase.*`
entries. It appears in **both** runs, so it is not caused by this ticket - it is another ticket's in-flight
edit in the shared working tree, and it is not one of the 273 entries already absorbed by
`baseline-app_v2.xml`. Left alone deliberately: touching a live edit from another session would collide.

## Baseline backlog, for the record

- `config/detekt/baseline-app_v2.xml`: 273 suppressed `ImportOrdering` entries.
- `config/detekt/baseline-wear.xml`: 5.

Total 278. Clearing them is a Non-goal of this spec (section 2) and stays open as section 6 question 3.
