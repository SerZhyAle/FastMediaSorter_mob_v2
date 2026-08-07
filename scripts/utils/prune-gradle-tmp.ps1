<#
.SYNOPSIS
    Removes stale build-JVM leftovers from the shared temp/gradle-tmp directory.

.DESCRIPTION
    S1463: gradlew.bat points TMP and TEMP of every build process at one shared temp/gradle-tmp, and
    nothing ever empties it. Each test-JVM start unpacks a fresh native library set and leaves it
    behind - measured 2026-08-07 at 3001 entries, including 746 sqlite-jdbc DLLs weighing 328 MB, 407
    conscrypt DLLs and 335 MockK boot jars (58 of them from that single day). forkEvery multiplies the
    JVM starts, so the pile grows with every unit run.

    Whether that pile is what kills the worker is NOT established - see PLAN/S1463 section 2.3. This
    script removes a documented aggravator; it is not billed as the cure, and its predicate is its own
    (the directory stays bounded), not the disappearance of an intermittent failure.

    Only patterns known to be build-JVM leftovers are touched. Anything younger than the age floor is
    left alone: that floor is the whole safety argument, since deleting a live run's temp files would
    turn this cleanup into the very failure it is meant to reduce.

.PARAMETER MaxAgeHours
    Age floor. 24 by default - no live run is a day old, so nothing in flight can match.

.PARAMETER Path
    Directory to prune. Defaults to temp/gradle-tmp under the repo root.

.OUTPUTS
    Exit 0 - pruned (or nothing to prune).
    Exit 2 - could not run: the directory does not exist.
#>
[CmdletBinding(SupportsShouldProcess)]
param(
    [int]$MaxAgeHours = 24,
    [string]$Path
)

$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..')).Path
if (-not $Path) { $Path = Join-Path $repoRoot 'temp/gradle-tmp' }

if (-not (Test-Path $Path)) {
    Write-Error "prune-gradle-tmp: no directory at $Path - nothing to prune, and nothing verified." -ErrorAction Continue
    exit 2
}

# Every entry here is unpacked fresh by a build JVM and never reclaimed by it. Deliberately narrow:
# an unrecognised name is left alone rather than guessed at, because this directory is also where
# project tooling drops artifacts a person may still want.
$leakedPatterns = @(
    'sqlite-*-sqlitejdbc.dll',      # sqlite-jdbc native lib, one per JVM
    'conscrypt_openjdk_jni-*.dll',  # Conscrypt native lib, one per JVM
    'mockk_boot*.jar',              # MockK bootstrap jar, appended to the boot classpath
    'robolectric-*',                # Robolectric sandbox dirs its shutdown hook failed to remove
    'kotlin-daemon.*.log',
    'kotlin-daemon.*.log.lck',
    'kotlin-compiler-*.alive'
)

$cutoff = (Get-Date).AddHours(-$MaxAgeHours)
$all = @(Get-ChildItem -Path $Path -Force -ErrorAction SilentlyContinue)

# Match by the numeric worker-dir shape Gradle uses (<millis>-<n>), then by the leaked-library names.
$stale = @($all | Where-Object {
        if ($_.LastWriteTime -ge $cutoff) { return $false }
        if ($_.Name -match '^\d+-\d+$') { return $true }
        foreach ($pattern in $leakedPatterns) {
            if ($_.Name -like $pattern) { return $true }
        }
        return $false
    })

$removed = 0
$freedBytes = 0L
$skipped = 0

foreach ($entry in $stale) {
    $size = if ($entry.PSIsContainer) { 0L } else { [long]$entry.Length }
    if (-not $PSCmdlet.ShouldProcess($entry.FullName, 'Remove')) { continue }
    try {
        Remove-Item -LiteralPath $entry.FullName -Recurse -Force -ErrorAction Stop
        $removed++
        $freedBytes += $size
    } catch {
        # A locked file belongs to a process still using it - leaving it is the correct outcome, not
        # a failure of this run.
        $skipped++
    }
}

$freedMb = [math]::Round($freedBytes / 1MB, 1)
Write-Host ("prune-gradle-tmp: {0} entries total, {1} older than {2}h, {3} removed ({4} MB), {5} in use." -f `
        $all.Count, $stale.Count, $MaxAgeHours, $removed, $freedMb, $skipped)
exit 0
