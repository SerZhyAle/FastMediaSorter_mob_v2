#requires -Version 7.0
<#
.SYNOPSIS
    Contract suite for spec-next-preflight.ps1 -Claim: the pick and the ticket lease as one step.

.DESCRIPTION
    Before -Claim the ranker only READ the lease store, so two pickers ranking at the same moment
    both saw the head free, both selected it, and the claim made later by whoever acted on the pick
    decided the race after the loser had already spent its run. The suite pins the closed window:

      - a plain call stays read-only: no lease is written and `selected` carries no `lease`;
      - -ClaimAs without -Claim is a usage error (exit 2);
      - -Claim returns a selection the caller already owns, written under the -ClaimAs identity
        with the given reason;
      - the same identity ranking again gets its own ticket back as `already-mine`;
      - a second identity ranking after it is offered a different ticket, and sees the first one
        among `leased_ids`;
      - three identities ranking AT ONCE come back with three different tickets, each lease owned
        by the identity that selected it - the race the change exists to close;
      - a lease claimed for an identity whose transcript does not exist yet records none, and the
        owner's first lease verb fills it in, so its liveness is not left to the heartbeat alone.

    Hermetic for every write: FMS_TICKET_LEASE_ROOT and FMS_AGENT_CHAT_ROOT point the leases, the
    handoffs and the claim chat lines at a throwaway root. The catalog, the release queue, the skip
    cache and the run journals are the repository's own and are only READ - preflight writes none
    of them, with or without -Claim - so the selected ids are whatever the live queue offers and
    the cases assert relations between them, never particular ids.

.NOTES
    Exit codes:
      0 - every case passed; the sandbox is deleted.
      1 - at least one case failed; the sandbox is kept and its path printed.
      2 - could not verify: the resolved harness predates -Claim, the sandbox could not be prepared,
          or the live queue offered no claimable ticket.
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..' '..')).Path
$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") { "$env:ProgramFiles\PowerShell\7\pwsh.exe" } else { 'pwsh' }
$preflight = Join-Path $repoRoot 'scripts/spec_catalog/spec-next-preflight.ps1'

$script:pass = 0
$script:fail = 0
function Assert-That([string]$Name, [bool]$Condition, [string]$Detail = '') {
    if ($Condition) { $script:pass++; Write-Host "  PASS  $Name" -ForegroundColor Green }
    else { $script:fail++; Write-Host "  FAIL  $Name`n        $Detail" -ForegroundColor Red }
}

function Get-JsonPayload([string]$Text) {
    foreach ($line in ($Text -split "`r?`n")) {
        $trimmed = $line.Trim()
        if ($trimmed.StartsWith('{')) {
            try { return ($trimmed | ConvertFrom-Json) } catch { continue }
        }
    }
    return $null
}

function Invoke-Preflight([string[]]$Arguments) {
    $out = & $pwshExe -NoProfile -File $preflight -NoDrift @Arguments 2>&1 | ForEach-Object { "$_" }
    $text = ($out -join "`n")
    return [pscustomobject]@{ Exit = $LASTEXITCODE; Text = $text; Payload = (Get-JsonPayload $text) }
}

function Get-LeaseFile([string]$TicketId) {
    $path = Join-Path $sandbox "temp/SPEC-TICKET.LEASES/$TicketId.json"
    if (-not (Test-Path -LiteralPath $path)) { return $null }
    return (Get-Content -LiteralPath $path -Raw | ConvertFrom-Json)
}

function Get-SelectedLease($Payload) {
    if ($null -eq $Payload -or $null -eq $Payload.selected) { return $null }
    if ($Payload.selected.PSObject.Properties.Name -notcontains 'lease') { return $null }
    return $Payload.selected.lease
}

# The suite exercises a harness change, so it asks the harness the forwarder will actually run.
try {
    . (Join-Path $repoRoot 'scripts/utils/agent-lock-domains.ps1')
    $harnessPreflight = Get-SzaHarnessScript 'spec_catalog/spec-next-preflight.ps1'
    $harnessLease = Get-SzaHarnessScript 'locks/ticket-lease.ps1'
} catch {
    Write-Host "spec-next-preflight tests: the harness could not be resolved - $_" -ForegroundColor Red
    exit 2
}
$hasClaim = (Test-Path -LiteralPath $harnessPreflight) -and
    (Select-String -LiteralPath $harnessPreflight -Pattern '\[string\]\$ClaimAs' -Quiet)
if (-not $hasClaim) {
    Write-Host '  SKIP pick-and-claim (whole suite) - the resolved harness predates spec-next-preflight.ps1 -Claim.' -ForegroundColor DarkGray
    Write-Host '        Run with SZA_HARNESS_ROOT pointed at the canon checkout, or update the plugin: claude plugin update sza@sza-unified-rules' -ForegroundColor DarkGray
    exit 2
}

$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$sandbox = Join-Path $repoRoot "temp/scratch/spec-next-preflight-tests/$stamp-$PID"
$saved = @{
    FMS_TICKET_LEASE_ROOT = $env:FMS_TICKET_LEASE_ROOT
    FMS_AGENT_CHAT_ROOT   = $env:FMS_AGENT_CHAT_ROOT
    FMS_AGENT_ID          = $env:FMS_AGENT_ID
}
try {
    New-Item -ItemType Directory -Path (Join-Path $sandbox 'temp') -Force | Out-Null
    $env:FMS_TICKET_LEASE_ROOT = $sandbox
    $env:FMS_AGENT_CHAT_ROOT = Join-Path $sandbox 'AGENT-CHAT'
    Remove-Item Env:FMS_AGENT_ID -ErrorAction SilentlyContinue
} catch {
    Write-Host "spec-next-preflight tests: sandbox could not be prepared - $_" -ForegroundColor Red
    exit 2
}

$couldNotVerify = $false
try {
    Write-Host 'Read-only by default'
    $plain = Invoke-Preflight @()
    $leaseDir = Join-Path $sandbox 'temp/SPEC-TICKET.LEASES'
    $leaseCount = @(Get-ChildItem -LiteralPath $leaseDir -Filter '*.json' -ErrorAction SilentlyContinue).Count
    Assert-That 'a plain call exits 0 with a JSON payload' ($plain.Exit -eq 0 -and $null -ne $plain.Payload) $plain.Text
    Assert-That 'a plain call writes no lease and attaches none' `
        ($leaseCount -eq 0 -and $null -eq (Get-SelectedLease $plain.Payload) -and -not $plain.Payload.claimed) `
        "expected: 0 lease files, no selected.lease, claimed=false | actual: $leaseCount file(s), claimed=$($plain.Payload.claimed)"

    Write-Host 'Usage'
    $orphan = Invoke-Preflight @('-ClaimAs', ([guid]::NewGuid().ToString()))
    Assert-That '-ClaimAs without -Claim is a usage error' ($orphan.Exit -eq 2) "expected: exit 2 | actual: exit $($orphan.Exit) | $($orphan.Text)"

    Write-Host 'Claim, re-claim, and a second picker'
    $idA = [guid]::NewGuid().ToString()
    $first = Invoke-Preflight @('-Claim', '-ClaimAs', $idA, '-Reason', 'preflight-tests-a')
    $leaseA = Get-SelectedLease $first.Payload
    if ($null -eq $first.Payload -or $null -eq $first.Payload.selected) {
        Write-Host "  SKIP the live queue offered no claimable ticket ($($first.Payload.selected_none_reason)) - nothing to claim." -ForegroundColor DarkGray
        $couldNotVerify = $true
    } else {
        $pickA = [string]$first.Payload.selected.id
        $fileA = Get-LeaseFile $pickA
        Assert-That 'the selection comes back already leased' ($null -ne $leaseA -and $leaseA.outcome -eq 'claimed') `
            "expected: lease.outcome claimed | actual: $(if ($leaseA) { $leaseA | ConvertTo-Json -Compress } else { 'no lease' })"
        Assert-That 'the lease is written under the -ClaimAs identity with the given reason' `
            ($null -ne $fileA -and [string]$fileA.sessionId -eq $idA -and [string]$fileA.reason -eq 'preflight-tests-a') `
            "expected: owner $idA, reason preflight-tests-a | actual: $(if ($fileA) { "$($fileA.sessionId) / $($fileA.reason)" } else { 'no lease file' })"
        Assert-That 'the claim reports a handoff path that exists' `
            ($null -ne $leaseA -and $leaseA.handoff_path -and (Test-Path -LiteralPath ([string]$leaseA.handoff_path))) `
            "actual: $(if ($leaseA) { $leaseA.handoff_path } else { 'no lease' })"
        Assert-That 'the lease of an identity with no transcript yet records none' `
            ($null -ne $fileA -and [string]::IsNullOrWhiteSpace([string]$fileA.transcriptPath)) `
            "actual transcriptPath: $(if ($fileA) { $fileA.transcriptPath } else { 'no lease file' })"

        $again = Invoke-Preflight @('-Claim', '-ClaimAs', $idA, '-Reason', 'preflight-tests-a')
        $againLease = Get-SelectedLease $again.Payload
        Assert-That 'the same identity gets its own ticket back as already-mine' `
            ($null -ne $again.Payload.selected -and [string]$again.Payload.selected.id -eq $pickA -and $againLease.outcome -eq 'already-mine') `
            "expected: $pickA / already-mine | actual: $(if ($again.Payload.selected) { "$($again.Payload.selected.id) / $($againLease.outcome)" } else { 'none' })"

        $idB = [guid]::NewGuid().ToString()
        $second = Invoke-Preflight @('-Claim', '-ClaimAs', $idB, '-Reason', 'preflight-tests-b')
        if ($null -eq $second.Payload.selected) {
            Write-Host "  SKIP second picker - the live queue holds only one claimable ticket ($($second.Payload.selected_none_reason))." -ForegroundColor DarkGray
        } else {
            $pickB = [string]$second.Payload.selected.id
            $leasedSeen = @($second.Payload.leased_ids | ForEach-Object { [string]$_.id })
            Assert-That 'a second identity is offered a different ticket' ($pickB -ne $pickA) "expected: not $pickA | actual: $pickB"
            Assert-That "the first identity's ticket is reported as leased to the second" ($leasedSeen -contains $pickA) `
                "expected leased_ids to contain $pickA | actual: $($leasedSeen -join ',')"
        }

        Write-Host 'The owner fills in its transcript on its first verb'
        # The transcript lookup reads USERPROFILE\.claude\projects, so the owner's verb runs against a
        # sandbox home holding a transcript named after the identity. The harness file is invoked
        # directly: the forwarder would resolve the plugin cache under the same, now fake, home.
        $fakeHome = Join-Path $sandbox 'home'
        $fakeTranscript = Join-Path $fakeHome ".claude/projects/sandbox-project/$idA.jsonl"
        New-Item -ItemType Directory -Path (Split-Path $fakeTranscript) -Force | Out-Null
        Set-Content -LiteralPath $fakeTranscript -Value '{}' -Encoding utf8NoBOM
        $savedHome = $env:USERPROFILE
        $savedRoot = $env:SZA_PROJECT_ROOT
        try {
            $env:USERPROFILE = $fakeHome
            $env:SZA_PROJECT_ROOT = $repoRoot
            $env:FMS_AGENT_ID = $idA
            $null = & $pwshExe -NoProfile -File $harnessLease -Verb Status -Json 2>&1
        } finally {
            $env:USERPROFILE = $savedHome
            $env:SZA_PROJECT_ROOT = $savedRoot
            Remove-Item Env:FMS_AGENT_ID -ErrorAction SilentlyContinue
        }
        $filled = Get-LeaseFile $pickA
        Assert-That "the owner's first lease verb records its transcript" `
            ($null -ne $filled -and [string]$filled.transcriptPath -eq (Get-Item -LiteralPath $fakeTranscript).FullName) `
            "expected: $fakeTranscript | actual: $(if ($filled) { $filled.transcriptPath } else { 'no lease file' })"

        Write-Host 'Three pickers at once'
        $raceDir = Join-Path $sandbox 'race'
        New-Item -ItemType Directory -Path $raceDir -Force | Out-Null
        $runs = foreach ($n in 1..3) {
            $who = [guid]::NewGuid().ToString()
            $outFile = Join-Path $raceDir "out-$n.json"
            $proc = Start-Process -FilePath $pwshExe -NoNewWindow -PassThru -RedirectStandardOutput $outFile `
                -ArgumentList @('-NoProfile', '-File', $preflight, '-NoDrift', '-Claim', '-ClaimAs', $who, '-Reason', "preflight-tests-race-$n")
            [pscustomobject]@{ Who = $who; Out = $outFile; Proc = $proc }
        }
        $runs | ForEach-Object { $_.Proc } | Wait-Process -Timeout 300
        $picks = @()
        $owned = $true
        $ownedDetail = @()
        foreach ($run in $runs) {
            $payload = Get-JsonPayload ((Get-Content -LiteralPath $run.Out -Raw -ErrorAction SilentlyContinue) + '')
            if ($null -eq $payload -or $null -eq $payload.selected) { continue }
            $id = [string]$payload.selected.id
            $picks += $id
            $file = Get-LeaseFile $id
            if ($null -eq $file -or [string]$file.sessionId -ne $run.Who) {
                $owned = $false
                $ownedDetail += "$id owned by $(if ($file) { $file.sessionId } else { 'nobody' }), selected by $($run.Who)"
            }
        }
        if ($picks.Count -lt 2) {
            Write-Host "  SKIP the race - the live queue offered $($picks.Count) claimable ticket(s) beside the two already held." -ForegroundColor DarkGray
        } else {
            $distinct = @($picks | Select-Object -Unique)
            Assert-That 'pickers ranking at once come back with different tickets' ($distinct.Count -eq $picks.Count) `
                "expected: $($picks.Count) distinct | actual: $($picks -join ',')"
            Assert-That 'each selected ticket is leased to the picker that selected it' $owned ($ownedDetail -join '; ')
            Assert-That 'no picker was handed a ticket held before the race' (-not ($picks -contains $pickA)) `
                "expected: none of $($picks -join ',') to be $pickA"
        }
    }
}
finally {
    foreach ($k in $saved.Keys) {
        if ($null -eq $saved[$k]) { Remove-Item "Env:$k" -ErrorAction SilentlyContinue }
        else { Set-Item "Env:$k" $saved[$k] }
    }
}

Write-Host ''
if ($script:fail -gt 0) {
    Write-Host ("spec-next-preflight tests: FAIL ({0} failed, {1} passed) - sandbox kept at {2}" -f $script:fail, $script:pass, $sandbox) -ForegroundColor Red
    exit 1
}
Remove-Item -LiteralPath $sandbox -Recurse -Force -ErrorAction SilentlyContinue
if ($couldNotVerify) {
    Write-Host ("spec-next-preflight tests: could not verify the claim ({0} passed)" -f $script:pass) -ForegroundColor Yellow
    exit 2
}
Write-Host ("spec-next-preflight tests: PASS ({0} passed, 0 failed)" -f $script:pass) -ForegroundColor Green
exit 0
