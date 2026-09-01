# Run-Tests.ps1 (S2326) - regression suite for scripts/utils/project-paths.ps1, the resolver every
# repository script dot-sources instead of writing a drive-letter path.
#
# Three of its properties are behavioural claims that reading the source cannot settle:
#   * the marker walk stops at the FIRST directory carrying the triple, so a tree nested inside
#     another tree resolves to its own root and not to the outer one (strategic S2326 section 7);
#   * a tree with no marker triple raises rather than returning a wrong answer;
#   * an unreachable artifact sink returns $null and never throws, which is what lets phases 02-04
#     skip a copy instead of failing a build (strategic S2326 ADR-3).
#
# Hermetic except for the two cases that deliberately read the live tree: every synthesized tree is
# built under temp/S2326/ and removed at the end. Nothing outside temp/ is written.
#
# Usage:  pwsh -NoProfile -File scripts/utils/project-paths.tests/Run-Tests.ps1
#
# Exit codes:
#   0   all cases pass.
#   1   at least one case failed.

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..' '..')).Path
$module = Join-Path $repoRoot 'scripts/utils/project-paths.ps1'
if (-not (Test-Path -LiteralPath $module -PathType Leaf)) {
    Write-Error "project-paths tests: module not found at '$module' - the harness resolved the repo root as '$repoRoot'." -ErrorAction Continue
    exit 1
}

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

# Runs one expression in a FRESH pwsh so the module's per-process caches start empty. A cached
# tool path would make an override set by the test invisible, which is correct behaviour and
# exactly why each case needs its own process.
function Invoke-InFreshSession([string]$Body) {
    $script = Join-Path $script:sandbox ("case_{0}.ps1" -f [guid]::NewGuid().ToString('N'))
    # Stop mode in the child so a failed dot-source exits non-zero instead of printing an error
    # and letting the case read as a pass on empty output - the first run of this suite scored
    # three false PASSes exactly that way.
    $header = "`$ErrorActionPreference = 'Stop'`n. '$module'`n"
    Set-Content -LiteralPath $script -Value ($header + $Body) -Encoding utf8
    $out = & pwsh -NoProfile -File $script 2>&1
    return [pscustomobject]@{ Output = ($out -join "`n"); ExitCode = $LASTEXITCODE }
}

$script:sandbox = Join-Path $repoRoot 'temp/S2326/tests'
if (Test-Path -LiteralPath $script:sandbox) { Remove-Item -LiteralPath $script:sandbox -Recurse -Force }
New-Item -ItemType Directory -Path $script:sandbox -Force | Out-Null

# Trees that must live outside the repository, because a directory inside it always has a real
# project root above it. Removed in the same finally block as the sandbox.
$script:outside = [System.Collections.Generic.List[string]]::new()

try {
    Write-Host 'A: root resolution against the live tree' -ForegroundColor Yellow
    $a1 = Invoke-InFreshSession 'Get-ProjectRoot'
    Assert-That 'A1 root from the module directory is the repo root' `
        ($a1.Output.Trim() -eq $repoRoot) "got '$($a1.Output.Trim())', expected '$repoRoot'"

    $a2 = Invoke-InFreshSession "Get-ProjectRoot -From '$repoRoot\scripts\quality\lib'"
    Assert-That 'A2 root from a nested directory is the same root' `
        ($a2.Output.Trim() -eq $repoRoot) "got '$($a2.Output.Trim())'"

    Write-Host 'B: a tree with no marker triple raises' -ForegroundColor Yellow
    # Outside the repository on purpose. The first version of this case built the barren tree
    # under temp/ and failed: the walk climbed out of it and found the real root above, which is
    # the resolver behaving correctly. A "no root anywhere above" case is only reachable from a
    # directory that is not itself inside the project.
    $barrenRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("fms-s2326-{0}" -f [guid]::NewGuid().ToString('N'))
    $script:outside.Add($barrenRoot)
    $barren = Join-Path $barrenRoot 'deep/deeper'
    New-Item -ItemType Directory -Path $barren -Force | Out-Null
    # One of the three markers only: a partial match must not satisfy the walk either.
    Set-Content -LiteralPath (Join-Path $barrenRoot 'a.ps1') -Value '# decoy' -Encoding utf8
    $b = Invoke-InFreshSession "try { Get-ProjectRoot -From '$barren' | Out-Null; 'NO-THROW' } catch { 'THREW: ' + `$_.Exception.Message }"
    Assert-That 'B1 a barren tree outside the repo raises rather than answering' `
        ($b.Output -like '*THREW*') "got '$($b.Output)'"
    Assert-That 'B2 the message names the marker triple that was missing' `
        ($b.Output -like '*settings.gradle.kts*' -and $b.Output -like '*CLAUDE.md*') "got '$($b.Output)'"

    Write-Host 'C: sibling resolution' -ForegroundColor Yellow
    $c = Invoke-InFreshSession "Get-SiblingPath -Name 'FastMediaSorter_release'"
    $expectedParent = Split-Path -Parent $repoRoot
    Assert-That 'C1 sibling sits beside the root, not inside it' `
        ((Split-Path -Parent $c.Output.Trim()) -eq $expectedParent) "got '$($c.Output.Trim())', parent expected '$expectedParent'"
    Assert-That 'C2 sibling leaf is the requested name' `
        ((Split-Path -Leaf $c.Output.Trim()) -eq 'FastMediaSorter_release') "got '$($c.Output.Trim())'"

    Write-Host 'D: tool override wins over discovery' -ForegroundColor Yellow
    $fakeAdb = Join-Path $script:sandbox 'fake-adb.exe'
    New-Item -ItemType File -Path $fakeAdb -Force | Out-Null
    $d1 = Invoke-InFreshSession "`$env:FMS_ADB = '$fakeAdb'; Get-ToolPath -Tool Adb -Quiet"
    Assert-That 'D1 FMS_ADB is honoured ahead of PATH and the probe list' `
        ($d1.Output.Trim() -eq $fakeAdb) "got '$($d1.Output.Trim())', expected '$fakeAdb'"

    $d2 = Invoke-InFreshSession "`$env:FMS_ADB = '$script:sandbox\does-not-exist.exe'; try { Get-ToolPath -Tool Adb -Quiet | Out-Null; 'NO-THROW' } catch { 'THREW' }"
    Assert-That 'D2 an override pointing at nothing raises instead of falling back silently' `
        ($d2.Output -like '*THREW*') "got '$($d2.Output)'"

    $d3 = Invoke-InFreshSession "try { Get-ToolPath -Tool Nope -Quiet | Out-Null } catch { `$_.Exception.Message }"
    Assert-That 'D3 an unknown tool names the known set' `
        ($d3.Output -like '*Adb*' -and $d3.Output -like '*SevenZip*') "got '$($d3.Output)'"

    Write-Host 'E: an unreachable sink returns null and never throws' -ForegroundColor Yellow
    $e1 = Invoke-InFreshSession "`$env:FMS_SINK_DRIVE = '$script:sandbox\no-such-sink'; `$v = Get-ArtifactSink -Kind Drive -Quiet; if (`$null -eq `$v) { 'NULL' } else { 'GOT:' + `$v }"
    Assert-That 'E1 Get-ArtifactSink returns $null for an absent directory' `
        ($e1.Output.Trim() -eq 'NULL') "got '$($e1.Output.Trim())'"
    Assert-That 'E2 the absent-sink case exits 0 - no throw reached the caller' `
        ($e1.ExitCode -eq 0) "exit code $($e1.ExitCode)"

    $e3 = Invoke-InFreshSession "`$env:FMS_SINK_DRIVE = '$script:sandbox'; Get-ArtifactSink -Kind Drive -Quiet"
    Assert-That 'E3 an existing override directory is returned' `
        ($e3.Output.Trim() -eq (Resolve-Path -LiteralPath $script:sandbox).Path) "got '$($e3.Output.Trim())'"
}
finally {
    Remove-Item -LiteralPath $script:sandbox -Recurse -Force -ErrorAction SilentlyContinue
    foreach ($o in $script:outside) { Remove-Item -LiteralPath $o -Recurse -Force -ErrorAction SilentlyContinue }
    Write-Host 'sandbox removed' -ForegroundColor DarkGray
}

Write-Host ''
if ($script:fail -eq 0) {
    Write-Host "project-paths tests: $script:pass passed" -ForegroundColor Green
    exit 0
}
Write-Host "project-paths tests: $script:pass passed, $script:fail FAILED" -ForegroundColor Red
exit 1
