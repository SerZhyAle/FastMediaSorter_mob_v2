#requires -Version 7.0
<#
.SYNOPSIS
    Ratchet gate: lone backslashes in string resources must never grow.

.DESCRIPTION
    S1586: AAPT2 reads a backslash inside a string resource as an escape introducer, not as a
    character. One that introduces a sequence it knows is expanded; one that introduces nothing is
    consumed and the following character survives alone. Either way the backslash never reaches the
    user, the build says nothing, and only reading the shipped string closely reveals the loss.
    Measured with aapt2 compile plus aapt2 dump apc, build-tools 36.1.0:

        a\b               -> ab               backslash consumed
        use / \ : * ?     -> use /  : * ?     two spaces where the character was
        a\\b              -> a\b              correct
        line1\nline2      -> a real newline   intended escape, left alone

    Recognised - and therefore not flagged - are \n, \t, \uXXXX, \', \" and \\. The table is the same
    one ConvertTo-AaptBackslash applies in scripts/utils/set-android-string.ps1 and
    scripts/utils/seed-locale-tranche.ps1; changing it in one place means changing it in all three,
    or this gate flags what those writers just produced.

    Thin wrapper. The rule itself - what counts as a violation, which files it reads, which baseline
    it ratchets against - lives once in scripts/quality/lib/source-matchers.ps1 and is executed by
    assert-source-gates.ps1, which applies every lexical rule over a SINGLE walk of the tree.

.NOTES
    Exit codes: 0 at or below baseline, 1 above baseline under -Gate, 2 cannot verify.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-string-lone-backslash.ps1 -Gate
    pwsh -NoProfile -File scripts/quality/assert-string-lone-backslash.ps1 -List
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

$forward = @{ Only = 'string-lone-backslash' }
if ($Gate) { $forward.Gate = $true }
if ($UpdateBaseline) { $forward.UpdateBaseline = $true }
if ($List) { $forward.List = $true }
if ($ChangedFiles) { $forward.ChangedFiles = $ChangedFiles }

& (Join-Path $PSScriptRoot 'assert-source-gates.ps1') @forward
exit $LASTEXITCODE
