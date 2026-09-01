#requires -Version 7.0
<#
.SYNOPSIS
    One definition of what a BlockNeedUserTest debug probe is, shared by the tree gate
    and the closing gate.

.DESCRIPTION
    Two scripts have to agree on the sentence "ticket Sxxxx carries a probe":

      scripts/quality/assert-no-ticket-logs.ps1   - judges the whole tree, after the fact.
      scripts/spec_catalog/check-probe-present.ps1 - judges one ticket, at the moment its
                                                     status is about to become BlockNeedUserTest.

    They must not answer differently for the same ticket, or the closing gate refuses a
    transition the tree gate would have passed (and the operator learns to route around it).
    That is the S1621 rule, and it is not hypothetical here: a Timber call may span several
    physical lines, so a per-line search finds a strictly smaller set than the reconstruction
    below - `Timber.d(\n    "Sxxxx: ..")` is a real probe that a naive grep misses.

    Everything that decides the answer therefore lives here and nowhere else: the probe form,
    the multi-line call reconstruction, and the baseline allow-list parse.
#>

Set-StrictMode -Version Latest

# Opener of a Timber log call. Kept here rather than at a call site because the reconstruction
# below is meaningless without the matching opener definition.
function Get-TimberOpenerRegex {
    return [regex]'Timber\.(?<level>[iwed])\('
}

# Probe form: Timber.d("Sxxxx: ..). The string may sit on a later physical line, so the span is
# matched from its start and \s spans newlines.
function Get-TimberProbeFormRegex {
    return [regex]'^Timber\.d\(\s*"S(?<num>\d{4}):'
}

function Get-TimberProbeFormRegexForId {
    # The same form, pinned to one ticket. Built from the id rather than filtered afterwards so a
    # single-ticket caller cannot accidentally accept a neighbour's probe.
    param([Parameter(Mandatory)][string] $Id)
    return [regex]('^Timber\.d\(\s*"' + [regex]::Escape($Id) + ':')
}

function Get-SanitizedTimberCallSpan {
    # Reconstruct a Timber call from its 'Timber.<level>' start through the ')' that closes its
    # opening '(', tracking string and comment state. Comments are blanked to spaces so a
    # `// Sxxxx` rationale note between arguments is not mistaken for log text; string literals
    # stay verbatim because that is exactly where a probe id lives. Parens inside strings and
    # comments do not skew the depth count. Kotlin raw triple-quoted strings and char literals
    # holding a quote are rare in Timber arguments and out of scope.
    param(
        [Parameter(Mandatory)][string] $Content,
        [Parameter(Mandatory)][int] $PrefixStart,
        [Parameter(Mandatory)][int] $OpenParenIndex
    )
    $sb = [System.Text.StringBuilder]::new()
    [void]$sb.Append($Content.Substring($PrefixStart, $OpenParenIndex - $PrefixStart))
    $depth = 0
    $inStr = $false
    $inLine = $false
    $inBlock = $false
    $i = $OpenParenIndex
    $len = $Content.Length
    $end = -1
    while ($i -lt $len) {
        $c = $Content[$i]
        if ($inLine) {
            if ($c -eq "`n") { $inLine = $false; [void]$sb.Append($c) } else { [void]$sb.Append(' ') }
            $i++; continue
        }
        if ($inBlock) {
            if ($c -eq '*' -and ($i + 1) -lt $len -and $Content[$i + 1] -eq '/') {
                $inBlock = $false; [void]$sb.Append('  '); $i += 2; continue
            }
            [void]$sb.Append($(if ($c -eq "`n") { $c } else { ' ' })); $i++; continue
        }
        if ($inStr) {
            [void]$sb.Append($c)
            if ($c -eq '\' -and ($i + 1) -lt $len) { [void]$sb.Append($Content[$i + 1]); $i += 2; continue }
            if ($c -eq '"') { $inStr = $false }
            $i++; continue
        }
        if ($c -eq '"') { $inStr = $true; [void]$sb.Append($c); $i++; continue }
        if ($c -eq '/' -and ($i + 1) -lt $len -and $Content[$i + 1] -eq '/') { $inLine = $true; [void]$sb.Append('  '); $i += 2; continue }
        if ($c -eq '/' -and ($i + 1) -lt $len -and $Content[$i + 1] -eq '*') { $inBlock = $true; [void]$sb.Append('  '); $i += 2; continue }
        [void]$sb.Append($c)
        if ($c -eq '(') { $depth++ }
        elseif ($c -eq ')') { $depth--; if ($depth -eq 0) { $end = $i; break } }
        $i++
    }
    if ($end -lt 0) { $end = [Math]::Min($len - 1, $OpenParenIndex + 2000) }
    return @{ End = $end; Span = $sb.ToString() }
}

function Get-ProbeSourceFile {
    # Kotlin sources under the given roots, excluding build output. Shared so the two gates
    # cannot disagree about which files even count as source.
    param([Parameter(Mandatory)][string[]] $SourceRoots)
    foreach ($root in $SourceRoots) {
        if (-not (Test-Path -LiteralPath $root)) { continue }
        Get-ChildItem -LiteralPath $root -Recurse -File -Filter '*.kt' -ErrorAction SilentlyContinue |
            Where-Object { $_.FullName -notmatch '[\\/](build|\.gradle|\.kotlin)[\\/]' }
    }
}

function Test-TicketProbeInSource {
    # Does exactly one ticket carry a probe? Returns the first hit as
    # @{ Found = $true; File = <path>; Line = <n> }, or @{ Found = $false }.
    # Stops at the first match: the single-ticket caller needs presence, not an inventory.
    param(
        [Parameter(Mandatory)][string] $Id,
        [Parameter(Mandatory)][string[]] $SourceRoots
    )
    $openerRx = Get-TimberOpenerRegex
    $probeRx = Get-TimberProbeFormRegexForId -Id $Id
    foreach ($file in (Get-ProbeSourceFile -SourceRoots $SourceRoots)) {
        $content = Get-Content -LiteralPath $file.FullName -Raw
        if ([string]::IsNullOrEmpty($content)) { continue }
        # Cheap reject before the per-call reconstruction: a file with no mention of the id at
        # all cannot hold its probe, and most files are in that class.
        if (-not $content.Contains($Id)) { continue }
        foreach ($m in $openerRx.Matches($content)) {
            $openParenIdx = $m.Index + $m.Length - 1
            $span = (Get-SanitizedTimberCallSpan -Content $content -PrefixStart $m.Index -OpenParenIndex $openParenIdx).Span
            if (-not $probeRx.IsMatch($span)) { continue }
            # An opener sitting in a comment is not log text - the tree gate skips those too.
            $lineStart = $content.LastIndexOf("`n", $m.Index) + 1
            $lineText = $content.Substring($lineStart, $m.Index - $lineStart)
            if ($lineText.Contains('//')) { continue }
            $trimmed = $lineText.TrimStart()
            if ($trimmed.StartsWith('*') -or $trimmed.StartsWith('/*')) { continue }
            $lineNo = ($content.Substring(0, $m.Index) -split "`n").Count
            return @{ Found = $true; File = $file.FullName; Line = $lineNo }
        }
    }
    return @{ Found = $false }
}

function Get-ExcusedProbeTickets {
    # Ids allowed to sit in BlockNeedUserTest with no probe, each with a stated reason.
    # Format: "Sxxxx  <reason>", '#' comments and blank lines ignored. A row with an id and no
    # reason does not count - the reason is the whole point of an allow-list over a counter.
    param([Parameter(Mandatory)][string] $BaselinePath)
    $excused = [System.Collections.Generic.HashSet[string]]::new()
    if (-not (Test-Path -LiteralPath $BaselinePath)) { return $excused }
    foreach ($line in Get-Content -LiteralPath $BaselinePath) {
        $trimmedLine = $line.Trim()
        if ($trimmedLine -eq '' -or $trimmedLine.StartsWith('#')) { continue }
        if ($trimmedLine -match '^(?<id>S\d{4})\s+\S') { [void]$excused.Add($Matches['id']) }
    }
    return $excused
}

function Get-ProbeBaselinePath {
    param([Parameter(Mandatory)][string] $RepoRoot)
    return (Join-Path $RepoRoot 'scripts/quality/blockneedusertest-probe-baseline.txt')
}

function Get-ProbeSourceRoot {
    # Module roots, not their src/ subdirectories, matching assert-no-ticket-logs.ps1 exactly.
    # The narrower pair would make the single-ticket check STRICTER than the tree gate - it would
    # refuse a close over a probe the tree gate is content with - and a gate that is stricter than
    # the rule it enforces is the one people route around.
    param([Parameter(Mandatory)][string] $RepoRoot)
    return @(
        (Join-Path $RepoRoot 'app_v2'),
        (Join-Path $RepoRoot 'wear')
    ) | Where-Object { Test-Path -LiteralPath $_ }
}
