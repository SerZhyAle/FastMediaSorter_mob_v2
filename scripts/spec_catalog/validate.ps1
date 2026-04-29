[CmdletBinding()]
param(
    [switch] $Strict
)

. (Join-Path $PSScriptRoot '_lib.ps1')

Set-StrictMode -Version Latest

$results = New-Object System.Collections.Generic.List[object]
$errCount  = 0
$warnCount = 0

function Add-Result {
    param([string] $Check, [string] $Level, [string] $Message)
    $results.Add([pscustomobject]@{ check = $Check; level = $Level; message = $Message })
    switch ($Level) {
        'FAIL' { $script:errCount++ }
        'WARN' { $script:warnCount++ }
    }
}

# 1. Schema (parse + required fields + enum)
try {
    $records = Read-Catalog
    foreach ($r in $records) { Assert-Record -Record $r }
    Add-Result 'Schema'         'OK' ('parsed {0} records' -f $records.Count)
} catch {
    Add-Result 'Schema'         'FAIL' $_.Exception.Message
    $records = @()
}

# 2. Uniqueness
$idGroups = $records | Group-Object -Property id | Where-Object { $_.Count -gt 1 }
if ($idGroups) {
    foreach ($g in $idGroups) {
        Add-Result 'Uniqueness' 'FAIL' ('duplicate id {0} ({1} times)' -f $g.Name, $g.Count)
    }
} else {
    Add-Result 'Uniqueness'     'OK' 'all ids unique'
}

# 3. Monotonicity (dense S0001..Smax, archived included)
if ($records.Count -gt 0) {
    $nums = $records | ForEach-Object {
        if ($_.id -match '^S(\d{4})$') { [int]$Matches[1] }
    } | Sort-Object
    $expected = 1
    $gaps = New-Object System.Collections.Generic.List[int]
    foreach ($n in $nums) {
        while ($expected -lt $n) { $gaps.Add($expected); $expected++ }
        $expected = $n + 1
    }
    if ($gaps.Count -gt 0) {
        Add-Result 'Monotonicity' 'WARN' ('id gaps: {0}' -f (($gaps | ForEach-Object { 'S{0:D4}' -f $_ }) -join ', '))
    } else {
        Add-Result 'Monotonicity' 'OK' ('dense S0001..S{0:D4}' -f ($nums[-1]))
    }
} else {
    Add-Result 'Monotonicity'   'OK' '(empty journal)'
}

# 4. Filesystem -> journal: every PLAN/S####_* file/folder has a record
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$planDir  = Join-Path $repoRoot 'PLAN'
$prefixed = Get-ChildItem -Path $planDir -Force | Where-Object { $_.Name -match '^S\d{4}_' }
$known = @{}
foreach ($r in $records) { $known[$r.id] = $true }
$orphans = New-Object System.Collections.Generic.List[string]
foreach ($item in $prefixed) {
    if ($item.Name -match '^(S\d{4})_') {
        $id = $Matches[1]
        if (-not $known.ContainsKey($id)) {
            $orphans.Add($item.Name)
        }
    }
}
if ($orphans.Count -gt 0) {
    Add-Result 'FS->Journal' 'FAIL' ('orphan filesystem entries (no journal record): {0}' -f ($orphans -join ', '))
} else {
    Add-Result 'FS->Journal' 'OK' ('all {0} prefixed entries have a record' -f $prefixed.Count)
}

# 5. Journal -> filesystem: every non-archived record has its file present
$missing = New-Object System.Collections.Generic.List[string]
foreach ($r in $records) {
    if ($r.status -eq 'Archived') { continue }
    $path = Join-Path $repoRoot $r.file
    if (-not (Test-Path -LiteralPath $path)) {
        $missing.Add(('{0} ({1})' -f $r.id, $r.file))
    }
}
if ($missing.Count -gt 0) {
    Add-Result 'Journal->FS' 'FAIL' ('missing files for: {0}' -f ($missing -join ', '))
} else {
    Add-Result 'Journal->FS' 'OK' 'every non-archived record points at an existing file'
}

# 6. Naming pattern on `file` (no `_spec_` segment after the id prefix)
$bad = New-Object System.Collections.Generic.List[string]
foreach ($r in $records) {
    $fp = ($r.file -replace '\\','/')
    if ($fp -notmatch '^PLAN/S\d{4}_' -or $fp -match '^PLAN/S\d{4}_spec_') {
        $bad.Add(('{0} -> {1}' -f $r.id, $r.file))
    }
}
if ($bad.Count -gt 0) {
    Add-Result 'Naming' 'FAIL' ('bad file pattern (must be PLAN/S####_<slug>, no _spec_): {0}' -f ($bad -join '; '))
} else {
    Add-Result 'Naming' 'OK' 'every file path matches PLAN/S####_<slug> (no _spec_ segment)'
}

# 7. Priority range
$badPri = New-Object System.Collections.Generic.List[string]
foreach ($r in $records) {
    if (-not ($r.PSObject.Properties.Name -contains 'priority')) {
        $badPri.Add(('{0}: missing priority' -f $r.id))
        continue
    }
    $p = [int]$r.priority
    if ($p -lt 0 -or $p -gt 100) {
        $badPri.Add(('{0}: priority={1}' -f $r.id, $p))
    }
}
if ($badPri.Count -gt 0) {
    Add-Result 'Priority' 'FAIL' ('out-of-range priority: {0}' -f ($badPri -join '; '))
} else {
    Add-Result 'Priority' 'OK' 'every record has priority in 0..100'
}

# 8. Stale specs (informational)
$stale = New-Object System.Collections.Generic.List[string]
foreach ($r in $records) {
    $days = Get-DaysSinceUpdated -Updated $r.updated
    $level = Get-StaleLevel -Status $r.status -Days $days
    if ($level -eq 'alert') { $stale.Add(('{0} ({1}d, {2})' -f $r.id, $days, $r.status)) }
}
if ($stale.Count -gt 0) {
    Add-Result 'Staleness' 'WARN' ('specs not updated > 30d: {0}' -f ($stale -join ', '))
} else {
    Add-Result 'Staleness' 'OK' 'no active spec older than 30d'
}

# Render report
$results | ForEach-Object {
    $color = switch ($_.level) { 'OK' { 'Green' }; 'WARN' { 'Yellow' }; default { 'Red' } }
    Write-Host ('  [{0}] {1,-14} {2}' -f $_.level, $_.check, $_.message) -ForegroundColor $color
}

Write-Host ''
Write-Host ('Summary: {0} OK, {1} WARN, {2} FAIL' -f
    (($results | Where-Object level -eq 'OK').Count),
    $warnCount,
    $errCount
) -ForegroundColor Cyan

if ($errCount -gt 0)               { exit 2 }
if ($Strict -and $warnCount -gt 0) { exit 1 }
exit 0
