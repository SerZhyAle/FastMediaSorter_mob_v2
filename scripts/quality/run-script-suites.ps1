#requires -Version 7.0
<#
.SYNOPSIS
    S2122: the run site for the repository's regression suites - one implementation, three callers.

.DESCRIPTION
    The repository holds 38 suites named `*.tests/Run-Tests.ps1`. Until this script existed, none of
    them was invoked from anywhere: not from a.ps1, not from post-change.ps1, not from the fast-gate
    batch, not from the release-scope runner. A suite nobody runs is indistinguishable from an absent
    one - it looks like protection and observes nothing. The first sweep of all 37 (2026-08-27) found
    two real failures nobody knew about, one of which had been red since the ticket that introduced
    it closed Verified.

    This script is the single implementation behind all three call sites, so the modes cannot drift:
      - per-ticket closure   post-change.ps1 passes -ChangedFiles and only the neighbouring suites run
      - release scope        assert-release-scope-gates.ps1 passes no changed set, so every suite runs
      - by hand              a.ps1 fs, with or without a changed set

    DISCOVERY IS BY PATH CONVENTION, NOT BY A REGISTRY (strategic ADR-2). A registry has to be kept in
    sync, and an out-of-sync registry is the exact failure this ticket exists to fix. A new suite is
    picked up by being placed, with no edit here and no entry anywhere.

    SUBJECT RESOLUTION - how a changed file selects a suite. The first four rules are pure path
    arithmetic; the fifth is the suite's own declaration, used only where the path cannot say.
      1. sibling script     <dir>/<name>.ps1              -> <dir>/<name>.tests/
      2. sibling library    <dir>/lib/<name>.ps1          -> <dir>/<name>.tests/
      3. sibling directory  <dir>/<name>/**               -> <dir>/<name>.tests/
      4. nested tests dir   <dir>/<name>/**               -> <dir>/<name>/tests/
      5. declared subject   a `# Subject: <path>[, <path>]` line in the suite's own header
    Rule 5 is not a registry: it lives INSIDE the suite it describes, so it cannot fall out of sync
    with a file it is not part of. It exists because six suites name something the path cannot reach -
    `oss-notices.tests` tests `generate-oss-notices.ps1`, the two adb suites drive `lib/ui-tree.ps1` -
    and a suite with no reachable subject is a suite the per-ticket gate can never fire, which is this
    ticket's own defect wearing a different hat. A suite that declares nothing and resolves nothing is
    REPORTED as such by -ListOnly rather than passing silently.
    Editing a file inside a suite's own directory always selects that suite.

    "FAILED" AND "COULD NOT VERIFY" ARE DIFFERENT ANSWERS. A suite exiting 2 says its environment is
    incomplete - `drift-check.tests` returns 2 when `rg` is absent from PATH - not that it found a
    defect. Merging the two would make a green tree look broken, and a run site that cries wolf is
    silenced. So exit 2 is advisory in the per-ticket closure (a developer machine missing an optional
    tool must still be able to close a ticket) and fatal before a release (where the environment must
    be complete). Both readings come from this one classifier; only the caller's -Gate switch differs.

.PARAMETER ChangedFiles
    Changed-file set. Only the suites whose subject is in the set run. Accepts a comma-separated
    string, because `pwsh -File` binds `-ChangedFiles a.ps1,b.ps1` as ONE array element. Absent means
    every discovered suite runs.

.PARAMETER Gate
    Fail-closed on an incomplete environment: exit 2 when a suite could not verify. Without it the
    same condition is reported and the run exits 0.

.PARAMETER Quiet
    Print the per-suite rows only for suites that did not pass, plus the summary line.

.PARAMETER ListOnly
    Print the selected suites and their resolved subjects, run nothing, exit 0.

.PARAMETER Root
    Discovery root (default: the repository's scripts/ directory). The suite's own tests point this
    at a fixture tree so they never execute the repository's real suites.

.PARAMETER Json
    Write the selection as JSON to this path, in addition to the human report. In the run mode that
    is the per-suite result set; under -ListOnly it is the DISCOVERY set - one record per selected
    suite carrying Suite (its repo-relative path), Subjects and Resolved. S2411 made the list mode
    honour it so a checker about a property OF a suite reads the same selection the runner would
    execute, instead of walking the tree a second time and judging a set nobody runs.

.PARAMETER Help
    Show help documentation and usage.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/run-script-suites.ps1
.EXAMPLE
    pwsh -NoProfile -File scripts/quality/run-script-suites.ps1 -ChangedFiles "scripts/post-change.ps1"
.EXAMPLE
    pwsh -NoProfile -File scripts/quality/run-script-suites.ps1 -ListOnly

.NOTES
    Exit codes (CLAUDE.md Rule 7):
      0  every selected suite passed, or none was selected, or -ListOnly.
      1  at least one suite failed.
      2  no suite failed, but at least one could not verify and -Gate was passed.
#>
[CmdletBinding()]
param(
    [string[]]$ChangedFiles,
    [switch]$Gate,
    [switch]$Quiet,
    [switch]$ListOnly,
    [string]$Root,
    [string]$Json,
    [switch]$Help
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if ($Help) {
    Get-Help $PSCommandPath
    exit 0
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..')).Path
$scanRoot = if ($Root) {
    if (-not (Test-Path -LiteralPath $Root)) {
        Write-Error "run-script-suites: cannot verify - discovery root not found: $Root" -ErrorAction Continue
        exit 2
    }
    (Resolve-Path -LiteralPath $Root).Path
}
else {
    Join-Path $repoRoot 'scripts'
}

# A suite that runs the closure facade would re-enter this runner through the facade's own gate.
# The nesting is bounded in practice, but an unbounded one is a fork bomb with a friendly name, so
# the inner run reports itself skipped instead of recursing.
#
# S2411 exempts -ListOnly: that mode executes no suite, so it cannot recurse, and the guard was
# refusing a question rather than a fork. Measured on this ticket's own closure - assert-suite-tracked
# asked for the selection from inside a suite run, got "skipped, exit 0" and no file, and had to
# answer CANNOT VERIFY. A recursion guard producing a silent non-answer is this ticket's own defect
# class, so the exemption is the fix and the guard keeps every case that can actually spawn a child.
if ($env:FMS_SCRIPT_SUITE_RUNNER -eq '1' -and -not $ListOnly) {
    Write-Host 'run-script-suites: SKIP - already running inside a suite run (re-entry guard).' -ForegroundColor DarkGray
    exit 0
}

function ConvertTo-RelPath([string]$Absolute) {
    $normalized = $Absolute -replace '\\', '/'
    $rootNormalized = ($repoRoot -replace '\\', '/').TrimEnd('/')
    if ($normalized.StartsWith("$rootNormalized/", [StringComparison]::OrdinalIgnoreCase)) {
        return $normalized.Substring($rootNormalized.Length + 1)
    }
    return $normalized
}

function Get-DeclaredSubject([string]$SuitePath) {
    # Read the header only: the declaration is a contract line, not something buried mid-file.
    $head = Get-Content -LiteralPath $SuitePath -TotalCount 40 -ErrorAction SilentlyContinue
    $declared = @()
    foreach ($line in $head) {
        if ($line -match '^\s*#?\s*Subject:\s*(.+?)\s*$') {
            $declared += ($Matches[1] -split ',' | ForEach-Object { $_.Trim() } | Where-Object { $_ })
        }
    }
    return @($declared | ForEach-Object { ($_ -replace '\\', '/').TrimEnd('/') })
}

function Get-SuiteDescriptor([string]$SuitePath) {
    $suiteDir = Split-Path -Parent $SuitePath
    $suiteDirRel = ConvertTo-RelPath $suiteDir
    $dirName = Split-Path -Leaf $suiteDir
    $parentRel = ConvertTo-RelPath (Split-Path -Parent $suiteDir)

    $fileSubjects = [System.Collections.Generic.List[string]]::new()
    $dirSubjects = [System.Collections.Generic.List[string]]::new()

    if ($dirName -ieq 'tests') {
        # Nested form: scripts/doc-drift/tests/ -> the whole scripts/doc-drift/ directory.
        $dirSubjects.Add($parentRel)
    }
    else {
        $base = $dirName -replace '(?i)\.tests$', ''
        $baseRel = if ($parentRel) { "$parentRel/$base" } else { $base }
        $fileSubjects.Add("$baseRel.ps1")
        $fileSubjects.Add("$parentRel/lib/$base.ps1")
        $dirSubjects.Add($baseRel)
    }

    foreach ($declaredPath in (Get-DeclaredSubject $SuitePath)) {
        if ($declaredPath -match '\.[A-Za-z0-9]+$') { $fileSubjects.Add($declaredPath) }
        else { $dirSubjects.Add($declaredPath) }
    }

    # A change inside the suite's own directory always selects it.
    $dirSubjects.Add($suiteDirRel)

    # Deduplicated: a suite that both sits beside its subject and declares it resolves the same path
    # twice, which reads in -ListOnly as two different subjects with the same name.
    $existing = @(@(
            @($fileSubjects | Where-Object { Test-Path -LiteralPath (Join-Path $repoRoot $_) -PathType Leaf }) +
            @($dirSubjects | Where-Object { $_ -ne $suiteDirRel -and (Test-Path -LiteralPath (Join-Path $repoRoot $_) -PathType Container) })
        ) | Select-Object -Unique)

    return [pscustomobject]@{
        Path         = $SuitePath
        Rel          = ConvertTo-RelPath $SuitePath
        FileSubjects = @($fileSubjects)
        DirSubjects  = @($dirSubjects)
        Subjects     = @($existing)
        Resolved     = ($existing.Count -gt 0)
    }
}

function Test-SuiteSelected([object]$Descriptor, [string[]]$Changed) {
    foreach ($changedPath in $Changed) {
        foreach ($fileSubject in $Descriptor.FileSubjects) {
            if ($changedPath -ieq $fileSubject) { return $true }
        }
        foreach ($dirSubject in $Descriptor.DirSubjects) {
            if ($changedPath.StartsWith("$dirSubject/", [StringComparison]::OrdinalIgnoreCase)) { return $true }
        }
    }
    return $false
}

$normalizedChanged = @(
    $ChangedFiles |
        ForEach-Object { ([string]$_) -split ',' } |
        ForEach-Object { $_.Trim() -replace '\\', '/' } |
        Where-Object { $_ }
)

$discovered = @(
    Get-ChildItem -LiteralPath $scanRoot -Recurse -File -Filter 'Run-Tests.ps1' -ErrorAction SilentlyContinue |
        Where-Object { ($_.FullName -replace '\\', '/') -notmatch '/node_modules/' } |
        Sort-Object FullName |
        ForEach-Object { Get-SuiteDescriptor $_.FullName }
)

# Re-wrapped after the if: PowerShell unrolls an empty pipeline result to $null on assignment from a
# statement, and under StrictMode the .Count below would then throw instead of reading 0.
$selected = @(if ($normalizedChanged.Count -gt 0) {
        $discovered | Where-Object { Test-SuiteSelected $_ $normalizedChanged }
    }
    else {
        $discovered
    })

if ($ListOnly) {
    Write-Host "run-script-suites: $($selected.Count) suite(s) selected of $($discovered.Count) discovered under $(ConvertTo-RelPath $scanRoot)" -ForegroundColor Cyan
    foreach ($descriptor in $selected) {
        $subjectText = if ($descriptor.Resolved) { ($descriptor.Subjects -join ', ') } else { 'NO RESOLVABLE SUBJECT - declare one with a "# Subject:" header line' }
        Write-Host ("  {0}`n      subject: {1}" -f $descriptor.Rel, $subjectText)
    }
    if ($Json) {
        # -AsArray: a one-suite selection would otherwise serialise as a bare object, and the
        # consumer's `@(ConvertFrom-Json)` would then iterate the object's properties.
        $listPath = if ([System.IO.Path]::IsPathRooted($Json)) { $Json } else { Join-Path $repoRoot $Json }
        $selected |
            ForEach-Object { [pscustomobject]@{ Suite = $_.Rel; Subjects = @($_.Subjects); Resolved = $_.Resolved } } |
            ConvertTo-Json -Depth 4 -AsArray |
            Set-Content -LiteralPath $listPath -Encoding utf8NoBOM
        Write-Host "Written: $listPath"
    }
    exit 0
}

if ($selected.Count -eq 0) {
    if (-not $Quiet) {
        Write-Host 'run-script-suites: PASS - no suite has a subject in the changed set.' -ForegroundColor Green
    }
    exit 0
}

$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") { "$env:ProgramFiles\PowerShell\7\pwsh.exe" } else { 'pwsh' }

$results = [System.Collections.Generic.List[object]]::new()
foreach ($descriptor in $selected) {
    $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
    $env:FMS_SCRIPT_SUITE_RUNNER = '1'
    try {
        $output = @(& $pwshExe -NoProfile -File $descriptor.Path *>&1 | ForEach-Object { [string]$_ })
        $code = [int]$LASTEXITCODE
    }
    finally {
        $env:FMS_SCRIPT_SUITE_RUNNER = $null
        $stopwatch.Stop()
    }

    $verdict = switch ($code) {
        0 { 'PASS' }
        2 { 'CANNOT-VERIFY' }
        default { 'FAIL' }
    }
    # A child's stderr arrives wrapped in ANSI colour escapes; they render as garbage once this line
    # is quoted inside another runner's report, which is where it is actually read.
    $lastLine = ''
    for ($i = $output.Count - 1; $i -ge 0; $i--) {
        $candidate = ($output[$i] -replace "`e\[[0-9;]*m", '').Trim()
        if (-not [string]::IsNullOrWhiteSpace($candidate)) { $lastLine = $candidate; break }
    }

    $results.Add([pscustomobject]@{
            Suite    = $descriptor.Rel
            Verdict  = $verdict
            ExitCode = $code
            Seconds  = [math]::Round($stopwatch.Elapsed.TotalSeconds, 1)
            LastLine = $lastLine
            Output   = $output
        })
}

$failed = @($results | Where-Object { $_.Verdict -eq 'FAIL' })
$unverified = @($results | Where-Object { $_.Verdict -eq 'CANNOT-VERIFY' })

foreach ($result in $results) {
    if ($Quiet -and $result.Verdict -eq 'PASS') { continue }
    $color = switch ($result.Verdict) { 'PASS' { 'Green' } 'FAIL' { 'Red' } default { 'Yellow' } }
    Write-Host ("  {0,-14} {1,6}s  {2}" -f $result.Verdict, $result.Seconds, $result.Suite) -ForegroundColor $color
    Write-Host ("                        {0}" -f $result.LastLine) -ForegroundColor DarkGray
}

# A failing suite's own output is the diagnosis; without it the caller must re-run it by hand,
# which is the blind-retry cost CLAUDE.md Rule 7 exists to prevent.
foreach ($result in $failed) {
    Write-Host ''
    Write-Host "--- $($result.Suite) (exit $($result.ExitCode)) ---" -ForegroundColor Red
    foreach ($line in $result.Output) { Write-Host "    $line" }
}

if ($Json) {
    $jsonPath = if ([System.IO.Path]::IsPathRooted($Json)) { $Json } else { Join-Path $repoRoot $Json }
    $results |
        Select-Object Suite, Verdict, ExitCode, Seconds, LastLine |
        ConvertTo-Json -Depth 4 |
        Set-Content -LiteralPath $jsonPath -Encoding utf8NoBOM
    Write-Host "Written: $jsonPath"
}

$totalSeconds = [math]::Round((@($results | ForEach-Object { $_.Seconds }) | Measure-Object -Sum).Sum, 1)
Write-Host ("run-script-suites: {0} suite(s), {1} passed, {2} failed, {3} could not verify, {4}s total." -f
    $results.Count, @($results | Where-Object { $_.Verdict -eq 'PASS' }).Count, $failed.Count, $unverified.Count, $totalSeconds) -ForegroundColor Cyan

if ($failed.Count -gt 0) {
    $names = (@($failed | ForEach-Object { $_.Suite }) -join ', ')
    $failMsg = "run-script-suites: FAIL - $($failed.Count) suite(s) failed: $names. Each suite's own output is printed above; fix the subject it guards, not the suite."
    Write-Error $failMsg -ErrorAction Continue
    exit 1
}

if ($unverified.Count -gt 0) {
    $names = (@($unverified | ForEach-Object { "$($_.Suite) ($($_.LastLine))" }) -join '; ')
    if ($Gate) {
        $verifyMsg = "run-script-suites: CANNOT VERIFY - $($unverified.Count) suite(s) could not run for want of an environment tool: $names. Install what each names; before a release the environment must be complete."
        Write-Error $verifyMsg -ErrorAction Continue
        exit 2
    }
    Write-Host ("run-script-suites: advisory - $($unverified.Count) suite(s) could not verify: $names") -ForegroundColor Yellow
}

Write-Host 'run-script-suites: PASS.' -ForegroundColor Green
exit 0
