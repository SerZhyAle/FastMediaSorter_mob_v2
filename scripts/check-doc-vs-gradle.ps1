# check-doc-vs-gradle.ps1 - documentation-vs-Gradle drift checker (S0271).
#
# Compares declared pin versions in docs/TECH_STACK.md, dev/TECH_REQUIREMENTS.md,
# and CLAUDE.md against the canonical versions in Gradle sources. Drift surfaces
# as FAIL / INCONSISTENT / MISSING records on stdout with non-zero exit code.
#
# Usage:
#   pwsh -NoProfile -File scripts/check-doc-vs-gradle.ps1
#   pwsh -NoProfile -File scripts/check-doc-vs-gradle.ps1 -Pin agp
#   pwsh -NoProfile -File scripts/check-doc-vs-gradle.ps1 -Doc 'dev/TECH_REQUIREMENTS.md'
#   pwsh -NoProfile -File scripts/check-doc-vs-gradle.ps1 -VerboseOutput
#   pwsh -NoProfile -File scripts/check-doc-vs-gradle.ps1 -Color
#   pwsh -NoProfile -File scripts/check-doc-vs-gradle.ps1 -AsBootstrapWarning
#
# Channels (strategic §5.4):
#   - Manual operator run: default invocation.
#   - Bootstrap warning (S0268): -AsBootstrapWarning forces exit 0 after printing.
#   - PR-gate (open Research item §6.2): plain invocation, exit 1 blocks the merge.
#
# Exit codes (per DECISIONS.md D-5):
#   0   no FAIL + no INCONSISTENT + no MISSING
#   1   otherwise
#   With -AsBootstrapWarning: always 0.

[CmdletBinding()]
param(
    [string] $Doc,
    [string] $Pin,
    [switch] $VerboseOutput,
    [switch] $Color,
    [switch] $AsBootstrapWarning
)

$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$driftDir = Join-Path $PSScriptRoot 'doc-drift'

# Dot-source the four library modules into THIS script's scope so all functions
# resolve without polluting the caller session.
. (Join-Path $driftDir 'GradleParser.ps1')
. (Join-Path $driftDir 'DocParser.ps1')
. (Join-Path $driftDir 'Comparator.ps1')
. (Join-Path $driftDir 'Output.ps1')

$manifestPath = Join-Path $driftDir 'pins.psd1'
if (-not (Test-Path -LiteralPath $manifestPath)) {
    throw "Pin manifest not found at $manifestPath"
}
$manifest = Import-PowerShellDataFile -LiteralPath $manifestPath

# -Pin filter (apply before parsing to avoid wasted work)
if (-not [string]::IsNullOrEmpty($Pin)) {
    $manifest.Pins = @($manifest.Pins | Where-Object { $_.name -eq $Pin })
    if ($manifest.Pins.Count -eq 0) {
        Write-Output "SUMMARY | total: 0 | pass: 0 | fail: 0 | warn: 0 | skip: 0 | inconsistent: 0 | missing: 0"
        exit 0
    }
}

$gradle = Get-GradlePins -RepoRoot $repoRoot
$mentions = Get-DocMentions -Manifest $manifest -RepoRoot $repoRoot

# -Doc filter (post-parse)
if (-not [string]::IsNullOrEmpty($Doc)) {
    $mentions = @($mentions | Where-Object { $_.DocPath -eq $Doc })
}

$records = Compare-PinsToDocs -GradlePins $gradle -DocMentions $mentions
$lines = Format-DriftReport -Records $records -VerboseOutput:$VerboseOutput -Color:$Color

foreach ($line in $lines) { Write-Output $line }

# Exit code: count FAIL + INCONSISTENT + MISSING.
$failCount = @($records | Where-Object { $_.Status -in @('FAIL','INCONSISTENT','MISSING') }).Count

if ($AsBootstrapWarning) {
    exit 0
}
if ($failCount -eq 0) { exit 0 }
Write-Error ("check-doc-vs-gradle: {0} pin(s) FAIL/INCONSISTENT/MISSING - see the rows above and align the doc with build.gradle.kts." -f $failCount) -ErrorAction Continue
exit 1
