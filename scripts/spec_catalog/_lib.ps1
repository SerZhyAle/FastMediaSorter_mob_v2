# Shared helpers for spec_catalog scripts.
# Compatible with PowerShell 5.1 and 7+.

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$libDir = $PSScriptRoot
if (-not $libDir) { $libDir = Split-Path -Parent $MyInvocation.MyCommand.Path }
$repoRoot = (Resolve-Path (Join-Path $libDir '..\..')).Path
$script:RepoRoot = $repoRoot
$script:CatalogPath = Join-Path $repoRoot 'PLAN\spec-catalog.jsonl'

$script:RequiredFields = @('id', 'name', 'status', 'priority', 'file', 'created', 'updated')
$script:StatusEnum = @(
    'Draft', 'Approved', 'Tactical', 'In Progress',
    'Implemented', 'Verified', 'Partial', 'Broken',
    'BlockByOtherTask', 'BlockNeedUserTest', 'BlockQuestions', 'BlockExternal',
    'Archived'
)
$script:IdPattern        = '^S\d{4}$'
$script:FilePattern      = '^PLAN/S\d{4}_(?!spec_)'
$script:PriorityMin      = 0
$script:PriorityMax      = 100
$script:PriorityDefault  = 50
$script:StaleWarnDays    = 14
$script:StaleAlertDays   = 30

function Get-CatalogPath {
    return $script:CatalogPath
}

function Get-Now {
    return (Get-Date -Format 'yyyy-MM-dd HH:mm')
}

function Get-Today {
    return (Get-Date -Format 'yyyy-MM-dd')
}

function Read-Catalog {
    if (-not (Test-Path $script:CatalogPath)) {
        return ,@()
    }
    $raw = Get-Content -LiteralPath $script:CatalogPath -Encoding UTF8 -ErrorAction Stop
    if (-not $raw) { return ,@() }
    $records = New-Object System.Collections.Generic.List[object]
    $lineNo = 0
    foreach ($line in @($raw)) {
        $lineNo++
        $trim = "$line".Trim()
        if (-not $trim) { continue }
        try {
            $records.Add(($trim | ConvertFrom-Json))
        } catch {
            throw "Catalog parse error at line ${lineNo}: $($_.Exception.Message)"
        }
    }
    $sorted = [object[]]@($records | Sort-Object -Property id)
    return ,$sorted
}

function Assert-Record {
    param([Parameter(Mandatory)] $Record)
    foreach ($f in $script:RequiredFields) {
        if (-not ($Record.PSObject.Properties.Name -contains $f)) {
            throw "Record missing required field '$f'."
        }
        if ($null -eq $Record.$f -or "$($Record.$f)" -eq '') {
            throw "Record field '$f' is empty."
        }
    }
    if ($Record.id -notmatch $script:IdPattern) {
        throw "Invalid id '$($Record.id)' - must match $script:IdPattern."
    }
    if ($script:StatusEnum -notcontains $Record.status) {
        throw "Invalid status '$($Record.status)'. Allowed: $($script:StatusEnum -join ', ')."
    }
    $pri = [int]$Record.priority
    if ($pri -lt $script:PriorityMin -or $pri -gt $script:PriorityMax) {
        throw "Invalid priority '$($Record.priority)' - must be in $script:PriorityMin..$script:PriorityMax."
    }
    $fileNorm = ($Record.file -replace '\\', '/')
    if ($fileNorm -notmatch $script:FilePattern) {
        throw "Invalid file path '$($Record.file)' - must match $script:FilePattern (no '_spec_' segment)."
    }
    if ($fileNorm -match '\.\.') {
        throw "Invalid file path '$($Record.file)' - must not contain '..' segments."
    }
}

function Write-Catalog {
    param([Parameter(Mandatory)][object[]] $Records)
    $sorted = @($Records | Sort-Object -Property id)
    foreach ($r in $sorted) { Assert-Record -Record $r }
    # Build one compact JSON per record. Use JavaScriptSerializer-style output via ConvertTo-Json -Compress.
    $lines = New-Object System.Collections.Generic.List[string]
    foreach ($r in $sorted) {
        # Re-shape to a fixed key order for stable diffs.
        $ordered = [ordered]@{
            id       = [string]$r.id
            name     = [string]$r.name
            status   = [string]$r.status
            priority = [int]$r.priority
        }
        if ($r.PSObject.Properties.Name -contains 'tier' -and $null -ne $r.tier -and "$($r.tier)" -ne '') {
            $ordered['tier'] = [int]$r.tier
        }
        $ordered['file']    = [string]$r.file
        $ordered['created'] = [string]$r.created
        $ordered['updated'] = [string]$r.updated
        $fixedKeys = @('id','name','status','priority','tier','file','created','updated')
        foreach ($prop in ($r.PSObject.Properties | Sort-Object Name)) {
            if ($fixedKeys -notcontains $prop.Name) {
                $ordered[$prop.Name] = $prop.Value
            }
        }
        $json = ($ordered | ConvertTo-Json -Compress -Depth 5)
        $lines.Add($json)
    }
    $payload = ($lines -join "`n")
    if ($payload.Length -gt 0) { $payload += "`n" }
    # Atomic write: temp file + Move-Item -Force.
    $tmp = "$($script:CatalogPath).tmp"
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($tmp, $payload, $utf8NoBom)
    Move-Item -LiteralPath $tmp -Destination $script:CatalogPath -Force
}

function New-CatalogId {
    $records = Read-Catalog
    $max = 0
    foreach ($r in $records) {
        if ($r.id -match '^S(\d{4})$') {
            $n = [int]$Matches[1]
            if ($n -gt $max) { $max = $n }
        }
    }
    $next = $max + 1
    if ($next -gt 9999) { throw "Id space exhausted (S9999 reached)." }
    return ('S{0:D4}' -f $next)
}

function Find-Record {
    param([Parameter(Mandatory)][string] $Id)
    $records = Read-Catalog
    foreach ($r in $records) { if ($r.id -eq $Id) { return $r } }
    return $null
}

function Resolve-SpecPath {
    # Resolve a record's `file` (repo-relative, e.g. 'PLAN/S0290_*.md') or an
    # already-absolute path to an absolute filesystem path.
    param([Parameter(Mandatory)][string] $PathRef)
    $p = $PathRef -replace '/', '\'
    if ([System.IO.Path]::IsPathRooted($p)) { return $p }
    return (Join-Path $script:RepoRoot $p)
}

function Sync-SpecHeaderStatus {
    # Mirror a status change (and optional human note) into the FIRST
    # '**Status:**' header block of a spec .md so the owner can read both
    # the status and its reason at a glance. Fail-soft: missing file or
    # absent header returns $false without throwing - the journal write is
    # never rolled back by a header problem. Only the first **Status:** match
    # is rewritten; deeper ones (ADR / Proposal blocks) keep their own values.
    #
    # StatusNote semantics:
    #   $null (omitted) - preserve any existing '**Status note:**' line as-is.
    #   non-empty string - upsert '**Status note:** <note>' right after **Status:**.
    #   empty string     - remove '**Status note:**' line if present.
    #
    # Returns $true when the header now reads the target Status.
    param(
        [Parameter(Mandatory)][string] $PathRef,
        [Parameter(Mandatory)][string] $Status,
        [string] $StatusNote = $null
    )
    try {
        $abs = Resolve-SpecPath -PathRef $PathRef
        if (-not (Test-Path -LiteralPath $abs -PathType Leaf)) { return $false }
        $raw = Get-Content -LiteralPath $abs -Raw

        # Capture: (1) **Status:** line body  (2) its line ending
        #          (3) optional immediately-following **Status note:** line with its ending
        $rx = [regex]'(?m)(^\*\*Status:\*\*[^\r\n]*)(\r?\n)(\*\*Status note:\*\*[^\r\n]*\r?\n?)?'
        $m  = $rx.Match($raw)
        if (-not $m.Success) { return $false }

        $lineEnd       = $m.Groups[2].Value   # \r\n or \n
        $newStatusLine = '**Status:** ' + $Status

        # Build replacement block: status line + optional note line
        if ($null -ne $StatusNote -and $StatusNote -ne '') {
            # Upsert note
            $newBlock = $newStatusLine + $lineEnd + '**Status note:** ' + $StatusNote + $lineEnd
        } elseif ($StatusNote -eq '') {
            # Clear note line
            $newBlock = $newStatusLine + $lineEnd
        } else {
            # $null - preserve existing note line verbatim (or nothing if absent)
            $newBlock = $newStatusLine + $lineEnd
            if ($m.Groups[3].Success -and $m.Groups[3].Value -ne '') {
                $newBlock += $m.Groups[3].Value
            }
        }

        $patched = $raw.Substring(0, $m.Index) + $newBlock + $raw.Substring($m.Index + $m.Length)
        if ($patched -ne $raw) {
            $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
            [System.IO.File]::WriteAllText($abs, $patched, $utf8NoBom)
        }
        return $true
    } catch {
        Write-Host ("  header sync warning ({0}): {1}" -f $PathRef, $_.Exception.Message) -ForegroundColor DarkYellow
        return $false
    }
}

function Get-DaysSinceUpdated {
    param([Parameter(Mandatory)][string] $Updated)
    try {
        $dt = [datetime]::ParseExact($Updated, 'yyyy-MM-dd HH:mm', $null)
        $span = (Get-Date) - $dt
        return [int][math]::Floor($span.TotalDays)
    } catch {
        return -1
    }
}

function Get-StaleLevel {
    param([Parameter(Mandatory)][string] $Status, [Parameter(Mandatory)][int] $Days)
    # Frozen states ignore staleness.
    $frozen = @('Verified', 'Archived', 'Implemented')
    if ($frozen -contains $Status) { return 'fresh' }
    if ($Days -lt 0)                          { return 'unknown' }
    if ($Days -ge $script:StaleAlertDays)     { return 'alert' }
    if ($Days -ge $script:StaleWarnDays)      { return 'warn' }
    return 'fresh'
}

