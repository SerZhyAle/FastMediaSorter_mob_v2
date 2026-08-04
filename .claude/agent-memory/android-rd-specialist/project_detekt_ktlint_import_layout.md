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
case-sensitive comparison (a one-off S0751 sorter script in `temp/` was wiped; if re-authoring,
sort with `[string]::CompareOrdinal` per group). Verify with `./gradlew.bat :app_v2:detekt --rerun-tasks` (plain `:app_v2:detekt` may replay a
cached failure without regenerating app_v2/build/reports/detekt/detekt.txt). Related: also fix
`TooGenericExceptionThrown` via `check(..)`/`error(..)` not `throw IllegalStateException`
(detekt's UseCheckOrError fires on the latter). See [[detekt-gate-in-post-change]].

**Do not script the check in PowerShell - both obvious ways lie.** On S1328 (2026-07-31) two
scripted audits of `StreamsActivity.kt` gave two different wrong answers before hand-comparison
found the single real violation. `Sort-Object -CaseSensitive` is culture-aware, not ordinal, and
reported 33 bogus out-of-place imports. Piping `Group-Object` output into
`[Array]::Sort($g, [System.StringComparer]::Ordinal)` reported 30 - the elements arrive
`PSObject`-wrapped, so the comparer is silently ignored and the sort falls back to
case-insensitive. Both flag the correct-by-rule cases (`com.x.R` above `com.x.core.*`,
`ui.player.PlayerActivity` above `ui.player.helpers.*`) as errors. Compare the suspect pair by hand
against the rule above, then let a fresh detekt run be the proof. Note also that `ImportOrdering`
is one finding for the whole block, so the finding count never tells you how many lines are wrong.
