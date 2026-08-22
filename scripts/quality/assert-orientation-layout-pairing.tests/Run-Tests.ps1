# Run-Tests.ps1 (S1549) - regression suite for the orientation/layout pairing gate.
#
# A gate that has only ever been seen green proves nothing. These four cases decide whether
# it is trustworthy, and none can be demonstrated against the live tree without breaking it:
# an absorber WITH a landscape layout is a finding; the same absorber pinned by
# android:screenOrientation is not; the same absorber listed in the exception file is not;
# and an absorber with no landscape layout is not.
#
# Each case runs against a synthetic repository built under a temp dir and removed in a
# finally block. The gate resolves its repo root from its own $PSScriptRoot, so the suite
# copies the gate into a sandbox 'scripts/quality/' and points it at a sandbox 'app_v2/src'.
# Nothing here writes into the real app_v2/src.
#
# Usage:  pwsh -NoProfile -File scripts/quality/assert-orientation-layout-pairing.tests/Run-Tests.ps1
#
# Exit codes:
#   0   all cases pass.
#   1   at least one case failed.

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..' '..')).Path
$gateSource = Join-Path $repoRoot 'scripts/quality/assert-orientation-layout-pairing.ps1'
$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") {
    "$env:ProgramFiles\PowerShell\7\pwsh.exe"
}
else { 'pwsh' }

$script:pass = 0
$script:fail = 0

function Assert-That([string]$name, [bool]$ok, [string]$detail) {
    if ($ok) {
        Write-Host "  PASS  $name" -ForegroundColor Green
        $script:pass++
    }
    else {
        Write-Host "  FAIL  $name -> $detail" -ForegroundColor Red
        $script:fail++
    }
}

# Build a sandbox repo: scripts/quality holds a copy of the gate plus the case's exception
# file, app_v2/src holds the case's manifest and resource tree. Returns the sandbox root.
function New-Sandbox {
    param(
        [string]$ManifestBody,
        [string[]]$LandscapeLayouts,
        [string]$ExceptionFileText
    )
    $sandbox = Join-Path ([System.IO.Path]::GetTempPath()) ("s1549-" + [System.IO.Path]::GetRandomFileName())
    $qualityDir = Join-Path $sandbox 'scripts/quality'
    $mainRes = Join-Path $sandbox 'app_v2/src/main/res'
    $landDir = Join-Path $mainRes 'layout-land'
    $manifestDir = Join-Path $sandbox 'app_v2/src/main'
    New-Item -ItemType Directory -Force -Path $qualityDir, $landDir, $manifestDir | Out-Null

    Copy-Item -LiteralPath $gateSource -Destination (Join-Path $qualityDir 'assert-orientation-layout-pairing.ps1')
    # The exception file always exists in a sandbox so a case controls it precisely (empty = none).
    Set-Content -LiteralPath (Join-Path $qualityDir 'orientation-layout-pairing-exceptions.txt') -Value $ExceptionFileText -Encoding utf8NoBOM

    foreach ($layout in $LandscapeLayouts) {
        Set-Content -LiteralPath (Join-Path $landDir "$layout.xml") -Value '<LinearLayout />' -Encoding utf8NoBOM
    }
    Set-Content -LiteralPath (Join-Path $manifestDir 'AndroidManifest.xml') -Value $ManifestBody -Encoding utf8NoBOM
    return $sandbox
}

function Invoke-SandboxedGate {
    param([string]$Sandbox, [switch]$Gate, [switch]$List)
    $script = Join-Path $Sandbox 'scripts/quality/assert-orientation-layout-pairing.ps1'
    $gateArgs = @('-NoProfile', '-File', $script)
    if ($Gate) { $gateArgs += '-Gate' }
    if ($List) { $gateArgs += '-List' }
    $out = & $pwshExe @gateArgs 2>&1 | Out-String
    return [PSCustomObject]@{ Output = $out; ExitCode = $LASTEXITCODE }
}

$manifestAbsorber = @'
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application>
        <activity
            android:name=".ui.calculator.CalculatorActivity"
            android:configChanges="orientation|screenSize|keyboardHidden"
            android:exported="false" />
    </application>
</manifest>
'@

$manifestPinned = $manifestAbsorber -replace 'android:exported="false"', "android:exported=""false""`n            android:screenOrientation=""portrait"""

try {
    # Case 1: absorber WITH a landscape layout is a finding.
    $sb = New-Sandbox -ManifestBody $manifestAbsorber -LandscapeLayouts @('activity_calculator') -ExceptionFileText ''
    try {
        $r = Invoke-SandboxedGate -Sandbox $sb -List
        Assert-That 'absorber-with-landscape is a finding' ($r.ExitCode -eq 0 -and $r.Output -match 'CalculatorActivity') "exit=$($r.ExitCode) out=$($r.Output.Trim())"
        $g = Invoke-SandboxedGate -Sandbox $sb -Gate
        Assert-That 'absorber-with-landscape fails -Gate (exit 1)' ($g.ExitCode -eq 1) "exit=$($g.ExitCode)"
    } finally { Remove-Item -LiteralPath $sb -Recurse -Force -ErrorAction SilentlyContinue }

    # Case 2: the same absorber pinned by screenOrientation is NOT a finding.
    $sb = New-Sandbox -ManifestBody $manifestPinned -LandscapeLayouts @('activity_calculator') -ExceptionFileText ''
    try {
        $r = Invoke-SandboxedGate -Sandbox $sb -List
        Assert-That 'screenOrientation-pinned absorber is not a finding' ($r.ExitCode -eq 0 -and $r.Output -notmatch 'CalculatorActivity  \[') "exit=$($r.ExitCode) out=$($r.Output.Trim())"
        $g = Invoke-SandboxedGate -Sandbox $sb -Gate
        Assert-That 'screenOrientation-pinned passes -Gate (exit 0)' ($g.ExitCode -eq 0) "exit=$($g.ExitCode)"
    } finally { Remove-Item -LiteralPath $sb -Recurse -Force -ErrorAction SilentlyContinue }

    # Case 3: the same absorber listed in the exception file is NOT a finding.
    $sb = New-Sandbox -ManifestBody $manifestAbsorber -LandscapeLayouts @('activity_calculator') -ExceptionFileText 'CalculatorActivity  # re-applies its landscape layout in onConfigurationChanged'
    try {
        $r = Invoke-SandboxedGate -Sandbox $sb -List
        Assert-That 'exception-listed absorber is not a finding' ($r.ExitCode -eq 0 -and $r.Output -notmatch 'CalculatorActivity  \[') "exit=$($r.ExitCode) out=$($r.Output.Trim())"
    } finally { Remove-Item -LiteralPath $sb -Recurse -Force -ErrorAction SilentlyContinue }

    # Case 4: an absorber with NO landscape layout is NOT a finding.
    $sb = New-Sandbox -ManifestBody $manifestAbsorber -LandscapeLayouts @() -ExceptionFileText ''
    try {
        $r = Invoke-SandboxedGate -Sandbox $sb -List
        Assert-That 'absorber-without-landscape is not a finding' ($r.ExitCode -eq 0 -and $r.Output -notmatch 'CalculatorActivity  \[') "exit=$($r.ExitCode) out=$($r.Output.Trim())"
    } finally { Remove-Item -LiteralPath $sb -Recurse -Force -ErrorAction SilentlyContinue }
}
finally {
    Write-Host ""
    Write-Host ("Result: {0} passed, {1} failed" -f $script:pass, $script:fail) -ForegroundColor ($script:fail -gt 0 ? 'Red' : 'Green')
}

if ($script:fail -gt 0) { exit 1 }
exit 0
