#requires -Version 7.0
<#
.SYNOPSIS
    Contract suite for scripts/ci/ensure-prebuilt-libs.ps1 (S3029).

.DESCRIPTION
    The check's whole value is the refusal it produces in a worktree nobody has prepared, and that
    state cannot be reached in the development checkout, where both AARs are already present - so
    every case here runs against a sandbox repository root with its own manifest. Nothing under the
    real app_v2/libs/ is read or written.

    Cases:
      - a manifest whose files are all present and non-empty exits 0;
      - comment lines, trailing comments and blank lines are ignored;
      - a zero-byte file counts as missing, not as present;
      - a missing file with -NoFetch exits 1 and names a fetch command;
      - an absent manifest exits 2, which is a different answer from "an AAR is missing";
      - a manifest that lists nothing exits 2 as well.

.NOTES
    Exit codes:
      0 - every case passed.
      1 - at least one case failed.
      2 - the fixture could not be prepared.
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") { "$env:ProgramFiles\PowerShell\7\pwsh.exe" } else { 'pwsh' }
$subject = Join-Path $repoRoot 'scripts/ci/ensure-prebuilt-libs.ps1'

$script:pass = 0
$script:fail = 0

function Assert-That {
    param([string] $Name, [bool] $Condition, [string] $Detail)
    if ($Condition) {
        $script:pass++
        Write-Host "  PASS  $Name" -ForegroundColor Green
    } else {
        $script:fail++
        Write-Host "  FAIL  $Name" -ForegroundColor Red
        if ($Detail) { Write-Host "        $Detail" -ForegroundColor DarkGray }
    }
}

function Invoke-Subject {
    param([string[]] $Arguments)
    $out = & $pwshExe -NoProfile -File $subject @Arguments 2>&1 | ForEach-Object { [string]$_ }
    return [pscustomobject]@{ Code = $LASTEXITCODE; Text = ($out -join "`n") }
}

$run = Get-Date -Format 'yyyyMMdd-HHmmss'
$sandbox = Join-Path $repoRoot "temp/S3029/ensure-prebuilt-tests/$run"
try { New-Item -ItemType Directory -Path (Join-Path $sandbox 'app_v2/libs') -Force | Out-Null }
catch { Write-Host "ensure-prebuilt-libs tests: fixture could not be prepared - $_" -ForegroundColor Red; exit 2 }

function New-Manifest {
    param([string] $Name, [string[]] $Lines)
    $path = Join-Path $sandbox $Name
    Set-Content -LiteralPath $path -Value $Lines -Encoding utf8
    return $path
}

function New-Aar {
    param([string] $Relative, [int] $Bytes = 16)
    $path = Join-Path $sandbox $Relative
    $dir = Split-Path -Parent $path
    if (-not (Test-Path -LiteralPath $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    [System.IO.File]::WriteAllBytes($path, (New-Object byte[] $Bytes))
    return $path
}

try {
    Write-Host 'Everything present'
    New-Aar -Relative 'app_v2/libs/one.aar' | Out-Null
    New-Aar -Relative 'app_v2/libs/two.aar' | Out-Null
    $m1 = New-Manifest -Name 'all-present.txt' -Lines @(
        '# a comment line the reader must skip',
        '',
        'app_v2/libs/one.aar',
        'app_v2/libs/two.aar   # trailing comment'
    )
    $r1 = Invoke-Subject @('-RepoRoot', $sandbox, '-ManifestPath', $m1)
    Assert-That 'a prepared tree exits 0' ($r1.Code -eq 0) "exit=$($r1.Code) $($r1.Text)"
    Assert-That 'both entries counted, comments and blanks ignored' ($r1.Text -match '2 prebuilt AAR\(s\) present') $r1.Text

    Write-Host 'A zero-byte file is missing, not present'
    New-Aar -Relative 'app_v2/libs/empty.aar' -Bytes 0 | Out-Null
    $m2 = New-Manifest -Name 'has-empty.txt' -Lines @('app_v2/libs/empty.aar')
    $r2 = Invoke-Subject @('-RepoRoot', $sandbox, '-ManifestPath', $m2, '-NoFetch')
    Assert-That 'a zero-byte AAR is refused' ($r2.Code -eq 1) "exit=$($r2.Code) $($r2.Text)"

    Write-Host 'Missing file, no fetch'
    $m3 = New-Manifest -Name 'missing.txt' -Lines @('app_v2/libs/absent.aar')
    $r3 = Invoke-Subject @('-RepoRoot', $sandbox, '-ManifestPath', $m3, '-NoFetch')
    Assert-That 'a missing AAR exits 1' ($r3.Code -eq 1) "exit=$($r3.Code) $($r3.Text)"
    Assert-That 'the refusal names the missing entry' ($r3.Text -match 'absent\.aar') $r3.Text
    Assert-That 'the refusal names a way to fetch it' ($r3.Text -match 'release download|GitHub CLI') $r3.Text

    Write-Host 'Manifest problems answer differently from AAR problems'
    $r4 = Invoke-Subject @('-RepoRoot', $sandbox, '-ManifestPath', (Join-Path $sandbox 'no-such-manifest.txt'))
    Assert-That 'an absent manifest exits 2' ($r4.Code -eq 2) "exit=$($r4.Code) $($r4.Text)"
    $m5 = New-Manifest -Name 'comments-only.txt' -Lines @('# nothing but a comment', '')
    $r5 = Invoke-Subject @('-RepoRoot', $sandbox, '-ManifestPath', $m5)
    Assert-That 'a manifest listing no AAR exits 2' ($r5.Code -eq 2) "exit=$($r5.Code) $($r5.Text)"

    Write-Host 'The real tree is untouched'
    $realLibs = Join-Path $repoRoot 'app_v2/libs'
    $sandboxed = $r1.Text + $r3.Text
    Assert-That 'no sandbox run reported a path under the real app_v2/libs' (-not ($sandboxed -match [regex]::Escape($realLibs))) $sandboxed
}
finally {
    Remove-Item -LiteralPath (Split-Path -Parent $sandbox) -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Host ''
Write-Host ("ensure-prebuilt-libs tests: {0} passed, {1} failed" -f $script:pass, $script:fail) -ForegroundColor $(if ($script:fail -eq 0) { 'Green' } else { 'Red' })
if ($script:fail -gt 0) { exit 1 }
exit 0
