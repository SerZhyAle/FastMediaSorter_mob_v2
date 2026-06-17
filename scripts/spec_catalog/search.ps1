[CmdletBinding()]
param(
    [string] $Query,
    [string] $Status,
    [string] $Tag,
    [string] $Type,
    [switch] $IncludeArchived,
    [ValidateSet('table','json')]
    [string] $Format = 'table'
)

. (Join-Path $PSScriptRoot '_lib.ps1')

$records = Read-Catalog -IncludeArchived:$IncludeArchived

if ($Query) {
    $records = $records | Where-Object {
        $_.name -ilike "*$Query*" -or
        ($_.PSObject.Properties.Name -contains 'title' -and $_.title -ilike "*$Query*")
    }
}
if ($Status) {
    $records = $records | Where-Object { $_.status -eq $Status }
}
if ($Tag) {
    $records = $records | Where-Object {
        $_.PSObject.Properties.Name -contains 'tags' -and $_.tags -contains $Tag
    }
}
if ($Type) {
    $records = $records | Where-Object {
        $_.PSObject.Properties.Name -contains 'type' -and $_.type -eq $Type
    }
}

switch ($Format) {
    'json' {
        if (-not $records) { Write-Output '[]'; return }
        $records | ConvertTo-Json -Depth 5 -Compress
    }
    default {
        if (-not $records) { Write-Output '(no records)'; return }
        $records | Select-Object id, name, status, priority,
            @{ Name = 'title'; Expression = {
                if ($_.PSObject.Properties.Name -contains 'title') { $_.title } else { '' }
            }} | Format-Table -AutoSize
    }
}
