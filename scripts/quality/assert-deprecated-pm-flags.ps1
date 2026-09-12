#requires -Version 7.0
<#
.SYNOPSIS
    Ratchet gate: raw-int PackageManager flag overloads in src/main must never grow (target: 0).

.DESCRIPTION
    S1338: thin wrapper. The rule itself - what counts as a violation, which files it reads,
    which baseline it ratchets against - lives once in scripts/quality/lib/source-matchers.ps1
    and is executed by assert-source-gates.ps1, which applies every lexical rule over a SINGLE
    walk of the tree instead of one walk per gate. This file stays on disk so every existing
    caller keeps working unchanged.

    Behaviour, baseline file and exit codes are identical to the standalone version.

    THIS FILE BEING IN NO RUNNER DOES NOT MEAN THE RULE IS UNGATED (S2517 moved this note here
    from the always-loaded rules page, because this file is where the mistake is made). Every
    closure judges the rule through the `neuroslop-gate` umbrella in scripts/post-change.ps1:
    assert-neuroslop.ps1 forwards to assert-source-gates.ps1 with no -Only filter, so all two
    dozen lexical dimensions are judged, not just the neuroslop nine. On 2026-08-22 an audit
    read this file's absence from the runner list as the rule being unenforced, and was wrong.

.NOTES
    Exit codes: 0 at or below baseline, 1 above baseline under -Gate, 2 cannot verify.
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

$forward = @{ Only = 'deprecated-pm-flags' }
if ($Gate) { $forward.Gate = $true }
if ($UpdateBaseline) { $forward.UpdateBaseline = $true }
if ($List) { $forward.List = $true }
if ($ChangedFiles) { $forward.ChangedFiles = $ChangedFiles }

& (Join-Path $PSScriptRoot 'assert-source-gates.ps1') @forward
exit $LASTEXITCODE
