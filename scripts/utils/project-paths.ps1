#requires -Version 7.0
<#
.SYNOPSIS
    Resolve every path a repository script needs without naming a drive letter (S2326).

.DESCRIPTION
    Dot-source this file to get the four resolution roles: the project root, a directory
    beside it, an external tool, and a delivery destination.

    The root is found by walking upward until a directory carries the marker triple -
    settings.gradle.kts, a.ps1 and CLAUDE.md together - rather than by counting `..` from the
    calling script. The count is a property of where a file happens to sit, so it stops being
    true the moment the file moves between subdirectories; the marker is a property of the tree
    and survives both that move and a move of the whole tree to another drive letter or
    directory name (strategic spec S2326 ADR-2).

    The walk starts from this file's own directory, not the caller's, so every caller gets the
    same answer regardless of its depth.

    Exit codes: none - this is a dot-sourced module and never calls `exit`. A failure that a
    caller must not continue past surfaces as a terminating error naming what could not be
    resolved.

.EXAMPLE
    . "$PSScriptRoot\..\utils\project-paths.ps1"
    $root = Get-ProjectRoot
    $apk  = Get-ProjectPath -Relative 'DOWNLOADS/FastMediaSorter_standard_debug.apk'
#>

# The marker triple. All three must sit in the same directory: settings.gradle.kts alone also
# appears in the release worktree beside us, and a.ps1 alone would match a copied launcher.
$script:FmsRootMarkers = @('settings.gradle.kts', 'a.ps1', 'CLAUDE.md')

$script:FmsProjectRoot = $null

function Get-ProjectRoot {
    <#
    .SYNOPSIS
        Absolute path of the project root, found by the marker triple.
    .PARAMETER From
        Directory to start the upward walk from. Defaults to this module's own directory.
    #>
    [CmdletBinding()]
    param(
        [string]$From
    )

    if (-not $From -and $script:FmsProjectRoot) { return $script:FmsProjectRoot }

    $start = if ($From) { $From } else { $PSScriptRoot }
    if (-not (Test-Path -LiteralPath $start)) {
        throw "project-paths: start directory '$start' does not exist."
    }

    $dir = (Resolve-Path -LiteralPath $start).Path
    if (Test-Path -LiteralPath $dir -PathType Leaf) { $dir = Split-Path -Parent $dir }

    while ($dir) {
        $complete = $true
        foreach ($marker in $script:FmsRootMarkers) {
            if (-not (Test-Path -LiteralPath (Join-Path $dir $marker) -PathType Leaf)) {
                $complete = $false
                break
            }
        }
        if ($complete) {
            if (-not $From) { $script:FmsProjectRoot = $dir }
            return $dir
        }

        $parent = Split-Path -Parent $dir
        if ($parent -eq $dir -or -not $parent) { break }
        $dir = $parent
    }

    throw ("project-paths: no project root above '{0}' - no directory carries {1} together." -f
        $start, ($script:FmsRootMarkers -join ', '))
}

function Get-ProjectPath {
    <#
    .SYNOPSIS
        Absolute path of a location inside the project tree.
    .PARAMETER Relative
        Repository-relative path, in either separator style.
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Relative
    )

    Join-Path (Get-ProjectRoot) ($Relative -replace '/', [System.IO.Path]::DirectorySeparatorChar)
}

function Get-SiblingPath {
    <#
    .SYNOPSIS
        Absolute path of a directory that lives beside the project root.
    .PARAMETER Name
        Leaf name of the sibling, e.g. FastMediaSorter_release.
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Name
    )

    Join-Path (Split-Path -Parent (Get-ProjectRoot)) $Name
}

# Adding a tool is a row here, never an edit at a call site (strategic spec S2326 section 5.3).
# Probe entries name directories to look inside; every one is built from an environment variable
# so this table itself carries no drive letter.
$script:FmsToolTable = @{
    Adb      = @{
        Override = 'FMS_ADB'
        Command  = 'adb'
        Leaf     = 'adb.exe'
        Probes   = @('ANDROID_HOME\platform-tools', 'ANDROID_SDK_ROOT\platform-tools', 'LOCALAPPDATA\Android\Sdk\platform-tools')
    }
    SevenZip = @{
        Override = 'FMS_SEVENZIP'
        Command  = '7z'
        Leaf     = '7z.exe'
        Probes   = @('ProgramFiles\7-Zip', 'ProgramW6432\7-Zip')
    }
    Pwsh     = @{
        Override = 'FMS_PWSH'
        Command  = 'pwsh'
        Leaf     = 'pwsh.exe'
        Probes   = @('ProgramFiles\PowerShell\7')
    }
    Node     = @{
        Override = 'FMS_NODE'
        Command  = 'node'
        Leaf     = 'node.exe'
        Probes   = @('ProgramFiles\nodejs')
    }
    Npm      = @{
        Override = 'FMS_NPM'
        Command  = 'npm'
        Leaf     = 'npm.cmd'
        Probes   = @('APPDATA\npm', 'ProgramFiles\nodejs')
    }
    Ffmpeg   = @{
        Override = 'FMS_FFMPEG'
        Command  = 'ffmpeg'
        Leaf     = 'ffmpeg.exe'
        # Virtual Desktop Streamer ships a full n7.x build with https/hls/libwebp enabled, which is
        # what the stream-catalog capture needs on a dev box carrying no standalone ffmpeg.
        Probes   = @('ProgramFiles\ffmpeg\bin', 'ProgramFiles(x86)\ffmpeg\bin',
            'ProgramFiles\Virtual Desktop Streamer', 'ProgramFiles(x86)\Virtual Desktop Streamer',
            'ProgramData\chocolatey\bin')
    }
}

$script:FmsToolCache = @{}

function Get-ToolPath {
    <#
    .SYNOPSIS
        Absolute path of an external tool, discovered rather than hardcoded.
    .PARAMETER Tool
        Key into the tool table: Adb, SevenZip, Pwsh, Node, Npm, Ffmpeg.
    .PARAMETER Quiet
        Suppress the one-time line naming the resolved path.
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Tool,
        [switch]$Quiet
    )

    if ($script:FmsToolCache.ContainsKey($Tool)) { return $script:FmsToolCache[$Tool] }

    $entry = $script:FmsToolTable[$Tool]
    if (-not $entry) {
        throw ("project-paths: unknown tool '{0}'. Known tools: {1}." -f
            $Tool, (($script:FmsToolTable.Keys | Sort-Object) -join ', '))
    }

    $resolved = $null

    $override = [System.Environment]::GetEnvironmentVariable($entry.Override)
    if ($override) {
        if (-not (Test-Path -LiteralPath $override -PathType Leaf)) {
            throw ("project-paths: {0} is set to '{1}', which is not a file." -f $entry.Override, $override)
        }
        $resolved = (Resolve-Path -LiteralPath $override).Path
    }

    if (-not $resolved) {
        $onPath = Get-Command $entry.Command -CommandType Application -ErrorAction SilentlyContinue |
            Select-Object -First 1
        if ($onPath) { $resolved = $onPath.Source }
    }

    if (-not $resolved) {
        foreach ($probe in $entry.Probes) {
            $parts = $probe -split '\\', 2
            $base = [System.Environment]::GetEnvironmentVariable($parts[0])
            if (-not $base) { continue }
            $dir = if ($parts.Count -gt 1) { Join-Path $base $parts[1] } else { $base }
            $candidate = Join-Path $dir $entry.Leaf
            if (Test-Path -LiteralPath $candidate -PathType Leaf) {
                $resolved = (Resolve-Path -LiteralPath $candidate).Path
                break
            }
        }
    }

    if (-not $resolved) {
        throw ("project-paths: {0} not found. Looked on PATH as '{1}' and in the known install locations. Set {2} to its full path." -f
            $Tool, $entry.Command, $entry.Override)
    }

    # A wrong pick - a second SDK's adb, say - is otherwise invisible until an install lands on
    # the wrong device (strategic spec S2326 section 7).
    if (-not $Quiet) { Write-Host ("[project-paths] {0} -> {1}" -f $Tool, $resolved) -ForegroundColor DarkGray }

    $script:FmsToolCache[$Tool] = $resolved
    return $resolved
}

# Delivery destinations. This table is the ONE place in the repository allowed to name a machine
# path literally, which is why the hardcoded-drive-path rule excludes this file by name: a default
# that lives nowhere would silently stop delivering artifacts on the machine that has these
# directories. Everything else overrides through the environment variable in the same row.
$script:FmsSinkTable = @{
    Drive         = @{ Override = 'FMS_SINK_DRIVE'; Default = 'c:\GD\WORK\FastMediaSorter' }
    Commander     = @{ Override = 'FMS_SINK_COMMANDER'; Default = 'c:\GD\tc\SZA\_APP' }
    Apk           = @{ Override = 'FMS_SINK_APK'; Default = 'c:\GD\i\APK' }
    Deobfuscation = @{ Override = 'FMS_SINK_DEOBFUSCATION'; Default = 'c:\GD\WORK\FastMediaSorter\deobfuscation' }
    RemoteLogs    = @{ Override = 'FMS_SINK_REMOTE_LOGS'; Default = 'c:\GD\temp' }
}

function Get-ArtifactSink {
    <#
    .SYNOPSIS
        Absolute path of a delivery destination, or $null when it is not reachable here.
    .DESCRIPTION
        Never throws. A missing sink is a delivery problem, not a build problem: the APK is
        already built by the time a caller asks, so failing here would turn distribution into a
        build blocker (strategic spec S2326 ADR-3). Callers skip their copy on $null.
    .PARAMETER Kind
        Key into the sink table: Drive, Commander, Apk, Deobfuscation, RemoteLogs.
    .PARAMETER Quiet
        Suppress the warning written when the sink is unreachable.
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Kind,
        [switch]$Quiet
    )

    $entry = $script:FmsSinkTable[$Kind]
    if (-not $entry) {
        Write-Warning ("project-paths: unknown artifact sink '{0}'. Known sinks: {1}." -f
            $Kind, (($script:FmsSinkTable.Keys | Sort-Object) -join ', '))
        return $null
    }

    $override = [System.Environment]::GetEnvironmentVariable($entry.Override)
    $candidate = if ($override) { $override } else { $entry.Default }

    if (Test-Path -LiteralPath $candidate -PathType Container) {
        return (Resolve-Path -LiteralPath $candidate).Path
    }

    if (-not $Quiet) {
        Write-Warning ("project-paths: artifact sink '{0}' is not reachable at '{1}' - copy skipped. Set {2} to change it." -f
            $Kind, $candidate, $entry.Override)
    }
    return $null
}
