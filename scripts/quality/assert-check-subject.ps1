#requires -Version 7.0
<#
.SYNOPSIS
    S3440: every check names the subject it checked - contract BUILD-EVIDENCE rule 1.

.DESCRIPTION
    A check's verdict is evidence only about the thing it inspected. Five incidents across three
    tickets quoted a phone-module check as proof about the watch module, which it never compiled
    (S1807); the cure was a banner naming the module, kept by habit alone until this gate.

    A check is every scripts/builders/check-*.ps1 (the fast a.ps1 targets: fk, fr, fc, fu, fw, fwr,
    fwu, fl, flr and their flavor twins) and every top-level scripts/quality/assert-*.ps1. It
    "names its subject" when its code calls Write-CheckSubject (scripts/quality/lib/check-subject.ps1)
    or prints a literal `subject:` line; comment lines are not read.

    scripts/quality/check-subject-baseline.txt is a set baseline of the checks that predate the rule.
    It only shrinks: a listed check that now prints its subject, or no longer exists, is a finding
    until its row is deleted. A new check is never added to it - it prints the line instead.

    Per ticket under the fixed-input contract (lib/fixed-input-scope.ps1): with -ChangedFiles, a
    finding is charged only when its own check, the baseline, this gate or the subject library is
    in the set.

.PARAMETER Gate
    Fail-closed: exit 1 when a chargeable finding exists.

.PARAMETER Quiet
    Suppress the PASS line.

.PARAMETER ChangedFiles
    Repo-relative paths the caller changed, comma-joined. Omit for the project-wide verdict.

.PARAMETER List
    Print the checks that name no subject, one repo-relative path per line, and exit 0 - the seed
    for the baseline, and the worklist for paying it down.

.PARAMETER Root
    Repository root to scan; defaults to this checkout. Exists for fixture tests.

.NOTES
    Exit codes:
      0 - every check names its subject or is listed; or findings without -Gate; or -List.
      1 - a chargeable finding and -Gate was passed.
      2 - could not verify: the baseline is missing, or no check was found under the root.
      3 - findings exist, but none of their files, the baseline, this gate or the library is in
          -ChangedFiles.
#>
[CmdletBinding()]
param(
    [switch]$Gate,
    [switch]$Quiet,
    # `pwsh -File` binds only the first element of a [string[]], so callers comma-join.
    [string[]]$ChangedFiles,
    [switch]$List,
    [string]$Root
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot 'lib/fixed-input-scope.ps1')
. (Join-Path $PSScriptRoot 'lib/check-subject.ps1')

if (-not $Root) { $Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot) }
$baselineRel = 'scripts/quality/check-subject-baseline.txt'
$baselinePath = Join-Path $Root $baselineRel
$ownInputs = @($baselineRel, 'scripts/quality/assert-check-subject.ps1', 'scripts/quality/lib/check-subject.ps1')

$changed = @(Expand-ChangedFiles -ChangedFiles $ChangedFiles)
if (-not $List -and -not $Quiet) {
    $subjectAxes = if ($changed.Count -gt 0) { [ordered]@{ scope = 'changed'; files = $changed.Count } } else { [ordered]@{ scope = 'repo' } }
    Write-CheckSubject -Axes $subjectAxes
}

function Get-CheckFile {
    foreach ($spec in @(@('scripts/builders', 'check-*.ps1'), @('scripts/quality', 'assert-*.ps1'))) {
        $dir = Join-Path $Root $spec[0]
        if (-not (Test-Path -LiteralPath $dir)) { continue }
        foreach ($f in (Get-ChildItem -LiteralPath $dir -File -Filter $spec[1])) {
            '{0}/{1}' -f $spec[0], $f.Name
        }
    }
}

function Test-NamesSubject([string]$Rel) {
    $code = Get-Content -LiteralPath (Join-Path $Root $Rel) -Encoding UTF8 |
        Where-Object { $_ -notmatch '^\s*#' }
    return [bool]($code | Where-Object { $_ -match 'Write-CheckSubject\b|[''"]subject: ' } | Select-Object -First 1)
}

$checks = @(Get-CheckFile | Sort-Object)
if ($checks.Count -eq 0) {
    Write-Error "assert-check-subject: could not verify - no check script found under $Root." -ErrorAction Continue
    exit 2
}
$silent = @($checks | Where-Object { -not (Test-NamesSubject $_) })

if ($List) {
    $silent | ForEach-Object { Write-Output $_ }
    exit 0
}

if (-not (Test-Path -LiteralPath $baselinePath)) {
    Write-Error "assert-check-subject: could not verify - $baselineRel is missing." -ErrorAction Continue
    exit 2
}
$listed = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
foreach ($line in (Get-Content -LiteralPath $baselinePath -Encoding UTF8)) {
    $entry = $line.Trim()
    if ($entry -and -not $entry.StartsWith('#')) { [void]$listed.Add($entry) }
}

$findings = [System.Collections.Generic.List[pscustomobject]]::new()
foreach ($rel in $silent) {
    if (-not $listed.Contains($rel)) {
        $findings.Add([pscustomobject]@{ File = $rel; Text = "${rel}: prints no 'subject:' line - call Write-CheckSubject from scripts/quality/lib/check-subject.ps1 naming what it checked" })
    }
}
foreach ($rel in $listed) {
    if ($checks -notcontains $rel) {
        $findings.Add([pscustomobject]@{ File = $rel; Text = "${rel}: listed in $baselineRel but no longer exists - delete its row" })
    }
    elseif ($silent -notcontains $rel) {
        $findings.Add([pscustomobject]@{ File = $rel; Text = "${rel}: prints its subject now - delete its row from $baselineRel" })
    }
}

if ($findings.Count -eq 0) {
    if (-not $Quiet) {
        Write-Host ("assert-check-subject: PASS - {0} check(s), {1} name their subject, {2} listed in the baseline." -f
            $checks.Count, ($checks.Count - $silent.Count), $listed.Count)
    }
    exit 0
}

$charged = @($findings | Where-Object {
        Test-FixedInputsChargeable -ChangedFiles $changed -InputPaths (@($_.File) + $ownInputs)
    })
if ($charged.Count -eq 0) {
    Write-NotChargedVerdict -GateName 'assert-check-subject' -Findings @($findings.Text)
    exit 3
}

Write-Host ("assert-check-subject: FAIL - {0} finding(s) (BUILD-EVIDENCE rule 1)." -f $charged.Count)
foreach ($finding in $charged) { Write-Host ("  {0}" -f $finding.Text) }
if ($Gate) { exit 1 }
exit 0
