#requires -Version 7.0
<#
.SYNOPSIS
    Contract tests for scripts/utils/mono-mode.ps1 - the MONO start step (S3158).

.DESCRIPTION
    The start drops leftover coordination state unconditionally, which makes it the one place in
    MONO where a wrong path destroys somebody's state. Every case therefore runs against throwaway
    roots: FMS_TICKET_LEASE_ROOT moves the lease store and FMS_AGENT_CHAT_ROOT moves the chat, both
    seams the canon harness honours.

    The lock files have no such seam - the harness resolves them from the git common directory - so
    no case here clears a lock. Lock clearing is covered only by the -DryRun listing, and every case
    that is not a dry run passes -Stores Leases so the repository's real locks are never touched.

.EXIT CODES
    0 - every case passed.
    1 - at least one case failed.
#>

[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$script = Join-Path (Split-Path -Parent $PSScriptRoot) 'mono-mode.ps1'
if (-not (Test-Path -LiteralPath $script)) {
    Write-Error "script not found: $script" -ErrorAction Continue
    exit 1
}

$pwshExe = (Get-Process -Id $PID).Path
$failures = 0

$root = Join-Path ([System.IO.Path]::GetTempPath()) ('mono-mode-tests-' + [guid]::NewGuid().ToString('n').Substring(0, 8))
$leaseDir = Join-Path $root 'temp\SPEC-TICKET.LEASES'
$chatRoot = Join-Path $root 'chat'
New-Item -ItemType Directory -Path $leaseDir -Force | Out-Null
New-Item -ItemType Directory -Path $chatRoot -Force | Out-Null

$previousLeaseRoot = $env:FMS_TICKET_LEASE_ROOT
$previousChatRoot = $env:FMS_AGENT_CHAT_ROOT
$env:FMS_TICKET_LEASE_ROOT = $root
$env:FMS_AGENT_CHAT_ROOT = $chatRoot

function Invoke-Mono {
    param([string[]] $MonoArgs)
    & $pwshExe -NoProfile -File $script @MonoArgs *> $null
    return $LASTEXITCODE
}

# A lease owned by a session that is not this one, written in the shape ticket-lease.ps1 reads.
function New-FixtureLease {
    param([string] $Id)
    $nowMs = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    $record = [ordered]@{
        schema         = 1
        id             = $Id
        sessionId      = 'ffffffff-0000-0000-0000-00000000cafe'
        host           = 'OTHER'
        pid            = $PID
        reason         = 'fixture'
        claimedAt      = $nowMs
        transcriptPath = Join-Path $root 'foreign-transcript.jsonl'
        lastSeenAt     = $nowMs
    }
    $path = Join-Path $leaseDir "$Id.json"
    Set-Content -LiteralPath $path -Value ($record | ConvertTo-Json -Depth 3) -Encoding UTF8
    return $path
}

function Assert-Case {
    param([string] $Name, [scriptblock] $Body)
    try {
        $message = & $Body
        if ($message) {
            Write-Host "FAIL  $Name - $message" -ForegroundColor Red
            $script:failures++
        }
        else {
            Write-Host "PASS  $Name" -ForegroundColor Green
        }
    }
    catch {
        Write-Host "FAIL  $Name - threw: $($_.Exception.Message)" -ForegroundColor Red
        $script:failures++
    }
}

try {
    Assert-Case 'M1 -DryRun leaves a leftover lease in place and exits 0' {
        $lease = New-FixtureLease -Id 'S9901'
        $code = Invoke-Mono @('-Verb', 'Start', '-Stores', 'Leases', '-DryRun')
        if ($code -ne 0) { return "expected exit 0, got $code" }
        if (-not (Test-Path -LiteralPath $lease)) { return 'the dry run removed the lease' }
    }

    Assert-Case 'M2 -DryRun posts nothing to the chat' {
        $before = @(Get-ChildItem -LiteralPath $chatRoot -Recurse -File -ErrorAction SilentlyContinue).Count
        $null = Invoke-Mono @('-Verb', 'Start', '-Stores', 'Leases', '-DryRun')
        $after = @(Get-ChildItem -LiteralPath $chatRoot -Recurse -File -ErrorAction SilentlyContinue).Count
        if ($after -ne $before) { return "chat files went from $before to $after" }
    }

    Assert-Case 'M3 Start drops a live-looking foreign lease without judging it' {
        $lease = New-FixtureLease -Id 'S9902'
        $code = Invoke-Mono @('-Verb', 'Start', '-Stores', 'Leases', '-Ticket', 'S9902')
        if ($code -ne 0) { return "expected exit 0, got $code" }
        if (Test-Path -LiteralPath $lease) { return 'the lease survived the start' }
    }

    Assert-Case 'M4 Start posts one journal note into the fixture chat' {
        $before = @(Get-ChildItem -LiteralPath $chatRoot -Recurse -File -ErrorAction SilentlyContinue).Count
        $null = Invoke-Mono @('-Verb', 'Start', '-Stores', 'Leases', '-Note', 'MONO start - suite')
        $after = @(Get-ChildItem -LiteralPath $chatRoot -Recurse -File -ErrorAction SilentlyContinue).Count
        if ($after -le $before) { return "no chat file was written ($before -> $after)" }
    }

    Assert-Case 'M5 an unknown verb is refused' {
        $code = Invoke-Mono @('-Verb', 'Stop')
        if ($code -eq 0) { return 'expected a non-zero exit' }
    }
}
finally {
    $env:FMS_TICKET_LEASE_ROOT = $previousLeaseRoot
    $env:FMS_AGENT_CHAT_ROOT = $previousChatRoot
    Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
}

if ($failures -gt 0) {
    Write-Host "mono-mode tests: $failures case(s) failed." -ForegroundColor Red
    exit 1
}
Write-Host 'mono-mode tests: all cases passed.' -ForegroundColor Green
exit 0
