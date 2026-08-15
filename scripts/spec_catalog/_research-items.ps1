# Shared parser for a spec's research / open-questions section.
#
# Dot-sourced, never invoked: this file sets no preferences, holds no mutable state and
# knows nothing about the catalog journal, so a consumer inherits only these functions.
# That is the point of it being separate from `_lib.ps1` - `preview.ps1` sits on the
# `/spec-next` hot path and cannot afford `_lib.ps1`'s `Set-StrictMode -Version Latest`,
# under which its own `$rec.statusNote` read throws on any record without a note (S1621).
#
# Consumers: `_lib.ps1` (which re-exports these to the whole spec_catalog CLI),
# `check-open-items-carried.ps1` (the closing gate) and `preview.ps1` (the operator's
# count). One parse, one definition - before S1621 the gate and the preview disagreed
# on 163 of 1601 spec files, including three where the preview printed 0 against live
# open items.
#
# Compatible with PowerShell 5.1 and 7+, and safe to load under Set-StrictMode Latest.

# Both this file and every consumer live in scripts/spec_catalog/, so the repo root is
# the same two levels up whichever of them $PSScriptRoot was bound from.
$script:ResearchSpecRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path

function Get-ResearchSectionHeadingPattern {
    # Heading spellings that mean "the open questions belonging to the owner".
    # Matched against heading TEXT, never against a section number: '## 6.' is not a
    # stable slot - live specs put "Критерии готовности", "Риски", "Implementation"
    # and "Owner decisions" there, while the research section itself drifts to other
    # numbers. Keying on the number both misses real sections and reads unrelated ones.
    return @(
        '(?i)\bResearch\s+items?\b',
        '(?i)\bOpen\s+Questions?\b',
        '(?i)\bOpen\s+items?\b',
        '(?i)Открытые\s+вопрос'
    )
}

function Get-OpenStatusPattern {
    # The status line of an item still open. Nine spellings occur in live specs:
    # bold and bare markers, both languages, with or without a trailing colon, and
    # `Open` followed by a period, a comma, a hyphen and prose, or a parenthetical.
    # `Open` is therefore matched as a WORD, not as the whole line - a whole-line
    # match misses the most common informative form and undercounts the real debt
    # by half (measured 2026-08-13: 67 whole-line matches against 134 word matches).
    #
    # Deliberately NOT anchored to the start of the line: a flat-bullet spec writes
    # the marker mid-line, after the item title (`- **Title.** Status: Open. ..`),
    # and an anchored pattern reports such a spec as clean. The marker itself is
    # specific enough to carry the match - the words must be contiguous.
    return '(?:^|\s)(?:\*\*)?(?:Статус|Status)\s*:?(?:\*\*)?\s*:?\s*Open\b'
}

function Get-CarrierTokenPattern {
    # The literal token handing an open question to another ticket (S1607). Read
    # literally for the same reason `Blocker: Sxxxx` is: the surrounding prose names
    # neighbours as often as owners, so a bare id cannot say which way the arrow points.
    return '(?:Carrier|Носитель)\s*:?\s*\*{0,2}\s*:?\s*(S\d{4})'
}

function Resolve-ResearchSpecPath {
    # Repo-relative ('PLAN/S0290_*.md') or already-absolute path -> absolute path.
    # Duplicated from _lib.ps1's Resolve-SpecPath rather than imported: this file must
    # stay free of _lib.ps1 so it can be loaded without it.
    param([Parameter(Mandatory)][string] $PathRef)
    $p = $PathRef -replace '/', '\'
    if ([System.IO.Path]::IsPathRooted($p)) { return $p }
    return (Join-Path $script:ResearchSpecRoot $p)
}

function Get-SpecSectionLines {
    # Return the lines of one top-level spec section, each carrying its 1-based file
    # line number so a caller can name the offending line back to the owner.
    #
    # The section is located by matching its HEADING TEXT against -HeadingPattern and
    # ends at the next '## ' heading. An absent section returns an empty collection
    # rather than throwing: a spec that never wrote the section is a different
    # condition from a malformed one, and callers treat "nothing to check" as a pass.
    param(
        [Parameter(Mandatory, ParameterSetName = 'Path')][string] $Path,
        [Parameter(Mandatory, ParameterSetName = 'Lines')][AllowEmptyCollection()][string[]] $Lines,
        [Parameter(Mandatory)][string[]] $HeadingPattern
    )
    if ($PSCmdlet.ParameterSetName -eq 'Path') {
        $abs = Resolve-ResearchSpecPath -PathRef $Path
        if (-not (Test-Path -LiteralPath $abs -PathType Leaf)) { return @() }
        # -Encoding UTF8 is load-bearing: spec files are UTF-8 and carry Russian prose,
        # and without it the offending line is echoed back as mojibake, which defeats
        # the point of naming the line the owner has to fix.
        $Lines = @(Get-Content -LiteralPath $abs -Encoding UTF8)
    }

    $out = New-Object System.Collections.Generic.List[object]
    $inSection = $false
    for ($i = 0; $i -lt $Lines.Count; $i++) {
        $line = $Lines[$i]
        if ($line -match '^##\s+(.+)$') {
            if ($inSection) { break }
            $heading = $Matches[1].Trim()
            foreach ($pattern in $HeadingPattern) {
                if ($heading -match $pattern) { $inSection = $true; break }
            }
            continue
        }
        if ($inSection) {
            $out.Add([pscustomobject]@{ LineNumber = $i + 1; Text = $line })
        }
    }
    # Returned unwrapped, not as `,$out.ToArray()`: every caller guards with @(),
    # and the unary comma would make that guard wrap the array a second time,
    # yielding a single-element collection whose one member is the real section.
    return $out.ToArray()
}

function Get-ResearchItems {
    # One object per item of the research section, carrying everything both consumers
    # need: whether it is open, which line says so, and which ticket carries it.
    #
    # -Path locates the section itself; -SectionLines takes an already-extracted one,
    # which is how the closing gate tells "no section at all" from "section with no
    # items" without reading the file twice.
    param(
        [Parameter(Mandatory, ParameterSetName = 'Path')][string] $Path,
        [Parameter(Mandatory, ParameterSetName = 'Lines')][AllowEmptyCollection()][object[]] $SectionLines
    )
    if ($PSCmdlet.ParameterSetName -eq 'Path') {
        $SectionLines = @(Get-SpecSectionLines -Path $Path -HeadingPattern (Get-ResearchSectionHeadingPattern))
    }
    if ($null -eq $SectionLines -or $SectionLines.Count -eq 0) { return @() }

    $statusOpenRx = [regex](Get-OpenStatusPattern)
    $carrierRx    = [regex](Get-CarrierTokenPattern)
    # An item starts at column zero. Sub-bullets of the template shape are indented, so
    # this splits `1. **Title**` items correctly and still treats a flat bullet list -
    # one question per unindented bullet, no title wrapper - as one item per bullet.
    $itemStartRx  = [regex]'^(?:\d+\.|[-*])\s+\S'

    $items = New-Object System.Collections.Generic.List[object]
    $current = $null
    foreach ($line in $SectionLines) {
        if ($itemStartRx.IsMatch($line.Text)) {
            # A flat-bullet item is one long line carrying title, status and prose at once,
            # so the title is clipped - LineNumber points at the full text.
            $title = $line.Text.Trim()
            if ($title.Length -gt 90) { $title = $title.Substring(0, 90) + '..' }
            $current = [pscustomobject]@{
                Title         = $title
                LineNumber    = $line.LineNumber
                Lines         = (New-Object System.Collections.Generic.List[object])
                IsOpen        = $false
                OpenLine      = $null
                Carrier       = $null
                IsPlaceholder = $false
            }
            $items.Add($current)
        }
        # The starting line joins its own item: in a flat list it carries the status itself.
        if ($null -ne $current) { $current.Lines.Add($line) }
    }

    foreach ($item in $items) {
        foreach ($line in $item.Lines) {
            if (-not $item.IsOpen -and $statusOpenRx.IsMatch($line.Text)) {
                $item.IsOpen = $true
                $item.OpenLine = $line
                # An unfilled template placeholder ('Open / Resolved', copied verbatim from
                # .claude/templates/strategic-spec.md) counts as open and is flagged as such:
                # the item was never decided either way.
                $item.IsPlaceholder = [bool]($line.Text -match 'Open\s*/\s*Resolved')
            }
            if ($null -eq $item.Carrier) {
                $m = $carrierRx.Match($line.Text)
                if ($m.Success) { $item.Carrier = $m.Groups[1].Value }
            }
        }
    }

    # Unwrapped for the same reason Get-SpecSectionLines is - callers guard with @().
    return $items.ToArray()
}
