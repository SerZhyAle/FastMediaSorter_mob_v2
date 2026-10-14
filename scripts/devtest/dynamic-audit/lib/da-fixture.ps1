#requires -Version 7.0
<#
.SYNOPSIS
    Deterministic synthetic fixture sets and the independent tree hash of the S4166 dynamic-audit stand.

.DESCRIPTION
    Dot-source after lib/da-common.ps1. Functions only; nothing is assigned at script scope.

      New-DaFixtureSet       generates a set on the host from a fixed seed into the scratch fixtures
                             directory and writes its manifest (relative path, size, SHA-256 per item):
                               small   about 30 items - JPEGs drawn with System.Drawing, short MP4 clips
                                       from ffmpeg, text files, an empty file, two deliberately corrupt
                                       images, Cyrillic, Hebrew, Arabic, CJK and emoji names, long names
                                       and a nested folder
                               n1000   1000 tiny JPEGs in one folder
                               n10000  10000 tiny JPEGs in one folder
      Publish-DaFixtureSet   pushes a set to a run-specific directory under the device's
                             Download/FastMediaSorter_DynAudit
      Get-DaTreeHash         hashes a device tree by relative path, size and content (sha256sum and stat
                             on the device), optionally through run-as for the private directory
      Get-DaLocalTreeHash    the same record shape for a host directory
      Compare-DaTreeHash     added, removed and changed entries between two trees

    WHY THE APP IS NOT ASKED. Fixture invariants (nothing lost, nothing duplicated, nothing silently
    altered) are judged by comparing these hashes before and after a scene, so the verdict never rests
    on what the app under test reports about itself.

    DETERMINISM. System.Random with a seed is the framework's seeded legacy generator, stable across
    .NET versions; shapes are drawn without anti-aliasing and without text, so the pixels do not depend
    on installed fonts; ffmpeg runs single-threaded with bitexact flags and no metadata. The manifest
    still records every hash, so a host whose encoder differs is detected rather than assumed equal.
#>

function Get-DaFixtureSetNames {
    <#
    .SYNOPSIS
        The defined fixture sets and their seeds.
    #>
    return [ordered]@{ small = 4166; n1000 = 41661000; n10000 = 416610000 }
}

function ConvertFrom-DaTreeListing {
    <#
    .SYNOPSIS
        Builds tree entries from device `sha256sum` and `stat -c '%s|%n'` output over one root (pure).
    .PARAMETER Root
        Absolute root path the listing was taken under; stripped from every name.
    .PARAMETER ShaLines
        Lines "<sha256>  <path>".
    .PARAMETER StatLines
        Lines "<size>|<path>".
    #>
    param([Parameter(Mandatory)][string]$Root, [string[]]$ShaLines, [string[]]$StatLines)
    $prefix = $Root.TrimEnd('/') + '/'
    $toRel = { param($p) $p = $p -replace '\\', '/'; if ($p.StartsWith($prefix)) { $p.Substring($prefix.Length) } else { $p -replace '^\./', '' } }
    $sizes = @{}
    foreach ($l in @($StatLines)) {
        if ($l -match '^(\d+)\|(.+)$') { $sizes[(& $toRel $Matches[2])] = [long]$Matches[1] }
    }
    $entries = foreach ($l in @($ShaLines)) {
        if ($l -match '^([0-9a-f]{64})\s+\*?(.+)$') {
            $rel = & $toRel $Matches[2]
            [pscustomobject]@{ path = $rel; size = if ($sizes.ContainsKey($rel)) { $sizes[$rel] } else { -1L }; sha256 = $Matches[1] }
        }
    }
    return New-DaTreeRecord -Entries @($entries)
}

function New-DaTreeRecord {
    <#
    .SYNOPSIS
        Wraps entries into {Entries; Count; TreeSha256}, ordinal-sorted by path (pure).
    .PARAMETER Entries
        {path; size; sha256} records.
    #>
    param([object[]]$Entries)
    $sorted = [System.Collections.Generic.List[object]]::new()
    foreach ($e in @($Entries)) { if ($e) { $sorted.Add($e) } }
    $sorted.Sort([System.Comparison[object]] { param($a, $b) [string]::CompareOrdinal([string]$a.path, [string]$b.path) })
    $sb = [System.Text.StringBuilder]::new()
    foreach ($e in $sorted) { [void]$sb.Append("$($e.path)|$($e.size)|$($e.sha256)`n") }
    return [pscustomobject]@{ Entries = $sorted.ToArray(); Count = $sorted.Count; TreeSha256 = Get-DaBytesSha256 -Text $sb.ToString() }
}

function Get-DaTreeHash {
    <#
    .SYNOPSIS
        Hashes a device directory tree by relative path, size and SHA-256 of content.
    .PARAMETER Serial
        A serial that already passed Assert-DaEmulatorTarget.
    .PARAMETER RemoteDir
        Absolute device directory.
    .PARAMETER RunAsPackage
        Read through run-as this package (app-private directories).
    #>
    param([Parameter(Mandatory)][string]$Serial, [Parameter(Mandatory)][string]$RemoteDir, [string]$RunAsPackage)
    $pre = if ($RunAsPackage) { "run-as $RunAsPackage " } else { '' }
    $sha = Invoke-DaAdb -Serial $Serial -Arguments @('shell', "${pre}find '$RemoteDir' -type f -exec sha256sum {} +") -TimeoutMs 600000
    if ($sha.ExitCode -ne 0) { throw "sha256sum over $RemoteDir failed (exit $($sha.ExitCode)): $($sha.Stderr)" }
    $st = Invoke-DaAdb -Serial $Serial -Arguments @('shell', "${pre}find '$RemoteDir' -type f -exec stat -c '%s|%n' {} +") -TimeoutMs 600000
    if ($st.ExitCode -ne 0) { throw "stat over $RemoteDir failed (exit $($st.ExitCode)): $($st.Stderr)" }
    $rec = ConvertFrom-DaTreeListing -Root $RemoteDir -ShaLines $sha.Lines -StatLines $st.Lines
    $rec | Add-Member -NotePropertyName ElapsedMs -NotePropertyValue ($sha.ElapsedMs + $st.ElapsedMs)
    return $rec
}

function Get-DaLocalTreeHash {
    <#
    .SYNOPSIS
        Hashes a host directory tree into the same record shape as Get-DaTreeHash.
    .PARAMETER Path
        Host directory.
    #>
    param([Parameter(Mandatory)][string]$Path)
    $root = (Resolve-Path -LiteralPath $Path).Path
    $entries = foreach ($f in (Get-ChildItem -LiteralPath $root -Recurse -File -Force)) {
        [pscustomobject]@{
            path   = ([System.IO.Path]::GetRelativePath($root, $f.FullName) -replace '\\', '/')
            size   = [long]$f.Length
            sha256 = Get-DaFileSha256 -Path $f.FullName
        }
    }
    return New-DaTreeRecord -Entries @($entries)
}

function Compare-DaTreeHash {
    <#
    .SYNOPSIS
        Added, removed and changed entries between two trees (pure). Accepts tree records or entry arrays.
    .PARAMETER Reference
        The before tree (or the manifest).
    .PARAMETER Difference
        The after tree.
    #>
    param([AllowEmptyCollection()][object[]]$Reference, [AllowEmptyCollection()][object[]]$Difference)
    $unwrap = { param($x) if ($x.Count -eq 1 -and $null -ne $x[0] -and $x[0].PSObject.Properties['Entries']) { @($x[0].Entries) } else { @($x | Where-Object { $_ }) } }
    $ref = @{}; foreach ($e in (& $unwrap @($Reference))) { $ref[[string]$e.path] = $e }
    $dif = @{}; foreach ($e in (& $unwrap @($Difference))) { $dif[[string]$e.path] = $e }
    $added = @($dif.Keys | Where-Object { -not $ref.ContainsKey($_) } | Sort-Object)
    $removed = @($ref.Keys | Where-Object { -not $dif.ContainsKey($_) } | Sort-Object)
    $changed = @($ref.Keys | Where-Object { $dif.ContainsKey($_) -and (
                [string]$ref[$_].sha256 -ne [string]$dif[$_].sha256 -or [long]$ref[$_].size -ne [long]$dif[$_].size) } | Sort-Object)
    $count = $added.Count + $removed.Count + $changed.Count
    $sample = (@($added | ForEach-Object { "+$_" }) + @($removed | ForEach-Object { "-$_" }) + @($changed | ForEach-Object { "~$_" }) | Select-Object -First 5) -join ', '
    return [pscustomobject]@{
        Added   = $added
        Removed = $removed
        Changed = $changed
        Count   = $count
        Summary = "$($added.Count) added, $($removed.Count) removed, $($changed.Count) changed$(if ($sample) { " ($sample)" })"
    }
}

function Get-DaFfmpegPath {
    <#
    .SYNOPSIS
        ffmpeg.exe from PATH or the WinGet links directory; $null when absent.
    #>
    $cmd = Get-Command ffmpeg.exe -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($cmd) { return $cmd.Source }
    $links = Join-Path $env:LOCALAPPDATA 'Microsoft\WinGet\Links\ffmpeg.exe'
    if (Test-Path -LiteralPath $links) { return $links }
    return $null
}

function New-DaFixtureJpeg {
    <#
    .SYNOPSIS
        Draws a deterministic JPEG (filled background, aliased rectangles and ellipses) from a seeded generator.
    .PARAMETER Path
        Output file.
    .PARAMETER Width
        Pixels.
    .PARAMETER Height
        Pixels.
    .PARAMETER Random
        Seeded System.Random shared by the set.
    .PARAMETER Shapes
        Number of shapes drawn.
    #>
    param(
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)][int]$Width,
        [Parameter(Mandatory)][int]$Height,
        [Parameter(Mandatory)][System.Random]$Random,
        [int]$Shapes = 6
    )
    Add-Type -AssemblyName System.Drawing
    $bmp = [System.Drawing.Bitmap]::new($Width, $Height, [System.Drawing.Imaging.PixelFormat]::Format24bppRgb)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    try {
        $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::None
        $g.Clear([System.Drawing.Color]::FromArgb($Random.Next(256), $Random.Next(256), $Random.Next(256)))
        for ($i = 0; $i -lt $Shapes; $i++) {
            $brush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb($Random.Next(256), $Random.Next(256), $Random.Next(256)))
            $x = $Random.Next([math]::Max(1, $Width)); $y = $Random.Next([math]::Max(1, $Height))
            $w = 1 + $Random.Next([math]::Max(1, [int]($Width / 2))); $h = 1 + $Random.Next([math]::Max(1, [int]($Height / 2)))
            if ($Random.Next(2) -eq 0) { $g.FillRectangle($brush, $x, $y, $w, $h) } else { $g.FillEllipse($brush, $x, $y, $w, $h) }
            $brush.Dispose()
        }
        $codec = [System.Drawing.Imaging.ImageCodecInfo]::GetImageEncoders() | Where-Object { $_.MimeType -eq 'image/jpeg' } | Select-Object -First 1
        $ep = [System.Drawing.Imaging.EncoderParameters]::new(1)
        $ep.Param[0] = [System.Drawing.Imaging.EncoderParameter]::new([System.Drawing.Imaging.Encoder]::Quality, [long]85)
        $bmp.Save($Path, $codec, $ep)
        $ep.Dispose()
    }
    finally { $g.Dispose(); $bmp.Dispose() }
}

function Add-DaJpegComment {
    <#
    .SYNOPSIS
        Inserts a JPEG comment segment (FF FE) right after SOI; the image data is untouched and the file stays a valid JPEG.
    .DESCRIPTION
        A 16x16 JPEG is about 650 bytes and the app's default image size filter hides images below 1 KiB, so the
        large sets would list as empty; the comment lifts every tiny image above the filter deterministically.
    .PARAMETER Path
        JPEG file rewritten in place.
    .PARAMETER Bytes
        Comment payload length.
    #>
    param([Parameter(Mandatory)][string]$Path, [ValidateRange(1, 65533)][int]$Bytes = 1100)
    $b = [System.IO.File]::ReadAllBytes($Path)
    if ($b.Length -lt 4 -or $b[0] -ne 0xFF -or $b[1] -ne 0xD8) { throw "not a JPEG: $Path" }
    $text = [System.Text.Encoding]::ASCII.GetBytes(('S4166 dynamic-audit fixture padding. ' * ([int][math]::Ceiling($Bytes / 37.0) + 1)).Substring(0, $Bytes))
    $len = $text.Length + 2
    $ms = [System.IO.MemoryStream]::new()
    try {
        $ms.Write($b, 0, 2)
        $ms.Write([byte[]](0xFF, 0xFE, (($len -shr 8) -band 0xFF), ($len -band 0xFF)), 0, 4)
        $ms.Write($text, 0, $text.Length)
        $ms.Write($b, 2, $b.Length - 2)
        [System.IO.File]::WriteAllBytes($Path, $ms.ToArray())
    }
    finally { $ms.Dispose() }
}

function New-DaFixtureVideo {
    <#
    .SYNOPSIS
        Encodes a short deterministic MP4 from an ffmpeg lavfi source; returns $true on success.
    .PARAMETER Ffmpeg
        ffmpeg.exe path.
    .PARAMETER Path
        Output file.
    .PARAMETER Source
        lavfi video source expression.
    .PARAMETER Audio
        Add a sine audio track.
    #>
    param([Parameter(Mandatory)][string]$Ffmpeg, [Parameter(Mandatory)][string]$Path, [Parameter(Mandatory)][string]$Source, [switch]$Audio)
    foreach ($codec in @('libx264', 'mpeg4')) {
        $a = @('-y', '-hide_banner', '-loglevel', 'error', '-f', 'lavfi', '-i', $Source)
        if ($Audio) { $a += @('-f', 'lavfi', '-i', 'sine=frequency=440:sample_rate=44100:duration=2') }
        $a += @('-c:v', $codec, '-pix_fmt', 'yuv420p', '-threads', '1')
        if ($Audio) { $a += @('-c:a', 'aac', '-b:a', '64k', '-shortest') }
        $a += @('-map_metadata', '-1', '-fflags', '+bitexact', '-flags:v', '+bitexact', '-flags:a', '+bitexact', '-movflags', '+faststart', $Path)
        $r = Invoke-DaProcess -FilePath $Ffmpeg -ArgumentList $a -TimeoutMs 120000
        if ($r.ExitCode -eq 0 -and (Test-Path -LiteralPath $Path)) { return $true }
    }
    return $false
}

function New-DaFixtureSet {
    <#
    .SYNOPSIS
        Generates (or reuses) a deterministic fixture set on the host; returns its manifest object.
    .PARAMETER Name
        small, n1000 or n10000.
    .PARAMETER Root
        Fixtures root; defaults to the scratch fixtures directory.
    .PARAMETER Force
        Regenerate even when a matching set exists.
    .PARAMETER SkipVideo
        Omit the MP4 clips (recorded in the manifest as skipped).
    #>
    param(
        [Parameter(Mandatory)][ValidateSet('small', 'n1000', 'n10000')][string]$Name,
        [string]$Root,
        [switch]$Force,
        [switch]$SkipVideo
    )
    if (-not $Root) { $Root = (Get-DaPaths).Fixtures }
    $setDir = Join-Path $Root $Name
    $filesDir = Join-Path $setDir 'files'
    $manifestPath = Join-Path $setDir 'manifest.json'
    if (-not $Force -and (Test-Path -LiteralPath $manifestPath) -and (Test-Path -LiteralPath $filesDir)) {
        $existing = Read-DaJson -Path $manifestPath
        $now = Get-DaLocalTreeHash -Path $filesDir
        if ($existing -and $existing.treeSha256 -eq $now.TreeSha256) { return $existing }
    }
    if (Test-Path -LiteralPath $setDir) { Remove-Item -LiteralPath $setDir -Recurse -Force }
    New-Item -ItemType Directory -Path $filesDir -Force | Out-Null
    $seed = (Get-DaFixtureSetNames)[$Name]
    $rng = [System.Random]::new($seed)
    $kinds = @{}
    $skipped = [System.Collections.Generic.List[string]]::new()
    $add = { param($rel, $kind) $kinds[$rel] = $kind; Join-Path $filesDir $rel }

    if ($Name -eq 'small') {
        $sizes = @(@(64, 64), @(320, 240), @(640, 480), @(1024, 768), @(480, 640), @(1920, 1080), @(1080, 1920), @(100, 1000),
            @(1000, 100), @(256, 256), @(800, 600), @(600, 800), @(32, 32), @(1280, 720), @(720, 1280), @(400, 300))
        for ($i = 0; $i -lt $sizes.Count; $i++) {
            New-DaFixtureJpeg -Path (& $add ('IMG_{0:D4}.jpg' -f ($i + 1)) 'jpeg') -Width $sizes[$i][0] -Height $sizes[$i][1] -Random $rng
        }
        New-Item -ItemType Directory -Path (Join-Path $filesDir 'nested/deeper') -Force | Out-Null
        New-DaFixtureJpeg -Path (& $add 'nested/deeper/IMG_nested.jpg' 'jpeg') -Width 200 -Height 150 -Random $rng
        $s = { param([int[]]$cps) -join ($cps | ForEach-Object { [char]::ConvertFromUtf32($_) }) }
        $unicode = @(
            ((& $s 0x0444, 0x043E, 0x0442, 0x043E) + '_' + (& $s 0x0442, 0x0435, 0x0441, 0x0442) + '.jpg'),
            ((& $s 0x05EA, 0x05DE, 0x05D5, 0x05E0, 0x05D4) + '.jpg'),
            ((& $s 0x0635, 0x0648, 0x0631, 0x0629) + '.jpg'),
            ('report_' + (& $s 0x062A, 0x0642, 0x0631, 0x064A, 0x0631) + '_2026.jpg'),
            ((& $s 0x5199, 0x771F) + '.jpg'),
            ((& $s 0x1F4F7) + '_camera.jpg')
        )
        foreach ($u in $unicode) { New-DaFixtureJpeg -Path (& $add $u 'jpeg-unicode-name') -Width 160 -Height 120 -Random $rng }
        New-DaFixtureJpeg -Path (& $add ('long_name_' + ('abcdefghij' * 14) + '.jpg') 'jpeg-long-name') -Width 120 -Height 90 -Random $rng
        New-DaFixtureJpeg -Path (& $add (('Long name with spaces ' * 6).Trim() + '.jpg') 'jpeg-long-name') -Width 120 -Height 90 -Random $rng

        $ffmpeg = if ($SkipVideo) { $null } else { Get-DaFfmpegPath }
        if ($ffmpeg) {
            $clips = @(
                @('clip_testsrc_2s.mp4', 'testsrc2=size=320x240:rate=15:duration=2', $true),
                @('clip_color_1s.mp4', 'color=c=0x3366cc:size=176x144:rate=10:duration=1', $false),
                @('clip_portrait_2s.mp4', 'testsrc=size=240x320:rate=15:duration=2', $false)
            )
            foreach ($c in $clips) {
                $ok = New-DaFixtureVideo -Ffmpeg $ffmpeg -Path (& $add $c[0] 'mp4') -Source $c[1] -Audio:([bool]$c[2])
                if (-not $ok) { $kinds.Remove($c[0]); $skipped.Add("$($c[0]): ffmpeg encode failed") }
            }
        }
        else { $skipped.Add('mp4 clips: ffmpeg not resolved or -SkipVideo') }

        $utf8 = [System.Text.UTF8Encoding]::new($false)
        $lines = for ($i = 1; $i -le 40; $i++) { "line $i value $($rng.Next(100000))" }
        [System.IO.File]::WriteAllText((& $add 'notes.txt' 'text'), (($lines -join "`n") + "`n"), $utf8)
        [System.IO.File]::WriteAllText((& $add 'readme.md' 'text'), "# Synthetic fixture`n`nGenerated for S4166, seed $seed.`n", $utf8)
        $csv = @('id,name,value') + (1..25 | ForEach-Object { "$_,item$_,$($rng.Next(1000))" })
        [System.IO.File]::WriteAllText((& $add 'data.csv' 'text'), (($csv -join "`n") + "`n"), $utf8)
        [System.IO.File]::WriteAllBytes((& $add 'empty.txt' 'text-empty'), [byte[]]::new(0))

        $donor = Join-Path $setDir 'donor.jpg'
        New-DaFixtureJpeg -Path $donor -Width 300 -Height 200 -Random $rng
        $donorBytes = [System.IO.File]::ReadAllBytes($donor)
        Remove-Item -LiteralPath $donor -Force
        [System.IO.File]::WriteAllBytes((& $add 'corrupt_truncated.jpg' 'corrupt'), [byte[]]$donorBytes[0..299])
        $garbage = [byte[]]::new(2048); $rng.NextBytes($garbage)
        $png = [byte[]](0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + $garbage
        [System.IO.File]::WriteAllBytes((& $add 'corrupt_garbage.png' 'corrupt'), [byte[]]$png)
    }
    else {
        $n = if ($Name -eq 'n1000') { 1000 } else { 10000 }
        for ($i = 1; $i -le $n; $i++) {
            $tiny = & $add ('tiny_{0:D5}.jpg' -f $i) 'jpeg-tiny-padded'
            New-DaFixtureJpeg -Path $tiny -Width 16 -Height 16 -Random $rng -Shapes 2
            Add-DaJpegComment -Path $tiny
        }
    }

    $tree = Get-DaLocalTreeHash -Path $filesDir
    $items = foreach ($e in $tree.Entries) {
        [ordered]@{ path = $e.path; size = $e.size; sha256 = $e.sha256; kind = if ($kinds.ContainsKey($e.path)) { $kinds[$e.path] } else { 'other' } }
    }
    $manifest = [ordered]@{
        schemaVersion = 1
        kind          = 'da-fixture-set'
        set           = $Name
        seed          = $seed
        count         = $tree.Count
        treeSha256    = $tree.TreeSha256
        skipped       = $skipped.ToArray()
        items         = @($items)
    }
    Write-DaJson -Path $manifestPath -InputObject $manifest
    return (Read-DaJson -Path $manifestPath)
}

function Get-DaFixtureRemoteRoot {
    <#
    .SYNOPSIS
        Device root under which every fixture run directory is created.
    #>
    return '/storage/emulated/0/Download/FastMediaSorter_DynAudit'
}

function Publish-DaFixtureSet {
    <#
    .SYNOPSIS
        Pushes a generated set to a fresh run directory under Download/FastMediaSorter_DynAudit; returns {RemoteDir; Count; PushMs}.
    .PARAMETER Serial
        A serial that already passed Assert-DaEmulatorTarget.
    .PARAMETER Name
        small, n1000 or n10000.
    .PARAMETER RunId
        Run id naming the remote directory; generated when empty.
    .PARAMETER Root
        Fixtures root; defaults to the scratch fixtures directory.
    #>
    param(
        [Parameter(Mandatory)][string]$Serial,
        [Parameter(Mandatory)][ValidateSet('small', 'n1000', 'n10000')][string]$Name,
        [string]$RunId,
        [string]$Root
    )
    $manifest = New-DaFixtureSet -Name $Name -Root $Root
    if (-not $Root) { $Root = (Get-DaPaths).Fixtures }
    if (-not $RunId) { $RunId = New-DaRunId }
    $remoteRoot = Get-DaFixtureRemoteRoot
    $remoteDir = "$remoteRoot/$RunId-$Name"
    $exists = Invoke-DaAdb -Serial $Serial -Arguments @('shell', "test -e '$remoteDir' && echo yes || echo no")
    if (($exists.Lines -join '').Trim() -eq 'yes') { throw "remote fixture directory already exists: $remoteDir" }
    $mk = Invoke-DaAdb -Serial $Serial -Arguments @('shell', 'mkdir', '-p', $remoteRoot)
    if ($mk.ExitCode -ne 0) { throw "mkdir $remoteRoot failed: $($mk.Stderr)" }
    $push = Invoke-DaAdb -Serial $Serial -Arguments @('push', (Join-Path (Join-Path $Root $Name) 'files'), $remoteDir) -TimeoutMs 1800000
    if ($push.ExitCode -ne 0) { throw "adb push of fixture $Name failed: $($push.Stderr) $($push.Lines -join ' ')" }
    return [pscustomobject]@{ RemoteDir = $remoteDir; Count = $manifest.count; PushMs = $push.ElapsedMs; Manifest = $manifest }
}
