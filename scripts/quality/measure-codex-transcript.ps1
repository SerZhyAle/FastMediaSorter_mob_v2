#requires -Version 7.0
<#
.SYNOPSIS
    Attribute a Codex session rollout transcript: context growth, tool-output size, and the
    S3141 hygiene flags (oversized inline reads, truncated output, sleep-polling, cross-ticket
    reads) for one call sequence.

.DESCRIPTION
    Repeatable form of the ad-hoc measurement research/01__s3137-transcript-measurement.md ran
    by hand (temp/S3141/attrib.ps1). Reads a Codex rollout JSONL - either named explicitly
    (-Path) or found by searching $HOME/.codex/sessions/**/*.jsonl for the newest file whose
    earliest `event_msg/user_message` record contains -Id as a token - and reports:
      - total / cached / output / reasoning tokens from the last `token_count` event;
      - context size at the first and last `token_count` event;
      - one row per `custom_tool_call` / `custom_tool_call_output` pair: output length, the
        call's own requested `max_output_tokens`, and three flags -
          oversized   - output length exceeds .sza-profile.json's codexContext.maxInlineChars;
          truncated   - the output text carries a truncation marker;
          sleep-poll  - the call's own command text runs Start-Sleep, or Wait-Process /
                        Wait-Job / Wait-Event with -Timeout; sleepStreak tolerates one
                        unrelated call between two waits;
          chained-read - the command text holds 2+ reads (read-window.ps1 or
                        Get-Content -Raw), which the per-call threshold cannot see;
          cross-ticket:<id> - the command text names a ticket id other than -Id that -Id's own
                        strategic spec does not mention.

    -Json emits one object (schema below) for scripts/quality/assert-codex-transcript-hygiene.ps1
    to consume; the default renders a human table. `found` is false, with everything else
    absent, when -Id was searched and no rollout named it - a normal outcome for most tickets,
    not a parse failure.

    JSON schema (-Json):
      { found, path, turns, totalInput, cachedInput, output, reasoning,
        contextFirst, contextLast, calls: [ { n, chars, maxOutputTokens, oversized,
        truncated, sleepPoll, sleepStreak, readOps, chainedRead, crossTicketId } ] }

    Exit codes (S1070):
      0 - ran to completion, whatever the outcome (including `found: false`).
      2 - cannot verify: an explicit -Path does not exist, or a found file does not parse as
          the expected rollout schema (named in the error).

.PARAMETER Id
    Ticket id (Sxxxx). Used to search for the rollout when -Path is omitted, and as the
    "active ticket" a cross-ticket reference is judged against.

.PARAMETER Path
    Explicit rollout JSONL path. Skips the search; must exist.

.PARAMETER Json
    Emit the machine-readable object instead of the human table.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/measure-codex-transcript.ps1 -Id S3137
    pwsh -NoProfile -File scripts/quality/measure-codex-transcript.ps1 -Id S3137 -Json
#>
[CmdletBinding()]
param(
    [string]$Id,
    [string]$Path,
    [switch]$Json
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)

if ([string]::IsNullOrWhiteSpace($Id) -and [string]::IsNullOrWhiteSpace($Path)) {
    Write-Host "measure-codex-transcript: give -Id or -Path." -ForegroundColor Red
    exit 2
}

function ConvertFrom-RolloutLine {
    param([string]$Line)
    if ([string]::IsNullOrWhiteSpace($Line)) { return $null }
    try { return ($Line | ConvertFrom-Json -Depth 50) } catch { return $null }
}

# --- Locate the rollout ---------------------------------------------------------------------
$resolvedPath = $null
if (-not [string]::IsNullOrWhiteSpace($Path)) {
    $candidate = if ([System.IO.Path]::IsPathRooted($Path)) { $Path } else { Join-Path $repoRoot $Path }
    if (-not (Test-Path -LiteralPath $candidate)) {
        Write-Host "measure-codex-transcript: not found - $Path" -ForegroundColor Red
        exit 2
    }
    $resolvedPath = $candidate
} else {
    # Named $homeDir, not $home - PowerShell's automatic $HOME is the same name case-insensitively.
    $homeDir = if ($env:USERPROFILE) { $env:USERPROFILE } elseif ($env:HOME) { $env:HOME } else { $null }
    if (-not $homeDir) {
        Write-Host "measure-codex-transcript: cannot resolve home directory to search sessions." -ForegroundColor Red
        exit 2
    }
    $sessionsRoot = Join-Path $homeDir '.codex/sessions'
    if (Test-Path -LiteralPath $sessionsRoot) {
        # Fast prefilter: any file whose raw text names the id at all. Narrows before the
        # per-file JSON parse below, which confirms the id is a real user_message, not an
        # incidental mention elsewhere in the transcript.
        $prefiltered = @(Get-ChildItem -Path $sessionsRoot -Filter '*.jsonl' -Recurse -File -ErrorAction SilentlyContinue |
            Where-Object { (Select-String -LiteralPath $_.FullName -Pattern $Id -SimpleMatch -List -ErrorAction SilentlyContinue) })
        # Only the EARLIEST user_message counts as "this session's ticket" - a later message
        # that merely discusses another ticket (as this very research pass's own authoring
        # session discussed S3137 while its own id was S3141) must not steal the match.
        $confirmed = foreach ($f in $prefiltered) {
            $isRealMatch = $false
            foreach ($line in (Get-Content -LiteralPath $f.FullName)) {
                $rec = ConvertFrom-RolloutLine $line
                if (-not $rec) { continue }
                $pl = $rec.payload
                if ($rec.type -eq 'event_msg' -and $pl -and $pl.type -eq 'user_message') {
                    $isRealMatch = ($pl.message -like "*$Id*")
                    break
                }
            }
            if ($isRealMatch) { $f }
        }
        $confirmed = @($confirmed)
        if ($confirmed.Count -gt 0) {
            $resolvedPath = ($confirmed | Sort-Object LastWriteTime -Descending | Select-Object -First 1).FullName
        }
    }
}

if (-not $resolvedPath) {
    if ($Json) {
        [pscustomobject]@{ found = $false } | ConvertTo-Json -Compress
    } else {
        Write-Host "measure-codex-transcript: no rollout found naming $Id under ~/.codex/sessions."
    }
    exit 0
}

# --- Parse --------------------------------------------------------------------------------
$rawLines = Get-Content -LiteralPath $resolvedPath
$records = foreach ($l in $rawLines) { ConvertFrom-RolloutLine $l }
$records = @($records | Where-Object { $_ })
if ($records.Count -eq 0) {
    Write-Host "measure-codex-transcript: $resolvedPath parsed to zero valid JSON records - not a rollout file." -ForegroundColor Red
    exit 2
}

$tokenCounts = @($records | Where-Object { $_.type -eq 'event_msg' -and $_.payload.type -eq 'token_count' -and $_.payload.info })
$calls = [ordered]@{}
$callOrder = @()
$outputs = @{}
foreach ($r in $records) {
    $pl = $r.payload
    if ($r.type -ne 'response_item') { continue }
    # Branched on the discriminant `type`, not on property presence: under StrictMode, probing
    # an optional property that this record's type does not carry (e.g. .arguments on a
    # custom_tool_call, which carries .input instead) throws rather than reading as falsy.
    if ($pl.type -eq 'custom_tool_call') {
        $calls[$pl.call_id] = [string]$pl.input
        $callOrder += $pl.call_id
    } elseif ($pl.type -eq 'function_call') {
        $calls[$pl.call_id] = [string]$pl.arguments
        $callOrder += $pl.call_id
    } elseif ($pl.type -eq 'local_shell_call') {
        $calls[$pl.call_id] = ($pl.action | ConvertTo-Json -Compress -Depth 10)
        $callOrder += $pl.call_id
    } elseif ($pl.type -in @('custom_tool_call_output', 'function_call_output')) {
        $txt = if ($pl.output -is [string]) { $pl.output } else { ($pl.output | ConvertTo-Json -Compress -Depth 10) }
        $outputs[$pl.call_id] = $txt
    }
}

if ($tokenCounts.Count -eq 0 -and $callOrder.Count -eq 0) {
    Write-Host "measure-codex-transcript: $resolvedPath has neither token_count events nor tool calls - unexpected schema." -ForegroundColor Red
    exit 2
}

$szaProfile = Get-Content -LiteralPath (Join-Path $repoRoot '.sza-profile.json') -Raw | ConvertFrom-Json
$maxInlineChars = [int]$szaProfile.codexContext.maxInlineChars

$total = if ($tokenCounts.Count -gt 0) { $tokenCounts[-1].payload.info.total_token_usage } else { $null }
$contextFirst = if ($tokenCounts.Count -gt 0) { $tokenCounts[0].payload.info.last_token_usage.input_tokens } else { $null }
$contextLast = if ($tokenCounts.Count -gt 0) { $tokenCounts[-1].payload.info.last_token_usage.input_tokens } else { $null }

# Same link test read-window.ps1's scope guard applies: an id the active ticket's own strategic
# spec names (Blocker:, Depends on, a direct reference) is linked work, not a stray read. Without
# it S3143, which depends on S3090, reported 19 cross-ticket findings that were all its blocker.
$linkedSpecText = ''
if (-not [string]::IsNullOrWhiteSpace($Id)) {
    # PLAN/archive/ too: a transcript is usually measured after its ticket closed, and S3143 was
    # archived minutes after its session - the PLAN/-only lookup then saw no spec and no links.
    $activeSpec = @(foreach ($dir in @('PLAN', 'PLAN/archive')) {
        Get-ChildItem -Path (Join-Path $repoRoot $dir) -Filter "${Id}_*.md" -File -ErrorAction SilentlyContinue
    })
    if ($activeSpec.Count -ge 1) { $linkedSpecText = Get-Content -LiteralPath $activeSpec[0].FullName -Raw }
}

$sleepStreak = 0
$sleepGap = 0
$callRows = for ($i = 0; $i -lt $callOrder.Count; $i++) {
    # Named $callId, not $id - PowerShell variable names are case-insensitive, so a local $id
    # here is the same variable as the script's own $Id (ticket id) parameter and silently
    # overwrites it for every later comparison against $Id in this loop.
    $callId = $callOrder[$i]
    $cmd = [string]$calls[$callId]
    $out = [string]$outputs[$callId]
    $chars = $out.Length
    $maxOut = if ($cmd -match '"max_output_tokens"\s*:\s*(\d+)') { [int]$Matches[1] } else { $null }
    $oversized = $chars -gt $maxInlineChars
    $truncated = ($out -match '(?i)truncat|omitted')
    # A timed wait of any cmdlet, not only Start-Sleep: after S3141 first landed, polling moved to
    # `Wait-Process -Id N -Timeout 55` loops (S3142: ten turns) that a Start-Sleep match never saw.
    $sleepPoll = ($cmd -match 'Start-Sleep|Wait-(Process|Job|Event)\b[^;|]*-Timeout')
    # One unrelated call between two waits does not end the streak: S3142 interleaved each
    # Wait-Process with a lock-status or Get-Process check, which reset a strict counter to 1.
    if ($sleepPoll) { $sleepStreak++; $sleepGap = 0 }
    elseif ($sleepStreak -gt 0 -and $sleepGap -eq 0) { $sleepGap = 1 }
    else { $sleepStreak = 0; $sleepGap = 0 }
    # Reads chained into one exec call: the per-call threshold inside read-window.ps1 cannot see
    # the sum, and the after-transcripts chained 3-6 reads plus whole command drivers per call.
    $readOps = ([regex]::Matches($cmd, 'read-window\.ps1|Get-Content\b[^;|]*-Raw')).Count
    $chainedRead = $readOps -ge 2
    $crossTicket = $null
    # Freestanding ticket-id token, not a PLAN/ path shape specifically: S3137's own call 13
    # named S3136 twice - `-Id S3136` and `-Filter 'S3136_*.md'` - and neither is
    # `PLAN/S3136_`. Same token regex `assert-no-ticket-logs.ps1` already uses for an Sxxxx id
    # inside log text, reused rather than re-invented (CLAUDE.md Rule 13).
    foreach ($m in [regex]::Matches($cmd, '(?<![A-Za-z0-9])S(\d{4})(?![0-9A-Za-z])')) {
        $found = "S$($m.Groups[1].Value)"
        if ($found -eq $Id) { continue }
        if ($linkedSpecText -and $linkedSpecText -match [regex]::Escape($found)) { continue }
        $crossTicket = $found
        break
    }
    [pscustomobject]@{
        n = $i + 1
        chars = $chars
        maxOutputTokens = $maxOut
        oversized = [bool]$oversized
        truncated = [bool]$truncated
        sleepPoll = [bool]$sleepPoll
        sleepStreak = $sleepStreak
        readOps = $readOps
        chainedRead = [bool]$chainedRead
        crossTicketId = $crossTicket
    }
}
$callRows = @($callRows)

if ($Json) {
    $result = [pscustomobject]@{
        found = $true
        path = $resolvedPath
        turns = $tokenCounts.Count
        totalInput = if ($total) { $total.input_tokens } else { $null }
        cachedInput = if ($total) { $total.cached_input_tokens } else { $null }
        output = if ($total) { $total.output_tokens } else { $null }
        reasoning = if ($total) { $total.reasoning_output_tokens } else { $null }
        contextFirst = $contextFirst
        contextLast = $contextLast
        calls = $callRows
    }
    $result | ConvertTo-Json -Depth 6 -Compress
} else {
    Write-Host "path: $resolvedPath"
    Write-Host "turns: $($tokenCounts.Count) | calls: $($callOrder.Count)"
    if ($total) {
        Write-Host "input: $($total.input_tokens) (cached $($total.cached_input_tokens)) | output: $($total.output_tokens) (reasoning $($total.reasoning_output_tokens))"
        Write-Host "context: $contextFirst -> $contextLast"
    }
    $flagged = @($callRows | Where-Object { $_.oversized -or $_.truncated -or $_.chainedRead -or ($_.sleepPoll -and $_.sleepStreak -ge 2) -or $_.crossTicketId })
    Write-Host "flagged calls: $($flagged.Count) of $($callOrder.Count)"
    $flagged | Format-Table n, chars, maxOutputTokens, oversized, truncated, readOps, sleepStreak, crossTicketId -AutoSize
}
exit 0
