<#
.SYNOPSIS
    S1495 - OSS notice conformance gate.

.DESCRIPTION
    Asserts that the published third-party notice pages still say what the build
    files and the licence manifest say. Two independent findings:

      drift    a rendered artifact differs from what the generator produces now
      unlisted a shipping coordinate has no entry in the licence manifest

    Before S1495 nothing compared the notice pages to the dependency set, so the
    published page named 2 libraries out of 97 shipping coordinates and the one
    GPL component in the tree appeared on no list at all. Drift was silent because
    the page was hand-maintained and nothing read the build files.

    The gate does not re-implement the comparison: it runs
    scripts/docs/generate-oss-notices.ps1 -Check, whose refusal semantics are the
    contract, and adds the manifest-coverage assertion so a missing licence is
    reported as its own finding rather than as generic drift.

.PARAMETER Gate
    Exit non-zero on any finding. Without it the script reports and exits 0,
    which is the read-only mode used while correcting the manifest.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-oss-notices.ps1
    pwsh -NoProfile -File scripts/quality/assert-oss-notices.ps1 -Gate -Quiet

.NOTES
    Exit codes:
      0  no findings (or findings reported without -Gate)
      1  -Gate and at least one finding
      2  could not verify: generator, parser or manifest missing or unreadable
#>
[CmdletBinding()]
param(
    [string] $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path,
    [switch] $Gate,
    [switch] $Quiet
)

$ErrorActionPreference = 'Stop'

$generator = Join-Path $RepoRoot 'scripts/docs/generate-oss-notices.ps1'
$parser = Join-Path $RepoRoot 'scripts/docs/OssDependencyParser.ps1'
$manifestPath = Join-Path $RepoRoot 'scripts/docs/oss-licenses.psd1'

foreach ($required in @($generator, $parser, $manifestPath)) {
    if (-not (Test-Path -LiteralPath $required)) {
        Write-Error "assert-oss-notices: cannot verify - missing $required" -ErrorAction Continue
        exit 2
    }
}

$findings = [System.Collections.Generic.List[string]]::new()

# 1 - manifest coverage. Reported separately from drift because "a dependency was added and
# nobody gave it a licence" and "somebody hand-edited a generated page" need different fixes.
try {
    . $parser
    $licences = Import-PowerShellDataFile -LiteralPath $manifestPath
    $flavors = Get-OssFlavorNames -Root $RepoRoot

    $declared = @()
    $declared += Get-OssDependencies -GradleFile (Join-Path $RepoRoot 'app_v2/build.gradle.kts') -Module 'app_v2' -Flavors $flavors
    $declared += Get-OssDependencies -GradleFile (Join-Path $RepoRoot 'wear/build.gradle.kts') -Module 'wear' -Flavors $flavors

    $shipping = @($declared | Where-Object { $_.Shipping } | ForEach-Object { "$($_.Group):$($_.Artifact)" } | Sort-Object -Unique)
    foreach ($coordinate in $shipping) {
        if (-not $licences.ContainsKey($coordinate)) {
            $findings.Add("unlisted: $coordinate ships but has no entry in scripts/docs/oss-licenses.psd1")
        }
    }
} catch {
    Write-Error "assert-oss-notices: cannot verify - $($_.Exception.Message)" -ErrorAction Continue
    exit 2
}

# 2 - rendered artifacts match what the generator produces now.
$checkOutput = & pwsh -NoProfile -File $generator -Check -Quiet 2>&1
$checkCode = $LASTEXITCODE

if ($checkCode -eq 2) {
    Write-Error "assert-oss-notices: cannot verify - the generator refused: $(($checkOutput | Out-String).Trim())" -ErrorAction Continue
    exit 2
}
if ($checkCode -eq 1) {
    foreach ($line in ($checkOutput | Out-String).Split("`n")) {
        if ($line -match 'out of date:\s*(.+)$') {
            $findings.Add("drift: $($Matches[1].Trim()) differs from the generated content")
        }
    }
    if ($findings.Count -eq 0) {
        $findings.Add('drift: the generator reported drift without naming an artifact')
    }
}

if ($findings.Count -eq 0) {
    if (-not $Quiet) { Write-Host "assert-oss-notices: PASS ($($shipping.Count) shipping coordinates, notices current)." }
    exit 0
}

foreach ($finding in $findings) { Write-Host "assert-oss-notices: $finding" }
Write-Host 'assert-oss-notices: fix by adding the missing licence entries, then run scripts/docs/generate-oss-notices.ps1'

if ($Gate) {
    Write-Error "assert-oss-notices: FAIL ($($findings.Count) finding(s))." -ErrorAction Continue
    exit 1
}
exit 0
