# Flavor isolation guard (S0313)

`flavor-isolation-guard.ps1` is a diff-aware static guard that enforces **CLAUDE.md Rule 15**: forbidden flavor gates must not be ADDED to the shared `app_v2/src/main/java` source set. Flavor-specific logic belongs in `src/<flavor>/java` (see `dev/FLAVOR_DEVELOPMENT_RULES.md`).

Forbidden tokens: `BuildConfig.IS_*`, `BuildConfig.SUPPORT_*`, `BuildConfig.ENABLE_*`.

## Blocking model (diff-only)

Only new/touched flavor gates in the change set drive a non-zero exit. Legacy debt (the pre-existing main-source gates) is reported but **never** blocks - there is no generated baseline file to rot (S0311 §6.2). Run with `-LegacyAudit` for a non-blocking full-tree count of the standing debt.

Change source:

- diff (default) - `git diff` (unstaged + staged), or against `-Ref`, filtered to `app_v2/src/main/java/**/*.kt`; only ADDED lines block.
- `-Path <list>` - explicit files; every match is treated as new-or-touched.
- `-Ref <ref>` - base ref to diff against instead of the working tree.

## Shared contract (S0311 §5.1)

- `-NoProfile`-safe; no dependency on the user's PowerShell profile.
- Stable, documented exit codes: `0` OK, `1` blocking (new/touched), `2` usage, `3` internal error.
- `-DryRun` is fully non-mutating; it prints the intended artifact path instead of writing it.
- `-Json` emits a single compact JSON object on stdout and suppresses human lines.
- A concise human verdict line is printed when `-Json` is absent.
- Artifact path: `temp/flavor-guard/report.json` (repo-relative paths only, no secrets).
- Expected-vs-actual is recorded by the fixture self-test.

## Usage

```powershell
# Guard the current working-tree change set (diff-only blocking)
pwsh -NoProfile -File scripts/guard/flavor-isolation-guard.ps1

# Guard an explicit set of files (every match treated as new/touched)
pwsh -NoProfile -File scripts/guard/flavor-isolation-guard.ps1 -Path @('app_v2/src/main/java/.../Foo.kt')

# Diff against a base ref, machine output
pwsh -NoProfile -File scripts/guard/flavor-isolation-guard.ps1 -Ref origin/main -Json

# Non-blocking full-tree legacy debt count
pwsh -NoProfile -File scripts/guard/flavor-isolation-guard.ps1 -LegacyAudit

# Non-mutating preview
pwsh -NoProfile -File scripts/guard/flavor-isolation-guard.ps1 -DryRun
```

## Self-test

```powershell
pwsh -NoProfile -File scripts/guard.tests/Run-Tests.ps1
```

Runs the `scripts/guard.tests/fixtures/*.kt.txt` fixtures (clean sample, new violation, legacy only) and asserts the blocking flag, exit code, and legacy count for each. The `.kt.txt` extension keeps the fixtures out of the Kotlin compile path.

## Integration

The guard is standalone and invoked manually. It is **not yet wired into** `post-change.ps1` or a commit/push helper - that integration point is the deferred S0311 §6.3 decision and is intentionally out of S0313 scope.
