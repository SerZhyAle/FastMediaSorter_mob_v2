# init-ramdisk.ps1
# Run at Windows startup (via Task Scheduler or shell:startup shortcut)
# Recreates directory structure on R: RAM disk that is wiped on every reboot.
# All paths are targets of symbolic links in %USERPROFILE%\.gradle and %TEMP%.

$dirs = @(
    "R:\temp",
    "R:\.gradle\daemon",
    "R:\.gradle\caches\build-cache-1",
    "R:\.gradle\caches\journal-1",
    "R:\.gradle\notifications",
    "R:\FastMediaSorter_build"
)

foreach ($dir in $dirs) {
    if (-not (Test-Path $dir)) {
        New-Item -ItemType Directory -Path $dir -Force | Out-Null
        Write-Host "[init-ramdisk] Created: $dir"
    }
}

Write-Host "[init-ramdisk] RAM disk initialized."
