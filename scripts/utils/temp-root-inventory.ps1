#requires -Version 7.0
<#
.SYNOPSIS
    Declares what may exist at the TOP LEVEL of temp/ - the single copy of that list (S3030).

.DESCRIPTION
    temp/ holds three kinds of content, and only the first is a membership question:

      1. Fixed infrastructure - a name that must stay at the root because a live script or a
         coordinating session addresses it there. This file declares that set.
      2. Per-ticket scratch - temp/Sxxxx/, judged by the ticket's catalog status, never by name.
      3. Loose per-run artifacts - timestamped logs and reports that are legal while a retention
         window covers them. Declared here as PATTERNS, not names.

    Before S3030 this list existed in three hand-kept copies that disagreed: CLAUDE.md Rule 10's
    prose (measured 2026-09-12 it accounted for 11 of 2470 root entries and named seven that were
    absent), archive-temp.ps1's own arrays (which omitted the release-freeze pair Rule 10 declares
    legitimate, plus seven live coordination directories), and .sza-profile.json's declared paths,
    which neither of the other two consulted. Nothing could assert over the directory while its
    definition contradicted itself, which is why S3025 repaired its own two writers and left the
    gate unbuilt.

    Derived, never retyped. The lock domains come from the domain table and the harness's own paths
    come from the profile the harness reads, so a sixth domain or a moved harness directory needs no
    edit here. S2170 established this inside archive-temp.ps1 after a hand-kept copy of the
    coordination names left five live queue directories unprotected - the archiver knew only the two
    pre-split names, so a sweep could have moved a running session's queue away. Only names that no
    authority declares are enumerated below, each with the writer that puts it there, so a later
    reader can re-judge the row instead of preserving it out of caution.

    Consumers: scripts/utils/archive-temp.ps1 (what a sweep may never move) and
    scripts/quality/assert-temp-root-inventory.ps1 (what the root may hold). Both read this file;
    neither keeps a copy.

.EXAMPLE
    . (Join-Path $PSScriptRoot 'temp-root-inventory.ps1')
    $inv = Get-TempRootInventory -RepoRoot $repoRoot

.NOTES
    Dot-sourced library. It defines ONE function and assigns nothing at script scope, because a
    dot-sourced file assigns into its CALLER's scope: S2441 records ten scripts under scripts/ whose
    parameters collided with a forwarder's internals, and every dev/CHANGELOG.md row written on
    2026-09-03 recorded a harness path where the ticket id belonged. RepoRoot is a parameter rather
    than $PSScriptRoot for the same reason the neighbouring libraries take it - a dot-sourced file's
    idea of its own location is the caller's, and scripts/quality/lib/gate-placement-registry.ps1
    already settled the pattern here.

    Exit codes: none of its own. Dot-sourcing defines the function and returns; a broken checkout
    that cannot resolve the lock-domain table throws, which is not a condition a caller can handle.
#>

function Get-TempRootInventory {
    <#
    .SYNOPSIS
        Returns the allowed top-level content of temp/.

    .DESCRIPTION
        Emits an object with four members:
          FixedDirs            - directory names that must never be moved or reported.
          FixedFiles           - file names of the same standing.
          FixedFilePatterns    - wildcard patterns of the same standing: a LIVE sink, appended to
                                 while it sits there, so a sweep must not move it either.
          RetainedFilePatterns - wildcard patterns for legal per-run artifacts. Legal at the root
                                 AND sweepable: retention is precisely what makes them legal, so a
                                 consumer that protected them would stop the retention that was the
                                 reason to allow them. This is the one member the two consumers read
                                 differently, and the difference is deliberate.

        The per-ticket class (temp/Sxxxx/) is deliberately NOT a member: it is decided by catalog
        status, which is the archiver's job and not a name list. A consumer must treat it as its own
        class or it will judge a live ticket's scratch by the wrong rule.

    .PARAMETER RepoRoot
        Repository root. Used to locate the lock-domain table and to resolve profile paths.
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$RepoRoot
    )

    # Dot-sourced INSIDE the function so the forwarder's own internals land in this function's
    # local scope and never reach the caller that dot-sourced this file (S2441).
    . (Join-Path $RepoRoot 'scripts/utils/agent-lock-domains.ps1')
    $domainNames = @(Get-AgentLockDomainNames)
    if ($domainNames.Count -eq 0) {
        throw 'temp-root-inventory: the lock-domain table returned no domains, so the coordination names cannot be derived.'
    }
    $upperDomains = @($domainNames | ForEach-Object { $_.ToUpper() })

    # Harness-owned paths. Read from the profile rather than spelled out: a literal here would be a
    # second declaration of a path the canon-shipped harness already owns, which is the defect this
    # file exists to remove. The per-key fallback matches release-freeze.ps1's pattern - a profile
    # that cannot be read must not make the whole inventory unavailable, because the consumer that
    # loses it is a sweep that would then move the unprotected name away.
    $harnessKeys = [ordered]@{
        leasesDir        = 'temp/SPEC-TICKET.LEASES'
        leaseHandoffDir  = 'temp/LEASE-HANDOFF'
        lockHandoffDir   = 'temp/LOCK-HANDOFF'
        agentChatDir     = 'temp/AGENT-CHAT'
        queueRunsDir     = 'temp/spec-queue'
        migrateDoneDir   = 'temp/done'
        contextSignalDir = 'temp/context-signal'
        queueStopFile    = 'temp/STOP-SPEC-QUEUE'
        specAllQueueLock = 'temp/spec-all-queue.lock'
        skipCache        = 'temp/spec-next-skip-cache.json'
    }
    $harnessDirKeys = @('leasesDir', 'leaseHandoffDir', 'lockHandoffDir', 'agentChatDir', 'queueRunsDir', 'migrateDoneDir', 'contextSignalDir')
    $harnessDirs = [System.Collections.Generic.List[string]]::new()
    $harnessFiles = [System.Collections.Generic.List[string]]::new()
    foreach ($key in $harnessKeys.Keys) {
        $relative = try { Get-SzaPath $key -Relative } catch { $harnessKeys[$key] }
        # Only the leaf matters: this inventory answers about the top level of temp/, so a harness
        # path pointed somewhere deeper contributes no root entry at all.
        $normalized = ($relative -replace '\\', '/').TrimEnd('/')
        if ($normalized -notmatch '^temp/[^/]+$') { continue }
        $leaf = $normalized.Substring('temp/'.Length)
        if ($harnessDirKeys -contains $key) { $harnessDirs.Add($leaf) } else { $harnessFiles.Add($leaf) }
    }

    $fixedDirs = [System.Collections.Generic.List[string]]::new()
    $fixedDirs.AddRange([string[]]$harnessDirs)
    # One queue directory per lock domain (S2109). Derived, per S2170.
    $fixedDirs.AddRange([string[]]@($upperDomains | ForEach-Object { "$_.QUEUE" }))
    # The two device stores, derived from their owner's declaration (S3036). Hand-listed when S3030
    # wrote this file, because nothing declared them then; the row that mattered is DEVICE.REGISTRY -
    # the durable roster Rule 35 reads, idle for a week in normal use, so the age rule must never
    # reach it. Contract: scripts/devtest/lib/device-store-paths.tests/ case E7 fails if a rename
    # reaches the declaration but not this protection.
    . (Join-Path $RepoRoot 'scripts/devtest/lib/device-store-paths.ps1')
    $fixedDirs.AddRange([string[]]@(@('Lease', 'Registry', 'State') | ForEach-Object { Get-DeviceStoreDirName -Store $_ }))
    $fixedDirs.AddRange([string[]]@(
        'archive'                          # archive-temp.ps1's own destination - it moves stale entries here.
        'scratch'                          # Rule 10's home for work with no active ticket.
        'gradle-tmp'                       # init-ramdisk.ps1; pruned by post-release-cleanup.ps1.
        'sessions'                         # protected before S3030; carried forward rather than narrowed.
        'test-devices'                     # protected before S3030; carried forward rather than narrowed.
        'GRADLE-RUN'                       # builders/check-standard-fast.ps1 - one run-<pid>.json per gradle run, read for orphan detection.
        'TEST-REPORTS'                     # builders/filtered-test-report.ps1.
        'monitor'                          # utils/dev-monitor-writer.ps1 (-OutDir default).
        'fast-path-signal'                 # the Rule 29 hook .claude/hooks/nudge-check-target-by-change-type.ps1 - one marker per session so the fast-path nudge is said once; pruned after 7 days by the hook itself.
        'metrics'                          # metrics/agent-cost-report.ps1, and the gate journal the profile names.
        'agent-cost'                       # metrics/ticket-cost.ps1 - the per-ticket cost journal ticket-cost.jsonl.
        'sza-forwarders-backup'            # utils/install-sza-forwarders.ps1 - pre-install copy of the replaced local scripts.
        'detekt-scoped'                    # quality/detekt-scoped.ps1.
        'flavor-guard'                     # guard/flavor-isolation-guard.ps1.
        'isolated-stdout'                  # utils/invoke-isolated-stdout.ps1 (-LogDirectory default).
        'ocrbench'                         # ocrbench/fetch-real-scenes.ps1 cache.
        'store-shot-media'                 # release/seed-store-shot-media.ps1.
        'play-shots'                       # release/capture-play-screenshots.ps1.
        'play-shots-tablet'                # release/capture-play-screenshots.ps1 -Tablet.
        'channel-preview-frames'           # streams/collect-stream-candidates.ps1 (-PreviewFrameDir default).
        'channel-preview-publish'          # protected before S3030; stream-catalog publish staging.
        'stream-logo-src'                  # protected before S3030; stream-catalog logo source.
        'stream-logo-publish'              # protected before S3030; stream-catalog logo staging.
        'tile-pack-publish'                # protected before S3030; tile-pack staging.
        'tile-pack-channel-preview-tiles'  # protected before S3030; tile-pack staging.
        'tile-pack-stream-logo-tiles'      # protected before S3030; tile-pack staging.
    ))

    $fixedFiles = [System.Collections.Generic.List[string]]::new()
    $fixedFiles.AddRange([string[]]$harnessFiles)
    # One lock file per domain (S2109). Derived, per S2170.
    $fixedFiles.AddRange([string[]]@($upperDomains | ForEach-Object { "$_.LOCK" }))
    $fixedFiles.AddRange([string[]]@(
        '.gitignore'                          # keeps the directory in the index while its contents stay out.
        'current.log'                         # utils/search-log.ps1 reads this name by default.
        'EMPTY-STDIN.txt'                     # utils/start-detached.ps1 - one reusable sentinel, created once.
        'catalog-touch.marker'                # dev/CATALOG/scripts/query.ps1 writes it, the Rule 29 hook guard-catalog-before-kt-search.ps1 reads it, reset-catalog-touch-marker.ps1 removes it at SessionStart. Absent from S3030's census because it exists only between a catalog query and the next session start - an intermittent name is the one a one-shot census cannot see.
        'build-failure-digest.json'           # builders/build-failure-digest.ps1, behind .\a.ps1 bf. Overwritten in place.
        'detekt-gate-cache.json'              # quality/assert-detekt.ps1 - the clean-run cache keyed by a fingerprint of the analysed set.
        'standard-surface-snapshot.json'      # release/standard-surface-snapshot.ps1 (-OutFile default), read by release/standard-release-gate.ps1.
        'RELEASE-FREEZE.json'                 # Rule 36's marker. Omitted by the pre-S3030 copy, so a freeze held past the age cutoff could be swept mid-sweep.
        'RELEASE-FREEZE-FINGERPRINTS.json'    # quality/release-scope-fingerprint.ps1 - the freeze's content half, omitted by the same copy.
        'RELEASE-FREEZE-ENDED.json'           # utils/release-freeze.ps1 - what the last freeze ended as, read by -Verb Status so "no freeze held" can be told apart from "yours ended under you" (S3320). A separate name from the marker on purpose: guard-release-freeze.ps1's hot path is one Test-Path on the marker, and a tombstone there would spawn its child on every Bash call forever.
        'STOP-AGENT-WATCHDOG'                 # operator-created stop flag, read by utils/agent-watchdog.ps1. No script writes it.
        'stream-catalog-liveness.csv'         # streams/collect-stream-candidates.ps1 (-CatalogLivenessReport default).
        'stream-catalog.zip'                  # streams/modules/StreamPublisher.Delivery.ps1.
        'stream-candidates.csv'               # streams/collect-stream-candidates.ps1 (-OutDir default is temp).
        'stream-candidates-report.csv'        # same writer, same default.
    ))

    $retained = [System.Collections.Generic.List[string]]::new()
    # Turn markers, one per domain (S2405). agent-lock.ps1 sweeps its own, so declaring them costs
    # no retention; they are declared because one exists at the root whenever a session is queued and
    # a gate that did not know them would report a live queue position as a stray.
    $retained.AddRange([string[]]@($upperDomains | ForEach-Object { "$_.TURN-*.json" }))
    $retained.AddRange([string[]]@(
        # The per-run check and build transcripts - the bulk of the root, and legal. Four entry
        # points write them (builders/check-standard-fast.ps1, builders/build-debug.PS1,
        # builders/build-standard-device.ps1, utils/check-typo-lint.ps1) and three scripts READ them
        # from the root by glob: utils/measure-build-lock-wait.ps1, metrics/measure-unit-fork-parallelism.ps1
        # and builders/get-last-build-failure.ps1. That makes the corpus load-bearing, not disposable:
        # dev/REFUTED_APPROACHES.md instructs a future session to re-run the first of those three
        # before re-proposing a refuted build-lock change, and S2606 reconstructed 1281 runs from it.
        # Relocating them is therefore a ticket with three readers to move, not a cleanup - and it is
        # a declared non-goal of S3030.
        'check_fast_*.log'
        'build_debug_*.log'
        # The logcat pattern is retained on different grounds from the three transcripts around it:
        # it has NO reader. Until S3297 it also had no bound - the five device builders started a
        # background `adb logcat` stream nobody stopped, and this very declaration is what kept the
        # accumulation legal at the root while it reached 1.8 GB across 16 files. Each file is now a
        # bounded per-run snapshot of the install-and-launch window, written by those builders
        # through devtest/lib/logcat-snapshot.ps1, which terminates on its own.
        'logcat_*.log'
        'lint-typo-activity-*.log'
        # The stderr sibling of any of the per-run logs above: builders/gradle-progress-watch.ps1
        # writes "$LogPath.err" beside the log it is watching. Undeclared until S3299, when the gate
        # reported two live build-run files as dead weight - the writer was there all along, only
        # the declaration was missing, and a per-run artifact nobody can account for is exactly the
        # reading this file exists to prevent.
        '*.log.err'
        'spec-next-session.*.json'            # one per session by design; the writer globs its own siblings to see cross-session state.
        'plan-tick-last-*.json'               # one per agent session; the Rule 29 hook guard-plan-tick-batching.ps1 writes it on a Done tick and reads it on the next one, which is the only way a PreToolUse hook can see two consecutive calls.
        'streams.csv.*.bak'                   # streams/modules/StreamPublisher.Delivery.ps1, -OutDir default temp.
        'stream-catalog-liveness.*.csv'       # timestamped variants of the canonical report above.
        'replacement-char-dropped.*.csv'      # same writer - per-run row-drop reports.
        'name-normalization-moves.*.csv'      # same writer.
        'identity-duplicates-dropped.*.csv'   # same writer.
    ))

    # A live sink, not a per-run artifact: the log tooling APPENDS to whichever of these is current,
    # so moving one mid-append is the one thing a sweep must not do. Protected before S3030 and
    # protected after it - this member exists so that standing is not quietly lost in the move to a
    # single declaration.
    $fixedFilePatterns = @('fastmediasorter_*.log')

    return [pscustomobject]@{
        FixedDirs            = @($fixedDirs | Sort-Object -Unique)
        FixedFiles           = @($fixedFiles | Sort-Object -Unique)
        FixedFilePatterns    = @($fixedFilePatterns | Sort-Object -Unique)
        RetainedFilePatterns = @($retained | Sort-Object -Unique)
    }
}
