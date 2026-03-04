# init-ramdisk.ps1
# Run at Windows startup (via Task Scheduler or shell:startup shortcut)
# Initializes project-local temp directories.
# No external RAM disk is required.

$projectRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")

$dirs = @(
    (Join-Path $projectRoot "temp"),
    (Join-Path $projectRoot "temp\gradle-tmp"),
    (Join-Path $projectRoot "temp\gradle-daemon"),
    (Join-Path $projectRoot "temp\gradle-build-cache"),
    (Join-Path $projectRoot "temp\gradle-journal"),
    (Join-Path $projectRoot "temp\gradle-notifications"),
    (Join-Path $projectRoot "temp\FastMediaSorter_build")
)

foreach ($dir in $dirs) {
    if (-not (Test-Path $dir)) {
        New-Item -ItemType Directory -Path $dir -Force | Out-Null
        Write-Host "[init-temp] Created: $dir"
    }
}

    Write-Host "[init-temp] Project temp directories initialized."
