#requires -Version 7.0
# Fixture for the reaper contract suite. Started WITHOUT -Required and with an open stdin pipe it
# reproduces the S2610 hang exactly: PowerShell prompts for the mandatory parameter and blocks
# before this file's first statement. Started WITH it, it is a long-running but healthy process.
# Exit codes: 0 always.
param(
    [Parameter(Mandatory)][string]$Required,
    [switch]$Loop
)
Start-Sleep -Seconds 300
