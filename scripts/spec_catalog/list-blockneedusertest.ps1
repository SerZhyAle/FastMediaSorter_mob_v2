#requires -Version 7.0
<#
.SYNOPSIS
    Read-only snapshot of the BlockNeedUserTest backlog.

.DESCRIPTION
    Lists every ticket currently in status BlockNeedUserTest from
    PLAN/spec-catalog.jsonl, sorted by priority (desc) then id, with file path and
    last-updated timestamp. Prints an `expected: 0 | actual: N` count so the
    backlog size is visible before and after a /spec-sweep pass. Never mutates the
    journal.

.PARAMETER MinPriority
    Optional: only list tickets with priority >= this value.

.EXAMPLE
    pwsh -NoProfile -File scripts/spec_catalog/list-blockneedusertest.ps1
    pwsh -NoProfile -File scripts/spec_catalog/list-blockneedusertest.ps1 -MinPriority 90
#>
[CmdletBinding()]
param(
    [int]$MinPriority = 0
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$catalog = Join-Path $repoRoot 'PLAN/spec-catalog.jsonl'
if (-not (Test-Path $catalog)) { Write-Error "Not found: $catalog"; exit 2 }

$rows = [System.Collections.Generic.List[object]]::new()
foreach ($line in Get-Content -LiteralPath $catalog) {
    if ([string]::IsNullOrWhiteSpace($line)) { continue }
    try { $rec = $line | ConvertFrom-Json } catch { continue }
    if ($rec.status -ne 'BlockNeedUserTest') { continue }
    if ([int]$rec.priority -lt $MinPriority) { continue }
    $rows.Add([pscustomobject]@{
        Priority = [int]$rec.priority
        Id       = $rec.id
        Updated  = $rec.updated
        File     = $rec.file
    })
}

$sorted = $rows | Sort-Object -Property @{Expression = 'Priority'; Descending = $true }, @{Expression = 'Id'; Descending = $false }
Write-Host ("{0,-4}  {1,-6}  {2,-16}  {3}" -f 'PRI', 'ID', 'UPDATED', 'FILE')
foreach ($r in $sorted) {
    Write-Host ("{0,-4}  {1,-6}  {2,-16}  {3}" -f $r.Priority, $r.Id, $r.Updated, $r.File)
}
Write-Host ("list-blockneedusertest: expected: 0 | actual: {0} (MinPriority {1})" -f $rows.Count, $MinPriority)
