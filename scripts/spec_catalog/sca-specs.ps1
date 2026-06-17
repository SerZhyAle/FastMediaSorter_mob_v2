[CmdletBinding()]
param(
    [switch] $All,
    [int]    $MinPriority = -1
)

. (Join-Path $PSScriptRoot '_lib.ps1')

Set-StrictMode -Version Latest

$excludedDefault = @('Verified', 'Archived')
# -All surfaces archived too (matches pre-split behaviour); the default view
# excludes Archived anyway, so the active journal alone suffices there.
$catalog = Read-Catalog -IncludeArchived:$All
$records = if ($All) { @($catalog) } else { @($catalog | Where-Object { $excludedDefault -notcontains $_.status }) }
if ($MinPriority -ge 0) {
    $records = @($records | Where-Object { [int]$_.priority -ge $MinPriority })
}

# Sort: priority desc (more urgent first), then days desc (most stale first), then id asc.
$decorated = foreach ($r in $records) {
    $days = Get-DaysSinceUpdated -Updated $r.updated
    $level = Get-StaleLevel -Status $r.status -Days $days
    [pscustomobject]@{
        record = $r
        days   = $days
        level  = $level
    }
}
$sorted = @($decorated | Sort-Object `
        -Property @{ Expression = { [int]$_.record.priority }; Descending = $true }, `
    @{ Expression = { $_.days }; Descending = $true }, `
    @{ Expression = { $_.record.id }; Descending = $false })

if ($sorted.Count -eq 0) {
    Write-Host 'No matching specs.' -ForegroundColor Green
    exit 0
}

function Format-Created {
    param([string]$DateStr)
    if (-not $DateStr) { return '   ?' }
    # Strip year: "2026-04-27" → "04-27"
    if ($DateStr -match '^\d{4}-(\d{2}-\d{2})') { return $Matches[1] }
    return $DateStr
}

function Format-Updated {
    param([string]$DateStr)
    if (-not $DateStr) { return '          ?' }
    # Strip year: "2026-04-28 01:32" → "04-28 01:32"; "2026-04-27" → "04-27"
    if ($DateStr -match '^\d{4}-(\d{2}-\d{2} \d{2}:\d{2})') { return $Matches[1] }
    if ($DateStr -match '^\d{4}-(\d{2}-\d{2})') { return $Matches[1] }
    return $DateStr
}

function Get-StatusColor {
    param([string]$Status)
    switch ($Status) {
        'Draft' { 'Gray' }
        'Approved' { 'Yellow' }
        'Tactical' { 'Cyan' }
        'In Progress' { 'Blue' }
        'Implemented' { 'Green' }
        'Verified' { 'DarkGreen' }
        'Partial' { 'Magenta' }
        'Broken' { 'Red' }
        'BlockByOtherTask' { 'DarkYellow' }
        'BlockNeedUserTest' { 'DarkCyan' }
        'BlockQuestions' { 'DarkMagenta' }
        'BlockExternal' { 'DarkGray' }
        'Archived' { 'DarkGray' }
        default { 'White' }
    }
}

function Get-StaleMarker {
    param([string]$Level)
    switch ($Level) {
        'alert' { '!!' }
        'warn' { '!' }
        default { '' }
    }
}

$fmt = '{0,-6} {1,-7} {2,-34} {3,-18} {4,5} {5,11} {6,-2} {7}'
$hdr = $fmt -f 'ID', 'Pri/T', 'Name', 'Status', 'Crtd', 'Updated', 'S', 'File'
Write-Host $hdr -ForegroundColor Cyan
Write-Host ('-' * 105) -ForegroundColor DarkGray

$stalePending = 0
foreach ($e in $sorted) {
    $r = $e.record
    # Combine priority and tier into one token: "80/T3" or "50"
    $priTier = if ($r.PSObject.Properties.Name -contains 'tier' -and "$($r.tier)" -ne '') {
        "$($r.priority)/T$($r.tier)"
    }
    else {
        "$($r.priority)"
    }
    $stMark = Get-StaleMarker $e.level
    $color = Get-StatusColor $r.status
    if ($e.level -eq 'alert') { $color = 'Red'; $stalePending++ }
    elseif ($e.level -eq 'warn') { if ($color -ne 'Red') { $color = 'Yellow' }; $stalePending++ }

    $line = $fmt -f $r.id, $priTier, $r.name, $r.status, (Format-Created $r.created), (Format-Updated $r.updated), $stMark, $r.file
    Write-Host $line -ForegroundColor $color
}

Write-Host ''
Write-Host ('{0} spec(s); {1} stale (>= {2}d).' -f $sorted.Count, $stalePending, 14) -ForegroundColor Cyan
if ($stalePending -gt 0) {
    Write-Host '  S column: ! >= 14d  ·  !! >= 30d  → consider /spec-update <Sxxxx>' -ForegroundColor DarkGray
}
Write-Host '  Filters: -All (include Verified/Archived)  ·  -MinPriority N' -ForegroundColor DarkGray
