# Run-Tests.ps1 (S2581) - regression suite for scripts/spec_catalog/check-block-note.ps1 and the
# shared resolver it decides with, spec_catalog/_blocker-links.ps1.
#
# What broke: CLAUDE.md section 4 has listed the Block* status note first among the gated
# transitions since it was written, and the same statement stands in .claude/rules/spec-catalog.md
# and AGENTS.md - but no gate implemented it. Assert-ClosingGates guarded Implemented, Verified and
# BlockNeedUserTest only, so three of the four Block* statuses never reached it at all. Measured
# 2026-09-05: of 151 Block* records exactly one carried no note, and that one (S1126) had been
# silently unselectable since 2026-08-21 because preview.ps1 skips it as `blocker-unresolvable` -
# a verdict that means "fix the spec", printed where only the picker looks.
#
# The cases that matter here are the ones where a naive implementation looks correct:
#   * a whitespace-only note is not a note,
#   * for BlockByOtherTask a note alone is not enough - the blocker must be readable as a literal
#     token or a **Depends on:** line, because section 10 prose names neighbours and consumers
#     beside blockers and as often DENIES the dependency it mentions (S1482),
#   * a duplicate heading must NOT refuse a BlockQuestions park: that status is /spec-tech's escape
#     hatch when a placement decision is missing and asking is forbidden, so refusing it leaves a
#     stuck pipeline with nowhere to put its ticket,
#   * BlockNeedUserTest keeps its full checker set - the note gate is added to it, not substituted,
#   * a malformed id exits 2, so "could not look" stays distinct from "looked and refused".
#
# Every mutating case runs against a SANDBOX catalog, never the live journal: SZA_PROJECT_ROOT
# decides WHERE the harness writes, and the harness scripts are invoked DIRECTLY rather than
# through scripts/spec_catalog/*.ps1, because a forwarder overwrites that variable with the repo
# root and would take a real lock on the real catalog (S2520).
#
# Usage:  pwsh -NoProfile -File scripts/spec_catalog/check-block-note.tests/Run-Tests.ps1
#
# Exit codes:
#   0   all cases pass (or the resolved harness predates the fix and they were skipped).
#   1   at least one case failed.
#   2   the fixtures could not be prepared.

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..' '..')).Path
$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") {
    "$env:ProgramFiles\PowerShell\7\pwsh.exe"
} else { 'pwsh' }

$script:pass = 0
$script:fail = 0
$script:skipped = 0

function Assert-That([string]$name, [bool]$ok, [string]$detail) {
    if ($ok) {
        $script:pass++
        Write-Host ("  PASS  {0}" -f $name) -ForegroundColor DarkGreen
    } else {
        $script:fail++
        Write-Host ("  FAIL  {0}" -f $name) -ForegroundColor Red
        if ($detail) { Write-Host ("        {0}" -f $detail) -ForegroundColor Red }
    }
}

# Dot-source any harness forwarder to bring the profile helpers (Get-SzaHarnessScript) into scope.
. (Join-Path $repoRoot 'scripts/spec_catalog/_status-sets.ps1')

$sandboxRoot = Join-Path $repoRoot 'temp/scratch/S2581-check-block-note'
$sandboxPlan = Join-Path $sandboxRoot 'PLAN'

try {
    if (Test-Path -LiteralPath $sandboxRoot) {
        Remove-Item -LiteralPath $sandboxRoot -Recurse -Force -Confirm:$false
    }
    New-Item -ItemType Directory -Force -Path $sandboxPlan | Out-Null
    New-Item -ItemType Directory -Force -Path (Join-Path $sandboxRoot 'temp') | Out-Null
    Copy-Item (Join-Path $repoRoot '.sza-profile.json') (Join-Path $sandboxRoot '.sza-profile.json') -Force
    # A project-root marker, so the harness's upward walk cannot escape into the real repository
    # even if SZA_PROJECT_ROOT were ever dropped.
    Set-Content -LiteralPath (Join-Path $sandboxRoot 'CLAUDE.md') -Value '# sandbox' -Encoding utf8
} catch {
    Write-Error "Could not prepare the sandbox: $($_.Exception.Message)" -ErrorAction Continue
    exit 2
}

$utf8 = New-Object System.Text.UTF8Encoding($false)
$fixtures = [ordered]@{
    'S9001' = @('plain',         "# S9001`n`n**Status:** Approved`n`n## 1. Problem`n`ntext`n")
    'S9002' = @('token-sec10',   "# S9002`n`n**Status:** Approved`n`n## 10. Links`n`n- Blocker: S9001 - waits on it`n")
    'S9003' = @('prose-only',    "# S9003`n`n**Status:** Approved`n`n## 10. Links`n`n- S9001 related, does not block`n")
    'S9004' = @('depends-line',  "# S9004`n`n**Status:** Approved`n`n**Depends on:** S9001`n`n## 1. Problem`n`ntext`n")
    'S9005' = @('dup-headings',  "# S9005`n`n**Status:** Approved`n`n## 1. Problem`n`na`n`n## 1. Problem`n`nb`n")
}

function Reset-Sandbox {
    $lines = New-Object System.Collections.Generic.List[string]
    foreach ($id in $fixtures.Keys) {
        $rel = 'PLAN/' + $id + '_' + $fixtures[$id][0] + '.md'
        [System.IO.File]::WriteAllText((Join-Path $sandboxRoot ($rel.Replace('/', '\'))), $fixtures[$id][1], $utf8)
        $lines.Add((@{
            id = $id; name = $fixtures[$id][0]; status = 'Approved'; priority = 50
            file = $rel; created = '2026-09-05'; updated = '2026-09-05 10:00'
        } | ConvertTo-Json -Compress))
    }
    [System.IO.File]::WriteAllLines((Join-Path $sandboxPlan 'spec-catalog.jsonl'), $lines, $utf8)
}

# S2402: the local check-block-note.ps1 is a generated forwarder, so the SUBJECT of these cases is
# the shipped harness, and between the canon edit and the deploy those are different files. Only the
# owner can deploy the plugin, so a suite that went red in the meantime would hand every
# neighbouring session a failure no session running it can fix - S2577 and S2578 met this first and
# skipped with a named reason instead. The resolved path comes from the harness's OWN resolver
# rather than a restatement of the forwarder's search order, so this reads the very file the CLI
# below will run; Get-Command would answer "absent" no matter what is deployed, because this is a
# CLI and its functions never enter the caller's scope (S2578).
#
# Deliberately an if/else and not an early `return`: a return here would leave the trailing `exit`
# unreached, so a run that had already FAILED an earlier case and then met an undeployed harness
# would end in a silent exit 0 - a green verdict that observed nothing.
#
# The resolution mirrors the FORWARDER's order, not just Get-SzaHarnessScript's answer. The two
# differ exactly in the state this ticket ships in: Get-SzaHarnessScript returns the plugin-cache
# path whether or not the file is there, while a forwarder whose cache probe misses falls through to
# the canon checkout - so on 2026-09-05 the gate ran correctly through scripts/spec_catalog/
# check-block-note.ps1 while a cache-only check called it undeployed and skipped all 13 cases. A
# suite that skips work it could have done is the same defect as one that passes without looking.
$gateSourcePath = ''
$gateCandidates = @()
if ($env:SZA_HARNESS_ROOT) { $gateCandidates += (Join-Path $env:SZA_HARNESS_ROOT 'spec_catalog\check-block-note.ps1') }
try { $gateCandidates += (Get-SzaHarnessScript 'spec_catalog/check-block-note.ps1') } catch { }
try {
    . (Join-Path $repoRoot 'scripts/utils/project-paths.ps1')
    $canonRoot = Get-CanonRoot
    if ($canonRoot) { $gateCandidates += (Join-Path $canonRoot 'tools\harness\spec_catalog\check-block-note.ps1') }
} catch { }
foreach ($cand in $gateCandidates) {
    if ($cand -and (Test-Path -LiteralPath $cand)) { $gateSourcePath = $cand; break }
}

if (-not $gateSourcePath) {
    Write-Host "  SKIP S2581 Block* status-note gate (13 cases) - the resolved harness has no check-block-note.ps1" -ForegroundColor DarkGray
    $script:skipped = 13
}
else {

# Every harness script comes from the SAME root the gate was found in, never from a second
# resolution. Mixing them is a live hazard while the change is undeployed: Get-SzaHarnessScript
# would hand back the cache's update.ps1, which does not pass -StatusNote down to the gate at all,
# so the suite would exercise the new checker through the old mutator and report the pairing as
# broken when neither half is.
$harnessSpecDir = Split-Path $gateSourcePath -Parent
$updatePs1 = Join-Path $harnessSpecDir 'update.ps1'
$linksPs1  = Join-Path $harnessSpecDir '_blocker-links.ps1'

$prevProjectRoot = $env:SZA_PROJECT_ROOT
$env:SZA_PROJECT_ROOT = $sandboxRoot
try {

    function Invoke-Update([string[]]$ArgList) {
        # Each argument is embedded as a single-quoted PowerShell literal inside one -Command
        # string. Neither of the two obvious forms works here:
        #   * `pwsh -File` hands every argument to the binder as a string, so a [bool] parameter
        #     fails to bind outright and a comma list arrives as ONE id - both produce an exit code
        #     that looks like a verdict and is really a bind error,
        #   * `-Command "& $script @args"` with the list splatted onto pwsh re-splits every value on
        #     spaces, so the note 'waiting on an upstream release' reached update.ps1 as five
        #     positional arguments and the run failed on 'on' rather than on anything under test.
        # Quoting here keeps a multi-word note one value, which is the shape every real caller uses.
        # Parameter NAMES are passed bare: quoting '-Id' turns it into a positional value, and
        # update.ps1 sets PositionalBinding = $false, so the run dies on the parameter rather than
        # on the case. None of the values below start with a hyphen.
        $quoted = ($ArgList | ForEach-Object {
            if ($_ -match '^-[A-Za-z]') { $_ } else { "'" + ($_ -replace "'", "''") + "'" }
        }) -join ' '
        $out = & $pwshExe -NoProfile -Command "& '$updatePs1' $quoted" 2>&1
        return [pscustomobject]@{ Code = $LASTEXITCODE; Out = ($out | Out-String) }
    }

    Write-Host 'Note required on entry into every Block* status'
    Reset-Sandbox
    $r = Invoke-Update @('-Id', 'S9001', '-Status', 'BlockExternal')
    Assert-That 'BlockExternal with no -StatusNote is refused' ($r.Code -eq 1) "exit $($r.Code)"
    Assert-That 'the refusal names the gate' ($r.Out -match 'check-block-note\.ps1') $r.Out

    $r = Invoke-Update @('-Id', 'S9001', '-Status', 'BlockQuestions', '-StatusNote', '   ')
    Assert-That 'a whitespace-only note is not a note' ($r.Code -eq 1) "exit $($r.Code)"

    Reset-Sandbox
    $r = Invoke-Update @('-Id', 'S9001', '-Status', 'BlockExternal', '-StatusNote', 'waiting on an upstream release')
    Assert-That 'BlockExternal with a reason is accepted' ($r.Code -eq 0) $r.Out

    Write-Host 'BlockByOtherTask needs a DIRECTIONAL blocker, not just a note'
    Reset-Sandbox
    $r = Invoke-Update @('-Id', 'S9003', '-Status', 'BlockByOtherTask', '-StatusNote', 'blocked by the other one')
    Assert-That 'prose naming no token is refused' ($r.Code -eq 1) "exit $($r.Code)"
    Assert-That 'the refusal names both accepted channels' (($r.Out -match 'Blocker: Sxxxx') -and ($r.Out -match 'Depends on')) $r.Out

    Reset-Sandbox
    $r = Invoke-Update @('-Id', 'S9002', '-Status', 'BlockByOtherTask', '-StatusNote', 'parked')
    Assert-That 'a Blocker: token in section 10 is accepted' ($r.Code -eq 0) $r.Out

    Reset-Sandbox
    $r = Invoke-Update @('-Id', 'S9004', '-Status', 'BlockByOtherTask', '-StatusNote', 'parked')
    Assert-That 'a **Depends on:** line is accepted' ($r.Code -eq 0) $r.Out

    Reset-Sandbox
    $r = Invoke-Update @('-Id', 'S9003', '-Status', 'BlockByOtherTask', '-StatusNote', 'Blocker: S9001 - needs its decode path')
    Assert-That 'a Blocker: token in the note itself is accepted' ($r.Code -eq 0) $r.Out

    Write-Host 'The escape hatch survives (S2581 section 3.2)'
    Reset-Sandbox
    $r = Invoke-Update @('-Id', 'S9005', '-Status', 'BlockQuestions', '-StatusNote', 'needs a placement decision')
    Assert-That 'a duplicate heading does not refuse a BlockQuestions park' ($r.Code -eq 0) $r.Out

    Write-Host 'BlockNeedUserTest gains the note gate, it does not lose its own'
    Reset-Sandbox
    $r = Invoke-Update @('-Id', 'S9001', '-Status', 'BlockNeedUserTest', '-StatusNote', 'observe playback on device')
    Assert-That 'the probe gate still runs for BlockNeedUserTest' (($r.Code -ne 0) -and ($r.Out -match 'check-probe-present\.ps1')) $r.Out

    Write-Host 'Exit-code contract: could not look is not found nothing'
    $out = & $pwshExe -NoProfile -File $gateSourcePath -Id 'nope' -NewStatus 'BlockExternal' 2>&1
    Assert-That 'a malformed id exits 2' ($LASTEXITCODE -eq 2) ($out | Out-String)
    $out = & $pwshExe -NoProfile -File $gateSourcePath -Id 'S9999' -NewStatus 'BlockExternal' 2>&1
    Assert-That 'an id naming no record exits 2' ($LASTEXITCODE -eq 2) ($out | Out-String)

    Write-Host 'One resolution, one answer (S1621)'
    # The gate and the picker must agree on what counts as a blocker. Asserted directly by running
    # the shared resolver over the LIVE tree: a suite that only exercised fixtures could pass while
    # the two disagreed in production, which is the divergence the leaf exists to prevent.
    . $linksPs1
    $liveRows = @(Get-Content (Join-Path $repoRoot 'PLAN/spec-catalog.jsonl') | ForEach-Object { $_ | ConvertFrom-Json })
    $disagree = 0
    foreach ($row in ($liveRows | Where-Object { $_.status -eq 'BlockByOtherTask' })) {
        $specPath = Join-Path $repoRoot ($row.file -replace '/', '\')
        if (-not (Test-Path -LiteralPath $specPath)) { continue }
        $note = if ($row.PSObject.Properties.Name -contains 'statusNote') { [string]$row.statusNote } else { '' }
        $ids = @(Get-BlockerLinks -SpecText ([System.IO.File]::ReadAllText($specPath)) -StatusNote $note -SelfId $row.id)
        # A live BlockByOtherTask resolving no blocker is exactly what the gate refuses and what the
        # picker skips - the two must classify it identically, whichever way it falls.
        if ($ids.Count -eq 0) { $disagree++ }
    }
    Assert-That 'every live BlockByOtherTask carries a resolvable blocker' ($disagree -eq 0) "$disagree record(s) resolve none"

} finally {
    if ($null -eq $prevProjectRoot) { Remove-Item Env:SZA_PROJECT_ROOT -ErrorAction SilentlyContinue }
    else { $env:SZA_PROJECT_ROOT = $prevProjectRoot }
}

}

Write-Host ''
# The skip count rides in the summary line rather than only in the SKIP notice above it: the gate
# that runs this suite reports the last line, and "13 passed" and "0 passed, 13 skipped" must not
# read the same.
$skipNote = if ($script:skipped -gt 0) { ", $script:skipped case(s) skipped" } else { '' }
Write-Host ("check-block-note: {0} passed, {1} failed{2}" -f $script:pass, $script:fail, $skipNote)
if ($script:fail -gt 0) { exit 1 }
exit 0
