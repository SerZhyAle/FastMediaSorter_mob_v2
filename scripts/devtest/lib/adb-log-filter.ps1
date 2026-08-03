# S1332: the whole line-selection decision for `adb.ps1 log`, with no adb call and no device
# access, so the production verb and the regression suite drive the same code. Dot-sourced:
# no param() block, no $ErrorActionPreference, no output at load time.

# The threadtime pid column. Same shape already proven in prerelease-log-audit.ps1.
$script:AdbLogThreadtimePidPattern =
    '^\s*\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d+\s+(?<pid>\d+)\s+\d+\s+[VDIWEF]\s'

function Get-AppPidsFromLog {
    <#
    .SYNOPSIS
        Process ids the capture itself attributes to the app.
    .DESCRIPTION
        Reads the ActivityManager "Start proc <pid>:<package>" announcements. Prefix-matching
        the package catches the release id, the .debug id and any :sub process in one pass,
        and recovers a process that already exited - the case pidof and ps cannot answer.
    #>
    param(
        [string[]]$Lines,
        [string]$BasePackage
    )

    $pattern = 'Start proc (?<p>\d+):' + [regex]::Escape($BasePackage)
    $found = [System.Collections.Generic.List[int]]::new()
    foreach ($line in $Lines) {
        if ($line -match $pattern) {
            $value = [int]$Matches['p']
            if (-not $found.Contains($value)) { $found.Add($value) }
        }
    }
    return $found.ToArray()
}

function Select-AppLogLines {
    <#
    .SYNOPSIS
        The app's lines: owned by pid, or naming the package in their text.
    .DESCRIPTION
        Pid is the ownership signal - it is what makes a Timber line under a bare class tag
        survive. The text arm is second, for system-side lines that talk ABOUT the app under
        system_server's pid: "Start proc", "ANR in <pkg>", "AndroidRuntime: Process:". Logcat
        buffer separators carry neither signal and stay dropped on purpose: keeping them would
        make prerelease-prepare.ps1 report a crash on any device with a non-empty crash buffer.
    #>
    param(
        [string[]]$Lines,
        [int[]]$AppPids,
        [string[]]$TextPatterns
    )

    $pidSet = @{}
    foreach ($appPid in $AppPids) { $pidSet[$appPid] = $true }

    $kept = [System.Collections.Generic.List[string]]::new()
    foreach ($line in $Lines) {
        $keep = $false
        if ($line -match $script:AdbLogThreadtimePidPattern) {
            $keep = $pidSet.ContainsKey([int]$Matches['pid'])
        }
        if (-not $keep) {
            foreach ($pattern in $TextPatterns) {
                if ($line -match $pattern) { $keep = $true; break }
            }
        }
        if ($keep) { $kept.Add($line) }
    }
    return $kept.ToArray()
}

function Measure-FilterCoverage {
    <#
    .SYNOPSIS
        How many lines matching a pattern the filter swallowed. The primitive behind both the
        runtime self-check and the regression suite. A non-zero `suppressed` is the exact
        defect S1332 fixed: the pattern matched lines that never reached the caller.
    #>
    param(
        [string[]]$RawLines,
        [string[]]$KeptLines,
        [string]$Pattern
    )

    $rawMatched  = @($RawLines  | Where-Object { $_ -match $Pattern }).Count
    $keptMatched = @($KeptLines | Where-Object { $_ -match $Pattern }).Count
    return [pscustomobject]@{
        rawMatched  = $rawMatched
        keptMatched = $keptMatched
        suppressed  = $rawMatched - $keptMatched
    }
}
