# requires -Version 7.0
<#
.SYNOPSIS
    S1824: shared library for English string fingerprinting and translation freshness tracking.

.DESCRIPTION
    Dot-source it; it declares functions and never exits, so it has no exit-code contract.

    Manages the translation provenance mapping in scripts/quality/locale-source-fingerprints.json.
    For each best-effort locale and each resource unit (module|set|file|key or
    module|set|file|key|slot), the store records the 16-character SHA-256 fingerprint of the
    normalized plain English text that was active when that translation was produced or imported.

    When the English text is edited in values/strings*.xml, its fingerprint changes; any locale
    whose recorded fingerprint does not match is considered stale and reported by list-new-lexemes.ps1.

    S1858: the identity carries the module because app_v2 and wear both ship
    src/main/res/values/strings.xml and share 14 key names with different English text. Without the
    module segment they addressed one slot, so whichever module imported last silently overwrote the
    other's provenance and the gate reported the other module's keys as untranslated. Build the
    identity only through Get-LocaleUnitId - five call sites concatenating it by hand is how the
    format drifted in the first place.
#>

Set-StrictMode -Version Latest

$script:LocaleFingerprintsDefaultPath = Join-Path (Split-Path -Parent (Split-Path -Parent $PSScriptRoot)) 'quality/locale-source-fingerprints.json'

# Bumped when the identity format changes. A store written before S1858 is version 1 and its
# unqualified identities cannot be read as module-qualified ones, so readers must refuse it.
$script:LocaleFingerprintsSchemaVersion = 2
$script:LocaleFingerprintsIdentityFormat = 'module|set|file|key[|slot]'
$script:LocaleFingerprintsSchemaKey = '__schema'

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

function ConvertFrom-ResourceBody {
    <#
    .SYNOPSIS
        Decodes a resource element body into the plain English text a fingerprint is taken from.
    .DESCRIPTION
        S2327: the one normalizer for fingerprint input. It lived in locale-bulk-export.ps1, whose
        sidecar field `en` is what every recorded hash is later compared against - so a second
        writer hashing the raw XML body instead would stamp a corpus that reads as stale the moment
        it is written. Entities and AAPT's backslash escapes for quote and apostrophe are dropped
        because the recorded text is text, not resource syntax; \n stays spelled as two characters,
        as the export contract requires.
    #>
    param(
        [AllowEmptyString()]
        [string]$Text
    )

    $plain = $Text -replace '\\([''"])', '$1'
    $plain = $plain.Replace('&apos;', "'").Replace('&quot;', '"').Replace('&amp;', '&')
    return ($plain -replace '[\r\n\t]+', ' ').Trim()
}

function Get-LocaleSourceFingerprintsPath {
    param([string]$Path)
    if ($Path) { return $Path }
    return $script:LocaleFingerprintsDefaultPath
}

function Get-LocaleUnitId {
    <#
    .SYNOPSIS
        Builds the module-qualified identity of one translatable unit.
    .DESCRIPTION
        The only place this format is assembled. -Module is mandatory so a caller cannot omit it and
        silently rebuild the pre-S1858 format, which collided across modules.
    #>
    param(
        [Parameter(Mandatory = $true)][string]$Module,
        [Parameter(Mandatory = $true)][string]$Set,
        [Parameter(Mandatory = $true)][string]$File,
        [Parameter(Mandatory = $true)][string]$Key,
        [string]$Slot
    )

    if ($Slot) { return "$Module|$Set|$File|$Key|$Slot" }
    return "$Module|$Set|$File|$Key"
}

function Get-LocaleFingerprintsSchemaVersion {
    <#
    .SYNOPSIS
        Reads the identity-format version a store on disk declares.
    .DESCRIPTION
        Resolved from the file rather than from a loaded map, so a caller can refuse a superseded
        store before it reads a single identity out of it.
    .OUTPUTS
        [int] the declared version, or 1 when the marker is absent (every store written before S1858).
    #>
    param([string]$Path)

    $resolvedPath = Get-LocaleSourceFingerprintsPath -Path $Path
    if (-not (Test-Path -LiteralPath $resolvedPath)) {
        return $script:LocaleFingerprintsSchemaVersion
    }

    $raw = Get-Content -LiteralPath $resolvedPath -Raw -Encoding UTF8
    if ([string]::IsNullOrWhiteSpace($raw)) {
        return $script:LocaleFingerprintsSchemaVersion
    }

    try {
        $json = $raw | ConvertFrom-Json -AsHashtable
        if ($json -is [hashtable] -and $json.ContainsKey($script:LocaleFingerprintsSchemaKey)) {
            $marker = $json[$script:LocaleFingerprintsSchemaKey]
            if ($marker -is [hashtable] -and $marker.ContainsKey('version')) {
                return [int]$marker['version']
            }
        }
    } catch {
        Write-Warning "locale-fingerprints: failed to parse $resolvedPath while reading its schema version: $_"
    }

    return 1
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
                # Metadata shares the root with the locale tags; no locale tag starts with an
                # underscore, so the prefix keeps the two apart without a second nesting level.
                if ([string]$loc -like '__*') { continue }
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
    $orderedRoot[$script:LocaleFingerprintsSchemaKey] = [ordered]@{
        version  = $script:LocaleFingerprintsSchemaVersion
        identity = $script:LocaleFingerprintsIdentityFormat
    }
    foreach ($loc in ($Fingerprints.Keys | Sort-Object)) {
        if ([string]$loc -like '__*') { continue }
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
    # S3008: write beside the target and move over it, so a writer killed mid-save leaves either the
    # whole old document or the whole new one. Writing 4.7 MB in place leaves a third state - a
    # truncated file - which Get-LocaleSourceFingerprints parses as empty and every reader then reads
    # as "no locale was ever translated". The pid in the name keeps two writers' temporaries apart
    # even though Edit-LocaleSourceFingerprints already orders them.
    $tempPath = "$resolvedPath.$PID.tmp"
    [System.IO.File]::WriteAllText($tempPath, $json + "`n", $utf8NoBom)
    [System.IO.File]::Move($tempPath, $resolvedPath, $true)
}

function Get-LocaleFingerprintsMutexName {
    <#
    .SYNOPSIS
        Names the cross-process mutex guarding one store file.
    #>
    param([Parameter(Mandatory = $true)][string]$Path)

    # Keyed on the store path so the contract suites, which point at scratch files under temp/, never
    # serialize against the shipped registry or against one another. A mutex name cannot carry a path
    # separator, hence the hash. Local\ rather than Global\ because every writer is a process in the
    # same logon session, and the global namespace needs a privilege a plain agent process may lack.
    $full = [System.IO.Path]::GetFullPath($Path).ToLowerInvariant()
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($full)
    $hash = [System.Security.Cryptography.SHA256]::Create().ComputeHash($bytes)
    return 'Local\FMS-locale-fingerprints-' + [System.Convert]::ToHexString($hash).Substring(0, 16).ToLowerInvariant()
}

function Edit-LocaleSourceFingerprints {
    <#
    .SYNOPSIS
        S3008: runs one read-modify-write of the store under a cross-process lock.
    .DESCRIPTION
        The read happens INSIDE the lock, and that is the whole point of this function. Every writer
        used to call Get-LocaleSourceFingerprints, mutate the returned map and call
        Save-LocaleSourceFingerprints, which rewrites the entire document - so a writer that loaded
        the file before another writer's save and saved after it discarded every identity the other
        had added. Both processes exit 0 and both print their own stamp count, so nothing reports the
        loss: on the r37 import round 145 of 146 stamps written for `de` were gone by the time the
        gate re-ran, and the single survivor was the one key stamped from a different source file by
        a separate process. Locking only the save would not have helped, because the stale snapshot
        is formed at the read.

        -Mutate receives the freshly loaded map and edits it in place through the
        Update-/Remove-/Rename-LocaleSourceFingerprint helpers; anything it emits is discarded.
    .OUTPUTS
        The saved map, so a caller can verify its own work without a second read.
    #>
    param(
        [Parameter(Mandatory = $true)][scriptblock]$Mutate,
        [string]$Path,
        [int]$TimeoutSeconds = 120
    )

    $resolvedPath = Get-LocaleSourceFingerprintsPath -Path $Path
    $mutex = [System.Threading.Mutex]::new($false, (Get-LocaleFingerprintsMutexName -Path $resolvedPath))
    $held = $false
    try {
        try {
            $held = $mutex.WaitOne([TimeSpan]::FromSeconds($TimeoutSeconds))
        } catch [System.Threading.AbandonedMutexException] {
            # The previous holder died between acquiring and releasing; ownership passes here. The
            # store is re-read below, and Save- replaces the file atomically, so that process can have
            # left neither a half-applied mutation nor a truncated document.
            $held = $true
        }
        if (-not $held) {
            throw "locale-fingerprints: waited ${TimeoutSeconds}s for another writer of $resolvedPath and gave up."
        }

        $fingerprints = Get-LocaleSourceFingerprints -Path $resolvedPath
        & $Mutate $fingerprints | Out-Null
        Save-LocaleSourceFingerprints -Fingerprints $fingerprints -Path $resolvedPath
        return $fingerprints
    }
    finally {
        if ($held) { $mutex.ReleaseMutex() }
        $mutex.Dispose()
    }
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
