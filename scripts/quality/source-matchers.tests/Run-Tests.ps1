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

function Assert-RuleCount([string]$Rule, [string]$Name, [string[]]$Lines, [int]$Expected) {
    $r = Get-SourceRules | Where-Object Name -eq $Rule
    if ($null -eq $r) { throw "Missing source rule '$Rule'." }
    # CRLF is joined explicitly rather than taken from a here-string: these rules anchor on `\r?$`,
    # and a here-string would inherit whatever line endings this file was checked out with, so the
    # very defect the anchor exists to prevent could pass on one machine and fail on another.
    $actual = & $r.CountInText (($Lines -join "`r`n") + "`r`n")
    if ($actual -ne $Expected) {
        throw "$Rule / $Name expected $Expected hit(s), got $actual."
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

Assert-SourceRule -Name 'ui-imports-data' -ExpectedBaseline 'ui-imports-data-baseline.txt'
Assert-SourceRule -Name 'ui-imports-room' -ExpectedBaseline 'ui-imports-room-baseline.txt'
Assert-SourceRule -Name 'ui-imports-impl' -ExpectedBaseline 'ui-imports-impl-baseline.txt'
Assert-SourceRule -Name 'viewmodel-imports-repository' `
    -ExpectedBaseline 'viewmodel-imports-repository-baseline.txt'

Assert-RuleCount -Rule 'ui-imports-data' -Name 'counts data imports, ignores domain and platform' `
    -Expected 2 -Lines @(
    'import com.sza.fastmediasorter.data.cloud.CloudFolder',
    'import com.sza.fastmediasorter.data.local.db.ResourceDao',
    'import com.sza.fastmediasorter.domain.model.Resource',
    'import androidx.fragment.app.Fragment')

Assert-RuleCount -Rule 'ui-imports-data' -Name 'a commented-out import is not an import' `
    -Expected 0 -Lines @(
    '// import com.sza.fastmediasorter.data.cloud.CloudFolder',
    '    import com.sza.fastmediasorter.data.cloud.CloudFolder')

Assert-RuleCount -Rule 'ui-imports-room' -Name 'Dao and Entity across CRLF lines' `
    -Expected 2 -Lines @(
    'import com.sza.fastmediasorter.data.local.db.StreamSourceEntity',
    'import com.sza.fastmediasorter.data.local.db.ResourceDao',
    'import com.sza.fastmediasorter.data.cloud.CloudFolder')

Assert-RuleCount -Rule 'ui-imports-room' -Name 'a class merely ending in Data is not Room' `
    -Expected 0 -Lines @('import com.sza.fastmediasorter.data.common.EntityHolder')

Assert-RuleCount -Rule 'ui-imports-impl' -Name 'impl counted, interface not' `
    -Expected 1 -Lines @(
    'import com.sza.fastmediasorter.data.repository.ResumeStateRepositoryImpl',
    'import com.sza.fastmediasorter.domain.repository.ResumeStateRepository')

Assert-RuleCount -Rule 'viewmodel-imports-repository' -Name 'domain repository import in a ViewModel' `
    -Expected 1 -Lines @(
    'import com.sza.fastmediasorter.domain.repository.ResourceRepository',
    'import com.sza.fastmediasorter.domain.usecase.LoadResourcesUseCase')

Write-Output 'source-matchers tests: PASS (19 cases)'
