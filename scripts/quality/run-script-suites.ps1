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

    A PASS IS REMEMBERED IN TWO SEPARATE MODES, AND NEVER ACROSS THEM.
    Per ticket (S3515). The closure of one adb.ps1 change took 146 s, 134.5 s of it the three adb
    suites the agent had run by hand a minute earlier against the same files. With -ChangedFiles, a
    passing suite is recorded under a fingerprint - SHA-256 over the path and content hash of every
    file in the suite's directory, of every resolved subject, and of every existing .ps1 those files
    name in a string literal (one level: the dot-sourced libraries) - and the next run with an
    identical fingerprint within -CacheMaxAgeMinutes reports a cached PASS without executing. The age
    bound is there because a suite may still read a file the fingerprint does not cover (a path built
    from several Join-Path segments, a library's own library, the canon harness behind a forwarder).
    Per release campaign (S4413). Release 46 paid 35.7 + 33.1 + 31.9 min for three identical full
    sweeps inside one campaign. With no changed set and a campaign id - -CampaignId, or the open
    campaign of scripts/release/lib/release-campaign.ps1 - a passing suite is recorded under the same
    fingerprint in a separate store keyed by suite and stamped with the campaign id; a later sweep in
    the same campaign reuses it with no age bound, an entry from another campaign is ignored and
    overwritten, so the first sweep of every campaign is cold and executes every suite.
    In both modes a failure, a could-not-verify and a skip are never recorded and drop a prior entry,
    and -NoCache executes regardless. A full sweep with no campaign neither reads nor writes either
    store. Run a suite through this runner rather than by its own path and the closure reuses the
    result. Every result carries Source (run or campaign-cache), and inside a campaign every suite's
    duration is journalled as phase "suite:<path>" whatever -Quiet says.

    SEVERAL EXECUTORS, ONE SERIAL LANE (S4413). A full sweep is 165 suites one after another, about
    32 minutes, while most of them share nothing. With -Workers N above 1 the suites that are not
    served from a cache run on N child pwsh processes at once, and the suites that declare
    `# Parallel: serial` in their 40-line header - the same header `# Subject:` is read from - run
    one at a time on one extra lane beside the pool. A suite declares itself serial when it works on
    a real store a sibling also touches: the lock store, the device lease and registry stores, the
    ticket journal, the live process table, a fixed fixture directory. Executors stay child
    PROCESSES, never in-process runspaces: the re-entry guard FMS_SCRIPT_SUITE_RUNNER is set on each
    child's own environment block, where a runspace pool would race on the parent's. Cache lookups
    happen before scheduling and cache writes after completion, so -Workers changes neither the
    cache semantics nor the verdict, and results are reported in suite-path order whatever order
    they finished in. -Workers 1 (the default) is the plain in-order loop of before.

.PARAMETER ChangedFiles
    Changed-file set. Only the suites whose subject is in the set run. Accepts a comma-separated
    string, because `pwsh -File` binds `-ChangedFiles a.ps1,b.ps1` as ONE array element. Absent means
    every discovered suite runs.

.PARAMETER NoCache
    Execute every selected suite even when a matching PASS is recorded. The result is still recorded.

.PARAMETER CacheMaxAgeMinutes
    Oldest recorded PASS that may stand in for a run (default 120). The store is
    temp/metrics/script-suite-pass-cache.json, or the path in FMS_SCRIPT_SUITE_CACHE.

.PARAMETER CampaignId
    Release campaign whose pass store a full sweep reads and writes. Absent, a sweep with no
    -ChangedFiles takes the id of the open campaign; with neither, the campaign store is untouched.
    Ignored with -ChangedFiles, which keeps the per-ticket store. The store is a file of its own
    under temp/metrics/, beside the per-ticket one, or the path in FMS_SCRIPT_SUITE_CAMPAIGN_CACHE.
    Source on a result reads run, campaign-cache, or ticket-cache for a per-ticket cached PASS.

.PARAMETER Workers
    Number of child executors for the suites that are not declared serial (default 1, range 1..16).
    Above 1, the serial suites run on one additional lane in parallel with the pool, so the peak is
    Workers + 1 child processes. 1 keeps the sequential in-order run.

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
.EXAMPLE
    pwsh -NoProfile -File scripts/quality/run-script-suites.ps1 -Workers 4 -Json temp/scratch/suites.json

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
    [switch]$NoCache,
    [int]$CacheMaxAgeMinutes = 120,
    [string]$CampaignId,
    [ValidateRange(1, 16)]
    [int]$Workers = 1,
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
        Write-Error "run-script-suites: COULD NOT VERIFY - discovery root not found: $Root" -ErrorAction Continue
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
# answer COULD NOT VERIFY. A recursion guard producing a silent non-answer is this ticket's own defect
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

function Test-DeclaredSerial([string]$SuitePath) {
    # Same 40-line header as the subject: a suite that must not share the machine with its siblings
    # says so where its other contract lines are, with no registry to fall out of sync.
    $head = Get-Content -LiteralPath $SuitePath -TotalCount 40 -ErrorAction SilentlyContinue
    return [bool](@($head | Where-Object { $_ -match '^\s*#\s*Parallel:\s*serial\s*$' }).Count -gt 0)
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
        Serial       = (Test-DeclaredSerial $SuitePath)
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
        $laneText = if ($descriptor.Serial) { '  [serial]' } else { '' }
        Write-Host ("  {0}{1}`n      subject: {2}" -f $descriptor.Rel, $laneText, $subjectText)
    }
    $serialCount = @($selected | Where-Object { $_.Serial }).Count
    Write-Host "run-script-suites: $serialCount of $($selected.Count) selected suite(s) declare '# Parallel: serial'." -ForegroundColor Cyan
    if ($Json) {
        # -AsArray: a one-suite selection would otherwise serialise as a bare object, and the
        # consumer's `@(ConvertFrom-Json)` would then iterate the object's properties.
        $listPath = if ([System.IO.Path]::IsPathRooted($Json)) { $Json } else { Join-Path $repoRoot $Json }
        # Materialised before serialising rather than piped: an EMPTY selection sends nothing down
        # the pipeline, ConvertTo-Json is never invoked, and Set-Content leaves no file behind while
        # the line below still reports one as written. assert-suite-tracked.ps1 then read exit 0 with
        # no list and refused the whole closure as COULD NOT VERIFY - a change touching no suite's
        # subject is the ordinary case, so "nothing selected" must serialise as `[]` (S2720).
        $listRecords = @($selected | ForEach-Object {
            [pscustomobject]@{ Suite = $_.Rel; Subjects = @($_.Subjects); Resolved = $_.Resolved; Serial = $_.Serial }
        })
        # -InputObject instead of the pipeline, and therefore WITHOUT -AsArray: an array handed over
        # this way already serialises as a JSON array at every length, including zero, while -AsArray
        # on top of it wraps the whole thing a second time.
        Set-Content -LiteralPath $listPath -Encoding utf8NoBOM `
            -Value (ConvertTo-Json -InputObject $listRecords -Depth 4)
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

$useCache = $normalizedChanged.Count -gt 0
$cachePath = if ($env:FMS_SCRIPT_SUITE_CACHE) { $env:FMS_SCRIPT_SUITE_CACHE } else { Join-Path $repoRoot 'temp/metrics/script-suite-pass-cache.json' }

# The campaign library only measures; a checkout without it, or one where it fails to load, keeps
# the plain sequential sweep instead of losing its verdict to a journal.
$campaignLibLoaded = $false
$campaignLib = Join-Path $repoRoot 'scripts/release/lib/release-campaign.ps1'
if (Test-Path -LiteralPath $campaignLib) {
    try {
        . $campaignLib
        $campaignLibLoaded = $true
    }
    catch { Write-Host "run-script-suites: release-campaign library not loaded - $($_.Exception.Message)" -ForegroundColor DarkGray }
}
if (-not $useCache -and -not $CampaignId -and $campaignLibLoaded) {
    $openCampaign = Get-ActiveReleaseCampaign
    if ($openCampaign) { $CampaignId = [string]$openCampaign.id }
}
# A changed set always means the per-ticket store: a closure inside a campaign must not stamp the
# campaign store with a pass over a selection that is not the full sweep's.
$campaignMode = (-not $useCache) -and [bool]$CampaignId
$campaignCachePath = if ($env:FMS_SCRIPT_SUITE_CAMPAIGN_CACHE) { $env:FMS_SCRIPT_SUITE_CAMPAIGN_CACHE } else { Join-Path $repoRoot 'temp/metrics/script-suite-campaign-cache.json' }

$scriptLiteral = [regex]::new('[''"]([^''"\r\n$]+?\.ps1)[''"]')

function Get-ReferencedScript([System.IO.FileInfo]$File) {
    # A subject that dot-sources a library is re-selected by a change to the subject only, so a
    # fingerprint over the subject alone let a library edit replay the old PASS: post-change.ps1
    # moved its document-registry step into lib/ in the same ticket that added this cache.
    if ($File.Extension -ne '.ps1') { return }
    $text = [System.IO.File]::ReadAllText($File.FullName)
    foreach ($match in $scriptLiteral.Matches($text)) {
        $literal = $match.Groups[1].Value -replace '\\', '/'
        foreach ($base in @($repoRoot, $File.DirectoryName)) {
            $candidate = Join-Path $base $literal
            if (Test-Path -LiteralPath $candidate -PathType Leaf) { Get-Item -LiteralPath $candidate; break }
        }
    }
}

function Get-SuiteFingerprint([object]$Descriptor) {
    $roots = @((Split-Path -Parent $Descriptor.Path)) + @($Descriptor.Subjects | ForEach-Object { Join-Path $repoRoot $_ })
    $direct = @($roots | ForEach-Object {
            if (Test-Path -LiteralPath $_ -PathType Leaf) { Get-Item -LiteralPath $_ }
            elseif (Test-Path -LiteralPath $_ -PathType Container) { Get-ChildItem -LiteralPath $_ -Recurse -File }
        })
    $files = @(@($direct) + @($direct | ForEach-Object { Get-ReferencedScript $_ }) | Sort-Object FullName -Unique)
    $lines = @($files | ForEach-Object {
            '{0}|{1}' -f (ConvertTo-RelPath $_.FullName), (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash
        })
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($lines -join "`n")
    return [System.Convert]::ToHexString([System.Security.Cryptography.SHA256]::HashData($bytes))
}

function Read-PassCache([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path)) { return @{} }
    # An unreadable store is treated as empty: the cost is one re-run, never a wrong PASS.
    try { return (Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json -AsHashtable) ?? @{} }
    catch { return @{} }
}

function Save-PassCache([hashtable]$Cache, [string]$Path) {
    $dir = Split-Path -Parent $Path
    if ($dir -and -not (Test-Path -LiteralPath $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    $tmp = "$Path.$PID.tmp"
    Set-Content -LiteralPath $tmp -Value ($Cache | ConvertTo-Json -Depth 4) -Encoding utf8NoBOM
    Move-Item -LiteralPath $tmp -Destination $Path -Force
}

function ConvertTo-RecordedAt([object]$Value) {
    # ConvertFrom-Json turns an ISO timestamp into a [datetime] by itself; parse only a string.
    if ($Value -is [datetime]) { return $Value }
    return [datetime]::Parse([string]$Value, [cultureinfo]::InvariantCulture, [System.Globalization.DateTimeStyles]::RoundtripKind)
}

function Write-SuitePhase([object]$Result, [long]$ElapsedMs) {
    # Journalled whatever -Quiet says: release scope runs this runner quiet, and before S4413 the
    # per-suite durations of a release sweep were recorded nowhere.
    if (-not $campaignLibLoaded) { return }
    Add-ReleaseCampaignPhase -Name "suite:$($Result.Suite)" -State point -ElapsedMs $ElapsedMs -ExitCode $Result.ExitCode -Detail $Result.Source
}

$passCache = if ($useCache) { Read-PassCache $cachePath } else { @{} }
$cacheDirty = $false
$campaignCache = if ($campaignMode) { Read-PassCache $campaignCachePath } else { @{} }
$campaignDirty = $false

function New-CachedResult([object]$Descriptor, [string]$Source, [string]$LastLine) {
    return [pscustomobject]@{
        Suite    = $Descriptor.Rel
        Verdict  = 'PASS'
        ExitCode = 0
        Seconds  = 0
        Source   = $Source
        LastLine = $LastLine
        Output   = @()
    }
}

function Find-CachedResult([object]$Descriptor, [string]$Fingerprint) {
    if ($NoCache) { return $null }
    if ($useCache) {
        $entry = $passCache[$Descriptor.Rel]
        if (-not $entry -or $entry.fingerprint -ne $Fingerprint) { return $null }
        $recordedAt = ConvertTo-RecordedAt $entry.at
        if (((Get-Date).ToUniversalTime() - $recordedAt.ToUniversalTime()).TotalMinutes -gt $CacheMaxAgeMinutes) { return $null }
        return New-CachedResult $Descriptor 'ticket-cache' "cached PASS from $($recordedAt.ToLocalTime().ToString('HH:mm:ss')) - suite and subjects unchanged (-NoCache to re-run)"
    }
    if ($campaignMode) {
        $entry = $campaignCache[$Descriptor.Rel]
        # No age bound here: the campaign id is the bound, and the next campaign starts cold.
        if (-not $entry -or [string]$entry.campaign -ne $CampaignId -or $entry.fingerprint -ne $Fingerprint) { return $null }
        $recordedAt = ConvertTo-RecordedAt $entry.at
        return New-CachedResult $Descriptor 'campaign-cache' "campaign-cached PASS from $($recordedAt.ToLocalTime().ToString('HH:mm:ss')) in $CampaignId - suite and subjects unchanged (-NoCache to re-run)"
    }
    return $null
}

function New-ExecutedResult([object]$Descriptor, [int]$Code, [string[]]$Output, [double]$Seconds) {
    $verdict = switch ($Code) {
        0 { 'PASS' }
        2 { 'CANNOT-VERIFY' }
        default { 'FAIL' }
    }
    # A child's stderr arrives wrapped in ANSI colour escapes; they render as garbage once this line
    # is quoted inside another runner's report, which is where it is actually read.
    $lastLine = ''
    for ($i = $Output.Count - 1; $i -ge 0; $i--) {
        $candidate = ($Output[$i] -replace "`e\[[0-9;]*m", '').Trim()
        if (-not [string]::IsNullOrWhiteSpace($candidate)) { $lastLine = $candidate; break }
    }
    return [pscustomobject]@{
        Suite    = $Descriptor.Rel
        Verdict  = $verdict
        ExitCode = $Code
        Seconds  = [math]::Round($Seconds, 1)
        Source   = 'run'
        LastLine = $lastLine
        Output   = $Output
    }
}

function Invoke-SuiteInline([object]$Descriptor) {
    $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
    $env:FMS_SCRIPT_SUITE_RUNNER = '1'
    try {
        $output = @(& $pwshExe -NoProfile -File $Descriptor.Path *>&1 | ForEach-Object { [string]$_ })
        $code = [int]$LASTEXITCODE
    }
    finally {
        $env:FMS_SCRIPT_SUITE_RUNNER = $null
        $stopwatch.Stop()
    }
    return [pscustomobject]@{
        Result    = (New-ExecutedResult $Descriptor $code $output $stopwatch.Elapsed.TotalSeconds)
        ElapsedMs = [long]$stopwatch.ElapsedMilliseconds
    }
}

function Start-SuiteProcess([object]$Descriptor) {
    $psi = [System.Diagnostics.ProcessStartInfo]::new($pwshExe)
    foreach ($argument in @('-NoProfile', '-File', $Descriptor.Path)) { $psi.ArgumentList.Add($argument) }
    $psi.UseShellExecute = $false
    $psi.CreateNoWindow = $true
    $psi.RedirectStandardInput = $true
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $psi.StandardOutputEncoding = [System.Text.Encoding]::UTF8
    $psi.StandardErrorEncoding = [System.Text.Encoding]::UTF8
    $psi.WorkingDirectory = (Get-Location).ProviderPath
    # The child's own environment block: siblings and this process never observe it.
    $psi.Environment['FMS_SCRIPT_SUITE_RUNNER'] = '1'
    $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
    $process = [System.Diagnostics.Process]::Start($psi)
    $process.StandardInput.Close()
    # Both pipes drained from the start: a child that fills one pipe while nobody reads it blocks
    # forever, and a pool only polls.
    return [pscustomobject]@{
        Descriptor = $Descriptor
        Process    = $process
        StdOut     = $process.StandardOutput.ReadToEndAsync()
        StdErr     = $process.StandardError.ReadToEndAsync()
        Stopwatch  = $stopwatch
    }
}

function Complete-SuiteProcess([object]$Running) {
    $Running.Process.WaitForExit()
    $Running.Stopwatch.Stop()
    $text = $Running.StdOut.GetAwaiter().GetResult() + $Running.StdErr.GetAwaiter().GetResult()
    $output = @($text -split "\r?\n")
    if ($output.Count -gt 0 -and $output[-1] -eq '') { $output = @($output | Select-Object -SkipLast 1) }
    $code = [int]$Running.Process.ExitCode
    $Running.Process.Dispose()
    return [pscustomobject]@{
        Result    = (New-ExecutedResult $Running.Descriptor $code $output $Running.Stopwatch.Elapsed.TotalSeconds)
        ElapsedMs = [long]$Running.Stopwatch.ElapsedMilliseconds
    }
}

# Slot per selected suite, in selection (= suite path) order, so the report and the JSON read the
# same whatever order the executors finished in.
$slots = [object[]]::new($selected.Count)
$fingerprints = @{}
$pending = [System.Collections.Generic.List[int]]::new()
for ($index = 0; $index -lt $selected.Count; $index++) {
    $descriptor = $selected[$index]
    if ($useCache -or $campaignMode) {
        $fingerprints[$index] = Get-SuiteFingerprint $descriptor
        $cached = Find-CachedResult $descriptor $fingerprints[$index]
        if ($cached) {
            $slots[$index] = $cached
            Write-SuitePhase $cached 0
            continue
        }
    }
    $pending.Add($index)
}

function Register-Executed([int]$Index, [object]$Outcome) {
    $script:slots[$Index] = $Outcome.Result
    Write-SuitePhase $Outcome.Result $Outcome.ElapsedMs
    $rel = $Outcome.Result.Suite
    $passed = ($Outcome.Result.Verdict -eq 'PASS')
    if ($useCache) {
        if ($passed) { $passCache[$rel] = @{ fingerprint = $fingerprints[$Index]; at = (Get-Date).ToUniversalTime().ToString('o') } }
        else { $passCache.Remove($rel) }
        $script:cacheDirty = $true
    }
    elseif ($campaignMode) {
        if ($passed) { $campaignCache[$rel] = @{ campaign = $CampaignId; fingerprint = $fingerprints[$Index]; at = (Get-Date).ToUniversalTime().ToString('o') } }
        else { $campaignCache.Remove($rel) }
        $script:campaignDirty = $true
    }
}

$wallClock = [System.Diagnostics.Stopwatch]::StartNew()
if ($Workers -le 1) {
    foreach ($index in $pending) { Register-Executed $index (Invoke-SuiteInline $selected[$index]) }
}
else {
    $parallelQueue = [System.Collections.Generic.Queue[int]]::new()
    $serialQueue = [System.Collections.Generic.Queue[int]]::new()
    foreach ($index in $pending) {
        if ($selected[$index].Serial) { $serialQueue.Enqueue($index) } else { $parallelQueue.Enqueue($index) }
    }
    $poolRunning = [System.Collections.Generic.List[object]]::new()
    $serialRunning = $null
    try {
        while ($parallelQueue.Count -gt 0 -or $serialQueue.Count -gt 0 -or $poolRunning.Count -gt 0 -or $serialRunning) {
            while ($poolRunning.Count -lt $Workers -and $parallelQueue.Count -gt 0) {
                $index = $parallelQueue.Dequeue()
                $started = Start-SuiteProcess $selected[$index]
                $started | Add-Member -NotePropertyName Index -NotePropertyValue $index
                $poolRunning.Add($started)
            }
            if (-not $serialRunning -and $serialQueue.Count -gt 0) {
                $index = $serialQueue.Dequeue()
                $serialRunning = Start-SuiteProcess $selected[$index]
                $serialRunning | Add-Member -NotePropertyName Index -NotePropertyValue $index
            }
            $progressed = $false
            foreach ($running in @($poolRunning)) {
                if ($running.Process.HasExited) {
                    Register-Executed $running.Index (Complete-SuiteProcess $running)
                    [void]$poolRunning.Remove($running)
                    $progressed = $true
                }
            }
            if ($serialRunning -and $serialRunning.Process.HasExited) {
                Register-Executed $serialRunning.Index (Complete-SuiteProcess $serialRunning)
                $serialRunning = $null
                $progressed = $true
            }
            if (-not $progressed) { Start-Sleep -Milliseconds 100 }
        }
    }
    finally {
        # An interrupted sweep must not leave executors running against real stores.
        foreach ($orphan in @(@($poolRunning) + @($serialRunning) | Where-Object { $_ })) {
            if (-not $orphan.Process.HasExited) {
                try { $orphan.Process.Kill($true) } catch { Write-Host "run-script-suites: could not stop $($orphan.Descriptor.Rel) - $($_.Exception.Message)" -ForegroundColor DarkGray }
            }
        }
    }
}
$wallClock.Stop()

$results = [System.Collections.Generic.List[object]]::new()
foreach ($slot in $slots) { $results.Add($slot) }

if ($cacheDirty) {
    # A store that cannot be written costs the next closure one re-run; it never changes this verdict.
    try { Save-PassCache $passCache $cachePath }
    catch { Write-Host "run-script-suites: pass cache not written - $($_.Exception.Message)" -ForegroundColor DarkGray }
}
if ($campaignDirty) {
    # Entries of an earlier campaign can never be reused again, so they are dropped rather than kept
    # to grow the store release after release.
    foreach ($staleKey in @($campaignCache.Keys | Where-Object { [string]$campaignCache[$_].campaign -ne $CampaignId })) {
        $campaignCache.Remove($staleKey)
    }
    try { Save-PassCache $campaignCache $campaignCachePath }
    catch { Write-Host "run-script-suites: campaign cache not written - $($_.Exception.Message)" -ForegroundColor DarkGray }
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
        Select-Object Suite, Verdict, ExitCode, Seconds, Source, LastLine |
        ConvertTo-Json -Depth 4 |
        Set-Content -LiteralPath $jsonPath -Encoding utf8NoBOM
    Write-Host "Written: $jsonPath"
}

$totalSeconds = [math]::Round((@($results | ForEach-Object { $_.Seconds }) | Measure-Object -Sum).Sum, 1)
# Two figures on purpose: the per-suite sum is what the suites cost, the wall clock is what the
# caller waited, and only the second shrinks with -Workers.
Write-Host ("run-script-suites: {0} suite(s), {1} passed, {2} failed, {3} could not verify, {4}s total, {5}s wall with {6} worker(s)." -f
    $results.Count, @($results | Where-Object { $_.Verdict -eq 'PASS' }).Count, $failed.Count, $unverified.Count, $totalSeconds,
    [math]::Round($wallClock.Elapsed.TotalSeconds, 1), $Workers) -ForegroundColor Cyan
if ($campaignMode) {
    $reused = @($results | Where-Object { $_.Source -eq 'campaign-cache' }).Count
    Write-Host ("campaign cache: {0} reused, {1} executed ({2})" -f $reused, ($results.Count - $reused), $CampaignId) -ForegroundColor Cyan
}

if ($failed.Count -gt 0) {
    $names = (@($failed | ForEach-Object { $_.Suite }) -join ', ')
    $failMsg = "run-script-suites: FAIL - $($failed.Count) suite(s) failed: $names. Each suite's own output is printed above; fix the subject it guards, not the suite."
    Write-Error $failMsg -ErrorAction Continue
    exit 1
}

if ($unverified.Count -gt 0) {
    $names = (@($unverified | ForEach-Object { "$($_.Suite) ($($_.LastLine))" }) -join '; ')
    if ($Gate) {
        $verifyMsg = "run-script-suites: COULD NOT VERIFY - $($unverified.Count) suite(s) could not run for want of an environment tool: $names. Install what each names; before a release the environment must be complete."
        Write-Error $verifyMsg -ErrorAction Continue
        exit 2
    }
    Write-Host ("run-script-suites: advisory - $($unverified.Count) suite(s) could not verify: $names") -ForegroundColor Yellow
}

Write-Host 'run-script-suites: PASS.' -ForegroundColor Green
exit 0
