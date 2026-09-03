#requires -Version 7.0
<#
.SYNOPSIS
    Run a repository script in a child process whose stdout and stderr are FILES, then stream those
    files back to the caller as they grow (S2412).

.DESCRIPTION
    A Gradle daemon born under an agent's tool call inherits that call's stdout handle and outlives
    the build. While that handle is a pipe, the end of stream never arrives and the call cannot
    complete - measured 2026-09-03, a wear check whose script finished at 00:39:50 left its caller
    blocked until the tool's own 600 s timeout killed it at 00:49:01, nine minutes after the work
    was done. The same work, the same fresh daemon, with the caller's stdout pointed at a file
    instead, returned on the very second the script finished.

    So the child is handed files and never the caller's pipe. Whatever the child spawns may inherit
    those file handles and hold them for hours; the caller's pipe is not among them, so the caller
    sees EOF the moment the child itself exits.

    Redirecting the child's streams is necessary but not sufficient, because redirecting is exactly
    what makes Windows hand down everything else too - see Block-StdHandleInheritance below, which
    is the half that actually closes the hole.

    Output is streamed rather than dumped at the end, so a long build still shows progress. stdout
    and stderr are streamed from separate files into the caller's own two streams, which keeps a
    caller that merged them with `2>&1` roughly in order and stops a caller that did not from
    seeing ordinary build noise on its error stream.

    Colour is lost: the child's host is a file, and PowerShell drops ANSI colour when its host is
    not a console. That is why a.ps1 reaches for this only when its own stdout is ALREADY
    redirected - an interactive run has nothing to gain here and its colour to lose.

.PARAMETER ScriptPath
    Absolute path of the .ps1 to run.

.PARAMETER Arguments
    Already-flattened argument list, handed to the child verbatim. A hashtable splat cannot cross a
    process boundary, so the caller flattens it first.

.PARAMETER LogDirectory
    Where the two stream files go. Defaults to temp/isolated-stdout under the repo root.

.PARAMETER WorkingDirectory
    Working directory for the child. Defaults to the caller's own, because a build script resolves
    the Gradle project from the current directory and moving it would build the wrong tree - the
    release worktree being the case that makes this a parameter rather than a constant.

.EXAMPLE
    pwsh -NoProfile -Command "& scripts/utils/invoke-isolated-stdout.ps1 -ScriptPath (Resolve-Path scripts/builders/check-standard-fast.ps1) -Arguments @('-Mode','Code')"

.NOTES
    Exit codes:
      2 - the child process could not be started at all; nothing ran. Distinguishable from a child
          that ran and chose 2 only by the message on stderr.
      * - any other code is the child's own, forwarded verbatim. This script adds no verdict of its
          own, so the caller reads exactly what the target returned.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$ScriptPath,
    [string[]]$Arguments = @(),
    [string]$LogDirectory,
    [string]$WorkingDirectory
)

$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..')).Path
if (-not $LogDirectory) { $LogDirectory = Join-Path $repoRoot 'temp\isolated-stdout' }
if (-not (Test-Path -LiteralPath $LogDirectory)) {
    New-Item -ItemType Directory -Path $LogDirectory -Force | Out-Null
}
if (-not $WorkingDirectory) { $WorkingDirectory = (Get-Location).Path }

# Every run leaves two files behind and every build goes through here, so the directory would grow
# without bound. A day is long enough to still hold the log of the build being investigated.
Get-ChildItem -LiteralPath $LogDirectory -Filter 'run-*.log' -File -ErrorAction SilentlyContinue |
    Where-Object { $_.LastWriteTime -lt (Get-Date).AddDays(-1) } |
    Remove-Item -Force -ErrorAction SilentlyContinue

$stamp = Get-Date -Format 'yyyyMMdd-HHmmss-fff'
$outPath = Join-Path $LogDirectory "run-$stamp.out.log"
$errPath = Join-Path $LogDirectory "run-$stamp.err.log"

# Both files are created up front: the reader below opens them on its first poll, which can happen
# before the child has written its first byte and created them itself.
New-Item -ItemType File -Path $outPath -Force | Out-Null
New-Item -ItemType File -Path $errPath -Force | Out-Null

# The running interpreter, not the name 'pwsh': a.ps1 may have been launched by an absolute path or
# a shim, and resolving through PATH could pick a different PowerShell than the one in use.
$pwshExe = (Get-Process -Id $PID).Path
if (-not $pwshExe) { $pwshExe = 'pwsh' }

function ConvertTo-QuotedArgument {
    param([string]$Value)

    # Start-Process joins an argument ARRAY with plain spaces and quotes nothing, so `-Tests 'a b'`
    # reaches the child as two arguments and the second is lost - measured 2026-09-03 on this very
    # helper, which reported `alpha=has` for `has space`. Handing it one pre-quoted command line
    # instead puts the escaping here, where CommandLineToArgvW's rules can be followed: a backslash
    # run before a quote doubles, and so does a trailing one.
    if ($Value -eq '') { return '""' }
    if ($Value -notmatch '[\s"]') { return $Value }
    $escaped = $Value -replace '(\\*)"', '$1$1\"'
    $escaped = $escaped -replace '(\\+)$', '$1$1'
    return '"' + $escaped + '"'
}

$childArgs = @('-NoProfile', '-File', $ScriptPath) + $Arguments
$childCommandLine = (($childArgs | ForEach-Object { ConvertTo-QuotedArgument $_ }) -join ' ')

function Block-StdHandleInheritance {
    # Pointing the child's streams at files is NOT enough on its own, and the reason is the thing
    # that made the first version of this fix fail its own acceptance: CreateProcess copies EVERY
    # inheritable handle a process owns, not only the three named in STARTUPINFO, and .NET turns
    # that inheritance ON precisely when a stream is redirected. So the redirection meant to keep
    # the agent's pipe away from the child was itself handing the child an extra, unnamed copy of
    # that pipe, which then travelled into gradlew.bat, the launcher JVM and the daemon.
    #
    # Measured 2026-09-03: `a.ps1 fk` piped into tail, isolation active, fresh daemon. The script
    # printed "Fast check passed" at 02:10:06 and both pwsh processes were gone by 02:10:32, yet
    # the call did not return; the orphaned daemon was killed at 02:10:48 and the call returned in
    # that same second. A reduced probe of the same shape took 31 s before this and 1 s after.
    #
    # stdout and stderr only. .NET replaces those two in the child with its own file handles, so
    # clearing the flag here costs the child nothing - it only removes the spare copies. stdin is
    # deliberately left alone: it is NOT redirected, so .NET passes this process's own stdin handle
    # through STARTUPINFO, and a non-inheritable handle passed that way reaches the child as an
    # invalid one - the child would lose its stdin to fix a defect that never involved it.
    if (-not $IsWindows) { return }
    if (-not ('Fms.StdHandles' -as [type])) {
        Add-Type -Namespace Fms -Name StdHandles -MemberDefinition @'
[System.Runtime.InteropServices.DllImport("kernel32.dll", SetLastError = true)]
public static extern System.IntPtr GetStdHandle(int nStdHandle);
[System.Runtime.InteropServices.DllImport("kernel32.dll", SetLastError = true)]
public static extern bool SetHandleInformation(System.IntPtr hObject, uint dwMask, uint dwFlags);
'@
    }
    $handleFlagInherit = 1
    foreach ($stdHandleId in -11, -12) {
        $handle = [Fms.StdHandles]::GetStdHandle($stdHandleId)
        if ($handle -ne [IntPtr]::Zero -and $handle -ne [IntPtr](-1)) {
            # Best effort: a console handle in some hosts refuses the call, and that case is the
            # interactive one, which never reproduced this defect in the first place.
            [void][Fms.StdHandles]::SetHandleInformation($handle, $handleFlagInherit, 0)
        }
    }
}

Block-StdHandleInheritance

try {
    $proc = Start-Process -FilePath $pwshExe -ArgumentList $childCommandLine `
        -RedirectStandardOutput $outPath -RedirectStandardError $errPath `
        -WorkingDirectory $WorkingDirectory -NoNewWindow -PassThru
}
catch {
    $startFailure = "invoke-isolated-stdout: could not start the child process - $($_.Exception.Message)"
    Write-Error $startFailure -ErrorAction Continue
    exit 2
}

# Read forward from a byte offset rather than re-reading the file: a long build writes tens of
# thousands of lines, and re-reading from the top on every poll is quadratic.
$offsets = @{ $outPath = 0L; $errPath = 0L }

function Write-NewText {
    param([string]$Path, [switch]$ToErrorStream)

    if (-not (Test-Path -LiteralPath $Path)) { return }
    # ReadWrite sharing: the child holds the same file open for writing for its whole life.
    $stream = [System.IO.File]::Open($Path, 'Open', 'Read', 'ReadWrite')
    try {
        if ($stream.Length -le $offsets[$Path]) { return }
        [void]$stream.Seek($offsets[$Path], 'Begin')
        $reader = New-Object System.IO.StreamReader($stream)
        $text = $reader.ReadToEnd()
        $offsets[$Path] = $stream.Length
        if ($text) {
            if ($ToErrorStream) { [Console]::Error.Write($text) } else { [Console]::Out.Write($text) }
        }
    }
    finally { $stream.Dispose() }
}

while (-not $proc.HasExited) {
    Write-NewText -Path $outPath
    Write-NewText -Path $errPath -ToErrorStream
    Start-Sleep -Milliseconds 200
}

# The child can write between the last poll and its exit, so flush once more after the wait - that
# tail is exactly what a poll loop drops when it stops at HasExited and never reads again.
$proc.WaitForExit()
Write-NewText -Path $outPath
Write-NewText -Path $errPath -ToErrorStream

exit $proc.ExitCode
