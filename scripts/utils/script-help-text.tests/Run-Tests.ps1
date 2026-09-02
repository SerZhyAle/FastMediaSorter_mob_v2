<#
.SYNOPSIS
    S2335: regression suite for scripts/utils/script-help-text.ps1, the header reader.

.DESCRIPTION
    The reader decides two ratchets in assert-script-described.ps1 - how many scripts carry no
    synopsis, and how many exit without a documented contract. Both are counts, so a reader that
    under-reports does not fail loudly: it inflates a debt list, and the ceiling set over that list
    then preserves debt nobody owes. That is how the S2335 defect survived - 38 scripts stating
    `Exit codes (CLAUDE.md Rule 7):` were counted as undocumented because the pattern allowed only
    whitespace between `codes` and the colon.

    So the cases below are about the RECOGNIZER's boundary, not about any one script:
      * every annotated spelling the tree actually uses is accepted;
      * prose that merely mentions exit codes is still refused, which is why the accepted
        annotation is a parenthesis run and not free text;
      * the two shapes the reader is still blind to are pinned as EXPECTED-FALSE, so the S2339
        fix has to change a test on purpose instead of silently widening the contract.

    Hermetic: every fixture is synthesized under temp/S2335/tests/ and removed at the end.
    Nothing outside temp/ is written.

.NOTES
    Exit codes (CLAUDE.md Rule 7):
      0  all cases pass.
      1  at least one case failed.
      2  cannot verify - the module under test is missing.

.EXAMPLE
    pwsh -NoProfile -File scripts/utils/script-help-text.tests/Run-Tests.ps1
#>

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..' '..')).Path
$module = Join-Path $repoRoot 'scripts/utils/script-help-text.ps1'
if (-not (Test-Path -LiteralPath $module -PathType Leaf)) {
    Write-Error "script-help-text tests: module not found at '$module' - the harness resolved the repo root as '$repoRoot'." -ErrorAction Continue
    exit 2
}
. $module

$script:pass = 0
$script:fail = 0
$sandbox = Join-Path $repoRoot 'temp/S2335/tests'
if (Test-Path -LiteralPath $sandbox) { Remove-Item -LiteralPath $sandbox -Recurse -Force }
New-Item -ItemType Directory -Path $sandbox -Force | Out-Null

function Assert-That([string]$name, [bool]$ok, [string]$detail) {
    if ($ok) {
        Write-Host "  PASS  $name" -ForegroundColor Green
        $script:pass++
    }
    else {
        Write-Host "  FAIL  $name -> $detail" -ForegroundColor Red
        $script:fail++
    }
}

# Every fixture ends in `exit 0` because only a script that exits is asked for a contract at all -
# assert-script-described.ps1 skips a library on exactly that test.
function New-Fixture([string]$Name, [string]$Body) {
    $path = Join-Path $sandbox "$Name.ps1"
    Set-Content -LiteralPath $path -Value $Body -Encoding utf8
    return $path
}

function Assert-Contract([string]$name, [string]$body, [bool]$expected, [string]$why) {
    $path = New-Fixture $name $body
    $actual = [bool](Get-ScriptExitCodesDocumented -Path $path)
    Assert-That $name ($actual -eq $expected) "expected: $expected | actual: $actual - $why"
}

Write-Host 'script-help-text: exit-contract recognition' -ForegroundColor Cyan

Assert-Contract 'flat-form' @'
<#
.SYNOPSIS
    Fixture.
.NOTES
    Exit codes:
      0 - fine
#>
exit 0
'@ $true 'the spelling 136 scripts already use'

# The regression this suite exists for. Before S2335 the pattern allowed only whitespace between
# `codes` and the colon, so this exact header - the repository's own convention - read as absent.
Assert-Contract 'rule-annotated-form' @'
<#
.SYNOPSIS
    Fixture.
.NOTES
    Exit codes (CLAUDE.md Rule 7):
      0 - fine
#>
exit 0
'@ $true 'S2335: the annotation names the rule the contract answers to'

Assert-Contract 'ticket-annotated-form' @'
<#
.SYNOPSIS
    Fixture.
.NOTES
    Exit codes (S1070 contract; S1077 added 2 to the diff-scoped path):
      0 - fine
#>
exit 0
'@ $true 'S2335: punctuation inside the annotation must not matter'

Assert-Contract 'nested-parens' @'
<#
.SYNOPSIS
    Fixture.
.NOTES
    Exit codes (stable; mirrors device-ready.ps1 (the probe) where they overlap):
      0 - fine
#>
exit 0
'@ $true 'S2335: the match must reach the LAST paren on the line, not the first'

Assert-Contract 'singular-code' @'
<#
.SYNOPSIS
    Fixture.
.NOTES
    Exit code: 0
#>
exit 0
'@ $true 'singular is a contract too'

# The boundary that decides the pattern's shape. A free-text pattern would accept these, which is
# why the accepted annotation is a parenthesis run.
Assert-Contract 'prose-mentioning-codes' @'
<#
.SYNOPSIS
    Fixture.
.DESCRIPTION
    The exit codes of the child process are: whatever gradle returned.
#>
exit 0
'@ $false 'prose mentioning exit codes states no contract'

Assert-Contract 'prose-deferring-elsewhere' @'
<#
.SYNOPSIS
    Fixture.
.DESCRIPTION
    Exit codes are documented in foo.ps1: read that instead.
#>
exit 0
'@ $false 'a pointer elsewhere is not this script''s contract'

Assert-Contract 'no-contract' @'
<#
.SYNOPSIS
    Fixture with a synopsis and nothing else.
#>
exit 0
'@ $false 'the genuine debt this ratchet counts'

# --- The two header syntaxes, settled by S2339 -------------------------------------------------
# S2335 pinned these as expected-false so the follow-up would have to change a test on purpose.
# S2339 did: a `#`-line header is a header, because 123 scripts write one and none of them carries a
# .SYNOPSIS tag, so the tag-based reader could never have seen them at all.
Assert-Contract 'hash-line-header-is-a-header' @'
# Fixture.
#
# Exit codes: 0 - fine
exit 0
'@ $true 'S2339: form is not what makes a header - CLAUDE.md Rule 7 asks it to list the codes'

# Still refused, and deliberately so: S2339 measured zero scripts writing their header this way, and
# Get-ScriptCommentBlock's guard is the only thing keeping a FUNCTION's help out of the inventory -
# all five non-leading blocks in the tree are exactly that.
Assert-Contract 'block-below-param-is-not-leading' @'
[CmdletBinding()]
param()
<#
.SYNOPSIS
    Fixture.
.NOTES
    Exit codes: 0 - fine
#>
exit 0
'@ $false 'S2339 D-4: the leading-block guard is not relaxed'

# The asymmetry of S2339 D-3, from the contract side: a literal marker is its own evidence, so the
# reader may look past the script's signature for it.
Assert-Contract 'hash-header-below-param-is-read' @'
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string] $Id
)

# Gate for a transition INTO Verified.
#
# Exit codes: 0 - fine
exit 0
'@ $true 'S2339: the six spec_catalog closing gates write their header here'

# A shebang is spelled with the same character as a header and is not one. Reading it as the header
# ended the run before the block below it and lost 11 release scripts' contracts.
Assert-Contract 'shebang-is-not-the-header' @'
#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Fixture.
.NOTES
    Exit codes: 0 - fine
#>
exit 0
'@ $true 'S2339: a shebang is preamble, like #requires'

Write-Host ''
Write-Host 'script-help-text: synopsis recognition' -ForegroundColor Cyan

# S1872's own regression: GetHelpContent() returns nothing when #requires sits above the help
# block, and this repository puts #requires on line 1. The literal fallback is the only reason a
# synopsis is readable at all, so a change that "simplifies" the reader back to the parser must
# fail here.
$reqPath = New-Fixture 'requires-above-help' @'
#requires -Version 7.0
<#
.SYNOPSIS
    Read through the literal fallback.
#>
exit 0
'@
$syn = Get-ScriptSynopsis -Path $reqPath
Assert-That 'synopsis-survives-requires-line' ($syn -eq 'Read through the literal fallback.') `
    "expected: 'Read through the literal fallback.' | actual: '$syn' - S1872 fallback"

$nonePath = New-Fixture 'no-synopsis' @'
<#
.DESCRIPTION
    No synopsis entry here.
#>
exit 0
'@
$synNone = Get-ScriptSynopsis -Path $nonePath
Assert-That 'synopsis-absent-returns-null' ([string]::IsNullOrWhiteSpace($synNone)) `
    "expected: empty | actual: '$synNone'"

$midPath = New-Fixture 'block-not-leading' @'
$x = 1
<#
.SYNOPSIS
    A block describing whatever follows it, not the script.
#>
exit 0
'@
$blk = Get-ScriptCommentBlock -Path $midPath
Assert-That 'non-leading-block-is-not-help' ($null -eq $blk) `
    "expected: null | actual: '$blk' - a mid-file block describes the next statement"

function Assert-Synopsis([string]$name, [string]$body, $expected, [string]$why) {
    $path = New-Fixture $name $body
    $actual = Get-ScriptSynopsis -Path $path
    $ok = if ($null -eq $expected) { [string]::IsNullOrWhiteSpace($actual) } else { $actual -eq $expected }
    Assert-That $name $ok "expected: '$expected' | actual: '$actual' - $why"
}

# S2339: no `#` header in the tree carries a .SYNOPSIS tag (0 of 135), so the first line is the
# summary those files actually wrote.
Assert-Synopsis 'hash-header-first-line-is-the-synopsis' @'
# Combined post-change runner.
# Chains the applicable mechanical post-change steps for a given change type.
exit 0
'@ 'Combined post-change runner.' 'S2339 D-2'

# The other side of D-3. Position is all this reader has, and position cannot tell a header from a
# comment about the first statement - 4 of the 12 hits below param() were exactly that mistake.
Assert-Synopsis 'hash-below-param-is-not-a-synopsis' @'
[CmdletBinding()]
param()

# Convert terminating errors into a non-zero exit.
trap { exit 1 }
exit 0
'@ $null 'S2339 D-3: the synopsis reader never looks below the signature'

# The cheatsheet prints the file name in its own column already.
Assert-Synopsis 'file-name-prefix-is-stripped' @'
# file-name-prefix-is-stripped.ps1 - Detect drift for a spec
exit 0
'@ 'Detect drift for a spec' 'S2339 D-5'

# A block comment is comment-based help, where the tag is the contract - no first-line guess there,
# or .DESCRIPTION prose would be promoted into the inventory.
Assert-Synopsis 'block-without-tag-stays-null' @'
<#
.DESCRIPTION
    Prose that is not a synopsis.
#>
exit 0
'@ $null 'S2339 D-2: the block path still requires the tag'

# Set-Content writes CRLF here, which is the point: the .SYNOPSIS terminator anchors on `$`, and the
# \r it cannot eat used to make the entry run to the end of the block. 15 cheatsheet rows carried a
# whole help text as their one-line summary.
Assert-Synopsis 'crlf-block-does-not-swallow-the-rest' @'
#requires -Version 7.0
<#
.SYNOPSIS
    Just the synopsis.
.DESCRIPTION
    This must not end up inside the synopsis.
#>
exit 0
'@ 'Just the synopsis.' 'S2339: CRLF-safe terminator'

Remove-Item -LiteralPath $sandbox -Recurse -Force -ErrorAction SilentlyContinue

Write-Host ''
Write-Host ("script-help-text tests: expected: 0 failures | actual: {0} failed, {1} passed" -f $script:fail, $script:pass)
if ($script:fail -gt 0) {
    Write-Error "script-help-text tests: FAIL - $($script:fail) case(s) failed." -ErrorAction Continue
    exit 1
}
Write-Host 'script-help-text tests: PASS' -ForegroundColor Green
exit 0
