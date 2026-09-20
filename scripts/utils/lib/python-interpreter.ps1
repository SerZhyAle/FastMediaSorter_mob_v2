#requires -Version 7.0
<#
.SYNOPSIS
    Resolves a Python interpreter that will actually RUN, skipping Windows Store execution aliases.

.DESCRIPTION
    S3342. `python3` and `python` under `%LOCALAPPDATA%\Microsoft\WindowsApps` are App Execution
    Aliases: zero-length reparse files that Windows resolves to a Store app. When that app is not
    installed, running one does not fail quietly - Windows raises the "Select an app to open
    'python3'" picker, a modal dialog in front of whoever owns the desktop. The owner reported it
    firing repeatedly on 2026-09-19.

    Every interpreter pick in this repository used to probe its candidates by RUNNING them, which
    is what opened the dialog: generate-splash-brand.ps1 ran `-c 'import fontTools'` on each
    candidate in turn, and the alias was a candidate. Detecting the alias by its shape instead -
    zero length, or a path under WindowsApps - costs one file stat and never launches anything.

    Note the shim at ~/bin/python3 (S1594) does NOT help here: it is a bash script with no
    extension, visible only inside Git Bash, so PowerShell and cmd never see it and fall through to
    the alias.

.PARAMETER ExtraCandidates
    Paths to try before the PATH lookups - a project virtual environment, typically.

.NOTES
    Dot-sourced, never invoked.
    Exit codes: none - this file defines functions and returns nothing on load.
#>

function Test-StoreAliasPath {
    <#
    .SYNOPSIS
        True when the path is a Windows Store execution alias rather than a real interpreter.
    .DESCRIPTION
        Two independent signs, either of which is enough. The length test is the reliable one - an
        alias is a zero-byte reparse point and no real interpreter is empty - and the directory test
        catches the case where the file cannot be stat'ed at all.
    #>
    param([string] $Path)
    if ([string]::IsNullOrWhiteSpace($Path)) { return $true }
    if ($Path -match '[\/]Microsoft[\/]WindowsApps[\/]') { return $true }
    try {
        $item = Get-Item -LiteralPath $Path -ErrorAction Stop
        if ($item.Length -eq 0) { return $true }
    } catch {
        return $true
    }
    return $false
}

function Get-WorkingPythonPath {
    <#
    .SYNOPSIS
        The first Python interpreter that is real, or $null when there is none.
    .DESCRIPTION
        `python3` is deliberately NOT among the PATH names tried. On this platform it resolves to
        the Store alias and to nothing else, so asking for it can only produce the dialog this file
        exists to avoid; `py` covers every launcher-managed install instead.
    #>
    param([string[]] $ExtraCandidates = @())

    $candidates = New-Object System.Collections.Generic.List[string]
    foreach ($extra in $ExtraCandidates) {
        if (-not [string]::IsNullOrWhiteSpace($extra)) { $candidates.Add($extra) }
    }
    foreach ($name in @('python', 'py')) {
        foreach ($command in @(Get-Command $name -CommandType Application -All -ErrorAction SilentlyContinue)) {
            if ($command.Source) { $candidates.Add($command.Source) }
        }
    }

    foreach ($candidate in $candidates) {
        if (-not (Test-Path -LiteralPath $candidate)) { continue }
        if (Test-StoreAliasPath -Path $candidate) { continue }
        return $candidate
    }
    return $null
}
