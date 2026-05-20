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
