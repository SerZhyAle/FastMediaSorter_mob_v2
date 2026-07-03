---
name: detekt-ktlint-import-layout
description: ktlint ImportOrdering layout used by detekt formatting (android=true) - grouping + case-sensitive sort
metadata:
  type: project
---

detekt `formatting` ruleset with `android: true` (config/detekt/detekt.yml) enforces ktlint
ImportOrdering with layout `*,java.**,javax.**,kotlin.**,^`:
- Group order (NO blank lines between, comma = no gap): everything-else first, then `java.*`,
  then `javax.*`, then `kotlin.*`, then aliases. NOTE `kotlinx.*` and `timber.*` are NOT
  `kotlin.*` - they stay in the first (`*`) group. So `kotlinx`/`timber` sort BEFORE `java`/`javax`.
- Within each group: case-SENSITIVE ASCII (uppercase before lowercase). So `com.x.R` and
  `kotlinx.coroutines.flow.MutableStateFlow` sort ABOVE their lowercase-segment siblings
  (`com.x.core.*`, `...flow.combine`).

**Why:** Fixing detekt ImportOrdering drift (S0751) took 3 wrong iterations because the default
assumption (flat case-insensitive, like IDE optimize-imports) is wrong on BOTH axes. detekt has
`autoCorrect: false`, so it reports but never fixes - you must produce the exact order by hand.

**How to apply:** When clearing ImportOrdering findings, reorder with the grouping above +
case-sensitive comparison. Reusable sorter: `temp/s0751_sort_imports.ps1` (PowerShell, uses
`[string]::CompareOrdinal` + a Get-Group helper; hardcode the file list, run with `pwsh -File`).
Verify with `./gradlew.bat :app_v2:detekt --rerun-tasks` (plain `:app_v2:detekt` may replay a
cached failure without regenerating app_v2/build/reports/detekt/detekt.txt). Related: also fix
`TooGenericExceptionThrown` via `check(..)`/`error(..)` not `throw IllegalStateException`
(detekt's UseCheckOrError fires on the latter). See [[detekt_gate_in_post_change]].
