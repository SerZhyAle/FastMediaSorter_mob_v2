#requires -Version 7.0
<#
.SYNOPSIS
    S1540: every launcher field of AppSettings is restored by the launcher reset, or excused by name.

.DESCRIPTION
    Adding one launcher setting takes four coordinated edits. Three of them are held by a gate; the
    fourth - the field list inside `ResetLauncherToDefaultsUseCase.restoreLauncherSettings()` - was held
    by nothing. A forgotten line there compiles, passes every other gate, and reaches the user as a
    reset that silently leaves one setting untouched: the toggle it belongs with goes back to default
    while it stays on, which is an inconsistent state in the store rather than a cosmetic miss.

    This gate compares two lists that are both derivable from source:

      * the `launcher*` properties declared in `AppSettings`;
      * the `<name> = defaults.<name>` assignments inside `restoreLauncherSettings()`.

    A field in the first list and not the second fails, unless it is named in `$ExcusedFields` below
    with the reason it must survive a reset. A name in the second list that no longer exists in
    `AppSettings` fails too - a stale line is how a rename hides an uncovered field.

    Deliberately lexical: it reads the two files as text rather than compiling them, so it costs
    milliseconds and runs inside `post-change.ps1` on every change, which is the only cadence at which
    it would have caught the case it was written for.

    Why the launcher prefix is the rule here and nowhere else: the launcher reset is the one call site
    whose contract is "all of this feature's settings". Its siblings - enable-all, backup restore,
    gesture seeding - are deliberate subsets chosen per field, so completeness is not derivable for
    them and a prefix rule would produce false failures.

.PARAMETER Gate
    Gate framing: exit 1 on any violation, print a one-line verdict.

.PARAMETER Quiet
    Suppress the informational counters. Violations are always printed.

.NOTES
    Exit codes (CLAUDE.md Rule 7 / S1070 contract):
      0  PASS - every launcher field is restored or excused.
      1  FAIL - a launcher field is not restored, or a restored name no longer exists.
      2  CANNOT VERIFY - a source file is missing or no launcher field could be parsed at all.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-launcher-reset-coverage.ps1 -Gate
#>
[CmdletBinding()]
param(
    [switch]$Gate,
    [switch]$Quiet
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$settingsFile = Join-Path $repoRoot 'app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt'
$resetFile = Join-Path $repoRoot 'app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/ResetLauncherToDefaultsUseCase.kt'

# A launcher field that must NOT be restored, keyed by field name, valued by the reason it survives a
# reset. Empty today: every launcher field currently belongs in the reset, including the one-shot hint
# flag (a fresh install has not shown the hint either). An entry here is a claim about product
# behaviour, so it needs a sentence a later reader can judge - not a name on its own.
$ExcusedFields = @{}

function Fail-CannotVerify([string]$message) {
    Write-Error "assert-launcher-reset-coverage: $message" -ErrorAction Continue
    exit 2
}

foreach ($path in @($settingsFile, $resetFile)) {
    if (-not (Test-Path -LiteralPath $path)) {
        Fail-CannotVerify "source file not found: $path"
    }
}

$settingsText = Get-Content -LiteralPath $settingsFile -Raw
$resetText = Get-Content -LiteralPath $resetFile -Raw

# `    val launcherFoo: Boolean = false,` - the declaration form AppSettings uses for every field.
$declared = [regex]::Matches($settingsText, '(?m)^\s*val\s+(launcher[A-Za-z0-9_]*)\s*:') |
    ForEach-Object { $_.Groups[1].Value } |
    Sort-Object -Unique

if ($declared.Count -eq 0) {
    Fail-CannotVerify 'no launcher field parsed from AppSettings - the declaration shape changed, so this gate is blind and must be fixed rather than trusted'
}

# `                launcherFoo = defaults.launcherFoo,` - the restore form, and it must be that form:
# a copy that does not read `defaults` is not a restore.
$restored = [regex]::Matches($resetText, '(?m)^\s*(launcher[A-Za-z0-9_]*)\s*=\s*defaults\.\1\s*,?\s*$') |
    ForEach-Object { $_.Groups[1].Value } |
    Sort-Object -Unique

if ($restored.Count -eq 0) {
    Fail-CannotVerify 'no restored field parsed from ResetLauncherToDefaultsUseCase - the restore shape changed, so this gate is blind and must be fixed rather than trusted'
}

$missing = @($declared | Where-Object { $_ -notin $restored -and -not $ExcusedFields.ContainsKey($_) })
$stale = @($restored | Where-Object { $_ -notin $declared })
$excused = @($declared | Where-Object { $ExcusedFields.ContainsKey($_) })

if (-not $Quiet) {
    Write-Host ("launcher-reset-coverage: {0} launcher field(s), {1} restored, {2} excused." -f
        $declared.Count, $restored.Count, $excused.Count)
    foreach ($name in $excused) {
        Write-Host ("  excused: {0} - {1}" -f $name, $ExcusedFields[$name])
    }
}

if ($missing.Count -gt 0) {
    Write-Host 'Launcher fields not restored by the launcher reset:'
    foreach ($name in $missing) {
        Write-Host ("  {0}" -f $name)
    }
    Write-Host ('Add `<name> = defaults.<name>` to ResetLauncherToDefaultsUseCase.restoreLauncherSettings(), ' +
        'or excuse it in this script with the reason it must survive a reset.')
}

if ($stale.Count -gt 0) {
    Write-Host 'Restored names that no longer exist in AppSettings:'
    foreach ($name in $stale) {
        Write-Host ("  {0}" -f $name)
    }
    Write-Host 'Remove or rename the line - a stale entry is how a renamed field stops being covered.'
}

if ($missing.Count -gt 0 -or $stale.Count -gt 0) {
    Write-Error ('assert-launcher-reset-coverage: FAIL - {0} field(s) not restored, {1} stale name(s).' -f
        $missing.Count, $stale.Count) -ErrorAction Continue
    exit 1
}

if ($Gate -or -not $Quiet) {
    Write-Host 'assert-launcher-reset-coverage: PASS (every launcher field is restored or excused).'
}
exit 0
