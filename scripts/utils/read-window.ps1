#requires -Version 7.0
<#
.SYNOPSIS
    Bounded-read / evidence-offload tool for Codex sessions (S3141).

.DESCRIPTION
    Reads a single file (-Path) or greps a pattern across a path (-Pattern + -SearchPath) and
    returns it to the caller either verbatim (under the configured threshold) or as a header
    plus excerpt, with the full text saved under temp/<Id>/evidence/ (at or over the
    threshold). This is the mechanism half of the S3141 bounded-read protocol: a large tool
    output stops re-entering the active context on every later turn, without losing the full
    text - it moves to a durable artifact and the model gets only what the read was for.

    Threshold is .sza-profile.json's codexContext.maxInlineChars, read directly from the
    project's own profile file rather than through the harness's Get-SzaProfileValue helper:
    that helper resolves a value merged against the harness's own shipped defaults, and
    codexContext carries no harness-side default to merge against - it is a project-only key
    (S3030 "declared once" pattern), so a direct read returns the identical value with one
    fewer moving part and no dependency on an unrelated forwarder purely for its side effect.

    Ticket-scope guard: when the target names a different ticket's PLAN/ spec file (matches
    PLAN/S<digits>), the read is refused unless that id also appears anywhere in the active
    ticket's own strategic spec (PLAN/<Id>_*.md) - covering an explicit Blocker:/Carrier:
    token or any other direct reference - or -AllowCrossTicket -Reason is supplied. This is
    the mechanism half of docs/NON_CLAUDE_RUNTIME_RULES.md's ticket-scope rule (research/05):
    a call shaped like S3137's call 13 (reading S3136's spec while S3137 was the active
    ticket, with no reference between the two) is refused here, not merely discouraged in
    prose that already failed once.

    Exit codes (S1070):
      0 - printed (inline, or offloaded with header + excerpt).
      2 - invalid arguments: neither or both of -Path / (-Pattern + -SearchPath) given, or
          -AllowCrossTicket without -Reason.
      3 - ticket-scope refusal: target names another ticket's PLAN/ file with no link found
          in the active ticket's own spec, and -AllowCrossTicket was not supplied.
      4 - target not found (file missing, or -Pattern matched nothing under -SearchPath).

.PARAMETER Id
    Active ticket id (Sxxxx). Mandatory - grounds the evidence directory and the scope guard.

.PARAMETER Path
    A single file to read in full. Mutually exclusive with -Pattern / -SearchPath.

.PARAMETER Pattern
    Regex to search for (Select-String -Pattern). Requires -SearchPath.

.PARAMETER SearchPath
    File or directory to search with -Pattern.

.PARAMETER ContextLines
    Lines of context around each match (Select-String -Context) in -Pattern mode; number of
    leading lines shown as the excerpt for an over-threshold -Path read. Default 20.

.PARAMETER AllowCrossTicket
    Bypass the ticket-scope guard. Requires -Reason.

.PARAMETER Reason
    Required with -AllowCrossTicket. Recorded in the printed header.

.EXAMPLE
    pwsh -NoProfile -File scripts/utils/read-window.ps1 -Id S3141 -Path AGENTS.md
    pwsh -NoProfile -File scripts/utils/read-window.ps1 -Id S3141 -Pattern 'maxInlineChars' -SearchPath .sza-profile.json
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$Id,
    [string]$Path,
    [string]$Pattern,
    [string]$SearchPath,
    [int]$ContextLines = 20,
    [switch]$AllowCrossTicket,
    [string]$Reason
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# scripts/utils -> scripts -> repo root.
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)

$pathMode = -not [string]::IsNullOrWhiteSpace($Path)
$patternMode = -not [string]::IsNullOrWhiteSpace($Pattern)
if ($pathMode -eq $patternMode) {
    Write-Host "read-window: give exactly one of -Path, or -Pattern together with -SearchPath." -ForegroundColor Red
    exit 2
}
if ($patternMode -and [string]::IsNullOrWhiteSpace($SearchPath)) {
    Write-Host "read-window: -Pattern requires -SearchPath." -ForegroundColor Red
    exit 2
}
if ($AllowCrossTicket -and [string]::IsNullOrWhiteSpace($Reason)) {
    Write-Host "read-window: -AllowCrossTicket requires -Reason." -ForegroundColor Red
    exit 2
}

function Resolve-RepoPath {
    param([string]$RelativeOrAbsolute)
    if ([System.IO.Path]::IsPathRooted($RelativeOrAbsolute)) { return $RelativeOrAbsolute }
    return (Join-Path $repoRoot $RelativeOrAbsolute)
}

# --- Ticket-scope guard -----------------------------------------------------------------
function Test-CrossTicketId {
    param([string]$TargetPath, [string]$ActiveId)
    if ($TargetPath -match 'PLAN[/\\]archive[/\\](S\d{4})_' -or $TargetPath -match 'PLAN[/\\](S\d{4})_') {
        $found = $Matches[1]
        if ($found -ne $ActiveId) { return $found }
    }
    return $null
}

$scopeSource = if ($pathMode) { $Path } else { $SearchPath }
$crossId = Test-CrossTicketId -TargetPath $scopeSource -ActiveId $Id
if ($crossId -and -not $AllowCrossTicket) {
    # PLAN/archive/ too, matching measure-codex-transcript.ps1: an archived active ticket still
    # owns its links, and a PLAN/-only lookup refused every read of its own blocker.
    $activeSpecCandidates = @(foreach ($dir in @('PLAN', 'PLAN/archive')) {
        Get-ChildItem -Path (Join-Path $repoRoot $dir) -Filter "${Id}_*.md" -File -ErrorAction SilentlyContinue
    })
    $linked = $false
    if ($activeSpecCandidates.Count -ge 1) {
        $activeSpecText = Get-Content -LiteralPath $activeSpecCandidates[0].FullName -Raw
        if ($activeSpecText -match [regex]::Escape($crossId)) { $linked = $true }
    }
    if (-not $linked) {
        Write-Host "read-window: refused - target names $crossId, active ticket is $Id, and $Id's own spec does not reference $crossId." -ForegroundColor Red
        Write-Host "read-window: pass -AllowCrossTicket -Reason '<why>' if this is intentional." -ForegroundColor Red
        exit 3
    }
}

# --- Collect text ------------------------------------------------------------------------
$sourceDescription = $null
$text = $null

if ($pathMode) {
    $resolved = Resolve-RepoPath $Path
    if (-not (Test-Path -LiteralPath $resolved)) {
        Write-Host "read-window: not found - $Path" -ForegroundColor Red
        exit 4
    }
    $text = Get-Content -LiteralPath $resolved -Raw
    $sourceDescription = $Path
} else {
    $resolvedSearch = Resolve-RepoPath $SearchPath
    if (-not (Test-Path -LiteralPath $resolvedSearch)) {
        Write-Host "read-window: not found - $SearchPath" -ForegroundColor Red
        exit 4
    }
    # Named $hits, not $matches - PowerShell's automatic $Matches (from -match) is the same
    # name case-insensitively, and this script also uses -match for the ticket-id and evidence
    # numbering checks below; a same-named script-scope variable would silently overwrite it.
    $isContainer = Test-Path -LiteralPath $resolvedSearch -PathType Container
    # @() wraps AFTER the if/else, never inside a branch: `if (c) { @(expr) } else { .. }` sends
    # its branch's output through the pipeline to reach $rawHits, and PowerShell unrolls a
    # one-element array back to its bare element on that trip - so a single match came out as a
    # bare MatchInfo with no .Count, while zero or 2+ matches happened to survive. Same class of
    # pitfall Get-SzaProfileValue's own header warns about for a function's `return`.
    $rawHits = if ($isContainer) {
        Select-String -Path (Join-Path $resolvedSearch '*') -Pattern $Pattern -Context $ContextLines, $ContextLines -ErrorAction SilentlyContinue
    } else {
        Select-String -LiteralPath $resolvedSearch -Pattern $Pattern -Context $ContextLines, $ContextLines -ErrorAction SilentlyContinue
    }
    $hits = @($rawHits)
    if ($hits.Count -eq 0) {
        Write-Host "read-window: pattern '$Pattern' matched nothing under $SearchPath" -ForegroundColor Red
        exit 4
    }
    $blocks = foreach ($m in $hits) {
        $pre = ($m.Context.PreContext -join "`n")
        $post = ($m.Context.PostContext -join "`n")
        "--- $($m.Path):$($m.LineNumber) ---`n$pre`n$($m.Line)`n$post"
    }
    $text = ($blocks -join "`n`n")
    $sourceDescription = "'$Pattern' in $SearchPath ($($hits.Count) match(es))"
}

$charCount = $text.Length

# --- Threshold (read directly - see .DESCRIPTION for why not Get-SzaProfileValue) --------
$szaProfile = Get-Content -LiteralPath (Join-Path $repoRoot '.sza-profile.json') -Raw | ConvertFrom-Json
$maxInlineChars = [int]$szaProfile.codexContext.maxInlineChars
$evidenceDirTemplate = [string]$szaProfile.codexContext.evidenceDirTemplate

$crossNote = if ($crossId -and $AllowCrossTicket) { " | cross-ticket: $crossId (reason: $Reason)" } else { '' }

if ($charCount -le $maxInlineChars) {
    Write-Host "source: $sourceDescription | chars: $charCount$crossNote"
    Write-Output $text
    exit 0
}

# --- Offload: full text to temp/<Id>/evidence/, excerpt to stdout ------------------------
$evidenceDirRelative = $evidenceDirTemplate -replace '\{Id\}', $Id
$evidenceDir = Resolve-RepoPath $evidenceDirRelative
New-Item -ItemType Directory -Path $evidenceDir -Force | Out-Null

$existing = @(Get-ChildItem -Path $evidenceDir -Filter '*__*.txt' -File -ErrorAction SilentlyContinue)
$nextNum = 1
foreach ($f in $existing) {
    if ($f.Name -match '^(\d+)__') {
        $n = [int]$Matches[1]
        if ($n -ge $nextNum) { $nextNum = $n + 1 }
    }
}
$nn = '{0:D2}' -f $nextNum
$slugSource = if ($pathMode) { $Path } else { $SearchPath }
$slug = ($slugSource -replace '[^A-Za-z0-9]+', '-').Trim('-').ToLowerInvariant()
if ($slug.Length -gt 60) { $slug = $slug.Substring(0, 60) }
$evidenceFile = Join-Path $evidenceDir "${nn}__${slug}.txt"
Set-Content -LiteralPath $evidenceFile -Value $text -NoNewline

$excerpt = if ($pathMode) {
    # S3176: the first lines alone left the caller blind, so it spent a second turn grepping the
    # evidence file for headings before a third turn fetched the section - measured 2-3 turns per
    # document in the S3103 Codex session, each resending a context of 100-200k tokens. The
    # outline lets the very next call be `-Pattern '<heading>' -SearchPath <evidence path>`.
    $allLines = $text -split "`r?`n"
    $head = ($allLines | Select-Object -First $ContextLines) -join "`n"
    $outlinePattern = '^(#{1,4} \S|\s{0,4}(private |internal |public |override )*(fun|class|object|interface|enum class|data class|sealed class) \S|function \S)'
    $outline = for ($i = 0; $i -lt $allLines.Count; $i++) {
        if ($allLines[$i] -match $outlinePattern) {
            $entry = $allLines[$i].Trim()
            if ($entry.Length -gt 120) { $entry = $entry.Substring(0, 120) + '..' }
            "outline: $($i + 1): $entry"
        }
    }
    $outlineText = (@($outline) -join "`n")
    $outlineBudget = [Math]::Max(0, $maxInlineChars - $head.Length)
    if ($outlineText.Length -gt $outlineBudget) {
        $outlineText = $outlineText.Substring(0, $outlineBudget) + "`n.. (outline cut at $maxInlineChars chars; the evidence file holds the rest)"
    }
    if ($outlineText) { "$head`n$outlineText" } else { $head }
} else {
    # One line per hit, never the context blocks: printing $text here put the whole over-threshold
    # result back inline (27,087 and 25,324 chars in the S3142/S3090 after-transcripts), so the
    # offload saved a file and spared the context nothing. The evidence file keeps the context.
    $hitLines = foreach ($m in $hits) {
        $rel = $m.Path
        if ($rel.StartsWith($repoRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
            $rel = $rel.Substring($repoRoot.Length).TrimStart('\', '/').Replace('\', '/')
        }
        $lineText = $m.Line.Trim()
        if ($lineText.Length -gt 160) { $lineText = $lineText.Substring(0, 160) + '..' }
        "${rel}:$($m.LineNumber): $lineText"
    }
    $joined = ($hitLines -join "`n")
    if ($joined.Length -gt $maxInlineChars) {
        $joined = $joined.Substring(0, $maxInlineChars) + "`n.. (hit list cut at $maxInlineChars chars; all $($hits.Count) hits with context are in the evidence file)"
    }
    $joined
}

$evidenceRelativeForDisplay = $evidenceFile.Substring($repoRoot.Length).TrimStart('\', '/').Replace('\', '/')
Write-Host "source: $sourceDescription | total chars: $charCount | evidence path: $evidenceRelativeForDisplay$crossNote"
Write-Output $excerpt
exit 0
