#requires -Version 7.0
<#
.SYNOPSIS
    Gate wrapper (S1075): documentation-vs-Gradle pin drift.

.DESCRIPTION
    Delegates to scripts/check-doc-vs-gradle.ps1 (S0271), which compares the
    hand-maintained pin mentions in dev/TECH_REQUIREMENTS.md against the canonical
    versions read from the Gradle build files. Any FAIL / INCONSISTENT / MISSING makes
    this gate fail, so drift is caught at change time instead of accumulating unseen
    until the next pre-release sweep.

    Scope note: CLAUDE.md and docs/TECH_STACK.md pins are OWNED by
    generate-toolchain-pins.ps1 (managed generated block) and verified by the separate
    doc-pins-sync gate - they are intentionally not required by the drift checker.

    S3445 added a second rule, run in this process after the pin checker: a user document may not
    credit a capability to a dependency no build declares (scripts/doc-drift/
    absent-dependency-claims.psd1, judged by scripts/doc-drift/AbsentDependencyClaims.ps1). Each
    such line prints as `CLAIM | doc:line | ..` and fails the gate like a pin drift; the documents
    it judges join the declared input set.

    S2827 gave it -ChangedFiles: this is a fixed-input gate in the S2824 sense - nine named
    files, one rule judged between them - and it had no way to decline a drift its caller
    could not have caused. Its trigger is a ChangeType category, not a path, so every Doc /
    Config / Mixed / Tooling closure in the repo ran it; S2815's closure was refused by a
    room-schema bump another ticket had left undocumented, with neither named file in its set.

    Exit codes (S1070):
      0 - no drift (doc pins match Gradle).
      1 - drift found (FAIL / INCONSISTENT / MISSING), a CLAIM finding, or the underlying
          checker could not run.
      3 - NOT CHARGED (S2824/S2827) - drift was found, but no file this gate declares as an
          input is in -ChangedFiles, so it is not attributable to this run. The drift is
          printed. Distinct from 1 because the caller cannot fix it and from 0 because
          something IS wrong in the tree.

.PARAMETER Gate
    Accepted for parity with the other assert-*.ps1 fast gates. The underlying checker
    is already fail-closed (exit 1 on any drift), so this switch is a no-op marker.

.PARAMETER Quiet
    Print only the checker's SUMMARY line, not every record.

.PARAMETER ChangedFiles
    Repo-relative paths of the files the caller changed, comma-joined. Supplying it lets the
    gate decline to charge drift when none of its declared inputs - the doc paths named in
    scripts/doc-drift/pins.psd1 and the Gradle sources named by Get-GradleSourcePaths - is
    among them. Omit it, as .\a.ps1 fg and the release path do, and every drift stays fatal.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-doc-pin-drift.ps1 -Gate
#>
[CmdletBinding()]
param(
    [switch]$Gate,
    [switch]$Quiet,
    # S1184/S1340: `pwsh -File` binds only the first element of a [string[]] and rejects the rest
    # as positional args, so callers comma-join and Expand-ChangedFiles splits it back.
    [string[]]$ChangedFiles
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# S2824: the chargeability test, shared with the other fixed-input gates.
. (Join-Path $PSScriptRoot 'lib/fixed-input-scope.ps1')

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
. (Join-Path $repoRoot 'scripts/doc-drift/AbsentDependencyClaims.ps1')
$checker = Join-Path $repoRoot 'scripts/check-doc-vs-gradle.ps1'

if (-not (Test-Path -LiteralPath $checker)) {
    Write-Error "doc-vs-gradle checker not found: $checker" -ErrorAction Continue
    exit 1
}

function Get-DocPinInputPaths {
    <# Every file the drift comparison reads: the doc side declared per pin in pins.psd1, the
       canonical side named by GradleParser. Both are read from their owners rather than copied
       here, so a new pin or a new doc mention cannot leave this gate judging a stale set. #>
    [CmdletBinding()]
    param([Parameter(Mandatory = $true)][string]$RepoRoot)

    $paths = [System.Collections.Generic.List[string]]::new()

    $manifestPath = Join-Path $RepoRoot 'scripts/doc-drift/pins.psd1'
    if (Test-Path -LiteralPath $manifestPath) {
        $manifest = Import-PowerShellDataFile -LiteralPath $manifestPath
        foreach ($pin in $manifest.Pins) {
            foreach ($docPath in $pin.docs.Keys) { $paths.Add((Join-Path $RepoRoot $docPath)) }
        }
    }

    # Dot-sourced, not invoked in a child process: this file only assigns regex constants and
    # defines functions at top level, and Get-GradleSourcePaths is Join-Path calls - neither
    # trips the strict mode this wrapper runs under, unlike the checker it deliberately isolates.
    $parser = Join-Path $RepoRoot 'scripts/doc-drift/GradleParser.ps1'
    if (Test-Path -LiteralPath $parser) {
        . $parser
        foreach ($sourcePath in (Get-GradleSourcePaths -RepoRoot $RepoRoot).Values) { $paths.Add($sourcePath) }
    }

    foreach ($claimDoc in (Get-AbsentDependencyClaimDocPaths -RepoRoot $RepoRoot)) { $paths.Add($claimDoc) }
    $paths.Add((Join-Path $RepoRoot 'scripts/doc-drift/absent-dependency-claims.psd1'))
    $paths.Add((Join-Path $RepoRoot 'gradle/libs.versions.toml'))

    return @($paths | Sort-Object -Unique)
}

# Run the checker in a SEPARATE process: this wrapper is under Set-StrictMode -Version
# Latest, and `& $checker` in-process would leak that into the checker + its dot-sourced
# modules (which are not strict-clean) and break them on legitimate scalar/$null .Count.
$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") {
    "$env:ProgramFiles\PowerShell\7\pwsh.exe"
}
else {
    'pwsh'
}
$output = & $pwshExe -NoProfile -File $checker
$checkerExit = $LASTEXITCODE

if ($Quiet) {
    $output | Where-Object { $_ -match '^SUMMARY' } | ForEach-Object { Write-Host $_ }
}
else {
    $output | ForEach-Object { Write-Host $_ }
}

$claimFindings = @(Get-AbsentDependencyClaimFindings -RepoRoot $repoRoot)
$claimFindings | ForEach-Object { Write-Host $_ }

if ($checkerExit -ne 0 -or $claimFindings.Count -gt 0) {
    # S2827: the declared input set, read from the same two places the checker reads it from, so
    # adding a pin or a doc to pins.psd1 widens this gate's chargeable set in the same edit.
    $declaredInputs = @(Get-DocPinInputPaths -RepoRoot $repoRoot)
    if (-not (Test-FixedInputsChargeable -ChangedFiles $ChangedFiles -InputPaths $declaredInputs)) {
        $findings = @(@($output | Where-Object { $_ -match '^(FAIL|INCONSISTENT|MISSING)\s*\|' }) + $claimFindings)
        if ($findings.Count -eq 0) { $findings = @('drift reported by check-doc-vs-gradle.ps1') }
        Write-NotChargedVerdict -GateName 'assert-doc-pin-drift' -Findings $findings
        exit 3
    }
    if ($checkerExit -ne 0) {
        Write-Host 'assert-doc-pin-drift: FAIL - dev/TECH_REQUIREMENTS.md is out of sync with Gradle pins (run: pwsh -NoProfile -File scripts/check-doc-vs-gradle.ps1).' -ForegroundColor Red
    }
    if ($claimFindings.Count -gt 0) {
        Write-Host "assert-doc-pin-drift: FAIL - $($claimFindings.Count) document line(s) credit a capability to a dependency no build declares (CLAIM rows above; rule: scripts/doc-drift/absent-dependency-claims.psd1)." -ForegroundColor Red
    }
    exit 1
}

Write-Host 'assert-doc-pin-drift: PASS (doc pins match Gradle, no absent-dependency claim).' -ForegroundColor Green
exit 0
