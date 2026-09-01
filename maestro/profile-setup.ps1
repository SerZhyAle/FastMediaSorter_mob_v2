# PowerShell Profile - FastMediaSorter Maestro Setup
# Add this to your PowerShell profile for permanent PATH configuration

. "$PSScriptRoot\..\scripts\utils\project-paths.ps1"

# Add Node.js and npm to PATH (for Maestro CLI). Discovered, not assumed (S2326).
$NodePath = try { Split-Path -Parent (Get-ToolPath -Tool Node -Quiet) } catch { $null }
$NpmPath = try { Split-Path -Parent (Get-ToolPath -Tool Npm -Quiet) } catch { $null }

if ($NodePath -and -not ($env:PATH -like "*$NodePath*")) {
    $env:PATH = "$NodePath;$env:PATH"
}

if ($NpmPath -and -not ($env:PATH -like "*$NpmPath*")) {
    $env:PATH = "$NpmPath;$env:PATH"
}

# Convenient aliases for FastMediaSorter development
function maestro-test {
    param(
        [string]$Test = "all",
        [switch]$Debug
    )
    
    $debugArg = if ($Debug) { "--debug" } else { "" }
    
    if ($Test -eq "all") {
        maestro-cli test smoke/ $debugArg
    }
    else {
        maestro-cli test smoke/$Test.yaml $debugArg
    }
}

function maestro-studio {
    maestro-cli studio
}

function fms-build {
    .\dev\build-with-version.ps1 -Flavor standard
}

function fms-test {
    .\maestro\maestro-verify.ps1
}

Write-Host "✅ FastMediaSorter profile loaded" -ForegroundColor Green
Write-Host "   Available aliases:" -ForegroundColor Cyan
Write-Host "   • maestro-test [test_name] [-Debug]  - Run Maestro tests"
Write-Host "   • maestro-studio                     - Open Maestro Studio"
Write-Host "   • fms-build                          - Build app"
Write-Host "   • fms-test                           - Run system verification"
