#requires -Version 7.0
<#
.SYNOPSIS
    S1338: run every lexical source rule over ONE walk of the tree.

.DESCRIPTION
    The nine neuroslop rules each used to be a separate pwsh process performing its own
    recursive walk of `app_v2/src/main` and re-reading every file. Measured: `a.ps1 fg`
    at 25.8 s, of which the duplicated walking and the pwsh cold starts were the bulk.
    This runner walks once (lib/source-scan.ps1), applies every rule to each file's text
    (lib/source-matchers.ps1), and compares each count to its committed integer baseline.

    Ratchet contract is unchanged: a rule fails only when its count EXCEEDS the baseline,
    and baselines are lowered, never raised. Each `assert-<rule>.ps1` remains on disk and
    delegates here, so every existing caller keeps working and there is exactly one
    definition of each violation.

    Modes:
      (default)       Report every rule's count against its baseline.
      -Gate           Exit 1 if any rule is above baseline.
      -ChangedFiles   Judge the growth these files introduce (working vs HEAD) instead of
                      a full scan - the same per-file delta the individual gates used.
      -Only           Restrict to the named rules (repeatable), for the wrapper scripts.
      -List           Print file:line for every hit (full-scan mode only).
      -UpdateBaseline Ratchet each baseline DOWN to the measured count. Full scan only.
      -Explain        S2110: name the files that moved a rule off its baseline. Reports only -
                      it never fails on a delta. Takes precedence over every mode above.

.NOTES
    Exit codes (CLAUDE.md Rule 7):
      0  every rule at or below its baseline, or a non-gate report run.
      1  -Gate and at least one rule is above its baseline.
      2  cannot verify - an unknown rule name in -Only, a source root that does not exist, or
         (-Explain) a rule whose baseline file has no commit, so there is no reference point.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-source-gates.ps1 -Gate
    pwsh -NoProfile -File scripts/quality/assert-source-gates.ps1 -Gate -Only em-dash
    pwsh -NoProfile -File scripts/quality/assert-source-gates.ps1 -Gate -ChangedFiles "a.kt,b.xml"
    pwsh -NoProfile -File scripts/quality/assert-source-gates.ps1 -Explain -Only layout-hardcoded-dimens
#>
[CmdletBinding()]
param(
    [switch]$Gate,
    [switch]$List,
    [switch]$UpdateBaseline,
    [switch]$Explain,
    [string[]]$Only,
    [string[]]$ChangedFiles
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
. (Join-Path $PSScriptRoot 'lib/source-matchers.ps1')
. (Join-Path $PSScriptRoot 'lib/changed-files.ps1')

$rules = @(Get-SourceRules)

if ($Only) {
    $wanted = @(Expand-ChangedFiles -ChangedFiles $Only)
    $unknown = @($wanted | Where-Object { $_ -notin $rules.Name })
    if ($unknown.Count -gt 0) {
        Write-Error "assert-source-gates: unknown rule name(s): $($unknown -join ', '). Known: $($rules.Name -join ', ')." -ErrorAction Continue
        exit 2
    }
    $rules = @($rules | Where-Object { $_.Name -in $wanted })
}

$failed = [System.Collections.Generic.List[string]]::new()

# --- explain mode ----------------------------------------------------------------------
# S2110: a full-scan failure prints a number and no address - `baseline 1893 | actual 1899` -
# and finding the six literals behind it cost an hour of git archaeology. This mode performs
# that archaeology mechanically. The reference point is the commit that last touched the rule's
# OWN baseline file, because that is the tree state the integer was true for: anything counted
# above it arrived after the threshold was set. Reports only - a delta never fails the run,
# since the gate modes above already own the refusal.
if ($Explain) {
    $repoRootNorm = ($repoRoot -replace '\\', '/').TrimEnd('/')
    $cannotVerify = $false

    $commitCache = @{}
    $workCache = @{}

    function Get-ExplainCommitFileMap([string]$repoRoot, [string]$refCommit, [string[]]$roots) {
        $rawCacheKey = "$refCommit`:;$($roots -join ';')"
        if ($commitCache.ContainsKey($rawCacheKey)) {
            return $commitCache[$rawCacheKey]
        }

        $validRoots = @(& git -C $repoRoot ls-tree --name-only $refCommit -- @roots 2>$null)
        if ($validRoots.Count -eq 0) {
            $commitCache[$rawCacheKey] = @{}
            return @{}
        }

        $map = @{}
        $ms = [System.IO.MemoryStream]::new()
        $psi = [System.Diagnostics.ProcessStartInfo]::new()
        $psi.FileName = 'git'
        $psi.Arguments = "-C ""$repoRoot"" archive --format=zip $refCommit $($validRoots -join ' ')"
        $psi.UseShellExecute = $false
        $psi.RedirectStandardOutput = $true
        $psi.RedirectStandardError = $true

        $proc = [System.Diagnostics.Process]::Start($psi)
        $proc.StandardOutput.BaseStream.CopyTo($ms)
        $proc.WaitForExit()

        if ($proc.ExitCode -eq 0 -and $ms.Length -gt 0) {
            $ms.Position = 0
            $zip = [System.IO.Compression.ZipArchive]::new($ms)
            foreach ($entry in $zip.Entries) {
                if ($entry.FullName.EndsWith('/')) { continue }
                $reader = [System.IO.StreamReader]::new($entry.Open(), [System.Text.Encoding]::UTF8)
                $map[$entry.FullName] = $reader.ReadToEnd()
                $reader.Close()
            }
            $zip.Dispose()
        }
        $ms.Dispose()

        $commitCache[$rawCacheKey] = $map
        return $map
    }

    function Get-ExplainWorkFileText([string]$repoRoot, [string]$relativePath) {
        if ($workCache.ContainsKey($relativePath)) {
            return $workCache[$relativePath]
        }
        $abs = Join-Path $repoRoot $relativePath
        $text = ''
        if ([System.IO.File]::Exists($abs)) {
            $text = [System.IO.File]::ReadAllText($abs)
        }
        $workCache[$relativePath] = $text
        return $text
    }

    foreach ($rule in $rules) {
        $baselineRel = ((Join-Path $PSScriptRoot $rule.Baseline) -replace '\\', '/')
        if ($baselineRel.ToLower().StartsWith($repoRootNorm.ToLower() + '/')) {
            $baselineRel = $baselineRel.Substring($repoRootNorm.Length + 1)
        }

        $refCommit = (& git -C $repoRoot log -1 --format=%H -- $baselineRel 2>$null | Out-String).Trim()
        if ([string]::IsNullOrWhiteSpace($refCommit)) {
            # Saying this out loud is the point (strategic S2110 section 7): an empty list would
            # read as "nothing drifted", which is the opposite of "I could not look".
            Write-Host ("{0}: NO REFERENCE POINT - {1} has no commit, so there is no tree state its baseline was true for." -f
                $rule.Name, $baselineRel) -ForegroundColor Yellow
            $cannotVerify = $true
            continue
        }

        $roots = @($rule.Roots)
        $tracked = @(& git -C $repoRoot diff --name-only $refCommit -- @roots 2>$null)
        # An untracked working file is part of "the working tree" too, and it is exactly the shape
        # that reaches neither judging mode, so leaving it out would reproduce the original hole.
        $untracked = @(& git -C $repoRoot ls-files --others --exclude-standard -- @roots 2>$null)

        $paths = @(@($tracked + $untracked) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } |
            ForEach-Object { $_ -replace '\\', '/' } | Sort-Object -Unique | Where-Object {
                ($_ -match $rule.PathFilter) -and
                ($rule.Extensions -contains [System.IO.Path]::GetExtension($_)) -and
                ($rule.ExcludeNames -notcontains [System.IO.Path]::GetFileName($_))
            })

        $refTotal = 0
        $workTotal = 0
        $drifted = [System.Collections.Generic.List[object]]::new()
        $refMap = Get-ExplainCommitFileMap $repoRoot $refCommit $roots

        foreach ($p in $paths) {
            $refText = if ($refMap.ContainsKey($p)) { $refMap[$p] } else { '' }
            $workText = Get-ExplainWorkFileText $repoRoot $p

            # Fast path: if reference text matches working tree text, count cannot change.
            if ($refText -eq $workText) { continue }

            $refCount = [int](& $rule.CountInText $refText)
            $workCount = [int](& $rule.CountInText $workText)
            $refTotal += $refCount
            $workTotal += $workCount
            if ($refCount -ne $workCount) {
                $drifted.Add([pscustomobject]@{ Path = $p; Ref = $refCount; Work = $workCount })
            }
        }

        Write-Host ("{0}: reference {1} ({2})" -f $rule.Name, $refCommit.Substring(0, 9), $baselineRel)
        foreach ($d in $drifted) { Write-Host ("  {0}  {1} -> {2}" -f $d.Path, $d.Ref, $d.Work) }
        Write-Host ("  {0} path(s) examined | reference {1} | working {2} | delta {3}" -f
            $paths.Count, $refTotal, $workTotal, ($workTotal - $refTotal)) -ForegroundColor DarkGray
    }

    if ($cannotVerify) {
        Write-Error 'assert-source-gates: CANNOT VERIFY - at least one rule has no reference commit.' -ErrorAction Continue
        exit 2
    }
    exit 0
}

# --- delta mode ------------------------------------------------------------------------
# One rule at a time, because the delta is defined per file against its HEAD version and
# the git read - not the walk - dominates. Same predicate as the full scan by construction.
if ($ChangedFiles -and @(Expand-ChangedFiles -ChangedFiles $ChangedFiles).Count -gt 0) {
    . (Join-Path $PSScriptRoot 'lib/changed-files-delta.ps1')
    $expanded = @(Expand-ChangedFiles -ChangedFiles $ChangedFiles)
    foreach ($rule in $rules) {
        $scoped = @($expanded | Where-Object { ($_ -replace '\\', '/') -match $rule.PathFilter })
        $d = Measure-ChangedFileGrowth -ChangedFiles $scoped -RepoRoot $repoRoot `
            -Extensions $rule.Extensions -CountInText $rule.CountInText -ExcludeNames $rule.ExcludeNames
        Write-Host ("{0} [delta over changed files]: new occurrences {1}" -f $rule.Name, $d.Growth)
        if ($d.Growth -gt 0) {
            foreach ($p in $d.PerFile) { if ($p.New -gt 0) { Write-Host ("  +{0} in {1}" -f $p.New, $p.Path) } }
            if ($Gate) {
                Write-Host ("FAIL: {0}" -f $rule.FailMessage)
                $failed.Add($rule.Name)
            }
        }
    }
    if ($Gate -and $failed.Count -gt 0) {
        Write-Host ("assert-source-gates: FAIL ({0} rule(s) above baseline in the changed files)." -f $failed.Count) -ForegroundColor Red
        exit 1
    }
    Write-Host 'assert-source-gates: PASS (no new occurrences in the changed files).' -ForegroundColor Green
    exit 0
}

# --- full scan -------------------------------------------------------------------------
$roots = @($rules | ForEach-Object { $_.Roots } | Select-Object -Unique)
$existingRoots = @($roots | Where-Object { Test-Path (Join-Path $repoRoot $_) })
if ($existingRoots.Count -eq 0) {
    Write-Error "assert-source-gates: none of the source roots exist under $repoRoot - nothing was scanned." -ErrorAction Continue
    exit 2
}

$matchers = @(ConvertTo-SourceMatchers -Rules $rules)
$scan = Invoke-SourceScan -RepoRoot $repoRoot -Matchers $matchers -Roots $existingRoots -Locate:$List

foreach ($rule in $rules) {
    $result = $scan[$rule.Name]
    $current = [int]$result.Count
    $baselineFile = Join-Path $PSScriptRoot $rule.Baseline

    if ($List -and $result.PerFile.Count -gt 0) {
        foreach ($f in $result.PerFile) {
            $lines = @($f.Lines)
            # A rule without a locator still names its files - reporting nothing under -List
            # reads as "no hits", which is the opposite of what the count says.
            if ($lines.Count -eq 0) { Write-Host ("{0}  x{1}  [{2}]" -f $f.Path, $f.Count, $rule.Name) }
            else { foreach ($ln in $lines) { Write-Host ("{0}:{1}  [{2}]" -f $f.Path, $ln, $rule.Name) } }
        }
    }

    if (-not (Test-Path $baselineFile)) {
        Write-Host ("{0}: NO BASELINE yet | actual {1} - run -UpdateBaseline to seed." -f $rule.Name, $current)
        if ($UpdateBaseline) { Set-Content -LiteralPath $baselineFile -Value "$current" }
        continue
    }

    $baseline = [int]((Get-Content -LiteralPath $baselineFile -Raw).Trim())

    if ($UpdateBaseline) {
        if ($current -lt $baseline) {
            Set-Content -LiteralPath $baselineFile -Value "$current"
            Write-Host ("{0} baseline ratcheted DOWN: {1} -> {2}" -f $rule.Name, $baseline, $current)
        }
        elseif ($current -eq $baseline) {
            Write-Host ("{0} baseline unchanged ({1})" -f $rule.Name, $baseline)
        }
        else {
            Write-Host ("{0}: refusing to RAISE baseline ({1} -> {2})." -f $rule.Name, $baseline, $current) -ForegroundColor Red
            $failed.Add($rule.Name)
        }
        continue
    }

    $delta = $current - $baseline
    # The scope is the rule's own roots, not a fixed 'src/main': rules already walk wear/src and
    # app_v2/src, and hardcoded-drive-path walks scripts/, maestro/ and dev/. A banner naming a
    # tree the run never opened is the S1807 hazard - a verdict quoted against the wrong module.
    $scope = if ($rule.Roots) { ($rule.Roots -join ', ') } else { 'the repository' }
    Write-Host ("{0} in {1}: baseline {2} | actual {3} | delta {4}" -f $rule.Name, $scope, $baseline, $current, $delta)
    if ($current -gt $baseline) {
        Write-Host ("FAIL: {0}" -f $rule.FailMessage)
        $failed.Add($rule.Name)
    }
    elseif ($current -lt $baseline) {
        # S1338: ratchet DOWN automatically on a green full-project run. The baselines were
        # never lowered - nothing lowered them - so full-scan mode was shipping 10 em-dashes,
        # 5 unsafe collects and 2 `!!` for free, and a regression back up to the old cap would
        # have passed. This branch is unreachable from the delta path above, which returns
        # before it: a scoped count is a fraction of the project total and recording it as the
        # baseline would slam the cap shut on files the run never looked at.
        Set-Content -LiteralPath $baselineFile -Value "$current"
        Write-Host ("  ratcheted DOWN: {0} baseline {1} -> {2}" -f $rule.Name, $baseline, $current) -ForegroundColor DarkGray
    }
}

$scanInfo = $scan['_scan']
Write-Host ("assert-source-gates: {0} rule(s) over ONE walk of {1} file(s), {2} read, {3} ms." -f
    $rules.Count, $scanInfo.FilesWalked, $scanInfo.FilesRead, $scanInfo.ElapsedMs) -ForegroundColor DarkGray

if ($failed.Count -gt 0) {
    if ($Gate -or $UpdateBaseline) {
        Write-Host ("assert-source-gates: FAIL ({0} rule(s) above baseline: {1})." -f $failed.Count, ($failed -join ', ')) -ForegroundColor Red
        exit 1
    }

    # S1857: audit mode still returns 0 - that is the same contract every sibling gate keeps, and changing
    # it would make each call without -Gate fail somebody else's run. What it must NOT do is claim the tree
    # is clean. The old code fell through to the PASS line below, so a run with a rule ABOVE its baseline
    # printed its own FAIL: two lines up and then "all rules at or below baseline" as its verdict. In a run
    # of twenty rules the last line is the one that gets read and quoted as evidence, which turned a red
    # measurement into a green step log.
    Write-Host ("assert-source-gates: ADVISORY ({0} rule(s) above baseline: {1}) - audit mode, pass -Gate to refuse." -f
        $failed.Count, ($failed -join ', ')) -ForegroundColor Yellow
    exit 0
}

Write-Host 'assert-source-gates: PASS (all rules at or below baseline).' -ForegroundColor Green
exit 0
