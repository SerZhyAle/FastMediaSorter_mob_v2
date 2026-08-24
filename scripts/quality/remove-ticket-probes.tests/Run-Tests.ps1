#requires -Version 7.0
$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$script = Join-Path $repoRoot 'scripts\quality\remove-ticket-probes.ps1'
$output = (& pwsh -NoProfile -File $script -Id S1060 -WhatIf) -join "`n"
if ($LASTEXITCODE -ne 0) { throw "What-if run failed with $LASTEXITCODE" }
if ($output -notmatch 'remove-ticket-probes: what-if') { throw 'What-if summary was not emitted.' }
Write-Output 'remove-ticket-probes tests: PASS'
