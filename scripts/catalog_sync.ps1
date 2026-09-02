# One-shot Catalogue sync: scan + render in a single PowerShell process.
#
# Replaces the two separate invocations:
#   pwsh -NoProfile -File dev/CATALOG/scripts/scan.ps1   -Module app_v2
#   pwsh -NoProfile -File dev/CATALOG/scripts/render.ps1 -Module app_v2
# with one process, eliminating PowerShell cold-start overhead twice.
#
# Usage:
#   pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2
#   pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module wear

param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('app_v2', 'wear')]
    [string]$Module,
    # S0848 incremental mode: forwarded to scan.ps1 so git last-touched is recomputed only
    # for these files. Omitted -> full refresh (unchanged behaviour; render is unaffected).
    [string[]]$ChangedFiles,
    # S1338: run scan+render even when the index is already newer than every source file.
    [switch]$Force
)

$ErrorActionPreference = 'Stop'

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot '..')
$scanScript   = Join-Path $repoRoot 'dev\CATALOG\scripts\scan.ps1'
$renderScript = Join-Path $repoRoot 'dev\CATALOG\scripts\render.ps1'

# S1184/S1338: `pwsh -File` binds `-ChangedFiles a,b` as ONE element and refuses separate
# elements outright, so every caller crossing a process boundary has to send a CSV. Expand it
# here, once, rather than leaving scan.ps1 to recompute git last-touched for a bogus joined path.
. (Join-Path $repoRoot 'scripts\quality\lib\changed-files.ps1')
$scopedFiles = @(Expand-ChangedFiles -ChangedFiles $ChangedFiles)

# S1338: the once-per-ticket rule was an instruction, and instructions are obeyed at a few
# percent - this was the largest non-gradle gate at 12.9 s across 280 runs, most of them
# re-deriving an index nothing had invalidated. Enforce it mechanically: if no source file
# under the module's scan roots is newer than the generated index, there is nothing to do.
if (-not $Force) {
    $indexPath = Join-Path $repoRoot "dev\CATALOG\$Module.jsonl"
    $renderPath = Join-Path $repoRoot "dev\CATALOG\$Module.md"
    if ((Test-Path -LiteralPath $indexPath) -and (Test-Path -LiteralPath $renderPath)) {
        # The older of the two generated artifacts is the watermark - a stale render with a
        # fresh index still needs the pass.
        $indexTime = @(
            (Get-Item -LiteralPath $indexPath).LastWriteTimeUtc,
            (Get-Item -LiteralPath $renderPath).LastWriteTimeUtc
        ) | Sort-Object | Select-Object -First 1
        $scanRoot = Join-Path $repoRoot "$Module\src"
        $newest = $null
        if (Test-Path -LiteralPath $scanRoot) {
            $newestTime = [DateTime]::MinValue
            $enumFiles = [System.IO.Directory]::EnumerateFiles($scanRoot, "*.*", [System.IO.SearchOption]::AllDirectories)
            foreach ($f in $enumFiles) {
                if ($f.EndsWith('.kt', [System.StringComparison]::OrdinalIgnoreCase) -or
                    $f.EndsWith('.java', [System.StringComparison]::OrdinalIgnoreCase)) {
                    if ($f -match '[\\/](build|\.gradle|\.kotlin)[\\/]') { continue }
                    $t = [System.IO.File]::GetLastWriteTimeUtc($f)
                    if ($t -gt $newestTime) {
                        $newestTime = $t
                    }
                }
            }
            if ($newestTime -gt [DateTime]::MinValue) {
                $newest = [PSCustomObject]@{ LastWriteTimeUtc = $newestTime }
            }
        }
        # S1344: the sector stamp is derived from dev/CATALOG/sectors.json, so editing a sector
        # definition must invalidate the index even when no source file moved - otherwise the
        # markup silently keeps the previous run's sectors, which is the "разметка устаревает"
        # risk the sector axis exists to avoid.
        $sectorDefFile = Join-Path $repoRoot 'dev\CATALOG\sectors.json'
        $sectorDefFresh = (Test-Path -LiteralPath $sectorDefFile) -and
            ((Get-Item -LiteralPath $sectorDefFile).LastWriteTimeUtc -gt $indexTime)
        if ($newest -and $newest.LastWriteTimeUtc -le $indexTime -and -not $sectorDefFresh) {
            Write-Host ("[catalog_sync] up to date ({0}) - newest source {1:yyyy-MM-dd HH:mm:ss} <= index {2:yyyy-MM-dd HH:mm:ss}. Use -Force to rebuild." -f
                $Module, $newest.LastWriteTimeUtc, $indexTime) -ForegroundColor DarkGray
            exit 0
        }
    }
}

Write-Host "[catalog_sync] scan  -> $Module" -ForegroundColor Cyan
if ($scopedFiles.Count -gt 0) {
    & $scanScript -Module $Module -ChangedFiles $scopedFiles
}
else {
    & $scanScript -Module $Module
}
if ($LASTEXITCODE -ne 0) { throw "scan.ps1 failed with exit $LASTEXITCODE" }

# S1344: stamp each record with the named product sector it belongs to. Runs between scan and
# render so the rendered index carries the sector too. Precedence matches query.ps1 -Sector:
# an explicit override wins, then the class-name axis, then the path axis.
Write-Host "[catalog_sync] sectors -> $Module" -ForegroundColor Cyan
$sectorFile = Join-Path $repoRoot 'dev\CATALOG\sectors.json'
$indexFile = Join-Path $repoRoot "dev\CATALOG\$Module.jsonl"
if ((Test-Path -LiteralPath $sectorFile) -and (Test-Path -LiteralPath $indexFile)) {
    $rawSectors = Get-Content -LiteralPath $sectorFile -Raw -Encoding UTF8 | ConvertFrom-Json
    $definitions = $rawSectors.sectors

    $overrideMap = @{}
    $classMatchList = [System.Collections.Generic.List[PSCustomObject]]::new()
    $pathMatchList = [System.Collections.Generic.List[PSCustomObject]]::new()

    foreach ($d in $definitions) {
        if ($d.overrides) {
            foreach ($o in $d.overrides) { $overrideMap[$o] = $d.name }
        }
        if ($d.classMatches) {
            foreach ($cm in $d.classMatches) {
                $regexStr = "^" + [regex]::Escape($cm).Replace("\*", ".*").Replace("\?", ".") + "$"
                $classMatchList.Add([PSCustomObject]@{ Regex = [regex]::new($regexStr); Name = $d.name })
            }
        }
        if ($d.pathMatches) {
            foreach ($pm in $d.pathMatches) {
                $regexStr = "^" + [regex]::Escape($pm).Replace("\*", ".*").Replace("\?", ".") + "$"
                $pathMatchList.Add([PSCustomObject]@{ Regex = [regex]::new($regexStr); Name = $d.name })
            }
        }
    }

    $claimedCount = 0
    $unclaimedCount = 0
    $unclaimedPrefixes = @{}
    $stampedLines = New-Object System.Collections.Generic.List[string]

    foreach ($line in (Get-Content -LiteralPath $indexFile -Encoding UTF8)) {
        if (-not $line) { continue }
        $record = $line | ConvertFrom-Json -AsHashtable
        $cls = if ($record.class) { $record.class } else { '' }
        $pth = if ($record.path) { $record.path } else { '' }

        $owner = $null
        if ($overrideMap.ContainsKey($cls)) { $owner = $overrideMap[$cls] }
        elseif ($overrideMap.ContainsKey($pth)) { $owner = $overrideMap[$pth] }

        if (-not $owner -and $cls) {
            foreach ($item in $classMatchList) {
                if ($item.Regex.IsMatch($cls)) { $owner = $item.Name; break }
            }
        }
        if (-not $owner -and $pth) {
            foreach ($item in $pathMatchList) {
                if ($item.Regex.IsMatch($pth)) { $owner = $item.Name; break }
            }
        }

        if ($owner) {
            $claimedCount++
            $record['sector'] = $owner
        }
        else {
            # Full coverage is explicitly not a goal - an unclaimed class simply carries no sector.
            $unclaimedCount++
            $prefix = (($pth -split '/') | Select-Object -First 4) -join '/'
            if ($prefix) { $unclaimedPrefixes[$prefix] = 1 + $unclaimedPrefixes[$prefix] }
        }
        $stampedLines.Add(($record | ConvertTo-Json -Depth 10 -Compress))
    }

    Set-Content -LiteralPath $indexFile -Value $stampedLines -Encoding utf8NoBOM
    $topUnclaimed = ($unclaimedPrefixes.GetEnumerator() | Sort-Object Value -Descending |
        Select-Object -First 5 | ForEach-Object { "$($_.Key) ($($_.Value))" }) -join ', '
    Write-Host ("[catalog_sync] sectors: claimed {0}, unclaimed {1}. Largest unclaimed prefixes: {2}" -f
        $claimedCount, $unclaimedCount, $topUnclaimed) -ForegroundColor DarkCyan
}
else {
    Write-Host "[catalog_sync] sectors: skipped - sectors.json or index missing" -ForegroundColor Yellow
}

Write-Host "[catalog_sync] render -> $Module" -ForegroundColor Cyan
& $renderScript -Module $Module
if ($LASTEXITCODE -ne 0) { throw "render.ps1 failed with exit $LASTEXITCODE" }

Write-Host "[catalog_sync] OK ($Module)" -ForegroundColor Green
