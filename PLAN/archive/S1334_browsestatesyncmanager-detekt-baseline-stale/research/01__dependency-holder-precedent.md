# Research 01 - Dependency-holder precedent for LongParameterList fixes

## Question

S1334 §2 leaves open whether the live `LongParameterList` finding on the affected class should be
fixed by bringing the constructor back under the detekt threshold, or by re-freezing the baseline
entry at the current (higher, never-approved) parameter count.

## Finding

The project already has an established, twice-applied pattern for exactly this situation: group a
subset of a long constructor's related dependencies into a small `data class` holder defined in a
sibling `<Feature>Dependencies.kt` file, and pass one holder instead of N flat parameters.

Confirmed via `dev/CATALOG/scripts/query.ps1 -ClassMatches "*Dependencies*"`:

- `ui/player/VideoPlayerDependencies.kt` - three holders (`VideoPlayerHostDependencies`,
  `VideoPlayerNetworkDependencies`, `VideoPlayerStoreDependencies`), each a plain `data class` of
  related fields, no behaviour.
- `ui/launcher/LauncherHomeDependencies.kt` - three holders (`LauncherDesktopDependencies`,
  `LauncherTaskbarDependencies`, `LauncherShortcutDependencies`), delivered by **S1314**
  (`launcherhomeviewmodel-detekt-debt`, status `Verified`), the direct sibling ticket in the
  per-class detekt-debt family this spec's §3 references.

Both precedents hold only genuine dependencies (repositories, use cases, clients) in the holder -
never lambdas/callbacks. That matches the affected class's own parameter list: three of its eleven
parameters are `UseCase` dependencies (the rest are scope/dispatcher/state-plumbing callbacks that
don't fit the same holder without changing call-site ergonomics).

## Conclusion

Group the three `UseCase` parameters into one holder, following the same shape as the two existing
precedents. This drops the parameter count from 11 to 9 - two clear of the threshold of 10, not
sitting exactly on it (sitting exactly on the threshold is its own resurfacing risk, per
`feedback_detekt_baseline_signature_resurface.md`: "a class sitting *exactly at* the threshold tips
into a genuine new finding at +1"). No behaviour change; the holder is constructed once at the call
site and passed through.

This resolves S1334 §2's first open question. Re-freezing at 11 is explicitly rejected by prior
guidance already recorded in `feedback_detekt_baseline_signature_resurface.md` §"Third shape": it
"quietly accepts one more than was ever approved" and does nothing about the next drift.
