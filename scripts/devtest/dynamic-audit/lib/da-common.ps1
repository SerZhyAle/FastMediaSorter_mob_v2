#requires -Version 7.0
<#
.SYNOPSIS
    Shared library of the S4166 dynamic-audit stand: exit codes, monotonic clock, adb process wrappers, JSON IO, paths.

.DESCRIPTION
    Dot-source this file to get the functions below; it assigns nothing at script scope (a dot-sourced
    assignment lands in the CALLER's scope, S2441), so every value is returned by a function.

      Get-DaExitCode          the facade's exit-code names: Ok 0, Finding 1, Refused 2 (cannot verify or refused)
      Get-DaMonotonicMs       milliseconds from [System.Diagnostics.Stopwatch]::GetTimestamp(); never wall clock
      Get-DaPaths             repo root and the ticket scratch root temp/S4166 with its state, private, runs
                              and fixtures children; DA_STATE_DIR overrides the state directory (tests)
      Invoke-DaProcess        one System.Diagnostics.Process run: text or binary stdout, optional binary stdin
      Invoke-DaAdb            adb -s <serial> <args>: stdout lines, stderr, exit code, host elapsed ms
      Invoke-DaAdbToFile      binary-safe adb stdout capture to a host file (never the PowerShell pipeline)
      Invoke-DaAdbFromFile    binary-safe host file fed to adb stdin (exec-in)
      New-DaRunId             a sortable run id
      Read-DaJson/Write-DaJson  UTF-8 without BOM, LF, pretty JSON; -Durable flushes and renames atomically
      Get-DaFileSha256        lower-case SHA-256 of a file
      Expand-DaTar            pure-PowerShell ustar/GNU/PAX reader: lists or extracts a tar with exact
                              UTF-8 names and per-entry SHA-256, refusing absolute or parent-relative names
      Write-DaVerdict         the facade's single verdict line

    WHY A PROCESS AND NOT THE PIPELINE. A native command's stdout piped through PowerShell is decoded
    as text and re-joined by lines, which corrupts every tar archive and image it carries. Every binary
    transfer therefore copies Process.StandardOutput.BaseStream into a FileStream directly.

    WHY A TAR READER HERE. The Windows tar binaries on this workstation both mislead: the first one on
    PATH is Git's GNU tar, which treats a drive letter as a remote host, and System32 bsdtar decodes
    entry names through the ANSI code page. A backup's trial extraction must reproduce the exact
    device names, so the stand reads the archive itself.
#>

. (Join-Path $PSScriptRoot '..\..\lib\find-adb.ps1')

function Get-DaExitCode {
    <#
    .SYNOPSIS
        Exit-code names of the dynamic-audit facade.
    #>
    return [pscustomobject]@{ Ok = 0; Finding = 1; Refused = 2 }
}

function Get-DaMonotonicMs {
    <#
    .SYNOPSIS
        Monotonic host milliseconds (Stopwatch timestamp); comparable only within one boot of the host.
    #>
    $ticks = [System.Diagnostics.Stopwatch]::GetTimestamp()
    return [long][math]::Floor($ticks * 1000.0 / [System.Diagnostics.Stopwatch]::Frequency)
}

function Get-DaRepoRoot {
    <#
    .SYNOPSIS
        Repository root resolved from this library's own location (lib -> dynamic-audit -> devtest -> scripts -> root).
    #>
    return (Resolve-Path (Join-Path $PSScriptRoot '..\..\..\..')).Path
}

function Get-DaPaths {
    <#
    .SYNOPSIS
        Storage layout of the stand. Nothing is created here; callers create what they write.
    .PARAMETER TicketId
        Ticket id that names the scratch root under temp/.
    #>
    param([string]$TicketId = 'S4166')
    $root = Get-DaRepoRoot
    $scratch = Join-Path (Join-Path $root 'temp') $TicketId
    $state = if ([string]::IsNullOrWhiteSpace($env:DA_STATE_DIR)) { Join-Path $scratch 'state' } else { $env:DA_STATE_DIR }
    return [pscustomobject]@{
        RepoRoot    = $root
        ScratchRoot = $scratch
        State       = $state
        Private     = Join-Path $scratch 'private'
        Runs        = Join-Path $scratch 'runs'
        Fixtures    = Join-Path $scratch 'fixtures'
    }
}

function Invoke-DaProcess {
    <#
    .SYNOPSIS
        Runs one native process through System.Diagnostics.Process and returns its outcome.
    .PARAMETER FilePath
        Executable.
    .PARAMETER ArgumentList
        Arguments, each passed as one argv element.
    .PARAMETER StdoutFile
        When set, stdout bytes are copied unchanged into this file instead of being decoded.
    .PARAMETER StdinFile
        When set, the bytes of this file are fed to stdin and stdin is then closed.
    .PARAMETER TimeoutMs
        Hard limit; on expiry the process tree is killed and TimedOut is true.
    #>
    param(
        [Parameter(Mandatory)][string]$FilePath,
        [Parameter(Mandatory)][string[]]$ArgumentList,
        [string]$StdoutFile,
        [string]$StdinFile,
        [int]$TimeoutMs = 600000
    )
    $utf8 = [System.Text.UTF8Encoding]::new($false)
    $psi = [System.Diagnostics.ProcessStartInfo]::new($FilePath)
    foreach ($a in $ArgumentList) { $psi.ArgumentList.Add($a) }
    $psi.UseShellExecute = $false
    $psi.CreateNoWindow = $true
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $psi.RedirectStandardInput = -not [string]::IsNullOrEmpty($StdinFile)
    $psi.StandardErrorEncoding = $utf8
    if ([string]::IsNullOrEmpty($StdoutFile)) { $psi.StandardOutputEncoding = $utf8 }

    $started = Get-DaMonotonicMs
    $proc = [System.Diagnostics.Process]::Start($psi)
    $outStream = $null
    $copyTask = $null
    $outTask = $null
    $timedOut = $false
    try {
        $errTask = $proc.StandardError.ReadToEndAsync()
        if ([string]::IsNullOrEmpty($StdoutFile)) {
            $outTask = $proc.StandardOutput.ReadToEndAsync()
        }
        else {
            $outStream = [System.IO.FileStream]::new($StdoutFile, [System.IO.FileMode]::Create, [System.IO.FileAccess]::Write)
            $copyTask = $proc.StandardOutput.BaseStream.CopyToAsync($outStream)
        }
        if ($psi.RedirectStandardInput) {
            $inStream = [System.IO.File]::OpenRead($StdinFile)
            try {
                $inStream.CopyTo($proc.StandardInput.BaseStream)
                $proc.StandardInput.BaseStream.Flush()
            }
            finally {
                $inStream.Dispose()
                $proc.StandardInput.Close()
            }
        }
        if (-not $proc.WaitForExit($TimeoutMs)) {
            $timedOut = $true
            $proc.Kill($true)
        }
        $proc.WaitForExit()
        if ($copyTask) { $copyTask.Wait() }
        $stdoutText = if ($outTask) { $outTask.Result } else { '' }
        $stderrText = $errTask.Result
    }
    finally {
        if ($outStream) { $outStream.Dispose() }
    }
    $elapsed = (Get-DaMonotonicMs) - $started
    $lines = @()
    if ($stdoutText) { $lines = @(($stdoutText -replace "`r`n", "`n").TrimEnd("`n") -split "`n") }
    $bytes = 0L
    if (-not [string]::IsNullOrEmpty($StdoutFile) -and (Test-Path -LiteralPath $StdoutFile)) { $bytes = (Get-Item -LiteralPath $StdoutFile).Length }
    $exit = if ($timedOut) { -1 } else { $proc.ExitCode }
    $proc.Dispose()
    return [pscustomobject]@{
        ExitCode  = $exit
        TimedOut  = $timedOut
        Lines     = $lines
        Stderr    = $stderrText
        ElapsedMs = $elapsed
        Bytes     = $bytes
    }
}

function Get-DaAdbPath {
    <#
    .SYNOPSIS
        adb resolved through scripts/devtest/lib/find-adb.ps1; throws when absent.
    #>
    $adb = Find-Adb
    if (-not $adb) { throw 'adb not found (ANDROID_HOME, ANDROID_SDK_ROOT, PATH and the default SDK location were tried).' }
    return $adb
}

function Invoke-DaAdb {
    <#
    .SYNOPSIS
        adb -s <serial> <arguments>, returning stdout lines, stderr, exit code and host elapsed milliseconds.
    .PARAMETER Serial
        A serial that already passed Assert-DaEmulatorTarget.
    .PARAMETER Arguments
        adb arguments after -s <serial>.
    .PARAMETER TimeoutMs
        Hard limit for the call.
    #>
    param(
        [Parameter(Mandatory)][string]$Serial,
        [Parameter(Mandatory)][string[]]$Arguments,
        [int]$TimeoutMs = 120000
    )
    $adb = Get-DaAdbPath
    return Invoke-DaProcess -FilePath $adb -ArgumentList (@('-s', $Serial) + $Arguments) -TimeoutMs $TimeoutMs
}

function Invoke-DaAdbToFile {
    <#
    .SYNOPSIS
        Binary-safe capture of adb stdout (use exec-out) into a host file.
    .PARAMETER Serial
        A serial that already passed Assert-DaEmulatorTarget.
    .PARAMETER Arguments
        adb arguments after -s <serial>, normally starting with exec-out.
    .PARAMETER OutFile
        Host file that receives the raw bytes.
    .PARAMETER TimeoutMs
        Hard limit for the call.
    #>
    param(
        [Parameter(Mandatory)][string]$Serial,
        [Parameter(Mandatory)][string[]]$Arguments,
        [Parameter(Mandatory)][string]$OutFile,
        [int]$TimeoutMs = 1800000
    )
    $adb = Get-DaAdbPath
    return Invoke-DaProcess -FilePath $adb -ArgumentList (@('-s', $Serial) + $Arguments) -StdoutFile $OutFile -TimeoutMs $TimeoutMs
}

function Invoke-DaAdbFromFile {
    <#
    .SYNOPSIS
        Binary-safe feed of a host file into adb stdin (use exec-in).
    .PARAMETER Serial
        A serial that already passed Assert-DaEmulatorTarget.
    .PARAMETER Arguments
        adb arguments after -s <serial>, normally starting with exec-in.
    .PARAMETER InFile
        Host file whose bytes are written to stdin.
    .PARAMETER TimeoutMs
        Hard limit for the call.
    #>
    param(
        [Parameter(Mandatory)][string]$Serial,
        [Parameter(Mandatory)][string[]]$Arguments,
        [Parameter(Mandatory)][string]$InFile,
        [int]$TimeoutMs = 1800000
    )
    $adb = Get-DaAdbPath
    return Invoke-DaProcess -FilePath $adb -ArgumentList (@('-s', $Serial) + $Arguments) -StdinFile $InFile -TimeoutMs $TimeoutMs
}

function New-DaRunId {
    <#
    .SYNOPSIS
        Sortable run id: yyMMdd-HHmmss-<4 hex>. The id names files only; durations use Get-DaMonotonicMs.
    #>
    $suffix = [System.Convert]::ToHexString([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(2)).ToLowerInvariant()
    return ('{0}-{1}' -f (Get-Date -Format 'yyMMdd-HHmmss'), $suffix)
}

function Read-DaJson {
    <#
    .SYNOPSIS
        Reads a UTF-8 JSON file; returns $null when the file is absent.
    .PARAMETER Path
        File to read.
    #>
    param([Parameter(Mandatory)][string]$Path)
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return $null }
    $text = [System.IO.File]::ReadAllText($Path, [System.Text.UTF8Encoding]::new($false))
    return ($text | ConvertFrom-Json -Depth 64)
}

function Write-DaJson {
    <#
    .SYNOPSIS
        Writes an object as pretty JSON, UTF-8 without BOM, LF line ends.
    .PARAMETER Path
        Target file; its directory is created.
    .PARAMETER InputObject
        Object to serialise.
    .PARAMETER Durable
        Write a sibling temp file, flush it to disk, then rename it over the target, so a crash leaves
        either the old file or the new one and never a torn one.
    #>
    param(
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)]$InputObject,
        [switch]$Durable
    )
    $dir = Split-Path -Parent $Path
    if ($dir -and -not (Test-Path -LiteralPath $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    $json = ($InputObject | ConvertTo-Json -Depth 64) -replace "`r`n", "`n"
    $bytes = [System.Text.UTF8Encoding]::new($false).GetBytes($json + "`n")
    $target = if ($Durable) { "$Path.tmp" } else { $Path }
    $fs = [System.IO.FileStream]::new($target, [System.IO.FileMode]::Create, [System.IO.FileAccess]::Write)
    try {
        $fs.Write($bytes, 0, $bytes.Length)
        $fs.Flush($true)
    }
    finally { $fs.Dispose() }
    if ($Durable) { [System.IO.File]::Move($target, $Path, $true) }
}

function Get-DaFileSha256 {
    <#
    .SYNOPSIS
        Lower-case hex SHA-256 of a file.
    .PARAMETER Path
        File to hash.
    #>
    param([Parameter(Mandatory)][string]$Path)
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Get-DaBytesSha256 {
    <#
    .SYNOPSIS
        Lower-case hex SHA-256 of a byte array or of a UTF-8 string.
    .PARAMETER Bytes
        Bytes to hash.
    .PARAMETER Text
        Text to hash as UTF-8 without BOM.
    #>
    param([byte[]]$Bytes, [string]$Text)
    if ($PSBoundParameters.ContainsKey('Text')) { $Bytes = [System.Text.UTF8Encoding]::new($false).GetBytes($Text) }
    return [System.Convert]::ToHexString([System.Security.Cryptography.SHA256]::HashData($Bytes)).ToLowerInvariant()
}

function Read-DaTarField {
    <#
    .SYNOPSIS
        Reads a NUL-terminated header field of a tar block as UTF-8.
    .PARAMETER Block
        The 512-byte header.
    .PARAMETER Offset
        Field offset.
    .PARAMETER Length
        Field length.
    #>
    param([byte[]]$Block, [int]$Offset, [int]$Length)
    $end = $Offset
    while ($end -lt $Offset + $Length -and $Block[$end] -ne 0) { $end++ }
    return [System.Text.Encoding]::UTF8.GetString($Block, $Offset, $end - $Offset)
}

function Expand-DaTar {
    <#
    .SYNOPSIS
        Lists, and optionally extracts, a tar archive with exact UTF-8 names and per-file SHA-256.
    .DESCRIPTION
        Supports ustar (with prefix), GNU long names ('L') and PAX path records ('x'). Returns
        @{ Files = list of {path,size,sha256}; Dirs = list; Links = list; Errors = list }. A name that
        is absolute or contains a '..' segment is an error and is never written.
    .PARAMETER TarPath
        Archive to read.
    .PARAMETER Destination
        When set, regular files are written under this directory (the trial extraction).
    #>
    param(
        [Parameter(Mandatory)][string]$TarPath,
        [string]$Destination
    )
    $files = [System.Collections.Generic.List[object]]::new()
    $dirs = [System.Collections.Generic.List[string]]::new()
    $links = [System.Collections.Generic.List[string]]::new()
    $errors = [System.Collections.Generic.List[string]]::new()
    $fs = [System.IO.File]::OpenRead($TarPath)
    try {
        $header = [byte[]]::new(512)
        $buffer = [byte[]]::new(65536)
        $pendingName = $null
        while ($true) {
            $read = 0
            while ($read -lt 512) {
                $n = $fs.Read($header, $read, 512 - $read)
                if ($n -le 0) { break }
                $read += $n
            }
            if ($read -lt 512) { break }
            $allZero = $true
            foreach ($b in $header) { if ($b -ne 0) { $allZero = $false; break } }
            if ($allZero) { break }

            $name = Read-DaTarField -Block $header -Offset 0 -Length 100
            $sizeText = (Read-DaTarField -Block $header -Offset 124 -Length 12).Trim([char]0, ' ')
            $size = if ($sizeText) { [Convert]::ToInt64($sizeText, 8) } else { 0L }
            $type = [char]$header[156]
            $magic = Read-DaTarField -Block $header -Offset 257 -Length 6
            if ($magic -like 'ustar*') {
                $prefix = Read-DaTarField -Block $header -Offset 345 -Length 155
                if ($prefix) { $name = "$prefix/$name" }
            }
            $padded = [long]([math]::Ceiling($size / 512.0) * 512)

            if ($type -eq 'L' -or $type -eq 'x' -or $type -eq 'K' -or $type -eq 'g') {
                $data = [byte[]]::new($padded)
                $got = 0
                while ($got -lt $padded) {
                    $n = $fs.Read($data, $got, [int]($padded - $got))
                    if ($n -le 0) { break }
                    $got += $n
                }
                if ($type -eq 'L') {
                    $pendingName = [System.Text.Encoding]::UTF8.GetString($data, 0, [int]$size).TrimEnd([char]0)
                }
                elseif ($type -eq 'x') {
                    $text = [System.Text.Encoding]::UTF8.GetString($data, 0, [int]$size)
                    foreach ($rec in ($text -split "`n")) {
                        if ($rec -match '^\d+ path=(.*)$') { $pendingName = $Matches[1] }
                    }
                }
                continue
            }
            if ($pendingName) { $name = $pendingName; $pendingName = $null }
            $rel = ($name -replace '\\', '/') -replace '^\./', ''
            $rel = $rel.TrimEnd('/')
            $unsafe = ($rel.StartsWith('/') -or $rel -match '(^|/)\.\.(/|$)' -or $rel -match '^[A-Za-z]:')

            if ($type -eq '0' -or $type -eq [char]0 -or $type -eq '7') {
                $sha = [System.Security.Cryptography.IncrementalHash]::CreateHash([System.Security.Cryptography.HashAlgorithmName]::SHA256)
                $out = $null
                if ($unsafe) { $errors.Add("unsafe entry name: $rel") }
                elseif ($Destination) {
                    $target = Join-Path $Destination $rel
                    try {
                        $parent = Split-Path -Parent $target
                        if (-not (Test-Path -LiteralPath $parent)) { New-Item -ItemType Directory -Path $parent -Force | Out-Null }
                        $out = [System.IO.FileStream]::new($target, [System.IO.FileMode]::Create, [System.IO.FileAccess]::Write)
                    }
                    catch { $errors.Add("cannot extract ${rel}: $($_.Exception.Message)") }
                }
                $remaining = $size
                try {
                    while ($remaining -gt 0) {
                        $want = [int][math]::Min($buffer.Length, $remaining)
                        $n = $fs.Read($buffer, 0, $want)
                        if ($n -le 0) { $errors.Add("truncated archive at $rel"); break }
                        $sha.AppendData($buffer, 0, $n)
                        if ($out) { $out.Write($buffer, 0, $n) }
                        $remaining -= $n
                    }
                }
                finally { if ($out) { $out.Dispose() } }
                $skip = $padded - $size
                if ($skip -gt 0) { [void]$fs.Seek($skip, [System.IO.SeekOrigin]::Current) }
                $files.Add([pscustomobject]@{
                        path   = $rel
                        size   = $size
                        sha256 = [System.Convert]::ToHexString($sha.GetHashAndReset()).ToLowerInvariant()
                    })
                $sha.Dispose()
                continue
            }
            if ($type -eq '5') { if ($rel) { $dirs.Add($rel) } }
            elseif ($type -eq '1' -or $type -eq '2') { $links.Add($rel) }
            if ($padded -gt 0) { [void]$fs.Seek($padded, [System.IO.SeekOrigin]::Current) }
        }
    }
    finally { $fs.Dispose() }
    return [pscustomobject]@{ Files = $files.ToArray(); Dirs = $dirs.ToArray(); Links = $links.ToArray(); Errors = $errors.ToArray() }
}

function Write-DaVerdict {
    <#
    .SYNOPSIS
        Prints the facade's one verdict line: "da <verb>: OK|FINDING|REFUSED - <detail>".
    .PARAMETER Verb
        Facade verb.
    .PARAMETER Code
        Exit code the verdict stands for.
    .PARAMETER Detail
        One-line reason or summary.
    #>
    param(
        [Parameter(Mandatory)][string]$Verb,
        [Parameter(Mandatory)][int]$Code,
        [Parameter(Mandatory)][string]$Detail
    )
    $word = switch ($Code) { 0 { 'OK' } 1 { 'FINDING' } default { 'REFUSED' } }
    Write-Output ("da {0}: {1} - {2}" -f $Verb, $word, $Detail)
}
