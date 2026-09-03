#requires -Version 7.0
<#
.SYNOPSIS
    Contract suite for scripts/utils/dev-monitor-writer.ps1 and scripts/utils/dev-monitor-html.ps1
    (S2406): the page shell, the data script and the detached writer's lifecycle.

.DESCRIPTION
    Runs against the live repository root (the snapshot is whatever the machine is doing) with
    every artifact under a fixture -OutDir beneath temp/S2406/, so a running owner's writer under
    temp/monitor/ is neither read nor touched.

    Pinned (strategic section 11 items 1, 4, 5, 7 and 9):
      - -Once writes exactly index.html and snapshot.js, no staging file left behind;
      - the data script is `window.__devMonitor(<json>);`, schema 1, with the writer block;
      - the shell is UTF-8, self-contained (no http/https/<link>), English, carries the loader for
        snapshot.js and the `writer silent` state, and has no animation, transition or keyframes;
      - start with -NoBrowser launches a detached writer whose pid file names a live process;
      - a second start prints `already running` and leaves the pid unchanged;
      - the tick counter grows across three seconds;
      - -Stop ends the process within five seconds, removes the pid file, and the last snapshot says
        `stopped`;
      - nothing new appears at the top level of temp/ (every artifact stays under -OutDir);
      - -Status exits 0 before and after.

    Exit codes:
      0 - every case passed.
      1 - at least one case failed.
      2 - the fixture could not be prepared.
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..' '..')).Path
$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") { "$env:ProgramFiles\PowerShell\7\pwsh.exe" } else { 'pwsh' }
$writer = Join-Path $repoRoot 'scripts/utils/dev-monitor-writer.ps1'

$script:pass = 0
$script:fail = 0
function Assert-That([string]$name, [bool]$condition, [string]$detail = '') {
    if ($condition) { $script:pass++; Write-Host "  PASS  $name" -ForegroundColor Green }
    else { $script:fail++; Write-Host "  FAIL  $name`n        $detail" -ForegroundColor Red }
}
function Invoke-Writer([string[]]$argv) {
    $out = & $pwshExe -NoProfile -File $writer @argv 2>&1 | Out-String
    return [pscustomobject]@{ Code = $LASTEXITCODE; Text = $out }
}
function Read-Data([string]$path) {
    $raw = [IO.File]::ReadAllText($path)
    if (-not $raw.StartsWith('window.__devMonitor(')) { return $null }
    $json = $raw.Substring('window.__devMonitor('.Length).TrimEnd("`n", "`r", ' ')
    if (-not $json.EndsWith(');')) { return $null }
    try { return ($json.Substring(0, $json.Length - 2) | ConvertFrom-Json -ErrorAction Stop) } catch { return $null }
}
function Get-TempTopLevel {
    return @(Get-ChildItem -LiteralPath (Join-Path $repoRoot 'temp') -Force -ErrorAction SilentlyContinue | ForEach-Object { $_.Name } | Sort-Object)
}

$run = Get-Date -Format 'yyyyMMdd-HHmmss'
$fixture = Join-Path $repoRoot "temp/S2406/writer-tests/$run/monitor"
try { New-Item -ItemType Directory -Path $fixture -Force | Out-Null }
catch { Write-Host "dev-monitor-writer tests: fixture could not be prepared - $_" -ForegroundColor Red; exit 2 }

$tempBefore = Get-TempTopLevel
try {
    Write-Host 'Once'
    $once = Invoke-Writer @('-Once', '-OutDir', $fixture, '-NoBrowser')
    $files = @(Get-ChildItem -LiteralPath $fixture -File | ForEach-Object { $_.Name } | Sort-Object)
    Assert-That '-Once exits 0 and writes exactly index.html and snapshot.js' ($once.Code -eq 0 -and ($files -join ',') -eq 'index.html,snapshot.js') "exit=$($once.Code) files=$($files -join ',') $($once.Text)"
    $data = Read-Data (Join-Path $fixture 'snapshot.js')
    Assert-That 'data script is window.__devMonitor(<json>) with schema 1 and the writer block' ($null -ne $data -and $data.schema -eq 1 -and $data.writer.state -eq 'once' -and $data.writer.intervalSeconds -eq 3) ''
    $shell = [IO.File]::ReadAllText((Join-Path $fixture 'index.html'))
    Assert-That 'shell carries the charset, the loader and the silent state' ($shell -match '<meta charset="utf-8">' -and $shell -match 'snapshot\.js\?t=' -and $shell -match 'writer silent' -and $shell -match 'writer stopped') ''
    $forbidden = @('animation', 'transition', '@keyframes', 'http://', 'https://', '<link', '<img')
    $found = @($forbidden | Where-Object { $shell -match [regex]::Escape($_) })
    Assert-That 'shell has no animation, transition, keyframes or external reference' ($found.Count -eq 0) ($found -join ',')
    $missingSections = @(@('running', 'ticket leases', 'locks', 'agents', 'next up', 'chat', 'findings', 'finished', 'stop') | Where-Object { $shell -notmatch ('<h2>' + [regex]::Escape($_)) })
    Assert-That 'shell is English-labelled and lists every section' ($missingSections.Count -eq 0) ($missingSections -join ',')
    # Read the stamp off the raw text: ConvertFrom-Json turns an ISO string into a DateTime, and the
    # page compares strings.
    $stampMatch = [regex]::Match([IO.File]::ReadAllText((Join-Path $fixture 'snapshot.js')), '"shellStamp":"([^"]+)"')
    Assert-That 'shell and data script carry the same stamp' ($stampMatch.Success -and $shell -match ("SHELL_STAMP = '" + [regex]::Escape($stampMatch.Groups[1].Value) + "'")) "stamp=$($stampMatch.Value)"
    $bytes = [IO.File]::ReadAllBytes((Join-Path $fixture 'index.html'))
    Assert-That 'shell is UTF-8 without BOM' (-not ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF)) ''

    Write-Host 'Status before start'
    $st0 = Invoke-Writer @('-Status', '-OutDir', $fixture)
    Assert-That '-Status exits 0 with no writer' ($st0.Code -eq 0 -and $st0.Text -match 'not running') $st0.Text

    Write-Host 'Start, second start, ticks'
    $start = Invoke-Writer @('-NoBrowser', '-IntervalSeconds', '1', '-OutDir', $fixture)
    $pidInfo = $null
    try { $pidInfo = Get-Content -LiteralPath (Join-Path $fixture 'writer.pid') -Raw | ConvertFrom-Json } catch { $pidInfo = $null }
    $alive = $false
    if ($pidInfo) { try { $alive = $null -ne (Get-Process -Id ([int]$pidInfo.pid) -ErrorAction Stop) } catch { $alive = $false } }
    Assert-That 'start exits 0, pid file names a live process' ($start.Code -eq 0 -and $alive) "exit=$($start.Code) $($start.Text)"
    $again = Invoke-Writer @('-NoBrowser', '-OutDir', $fixture)
    $pidAgain = $null
    try { $pidAgain = (Get-Content -LiteralPath (Join-Path $fixture 'writer.pid') -Raw | ConvertFrom-Json).pid } catch { $pidAgain = $null }
    Assert-That 'second start says already running and keeps the pid' ($again.Code -eq 0 -and $again.Text -match 'already running' -and $pidInfo -and $pidAgain -eq $pidInfo.pid) "exit=$($again.Code) $($again.Text)"
    $d1 = Read-Data (Join-Path $fixture 'snapshot.js')
    Start-Sleep -Seconds 3
    $d2 = Read-Data (Join-Path $fixture 'snapshot.js')
    Assert-That 'tick grows across three seconds, state running' ($null -ne $d1 -and $null -ne $d2 -and $d2.writer.tick -gt $d1.writer.tick -and $d2.writer.state -eq 'running') "t1=$($d1.writer.tick) t2=$($d2.writer.tick)"
    Assert-That 'no staging file is left between ticks' (@(Get-ChildItem -LiteralPath $fixture -Filter '*.tmp-*' -File).Count -eq 0) ''
    $st1 = Invoke-Writer @('-Status', '-OutDir', $fixture)
    Assert-That '-Status reports the running writer' ($st1.Code -eq 0 -and $st1.Text -match 'running, pid') $st1.Text

    Write-Host 'Stop'
    $stop = Invoke-Writer @('-Stop', '-IntervalSeconds', '1', '-OutDir', $fixture)
    $gone = $false
    $deadline = (Get-Date).AddSeconds(5)
    while ((Get-Date) -lt $deadline) {
        try { $null = Get-Process -Id ([int]$pidInfo.pid) -ErrorAction Stop; Start-Sleep -Milliseconds 200 } catch { $gone = $true; break }
    }
    $d3 = Read-Data (Join-Path $fixture 'snapshot.js')
    Assert-That '-Stop exits 0, the process is gone within five seconds' ($stop.Code -eq 0 -and $gone) "exit=$($stop.Code) $($stop.Text)"
    Assert-That 'pid file removed, last snapshot says stopped' (-not (Test-Path (Join-Path $fixture 'writer.pid')) -and $null -ne $d3 -and $d3.writer.state -eq 'stopped') "state=$($d3.writer.state)"
    $stopAgain = Invoke-Writer @('-Stop', '-OutDir', $fixture)
    Assert-That '-Stop with nothing running exits 0' ($stopAgain.Code -eq 0) $stopAgain.Text

    Write-Host 'Read-only outside -OutDir'
    $tempAfter = Get-TempTopLevel
    $new = @($tempAfter | Where-Object { $tempBefore -notcontains $_ -and $_ -ne 'S2406' })
    Assert-That 'nothing new at the top level of temp/' ($new.Count -eq 0) ($new -join ',')
}
finally {
    # A writer the suite failed to stop must not outlive it.
    try {
        $left = $null
        if (Test-Path (Join-Path $fixture 'writer.pid')) { $left = (Get-Content -LiteralPath (Join-Path $fixture 'writer.pid') -Raw | ConvertFrom-Json).pid }
        if ($left) { Stop-Process -Id ([int]$left) -Force -ErrorAction SilentlyContinue }
    } catch { }
    Remove-Item -LiteralPath (Split-Path -Parent $fixture) -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Host ''
Write-Host ("dev-monitor-writer tests: {0} passed, {1} failed" -f $script:pass, $script:fail) -ForegroundColor $(if ($script:fail -eq 0) { 'Green' } else { 'Red' })
if ($script:fail -gt 0) { exit 1 }
exit 0
