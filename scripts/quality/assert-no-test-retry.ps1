#requires -Version 7.0
<#
.SYNOPSIS
    Gate: no test in either module may be re-run to green. A retry annotation, a retry TestRule, a
    retry runner or the Gradle test-retry plugin is refused wherever a test source can reach it.

.DESCRIPTION
    S3371 phase 05, the mechanical half of the flaky policy. An intermittently failing test is
    quarantined with a ticket in docs/test-flaky-quarantine.jsonl and fixed; it is never wrapped in
    a mechanism that runs it again until it passes, and it is never silently deleted.

    WHY RETRY IS THE ONE FORBIDDEN CURE. Retry-to-green does not remove the flake, it removes the
    REPORT of the flake, and it does so with a fixed probability: a test that fails one run in five
    passes 99.9% of the time behind three retries, so the defect underneath it - a race, an
    uncancelled coroutine, a shared static, a real product bug that only shows under load - stays in
    the tree and keeps its statistical cover indefinitely. The same test under quarantine stays
    visible in every CI report, carries a ticket, and the ledger row is what someone eventually
    reads. One mechanism hides the signal; the other keeps it and names its owner.

    It also travels. A retry rule added for one class is a dependency in the module, and the next
    flaky test reaches for the rule that is already there rather than for the ledger. That is why
    this is a gate and not a review note - by the time a reviewer sees the second use, the habit
    already exists.

    WHAT IS A SUBJECT. Every .kt and .java file under the test source sets of both modules (see
    $script:TestTrees), plus the build scripts that can install a retry plugin for those tasks (see
    $script:BuildScripts) - the plugin route reaches every test in the module without touching one
    test file, so a gate that read only test sources would miss the cheapest way to do the thing it
    forbids.

    FOUR DETECTORS, each matching a MECHANISM and never a name:

      retry-annotation  @Retry, @RetryTest, @RetryOnFailure, @RepeatedIfExceptionsTest,
                        @RepeatFailedTest - the annotation forms shipped by the common JUnit 4 and
                        JUnit 5 retry extensions.
      retry-rule        a @Rule / @get:Rule / @ClassRule declaration whose type name carries Retry,
                        which is the JUnit 4 way to wrap a test in a retry loop.
      retry-runner      @RunWith(..Retry..Runner::class), the same trick one level up.
      gradle-test-retry the org.gradle.test-retry plugin or a testRetry { } block, which retries
                        every test of a module from outside the sources.

    Deliberately NOT a detector: the word "retry" in a class or method name. The tree legitimately
    holds IoContractErrorRetryTest, which TESTS the transport's own retry policy - a gate that read
    names rather than mechanisms would refuse the test of a feature because of the feature. For the
    same reason a caught AssertionError is not a detector: this phase's own leak-watch canary
    catches one to prove the harness detects a seeded leak, and that is evidence, not concealment.

.PARAMETER Gate
    Exit 1 when any retry mechanism is found. Without it the script reports and exits 0.

.PARAMETER List
    Print every scanned tree and the file count behind the verdict.

.PARAMETER RepoRoot
    Repository root to scan. Defaults to the parent of scripts/quality. The contract suite points it
    at a fixture tree so the refusals really execute.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-no-test-retry.ps1 -Gate

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-no-test-retry.ps1 -List

.NOTES
    Exit codes (CLAUDE.md Rule 7):
      0  no retry mechanism in any test source or build script (or reporting only).
      1  under -Gate: at least one retry mechanism was found.
      2  cannot verify - not one scanned test tree exists under -RepoRoot, so a green verdict would
         mean "did not look".
#>
[CmdletBinding()]
param(
    [switch]$Gate,
    [switch]$List,
    [string]$RepoRoot,
    [switch]$Help
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if ($Help) {
    Get-Help -Full $MyInvocation.MyCommand.Path
    exit 0
}

if (-not $RepoRoot) { $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path }

# The test source sets of both active modules. A third module is a row here and no other edit.
$script:TestTrees = @(
    'app_v2/src/test'
    'app_v2/src/androidTest'
    'wear/src/test'
    'wear/src/androidTest'
)

# The build scripts that can install a retry plugin for the tasks above.
$script:BuildScripts = @(
    'build.gradle.kts'
    'settings.gradle.kts'
    'app_v2/build.gradle.kts'
    'wear/build.gradle.kts'
)

# Each detector is a mechanism, never a name. See the header for what each one is and what it
# deliberately does not match.
$script:SourceDetectors = @(
    [pscustomobject]@{
        Name    = 'retry-annotation'
        Pattern = [regex]'(?m)^\s*@(?:Retry|RetryTest|RetryOnFailure|RepeatedIfExceptionsTest|RepeatFailedTest)\b'
        Cure    = 'Delete the annotation and quarantine the test in docs/test-flaky-quarantine.jsonl with a ticket.'
    }
    [pscustomobject]@{
        Name    = 'retry-rule'
        Pattern = [regex]'(?ms)@(?:get:)?(?:Class)?Rule[^\r\n]*[\r\n\s]*(?:@[A-Za-z][\w.]*[^\r\n]*[\r\n\s]*)*(?:public\s+|final\s+|val\s+|var\s+)[^\r\n]*\b[A-Za-z0-9_.]*Retry[A-Za-z0-9_.]*Rule\b'
        Cure    = 'Delete the rule declaration. A rule that re-runs a failed test hides the defect from every later reader.'
    }
    [pscustomobject]@{
        Name    = 'retry-runner'
        Pattern = [regex]'(?m)@RunWith\s*\(\s*[A-Za-z0-9_.]*Retry[A-Za-z0-9_.]*'
        Cure    = 'Run the test with the ordinary runner and quarantine it instead.'
    }
)

$script:BuildScriptDetectors = @(
    [pscustomobject]@{
        Name    = 'gradle-test-retry'
        Pattern = [regex]'(?m)(?:org\.gradle\.test-retry|gradle-test-retry|\btestRetry\b)'
        Cure    = 'Remove the plugin or the testRetry block. It retries every test of the module, so it hides flakes nobody has even seen yet.'
    }
)

function Get-RetryFinding {
    <#
        One record per detector hit. Returns an empty list when nothing matches; the caller wraps
        the call in @( ) because an empty List[T] unrolls to $null on return and $null.Count throws
        under StrictMode.
    #>
    param(
        [Parameter(Mandatory)][string]$Root,
        [Parameter(Mandatory)][AllowEmptyCollection()][string[]]$Trees,
        [Parameter(Mandatory)][AllowEmptyCollection()][string[]]$Scripts
    )

    $findings = [System.Collections.Generic.List[object]]::new()

    foreach ($tree in $Trees) {
        $full = Join-Path $Root $tree
        if (-not (Test-Path -LiteralPath $full)) { continue }

        foreach ($file in Get-ChildItem -LiteralPath $full -Recurse -File | Where-Object { $_.Extension -in '.kt', '.java' }) {
            $text = Get-Content -LiteralPath $file.FullName -Raw
            if (-not $text) { continue }
            $relative = $file.FullName.Substring($Root.Length).TrimStart('\', '/').Replace('\', '/')

            foreach ($detector in $script:SourceDetectors) {
                $match = $detector.Pattern.Match($text)
                if (-not $match.Success) { continue }
                $findings.Add([pscustomobject]@{
                        File     = $relative
                        Detector = $detector.Name
                        Evidence = ($match.Value -split '\r?\n' | Where-Object { $_.Trim() } | Select-Object -First 1).Trim()
                        Cure     = $detector.Cure
                    })
            }
        }
    }

    foreach ($script in $Scripts) {
        $full = Join-Path $Root $script
        if (-not (Test-Path -LiteralPath $full)) { continue }
        $text = Get-Content -LiteralPath $full -Raw
        if (-not $text) { continue }

        foreach ($detector in $script:BuildScriptDetectors) {
            $match = $detector.Pattern.Match($text)
            if (-not $match.Success) { continue }
            $findings.Add([pscustomobject]@{
                    File     = $script
                    Detector = $detector.Name
                    Evidence = $match.Value.Trim()
                    Cure     = $detector.Cure
                })
        }
    }

    return $findings
}

$existingTrees = @($script:TestTrees | Where-Object { Test-Path -LiteralPath (Join-Path $RepoRoot $_) })
if ($existingTrees.Count -eq 0) {
    [Console]::Error.WriteLine("assert-no-test-retry: cannot verify - not one test source tree exists under '$RepoRoot' (looked for: $($script:TestTrees -join ', ')).")
    exit 2
}

$existingScripts = @($script:BuildScripts | Where-Object { Test-Path -LiteralPath (Join-Path $RepoRoot $_) })
$findings = @(Get-RetryFinding -Root $RepoRoot -Trees $existingTrees -Scripts $existingScripts)

if ($List) {
    Write-Host ("assert-no-test-retry: scanned {0} test tree(s) and {1} build script(s) under {2}" -f `
            $existingTrees.Count, $existingScripts.Count, $RepoRoot)
    foreach ($tree in $existingTrees) { Write-Host ("  tree   {0}" -f $tree) }
    foreach ($script in $existingScripts) { Write-Host ("  script {0}" -f $script) }
}

if ($findings.Count -gt 0) {
    Write-Host ("assert-no-test-retry: FAIL - {0} retry-to-green mechanism(s) reach a test of this repository:" -f $findings.Count) -ForegroundColor Red
    foreach ($finding in $findings) {
        Write-Host ("  {0} | {1} | {2}" -f $finding.File, $finding.Detector, $finding.Evidence) -ForegroundColor Red
        Write-Host ("      {0}" -f $finding.Cure) -ForegroundColor Red
    }
    Write-Host "  A retried test hides its own defect at a fixed probability and keeps hiding it. Quarantine it instead:" -ForegroundColor Red
    Write-Host "  add a row to docs/test-flaky-quarantine.jsonl naming the test, the symptom, a live ticket and the date it entered." -ForegroundColor Red
    if ($Gate) { exit 1 }
    exit 0
}

Write-Host ("assert-no-test-retry: PASS - {0} test tree(s) and {1} build script(s) carry no retry-to-green mechanism." -f `
        $existingTrees.Count, $existingScripts.Count) -ForegroundColor Green
exit 0
