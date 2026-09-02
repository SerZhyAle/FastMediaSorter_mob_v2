#requires -Version 7.0
<#
.SYNOPSIS
    Regression suite for scripts/utils/publish-artifact.ps1 (S2332).

.DESCRIPTION
    The defect being guarded: that script is the single delivery block 25 builders call, so a defect
    in it breaks every delivery path at once. The previous mass edit of the same block (S2326 phase
    03) was driven by a generated patch and shipped two defects that had to be found by reading
    diffs - one an else-branch rebind that fired the "7-Zip not found" warning exactly when 7-Zip WAS
    available. A suite that only exercises the happy path would have passed on it, so the skip paths
    are asserted as loudly as the delivery paths.

    Hermetic: both sinks are redirected under temp/ through -DriveDir / -CommanderDir, so no case can
    write to the real Google Drive or Total Commander folder.

.EXAMPLE
    pwsh -NoProfile -File scripts/utils/publish-artifact.tests/Run-Tests.ps1

.NOTES
    Exit codes:
      0 - all cases pass
      1 - at least one case failed
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..' '..')).Path
$script:publish = Join-Path $repoRoot 'scripts/utils/publish-artifact.ps1'
$script:sandbox = Join-Path $repoRoot 'temp/S2332/publish-artifact.tests'

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

function New-Case([string]$slug) {
    $root = Join-Path $script:sandbox $slug
    if (Test-Path -LiteralPath $root) { Remove-Item -LiteralPath $root -Recurse -Force }
    $src = Join-Path $root 'src'
    New-Item -ItemType Directory -Path $src -Force | Out-Null
    [pscustomobject]@{
        Root      = $root
        Src       = $src
        Drive     = Join-Path $root 'drive'
        Commander = Join-Path $root 'tc'
    }
}

function New-Artifact([object]$case, [string]$name, [int]$sizeKb = 4) {
    $p = Join-Path $case.Src $name
    $bytes = [byte[]]::new($sizeKb * 1024)
    [System.IO.File]::WriteAllBytes($p, $bytes)
    return $p
}

# The call operator, never `pwsh -File`: -File binds a comma-separated value as one string and never
# produces an array, so the two-artifact case would silently degrade into a missing-file failure.
function Invoke-Publish([hashtable]$params) {
    $out = & $script:publish @params 2>&1
    return [pscustomobject]@{ Exit = $LASTEXITCODE; Output = ($out | Out-String) }
}

function Get-ZipMembers([string]$zipPath) {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [System.IO.Compression.ZipFile]::OpenRead($zipPath)
    try { return @($zip.Entries | ForEach-Object { $_.FullName }) }
    finally { $zip.Dispose() }
}

Write-Host 'publish-artifact regression suite' -ForegroundColor Cyan

# --- Case 1: both sinks reachable ------------------------------------------------------------
$c = New-Case 'both-sinks'
$apk = New-Artifact $c 'App.apk'
$r = Invoke-Publish @{ Path = $apk; Name = 'Delivered.apk'; DriveDir = $c.Drive; CommanderDir = $c.Commander }
Assert-That 'both sinks - exit 0' ($r.Exit -eq 0) "exit $($r.Exit): $($r.Output)"
Assert-That 'both sinks - raw file on Drive' (Test-Path (Join-Path $c.Drive 'Delivered.apk')) 'raw copy missing'
Assert-That 'both sinks - ZIP on Drive' (Test-Path (Join-Path $c.Drive 'Delivered.zip')) 'zip missing'
Assert-That 'both sinks - file in Commander' (Test-Path (Join-Path $c.Commander 'Delivered.apk')) 'commander copy missing'

# The archive must carry bare member names. A relative destination used to leak its path segments
# into the archive, so a recipient unzipping got a directory tree instead of the artifact (S2332).
$members = Get-ZipMembers (Join-Path $c.Drive 'Delivered.zip')
Assert-That 'both sinks - ZIP member is a bare name' ($members -contains 'Delivered.apk') "members: $($members -join ', ')"

# --- Case 2: Commander unreachable, Drive still delivered -------------------------------------
$c = New-Case 'no-commander-sink'
$apk = New-Artifact $c 'App.apk'
$absent = Join-Path $c.Root 'nope'
$r = Invoke-Publish @{ Path = $apk; DriveDir = $c.Drive; CommanderDir = $absent }
Assert-That 'absent Commander dir - exit 0' ($r.Exit -eq 0) "exit $($r.Exit): $($r.Output)"
Assert-That 'absent Commander dir - Drive copy still landed' (Test-Path (Join-Path $c.Drive 'App.apk')) 'raw copy missing'
Assert-That 'absent Commander dir - created on demand' (Test-Path (Join-Path $absent 'App.apk')) 'commander copy missing'

# --- Case 3: -NoCommander skips the second sink entirely ---------------------------------------
$c = New-Case 'no-commander-switch'
$apk = New-Artifact $c 'App.apk'
$r = Invoke-Publish @{ Path = $apk; DriveDir = $c.Drive; CommanderDir = $c.Commander; NoCommander = $true }
Assert-That '-NoCommander - exit 0' ($r.Exit -eq 0) "exit $($r.Exit): $($r.Output)"
Assert-That '-NoCommander - Drive copy landed' (Test-Path (Join-Path $c.Drive 'App.apk')) 'raw copy missing'
Assert-That '-NoCommander - nothing in Commander' (-not (Test-Path (Join-Path $c.Commander 'App.apk'))) 'commander copy was made'

# --- Case 4: -NoZip delivers raw only ----------------------------------------------------------
$c = New-Case 'no-zip'
$apk = New-Artifact $c 'App.apk'
$r = Invoke-Publish @{ Path = $apk; DriveDir = $c.Drive; CommanderDir = $c.Commander; NoZip = $true }
Assert-That '-NoZip - exit 0' ($r.Exit -eq 0) "exit $($r.Exit): $($r.Output)"
Assert-That '-NoZip - raw file on Drive' (Test-Path (Join-Path $c.Drive 'App.apk')) 'raw copy missing'
Assert-That '-NoZip - no archive written' (-not (Test-Path (Join-Path $c.Drive 'App.zip'))) 'zip was created'
Assert-That '-NoZip - Commander copy still made' (Test-Path (Join-Path $c.Commander 'App.apk')) 'commander copy missing'

# --- Case 5: two artifacts, one archive, APK to Commander --------------------------------------
$c = New-Case 'aab-plus-apk'
$aab = New-Artifact $c 'Release.aab'
$apk = New-Artifact $c 'Release.apk'
$r = Invoke-Publish @{ Path = @($aab, $apk); CommanderPath = $apk; DriveDir = $c.Drive; CommanderDir = $c.Commander }
Assert-That 'AAB+APK - exit 0' ($r.Exit -eq 0) "exit $($r.Exit): $($r.Output)"
Assert-That 'AAB+APK - both raw files on Drive' `
    ((Test-Path (Join-Path $c.Drive 'Release.aab')) -and (Test-Path (Join-Path $c.Drive 'Release.apk'))) 'a raw copy is missing'
$members = Get-ZipMembers (Join-Path $c.Drive 'Release.zip')
Assert-That 'AAB+APK - one archive holds both' `
    (($members -contains 'Release.aab') -and ($members -contains 'Release.apk')) "members: $($members -join ', ')"
# The Commander folder is a sideload staging area, so the APK belongs there and the AAB does not.
Assert-That 'AAB+APK - Commander gets the APK' (Test-Path (Join-Path $c.Commander 'Release.apk')) 'apk missing from commander'
Assert-That 'AAB+APK - Commander does not get the AAB' (-not (Test-Path (Join-Path $c.Commander 'Release.aab'))) 'aab reached commander'

# --- Case 6: a missing input is a refusal, not a silent skip -----------------------------------
$c = New-Case 'missing-input'
$r = Invoke-Publish @{ Path = (Join-Path $c.Src 'never-built.apk'); DriveDir = $c.Drive; CommanderDir = $c.Commander }
Assert-That 'missing input - exit 1' ($r.Exit -eq 1) "exit $($r.Exit): $($r.Output)"
Assert-That 'missing input - nothing delivered' (-not (Test-Path (Join-Path $c.Drive 'never-built.apk'))) 'something was copied'

# --- Case 7: a missing -CommanderPath is a refusal ---------------------------------------------
$c = New-Case 'missing-commander-path'
$apk = New-Artifact $c 'App.apk'
$r = Invoke-Publish @{ Path = $apk; CommanderPath = (Join-Path $c.Src 'ghost.apk'); DriveDir = $c.Drive; CommanderDir = $c.Commander }
Assert-That 'missing -CommanderPath - exit 1' ($r.Exit -eq 1) "exit $($r.Exit): $($r.Output)"

Write-Host ''
Write-Host "passed: $script:pass   failed: $script:fail" -ForegroundColor $(if ($script:fail) { 'Red' } else { 'Green' })
if ($script:fail -gt 0) { exit 1 }
exit 0
