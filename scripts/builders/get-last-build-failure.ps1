param(
    [string]$LogPath,
    [int]$MaxLines = 200
)

$ErrorActionPreference = "Stop"

function Get-ProjectRoot {
    return Resolve-Path (Join-Path $PSScriptRoot "..\..")
}

function Get-SelectedLogPath {
    param(
        [string]$ExplicitLogPath
    )

    if ($ExplicitLogPath) {
        try {
            return (Resolve-Path $ExplicitLogPath).Path
        }
        catch {
            Write-Output "Build log not found: $ExplicitLogPath"
            Write-Output "Save the next build log under temp/build_<tag>.log and retry."
            exit 2
        }
    }

    $projectRoot = Get-ProjectRoot
    $tempDir = Join-Path $projectRoot "temp"
    if (-not (Test-Path $tempDir)) {
        Write-Output "No build log found in temp/."
        Write-Output "Save the next build log under temp/build_<tag>.log and retry."
        exit 2
    }

    $latestLog = Get-ChildItem -Path $tempDir -Filter '*build*.log' -File |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1

    if ($null -eq $latestLog) {
        Write-Output "No build log found in temp/."
        Write-Output "Save the next build log under temp/build_<tag>.log and retry."
        exit 2
    }

    return $latestLog.FullName
}

function Write-Section {
    param(
        [string]$Title,
        [string[]]$Lines
    )

    if ($Lines.Count -eq 0) {
        return
    }

    Write-Output $Title
    $Lines | ForEach-Object { Write-Output $_ }
    Write-Output ""
}

$selectedLogPath = Get-SelectedLogPath -ExplicitLogPath $LogPath
$content = Get-Content -Path $selectedLogPath -Raw
if ([string]::IsNullOrWhiteSpace($content)) {
    Write-Output "Build log is empty: $selectedLogPath"
    exit 3
}

$lines = [System.IO.File]::ReadAllLines($selectedLogPath)
$compilerLines = @($lines | Where-Object { $_ -match '^\s*e: file:///.+$' })
$failedTaskLines = @($lines | Where-Object { $_ -match '^\s*> Task .+ FAILED$' })

# S2377: an install refusal is printed by the installer BEFORE gradle's own FAILURE: block - in the
# reference log temp/check_fast_20260902_165336.log the INSTALL_FAILED lines sit at 97-168 and the
# marker at 187 - so it reaches none of the block-bound output below, and the digest built on top of
# this script cannot name a cause it is never handed. What the operator saw instead was
# "Could not load test results", which reads as a failed Room migration: the most expensive false red
# in the whole pre-release sweep. Scanned across the whole file for exactly that reason.
#
# One line per distinct token: the same refusal repeats (seven times in the reference log) and the
# first occurrence is the informative one, because that is the line carrying both version codes.
$installFailureLines = @()
$seenInstallTokens = @{}
foreach ($logLine in $lines) {
    if ($logLine -match 'INSTALL_FAILED_[A-Z_]+') {
        $installToken = $Matches[0]
        if (-not $seenInstallTokens.ContainsKey($installToken)) {
            $seenInstallTokens[$installToken] = $true
            $installFailureLines += $logLine.Trim()
        }
    }
}
$failureStart = -1

for ($index = 0; $index -lt $lines.Length; $index++) {
    if ($lines[$index] -match '^\s*FAILURE:') {
        $failureStart = $index
        break
    }
}

if ($failureStart -ge 0) {
    $failureEnd = $lines.Length - 1
    for ($index = $failureStart + 1; $index -lt $lines.Length; $index++) {
        if ($lines[$index] -match '^BUILD FAILED in ') {
            $failureEnd = $index - 1
            break
        }
    }

    $failureBlock = @()
    for ($index = $failureStart; $index -le $failureEnd; $index++) {
        $failureBlock += $lines[$index]
    }

    Write-Output "Build log: $selectedLogPath"
    Write-Output ""
    Write-Section -Title "Compiler errors:" -Lines $compilerLines
    # Before the failed task, because the task line is the symptom and this is the cause.
    Write-Section -Title "Install failures:" -Lines $installFailureLines
    Write-Section -Title "Failed tasks:" -Lines $failedTaskLines
    Write-Section -Title "Failure block:" -Lines $failureBlock
    exit 0
}

if ($content -match 'BUILD SUCCESSFUL') {
    Write-Output "Last saved build completed successfully: $selectedLogPath"
    exit 0
}

$tailCount = [Math]::Min($MaxLines, $lines.Length)
$tailLines = @($lines | Select-Object -Last $tailCount)
Write-Output "No FAILURE marker found in $selectedLogPath. Showing the last $tailCount line(s)."
Write-Output ""
$tailLines | ForEach-Object { Write-Output $_ }
exit 0
