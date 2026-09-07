@echo off
rem Wrapper that lets prerelease-configure.ps1's Get-Adb pick this stub off PATH as "adb" (S2709).
rem %* keeps adb's own -s <id> and any spaced argument intact; adb-stub.ps1 reads them from $args.
pwsh -NoProfile -File "%~dp0adb-stub.ps1" %*
exit /b %ERRORLEVEL%
