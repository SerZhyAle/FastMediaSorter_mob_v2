# requires -Version 7.0
<#
.SYNOPSIS
    S1824: shared library for English string fingerprinting and translation freshness tracking.

.DESCRIPTION
    Dot-source it; it declares functions and never exits, so it has no exit-code contract.

    Manages the translation provenance mapping in scripts/quality/locale-source-fingerprints.json.
    For each best-effort locale and each resource unit (set|file|key or set|file|key|slot), the
    store records the 16-character SHA-256 fingerprint of the normalized plain English text that
    was active when that translation was produced or imported.

    When the English text is edited in values/strings*.xml, its fingerprint changes; any locale
    whose recorded fingerprint does not match is considered stale and reported by list-new-lexemes.ps1.
#>

Set-StrictMode -Version Latest

$script:LocaleFingerprintsDefaultPath = Join-Path (Split-Path -Parent (Split-Path -Parent $PSScriptRoot)) 'quality/locale-source-fingerprints.json'

function Get-EnglishStringFingerprint {
    <#
    .SYNOPSIS
        Computes a 16-character lowercase hex SHA-256 fingerprint for plain English text.
    #>
    param(
        [AllowEmptyString()]
        [string]$Text
    )

    if ($null -eq $Text) { $Text = '' }
    $normalized = ($Text -replace '[\r\n\t]+', ' ').Trim()
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($normalized)
    $hasher = [System.Security.Cryptography.SHA256]::Create()
    $hash = $hasher.ComputeHash($bytes)
    return [System.Convert]::ToHexString($hash).Substring(0, 16).ToLowerInvariant()
}

function Get-LocaleSourceFingerprintsPath {
    param([string]$Path)
    if ($Path) { return $Path }
    return $script:LocaleFingerprintsDefaultPath
}

function Get-LocaleSourceFingerprints {
    <#
    .SYNOPSIS
        Loads the locale source fingerprints map from JSON.
    .OUTPUTS
        Hashtable of [string]$Locale -> [hashtable]($Identity -> $Hash).
    #>
    param([string]$Path)

    $resolvedPath = Get-LocaleSourceFingerprintsPath -Path $Path
    $result = @{}
    if (-not (Test-Path -LiteralPath $resolvedPath)) {
        return $result
    }

    $raw = Get-Content -LiteralPath $resolvedPath -Raw -Encoding UTF8
    if ([string]::IsNullOrWhiteSpace($raw)) {
        return $result
    }

    try {
        $json = $raw | ConvertFrom-Json -AsHashtable
        if ($json -is [hashtable]) {
            foreach ($loc in $json.Keys) {
                $subMap = @{}
                if ($json[$loc] -is [hashtable]) {
                    foreach ($id in $json[$loc].Keys) {
                        $subMap[[string]$id] = [string]$json[$loc][$id]
                    }
                }
                $result[[string]$loc] = $subMap
            }
        }
    } catch {
        Write-Warning "locale-fingerprints: failed to parse $resolvedPath - returning empty map: $_"
    }

    return $result
}

function Save-LocaleSourceFingerprints {
    <#
    .SYNOPSIS
        Saves the locale source fingerprints map to JSON in deterministic sorted order.
    #>
    param(
        [Parameter(Mandatory = $true)][hashtable]$Fingerprints,
        [string]$Path
    )

    $resolvedPath = Get-LocaleSourceFingerprintsPath -Path $Path
    $dir = Split-Path -Parent $resolvedPath
    if (-not (Test-Path -LiteralPath $dir)) {
        New-Item -ItemType Directory -Path $dir -Force | Out-Null
    }

    $orderedRoot = [ordered]@{}
    foreach ($loc in ($Fingerprints.Keys | Sort-Object)) {
        $sub = $Fingerprints[$loc]
        if ($sub -is [hashtable] -and $sub.Count -gt 0) {
            $orderedSub = [ordered]@{}
            foreach ($id in ($sub.Keys | Sort-Object)) {
                $orderedSub[[string]$id] = [string]$sub[$id]
            }
            $orderedRoot[[string]$loc] = $orderedSub
        }
    }

    $json = $orderedRoot | ConvertTo-Json -Depth 5
    $utf8NoBom = [System.Text.UTF8Encoding]::new($false)
    [System.IO.File]::WriteAllText($resolvedPath, $json + "`n", $utf8NoBom)
}

function Update-LocaleSourceFingerprint {
    <#
    .SYNOPSIS
        Updates or adds an identity fingerprint for a specific locale.
    #>
    param(
        [Parameter(Mandatory = $true)][hashtable]$Fingerprints,
        [Parameter(Mandatory = $true)][string]$Locale,
        [Parameter(Mandatory = $true)][string]$Identity,
        [Parameter(Mandatory = $true)][string]$Hash
    )

    if (-not $Fingerprints.ContainsKey($Locale)) {
        $Fingerprints[$Locale] = @{}
    }
    $Fingerprints[$Locale][$Identity] = $Hash
}

function Remove-LocaleSourceFingerprint {
    <#
    .SYNOPSIS
        Removes an identity fingerprint from a specific locale or all locales.
    #>
    param(
        [Parameter(Mandatory = $true)][hashtable]$Fingerprints,
        [Parameter(Mandatory = $true)][string]$Identity,
        [string]$Locale
    )

    if ($Locale) {
        if ($Fingerprints.ContainsKey($Locale) -and $Fingerprints[$Locale].ContainsKey($Identity)) {
            [void]$Fingerprints[$Locale].Remove($Identity)
        }
    } else {
        foreach ($loc in $Fingerprints.Keys) {
            if ($Fingerprints[$loc].ContainsKey($Identity)) {
                [void]$Fingerprints[$loc].Remove($Identity)
            }
        }
    }
}

function Rename-LocaleSourceFingerprint {
    <#
    .SYNOPSIS
        Renames an identity across all locales in the store.
    #>
    param(
        [Parameter(Mandatory = $true)][hashtable]$Fingerprints,
        [Parameter(Mandatory = $true)][string]$OldIdentity,
        [Parameter(Mandatory = $true)][string]$NewIdentity
    )

    foreach ($loc in $Fingerprints.Keys) {
        if ($Fingerprints[$loc].ContainsKey($OldIdentity)) {
            $val = $Fingerprints[$loc][$OldIdentity]
            [void]$Fingerprints[$loc].Remove($OldIdentity)
            $Fingerprints[$loc][$NewIdentity] = $val
        }
    }
}
