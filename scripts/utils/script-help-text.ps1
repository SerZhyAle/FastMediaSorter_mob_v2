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

    $block = Get-ScriptCommentBlock -Path $Path
    if (-not $block) { return $null }
    $m = [regex]::Match($block, '(?ims)^[ \t]*\.SYNOPSIS[ \t]*\r?\n(?<body>.*?)(?=^[ \t]*\.[A-Z]+[ \t]*$|\z)')
    if (-not $m.Success) { return $null }
    $body = ($m.Groups['body'].Value -replace '\s+', ' ').Trim()
    if ([string]::IsNullOrWhiteSpace($body)) { return $null }
    return $body
}

function Get-ScriptExitCodesDocumented {
    <#
    .SYNOPSIS
        True when the script's leading help block states an exit-code contract.
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)] [string] $Path)

    $block = Get-ScriptCommentBlock -Path $Path
    if (-not $block) { return $false }
    return [bool]([regex]::IsMatch($block, '(?i)exit\s*codes?\s*:'))
}
