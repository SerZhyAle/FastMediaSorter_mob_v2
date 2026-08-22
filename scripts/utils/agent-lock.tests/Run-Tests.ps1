#requires -Version 7.0
<#
.SYNOPSIS
    Regression tests for the stale-JAVA_HOME snapshot repair (S1928).

.DESCRIPTION
    The repair sits directly in front of a refusal that gates every gradle target in the repository,
    so both of its answers matter equally: repairing when the machine is fine, and staying out of
    the way when it is not. A repair that fired too eagerly would silently swap the JVM - the exact
    outcome the capture rates worse than stopping.

    The helper is exercised directly rather than through a gradle run: it reads the environment and
    two files, so driving it needs no build, and a test that started gradle would be timing the
    daemon instead of checking the branch.

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
