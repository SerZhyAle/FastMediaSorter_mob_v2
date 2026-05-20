[CmdletBinding()]
param(
    [Parameter(Mandatory = $false)]
    [string] $Ticket
)

# Agent Continuity Layer (S0268) - Bootstrap packet.
# Prints seven informational blocks at the start of a significant session.
# See: PLAN/S0268_agent_continuity_layer.md  §5.1
#      scripts/agent_continuity/README.md    Pillars / Tactical decisions

$ErrorActionPreference = 'Stop'

function Write-Block([string]$Header, [string[]]$Lines) {
    Write-Output ""
    Write-Output $Header
    foreach ($l in $Lines) { Write-Output $l }
}

# ----- Locate repo root (script lives at scripts/agent_continuity/) -----
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
Push-Location $repoRoot
try {

    # ----- Block 1: branch -----
    $branch = (git branch --show-current 2>$null).Trim()
    if ([string]::IsNullOrEmpty($branch)) { $branch = '(detached)' }
    $role = 'feature'
    if ($branch -eq 'main') { $role = 'release-stable' }
    elseif ($branch -match '^DEBUG-v\d{3}$') { $role = 'debug-current' }
    Write-Block '## branch' @("$branch ($role)")

    # ----- Block 2: dirty-tree -----
    $porcelain = git status --porcelain 2>$null
    $dirtyCount = if ($null -eq $porcelain) { 0 } else { ($porcelain | Measure-Object).Count }
    $dirtyState = if ($dirtyCount -le 30) { 'steady' } elseif ($dirtyCount -le 125) { 'active' } else { 'surge' }
    Write-Block '## dirty-tree' @("count=$dirtyCount  state=$dirtyState")

    # ----- Block 3: active-ticket -----
    $journalPath = Join-Path $repoRoot 'PLAN\spec-catalog.jsonl'
    $activeStatuses = @('Draft', 'Approved', 'Tactical', 'In Progress', 'BlockNeedUserTest')
    if ($Ticket) {
        if ($Ticket -notmatch '^S\d{4}$') {
            Write-Output ""
            Write-Output "ERROR: -Ticket must match S####, got: $Ticket"
            exit 1
        }
        $hit = $null
        if (Test-Path $journalPath) {
            foreach ($line in Get-Content $journalPath) {
                if ([string]::IsNullOrWhiteSpace($line)) { continue }
                try { $rec = $line | ConvertFrom-Json } catch { continue }
                if ($rec.id -eq $Ticket) { $hit = $rec; break }
            }
        }
        if ($hit) {
            Write-Block '## active-ticket' @("$($hit.id) - $($hit.name) [$($hit.status)]")
        } else {
            Write-Block '## active-ticket' @("$Ticket - (not found in journal)")
        }
    } else {
        $best = $null
        if (Test-Path $journalPath) {
            foreach ($line in Get-Content $journalPath) {
                if ([string]::IsNullOrWhiteSpace($line)) { continue }
                try { $rec = $line | ConvertFrom-Json } catch { continue }
                if ($activeStatuses -notcontains $rec.status) { continue }
                if ($null -eq $best -or $rec.updated -gt $best.updated) { $best = $rec }
            }
        }
        if ($best) {
            Write-Block '## active-ticket' @("$($best.id) - $($best.name) [$($best.status)]  (heuristic: most recent active)")
        } else {
            Write-Block '## active-ticket' @('none')
        }
    }

    # ----- Block 4: modules -----
    $modulesOut = New-Object System.Collections.Generic.List[string]
    try {
        $diff = git diff --name-only HEAD~10..HEAD 2>$null
    } catch { $diff = @() }
    if ($null -eq $diff) { $diff = @() }
    $buckets = @{}
    $known = @('app_v2/', 'wear/', 'scripts/', 'dev/', 'docs/', 'PLAN/')
    foreach ($f in $diff) {
        if ([string]::IsNullOrWhiteSpace($f)) { continue }
        $bk = 'other'
        foreach ($p in $known) { if ($f.StartsWith($p)) { $bk = $p; break } }
        if (-not $buckets.ContainsKey($bk)) { $buckets[$bk] = 0 }
        $buckets[$bk]++
    }
    if ($buckets.Count -eq 0) {
        $modulesOut.Add('(no changes in last 10 commits)')
    } else {
        foreach ($kv in ($buckets.GetEnumerator() | Sort-Object Value -Descending)) {
            $modulesOut.Add(("{0,-12} {1}" -f $kv.Key, $kv.Value))
        }
    }
    Write-Block '## modules' $modulesOut.ToArray()

    # ----- Block 5: prompt-routing -----
    $routeOut = New-Object System.Collections.Generic.List[string]
    $routeOut.Add('See CLAUDE.md "Mandatory Skills" table - route by situation column.')
    $dominant = 'other'
    if ($buckets.Count -gt 0) {
        $dominant = ($buckets.GetEnumerator() | Sort-Object Value -Descending | Select-Object -First 1).Key
    }
    $candidates = switch ($dominant) {
        'PLAN/'    { @('/spec', '/spec-tech', '/spec-dev', '/spec-check', '/spec-update') }
        'app_v2/'  { @('/spec-dev', '/build', '/spec-check', '/quick', '/log-reader') }
        'wear/'    { @('/spec-dev', '/build', '/spec-check', '/quick', '/log-reader') }
        'scripts/' { @('/quick', '/spec', '/spec-tech', '/spec-dev', '/build') }
        'docs/'    { @('/quick', '/spec', '/spec-tech', '/spec-dev', '/spec-update') }
        'dev/'     { @('/quick', '/git', '/spec', '/spec-tech', '/catalog') }
        default    { @('/quick', '/spec', '/spec-all', '/build', '/log-reader') }
    }
    foreach ($c in $candidates) { $routeOut.Add(" - $c") }
    Write-Block '## prompt-routing' $routeOut.ToArray()

    # ----- Block 6: docs-vs-gradle -----
    $gradlePath = Join-Path $repoRoot 'app_v2\build.gradle.kts'
    $techStackPath = Join-Path $repoRoot 'docs\TECH_STACK.md'
    $dvgOut = New-Object System.Collections.Generic.List[string]
    if (-not (Test-Path $gradlePath) -or -not (Test-Path $techStackPath)) {
        $dvgOut.Add('SKIP - app_v2/build.gradle.kts or docs/TECH_STACK.md missing')
    } else {
        $gradleText = Get-Content $gradlePath -Raw
        $docsText   = Get-Content $techStackPath -Raw
        $mismatches = @()
        if ($gradleText -match 'compileSdk\s*=\s*(\d+)') {
            $g = $Matches[1]
            if ($docsText -match 'compileSdk\s*[`]?(\d+)') {
                if ($g -ne $Matches[1]) { $mismatches += "MISMATCH: compileSdk docs=$($Matches[1]) gradle=$g" }
            }
        }
        if ($gradleText -match 'minSdk\s*=\s*(\d+)') {
            $g = $Matches[1]
            if ($docsText -match 'minSdk\s*[`]?(\d+)\s*\(standard\)?') {
                if ($g -ne $Matches[1]) { $mismatches += "MISMATCH: minSdk docs=$($Matches[1]) gradle=$g" }
            }
        }
        if ($mismatches.Count -eq 0) { $dvgOut.Add('OK - in sync') }
        else { foreach ($m in $mismatches) { $dvgOut.Add($m) } }
    }
    Write-Block '## docs-vs-gradle' $dvgOut.ToArray()

    # ----- Block 7: ux-volatility -----
    $funcLog = Join-Path $repoRoot 'dev\FUNCTIONALITY.log'
    $uxOut = New-Object System.Collections.Generic.List[string]
    if (Test-Path $funcLog) {
        $matches = Get-Content $funcLog | Where-Object { $_ -match 'CHANGE|FIX' } | Select-Object -Last 10
        if ($matches.Count -eq 0) {
            $uxOut.Add('(no CHANGE/FIX entries in dev/FUNCTIONALITY.log)')
        } else {
            foreach ($m in $matches) { $uxOut.Add($m) }
            if ($matches.Count -lt 10) { $uxOut.Add("(only $($matches.Count) matching entries available)") }
        }
    } else {
        $uxOut.Add('(dev/FUNCTIONALITY.log not present)')
    }
    Write-Block '## ux-volatility' $uxOut.ToArray()

    exit 0
}
finally {
    Pop-Location
}
