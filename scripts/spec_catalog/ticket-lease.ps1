#requires -Version 7.0
<#
.SYNOPSIS
    Forwarder to the canon-shipped harness script locks\ticket-lease.ps1 (S2402).

.DESCRIPTION
    GENERATED - do not edit. The mechanism lives in the SZA canon plugin (tools/harness) and this
    repository consumes it; the file kept here is only the address every existing call site already
    knows. Regenerate with scripts/utils/install-sza-forwarders.ps1. What this project configures lives in
    .sza-profile.json at the repository root, never in a script body.

Exit codes: whatever locks\ticket-lease.ps1 returns, plus 2 when the harness cannot be located.
#>
# S2441: every name below carries a $szaFwd prefix because HALF of this set is dot-sourced, and a
# dot-sourced file assigns into its CALLER's scope. PowerShell names are case-insensitive, so the
# `$target` this file used to resolve into WAS the caller's `-Target` parameter: post-change.ps1
# dot-sources the agent-lock-domains forwarder before it journals, and every dev/CHANGELOG.md row
# written on 2026-09-03 recorded a harness path where the ticket id belonged. The second failure
# mode is worse than the substitution - a caller declaring `[string]$Candidates` type-constrains
# this file's own accumulator, so `$candidates = @()` collapses to '' and every `+=` concatenates
# instead of appending, leaving one unusable path and a forwarder that cannot find the harness at
# all. Ten scripts under scripts/ declare a parameter that collided. Contract suite:
# scripts/utils/install-sza-forwarders.tests/.
$szaFwdCandidates = @()
if ($env:SZA_HARNESS_ROOT) { $szaFwdCandidates += $env:SZA_HARNESS_ROOT }
$szaFwdCache = Join-Path $env:USERPROFILE '.claude\plugins\cache\sza-unified-rules\sza'
if (Test-Path -LiteralPath $szaFwdCache) {
    # Ordered as VERSIONS, not as strings: the plugin version is date-derived (2026.903.1), so a
    # string sort puts October's 2026.1001.1 below September's 2026.903.1 and the forwarder would
    # keep calling the older copy after an update. A directory that does not parse sorts last
    # rather than being dropped - it may still be the only harness present.
    $szaFwdVersions = @(Get-ChildItem -LiteralPath $szaFwdCache -Directory -ErrorAction SilentlyContinue |
        ForEach-Object {
            $szaFwdParsed = $null
            [void][version]::TryParse($_.Name, [ref]$szaFwdParsed)
            [pscustomobject]@{ Path = $_.FullName; Version = $szaFwdParsed }
        } | Sort-Object @{ Expression = { $null -ne $_.Version }; Descending = $true },
                        @{ Expression = { $_.Version }; Descending = $true },
                        @{ Expression = { $_.Path }; Descending = $true })
    $szaFwdCandidates += @($szaFwdVersions | ForEach-Object { Join-Path $_.Path 'tools\harness' })
}
$szaFwdTarget = $null
foreach ($szaFwdDir in $szaFwdCandidates) {
    $szaFwdProbe = Join-Path $szaFwdDir 'locks\ticket-lease.ps1'
    if (Test-Path -LiteralPath $szaFwdProbe) { $szaFwdTarget = $szaFwdProbe; break }
}

# S2452: candidate 3, the canon checkout, reached only when the two above miss. The plugin cache
# is what actually resolves on every invocation, so the resolver below is never read on the hot
# path. Its default is held by scripts/utils/project-paths.ps1 and by nothing else - before this it
# was written in 76 files, 74 of them generated and stamped `GENERATED - do not edit`, so moving
# the canon required editing files that forbid editing. That is the exact non-portability the
# hardcoded-drive-path rule was installed to refuse (S2326).
#
# The dot-source runs inside `& { }` deliberately. HALF this set is itself dot-sourced, so at top
# level project-paths.ps1 would define its functions and set its script variables in the CALLER's
# scope - the S2441 failure one level further out. A child scope cannot reach the caller at all.
if (-not $szaFwdTarget) {
    $szaFwdCheckout = $env:SZA_CANON_ROOT
    if (-not $szaFwdCheckout) {
        $szaFwdResolver = Join-Path $PSScriptRoot '..\..\scripts\utils\project-paths.ps1'
        if (Test-Path -LiteralPath $szaFwdResolver) {
            # A resolver that is absent or throws must not stop the forwarder from printing its own
            # refusal, which is the only message that names all three candidates and the fix.
            $szaFwdCheckout = & {
                param($szaFwdResolverPath)
                try { . $szaFwdResolverPath; Get-CanonRoot } catch { $null }
            } $szaFwdResolver
        }
    }
    if ($szaFwdCheckout) {
        $szaFwdCandidates += (Join-Path $szaFwdCheckout 'tools\harness')
        $szaFwdProbe = Join-Path $szaFwdCandidates[-1] 'locks\ticket-lease.ps1'
        if (Test-Path -LiteralPath $szaFwdProbe) { $szaFwdTarget = $szaFwdProbe }
    }
}
if (-not $szaFwdTarget) {
    Write-Host "ticket-lease.ps1: the SZA harness is not installed - looked in:" -ForegroundColor Red
    foreach ($szaFwdDir in $szaFwdCandidates) { Write-Host "    $szaFwdDir" -ForegroundColor Gray }
    Write-Host "  Install or update it:  claude plugin update sza@sza-unified-rules" -ForegroundColor Yellow
    Write-Host "  Or point at a checkout: `$env:SZA_HARNESS_ROOT = '<repo>\tools\harness'" -ForegroundColor Yellow
    exit 2
}

$env:SZA_PROJECT_ROOT = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path

# Half of this set is dot-sourced as a library and half is invoked as a CLI, and the two cannot be
# forwarded the same way: `& $szaFwdTarget` would run a library in its own scope and define nothing
# the caller can see, while `exit` inside a dot-sourced file would kill the caller. InvocationName
# is '.' exactly when this file was dot-sourced, so one template serves both.
if ($MyInvocation.InvocationName -eq '.') {
    . $szaFwdTarget
} else {
    # Deliberately NOT 'Stop'. A native child's stderr arrives here as ErrorRecord objects, and
    # under 'Stop' the first one terminates this forwarder before `exit $LASTEXITCODE` runs - so a
    # script that reported a FAIL and exited 1 would reach its caller as a crashed forwarder with a
    # different code. Every script in this set states its exit codes; passing them through unchanged
    # is the whole job. S2441 moved it inside this branch: at the file's top level it also rewrote
    # the preference of every caller that dot-sources a forwarder, silently downgrading a script
    # running under 'Stop' for the rest of its life.
    $ErrorActionPreference = 'Continue'
    $global:LASTEXITCODE = 0
    try {
        & $szaFwdTarget @args
    } catch {
        # A child that THROWS never reaches its own `exit`, so $LASTEXITCODE stays 0 and the
        # forwarder would report success for a script that failed - the one way a forwarder can
        # turn a red verdict green. Every such refusal is a failure, so it leaves as exit 1.
        Write-Error $_ -ErrorAction Continue
        exit 1
    }
    $szaFwdCode = $LASTEXITCODE
    exit $(if ($null -eq $szaFwdCode) { 0 } else { $szaFwdCode })
}