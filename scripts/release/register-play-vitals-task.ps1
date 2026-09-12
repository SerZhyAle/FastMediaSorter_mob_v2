#!/usr/bin/env pwsh
#requires -Version 7.0
<#
.SYNOPSIS
    Register, remove or inspect the daily Windows scheduled task that runs the Play vitals watch (S2917).

.DESCRIPTION
    The watch (scripts/release/watch-play-vitals.ps1) is the one trigger in this repository with no
    person in the invocation path: the scheduler starts it once a day, it reads Android vitals, rewrites
    the two measured records and files a Draft ticket on a red band. No agent session is held open for
    it, because nothing in the watch needs a model (S2917 ADR-1, ADR-6).

    The task is NOT registered by default - registering it is the owner's choice. It runs as the current
    user, only while that user is logged on, from the repository root with the same virtual environment
    and service-account key the release scripts use; no copy of the key is made. Each run leaves
    temp\play-vitals\run-<utc stamp>.log and temp\play-vitals\last-exit.json.

    This script takes no lock and posts nothing to the agent chat: it configures a reader, it is not an
    agent. It removes only the task it is named; nothing else on the workstation is touched.

.PARAMETER Action
    Register, Unregister or Status (the default).

.PARAMETER TaskName
    Name of the scheduled task. Defaults to FastMediaSorter-PlayVitalsWatch.

.PARAMETER At
    Local time of the daily run, HH:mm. Defaults to 09:00.

.PARAMETER WhatIf
    With Register: print the exact definition that would be registered and change nothing.

.EXAMPLE
    pwsh -NoProfile -File scripts/release/register-play-vitals-task.ps1 -Action Register -WhatIf

.EXAMPLE
    pwsh -NoProfile -File scripts/release/register-play-vitals-task.ps1 -Action Register -At 08:30

.EXAMPLE
    pwsh -NoProfile -File scripts/release/register-play-vitals-task.ps1

.NOTES
    Exit codes:
      0 - done: registered, removed, or the Status of a registered task printed
      1 - Status or Unregister found no task of that name
      2 - could not act: the ScheduledTasks module is unavailable, -At is not a time, or the scheduler refused
#>
[CmdletBinding()]
param(
    [ValidateSet('Register', 'Unregister', 'Status')]
    [string] $Action = 'Status',
    [string] $TaskName = 'FastMediaSorter-PlayVitalsWatch',
    [string] $At = '09:00',
    [switch] $WhatIf
)

$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$pwshExe = [Diagnostics.Process]::GetCurrentProcess().MainModule.FileName
$watchScript = Join-Path $repoRoot 'scripts\release\watch-play-vitals.ps1'
$logDir = Join-Path $repoRoot 'temp\play-vitals'
$taskArgument = "-NoProfile -WindowStyle Hidden -File `"$watchScript`" -LogDir `"$logDir`""

if (-not (Get-Command -Name Register-ScheduledTask -ErrorAction SilentlyContinue)) {
    Write-Error 'register-play-vitals-task: the ScheduledTasks module is unavailable on this machine.' -ErrorAction Continue
    exit 2
}

$parsedAt = [datetime]::MinValue
if (-not [datetime]::TryParseExact($At, 'HH:mm', [System.Globalization.CultureInfo]::InvariantCulture,
        [System.Globalization.DateTimeStyles]::None, [ref] $parsedAt)) {
    Write-Error "register-play-vitals-task: -At '$At' is not a time in HH:mm form." -ErrorAction Continue
    exit 2
}

$existing = Get-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue

switch ($Action) {
    'Register' {
        Write-Host "register-play-vitals-task: task      $TaskName"
        Write-Host "register-play-vitals-task: runs      daily at $At, as $([Environment]::UserName), only while logged on"
        Write-Host "register-play-vitals-task: execute   $pwshExe"
        Write-Host "register-play-vitals-task: arguments $taskArgument"
        Write-Host "register-play-vitals-task: directory $repoRoot"
        if ($WhatIf) {
            Write-Host 'register-play-vitals-task: -WhatIf - nothing registered.'
            exit 0
        }
        try {
            $taskAction = New-ScheduledTaskAction -Execute $pwshExe -Argument $taskArgument -WorkingDirectory $repoRoot
            $trigger = New-ScheduledTaskTrigger -Daily -At $parsedAt
            $principal = New-ScheduledTaskPrincipal -UserId ([Security.Principal.WindowsIdentity]::GetCurrent().Name) -LogonType Interactive
            $settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -ExecutionTimeLimit (New-TimeSpan -Minutes 30)
            $null = Register-ScheduledTask -TaskName $TaskName -Action $taskAction -Trigger $trigger -Principal $principal `
                -Settings $settings -Description 'FastMediaSorter S2917: daily Play vitals watch (scripts/release/watch-play-vitals.ps1).' -Force
        } catch {
            Write-Error "register-play-vitals-task: the scheduler refused - $($_.Exception.Message)" -ErrorAction Continue
            exit 2
        }
        $replaced = if ($existing) { ' (replaced the previous definition)' } else { '' }
        Write-Host "register-play-vitals-task: registered $TaskName$replaced. Logs go to $logDir."
        exit 0
    }
    'Unregister' {
        if (-not $existing) {
            Write-Host "register-play-vitals-task: no task named $TaskName - nothing to remove."
            exit 1
        }
        try {
            Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false
        } catch {
            Write-Error "register-play-vitals-task: the scheduler refused to remove $TaskName - $($_.Exception.Message)" -ErrorAction Continue
            exit 2
        }
        Write-Host "register-play-vitals-task: removed $TaskName."
        exit 0
    }
    default {
        if (-not $existing) {
            Write-Host "register-play-vitals-task: no task named $TaskName is registered (the default - registration is the owner's choice)."
            exit 1
        }
        $info = Get-ScheduledTaskInfo -TaskName $TaskName
        Write-Host "register-play-vitals-task: $TaskName is $($existing.State)"
        $stamp = { param($t) if ($t -and $t.Year -gt 2000) { $t.ToString('yyyy-MM-dd HH:mm', [System.Globalization.CultureInfo]::InvariantCulture) } else { 'never' } }
        Write-Host "  next run: $(& $stamp $info.NextRunTime)   last run: $(& $stamp $info.LastRunTime)   last result: $($info.LastTaskResult)"
        $marker = Join-Path $logDir 'last-exit.json'
        if (Test-Path -LiteralPath $marker) {
            Write-Host "  last-exit.json: $((Get-Content -LiteralPath $marker -Raw) -replace '\s+', ' ')"
        } else {
            Write-Host '  last-exit.json: none yet'
        }
        exit 0
    }
}
