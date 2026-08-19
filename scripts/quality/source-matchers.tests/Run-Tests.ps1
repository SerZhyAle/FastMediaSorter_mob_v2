#requires -Version 7.0
<#!
.SYNOPSIS
    Regression tests for structural lexical source matchers.
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot '..\lib\source-matchers.ps1')

function Assert-UnsafeCollectCount([string]$Name, [string]$Source, [int]$Expected) {
    $rule = Get-SourceRules | Where-Object Name -eq 'unsafe-collect'
    $actual = & $rule.CountInText $Source
    if ($actual -ne $Expected) {
        throw "$Name expected $Expected unsafe collect(s), got $actual."
    }
}

function Assert-NamingCount([string]$Name, [string]$Source, [int]$Expected) {
    $rule = Get-SourceRules | Where-Object Name -eq 'class-architecture-naming'
    $actual = & $rule.CountInText $Source
    if ($actual -ne $Expected) {
        throw "$Name expected $Expected naming violation(s), got $actual."
    }
}

function Assert-SourceRule([string]$Name, [string]$ExpectedBaseline) {
    $rule = Get-SourceRules | Where-Object Name -eq $Name
    if ($null -eq $rule) {
        throw "Missing source rule '$Name'."
    }
    if ([string]::IsNullOrWhiteSpace($rule.Baseline)) {
        throw "Source rule '$Name' has no baseline."
    }
    if ($rule.Baseline -ne $ExpectedBaseline) {
        throw "Source rule '$Name' baseline expected '$ExpectedBaseline', got '$($rule.Baseline)'."
    }
}

Assert-SourceRule -Name 'flavor-flags' -ExpectedBaseline 'flavor-flag-baseline.txt'
Assert-SourceRule -Name 'public-mutable-flow' -ExpectedBaseline 'public-mutable-flow-baseline.txt'
Assert-SourceRule -Name 'deprecated-pm-flags' -ExpectedBaseline 'deprecated-pm-flags-baseline.txt'

Assert-UnsafeCollectCount -Name 'nested repeatOnLifecycle' -Expected 0 -Source @'
lifecycleScope.launch {
    repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.state.collect { render(it) }
    }
}
'@

Assert-UnsafeCollectCount -Name 'adjacent repeatOnLifecycle does not exempt collect' -Expected 1 -Source @'
lifecycleScope.launch {
    repeatOnLifecycle(Lifecycle.State.STARTED) { refresh() }
    viewModel.state.collect { render(it) }
}
'@

Assert-UnsafeCollectCount -Name 'flowWithLifecycle chain is safe' -Expected 0 -Source @'
lifecycleScope.launch {
    viewModel.state.flowWithLifecycle(lifecycle).collect { render(it) }
}
'@

Assert-UnsafeCollectCount -Name 'safe and unsafe collects share one launch' -Expected 1 -Source @'
lifecycleScope.launch {
    repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.state.collect { render(it) }
    }
    viewModel.events.collect { handle(it) }
}
'@

Assert-NamingCount -Name 'nested Values holder in a section store is not a naming violation' -Expected 0 -Source @'
package com.sza.fastmediasorter.data.repository.settings

object LauncherSettingsStore {
    data class Values(
        val launcherDesktopLocked: Boolean,
    )
}
'@

Assert-NamingCount -Name 'misnamed repository class is still a naming violation' -Expected 1 -Source @'
package com.sza.fastmediasorter.data.repository

class LauncherDesktopThing {
    fun load() = Unit
}
'@

Write-Output 'source-matchers tests: PASS (9 cases)'
