#requires -Version 7.0
<#
.SYNOPSIS
    S3075, S4380: one way for a gate to say "my input is not in this checkout" - COULD NOT VERIFY
    by default, NOT APPLICABLE when the caller declared such a checkout acceptable.

.DESCRIPTION
    Two of this project's directories are gitignored whole - `PLAN/` (the ticket journal and every
    spec file) and `.claude/` (commands, rules, hooks, agent memory). A gate whose subject lives in
    one of them can be run in three environments: the workstation, where the directory is present
    and the verdict is real; a fresh clone or a release worktree, where it is absent; and a CI
    runner, where it is absent for the same reason.

    Before S3075 those gates ended the absent case with Write-Error + exit 1, which says
    "violation". The batch runner then had nothing to go on - it maps any non-zero child to FAIL -
    so the GitHub "Static Gates" job of run 34694019558 reported nine findings that were really one
    fact stated nine times: the directories are not published. A verdict that is red on every run
    is not read, which is the same degradation the YOUR SET / THE TREE split in
    assert-fast-gates.ps1 exists to prevent.

    S3075 gave the absent case exit 3, and S4380 took it back: CHECK-VERDICT 2.1 reserves 3 for a
    check that ran, found something and could not attribute it to the caller, and 0.12 item A.2
    makes a requested check that did not run for a reason the caller did not choose
    `COULD NOT VERIFY` with exit 2. A missing input found nothing, so exit 3 told a caller "an
    advisory stands" about a run that judged nothing. The published fact is still real, so the
    caller that expects it says so: a gate takes `-AllowAbsentInput`, passes it here as `-Allow`,
    and the same absence then ends `NOT APPLICABLE` with exit 0 (item A.1), which every aggregator
    counts with the skipped and never with the passed. `assert-fast-gates.ps1` is that caller,
    because the GitHub Static Gates job runs it bare on a clone.

    What this is NOT for: an input that is absent because something is broken. A missing baseline
    file, an unreadable snapshot, a generator that will not run - those are findings, and a gate
    that routes them here would report "not verifiable" about its own defect. The test is whether
    the absence is a PUBLISHED property of the checkout: a gitignored root, and nothing else.

.EXAMPLE
    . (Join-Path $PSScriptRoot 'lib/absent-input.ps1')
    if (-not (Test-Path -LiteralPath $planRoot)) {
        Exit-InputAbsent -Gate 'assert-no-line-budget' -Path 'PLAN/' `
            -Reason 'gitignored - present only on a workstation checkout' -Allow:$AllowAbsentInput
    }

Exit codes: this file defines a function that ends its CALLING script with 2 (COULD NOT VERIFY) or,
with -Allow, 0 (NOT APPLICABLE); it returns no code of its own.
#>

function Exit-InputAbsent {
    <#
        Names the gate, the missing path and why its absence is legitimate, then ends the CALLING
        script. Dot-sourced deliberately: `exit` inside a dot-sourced function ends the caller,
        which is exactly the contract - a gate that cannot verify must not continue into a
        judgement it would have to invent.

        -Allow is the caller's declaration (the gate's -AllowAbsentInput switch): the absence is
        expected for this run, so nothing was requested of the gate and it answers
        `NOT APPLICABLE`. Without it the work was requested and could not run: reason on stderr
        (rule 2.4), `COULD NOT VERIFY` as the last stdout line (rules 2.5, 2.11), exit 2.
    #>
    param(
        [Parameter(Mandatory = $true)][string]$Gate,
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$Reason,
        [switch]$Allow
    )

    if ($Allow) {
        Write-Host "${Gate}: NOT APPLICABLE ($Path absent: $Reason; -AllowAbsentInput)"
        exit 0
    }

    # -ErrorAction Continue: a gate runs under $ErrorActionPreference = 'Stop', where a bare
    # Write-Error is terminating and the exit below would never run (CHECK-VERDICT rule 2.3).
    Write-Error "${Gate}: cannot verify - $Path absent ($Reason)" -ErrorAction Continue
    Write-Host "${Gate}: COULD NOT VERIFY ($Path absent)"
    exit 2
}
