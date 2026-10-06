<#
.SYNOPSIS
    Normalize the hand-maintained documentation hubs using the shared portal shell.
.PARAMETER Check
    Check generated shell freshness without changing files.
.NOTES
    Exit codes:
      0 - every hub is current, or the requested shell updates were written.
      1 - Check found at least one stale shell.
#>
# Normalize the hand-maintained portal hubs. Recipe/glossary/index pages use their owning generators.
[CmdletBinding()]
param([switch]$Check)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'DocumentationShell.ps1')
$root = Join-Path $PSScriptRoot '../../documentation'
$failed = $false
$designFile = Join-Path $root 'design-system/index.html'
$designOld = [IO.File]::ReadAllText($designFile)
$designNew = ConvertTo-DocumentationShell -Html $designOld -RelativePath 'design-system/index.html' -DocumentationRoot $root
if ($Check) { if ($designOld -ne $designNew) { Write-Host 'OUTDATED: design-system/index.html'; $failed = $true } }
else { [IO.File]::WriteAllText($designFile, $designNew, [Text.UTF8Encoding]::new($false)) }
foreach ($stem in 'index', 'overview') {
    foreach ($suffix in '', '-ru', '-uk') {
        $relative = "$stem$suffix.html"
        $file = Join-Path $root $relative
        $old = [IO.File]::ReadAllText($file)
        $new = ConvertTo-DocumentationShell -Html $old -RelativePath $relative -DocumentationRoot $root
        if ($Check) {
            if ($old -ne $new) { Write-Host "OUTDATED: $relative"; $failed = $true }
        } else { [IO.File]::WriteAllText($file, $new, [Text.UTF8Encoding]::new($false)) }
    }
}
if ($failed) { exit 1 }
Write-Host 'Documentation hub shell: PASS'
exit 0
