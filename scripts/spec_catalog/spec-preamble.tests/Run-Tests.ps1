<#
.SYNOPSIS
    Regression suite for scripts/spec_catalog/spec-preamble.ps1 - the one-process skill preamble (S2400).
.DESCRIPTION
    Pins the contract the ticket-bound skills read once per run:
      A. an id in neither journal -> exit 2, no lease file created.
      B. a sandboxed record with -NoLease -> exit 0, the block names status, file, tactical and
         last-audit state read off disk, and the drift line says why it was skipped for that status.
      C. the same record under -Json -> one parseable object with the same fields.
      D. a Draft record -> drift runs (verdict CLEAN for a fixture id no source carries) and the lease
         is claimed then released, leaving no lease file behind.
      E. -ProductArea prints an indented registry block for a known area, filtered by that area - the
         facet must reach query.ps1 (a splat named `$args` once passed nothing and listed everything).

    Hermetic: the journals live under temp/S2400/preamble-tests/<run>/ through
    $env:FMS_SPEC_CATALOG_DIR; the fixture spec files sit in the real PLAN/ for the seconds the suite
    runs (Resolve-SpecPath joins the repo root, S1534) and are removed in the finally block. Ids
    S9976-S9977 are never live. The lease in case D is a real file under temp/SPEC-TICKET.LEASES/ for
    one call and is released in the same case.

    Exit codes:
      0  all cases pass.
      1  at least one case failed.
      2  the fixtures could not be prepared.
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..' '..')).Path
$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") {
    "$env:ProgramFiles\PowerShell\7\pwsh.exe"
} else { 'pwsh' }
$preamblePs1 = Join-Path $repoRoot 'scripts/spec_catalog/spec-preamble.ps1'
$leasePs1 = Join-Path $repoRoot 'scripts/spec_catalog/ticket-lease.ps1'

$script:pass = 0
$script:fail = 0
function Assert-That([string]$name, [bool]$condition, [string]$detail = '') {
    if ($condition) { $script:pass++; Write-Host "  PASS  $name" -ForegroundColor Green }
    else { $script:fail++; Write-Host "  FAIL  $name`n        $detail" -ForegroundColor Red }
}

function Invoke-Preamble([string[]] $Arguments) {
    $out = & $pwshExe -NoProfile -File $preamblePs1 @Arguments 2>&1 | ForEach-Object { "$_" }
    return [pscustomobject]@{ Exit = $LASTEXITCODE; Text = ($out -join "`n"); Lines = @($out) }
}

$run = Get-Date -Format 'yyyyMMdd-HHmmss'
$sandbox = Join-Path $repoRoot "temp/S2400/preamble-tests/$run"
$planDir = Join-Path $repoRoot 'PLAN'
$fixtureFiles = @()
$fixtureIds = @('S9976', 'S9977')

try {
    New-Item -ItemType Directory -Path $sandbox -Force | Out-Null
    $now = (Get-Date).ToString('yyyy-MM-ddTHH:mm:ss')
    $records = @(
        @{ id = 'S9976'; name = 's2400-fixture-partial'; file = 'PLAN/S9976_s2400-fixture-partial.md'; status = 'Partial'; tier = 3; priority = 10; created = $now; updated = $now },
        @{ id = 'S9977'; name = 's2400-fixture-draft';   file = 'PLAN/S9977_s2400-fixture-draft.md';   status = 'Draft';   tier = 3; priority = 10; created = $now; updated = $now }
    )
    $journal = Join-Path $sandbox 'spec-catalog.jsonl'
    $records | ForEach-Object { $_ | ConvertTo-Json -Compress } | Set-Content -LiteralPath $journal -Encoding utf8
    Set-Content -LiteralPath (Join-Path $sandbox 'spec-catalog-archive.jsonl') -Value '' -Encoding utf8
    foreach ($r in $records) {
        $p = Join-Path $repoRoot $r.file
        if (Test-Path -LiteralPath $p) { throw "fixture path already exists: $p" }
        $body = "# $($r.id) fixture`n`n**Status:** $($r.status)`n`n## Goal`n`nfixture`n"
        if ($r.id -eq 'S9976') { $body += "`n## 6. Last Audit`n`n**Outcome:** fixture`n" }
        Set-Content -LiteralPath $p -Value $body -Encoding utf8
        $fixtureFiles += $p
    }
    $tactical = Join-Path $planDir 'S9976_s2400-fixture-partial'
    New-Item -ItemType Directory -Path $tactical -Force | Out-Null
    Set-Content -LiteralPath (Join-Path $tactical 'PHASE_01__fixture.md') -Value '# Phase 01' -Encoding utf8
    Set-Content -LiteralPath (Join-Path $tactical 'PHASE_02__fixture.md') -Value '# Phase 02' -Encoding utf8
    $fixtureFiles += $tactical
    $env:FMS_SPEC_CATALOG_DIR = $sandbox
    $env:FMS_SKIP_RELEASE_QUEUE = '1'
    # S2372: the real lease claim in case D posts to the agent chat - keep that in the sandbox too.
    $env:FMS_AGENT_CHAT_ROOT = Join-Path $sandbox 'AGENT-CHAT'
} catch {
    Write-Host "spec-preamble tests: fixtures could not be prepared - $_" -ForegroundColor Red
    exit 2
}

try {
    Write-Host 'Case A - unknown id'
    $a = Invoke-Preamble @('-Id', 'S9999', '-NoLease')
    Assert-That 'A exit 2' ($a.Exit -eq 2) $a.Text
    Assert-That 'A names the id' ($a.Text -match 'S9999 is in neither journal') $a.Text
    Assert-That 'A left no lease file' (-not (Test-Path (Join-Path $repoRoot 'temp/SPEC-TICKET.LEASES/S9999.json')))

    Write-Host 'Case B - sandboxed Partial record, -NoLease'
    $b = Invoke-Preamble @('-Id', 'S9976', '-NoLease')
    Assert-That 'B exit 0' ($b.Exit -eq 0) $b.Text
    Assert-That 'B status line' ($b.Text -match 'status:\s+Partial\s+tier: 3\s+priority: 10') $b.Text
    Assert-That 'B file line' ($b.Text -match 'file:\s+PLAN/S9976_s2400-fixture-partial\.md\s*(\r?\n|$)') $b.Text
    Assert-That 'B tactical + numbered last audit' ($b.Text -match 'tactical: present, 2 phase file\(s\)\s+last audit: present') $b.Text
    Assert-That 'B lease not requested' ($b.Text -match 'lease:\s+not requested') $b.Text
    Assert-That 'B drift skipped for Partial' ($b.Text -match 'drift:\s+skipped \(status Partial') $b.Text

    Write-Host 'Case C - -Json'
    $c = Invoke-Preamble @('-Id', 'S9976', '-NoLease', '-Json')
    $obj = $null
    try { $obj = $c.Text | ConvertFrom-Json } catch { $obj = $null }
    Assert-That 'C parseable' ($null -ne $obj) $c.Text
    if ($null -ne $obj) {
        Assert-That 'C fields' ($obj.status -eq 'Partial' -and $obj.last_audit -eq $true -and $obj.phase_files -eq 2 -and $obj.drift -eq 'skipped') $c.Text
        Assert-That 'C lease null under -NoLease' ($null -eq $obj.lease_exit) $c.Text
    }

    Write-Host 'Case D - Draft record: drift runs, lease claimed and released'
    $d = Invoke-Preamble @('-Id', 'S9977', '-Reason', 's2400 preamble test')
    $leaseFile = Join-Path $repoRoot 'temp/SPEC-TICKET.LEASES/S9977.json'
    Assert-That 'D exit 0' ($d.Exit -eq 0) $d.Text
    Assert-That 'D lease claimed' ($d.Text -match 'lease:\s+exit 0 - claimed S9977') $d.Text
    Assert-That 'D lease file exists' (Test-Path -LiteralPath $leaseFile)
    Assert-That 'D drift CLEAN' ($d.Text -match 'drift:\s+CLEAN') $d.Text
    Assert-That 'D last audit absent, no tactical' ($d.Text -match 'tactical: none\s+last audit: absent') $d.Text
    # S2404: claim and release are different processes, so in a runtime without a session id
    # the release must carry the handoff path the preamble just printed - the bare release this
    # case used before exits 4 behind the liveness window exactly when no session id exists.
    $handoffPath = $null
    if ($d.Text -match 'lease handoff:\s*(\S+)') { $handoffPath = $Matches[1] }
    Assert-That 'D printed a lease handoff path' (-not [string]::IsNullOrWhiteSpace($handoffPath)) $d.Text
    $releaseArgs = @('-Verb', 'Release', '-Id', 'S9977')
    if ($handoffPath) { $releaseArgs += @('-Handoff', $handoffPath) }
    & $pwshExe -NoProfile -File $leasePs1 @releaseArgs *> $null
    $releaseExit = $LASTEXITCODE
    Assert-That 'D lease released' ($releaseExit -eq 0 -and -not (Test-Path -LiteralPath $leaseFile))

    Write-Host 'Case E - registry facet'
    $e = Invoke-Preamble @('-Id', 'S9976', '-NoLease', '-ProductArea', 'build')
    Assert-That 'E exit 0' ($e.Exit -eq 0) $e.Text
    Assert-That 'E registry header' ($e.Text -match "registry -ProductArea 'build':") $e.Text
    $indented = @($e.Lines | Where-Object { $_ -match '^    \S' })
    Assert-That 'E indented registry lines' ($indented.Count -gt 0) $e.Text
    Assert-That 'E child ran under its own strict-mode defaults' ($e.Text -notmatch 'cannot be found|Exception') $e.Text
    Assert-That 'E facet reached the query (a build record is listed)' ($e.Text -match '(?m)^\s{4}developer-operations \|') $e.Text
    Assert-That 'E facet filtered (a non-build record is not)' ($e.Text -notmatch 'brand-visual-waves') $e.Text
}
finally {
    Remove-Item Env:FMS_SPEC_CATALOG_DIR -ErrorAction SilentlyContinue
    Remove-Item Env:FMS_SKIP_RELEASE_QUEUE -ErrorAction SilentlyContinue
    Remove-Item Env:FMS_AGENT_CHAT_ROOT -ErrorAction SilentlyContinue
    foreach ($p in $fixtureFiles) {
        if (Test-Path -LiteralPath $p) { Remove-Item -LiteralPath $p -Recurse -Force }
    }
    foreach ($id in $fixtureIds) {
        $lf = Join-Path $repoRoot "temp/SPEC-TICKET.LEASES/$id.json"
        if (Test-Path -LiteralPath $lf) { Remove-Item -LiteralPath $lf -Force }
    }
    if (Test-Path -LiteralPath $sandbox) { Remove-Item -LiteralPath $sandbox -Recurse -Force }
}

Write-Host ("spec-preamble tests: {0} passed, {1} failed" -f $script:pass, $script:fail)
if ($script:fail -gt 0) { exit 1 }
exit 0
