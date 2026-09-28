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
Assert-SourceRule -Name 'ui-hardcoded-io-dispatcher' -ExpectedBaseline 'ui-hardcoded-io-dispatcher-baseline.txt'
Assert-SourceRule -Name 'deprecated-pm-flags' -ExpectedBaseline 'deprecated-pm-flags-baseline.txt'
Assert-RuleCount -Rule 'ui-hardcoded-io-dispatcher' -Name 'literal occurrences' -Expected 2 -Lines @(
    'withContext(Dispatchers.IO) { load() }',
    'launch(Dispatchers.IO) { save() }',
    'withContext(ioDispatcher) { update() }')
$uiIoRule = Get-SourceRules | Where-Object Name -eq 'ui-hardcoded-io-dispatcher'
foreach ($path in @(
        'app_v2/src/main/java/com/sza/fastmediasorter/ui/MainViewModel.kt',
        'app_v2/src/test/java/com/sza/fastmediasorter/ui/MainViewModelTest.kt',
        'app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/XrViewModel.kt',
        'wear/src/main/java/com/sza/fastmediasorter/wear/ui/WatchViewModel.kt')) {
    if ($path -notmatch $uiIoRule.PathFilter) { throw "UI IO rule missed $path." }
}
foreach ($path in @(
        'app_v2/src/main/java/com/sza/fastmediasorter/data/MediaRepository.kt',
        'wear/src/main/java/com/sza/fastmediasorter/wear/data/WatchRepository.kt')) {
    if ($path -match $uiIoRule.PathFilter) { throw "UI IO rule included $path." }
}
Assert-SourceRule -Name 'recycled-checked-listener' -ExpectedBaseline 'recycled-checked-listener-baseline.txt'

Assert-RuleCount -Rule 'recycled-checked-listener' -Name 'old row listener' -Expected 1 -Lines @(
    'class ExampleAdapter {',
    '  fun bind(item: Item) {',
    '    binding.checkbox.isChecked = item.selected',
    '    binding.checkbox.setOnCheckedChangeListener { _, _ -> save(item) }',
    '  }',
    '}')
Assert-RuleCount -Rule 'recycled-checked-listener' -Name 'detach before bind' -Expected 0 -Lines @(
    'fun bind(item: Item) {',
    '  binding.checkbox.setOnCheckedChangeListener(null)',
    '  binding.checkbox.isChecked = item.selected',
    '  binding.checkbox.setOnCheckedChangeListener { _, _ -> save(item) }',
    '}')
Assert-RuleCount -Rule 'recycled-checked-listener' -Name 'detach belongs to same view' -Expected 1 -Lines @(
    'fun bind(item: Item) {',
    '  binding.first.setOnCheckedChangeListener(null)',
    '  binding.second.isChecked = item.selected',
    '  binding.second.setOnCheckedChangeListener { _, _ -> save(item) }',
    '}')
Assert-RuleCount -Rule 'recycled-checked-listener' -Name 'reattached listener needs another detach' -Expected 1 -Lines @(
    'fun bind(item: Item) {',
    '  cb.setOnCheckedChangeListener(null)',
    '  cb.isChecked = item.selected',
    '  cb.setOnCheckedChangeListener { _, _ -> save(item) }',
    '  cb.isChecked = item.other',
    '}')
Assert-RuleCount -Rule 'recycled-checked-listener' -Name 'other state renderer' -Expected 0 -Lines @(
    'fun bind(item: Item) { checkbox.setOnCheckedChangeListener { _, _ -> save(item) } }',
    'fun render(state: State) {',
    '  checkbox.isChecked = state.selected',
    '}')
Assert-RuleCount -Rule 'recycled-checked-listener' -Name 'listener-free radio bind' -Expected 0 -Lines @(
    'fun bind(item: Item) {',
    '  radio.isChecked = item.selected',
    '}')
Assert-RuleCount -Rule 'recycled-checked-listener' -Name 'unrelated listened view' -Expected 0 -Lines @(
    'fun bind(item: Item) {',
    '  radio.isChecked = item.selected',
    '  checkbox.setOnCheckedChangeListener { _, _ -> save(item) }',
    '}')

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

# S2326 - hardcoded-drive-path. The baseline is zero, which proves only that nothing is there
# now; these cases prove what the rule does when a literal appears.
Assert-SourceRule -Name 'hardcoded-drive-path' -ExpectedBaseline 'hardcoded-drive-path-baseline.txt'

Assert-RuleCount -Rule 'hardcoded-drive-path' -Name 'a literal drive path in code is a hit' `
    -Expected 1 -Lines @(
    '$ProjectRoot = "c:\GD\WORK\FastMediaSorter"')

Assert-RuleCount -Rule 'hardcoded-drive-path' -Name 'forward slashes count too' `
    -Expected 1 -Lines @(
    '$sink = ''D:/deliveries/apk''')

Assert-RuleCount -Rule 'hardcoded-drive-path' -Name 'two literals on separate lines are two hits' `
    -Expected 2 -Lines @(
    '$a = "c:\one\path"',
    '$b = "e:\other\path"')

# The ignore rules from the matcher, one case each.
Assert-RuleCount -Rule 'hardcoded-drive-path' -Name 'a URL scheme is not a drive' `
    -Expected 0 -Lines @(
    '$url = "https://example.org/x"',
    '$repo = "git+ssh://host/y"')

Assert-RuleCount -Rule 'hardcoded-drive-path' -Name 'an env-derived path is not a literal' `
    -Expected 0 -Lines @(
    '$sdk = Join-Path $env:LOCALAPPDATA "Android\Sdk"',
    '$root = "$env:ProgramFiles\7-Zip\7z.exe"')

Assert-RuleCount -Rule 'hardcoded-drive-path' -Name 'a whole-line comment carries no binding' `
    -Expected 0 -Lines @(
    '#   .\install.ps1 -ApkPath C:\custom\path.apk',
    '# gh is often absent from PATH (e.g. C:\Program Files\GitHub CLI)')

# The shape that made the first draft of this rule report two live gate scripts.
Assert-RuleCount -Rule 'hardcoded-drive-path' -Name 'a regex character class is not a drive path' `
    -Expected 0 -Lines @(
    'if ($line -match ''^\s*\|[\s:\-|]+\|?\s*$'') { $bodyStart++ }',
    'if ($lines[$i] -match ''^\|[\s:\-\|]+$'') { [void]$sepIdx.Add($i) }')

# A trailing comment must NOT blank the code before it, or a literal hides behind one.
Assert-RuleCount -Rule 'hardcoded-drive-path' -Name 'a trailing comment does not hide the literal' `
    -Expected 1 -Lines @(
    '$dst = "d:\out"  # delivery sink')

# Find- must agree with Measure-: -List printing a different set than the gate counted is the
# defect CLAUDE.md S1621 names - the verdict and the operator''s view disagreeing.
$drivePathRule = Get-SourceRules | Where-Object Name -eq 'hardcoded-drive-path'
$locatorSource = @(
    '# C:\ignored\comment',
    '$a = "c:\one\path"',
    '$url = "https://example.org"',
    '$b = "e:\other\path"') -join "`r`n"
$located = @(& $drivePathRule.LocateInText $locatorSource)
if ($located.Count -ne 2) {
    throw "hardcoded-drive-path locator expected 2 line(s), got $($located.Count)."
}
if ($located[0] -ne 2 -or $located[1] -ne 4) {
    throw "hardcoded-drive-path locator expected lines 2 and 4, got $($located -join ', ')."
}
if ((& $drivePathRule.CountInText $locatorSource) -ne $located.Count) {
    throw 'hardcoded-drive-path: locator and counter disagree.'
}

# S3784 - notify-dataset-changed. The metric is a token count: a comment mention counts too, which
# is why the sweep reworded the one comment that carried the literal.
Assert-SourceRule -Name 'notify-dataset-changed' -ExpectedBaseline 'notify-dataset-changed-baseline.txt'

Assert-RuleCount -Rule 'notify-dataset-changed' -Name 'a real call is a hit' `
    -Expected 1 -Lines @(
    'adapter.notifyDataSetChanged()')

Assert-RuleCount -Rule 'notify-dataset-changed' -Name 'a comment mention is a hit too' `
    -Expected 1 -Lines @(
    '// avoiding a full-list notifyDataSetChanged() flicker')

Assert-RuleCount -Rule 'notify-dataset-changed' -Name 'ranged and diff paths are clean' `
    -Expected 0 -Lines @(
    'adapter.notifyItemRangeChanged(0, adapter.itemCount)',
    'adapter.submitList(items)')

# End to end, because every case above calls CountInText directly and so proves nothing about
# Roots, PathFilter or the exit code - the rule could be correct and still never reach scripts/.
# The probe file is removed in a finally block: left behind it would turn every later closure red
# for a reason belonging to no ticket.
$probeRelative = 'scripts/zz-hardcoded-drive-path-probe.ps1'
# Located by the resolver this rule exists to enforce, rather than by counting `..` - which is the
# mistake ADR-2 is about, and which this very block got wrong on its first run.
. (Join-Path $PSScriptRoot '..\..\utils\project-paths.ps1')
$probePath = Get-ProjectPath -Relative $probeRelative
$gate = Get-ProjectPath -Relative 'scripts/quality/assert-source-gates.ps1'
try {
    Set-Content -LiteralPath $probePath -Value '$sink = "c:\GD\WORK\FastMediaSorter"' -Encoding UTF8
    $gateOutput = & pwsh -NoProfile -File $gate -Only hardcoded-drive-path -Gate -List 2>&1
    $gateExit = $LASTEXITCODE
    $gateText = ($gateOutput | ForEach-Object { $_.ToString() }) -join "`n"
    if ($gateExit -eq 0) {
        throw "hardcoded-drive-path: a planted literal left the gate green (exit 0).`n$gateText"
    }
    if ($gateText -notmatch 'zz-hardcoded-drive-path-probe\.ps1') {
        throw "hardcoded-drive-path: the gate failed but never named the offending file.`n$gateText"
    }
}
finally {
    Remove-Item -LiteralPath $probePath -Force -ErrorAction SilentlyContinue
}
if (Test-Path -LiteralPath $probePath) {
    throw "hardcoded-drive-path: probe file survived at $probeRelative - remove it before committing."
}

Write-Output 'source-matchers tests: PASS'
