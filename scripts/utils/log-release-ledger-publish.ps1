#requires -Version 7.0
<#
.SYNOPSIS
    Record a release channel step in the release ledger (RELEASE-LEDGER 0.1). Never fails the caller.

.DESCRIPTION
    S4679. Called by the release publishers after a channel step succeeded: appends one publication
    event through the canon writer `releases/release-ledger publish` and reports, never fails, on
    any refusal - a publication that already reached the channel must not be aborted by its own
    recording (canon RELEASE_AND_DISTRIBUTION section 10). The writer is resolved exactly like the
    build wrapper resolves it: $env:SZA_HARNESS_ROOT, the plugin cache's newest version that ships
    the script, then the canon checkout.

    Exit codes: 0 always - a ledger write never fails the release; every failure is reported.
#>
param(
    [string]$Key,
    [string]$Version,
    [string]$State,
    [string]$Ref,
    [string]$Url,
    [string]$Note
)

$ErrorActionPreference = 'Continue'

function Stop-RlLog([string]$Message) {
    Write-Warning "release ledger: $Message The publication stands; the ledger event is not written."
    exit 0
}

if ([string]::IsNullOrWhiteSpace($Key)) { Stop-RlLog '-Key is empty.' }
if ([string]::IsNullOrWhiteSpace($Version)) { Stop-RlLog '-Version is empty.' }
if (@('pending', 'submitted', 'live', 'blocked', 'rejected', 'withdrawn', 'n/a') -cnotcontains $State) {
    Stop-RlLog "-State '$State' is not one of pending, submitted, live, blocked, rejected, withdrawn, n/a."
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$env:SZA_PROJECT_ROOT = $repoRoot

$candidates = @()
if ($env:SZA_HARNESS_ROOT) { $candidates += $env:SZA_HARNESS_ROOT }
$rlHome = if ($env:USERPROFILE) { $env:USERPROFILE } elseif ($env:HOME) { $env:HOME } else { $null }
$rlCache = if ($rlHome) { Join-Path $rlHome '.claude/plugins/cache/sza-unified-rules/sza' } else { $null }
if ($rlCache -and (Test-Path -LiteralPath $rlCache -PathType Container)) {
    # Ordered as VERSIONS, not as strings - a string sort puts 2026.1001.1 below 2026.903.1.
    $rlVersions = @(Get-ChildItem -LiteralPath $rlCache -Directory -ErrorAction SilentlyContinue |
        ForEach-Object {
            $rlParsed = $null
            [void][version]::TryParse($_.Name, [ref]$rlParsed)
            [pscustomobject]@{ Path = $_.FullName; Version = $rlParsed }
        } | Sort-Object @{ Expression = { $null -ne $_.Version }; Descending = $true },
                        @{ Expression = { $_.Version }; Descending = $true },
                        @{ Expression = { $_.Path }; Descending = $true })
    $candidates += @($rlVersions | ForEach-Object { Join-Path $_.Path 'tools/harness' })
}
$rlCheckout = $env:SZA_CANON_ROOT
if (-not $rlCheckout) {
    try { . (Join-Path $PSScriptRoot 'project-paths.ps1'); $rlCheckout = Get-CanonRoot } catch { $rlCheckout = $null }
}
if ($rlCheckout) { $candidates += (Join-Path $rlCheckout 'tools/harness') }

$rlWriter = $null
foreach ($rlDir in $candidates) {
    $rlProbe = Join-Path $rlDir 'releases/release-ledger.ps1'
    if (Test-Path -LiteralPath $rlProbe -PathType Leaf) { $rlWriter = $rlProbe; break }
}
if (-not $rlWriter) { Stop-RlLog 'the writer releases/release-ledger.ps1 was not found (SZA_HARNESS_ROOT, plugin cache, canon checkout).' }

# Hashtable splat, not an array: array splatting passes its elements positionally, so the
# '-Key' string would look for a positional slot and the writer would refuse the invocation.
$rlSplat = @{ Verb = 'publish'; Key = $Key; Version = $Version; State = $State }
if (-not [string]::IsNullOrWhiteSpace($Ref)) { $rlSplat.Ref = $Ref }
if (-not [string]::IsNullOrWhiteSpace($Url)) { $rlSplat.Url = $Url }
if (-not [string]::IsNullOrWhiteSpace($Note)) { $rlSplat.Note = $Note }

try {
    & $rlWriter @rlSplat 2>&1 | ForEach-Object { Write-Host $_ }
    if ($LASTEXITCODE -ne 0) {
        Write-Warning "release ledger: the writer exited $LASTEXITCODE. The publication stands; the ledger event is not written."
    }
} catch {
    Write-Warning "release ledger: the write failed ($($_.Exception.Message)). The publication stands; the ledger event is not written."
}
exit 0
