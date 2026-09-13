#requires -Version 7.0
<#
.SYNOPSIS
    S2635: every script that writes a Code.* path takes that domain, and no new writer slips in.

.DESCRIPTION
    S2615 built the mechanism (scripts/utils/code-lock-scope.ps1) and adopted it in eleven
    render-target generators. Its one unresolved research item was carried here: what stops the
    NEXT writer from bypassing the domain again. Nothing did - the convention was written in
    prune-detekt-baseline.ps1's own header, checked by nothing, and obeyed by no caller.

    Two dimensions, because either alone leaves a hole.

    1. ADOPTION, over the registry `code-domain-writers.manifest.txt`. Every script named there
       must dot-source the helper and call it. This is exact, not heuristic: the manifest names
       the file, so the check cannot be fooled by how the path is built. A manifest entry whose
       file no longer exists also fails - a renamed writer loses its adoption exactly as quietly
       as one that forgot it, and a registry that silently tolerates dead rows stops being a
       registry.

    2. NEW WRITERS, over every .ps1 under scripts/. A script that writes and is not in the
       manifest is counted, and the count is compared to a ratchet baseline rather than to zero.
       Zero is not reachable and pretending otherwise would make the gate permanently red: this
       dimension can only recognise a write whose target appears as a STRING LITERAL in the same
       file, and most real writers build the path from variables. So it is a net for the obvious
       case, and the manifest above is the mechanism. The baseline records the writers this
       heuristic sees today that are deliberately not in the registry; a new one raises the count
       and fails.

    Class A vs class B is the S2635 distinction and is checked too, because the classes are not
    interchangeable. Class A writes from inside the concurrent `.\a.ps1 fg` battery on a branch
    whose verdict is already computed, so it must be able to SKIP a busy domain
    (Enter-CodeLockOrSkip) rather than exit 4 and paint a passing gate red. Class B is hand-run in
    the foreground, where skipping would be work left undone, so it must exit 4
    (Enter-CodeLockOrExit).

.NOTES
    Exit codes (CLAUDE.md Rule 7):
      0  every manifest entry adopts the helper, and the unregistered-writer count is at or below
         its baseline.
      1  -Gate and at least one entry lost its adoption, or the unregistered-writer count grew.
      2  cannot verify - the manifest or the helper is missing, so there is nothing to check
         against.
      4  -UpdateBaseline only: Code.Scripts is held by another session, so the baseline was not
         written. The queue place is held - wait for the turn in the background and rerun.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-code-domain-writers.ps1 -Gate
    pwsh -NoProfile -File scripts/quality/assert-code-domain-writers.ps1 -UpdateBaseline
#>
[CmdletBinding()]
param(
    [switch]$Gate,
    [switch]$UpdateBaseline,
    [switch]$Quiet
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
. (Join-Path $PSScriptRoot '../utils/code-lock-scope.ps1')

$manifestPath = Join-Path $PSScriptRoot 'code-domain-writers.manifest.txt'
$baselineFile = Join-Path $PSScriptRoot 'code-domain-writers-baseline.txt'
$helperRel = 'scripts/utils/code-lock-scope.ps1'

if (-not (Test-Path -LiteralPath $manifestPath)) {
    Write-Error "assert-code-domain-writers: cannot verify - the registry $manifestPath is missing." -ErrorAction Continue
    exit 2
}
if (-not (Test-Path -LiteralPath (Join-Path $repoRoot $helperRel))) {
    Write-Error "assert-code-domain-writers: cannot verify - the helper $helperRel is missing, so no script could adopt it." -ErrorAction Continue
    exit 2
}

Write-Host "assert-code-domain-writers: registry $((Resolve-Path -LiteralPath $manifestPath -Relative) -replace '\\', '/'), helper $helperRel." -ForegroundColor DarkGray

# --- dimension 1: every registered writer still adopts the helper --------------------------
$entries = [System.Collections.Generic.List[object]]::new()
foreach ($line in (Get-Content -LiteralPath $manifestPath)) {
    $trimmed = $line.Trim()
    if (-not $trimmed -or $trimmed.StartsWith('#')) { continue }
    $parts = $trimmed -split '\|'
    if ($parts.Count -lt 2) { continue }
    $entries.Add([pscustomobject]@{
        Path  = $parts[0].Trim()
        Class = $parts[1].Trim().ToUpperInvariant()
    })
}

$failures = [System.Collections.Generic.List[string]]::new()

foreach ($entry in $entries) {
    $full = Join-Path $repoRoot $entry.Path
    if (-not (Test-Path -LiteralPath $full)) {
        $failures.Add("$($entry.Path): named in the registry but not on disk - renamed or deleted without updating the registry.")
        continue
    }
    $text = Get-Content -LiteralPath $full -Raw

    if ($text -notmatch [regex]::Escape('code-lock-scope.ps1')) {
        $failures.Add("$($entry.Path): does not dot-source $helperRel - it writes a Code.* path with no domain taken.")
        continue
    }

    $usesExit = $text -match 'Enter-CodeLockOrExit'
    $usesSkip = $text -match 'Enter-CodeLockOrSkip'
    if (-not ($usesExit -or $usesSkip)) {
        $failures.Add("$($entry.Path): dot-sources the helper but never calls it - the import alone takes no domain.")
        continue
    }
    # A class A writer that can only exit 4 would turn a passing gate red inside the fg battery;
    # that is the whole reason the two classes are distinguished, so a missing skip path is a
    # defect and not a style choice.
    if ($entry.Class -eq 'A' -and -not $usesSkip) {
        $failures.Add("$($entry.Path): registered class A but never calls Enter-CodeLockOrSkip - a busy domain would fail a gate whose verdict already passed.")
    }
    if ($entry.Class -eq 'B' -and -not $usesExit) {
        $failures.Add("$($entry.Path): registered class B but never calls Enter-CodeLockOrExit - a busy domain would silently skip work the caller asked for.")
    }
}

if (-not $Quiet) {
    Write-Host ("  adoption: {0} registered writer(s) checked, {1} problem(s)." -f $entries.Count, $failures.Count)
}
foreach ($f in $failures) { Write-Host "  FAIL $f" -ForegroundColor Red }

# --- dimension 2: a writer that is not registered at all -----------------------------------
# Deliberately narrow. It recognises only a write whose target is a string literal naming a
# Code.* prefix in the same file, which is the shape a newly written generator most often has
# before it grows a path variable. Everything else is the manifest's job.
$writeCmdlet = 'Set-Content|Out-File|Add-Content|\[System\.IO\.File\]::Write|\[IO\.File\]::Write|Remove-Item'
$codePrefix = "['`"](docs/|scripts/|app_v2/|wear/|config/|fastlane/|maestro/|delivery/|play/|store_assets/)"

$registered = @{}
foreach ($entry in $entries) { $registered[$entry.Path.ToLowerInvariant()] = $true }

$unregistered = [System.Collections.Generic.List[string]]::new()
foreach ($file in (Get-ChildItem -LiteralPath (Join-Path $repoRoot 'scripts') -Recurse -Filter '*.ps1' -File)) {
    $rel = ($file.FullName.Substring($repoRoot.Length + 1) -replace '\\', '/')
    if ($registered.ContainsKey($rel.ToLowerInvariant())) { continue }
    # The helper and its own suite write nothing of their own; a test harness writing into temp/
    # is not a writer of a Code.* path either.
    if ($rel -match '\.tests/' -or $rel -eq $helperRel) { continue }

    # S3083: the write and the Code.* literal must share a line. Matching them anywhere in the file
    # counted a script that writes only temp/ output but dot-sources a scripts/ helper, and the
    # ratchet then went red on whichever such script was touched next.
    $lines = Get-Content -LiteralPath $file.FullName
    $hit = $false
    foreach ($line in $lines) {
        if ($line -match $writeCmdlet -and $line -match $codePrefix) { $hit = $true; break }
    }
    if (-not $hit) { continue }
    $unregistered.Add($rel)
}

$current = $unregistered.Count

if (-not (Test-Path -LiteralPath $baselineFile)) {
    Write-Host ("  new writers: NO BASELINE yet | actual {0} - run -UpdateBaseline to seed." -f $current)
    if ($UpdateBaseline) {
        $scope = $null
        try {
            $scope = Enter-CodeLockOrExit -Path $baselineFile -Reason 'assert-code-domain-writers.ps1 -UpdateBaseline'
            Set-Content -LiteralPath $baselineFile -Value "$current"
        }
        finally { Exit-CodeLockScope -Scope $scope }
    }
    if ($failures.Count -gt 0 -and $Gate) {
        Write-Error "assert-code-domain-writers: FAIL - $($failures.Count) registered writer(s) no longer take their code domain (listed above). Wrap each write with $helperRel, or drop the row if the script stopped writing (S2635)." -ErrorAction Continue
        exit 1
    }
    exit 0
}

$baseline = [int]((Get-Content -LiteralPath $baselineFile -Raw).Trim())

if ($UpdateBaseline) {
    if ($current -gt $baseline) {
        Write-Host ("  refusing to RAISE the baseline ({0} -> {1}). Register the new writer instead." -f $baseline, $current) -ForegroundColor Red
        exit 1
    }
    if ($current -lt $baseline) {
        $scope = $null
        try {
            $scope = Enter-CodeLockOrExit -Path $baselineFile -Reason 'assert-code-domain-writers.ps1 -UpdateBaseline'
            Set-Content -LiteralPath $baselineFile -Value "$current"
        }
        finally { Exit-CodeLockScope -Scope $scope }
        Write-Host ("  baseline ratcheted DOWN: {0} -> {1}" -f $baseline, $current)
    }
    else { Write-Host ("  baseline unchanged ({0})" -f $baseline) }
    if ($failures.Count -gt 0 -and $Gate) {
        Write-Error "assert-code-domain-writers: FAIL - $($failures.Count) registered writer(s) no longer take their code domain (listed above). Wrap each write with $helperRel, or drop the row if the script stopped writing (S2635)." -ErrorAction Continue
        exit 1
    }
    exit 0
}

if (-not $Quiet) {
    Write-Host ("  new writers: baseline {0} | actual {1} | delta {2}" -f $baseline, $current, ($current - $baseline))
}

$grew = $current -gt $baseline
if ($grew) {
    Write-Host '  FAIL a .ps1 under scripts/ writes a Code.* path and is not in the registry:' -ForegroundColor Red
    foreach ($u in $unregistered) { Write-Host "    $u" -ForegroundColor Red }
    Write-Host "  Add it to code-domain-writers.manifest.txt AND wrap its write with $helperRel (S2635)." -ForegroundColor Red
}

if ($Gate -and ($failures.Count -gt 0 -or $grew)) {
    Write-Error ("assert-code-domain-writers: FAIL - {0} registered writer(s) lost their lock, and the unregistered-writer count is {1} against a baseline of {2}. Register the writer AND wrap its write with {3} (S2635)." -f $failures.Count, $current, $baseline, $helperRel) -ErrorAction Continue
    exit 1
}

Write-Host 'assert-code-domain-writers: PASS' -ForegroundColor Green
exit 0
</content>
