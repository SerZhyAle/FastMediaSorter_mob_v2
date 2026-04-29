[CmdletBinding()]
param(
    [string] $Id,
    [ValidateSet('Draft','Approved','Tactical','In Progress',
        'Implemented','Verified','Partial','Broken',
        'BlockByOtherTask','BlockNeedUserTest','BlockQuestions','BlockExternal',
        'Archived')]
    [string] $Status,
    [string] $Name,
    [int]    $MinPriority = -1,
    [ValidateSet('table','json','tsv')]
    [string] $Format = 'table'
)

. (Join-Path $PSScriptRoot '_lib.ps1')

$records = Read-Catalog

if ($Id) {
    if ($Id -notmatch '^S\d{4}$') { throw "Invalid -Id '$Id' (must match S####)." }
    $records = $records | Where-Object { $_.id -eq $Id }
}
if ($Status) { $records = $records | Where-Object { $_.status -eq $Status } }
if ($Name)   { $records = $records | Where-Object { $_.name -like $Name } }
if ($MinPriority -ge 0) { $records = $records | Where-Object { [int]$_.priority -ge $MinPriority } }

switch ($Format) {
    'json' {
        if (-not $records) { Write-Output '[]'; return }
        $records | ConvertTo-Json -Depth 5 -Compress
    }
    'tsv' {
        $headers = 'id','name','status','priority','tier','file','created','updated','days'
        Write-Output ($headers -join "`t")
        foreach ($r in $records) {
            $tier = if ($r.PSObject.Properties.Name -contains 'tier') { $r.tier } else { '' }
            $days = Get-DaysSinceUpdated -Updated $r.updated
            $row = @($r.id, $r.name, $r.status, $r.priority, $tier, $r.file, $r.created, $r.updated, $days)
            Write-Output ($row -join "`t")
        }
    }
    default {
        if (-not $records) { Write-Output '(no records)'; return }
        $records `
            | Select-Object id, name, status, priority, tier, file, created, updated, `
                @{ Name = 'days'; Expression = { Get-DaysSinceUpdated -Updated $_.updated } } `
            | Format-Table -AutoSize
    }
}
