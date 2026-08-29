#requires -Version 7.0
<#
.SYNOPSIS
    S2243: every field of AppSettings is persisted in settings stores or SettingsRepositoryImpl.

.DESCRIPTION
    Forwards to ssert-source-gates.ps1 -Only appsettings-persistence.

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
