#requires -Version 7.0
<#
.SYNOPSIS
    S1568 ratchet gate: no NEW unreferenced name may appear in a module's strings file.

.DESCRIPTION
    397 of 3234 names in app_v2's values/strings.xml had accumulated with nothing referencing them,
    at a cost paid ten times over by every locale tranche. 390 were deleted; this gate is what stops
    the file refilling, because the reason they accumulated is that nothing was measuring.

    The measurement is NOT reimplemented here - it comes from
    scripts/quality/lib/android-string-liveness.ps1, shared with the audit report and the removal
    tool (strategic ADR-2). Two properties of it decide every verdict below and are documented there:
    liveness is decided per module, and every source set under <module>/src is scanned, not src/main
    alone.

    The baseline is an ALLOWLIST OF NAMES, not a count. A count-ratchet would let one new dead name
    in whenever another was deleted; naming them means a new one is always visible. Each baseline
    line may carry a `# reason` comment, and the seeded reasons are the record of why a measured-dead
    name was deliberately kept - the only durable answer to "on what basis was this judged".

    Ratchet slack - a baseline name that has since become referenced or been deleted - is reported,
    never failed. Tightening is a `-UpdateBaseline` away and must never block an unrelated change.

.PARAMETER Gate
    Fail (exit 1) when a name is unreferenced and absent from the baseline. Without it, report only.

.PARAMETER UpdateBaseline
    Rewrite the baseline from the current measurement. Reason comments for names that survive are
    preserved; a newly added name lands without one and should be given a reason by hand.

.PARAMETER List
    Print the current baseline with its reasons and exit.

.PARAMETER Quiet
    Print only the verdict line. Used by the fast-gates batch.

.PARAMETER Module
    Module directory name relative to the repository root. Default app_v2.

.PARAMETER File
    Strings file basename inside <module>/src/main/res/values. Default strings.xml.

.NOTES
    Exit codes:
      0 - no new unreferenced name (or reporting mode, which never fails).
      1 - under -Gate: at least one unreferenced name is not in the baseline.
      2 - cannot verify: the module, its values directory, or the strings file could not be read.
          Never conflated with 0 - a gate that could not look has not passed.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-unreferenced-strings.ps1 -Gate

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-unreferenced-strings.ps1 -List
#>
[CmdletBinding()]
param(
    [switch]$Gate,
    [switch]$UpdateBaseline,
    [switch]$List,
    [switch]$Quiet,
    [string]$Module = 'app_v2',
    [string]$File = 'strings.xml'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot 'lib/android-string-liveness.ps1')

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$baselineFile = Join-Path $PSScriptRoot 'assert-unreferenced-strings-baseline.txt'

# name -> reason (possibly empty). Everything from '#' on is the reason; blank lines and whole-line
# comments are skipped, so the Phase 03 hold-back file is accepted verbatim.
$baseline = [ordered]@{}
if (Test-Path -LiteralPath $baselineFile) {
    foreach ($line in [System.IO.File]::ReadAllLines($baselineFile)) {
        $trimmed = $line.Trim()
        if (-not $trimmed -or $trimmed.StartsWith('#')) { continue }
        $parts = $trimmed -split '#', 2
        $name = ($parts[0].Trim() -split '\s+')[0]
        if (-not $name) { continue }
        $baseline[$name] = if ($parts.Count -gt 1) { $parts[1].Trim() } else { '' }
    }
}

if ($List) {
    Write-Host "assert-unreferenced-strings: baseline holds $($baseline.Count) allowed name(s)."
    foreach ($name in $baseline.Keys) {
        $reason = if ($baseline[$name]) { $baseline[$name] } else { 'no reason recorded' }
        Write-Host ("  {0,-42} {1}" -f $name, $reason)
    }
    exit 0
}

try {
    $result = Get-UnreferencedResourceNames -Module $Module -File $File -RepoRoot $repoRoot
}
catch {
    Write-Error "assert-unreferenced-strings: cannot verify - $($_.Exception.Message)" -ErrorAction Continue
    exit 2
}

$current = @($result.Entries | ForEach-Object { $_.Name })
$new = @($current | Where-Object { -not $baseline.Contains($_) })
$slack = @($baseline.Keys | Where-Object { $_ -notin $current })

if ($UpdateBaseline) {
    $lines = [System.Collections.Generic.List[string]]::new()
    $lines.Add('# assert-unreferenced-strings baseline (S1568).')
    $lines.Add('# Names measured as unreferenced that are deliberately KEPT. One per line, with the reason.')
    $lines.Add("# Regenerate the measurement with: pwsh -NoProfile -File scripts/utils/audit-unreferenced-strings.ps1 -Module $Module -File $File")
    $lines.Add('# A new name here needs a written reason - an unexplained entry is how the previous 397 accumulated.')
    foreach ($name in $current) {
        $reason = if ($baseline.Contains($name) -and $baseline[$name]) { $baseline[$name] } else { 'TODO: state why this name is kept' }
        $lines.Add(("{0}  # {1}" -f $name, $reason))
    }
    Set-Content -LiteralPath $baselineFile -Value ($lines -join [Environment]::NewLine) -Encoding UTF8
    Write-Host "assert-unreferenced-strings: baseline updated - $($current.Count) name(s)." -ForegroundColor Cyan
    exit 0
}

if (-not $Quiet) {
    Write-Host "assert-unreferenced-strings: declared=$($result.DeclaredCount) unreferenced=$($current.Count) baseline=$($baseline.Count) new=$($new.Count) slack=$($slack.Count)"
    foreach ($name in $new) {
        $kind = ($result.Entries | Where-Object { $_.Name -eq $name } | Select-Object -First 1).Kind
        Write-Host ("  NEW  {0,-42} <{1}> nothing under {2}/src references it" -f $name, $kind, $Module) -ForegroundColor Yellow
    }
    foreach ($name in $slack) {
        Write-Host ("  slack {0,-41} baselined but no longer unreferenced - tighten with -UpdateBaseline" -f $name) -ForegroundColor DarkGray
    }
}

if ($new.Count -gt 0 -and $Gate) {
    Write-Error "assert-unreferenced-strings: FAIL - $($new.Count) new unreferenced name(s) in $Module/$File. Reference them, delete them with 'set-android-string.ps1 -Action remove', or add them to $baselineFile with a reason." -ErrorAction Continue
    exit 1
}

Write-Host "assert-unreferenced-strings: expected: 0 | actual: $($new.Count) new unreferenced name(s) (baseline $($baseline.Count))"
if ($new.Count -eq 0) {
    Write-Host 'assert-unreferenced-strings: PASS - every declared name is referenced or explicitly baselined.' -ForegroundColor Green
}
exit 0
