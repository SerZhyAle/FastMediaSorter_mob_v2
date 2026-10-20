<#
.SYNOPSIS
  Concurrent child-process lanes for the parallel pre-release halves (S4249).

.DESCRIPTION
  Dot-sourced by scripts/devtest/prerelease-device-half.ps1 and scripts/devtest/prerelease-wear-half.ps1.
  A lane is an ordered list of steps bound to one device; lanes run concurrently, the steps of one
  lane one after another. Each process step is a child process whose stdout and stderr go to files
  under the run's child directory - the orchestrator never holds a child's stream, because a
  background `adb logcat` sharing an inherited handle is how prerelease-prepare.ps1 once deadlocked
  its parent. Every finished step is recorded with New-StageRecord (lib/device-half-plan.ps1, sourced
  first).

  A step is a [pscustomobject] with Name and Device, and either
    Arguments - the child's argument vector (Exe optional, default this pwsh; Env optional, a
                hashtable set for the child's start only), or
    Action    - a scriptblock run in this process that returns an exit code; for short work that
                must happen exactly once between other steps (clearing a shared directory, starting
                a capture).
  Requires (optional) names steps of ANY lane that must have finished with exit 0 first. The step
  waits while one of them is still pending; when one of them failed or was skipped, the step is
  skipped and recorded with a $null exit, which every exit-code fold reads as "could not verify".

  Exit codes: none - a library defines functions and returns to its caller.
#>

function Test-StepHasField {
    param($Step, [string]$Field)
    return ($Step.PSObject.Properties.Name -contains $Field) -and ($null -ne $Step.$Field)
}

function Start-LaneStep {
    param($Step, [string]$ChildDir, [string]$WorkingDirectory)
    $exe = if (Test-StepHasField $Step 'Exe') { $Step.Exe } else { (Get-Process -Id $PID).Path }
    $safe = ($Step.Name -replace '[^A-Za-z0-9_.-]', '_')
    $out = Join-Path $ChildDir ("{0}_{1}.out" -f $safe, $Step.Device)
    $err = Join-Path $ChildDir ("{0}_{1}.err" -f $safe, $Step.Device)
    # Start-Process joins an argument array with bare spaces, so an argument carrying a space or a
    # quote is quoted here or it splits into several.
    $argLine = (@($Step.Arguments) | ForEach-Object {
            $a = [string]$_
            if ($a -match '[\s"]') { '"' + ($a -replace '"', '\"') + '"' } else { $a }
        }) -join ' '
    $envSet = @()
    if (Test-StepHasField $Step 'Env') {
        foreach ($k in $Step.Env.Keys) { Set-Item -Path "env:$k" -Value $Step.Env[$k]; $envSet += $k }
    }
    try {
        $proc = Start-Process -FilePath $exe -ArgumentList $argLine -NoNewWindow -PassThru `
            -RedirectStandardOutput $out -RedirectStandardError $err -WorkingDirectory $WorkingDirectory
    }
    finally {
        foreach ($k in $envSet) { Remove-Item -Path "env:$k" -ErrorAction SilentlyContinue }
    }
    # Touching Handle keeps ExitCode readable after the process exits (a .NET quirk on Windows).
    $null = $proc.Handle
    return [pscustomobject]@{ Name = $Step.Name; Device = $Step.Device; Process = $proc; Start = Get-Date; Out = $out }
}

function Write-LaneLine {
    param([string]$Name, [string]$Device, $Code, [string]$Detail, [switch]$Quiet)
    if ($Quiet) { return }
    $mark = if ($null -eq $Code) { 'SKIP' } elseif ($Code -eq 0) { 'OK  ' } else { 'FAIL' }
    $colour = if ($null -eq $Code) { 'Yellow' } elseif ($Code -eq 0) { 'Green' } else { 'Red' }
    Write-Host ("  [{0}] {1,-22} {2}  exit {3}  ({4})" -f $mark, $Name, $Device, $(if ($null -eq $Code) { '-' } else { $Code }), $Detail) -ForegroundColor $colour
}

# 'ready' - every required step finished with 0; 'wait' - one is still pending; 'skip' - one failed
# or was skipped, so this step can only report "could not verify".
function Get-StepReadiness {
    param($Step, [hashtable]$Codes)
    if (-not (Test-StepHasField $Step 'Requires')) { return 'ready' }
    foreach ($name in @($Step.Requires)) {
        if (-not $Codes.ContainsKey($name)) { return 'wait' }
        if ($null -eq $Codes[$name] -or [int]$Codes[$name] -ne 0) { return 'skip' }
    }
    return 'ready'
}

# Returns a hashtable step name -> exit code ($null for a skipped step). A failed step does not stop
# its lane: the next step decides through Requires whether it still makes sense to run.
function Invoke-Lanes {
    param(
        [object[]]$Lanes,
        [string]$ChildDir,
        [string]$WorkingDirectory,
        [System.Collections.Generic.List[object]]$Stages,
        [switch]$Quiet,
        [int]$PollMilliseconds = 1000
    )
    $codes = @{}
    # PowerShell's unary comma nests a lane one level deeper at some call sites than at others, so
    # each lane is unwrapped down to its list of steps before it is queued.
    $state = @($Lanes | ForEach-Object {
            $steps = @($_)
            while ($steps.Count -eq 1 -and $steps[0] -is [array]) { $steps = @($steps[0]) }
            [pscustomobject]@{ Steps = [System.Collections.Queue]::new($steps); Current = $null }
        })
    while ($true) {
        $busy = $false
        $progressed = $false
        foreach ($lane in $state) {
            if ($lane.Current -and $lane.Current.Process.HasExited) {
                $lane.Current.Process.WaitForExit()
                $code = $lane.Current.Process.ExitCode
                $codes[$lane.Current.Name] = $code
                $Stages.Add((New-StageRecord -Name $lane.Current.Name -Device $lane.Current.Device -Start $lane.Current.Start -End (Get-Date) -Exit $code))
                Write-LaneLine -Name $lane.Current.Name -Device $lane.Current.Device -Code $code -Detail $lane.Current.Out -Quiet:$Quiet
                $lane.Current = $null
                $progressed = $true
            }
            while (-not $lane.Current -and $lane.Steps.Count -gt 0) {
                $next = $lane.Steps.Peek()
                $readiness = Get-StepReadiness -Step $next -Codes $codes
                if ($readiness -eq 'wait') { break }
                $null = $lane.Steps.Dequeue()
                $progressed = $true
                if ($readiness -eq 'skip') {
                    $codes[$next.Name] = $null
                    $now = Get-Date
                    $Stages.Add((New-StageRecord -Name $next.Name -Device $next.Device -Start $now -End $now -Exit $null))
                    Write-LaneLine -Name $next.Name -Device $next.Device -Code $null -Detail 'a required step did not pass' -Quiet:$Quiet
                    continue
                }
                if (Test-StepHasField $next 'Action') {
                    $start = Get-Date
                    $code = $null
                    try { $code = [int](& $next.Action) } catch { $code = 2 }
                    $codes[$next.Name] = $code
                    $Stages.Add((New-StageRecord -Name $next.Name -Device $next.Device -Start $start -End (Get-Date) -Exit $code))
                    Write-LaneLine -Name $next.Name -Device $next.Device -Code $code -Detail 'in-process' -Quiet:$Quiet
                    continue
                }
                $lane.Current = Start-LaneStep -Step $next -ChildDir $ChildDir -WorkingDirectory $WorkingDirectory
            }
            if ($lane.Current -or $lane.Steps.Count -gt 0) { $busy = $true }
        }
        if (-not $busy) { break }
        $running = @($state | Where-Object { $_.Current }).Count
        if ($running -eq 0 -and -not $progressed) {
            # Every remaining step waits on a name no lane will ever produce - a wiring error in the
            # caller. Skip them rather than spin forever.
            foreach ($lane in $state) {
                while ($lane.Steps.Count -gt 0) {
                    $stuck = $lane.Steps.Dequeue()
                    $codes[$stuck.Name] = $null
                    $now = Get-Date
                    $Stages.Add((New-StageRecord -Name $stuck.Name -Device $stuck.Device -Start $now -End $now -Exit $null))
                    Write-LaneLine -Name $stuck.Name -Device $stuck.Device -Code $null -Detail 'required step never ran' -Quiet:$Quiet
                }
            }
            break
        }
        Start-Sleep -Milliseconds $PollMilliseconds
    }
    return $codes
}
