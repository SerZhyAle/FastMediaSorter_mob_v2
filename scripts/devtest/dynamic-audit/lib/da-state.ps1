#requires -Version 7.0
<#
.SYNOPSIS
    Verified backup, restore, durable restore checkpoint and next-start recovery of the S4166 dynamic-audit stand.

.DESCRIPTION
    Dot-source after lib/da-common.ps1, lib/da-emulator.ps1 and lib/da-fixture.ps1 (the remote tree
    hash comes from there). Functions only; nothing is assigned at script scope.

      New-DaBackup             archives the debug package's private directory (run-as tar streamed to a
                               host file), the app-scoped external directory, the tracked settings keys,
                               the display overrides and the runtime-permission state; writes a manifest
                               with run id, absolute device paths, SHA-256 of every file, restore commands
                               in dependency order and the allowed-change list. Absent objects are recorded
                               as absent.
      Test-DaBackup            re-reads every archive, checks both hashes, trial-extracts it into a host
                               scratch directory, compares file list, sizes and hashes with the manifest and
                               stamps the manifest verified.
      New-DaRestoreCheckpoint  writes the durable restore-pending.json BEFORE any mutation; refuses when no
                               verified backup exists or a restore is already pending.
      Restore-DaBackup         idempotent restore in dependency order; stops only the debug package; compares
                               the restored device state with the manifest; removes the checkpoint only on a
                               full match and leaves backup and checkpoint in place on any failure.
      Invoke-DaRecover         runs the pending restore at the next start.
      Assert-DaBackupVerified  refused code when no verified backup exists or a restore is pending.

    STORAGE AND PROTECTION. Everything lives under the ticket scratch root (Get-DaPaths): the checkpoint
    and the current-backup pointer in state/ (DA_STATE_DIR overrides it), each backup in
    private/<runId>/. private/ is restricted to the current user with icacls (inheritance removed), and
    every archive is AES-256-CBC with an HMAC-SHA256 tag (encrypt-then-MAC) under a fresh per-backup key
    that is stored only wrapped by DPAPI ProtectedData in CurrentUser scope. The manifest carries hashes,
    paths and setting values the stand tracks, never key material.

    WHAT IS NOT RESTORED. Keystore-bound credential material is restored only onto the same install:
    the archive brings back the encrypted bytes, and the key stays in the device Keystore. A reinstall
    between backup and restore makes those credentials unreadable, which the stand reports, not hides.
#>

function Get-DaTrackedSettingKeys {
    <#
    .SYNOPSIS
        Device settings the stand records and restores: namespace and key pairs.
    #>
    return @(
        @('system', 'font_scale'), @('system', 'screen_off_timeout'), @('system', 'accelerometer_rotation'),
        @('system', 'user_rotation'), @('global', 'window_animation_scale'), @('global', 'transition_animation_scale'),
        @('global', 'animator_duration_scale'), @('global', 'stay_on_while_plugged_in'),
        @('secure', 'enabled_accessibility_services'), @('secure', 'accessibility_enabled'),
        @('secure', 'show_ime_with_hard_keyboard')
    ) | ForEach-Object { [pscustomobject]@{ namespace = $_[0]; key = $_[1] } }
}

function Get-DaDevicePaths {
    <#
    .SYNOPSIS
        Absolute device paths the backup covers for a package.
    .PARAMETER Package
        Application id.
    #>
    param([Parameter(Mandatory)][string]$Package)
    return [pscustomobject]@{
        Private      = "/data/user/0/$Package"
        External     = "/storage/emulated/0/Android/data/$Package"
        StandObjects = '/storage/emulated/0/Download/FastMediaSorter_DynAudit'
    }
}

function Initialize-DaPrivateDir {
    <#
    .SYNOPSIS
        Creates the private directory and restricts its ACL to the current user (inheritance removed).
    .PARAMETER Path
        Directory to protect.
    #>
    param([Parameter(Mandatory)][string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) { New-Item -ItemType Directory -Path $Path -Force | Out-Null }
    $user = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
    $icacls = Join-Path $env:SystemRoot 'System32\icacls.exe'
    $r = Invoke-DaProcess -FilePath $icacls -ArgumentList @($Path, '/inheritance:r', '/grant:r', "${user}:(OI)(CI)F") -TimeoutMs 60000
    if ($r.ExitCode -ne 0) { throw "icacls failed on ${Path}: $($r.Stderr) $($r.Lines -join ' ')" }
}

function Protect-DaArchive {
    <#
    .SYNOPSIS
        Encrypts a file with AES-256-CBC plus HMAC-SHA256; the 64-byte key pair is written only DPAPI-wrapped.
    .PARAMETER InFile
        Plain file.
    .PARAMETER OutFile
        Encrypted file: 'DAE1' magic, 16-byte IV, ciphertext, 32-byte tag.
    .PARAMETER KeyFile
        DPAPI-wrapped key file; created when absent, reused when present.
    #>
    param(
        [Parameter(Mandatory)][string]$InFile,
        [Parameter(Mandatory)][string]$OutFile,
        [Parameter(Mandatory)][string]$KeyFile
    )
    $keys = Get-DaArchiveKey -KeyFile $KeyFile -Create
    try {
        $aes = [System.Security.Cryptography.Aes]::Create()
        $aes.KeySize = 256
        $aes.Key = [byte[]]$keys[0..31]
        $aes.GenerateIV()
        $aes.Mode = [System.Security.Cryptography.CipherMode]::CBC
        $aes.Padding = [System.Security.Cryptography.PaddingMode]::PKCS7
        $out = [System.IO.FileStream]::new($OutFile, [System.IO.FileMode]::Create, [System.IO.FileAccess]::ReadWrite)
        try {
            $magic = [System.Text.Encoding]::ASCII.GetBytes('DAE1')
            $out.Write($magic, 0, 4)
            $out.Write($aes.IV, 0, 16)
            $in = [System.IO.File]::OpenRead($InFile)
            $crypto = [System.Security.Cryptography.CryptoStream]::new($out, $aes.CreateEncryptor(), [System.Security.Cryptography.CryptoStreamMode]::Write, $true)
            try { $in.CopyTo($crypto); $crypto.FlushFinalBlock() }
            finally { $crypto.Dispose(); $in.Dispose() }
            $out.Flush()
            $out.Position = 0
            $hmac = [System.Security.Cryptography.HMACSHA256]::new([byte[]]$keys[32..63])
            $tag = $hmac.ComputeHash($out)
            $hmac.Dispose()
            $out.Position = $out.Length
            $out.Write($tag, 0, 32)
            $out.Flush($true)
        }
        finally { $out.Dispose(); $aes.Dispose() }
    }
    finally { [System.Array]::Clear($keys, 0, $keys.Length) }
}

function Unprotect-DaArchive {
    <#
    .SYNOPSIS
        Verifies the HMAC tag of an encrypted archive, then decrypts it; throws on a wrong tag.
    .PARAMETER InFile
        Encrypted file.
    .PARAMETER OutFile
        Plain output.
    .PARAMETER KeyFile
        DPAPI-wrapped key file.
    #>
    param(
        [Parameter(Mandatory)][string]$InFile,
        [Parameter(Mandatory)][string]$OutFile,
        [Parameter(Mandatory)][string]$KeyFile
    )
    $keys = Get-DaArchiveKey -KeyFile $KeyFile
    try {
        $in = [System.IO.File]::OpenRead($InFile)
        try {
            if ($in.Length -lt 52) { throw "encrypted archive too short: $InFile" }
            $magic = [byte[]]::new(4); [void]$in.Read($magic, 0, 4)
            if ([System.Text.Encoding]::ASCII.GetString($magic) -ne 'DAE1') { throw "not a stand archive: $InFile" }
            $bodyLength = $in.Length - 32
            $hmac = [System.Security.Cryptography.IncrementalHash]::CreateHMAC([System.Security.Cryptography.HashAlgorithmName]::SHA256, [byte[]]$keys[32..63])
            $in.Position = 0
            $buf = [byte[]]::new(65536)
            $left = $bodyLength
            while ($left -gt 0) {
                $n = $in.Read($buf, 0, [int][math]::Min($buf.Length, $left))
                if ($n -le 0) { throw 'short read while authenticating' }
                $hmac.AppendData($buf, 0, $n); $left -= $n
            }
            $expected = $hmac.GetHashAndReset(); $hmac.Dispose()
            $tag = [byte[]]::new(32); [void]$in.Read($tag, 0, 32)
            if (-not [System.Security.Cryptography.CryptographicOperations]::FixedTimeEquals($expected, $tag)) {
                throw "authentication tag mismatch: $InFile was altered or the key is wrong"
            }
            $iv = [byte[]]::new(16)
            $in.Position = 4; [void]$in.Read($iv, 0, 16)
            $aes = [System.Security.Cryptography.Aes]::Create()
            $aes.KeySize = 256; $aes.Key = [byte[]]$keys[0..31]; $aes.IV = $iv
            $aes.Mode = [System.Security.Cryptography.CipherMode]::CBC
            $aes.Padding = [System.Security.Cryptography.PaddingMode]::PKCS7
            $out = [System.IO.FileStream]::new($OutFile, [System.IO.FileMode]::Create, [System.IO.FileAccess]::Write)
            $crypto = [System.Security.Cryptography.CryptoStream]::new($out, $aes.CreateDecryptor(), [System.Security.Cryptography.CryptoStreamMode]::Write)
            try {
                $left = $bodyLength - 20
                while ($left -gt 0) {
                    $n = $in.Read($buf, 0, [int][math]::Min($buf.Length, $left))
                    if ($n -le 0) { throw 'short read while decrypting' }
                    $crypto.Write($buf, 0, $n); $left -= $n
                }
                $crypto.FlushFinalBlock()
            }
            finally { $crypto.Dispose(); $out.Dispose(); $aes.Dispose() }
        }
        finally { $in.Dispose() }
    }
    finally { [System.Array]::Clear($keys, 0, $keys.Length) }
}

function Get-DaArchiveKey {
    <#
    .SYNOPSIS
        Returns the 64-byte archive key pair, unwrapping it with DPAPI (CurrentUser); -Create makes a new one when absent.
    .PARAMETER KeyFile
        DPAPI-wrapped key file.
    .PARAMETER Create
        Generate and store a fresh key when the file is absent.
    #>
    param([Parameter(Mandatory)][string]$KeyFile, [switch]$Create)
    $entropy = [System.Text.Encoding]::UTF8.GetBytes('S4166-dynamic-audit-archive')
    $scope = [System.Security.Cryptography.DataProtectionScope]::CurrentUser
    if (-not (Test-Path -LiteralPath $KeyFile)) {
        if (-not $Create) { throw "archive key file missing: $KeyFile" }
        $fresh = [System.Security.Cryptography.RandomNumberGenerator]::GetBytes(64)
        $wrapped = [System.Security.Cryptography.ProtectedData]::Protect($fresh, $entropy, $scope)
        $fs = [System.IO.FileStream]::new($KeyFile, [System.IO.FileMode]::CreateNew, [System.IO.FileAccess]::Write)
        try { $fs.Write($wrapped, 0, $wrapped.Length); $fs.Flush($true) } finally { $fs.Dispose() }
        return $fresh
    }
    $wrappedBytes = [System.IO.File]::ReadAllBytes($KeyFile)
    return [System.Security.Cryptography.ProtectedData]::Unprotect($wrappedBytes, $entropy, $scope)
}

function ConvertFrom-DaRuntimePermissions {
    <#
    .SYNOPSIS
        Parses the runtime-permission lines of `dumpsys package <pkg>` into {name; granted} records.
    .PARAMETER Lines
        dumpsys output lines.
    #>
    param([string[]]$Lines)
    $result = [ordered]@{}
    $inRuntime = $false
    foreach ($line in $Lines) {
        if ($line -match '^\s*runtime permissions:[ \t]*$') { $inRuntime = $true; continue }
        if ($inRuntime) {
            if ($line -match '^\s+([A-Za-z0-9_.]+): granted=(true|false)') { $result[$Matches[1]] = ($Matches[2] -eq 'true'); continue }
            $inRuntime = $false
        }
    }
    return @($result.Keys | Sort-Object | ForEach-Object { [pscustomobject]@{ name = $_; granted = [bool]$result[$_] } })
}

function Get-DaPackageState {
    <#
    .SYNOPSIS
        Runtime permissions, tracked settings and display overrides currently on the device.
    .PARAMETER Serial
        A serial that already passed Assert-DaEmulatorTarget.
    .PARAMETER Package
        Application id.
    #>
    param([Parameter(Mandatory)][string]$Serial, [Parameter(Mandatory)][string]$Package)
    $dump = Invoke-DaAdb -Serial $Serial -Arguments @('shell', 'dumpsys', 'package', $Package)
    $perms = ConvertFrom-DaRuntimePermissions -Lines $dump.Lines
    $settings = foreach ($k in Get-DaTrackedSettingKeys) {
        $v = ((Invoke-DaAdb -Serial $Serial -Arguments @('shell', 'settings', 'get', $k.namespace, $k.key)).Lines -join '').Trim()
        [pscustomobject]@{ namespace = $k.namespace; key = $k.key; present = ($v -ne 'null'); value = if ($v -eq 'null') { $null } else { $v } }
    }
    $size = (Invoke-DaAdb -Serial $Serial -Arguments @('shell', 'wm', 'size')).Lines
    $density = (Invoke-DaAdb -Serial $Serial -Arguments @('shell', 'wm', 'density')).Lines
    $override = { param($lines) $m = $lines | Where-Object { $_ -match '^Override \w+: (.+)$' } | Select-Object -First 1; if ($m -match '^Override \w+: (.+)$') { $Matches[1].Trim() } else { $null } }
    return [pscustomobject]@{
        permissions      = @($perms)
        settings         = @($settings)
        displayOverrides = [pscustomobject]@{ size = & $override $size; density = & $override $density }
    }
}

function Test-DaRemoteDir {
    <#
    .SYNOPSIS
        True when a device directory exists (as the shell user, or as the package with -RunAsPackage).
    .PARAMETER Serial
        A serial that already passed Assert-DaEmulatorTarget.
    .PARAMETER Path
        Absolute device path.
    .PARAMETER RunAsPackage
        Run the test through run-as this package.
    #>
    param([Parameter(Mandatory)][string]$Serial, [Parameter(Mandatory)][string]$Path, [string]$RunAsPackage)
    $cmd = if ($RunAsPackage) { "run-as $RunAsPackage test -d '$Path' && echo yes || echo no" } else { "test -d '$Path' && echo yes || echo no" }
    return (((Invoke-DaAdb -Serial $Serial -Arguments @('shell', $cmd)).Lines -join '').Trim() -eq 'yes')
}

function New-DaManifestObject {
    <#
    .SYNOPSIS
        Builds the backup manifest (pure; no device, no file IO), so its shape is testable without hardware.
    .PARAMETER RunId
        Backup run id.
    .PARAMETER Device
        {serial; avd; api}.
    .PARAMETER Subject
        Get-DaSubject output.
    .PARAMETER Components
        List of {id; devicePath; present; archive; files}.
    .PARAMETER State
        Get-DaPackageState output.
    #>
    param(
        [Parameter(Mandatory)][string]$RunId,
        [Parameter(Mandatory)]$Device,
        [Parameter(Mandatory)]$Subject,
        [Parameter(Mandatory)][object[]]$Components,
        [Parameter(Mandatory)]$State
    )
    $pkg = [string]$Subject.package
    $paths = Get-DaDevicePaths -Package $pkg
    $commands = @(
        "am force-stop $pkg",
        "run-as $pkg find . -mindepth 1 -maxdepth 1 -exec rm -rf {} + ; exec-in run-as $pkg tar -xf - < private.tar (or pm uninstall $pkg when the package was absent)",
        "find $($paths.External) -mindepth 1 -delete ; exec-in tar -xf - -C $($paths.External) < external.tar (or rm -rf when absent)",
        "rm -rf $($paths.StandObjects) when it was absent at backup",
        'pm grant / pm revoke each runtime permission back to its recorded state',
        'settings put / settings delete each tracked key; wm size / wm density back to the recorded override',
        'compare private and external tree hashes, permissions and settings with this manifest'
    )
    $order = 0
    return [ordered]@{
        schemaVersion    = 1
        kind             = 'da-backup'
        runId            = $RunId
        createdAt        = (Get-Date).ToUniversalTime().ToString('o')
        createdMonoMs    = Get-DaMonotonicMs
        device           = [ordered]@{ kind = 'emulator'; serial = $Device.serial; avd = $Device.avd; api = $Device.api }
        package          = [ordered]@{
            name             = $pkg
            present          = [bool]$Subject.installed
            versionName      = $Subject.versionName
            versionCode      = $Subject.versionCode
            firstInstallTime = $Subject.firstInstallTime
            apkSha256        = $Subject.apkSha256
            hostApk          = $Subject.hostApk
        }
        components       = @($Components)
        permissions      = @($State.permissions)
        settings         = @($State.settings)
        displayOverrides = $State.displayOverrides
        restoreCommands  = @($commands | ForEach-Object { $order++; [ordered]@{ order = $order; command = $_ } })
        allowedChanges   = @("$($paths.Private)/**", "$($paths.External)/**", "$($paths.StandObjects)/**",
            'runtime permissions of the package', 'tracked settings keys', 'wm size and wm density overrides')
        encryption       = [ordered]@{
            cipher        = 'AES-256-CBC with HMAC-SHA256 tag (encrypt-then-MAC)'
            keyFile       = 'key.dpapi'
            keyProtection = 'DPAPI ProtectedData, CurrentUser scope; the manifest holds no key material'
        }
        verified         = [ordered]@{ status = 'unverified' }
        restores         = @()
    }
}

function Test-DaManifestShape {
    <#
    .SYNOPSIS
        Returns the list of shape errors of a backup manifest (empty when the shape is valid).
    .PARAMETER Manifest
        Manifest object (from New-DaManifestObject or Read-DaJson).
    #>
    param([Parameter(Mandatory)]$Manifest)
    $errors = [System.Collections.Generic.List[string]]::new()
    foreach ($k in 'schemaVersion', 'runId', 'device', 'package', 'components', 'permissions', 'settings', 'restoreCommands', 'allowedChanges', 'verified') {
        if ($null -eq $Manifest.$k) { $errors.Add("missing $k") }
    }
    if ($Manifest.schemaVersion -ne 1) { $errors.Add("schemaVersion $($Manifest.schemaVersion) is not 1") }
    foreach ($c in @($Manifest.components)) {
        if (-not $c.id -or -not $c.devicePath -or $null -eq $c.present) { $errors.Add("component without id/devicePath/present") ; continue }
        if (-not ([string]$c.devicePath).StartsWith('/')) { $errors.Add("component $($c.id) device path is not absolute") }
        if ($c.present -and $c.archive) {
            foreach ($f in @($c.files)) {
                if (-not $f.path -or $null -eq $f.size -or ([string]$f.sha256) -notmatch '^[0-9a-f]{64}$') { $errors.Add("component $($c.id) has a file record without path/size/sha256"); break }
            }
        }
    }
    $orders = @($Manifest.restoreCommands | ForEach-Object { [int]$_.order })
    for ($i = 0; $i -lt $orders.Count; $i++) { if ($orders[$i] -ne $i + 1) { $errors.Add('restoreCommands are not in dependency order 1..n'); break } }
    $json = $Manifest | ConvertTo-Json -Depth 64
    if ($json -match '"(keyBytes|keyMaterial|aesKey|macKey|secret|password|token)"\s*:') { $errors.Add('manifest carries a secret-shaped field') }
    return $errors.ToArray()
}

function Get-DaCurrentBackupPointerPath { return (Join-Path (Get-DaPaths).State 'current-backup.json') }
function Get-DaRestorePendingPath { return (Join-Path (Get-DaPaths).State 'restore-pending.json') }

function Get-DaAvdName {
    <#
    .SYNOPSIS
        AVD name of an emulator serial (adb emu avd name).
    .PARAMETER Serial
        A serial that already passed Assert-DaEmulatorTarget.
    #>
    param([Parameter(Mandatory)][string]$Serial)
    return ((Invoke-DaAdb -Serial $Serial -Arguments @('emu', 'avd', 'name')).Lines | Select-Object -First 1).Trim()
}

function Save-DaComponentArchive {
    <#
    .SYNOPSIS
        Streams one device tree as tar into the run directory, checks it against the device listing, encrypts it.
    .PARAMETER Serial
        A serial that already passed Assert-DaEmulatorTarget.
    .PARAMETER Id
        Component id (private, external).
    .PARAMETER DevicePath
        Absolute device directory.
    .PARAMETER RunAsPackage
        Read through run-as this package (the private directory).
    .PARAMETER RunDir
        private/<runId> directory.
    #>
    param(
        [Parameter(Mandatory)][string]$Serial,
        [Parameter(Mandatory)][string]$Id,
        [Parameter(Mandatory)][string]$DevicePath,
        [string]$RunAsPackage,
        [Parameter(Mandatory)][string]$RunDir
    )
    $listing = Get-DaTreeHash -Serial $Serial -RemoteDir $DevicePath -RunAsPackage $RunAsPackage
    $plain = Join-Path $RunDir "$Id.tar"
    $tarCmd = if ($RunAsPackage) { "run-as $RunAsPackage tar -cf - -C '$DevicePath' ." } else { "tar -cf - -C '$DevicePath' ." }
    $cap = Invoke-DaAdbToFile -Serial $Serial -Arguments @('exec-out', $tarCmd) -OutFile $plain
    if ($cap.ExitCode -ne 0 -or $cap.Bytes -lt 1024) { throw "tar capture of $DevicePath failed (exit $($cap.ExitCode), $($cap.Bytes) bytes): $($cap.Stderr)" }
    $read = Expand-DaTar -TarPath $plain
    if ($read.Errors.Count) { throw "archive of $DevicePath is unreadable: $($read.Errors -join '; ')" }
    $diff = Compare-DaTreeHash -Reference $listing.Entries -Difference $read.Files
    if ($diff.Count) { throw "archive of $DevicePath differs from the device listing ($($diff.Summary)); was the app running?" }
    $plainSha = Get-DaFileSha256 -Path $plain
    $enc = Join-Path $RunDir "$Id.tar.enc"
    Protect-DaArchive -InFile $plain -OutFile $enc -KeyFile (Join-Path $RunDir 'key.dpapi')
    Remove-Item -LiteralPath $plain -Force
    return [ordered]@{
        id         = $Id
        devicePath = $DevicePath
        present    = $true
        runAs      = [bool]$RunAsPackage
        archive    = [ordered]@{ file = "$Id.tar.enc"; encryptedSha256 = Get-DaFileSha256 -Path $enc; plainSha256 = $plainSha; plainBytes = $cap.Bytes; captureMs = $cap.ElapsedMs }
        fileCount  = @($listing.Entries).Count
        treeSha256 = $listing.TreeSha256
        files      = @($listing.Entries)
    }
}

function New-DaBackup {
    <#
    .SYNOPSIS
        Backs up the package's device state; returns {Code; Reason; ManifestPath; RunId}.
    .PARAMETER Serial
        A serial that already passed Assert-DaEmulatorTarget.
    .PARAMETER Package
        Debug application id (run-as needs a debuggable build).
    .PARAMETER RunId
        Backup id; generated when empty.
    #>
    param([Parameter(Mandatory)][string]$Serial, [Parameter(Mandatory)][string]$Package, [string]$RunId)
    $codes = Get-DaExitCode
    if (Test-Path -LiteralPath (Get-DaRestorePendingPath)) {
        return [pscustomobject]@{ Code = $codes.Refused; Reason = 'a restore is pending; run -Verb Recover before a new backup'; ManifestPath = $null; RunId = $null }
    }
    if (-not $RunId) { $RunId = New-DaRunId }
    $paths = Get-DaPaths
    Initialize-DaPrivateDir -Path $paths.Private
    $runDir = Join-Path $paths.Private $RunId
    New-Item -ItemType Directory -Path $runDir -Force | Out-Null
    $dev = Get-DaDevicePaths -Package $Package

    $null = Invoke-DaAdb -Serial $Serial -Arguments @('shell', 'am', 'force-stop', $Package)
    $subject = Get-DaSubject -Serial $Serial -Package $Package
    $components = [System.Collections.Generic.List[object]]::new()
    if ($subject.installed) {
        $components.Add((Save-DaComponentArchive -Serial $Serial -Id 'private' -DevicePath $dev.Private -RunAsPackage $Package -RunDir $runDir))
    }
    else {
        $components.Add([ordered]@{ id = 'private'; devicePath = $dev.Private; present = $false })
    }
    if (Test-DaRemoteDir -Serial $Serial -Path $dev.External) {
        $components.Add((Save-DaComponentArchive -Serial $Serial -Id 'external' -DevicePath $dev.External -RunDir $runDir))
    }
    else {
        $components.Add([ordered]@{ id = 'external'; devicePath = $dev.External; present = $false })
    }
    $components.Add([ordered]@{ id = 'stand-objects'; devicePath = $dev.StandObjects; present = (Test-DaRemoteDir -Serial $Serial -Path $dev.StandObjects) })
    $state = Get-DaPackageState -Serial $Serial -Package $Package
    $api = ((Invoke-DaAdb -Serial $Serial -Arguments @('shell', 'getprop', 'ro.build.version.sdk')).Lines -join '').Trim()
    $device = [pscustomobject]@{ serial = $Serial; avd = Get-DaAvdName -Serial $Serial; api = $api }
    $manifest = New-DaManifestObject -RunId $RunId -Device $device -Subject $subject -Components $components.ToArray() -State $state
    $manifestPath = Join-Path $runDir 'manifest.json'
    Write-DaJson -Path $manifestPath -InputObject $manifest -Durable
    Write-DaJson -Path (Get-DaCurrentBackupPointerPath) -Durable -InputObject ([ordered]@{
            schemaVersion = 1; runId = $RunId; manifestPath = $manifestPath; package = $Package; avd = $device.avd
        })
    $count = (@($components | Where-Object { $_.present -and $_.files }) | ForEach-Object { $_.fileCount } | Measure-Object -Sum).Sum
    return [pscustomobject]@{ Code = $codes.Ok; Reason = "backup $RunId written ($count files), not yet verified"; ManifestPath = $manifestPath; RunId = $RunId }
}

function Resolve-DaManifestPath {
    <#
    .SYNOPSIS
        Manifest path from the argument or the current-backup pointer; $null when neither exists.
    .PARAMETER ManifestPath
        Explicit manifest path.
    #>
    param([string]$ManifestPath)
    if ($ManifestPath) { return $ManifestPath }
    $ptr = Read-DaJson -Path (Get-DaCurrentBackupPointerPath)
    if ($ptr) { return [string]$ptr.manifestPath }
    return $null
}

function Test-DaBackup {
    <#
    .SYNOPSIS
        Verifies a backup by hash, authentication tag and trial extraction; stamps the manifest. Returns {Code; Reason}.
    .PARAMETER ManifestPath
        Manifest to verify; defaults to the current-backup pointer.
    #>
    param([string]$ManifestPath)
    $codes = Get-DaExitCode
    $ManifestPath = Resolve-DaManifestPath -ManifestPath $ManifestPath
    $manifest = if ($ManifestPath) { Read-DaJson -Path $ManifestPath } else { $null }
    if (-not $manifest) { return [pscustomobject]@{ Code = $codes.Refused; Reason = 'no backup manifest found' } }
    $shape = @(Test-DaManifestShape -Manifest $manifest)
    if ($shape.Count) { return [pscustomobject]@{ Code = $codes.Refused; Reason = "manifest shape: $($shape -join '; ')" } }
    $runDir = Split-Path -Parent $ManifestPath
    $keyFile = Join-Path $runDir 'key.dpapi'
    $problems = [System.Collections.Generic.List[string]]::new()
    $checked = 0
    foreach ($c in @($manifest.components | Where-Object { $_.present -and $_.archive })) {
        $enc = Join-Path $runDir $c.archive.file
        if (-not (Test-Path -LiteralPath $enc)) { $problems.Add("$($c.id): archive missing"); continue }
        if ((Get-DaFileSha256 -Path $enc) -ne $c.archive.encryptedSha256) { $problems.Add("$($c.id): encrypted archive hash differs"); continue }
        $plain = Join-Path $runDir "$($c.id).verify.tar"
        $trial = Join-Path $runDir "trial-$($c.id)"
        try {
            Unprotect-DaArchive -InFile $enc -OutFile $plain -KeyFile $keyFile
            if ((Get-DaFileSha256 -Path $plain) -ne $c.archive.plainSha256) { $problems.Add("$($c.id): plain archive hash differs"); continue }
            if (Test-Path -LiteralPath $trial) { Remove-Item -LiteralPath $trial -Recurse -Force }
            New-Item -ItemType Directory -Path $trial -Force | Out-Null
            $x = Expand-DaTar -TarPath $plain -Destination $trial
            if ($x.Errors.Count) { $problems.Add("$($c.id): trial extraction: $($x.Errors -join '; ')"); continue }
            $diff = Compare-DaTreeHash -Reference @($c.files) -Difference $x.Files
            if ($diff.Count) { $problems.Add("$($c.id): archive list differs from manifest ($($diff.Summary))"); continue }
            $onDisk = Get-DaLocalTreeHash -Path $trial
            $diskDiff = Compare-DaTreeHash -Reference @($c.files) -Difference $onDisk.Entries
            if ($diskDiff.Count) { $problems.Add("$($c.id): extracted files differ from manifest ($($diskDiff.Summary))"); continue }
            $checked += @($c.files).Count
        }
        catch { $problems.Add("$($c.id): $($_.Exception.Message)") }
        finally {
            if (Test-Path -LiteralPath $plain) { Remove-Item -LiteralPath $plain -Force }
            if (Test-Path -LiteralPath $trial) { Remove-Item -LiteralPath $trial -Recurse -Force }
        }
    }
    if ($problems.Count) {
        $manifest.verified = [ordered]@{ status = 'failed'; at = (Get-Date).ToUniversalTime().ToString('o'); problems = $problems.ToArray() }
        Write-DaJson -Path $ManifestPath -InputObject $manifest -Durable
        return [pscustomobject]@{ Code = $codes.Refused; Reason = "backup $($manifest.runId) failed verification: $($problems -join '; ')" }
    }
    $manifest.verified = [ordered]@{ status = 'verified'; at = (Get-Date).ToUniversalTime().ToString('o'); filesChecked = $checked; trialExtraction = $true }
    Write-DaJson -Path $ManifestPath -InputObject $manifest -Durable
    return [pscustomobject]@{ Code = $codes.Ok; Reason = "backup $($manifest.runId) verified: $checked files hash-checked after trial extraction" }
}

function Assert-DaBackupVerified {
    <#
    .SYNOPSIS
        Refused code when a restore is pending or no verified backup exists; returns {Code; Reason; ManifestPath}.
    .PARAMETER Package
        When set, the backup must belong to this package.
    .PARAMETER Avd
        When set, the backup must have been taken on this AVD.
    #>
    param([string]$Package, [string]$Avd)
    $codes = Get-DaExitCode
    $refused = { param($why) [pscustomobject]@{ Code = $codes.Refused; Reason = $why; ManifestPath = $null } }
    $pendingPath = Get-DaRestorePendingPath
    if (Test-Path -LiteralPath $pendingPath) {
        $p = Read-DaJson -Path $pendingPath
        return (& $refused "a restore is pending (run $($p.runId), $($p.reason)); run -Verb Recover first")
    }
    $ptr = Read-DaJson -Path (Get-DaCurrentBackupPointerPath)
    if (-not $ptr) { return (& $refused 'no backup exists; run -Verb Backup then -Verb VerifyBackup') }
    $manifest = Read-DaJson -Path ([string]$ptr.manifestPath)
    if (-not $manifest) { return (& $refused "backup manifest unreadable: $($ptr.manifestPath)") }
    if ($manifest.schemaVersion -ne 1) { return (& $refused "backup manifest schemaVersion $($manifest.schemaVersion) is not 1") }
    if ([string]$manifest.verified.status -ne 'verified') { return (& $refused "backup $($manifest.runId) is $($manifest.verified.status); run -Verb VerifyBackup") }
    if ($Package -and [string]$manifest.package.name -ne $Package) { return (& $refused "backup $($manifest.runId) is for $($manifest.package.name), not $Package") }
    if ($Avd -and [string]$manifest.device.avd -ne $Avd) { return (& $refused "backup $($manifest.runId) was taken on AVD $($manifest.device.avd), not $Avd") }
    $runDir = Split-Path -Parent ([string]$ptr.manifestPath)
    foreach ($c in @($manifest.components | Where-Object { $_.present -and $_.archive })) {
        $enc = Join-Path $runDir $c.archive.file
        if (-not (Test-Path -LiteralPath $enc)) { return (& $refused "backup $($manifest.runId) archive $($c.archive.file) is missing") }
        if ((Get-DaFileSha256 -Path $enc) -ne $c.archive.encryptedSha256) { return (& $refused "backup $($manifest.runId) archive $($c.archive.file) changed since verification") }
    }
    if (@($manifest.components | Where-Object { $_.present -and $_.archive }).Count -and -not (Test-Path -LiteralPath (Join-Path $runDir 'key.dpapi'))) {
        return (& $refused "backup $($manifest.runId) key file is missing")
    }
    return [pscustomobject]@{ Code = $codes.Ok; Reason = "verified backup $($manifest.runId), no restore pending"; ManifestPath = [string]$ptr.manifestPath }
}

function New-DaRestoreCheckpoint {
    <#
    .SYNOPSIS
        Writes the durable restore-pending.json before a mutation; refuses without a verified backup. Returns {Code; Reason}.
    .PARAMETER Serial
        A serial that already passed Assert-DaEmulatorTarget.
    .PARAMETER Package
        Application id the mutation will touch.
    .PARAMETER Reason
        What is about to mutate the device.
    .PARAMETER RunId
        Run id of the mutating run; generated when empty.
    #>
    param([Parameter(Mandatory)][string]$Serial, [Parameter(Mandatory)][string]$Package, [Parameter(Mandatory)][string]$Reason, [string]$RunId)
    $avd = Get-DaAvdName -Serial $Serial
    $gate = Assert-DaBackupVerified -Package $Package -Avd $avd
    if ($gate.Code -ne 0) { return $gate }
    if (-not $RunId) { $RunId = New-DaRunId }
    Write-DaJson -Path (Get-DaRestorePendingPath) -Durable -InputObject ([ordered]@{
            schemaVersion = 1; runId = $RunId; reason = $Reason; manifestPath = $gate.ManifestPath
            serial = $Serial; avd = $avd; package = $Package
            writtenAt = (Get-Date).ToUniversalTime().ToString('o'); writtenMonoMs = Get-DaMonotonicMs
        })
    return [pscustomobject]@{ Code = (Get-DaExitCode).Ok; Reason = "restore checkpoint written for run $RunId"; ManifestPath = $gate.ManifestPath }
}

function Compare-DaDeviceWithManifest {
    <#
    .SYNOPSIS
        Lists every difference between the device and a backup manifest (empty when they match).
    .PARAMETER Serial
        A serial that already passed Assert-DaEmulatorTarget.
    .PARAMETER Manifest
        Backup manifest.
    #>
    param([Parameter(Mandatory)][string]$Serial, [Parameter(Mandatory)]$Manifest)
    $pkg = [string]$Manifest.package.name
    $diffs = [System.Collections.Generic.List[string]]::new()
    foreach ($c in @($Manifest.components)) {
        $runAs = if ($c.id -eq 'private') { $pkg } else { $null }
        if ($c.id -eq 'private' -and -not $Manifest.package.present) {
            $installed = ((Invoke-DaAdb -Serial $Serial -Arguments @('shell', 'pm', 'path', $pkg)).Lines -join '') -match 'package:'
            if ($installed) { $diffs.Add("package $pkg is installed but was absent") }
            continue
        }
        $exists = Test-DaRemoteDir -Serial $Serial -Path $c.devicePath -RunAsPackage $runAs
        if (-not $c.present) { if ($exists) { $diffs.Add("$($c.id): $($c.devicePath) exists but was absent") }; continue }
        if (-not $exists) { $diffs.Add("$($c.id): $($c.devicePath) is missing"); continue }
        if ($c.archive) {
            $now = Get-DaTreeHash -Serial $Serial -RemoteDir $c.devicePath -RunAsPackage $runAs
            $d = Compare-DaTreeHash -Reference @($c.files) -Difference $now.Entries
            if ($d.Count) { $diffs.Add("$($c.id): $($d.Summary)") }
        }
    }
    if ($Manifest.package.present) {
        $state = Get-DaPackageState -Serial $Serial -Package $pkg
        $nowPerm = @{}; foreach ($p in $state.permissions) { $nowPerm[$p.name] = $p.granted }
        foreach ($p in @($Manifest.permissions)) {
            if ($nowPerm.ContainsKey($p.name) -and [bool]$nowPerm[$p.name] -ne [bool]$p.granted) { $diffs.Add("permission $($p.name) granted=$($nowPerm[$p.name]), expected $($p.granted)") }
        }
    }
    else { $state = Get-DaPackageState -Serial $Serial -Package $pkg }
    $nowSet = @{}; foreach ($s in $state.settings) { $nowSet["$($s.namespace)/$($s.key)"] = $s.value }
    foreach ($s in @($Manifest.settings)) {
        $k = "$($s.namespace)/$($s.key)"
        if ([string]$nowSet[$k] -ne [string]$s.value) { $diffs.Add("setting $k = '$($nowSet[$k])', expected '$($s.value)'") }
    }
    if ([string]$state.displayOverrides.size -ne [string]$Manifest.displayOverrides.size) { $diffs.Add("wm size override '$($state.displayOverrides.size)', expected '$($Manifest.displayOverrides.size)'") }
    if ([string]$state.displayOverrides.density -ne [string]$Manifest.displayOverrides.density) { $diffs.Add("wm density override '$($state.displayOverrides.density)', expected '$($Manifest.displayOverrides.density)'") }
    return $diffs.ToArray()
}

function Restore-DaBackup {
    <#
    .SYNOPSIS
        Idempotent restore of a verified backup; removes the checkpoint only after a full match. Returns {Code; Reason; Steps}.
    .PARAMETER Serial
        A serial that already passed Assert-DaEmulatorTarget.
    .PARAMETER ManifestPath
        Manifest to restore; defaults to the current-backup pointer.
    #>
    param([Parameter(Mandatory)][string]$Serial, [string]$ManifestPath)
    $codes = Get-DaExitCode
    $ManifestPath = Resolve-DaManifestPath -ManifestPath $ManifestPath
    $manifest = if ($ManifestPath) { Read-DaJson -Path $ManifestPath } else { $null }
    if (-not $manifest) { return [pscustomobject]@{ Code = $codes.Refused; Reason = 'no backup manifest to restore from'; Steps = @() } }
    if ([string]$manifest.verified.status -ne 'verified') { return [pscustomobject]@{ Code = $codes.Refused; Reason = "backup $($manifest.runId) is not verified; refusing to restore from it"; Steps = @() } }
    $avd = Get-DaAvdName -Serial $Serial
    if ([string]$manifest.device.avd -ne $avd) { return [pscustomobject]@{ Code = $codes.Refused; Reason = "backup $($manifest.runId) belongs to AVD $($manifest.device.avd), target is $avd"; Steps = @() } }
    $pendingPath = Get-DaRestorePendingPath
    if (-not (Test-Path -LiteralPath $pendingPath)) {
        Write-DaJson -Path $pendingPath -Durable -InputObject ([ordered]@{
                schemaVersion = 1; runId = $manifest.runId; reason = 'restore started'; manifestPath = $ManifestPath
                serial = $Serial; avd = $avd; package = $manifest.package.name
                writtenAt = (Get-Date).ToUniversalTime().ToString('o'); writtenMonoMs = Get-DaMonotonicMs
            })
    }
    $pkg = [string]$manifest.package.name
    $runDir = Split-Path -Parent $ManifestPath
    $keyFile = Join-Path $runDir 'key.dpapi'
    $steps = [System.Collections.Generic.List[object]]::new()
    $step = {
        param($name, [scriptblock]$body)
        $t0 = Get-DaMonotonicMs
        try { $detail = & $body; $steps.Add([ordered]@{ step = $name; ok = $true; detail = "$detail"; ms = (Get-DaMonotonicMs) - $t0 }) }
        catch { $steps.Add([ordered]@{ step = $name; ok = $false; detail = $_.Exception.Message; ms = (Get-DaMonotonicMs) - $t0 }) }
    }
    $comp = @{}; foreach ($c in @($manifest.components)) { $comp[[string]$c.id] = $c }

    & $step 'stop-app' { $null = Invoke-DaAdb -Serial $Serial -Arguments @('shell', 'am', 'force-stop', $pkg); 'force-stopped' }
    & $step 'private-data' {
        $c = $comp['private']
        $installed = ((Invoke-DaAdb -Serial $Serial -Arguments @('shell', 'pm', 'path', $pkg)).Lines -join '') -match 'package:'
        if (-not $manifest.package.present) {
            if ($installed) { $u = Invoke-DaAdb -Serial $Serial -Arguments @('shell', 'pm', 'uninstall', $pkg); return "uninstalled: $($u.Lines -join ' ')" }
            return 'package absent, as recorded'
        }
        if (-not $installed) { throw "package $pkg is not installed; reinstall the recorded APK ($($manifest.package.hostApk)) first" }
        $plain = Join-Path $runDir 'private.restore.tar'
        try {
            Unprotect-DaArchive -InFile (Join-Path $runDir $c.archive.file) -OutFile $plain -KeyFile $keyFile
            $clear = Invoke-DaAdb -Serial $Serial -Arguments @('shell', "run-as $pkg find . -mindepth 1 -maxdepth 1 -exec rm -rf {} +")
            if ($clear.ExitCode -ne 0) { throw "clearing $($c.devicePath) failed: $($clear.Stderr)" }
            $x = Invoke-DaAdbFromFile -Serial $Serial -Arguments @('exec-in', "run-as $pkg tar -xf - -C '$($c.devicePath)'") -InFile $plain
            if ($x.ExitCode -ne 0) { throw "extract into $($c.devicePath) failed: $($x.Stderr)" }
            "restored $($c.fileCount) files"
        }
        finally { if (Test-Path -LiteralPath $plain) { Remove-Item -LiteralPath $plain -Force } }
    }
    & $step 'external-data' {
        $c = $comp['external']
        $exists = Test-DaRemoteDir -Serial $Serial -Path $c.devicePath
        if (-not $c.present) {
            if ($exists) { $null = Invoke-DaAdb -Serial $Serial -Arguments @('shell', "rm -rf '$($c.devicePath)'"); return 'removed (absent at backup)' }
            return 'absent, as recorded'
        }
        $plain = Join-Path $runDir 'external.restore.tar'
        try {
            Unprotect-DaArchive -InFile (Join-Path $runDir $c.archive.file) -OutFile $plain -KeyFile $keyFile
            $null = Invoke-DaAdb -Serial $Serial -Arguments @('shell', "mkdir -p '$($c.devicePath)' && find '$($c.devicePath)' -mindepth 1 -delete")
            $x = Invoke-DaAdbFromFile -Serial $Serial -Arguments @('exec-in', "tar -xf - -C '$($c.devicePath)'") -InFile $plain
            if ($x.ExitCode -ne 0) { throw "extract into $($c.devicePath) failed: $($x.Stderr)" }
            "restored $($c.fileCount) files"
        }
        finally { if (Test-Path -LiteralPath $plain) { Remove-Item -LiteralPath $plain -Force } }
    }
    & $step 'stand-objects' {
        $c = $comp['stand-objects']
        if ($c -and -not $c.present -and (Test-DaRemoteDir -Serial $Serial -Path $c.devicePath)) {
            # A published n10000 set is ten thousand FUSE deletions; the 120 s default timed out mid-delete and left the root behind.
            $rm = Invoke-DaAdb -Serial $Serial -Arguments @('shell', "rm -rf '$($c.devicePath)'") -TimeoutMs 1800000
            if ($rm.ExitCode -ne 0) { throw "removing $($c.devicePath) failed (exit $($rm.ExitCode), timed out $($rm.TimedOut)): $($rm.Stderr)" }
            return 'removed stand-created fixture root'
        }
        'unchanged'
    }
    & $step 'permissions' {
        if (-not $manifest.package.present) { return 'package absent' }
        $now = @{}; foreach ($p in (Get-DaPackageState -Serial $Serial -Package $pkg).permissions) { $now[$p.name] = $p.granted }
        $changed = 0
        foreach ($p in @($manifest.permissions)) {
            if (-not $now.ContainsKey($p.name) -or [bool]$now[$p.name] -eq [bool]$p.granted) { continue }
            $verb = if ($p.granted) { 'grant' } else { 'revoke' }
            $null = Invoke-DaAdb -Serial $Serial -Arguments @('shell', 'pm', $verb, $pkg, $p.name)
            $changed++
        }
        "$changed permission(s) reset"
    }
    & $step 'settings' {
        $changed = 0
        $now = (Get-DaPackageState -Serial $Serial -Package $pkg)
        $nowSet = @{}; foreach ($s in $now.settings) { $nowSet["$($s.namespace)/$($s.key)"] = $s.value }
        foreach ($s in @($manifest.settings)) {
            if ([string]$nowSet["$($s.namespace)/$($s.key)"] -eq [string]$s.value) { continue }
            $cmd = if ($s.present) { @('shell', 'settings', 'put', $s.namespace, $s.key, [string]$s.value) } else { @('shell', 'settings', 'delete', $s.namespace, $s.key) }
            $null = Invoke-DaAdb -Serial $Serial -Arguments $cmd
            $changed++
        }
        foreach ($pair in @(@('size', 'size'), @('density', 'density'))) {
            $want = [string]$manifest.displayOverrides.($pair[0]); $have = [string]$now.displayOverrides.($pair[0])
            if ($want -eq $have) { continue }
            $arg = if ($want) { $want } else { 'reset' }
            $null = Invoke-DaAdb -Serial $Serial -Arguments @('shell', 'wm', $pair[1], $arg)
            $changed++
        }
        "$changed setting(s) reset"
    }
    $diffs = [System.Collections.Generic.List[string]]::new()
    & $step 'compare' {
        foreach ($d in @(Compare-DaDeviceWithManifest -Serial $Serial -Manifest $manifest)) { $diffs.Add($d) }
        if ($diffs.Count) { throw "device differs from manifest: $($diffs -join '; ')" }
        'device state equals the manifest'
    }
    $ok = -not ($steps | Where-Object { -not $_.ok })
    $entry = [ordered]@{ at = (Get-Date).ToUniversalTime().ToString('o'); ok = [bool]$ok; steps = $steps.ToArray() }
    $manifest.restores = @(@($manifest.restores) + $entry)
    Write-DaJson -Path $ManifestPath -InputObject $manifest -Durable
    if ($ok) {
        Remove-Item -LiteralPath $pendingPath -Force
        return [pscustomobject]@{ Code = $codes.Ok; Reason = "restored backup $($manifest.runId); device state equals the manifest; checkpoint cleared"; Steps = $steps.ToArray() }
    }
    $failed = ($steps | Where-Object { -not $_.ok } | ForEach-Object { "$($_.step): $($_.detail)" }) -join ' | '
    return [pscustomobject]@{ Code = $codes.Refused; Reason = "restore of $($manifest.runId) incomplete, checkpoint and backup kept ($($diffs.Count) differences): $failed"; Steps = $steps.ToArray() }
}

function Invoke-DaRecover {
    <#
    .SYNOPSIS
        Next-start recovery: runs the pending restore when restore-pending.json exists. Returns {Code; Reason}.
    .PARAMETER Serial
        A serial that already passed Assert-DaEmulatorTarget.
    #>
    param([Parameter(Mandatory)][string]$Serial)
    $pending = Read-DaJson -Path (Get-DaRestorePendingPath)
    if (-not $pending) { return [pscustomobject]@{ Code = (Get-DaExitCode).Ok; Reason = 'no restore pending; nothing to recover' } }
    $r = Restore-DaBackup -Serial $Serial -ManifestPath ([string]$pending.manifestPath)
    return [pscustomobject]@{ Code = $r.Code; Reason = "recovered interrupted run $($pending.runId) ($($pending.reason)): $($r.Reason)" }
}
