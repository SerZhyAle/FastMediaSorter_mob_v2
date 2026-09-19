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

    S3304: provenance alone cannot tell a translation from a copy of the English source, because a
    stamp answers the question "which English text was this written against" and never "is this text
    English". The two members added here answer the second question - Get-LocaleFileUnitValues reads
    a locale file's actual values, and the locale-identical allow-list names the keys entitled to
    equal their English source (brand names, acronyms, units, pure format tokens). Both are consumed
    by list-new-lexemes.ps1 and seed-locale-tranche.ps1; neither is stored in the fingerprint
    registry, so an allow-list entry is a reviewable line in a diff rather than a flag buried in a
    4 MB generated document.

    S3310: both stores are replaced through Save-LocaleStoreFileAtomic, which retries a denied move
    and removes its temporary on every failing path. Never write a store with a bare
    WriteAllText-plus-Move pair again - that is the shape that left a resource value on disk under
    its old fingerprint and a gitignored temporary beside the store.
#>

Set-StrictMode -Version Latest

$script:LocaleFingerprintsDefaultPath = Join-Path (Split-Path -Parent (Split-Path -Parent $PSScriptRoot)) 'quality/locale-source-fingerprints.json'
$script:LocaleIdenticalAllowlistDefaultPath = Join-Path (Split-Path -Parent (Split-Path -Parent $PSScriptRoot)) 'quality/locale-identical-allowlist.json'

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

function Get-LocaleIdenticalAllowlistPath {
    param([string]$Path)
    if ($Path) { return $Path }
    return $script:LocaleIdenticalAllowlistDefaultPath
}

function Clear-LocaleStoreOrphanedTemp {
    <#
    .SYNOPSIS
        S3310: removes stale "<store>.<pid>.tmp" debris left beside a store by a killed writer.
    .DESCRIPTION
        Save-LocaleStoreFileAtomic tidies up after itself, but a process killed between its write and
        its move cannot. Such a temporary matches *.tmp in .gitignore, so it never reaches a diff -
        two were found sitting in scripts/quality/ at the start of the S3305 batch, one of them left
        by an earlier session. A live writer's temporary exists for milliseconds, so an hour of age
        sits far outside any window in which one could still be in use, this process's own included.
    #>
    param(
        [Parameter(Mandatory = $true)][string]$TargetPath,
        [int]$OlderThanMinutes = 60
    )

    $dir = Split-Path -Parent $TargetPath
    if (-not $dir -or -not (Test-Path -LiteralPath $dir)) { return }

    $cutoff = (Get-Date).AddMinutes(-$OlderThanMinutes)
    $pattern = (Split-Path -Leaf $TargetPath) + '.*.tmp'
    foreach ($stale in @(Get-ChildItem -LiteralPath $dir -Filter $pattern -File | Where-Object { $_.LastWriteTime -lt $cutoff })) {
        try {
            Remove-Item -LiteralPath $stale.FullName -Force
        } catch {
            # The save this ran after already succeeded, so failing to tidy must not turn a written
            # document into an error the caller has to handle - it is reported and dropped.
            Write-Warning "locale-fingerprints: could not remove orphaned temporary $($stale.FullName) - $_"
        }
    }
}

function Save-LocaleStoreFileAtomic {
    <#
    .SYNOPSIS
        S3310: writes one store document beside its target and replaces the target with it.
    .DESCRIPTION
        Both savers in this library go through here, so the S3008 temp-file-plus-move discipline has
        one implementation and one failure story.

        [System.IO.File]::Move(.., $true) fails with "Access to the path is denied" while another
        process holds the DESTINATION open without FILE_SHARE_DELETE - a scanner or an indexer
        re-reading the 4.7 MB store it has just seen change. Get-LocaleFingerprintsMutexName cannot
        prevent that, because the holder is not a writer of this project: the S3305 batch was
        strictly sequential in a MONO run and one of its 199 saves still threw. That is why the cure
        is a bounded retry rather than more locking.

        Two consequences of that throw went unhandled, and they are what this function exists for.
        The temporary was orphaned, silently, because *.tmp is gitignored. And the caller had already
        written the resource value, leaving a NEW translation under its OLD fingerprint - precisely
        the state list-new-lexemes.ps1 reports as stale. The finally block removes the temporary on
        every path, and the final error names that half-landed state rather than leaving the operator
        to infer it from an exit code.
    #>
    param(
        [Parameter(Mandatory = $true)][string]$TargetPath,
        [Parameter(Mandatory = $true)][AllowEmptyString()][string]$Content,
        [int]$Attempts = 5,
        [int]$InitialDelayMs = 50
    )

    $utf8NoBom = [System.Text.UTF8Encoding]::new($false)
    # The pid in the name keeps two writers' temporaries apart even where a mutex already orders them.
    $tempPath = "$TargetPath.$PID.tmp"
    try {
        [System.IO.File]::WriteAllText($tempPath, $Content, $utf8NoBom)

        $delayMs = $InitialDelayMs
        for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
            try {
                [System.IO.File]::Move($tempPath, $TargetPath, $true)
                Clear-LocaleStoreOrphanedTemp -TargetPath $TargetPath
                return
            } catch [System.IO.IOException], [System.UnauthorizedAccessException] {
                if ($attempt -eq $Attempts) {
                    throw ("locale-fingerprints: could not replace $TargetPath after $Attempts attempt(s) - " +
                        "$($_.Exception.Message) Another process is holding the destination open. The temporary " +
                        "has been removed, so the store still holds its previous contents - but anything the " +
                        "caller wrote just before this call, a resource value above all, is now on disk WITHOUT " +
                        "its fingerprint. Re-run the same call to stamp it.")
                }
                Start-Sleep -Milliseconds $delayMs
                $delayMs = $delayMs * 2
            }
        }
    }
    finally {
        # Debris from a failed write or a failed move; a successful move consumed it. Removing it must
        # never mask the real failure, so a second error here is reported and dropped, not thrown over
        # the first.
        if (Test-Path -LiteralPath $tempPath) {
            try { Remove-Item -LiteralPath $tempPath -Force }
            catch { Write-Warning "locale-fingerprints: could not remove the temporary $tempPath - $_" }
        }
    }
}

function New-LocaleIdenticalAllowlistScope {
    <#
    .SYNOPSIS
        Builds the locale scope of one allow-list entry from whatever the caller has.
    .DESCRIPTION
        S3309: an entry entitles a key in EVERY locale ($null) or in a named set. An empty or absent
        locale list is read as "every locale" rather than as "no locale", because that is the shape a
        hand-edited object degrades to and the strict reading would silently retire the entry.
    .OUTPUTS
        $null for an unscoped entry, otherwise HashSet[string] of locale tags.
    #>
    param($Locales)

    if ($null -eq $Locales) { return $null }
    $tags = @($Locales | ForEach-Object { [string]$_ } | Where-Object { $_.Trim() } | ForEach-Object { $_.Trim() })
    if ($tags.Count -eq 0) { return $null }

    $set = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
    foreach ($tag in $tags) { [void]$set.Add($tag) }
    return , $set
}

function Get-LocaleIdenticalAllowlist {
    <#
    .SYNOPSIS
        Loads the checked-in allow-list of keys allowed to be English-identical.
    .DESCRIPTION
        S3309: an entry is a bare key name, entitling every locale, or an object naming the entitled
        ones - {"key": "camera_mode_photo", "locales": ["fr", "it"]}. The property is per (key,
        locale): a key can be a legitimate identical in French and an untranslated leftover in
        Arabic, and a key-level list is wrong for one of the two groups it covers.
    .OUTPUTS
        OrderedDictionary of key name -> $null (every locale) or HashSet[string] of locale tags.
    #>
    param([string]$Path)

    # S3306: the comma operator on every return path. PowerShell unrolls an enumerable on return, so
    # an EMPTY container arrives at the caller as $null - which is the normal case, because the
    # allow-list file is optional. Every caller then passes $null into the mandatory -Allowlist and
    # dies on the binding, not on anything it was testing.
    $resolvedPath = Get-LocaleIdenticalAllowlistPath -Path $Path
    $map = [ordered]@{}
    if (-not (Test-Path -LiteralPath $resolvedPath)) {
        return , $map
    }

    $raw = Get-Content -LiteralPath $resolvedPath -Raw -Encoding UTF8
    if ([string]::IsNullOrWhiteSpace($raw)) {
        return , $map
    }

    try {
        # S3304: @() rather than an -is [array] test. ConvertFrom-Json unrolls, so a one-key
        # allow-list arrives as a bare String, the branch never runs and the single exemption is
        # silently dropped - which reads exactly like the key not being listed at all. Measured on a
        # fixture holding ["s3304_brand"]: the key was omitted from the seeded locale file although
        # it was the only thing on the list.
        foreach ($item in @($raw | ConvertFrom-Json)) {
            if ($item -is [string]) {
                if ($item) { $map[$item] = $null }
                continue
            }
            if ($null -eq $item) { continue }
            # Property access through PSObject: the library runs under Set-StrictMode -Version
            # Latest, where naming a property a hand-edited object does not carry throws instead of
            # yielding $null, and a malformed entry must degrade, not kill the run.
            $keyProp = $item.PSObject.Properties['key']
            $name = if ($keyProp) { [string]$keyProp.Value } else { '' }
            if (-not $name) { continue }
            $localesProp = $item.PSObject.Properties['locales']
            $scope = New-LocaleIdenticalAllowlistScope -Locales $(if ($localesProp) { $localesProp.Value } else { $null })
            # A key repeated across entries keeps the widest scope it was given anywhere: two entries
            # for one key are a hand-edit accident, and narrowing on the second would drop an
            # entitlement the first one granted.
            if ($map.Contains($name) -and $null -eq $map[$name]) { continue }
            if ($map.Contains($name) -and $null -ne $scope) {
                foreach ($tag in $map[$name]) { [void]$scope.Add($tag) }
            }
            $map[$name] = $scope
        }
    } catch {
        Write-Warning "locale-fingerprints: failed to parse allowlist at $resolvedPath - returning empty set: $_"
        $map = [ordered]@{}
    }

    return , $map
}

function Save-LocaleIdenticalAllowlist {
    <#
    .SYNOPSIS
        Saves the allow-list of English-identical keys to JSON in sorted order.
    .DESCRIPTION
        Accepts what Get-LocaleIdenticalAllowlist returns, and also a plain list of key names, which
        saves as unscoped entries. An unscoped entry is written as a bare string so the file stays
        readable and diffable for the majority of entries that need no scope.
    #>
    param(
        [Parameter(Mandatory = $true)]$Allowlist,
        [string]$Path
    )

    $resolvedPath = Get-LocaleIdenticalAllowlistPath -Path $Path
    $dir = Split-Path -Parent $resolvedPath
    if (-not (Test-Path -LiteralPath $dir)) {
        New-Item -ItemType Directory -Path $dir -Force | Out-Null
    }

    $scopeByKey = [ordered]@{}
    if ($Allowlist -is [System.Collections.IDictionary]) {
        foreach ($name in $Allowlist.Keys) { $scopeByKey[[string]$name] = $Allowlist[$name] }
    } else {
        foreach ($name in @($Allowlist)) {
            if ($name -is [string] -and $name) { $scopeByKey[$name] = $null }
        }
    }

    $entries = foreach ($name in @($scopeByKey.Keys | Sort-Object -Unique)) {
        $scope = $scopeByKey[$name]
        if ($null -eq $scope) { $name }
        else { [pscustomobject]@{ key = $name; locales = @($scope | Sort-Object) } }
    }

    # -AsArray, not -Depth alone: ConvertTo-Json emits a bare scalar for a one-element collection, so
    # a single-entry allow-list would be written as a string and read back as nothing.
    $json = @($entries) | ConvertTo-Json -Depth 3 -AsArray
    Save-LocaleStoreFileAtomic -TargetPath $resolvedPath -Content ($json + "`n")
}

function Test-LocaleIdenticalAllowlistHasKey {
    <#
    .SYNOPSIS
        Checks if the allow-list mentions a key at all, whatever the entry's locale scope.
    .DESCRIPTION
        For the reviewer's dump, which lists every entry and prints its scope as evidence. Judging
        whether a VALUE is entitled is Test-LocaleIdenticalAllowlisted's job and needs a locale.
    #>
    param(
        [Parameter(Mandatory = $true)][string]$Key,
        [Parameter(Mandatory = $true)]$Allowlist
    )

    if ($null -eq $Allowlist) { return $false }
    if ($Allowlist -is [System.Collections.IDictionary]) { return $Allowlist.Contains($Key) }
    return $Allowlist.Contains($Key)
}

function Test-LocaleIdenticalAllowlisted {
    <#
    .SYNOPSIS
        Checks if a key is allowed to equal its English source IN ONE LOCALE.
    .DESCRIPTION
        S3309: -Locale is mandatory on purpose. The predicate used to answer per key, which is wrong
        for 517 of the 646 allow-listed units measured on 2026-09-19 - some locales translated them,
        others carry the English verbatim. An optional locale would let a call site keep the old
        key-level answer by omitting one argument, which is exactly the regression this shape exists
        to make impossible: a forgotten locale now fails parameter binding instead.
    #>
    param(
        [Parameter(Mandatory = $true)][string]$Key,
        [Parameter(Mandatory = $true)][string]$Locale,
        [Parameter(Mandatory = $true)]$Allowlist
    )

    if ($null -eq $Allowlist) { return $false }
    if (-not ($Allowlist -is [System.Collections.IDictionary])) {
        # A legacy key-level container (a HashSet of names) carries no scope, so every locale it
        # names is entitled - the pre-S3309 reading, kept so a caller holding one is not silently
        # told "no".
        return $Allowlist.Contains($Key)
    }
    if (-not $Allowlist.Contains($Key)) { return $false }

    $scope = $Allowlist[$Key]
    if ($null -eq $scope) { return $true }
    return $scope.Contains($Locale)
}

# The element grammar of a resource file, matched here exactly as locale-bulk-export.ps1 matches the
# English source - a unit is a <string>, one <item quantity=".."> of a <plurals>, or one positional
# <item> of a <string-array>. Reading a locale file with a different grammar would address slots the
# exporter never numbered, so the value comparison below would silently compare nothing.
$script:LocaleElementRx = [regex]'(?s)<(string|plurals|string-array)\s+name="([^"]+)"([^>]*)>(.*?)</\1>'
$script:LocalePluralItemRx = [regex]'(?s)<item\s+quantity="([^"]+)"[^>]*>(.*?)</item>'
$script:LocaleArrayItemRx = [regex]'(?s)<item[^>]*>(.*?)</item>'

function Get-LocaleUnitSlotKey {
    <#
    .SYNOPSIS
        Addresses one unit inside a single resource file, as "<key>|<slot>".
    .DESCRIPTION
        S3304: narrower than Get-LocaleUnitId, which addresses a unit across the whole repository.
        A value comparison happens inside one file that is already fixed by module, set and file, so
        the identity that indexes it carries only what still varies. The slot is empty for a
        <string> and is the quantity or the zero-based position for the two container kinds, exactly
        as locale-bulk-export.ps1 records it in its sidecar.
    #>
    param(
        [Parameter(Mandatory = $true)][string]$Key,
        [AllowEmptyString()][string]$Slot
    )

    return "$Key|$Slot"
}

function Get-LocaleFileUnitValues {
    <#
    .SYNOPSIS
        S3304: decodes one resource file into slot key -> plain localized text.
    .DESCRIPTION
        The plain text comes out through ConvertFrom-ResourceBody, the same normalizer that produces
        the `en` field every fingerprint is taken from. That is what makes an equality test between
        the two meaningful: comparing raw element bodies instead would call a locale value different
        from English over an escaping difference alone, and equal over a decoded entity.
    .OUTPUTS
        Hashtable of "<key>|<slot>" -> plain text. An absent file yields an empty map.
    #>
    param([Parameter(Mandatory = $true)][string]$Path)

    $values = @{}
    if (-not (Test-Path -LiteralPath $Path)) { return $values }

    $text = Get-Content -LiteralPath $Path -Raw -Encoding UTF8
    foreach ($element in $script:LocaleElementRx.Matches($text)) {
        $kind = $element.Groups[1].Value
        $key = $element.Groups[2].Value
        $body = $element.Groups[4].Value
        switch ($kind) {
            'plurals' {
                foreach ($item in $script:LocalePluralItemRx.Matches($body)) {
                    $slotKey = Get-LocaleUnitSlotKey -Key $key -Slot $item.Groups[1].Value
                    $values[$slotKey] = ConvertFrom-ResourceBody $item.Groups[2].Value
                }
            }
            'string-array' {
                $slot = 0
                foreach ($item in $script:LocaleArrayItemRx.Matches($body)) {
                    $slotKey = Get-LocaleUnitSlotKey -Key $key -Slot ([string]$slot)
                    $values[$slotKey] = ConvertFrom-ResourceBody $item.Groups[1].Value
                    $slot++
                }
            }
            default {
                $values[(Get-LocaleUnitSlotKey -Key $key -Slot '')] = ConvertFrom-ResourceBody $body
            }
        }
    }

    return $values
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
    # S3008: write beside the target and move over it, so a writer killed mid-save leaves either the
    # whole old document or the whole new one. Writing 4.7 MB in place leaves a third state - a
    # truncated file - which Get-LocaleSourceFingerprints parses as empty and every reader then reads
    # as "no locale was ever translated".
    Save-LocaleStoreFileAtomic -TargetPath $resolvedPath -Content ($json + "`n")
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
