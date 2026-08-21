#requires -Version 7.0
<#
.SYNOPSIS
    S1858: migrates the locale fingerprint registry and baseline to module-qualified identities.

.DESCRIPTION
    Before S1858 both files addressed a translatable unit as set|file|key, with no module segment.
    app_v2 and wear each ship src/main/res/values/strings.xml and share 14 key names, so those keys
    addressed one slot in a store that had room for one hash. Whichever module imported last wrote
    it, and the gate then read the other module's text against the wrong hash and called it
    untranslated. This command rewrites both files to module|set|file|key[|slot] once.

    Ownership is resolved from the corpus each module actually declares, exported through
    locale-bulk-export.ps1 so that "a translatable unit" means here exactly what it means to the
    gate - a value marked translatable="false", a glyph or a layout literal is not one.

    Per slot:
      - exactly one module declares the unit -> renamed, lossless. This is almost the whole file.
      - no module declares it -> dropped as an orphan; the key no longer exists anywhere.
      - both modules declare it -> the hash is kept under the module whose current English text
        fingerprints to it, and dropped for the other. The overwritten half is unrecoverable, and
        stamping the current text's hash instead would assert a freshness nothing here can prove,
        so the record is dropped and the unit re-enters the ordinary bulk round trip.

    Every dropped identity is listed, because a dropped record is the only cost this migration has.

.PARAMETER FingerprintsPath
    Registry to migrate. Default scripts/quality/locale-source-fingerprints.json.

.PARAMETER BaselinePath
    Baseline to migrate. Default scripts/quality/locale-untranslated-baseline.txt.

.PARAMETER Modules
    Modules owning resources, in the order preferred when reporting. Default app_v2,wear.

.PARAMETER CorpusDir
    Where the per-module corpus exports are written. Default temp/S1858/migrate-corpus.

.PARAMETER DryRun
    Classify and report, write nothing.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/migrate-locale-fingerprints-module.ps1 -DryRun

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/migrate-locale-fingerprints-module.ps1

.OUTPUTS
    Exit codes:
      0 - migration completed, or -DryRun classified without writing.
      1 - a corpus export failed, so ownership could not be resolved; nothing was written.
      2 - the registry is already at the current schema version; nothing to do.
#>
[CmdletBinding()]
param(
    [string]$FingerprintsPath,
    [string]$BaselinePath,
    [string[]]$Modules = @('app_v2', 'wear'),
    [string]$CorpusDir,
    [switch]$DryRun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
. (Join-Path $repoRoot 'scripts/quality/lib/locale-fingerprints.ps1')

$Modules = @($Modules | ForEach-Object { $_ -split ',' } | Where-Object { $_ } | ForEach-Object { $_.Trim() })
if (-not $FingerprintsPath) { $FingerprintsPath = Join-Path $repoRoot 'scripts/quality/locale-source-fingerprints.json' }
if (-not $BaselinePath) { $BaselinePath = Join-Path $repoRoot 'scripts/quality/locale-untranslated-baseline.txt' }
if (-not $CorpusDir) { $CorpusDir = Join-Path $repoRoot 'temp/S1858/migrate-corpus' }

$currentVersion = Get-LocaleFingerprintsSchemaVersion -Path $FingerprintsPath
if ($currentVersion -ge 2) {
    Write-Host "migrate-locale-fingerprints-module: registry already declares schema v$currentVersion - nothing to migrate."
    exit 2
}

# Source sets per module: the gate checks app_v2's flavor string sets, wear ships only main.
$setsByModule = @{ 'app_v2' = 'main,vr,noLegal'; 'wear' = 'main' }
$exporter = Join-Path $repoRoot 'scripts/utils/locale-bulk-export.ps1'

$ownership = @{}   # unqualified unit id -> hashtable(module -> english hash)
foreach ($module in $Modules) {
    $sets = if ($setsByModule.ContainsKey($module)) { $setsByModule[$module] } else { 'main' }
    $outDir = Join-Path $CorpusDir $module
    & pwsh -NoProfile -File $exporter -Module $module -SourceSet $sets -All -OutDir $outDir | Out-Null
    if ($LASTEXITCODE -ne 0) {
        Write-Error "migrate-locale-fingerprints-module: corpus export failed for $module (exit $LASTEXITCODE) - ownership cannot be resolved." -ErrorAction Continue
        exit 1
    }

    $sidecar = Join-Path $outDir 'all_texts_index.jsonl'
    if (-not (Test-Path -LiteralPath $sidecar)) {
        Write-Error "migrate-locale-fingerprints-module: no sidecar at $sidecar - ownership cannot be resolved." -ErrorAction Continue
        exit 1
    }

    $units = 0
    foreach ($line in (Get-Content -LiteralPath $sidecar -Encoding UTF8 | Where-Object { $_ })) {
        $record = $line | ConvertFrom-Json
        $unit = if ($record.slot) { "$($record.set)|$($record.file)|$($record.key)|$($record.slot)" } else { "$($record.set)|$($record.file)|$($record.key)" }
        if (-not $ownership.ContainsKey($unit)) { $ownership[$unit] = @{} }
        $ownership[$unit][$module] = Get-EnglishStringFingerprint -Text ([string]$record.en)
        $units++
    }
    Write-Host "migrate-locale-fingerprints-module: $module declares $units translatable unit(s)."
}

$fingerprints = Get-LocaleSourceFingerprints -Path $FingerprintsPath
$migrated = @{}
$renamed = 0
$orphaned = 0
$ambiguousKept = 0
$dropped = [System.Collections.Generic.List[string]]::new()

foreach ($locale in ($fingerprints.Keys | Sort-Object)) {
    $migrated[$locale] = @{}
    foreach ($unit in $fingerprints[$locale].Keys) {
        $hash = $fingerprints[$locale][$unit]
        if (-not $ownership.ContainsKey($unit)) { $orphaned++; continue }

        $owners = @($ownership[$unit].Keys)
        if ($owners.Count -eq 1) {
            $migrated[$locale]["$($owners[0])|$unit"] = $hash
            $renamed++
            continue
        }

        foreach ($owner in $owners) {
            if ($ownership[$unit][$owner] -eq $hash) {
                $migrated[$locale]["$owner|$unit"] = $hash
                $ambiguousKept++
            } else {
                $dropped.Add("$owner|$unit")
            }
        }
    }
}

$droppedIdentities = @($dropped | Sort-Object -Unique)

Write-Host ''
Write-Host 'migrate-locale-fingerprints-module: registry classification'
Write-Host "  renamed (single owner)        : $renamed"
Write-Host "  kept (shared unit, hash match) : $ambiguousKept"
Write-Host "  dropped (shared unit, no match): $($dropped.Count) across $($droppedIdentities.Count) identity(ies)"
Write-Host "  dropped (orphan, key is gone)  : $orphaned"

if ($droppedIdentities.Count -gt 0) {
    Write-Host ''
    Write-Host 'Identities losing provenance - they re-enter the ordinary bulk round trip:'
    foreach ($identity in $droppedIdentities) { Write-Host "  $identity" }
}

# The baseline shares the identity space, so an app_v2 entry silences the same key name in wear
# until it is qualified too. Comments and blank lines pass through untouched.
$baselineLines = @()
$baselineRewritten = 0
$baselineOrphans = [System.Collections.Generic.List[string]]::new()
if (Test-Path -LiteralPath $BaselinePath) {
    foreach ($line in (Get-Content -LiteralPath $BaselinePath -Encoding UTF8)) {
        $trimmed = $line.Trim()
        if (-not $trimmed -or $trimmed.StartsWith('#')) { $baselineLines += $line; continue }
        # No module declares it, so no run can ever match it again; keeping it unqualified would
        # only make the producer report it as a stale entry forever. Dropped like a registry orphan.
        if (-not $ownership.ContainsKey($trimmed)) {
            $baselineOrphans.Add($trimmed)
            continue
        }
        $owners = @($ownership[$trimmed].Keys | Sort-Object)
        foreach ($owner in $owners) {
            $baselineLines += "$owner|$trimmed"
            $baselineRewritten++
        }
    }
}

Write-Host ''
Write-Host "migrate-locale-fingerprints-module: baseline entries qualified: $baselineRewritten"
if ($baselineOrphans.Count -gt 0) {
    Write-Host "  dropped (no module declares them - the key is gone): $($baselineOrphans.Count)"
    foreach ($entry in $baselineOrphans) { Write-Host "    $entry" }
}

if ($DryRun) {
    Write-Host ''
    Write-Host 'migrate-locale-fingerprints-module: -DryRun, no file changed.'
    exit 0
}

Save-LocaleSourceFingerprints -Fingerprints $migrated -Path $FingerprintsPath
if (Test-Path -LiteralPath $BaselinePath) {
    $utf8NoBom = [System.Text.UTF8Encoding]::new($false)
    [System.IO.File]::WriteAllText($BaselinePath, (($baselineLines -join "`n") + "`n"), $utf8NoBom)
}

Write-Host ''
Write-Host "migrate-locale-fingerprints-module: wrote $FingerprintsPath (schema v2) and $BaselinePath."
exit 0
