#requires -Version 7.0
# Fixture for the reaper contract suite: a repository script that is idle itself but supervises a
# live child which is not a repository script. The detached dev-monitor wrapper has this exact
# shape - 1757 minutes old on 1.1 s of CPU - and no age or CPU threshold can tell it from an
# abandoned process, so only the live-child rule protects it.
# Exit codes: 0 always.
Start-Process -FilePath 'pwsh' -ArgumentList '-NoProfile', '-Command', 'Start-Sleep -Seconds 300' -WindowStyle Hidden | Out-Null
Start-Sleep -Seconds 300
