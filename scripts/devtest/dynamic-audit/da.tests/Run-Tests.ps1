#requires -Version 7.0
<#
.SYNOPSIS
    Contract suite of the S4166 dynamic-audit stand, run without a device.

.DESCRIPTION
    Covers the pure parts of scripts/devtest/dynamic-audit/: tree-hash determinism and diff, the device
    listing parser, the tar reader (UTF-8 and GNU long names, path-traversal refusal), the archive
    encryption round trip and tamper detection, the backup manifest shape, the runtime-permission
    parser, JPEG fixture determinism, and the refusal path of the verified-backup precondition through
    the DA_STATE_DIR override - both in-process and through the facade, whose emulator guard must refuse
    a non-emulator serial before any adb call.

    Statistics and validity (lib/da-stats.ps1): percentile values on a known series, the p99 sample-size
    flag, failures ranked into the tail, block-bootstrap determinism for a fixed seed, slope recovery on a
    noisy linear series, a plateau after warm-up that must not report growth, the three budget verdicts
    with the crossing case, every invalid-run trigger returning the refused code, and thresholds read from
    the calibration record rather than a call site. Probe and sampler parsers (stat, meminfo, framestats,
    gfxinfo summary, am start), the tick scheduler's lateness and missed-tick accounting, the seeded run
    order and the paired summary are covered without a device.

    Faults and scenes (lib/da-fault.ps1, lib/da-ui.ps1, lib/da-scenes.ps1): the live fault catalogue and its
    shape errors, the run-directory confinement of every injector through a fake adb, the free-space
    filler arithmetic and its cleanup, the accessibility-event parser and outcome channels, message
    readability, tree expectations, crash signals, the scene verdict and registry, the mutation envelope
    (restore after a failing body, no restore after a refused checkpoint, a failed restore wins), and the
    heavy-run sequence with stubs: step order, restore in finally after a scene failure, no mutation after
    a refused preflight, an outside-the-run change reported as a finding.

    Every artefact is written under a fresh directory in the system temp folder and removed at the end;
    the real state directory under temp/S4166 is never read or written.

.NOTES
    Subject: scripts/devtest/dynamic-audit/lib

    Exit codes:
      0 - every case passed
      1 - a case failed
#>
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$daSuiteRoot = Join-Path ([System.IO.Path]::GetTempPath()) ('da-suite-' + [guid]::NewGuid().ToString('N'))
$daStandDir = Split-Path -Parent $PSScriptRoot
$daFacade = Join-Path $daStandDir 'da.ps1'
. (Join-Path $PSScriptRoot '..\lib\da-common.ps1')
. (Join-Path $PSScriptRoot '..\lib\da-emulator.ps1')
. (Join-Path $PSScriptRoot '..\lib\da-fixture.ps1')
. (Join-Path $PSScriptRoot '..\lib\da-state.ps1')
. (Join-Path $PSScriptRoot '..\lib\da-stats.ps1')
. (Join-Path $PSScriptRoot '..\lib\da-sampler.ps1')
. (Join-Path $PSScriptRoot '..\lib\da-probe.ps1')
. (Join-Path $PSScriptRoot '..\lib\da-fault.ps1')
. (Join-Path $PSScriptRoot '..\lib\da-ui.ps1')
. (Join-Path $PSScriptRoot '..\lib\da-scenes.ps1')

$script:daPassed = 0
$script:daFailed = [System.Collections.Generic.List[string]]::new()
function Assert-Case {
    param([Parameter(Mandatory)][string]$Name, [Parameter(Mandatory)][bool]$Condition, [string]$Detail = '')
    if ($Condition) { $script:daPassed++ } else { $script:daFailed.Add("$Name $Detail") ; Write-Host "FAIL: $Name $Detail" }
}

function New-TestTar {
    param([string]$Path, [object[]]$Entries)
    $fs = [System.IO.File]::Create($Path)
    try {
        foreach ($e in $Entries) {
            $data = [System.Text.Encoding]::UTF8.GetBytes([string]$e.content)
            $nameBytes = [System.Text.Encoding]::UTF8.GetBytes([string]$e.name)
            if ($nameBytes.Length -gt 99) {
                $long = [byte[]]::new(512)
                [System.Text.Encoding]::ASCII.GetBytes('././@LongLink').CopyTo($long, 0)
                [System.Text.Encoding]::ASCII.GetBytes([Convert]::ToString($nameBytes.Length + 1, 8).PadLeft(11, '0')).CopyTo($long, 124)
                $long[156] = [byte][char]'L'
                [System.Text.Encoding]::ASCII.GetBytes('ustar ').CopyTo($long, 257)
                $fs.Write($long, 0, 512)
                $padLen = [int]([math]::Ceiling(($nameBytes.Length + 1) / 512.0) * 512)
                $body = [byte[]]::new($padLen); $nameBytes.CopyTo($body, 0)
                $fs.Write($body, 0, $padLen)
                $nameBytes = $nameBytes[0..98]
            }
            $h = [byte[]]::new(512)
            ([byte[]]$nameBytes).CopyTo($h, 0)
            [System.Text.Encoding]::ASCII.GetBytes([Convert]::ToString($data.Length, 8).PadLeft(11, '0')).CopyTo($h, 124)
            $h[156] = [byte][char]'0'
            [System.Text.Encoding]::ASCII.GetBytes('ustar').CopyTo($h, 257)
            $fs.Write($h, 0, 512)
            $fs.Write($data, 0, $data.Length)
            $pad = (512 - ($data.Length % 512)) % 512
            if ($pad) { $fs.Write([byte[]]::new($pad), 0, $pad) }
        }
        $fs.Write([byte[]]::new(1024), 0, 1024)
    }
    finally { $fs.Dispose() }
}

$daSavedState = $env:DA_STATE_DIR
try {
    New-Item -ItemType Directory -Path $daSuiteRoot -Force | Out-Null

    # 1. exit-code names and monotonic clock
    $codes = Get-DaExitCode
    Assert-Case 'exit codes 0/1/2' ($codes.Ok -eq 0 -and $codes.Finding -eq 1 -and $codes.Refused -eq 2)
    $t1 = Get-DaMonotonicMs; $t2 = Get-DaMonotonicMs
    Assert-Case 'monotonic clock does not go back' ($t2 -ge $t1)

    # 2. local tree hash is deterministic and order independent, Unicode names included
    $tree = Join-Path $daSuiteRoot 'tree'
    New-Item -ItemType Directory -Path (Join-Path $tree 'sub') -Force | Out-Null
    $cyr = -join ([char]0x0444, [char]0x043E, [char]0x0442, [char]0x043E)
    $heb = -join ([char]0x05EA, [char]0x05DE, [char]0x05D5)
    $utf8 = [System.Text.UTF8Encoding]::new($false)
    [System.IO.File]::WriteAllText((Join-Path $tree 'a.txt'), "alpha`n", $utf8)
    [System.IO.File]::WriteAllText((Join-Path $tree "$cyr.txt"), "cyr`n", $utf8)
    [System.IO.File]::WriteAllText((Join-Path $tree "sub/$heb.txt"), "heb`n", $utf8)
    $h1 = Get-DaLocalTreeHash -Path $tree
    $h2 = Get-DaLocalTreeHash -Path $tree
    Assert-Case 'tree hash deterministic' ($h1.TreeSha256 -eq $h2.TreeSha256 -and $h1.Count -eq 3)
    Assert-Case 'tree paths use forward slashes' (@($h1.Entries.path) -contains "sub/$heb.txt")
    $shuffled = New-DaTreeRecord -Entries @($h1.Entries[2], $h1.Entries[0], $h1.Entries[1])
    Assert-Case 'tree hash order independent' ($shuffled.TreeSha256 -eq $h1.TreeSha256)

    # 3. diff reports added, removed and changed
    $same = Compare-DaTreeHash -Reference $h1 -Difference $h2
    Assert-Case 'identical trees have 0 differences' ($same.Count -eq 0)
    [System.IO.File]::WriteAllText((Join-Path $tree 'a.txt'), "alpha changed`n", $utf8)
    Remove-Item -LiteralPath (Join-Path $tree "$cyr.txt")
    [System.IO.File]::WriteAllText((Join-Path $tree 'new.txt'), "new`n", $utf8)
    $h3 = Get-DaLocalTreeHash -Path $tree
    $d = Compare-DaTreeHash -Reference $h1 -Difference $h3
    Assert-Case 'diff counts' ($d.Count -eq 3 -and $d.Added -contains 'new.txt' -and $d.Removed -contains "$cyr.txt" -and $d.Changed -contains 'a.txt') $d.Summary
    Assert-Case 'diff against empty' ((Compare-DaTreeHash -Reference $h3 -Difference @()).Removed.Count -eq 3)

    # 4. the device listing parser yields the same record as a host hash of the same content
    $root = '/storage/emulated/0/Download/X'
    $shaLines = @($h3.Entries | ForEach-Object { "$($_.sha256)  $root/$($_.path)" })
    $statLines = @($h3.Entries | ForEach-Object { "$($_.size)|$root/$($_.path)" })
    $parsed = ConvertFrom-DaTreeListing -Root $root -ShaLines $shaLines -StatLines $statLines
    Assert-Case 'device listing parser matches host tree' ($parsed.TreeSha256 -eq $h3.TreeSha256) "$($parsed.TreeSha256) vs $($h3.TreeSha256)"

    # 5. tar reader: UTF-8 names, GNU long name, trial extraction, traversal refusal
    $longName = 'long_' + ('x' * 150) + '.txt'
    $tarPath = Join-Path $daSuiteRoot 'probe.tar'
    New-TestTar -Path $tarPath -Entries @(
        [pscustomobject]@{ name = './shared_prefs/app.xml'; content = '<map/>' },
        [pscustomobject]@{ name = "./files/$cyr.bin"; content = 'cyr payload' },
        [pscustomobject]@{ name = "./$longName"; content = 'long' }
    )
    $trial = Join-Path $daSuiteRoot 'trial'
    $x = Expand-DaTar -TarPath $tarPath -Destination $trial
    Assert-Case 'tar reader lists 3 files, no errors' ($x.Files.Count -eq 3 -and $x.Errors.Count -eq 0) ($x.Errors -join ';')
    Assert-Case 'tar reader keeps UTF-8 and long names' ((@($x.Files.path) -contains "files/$cyr.bin") -and (@($x.Files.path) -contains $longName))
    $onDisk = Get-DaLocalTreeHash -Path $trial
    Assert-Case 'trial extraction equals tar listing' ((Compare-DaTreeHash -Reference $x.Files -Difference $onDisk).Count -eq 0)
    $evil = Join-Path $daSuiteRoot 'evil.tar'
    New-TestTar -Path $evil -Entries @([pscustomobject]@{ name = '../escape.txt'; content = 'nope' })
    $ex = Expand-DaTar -TarPath $evil -Destination (Join-Path $daSuiteRoot 'trial-evil')
    Assert-Case 'tar reader refuses parent-relative names' ($ex.Errors.Count -eq 1 -and -not (Test-Path -LiteralPath (Join-Path $daSuiteRoot 'escape.txt')))

    # 6. archive encryption round trip; a flipped byte is refused
    $plain = Join-Path $daSuiteRoot 'plain.bin'
    [System.IO.File]::WriteAllBytes($plain, [System.Security.Cryptography.RandomNumberGenerator]::GetBytes(200000))
    $enc = Join-Path $daSuiteRoot 'plain.bin.enc'
    $key = Join-Path $daSuiteRoot 'key.dpapi'
    Protect-DaArchive -InFile $plain -OutFile $enc -KeyFile $key
    $back = Join-Path $daSuiteRoot 'back.bin'
    Unprotect-DaArchive -InFile $enc -OutFile $back -KeyFile $key
    Assert-Case 'encryption round trip' ((Get-DaFileSha256 -Path $plain) -eq (Get-DaFileSha256 -Path $back))
    Assert-Case 'ciphertext differs from plain' ((Get-DaFileSha256 -Path $plain) -ne (Get-DaFileSha256 -Path $enc))
    $bytes = [System.IO.File]::ReadAllBytes($enc); $bytes[100] = $bytes[100] -bxor 0xFF; [System.IO.File]::WriteAllBytes($enc, $bytes)
    $tamperRefused = $false
    try { Unprotect-DaArchive -InFile $enc -OutFile $back -KeyFile $key } catch { $tamperRefused = $_.Exception.Message -match 'authentication tag' }
    Assert-Case 'tampered archive refused' $tamperRefused

    # 7. manifest shape
    $subject = [pscustomobject]@{ package = 'com.example.debug'; installed = $true; versionName = '1.0'; versionCode = '1'; firstInstallTime = 'x'; apkSha256 = $null; hostApk = $null }
    $device = [pscustomobject]@{ serial = 'emulator-5554'; avd = 'Pixel_9'; api = '35' }
    $comp = @([ordered]@{ id = 'private'; devicePath = '/data/user/0/com.example.debug'; present = $true; archive = [ordered]@{ file = 'private.tar.enc' }; files = @($h3.Entries) },
        [ordered]@{ id = 'external'; devicePath = '/storage/emulated/0/Android/data/com.example.debug'; present = $false })
    $state = [pscustomobject]@{ permissions = @([pscustomobject]@{ name = 'android.permission.CAMERA'; granted = $true }); settings = @(); displayOverrides = [pscustomobject]@{ size = $null; density = $null } }
    $manifest = New-DaManifestObject -RunId 'r1' -Device $device -Subject $subject -Components $comp -State $state
    $shape = @(Test-DaManifestShape -Manifest ($manifest | ConvertTo-Json -Depth 64 | ConvertFrom-Json))
    Assert-Case 'manifest shape valid' ($shape.Count -eq 0) ($shape -join ';')
    Assert-Case 'manifest starts unverified' ($manifest.verified.status -eq 'unverified')
    Assert-Case 'manifest restore order 1..n' (($manifest.restoreCommands | ForEach-Object { $_.order }) -join ',' -eq ((1..$manifest.restoreCommands.Count) -join ','))
    Assert-Case 'manifest absolute device paths' (@($manifest.components | Where-Object { -not ([string]$_.devicePath).StartsWith('/') }).Count -eq 0)
    $bad = $manifest | ConvertTo-Json -Depth 64 | ConvertFrom-Json
    $bad | Add-Member -NotePropertyName password -NotePropertyValue 'x'
    Assert-Case 'manifest with a secret-shaped field rejected' (@(Test-DaManifestShape -Manifest $bad).Count -ge 1)
    $bad2 = $manifest | ConvertTo-Json -Depth 64 | ConvertFrom-Json
    $bad2.restoreCommands[0].order = 5
    Assert-Case 'manifest out of order rejected' (@(Test-DaManifestShape -Manifest $bad2).Count -ge 1)

    # 8. runtime permission parser
    $perm = ConvertFrom-DaRuntimePermissions -Lines @('    install permissions:', '      android.permission.INTERNET: granted=true',
        '      runtime permissions:', '        android.permission.CAMERA: granted=true, flags=[ USER_SET ]',
        '        android.permission.READ_MEDIA_IMAGES: granted=false, flags=[ ]', '      disabledComponents:', '        x.Y: granted=true')
    Assert-Case 'permission parser reads only runtime permissions' ($perm.Count -eq 2 -and ($perm | Where-Object name -eq 'android.permission.CAMERA').granted -and -not ($perm | Where-Object name -eq 'android.permission.READ_MEDIA_IMAGES').granted)

    # 9. JPEG fixture determinism
    $j1 = Join-Path $daSuiteRoot 'j1.jpg'; $j2 = Join-Path $daSuiteRoot 'j2.jpg'
    New-DaFixtureJpeg -Path $j1 -Width 64 -Height 48 -Random ([System.Random]::new(7))
    New-DaFixtureJpeg -Path $j2 -Width 64 -Height 48 -Random ([System.Random]::new(7))
    Assert-Case 'JPEG fixture deterministic' ((Get-DaFileSha256 -Path $j1) -eq (Get-DaFileSha256 -Path $j2))
    Assert-Case 'fixture sets named' ((@((Get-DaFixtureSetNames).Keys) -join ',') -eq 'small,n1000,n10000')

    # 10. refusal path through the DA_STATE_DIR override
    $env:DA_STATE_DIR = Join-Path $daSuiteRoot 'state'
    New-Item -ItemType Directory -Path $env:DA_STATE_DIR -Force | Out-Null
    Assert-Case 'state dir override honoured' ((Get-DaPaths).State -eq $env:DA_STATE_DIR)
    $g = Assert-DaBackupVerified
    Assert-Case 'no backup -> refused' ($g.Code -eq 2 -and $g.Reason -match 'no backup') $g.Reason
    $runDir = Join-Path $daSuiteRoot 'backup-r1'
    New-Item -ItemType Directory -Path $runDir -Force | Out-Null
    $arch = Join-Path $runDir 'private.tar.enc'
    Protect-DaArchive -InFile $tarPath -OutFile $arch -KeyFile (Join-Path $runDir 'key.dpapi')
    $manifest.components[0].archive = [ordered]@{ file = 'private.tar.enc'; encryptedSha256 = Get-DaFileSha256 -Path $arch; plainSha256 = Get-DaFileSha256 -Path $tarPath }
    $manifestPath = Join-Path $runDir 'manifest.json'
    Write-DaJson -Path $manifestPath -InputObject $manifest
    Write-DaJson -Path (Get-DaCurrentBackupPointerPath) -InputObject ([ordered]@{ schemaVersion = 1; runId = 'r1'; manifestPath = $manifestPath; package = 'com.example.debug'; avd = 'Pixel_9' })
    $g = Assert-DaBackupVerified -Package 'com.example.debug' -Avd 'Pixel_9'
    Assert-Case 'unverified backup -> refused' ($g.Code -eq 2 -and $g.Reason -match 'unverified') $g.Reason
    $m2 = Read-DaJson -Path $manifestPath
    $m2.verified = [ordered]@{ status = 'verified' }
    Write-DaJson -Path $manifestPath -InputObject $m2
    $g = Assert-DaBackupVerified -Package 'com.example.debug' -Avd 'Pixel_9'
    Assert-Case 'verified backup -> ok' ($g.Code -eq 0) $g.Reason
    $g = Assert-DaBackupVerified -Package 'com.example.debug' -Avd 'Other_AVD'
    Assert-Case 'backup of another AVD -> refused' ($g.Code -eq 2)
    Write-DaJson -Path (Get-DaRestorePendingPath) -InputObject ([ordered]@{ schemaVersion = 1; runId = 'r2'; reason = 'suite'; manifestPath = $manifestPath }) -Durable
    $g = Assert-DaBackupVerified -Package 'com.example.debug' -Avd 'Pixel_9'
    Assert-Case 'pending restore -> refused' ($g.Code -eq 2 -and $g.Reason -match 'pending') $g.Reason
    Remove-Item -LiteralPath (Get-DaRestorePendingPath)
    $bytes = [System.IO.File]::ReadAllBytes($arch); $bytes[60] = $bytes[60] -bxor 1; [System.IO.File]::WriteAllBytes($arch, $bytes)
    $g = Assert-DaBackupVerified -Package 'com.example.debug' -Avd 'Pixel_9'
    Assert-Case 'archive changed after verification -> refused' ($g.Code -eq 2 -and $g.Reason -match 'changed') $g.Reason

    # 11. facade: Status reads the overridden state; the guard refuses a non-emulator serial with no adb call
    $out = & pwsh -NoProfile -File $daFacade -Verb Status 2>&1
    Assert-Case 'facade Status exit 0' ($LASTEXITCODE -eq 0 -and ($out -join ' ') -match 'backup: r1') ($out -join ' ')
    $out = & pwsh -NoProfile -File $daFacade -Verb Preflight -Serial 'RFCR110NBQJ' 2>&1
    Assert-Case 'facade refuses a phone serial with exit 2 naming DEVICE_FLEET.md' ($LASTEXITCODE -eq 2 -and ($out -join ' ') -match 'DEVICE_FLEET\.md') ($out -join ' ')
    $out = & pwsh -NoProfile -File $daFacade -Verb Restore -Serial '192.168.1.5:5555' 2>&1
    Assert-Case 'facade refuses a network serial with exit 2' ($LASTEXITCODE -eq 2) ($out -join ' ')
    $out = & pwsh -NoProfile -File $daFacade -Verb Control -Serial 'RFCR110NBQJ' 2>&1
    Assert-Case 'facade Control refuses a phone serial with exit 2' ($LASTEXITCODE -eq 2 -and ($out -join ' ') -match 'DEVICE_FLEET\.md') ($out -join ' ')

    # 12. percentiles: known series, p99 sample-size flag, failures ranked into the tail
    $p = Get-DaPercentiles -Values ([double[]](1..100))
    Assert-Case 'percentiles of 1..100' ($p.p50 -eq 50 -and $p.p95 -eq 95 -and $p.p99 -eq 99 -and $p.max -eq 100 -and $p.count -eq 100) "$($p.p50)/$($p.p95)/$($p.p99)/$($p.max)"
    Assert-Case 'p99 below 1000 valid probes flagged' (@($p.flags) -contains 'insufficient-for-p99')
    $p = Get-DaPercentiles -Values ([double[]](1..1000))
    Assert-Case 'p99 of 1000 probes not flagged' ($p.p99 -eq 990 -and -not (@($p.flags) -contains 'insufficient-for-p99')) "$($p.p99) $($p.flags -join ',')"
    $p = Get-DaPercentiles -Values ([double[]](1..97)) -Lost 1 -Timeouts 2
    Assert-Case 'failures rank above valid values (p99 censored, shares kept)' ($null -eq $p.p99 -and @($p.censored) -contains 'p99' -and $p.p95 -eq 95 -and $p.timeoutShare -eq 0.02 -and $p.lossShare -eq 0.01 -and $p.total -eq 100) "p99=$($p.p99) p95=$($p.p95) $($p.censored -join ',')"
    $p = Get-DaPercentiles -Values @()
    Assert-Case 'empty series flagged' ($null -eq $p.p50 -and @($p.flags) -contains 'empty')

    # 13. block bootstrap determinism for a fixed seed
    $rnd = [System.Random]::new(17)
    $x = [double[]](0..199)
    $y = [double[]]@($x | ForEach-Object { 3.0 + 0.5 * $_ + ($rnd.NextDouble() * 4.0 - 2.0) })
    $s1 = Get-DaSlopeCI -Y $y -Time $x -Seed 99 -Repeats 400
    $s2 = Get-DaSlopeCI -Y $y -Time $x -Seed 99 -Repeats 400
    $s3 = Get-DaSlopeCI -Y $y -Time $x -Seed 100 -Repeats 400
    Assert-Case 'bootstrap deterministic for a fixed seed' ($s1.time.lower -eq $s2.time.lower -and $s1.time.upper -eq $s2.time.upper)
    Assert-Case 'bootstrap records seed, block length and repeats' ($s1.time.seed -eq 99 -and $s1.time.repeats -eq 400 -and $s1.time.blockLength -eq 6) "$($s1.time.blockLength)"
    Assert-Case 'another seed gives another interval' ($s1.time.lower -ne $s3.time.lower -or $s1.time.upper -ne $s3.time.upper)
    $sd = Get-DaSlopeCI -Y $y -Time $x
    Assert-Case 'bootstrap defaults to the fixed seed' ($sd.time.seed -eq (Get-DaBootstrapDefaults).seed -and $sd.time.repeats -eq (Get-DaBootstrapDefaults).repeats)

    # 14. slope recovery on a noisy linear series, over time and over cycles
    $cyc = [double[]]@($x | ForEach-Object { $_ * 2 })
    $s = Get-DaSlopeCI -Y $y -Time $x -Cycle $cyc -Seed 5
    Assert-Case 'slope recovered within its interval' ($s.time.lower -le 0.5 -and $s.time.upper -ge 0.5 -and [math]::Abs($s.time.slope - 0.5) -lt 0.02 -and $s.time.growth -eq 'growing') "$($s.time.slope) [$($s.time.lower), $($s.time.upper)]"
    Assert-Case 'slope over cycles scales with the cycle axis' ([math]::Abs($s.cycle.slope - 0.25) -lt 0.01 -and $s.cycle.growth -eq 'growing') "$($s.cycle.slope)"

    # 15. plateau after an explicit warm-up must not report growth
    $rnd = [System.Random]::new(23)
    $py = [double[]]@((0..119) | ForEach-Object { if ($_ -lt 20) { 10.0 + $_ } else { 30.0 } } | ForEach-Object { $_ + ($rnd.NextDouble() * 2.0 - 1.0) })
    $px = [double[]](0..119)
    $pl = Get-DaSlopeCI -Y $py -Time $px -WarmupCount 20 -Seed 7
    Assert-Case 'plateau after warm-up reports no growth' ($pl.time.growth -eq 'none-detected' -and $pl.warmupDropped -eq 20 -and $pl.time.n -eq 100) "$($pl.time.growth) [$($pl.time.lower), $($pl.time.upper)]"
    Assert-Case 'plateau passes a small growth budget' ((Get-DaBudgetVerdict -Lower $pl.time.lower -Upper $pl.time.upper -Budget 0.1).verdict -eq 'pass')

    # 16. budget verdicts, crossing included
    Assert-Case 'budget pass when upper bound below budget' ((Get-DaBudgetVerdict -Lower 0.1 -Upper 0.4 -Budget 1).verdict -eq 'pass')
    Assert-Case 'budget fail when lower bound above budget' ((Get-DaBudgetVerdict -Lower 1.2 -Upper 2 -Budget 1).verdict -eq 'fail')
    Assert-Case 'budget inconclusive when the interval crosses it' ((Get-DaBudgetVerdict -Lower 0.5 -Upper 1.5 -Budget 1).verdict -eq 'inconclusive')
    Assert-Case 'budget inconclusive without an interval' ((Get-DaBudgetVerdict -Lower $null -Upper 0.2 -Budget 1).verdict -eq 'inconclusive')
    Assert-Case 'pass is scoped to the observed window' ((Get-DaBudgetVerdict -Lower 0.1 -Upper 0.4 -Budget 1).scope -eq 'observed-window')

    # 17. every invalid-run trigger returns the refused code; thresholds come from the calibration record
    $calib = [pscustomobject]@{ thresholds = [pscustomobject](Get-DaDefaultThresholds) }
    $base = @{ schedulerLatenessMs = [double[]](@(1) * 100); plannedProbes = 100; missedProbes = 0; samplerTicks = 100; samplerGaps = 0; powerChange = $null; frequencyChange = $null; foregroundChange = $null; processRestart = $null }
    $v = Test-DaRunValidity -Measurements ([pscustomobject]$base) -Calibration $calib
    Assert-Case 'clean run is valid with code 0' ($v.Valid -and $v.Code -eq 0 -and @($v.Causes).Count -eq 0) (($v.Causes | ForEach-Object { $_.id }) -join ',')
    $triggers = [ordered]@{
        'scheduler-lateness' = @{ schedulerLatenessMs = [double[]](@(1) * 97 + @(25, 25, 25)) }
        'missed-probes'      = @{ missedProbes = 2 }
        'sampler-gaps'       = @{ samplerGaps = 3 }
        'power-change'       = @{ powerChange = "acPowered 'true' -> 'false'" }
        'frequency-change'   = @{ frequencyChange = "cpuMaxFreq '2000' -> '1000'" }
        'foreground-change'  = @{ foregroundChange = 'key tick 3 saw launcher' }
        'process-restarted'  = @{ processRestart = 'pid 42 start identity changed' }
    }
    foreach ($id in $triggers.Keys) {
        $m = $base.Clone()
        foreach ($k in $triggers[$id].Keys) { $m[$k] = $triggers[$id][$k] }
        $v = Test-DaRunValidity -Measurements ([pscustomobject]$m) -Calibration $calib
        Assert-Case "invalid-run trigger $id -> code 2" (-not $v.Valid -and $v.Code -eq 2 -and @($v.Causes | ForEach-Object { $_.id }) -contains $id) (($v.Causes | ForEach-Object { $_.id }) -join ',')
    }
    $m = $base.Clone(); $m.missedProbes = 1
    Assert-Case 'exactly 1 percent missed is still valid' ((Test-DaRunValidity -Measurements ([pscustomobject]$m) -Calibration $calib).Valid)
    $v = Test-DaRunValidity -Measurements ([pscustomobject]$base) -Calibration $null
    Assert-Case 'no calibration record -> code 2' (-not $v.Valid -and $v.Code -eq 2 -and $v.Causes[0].id -eq 'calibration-missing')
    $loose = Get-DaDefaultThresholds; $loose.schedulerLatenessP99Ms = 50.0
    $m = $base.Clone(); $m.schedulerLatenessMs = [double[]](@(1) * 97 + @(25, 25, 25))
    Assert-Case 'lateness judged by the calibration threshold, not a constant' ((Test-DaRunValidity -Measurements ([pscustomobject]$m) -Calibration ([pscustomobject]@{ thresholds = [pscustomobject]$loose })).Valid)

    # 18. probe and sampler parsers
    $st = ConvertFrom-DaProcStat -Line '5924 (my (odd) proc) S 385 385 0 0 -1 4194624 16283 0 1576 0 31 71 0 0 10 -10 29 0 61563 16392769536 39619'
    Assert-Case 'stat parser survives spaces and parens in comm' ($st.pid -eq 5924 -and $st.comm -eq 'my (odd) proc' -and $st.utime -eq 31 -and $st.stime -eq 71 -and $st.startTicks -eq 61563) "$($st.comm) $($st.startTicks)"
    $mi = ConvertFrom-DaMeminfo -Lines @(' App Summary', '           Java Heap:     4856                          22476', '         Native Heap:    12412                          13652',
        '            Graphics:        0                              0', '           TOTAL PSS:    53149            TOTAL RSS:   158652', '               Views:      162         ViewRootImpl:        1',
        '         AppContexts:        7           Activities:        1', '       Local Binders:       44        Proxy Binders:       73')
    Assert-Case 'meminfo parser' ($mi.javaPssKb -eq 4856 -and $mi.nativePssKb -eq 12412 -and $mi.graphicsPssKb -eq 0 -and $mi.totalPssKb -eq 53149 -and $mi.views -eq 162 -and $mi.activities -eq 1 -and $mi.appContexts -eq 7 -and $mi.localBinders -eq 44 -and $mi.proxyBinders -eq 73)
    $fsLines = @('---PROFILEDATA---', 'Flags,FrameTimelineVsyncId,IntendedVsync,Vsync,InputEventId,HandleInputStart,FrameCompleted,',
        '0,5475,2000000000,2000000000,0,2001000000,2012000000,', '0,5467,1000000000,1000000000,-3238,1000400000,1009400000,', '---PROFILEDATA---',
        '---PROFILEDATA---', 'Flags,FrameTimelineVsyncId,IntendedVsync,Vsync,InputEventId,HandleInputStart,FrameCompleted,', '0,5467,1000000000,1000000000,-3238,1000400000,1009400000,', '---PROFILEDATA---')
    $rows = @(ConvertFrom-DaFramestats -Lines $fsLines)
    Assert-Case 'framestats parser merges blocks, dedupes and sorts' ($rows.Count -eq 2 -and $rows[0].InputEventId -eq -3238 -and $rows[1].FrameCompleted -eq 2012000000) "$($rows.Count)"
    $gs = ConvertFrom-DaGfxSummary -Lines @('Total frames rendered: 40', 'Janky frames: 3 (7.50%)', '50th percentile: 9ms', '90th percentile: 14ms', '95th percentile: 20ms', '99th percentile: 150ms',
        'HISTOGRAM: 5ms=10 100ms=5 101ms=2 150ms=1', '50th gpu percentile: 7ms', 'GPU HISTOGRAM: 1ms=0 200ms=9', 'HISTOGRAM: 5ms=99 500ms=99')
    Assert-Case 'gfxinfo summary parser (first block, frames over 100 ms)' ($gs.totalFrames -eq 40 -and $gs.jankyPercent -eq 7.5 -and $gs.p50Ms -eq 9 -and $gs.p99Ms -eq 150 -and $gs.framesOver100Ms -eq 3) "$($gs.framesOver100Ms)"
    $am = ConvertFrom-DaAmStart -Lines @('Starting: Intent { cmp=com.android.settings/.Settings }', 'Status: ok', 'LaunchState: COLD', 'Activity: com.android.settings/.homepage.SettingsHomepageActivity', 'TotalTime: 655', 'WaitTime: 658', 'Complete')
    Assert-Case 'am start parser' ($am.status -eq 'ok' -and $am.launchState -eq 'COLD' -and $am.totalTimeMs -eq 655 -and $am.waitTimeMs -eq 658)
    Assert-Case 'oom class names' ((Get-DaOomClass -Adj 0) -eq 'foreground' -and (Get-DaOomClass -Adj 905) -eq 'cached' -and (Get-DaOomClass -Adj $null) -eq 'gone')

    # 19. tick scheduler: lateness, injected stall, missed ticks
    $ts = Invoke-DaTickSchedule -PeriodMs 40 -Count 8 -Body { param($i) $i }
    Assert-Case 'scheduler runs every planned tick' ($ts.Missed -eq 0 -and $ts.Executed -eq 8 -and @($ts.Ticks)[7].result -eq 7)
    $ts = Invoke-DaTickSchedule -PeriodMs 40 -Count 6 -InjectDelayMs 30 -InjectEvery 2 -Body { param($i) $null }
    $tp = Get-DaPercentiles -Values $ts.LatenessMs
    Assert-Case 'injected stall shows as scheduler lateness' ($tp.max -ge 30) "$($tp.max)"
    $ts = Invoke-DaTickSchedule -PeriodMs 30 -Count 5 -Body { param($i) Start-Sleep -Milliseconds 100 }
    Assert-Case 'overrunning body produces missed ticks, not silent drops' ($ts.Missed -ge 2 -and @($ts.Ticks).Count -eq 5) "$($ts.Missed)"

    # 20. seeded run order and paired summary
    $o1 = (Get-DaShuffledOrder -Random ([System.Random]::new(4166)) -Items @('a', 'b', 'c', 'd')) -join ''
    $o2 = (Get-DaShuffledOrder -Random ([System.Random]::new(4166)) -Items @('a', 'b', 'c', 'd')) -join ''
    Assert-Case 'seeded run order is reproducible' ($o1 -eq $o2 -and ($o1.ToCharArray() | Sort-Object) -join '' -eq 'abcd') "$o1 $o2"
    $pr = @([pscustomobject]@{ pair = 1; target = 'app'; t = 120 }, [pscustomobject]@{ pair = 1; target = 'control'; t = 100 },
        [pscustomobject]@{ pair = 2; target = 'control'; t = 90 }, [pscustomobject]@{ pair = 2; target = 'app'; t = 130 }, [pscustomobject]@{ pair = 3; target = 'app'; t = 500 })
    $ps = Get-DaPairedSummary -Records $pr -Metric 't'
    Assert-Case 'paired summary uses only complete pairs' ($ps.pairs -eq 2 -and $ps.difference.min -eq 20 -and $ps.difference.max -eq 40) "$($ps.pairs)"
    # 21. fault catalogue: the live file is valid, every injectable fault names a known injector, shape errors are caught
    $cat = Get-DaFaultCatalog
    Assert-Case 'live fault catalogue valid' (@(Test-DaFaultCatalog -Catalog $cat).Count -eq 0)
    Assert-Case 'catalogue lists injectable and open faults' (@($cat.faults | Where-Object status -eq 'injectable').Count -ge 7 -and @($cat.faults | Where-Object status -eq 'open').Count -ge 1)
    $badCat = $cat | ConvertTo-Json -Depth 16 | ConvertFrom-Json
    $badCat.faults[0].invariant = ''
    $badCat.faults[1].injector = 'teleport'
    $badCat.faults[-1].reason = $null
    $badCat.faults[2].id = $badCat.faults[3].id
    $errs = @(Test-DaFaultCatalog -Catalog $badCat)
    Assert-Case 'catalogue shape errors caught' (($errs -join ';') -match 'no invariant' -and ($errs -join ';') -match 'known injector' -and ($errs -join ';') -match 'open without reason' -and ($errs -join ';') -match 'duplicate') ($errs -join ';')

    # 22. injectors stay inside the run directory and record their moment; filler arithmetic and cleanup
    $root = '/storage/emulated/0/Download/FastMediaSorter_DynAudit/r1'
    Assert-Case 'path inside root' (Test-DaPathInside -Root $root -Path "$root/src/a.jpg")
    Assert-Case 'parent segment refused' (-not (Test-DaPathInside -Root $root -Path "$root/src/../../x"))
    Assert-Case 'sibling root refused' (-not (Test-DaPathInside -Root $root -Path "$root-other/src/a.jpg"))
    Assert-Case 'root itself refused' (-not (Test-DaPathInside -Root $root -Path $root))
    Assert-Case 'quote in path refused' (-not (Test-DaPathInside -Root $root -Path "$root/src/a'b.jpg"))
    $script:fakeCalls = [System.Collections.Generic.List[string]]::new()
    $fakeAdb = { param($a) $cmd = ($a -join ' '); $script:fakeCalls.Add($cmd)
        if ($cmd -match "stat -f") { $free = if (@($script:fakeCalls | Where-Object { $_ -match 'fallocate' }).Count) { 32 } else { 25600 }; return [pscustomobject]@{ ExitCode = 0; Lines = @("$free 4096"); Stderr = '' } }
        if ($cmd -match 'test -e .* && echo still') { return [pscustomobject]@{ ExitCode = 0; Lines = @('gone'); Stderr = '' } }
        if ($cmd -match 'fallocate') { return [pscustomobject]@{ ExitCode = 0; Lines = @('filled'); Stderr = '' } }
        [pscustomobject]@{ ExitCode = 0; Lines = @('ok'); Stderr = '' } }
    $rec = Invoke-DaFault -Id 'F01' -Catalog $cat -RunRemoteDir $root -Target 'src/IMG_0007.jpg' -OperationStartMonoMs ((Get-DaMonotonicMs) - 50) -Adb $fakeAdb
    Assert-Case 'remove-source acts inside the run directory' ($script:fakeCalls[-1] -match [regex]::Escape("rm -f '$root/src/IMG_0007.jpg'"))
    Assert-Case 'fault record carries correlation id and moment' ($rec.correlationId -match '^da-[0-9a-f]{8}$' -and $rec.relativeToOperationMs -ge 50 -and $rec.boundary -eq 'open' -and $rec.faultId -eq 'F01')
    $refused = $false
    try { $null = Invoke-DaFault -Id 'F01' -Catalog $cat -RunRemoteDir $root -Target '../other/x.jpg' -Adb $fakeAdb } catch { $refused = $_.Exception.Message -match 'not inside the run directory' }
    Assert-Case 'injector refuses a target outside the run directory' $refused
    $refused = $false
    try { $null = Invoke-DaFault -Id 'F11' -Catalog $cat -RunRemoteDir $root -Adb $fakeAdb } catch { $refused = $_.Exception.Message -match 'is open' }
    Assert-Case 'open fault has no injector' $refused
    $fill = Invoke-DaFault -Id 'F05' -Catalog $cat -RunRemoteDir $root -MarginBytes 131072 -Adb $fakeAdb
    $fallocate = @($script:fakeCalls | Where-Object { $_ -match 'fallocate' })[0]
    Assert-Case 'filler leaves the margin and stays in the run directory' ($fallocate -match "fallocate -l $(25600 * 4096 - 131072) '$([regex]::Escape($root))/_fill-da-" -and $fill.cleanup.kind -eq 'remove-file') $fallocate
    Assert-Case 'filler cleanup removes it' ((Clear-DaFault -Record $fill -Adb $fakeAdb) -match 'removed filler')
    $c = [pscustomobject]@{ Serial = 'emulator-5554' }
    $script:seenSerial = $null
    $null = Clear-DaFault -Record $fill -Adb { param($a) $script:seenSerial = $c.Serial; [pscustomobject]@{ ExitCode = 0; Lines = @('gone'); Stderr = '' } }
    Assert-Case 'cleanup does not shadow a caller variable used by the adb hook' ($script:seenSerial -eq 'emulator-5554') "saw '$($script:seenSerial)'"
    $script:fallocTries = 0
    $tightAdb = { param($a) $cmd = ($a -join ' ')
        if ($cmd -match 'stat -f') { return [pscustomobject]@{ ExitCode = 0; Lines = @('25600 4096'); Stderr = '' } }
        if ($cmd -match 'fallocate') { $script:fallocTries++; $ok = if ($script:fallocTries -ge 2) { 'filled' } else { 'failed' }; return [pscustomobject]@{ ExitCode = 0; Lines = @($ok); Stderr = '' } }
        [pscustomobject]@{ ExitCode = 0; Lines = @('ok'); Stderr = '' } }
    $tight = Invoke-DaFault -Id 'F05' -Catalog $cat -RunRemoteDir $root -MarginBytes 16384 -Adb $tightAdb
    Assert-Case 'filler widens the margin after ENOSPC' ($script:fallocTries -eq 2 -and $tight.detail -match 'used 81920') $tight.detail
    $rv = Invoke-DaFault -Id 'F06' -Catalog $cat -RunRemoteDir $root -Adb $fakeAdb
    Assert-Case 'revocation records a grant-back cleanup' ($rv.cleanup.kind -eq 'grant-permission' -and (Clear-DaFault -Record $rv -Adb $fakeAdb) -match 'granted')

    # 23. accessibility events, outcome channels and message readability
    $ev = @('10-11 22:25:03.900 EventType: TYPE_WINDOW_STATE_CHANGED; EventTime: 1; PackageName: com.example.debug; MovementGranularity: 0; Action: 0; ContentChangeTypes: []; WindowChangeTypes: [] [ ClassName: x.Dialog; Text: [Copying.., primary%3ADownload%2Fa.jpg, 1 / 1, Cancel]; ContentDescription: null; ItemCount: -1 ]',
        '10-11 22:25:04.000 EventType: TYPE_NOTIFICATION_STATE_CHANGED; EventTime: 2; PackageName: com.example.debug; MovementGranularity: 0; Action: 0; ContentChangeTypes: []; WindowChangeTypes: [] [ ClassName: android.widget.Toast$TN; Text: [Copied 1 files., Fast Media Sorter & Organizer]; ContentDescription: null; ItemCount: -1 ]',
        '10-11 22:25:04.100 EventType: TYPE_NOTIFICATION_STATE_CHANGED; EventTime: 3; PackageName: com.other; MovementGranularity: 0; Action: 0; ContentChangeTypes: []; WindowChangeTypes: [] [ ClassName: t; Text: [Other app]; ContentDescription: null; ItemCount: -1 ]')
    $parsed = @(ConvertFrom-DaUiEventLines -Lines $ev)
    $chan = Get-DaUiOutcomeChannels -Events $parsed -Package 'com.example.debug'
    Assert-Case 'event parser and toast channel' ($parsed.Count -eq 3 -and @($chan.Toasts) -contains 'Copied 1 files.' -and @($chan.Toasts).Count -eq 1 -and @($chan.Dialogs).Count -eq 1) "$(@($chan.Toasts) -join '|')"
    Assert-Case 'message readability' ((Test-DaMessageText -Text 'Copied 1 files.') -contains 'number-agreement' -and (Test-DaMessageText -Text $chan.Dialogs[0]) -contains 'encoded-path' -and @(Test-DaMessageText -Text 'Copied 2 files.').Count -eq 0 -and (Test-DaMessageText -Text '') -contains 'empty')
    $tree = [pscustomobject]@{ Ok = $true; Nodes = @([pscustomobject]@{ resIdShort = 'btnNext'; resId = 'p:id/btnNext'; text = 'Next'; desc = ''; label = 'Next' }, [pscustomobject]@{ resIdShort = 'title'; resId = 'p:id/title'; text = 'src'; desc = ''; label = 'src' }) }
    Assert-Case 'node lookup by id and exact label' (@(Find-DaUiNode -Tree $tree -Id 'btnNext').Count -eq 1 -and @(Find-DaUiNode -Tree $tree -Label 'sr' -Exact).Count -eq 0 -and @(Find-DaUiNode -Tree $tree -Label 'sr').Count -eq 1)

    # 24. tree expectations, crash signals, verdict and registry
    $b = New-DaTreeRecord -Entries @([pscustomobject]@{ path = 'src/a.jpg'; size = 3; sha256 = ('a' * 64) }, [pscustomobject]@{ path = 'src/b.jpg'; size = 4; sha256 = ('b' * 64) })
    $copyOk = New-DaTreeRecord -Entries (@($b.Entries) + [pscustomobject]@{ path = 'dst/a.jpg'; size = 3; sha256 = ('a' * 64) })
    Assert-Case 'copy expectation holds' (Test-DaTreeExpectation -Before $b -After $copyOk -Added @('dst/a.jpg') -SameAs @{ 'dst/a.jpg' = 'src/a.jpg' }).holds
    $trunc = New-DaTreeRecord -Entries (@($b.Entries) + [pscustomobject]@{ path = 'dst/a.jpg'; size = 1; sha256 = ('c' * 64) })
    $jt = Test-DaTreeExpectation -Before $b -After $trunc -Added @('dst/a.jpg') -SameAs @{ 'dst/a.jpg' = 'src/a.jpg' }
    Assert-Case 'truncated copy breaks the expectation' (-not $jt.holds -and ($jt.problems -join ';') -match 'differs from its source')
    $lost = New-DaTreeRecord -Entries @($b.Entries[0])
    Assert-Case 'undeclared loss breaks the expectation' (-not (Test-DaTreeExpectation -Before $b -After $lost).holds)
    Assert-Case 'optional pattern accepts either outcome' ((Test-DaTreeExpectation -Before $b -After $trunc -OptionalPattern '^dst/').holds -and (Test-DaTreeExpectation -Before $b -After $b -OptionalPattern '^dst/').holds)
    $sig = @(Find-DaCrashSignals -Package 'com.example.debug' -Lines @('10-11 E AndroidRuntime: FATAL EXCEPTION: main', '10-11 E AndroidRuntime: Process: com.example.debug, PID: 42', '10-11 E ActivityManager: ANR in com.example.debug (x/.Main)', '10-11 E AndroidRuntime: FATAL EXCEPTION: main', '10-11 E AndroidRuntime: Process: com.other, PID: 7'))
    Assert-Case 'crash and ANR signals of the package only' ($sig.Count -eq 2 -and $sig[0].kind -eq 'crash' -and $sig[1].kind -eq 'anr')
    Assert-Case 'scene verdict 2/1/0' ((Get-DaSceneVerdict -Valid $false) -eq 2 -and (Get-DaSceneVerdict -Valid $true) -eq 2 -and (Get-DaSceneVerdict -Valid $true -Invariants @([pscustomobject]@{ holds = $false })) -eq 1 -and (Get-DaSceneVerdict -Valid $true -Findings @([pscustomobject]@{ title = 'x' })) -eq 1 -and (Get-DaSceneVerdict -Valid $true -Invariants @([pscustomobject]@{ holds = $true })) -eq 0)
    $reg = Get-DaSceneRegistry
    Assert-Case 'registry holds C01 C02 C03 C07 C15 with existing runners' ((@($reg.Keys) -join ',') -eq 'C01,C02,C03,C07,C15' -and @($reg.Values | Where-Object { -not (Get-Command $_.runner -ErrorAction SilentlyContinue) }).Count -eq 0 -and $reg.C15.fault -and -not $reg.C01.fault)

    # 25. envelope: restore after a failing body, no restore after a refused checkpoint, a failed restore wins
    $script:envLog = [System.Collections.Generic.List[string]]::new()
    $okCk = { $script:envLog.Add('checkpoint'); [pscustomobject]@{ Code = 0; Reason = 'ck' } }
    $okRs = { $script:envLog.Add('restore'); [pscustomobject]@{ Code = 0; Reason = 'restored' } }
    $e1 = Invoke-DaEnvelope -Serial 'emulator-5554' -Package 'p' -RunId 'r' -Reason 't' -Checkpoint $okCk -Restore $okRs -Body { $script:envLog.Add('body'); throw 'boom' }
    Assert-Case 'envelope restores after a failing body' (($script:envLog -join ',') -eq 'checkpoint,body,restore' -and $e1.Code -eq 2 -and $e1.Restored -and $e1.Reason -match 'boom') "$($script:envLog -join ',') $($e1.Reason)"
    $script:envLog.Clear()
    $e2 = Invoke-DaEnvelope -Serial 'emulator-5554' -Package 'p' -RunId 'r' -Reason 't' -Checkpoint { $script:envLog.Add('checkpoint'); [pscustomobject]@{ Code = 2; Reason = 'no verified backup' } } -Restore $okRs -Body { $script:envLog.Add('body') }
    Assert-Case 'refused checkpoint: no body, no restore' (($script:envLog -join ',') -eq 'checkpoint' -and $e2.Code -eq 2)
    $script:envLog.Clear()
    $e3 = Invoke-DaEnvelope -Serial 'emulator-5554' -Package 'p' -RunId 'r' -Reason 't' -Checkpoint $okCk -Restore { [pscustomobject]@{ Code = 2; Reason = 'device differs' } } -Body { [pscustomobject]@{ Code = 0; Reason = 'fine' } }
    Assert-Case 'failed restore makes the envelope 2' ($e3.Code -eq 2 -and -not $e3.Restored)

    # 26. heavy run with stubs: order, restore in finally on failure, refused preflight, outside change
    $runsRoot = Join-Path $daSuiteRoot 'runs'
    $script:heavyLog = [System.Collections.Generic.List[string]]::new()
    $mk = { param($n, $c) $script:heavyLog.Add($n); [pscustomobject]@{ Code = $c; Reason = $n } }
    $hooks = @{
        Preflight = { & $mk 'preflight' 0 }; Backup = { & $mk 'backup' 0 }; Checkpoint = { & $mk 'checkpoint' 0 }
        Publish = { $script:heavyLog.Add('publish'); [pscustomobject]@{ count = 33; runRemoteDir = '/x' } }; Calibration = { & $mk 'calibration' 0 }
        Outside = { $script:heavyLog.Add('outside'); @('1|/a') }; Scene = { param($fx) & $mk 'scene' 0 }; Restore = { & $mk 'restore' 0 }
    }
    $hv = Invoke-DaHeavyRun -Serial 'emulator-5554' -Package 'p' -Scene 'C07' -RunId 'h1' -Hooks $hooks -RunsRoot $runsRoot
    Assert-Case 'heavy sequence order' (($script:heavyLog -join ',') -eq 'preflight,backup,checkpoint,publish,calibration,outside,scene,outside,restore' -and $hv.Code -eq 0) ($script:heavyLog -join ',')
    Assert-Case 'heavy record written' ((Read-DaJson -Path (Join-Path $runsRoot 'h1/heavy.json')).restored -eq $true)
    $script:heavyLog.Clear()
    $h2 = $hooks.Clone(); $h2.Scene = { param($fx) $script:heavyLog.Add('scene'); throw 'scene crashed' }
    $hv = Invoke-DaHeavyRun -Serial 'emulator-5554' -Package 'p' -Scene 'C07' -RunId 'h2' -Hooks $h2 -RunsRoot $runsRoot
    Assert-Case 'heavy restores in finally after a scene failure' ($script:heavyLog[-1] -eq 'restore' -and $hv.Code -eq 2 -and $hv.Reason -match 'scene crashed') "$($script:heavyLog -join ',') $($hv.Reason)"
    $script:heavyLog.Clear()
    $h3 = $hooks.Clone(); $h3.Preflight = { & $mk 'preflight' 2 }
    $hv = Invoke-DaHeavyRun -Serial 'emulator-5554' -Package 'p' -Scene 'C07' -RunId 'h3' -Hooks $h3 -RunsRoot $runsRoot
    Assert-Case 'refused preflight: no checkpoint, no restore' (($script:heavyLog -join ',') -eq 'preflight' -and $hv.Code -eq 2)
    $script:heavyLog.Clear()
    $h4 = $hooks.Clone(); $h4.Publish = { $script:heavyLog.Add('publish'); throw 'push failed' }
    $hv = Invoke-DaHeavyRun -Serial 'emulator-5554' -Package 'p' -Scene 'C07' -RunId 'h4' -Hooks $h4 -RunsRoot $runsRoot
    Assert-Case 'publish failure after the checkpoint still restores' ($script:heavyLog[-1] -eq 'restore' -and $hv.Code -eq 2)
    $script:heavyLog.Clear()
    $script:outsideN = 0
    $h5 = $hooks.Clone(); $h5.Outside = { $script:outsideN++; if ($script:outsideN -eq 1) { @('1|/a') } else { @('1|/a', '9|/Download/leak.jpg') } }
    $hv = Invoke-DaHeavyRun -Serial 'emulator-5554' -Package 'p' -Scene 'C07' -RunId 'h5' -Hooks $h5 -RunsRoot $runsRoot
    Assert-Case 'change outside the run directory is a finding' ($hv.Code -eq 1 -and @($hv.Steps | Where-Object { $_.step -eq 'outside-invariant' -and $_.code -eq 1 }).Count -eq 1)
    $h6 = $hooks.Clone(); $h6.Restore = { [pscustomobject]@{ Code = 2; Reason = 'restore incomplete' } }
    $hv = Invoke-DaHeavyRun -Serial 'emulator-5554' -Package 'p' -Scene 'C07' -RunId 'h6' -Hooks $h6 -RunsRoot $runsRoot
    Assert-Case 'failed restore makes the heavy run 2' ($hv.Code -eq 2 -and $hv.Reason -match 'restore incomplete')

    # 27. facade: Scene and Heavy are guarded like every device verb
    $out = & pwsh -NoProfile -File $daFacade -Verb Scene -Scene C07 -Serial 'RFCR110NBQJ' 2>&1
    Assert-Case 'facade Scene refuses a phone serial with exit 2' ($LASTEXITCODE -eq 2 -and ($out -join ' ') -match 'DEVICE_FLEET\.md') ($out -join ' ')
    $out = & pwsh -NoProfile -File $daFacade -Verb Heavy -Scene C15 -Fault F07 -Serial '192.168.1.5:5555' 2>&1
    Assert-Case 'facade Heavy refuses a network serial with exit 2' ($LASTEXITCODE -eq 2) ($out -join ' ')
}
catch {
    $script:daFailed.Add("suite error: $($_.Exception.Message) at $($_.InvocationInfo.ScriptLineNumber)")
}
finally {
    $env:DA_STATE_DIR = $daSavedState
    if (Test-Path -LiteralPath $daSuiteRoot) { Remove-Item -LiteralPath $daSuiteRoot -Recurse -Force -ErrorAction SilentlyContinue }
}

if ($script:daFailed.Count) {
    Write-Host ("da.tests: FAIL - {0} passed, {1} failed: {2}" -f $script:daPassed, $script:daFailed.Count, ($script:daFailed -join ' | '))
    exit 1
}
Write-Host ("da.tests: PASS - {0} cases" -f $script:daPassed)
exit 0
