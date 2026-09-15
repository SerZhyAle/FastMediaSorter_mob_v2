#requires -Version 7.0
<#
.SYNOPSIS
    S3075: one way for a gate to say "cannot verify - my input is not in this checkout".

.DESCRIPTION
    Two of this project's directories are gitignored whole - `PLAN/` (the ticket journal and every
    spec file) and `.claude/` (commands, rules, hooks, agent memory). A gate whose subject lives in
    one of them can be run in three environments: the workstation, where the directory is present
    and the verdict is real; a fresh clone or a release worktree, where it is absent; and a CI
    runner, where it is absent for the same reason.

    Before this file those gates ended the absent case with Write-Error + exit 1, which says
    "violation". The batch runner then had nothing to go on - it maps any non-zero child to FAIL -
    so the GitHub "Static Gates" job of run 34694019558 reported nine findings that were really one
    fact stated nine times: the directories are not published. A verdict that is red on every run
    is not read, which is the same degradation the YOUR SET / THE TREE split in
    assert-fast-gates.ps1 exists to prevent.

    So the absent case gets its own code. Exit 3 is not a new convention: lib/fixed-input-scope.ps1
    already leaves with it when a fixed-input gate was handed a changed set that charges it nothing,
    and it means the same thing there - this run judged nothing, so it is not evidence either way.
    assert-fast-gates.ps1 renders it as SKIP in its own NOT RUN block, and -FailOnSkipped puts it
    back into the failure count for a caller that requires full coverage.

    What this is NOT for: an input that is absent because something is broken. A missing baseline
    file, an unreadable snapshot, a generator that will not run - those are findings, and a gate
    that routes them here would report "not verifiable" about its own defect. The test is whether
    the absence is a PUBLISHED property of the checkout: a gitignored root, and nothing else.

.EXAMPLE
    . (Join-Path $PSScriptRoot 'lib/absent-input.ps1')
    if (-not (Test-Path -LiteralPath $planRoot)) {
        Exit-InputAbsent -Gate 'assert-no-line-budget' -Path 'PLAN/' -Reason 'gitignored - present only on a workstation checkout'
    }

Exit codes: this file defines a function that exits 3 in its caller; it returns no code of its own.
#>

function Exit-InputAbsent {
    <#
        Prints one line naming the gate, the missing path and why its absence is legitimate, then
        ends the CALLING script with exit 3. Dot-sourced deliberately: `exit` inside a dot-sourced
        function ends the caller, which is exactly the contract - a gate that cannot verify must not
        continue into a judgement it would have to invent.
    #>
    param(
        [Parameter(Mandatory = $true)][string]$Gate,
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$Reason
    )

    Write-Host "${Gate}: cannot verify - $Path absent ($Reason)" -ForegroundColor Yellow
    exit 3
}
