#requires -Version 7.0
<#
.SYNOPSIS
    Ratchet gate: window-inset handling that ignores displayCutout must never grow (S1338, Rule 17).

.DESCRIPTION
    Rule 17 requires UI to stay inside `systemBars` PLUS `displayCutout` safe bounds in both
    orientations. It had no gate, and the same defect reached the owner twice across the five
    standalone player hosts, which pad for statusBars/navigationBars and never mention the
    cutout - so on a notched or punch-hole device the landscape edge still eats content.

    A file is judged only when it owns a safe-bounds surface: it registers
    `ViewCompat.setOnApplyWindowInsetsListener`, or it calls
    `WindowCompat.setDecorFitsSystemWindows(window, false)` and thereby takes the insets
    problem away from the system. Such a file is compliant when it either names
    `displayCutout()` itself or delegates to `View.applySystemBarInsetPadding()`
    (utils/ViewExtensions.kt), which already takes `maxOf(systemBars, displayCutout)` per edge.

    Known limitation, found by the gate's own fixture: the compliance signal is lexical, so a
    COMMENT naming `displayCutout()` clears the file. The first fixture written for this gate
    said "never reads displayCutout()" in a comment and scored 0. Left as-is - narrowing the
    match to code would need a Kotlin parse, and a comment claiming cutout handling that is
    absent is a lie a reviewer catches, not a pattern the ratchet is meant to police.

    Thin wrapper. The predicate, its extensions, its baseline and its ratchet live once in
    scripts/quality/lib/source-matchers.ps1 and run inside assert-source-gates.ps1's SINGLE
    walk of the tree, so this gate adds a rule to an existing pass rather than a fourteenth
    pwsh start.

.NOTES
    Exit codes (CLAUDE.md Rule 7):
      0  at or below baseline, or a non-gate report run.
      1  -Gate and the count is above the baseline.
      2  cannot verify - the source root does not exist.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-window-insets.ps1 -Gate
    pwsh -NoProfile -File scripts/quality/assert-window-insets.ps1 -Gate -ChangedFiles "a.kt,b.kt"
    pwsh -NoProfile -File scripts/quality/assert-window-insets.ps1 -List
#>
[CmdletBinding()]
param(
    [switch]$Gate,
    [switch]$UpdateBaseline,
    [switch]$List,
    [string[]]$ChangedFiles
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$forward = @{ Only = 'window-insets' }
if ($Gate) { $forward.Gate = $true }
if ($UpdateBaseline) { $forward.UpdateBaseline = $true }
if ($List) { $forward.List = $true }
if ($ChangedFiles) { $forward.ChangedFiles = $ChangedFiles }

& (Join-Path $PSScriptRoot 'assert-source-gates.ps1') @forward
exit $LASTEXITCODE
