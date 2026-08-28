<#
.SYNOPSIS
  S1502 streams performance sweep - seed a device with the full shipped stream catalog.

.DESCRIPTION
  Brings a connected device's debug install to the full shipped catalog and reports how many
  `stream_sources` rows it ended up with, so a measurement run starts from a known N instead of
  whatever the device happened to hold.

  The rows are written straight into the app database rather than through the in-app catalog
  refresh. The production import downloads a zip release asset, so driving it would make the seed
  depend on network reachability and on the release asset matching the working tree - two things a
  performance baseline must not vary with. Seeding from `delivery/stream-catalog/streams.csv`
  reproduces the exact catalog the tree ships.

  Only catalog columns are written (id, url, title, mediaKind, sourceOrigin, sortIndex, pinned,
  addedAt, category, topic, language, country, access). Play-outcome columns are deliberately not
  named, so the script keeps working across the S1502 schema split that moves them out of this table.

  Existing CATALOG-origin rows are replaced; MANUAL and IMPORTED rows are left alone, mirroring
  StreamSourceRepository.mergeCatalog.

  Requires `sqlite3`, which ships in the same platform-tools directory as adb.

  Exit codes:
    0  - seeded and verified: the table holds the expected row count
    1  - bad arguments / adb or sqlite3 missing / catalog csv missing / package not installed
    2  - no device reachable
    11 - device reachable but the catalog did not reach the expected size

.PARAMETER DeviceId
  Specific adb device id. Omit when exactly one device is attached.

.PARAMETER Json
  Emit a single JSON object instead of human-readable lines.

.EXAMPLE
  pwsh -NoProfile -File scripts/devtest/streams-perf-seed.ps1 -Json
#>
[CmdletBinding()]
param(
    [string]$DeviceId,
    [switch]$Json
)

$ErrorActionPreference = 'Stop'

[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)

$RepoRoot     = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$DebugPackage = 'com.sza.fastmediasorter.debug'
$DbName       = 'fastmediasorter_v2.db'
$CatalogCsv   = Join-Path $RepoRoot 'delivery/stream-catalog/streams.csv'
$WorkDir      = Join-Path $RepoRoot 'temp/S1502/seed'

# Fixed so a re-seed produces byte-identical rows; a wall-clock value would make two baseline runs
# differ in a column the sort mode reads.
$SeedAddedAt = 1700000000000

function Get-Adb {
    foreach ($root in @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT)) {
        if ($root) { $c = Join-Path $root 'platform-tools\adb.exe'; if (Test-Path $c) { return $c } }
    }
    $onPath = Get-Command adb -ErrorAction SilentlyContinue
    if ($onPath) { return $onPath.Source }
    $known = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
    if (Test-Path $known) { return $known }
    return $null
}

# sqlite3 ships beside adb in platform-tools, so resolve it from the adb we just found before
# falling back to PATH - that keeps the two tools from coming from different SDK installs.
function Get-Sqlite3 {
    param([string]$Adb)
    if ($Adb) {
        $sibling = Join-Path (Split-Path -Parent $Adb) 'sqlite3.exe'
        if (Test-Path $sibling) { return $sibling }
    }
    $onPath = Get-Command sqlite3 -ErrorAction SilentlyContinue
    if ($onPath) { return $onPath.Source }
    return $null
}

function Write-Result {
    param([hashtable]$Record, [int]$Code)
    if ($Json) { [pscustomobject]$Record | ConvertTo-Json -Compress }
    else {
        foreach ($k in $Record.Keys) { Write-Host ("{0}: {1}" -f $k, $Record[$k]) }
    }
    exit $Code
}

function Fail {
    param([string]$Message, [int]$Code)
    if ($Json) { [pscustomobject]@{ error = $Message; seeded = $false } | ConvertTo-Json -Compress }
    else { Write-Host $Message }
    if ($Code -ne 1) { Write-Error $Message -ErrorAction Continue }
    exit $Code
}

$adb = Get-Adb
if (-not $adb) { Fail -Message 'adb not found' -Code 1 }

$sqlite = Get-Sqlite3 -Adb $adb
if (-not $sqlite) { Fail -Message 'sqlite3 not found (expected beside adb in platform-tools)' -Code 1 }

if (-not (Test-Path $CatalogCsv)) { Fail -Message "catalog csv not found: $CatalogCsv" -Code 1 }

$adbTarget = @(); if ($DeviceId) { $adbTarget = @('-s', $DeviceId) }

$state = "$(& $adb @adbTarget get-state 2>&1)".Trim()
if ($state -ne 'device') { Fail -Message "no device reachable (adb get-state: $state)" -Code 2 }

$installed = "$(& $adb @adbTarget shell pm list packages $DebugPackage 2>&1)".Trim()
if ($installed -notmatch [regex]::Escape($DebugPackage)) {
    Fail -Message "$DebugPackage is not installed on the device" -Code 1
}

# ---------- build the seed SQL from the shipped catalog ----------
$rows = Import-Csv -Path $CatalogCsv
$expected = $rows.Count
if ($expected -le 0) { Fail -Message 'catalog csv parsed to zero rows' -Code 1 }

New-Item -ItemType Directory -Force -Path $WorkDir | Out-Null
$sqlPath   = Join-Path $WorkDir 'seed.sql'
$localDb   = Join-Path $WorkDir $DbName

function Quote {
    param($Value)
    if ($null -eq $Value) { return 'NULL' }
    $s = [string]$Value
    if ($s.Length -eq 0) { return 'NULL' }
    return "'" + $s.Replace("'", "''") + "'"
}

$sb = [System.Text.StringBuilder]::new()
[void]$sb.AppendLine('PRAGMA journal_mode=DELETE;')
[void]$sb.AppendLine('BEGIN TRANSACTION;')
[void]$sb.AppendLine("DELETE FROM stream_sources WHERE sourceOrigin = 'CATALOG';")

$index = 0
foreach ($row in $rows) {
    # Deterministic id: a random UUID would make two seeds of the same catalog produce different
    # primary keys, which defeats comparing a baseline run against an after run.
    $id = 'seed-' + $index.ToString('D6')
    $values = @(
        (Quote $id)
        (Quote $row.url)
        (Quote $row.name)
        (Quote $row.media_kind)
        "'CATALOG'"
        $index
        0
        $SeedAddedAt
        (Quote $row.category)
        (Quote $row.topic)
        (Quote $row.language)
        (Quote $row.country)
        (Quote $row.access)
    ) -join ', '
    [void]$sb.AppendLine("INSERT OR IGNORE INTO stream_sources (id, url, title, mediaKind, sourceOrigin, sortIndex, pinned, addedAt, category, topic, language, country, access) VALUES ($values);")
    $index++
}

[void]$sb.AppendLine('COMMIT;')
[void]$sb.AppendLine('VACUUM;')
Set-Content -Path $sqlPath -Value $sb.ToString() -Encoding UTF8

# ---------- pull, seed, push ----------
# Stop the app first: a live process holds the WAL open, so a copy taken underneath it can miss
# rows that were never checkpointed into the main file.
& $adb @adbTarget shell am force-stop $DebugPackage *> $null

Remove-Item -Path "$localDb*" -Force -ErrorAction SilentlyContinue
foreach ($suffix in @('', '-wal', '-shm')) {
    $remote = "databases/$DbName$suffix"
    $exists = "$(& $adb @adbTarget shell "run-as $DebugPackage test -f $remote && echo YES || echo NO" 2>&1)".Trim()
    if ($exists -notmatch 'YES') { continue }
    # exec-out keeps the byte stream intact, but it has to land in the file without passing through
    # the PowerShell pipeline: a native command's stdout arrives there as decoded strings, which
    # corrupts a SQLite file. Start-Process redirects the raw stream straight to disk.
    $pullArgs = @($adbTarget) + @('exec-out', "run-as $DebugPackage cat $remote")
    Start-Process -FilePath $adb -ArgumentList $pullArgs -NoNewWindow -Wait `
        -RedirectStandardOutput "$localDb$suffix" | Out-Null
}

if (-not (Test-Path $localDb)) {
    Fail -Message "could not pull databases/$DbName - launch the app once so the database exists" -Code 1
}

& $sqlite $localDb ".read `"$($sqlPath -replace '\\', '/')`"" 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) { Fail -Message "sqlite3 failed to apply the seed script" -Code 1 }

$actual = [int]"$(& $sqlite $localDb 'SELECT COUNT(*) FROM stream_sources;')".Trim()

$stagePath = "/data/local/tmp/$DbName"
& $adb @adbTarget push $localDb $stagePath *> $null
& $adb @adbTarget shell "run-as $DebugPackage cp $stagePath databases/$DbName" *> $null
# VACUUM folded the WAL into the main file, so a leftover -wal/-shm on the device would replay
# stale pages over what we just pushed.
& $adb @adbTarget shell "run-as $DebugPackage rm -f databases/$DbName-wal databases/$DbName-shm" *> $null
& $adb @adbTarget shell rm -f $stagePath *> $null

$record = [ordered]@{
    seeded     = $true
    package    = $DebugPackage
    catalogCsv = $CatalogCsv
    expected   = $expected
    actual     = $actual
    device     = "$(& $adb @adbTarget shell getprop ro.product.model 2>&1)".Trim()
    apiLevel   = "$(& $adb @adbTarget shell getprop ro.build.version.sdk 2>&1)".Trim()
}

if ($actual -lt $expected) {
    if ($Json) { [pscustomobject]$record | ConvertTo-Json -Compress }
    else { foreach ($k in $record.Keys) { Write-Host ("{0}: {1}" -f $k, $record[$k]) } }
    Write-Error "catalog did not reach the expected size: expected $expected, actual $actual" -ErrorAction Continue
    exit 11
}

Write-Result -Record $record -Code 0
