#requires -Version 7.0
<#
.SYNOPSIS
    The record behind a red script verdict - one row per failing invocation (S3288).

.DESCRIPTION
    A tool result scrolls out of the agent's reach within a turn or two, so "what did that command
    actually say" stops being answerable moments after it is worth asking. S3288 measured the shape
    of that gap: temp/metrics/gate-executions.jsonl held 189 116 rows and 187 FAIL verdicts over two
    days, written by exactly three gate runners and read by nobody, while every other script in the
    repository - the spec_catalog CLI, catalog_sync, adb, set-android-string, the fast a.ps1 targets -
    left no trace at all when it refused.

    This library is the durable half of the cure. Its writer is called by
    .claude/hooks/observe-red-script-exit.ps1 the moment a .ps1 returns 1 or 2, and its rows are what
    scripts/quality/explain-last-failure.ps1 reads back when the hook tells the agent to look.

    Two properties are deliberate and both are about not becoming the problem:

      1. Every IO operation is swallowed. A journal that throws would turn a script's own failure
         into a second, unrelated failure inside the hook that was only trying to record the first.
      2. The file is trimmed to its last 500 rows on every write. The journal this one sits beside
         reached 30 MB unattended; a record nobody prunes becomes a cost rather than an answer, and
         500 rows is far more than one session's worth of red verdicts.

.NOTES
    Dot-sourced library. It defines two functions and assigns nothing at script scope, because a
    dot-sourced file assigns into its CALLER's scope - S2441 records ten scripts under scripts/
    whose parameters collided with a forwarder's internals.

    Exit codes: none of its own. Dot-sourcing defines the functions and returns.
#>

function Get-ToolFailureJournalPath {
    <#
    .SYNOPSIS
        Returns the path of the failing-invocation journal.

    .DESCRIPTION
        Resolved from this file's own location rather than from the caller's working directory: a
        hook runs with whatever directory the tool call had, and a journal that moved with it would
        scatter rows across the disk instead of answering one question in one place.
    #>
    $repositoryRoot = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))
    return Join-Path $repositoryRoot 'temp/metrics/tool-failures.jsonl'
}

function Write-ToolFailureRecord {
    <#
    .SYNOPSIS
        Appends one row describing a failing invocation, then trims the journal.

    .PARAMETER Tool
        The tool that ran the command - 'Bash' or 'PowerShell'.

    .PARAMETER Command
        The command line as invoked, so a later reader can recognise and re-run it.

    .PARAMETER ExitCode
        The code the command returned. The caller decides which codes are worth recording; this
        writer records whatever it is handed.

    .PARAMETER OutputTail
        What the command printed. Kept from the END - a script states its reason last, right before
        it exits, so the tail is the half that carries the refusal.

    .PARAMETER WorkingDirectory
        Where the command ran. A relative path in the command line means nothing without it.
    #>
    param(
        [Parameter(Mandatory = $true)][string]$Tool,
        [Parameter(Mandatory = $true)][string]$Command,
        [Parameter(Mandatory = $true)][int]$ExitCode,
        [string]$OutputTail,
        [string]$WorkingDirectory
    )

    $maxOutputChars = 2000
    $maxRows = 500

    try {
        $path = Get-ToolFailureJournalPath
        $directory = Split-Path -Parent $path
        [System.IO.Directory]::CreateDirectory($directory) | Out-Null

        $tail = if ($OutputTail) { $OutputTail } else { '' }
        if ($tail.Length -gt $maxOutputChars) {
            $tail = $tail.Substring($tail.Length - $maxOutputChars)
        }

        $record = [ordered]@{
            timestampUtc = [DateTime]::UtcNow.ToString('o')
            sessionId    = [string]$env:CLAUDE_CODE_SESSION_ID
            tool         = $Tool
            command      = $Command
            exitCode     = $ExitCode
            outputTail   = $tail
            cwd          = [string]$WorkingDirectory
        }

        [System.IO.File]::AppendAllText(
            $path,
            (($record | ConvertTo-Json -Compress) + [Environment]::NewLine)
        )

        $lines = [System.IO.File]::ReadAllLines($path)
        if ($lines.Length -gt $maxRows) {
            [System.IO.File]::WriteAllLines($path, $lines[($lines.Length - $maxRows)..($lines.Length - 1)])
        }
    }
    catch {
        # Recording a failure must never become a second failure: the hook that calls this is
        # already reporting something that went wrong, and throwing here would bury it.
    }
}
