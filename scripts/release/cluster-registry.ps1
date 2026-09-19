<#
.SYNOPSIS
    cluster-registry.ps1 (S3286) - read and write the Play Vitals crash-cluster registry from a command line.

.DESCRIPTION
    docs/play-vitals-cluster-registry.jsonl maps a Play console crash-cluster id onto the ticket that owns it,
    including tickets that have been archived. It exists because the cluster id is the only exact key two
    vitals reads of the same crash share, and it lives in no place a catalog query reaches - so a crash fixed
    and archived between two reads was re-parked as a fresh ticket every time Play still reported it.

    The automated filer (scripts/release/lib/play-vitals-filing.ps1) reads and writes the registry on its own.
    This CLI is for the capture step a person or an agent runs by hand (/spec-prerelease step 0.10): Lookup
    before parking a ticket, Register right after parking one.

.PARAMETER Verb
    Lookup   - print the owner of -ClusterId, or nothing. Exit 1 when the cluster is unknown.
    Register - add -ClusterId owned by -TicketId. Refuses an id that is already registered.
    Set      - change -State and/or -FixVersionCode and/or -TicketStatus of an already registered -ClusterId.
    List     - print every row.

.EXAMPLE
    pwsh -NoProfile -File scripts/release/cluster-registry.ps1 -Verb Lookup -ClusterId 8210bf01b4d65c648a82dbf47e0df6ef

.NOTES
    Exit codes:
      0  the verb succeeded (Lookup: the cluster is registered).
      1  Lookup: the cluster is not registered. Register: the cluster is already registered.
      2  the call could not be served - a missing argument, an unknown cluster on Set, an unwritable registry.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)] [ValidateSet('Lookup', 'Register', 'Set', 'List')] [string] $Verb,
    [string] $ClusterId,
    [string] $TicketId,
    [string] $TicketStatus,
    [ValidateSet('open', 'fixed-unpublished', 'fixed-published')] [string] $State,
    [string] $FixVersionCode
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..')).Path
. (Join-Path $PSScriptRoot 'lib/play-vitals-cluster-registry.ps1')
$registryPath = Get-ClusterRegistryPath -ProjectRoot $repoRoot

function Write-Row($Entry) {
    Write-Host ('{0}  {1}  {2}  state {3}  fix {4}  registered {5}' -f $Entry.clusterId, $Entry.ticketId,
        $Entry.ticketStatus, $Entry.state, ($(if ($Entry.fixVersionCode) { $Entry.fixVersionCode } else { '-' })),
        $Entry.registeredUtc)
}

if ($Verb -ne 'List' -and -not $ClusterId) {
    Write-Host 'cluster-registry: -ClusterId is required for this verb.'
    exit 2
}

try {
    $registry = @(Read-ClusterRegistry -Path $registryPath)
} catch {
    Write-Host "cluster-registry: the registry could not be read - $($_.Exception.Message)"
    exit 2
}

switch ($Verb) {
    'List' {
        if ($registry.Count -eq 0) { Write-Host 'cluster-registry: no clusters registered yet.' }
        foreach ($entry in $registry) { Write-Row $entry }
        exit 0
    }
    'Lookup' {
        $entry = Find-ClusterRegistryEntry -Registry $registry -ClusterId $ClusterId
        if ($null -eq $entry) {
            Write-Host "cluster-registry: $ClusterId is not registered - it is a new crash cluster."
            exit 1
        }
        Write-Host "cluster-registry: $ClusterId is already owned - do NOT park a second ticket for it."
        Write-Row $entry
        exit 0
    }
    'Register' {
        if (-not $TicketId) {
            Write-Host 'cluster-registry: -TicketId is required to register a cluster.'
            exit 2
        }
        if ($null -ne (Find-ClusterRegistryEntry -Registry $registry -ClusterId $ClusterId)) {
            Write-Host "cluster-registry: $ClusterId is already registered - use -Verb Set to change it."
            exit 1
        }
        try {
            $added = Add-ClusterRegistryEntry -Path $registryPath -Entry @{
                clusterId      = $ClusterId
                ticketId       = $TicketId
                ticketStatus   = $TicketStatus
                state          = $State
                fixVersionCode = $FixVersionCode
            }
        } catch {
            Write-Host "cluster-registry: the entry was refused - $($_.Exception.Message)"
            exit 2
        }
        Write-Host 'cluster-registry: registered.'
        Write-Row $added
        exit 0
    }
    default {
        $fields = @{}
        if ($State) { $fields['state'] = $State }
        if ($TicketStatus) { $fields['ticketStatus'] = $TicketStatus }
        if ($FixVersionCode) { $fields['fixVersionCode'] = $FixVersionCode }
        if ($TicketId) { $fields['ticketId'] = $TicketId }
        if ($fields.Count -eq 0) {
            Write-Host 'cluster-registry: -Verb Set needs at least one of -State, -TicketStatus, -TicketId, -FixVersionCode.'
            exit 2
        }
        try {
            $updated = Update-ClusterRegistryEntry -Path $registryPath -ClusterId $ClusterId -Fields $fields
        } catch {
            Write-Host "cluster-registry: the update was refused - $($_.Exception.Message)"
            exit 2
        }
        Write-Host 'cluster-registry: updated.'
        Write-Row $updated
        exit 0
    }
}
