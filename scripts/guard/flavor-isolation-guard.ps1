# flavor-isolation-guard.ps1 (S0313) - diff-aware flavor-isolation guard.
#
# Enforces CLAUDE.md Rule 15 for app_v2/src/main/java Kotlin: forbidden flavor
# gates must not be ADDED to the shared main source set. Flavor-specific logic
# belongs in src/<flavor>/java (see dev/FLAVOR_DEVELOPMENT_RULES.md).
#
# Forbidden tokens (from CLAUDE.md Rule 15):
#   BuildConfig.IS_*        e.g. BuildConfig.IS_NO_LEGAL_FLAVOR
#   BuildConfig.SUPPORT_*   e.g. BuildConfig.SUPPORT_VR_PLAYER
#   BuildConfig.ENABLE_*    e.g. BuildConfig.ENABLE_*
#
# Change-source model:
#   diff (default)   git diff (unstaged + staged), or against -Ref, filtered to
#                    app_v2/src/main/java/**/*.kt. Only ADDED lines block.
#   -Path <list>     explicit files (also filtered to that root); every match is
#                    treated as new-or-touched (caller asserted intent).
#   -Ref <ref>       base ref to diff against instead of the working tree.
#   -ScanFile <p>    TEST SEAM ONLY: scan one arbitrary file (used by the fixture
#                    self-test, scripts/guard.tests/Run-Tests.ps1). Matches count
#                    as new-or-touched, or as legacy when combined with
#                    -LegacyAudit. Not part of normal production invocation.
#
# Blocking model (diff-only, S0311 6.2 - no generated baseline file to rot):
#   Legacy debt (pre-existing gates) is reported but NEVER blocks. Only
#   new/touched gates in the change set drive a non-zero exit. Run with
#   -LegacyAudit for a non-blocking full-tree count of the standing debt.
#
# Switches:
#   -LegacyAudit  additionally full-scan app_v2/src/main/java and report the
#                 legacy count; exit code still governed solely by new/touched.
#   -Json         emit a single compact JSON object on stdout, suppress human
#                 lines. Keys: blocking, exitCode, newOrTouched, legacyCount,
#                 scannedFiles, ref, mode.
#   -DryRun       fully non-mutating: do not write the JSON artifact, print the
#                 intended artifact path instead.
#
# Artifact: temp/flavor-guard/report.json (repo-relative paths only; no secrets).
#
# Exit codes:
#   0   OK    - no new/touched violations (legacy may exist).
#   1   BLOCK - at least one new-or-touched violation.
#   2   USAGE - bad usage (e.g. -Path outside app_v2/src/main/java, unreadable
#               ref, missing -ScanFile target).
#   3   ERROR - internal/unexpected error.

[CmdletBinding()]
param(
    [string[]] $Path,
    [string] $Ref,
    [string] $ScanFile,
    [switch] $Json,
    [switch] $LegacyAudit,
    [switch] $DryRun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# Stable exit-code constants (documented in the header above).
$EXIT_OK = 0
$EXIT_BLOCK = 1
$EXIT_USAGE = 2
$EXIT_ERROR = 3

$guardDir = $PSScriptRoot
$repoRoot = (Resolve-Path (Join-Path $guardDir '..' '..')).Path
$mainJavaRoot = 'app_v2/src/main/java'

# Dot-source the four library modules into this script's scope.
. (Join-Path $guardDir 'FlavorTokens.ps1')
. (Join-Path $guardDir 'ChangeSource.ps1')
. (Join-Path $guardDir 'ViolationRecord.ps1')
. (Join-Path $guardDir 'Classifier.ps1')

function Write-GuardHost {
    param([string] $Text)
    if (-not $Json) { Write-Host $Text }
}

function Get-MatchesInFile {
    # Reads one file and returns a violation record per forbidden-token match,
    # with classification left 'unset' for the caller to fill. `forceFamily` is
    # never needed - the family is captured live from the match.
    param(
        [Parameter(Mandatory)][string] $AbsolutePath,
        [Parameter(Mandatory)][string] $RepoRelPath
    )
    $regex = Get-FlavorTokenRegex
    $records = @()
    if (-not (Test-Path -LiteralPath $AbsolutePath)) { return $records }
    $lineNo = 0
    foreach ($text in [System.IO.File]::ReadLines($AbsolutePath)) {
        $lineNo++
        foreach ($m in [regex]::Matches($text, $regex)) {
            $token = $m.Value
            $family = if ($token -match 'BuildConfig\.(IS_|SUPPORT_|ENABLE_)') {
                $Matches[1].TrimEnd('_')
            } else { 'UNKNOWN' }
            $records += New-FlavorViolation `
                -File $RepoRelPath `
                -Line $lineNo `
                -MatchedToken $token `
                -TokenFamily $family `
                -RemediationCategory (Get-RemediationCategoryForFamily -TokenFamily $family)
        }
    }
    return $records
}

function Get-LegacyAuditCount {
    # Non-blocking full scan of every main-source Kotlin file. Returns the total
    # forbidden-token match count across app_v2/src/main/java.
    $root = Join-Path $repoRoot $mainJavaRoot
    if (-not (Test-Path -LiteralPath $root)) { return 0 }
    $count = 0
    $regex = Get-FlavorTokenRegex
    foreach ($f in [System.IO.Directory]::EnumerateFiles($root, '*.kt', 'AllDirectories')) {
        foreach ($text in [System.IO.File]::ReadLines($f)) {
            $count += ([regex]::Matches($text, $regex)).Count
        }
    }
    return $count
}

try {
    $violations = @()
    $scannedFiles = 0
    $mode = 'diff'
    $effectiveRef = if ([string]::IsNullOrWhiteSpace($Ref)) { 'working-tree' } else { $Ref }

    # ----- TEST SEAM: -ScanFile ------------------------------------------------
    if ($PSBoundParameters.ContainsKey('ScanFile')) {
        $mode = if ($LegacyAudit) { 'scanfile-legacy' } else { 'scanfile' }
        if ([string]::IsNullOrWhiteSpace($ScanFile) -or -not (Test-Path -LiteralPath $ScanFile)) {
            Write-GuardHost "USAGE: -ScanFile target not found: $ScanFile"
            exit $EXIT_USAGE
        }
        $abs = (Resolve-Path -LiteralPath $ScanFile).Path
        $recs = Get-MatchesInFile -AbsolutePath $abs -RepoRelPath ($ScanFile -replace '\\', '/')
        $scannedFiles = 1
        foreach ($r in $recs) {
            # -LegacyAudit simulates a non-blocking full-scan: matches are legacy.
            $r.classification = if ($LegacyAudit) { 'legacy' } else { 'new-or-touched' }
            $violations += $r
        }
    }
    else {
        # ----- Resolve the file set -------------------------------------------
        $fileSet = if ($PSBoundParameters.ContainsKey('Path')) {
            Get-ChangedMainKotlin -Path $Path
        } elseif ($PSBoundParameters.ContainsKey('Ref')) {
            Get-ChangedMainKotlin -Ref $Ref
        } else {
            Get-ChangedMainKotlin
        }

        # An explicit path outside app_v2/src/main/java is a usage error.
        $rejected = @($fileSet | Where-Object { $_.source -eq 'explicit-rejected' })
        if (($rejected | Measure-Object).Count -gt 0) {
            Write-GuardHost ("USAGE: -Path entries outside {0}: {1}" -f $mainJavaRoot, (($rejected.file) -join ', '))
            exit $EXIT_USAGE
        }

        foreach ($entry in $fileSet) {
            $scannedFiles++
            $abs = Join-Path $repoRoot $entry.file
            $recs = Get-MatchesInFile -AbsolutePath $abs -RepoRelPath $entry.file
            if (($recs | Measure-Object).Count -eq 0) { continue }

            $added = $null
            if ($entry.source -eq 'diff') {
                $added = Get-AddedLineSet -RepoRoot $repoRoot -RelPath $entry.file -Ref $Ref
            }
            foreach ($r in $recs) {
                $violations += (Get-LineClassification -Violation $r -Source $entry.source -AddedLines $added)
            }
        }
    }

    # ----- Optional non-blocking legacy full-scan (real run, not -ScanFile) ----
    $legacyCount = 0
    foreach ($v in $violations) {
        if ($v.classification -eq 'legacy') { $legacyCount++ }
    }
    if ($LegacyAudit -and -not $PSBoundParameters.ContainsKey('ScanFile')) {
        $legacyCount = Get-LegacyAuditCount
    }

    # ----- Gate: count ONLY new-or-touched ------------------------------------
    $newOrTouched = [System.Collections.Generic.List[object]]::new()
    foreach ($v in $violations) {
        if ($v.classification -eq 'new-or-touched') { $newOrTouched.Add($v) }
    }
    $blocking = $newOrTouched.Count -gt 0
    $exitCode = if ($blocking) { $EXIT_BLOCK } else { $EXIT_OK }

    # ----- Artifact -----------------------------------------------------------
    $artifactDir = Join-Path $repoRoot 'temp/flavor-guard'
    $artifactPath = Join-Path $artifactDir 'report.json'
    $report = [ordered]@{
        blocking     = $blocking
        exitCode     = $exitCode
        newOrTouched = @($newOrTouched)
        legacyCount  = $legacyCount
        scannedFiles = $scannedFiles
        ref          = $effectiveRef
        mode         = $mode
    }
    if ($DryRun) {
        Write-GuardHost "DRY-RUN: would write artifact to temp/flavor-guard/report.json"
    } else {
        if (-not (Test-Path -LiteralPath $artifactDir)) {
            New-Item -ItemType Directory -Path $artifactDir -Force | Out-Null
        }
        ($report | ConvertTo-Json -Depth 8 -Compress) | Set-Content -LiteralPath $artifactPath -Encoding UTF8
    }

    # ----- Output -------------------------------------------------------------
    if ($Json) {
        ($report | ConvertTo-Json -Depth 8 -Compress)
    } else {
        foreach ($v in $newOrTouched) {
            Write-Host ("{0}:{1}  {2}  ->  {3}" -f $v.file, $v.line, $v.matchedToken, $v.remediationCategory)
        }
        Write-Host ("legacy debt: {0} (non-blocking)" -f $legacyCount)
        if ($blocking) {
            Write-Host ("VERDICT: BLOCK ({0} new/touched) | expected exit: {1} | actual exit: {1}" -f ($newOrTouched | Measure-Object).Count, $EXIT_BLOCK)
        } else {
            Write-Host ("VERDICT: OK (0 new/touched, {0} legacy) | expected exit: {1} | actual exit: {1}" -f $legacyCount, $EXIT_OK)
        }
    }

    exit $exitCode
}
catch {
    if (-not $Json) {
        Write-Host ("ERROR: {0}" -f $_.Exception.Message)
    } else {
        ([ordered]@{ blocking = $false; exitCode = $EXIT_ERROR; error = $_.Exception.Message } | ConvertTo-Json -Compress)
    }
    exit $EXIT_ERROR
}
