<#
.SYNOPSIS
    Tells "another process is holding this module's build output" apart from "the change under test
    is broken".

.DESCRIPTION
    S2584: a hung Gradle test worker outlives the build that spawned it and keeps R.jar open. Every
    later entry point for that variant starts with process<Variant>Resources, which has to rewrite
    that jar, so it dies with `java.io.IOException: Couldn't delete .. R.jar` - a message that reads
    as a defect in whatever change is being built. Measured twice on 2026-09-05; one holder kept the
    jar for 102 minutes while ten sessions queued behind a domain the lock reported FREE.

    The lock mechanism cannot answer this and is not asked to. It tracks the wrapper pid, and the
    holder is a worker the wrapper spawned - not the wrapper, not even the daemon. `gradlew --status`
    does not list it either. So occupancy is judged the one way that worked in both incidents: open
    the file. The lock keeps answering "who holds the domain"; this answers "is the build directory
    actually free", and the two never contradict each other because they are different questions.

    Holder identity is deliberately NOT what the verdict rests on. Test-BuildOutputLocked decides;
    Get-BuildOutputHolder only supplies a name for the operator, and returns nothing without
    changing the verdict. A verdict resting on a process-scan heuristic would turn green on a busy
    directory the first time the heuristic missed.

    Dot-source this file; it defines functions and never exits.
#>

# Deliberately no Set-StrictMode here: this file is dot-sourced, so it would impose strict mode on
# every consumer's whole scope - a side effect none of them asked for. Same reasoning as
# gradle-run-verdict.ps1 next to it.

function Get-BuildOutputVariantDir {
    <#
        The intermediates directory is named for the variant with a lower-case first letter
        (standardDebug), while the Gradle task segment is capitalised (processStandardDebugResources).
        A module with no flavor dimension (watchface) has no variant segment at all and its directory
        is just the build type.
    #>
    param(
        [string] $Variant,
        [Parameter(Mandatory)][string] $BuildType
    )
    if ([string]::IsNullOrWhiteSpace($Variant)) { return $BuildType.ToLowerInvariant() }
    return $Variant.Substring(0, 1).ToLowerInvariant() + $Variant.Substring(1) + $BuildType
}

function Get-BuildOutputRJarPath {
    <#
        Found by walking the tree, never by composing the path from a rule. A composed path is wrong
        for the flavorless module and silently points at nothing, and a probe that observes nothing
        reports "free" - the exact false green this whole file exists to prevent.
    #>
    param(
        [Parameter(Mandatory)][string] $ProjectRoot,
        [Parameter(Mandatory)][string] $Module,
        [string] $VariantDir
    )
    $root = Join-Path $ProjectRoot "$Module\build\intermediates\compile_and_runtime_r_class_jar"
    if (-not (Test-Path -LiteralPath $root)) { return @() }
    $found = @(Get-ChildItem -LiteralPath $root -Recurse -Filter 'R.jar' -File -ErrorAction SilentlyContinue)
    if ([string]::IsNullOrWhiteSpace($VariantDir)) {
        return @($found | ForEach-Object { $_.FullName })
    }
    # Scoped to this run's variant: a held noLegalDebug jar does not block a standardDebug run, and
    # refusing on one would be a false alarm against a directory this run never touches.
    $segment = [regex]::Escape("\$VariantDir\")
    return @($found | Where-Object { $_.FullName -match $segment } | ForEach-Object { $_.FullName })
}

function Test-BuildOutputLocked {
    <#
        The whole verdict. FileShare::None fails when anyone else holds the file, which is what a
        live worker does and what a finished one does not. Only IOException counts as held: an ACL
        problem is a different failure and must not be reported as another agent's build.
    #>
    param([Parameter(Mandatory)][string] $Path)

    if (-not (Test-Path -LiteralPath $Path)) { return $false }
    try {
        $stream = [System.IO.File]::Open(
            $Path,
            [System.IO.FileMode]::Open,
            [System.IO.FileAccess]::ReadWrite,
            [System.IO.FileShare]::None)
        $stream.Dispose()
        return $false
    } catch [System.IO.IOException] {
        return $true
    } catch {
        return $false
    }
}

function Get-BuildOutputHolder {
    <#
        Best effort, and never load-bearing. A Gradle test worker names itself in its own command
        line via -Dorg.gradle.internal.worker.tmpdir, which is how the holder was identified in both
        incidents; the daemon that spawned it does not carry that switch and is correctly skipped.
    #>
    param([Parameter(Mandatory)][string] $Module)

    $procs = @(Get-CimInstance Win32_Process -Filter "Name='java.exe'" `
            -Property ProcessId, CreationDate, CommandLine, KernelModeTime, UserModeTime `
            -ErrorAction SilentlyContinue)
    $moduleWorkerDir = [regex]::Escape("$Module\build\tmp\test")
    $holders = @()
    foreach ($p in $procs) {
        if ([string]::IsNullOrWhiteSpace($p.CommandLine)) { continue }
        if ($p.CommandLine -notmatch 'org\.gradle\.internal\.worker\.tmpdir') { continue }
        if ($p.CommandLine -notmatch $moduleWorkerDir) { continue }
        # 100-ns ticks to seconds. High CPU against a long wall clock is what separates a hung worker
        # from a working one - neither number alone says anything.
        $cpuSeconds = [math]::Round((([double]$p.KernelModeTime + [double]$p.UserModeTime) / 1e7), 1)
        $holders += [pscustomobject]@{
            ProcessId   = $p.ProcessId
            StartTime   = $p.CreationDate
            CpuSeconds  = $cpuSeconds
            CommandLine = $p.CommandLine
        }
    }
    return @($holders)
}

function Write-BuildOutputHolderDiagnosis {
    <#
        The missing surface. The only signal today is Gradle's own "Couldn't delete" line, which
        names a file and no cause, so the reader attributes it to their own change and starts
        editing working code.
    #>
    param(
        [Parameter(Mandatory)][string] $Module,
        [Parameter(Mandatory)][string[]] $LockedPaths,
        [string] $LogPath
    )

    $lines = @()
    $lines += ''
    $lines += "check-standard-fast: $Module build output is HELD by another process - this run produced NO verdict."
    $lines += 'Nothing was proven about your change; do not read this as a build failure and do not start fixing code.'
    foreach ($path in $LockedPaths) { $lines += "  held: $path" }

    $holders = @(Get-BuildOutputHolder -Module $Module)
    if ($holders.Count -gt 0) {
        foreach ($h in $holders) {
            $lines += "  holder: pid $($h.ProcessId)  started $($h.StartTime)  cpu $($h.CpuSeconds)s"
            $lines += "          $($h.CommandLine)"
        }
        $lines += 'A Gradle test worker that outlived its build. High CPU against a long wall clock means hung, not busy.'
    } else {
        $lines += '  holder: not identified - the file is held, but no Gradle test worker of this module claims it.'
    }

    $lines += 'The lock is not lying: it tracks the wrapper pid, and this holder is a worker outside the mechanism,'
    $lines += 'so Build.* can legitimately read FREE while the directory is occupied.'
    $lines += 'CLAUDE.md Rule 35 forbids killing a process this session did not start - the reaper is'
    $lines += 'scripts/utils/agent-watchdog.ps1; check whether it is running before sweeping by hand. See PLAN/S2584.'

    foreach ($line in $lines) { Write-Host $line -ForegroundColor Yellow }
    if ($LogPath) {
        [System.IO.File]::AppendAllLines($LogPath, [string[]]$lines)
    }
}
