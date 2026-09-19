# play-vitals-cluster-registry.ps1 (S3286) - the cluster-id index the Play vitals filer dedups on. Dot-source it.
#
# Why a registry file and not the catalog: the crash-cluster id is the only exact key two vitals reads of the
# same crash share, and it lives nowhere a catalog query can reach - only in a spec body and in
# docs/PLAY_PUBLISHING_STATE.md. A cluster already fixed and ARCHIVED is exactly the set the name-based dedup
# in play-vitals-filing.ps1 cannot see, so every crash fixed between two reads was a candidate for re-filing.
#
# One JSON object per line in docs/play-vitals-cluster-registry.jsonl, plus a leading `_comment` header line:
#
#   {"clusterId","ticketId","ticketStatus","fixVersionCode","state","registeredUtc","updatedUtc"}
#
# `state` is one of:
#   open              - the ticket owns the cluster and the fix is not in the tree yet
#   fixed-unpublished - the fix is in the tree but no released build carries it, so Play keeps reporting it
#   fixed-published   - a released build carries the fix; a later report is a genuine regression
#
# Every write is UTF-8 without BOM and LF, because the file is read by scripts on both line endings.

Set-StrictMode -Version Latest

$script:ClusterRegistryStates = @('open', 'fixed-unpublished', 'fixed-published')
$script:ClusterRegistryRelativePath = 'docs/play-vitals-cluster-registry.jsonl'
$script:ClusterRegistryHeader = '{"_comment": "Cluster-to-ticket registry for Play Vitals crash dedup (S3286). One JSON object per line. Machine-written by scripts/release/lib/play-vitals-cluster-registry.ps1, never hand-edited."}'

function Get-ClusterRegistryPath {
    [CmdletBinding()]
    param([Parameter(Mandatory)] [string] $ProjectRoot)
    return (Join-Path $ProjectRoot $script:ClusterRegistryRelativePath)
}

# StrictMode turns a missing property into a throw, so every field read goes through this.
function Get-ClusterRegistryField {
    param($Entry, [string] $Name)
    if ($null -eq $Entry) { return $null }
    if ($Entry -is [hashtable]) {
        if ($Entry.ContainsKey($Name)) { return $Entry[$Name] }
        return $null
    }
    $property = $Entry.PSObject.Properties[$Name]
    if ($null -eq $property) { return $null }
    return $property.Value
}

function Read-ClusterRegistry {
    [CmdletBinding()]
    param([Parameter(Mandatory)] [string] $Path)
    if (-not (Test-Path -LiteralPath $Path)) { return @() }
    $entries = [System.Collections.Generic.List[object]]::new()
    foreach ($line in [System.IO.File]::ReadAllLines($Path)) {
        $text = "$line".Trim()
        if (-not $text) { continue }
        $entry = $null
        # -DateKind String: an ISO timestamp read back as [datetime] re-serialises in the session culture,
        # so the two Utc fields would drift out of ISO on the first update of a row.
        try { $entry = $text | ConvertFrom-Json -DateKind String } catch {
            throw "play-vitals-cluster-registry: $Path carries a line that is not JSON - $text"
        }
        if ($null -eq $entry) { continue }
        if ($null -ne $entry.PSObject.Properties['_comment']) { continue }
        $entries.Add($entry)
    }
    return $entries.ToArray()
}

function Find-ClusterRegistryEntry {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)] [AllowEmptyCollection()] [AllowNull()] [object[]] $Registry,
        [Parameter(Mandatory)] [string] $ClusterId
    )
    if ($null -eq $Registry -or -not $ClusterId) { return $null }
    foreach ($entry in $Registry) {
        if ($null -eq $entry) { continue }
        if ((Get-ClusterRegistryField -Entry $entry -Name 'clusterId') -eq $ClusterId) { return $entry }
    }
    return $null
}

function ConvertTo-ClusterRegistryLine {
    param([Parameter(Mandatory)] [hashtable] $Entry)
    # Fixed key order, so a hand read of the file and a diff of it both stay legible.
    $ordered = [ordered] @{}
    foreach ($key in @('clusterId', 'ticketId', 'ticketStatus', 'fixVersionCode', 'state', 'registeredUtc', 'updatedUtc')) {
        $ordered[$key] = $Entry[$key]
    }
    return ([pscustomobject] $ordered | ConvertTo-Json -Compress -Depth 4)
}

function Add-ClusterRegistryEntry {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)] [string] $Path,
        [Parameter(Mandatory)] [hashtable] $Entry
    )
    foreach ($required in @('clusterId', 'ticketId')) {
        if (-not $Entry.ContainsKey($required) -or -not "$($Entry[$required])".Trim()) {
            throw "play-vitals-cluster-registry: an entry needs a non-empty '$required'."
        }
    }
    $state = if ($Entry.ContainsKey('state') -and "$($Entry['state'])".Trim()) { "$($Entry['state'])" } else { 'open' }
    if ($script:ClusterRegistryStates -notcontains $state) {
        throw "play-vitals-cluster-registry: state '$state' is not one of $($script:ClusterRegistryStates -join ', ')."
    }
    $now = [DateTime]::UtcNow.ToString('yyyy-MM-ddTHH:mm:ssZ')
    $row = @{
        clusterId      = "$($Entry['clusterId'])"
        ticketId       = "$($Entry['ticketId'])"
        ticketStatus   = if ($Entry.ContainsKey('ticketStatus') -and $Entry['ticketStatus']) { "$($Entry['ticketStatus'])" } else { 'Draft' }
        fixVersionCode = if ($Entry.ContainsKey('fixVersionCode') -and $Entry['fixVersionCode']) { "$($Entry['fixVersionCode'])" } else { $null }
        state          = $state
        registeredUtc  = $now
        updatedUtc     = $now
    }
    $directory = Split-Path -Parent $Path
    if ($directory -and -not (Test-Path -LiteralPath $directory)) {
        $null = New-Item -ItemType Directory -Force -Path $directory
    }
    $encoding = [System.Text.UTF8Encoding]::new($false)
    if (-not (Test-Path -LiteralPath $Path)) {
        [System.IO.File]::WriteAllText($Path, ($script:ClusterRegistryHeader + "`n"), $encoding)
    }
    [System.IO.File]::AppendAllText($Path, ((ConvertTo-ClusterRegistryLine -Entry $row) + "`n"), $encoding)
    return [pscustomobject] $row
}

function Update-ClusterRegistryEntry {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)] [string] $Path,
        [Parameter(Mandatory)] [string] $ClusterId,
        [Parameter(Mandatory)] [hashtable] $Fields
    )
    if (-not (Test-Path -LiteralPath $Path)) {
        throw "play-vitals-cluster-registry: no registry at $Path - nothing to update."
    }
    $lines = [System.IO.File]::ReadAllLines($Path)
    $next = [System.Collections.Generic.List[string]]::new()
    $found = $false
    $updated = $null
    foreach ($line in $lines) {
        $text = "$line".Trim()
        if (-not $text) { continue }
        $entry = $null
        try { $entry = $text | ConvertFrom-Json -DateKind String } catch { $entry = $null }
        if ($null -eq $entry -or $null -ne $entry.PSObject.Properties['_comment'] -or
            (Get-ClusterRegistryField -Entry $entry -Name 'clusterId') -ne $ClusterId) {
            $next.Add($text)
            continue
        }
        $row = @{}
        foreach ($key in @('clusterId', 'ticketId', 'ticketStatus', 'fixVersionCode', 'state', 'registeredUtc', 'updatedUtc')) {
            $row[$key] = Get-ClusterRegistryField -Entry $entry -Name $key
        }
        foreach ($key in $Fields.Keys) { $row[$key] = $Fields[$key] }
        if ($script:ClusterRegistryStates -notcontains "$($row['state'])") {
            throw "play-vitals-cluster-registry: state '$($row['state'])' is not one of $($script:ClusterRegistryStates -join ', ')."
        }
        $row['updatedUtc'] = [DateTime]::UtcNow.ToString('yyyy-MM-ddTHH:mm:ssZ')
        $updated = [pscustomobject] $row
        $found = $true
        $next.Add((ConvertTo-ClusterRegistryLine -Entry $row))
    }
    if (-not $found) {
        throw "play-vitals-cluster-registry: cluster $ClusterId is not in $Path."
    }
    [System.IO.File]::WriteAllText($Path, (($next -join "`n") + "`n"), [System.Text.UTF8Encoding]::new($false))
    return $updated
}
