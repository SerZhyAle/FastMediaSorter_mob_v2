<#
.SYNOPSIS
  Enumerates recent device/emulator screenshots under temp/ for the S2108 usability audit.

.DESCRIPTION
  Screenshots accumulate under temp/ from several unrelated capture paths - the spec-test-device
  scenario runner, ad-hoc adb.ps1 shot captures, the wear pre-release walk - and nothing ever cleans
  them up. This script is the single discovery entry point for that corpus: it walks temp/ broadly and
  filters by file age, rather than trusting a fixed list of subfolders, because the sink locations are
  heterogeneous by construction (S2108 research 02).

  Each surviving file is attributed to the ticket that produced it when the path allows: the first
  path segment under temp/ is read as the ticket id when it looks like Sxxxx. A capture taken with no
  active ticket lives under temp/scratch and carries no ticket.

  A ticket's temp/Sxxxx/ can also hold a cloned checkout of a third-party tool (e.g. a Watch Face
  Format validator vendored for reference). That clone's own test fixtures are shipped as .png under
  Android-resource-shaped paths (res/drawable*/, res/mipmap*/) and are never a device/emulator capture,
  so they are excluded by path shape the same way gradle-tmp's Robolectric fixtures are excluded by
  directory name (S2173).

  Exit codes:
    0 - the scan ran to completion, including when it found nothing (an empty corpus is a valid
        answer to this query, not a failure of it)
    1 - a bad argument or a missing temp/ directory prevented the scan from running at all

.PARAMETER MaxAgeDays
  Freshness window in days, counted back from now against each file's LastWriteTime. Default 7; the
  owner's framing allows widening to 14 for a deliberate catch-up run. Older frames are out of scope
  for a routine run because temp/ is not version-controlled and an old frame cannot be trusted to
  still represent the screen it shows.

.PARAMETER Extensions
  Image extensions to consider, without the leading dot. Default 'png' - the only format observed in
  the corpus at measurement time - kept as a parameter because nothing guarantees it stays the only one.

.PARAMETER ExcludeDirs
  Directory names anywhere in the path whose contents are never screenshots. Default 'gradle-tmp',
  which holds Robolectric fixtures written as .png but only a few bytes long; without this they enter
  the corpus as unreadable images and read as capture failures.

.PARAMETER TempRoot
  Root to scan. Defaults to the repository's own temp/ directory, derived from this script's location.

.PARAMETER OutFile
  Where the JSON manifest is written. Default temp/scratch/screenshot-audit-manifest.json.

.PARAMETER NearBlackVarianceMax
  Upper bound on sampled-pixel luminance variance for a frame to count as near-uniform.

.PARAMETER NearBlackLuminanceMax
  Upper bound on mean sampled luminance for a frame to count as dark. A frame is skipped as
  near-black only when it is BOTH near-uniform and dark: a legitimately dark-themed screen carries
  real content and must not be dropped on brightness alone.

.EXAMPLE
  pwsh -NoProfile -File scripts/devtest/find-recent-screenshots.ps1

  Lists every .png under temp/ written in the last 7 days, with its ticket attribution.

.EXAMPLE
  pwsh -NoProfile -File scripts/devtest/find-recent-screenshots.ps1 -MaxAgeDays 14

  Widens the window to the two-week catch-up run.
#>
[CmdletBinding()]
param(
    [ValidateRange(1, 90)]
    [int]$MaxAgeDays = 7,

    [string[]]$Extensions = @('png'),

    [string[]]$ExcludeDirs = @('gradle-tmp'),

    [string]$TempRoot,

    [string]$OutFile,

    [double]$NearBlackVarianceMax = 25.0,

    [double]$NearBlackLuminanceMax = 12.0
)

$ErrorActionPreference = 'Stop'

[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if (-not $TempRoot) { $TempRoot = Join-Path $repoRoot 'temp' }

if (-not (Test-Path -LiteralPath $TempRoot -PathType Container)) {
    Write-Error "find-recent-screenshots: temp root not found: $TempRoot"
    exit 1
}

$normalizedExtensions = @($Extensions |
    ForEach-Object { $_ -split ',' } |
    ForEach-Object { $_.Trim().TrimStart('.').ToLowerInvariant() } |
    Where-Object { $_ })

if ($normalizedExtensions.Count -eq 0) {
    Write-Error 'find-recent-screenshots: -Extensions resolved to an empty set.'
    exit 1
}

$excluded = @($ExcludeDirs | ForEach-Object { $_ -split ',' } | ForEach-Object { $_.Trim() } | Where-Object { $_ })
$tempRootFull = (Resolve-Path -LiteralPath $TempRoot).Path
$cutoff = (Get-Date).AddDays(-$MaxAgeDays)

# Path SHAPE markers of a vendored third-party checkout, as opposed to $ExcludeDirs' single directory
# NAME match: a checkout's resource fixtures span a multi-segment shape (res/drawable-hdpi/, ..) that
# no fixed directory name enumerates. Not exposed as a parameter - this is a structural fact about what
# a screenshot capture never looks like, not a per-run tuning knob (S2173).
$VendorCheckoutPatterns = @(
    '(^|/)res/drawable[^/]*/',
    '(^|/)res/mipmap[^/]*/',
    '(^|/)\.git/',
    '(^|/)node_modules/'
)

function Get-RelativeTempPath {
    param([string]$FullName)
    $relative = $FullName.Substring($tempRootFull.Length).TrimStart('\', '/')
    return ($relative -replace '\\', '/')
}

# The ticket that produced a frame is recoverable only from where it was written: the capture tools
# put ticket-bound work in temp/Sxxxx/ and untracked work in temp/scratch/. Nothing else records it.
function Get-SourceTicket {
    param([string]$RelativePath)
    $firstSegment = ($RelativePath -split '/')[0]
    if ($firstSegment -match '^S\d{4}$') { return $firstSegment }
    return $null
}

# S2191: attributes a screenshot to the nearest preceding build log run in the same directory,
# so screenshot audits do not conflate captures from two different APK builds into one render.
function Get-NearestPrecedingBuildMarker {
    param([string]$DirectoryPath, [datetime]$ImageMtime)
    if (-not $DirectoryPath -or -not (Test-Path -LiteralPath $DirectoryPath)) { return $null }
    $logs = Get-ChildItem -LiteralPath $DirectoryPath -File -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -match 'build.*\.log$|^gradle.*\.log$' } |
        Where-Object { $_.LastWriteTime -le $ImageMtime } |
        Sort-Object LastWriteTime -Descending
    if ($logs.Count -gt 0) {
        $log = $logs[0]
        return "$($log.Name)@$($log.LastWriteTime.ToString('s'))"
    }
    return $null
}

$candidates = Get-ChildItem -LiteralPath $tempRootFull -Recurse -File -ErrorAction SilentlyContinue |
    Where-Object { $normalizedExtensions -contains $_.Extension.TrimStart('.').ToLowerInvariant() } |
    Where-Object { $_.LastWriteTime -ge $cutoff }

Add-Type -AssemblyName System.Drawing

# A fixed sample grid rather than every pixel: the question is only whether the frame carries any
# content at all, and a full read of 300+ full-resolution captures costs far more than it settles.
$SAMPLE_GRID = 16

<#
.SYNOPSIS
  Classifies one image as carrying signal or not.
.DESCRIPTION
  Returns $null when the frame is worth analysing, or the skip reason when it is not. A black or
  zero-byte capture is FLAG_SECURE working as designed, not a rendering defect - it has been misread
  as a real bug at least four times in this repo, so it must never reach the findings list. The
  verdict is taken from the file's own bytes: the in-app secure-window detector has a documented
  false negative and cannot be trusted about the same frame.
#>
function Get-SkipReason {
    param([string]$Path, [long]$SizeBytes)

    if ($SizeBytes -eq 0) { return 'zero-byte' }

    $bitmap = $null
    try {
        $bitmap = [System.Drawing.Bitmap]::FromFile($Path)
        if ($bitmap.Width -eq 0 -or $bitmap.Height -eq 0) { return 'unreadable' }

        $luminances = [System.Collections.Generic.List[double]]::new()
        for ($ix = 0; $ix -lt $SAMPLE_GRID; $ix++) {
            for ($iy = 0; $iy -lt $SAMPLE_GRID; $iy++) {
                $x = [int](($ix + 0.5) * $bitmap.Width / $SAMPLE_GRID)
                $y = [int](($iy + 0.5) * $bitmap.Height / $SAMPLE_GRID)
                $pixel = $bitmap.GetPixel([Math]::Min($x, $bitmap.Width - 1), [Math]::Min($y, $bitmap.Height - 1))
                $luminances.Add(0.299 * $pixel.R + 0.587 * $pixel.G + 0.114 * $pixel.B)
            }
        }

        $mean = ($luminances | Measure-Object -Average).Average
        $variance = ($luminances | ForEach-Object { [Math]::Pow($_ - $mean, 2) } | Measure-Object -Average).Average

        if ($variance -le $NearBlackVarianceMax -and $mean -le $NearBlackLuminanceMax) { return 'near-black' }
        return $null
    } catch {
        # A frame the imaging stack cannot open is a broken capture, not a usability finding.
        return 'unreadable'
    } finally {
        if ($bitmap) { $bitmap.Dispose() }
    }
}

$kept = [System.Collections.Generic.List[object]]::new()
$skipped = [System.Collections.Generic.List[object]]::new()
$candidateCount = 0

foreach ($file in $candidates) {
    $relative = Get-RelativeTempPath -FullName $file.FullName
    $segments = $relative -split '/'
    if ($excluded | Where-Object { $segments -contains $_ }) { continue }
    if ($VendorCheckoutPatterns | Where-Object { $relative -match $_ }) { continue }

    $candidateCount++
    $reason = Get-SkipReason -Path $file.FullName -SizeBytes $file.Length
    if ($reason) {
        $skipped.Add([pscustomobject]@{ path = "temp/$relative"; reason = $reason })
        continue
    }

    $kept.Add([pscustomobject]@{
        path         = "temp/$relative"
        mtime        = $file.LastWriteTime.ToString('s')
        sizeBytes    = $file.Length
        sourceTicket = Get-SourceTicket -RelativePath $relative
        buildMarker  = Get-NearestPrecedingBuildMarker -DirectoryPath $file.DirectoryName -ImageMtime $file.LastWriteTime
    })
}

if (-not $OutFile) { $OutFile = Join-Path $repoRoot 'temp/scratch/screenshot-audit-manifest.json' }
$outDir = Split-Path -Parent $OutFile
if ($outDir -and -not (Test-Path -LiteralPath $outDir)) { New-Item -ItemType Directory -Path $outDir -Force | Out-Null }

[pscustomobject]@{
    generatedAt = (Get-Date).ToString('s')
    maxAgeDays  = $MaxAgeDays
    kept        = @($kept)
    skipped     = @($skipped)
} | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $OutFile -Encoding UTF8

$byReason = ($skipped | Group-Object reason | Sort-Object Name | ForEach-Object { "$($_.Name)=$($_.Count)" }) -join ', '
if (-not $byReason) { $byReason = 'none' }
Write-Host "find-recent-screenshots: candidates=$candidateCount kept=$($kept.Count) skipped=$($skipped.Count) ($byReason) -> $OutFile"
exit 0
