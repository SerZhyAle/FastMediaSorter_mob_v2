#requires -Version 7.0
<#
.SYNOPSIS
    S1568: report the names one Android strings file declares that nothing in its module references.

.DESCRIPTION
    The reproducible answer to "which string keys are dead". It reports, it does not judge: the count
    is data, and the exit code says only whether the scan could be performed. The gate that turns the
    same measurement into a pass/fail verdict is scripts/quality/assert-unreferenced-strings.ps1.

    The measurement itself lives once, in scripts/quality/lib/android-string-liveness.ps1, and is
    shared with the removal tool and the gate so the three can never disagree about what a reference
    is. Two properties of it decide the numbers below and are documented in that library: liveness is
    decided per module, and the scan covers every source set under <module>/src rather than src/main
    alone.

    Works on any strings file of any module - pass -File strings_input.xml or -Module wear - so the
    next audit needs no second script.

.PARAMETER Module
    Module directory name relative to the repository root. Default app_v2.

.PARAMETER File
    Strings file basename inside <module>/src/main/res/values. Default strings.xml.

.PARAMETER Format
    text (default) - one "<kind><TAB><name>" line per unreferenced name, then a summary line.
    json - a single object carrying the same entries and counts, for a caller that parses.

.PARAMETER OutFile
    Write the report to this path instead of the host. The summary line still prints to the host so
    a run is never silent.

.NOTES
    Exit codes:
      0 - the scan completed. Any number of unreferenced names is a valid result, including zero.
      2 - cannot verify: the module, its values directory, or the strings file does not exist.

.EXAMPLE
    pwsh -NoProfile -File scripts/utils/audit-unreferenced-strings.ps1

.EXAMPLE
    pwsh -NoProfile -File scripts/utils/audit-unreferenced-strings.ps1 -Module app_v2 -File strings.xml -OutFile temp/S1568/removal-candidates.txt

.EXAMPLE
    pwsh -NoProfile -File scripts/utils/audit-unreferenced-strings.ps1 -Format json
#>
[CmdletBinding()]
param(
    [string]$Module = 'app_v2',
    [string]$File = 'strings.xml',
    [ValidateSet('text', 'json')]
    [string]$Format = 'text',
    [string]$OutFile
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot '..\quality\lib\android-string-liveness.ps1')

$repoRoot = Split-Path -Parent $PSScriptRoot | Split-Path -Parent

try {
    $result = Get-UnreferencedResourceNames -Module $Module -File $File -RepoRoot $repoRoot
}
catch {
    # Exit 2, not 1: "I could not look" and "I looked and found nothing" must not share a code, or a
    # caller reads a typo in -Module as a clean file.
    Write-Error "audit-unreferenced-strings: cannot verify - $($_.Exception.Message)" -ErrorAction Continue
    exit 2
}

$summary = "declared=$($result.DeclaredCount) referenced=$($result.ReferencedCount) scanned=$($result.ScannedFileCount) unreferenced=$($result.Entries.Count) module=$Module file=$File"

if ($Format -eq 'json') {
    $payload = [pscustomobject]@{
        module           = $Module
        file             = $File
        declaredCount    = $result.DeclaredCount
        referencedCount  = $result.ReferencedCount
        scannedFileCount = $result.ScannedFileCount
        unreferenced     = @($result.Entries | ForEach-Object { [pscustomobject]@{ name = $_.Name; kind = $_.Kind } })
    }
    $body = $payload | ConvertTo-Json -Depth 4
}
else {
    $lines = @($result.Entries | ForEach-Object { "{0}`t{1}" -f $_.Kind, $_.Name })
    $body = ($lines -join [Environment]::NewLine)
}

if ($OutFile) {
    $outDir = Split-Path -Parent $OutFile
    if ($outDir -and -not (Test-Path -LiteralPath $outDir)) { New-Item -ItemType Directory -Path $outDir -Force | Out-Null }
    Set-Content -LiteralPath $OutFile -Value $body -Encoding UTF8
    Write-Host "audit-unreferenced-strings: wrote $($result.Entries.Count) name(s) to $OutFile" -ForegroundColor Cyan
}
else {
    if ($body) { Write-Host $body }
}

# Printed in both modes and to the host even when -OutFile redirected the body: a run that reports
# nothing to the operator is indistinguishable from a run that did not happen.
Write-Host $summary -ForegroundColor Cyan
exit 0
