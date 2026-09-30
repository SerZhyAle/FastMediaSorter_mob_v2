<#
.SYNOPSIS
    Decides whether the standard device build must rebuild from scratch, and recognises the launch
    crash that an incremental build can leave behind.

.DESCRIPTION
    S3510: build-standard-device.ps1 used to pass the S3094 reuse-disabling flags on every run, so
    every device build executed 49 of 49 tasks (4m 51s measured 2026-09-18). The crash those flags
    prevent - a Hilt-aggregated component paired with a consumer from another build - needs a stale
    Hilt graph, and the graph changes only when a Hilt declaration or the build itself changes.

    So the builder keeps a snapshot of the tree it last built successfully and compares:
      * a build file whose hash moved                                     -> Full
      * a source file whose Hilt signature moved (edited, added, removed) -> Full
      * no snapshot, or a snapshot of another schema                      -> Full
      * anything else                                                      -> Incremental

    A file's Hilt signature is the hash of its package line, its Hilt-marker lines and its class
    declaration lines, and is empty for a file carrying no marker. A body edit therefore leaves it
    unchanged, while a new entry point, a new binding, a moved package or a changed supertype does
    not. What the signature cannot see - a constructor parameter on a following line - is covered by
    the second half: after launch the builder reads logcat for the S3094 ClassCastException and
    rebuilds from scratch once when it appears.

    The snapshot lives in app_v2/build/ so that `clean`, which invalidates every reused output,
    also invalidates the snapshot.

    Dot-source this file; it defines functions and never exits.
#>

$script:DeviceBuildStateSchema = 1

$script:DeviceBuildHiltMarkerPattern =
'@(AndroidEntryPoint|HiltAndroidApp|HiltViewModel|HiltWorker|Module|InstallIn|Provides|Binds|EntryPoint|Inject|AssistedInject|AssistedFactory)\b'

$script:DeviceBuildDeclarationPattern =
'^\s*(?:(?:public|internal|private|protected|abstract|open|sealed|data|inner|enum|annotation|value|fun)\s+)*(?:class|object|interface)\s+\w'

# Source sets that never reach assembleStandardDebug: a Hilt test module edited there must not cost
# a full rebuild of the app.
$script:DeviceBuildExcludedSourceSetPattern = '^(test|androidTest|benchmark)'

function Get-DeviceBuildStatePath {
    param([Parameter(Mandatory)][string]$ProjectRoot)
    return (Join-Path $ProjectRoot 'app_v2/build/fms-device-build-state.json')
}

function Get-DeviceBuildHiltSignature {
    <#
    .SYNOPSIS
        The Hilt signature of one source file: empty when it carries no marker.
    #>
    param([AllowNull()][AllowEmptyCollection()][string[]]$Lines)

    $hasMarker = $false
    foreach ($line in $Lines) {
        if ($line -match $script:DeviceBuildHiltMarkerPattern) { $hasMarker = $true; break }
    }
    if (-not $hasMarker) { return '' }

    $kept = foreach ($line in $Lines) {
        if ($line -match '^\s*package\s' -or
            $line -match $script:DeviceBuildHiltMarkerPattern -or
            $line -match $script:DeviceBuildDeclarationPattern) {
            $line.Trim()
        }
    }
    $bytes = [System.Text.Encoding]::UTF8.GetBytes(($kept -join "`n"))
    $hash = [System.Security.Cryptography.SHA256]::HashData($bytes)
    return ([System.Convert]::ToHexString($hash)).Substring(0, 32)
}

function Get-DeviceBuildBuildFiles {
    param([Parameter(Mandatory)][string]$ProjectRoot)

    $candidates = @()
    foreach ($dir in @('', 'app_v2', 'gradle')) {
        $full = if ($dir) { Join-Path $ProjectRoot $dir } else { $ProjectRoot }
        if (Test-Path -LiteralPath $full) {
            $candidates += @(Get-ChildItem -LiteralPath $full -File -Filter '*.gradle.kts')
        }
    }
    foreach ($rel in @('gradle.properties', 'gradle/libs.versions.toml', 'gradle/wrapper/gradle-wrapper.properties')) {
        $full = Join-Path $ProjectRoot $rel
        if (Test-Path -LiteralPath $full) { $candidates += Get-Item -LiteralPath $full }
    }
    return $candidates
}

function ConvertTo-DeviceBuildRelativePath {
    param([string]$ProjectRoot, [string]$Path)
    return ([System.IO.Path]::GetRelativePath($ProjectRoot, $Path)) -replace '\\', '/'
}

function Get-DeviceBuildSourceSnapshot {
    <#
    .SYNOPSIS
        Snapshot of the build files and the app_v2 sources, reusing the previous signature of every
        source file whose size and write time are unchanged.
    #>
    param(
        [Parameter(Mandatory)][string]$ProjectRoot,
        [AllowNull()][System.Collections.IDictionary]$Previous
    )

    $previousSources = $null
    if ($Previous -and $Previous['schema'] -eq $script:DeviceBuildStateSchema) {
        $previousSources = $Previous['sources']
    }

    $build = [ordered]@{}
    foreach ($file in (Get-DeviceBuildBuildFiles -ProjectRoot $ProjectRoot)) {
        $rel = ConvertTo-DeviceBuildRelativePath -ProjectRoot $ProjectRoot -Path $file.FullName
        $build[$rel] = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash
    }

    $sources = [ordered]@{}
    $srcRoot = Join-Path $ProjectRoot 'app_v2/src'
    if (Test-Path -LiteralPath $srcRoot) {
        $sets = Get-ChildItem -LiteralPath $srcRoot -Directory |
            Where-Object { $_.Name -notmatch $script:DeviceBuildExcludedSourceSetPattern }
        foreach ($set in $sets) {
            $files = Get-ChildItem -LiteralPath $set.FullName -Recurse -File |
                Where-Object { $_.Extension -eq '.kt' -or $_.Extension -eq '.java' }
            foreach ($file in $files) {
                $rel = ConvertTo-DeviceBuildRelativePath -ProjectRoot $ProjectRoot -Path $file.FullName
                $length = $file.Length
                $ticks = $file.LastWriteTimeUtc.Ticks
                $old = if ($previousSources) { $previousSources[$rel] } else { $null }
                $signature = if ($old -and $old['length'] -eq $length -and $old['ticks'] -eq $ticks) {
                    [string]$old['signature']
                } else {
                    Get-DeviceBuildHiltSignature -Lines (Get-Content -LiteralPath $file.FullName)
                }
                $sources[$rel] = [ordered]@{ length = $length; ticks = $ticks; signature = $signature }
            }
        }
    }

    return [ordered]@{ schema = $script:DeviceBuildStateSchema; build = $build; sources = $sources }
}

function Read-DeviceBuildState {
    <#
    .SYNOPSIS
        The snapshot of the last successful device build, or $null when there is none to trust.
    #>
    param([Parameter(Mandatory)][string]$Path)

    if (-not (Test-Path -LiteralPath $Path)) { return $null }
    try {
        return (Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json -AsHashtable)
    }
    catch {
        # A torn or hand-edited file is not a reason to fail the build: returning nothing forces
        # the one mode that needs no snapshot, which is the safe one.
        Write-Warning "device-build-mode: unreadable state $Path ($($_.Exception.Message)); rebuilding from scratch."
        return $null
    }
}

function Save-DeviceBuildState {
    param(
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)][System.Collections.IDictionary]$Snapshot
    )

    $parent = Split-Path -Parent $Path
    if (-not (Test-Path -LiteralPath $parent)) { New-Item -ItemType Directory -Path $parent -Force | Out-Null }
    $Snapshot | ConvertTo-Json -Depth 5 -Compress | Set-Content -LiteralPath $Path -Encoding utf8
}

function Format-DeviceBuildPathList {
    param([string[]]$Paths, [int]$Show = 3)
    $head = @($Paths | Select-Object -First $Show) -join ', '
    if ($Paths.Count -gt $Show) { $head += " (+$($Paths.Count - $Show) more)" }
    return $head
}

function Get-DeviceBuildMode {
    <#
    .SYNOPSIS
        Full or Incremental, with the reasons that decided it.
    #>
    param(
        [AllowNull()][System.Collections.IDictionary]$Previous,
        [Parameter(Mandatory)][System.Collections.IDictionary]$Current,
        [switch]$ForceFull
    )

    $reasons = [System.Collections.Generic.List[string]]::new()
    if ($ForceFull) { $reasons.Add('-Full was passed') }

    if (-not $Previous) {
        $reasons.Add('no snapshot of a previous device build')
    } elseif ($Previous['schema'] -ne $Current['schema']) {
        $reasons.Add("snapshot schema $($Previous['schema']) is not $($Current['schema'])")
    } else {
        $oldBuild = $Previous['build']
        $newBuild = $Current['build']
        $buildChanged = @(@($oldBuild.Keys) + @($newBuild.Keys) | Sort-Object -Unique |
            Where-Object { $oldBuild[$_] -ne $newBuild[$_] })
        if ($buildChanged.Count -gt 0) {
            $reasons.Add("build files changed: $(Format-DeviceBuildPathList $buildChanged)")
        }

        $oldSources = $Previous['sources']
        $newSources = $Current['sources']
        $hiltChanged = [System.Collections.Generic.List[string]]::new()
        foreach ($key in $newSources.Keys) {
            $newSig = [string]$newSources[$key]['signature']
            $oldEntry = $oldSources[$key]
            $oldSig = if ($oldEntry) { [string]$oldEntry['signature'] } else { '' }
            if ($newSig -ne $oldSig) { $hiltChanged.Add($key) }
        }
        foreach ($key in $oldSources.Keys) {
            if (-not $newSources.Contains($key) -and [string]$oldSources[$key]['signature']) {
                $hiltChanged.Add($key)
            }
        }
        if ($hiltChanged.Count -gt 0) {
            $reasons.Add("Hilt declarations changed: $(Format-DeviceBuildPathList $hiltChanged.ToArray())")
        }
    }

    $mode = if ($reasons.Count -gt 0) { 'Full' } else { 'Incremental' }
    return [pscustomobject]@{ Mode = $mode; Reasons = $reasons.ToArray() }
}

function Test-StaleHiltLaunchCrash {
    <#
    .SYNOPSIS
        True when logcat carries the S3094 crash: a Hilt component cast to an injector it does not
        implement.
    #>
    param([AllowNull()][AllowEmptyString()][AllowEmptyCollection()][string[]]$Lines)

    foreach ($line in $Lines) {
        if ($line -match 'ClassCastException' -and $line -match '(_HiltComponents_|_GeneratedInjector)') {
            return $true
        }
    }
    return $false
}

function Wait-DeviceLaunchOutcome {
    <#
    .SYNOPSIS
        Polls logcat after a launch until it shows the S3094 crash, a displayed activity, or the
        timeout: StaleHiltCrash | Displayed | NoSignal.

    .DESCRIPTION
        `am start` returns when the intent is dispatched, so the crash, when it comes, arrives a
        moment later. The reader is injected so the poll can be driven without a device.
    #>
    param(
        [Parameter(Mandatory)][scriptblock]$ReadLog,
        [Parameter(Mandatory)][string]$Package,
        [int]$TimeoutSeconds = 10,
        [double]$PollSeconds = 1
    )

    $displayed = 'Displayed\s+' + [regex]::Escape($Package) + '/'
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        $lines = @(& $ReadLog)
        if (Test-StaleHiltLaunchCrash -Lines $lines) { return 'StaleHiltCrash' }
        if (@($lines -match $displayed).Count -gt 0) { return 'Displayed' }
        if ($PollSeconds -gt 0) { Start-Sleep -Milliseconds ([int]($PollSeconds * 1000)) }
    } while ([DateTime]::UtcNow -lt $deadline)
    return 'NoSignal'
}
