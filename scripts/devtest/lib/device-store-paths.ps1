#requires -Version 7.0
<#
.SYNOPSIS
    Declares WHERE the device stores live and HOW a serial becomes a file name (S3036).

.DESCRIPTION
    Two coordination stores sit at the root of temp/ with opposite lifecycles (docs/DEV_OPS.md):
    DEVICE.LEASES/ is ephemeral and swept by session liveness, DEVICE.REGISTRY/ is durable and never
    swept. S3201 added a third, DEVICE.STATE/ - the per-device journal of state a test changed and must
    put back, durable for the same reason as the registry. Every OTHER coordination directory at that root resolves its path through
    .sza-profile.json, read by the canon-shipped harness. These two cannot: the harness reads no
    device path at all (verified 2026-09-12 across tools/harness - a device appears there only as a
    serial in the agent chat), and Get-SzaProfileValue throws on an unknown key by design (S2705),
    so a project-only paths.* key would be unreadable until the canon's own defaults declared a key
    the canon never reads. Hence a repository-local declaration, beside the scripts that own the
    stores - the same conclusion, for the same reason, as S3030's temp-root allowlist.

    Before S3036 the address was copied instead of declared: four production sites carried
    'temp\DEVICE.*' (device-lease.ps1, device-registry.ps1, device-ready.ps1, dev-monitor-writer.ps1)
    and three of them re-implemented the ':' -> '_' serial encoding, so "where the store is and how
    it is addressed" was written down seven times for two directories. The consequence is not a bug
    today but a silent one later: a foreign reader with its own copy is how a moved store keeps
    answering "absent" instead of failing.

    Consumers: the two owning CLIs (scripts/devtest/device-lease.ps1, device-registry.ps1), the
    readiness probe (device-ready.ps1, read-only), the monitor writer (scripts/utils/dev-monitor-writer.ps1,
    read-only) and scripts/utils/temp-root-inventory.ps1, which needs the leaf names alone so the
    archiver's protected set follows a rename with no edit of its own.

    The two contract suites of device-lease.tests and adb.tests keep their own literals on purpose:
    a test that derives its expectation from the declaration under test cannot catch a wrong
    declaration, and both of them seed fixtures in the REAL store, so their copies are independent
    pins (S3036 ADR-4).

.EXAMPLE
    . (Join-Path $PSScriptRoot 'lib\device-store-paths.ps1')
    $leaseDir = Get-DeviceStoreDir -RepoRoot $root -Store Lease
    $record = Get-DeviceStoreRecordPath -RepoRoot $root -Store Registry -Serial '192.168.1.5:5555'

.NOTES
    Dot-sourced library. It defines functions and assigns nothing at script scope, because a
    dot-sourced file assigns into its CALLER's scope: S2441 records ten scripts under scripts/ whose
    parameters collided with a forwarder's internals. RepoRoot is a parameter rather than
    $PSScriptRoot for the same reason the neighbouring libraries take it - a dot-sourced file's idea
    of its own location is the caller's.

    NOTHING here creates a directory. The registry's read-only verbs, the readiness probe and the
    monitor writer must leave a missing store missing (S2855), so path arithmetic and directory
    creation stay separate: the two writers create at their own call sites, where the decision to
    write is actually taken.

    Exit codes: none of its own. Dot-sourcing defines the functions and returns; an unknown -Store
    is refused at parameter-bind time, which is a caller bug rather than a runtime condition.
#>

function Get-DeviceStoreDirName {
    <#
    .SYNOPSIS
        The top-level name of one device store under temp/ - the leaf alone.

    .DESCRIPTION
        The one place either name is spelled out. Kept separate from Get-DeviceStoreDir because
        scripts/utils/temp-root-inventory.ps1 answers about the TOP LEVEL of temp/ and a full path
        would have to be re-split there, which is how the leaf would acquire a second spelling.
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][ValidateSet('Lease', 'Registry', 'State')][string]$Store
    )
    switch ($Store) {
        'Lease' { return 'DEVICE.LEASES' }
        'Registry' { return 'DEVICE.REGISTRY' }
        # S3201: the per-device journal of state a test changed and must put back. Durable like the
        # registry - an unrestored journal is exactly the record the next run needs.
        'State' { return 'DEVICE.STATE' }
    }
}

function Get-DeviceStoreDir {
    <#
    .SYNOPSIS
        Absolute directory of one device store. Never creates it.

    .PARAMETER RepoRoot
        Repository root, or any root a caller wants the store resolved under - the contract suite
        points it at a fixture directory. That is why the 'temp' segment is joined here rather than
        resolved through Get-SzaPath 'tempDir': the profile resolves against the REAL project root,
        which would silently pull a sandboxed caller back to the live store.
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$RepoRoot,
        [Parameter(Mandatory)][ValidateSet('Lease', 'Registry', 'State')][string]$Store
    )
    return (Join-Path (Join-Path $RepoRoot 'temp') (Get-DeviceStoreDirName -Store $Store))
}

function ConvertTo-DeviceSerialFileName {
    <#
    .SYNOPSIS
        The serial-to-file-name encoding shared by both stores.

    .DESCRIPTION
        ':' is legal in an adb serial (192.168.1.5:5555) and illegal in a Windows path segment, so
        it is encoded rather than rejected. One key, one shape (S1926): a serial found in either
        store is spelled identically in both, and this is the only implementation of that rule.
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Serial
    )
    return ($Serial -replace ':', '_')
}

function Get-DeviceStoreRecordPath {
    <#
    .SYNOPSIS
        Absolute path of one device's record in one store. Never creates anything.

    .DESCRIPTION
        Exists because a caller that needs a record rather than a directory would otherwise rebuild
        the encoding itself - which is what device-ready.ps1 did inline before S3036.
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$RepoRoot,
        [Parameter(Mandatory)][ValidateSet('Lease', 'Registry', 'State')][string]$Store,
        [Parameter(Mandatory)][string]$Serial
    )
    $dir = Get-DeviceStoreDir -RepoRoot $RepoRoot -Store $Store
    return (Join-Path $dir ((ConvertTo-DeviceSerialFileName -Serial $Serial) + '.json'))
}
