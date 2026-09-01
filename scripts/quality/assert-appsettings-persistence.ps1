#requires -Version 7.0
<#
.SYNOPSIS
    S2243: every field of AppSettings is persisted in settings stores or SettingsRepositoryImpl.

.DESCRIPTION
    Forwards to assert-source-gates.ps1 -Only appsettings-persistence.

    Manual tool: a named hand-run entry point for one dimension of the source-gates umbrella. No runner
    invokes it, and none should - assert-neuroslop.ps1 forwards to the umbrella with no -Only filter, so
    every closure already judges this rule under the neuroslop-gate label. The forwarder exists to run
    this one dimension alone while working on AppSettings persistence.

.PARAMETER Gate
    Gate framing: exit 1 on any violation.
#>
[CmdletBinding()]
param(
    [switch]$Gate,
    [switch]$Explain
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$argsToPass = @('-Only', 'appsettings-persistence')
if ($Gate) { $argsToPass += '-Gate' }
if ($Explain) { $argsToPass += '-Explain' }

& pwsh -NoProfile -File (Join-Path $PSScriptRoot 'assert-source-gates.ps1') @argsToPass
exit $LASTEXITCODE
