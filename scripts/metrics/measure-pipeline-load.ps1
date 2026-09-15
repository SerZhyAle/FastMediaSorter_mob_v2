#requires -Version 7.0
<#
.SYNOPSIS
    S3151: classify the tool calls of recent pipeline sessions into the nine load buckets.

.DESCRIPTION
    Re-creates the classifier behind S3151 research 01 so the after-measurement uses the method of
    the baseline; the original lived in a session scratchpad and did not survive.

    A session is a main-tier transcript (a top-level *.jsonl; subagent transcripts in session
    subfolders are not read) whose first user message carries /spec-all, /spec-code, /spec-next,
    /spec-dev or run-spec-queue. Every tool_use is classified once:
      CODE          Read/Edit/Write/Grep/Glob under app_v2/ or wear/
      BUILD         gradlew, a.ps1 build and compile targets
      DEVICE        adb.ps1, adb, Maestro, mobile MCP tools
      SPEC_DOC      file tools under PLAN/
      CATALOG_CLI   spec_catalog CLI, release-queue, document_registry, all_features, dev log,
                    catalog_sync, close-and-log, post-change
      GATES         assert-*.ps1, a.ps1 fg
      INSTRUCTIONS  .claude/commands|reference|rules|agents|skills, CLAUDE.md, AGENTS.md, skill loads
      LOCKS_CHAT    code locks, lock turns, agent chat, ticket leases
      OTHER         everything else
    A failure is a tool_result with is_error = true, nothing inferred from its text. Output tokens
    are summed once per requestId, because one API response is split across several transcript
    lines that each repeat its usage.

.PARAMETER Sessions
    Most recent matching sessions to read. Default 40.

.PARAMETER Since
    Ignore transcripts last written before this date (yyyy-MM-dd).

.PARAMETER TranscriptRoot
    Folder holding the transcripts. Defaults to this project's Claude Code transcript folder.

.PARAMETER Json
    Also write the report as JSON, to -JsonPath or temp/metrics/pipeline-load-<yyyyMMdd>.json.

.PARAMETER JsonPath
    Explicit JSON destination; implies -Json.

    Exit codes:
      0  report written.
      2  bad invocation, or no matching transcript found.

.EXAMPLE
    pwsh -NoProfile -File scripts/metrics/measure-pipeline-load.ps1 -Since 2026-09-16 -Json
#>
[CmdletBinding()]
param(
    [ValidateRange(1, 1000)]
    [int]$Sessions = 40,
    [string]$Since = '',
    [string]$TranscriptRoot = '',
    [switch]$Json,
    [string]$JsonPath = ''
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Stop-NoReport([string]$Reason) {
    Write-Host "measure-pipeline-load: $Reason"
    exit 2
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
if (-not $TranscriptRoot) {
    $home_ = if ($env:USERPROFILE) { $env:USERPROFILE } else { $env:HOME }
    # Claude Code names the folder after the working directory with every non-alphanumeric character dashed.
    $TranscriptRoot = Join-Path $home_ ('.claude/projects/' + ($repoRoot -replace '[^A-Za-z0-9]', '-'))
}
if (-not (Test-Path -LiteralPath $TranscriptRoot -PathType Container)) { Stop-NoReport "transcript root '$TranscriptRoot' does not exist" }
$sinceDate = $null
if ($Since) {
    $parsed = [datetime]::MinValue
    if (-not [datetime]::TryParseExact($Since, 'yyyy-MM-dd', $null, 'None', [ref]$parsed)) { Stop-NoReport "-Since '$Since' is not yyyy-MM-dd" }
    $sinceDate = $parsed
}

$buckets = @('CODE', 'BUILD', 'DEVICE', 'SPEC_DOC', 'CATALOG_CLI', 'GATES', 'INSTRUCTIONS', 'LOCKS_CHAT', 'OTHER')
$pipelinePattern = '/spec-all|/spec-code|/spec-next|/spec-dev|run-spec-queue'

function Get-TextLength($Content) {
    if ($null -eq $Content) { return 0 }
    if ($Content -is [string]) { return $Content.Length }
    $total = 0
    foreach ($block in @($Content)) {
        if ($block -is [string]) { $total += $block.Length }
        elseif ($block.PSObject.Properties.Name -contains 'text') { $total += ([string]$block.text).Length }
    }
    return $total
}

function Get-PathBucket([string]$Path) {
    $p = $Path -replace '\\', '/'
    if ($p -match '(^|/)(app_v2|wear)/') { return 'CODE' }
    if ($p -match '(^|/)PLAN/') { return 'SPEC_DOC' }
    if ($p -match '(^|/)\.claude/(commands|reference|rules|agents|skills)/|(^|/)(CLAUDE|AGENTS)\.md$') { return 'INSTRUCTIONS' }
    return 'OTHER'
}

function Get-CommandBucket([string]$Command) {
    $c = $Command -replace '\\', '/'
    if ($c -match 'adb\.ps1|\badb\b|maestro|a\.ps1\s+(adb|ivn)\b') { return 'DEVICE' }
    if ($c -match 'gradlew|a\.ps1\s+(d|db|dav|dq|cd|nd|nl|r|fk|fkn|fr|fc|fu|fw|fwr|fwu|bf)\b') { return 'BUILD' }
    # Leases live under spec_catalog/, so the lock family is matched before the catalog CLI.
    if ($c -match 'enter-code-lock|exit-code-lock|wait-for-lock-turn|withdraw-lock-ticket|lock-status|agent-chat|ticket-lease') { return 'LOCKS_CHAT' }
    if ($c -match 'assert-[\w-]+\.ps1|a\.ps1\s+fg\b') { return 'GATES' }
    if ($c -match 'spec_catalog/|release-queue|document_registry|all_features/|add_to_dev_log|catalog_sync|close-and-log|post-change') { return 'CATALOG_CLI' }
    if ($c -match '\.claude/(commands|reference|rules)|CLAUDE\.md') { return 'INSTRUCTIONS' }
    return 'OTHER'
}

function Get-ToolBucket([string]$Name, $ToolInput) {
    if ($Name -match '^mcp__(maestro|mobile-mcp)__') { return 'DEVICE' }
    if ($Name -eq 'Skill') { return 'INSTRUCTIONS' }
    $props = if ($null -ne $ToolInput) { @($ToolInput.PSObject.Properties.Name) } else { @() }
    if ($Name -in 'Bash', 'PowerShell') {
        return $(if ($props -contains 'command') { Get-CommandBucket ([string]$ToolInput.command) } else { 'OTHER' })
    }
    if ($Name -in 'Read', 'Edit', 'Write', 'MultiEdit', 'NotebookEdit', 'Grep', 'Glob') {
        foreach ($key in 'file_path', 'notebook_path', 'path') {
            if ($props -contains $key -and $ToolInput.$key) { return Get-PathBucket ([string]$ToolInput.$key) }
        }
    }
    return 'OTHER'
}

function Test-PipelineSession([System.IO.FileInfo]$File) {
    foreach ($line in [System.IO.File]::ReadLines($File.FullName)) {
        if (-not $line) { continue }
        try { $record = $line | ConvertFrom-Json -Depth 64 } catch { continue }
        if ($record.PSObject.Properties.Name -notcontains 'type' -or $record.type -ne 'user') { continue }
        $content = $record.message.content
        $hasToolResult = $content -isnot [string] -and @($content | Where-Object { $_.PSObject.Properties.Name -contains 'type' -and $_.type -eq 'tool_result' }).Count
        if ($hasToolResult) { continue }
        $text = if ($content -is [string]) { $content } else { (@($content | Where-Object { $_.PSObject.Properties.Name -contains 'text' } | ForEach-Object text) -join "`n") }
        return [bool]($text -match $pipelinePattern)
    }
    return $false
}

function New-Tally {
    $tally = [ordered]@{ sessions = 0; assistantTextChars = 0; outputTokens = 0; buckets = [ordered]@{} }
    foreach ($b in $buckets) { $tally.buckets[$b] = [ordered]@{ calls = 0; resultChars = 0; failures = 0 } }
    return $tally
}

function Add-Session($Tally, $Session) {
    $Tally.sessions++
    $Tally.assistantTextChars += $Session.textChars
    $Tally.outputTokens += $Session.outputTokens
    foreach ($b in $buckets) {
        $Tally.buckets[$b].calls += $Session.buckets[$b].calls
        $Tally.buckets[$b].resultChars += $Session.buckets[$b].resultChars
        $Tally.buckets[$b].failures += $Session.buckets[$b].failures
    }
}

function Read-Session([System.IO.FileInfo]$File) {
    $calls = @{}
    $results = @{}
    $seenRequests = @{}
    $textChars = 0
    $outputTokens = 0
    foreach ($line in [System.IO.File]::ReadLines($File.FullName)) {
        if (-not $line) { continue }
        try { $record = $line | ConvertFrom-Json -Depth 64 } catch { continue }
        $names = @($record.PSObject.Properties.Name)
        if ($names -notcontains 'type' -or $names -notcontains 'message' -or $null -eq $record.message) { continue }
        $content = $record.message.content
        if ($record.type -eq 'assistant') {
            $requestId = if ($names -contains 'requestId') { [string]$record.requestId } else { '' }
            if ($record.message.PSObject.Properties.Name -contains 'usage' -and $record.message.usage -and
                (-not $requestId -or -not $seenRequests.ContainsKey($requestId))) {
                if ($requestId) { $seenRequests[$requestId] = $true }
                if ($record.message.usage.PSObject.Properties.Name -contains 'output_tokens') { $outputTokens += [int]$record.message.usage.output_tokens }
            }
            foreach ($block in @($content)) {
                if ($block -is [string] -or $block.PSObject.Properties.Name -notcontains 'type') { continue }
                if ($block.type -eq 'text') { $textChars += ([string]$block.text).Length }
                elseif ($block.type -eq 'tool_use') {
                    $toolInput = if ($block.PSObject.Properties.Name -contains 'input') { $block.input } else { $null }
                    $calls[[string]$block.id] = Get-ToolBucket ([string]$block.name) $toolInput
                }
            }
        }
        elseif ($record.type -eq 'user' -and $content -isnot [string]) {
            foreach ($block in @($content)) {
                if ($block -is [string] -or $block.PSObject.Properties.Name -notcontains 'type' -or $block.type -ne 'tool_result') { continue }
                $isError = $block.PSObject.Properties.Name -contains 'is_error' -and [bool]$block.is_error
                $results[[string]$block.tool_use_id] = @{ chars = (Get-TextLength $block.content); error = $isError }
            }
        }
    }
    $session = @{ textChars = $textChars; outputTokens = $outputTokens; buckets = @{}; codeEditPaths = @{} }
    foreach ($b in $buckets) { $session.buckets[$b] = @{ calls = 0; resultChars = 0; failures = 0 } }
    foreach ($id in $calls.Keys) {
        $bucket = $calls[$id]
        $session.buckets[$bucket].calls++
        if ($results.ContainsKey($id)) {
            $session.buckets[$bucket].resultChars += $results[$id].chars
            if ($results[$id].error) { $session.buckets[$bucket].failures++ }
        }
    }
    return $session
}

function Get-CodeEditCount([System.IO.FileInfo]$File) {
    $paths = @{}
    foreach ($line in [System.IO.File]::ReadLines($File.FullName)) {
        if ($line -notmatch '"tool_use"') { continue }
        try { $record = $line | ConvertFrom-Json -Depth 64 } catch { continue }
        if ($record.PSObject.Properties.Name -notcontains 'message' -or $null -eq $record.message) { continue }
        foreach ($block in @($record.message.content)) {
            if ($block -is [string] -or $block.PSObject.Properties.Name -notcontains 'type' -or $block.type -ne 'tool_use') { continue }
            if ($block.name -notin 'Edit', 'Write', 'MultiEdit', 'NotebookEdit') { continue }
            foreach ($key in 'file_path', 'notebook_path') {
                if ($block.input.PSObject.Properties.Name -contains $key) {
                    $p = ([string]$block.input.$key) -replace '\\', '/'
                    if ((Get-PathBucket $p) -eq 'CODE') { $paths[$p] = $true }
                }
            }
        }
    }
    return $paths.Count
}

$candidates = @(Get-ChildItem -LiteralPath $TranscriptRoot -Filter *.jsonl -File |
        Where-Object { -not $sinceDate -or $_.LastWriteTime -ge $sinceDate } |
        Sort-Object LastWriteTime -Descending)
$selected = [System.Collections.Generic.List[System.IO.FileInfo]]::new()
foreach ($file in $candidates) {
    if ($selected.Count -ge $Sessions) { break }
    if (Test-PipelineSession $file) { $selected.Add($file) }
}
if ($selected.Count -eq 0) { Stop-NoReport "no pipeline transcript under '$TranscriptRoot'" }

$all = New-Tally
$small = New-Tally
foreach ($file in $selected) {
    $session = Read-Session $file
    Add-Session $all $session
    $edits = Get-CodeEditCount $file
    if ($edits -ge 1 -and $edits -le 3) { Add-Session $small $session }
}

function ConvertTo-Report($Tally) {
    $totalCalls = 0
    foreach ($b in $buckets) { $totalCalls += $Tally.buckets[$b].calls }
    $rows = foreach ($b in $buckets) {
        $row = $Tally.buckets[$b]
        $share = if ($totalCalls) { [math]::Round(100.0 * $row.calls / $totalCalls, 1) } else { 0 }
        [ordered]@{ name = $b; calls = $row.calls; share = $share; resultChars = $row.resultChars; failures = $row.failures }
    }
    return [ordered]@{
        sessions = $Tally.sessions; calls = [int]$totalCalls
        assistantTextChars = $Tally.assistantTextChars; outputTokens = $Tally.outputTokens
        buckets = @($rows)
    }
}

$report = [ordered]@{
    generated = (Get-Date -Format 'yyyy-MM-ddTHH:mm:ss')
    transcriptRoot = $TranscriptRoot
    since = $Since
    all = ConvertTo-Report $all
    smallCodeSessions = ConvertTo-Report $small
}

foreach ($part in @(@('all sessions', $report.all), @('sessions with 1-3 code files edited', $report.smallCodeSessions))) {
    $r = $part[1]
    Write-Host ("{0}: {1} session(s), {2} calls, {3} assistant text chars, {4} output tokens" -f $part[0], $r.sessions, $r.calls, $r.assistantTextChars, $r.outputTokens)
    foreach ($row in $r.buckets) {
        Write-Host ("  {0,-13} calls {1,6}  share {2,5}%  result chars {3,10}  failures {4,4}" -f $row.name, $row.calls, $row.share, $row.resultChars, $row.failures)
    }
}

if ($Json -or $JsonPath) {
    if (-not $JsonPath) { $JsonPath = Join-Path $repoRoot ("temp/metrics/pipeline-load-{0}.json" -f (Get-Date -Format 'yyyyMMdd')) }
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $JsonPath) | Out-Null
    $report | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $JsonPath -Encoding utf8
    Write-Host "json: $JsonPath"
}
exit 0
