#requires -Version 7.0
<#
.SYNOPSIS
    Single home of "which built file did the caller mean" - resolves one APK/AAB out of a variant
    output directory, by ABI when asked, and refuses to guess when it cannot tell.

.DESCRIPTION
    Dot-source this file to get Find-BuildArtifact. No top-level side effects, no exit, no
    preference variables assigned - a caller's own error mode is left alone.

    The block this replaces was written out by hand in roughly 33 scripts (S1972) in two shapes,
    and both of them break the moment a variant emits more than one output:

      $meta.elements[0].outputFile                                   <- index 0 means nothing
      Get-ChildItem *.apk | Sort-Object LastWriteTime | Select -First <- picks by clock

    Neither reports anything when it picks wrong. With splits disabled there is exactly one element,
    so both happen to be correct today and stop being correct silently - the wrong architecture goes
    onto a device, the wrong asset goes into a release, and nothing says so.

    Selection. When output-metadata.json is present and parses, elements are chosen from it:
    -Abi <name> matches the element whose filters[] carries {filterType: "ABI", value: <name>};
    -Abi universal matches the element whose type is "UNIVERSAL" (its filters[] is empty, so it can
    never be returned for a request that named a concrete architecture). With no -Abi and exactly
    one element, that element is returned - which is every build in this repository while
    splits.abi stays off, so migrating a caller changes nothing until splits are turned on.

    A single non-split element (type SINGLE) answers every request, ABI-named ones included: it is
    the build's only output, it carries whatever ABIs ndk.abiFilters allowed, and AGP records no
    filters[] to test a request against. That is what lets an installer hand over the connected
    device's ABI unconditionally instead of first asking whether this build happens to be split.
    A directory glob is used only when the metadata is absent or unparsable, and only when it finds
    exactly one file. File modification time is never a tiebreak.

    Two outcomes, deliberately different, because they are different answers:

      $null   nothing was built - the directory is missing, or holds no artifact at all. This is the
              shape every pre-S1972 caller already handles with its own "not found" guard, so an
              existing `if (-not $path) { exit 1 }` stays correct after migration.
      throw   something was built, but the question has no single answer - several candidates with
              no selector separating them, or a named ABI this build does not contain. The message
              names the directory and lists what is actually there.

    An AAB is resolved by glob on purpose: AGP writes no output-metadata.json beside a bundle.

.PARAMETER Dir
    The variant output directory, e.g. app_v2/build/outputs/apk/standard/debug.

.PARAMETER Abi
    Architecture to select: an ABI name as AGP spells it (arm64-v8a, x86_64, armeabi-v7a, x86), or
    the literal 'universal' for the all-architecture output. Omit it when the build is known to emit
    a single output; omitting it against a split build is an error, not a default.

.PARAMETER Extension
    'apk' (default) or 'aab'.

.EXAMPLE
    . "$PSScriptRoot/../utils/find-build-artifact.ps1"
    $apk = Find-BuildArtifact -Dir $apkDir -Abi (& adb shell getprop ro.product.cpu.abi).Trim()
    if (-not $apk) { Write-Host "Error: APK not found in $apkDir"; exit 1 }

.NOTES
    Exit codes: none. This file defines functions and returns nothing; it is dot-sourced, never
    invoked as a script.
#>

function Resolve-SingleArtifactFile {
    <#
    .SYNOPSIS
        Glob fallback for directories AGP leaves without usable metadata.
    .OUTPUTS
        FileInfo, or $null when the directory holds no match. Throws when it holds several.
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)] [string] $Dir,
        [Parameter(Mandatory)] [string] $Pattern
    )

    $found = @(Get-ChildItem -LiteralPath $Dir -Filter $Pattern -File -ErrorAction SilentlyContinue)

    if ($found.Count -eq 0) { return $null }
    if ($found.Count -eq 1) { return $found[0] }

    $names = ($found | Sort-Object Name | ForEach-Object { $_.Name }) -join ', '
    throw ("Ambiguous artifact in ${Dir}: $($found.Count) files match '$Pattern' and there is no " +
        "output-metadata.json to tell them apart ($names). Pass -Abi, or clean the directory. " +
        "Picking the newest file here is what S1972 removed - it selects an architecture at random.")
}

function Select-ArtifactElement {
    <#
    .SYNOPSIS
        Choose one element of an output-metadata.json document.
    .OUTPUTS
        The element, or $null when the document lists none. Throws when the request is ambiguous
        or names an ABI this build does not carry.
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)] [AllowEmptyCollection()] [array] $Elements,
        [Parameter(Mandatory)] [string] $Dir,
        [string] $Abi
    )

    if ($Elements.Count -eq 0) { return $null }

    # A build that emitted a single non-split element emitted one APK carrying every ABI its
    # ndk.abiFilters allowed, and AGP writes no filters[] beside it - so there is nothing to check a
    # named -Abi against, and one candidate cannot be ambiguous. That element therefore answers every
    # request, including 'universal'. This is what lets a caller pass the connected device's ABI
    # unconditionally and still behave exactly as it did before S1972 while splits.abi stays off;
    # without it, migrating an installer would turn today's successful install into a throw.
    if ($Elements.Count -eq 1 -and $Elements[0].type -ne 'ONE_OF_MANY') { return $Elements[0] }

    # What this build actually offers, for every message below. A UNIVERSAL element carries no ABI
    # filter, so it is listed under its own name rather than being reported as an architecture.
    $offered = @($Elements | ForEach-Object {
            if ($_.type -eq 'UNIVERSAL') { 'universal' }
            else { @($_.filters | Where-Object { $_.filterType -eq 'ABI' } | ForEach-Object { $_.value }) }
        } | Where-Object { $_ } | Sort-Object -Unique)

    if ($Abi -eq 'universal') {
        $universal = @($Elements | Where-Object { $_.type -eq 'UNIVERSAL' })
        if ($universal.Count -eq 1) { return $universal[0] }
        if ($universal.Count -eq 0) {
            throw ("No universal output in ${Dir}. This build carries: $($offered -join ', '). " +
                'A universal APK exists only when splits.abi sets isUniversalApk = true.')
        }
        throw "Malformed output-metadata.json in ${Dir}: $($universal.Count) UNIVERSAL elements."
    }

    if ($Abi) {
        $matched = @($Elements | Where-Object {
                $_.type -ne 'UNIVERSAL' -and
                @($_.filters | Where-Object { $_.filterType -eq 'ABI' -and $_.value -eq $Abi }).Count -gt 0
            })
        if ($matched.Count -eq 1) { return $matched[0] }
        if ($matched.Count -eq 0) {
            # Loud rather than $null: the directory is not empty, so "not found" would send the
            # caller down its "run the builder first" path when the real answer is that this build
            # was made for other architectures.
            throw ("No '$Abi' output in ${Dir}. This build carries: $($offered -join ', '). " +
                'Rebuild for that architecture, or ask for one of the above.')
        }
        throw "Malformed output-metadata.json in ${Dir}: $($matched.Count) elements claim ABI '$Abi'."
    }

    if ($Elements.Count -eq 1) { return $Elements[0] }

    throw ("Ambiguous artifact in ${Dir}: the build emitted $($Elements.Count) outputs " +
        "($($offered -join ', ')) and no -Abi was given. Name the architecture you want, or " +
        "'universal'. This is the pick S1972 stopped making by index.")
}

function Find-BuildArtifact {
    <#
    .SYNOPSIS
        Resolve one built APK/AAB from a variant output directory.
    .OUTPUTS
        FileInfo for the resolved artifact, or $null when nothing was built. Throws when the
        request cannot be answered unambiguously - see this file's .DESCRIPTION.
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)] [string] $Dir,
        [string] $Abi,
        [ValidateSet('apk', 'aab')] [string] $Extension = 'apk'
    )

    if (-not (Test-Path -LiteralPath $Dir)) { return $null }

    # AGP writes no output-metadata.json beside a bundle, so the glob is the only route for an AAB.
    if ($Extension -eq 'aab') {
        return Resolve-SingleArtifactFile -Dir $Dir -Pattern '*.aab'
    }

    $metadataPath = Join-Path $Dir 'output-metadata.json'
    if (Test-Path -LiteralPath $metadataPath) {
        $meta = $null
        try {
            $meta = Get-Content -LiteralPath $metadataPath -Raw | ConvertFrom-Json
        }
        catch {
            # Unparsable metadata is a corrupt-or-partial write, not an answer. Fall through to the
            # glob, which refuses on its own when it cannot tell the candidates apart.
            Write-Verbose "find-build-artifact: cannot parse $metadataPath - falling back to glob."
            $meta = $null
        }

        if ($null -ne $meta) {
            $element = Select-ArtifactElement -Elements @($meta.elements) -Dir $Dir -Abi $Abi
            if ($null -ne $element -and $element.outputFile) {
                $candidate = Join-Path $Dir $element.outputFile
                if (Test-Path -LiteralPath $candidate) { return Get-Item -LiteralPath $candidate }
                # Metadata naming a file that is gone means the directory was cleaned under us.
                Write-Verbose "find-build-artifact: $metadataPath names a missing $($element.outputFile)."
                return $null
            }
        }
    }

    return Resolve-SingleArtifactFile -Dir $Dir -Pattern "*.$Extension"
}
