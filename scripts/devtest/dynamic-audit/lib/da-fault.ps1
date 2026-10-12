#requires -Version 7.0
<#
.SYNOPSIS
    Fault catalogue reader and emulator fault injectors of the S4166 dynamic-audit stand (scene C15).

.DESCRIPTION
    Dot-source after lib/da-common.ps1. Functions only; nothing is assigned at script scope.

      Get-DaFaultCatalogPath   PLAN/S4166_deep-dynamic-audit/audit/faults.json under the repo root
      Get-DaFaultCatalog       reads the catalogue; throws on another schemaVersion or a shape error
      Test-DaFaultCatalog      the shape errors of a catalogue (pure): every fault names its boundary,
                               moment, expected outcome, invariant and status; an injectable fault names a
                               known injector, an open one its reason, owner and next step
      Get-DaFaultInjectorNames the injectors this library implements
      Test-DaPathInside        true when a device path lies inside a root (pure; '..' and other roots refused)
      New-DaCorrelationId      the id that ties a fault record, a scene action and the run record together
      Get-DaDeviceFreeBytes    bytes available to apps on the filesystem holding a device path
      Invoke-DaFault           runs one injector and returns the fault record: fault id, correlation id,
                               boundary, declared moment, host monotonic injection time and its distance
                               from the operation start, what was done, and the cleanup it needs
      Clear-DaFault            undoes an emulator-wide condition a fault record says it left (filler
                               file, revoked permission); idempotent

    WHERE A FAULT MAY ACT. Every file an injector creates or removes must lie inside the run's own fixture
    directory (Test-DaPathInside refuses anything else before a command is sent). Three faults change an
    emulator-wide condition by design - free space (the filler is still a file inside the run directory,
    so the free-space drop is the only outside effect), a runtime permission, the app process - and each
    such record carries a cleanup that Clear-DaFault applies in the scene's finally block.

    TESTABILITY. Every device call goes through an -Adb scriptblock (default: Invoke-DaAdb on the serial),
    so the suite drives the injectors against a fake device without hardware.
#>

function Get-DaFaultCatalogPath {
    <#
    .SYNOPSIS
        Path of the fault catalogue in the ticket's audit data folder.
    #>
    return (Join-Path (Get-DaPaths).RepoRoot 'PLAN/S4166_deep-dynamic-audit/audit/faults.json')
}

function Get-DaFaultInjectorNames {
    <#
    .SYNOPSIS
        Injector keys implemented by Invoke-DaFault.
    #>
    return @('remove-source', 'corrupt-media', 'zero-byte-media', 'fill-destination', 'revoke-permission', 'kill-process', 'long-name')
}

function Test-DaFaultCatalog {
    <#
    .SYNOPSIS
        Returns the shape errors of a fault catalogue (empty when valid). Pure.
    .PARAMETER Catalog
        Catalogue object (Read-DaJson output).
    #>
    param([Parameter(Mandatory)]$Catalog)
    $errors = [System.Collections.Generic.List[string]]::new()
    if ($Catalog.schemaVersion -ne 1) { $errors.Add("schemaVersion $($Catalog.schemaVersion) is not 1") }
    $bounds = @($Catalog.boundaries)
    $known = Get-DaFaultInjectorNames
    $seen = @{}
    foreach ($f in @($Catalog.faults)) {
        $id = [string]$f.id
        if ($id -notmatch '^F\d{2}$') { $errors.Add("fault id '$id' is not F<nn>"); continue }
        if ($seen.ContainsKey($id)) { $errors.Add("duplicate fault id $id") }
        $seen[$id] = $true
        foreach ($k in 'title', 'atomic', 'boundary', 'operation', 'moment', 'expectedOutcome', 'invariant', 'status') {
            if ([string]::IsNullOrWhiteSpace([string]$f.$k)) { $errors.Add("$id has no $k") }
        }
        if ($f.boundary -and $bounds -notcontains [string]$f.boundary) { $errors.Add("$id boundary '$($f.boundary)' is not a declared boundary") }
        switch ([string]$f.status) {
            'injectable' { if ($known -notcontains [string]$f.injector) { $errors.Add("$id is injectable but names no known injector ('$($f.injector)')") } }
            'open' { foreach ($k in 'reason', 'owner', 'nextStep') { if ([string]::IsNullOrWhiteSpace([string]$f.$k)) { $errors.Add("$id is open without $k") } } }
            default { $errors.Add("$id status '$($f.status)' is neither injectable nor open") }
        }
    }
    if ($seen.Count -eq 0) { $errors.Add('the catalogue lists no fault') }
    return $errors.ToArray()
}

function Get-DaFaultCatalog {
    <#
    .SYNOPSIS
        Reads and checks the fault catalogue; throws when it is absent or malformed.
    .PARAMETER Path
        Catalogue path; defaults to Get-DaFaultCatalogPath.
    #>
    param([string]$Path)
    if (-not $Path) { $Path = Get-DaFaultCatalogPath }
    $cat = Read-DaJson -Path $Path
    if (-not $cat) { throw "fault catalogue not found: $Path" }
    $errs = @(Test-DaFaultCatalog -Catalog $cat)
    if ($errs.Count) { throw "fault catalogue $Path is malformed: $($errs -join '; ')" }
    return $cat
}

function Test-DaPathInside {
    <#
    .SYNOPSIS
        True when a device path lies strictly inside a root directory. Pure.
    .PARAMETER Root
        Absolute device directory.
    .PARAMETER Path
        Absolute device path to check.
    #>
    param([Parameter(Mandatory)][string]$Root, [Parameter(Mandatory)][string]$Path)
    $r = ($Root -replace '\\', '/').TrimEnd('/')
    $p = $Path -replace '\\', '/'
    if (-not $r.StartsWith('/') -or -not $p.StartsWith('/')) { return $false }
    if ($p -match '(^|/)\.\.?(/|$)' -or $r -match '(^|/)\.\.?(/|$)') { return $false }
    if ($p -match "['`"`$``\\;&|<>]") { return $false }
    return $p.StartsWith("$r/") -and $p.Length -gt $r.Length + 1
}

function New-DaCorrelationId {
    <#
    .SYNOPSIS
        Correlation id: da-<8 hex>; printed into the run record, the fault record and the scene actions.
    #>
    return 'da-' + [System.Convert]::ToHexString([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(4)).ToLowerInvariant()
}

function Get-DaDeviceFreeBytes {
    <#
    .SYNOPSIS
        Bytes available to unprivileged writers on the filesystem that holds a device path (stat -f).
    .PARAMETER Path
        Device path.
    .PARAMETER Adb
        Scriptblock taking an argument array and returning {ExitCode; Lines; Stderr}.
    #>
    param([Parameter(Mandatory)][string]$Path, [Parameter(Mandatory)][scriptblock]$Adb)
    $r = & $Adb @('shell', "stat -f -c '%a %S' '$Path'")
    $line = (@($r.Lines) -join ' ').Trim()
    if ($line -notmatch '^(\d+)\s+(\d+)') { throw "cannot read free space of ${Path}: '$line' $($r.Stderr)" }
    return [long]$Matches[1] * [long]$Matches[2]
}

function Invoke-DaFault {
    <#
    .SYNOPSIS
        Injects one catalogued fault and returns its fault record.
    .PARAMETER Id
        Fault id from the catalogue (F01..).
    .PARAMETER Catalog
        Catalogue object; read from disk when omitted.
    .PARAMETER Package
        Application id (kill-process, revoke-permission).
    .PARAMETER RunRemoteDir
        The run's fixture directory on the device; every file the injector touches lies inside it.
    .PARAMETER Target
        Path relative to RunRemoteDir the fault acts on (remove-source, corrupt-media, zero-byte-media), or the
        name of the file to create.
    .PARAMETER Source
        For corrupt-media: path relative to RunRemoteDir of the image that is truncated into Target.
    .PARAMETER MarginBytes
        For fill-destination: free bytes left after the filler.
    .PARAMETER Permission
        For revoke-permission: the runtime permission revoked.
    .PARAMETER OperationStartMonoMs
        Host monotonic time the operation started; the record states the injection moment relative to it.
    .PARAMETER CorrelationId
        Correlation id of the run; generated when empty.
    .PARAMETER Adb
        Scriptblock taking an argument array and returning {ExitCode; Lines; Stderr}.
    #>
    param(
        [Parameter(Mandatory)][ValidatePattern('^F\d{2}$')][string]$Id,
        $Catalog,
        [string]$Package = 'com.sza.fastmediasorter.debug',
        [Parameter(Mandatory)][string]$RunRemoteDir,
        [string]$Target,
        [string]$Source,
        [long]$MarginBytes = 131072,
        [string]$Permission = 'android.permission.READ_MEDIA_IMAGES',
        [long]$OperationStartMonoMs = 0,
        [string]$CorrelationId,
        [Parameter(Mandatory)][scriptblock]$Adb
    )
    if (-not $Catalog) { $Catalog = Get-DaFaultCatalog }
    $fault = @($Catalog.faults | Where-Object { $_.id -eq $Id }) | Select-Object -First 1
    if (-not $fault) { throw "fault $Id is not in the catalogue" }
    if ([string]$fault.status -ne 'injectable') { throw "fault $Id is $($fault.status) ($($fault.reason)); it has no injector" }
    if (-not $CorrelationId) { $CorrelationId = New-DaCorrelationId }
    $root = $RunRemoteDir.TrimEnd('/')
    $inside = {
        param($rel)
        $abs = "$root/$($rel.TrimStart('/'))"
        if (-not (Test-DaPathInside -Root $root -Path $abs)) { throw "fault $Id refused: '$abs' is not inside the run directory $root" }
        $abs
    }
    $run = {
        param([string]$cmd)
        $r = & $Adb @('shell', $cmd)
        if ($r.ExitCode -ne 0) { throw "fault $Id command failed (exit $($r.ExitCode)): $cmd - $($r.Stderr) $(@($r.Lines) -join ' ')" }
        (@($r.Lines) -join ' ').Trim()
    }
    $cleanup = $null
    $detail = ''
    $t0 = Get-DaMonotonicMs
    switch ([string]$fault.injector) {
        'remove-source' {
            $abs = & $inside $Target
            $null = & $run "rm -f '$abs' && test ! -e '$abs' && echo removed"
            $detail = "removed $Target"
        }
        'corrupt-media' {
            $abs = & $inside $Target
            $src = & $inside $Source
            $null = & $run "head -c 512 '$src' > '$abs' && stat -c %s '$abs'"
            $detail = "wrote the first 512 bytes of $Source as $Target"
        }
        'zero-byte-media' {
            $abs = & $inside $Target
            $null = & $run ": > '$abs' && stat -c %s '$abs'"
            $detail = "created zero-byte $Target"
        }
        'fill-destination' {
            $fill = & $inside "_fill-$CorrelationId.bin"
            $free = Get-DaDeviceFreeBytes -Path $root -Adb $Adb
            # fallocate cannot take the last blocks (filesystem metadata): measured 2026-10-12, a 16 KiB
            # margin failed with ENOSPC, so the margin widens step by step until the filler fits.
            $size = 0L; $used = $null
            foreach ($m in @($MarginBytes, ($MarginBytes + 65536), ($MarginBytes + 262144), ($MarginBytes + 1048576))) {
                $try = $free - $m
                if ($try -le 0) { continue }
                $r = & $Adb @('shell', "fallocate -l $try '$fill' && echo filled || { rm -f '$fill'; echo failed; }")
                if ((@($r.Lines) -join ' ') -match 'filled') { $size = $try; $used = $m; break }
            }
            if ($size -le 0) { throw "fault $Id refused: no filler fitted below $free free bytes with a margin from $MarginBytes" }
            $after = Get-DaDeviceFreeBytes -Path $root -Adb $Adb
            $detail = "filler of $size bytes; free space $free -> $after bytes (margin asked $MarginBytes, used $used)"
            $cleanup = [ordered]@{ kind = 'remove-file'; path = $fill }
        }
        'revoke-permission' {
            if ($Permission -notmatch '^android\.permission\.[A-Z_]+$') { throw "fault $Id refused: '$Permission' is not a runtime permission name" }
            $null = & $run "pm revoke $Package $Permission && echo revoked"
            $detail = "revoked $Permission from $Package"
            $cleanup = [ordered]@{ kind = 'grant-permission'; package = $Package; permission = $Permission }
        }
        'kill-process' {
            $null = & $run "am force-stop $Package && echo stopped"
            $detail = "force-stopped $Package"
        }
        'long-name' {
            $detail = 'names come from the published fixture; the over-long name is typed by the scene'
        }
        default { throw "fault $Id names injector '$($fault.injector)' this library does not implement" }
    }
    $t1 = Get-DaMonotonicMs
    return [ordered]@{
        faultId               = $Id
        correlationId         = $CorrelationId
        atomic                = $fault.atomic
        boundary              = $fault.boundary
        declaredMoment        = $fault.moment
        injector              = $fault.injector
        target                = $Target
        injectedAtMonoMs      = $t0
        injectMs              = $t1 - $t0
        operationStartMonoMs  = if ($OperationStartMonoMs -gt 0) { $OperationStartMonoMs } else { $null }
        relativeToOperationMs = if ($OperationStartMonoMs -gt 0) { $t0 - $OperationStartMonoMs } else { $null }
        expectedOutcome       = $fault.expectedOutcome
        invariant             = $fault.invariant
        ok                    = $true
        detail                = $detail
        cleanup               = $cleanup
        cleared               = $null
    }
}

function Clear-DaFault {
    <#
    .SYNOPSIS
        Undoes the emulator-wide condition a fault record left; returns a one-line outcome. Idempotent.
    .PARAMETER Record
        Fault record from Invoke-DaFault.
    .PARAMETER Adb
        Scriptblock taking an argument array and returning {ExitCode; Lines; Stderr}.
    #>
    param([Parameter(Mandatory)]$Record, [Parameter(Mandatory)][scriptblock]$Adb)
    # Local names are prefixed: the -Adb scriptblock resolves its free variables dynamically, and a
    # local called $c once shadowed the caller's context and sent an empty serial (run 261012-001300-fa3e).
    $daClearSpec = $Record.cleanup
    if (-not $daClearSpec) { return 'nothing to clear' }
    switch ([string]$daClearSpec.kind) {
        'remove-file' {
            $daClearOut = & $Adb @('shell', "rm -f '$($daClearSpec.path)'; test -e '$($daClearSpec.path)' && echo still || echo gone")
            $daClearState = (@($daClearOut.Lines) -join '').Trim()
            if ($daClearState -ne 'gone') { throw "filler $($daClearSpec.path) is still present after removal" }
            return "removed filler $($daClearSpec.path)"
        }
        'grant-permission' {
            $null = & $Adb @('shell', "pm grant $($daClearSpec.package) $($daClearSpec.permission)")
            return "granted $($daClearSpec.permission) back to $($daClearSpec.package)"
        }
        default { throw "unknown cleanup kind '$($daClearSpec.kind)'" }
    }
}
