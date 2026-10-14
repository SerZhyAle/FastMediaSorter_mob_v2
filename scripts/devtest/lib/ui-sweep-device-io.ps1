<#
.SYNOPSIS
  S2380 - the device I/O layer of the phone UI sweep: console notes, the timeline, the adb.ps1 child
  calls, and the node-tree reads.

.DESCRIPTION
  Dot-sourced by scripts/devtest/ui-sweep-walk.ps1 into its own script scope, so every `$script:`
  variable here is the walker's: `$script:dev` (the serial of the bench being walked),
  `$script:timelinePath`, and the walker's `$adbWrapper`, `$repoRoot` and `Stop-Run`. Extracted from
  the walker when it crossed the 2000-line ceiling of CLAUDE.md Rule 2.

  A walk outlives the console it was started from. Run quick8 of 2026-10-03 (emulator-5560) is the
  measurement: from 05:38:41 every child pwsh exited 0xC0000142 (STATUS_DLL_INIT_FAILED) within 5 ms,
  the walk kept going for twenty minutes recording each screen as unreachable, and it finally died on
  a coloured Write-Host with Win32 0xE9 "No process is on the other end of the pipe ... while getting
  console output buffer information" - the same message that ended runs quick4, quick6 and quick7
  between 8 and 62 minutes in. Two guards live here for that: notes never require the console, and a
  run of loader failures stops the walk with exit 2 instead of journalling a dead process as a
  defective app. The third guard, launching with its own console, is the walker's `-Detach`.

  Sourced, never executed directly, so it declares no exit codes of its own.
#>

$script:consoleGone = $false

function Write-Note {
    # A progress line. Colour needs the console's screen-buffer info, which is exactly the call that
    # fails once the launching console is gone; plain stdout keeps working into a redirected log.
    param([string]$Text, [string]$Color = 'Gray')
    if (-not $script:consoleGone) {
        try { Write-Host $Text -ForegroundColor $Color; return }
        catch { $script:consoleGone = $true }
    }
    try { [Console]::Out.WriteLine($Text) }
    catch {
        # stdout is gone as well. The timeline file still carries the line, so the record survives
        # the dead stream instead of a trap turning a progress note into the run's verdict.
        Write-Timeline -Kind 'note-lost' -Detail $Text
    }
}

# The timeline: one JSON line per device action, screenshot and screen boundary, stamped to the
# millisecond. A walk only collects; a reader studies the frames later and finds the moment each one
# belongs to by timestamp, so what led up to a frame (the taps, the typing, the scroll before it) is
# read from here and nothing needs to be judged while the walk runs.
function Write-Timeline {
    param([string]$Kind, [string]$Detail, [hashtable]$Extra)
    if (-not $script:timelinePath) { return }
    $line = [ordered]@{ t = (Get-Date).ToString('yyyy-MM-ddTHH:mm:ss.fffzzz'); kind = $Kind; detail = $Detail }
    if ($Extra) { foreach ($k in $Extra.Keys) { $line[$k] = $Extra[$k] } }
    Add-Content -LiteralPath $script:timelinePath -Value ($line | ConvertTo-Json -Compress) -Encoding UTF8
}

# A negative exit is an NTSTATUS from the Windows loader, never an adb.ps1 code (those are 0..9), so
# three in a row means child processes cannot start at all and nothing after them is evidence.
$script:spawnFailureLimit = 3
$script:spawnFailures = 0
$script:spawnDeadCode = $null

function Invoke-AdbVerb {
    param([Parameter(Mandatory)][string[]]$Arguments)
    if ($null -ne $script:spawnDeadCode) {
        # Teardown still walks its steps after the stop; each answers at once instead of spawning.
        return [pscustomobject]@{ Exit = $script:spawnDeadCode; Output = '' }
    }
    $callArgs = @($Arguments)
    if ($script:dev) { $callArgs += @('-DeviceId', $script:dev) }
    $output = & pwsh -NoProfile -File $adbWrapper @callArgs 2>&1
    $exit = $LASTEXITCODE
    Write-Timeline -Kind 'adb' -Detail ($Arguments -join ' ') -Extra @{ exit = $exit; device = $script:dev }
    if ($exit -lt 0) {
        $script:spawnFailures++
        if ($script:spawnFailures -ge $script:spawnFailureLimit) {
            $script:spawnDeadCode = $exit
            $why = ("child processes no longer start - exit 0x{0:X8} on {1} adb.ps1 calls in a row, last '{2}'. " -f
                $exit, $script:spawnFailures, ($Arguments -join ' ')) +
                'The console this walk was launched from is gone; relaunch it with -Detach so it owns its console.'
            Stop-Run 2 $why
        }
    }
    else { $script:spawnFailures = 0 }
    return [pscustomobject]@{ Exit = $exit; Output = (($output | ForEach-Object { $_.ToString() }) -join "`n") }
}

function Invoke-AdbJson {
    # One -Json verb call, parsed. Returns $null when the verb failed or printed nothing parseable.
    param([Parameter(Mandatory)][string[]]$Arguments)
    $call = Invoke-AdbVerb -Arguments $Arguments
    if ($call.Exit -ne 0) { return $null }
    $line = ($call.Output -split "`r?`n" | Where-Object { $_.StartsWith('{') } | Select-Object -First 1)
    if (-not $line) { return $null }
    try { $obj = $line | ConvertFrom-Json } catch { return $null }
    if ($obj.data) { return $obj.data }
    return $obj
}

function Invoke-Shell {
    param([Parameter(Mandatory)][string]$Command)
    return (Invoke-AdbVerb -Arguments @('shell', '-Cmd', $Command))
}

# --- tree reading --------------------------------------------------------------------------------

$script:adbExe = $null

function Read-UiDumpFast {
    # The same payload shape as the uidump verb (label, desc, resId, resIdShort per node plus the
    # saved XML), without the verb's own process start and node parsing. Measured emulator-5560,
    # 2026-10-03: 2 s here against 6-8 s through the wrapper, and a hunt reads the tree after every
    # swipe. Returns $null on any problem so the caller falls back to the verb.
    if (-not $script:dev) { return $null }
    if (-not $script:adbExe) {
        $candidate = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
        if (-not (Test-Path -LiteralPath $candidate)) { return $null }
        $script:adbExe = $candidate
    }
    try {
        $raw = (& $script:adbExe -s $script:dev exec-out uiautomator dump /dev/tty 2>$null | Out-String)
        $start = $raw.IndexOf('<?xml')
        $end = $raw.LastIndexOf('</hierarchy>')
        if ($start -lt 0 -or $end -lt 0) { return $null }
        $xmlText = $raw.Substring($start, $end - $start + '</hierarchy>'.Length)
        $scratch = Join-Path $repoRoot 'temp/scratch'
        New-Item -ItemType Directory -Path $scratch -Force | Out-Null
        $file = Join-Path $scratch ("uitree_{0}_{1}.xml" -f $script:dev, (Get-Date -Format 'yyyyMMdd_HHmmss_fff'))
        [System.IO.File]::WriteAllText($file, $xmlText, [System.Text.UTF8Encoding]::new($false))
        $nodes = [System.Collections.Generic.List[object]]::new()
        foreach ($m in [regex]::Matches($xmlText, '<node [^>]*>')) {
            $tag = $m.Value
            $text = [System.Net.WebUtility]::HtmlDecode(([regex]::Match($tag, ' text="([^"]*)"')).Groups[1].Value)
            $desc = [System.Net.WebUtility]::HtmlDecode(([regex]::Match($tag, ' content-desc="([^"]*)"')).Groups[1].Value)
            if (-not $text -and -not $desc) { continue }
            $rid = ([regex]::Match($tag, ' resource-id="([^"]*)"')).Groups[1].Value
            $nodes.Add([pscustomobject]@{
                label = $text; desc = $desc; resId = $rid
                resIdShort = if ($rid -like '*/*') { $rid.Substring($rid.LastIndexOf('/') + 1) } else { $rid }
            })
        }
        if ($nodes.Count -eq 0) { return $null }
        return [pscustomobject]@{ file = $file; nodes = @($nodes) }
    }
    catch { return $null }
}

function Read-UiDump {
    # One tree dump. Returns the parsed verb payload (nodes + the saved XML file) or $null.
    $fast = Read-UiDumpFast
    if ($fast) {
        Write-Timeline -Kind 'dump' -Detail 'fast' -Extra @{ nodes = @($fast.nodes).Count; device = $script:dev }
        return $fast
    }
    $dump = Invoke-AdbJson -Arguments @('uidump', '-Json')
    if (-not $dump -or -not $dump.nodes -or @($dump.nodes).Count -eq 0) { return $null }
    return $dump
}

function Get-Haystack {
    # Two sources, because they are not the same set. The parsed node list carries only nodes that
    # draw text or a description, so a CONTAINER - a tab strip, a section header - is in the raw tree
    # and in none of those nodes; the raw XML is therefore read for its resource-ids as well. An
    # `expectId` matched against the parsed list alone made a screen that was open read as never
    # reached: measured emulator-5554 2026-09-20, `tabResourceTypes` is in the start screen's tree
    # and in none of its 45 parsed nodes, so the walk refused the whole matrix from the start screen.
    param($Dump)
    $parts = @($Dump.nodes | ForEach-Object { "$($_.label) $($_.desc) $($_.resId)" })
    if ($Dump.file -and (Test-Path -LiteralPath $Dump.file)) {
        try {
            $raw = Get-Content -LiteralPath $Dump.file -Raw -Encoding UTF8
            $parts += @([regex]::Matches($raw, 'resource-id="([^"]+)"') |
                ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique)
        } catch {
            # The parsed nodes stay the haystack - which is exactly what the walk matched against
            # before this second source existed, so an unreadable dump file loses no ground.
        }
    }
    return ($parts -join "`n")
}

function Test-HaystackHasToken {
    param([string]$Haystack, [string]$Token)
    return ($null -ne $Haystack -and $Haystack -match [regex]::Escape($Token))
}

function Get-CheckedState {
    # The checked state of the switch inside a toggle row, read from the raw XML dump the tree verb
    # saves: the parsed nodes carry no checked attribute, and the sweep must READ a toggle before
    # tapping it - a blind tap flips a switch that may already be in the target position.
    param([Parameter(Mandatory)][string]$DumpFile, [Parameter(Mandatory)][string]$RowId)
    try { $xml = [xml](Get-Content -LiteralPath $DumpFile -Raw -Encoding UTF8) } catch { return $null }
    $rows = @($xml.SelectNodes("//*[@resource-id]") | Where-Object {
        $_.GetAttribute('resource-id') -match ('/' + [regex]::Escape($RowId) + '$')
    })
    if ($rows.Count -eq 0) { return $null }
    $row = $rows[0]
    # checkable="true", not merely [@checked]: uiautomator writes checked="false" on EVERY node, so the
    # first descendant - the row's title text - answered for the switch and a row that was ON read OFF.
    $checkedNode = @($row.SelectNodes(".//*[@checkable='true']")) | Select-Object -First 1
    if (-not $checkedNode) {
        if ($row.HasAttribute('checked')) { return ($row.GetAttribute('checked') -eq 'true') }
        return $null
    }
    return ($checkedNode.GetAttribute('checked') -eq 'true')
}

function Get-ToggleTapPoint {
    # The centre of the switch inside a toggle row, from the same dump the checked state was read
    # from; $null when the row or its switch is not in that tree, so the caller falls back to the row.
    param([Parameter(Mandatory)][string]$DumpFile, [Parameter(Mandatory)][string]$RowId)
    try { $xml = [xml](Get-Content -LiteralPath $DumpFile -Raw -Encoding UTF8) } catch { return $null }
    $row = @($xml.SelectNodes("//*[@resource-id]") | Where-Object {
        $_.GetAttribute('resource-id') -match ('/' + [regex]::Escape($RowId) + '$')
    }) | Select-Object -First 1
    if (-not $row) { return $null }
    $switch = @($row.SelectNodes(".//*[@checkable='true']")) | Select-Object -First 1
    if (-not $switch) { return $null }
    $b = [regex]::Match($switch.GetAttribute('bounds'), '^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$')
    if (-not $b.Success) { return $null }
    $l = [int]$b.Groups[1].Value; $t = [int]$b.Groups[2].Value; $r = [int]$b.Groups[3].Value; $btm = [int]$b.Groups[4].Value
    if ($r -le $l -or $btm -le $t) { return $null }
    return [pscustomobject]@{ X = [int](($l + $r) / 2); Y = [int](($t + $btm) / 2) }
}
