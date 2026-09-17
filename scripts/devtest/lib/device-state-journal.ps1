#requires -Version 7.0
<#
.SYNOPSIS
    The device state journal: what a test changed on a device, and what it was before (S3201).

.DESCRIPTION
    On 2026-09-16 one device test left two changes on the owner's watch: `wm density 380` over the
    physical 340, and the app's geometry setting flipped to STORE. Its recipe said "reset after";
    nothing reset it, because putting state back was the agent's memory and not a mechanism. This
    library is the mechanism's data half. scripts/devtest/adb.ps1 is its only writer: every verb
    that changes device state records the ORIGINAL value here before it runs, and `state-begin` /
    `state-check` compare the device with the journal and put every drifted value back.

    Tracked keys:
      wm.density            the `Override density` line of `wm density`; $null = no override
      wm.size               the `Override size` line of `wm size`; $null = no override
      settings.<ns>.<key>   a `settings` value; $null = the key was never written
      datastore.<pkg>       the app's files/datastore/ directory: file name -> SHA-256, with a local
                            byte copy of every file beside the journal

    First original wins. A second mutation of the same key during one run must not overwrite the
    value recorded before the first one - that value is the only one the owner ever had.

    Journal file: temp/DEVICE.STATE/<encoded-serial>.json (scripts/devtest/lib/device-store-paths.ps1).
    The DataStore copies live in temp/DEVICE.STATE/<encoded-serial>.datastore/<pkg>/.

.NOTES
    Dot-sourced library. Functions only, nothing assigned at script scope (S2441). The functions here
    never talk to a device: they parse what adb printed and decide which commands put a value back,
    so the contract suite runs without adb. The device calls themselves stay in adb.ps1, where the
    stubbed-adb suite (scripts/devtest/adb.tests) drives them.

    Exit codes: none of its own. Dot-sourcing defines the functions and returns.
#>

function ConvertFrom-WmOverrideOutput {
    <#
    .SYNOPSIS
        The override value from `wm density` / `wm size` output, or $null when none is set.

    .DESCRIPTION
        Both commands print `Physical ..: <v>` and, only when an override is in force, a second line
        `Override ..: <v>`. The physical line is never a thing to restore - it is what `reset` returns
        to - so it is not reported.
    #>
    [CmdletBinding()]
    param(
        [AllowEmptyString()][AllowNull()][string]$Text
    )
    if ([string]::IsNullOrWhiteSpace($Text)) { return $null }
    $m = [regex]::Match($Text, '(?m)^\s*Override\s+(?:density|size)\s*:\s*(\S+)\s*$')
    if ($m.Success) { return $m.Groups[1].Value }
    return $null
}

function ConvertFrom-SettingsValue {
    <#
    .SYNOPSIS
        A `settings get` answer as a value, with the never-written key as $null.
    #>
    [CmdletBinding()]
    param(
        [AllowEmptyString()][AllowNull()][string]$Text
    )
    if ($null -eq $Text) { return $null }
    $v = $Text.Trim()
    if ($v -eq '' -or $v -eq 'null') { return $null }
    return $v
}

function ConvertFrom-ShellMutation {
    <#
    .SYNOPSIS
        Which journal key a free-form shell command would change, or $null when it changes none.

    .DESCRIPTION
        The `shell` verb is a passthrough, so it is the one route by which `wm density 380` reached
        the owner's watch. Recognised: `wm density ..`, `wm size ..`, `settings put|delete <ns> <key>`.
        A command that only reads (`wm density` alone, `settings get`) changes nothing.
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Command
    )
    $c = $Command.Trim()
    if ($c -match '^wm\s+density\s+\S+') { return [pscustomobject]@{ Key = 'wm.density' } }
    if ($c -match '^wm\s+size\s+\S+') { return [pscustomobject]@{ Key = 'wm.size' } }
    $m = [regex]::Match($c, '^settings\s+(?:put|delete)\s+(system|secure|global)\s+(\S+)')
    if ($m.Success) {
        return [pscustomobject]@{ Key = "settings.$($m.Groups[1].Value).$($m.Groups[2].Value)" }
    }
    return $null
}

function Get-DeviceStateReadCommand {
    <#
    .SYNOPSIS
        The adb shell arguments that read the current value of one scalar key.
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Key
    )
    switch -Regex ($Key) {
        '^wm\.density$' { return , @('shell', 'wm', 'density') }
        '^wm\.size$' { return , @('shell', 'wm', 'size') }
        '^settings\.(system|secure|global)\.(.+)$' { return , @('shell', 'settings', 'get', $Matches[1], $Matches[2]) }
    }
    throw "device-state-journal: no read command for key '$Key'"
}

function ConvertFrom-DeviceStateReading {
    <#
    .SYNOPSIS
        Turns the raw output of Get-DeviceStateReadCommand into the value the journal stores.
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Key,
        [AllowEmptyString()][AllowNull()][string]$Text
    )
    if ($Key -like 'wm.*') { return (ConvertFrom-WmOverrideOutput -Text $Text) }
    return (ConvertFrom-SettingsValue -Text $Text)
}

function Get-DeviceStateRestoreCommand {
    <#
    .SYNOPSIS
        The adb shell arguments that put one scalar key back to its recorded original.

    .DESCRIPTION
        A $null original means "nothing was set", so the restore is the reset, never a write of some
        default: writing font_scale 1.0 over a never-written key is a different device state from the
        one the owner had, even though it looks the same on screen.
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Key,
        # Untyped on purpose: a [string] parameter turns $null into '', and '' is a value to write,
        # while $null is "nothing was set" and must become the reset.
        [AllowNull()]$Original
    )
    switch -Regex ($Key) {
        '^wm\.(density|size)$' {
            $what = $Matches[1]
            if ($null -eq $Original) { return , @('shell', 'wm', $what, 'reset') }
            return , @('shell', 'wm', $what, $Original)
        }
        '^settings\.(system|secure|global)\.(.+)$' {
            if ($null -eq $Original) { return , @('shell', 'settings', 'delete', $Matches[1], $Matches[2]) }
            return , @('shell', 'settings', 'put', $Matches[1], $Matches[2], $Original)
        }
    }
    throw "device-state-journal: no restore command for key '$Key'"
}

function New-DeviceStateJournal {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Serial
    )
    return [ordered]@{
        serial  = $Serial
        created = (Get-Date).ToString('o')
        entries = [ordered]@{}
    }
}

function Read-DeviceStateJournal {
    <#
    .SYNOPSIS
        The journal at -Path as an ordered dictionary, or $null when there is none.
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Path
    )
    if (-not (Test-Path -LiteralPath $Path)) { return $null }
    $raw = Get-Content -LiteralPath $Path -Raw -Encoding UTF8
    return ($raw | ConvertFrom-Json -AsHashtable)
}

function Write-DeviceStateJournal {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)][System.Collections.IDictionary]$Journal
    )
    $dir = Split-Path -Parent $Path
    if (-not (Test-Path -LiteralPath $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    $Journal | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $Path -Encoding UTF8
}

function Add-DeviceStateOriginal {
    <#
    .SYNOPSIS
        Records the original of one key unless the journal already holds one. Returns $true when added.
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][System.Collections.IDictionary]$Journal,
        [Parameter(Mandatory)][string]$Key,
        [AllowNull()]$Original
    )
    if ($Journal.entries.Contains($Key)) { return $false }
    $Journal.entries[$Key] = [ordered]@{ original = $Original; recorded = (Get-Date).ToString('o') }
    return $true
}

function Compare-DeviceStateScalar {
    <#
    .SYNOPSIS
        $true when the current value differs from the recorded original.
    #>
    [CmdletBinding()]
    param(
        [AllowNull()][string]$Original,
        [AllowNull()][string]$Current
    )
    return ([string]$Original -ne [string]$Current)
}

function Compare-DeviceStateFiles {
    <#
    .SYNOPSIS
        File-level drift between a recorded DataStore snapshot and the current one.

    .DESCRIPTION
        Both inputs map file name -> SHA-256. Returns objects with Name and Action: `restore` for a
        file that changed or disappeared, `remove` for a file the run created.
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][System.Collections.IDictionary]$Recorded,
        [Parameter(Mandatory)][System.Collections.IDictionary]$Current
    )
    $drift = [System.Collections.Generic.List[object]]::new()
    foreach ($name in @($Recorded.Keys | Sort-Object)) {
        if (-not $Current.Contains($name) -or $Current[$name] -ne $Recorded[$name]) {
            $drift.Add([pscustomobject]@{ Name = $name; Action = 'restore' })
        }
    }
    foreach ($name in @($Current.Keys | Sort-Object)) {
        if (-not $Recorded.Contains($name)) {
            $drift.Add([pscustomobject]@{ Name = $name; Action = 'remove' })
        }
    }
    return $drift.ToArray()
}

function Get-Sha256Hex {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][byte[]]$Bytes
    )
    return ([System.Convert]::ToHexString([System.Security.Cryptography.SHA256]::HashData($Bytes))).ToLowerInvariant()
}
