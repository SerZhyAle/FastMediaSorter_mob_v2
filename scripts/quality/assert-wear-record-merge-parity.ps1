#requires -Version 7.0
<#
.SYNOPSIS
    S2502: fails when the two WearRecordMergeResolver copies stop agreeing.

.DESCRIPTION
    The rule deciding whether an incoming resource outranks the stored one has to give the same
    answer on the phone and on the watch, or the exchange never converges: each side keeps its own
    version, each side believes it won, and the next sync repeats the disagreement forever.

    The two modules compile separately and share no artifact, so the class is written twice. That is
    the same arrangement S2093 made for WearSettingsMergeResolver, and the settings copies had
    already drifted once before a gate was put over them.

    The comparison is textual and deliberately strict: everything except the `package` line and blank
    lines must match byte for byte, KDoc included. A comment that explains the rule differently on
    one side is how the next divergence starts.

.NOTES
    Run from anywhere; paths are resolved relative to the repo root.

    Exit codes:
      0 - clean, the two copies agree
      1 - the copies diverge; the first differing line is named
      2 - cannot verify: one of the two files is missing
#>
[CmdletBinding()]
param(
    [switch]$Gate,
    [switch]$Quiet
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$root = (Resolve-Path "$PSScriptRoot/../..").Path

$phonePath = Join-Path $root 'app_v2/src/main/java/com/sza/fastmediasorter/domain/model/WearRecordMergeResolver.kt'
$watchPath = Join-Path $root 'wear/src/main/java/com/sza/fastmediasorter/wear/domain/model/WearRecordMergeResolver.kt'

foreach ($path in @($phonePath, $watchPath)) {
    if (-not (Test-Path -LiteralPath $path)) {
        Write-Host "assert-wear-record-merge-parity: cannot verify - missing $path" -ForegroundColor Yellow
        exit 2
    }
}

# The package line is the one line that MUST differ, and blank lines carry no policy.
function Get-ComparableLines {
    param([string]$Path)
    return @(
        Get-Content -LiteralPath $Path |
            Where-Object { $_ -notmatch '^\s*package\s' -and $_.Trim() -ne '' }
    )
}

$phoneLines = Get-ComparableLines -Path $phonePath
$watchLines = Get-ComparableLines -Path $watchPath

$limit = [Math]::Max($phoneLines.Count, $watchLines.Count)
$firstDiff = -1
for ($i = 0; $i -lt $limit; $i++) {
    $phoneLine = if ($i -lt $phoneLines.Count) { $phoneLines[$i] } else { $null }
    $watchLine = if ($i -lt $watchLines.Count) { $watchLines[$i] } else { $null }
    if ($phoneLine -ne $watchLine) {
        $firstDiff = $i
        break
    }
}

if ($firstDiff -ge 0) {
    $phoneText = if ($firstDiff -lt $phoneLines.Count) { $phoneLines[$firstDiff] } else { '<end of file>' }
    $watchText = if ($firstDiff -lt $watchLines.Count) { $watchLines[$firstDiff] } else { '<end of file>' }
    Write-Host 'assert-wear-record-merge-parity: FAIL - the two resolver copies diverge.' -ForegroundColor Red
    Write-Host "  comparable line $($firstDiff + 1) (package and blank lines excluded)"
    Write-Host "  app_v2: $phoneText"
    Write-Host "  wear:   $watchText"
    Write-Host '  Fix by copying one file over the other and changing only the package line.'
    exit 1
}

if (-not $Quiet) {
    Write-Host "assert-wear-record-merge-parity: PASS - $($phoneLines.Count) comparable line(s) identical in both modules."
}
exit 0
