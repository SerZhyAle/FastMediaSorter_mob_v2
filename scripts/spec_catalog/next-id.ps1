[CmdletBinding()]
param()

. (Join-Path $PSScriptRoot '_lib.ps1')

Write-Output (New-CatalogId)
