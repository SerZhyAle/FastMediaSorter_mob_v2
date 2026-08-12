#requires -Version 7.0
<#
.SYNOPSIS
    Ratchet gate: build-invisible double quotes in string resources must never grow.

.DESCRIPTION
    S1567: a double quote inside an Android string resource survives the build only when a backslash
    precedes it after XML decoding. A bare " is a quoting delimiter and AAPT2 drops it; the &quot;
    entity is decoded by the XML parser before that pass, so it is dropped identically. Either
    spelling deletes the character with no warning and no build failure - which is why the defect
    shipped 754 times across twelve locales before anyone read a string closely enough to notice.

    Exempt: a body wrapped in a quote pair with whitespace just inside it. That is Android's
    whitespace-preservation form rather than a visible quote.

    Thin wrapper. The rule itself - what counts as a violation, which files it reads, which baseline
    it ratchets against - lives once in scripts/quality/lib/source-matchers.ps1 and is executed by
    assert-source-gates.ps1, which applies every lexical rule over a SINGLE walk of the tree.

.NOTES
    Exit codes: 0 at or below baseline, 1 above baseline under -Gate, 2 cannot verify.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-string-quote-escaping.ps1 -Gate
    pwsh -NoProfile -File scripts/quality/assert-string-quote-escaping.ps1 -List
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

$forward = @{ Only = 'string-quote-escaping' }
if ($Gate) { $forward.Gate = $true }
if ($UpdateBaseline) { $forward.UpdateBaseline = $true }
if ($List) { $forward.List = $true }
if ($ChangedFiles) { $forward.ChangedFiles = $ChangedFiles }

& (Join-Path $PSScriptRoot 'assert-source-gates.ps1') @forward
exit $LASTEXITCODE
