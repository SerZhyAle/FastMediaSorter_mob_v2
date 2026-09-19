#requires -Version 7.0
# S3301: the closure ledger - what scripts/post-change.ps1 remembers about what it already judged.
#
# The facade judges file CONTENT but held no memory of having judged it, so a ticket whose edits
# arrive in fragments pays for the whole gate batch once per fragment. Measured 2026-09-18 on the
# S3193 run: two closures 69 seconds apart over the same six files, 39577 ms then 27532 ms, four
# build-domain round-trips instead of two, and not one new judgement out of the second pass.
#
# The ledger answers exactly one question - "did this same closure already pass over these same
# bytes just now?" - and answers it conservatively: anything unknown, unreadable or merely
# different means NO, and the batch runs. A needless run costs seconds; a skipped one costs the
# meaning of the verdict.
#
# Dot-sourced into the facade`s scope, never invoked as a child: it reads $root and the resolved
# sets from the caller and sets the two reuse variables the step wrappers read. It owns no exit
# code - every read and write swallows its own failure, so a broken ledger can never fail a
# closure or change one.

$script:ClosureLedgerMaxRecords = 500
# Long enough to cover a ticket's fragmented edits (the measured pair was 69 s apart, and a stage
# of a spec run is minutes), short enough that a record cannot outlive the working session that
# produced it.
$script:ClosureLedgerDefaultWindowMinutes = 45

function Get-ClosureLedgerPath {
    $repositoryRoot = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))
    return Join-Path $repositoryRoot 'temp/metrics/post-change-closures.jsonl'
}

function Get-ClosureLedgerWindowMinutes {
    $override = $env:FMS_POSTCHANGE_REUSE_WINDOW_MIN
    if ($override) {
        $parsed = 0
        if ([int]::TryParse($override, [ref]$parsed) -and $parsed -gt 0) { return $parsed }
    }
    return $script:ClosureLedgerDefaultWindowMinutes
}

function Test-ClosureLedgerDisabled {
    return ($env:FMS_POSTCHANGE_NO_REUSE -eq '1')
}

function Get-ClosureLedgerHead([string]$RepositoryRoot) {
    try {
        $head = & git -C $RepositoryRoot rev-parse HEAD 2>$null
        if ($LASTEXITCODE -ne 0) { return '' }
        return ([string]$head).Trim()
    }
    catch { return '' }
}

# ADR-2 of the spec: length plus content hash, never the write time. The repository`s own tooling
# rewrites whole files, including writing back identical bytes, so a timestamp would report a
# change where a gate would see none - and reuse would miss in exactly the common case.
function Get-ClosureFileStamp([string]$RepositoryRoot, [string]$RelativePath) {
    $full = if ([System.IO.Path]::IsPathRooted($RelativePath)) { $RelativePath } else { Join-Path $RepositoryRoot $RelativePath }
    if (-not (Test-Path -LiteralPath $full -PathType Leaf)) { return $null }
    try {
        $bytes = [System.IO.File]::ReadAllBytes($full)
        $sha = [System.Security.Cryptography.SHA256]::Create()
        try { $digest = $sha.ComputeHash($bytes) } finally { $sha.Dispose() }
        return [pscustomobject]@{
            path = (($RelativePath -replace '\\', '/') -replace '^\./', '')
            len  = $bytes.Length
            hash = [System.BitConverter]::ToString($digest).Replace('-', '').ToLowerInvariant()
        }
    }
    catch { return $null }
}

function Get-ClosureFingerprint {
    param(
        [Parameter(Mandatory = $true)][string]$RepositoryRoot,
        [string[]]$Changed = @(),
        [string[]]$Deleted = @(),
        [string]$ChangeType,
        [string]$Module,
        [bool]$Scoped
    )

    $stamps = [System.Collections.Generic.List[object]]::new()
    foreach ($candidate in $Changed) {
        $stamp = Get-ClosureFileStamp -RepositoryRoot $RepositoryRoot -RelativePath $candidate
        # An unreadable member makes the whole fingerprint unusable: a partial one would compare
        # equal to a record that covered more than it does.
        if (-not $stamp) { return $null }
        $stamps.Add($stamp)
    }

    return [pscustomobject]@{
        changeType = $ChangeType
        module     = $Module
        scoped     = $Scoped
        head       = Get-ClosureLedgerHead $RepositoryRoot
        files      = @($stamps | Sort-Object -Property path)
        deleted    = @(@($Deleted | ForEach-Object { ($_ -replace '\\', '/') -replace '^\./', '' }) | Sort-Object)
    }
}

function Read-ClosureLedgerRecords {
    $path = Get-ClosureLedgerPath
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { return @() }
    try {
        $records = [System.Collections.Generic.List[object]]::new()
        foreach ($line in (Get-Content -LiteralPath $path -ErrorAction Stop)) {
            if ([string]::IsNullOrWhiteSpace($line)) { continue }
            # One malformed line is not a reason to lose the rest of the journal.
            try { $records.Add(($line | ConvertFrom-Json -ErrorAction Stop)) } catch { }
        }
        return @($records)
    }
    catch { return @() }
}

# Condition 5 of the spec: this run`s set must be covered by the record, file for file, byte for
# byte. A subset qualifies - what a narrower closure would judge, the wider one already judged -
# but a set carrying one member the record never saw does not.
function Test-ClosureSetCovered($Record, $Fingerprint) {
    $recorded = @{}
    foreach ($entry in @($Record.files)) { $recorded[[string]$entry.path] = $entry }
    foreach ($entry in @($Fingerprint.files)) {
        $known = $recorded[[string]$entry.path]
        if (-not $known) { return $false }
        if ([int]$known.len -ne [int]$entry.len) { return $false }
        if ([string]$known.hash -ne [string]$entry.hash) { return $false }
    }

    $recordedDeleted = @(@($Record.deleted) | ForEach-Object { [string]$_ })
    foreach ($entry in @($Fingerprint.deleted)) {
        if ($recordedDeleted -notcontains [string]$entry) { return $false }
    }
    return $true
}

# The six conditions of the spec, in the order that rejects most cheaply.
function Find-ReusableClosure {
    param(
        [Parameter(Mandatory = $true)][string]$Target,
        $Fingerprint
    )

    if (Test-ClosureLedgerDisabled) { return $null }
    if (-not $Fingerprint) { return $null }
    # Condition 1: a closure with no ticket has no "one ticket" boundary for reuse to live inside.
    if ($Target -notmatch '^S\d{4}$') { return $null }

    $cutoff = (Get-Date).ToUniversalTime().AddMinutes(-1 * (Get-ClosureLedgerWindowMinutes))
    $best = $null
    foreach ($record in (Read-ClosureLedgerRecords)) {
        try {
            if ([string]$record.target -ne $Target) { continue }
            if ([string]$record.changeType -ne [string]$Fingerprint.changeType) { continue }
            if ([string]$record.module -ne [string]$Fingerprint.module) { continue }
            if ([bool]$record.scoped -ne [bool]$Fingerprint.scoped) { continue }
            if ([string]$record.head -ne [string]$Fingerprint.head) { continue }
            $stamped = [datetime]::Parse([string]$record.timestampUtc, [cultureinfo]::InvariantCulture,
                [System.Globalization.DateTimeStyles]::AdjustToUniversal -bor [System.Globalization.DateTimeStyles]::AssumeUniversal)
            if ($stamped -lt $cutoff) { continue }
            if (-not (Test-ClosureSetCovered $record $Fingerprint)) { continue }
            if (-not $best -or $stamped -gt $best.Stamped) {
                $best = [pscustomobject]@{ Record = $record; Stamped = $stamped }
            }
        }
        catch { continue }
    }

    if (-not $best) { return $null }
    return [pscustomobject]@{
        RunId     = [string]$best.Record.runId
        ElapsedMs = [int]$best.Record.elapsedMs
        AgeSec    = [int]((Get-Date).ToUniversalTime() - $best.Stamped).TotalSeconds
        Files     = @($best.Record.files).Count
        Protocol  = [string]$best.Record.protocol
    }
}

# Condition 2 lives at the call site: only a clean PASS reaches this function. An advisory finding
# is a gate saying it saw something it could not attribute - the next run looks again.
function Add-ClosureLedgerRecord {
    param(
        [Parameter(Mandatory = $true)][string]$Target,
        $Fingerprint,
        [string]$RunId,
        [int]$ElapsedMs,
        [string]$Protocol
    )

    if (Test-ClosureLedgerDisabled) { return }
    if (-not $Fingerprint) { return }
    if ($Target -notmatch '^S\d{4}$') { return }

    try {
        $path = Get-ClosureLedgerPath
        $directory = Split-Path -Parent $path
        if (-not (Test-Path -LiteralPath $directory)) { New-Item -ItemType Directory -Force -Path $directory | Out-Null }
        $record = [ordered]@{
            timestampUtc = (Get-Date).ToUniversalTime().ToString('o')
            target       = $Target
            runId        = $RunId
            elapsedMs    = $ElapsedMs
            protocol     = $Protocol
            changeType   = $Fingerprint.changeType
            module       = $Fingerprint.module
            scoped       = $Fingerprint.scoped
            head         = $Fingerprint.head
            files        = @($Fingerprint.files)
            deleted      = @($Fingerprint.deleted)
        }
        Add-Content -LiteralPath $path -Value ($record | ConvertTo-Json -Depth 5 -Compress) -Encoding utf8

        $lines = @(Get-Content -LiteralPath $path -ErrorAction Stop)
        if ($lines.Count -gt $script:ClosureLedgerMaxRecords) {
            $kept = $lines[($lines.Count - $script:ClosureLedgerMaxRecords)..($lines.Count - 1)]
            Set-Content -LiteralPath $path -Value $kept -Encoding utf8
        }
    }
    catch {
        # Telemetry never changes a verdict: a ledger that could not be written costs the next run
        # its shortcut and nothing else.
    }
}

function Enable-ClosureReuse($Reuse) {
    $script:ClosureReuseActive = $true
    $script:ClosureReuseRecord = $Reuse
}
