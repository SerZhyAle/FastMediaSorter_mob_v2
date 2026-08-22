#requires -Version 7.0
<#
.SYNOPSIS
    Arm or disarm the endless marker that stops a /spec-do session from ending its own turn.

.DESCRIPTION
    /spec-do is contractually endless: only the operator may end it. That contract used to live in
    prose alone, and prose is the 1-8% tier - a loop repeatedly ended itself at a round boundary and
    asked the operator for a compaction, which is a stop wearing a report's clothes.

    This script arms the marker that makes the contract mechanical. While a marker file exists in
    temp/, the Stop hook .claude/hooks/refuse-spec-do-stop.ps1 refuses to let the session finish its
    turn and hands the loop back its own next step. Disarming is the only in-band way out, and the
    command text says so, so a session cannot drift into stopping - it has to decide to.

    One marker per armed loop, named by its token, so two parallel /spec-do sessions do not fight
    over one file. The hook claims an unclaimed marker for the first session whose turn it ends.

.PARAMETER Action
    arm    - create a marker and print its token.
    disarm - remove the marker named by -Token, or every marker with -All.
    list   - print every armed marker, its age, its claiming session and its block count.

.PARAMETER Token
    Marker identity. Generated on arm when omitted; required to disarm one marker.

.PARAMETER Reason
    Free text recorded in the marker - shown to whoever inspects a session that will not stop.

.PARAMETER All
    disarm only: remove every marker, including one whose token this session no longer remembers
    (a platform reset loses the token, never the file).

.EXIT CODES
    0 - the requested state holds (armed, disarmed, or listed).
    2 - could not verify - temp/ is unreadable or a marker could not be written.
    4 - usage error (disarm without -Token and without -All).

.EXAMPLE
    pwsh -NoProfile -File scripts/utils/spec-do-marker.ps1 -Action arm -Reason "/spec-do"
    pwsh -NoProfile -File scripts/utils/spec-do-marker.ps1 -Action disarm -Token 3f9a1c02
#>

[CmdletBinding(PositionalBinding = $false)]
param(
    [ValidateSet('arm', 'disarm', 'list')]
    [string]$Action = 'list',

    [string]$Token = '',

    [string]$Reason = '',

    [switch]$All,

    [string]$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
)

$ErrorActionPreference = 'Stop'

$tempDir = Join-Path $RepoRoot 'temp'
$pattern = 'SPEC-DO.ACTIVE-*.json'

try {
    if (-not (Test-Path -LiteralPath $tempDir)) {
        New-Item -ItemType Directory -Path $tempDir -Force | Out-Null
    }
}
catch {
    Write-Error "spec-do-marker: cannot use $tempDir - $($_.Exception.Message)" -ErrorAction Continue
    exit 2
}

function Get-Markers {
    @(Get-ChildItem -LiteralPath $tempDir -Filter $pattern -File -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime)
}

function Read-Marker([string]$Path) {
    try { return (Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json) }
    catch { return $null }
}

switch ($Action) {

    'arm' {
        if ([string]::IsNullOrWhiteSpace($Token)) {
            $Token = ([guid]::NewGuid().ToString('n')).Substring(0, 8)
        }
        $path = Join-Path $tempDir "SPEC-DO.ACTIVE-$Token.json"
        $body = [ordered]@{
            token       = $Token
            reason      = $Reason
            armedAt     = (Get-Date).ToString('s')
            sessionId   = $null   # claimed by the Stop hook, which is the only caller that knows it
            claimedAt   = $null
            blocks      = 0
            rapidBlocks = 0
            lastBlockAt = $null
        }
        try {
            $body | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $path -Encoding UTF8
        }
        catch {
            Write-Error "spec-do-marker: could not write $path - $($_.Exception.Message)" -ErrorAction Continue
            exit 2
        }
        Write-Output "spec-do-marker: ARMED token=$Token file=$path"
        Write-Output "  This session can no longer end its own turn. Disarm only on an explicit operator stop:"
        Write-Output "  pwsh -NoProfile -File scripts/utils/spec-do-marker.ps1 -Action disarm -Token $Token"
        exit 0
    }

    'disarm' {
        if (-not $All -and [string]::IsNullOrWhiteSpace($Token)) {
            Write-Error 'spec-do-marker: disarm needs -Token <token>, or -All to clear every marker.' -ErrorAction Continue
            exit 4
        }
        $targets = if ($All) { Get-Markers } else {
            @(Get-ChildItem -LiteralPath $tempDir -Filter "SPEC-DO.ACTIVE-$Token.json" -File -ErrorAction SilentlyContinue)
        }
        if ($targets.Count -eq 0) {
            Write-Output 'spec-do-marker: DISARMED (nothing was armed)'
            exit 0
        }
        $removed = 0
        foreach ($f in $targets) {
            try { Remove-Item -LiteralPath $f.FullName -Force; $removed++ }
            catch {
                Write-Error "spec-do-marker: could not remove $($f.Name) - $($_.Exception.Message)" -ErrorAction Continue
                exit 2
            }
        }
        Write-Output "spec-do-marker: DISARMED ($removed marker(s) removed) - this session may end its turn again."
        exit 0
    }

    default {
        $markers = Get-Markers
        if ($markers.Count -eq 0) {
            Write-Output 'spec-do-marker: no marker armed - no session is held open.'
            exit 0
        }
        foreach ($f in $markers) {
            $m = Read-Marker $f.FullName
            if ($null -eq $m) {
                Write-Output "  $($f.Name) - unreadable (the hook will purge it)"
                continue
            }
            $ageMin = [int]((Get-Date) - $f.LastWriteTime).TotalMinutes
            $owner = if ($m.sessionId) { $m.sessionId } else { 'unclaimed' }
            Write-Output "  token=$($m.token) armed=$($m.armedAt) idle=${ageMin}m session=$owner blocks=$($m.blocks) reason='$($m.reason)'"
        }
        exit 0
    }
}
