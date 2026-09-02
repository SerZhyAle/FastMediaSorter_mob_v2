<#
.SYNOPSIS
    One home for reading a script's comment-based help when the parser refuses to (S1872).

.DESCRIPTION
    Dot-source this file to get Get-ScriptSynopsis and Get-ScriptExitCodesDocumented. No top-level
    side effects, no exit, no preference variables assigned.

    WHY IT EXISTS. GetHelpContent() on a parsed script returns nothing at all when a #requires
    statement sits above the help block. Measured 2026-08-21 on five header shapes, where HELP
    stands for a leading block comment carrying a .SYNOPSIS entry:

        HELP then param                             -> synopsis found
        HELP then two blank lines then param        -> synopsis found
        #requires then HELP then param              -> NOTHING
        #requires then HELP then blanks then param  -> NOTHING
        #requires then full help then CmdletBinding -> NOTHING

    This repository's convention puts #requires on line 1, so every script that follows the
    convention is invisible to the parser. The consequence was silent and total: the generated
    cheatsheet carried a synopsis for exactly ZERO of its 371 entries, which reads as "nobody
    writes synopses" when in fact many do and none could be read.

    So the parser is tried first and a literal read of the leading comment block is the fallback.
    Repairing the reader was chosen over moving #requires in 370 files: one change against 370, and
    the convention itself is not wrong.

    This file deliberately carries no #requires line of its own, so its help stays readable by
    either route.

    TWO HEADER SYNTAXES (S2339). A leading block comment is not the only way this repository writes
    a header: 123 scripts write theirs as a run of hash-prefixed lines, and not one of those runs
    carries a .SYNOPSIS tag - so the tag-based reader could not merely have under-reported them, it
    could never have seen them. Get-ScriptHeaderText returns whichever syntax comes first, and the
    two readers below differ deliberately in how far they will look for it:

        contract - found by a literal 'Exit codes:' marker     -> may look below a param block
        synopsis - found by POSITION (the header's first line) -> must not

    The asymmetry is the marker. A comment sitting under param() may be about the first statement
    rather than about the script, and only the marker can tell the two apart: measured 2026-09-02,
    all 6 contracts found there were genuine, while 4 of the 12 synopses found there were a comment
    on the next statement.
#>

function Get-ScriptCommentBlock {
    <#
    .SYNOPSIS
        Return the text of a script's leading block comment, or $null.
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)] [string] $Path)

    $text = [IO.File]::ReadAllText($Path)
    $openToken = '<' + '#'
    $closeToken = '#' + '>'
    $open = $text.IndexOf($openToken)
    if ($open -lt 0) { return $null }
    # Only a LEADING block is help: a block comment further down describes whatever follows it, and
    # treating that as the script's purpose would put a random paragraph into the inventory.
    $before = $text.Substring(0, $open)
    if ($before -match '(?m)^\s*[^\s#<]') { return $null }
    $close = $text.IndexOf($closeToken, $open)
    if ($close -lt 0) { return $null }
    return $text.Substring($open + 2, $close - $open - 2)
}

function Get-ScriptHeaderText {
    <#
    .SYNOPSIS
        The script's header comment plus the syntax it was written in.
    .DESCRIPTION
        Returns a hashtable: kind is 'block', 'hash' or 'none', and text is the comment body with
        any leading hash markers stripped. The header is whichever comment comes first once
        #requires lines and blank lines are skipped.
    .PARAMETER Path
        Path to the .ps1 file.
    .PARAMETER AllowBelowParam
        When nothing precedes the script's signature, also look for a hash-line run directly below
        one leading attribute run and one param block. Only callers that then require a literal
        marker may pass this - see the file header. A block comment is deliberately not accepted
        there: no script in the tree writes its header that way, and Get-ScriptCommentBlock's guard
        is what keeps a function's help out of the inventory.
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)] [string] $Path,
        [switch] $AllowBelowParam
    )

    $lines = [IO.File]::ReadAllText($Path) -split "\r?\n"
    $openToken = '<' + '#'

    function Read-HashRun([int] $Start) {
        $acc = New-Object System.Collections.Generic.List[string]
        $j = $Start
        while ($j -lt $lines.Count -and $lines[$j].Trim().StartsWith('#')) {
            $acc.Add(($lines[$j].Trim() -replace '^#+[ \t]?', ''))
            $j++
        }
        return ($acc -join "`n")
    }

    # A shebang is a preamble, not a header, and it is spelled with the same '#' as one: reading it
    # as the header stopped the run before the block comment below it and lost the contract of
    # 11 release scripts. Skipped alongside #requires for the same reason.
    $i = 0
    while ($i -lt $lines.Count -and ($lines[$i].Trim() -eq '' -or $lines[$i].Trim() -match '^#(requires\b|!)')) { $i++ }

    if ($i -lt $lines.Count) {
        $first = $lines[$i].Trim()
        if ($first.StartsWith($openToken)) {
            # Only comments and #requires precede it, so the guard passes and both routes agree.
            $block = Get-ScriptCommentBlock -Path $Path
            # Normalized to LF like the hash branch below, because the .SYNOPSIS terminator anchors
            # on `$` and a CRLF file leaves a \r the character class cannot eat - so the entry ran to
            # the end of the block and swallowed .DESCRIPTION, .PARAMETER and .NOTES with it. It bit
            # only where GetHelpContent() had already failed, which is every #requires script, so
            # 15 cheatsheet rows carried the whole help text as their one-line summary.
            if ($block) { return @{ kind = 'block'; text = ($block -replace "`r`n", "`n") } }
        }
        elseif ($first.StartsWith('#')) {
            return @{ kind = 'hash'; text = (Read-HashRun $i) }
        }
    }

    if ($AllowBelowParam) {
        $j = $i
        while ($j -lt $lines.Count -and $lines[$j].Trim() -match '^\[[A-Za-z].*\]$') { $j++ }
        while ($j -lt $lines.Count -and $lines[$j].Trim() -eq '') { $j++ }
        if ($j -lt $lines.Count -and $lines[$j].Trim() -match '^param[ \t]*\(') {
            $depth = 0
            while ($j -lt $lines.Count) {
                $depth += ([regex]::Matches($lines[$j], '\(')).Count
                $depth -= ([regex]::Matches($lines[$j], '\)')).Count
                $j++
                if ($depth -le 0) { break }
            }
            while ($j -lt $lines.Count -and $lines[$j].Trim() -eq '') { $j++ }
            if ($j -lt $lines.Count -and $lines[$j].Trim().StartsWith('#') -and -not $lines[$j].Trim().StartsWith($openToken)) {
                return @{ kind = 'hash'; text = (Read-HashRun $j) }
            }
        }
    }

    return @{ kind = 'none'; text = '' }
}

function Get-ScriptSynopsis {
    <#
    .SYNOPSIS
        The script's .SYNOPSIS text as one line, or $null when it states none.
    .PARAMETER Path
        Path to the .ps1 file.
    .PARAMETER Ast
        Optional pre-parsed AST, so a caller that already parsed the file does not parse it twice.
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)] [string] $Path,
        $Ast = $null
    )

    if ($Ast) {
        try {
            $help = $Ast.GetHelpContent()
            if ($help -and -not [string]::IsNullOrWhiteSpace($help.Synopsis)) {
                return (($help.Synopsis -replace '\s+', ' ').Trim())
            }
        }
        catch { }
    }

    # No -AllowBelowParam: this reader finds its answer by position, and position alone cannot tell
    # a header from a comment about the first statement (S2339, see the file header).
    $header = Get-ScriptHeaderText -Path $Path
    if ($header.kind -eq 'none') { return $null }

    $m = [regex]::Match($header.text, '(?ims)^[ \t]*\.SYNOPSIS[ \t]*\r?\n(?<body>.*?)(?=^[ \t]*\.[A-Z]+[ \t]*$|\z)')
    if ($m.Success) {
        $body = ($m.Groups['body'].Value -replace '\s+', ' ').Trim()
        if (-not [string]::IsNullOrWhiteSpace($body)) { return $body }
    }

    # A `<# #>` block is comment-based help, where the tag IS the contract - guessing there would
    # promote .DESCRIPTION prose into the inventory. A `#` run is plain prose whose first line is
    # already the summary, and no such run in the tree carries the tag at all (0 of 135).
    if ($header.kind -ne 'hash') { return $null }
    $first = @($header.text -split "`n" | Where-Object { $_.Trim() -ne '' })[0]
    if (-not $first) { return $null }
    $first = ($first -replace '\s+', ' ').Trim()

    # The cheatsheet prints the file name in its own column, so a first line that opens by repeating
    # it says nothing twice. Exact base name only, so no real sentence can be truncated.
    $leaf = [regex]::Escape([IO.Path]::GetFileNameWithoutExtension($Path))
    $stripped = ($first -replace ('^' + $leaf + '(\.ps1)?[ \t]*[-:][ \t]*'), '').Trim()
    if (-not [string]::IsNullOrWhiteSpace($stripped)) { return $stripped }
    return $first
}

function Get-ScriptExitCodesDocumented {
    <#
    .SYNOPSIS
        True when the script's leading help block states an exit-code contract.
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)] [string] $Path)

    # -AllowBelowParam is safe here and only here: the verdict rests on a literal marker, which a
    # comment about the first statement does not carry (S2339, see the file header).
    $header = Get-ScriptHeaderText -Path $Path -AllowBelowParam
    if ($header.kind -eq 'none') { return $false }
    $block = $header.text
    # The parenthesised annotation is accepted because it names the rule the contract answers to -
    # 'Exit codes (CLAUDE.md Rule 7):' is this repository's own convention, and refusing it counted
    # 38 compliant scripts as debt (S2335). Prose is still refused: the optional group must be a
    # parenthesis run, so 'the exit codes of the child are:' does not pass as a contract.
    return [bool]([regex]::IsMatch($block, '(?i)exit\s*codes?\s*(?:\([^\r\n]*\))?\s*:'))
}
