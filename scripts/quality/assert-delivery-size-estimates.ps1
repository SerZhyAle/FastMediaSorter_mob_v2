#requires -Version 7.0
<#
.SYNOPSIS
    Gate: every download size compiled into the app still describes the asset it names (S2652).

.DESCRIPTION
    Three payloads are rebuilt and re-uploaded by the publisher with no build involved: the stream
    catalog archive and the two artwork tile packs. The app carries a byte count for each one so the
    Extensions row can promise a size before the download starts, and nothing ever compared those
    numbers to the files they describe.

    Measured 2026-09-06, at the moment this gate was written:

      stream-catalog.zip        compiled 2 500 000   published 6 994 765   (2.8x understated)
      channel-preview (pack+coords) compiled 10 975 853  published 14 806 317
      stream-logo    (pack+coords)  compiled  5 925 785  published 10 232 740

    The first of those had stood since 2026-07-19. A user on a mobile link consented to 2 MB and
    received 7 MB, which is precisely the decision the size is shown for.

    S2652 made the LIVE size authoritative - the catalog row reads Content-Length off the asset, the
    artwork rows read the published manifest - so these constants are now the offline fallback only.
    That is what this gate guards: a fallback nobody looks at is exactly the number that rots, and
    the offline user is the one who cannot tell.

    RELEASE SCOPE, per CLAUDE.md Rule 33, on all four criteria. A stale estimate reaches a user only
    when a build ships; its subject is the published release rather than any changed file, and no
    session's diff can be blamed for a publish someone else made; every finding names its own asset
    and both numbers; and correcting a batch of them costs one edit whenever it is done.

    OFFLINE IS NOT A DEFECT. Without gh, without a network, or on an HTTP failure this exits 0 with
    a printed advisory. The release-scope runner collapses every non-zero code to FAIL, and failing
    a release because the machine could not reach GitHub would teach exactly one lesson - to stop
    reading this gate's output.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-delivery-size-estimates.ps1

.NOTES
    Exit codes:
      0 - every compiled fallback is within tolerance of its published asset, or the release could
          not be read (advisory printed; being offline is not a finding).
      1 - at least one fallback drifted past the tolerance. The output names the asset, both byte
          counts and the literal to write.
      2 - the check could not happen: the source file is missing, or a constant it must read is not
          in it any more.
#>

[CmdletBinding()]
param(
    [switch]$Quiet,
    # Share of the published size a compiled fallback may differ by before it is a finding. The
    # payloads grow continuously, so a tight bound would report the ordinary week; 25% is the point
    # past which the number stops being a useful promise.
    [double]$TolerancePercent = 25.0
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$inventoryPath = Join-Path $repoRoot 'app_v2/src/main/java/com/sza/fastmediasorter/data/delivery/DeliverableInventoryImpl.kt'
$releaseTag = 'delivery-so-v1'

if (-not (Test-Path $inventoryPath)) {
    Write-Host "assert-delivery-size-estimates: CANNOT VERIFY - not found: $inventoryPath" -ForegroundColor Yellow
    exit 2
}

$source = Get-Content -Path $inventoryPath -Raw

function Read-LongConstant {
    param([string]$Pattern, [string]$Label)
    $match = [regex]::Match($source, $Pattern)
    if (-not $match.Success) { return $null }
    $digits = $match.Groups[1].Value -replace '_', ''
    return [long]$digits
}

# name -> what the app compiled for it. Each entry lists the published assets whose sizes add up to
# the one number the row shows, which is why the artwork entries carry their coords sidecar: the
# download fetches both and the user waits for both.
$expectations = @(
    @{
        label    = 'stream catalog'
        constant = 'STREAM_CATALOG_SIZE'
        compiled = Read-LongConstant 'STREAM_CATALOG_SIZE\s*=\s*([0-9_]+)L'
        assets   = @('stream-catalog.zip')
    },
    @{
        label    = 'channel preview atlas'
        constant = 'FALLBACK_SIZE[CHANNEL_PREVIEW_ATLAS]'
        compiled = Read-LongConstant 'CHANNEL_PREVIEW_ATLAS\s+to\s+([0-9_]+)L'
        assets   = @('channel-preview-tiles.zip', 'channel-preview-coords.json')
    },
    @{
        label    = 'stream logo atlas'
        constant = 'FALLBACK_SIZE[STREAM_LOGO_ATLAS]'
        compiled = Read-LongConstant 'STREAM_LOGO_ATLAS\s+to\s+([0-9_]+)L'
        assets   = @('stream-logo-tiles.zip', 'stream-logo-coords.json')
    }
)

$missing = @($expectations | Where-Object { $null -eq $_.compiled })
if ($missing.Count -gt 0) {
    foreach ($entry in $missing) {
        Write-Host ("assert-delivery-size-estimates: CANNOT VERIFY - {0} not found in {1}" -f `
                $entry.constant, (Split-Path -Leaf $inventoryPath)) -ForegroundColor Yellow
    }
    Write-Host 'A renamed constant is a decision, not a drift - point this gate at the new name.' -ForegroundColor Yellow
    exit 2
}

# Same discovery the publisher uses (StreamPublisher.Artwork.ps1 Get-GhExe): gh is routinely absent
# from PATH on this machine while installed under Program Files.
$ghCommand = Get-Command gh -ErrorAction SilentlyContinue
$ghPath = if ($ghCommand) { $ghCommand.Source } else { $null }
if (-not $ghPath) {
    foreach ($root in @($env:ProgramFiles, ${env:ProgramFiles(x86)}) | Where-Object { $_ }) {
        $candidate = Join-Path $root 'GitHub CLI\gh.exe'
        if (Test-Path $candidate) { $ghPath = $candidate; break }
    }
}
if (-not $ghPath) {
    Write-Host 'assert-delivery-size-estimates: ADVISORY - gh not available, published sizes not read.' -ForegroundColor Yellow
    exit 0
}

$published = $null
try {
    $raw = & $ghPath api "repos/SerZhyAle/FastMediaSorter_mob_v2/releases/tags/$releaseTag" 2>$null
    if ($LASTEXITCODE -eq 0 -and $raw) {
        $published = @{}
        foreach ($asset in ($raw | ConvertFrom-Json).assets) { $published[$asset.name] = [long]$asset.size }
    }
}
catch {
    $published = $null
}

if (-not $published -or $published.Count -eq 0) {
    Write-Host ("assert-delivery-size-estimates: ADVISORY - release {0} could not be read; nothing compared." -f $releaseTag) -ForegroundColor Yellow
    exit 0
}

$findings = @()
foreach ($entry in $expectations) {
    $absent = @($entry.assets | Where-Object { -not $published.ContainsKey($_) })
    if ($absent.Count -gt 0) {
        Write-Host ("assert-delivery-size-estimates: ADVISORY - {0}: release carries no {1}." -f `
                $entry.label, ($absent -join ', ')) -ForegroundColor Yellow
        continue
    }
    $actual = ($entry.assets | ForEach-Object { $published[$_] } | Measure-Object -Sum).Sum
    $driftShare = [math]::Abs($actual - $entry.compiled) / [double]$actual * 100.0
    if (-not $Quiet) {
        Write-Host ("  {0,-22} compiled {1,12:N0}  published {2,12:N0}  drift {3,5:N1}%" -f `
                $entry.label, $entry.compiled, $actual, $driftShare) -ForegroundColor DarkGray
    }
    if ($driftShare -gt $TolerancePercent) {
        $findings += [pscustomobject]@{
            Label    = $entry.label
            Constant = $entry.constant
            Compiled = $entry.compiled
            Actual   = $actual
            Drift    = $driftShare
        }
    }
}

if ($findings.Count -eq 0) {
    Write-Host ("assert-delivery-size-estimates: PASS (every fallback within {0:N0}% of its published asset)." -f $TolerancePercent) -ForegroundColor Green
    exit 0
}

Write-Host ''
foreach ($finding in $findings) {
    Write-Host ("FAIL {0}: {1} is {2:N0}, the published asset is {3:N0} ({4:N1}% off)." -f `
            $finding.Label, $finding.Constant, $finding.Compiled, $finding.Actual, $finding.Drift) -ForegroundColor Red
    Write-Host ("     write {0}L, measured off release {1}." -f $finding.Actual, $releaseTag) -ForegroundColor Red
}
Write-Host ''
Write-Host 'These are the OFFLINE fallbacks: a user with no network sees them and cannot tell they are wrong.' -ForegroundColor Yellow
Write-Host ("Source: {0}" -f $inventoryPath) -ForegroundColor Yellow
exit 1
