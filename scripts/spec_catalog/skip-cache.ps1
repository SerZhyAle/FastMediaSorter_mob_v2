# skip-cache.ps1 - Persistent skip cache for /spec-next between sessions
#
# Problem this solves:
#   /spec-next session 1 skips S0240 (tier-5 epic), S0242 (owner gate), S0245 (vr child).
#   Session 2 starts at 12:55, top-3 candidates are still S0240/S0242/S0245.
#   Without a persistent cache, the same skip questions get asked every session.
#
# Storage: temp/spec-next-skip-cache.json
# Schema (one record per skipped Sxxxx):
#   {
#     "S0240": { "reason": "tier-5-epic", "skipped_at": "2026-05-18T13:24:00", "expires": "2026-05-25T13:24:00" },
#     "S0242": { "reason": "owner-gate",  "skipped_at": "...", "expires": "..." }
#   }
#
# Default TTL: 7 days. Expired entries are pruned on read.
# Manual reset: -Reset clears the file.
#
# Usage:
#   skip-cache.ps1 -Action add    -Id Sxxxx -Reason "tier-5-epic" [-Ttl 7]
#   skip-cache.ps1 -Action remove -Id Sxxxx
#   skip-cache.ps1 -Action list                                # prints active skips as JSON
#   skip-cache.ps1 -Action check  -Id Sxxxx                   # exit 0 if active skip, 1 otherwise (also prints reason)
#   skip-cache.ps1 -Action reset                              # clear all entries
#
# Exit codes (S1070):
#   0 - action done (for -Action check: an active skip exists).
#   1 - substantive answer "no" (-Action check: no active skip for this id).
#   2 - bad arguments: -Id missing or malformed, -Reason missing for 'add'.
#       Distinct from 1 on purpose - a typo'd id must not read as "not skipped".

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('add', 'remove', 'list', 'check', 'reset')]
    [string]$Action,

    [string]$Id,
    [string]$Reason = '',
    [int]$Ttl = 7
)

$ErrorActionPreference = 'Stop'

$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$tempDir = Join-Path $root 'temp'
if (-not (Test-Path $tempDir)) {
    New-Item -ItemType Directory -Path $tempDir | Out-Null
}
$cachePath = Join-Path $tempDir 'spec-next-skip-cache.json'

function Read-Cache {
    if (-not (Test-Path $cachePath)) {
        return @{}
    }
    try {
        $raw = Get-Content -Path $cachePath -Raw
        if (-not $raw -or $raw.Trim() -eq '') { return @{} }
        $parsed = $raw | ConvertFrom-Json
        $hash = @{}
        foreach ($prop in $parsed.PSObject.Properties) {
            $hash[$prop.Name] = $prop.Value
        }
        return $hash
    }
    catch {
        Write-Warning "Cache file malformed, treating as empty: $_"
        return @{}
    }
}

function Write-Cache([hashtable]$cache) {
    if ($cache.Count -eq 0) {
        # Write empty object so subsequent reads parse cleanly.
        Set-Content -Path $cachePath -Value '{}' -Encoding utf8NoBOM
        return
    }
    $obj = [PSCustomObject]@{}
    foreach ($key in ($cache.Keys | Sort-Object)) {
        Add-Member -InputObject $obj -MemberType NoteProperty -Name $key -Value $cache[$key]
    }
    $json = $obj | ConvertTo-Json -Depth 5
    Set-Content -Path $cachePath -Value $json -Encoding utf8NoBOM
}

function Prune-Expired([hashtable]$cache) {
    $now = Get-Date
    $live = @{}
    foreach ($key in $cache.Keys) {
        $entry = $cache[$key]
        if (-not $entry.expires) { $live[$key] = $entry; continue }
        try {
            $exp = [DateTime]::Parse($entry.expires)
            if ($exp -gt $now) { $live[$key] = $entry }
        }
        catch {
            # Bad expiry → keep, conservative
            $live[$key] = $entry
        }
    }
    return $live
}

if ($Action -ne 'list' -and $Action -ne 'reset') {
    if (-not $Id) {
        Write-Error "-Id is required for action '$Action'" -ErrorAction Continue
        exit 2
    }
    if ($Id -notmatch '^S\d{4}$') {
        Write-Error "Invalid -Id '$Id' (must match S####)" -ErrorAction Continue
        exit 2
    }
}

$cache = Read-Cache
$cache = Prune-Expired $cache

switch ($Action) {
    'add' {
        if (-not $Reason) {
            Write-Error "-Reason is required for action 'add'" -ErrorAction Continue
            exit 2
        }
        $now = Get-Date
        $expires = $now.AddDays($Ttl)
        $cache[$Id] = [PSCustomObject]@{
            reason     = $Reason
            skipped_at = $now.ToString('s')
            expires    = $expires.ToString('s')
        }
        Write-Cache $cache
        Write-Host "skip-cache: added $Id (reason=$Reason, ttl=${Ttl}d)" -ForegroundColor DarkGray
    }
    'remove' {
        if ($cache.ContainsKey($Id)) {
            $cache.Remove($Id) | Out-Null
            Write-Cache $cache
            Write-Host "skip-cache: removed $Id" -ForegroundColor DarkGray
        }
        else {
            Write-Host "skip-cache: $Id not in cache" -ForegroundColor DarkGray
        }
    }
    'list' {
        # Always emit JSON for machine consumption.
        Write-Cache $cache  # prune side effect persists
        if ($cache.Count -eq 0) { Write-Output '{}'; exit 0 }
        $obj = [PSCustomObject]@{}
        foreach ($key in ($cache.Keys | Sort-Object)) {
            Add-Member -InputObject $obj -MemberType NoteProperty -Name $key -Value $cache[$key]
        }
        $obj | ConvertTo-Json -Depth 5 -Compress
    }
    'check' {
        Write-Cache $cache
        if ($cache.ContainsKey($Id)) {
            $entry = $cache[$Id]
            Write-Output "$Id|$($entry.reason)|$($entry.expires)"
            exit 0
        }
        exit 1
    }
    'reset' {
        Write-Cache @{}
        Write-Host "skip-cache: reset (0 entries)" -ForegroundColor DarkGray
    }
}

exit 0
