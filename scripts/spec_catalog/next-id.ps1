[CmdletBinding()]
param()

. (Join-Path $PSScriptRoot '_lib.ps1')

# S1437: read-only, but still serialized - New-CatalogId is "max + 1" with no reservation, so two
# callers reading concurrently is exactly how one id gets handed out twice.
# stdout stays the bare S#### token: /spec captures it directly as the ticket id.
Enter-CatalogLock
try { $nextId = New-CatalogId }
finally { Exit-CatalogLock }

Write-Output $nextId
