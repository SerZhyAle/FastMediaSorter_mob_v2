<#
.SYNOPSIS
    Start a job that must outlive the agent session - hidden, parent-independent, logged to temp/ (S2400).
.DESCRIPTION
    The harness's background task dies with the session that started it, so a run longer than the
    session (a full Maestro sweep, about 30 minutes) had to be started by hand with
    `Start-Process -WindowStyle Hidden` and a hand-typed timestamp. This is that pattern, once.

    The command runs inside a hidden `pwsh -NoProfile` wrapper that owns no console of the caller's,
    so the caller may exit at any point. stdout streams live into
    `temp/<Ticket|scratch>/detached-<label>-<yyyyMMdd-HHmmss>.log`; stderr is captured beside it
    and appended to the log when the command ends, together with the exit marker
    `<same>.done` holding `exit <code>`. `-Status <log>` reads the marker.

    Not a replacement for the harness's own background for anything shorter than the session:
    a job that fits the session keeps its completion notification there. Locks: a detached job
    takes no build or code lock by itself - the caller acquires whatever its command needs.

    `-Arguments` is ONE raw string handed to the child verbatim, because `pwsh -File` cannot pass an
    array across a process boundary (S1184): quote inside it as the child expects.

    `-OutDir` (S2406) replaces the `temp/<Ticket|scratch>` bucket as the directory of the log and the
    marker - repo-relative or absolute, created if missing - for a job whose artifacts all belong in
    one directory (the monitor writer keeps page, data, pid file and log under temp/monitor/).

    Exit codes:
      0  started (prints pid, log and marker paths) - or, with -Status, the marker was read.
      1  bad arguments: no command, or -Status given a log path that does not exist.
      2  the command could not be resolved (a .ps1 that does not exist, or a name not on PATH).
.EXAMPLE
    pwsh -NoProfile -File scripts/utils/start-detached.ps1 -Command scripts/devtest/maestro-run.ps1 -Arguments '-Flow scripts/devtest/maestro/S0398.yaml -Json' -Ticket S0398 -Label maestro
.EXAMPLE
    pwsh -NoProfile -File scripts/utils/start-detached.ps1 -Status temp/S0398/detached-maestro-20260902-213000.log
#>
[CmdletBinding(DefaultParameterSetName = 'Start')]
param(
    [Parameter(ParameterSetName = 'Start', Mandatory = $true, Position = 0)]
    [string]$Command,
    [Parameter(ParameterSetName = 'Start')]
    [string]$Arguments = '',
    [Parameter(ParameterSetName = 'Start')]
    [ValidatePattern('^(S\d{4})?$')]
    [string]$Ticket = '',
    [Parameter(ParameterSetName = 'Start')]
    [string]$Label = 'job',
    [Parameter(ParameterSetName = 'Start')]
    [string]$OutDir = '',
    [Parameter(ParameterSetName = 'Status', Mandatory = $true)]
    [string]$Status
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..')).Path

if ($PSCmdlet.ParameterSetName -eq 'Status') {
    $logPath = if ([System.IO.Path]::IsPathRooted($Status)) { $Status } else { Join-Path $repoRoot $Status }
    if (-not (Test-Path -LiteralPath $logPath -PathType Leaf)) {
        Write-Host "start-detached: no such log '$Status'." -ForegroundColor Red
        exit 1
    }
    $donePath = [System.IO.Path]::ChangeExtension($logPath, '.done')
    if (Test-Path -LiteralPath $donePath -PathType Leaf) {
        Write-Output ((Get-Content -LiteralPath $donePath -Raw).Trim())
    } else {
        Write-Output 'running'
    }
    exit 0
}

if ([string]::IsNullOrWhiteSpace($Command)) {
    Write-Host 'start-detached: -Command is empty.' -ForegroundColor Red
    exit 1
}

$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") {
    "$env:ProgramFiles\PowerShell\7\pwsh.exe"
} else { (Get-Command pwsh).Source }

# Resolve what the child runs: a repository .ps1 goes through pwsh -File, anything else must be
# on PATH. Refusing here is the only chance to refuse at all - a hidden process has no one to
# report "not found" to.
if ($Command -match '\.ps1$') {
    $scriptPath = if ([System.IO.Path]::IsPathRooted($Command)) { $Command } else { Join-Path $repoRoot $Command }
    if (-not (Test-Path -LiteralPath $scriptPath -PathType Leaf)) {
        Write-Host "start-detached: script not found '$Command'." -ForegroundColor Red
        exit 2
    }
    $exe = $pwshExe
    $rawArgs = ('-NoProfile -File "{0}" {1}' -f (Resolve-Path -LiteralPath $scriptPath).Path, $Arguments).TrimEnd()
} else {
    $resolved = Get-Command $Command -ErrorAction SilentlyContinue
    if (-not $resolved -or -not $resolved.Source) {
        Write-Host "start-detached: command not found '$Command'." -ForegroundColor Red
        exit 2
    }
    $exe = $resolved.Source
    $rawArgs = $Arguments
}

$outDir = if ($OutDir) {
    if ([System.IO.Path]::IsPathRooted($OutDir)) { $OutDir } else { Join-Path $repoRoot $OutDir }
} else {
    Join-Path $repoRoot ("temp/" + $(if ($Ticket) { $Ticket } else { 'scratch' }))
}
if (-not (Test-Path -LiteralPath $outDir)) { New-Item -ItemType Directory -Path $outDir -Force | Out-Null }
$safeLabel = ($Label -replace '[^A-Za-z0-9_.-]', '-')
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$logPath = Join-Path $outDir "detached-$safeLabel-$stamp.log"
$errPath = [System.IO.Path]::ChangeExtension($logPath, '.err')
$donePath = [System.IO.Path]::ChangeExtension($logPath, '.done')

function ConvertTo-PsLiteral([string]$s) { return "'" + ($s -replace "'", "''") + "'" }

# The wrapper is the process that waits, so the marker is written by something that saw the exit.
# It is handed over base64-encoded: no quoting layer between this script and the wrapper, so the
# raw argument string reaches the child exactly as typed.
$wrapper = @"
`$ErrorActionPreference = 'Continue'
`$code = 1
try {
    `$startArgs = @{
        FilePath = $(ConvertTo-PsLiteral $exe)
        NoNewWindow = `$true
        Wait = `$true
        PassThru = `$true
        WorkingDirectory = $(ConvertTo-PsLiteral $repoRoot)
        RedirectStandardOutput = $(ConvertTo-PsLiteral $logPath)
        RedirectStandardError = $(ConvertTo-PsLiteral $errPath)
    }
    if ($(ConvertTo-PsLiteral $rawArgs) -ne '') { `$startArgs.ArgumentList = $(ConvertTo-PsLiteral $rawArgs) }
    `$p = Start-Process @startArgs
    `$code = `$p.ExitCode
} catch {
    Add-Content -LiteralPath $(ConvertTo-PsLiteral $logPath) -Value ("start-detached wrapper: " + `$_.Exception.Message)
}
if (Test-Path -LiteralPath $(ConvertTo-PsLiteral $errPath)) {
    `$err = Get-Content -LiteralPath $(ConvertTo-PsLiteral $errPath) -Raw
    if (`$err) { Add-Content -LiteralPath $(ConvertTo-PsLiteral $logPath) -Value ("--- stderr ---`n" + `$err) }
    Remove-Item -LiteralPath $(ConvertTo-PsLiteral $errPath) -Force -ErrorAction SilentlyContinue
}
Set-Content -LiteralPath $(ConvertTo-PsLiteral $donePath) -Value ("exit " + `$code) -Encoding ascii
"@
$encoded = [Convert]::ToBase64String([System.Text.Encoding]::Unicode.GetBytes($wrapper))

$proc = Start-Process -FilePath $pwshExe -ArgumentList @('-NoProfile', '-NonInteractive', '-EncodedCommand', $encoded) `
    -WindowStyle Hidden -PassThru

$rel = {
    param($p)
    # A repo path prints relative, an -OutDir outside the repository prints as it is.
    if ($p.StartsWith($repoRoot, [System.StringComparison]::OrdinalIgnoreCase)) { return ($p.Substring($repoRoot.Length).TrimStart('\', '/') -replace '\\', '/') }
    return ($p -replace '\\', '/')
}
Write-Output ("started pid={0} log={1} done={2}" -f $proc.Id, (& $rel $logPath), (& $rel $donePath))
Write-Output ("status: pwsh -NoProfile -File scripts/utils/start-detached.ps1 -Status {0}" -f (& $rel $logPath))
exit 0
