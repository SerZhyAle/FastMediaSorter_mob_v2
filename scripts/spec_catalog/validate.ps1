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

# 1. Schema (parse + required fields + enum) - validate the FULL catalog
#    (active + archive) so uniqueness/monotonicity see every id.
try {
    $records = Read-Catalog -IncludeArchived
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
    # S1534: a gap whose id sits in the burned registry was removed on purpose and is accounted for.
    # Reporting it would bury a genuinely lost record among 21 known holes, which is the only thing
    # this check is here to catch.
    $burned = @{}
    foreach ($b in (Read-BurnedIds)) { $burned[$b.id] = $true }
    $unexplained = @($gaps | Where-Object { -not $burned.ContainsKey(('S{0:D4}' -f $_)) })
    $accounted = $gaps.Count - $unexplained.Count
    if ($unexplained.Count -gt 0) {
        Add-Result 'Monotonicity' 'WARN' ('id gaps: {0}' -f (($unexplained | ForEach-Object { 'S{0:D4}' -f $_ }) -join ', '))
    } elseif ($accounted -gt 0) {
        Add-Result 'Monotonicity' 'OK' ('dense S0001..S{0:D4} ({1} burned id(s) accounted for)' -f $nums[-1], $accounted)
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

# 5. Journal -> filesystem: every record should point at an existing file.
#    - Archived            : skipped - soft-deleted, file is expected to live under PLAN/archive/.
#    - Verified / Implemented : strategic spec already consumed by /spec-tech & /spec-dev; a
#                            missing .md is recoverable hygiene debt, not a blocker -> WARN.
#    - any other (in-flight) status : the spec is being worked on right now and MUST exist -> FAIL.
$workDoneStatuses = @('Verified', 'Implemented')
$missingActive = New-Object System.Collections.Generic.List[string]   # in-flight -> FAIL
$missingDone   = New-Object System.Collections.Generic.List[string]   # work-done -> WARN
foreach ($r in $records) {
    if ($r.status -eq 'Archived') { continue }
    $path = Join-Path $repoRoot $r.file
    if (-not (Test-Path -LiteralPath $path)) {
        if ($workDoneStatuses -contains $r.status) {
            $missingDone.Add(('{0} [{1}] ({2})' -f $r.id, $r.status, $r.file))
        } else {
            $missingActive.Add(('{0} [{1}] ({2})' -f $r.id, $r.status, $r.file))
        }
    }
}
if ($missingActive.Count -gt 0) {
    Add-Result 'Journal->FS' 'FAIL' ('missing files for in-flight specs: {0}' -f ($missingActive -join ', '))
}
if ($missingDone.Count -gt 0) {
    Add-Result 'Journal->FS' 'WARN' ('Verified/Implemented specs with missing .md (hygiene debt - restore or archive): {0}' -f ($missingDone -join ', '))
}
if ($missingActive.Count -eq 0 -and $missingDone.Count -eq 0) {
    Add-Result 'Journal->FS' 'OK' 'every non-archived record points at an existing file'
}

# 6. Naming pattern on `file` (no `_spec_` segment after the id prefix)
$bad = New-Object System.Collections.Generic.List[string]
foreach ($r in $records) {
    $fp = ($r.file -replace '\\','/')
    # Reuse the shared pattern rather than restating it: this check carried its own copy
    # and silently disagreed with Assert-Record when PLAN/archive/ was introduced (S1620) -
    # the mutators accepted a path the validator then called invalid.
    if ($fp -notmatch $script:FilePattern -or $fp -match '^PLAN/(archive/)?S\d{4}_spec_') {
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

# 9. Archive split invariant: no Archived row in the active journal, no
#    non-Archived row in the archive journal.
try {
    $activeOnly  = Read-JsonlFile -Path (Get-CatalogPath)
    $archiveOnly = Read-JsonlFile -Path (Get-ArchivePath)
    $stray    = @($activeOnly  | Where-Object { $_.status -eq 'Archived' })
    $misfiled = @($archiveOnly | Where-Object { $_.status -ne 'Archived' })
    if ($stray.Count -gt 0) {
        Add-Result 'ArchiveSplit' 'FAIL' ('Archived records in active journal: {0}' -f (($stray | ForEach-Object { $_.id }) -join ', '))
    }
    if ($misfiled.Count -gt 0) {
        Add-Result 'ArchiveSplit' 'FAIL' ('non-Archived records in archive journal: {0}' -f (($misfiled | ForEach-Object { $_.id }) -join ', '))
    }
    if ($stray.Count -eq 0 -and $misfiled.Count -eq 0) {
        Add-Result 'ArchiveSplit' 'OK' ('split clean: {0} active, {1} archived' -f $activeOnly.Count, $archiveOnly.Count)
    }
} catch {
    Add-Result 'ArchiveSplit' 'FAIL' $_.Exception.Message
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

if ($errCount -gt 0) {
    Write-Error ("spec-catalog validate: {0} FAIL check(s) - fix the [FAIL] rows above before mutating the catalog." -f $errCount) -ErrorAction Continue
    exit 2
}
if ($Strict -and $warnCount -gt 0) {
    Write-Error ("spec-catalog validate: -Strict and {0} WARN check(s) - fix the [WARN] rows above or drop -Strict." -f $warnCount) -ErrorAction Continue
    exit 1
}
exit 0
