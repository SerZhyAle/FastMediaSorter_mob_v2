#requires -Version 7.0
<#
.SYNOPSIS
    S0720 static-analysis gate: run detekt (+ ktlint formatting) over :app_v2 and :wear.

.DESCRIPTION
    detekt is configured per-subproject in the root build.gradle.kts as a SEPARATE
    static gate - it is NOT wired into assemble*, so it never changes the runtime
    artifact or slows a normal build. This wrapper just invokes the two detekt tasks
    (or one task when -Module is provided) and translates the gradle exit code into
    a PASS/FAIL verdict.

    Ratchet contract: each module has a committed baseline
    (config/detekt/baseline-<module>.xml) freezing every pre-existing finding. detekt
    only fails on findings NOT in the baseline, so this gate blocks NEW smells while
    leaving the historical debt untouched.

    When this gate fails, the remedy is to FIX the finding. Re-freezing the baseline is not
    the normal way out, and S1356 is why: `detektBaseline` regenerates the WHOLE module, so it
    accepts every live finding in it - including debt that other open tickets were written
    about. On 2026-08-02 one such run absorbed the findings behind S1198 and S1328 and left no
    dev-log row, so the loss was only discovered by accident days later. The failure is usually
    one file; the cure was the whole project.

    Re-freezing deliberately (a large intentional refactor, not a red gate you want to silence):
        .\gradlew.bat :app_v2:detektBaseline
        pwsh -NoProfile -File scripts/quality/assert-detekt-baseline-absorption.ps1 `
            -Module app_v2 -Update -Reason '<why>'
    then close the change through post-change.ps1 so the accepted debt has a journal row naming
    it. The absorption gate refuses any baseline carrying an ID absent from its committed
    snapshot, so an unexplained re-freeze fails loudly instead of passing silently.

    Runs lexically (no type resolution) - fast, no full compile.

    Modes:
      (default)  Report the verdict. Exit 0 on PASS.
      -Gate      Same run; exit 1 if detekt reports any NEW finding.
      -NoCache   Skip the clean-verdict cache below and always invoke gradle.

    Clean-verdict cache (2026-08-08):
      A fully clean run is cached in temp/detekt-gate-cache.json under a fingerprint of every
      input the verdict can depend on - the analysed .kt/.kts sources, the detekt config, both
      baselines, the build files and this script. A later run whose fingerprint matches exits
      PASS without starting gradle, and does not queue for BUILD.LOCK to do it.

      Why: measured over 947 gate runs in three weeks, 530 of them (56%, 1,747 minutes of wall
      time) analysed a tree in which no Kotlin file had changed since the previous run - the
      shape produced by closing one ticket file by file, where the second and third close
      re-analyse an identical tree. detekt is 86% of all gate wall time here.

      Only the "no new findings anywhere" verdict is cached, because that answer holds for every
      -Gate and -ChangedFiles combination a later caller can pass. A scoped PASS is not cached:
      it means the project is dirty and only the caller's own files were clean, which says
      nothing about the next caller's files. FAIL is never cached.

      The fingerprint is recomputed after the run and stored only if it still matches the
      pre-run value, so a source edited by a concurrent session mid-run is never certified.

    Scope:
      (default)       Run detekt for both modules (:app_v2 + :wear).
      -Module app_v2  Run detekt for :app_v2 only.
      -Module wear    Run detekt for :wear only.

    Exit codes (S1070 contract; S1077 added 2 to the diff-scoped path):
      0  PASS - no new findings, or none in -ChangedFiles, or non-gate mode.
      1  FAIL - -Gate and detekt reported a new finding (in -ChangedFiles when diff-scoped).
      2  Cannot verify - gradlew.bat missing, or -ChangedFiles given but a run module's
         detekt.xml is absent/unparseable/older than the changed files, so the failure cannot
         be narrowed. Never a PASS: "could not check" is a different fact from "checked and
         found nothing". S1189 added the staleness case, which used to be narrowed silently
         against a previous run's report.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-detekt.ps1
    pwsh -NoProfile -File scripts/quality/assert-detekt.ps1 -Gate
    pwsh -NoProfile -File scripts/quality/assert-detekt.ps1 -Module wear -Gate
#>
[CmdletBinding()]
param(
    [ValidateSet('app_v2', 'wear')]
    [string]$Module,
    [switch]$Gate,
    # S0826: diff-scoped mode. When set, a project-wide detekt failure is re-judged against
    # only these files (repo-relative or absolute paths) - PASS if none of the NEW findings
    # land in them. Lets post-change.ps1 close a change on an always-dirty tree without
    # failing on other tickets' in-flight findings. Release/CI omit it for a full project gate.
    [string[]]$ChangedFiles,
    # S1338: wall-clock ceiling for the gradle call. One post-change run hung for three hours
    # and still reported PASS. The observed tail is 5 runs over 300 s out of 311, so 600 s
    # never fires on a healthy run; raise it deliberately for a genuinely long release run.
    # Expiry is exit 2 "cannot verify" - never PASS, never FAIL.
    [int]$TimeoutSeconds = 600,
    # Bypass the clean-verdict cache described below and always run gradle. Release and CI paths
    # pass this: there the point is the run itself, not the answer.
    [switch]$NoCache
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$gradlew = Join-Path $repoRoot 'gradlew.bat'

if (-not (Test-Path $gradlew)) {
    Write-Host "assert-detekt: FAIL - gradlew.bat not found at $gradlew" -ForegroundColor Red
    exit 2
}

. (Join-Path $repoRoot "scripts/quality/lib/detekt-report.ps1")
. (Join-Path $repoRoot "scripts/quality/lib/changed-files.ps1")

$reportModules = if ($PSBoundParameters.ContainsKey('Module')) { @($Module) } else { @('app_v2', 'wear') }
$lockReason = if ($PSBoundParameters.ContainsKey('Module')) { "assert-detekt.ps1 -Module $Module" } else { "assert-detekt.ps1 (app_v2 + wear)" }
$cacheLabel = if ($PSBoundParameters.ContainsKey('Module')) { $Module } else { 'app_v2 + wear' }
# S2109: detekt reads a whole module, so its domain is the module - and a run WITHOUT -Module
# compiles both, which artifact 04 flagged as the third genuinely multi-domain entry point. Taking
# one domain there would let a sibling build a module this run is already compiling.
$detektDomains = if ($PSBoundParameters.ContainsKey('Module')) {
    @(if ($Module -eq 'wear') { 'Build.Wear' } else { 'Build.Phone' })
} else { @('Build.Phone', 'Build.Wear') }
$cachePath = Join-Path $repoRoot 'temp/detekt-gate-cache.json'

function Get-DetektTreeFingerprint {
    param([string]$RepoRoot, [string[]]$Modules)

    # Every input the verdict can depend on goes in. A fingerprint that misses one turns a stale
    # PASS into a certified one, which is strictly worse than not caching at all.
    $entries = [System.Collections.Generic.List[string]]::new()

    foreach ($m in $Modules) {
        $srcRoot = Join-Path $RepoRoot "$m/src"
        if (Test-Path $srcRoot) {
            foreach ($f in (Get-ChildItem $srcRoot -Recurse -File -Include '*.kt', '*.kts' -ErrorAction SilentlyContinue)) {
                $entries.Add(('{0}|{1}|{2}' -f $f.FullName, $f.Length, $f.LastWriteTimeUtc.Ticks))
            }
        }
    }

    $extras = @('config/detekt/detekt.yml', 'build.gradle.kts', 'settings.gradle.kts', 'gradle.properties',
        'scripts/quality/assert-detekt.ps1')
    foreach ($m in $Modules) {
        $extras += "$m/build.gradle.kts"
        $extras += "config/detekt/baseline-$m.xml"
    }
    foreach ($rel in $extras) {
        $p = Join-Path $RepoRoot $rel
        if (Test-Path $p) {
            $item = Get-Item $p
            $entries.Add(('{0}|{1}|{2}' -f $item.FullName, $item.Length, $item.LastWriteTimeUtc.Ticks))
        }
    }

    $entries.Sort()
    $payload = "modules=$($Modules -join ',')`n" + ($entries -join "`n")
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        return [BitConverter]::ToString($sha.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($payload))).Replace('-', '')
    }
    finally { $sha.Dispose() }
}

$cacheKey = $null
if (-not $NoCache) {
    $cacheKey = Get-DetektTreeFingerprint -RepoRoot $repoRoot -Modules $reportModules
    if (Test-Path $cachePath) {
        # A corrupt or half-written cache must never block the gate: any failure here falls
        # through to a real gradle run, which is the same answer the cache was standing in for.
        try {
            $cached = Get-Content $cachePath -Raw -ErrorAction Stop | ConvertFrom-Json -ErrorAction Stop
            $props = @($cached.PSObject.Properties.Name)
            if (($props -contains 'fingerprint') -and ($props -contains 'verdict') -and ($props -contains 'utc') -and
                $cached.fingerprint -eq $cacheKey -and $cached.verdict -eq 'clean') {
                $ageHours = ((Get-Date).ToUniversalTime() - [datetime]::Parse($cached.utc, $null, 'RoundtripKind')).TotalHours
                # A day-old entry is re-earned rather than trusted. Nothing observed can invalidate a
                # matching fingerprint, but an unbounded cache would outlive the reasoning behind it.
                if ($ageHours -ge 0 -and $ageHours -lt 24) {
                    Write-Host ("assert-detekt: PASS [$cacheLabel] (cached - no analysed input changed since the clean run " +
                        "{0:N1}h ago; pass -NoCache to force a real run)." -f $ageHours) -ForegroundColor Green
                    exit 0
                }
            }
        }
        catch {
            Write-Host "assert-detekt: cache unreadable ($($_.Exception.Message)); running detekt." -ForegroundColor DarkGray
        }
    }
}

. (Join-Path $repoRoot "scripts/utils/agent-lock.ps1")
. (Join-Path $repoRoot "scripts/utils/process-timeout.ps1")
# S1432: queues rather than failing fast. A gate that refuses because a sibling session is mid
# build reports "could not verify" at the exact moment a ticket is being closed, and that reads
# as a defect in the change under review. The ceiling is shorter than a build's, because a gate
# that waits an hour has stopped being a gate.
Enter-BuildLockOrExit -Reason $lockReason -WaitTimeoutSeconds 900 -Domain $detektDomains

Push-Location $repoRoot
try {
    # S1191: the @(...) MUST wrap the whole if-expression. A single-element array returned from an
    # if-expression collapses to a scalar String, and `& $gradlew @tasks` then splats that string one
    # CHARACTER per argument - gradle receives ':' as its first task path and fails with
    # "Cannot locate tasks that match ':'" before writing any report. Only the -Module path was
    # affected (the two-task branch stays a real array), which is why detekt silently stopped running
    # for post-change.ps1 while a bare run looked fine.
    $tasks = @(
        if ($PSBoundParameters.ContainsKey('Module')) {
            ":${Module}:detekt"
        }
        else {
            ':app_v2:detekt'
            ':wear:detekt'
        }
    )

    $scopeLabel = if ($PSBoundParameters.ContainsKey('Module')) {
        $Module
    }
    else {
        'app_v2 + wear'
    }

    # S1338: gradle.properties disables the configuration cache globally because
    # Chaquopy breaks its store for the noLegal task graph. The detekt graph does
    # not pull those tasks in - measured over 6 runs: entry stored then reused,
    # zero configuration-cache problems, 23.4s -> 18.8s warm. Enabled here only,
    # so the global opt-out stays intact for every other caller.
    $run = Invoke-ProcessWithTimeout -FilePath $gradlew -WorkingDirectory $repoRoot `
        -ArgumentList (@($tasks) + '--configuration-cache') -TimeoutSeconds $TimeoutSeconds
    $output = $run.Output
    $exit = if ($run.TimedOut) { $null } else { $run.ExitCode }
    $timedOut = $run.TimedOut
    $elapsedMs = $run.ElapsedMs
    # S1189: remember when each report was written, so a failure that never reached the report
    # writer can be told apart from a real finding. Without this the caller narrows a fresh
    # failure against a stale report and blames whichever file happened to be in yesterday's run.
    $reportStamps = @{}
    foreach ($m in $reportModules) {
        $rp = Join-Path $repoRoot "$m/build/reports/detekt/detekt.xml"
        $reportStamps[$m] = if (Test-Path $rp) { (Get-Item $rp).LastWriteTimeUtc } else { $null }
    }
}
finally {
    Pop-Location
    Exit-AgentLock -Name 'Build' -Domains $detektDomains
}

if ($timedOut) {
    # A ceiling that reported PASS would be worse than no ceiling: it would certify a gate
    # that never ran. The BUILD.LOCK was already released by the finally block above.
    $why = "assert-detekt: CANNOT VERIFY - the detekt gradle run exceeded ${TimeoutSeconds}s " +
    "(elapsed ${elapsedMs} ms) and was killed. Nothing was judged. Re-run, or raise " +
    "-TimeoutSeconds if this build is genuinely that long."
    Write-Error $why -ErrorAction Continue
    exit 2
}

if ($exit -eq 0) {
    Write-Host "assert-detekt: PASS [$scopeLabel] (no new findings; baselines hold)." -ForegroundColor Green
    if (-not $NoCache -and $cacheKey) {
        # Re-fingerprint after the run. A source edited by a concurrent session while gradle was
        # working is not the tree detekt judged, and storing the pre-run key would certify it.
        $postKey = Get-DetektTreeFingerprint -RepoRoot $repoRoot -Modules $reportModules
        if ($postKey -eq $cacheKey) {
            try {
                $dir = Split-Path -Parent $cachePath
                if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
                [PSCustomObject]@{
                    fingerprint = $cacheKey
                    verdict     = 'clean'
                    modules     = $reportModules
                    utc         = (Get-Date).ToUniversalTime().ToString('o')
                } | ConvertTo-Json -Depth 3 | Set-Content -LiteralPath $cachePath -Encoding UTF8
            }
            catch {
                # Failing to persist the cache costs a future re-run, nothing else. The verdict
                # above already stands on its own, so this must not change this run's exit code.
                Write-Host "assert-detekt: could not write the verdict cache ($($_.Exception.Message))." -ForegroundColor DarkGray
            }
        }
        else {
            Write-Host "assert-detekt: sources changed during the run; verdict not cached." -ForegroundColor DarkGray
        }
    }
    exit 0
}

# S0826: diff-scoped re-judgement. detekt failed project-wide; if the caller named the
# changed files, the failure is re-judged against only them - PASS unless a NEW finding
# actually lands in a changed file. Source the findings from the Checkstyle XML report
# (a declared task output, restored on cache hits) - NOT stdout, whose per-finding lines
# vanish on a build-cache hit and would yield a false PASS. detekt's baseline suppresses
# old findings everywhere, so any file in the report is a genuinely-new finding; a file
# outside the changed set is another ticket's in-flight WIP, not this change.
if ($ChangedFiles -and $ChangedFiles.Count -gt 0) {
    # S1189: a report older than the newest changed file describes a different tree. Narrowing
    # against it attributes an unrelated file's old findings to this change (and, worse, would
    # report PASS for a change whose findings the stale report cannot contain). Same fail-closed
    # rule as S1077: "could not check" is not "checked and found nothing".
    # S1338: judge staleness PER MODULE, against that module's own changed files. The global-newest
    # comparison this replaced reported "cannot narrow" for a module whose detekt task gradle had
    # correctly skipped as UP-TO-DATE: gradle does not rewrite an up-to-date task's report, so wear's
    # report kept yesterday's timestamp while the changed set held a doc edited minutes ago, and the
    # gate refused every closure that did not touch wear. A module whose own sources are untouched
    # cannot have gained a finding, so its report - refreshed or not - still describes its tree.
    $normalizedChanges = @($ChangedFiles |
        ForEach-Object { $_ -split ',' } |
        ForEach-Object { $_.Trim() } |
        Where-Object { $_ -and (Test-Path $_) })
    $staleModules = @($reportModules | Where-Object {
            $module = $_
            $stamp = $reportStamps[$module]
            # Match on the repo-relative prefix rather than a resolved absolute path: this runs after
            # the finally block popped back out of $repoRoot, so the working directory is not
            # guaranteed and Resolve-Path could bind a relative entry to the wrong root.
            $moduleChanges = @($normalizedChanges | Where-Object {
                    $rel = $_ -replace '\\', '/'
                    $rel = $rel -replace ('^' + [regex]::Escape(($repoRoot -replace '\\', '/')) + '/?'), ''
                    $rel -like "$module/*"
                })
            if ($moduleChanges.Count -eq 0) { return $false }
            $newestChange = ($moduleChanges |
                ForEach-Object { (Get-Item $_).LastWriteTimeUtc } |
                Measure-Object -Maximum).Maximum
            $null -eq $stamp -or ($newestChange -and $stamp -lt $newestChange)
        })
    if ($staleModules.Count -gt 0) {
        Write-Host "assert-detekt: detekt FAILED without refreshing the report for: $($staleModules -join ', ')" -ForegroundColor Red
        Write-Host 'Raw gradle output (the failure happened before detekt wrote its findings):' -ForegroundColor Yellow
        $output | Select-Object -Last 40 | ForEach-Object { Write-Host "  $_" }
        $why = 'assert-detekt: cannot narrow - the detekt report predates the changed files, so it ' +
        'describes a different tree. Fix the gradle failure above, then re-run.'
        Write-Error $why -ErrorAction Continue
        exit 2
    }
    $report = Get-DetektFindings -RepoRoot $repoRoot -Modules $reportModules
    # S1077: fail closed. detekt has already FAILED to reach this point, so an unreadable report means
    # the narrowing cannot be done - and "could not check" must never be reported as "clean". This used
    # to `continue` past a missing report into the empty-list branch below, printing PASS on top of the
    # failure. Distinct exit code from a real finding: 2 = cannot verify, 1 = verified and found.
    if (-not $report.Ok) {
        $why = "assert-detekt: cannot narrow a project-wide detekt failure - $($report.Reason). " +
        'Re-run detekt so the report exists; refusing to report PASS without reading it.'
        Write-Error $why -ErrorAction Continue
        exit 2
    }
    $findingFiles = @(@($report.Findings) | ForEach-Object { $_.File } | Select-Object -Unique)
    # S1184: Select-ChangedFileFindings splits a comma-joined -ChangedFiles (pwsh -File binds a CSV
    # as one element) so a multi-file scope matches file-by-file, not as one bogus path.
    $mine = @(Select-ChangedFileFindings -FindingFiles $findingFiles -ChangedFiles $ChangedFiles)
    if ($mine.Count -eq 0) {
        Write-Host "assert-detekt: PASS [scoped] - $($findingFiles.Count) file(s) with new findings project-wide, none among changed files." -ForegroundColor Green
        exit 0
    }
    # S1338: print the rule, line and message the report already carries. Printing the bare
    # file path cost 2.38 further gradle round-trips per failure to discover what to fix.
    # S1600: the detail lines carry this script's own prefix, and the verdict line names the rules
    # itself. Every observed "header printed, nothing under it" failure (19 in the transcript
    # corpus, all of them) was a CALLER-side line filter: the caller pipes this gate through
    # `Select-String -Pattern 'assert-detekt'` or `'detekt|preflight|FAIL'`, which matches the
    # header and the verdict but not an indented, unprefixed detail line. The gate had named the
    # rule and the filter ate it, so the next step was another gradle round-trip to ask again.
    # The gate cannot fix the caller's pattern; it can make its own lines survive one.
    $myFindings = @(@($report.Findings) |
            Where-Object { $mine -contains $_.File } |
            Sort-Object File, { $_.Line -as [int] })
    Write-Host "assert-detekt: NEW findings in changed file(s):" -ForegroundColor Red
    foreach ($f in $myFindings) {
        Write-Host "assert-detekt:   $($f.File):$($f.Line):$($f.Column) - $($f.RuleId) - $($f.Message)"
    }
    if ($myFindings.Count -eq 0) {
        # Unreachable by construction - $mine is a subset of the reported files, so at least one
        # finding must survive the filter above. Kept because the bare header is the exact symptom
        # this ticket removed: if it ever does happen, it says so instead of looking like a gate
        # that failed for no reason.
        foreach ($file in $mine) { Write-Host "assert-detekt:   $file - (detail lost; read the report)" }
    }
    if ($Gate) {
        # Self-contained on purpose: a caller keeping only the FAIL line - or only the last line -
        # still walks away with the rule names, which is the whole cost of an unattributable gate.
        $rules = @($myFindings | ForEach-Object { $_.RuleId } | Select-Object -Unique | Sort-Object)
        $what = if ($rules.Count -gt 0) {
            "$($myFindings.Count) finding(s) in $($mine.Count) file(s) - $($rules -join ', ')"
        }
        else {
            "$($mine.Count) file(s) - see the lines above"
        }
        Write-Host "assert-detekt: FAIL [scoped] - $what. Fix the finding(s) listed above." -ForegroundColor Red
        exit 1
    }
    exit 0
}

# Surface the detekt summary lines so the failing rule(s) are visible without
# re-running gradle. detekt prints lines containing "(file path):(line):(col):" plus a
# trailing finding-count summary.
$output | Where-Object {
    $_ -match 'detekt|finding|\.kt:\d+:\d+:' -or $_ -match 'FAILURE|What went wrong'
} | Select-Object -Last 30 | ForEach-Object { Write-Host $_ }

if ($Gate) {
    # S1356: this line used to prescribe a two-module `detektBaseline` re-freeze as the ordinary
    # remedy. Whoever followed it accepted every live finding in the project - including the debt
    # other open tickets were written about - without breaking a single rule. The radius of the cure
    # has to match the radius of the disease: the findings above are the disease.
    Write-Host "assert-detekt: FAIL [$scopeLabel] - detekt found NEW issues above baseline. Fix the finding(s) listed above." -ForegroundColor Red
    Write-Host "  Do NOT reach for ':app_v2:detektBaseline' to clear this - it re-freezes the WHOLE module and" -ForegroundColor Yellow
    Write-Host "  silently accepts other tickets' live debt as permanent (that is S1356; it cost S1198 and S1328)." -ForegroundColor Yellow
    Write-Host "  A genuinely intentional re-freeze is a separate, journalled operation - see this script's" -ForegroundColor Yellow
    Write-Host "  header and scripts/quality/assert-detekt-baseline-absorption.ps1." -ForegroundColor Yellow
    exit 1
}

Write-Host "assert-detekt: [$scopeLabel] detekt reported new findings (exit $exit)." -ForegroundColor Yellow
exit 0
