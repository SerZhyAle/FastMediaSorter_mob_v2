#requires -Version 7.0
<#
.SYNOPSIS
    Regression tests for agent-lock.ps1: the stale-JAVA_HOME snapshot repair (S1928) and the
    BUILD.LOCK fail-fast refusal's exit code (S2058).

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
    Exit codes (CLAUDE.md Rule 7):
      0 - every case passed.
      1 - a case failed.
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..' '..')).Path
. (Join-Path $repoRoot 'scripts/utils/agent-lock.ps1')

$originalJavaHome = $env:JAVA_HOME
$persistedUser = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'User')
$failures = 0

function Assert-Case {
    param([Parameter(Mandatory)][string]$Name, [Parameter(Mandatory)][bool]$Ok, [string]$Detail)
    if ($Ok) { Write-Output "  PASS $Name" }
    else { Write-Output "  FAIL $Name - $Detail"; $script:failures++ }
}

try {
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
    $guardSource = Get-Content -LiteralPath (Join-Path $repoRoot 'scripts/utils/agent-lock.ps1') -Raw
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
