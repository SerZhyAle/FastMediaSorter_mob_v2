#requires -Version 7.0
<#
.SYNOPSIS
    S1540: every launcher setting is restored by the launcher reset, or excused by name.

.DESCRIPTION
    Adding one launcher setting takes four coordinated edits. Three of them are held by a gate; the
    fourth - what `ResetLauncherToDefaultsUseCase.restoreLauncherSettings()` writes - was held by
    nothing. A forgotten field there compiles, passes every other gate, and reaches the user as a reset
    that silently leaves one setting untouched: the toggle it belongs with goes back to default while it
    stays on, which is an inconsistent state in the store rather than a cosmetic miss.

    S2300 changed the shape the reset can take. The launcher group left `AppSettings` for the nested
    `LauncherSettings` (the class had crossed the JVM's 255 descriptor-slot ceiling), so the reset now
    assigns the whole group at once and coverage of a NEW field is structural - a field added to
    `LauncherSettings` is reset by construction, and no list can fall behind it.

    What still needs a gate is the opposite direction: the exceptions. The reset deliberately carries
    three values across, by copying them off the current group, and each of those is a product claim
    that nothing else watches. So this gate now checks that:

      * the reset really does assign the whole group (`launcher = <defaults>.copy(..)`), because a
        return to a field-by-field list would silently restore the old defect class;
      * every field the reset preserves (`<name> = current.launcher.<name>`) is excused below with the
        reason it must survive a reset;
      * every excused field is actually preserved - an excusal whose line was dropped is a claim the
        code no longer honours;
      * every name on either side still exists in `LauncherSettings`, since a rename is how a field
        stops being covered without anything failing.

    Deliberately lexical: it reads the two files as text rather than compiling them, so it costs
    milliseconds and runs inside `post-change.ps1` on every change, which is the only cadence at which
    it would have caught the case it was written for.

.PARAMETER Gate
    Gate framing: exit 1 on any violation, print a one-line verdict.

.PARAMETER Quiet
    Suppress the informational counters. Violations are always printed.

.NOTES
    Exit codes (CLAUDE.md Rule 7 / S1070 contract):
      0  PASS - the reset assigns the whole group and its exceptions match the registry below.
      1  FAIL - the reset no longer assigns the group wholesale, a preserved field is not excused, an
         excused field is not preserved, or either side names a field LauncherSettings does not declare.
      2  CANNOT VERIFY - a source file is missing, or no field could be parsed at all.

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
$groupFile = Join-Path $repoRoot 'app_v2/src/main/java/com/sza/fastmediasorter/domain/model/launcher/LauncherSettings.kt'
$resetFile = Join-Path $repoRoot 'app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/ResetLauncherToDefaultsUseCase.kt'

# A launcher field the reset must NOT clear, keyed by field name, valued by the reason it survives a
# reset. An entry here is a claim about product behaviour, so it needs a sentence a later reader can
# judge - not a name on its own. Every other launcher field is reset, including the one-shot hint flag
# (a fresh install has not shown the hint either).
$ExcusedFields = @{
    weatherLastLocation = 'S2213: the city the user picked for a weather gadget. The reset clears the desktop and the launcher then re-seeds the starter set, which brings a weather cell back with no place of its own; a cleared value would hand the user back an empty block and make him search for his city again after every reset. Restoring the desktop layout is what the reset promises - discarding a choice the user made is not. Re-judge this only if the weather cell leaves the starter set, which is what makes the value observable after a reset at all.'
    allAppsSortOrder = 'S1401: the order of the all-apps list is a reading preference for a screen the desktop reset does not touch. It sits in the launcher group because it is launcher-owned state, not because it is desktop layout, and it was never part of the field-by-field reset that preceded S2300.'
    allAppsSortDescending = 'S1401: the direction half of the all-apps order above, and it must move with it - resetting one of the pair alone would leave the list sorted in a way the user never chose.'
}

# A launcher field the reset writes from a caller-supplied parameter rather than from the group's own
# default, keyed by field name, valued by the reason. Like an excusal, an entry here is a claim about
# product behaviour: it says the factory value is deliberately not what this reset writes.
$ParameterRestoredFields = @{
    densityFactor = 'S1886: the launcher reset writes the icon density chosen in the reset dialog, so the value comes from that choice rather than from the group default.'
}

function Fail-CannotVerify([string]$message) {
    Write-Error "assert-launcher-reset-coverage: $message" -ErrorAction Continue
    exit 2
}

foreach ($path in @($groupFile, $resetFile)) {
    if (-not (Test-Path -LiteralPath $path)) {
        Fail-CannotVerify "source file not found: $path"
    }
}

$groupText = Get-Content -LiteralPath $groupFile -Raw
$resetText = Get-Content -LiteralPath $resetFile -Raw

# `    val wallpaperMode: String = ..,` - the declaration form LauncherSettings uses for every field.
$declared = @([regex]::Matches($groupText, '(?m)^\s*val\s+([A-Za-z][A-Za-z0-9_]*)\s*:') |
    ForEach-Object { $_.Groups[1].Value } |
    Sort-Object -Unique)

if ($declared.Count -eq 0) {
    Fail-CannotVerify 'no field parsed from LauncherSettings - the declaration shape changed, so this gate is blind and must be fixed rather than trusted'
}

# `            launcher = defaults.copy(` - the whole-group assignment. Without it the reset is back to a
# per-field list, which is the shape that produced the defect this gate was written for.
$groupAssigned = [regex]::IsMatch($resetText, '(?m)^\s*launcher\s*=\s*[A-Za-z_][A-Za-z0-9_]*(\.copy\()?')
if (-not $groupAssigned) {
    Fail-CannotVerify 'the launcher reset does not assign the launcher group as a whole - the restore shape changed, so this gate is blind and must be fixed rather than trusted'
}

# `                    weatherLastLocation = current.launcher.weatherLastLocation,` - the preserve form.
$preserved = @([regex]::Matches($resetText, '(?m)^\s*([A-Za-z][A-Za-z0-9_]*)\s*=\s*[A-Za-z_][A-Za-z0-9_]*\.launcher\.\1\s*,?\s*$') |
    ForEach-Object { $_.Groups[1].Value } |
    Sort-Object -Unique)

# `        val defaults = LauncherSettings(densityFactor = densityFactor)` - the parameter form. The
# identifier is deliberately dot-free, so a preserve line cannot match here and be counted twice.
$parameterWritten = @([regex]::Matches($resetText, '(?m)([A-Za-z][A-Za-z0-9_]*)\s*=\s*(?!current\b)[A-Za-z_][A-Za-z0-9_]*\s*[,)]') |
    ForEach-Object { $_.Groups[1].Value } |
    Where-Object { $_ -in $declared } |
    Sort-Object -Unique)

$undeclaredParameter = @($parameterWritten | Where-Object { -not $ParameterRestoredFields.ContainsKey($_) })
$unexcusedPreserved = @($preserved | Where-Object { -not $ExcusedFields.ContainsKey($_) })
$excused = @($ExcusedFields.Keys | Sort-Object)
$droppedExcusal = @($excused | Where-Object { $_ -notin $preserved })
$stale = @(@($preserved) + @($excused) | Sort-Object -Unique | Where-Object { $_ -notin $declared })

if (-not $Quiet) {
    Write-Host ("launcher-reset-coverage: {0} launcher field(s), {1} preserved, {2} excused." -f
        $declared.Count, $preserved.Count, $excused.Count)
    foreach ($name in $excused) {
        Write-Host ("  excused: {0} - {1}" -f $name, $ExcusedFields[$name])
    }
    foreach ($name in $parameterWritten) {
        if ($ParameterRestoredFields.ContainsKey($name)) {
            Write-Host ("  parameter-written: {0} - {1}" -f $name, $ParameterRestoredFields[$name])
        }
    }
}

if ($undeclaredParameter.Count -gt 0) {
    Write-Host 'Launcher fields written from a parameter without being declared as such:'
    foreach ($name in $undeclaredParameter) {
        Write-Host ("  {0}" -f $name)
    }
    Write-Host ('Declare it in $ParameterRestoredFields in this script with the reason the reset does not ' +
        'write the group default.')
}

if ($unexcusedPreserved.Count -gt 0) {
    Write-Host 'Launcher fields the reset carries across without an excusal:'
    foreach ($name in $unexcusedPreserved) {
        Write-Host ("  {0}" -f $name)
    }
    Write-Host ('Drop the `<name> = current.launcher.<name>` line so the reset restores the default, or ' +
        'excuse it in this script with the reason it must survive a reset.')
}

if ($droppedExcusal.Count -gt 0) {
    Write-Host 'Launcher fields excused from the reset that the reset now clears anyway:'
    foreach ($name in $droppedExcusal) {
        Write-Host ("  {0} - excused because: {1}" -f $name, $ExcusedFields[$name])
    }
    Write-Host ('Restore the `<name> = current.launcher.<name>` line, or drop the excusal in this script ' +
        'if the reason above no longer holds. A field cannot be both.')
}

if ($stale.Count -gt 0) {
    Write-Host 'Names that no longer exist in LauncherSettings:'
    foreach ($name in $stale) {
        Write-Host ("  {0}" -f $name)
    }
    Write-Host 'Remove or rename it - a stale entry is how a renamed field stops being covered.'
}

if ($unexcusedPreserved.Count -gt 0 -or $droppedExcusal.Count -gt 0 -or $stale.Count -gt 0 -or $undeclaredParameter.Count -gt 0) {
    Write-Error ('assert-launcher-reset-coverage: FAIL - {0} unexcused preserved field(s), {1} dropped excusal(s), {2} stale name(s), {3} undeclared parameter assignment(s).' -f
        $unexcusedPreserved.Count, $droppedExcusal.Count, $stale.Count, $undeclaredParameter.Count) -ErrorAction Continue
    exit 1
}

if ($Gate -or -not $Quiet) {
    Write-Host 'assert-launcher-reset-coverage: PASS (the reset assigns the whole launcher group; every exception is excused).'
}
exit 0
