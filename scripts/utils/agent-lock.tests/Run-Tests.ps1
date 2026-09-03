#requires -Version 7.0
<#
.SYNOPSIS
    Regression tests for agent-lock.ps1: the domain taxonomy (S2109), the stale-JAVA_HOME
    snapshot repair (S1928), the BUILD.LOCK fail-fast refusal's exit code (S2058) and the two
    liveness keep-signals of S2408 (a running owner process; a session writing only into its
    subagent subtree).

.DESCRIPTION
    The repair sits directly in front of a refusal that gates every gradle target in the repository,
    so both of its answers matter equally: repairing when the machine is fine, and staying out of
    the way when it is not. A repair that fired too eagerly would silently swap the JVM - the exact
    outcome the capture rates worse than stopping.

    The helper is exercised directly rather than through a gradle run: it reads the environment and
    two files, so driving it needs no build, and a test that started gradle would be timing the
    daemon instead of checking the branch.

    S2058 adds a second, unrelated concern to this same file because both exercise agent-lock.ps1
    directly: Enter-BuildLockOrExit's fail-fast refusal (a held BUILD.LOCK, -NoWait) must exit 1,
    never 0 - a refusal that exits 0 is indistinguishable from a successful build to any caller that
    only reads $LASTEXITCODE, which is exactly how the defect was observed (CLAUDE.md Rule 7,
    "reachable exit codes"). The refusal is driven in a NESTED pwsh process, never in-process,
    because Enter-BuildLockOrExit calls `exit` directly - dot-sourcing it into this test process
    would terminate the test runner itself instead of producing an observable exit code.

.NOTES
    Exit codes:
      0 - every case passed.
      1 - a case failed.
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..' '..')).Path
. (Join-Path $repoRoot 'scripts/utils/agent-lock.ps1')
# S2372: the real BUILD.LOCK hold below posts to the agent chat - keep it out of the live store.
$env:FMS_AGENT_CHAT_ROOT = Join-Path $repoRoot 'temp/S2372/chat-agent-lock-tests'

$originalJavaHome = $env:JAVA_HOME
$persistedUser = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'User')
$failures = 0

function Assert-Case {
    param([Parameter(Mandatory)][string]$Name, [Parameter(Mandatory)][bool]$Ok, [string]$Detail)
    if ($Ok) { Write-Output "  PASS $Name" }
    else { Write-Output "  FAIL $Name - $Detail"; $script:failures++ }
}

try {
    # 0. S2109 domain taxonomy. These cases touch no lock file at all - they read the table and
    #    the two path builders - so they run first and unconditionally, before any case that has
    #    to skip itself around a live BUILD.LOCK.
    $codeDomains = @(Resolve-AgentLockDomains -Name 'Code')
    Assert-Case -Name 'a bare Code resolves to the three code domains in canonical order' `
        -Ok (($codeDomains -join ',') -eq 'Code.Phone,Code.Wear,Code.Scripts') `
        -Detail "got '$($codeDomains -join ',')'"

    $buildDomains = @(Resolve-AgentLockDomains -Name 'Build')
    Assert-Case -Name 'a bare Build resolves to both build domains in canonical order' `
        -Ok (($buildDomains -join ',') -eq 'Build.Phone,Build.Wear') `
        -Detail "got '$($buildDomains -join ',')'"

    Assert-Case -Name 'a concrete domain resolves to itself' `
        -Ok ((@(Resolve-AgentLockDomains -Name 'Code.Wear') -join ',') -eq 'Code.Wear') `
        -Detail 'a concrete domain did not resolve to a single-element set'

    # An unknown name must throw rather than return empty (strategic S2109 section 11 criterion 6):
    # an empty set is a lock nobody holds and every caller believes in.
    $unknownThrew = $false
    try { Resolve-AgentLockDomains -Name 'Code.Tablet' | Out-Null }
    catch { $unknownThrew = $true }
    Assert-Case -Name 'an unknown resource name throws instead of resolving to nothing' `
        -Ok $unknownThrew -Detail 'Resolve-AgentLockDomains returned quietly for an unknown name'

    Assert-Case -Name 'two domains produce two different lock paths' `
        -Ok ((Get-AgentLockPath -Name 'Code.Wear') -ne (Get-AgentLockPath -Name 'Code.Phone')) `
        -Detail 'two distinct domains share one lock file'

    Assert-Case -Name 'a bare name still resolves to the pre-split path' `
        -Ok ((Get-AgentLockPath -Name 'Code').EndsWith('CODE.LOCK') -and
             (Get-AgentLockPath -Name 'Build').EndsWith('BUILD.LOCK')) `
        -Detail 'a bare name no longer points at the path every existing caller uses'

    Assert-Case -Name 'every concrete domain has its own timings record' `
        -Ok (@('Build.Phone', 'Build.Wear', 'Code.Phone', 'Code.Wear', 'Code.Scripts' |
            Where-Object { $null -eq (Get-AgentLockTimings -Name $_) }).Count -eq 0) `
        -Detail 'a concrete domain resolved to no timings record'

    # S2109 derivation table. These are strategic criteria 1 and 2 stated as arithmetic on the
    # mapping: the whole benefit of the split is that these sets come out DISJOINT, and a table
    # without a test is one rename away from quietly mapping everything to the full set again -
    # which would restore the old serialisation while every banner still printed a domain name.
    $wearSet = @(Resolve-CodeDomainsForPaths -Path @('wear/src/main/java/A.kt'))
    $phoneSet = @(Resolve-CodeDomainsForPaths -Path @('app_v2/src/main/java/B.kt'))
    $scriptSet = @(Resolve-CodeDomainsForPaths -Path @('scripts/post-change.ps1'))
    Assert-Case -Name 'disjoint code domains: a wear set and a phone set share none' `
        -Ok (@($wearSet | Where-Object { $phoneSet -contains $_ }).Count -eq 0 -and
             $wearSet.Count -eq 1 -and $phoneSet.Count -eq 1) `
        -Detail "wear=$($wearSet -join ','), phone=$($phoneSet -join ',')"
    Assert-Case -Name 'disjoint code domains: a scripts set shares none with either module' `
        -Ok (@($scriptSet | Where-Object { $wearSet -contains $_ -or $phoneSet -contains $_ }).Count -eq 0) `
        -Detail "scripts=$($scriptSet -join ',')"
    Assert-Case -Name 'a set spanning both modules resolves to both module domains' `
        -Ok ((@(Resolve-CodeDomainsForPaths -Path @('wear/src/A.kt', 'app_v2/src/B.kt')) -join ',') -eq 'Code.Phone,Code.Wear') `
        -Detail "got '$(@(Resolve-CodeDomainsForPaths -Path @('wear/src/A.kt','app_v2/src/B.kt')) -join ',')'"
    # A module's OWN build file is deliberately NOT that module's domain - the configuration phase
    # processes every subproject, so a broken one fails a check requested for the other module.
    Assert-Case -Name 'a build file resolves to the full code set, not to its own module' `
        -Ok ((@(Resolve-CodeDomainsForPaths -Path @('wear/build.gradle.kts')).Count -eq 3) -and
             (@(Resolve-CodeDomainsForPaths -Path @('settings.gradle.kts')).Count -eq 3)) `
        -Detail "wear build file=$(@(Resolve-CodeDomainsForPaths -Path @('wear/build.gradle.kts')) -join ',')"
    Assert-Case -Name 'an unrecognised path fails closed to the full code set' `
        -Ok (@(Resolve-CodeDomainsForPaths -Path @('brand_new_module/src/A.kt')).Count -eq 3) `
        -Detail 'an unknown path narrowed the set instead of widening it'

    # A leading './' has to be stripped as a PREFIX. TrimStart('./') takes a character SET, so it
    # also ate the dot of a dotfile path: '.claude/..' became 'claude/..', matched no branch, and
    # fell through to the fail-closed full set. Every command, hook, skill and agent-memory edit
    # therefore took all three code domains - the most common changed set in this repository.
    $dotSet = @(Resolve-CodeDomainsForPaths -Path @('.claude/commands/spec-all.md'))
    $ghSet = @(Resolve-CodeDomainsForPaths -Path @('.github/workflows/ci.yml'))
    Assert-Case -Name 'a dotfile path keeps its leading dot and resolves to Code.Scripts' `
        -Ok (($dotSet -join ',') -eq 'Code.Scripts' -and ($ghSet -join ',') -eq 'Code.Scripts') `
        -Detail ".claude=$($dotSet -join ','), .github=$($ghSet -join ',')"
    Assert-Case -Name "a leading './' is still stripped from a module path" `
        -Ok ((@(Resolve-CodeDomainsForPaths -Path @('./app_v2/src/B.kt')) -join ',') -eq 'Code.Phone') `
        -Detail "got '$(@(Resolve-CodeDomainsForPaths -Path @('./app_v2/src/B.kt')) -join ',')'"

    # A per-module detekt baseline is that module's file, not a shared config: it is named for its
    # module and no other module's check reads it. Widening on it serialised every Kotlin closure
    # that regenerated one. The shared config beside it must still fail closed.
    $baselineSet = @(Resolve-CodeDomainsForPaths -Path @('app_v2/src/B.kt,config/detekt/baseline-app_v2.xml'))
    Assert-Case -Name "a module's detekt baseline stays in that module's domain" `
        -Ok (($baselineSet -join ',') -eq 'Code.Phone' -and
             (@(Resolve-CodeDomainsForPaths -Path @('config/detekt/baseline-wear.ids')) -join ',') -eq 'Code.Wear') `
        -Detail "phone edit + its baseline=$($baselineSet -join ',')"
    Assert-Case -Name 'the shared detekt config still resolves to the full code set' `
        -Ok ((@(Resolve-CodeDomainsForPaths -Path @('config/detekt/detekt.yml')).Count -eq 3) -and
             (@(Resolve-CodeDomainsForPaths -Path @('config/detekt/rule-categories.txt')).Count -eq 3)) `
        -Detail "detekt.yml=$(@(Resolve-CodeDomainsForPaths -Path @('config/detekt/detekt.yml')) -join ',')"

    # `pwsh -File` collapses a comma list into ONE string element, so an unsplit list matched only
    # its first prefix and resolved NARROWER than the change - the one direction this must never
    # fail in. Observed live: -Files with three script paths reported "1 changed path".
    Assert-Case -Name 'a comma-joined file list is split, not matched as one path' `
        -Ok ((@(Resolve-CodeDomainsForPaths -Path @('wear/src/A.kt,app_v2/src/B.kt')) -join ',') -eq 'Code.Phone,Code.Wear') `
        -Detail "got '$(@(Resolve-CodeDomainsForPaths -Path @('wear/src/A.kt,app_v2/src/B.kt')) -join ',')' - a collapsed list must not narrow the set"

    # S2338: PLAN/ is the table's one exemption - it resolves to NO domain, because every path
    # under it is already exclusive by ticket lease (a spec file belongs to one ticket) or by the
    # catalog mutex (the journals and both release files). Measured 2026-09-02: 55% of recent
    # closures touched PLAN/ and nothing else, so before the exemption the majority of them took a
    # domain that protected nothing while serialising every other scripts/docs edit in the repo.
    $planSet = @(Resolve-CodeDomainsForPaths -Path @('PLAN/S2338_lock-domain-claims-specs-and-docs.md'))
    $planQueueSet = @(Resolve-CodeDomainsForPaths -Path @('PLAN/RELEASE_QUEUE.md'))
    Assert-Case -Name 'a PLAN-only set resolves to no code domain at all' `
        -Ok ($planSet.Count -eq 0 -and $planQueueSet.Count -eq 0) `
        -Detail "spec file=$($planSet.Count) domain(s), RELEASE_QUEUE=$($planQueueSet.Count) domain(s) - both must be 0"
    # The exemption must not leak into a mixed set: a change that also touches a module still takes
    # that module's domain, and the PLAN path simply contributes nothing to it.
    Assert-Case -Name 'a PLAN path mixed with a module path resolves to the module domain only' `
        -Ok ((@(Resolve-CodeDomainsForPaths -Path @('PLAN/S2338_x.md', 'app_v2/src/B.kt')) -join ',') -eq 'Code.Phone') `
        -Detail "got '$(@(Resolve-CodeDomainsForPaths -Path @('PLAN/S2338_x.md','app_v2/src/B.kt')) -join ',')'"
    # docs/ and dev/ are deliberately NOT exempt. They are hand-edited prose with no finer
    # mechanism over them, so a concurrent edit there is an ordinary lost update. Narrowing them
    # out along with PLAN/ is the tempting mistake this case exists to catch.
    Assert-Case -Name 'docs/ and dev/ still resolve to Code.Scripts' `
        -Ok ((@(Resolve-CodeDomainsForPaths -Path @('docs/ARCHITECTURE.md')) -join ',') -eq 'Code.Scripts' -and
             (@(Resolve-CodeDomainsForPaths -Path @('dev/CHANGELOG.md')) -join ',') -eq 'Code.Scripts') `
        -Detail "docs=$(@(Resolve-CodeDomainsForPaths -Path @('docs/ARCHITECTURE.md')) -join ','), dev=$(@(Resolve-CodeDomainsForPaths -Path @('dev/CHANGELOG.md')) -join ',')"
    # S2342: content with no code in it resolves to Code.Scripts instead of failing closed. None of
    # these paths compiles, links or packs into an APK, so the full set protected nothing and
    # serialised phone and watch work against a store-listing edit. Measured 2026-09-02: of the 11
    # recent changed sets that took the full code set, 8 were content only.
    $contentTrees = @(
        'play/listing/README.md', 'fastlane/metadata/android/en-US/title.txt',
        'store_assets/design_brief.md', 'delivery/INVENTORY.md', 'maestro/README.md'
    )
    $widerTree = @($contentTrees | Where-Object {
        (@(Resolve-CodeDomainsForPaths -Path @($_)) -join ',') -ne 'Code.Scripts' })
    Assert-Case -Name 'a store/content tree resolves to Code.Scripts, not the full set' `
        -Ok ($widerTree.Count -eq 0) -Detail "still wider than Code.Scripts: $($widerTree -join ', ')"

    $rootContent = @(
        'index.html', 'index-ru.html', 'nolegal-uk.html', 'styles.css', 'sitemap.xml', 'robots.txt',
        '_config.yml', '_typos.toml', 'GEMINI.md', 'LICENSE', 'THIRD_PARTY_LICENSES.md',
        'favicon.ico', 'favicon-32x32.png', 'icon.png', 'apple-touch-icon.png'
    )
    $widerRoot = @($rootContent | Where-Object {
        (@(Resolve-CodeDomainsForPaths -Path @($_)) -join ',') -ne 'Code.Scripts' })
    Assert-Case -Name 'a root site page, document or icon resolves to Code.Scripts' `
        -Ok ($widerRoot.Count -eq 0) -Detail "still wider than Code.Scripts: $($widerRoot -join ', ')"

    # The motivating call (S2340 phase 03): one repository script plus one listing file used to take
    # all three domains and queue behind a wear session it could not possibly conflict with.
    $motivating = @(Resolve-CodeDomainsForPaths -Path @('scripts/release/publish-play-listing.py,play/listing/README.md'))
    Assert-Case -Name 'a script plus a listing file resolves to Code.Scripts alone' `
        -Ok (($motivating -join ',') -eq 'Code.Scripts') -Detail "got '$($motivating -join ',')'"

    # The fail-closed remainder is the point of the branch, so it is asserted rather than assumed:
    # naming content trees must not become a habit of naming any directory that shows up. corex/ is
    # unrecognised source, and benchmark/ and watchface/ are real Gradle modules with no Build.*
    # domain of their own - all three must keep taking every code domain.
    $stillClosed = @(
        'corex/androidx/core/content/ContextCompat.java', 'benchmark/src/A.kt', 'watchface/src/A.kt'
    )
    $narrowed = @($stillClosed | Where-Object { @(Resolve-CodeDomainsForPaths -Path @($_)).Count -ne 3 })
    Assert-Case -Name 'unrecognised source and the domain-less modules still fail closed' `
        -Ok ($narrowed.Count -eq 0) -Detail "narrowed instead of failing closed: $($narrowed -join ', ')"

    # "Every path was exempt" and "there were no paths" are different questions with opposite safe
    # answers. Collapsing them would send every PLAN-only closure back to the full set, silently
    # undoing the exemption while every test above still passed.
    Assert-Case -Name 'an empty input still fails closed to the full code set' `
        -Ok ((@(Resolve-CodeDomainsForPaths -Path @()).Count -eq 3) -and
             (@(Resolve-CodeDomainsForPaths -Path @('')).Count -eq 3)) `
        -Detail "empty array=$(@(Resolve-CodeDomainsForPaths -Path @()).Count), empty string=$(@(Resolve-CodeDomainsForPaths -Path @('')).Count) - both must be 3"

    # Deliberately table-only. Proving disjointness by actually TAKING the two sets belongs in
    # test-agent-lock-queue.ps1 (case 11), which runs in a throwaway sandbox: this file resolves
    # against the real repository root, so acquiring here would contend with whatever sibling
    # session is editing right now - and releasing afterwards would drop a lock this process never
    # owned. A test that can hand away a working session's lock is worse than the gap it closes.

    $missingDir = Join-Path $repoRoot 'temp/S1928-no-such-jdk'

    # 1. Stale snapshot, persisted value usable and different -> repaired, and the scope is named.
    if ([string]::IsNullOrWhiteSpace($persistedUser) -or
        (Test-JvmHomeMissingParts -JvmHome $persistedUser).Count -gt 0) {
        # Nothing to assert against on a machine with no usable persisted value. Say so rather
        # than passing: a case that silently did not run reads exactly like one that succeeded.
        Write-Output '  SKIP stale snapshot repaired - this machine has no usable persisted JAVA_HOME'
    }
    else {
        $repair = Resolve-PersistedJavaHomeRepair -CurrentValue $missingDir
        Assert-Case -Name 'stale snapshot is repaired from the persisted value' `
            -Ok ($null -ne $repair -and $repair.Path -eq $persistedUser) `
            -Detail "got '$($repair.Path)', expected '$persistedUser'"
        Assert-Case -Name 'the repair names the scope it came from' `
            -Ok ($null -ne $repair -and $repair.Scope -in @('User', 'Machine')) `
            -Detail "scope was '$($repair.Scope)'"
    }

    # 2. Persisted value equal to the snapshot -> nothing to refresh, the JDK really is gone.
    $sameValue = Resolve-PersistedJavaHomeRepair -CurrentValue $persistedUser
    Assert-Case -Name 'a persisted value equal to the snapshot is not a repair' `
        -Ok ($null -eq $sameValue -or $sameValue.Path -ne $persistedUser) `
        -Detail 'the helper offered the value the caller already had'

    # 3. Persisted value itself unusable -> no repair. Simulated by asking the usability probe
    #    directly, since the persisted variable cannot be rewritten from a test.
    Assert-Case -Name 'an unusable path is rejected by the usability probe' `
        -Ok ((Test-JvmHomeMissingParts -JvmHome $missingDir).Count -gt 0) `
        -Detail 'a non-existent JDK directory was judged usable'

    # 4. The healthy path pays nothing: a usable snapshot never reaches the helper at all, which is
    #    a property of the caller's guard rather than of the helper.
    # S2402: the local path is a generated forwarder to the canon-shipped harness, so a check that reads
# the SUBJECT'S TEXT has to read the shipped file - the forwarder's body is not the mechanism. The
# resolution is the forwarder's own, taken from it rather than restated: SZA_HARNESS_ROOT, then the
# plugin cache's newest version, then the canon checkout.
function Get-AgentLockSourcePath {
    param([Parameter(Mandatory)][string]$RepoRoot)
    $local = Join-Path $RepoRoot 'scripts/utils/agent-lock.ps1'
    $body = Get-Content -LiteralPath $local -Raw
    if ($body -notmatch 'Forwarder to the canon-shipped harness') { return $local }
    $candidates = @()
    if ($env:SZA_HARNESS_ROOT) { $candidates += $env:SZA_HARNESS_ROOT }
    $cache = Join-Path $env:USERPROFILE '.claude\plugins\cache\sza-unified-rules\sza'
    if (Test-Path -LiteralPath $cache) {
        $candidates += @(Get-ChildItem -LiteralPath $cache -Directory -ErrorAction SilentlyContinue |
            Sort-Object Name -Descending | ForEach-Object { Join-Path $_.FullName 'tools\harness' })
    }
    $checkout = if ($env:SZA_CANON_ROOT) { $env:SZA_CANON_ROOT } else { 'P:\WEB\sza-unified-rules' }
    $candidates += (Join-Path $checkout 'tools\harness')
    foreach ($c in $candidates) {
        $p = Join-Path $c 'locks\agent-lock.ps1'
        if (Test-Path -LiteralPath $p) { return $p }
    }
    throw "agent-lock.tests: the local agent-lock.ps1 is a forwarder and the shipped harness was not found in: $($candidates -join '; ')"
}

$agentLockSourcePath = Get-AgentLockSourcePath -RepoRoot $repoRoot
$guardSource = Get-Content -LiteralPath $agentLockSourcePath -Raw
    Assert-Case -Name 'the helper is only consulted after the snapshot is judged unusable' `
        -Ok ($guardSource -match '(?s)if \(\$launcherMissing\.Count -gt 0\) \{\s*\r?\n\s*Resolve-PersistedJavaHomeRepair') `
        -Detail 'the repair is not guarded by the unusable-snapshot condition'

    # 5. The refusal must survive intact for the case with nothing to repair.
    Assert-Case -Name 'the original refusal and its exit code are unchanged' `
        -Ok ($guardSource -match 'Launcher JVM unusable - refusing to start gradle\. Nothing was built\.') `
        -Detail 'the refusal text was altered or removed'

    # Both S2058 cases below fabricate or hold a real temp/BUILD.LOCK, so both are skipped rather
    # than forced when a real lock is already live: stealing it here would corrupt whatever build
    # or gate holds it (CLAUDE.md Rule 23 - never contend for BUILD.LOCK outside its own protocol).
    $preExisting = Get-AgentLockStatus -Name Build
    if ($preExisting.Exists -and -not $preExisting.Stale) {
        Write-Output "  SKIP fail-fast refusal exits 1 - BUILD.LOCK is already live (pid $($preExisting.Pid))"
        Write-Output "  SKIP genuine nested reuse still succeeds - BUILD.LOCK is already live (pid $($preExisting.Pid))"
    }
    else {
        # 6. S2058 regression: the re-entrancy guard used to match on a bare inherited PID. A
        #    process that inherits FMS_BUILD_LOCK_HELD_BY from an ancestor which is NOT the
        #    current lock's actual holder - simulating Windows reusing that PID for an unrelated,
        #    later holder - must still refuse/queue, never silently `return` as if self-held. Real
        #    PID reuse cannot be forced from a test, so this fabricates the mismatch directly: a
        #    lock file whose recorded pid is this test process's own (real, alive) PID, paired
        #    with an inherited env value that names the SAME pid but the WRONG start ticks - the
        #    one piece of information a reused PID cannot carry forward.
        $selfProc = Get-Process -Id $PID
        $fakeLockPath = Get-AgentLockPath -Name Build
        $fakeLockBody = [ordered]@{
            schema = 2; lockType = 'Build'; pid = $PID; procStart = $selfProc.StartTime.Ticks
            acquiredAt = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
            reason = 'S2058-simulated-unrelated-holder'; host = $env:COMPUTERNAME
            sessionId = $null; transcriptPath = $null
        } | ConvertTo-Json -Compress
        Set-Content -LiteralPath $fakeLockPath -Value $fakeLockBody -Encoding utf8NoBOM
        $originalHeldBy = $env:FMS_BUILD_LOCK_HELD_BY
        try {
            $env:FMS_BUILD_LOCK_HELD_BY = "$PID`:1"
            $childCommand = ". `"$repoRoot\scripts\utils\agent-lock.ps1`"; " +
                "Enter-BuildLockOrExit -Reason 'S2058-regression-mismatched-ticks' -NoWait"
            $proc = Start-Process -FilePath 'pwsh' `
                -ArgumentList @('-NoProfile', '-Command', $childCommand) `
                -NoNewWindow -Wait -PassThru -RedirectStandardOutput (Join-Path $repoRoot 'temp/S2058-refusal-stdout.log') `
                -RedirectStandardError (Join-Path $repoRoot 'temp/S2058-refusal-stderr.log')
            Assert-Case -Name 'a PID-reused inherited holder is refused, not treated as self-held' `
                -Ok ($proc.ExitCode -eq 1) `
                -Detail "child process exited $($proc.ExitCode), expected 1 (fail-fast refusal)"
        }
        finally {
            $env:FMS_BUILD_LOCK_HELD_BY = $originalHeldBy
            Remove-Item -LiteralPath $fakeLockPath -Force -ErrorAction SilentlyContinue
            Remove-Item -LiteralPath (Join-Path $repoRoot 'temp/S2058-refusal-stdout.log') -Force -ErrorAction SilentlyContinue
            Remove-Item -LiteralPath (Join-Path $repoRoot 'temp/S2058-refusal-stderr.log') -Force -ErrorAction SilentlyContinue
        }

        # 7. Positive control for case 6: a genuinely nested subprocess of a run that really does
        #    hold BUILD.LOCK (matching pid AND start ticks) must still take the fast reuse path -
        #    the fix must not turn every nested invocation into an unwanted queue/refuse.
        $acquired = Enter-AgentLock -Name Build -Reason 'S2058-regression-test-hold'
        if (-not $acquired.Acquired) {
            Write-Output '  SKIP genuine nested reuse still succeeds - could not acquire BUILD.LOCK for the probe'
        }
        else {
            try {
                $childCommand = ". `"$repoRoot\scripts\utils\agent-lock.ps1`"; " +
                    "Enter-BuildLockOrExit -Reason 'S2058-regression-genuine-reuse' -NoWait"
                $proc = Start-Process -FilePath 'pwsh' `
                    -ArgumentList @('-NoProfile', '-Command', $childCommand) `
                    -NoNewWindow -Wait -PassThru -RedirectStandardOutput (Join-Path $repoRoot 'temp/S2058-reuse-stdout.log') `
                    -RedirectStandardError (Join-Path $repoRoot 'temp/S2058-reuse-stderr.log')
                Assert-Case -Name 'genuine nested reuse (matching pid and start ticks) still succeeds' `
                    -Ok ($proc.ExitCode -eq 0) `
                    -Detail "child process exited $($proc.ExitCode), expected 0 (fast reuse path)"
            }
            finally {
                Exit-AgentLock -Name Build
                Remove-Item -LiteralPath (Join-Path $repoRoot 'temp/S2058-reuse-stdout.log') -Force -ErrorAction SilentlyContinue
                Remove-Item -LiteralPath (Join-Path $repoRoot 'temp/S2058-reuse-stderr.log') -Force -ErrorAction SilentlyContinue
            }
        }
    }

    # 8. S2405 turn marker cleanup test: Remove-StaleTurnMarkers deletes markers older than cutoff
    #    while preserving markers younger than cutoff.
    $tempDir = Join-Path $repoRoot 'temp'
    $oldMarker = Join-Path $tempDir "CODE.PHONE.TURN-test-old-s2405.json"
    $freshMarker = Join-Path $tempDir "CODE.PHONE.TURN-test-fresh-s2405.json"
    try {
        Set-Content -LiteralPath $oldMarker -Value '{"outcome":"granted"}' -Encoding utf8NoBOM
        Set-Content -LiteralPath $freshMarker -Value '{"outcome":"granted"}' -Encoding utf8NoBOM
        (Get-Item -LiteralPath $oldMarker).LastWriteTime = (Get-Date).AddMinutes(-30)

        [void](Remove-StaleTurnMarkers -Name 'Code.Phone')

        $oldDeleted = -not (Test-Path -LiteralPath $oldMarker)
        $freshKept = Test-Path -LiteralPath $freshMarker

        Assert-Case -Name 'S2405: Remove-StaleTurnMarkers deletes turn marker older than cutoff' `
            -Ok $oldDeleted -Detail 'old turn marker was not deleted'
        Assert-Case -Name 'S2405: Remove-StaleTurnMarkers preserves fresh turn marker' `
            -Ok $freshKept -Detail 'fresh turn marker was deleted'
    }
    finally {
        Remove-Item -LiteralPath $oldMarker -Force -ErrorAction SilentlyContinue
        Remove-Item -LiteralPath $freshMarker -Force -ErrorAction SilentlyContinue
    }

    # 9. S2408: the two keep-signals. Both are one-directional - they may only answer live - so
    #    each case here proves that a working owner is NOT evicted, which is the failure that
    #    opened the ticket (a Code.Scripts lock taken from a session mid-edit on 2026-09-03).
    $selfStart = (Get-Process -Id $PID).StartTime
    # Written by this very process, so the record cannot predate the process - the shape every
    # real record has. StaleMinutes 0 puts it outside the clock-based window, so only the process
    # check can answer live.
    $liveTicket = [pscustomobject]@{
        sessionId      = "pid-$PID"
        transcriptPath = $null
        enqueuedAt     = [DateTimeOffset]::new($selfStart).ToUnixTimeMilliseconds()
    }
    # A record from BEFORE this process existed cannot have been written by it: this is the
    # recycled-pid case, and the keep-signal must refuse to resurrect its owner.
    $recycledTicket = [pscustomobject]@{
        sessionId      = "pid-$PID"
        transcriptPath = $null
        enqueuedAt     = [DateTimeOffset]::UtcNow.AddHours(-9).ToUnixTimeMilliseconds()
    }
    $liveVerdict = 'unset'
    $recycledVerdict = 'unset'
    # The owner must not be US, or the verdict short-circuits to 'self' and proves nothing.
    $previousAgentId = $env:FMS_AGENT_ID
    try {
        $env:FMS_AGENT_ID = 'agent-lock-tests-observer'
        $liveVerdict = Get-AgentTicketLiveness -Ticket $liveTicket -StaleMinutes 0
        $recycledVerdict = Get-AgentTicketLiveness -Ticket $recycledTicket -StaleMinutes 15
    }
    finally {
        if ($null -eq $previousAgentId) { Remove-Item Env:FMS_AGENT_ID -ErrorAction SilentlyContinue }
        else { $env:FMS_AGENT_ID = $previousAgentId }
    }
    Assert-Case -Name 'S2408: an owner whose process is running is live past the clock window' `
        -Ok ($liveVerdict -eq 'foreign-live') -Detail "verdict was '$liveVerdict', expected foreign-live"
    Assert-Case -Name 'S2408: a pid whose process is younger than the record does not revive it' `
        -Ok ($recycledVerdict -eq 'foreign-stale') -Detail "verdict was '$recycledVerdict', expected foreign-stale"

    $transcriptRoot = Join-Path $repoRoot 'temp/S2408/lock-tests'
    $fakeSession = 'sess-s2408-fixture'
    $mainTranscript = Join-Path $transcriptRoot "$fakeSession.jsonl"
    $subagentDir = Join-Path (Join-Path $transcriptRoot $fakeSession) 'subagents'
    try {
        New-Item -ItemType Directory -Path $subagentDir -Force | Out-Null
        Set-Content -LiteralPath $mainTranscript -Value '{}' -Encoding utf8NoBOM
        $subagentFile = Join-Path $subagentDir 'agent-fixture.jsonl'
        Set-Content -LiteralPath $subagentFile -Value '{}' -Encoding utf8NoBOM
        # The shape of the incident: the session's own file went quiet an hour ago while its
        # subagent kept writing.
        (Get-Item -LiteralPath $mainTranscript).LastWriteTime = (Get-Date).AddHours(-1)
        $subagentWrite = (Get-Item -LiteralPath $subagentFile).LastWriteTime

        $newest = Get-AgentSessionTranscriptLastWrite -TranscriptPath $mainTranscript
        Assert-Case -Name 'S2408: transcript freshness counts the subagent subtree' `
            -Ok ($null -ne $newest -and $newest -eq $subagentWrite) `
            -Detail "helper returned '$newest', expected the subagent file's $subagentWrite"

        $missing = Get-AgentSessionTranscriptLastWrite -TranscriptPath (Join-Path $transcriptRoot 'absent.jsonl')
        Assert-Case -Name 'S2408: a transcript with neither file nor subtree returns null' `
            -Ok ($null -eq $missing) -Detail "helper returned '$missing', expected null"
    }
    finally {
        Remove-Item -LiteralPath $transcriptRoot -Recurse -Force -ErrorAction SilentlyContinue
    }

    # The waiter's self-acquire eligibility. Driven through the helper rather than through
    # wait-for-lock-turn.ps1 itself: the script enqueues in a LIVE queue, and a test that took a
    # place in Code.Scripts would compete with whatever session is actually working.
    $eligible = Test-WaiterAcquireEligible -Domains @('Code.Scripts') -SessionId 'host-code-1234-5678'
    Assert-Case -Name 'waiter self-acquire: a code domain under a stable identity is eligible' `
        -Ok $eligible.Eligible -Detail $eligible.Reason

    $buildVerdict = Test-WaiterAcquireEligible -Domains @('Code.Phone', 'Build.Phone') -SessionId 'host-code-1234-5678'
    Assert-Case -Name 'waiter self-acquire: a build domain anywhere in the set is refused' `
        -Ok (-not $buildVerdict.Eligible) -Detail 'a PID-judged lock would read as dead the moment the waiter exits'

    $pidVerdict = Test-WaiterAcquireEligible -Domains @('Code.Scripts') -SessionId 'pid-4242'
    Assert-Case -Name 'waiter self-acquire: a pid- identity is refused' `
        -Ok (-not $pidVerdict.Eligible) -Detail 'the lock would be foreign to the session that asked for the wait'

    # S2413 - the stalled-holder predicate. Every case supplies the holder and the queue rather
    # than writing a lock file: the live domains belong to whichever sessions are actually working,
    # and a test that took one would block them to prove a read-only signal. Three of the four are
    # negative on purpose - a signal that lights on a healthy tree is switched off within a day,
    # and then the incident it exists for passes unremarked.
    $stallRoot = Join-Path $repoRoot 'temp/S2413/stall-predicate'
    New-Item -ItemType Directory -Path $stallRoot -Force | Out-Null
    try {
        $quietOwner = 'stall-quiet-owner-session'
        $quietTranscript = Join-Path $stallRoot 'quiet.jsonl'
        Set-Content -LiteralPath $quietTranscript -Value '{}' -Encoding utf8
        # Well past Code.Scripts' LockStaleMinutes of 10, and past its SessionStaleMinutes of 15 too,
        # so the case cannot pass by accident on a machine whose clock granularity differs.
        (Get-Item -LiteralPath $quietTranscript).LastWriteTime = (Get-Date).AddMinutes(-40)

        $freshOwner = 'stall-fresh-owner-session'
        $freshTranscript = Join-Path $stallRoot 'fresh.jsonl'
        Set-Content -LiteralPath $freshTranscript -Value '{}' -Encoding utf8

        $waiter = @([pscustomobject]@{ seq = 7; sessionId = 'stall-waiter-session'; waitedMinutes = 6 })

        $stalled = Get-AgentLockStall -Name 'Code.Scripts' -HolderSessionId $quietOwner `
            -HolderTranscriptPath $quietTranscript -HeldMinutes 12 -Queue $waiter
        Assert-Case -Name 'S2413: held, a queue behind it and a quiet owner is a stall' `
            -Ok ($null -ne $stalled -and $stalled.domain -eq 'Code.Scripts' -and
                 $stalled.queueDepth -eq 1 -and $stalled.quietMinutes -gt $stalled.thresholdMinutes) `
            -Detail "predicate returned '$stalled'"

        Assert-Case -Name 'S2413: the threshold is the domain LockStaleMinutes, not a literal' `
            -Ok ($null -ne $stalled -and $stalled.thresholdMinutes -eq (Get-AgentLockTimings -Name 'Code.Scripts').LockStaleMinutes) `
            -Detail "threshold was '$($stalled.thresholdMinutes)'"

        $fresh = Get-AgentLockStall -Name 'Code.Scripts' -HolderSessionId $freshOwner `
            -HolderTranscriptPath $freshTranscript -HeldMinutes 12 -Queue $waiter
        Assert-Case -Name 'S2413: a fresh owner holding with a queue is not a stall' `
            -Ok ($null -eq $fresh) -Detail "predicate returned '$fresh' for an owner seen seconds ago"

        $noQueue = Get-AgentLockStall -Name 'Code.Scripts' -HolderSessionId $quietOwner `
            -HolderTranscriptPath $quietTranscript -HeldMinutes 12 -Queue @()
        Assert-Case -Name 'S2413: a quiet holder with an empty queue blocks nobody and is not a stall' `
            -Ok ($null -eq $noQueue) -Detail "predicate returned '$noQueue' with nobody waiting"

        $buildStall = Get-AgentLockStall -Name 'Build.Phone' -HolderSessionId $quietOwner `
            -HolderTranscriptPath $quietTranscript -HeldMinutes 12 -Queue $waiter
        Assert-Case -Name 'S2413: a build domain is never a stall - a dead pid already makes it stale' `
            -Ok ($null -eq $buildStall) -Detail "predicate returned '$buildStall' for a PID-judged domain"

        $selfQueued = Get-AgentLockStall -Name 'Code.Scripts' -HolderSessionId $quietOwner `
            -HolderTranscriptPath $quietTranscript -HeldMinutes 12 `
            -Queue @([pscustomobject]@{ seq = 3; sessionId = $quietOwner; waitedMinutes = 9 })
        Assert-Case -Name 'S2413: the holder own leftover ticket is not a second session waiting' `
            -Ok ($null -eq $selfQueued) -Detail "predicate counted the holder as its own waiter"

        Assert-Case -Name 'S2413: quiet time for a session with no readable mark is null, not zero' `
            -Ok ($null -eq (Get-AgentOwnerQuietMinutes -SessionId 'stall-no-marks-anywhere-session')) `
            -Detail 'an unmeasurable owner would otherwise read as seen just now'
    }
    finally {
        Remove-Item -LiteralPath $stallRoot -Recurse -Force -ErrorAction SilentlyContinue
    }

    if ($failures -gt 0) {
        Write-Output "agent-lock tests: FAIL ($failures case(s))"
        exit 1
    }
    Write-Output 'agent-lock tests: PASS (JAVA_HOME snapshot repair, both directions)'
    exit 0
}
finally {
    $env:JAVA_HOME = $originalJavaHome
}
