#requires -Version 7.0
<#
.SYNOPSIS
    Facade of the S4166 dynamic-audit stand: emulator guard, verified backup, restore, recovery and fixtures.

.DESCRIPTION
    Manual tool: scripts/devtest/dynamic-audit/da.ps1

    One verb per call, one verdict line per call ("da <verb>: OK|FINDING|REFUSED - <detail>").

      Preflight      emulator guard, then the verified-backup precondition: refused (2) when no verified
                     backup exists for this package and AVD, or a restore is still pending
      Backup         force-stops the debug package and backs up its private directory, app-scoped external
                     directory, runtime permissions, tracked settings and display overrides
      VerifyBackup   hash, authentication-tag and trial-extraction check; stamps the manifest verified
      BeginMutation  writes the durable restore-pending checkpoint after the Preflight precondition; any
                     scene that mutates the device starts here, and only a full restore clears it
      Restore        idempotent restore of the current verified backup, compared with the manifest
      Recover        next-start recovery: runs the pending restore left by an interrupted run
      Fixture        generates a deterministic set, publishes it to a fresh directory under
                     Download/FastMediaSorter_DynAudit and compares the device tree hash with the set
                     manifest; with -RemoteDir it only re-checks an already published directory
      Status         prints the state directory's view, current calibration and last control run included
                     (no device access)
      BuildInventory regenerates manifest, capabilities, scenes, budgets and matrix of the audit data folder
                     from the live ledgers (no device access; deterministic, byte-identical on re-run)
      BuildMatrix    same builder under the name of the plan step that names the matrix
      BuildUCards    regenerates the switch cards and the owner's execution protocol from the settings manifest
                     (no device access)
      Access         accessibility, RTL and text-size preconditions of the screen in front, inside the
                     verified-backup envelope; locale and font scale are restored; never claims a TalkBack pass
      Calibrate      measures the stand on a quiet emulator - host scheduler lateness of the probe loop,
                     adb transport round trip, sampler cost on an idle stock process - and writes the
                     calibration record (runs/<runId>/calibration.json, pointer state/current-calibration.json)
                     with the invalid-run thresholds, before any application run; refused (2) when the
                     stand misses its own thresholds
      Control        the same controller, probes and sampler against the stock Settings activity and the app,
                     interleaved in a seeded random order (cold and warm starts, key probe blocks with the
                     sampler, frame statistics); paired records in runs/<runId>/control-records.jsonl and a
                     run record run.json judged by Test-DaRunValidity against the calibration thresholds; an
                     invalid run keeps its raw data, names its cause and returns 2, and the next Control run
                     is refused until -StandFix names the stand fix
      Scene          one registered scene (C01, C02, C03, C07, C15; C15 with -Fault) inside the mutation
                     envelope: restore checkpoint, the scene's named actions with probe and sampler attached,
                     Restore-DaBackup in finally; the run record is runs/<runId>/scene.json (invariants judged
                     by an independent tree hash, raw message texts, candidate findings, open atomic scenes)
      Heavy          the heavy-run sequence: preflight, verified backup (reused, or new with -FreshBackup),
                     restore checkpoint, fixture publish, calibration check, one scene with at most one fault,
                     the outside-the-run invariant, Restore-DaBackup in finally, runs/<runId>/heavy.json; a
                     killed run leaves the durable checkpoint for -Verb Recover

    Calibrate, Control, Scene and Heavy run inside the mutation envelope: verified-backup precondition, restore
    checkpoint, and a full Restore at the end whatever the outcome (a failed restore makes the verdict 2).

    Every verb that touches a device runs Assert-DaEmulatorTarget first: only a serial matching
    ^emulator-\d+$ whose kernel reports ro.kernel.qemu=1 is accepted (docs/DEVICE_FLEET.md). Mutating
    verbs (Fixture publish) also require the Preflight precondition. Storage: the ticket scratch root
    temp/S4166 (state/, private/ under a current-user ACL with encrypted archives, runs/, fixtures/);
    DA_STATE_DIR overrides the state directory.

.PARAMETER Verb
    Preflight, Backup, VerifyBackup, BeginMutation, Restore, Recover, Fixture, Status, BuildInventory,
    BuildMatrix, BuildUCards, Calibrate, Control, Access, Scene or Heavy.

.PARAMETER Scene
    For -Verb Scene and -Verb Heavy: the registered scene id (C01, C02, C03, C07, C15).

.PARAMETER Fault
    For scene C15: one fault id from PLAN/S4166_deep-dynamic-audit/audit/faults.json (F01..).

.PARAMETER FreshBackup
    For -Verb Heavy: take and verify a new backup instead of reusing the current verified one.

.PARAMETER Screen
    For -Verb Access: the name of the screen in front, used only to label the report.

.PARAMETER Serial
    Emulator serial; when empty the single online emulator-* device is taken.

.PARAMETER Package
    Debug application id (run-as needs a debuggable build). Default com.sza.fastmediasorter.debug.

.PARAMETER Set
    Fixture set for -Verb Fixture: small, n1000 or n10000.

.PARAMETER RemoteDir
    For -Verb Fixture: re-check this already published device directory instead of publishing.

.PARAMETER Reason
    For -Verb BeginMutation: what is about to mutate the device.

.PARAMETER RunId
    Run id for Backup, BeginMutation, Fixture, Calibrate, Control, Scene or Heavy; generated when empty.

.PARAMETER Seed
    For -Verb Control: seed of the interleaved run order (System.Random). Default 4166.

.PARAMETER ColdStarts
    For -Verb Control: cold starts per target. Default 10.

.PARAMETER WarmStarts
    For -Verb Control: warm starts per target. Default 10.

.PARAMETER KeyProbes
    For -Verb Control: key probes per target. Default 30.

.PARAMETER KeyBlocks
    For -Verb Control: interleaved key blocks the probes are split into. Default 3.

.PARAMETER KeyPeriodMs
    For -Verb Control: planned key probe period. Default 1000.

.PARAMETER ProbePeriodMs
    For -Verb Calibrate: period of the probe loop whose scheduler lateness is measured. Default 200.

.PARAMETER CalibrationTicks
    For -Verb Calibrate: ticks of that measurement. Default 150.

.PARAMETER TransportProbes
    For -Verb Calibrate: adb round trips measured. Default 40.

.PARAMETER SamplerCostSec
    For -Verb Calibrate: length of the quiet and of the sampled window. Default 20.

.PARAMETER InjectFault
    For -Verb Control, test only: none, late-tick or sampler-gap. Injects a stand fault so the validity
    rule can be seen refusing the run; the run is recorded as injected and does not block the next one.

.PARAMETER StandFix
    For -Verb Control: what was fixed in the stand since the last invalid run; required after one.

.EXAMPLE
    pwsh -NoProfile -File scripts/devtest/dynamic-audit/da.ps1 -Verb Backup -Serial emulator-5554

.EXAMPLE
    pwsh -NoProfile -File scripts/devtest/dynamic-audit/da.ps1 -Verb Calibrate

.EXAMPLE
    pwsh -NoProfile -File scripts/devtest/dynamic-audit/da.ps1 -Verb Control -ColdStarts 10 -KeyProbes 30

.EXAMPLE
    pwsh -NoProfile -File scripts/devtest/dynamic-audit/da.ps1 -Verb Fixture -Set small

.EXAMPLE
    pwsh -NoProfile -File scripts/devtest/dynamic-audit/da.ps1 -Verb Scene -Scene C07

.EXAMPLE
    pwsh -NoProfile -File scripts/devtest/dynamic-audit/da.ps1 -Verb Heavy -Scene C15 -Fault F07

.NOTES
    Exit codes:
      0 - ok
      1 - finding (a fixture tree differs from its manifest; a scene broke an invariant, recorded a candidate
          finding, or changed something outside its run directory)
      2 - cannot verify or refused (not an emulator, no verified backup, restore pending, restore incomplete,
          tool failure, no calibration record, an invalid calibration, control or scene run, a repeat after an
          invalid run without -StandFix, a scene aborted by an error)
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidateSet('Preflight', 'Backup', 'VerifyBackup', 'BeginMutation', 'Restore', 'Recover', 'Fixture', 'Status', 'BuildInventory', 'BuildMatrix', 'BuildUCards', 'Calibrate', 'Control', 'Access', 'Scene', 'Heavy')]
    [string]$Verb,
    [string]$Serial,
    [ValidatePattern('^[A-Za-z][A-Za-z0-9_.]+$')][string]$Package = 'com.sza.fastmediasorter.debug',
    [ValidateSet('small', 'n1000', 'n10000')][string]$Set = 'small',
    [string]$RemoteDir,
    [string]$Reason = 'scene run',
    [string]$RunId,
    [int]$Seed = 4166,
    [ValidateRange(1, 1000)][int]$ColdStarts = 10,
    [ValidateRange(1, 1000)][int]$WarmStarts = 10,
    [ValidateRange(1, 100000)][int]$KeyProbes = 30,
    [ValidateRange(1, 100)][int]$KeyBlocks = 3,
    [ValidateRange(200, 60000)][int]$KeyPeriodMs = 1000,
    [ValidateRange(20, 60000)][int]$ProbePeriodMs = 200,
    [ValidateRange(10, 100000)][int]$CalibrationTicks = 150,
    [ValidateRange(5, 10000)][int]$TransportProbes = 40,
    [ValidateRange(5, 3600)][int]$SamplerCostSec = 20,
    [ValidateSet('none', 'late-tick', 'sampler-gap')][string]$InjectFault = 'none',
    [string]$StandFix,
    [string]$Screen = 'current screen',
    [ValidateSet('C01', 'C02', 'C03', 'C07', 'C15')][string]$Scene,
    [ValidatePattern('^(F\d{2})?$')][string]$Fault,
    [switch]$FreshBackup
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)

. (Join-Path $PSScriptRoot 'lib\da-common.ps1')
. (Join-Path $PSScriptRoot 'lib\da-emulator.ps1')
. (Join-Path $PSScriptRoot 'lib\da-fixture.ps1')
. (Join-Path $PSScriptRoot 'lib\da-state.ps1')
. (Join-Path $PSScriptRoot 'lib\da-stats.ps1')
. (Join-Path $PSScriptRoot 'lib\da-sampler.ps1')
. (Join-Path $PSScriptRoot 'lib\da-probe.ps1')
. (Join-Path $PSScriptRoot 'lib\da-access.ps1')
. (Join-Path $PSScriptRoot 'lib\da-fault.ps1')
. (Join-Path $PSScriptRoot 'lib\da-ui.ps1')
. (Join-Path $PSScriptRoot 'lib\da-scenes.ps1')

$daCodes = Get-DaExitCode
$daCode = $daCodes.Refused
$daDetail = ''

try {
    if ($Verb -eq 'Status') {
        $daPaths = Get-DaPaths
        $daPtr = Read-DaJson -Path (Get-DaCurrentBackupPointerPath)
        $daPending = Read-DaJson -Path (Get-DaRestorePendingPath)
        $daEmu = Read-DaJson -Path (Get-DaEmulatorRecordPath)
        $daBackup = 'none'
        if ($daPtr) {
            $daMan = Read-DaJson -Path ([string]$daPtr.manifestPath)
            $daBackup = if ($daMan) { "$($daMan.runId) ($($daMan.verified.status), $($daMan.package.name) on $($daMan.device.avd))" } else { "$($daPtr.runId) (manifest unreadable)" }
        }
        $daPendingText = if ($daPending) { "PENDING run $($daPending.runId) ($($daPending.reason))" } else { 'none' }
        $daEmuText = if ($daEmu) { "$($daEmu.avd) pid $($daEmu.pid) $($daEmu.serial)" } else { 'none' }
        $daCal = Read-DaJson -Path (Get-DaCalibrationPointerPath)
        $daLast = Read-DaJson -Path (Get-DaLastControlPath)
        $daCalText = if ($daCal) { "$($daCal.runId) at $(([datetime]$daCal.at).ToUniversalTime().ToString('o')) on $($daCal.avd)" } else { 'none' }
        $daLastText = if ($daLast) { "$($daLast.runId) $(if ($daLast.valid) { 'valid' } else { 'INVALID' })$(if ($daLast.injected) { ' (injected fault)' })" } else { 'none' }
        $daCode = $daCodes.Ok
        $daDetail = "state $($daPaths.State); backup: $daBackup; restore pending: $daPendingText; stand emulator: $daEmuText; calibration: $daCalText; last control: $daLastText"
    }
    elseif ($Verb -in @('BuildInventory', 'BuildMatrix')) {
        # Loaded only for the two builder verbs: no device verb needs a 1300-line inventory library, and
        # its LF-normalising hash is named apart from the byte-exact Get-DaFileSha256 that backup,
        # verification and restore rely on (a same-named copy once replaced it silently).
        . (Join-Path $PSScriptRoot 'lib\da-inventory.ps1')
        $daPaths = Get-DaPaths
        $daBuilt = Invoke-DaBuildInventory -RepoRoot $daPaths.RepoRoot
        $daCode = $daCodes.Ok
        $daDetail = "wrote $(@($daBuilt.Written).Count) audit data files from the live inventories"
    }
    elseif ($Verb -eq 'BuildUCards') {
        $daPaths = Get-DaPaths
        $daAuditRoot = Join-Path $daPaths.RepoRoot 'PLAN/S4166_deep-dynamic-audit/audit'
        $daReportsRoot = Join-Path $daPaths.RepoRoot 'PLAN/S4166_deep-dynamic-audit/reports'
        $daCards = Write-DaUCards -RepoRoot $daPaths.RepoRoot -AuditRoot $daAuditRoot -ReportsRoot $daReportsRoot
        $daCode = $daCodes.Ok
        $daDetail = "wrote $(@($daCards.Written).Count) files (switch cards and the owner's execution protocol)"
    }
    else {
        $daGuard = Assert-DaEmulatorTarget -Serial $Serial
        if ($daGuard.Code -ne 0) {
            $daCode = $daGuard.Code
            $daDetail = $daGuard.Reason
        }
        else {
            $daSerial = $daGuard.Serial
            switch ($Verb) {
                'Preflight' {
                    $daGate = Assert-DaBackupVerified -Package $Package -Avd (Get-DaAvdName -Serial $daSerial)
                    $daCode = $daGate.Code
                    $daDetail = "$daSerial - $($daGate.Reason)"
                }
                'Backup' {
                    $daResult = New-DaBackup -Serial $daSerial -Package $Package -RunId $RunId
                    $daCode = $daResult.Code
                    $daDetail = "$daSerial - $($daResult.Reason)$(if ($daResult.ManifestPath) { "; manifest $($daResult.ManifestPath)" })"
                }
                'VerifyBackup' {
                    $daResult = Test-DaBackup
                    $daCode = $daResult.Code
                    $daDetail = "$daSerial - $($daResult.Reason)"
                }
                'BeginMutation' {
                    $daResult = New-DaRestoreCheckpoint -Serial $daSerial -Package $Package -Reason $Reason -RunId $RunId
                    $daCode = $daResult.Code
                    $daDetail = "$daSerial - $($daResult.Reason)"
                }
                'Restore' {
                    $daResult = Restore-DaBackup -Serial $daSerial
                    $daCode = $daResult.Code
                    $daDetail = "$daSerial - $($daResult.Reason)"
                }
                'Recover' {
                    $daResult = Invoke-DaRecover -Serial $daSerial
                    $daCode = $daResult.Code
                    $daDetail = "$daSerial - $($daResult.Reason)"
                    $daSerialForAccess = $daSerial
                    $daAccessRec = Invoke-DaAccessRecover -Adb { param($a) Invoke-DaAdb -Serial $daSerialForAccess -Arguments $a }
                    if (-not $daAccessRec.Ok) {
                        $daCode = $daCodes.Refused
                        $daDetail = "$daDetail; access probe recovery: $($daAccessRec.Reason)"
                    }
                }
                'Fixture' {
                    $daManifest = New-DaFixtureSet -Name $Set
                    $daTarget = $RemoteDir
                    $daPublished = ''
                    if (-not $daTarget) {
                        $daGate = Assert-DaBackupVerified -Package $Package -Avd (Get-DaAvdName -Serial $daSerial)
                        if ($daGate.Code -ne 0) {
                            $daCode = $daGate.Code
                            $daDetail = "$daSerial - fixture publish refused: $($daGate.Reason)"
                            break
                        }
                        $daPub = Publish-DaFixtureSet -Serial $daSerial -Name $Set -RunId $RunId
                        $daTarget = $daPub.RemoteDir
                        $daPublished = "published $($daPub.Count) items in $($daPub.PushMs) ms; "
                    }
                    $daTree = Get-DaTreeHash -Serial $daSerial -RemoteDir $daTarget
                    $daDiff = Compare-DaTreeHash -Reference @($daManifest.items) -Difference $daTree
                    $daCode = if ($daDiff.Count -eq 0) { $daCodes.Ok } else { $daCodes.Finding }
                    $daDetail = "$daSerial - set $Set at ${daTarget}: ${daPublished}$($daTree.Count) remote files, $($daDiff.Count) differences ($($daDiff.Summary))"
                }
                { $_ -in @('Calibrate', 'Control') } {
                    if (-not $RunId) { $RunId = New-DaRunId }
                    $daCk = New-DaRestoreCheckpoint -Serial $daSerial -Package $Package -Reason "$Verb run $RunId" -RunId $RunId
                    if ($daCk.Code -ne 0) {
                        $daCode = $daCk.Code
                        $daDetail = "$daSerial - $Verb refused: $($daCk.Reason)"
                    }
                    else {
                        $daResult = $null
                        try {
                            if ($Verb -eq 'Calibrate') {
                                $daResult = Invoke-DaCalibration -Serial $daSerial -Package $Package -ProbePeriodMs $ProbePeriodMs -Ticks $CalibrationTicks `
                                    -TransportCount $TransportProbes -SamplerCostSec $SamplerCostSec -RunId $RunId
                            }
                            else {
                                $daResult = Invoke-DaControlRun -Serial $daSerial -Package $Package -Seed $Seed -ColdStarts $ColdStarts -WarmStarts $WarmStarts `
                                    -KeyProbes $KeyProbes -KeyBlocks $KeyBlocks -KeyPeriodMs $KeyPeriodMs -InjectFault $InjectFault -StandFix $StandFix -RunId $RunId
                            }
                        }
                        catch {
                            $daResult = [pscustomobject]@{ Code = $daCodes.Refused; Reason = "$Verb run $RunId failed: $($_.Exception.Message) at line $($_.InvocationInfo.ScriptLineNumber)" }
                        }
                        finally {
                            $daRestore = Restore-DaBackup -Serial $daSerial
                        }
                        $daCode = [math]::Max([int]$daResult.Code, [int]$daRestore.Code)
                        $daDetail = "$daSerial - $($daResult.Reason); restore: $($daRestore.Reason)"
                    }
                }
                { $_ -in @('Scene', 'Heavy') } {
                    if (-not $Scene) {
                        $daCode = $daCodes.Refused
                        $daDetail = "$daSerial - $Verb needs -Scene (C01, C02, C03, C07, C15)"
                        break
                    }
                    if (($Scene -eq 'C15') -ne [bool]$Fault) {
                        $daCode = $daCodes.Refused
                        $daDetail = "$daSerial - scene C15 needs exactly one -Fault and the other scenes take none"
                        break
                    }
                    if ($Verb -eq 'Scene') { $daResult = Invoke-DaSceneRun -Serial $daSerial -Package $Package -Scene $Scene -Fault $Fault -RunId $RunId }
                    else { $daResult = Invoke-DaHeavyRun -Serial $daSerial -Package $Package -Scene $Scene -Fault $Fault -RunId $RunId -FreshBackup:$FreshBackup }
                    $daCode = [int]$daResult.Code
                    $daDetail = "$daSerial - $($daResult.Reason)"
                }
                'Access' {
                    if (-not $RunId) { $RunId = New-DaRunId }
                    $daCk = New-DaRestoreCheckpoint -Serial $daSerial -Package $Package -Reason "Access run $RunId" -RunId $RunId
                    if ($daCk.Code -ne 0) {
                        $daCode = $daCk.Code
                        $daDetail = "$daSerial - Access refused: $($daCk.Reason)"
                    }
                    else {
                        $daResult = $null
                        try {
                            $daResult = Invoke-DaAccessProbe -Serial $daSerial -Package $Package -Screen $Screen
                            $daRunDir = Join-Path (Get-DaPaths).Runs $RunId
                            [void][System.IO.Directory]::CreateDirectory($daRunDir)
                            Write-DaJson -Path (Join-Path $daRunDir 'access.json') -InputObject $daResult
                        }
                        catch {
                            $daResult = [pscustomobject]@{ Code = $daCodes.Refused; Reason = "Access run $RunId failed: $($_.Exception.Message) at line $($_.InvocationInfo.ScriptLineNumber)" }
                        }
                        finally {
                            $daRestore = Restore-DaBackup -Serial $daSerial
                        }
                        $daCode = [math]::Max([int]$daResult.Code, [int]$daRestore.Code)
                        $daDetail = "$daSerial - $($daResult.Reason); restore: $($daRestore.Reason)"
                    }
                }
            }
        }
    }
}
catch {
    $daCode = $daCodes.Refused
    $daDetail = "cannot verify: $($_.Exception.Message)"
}

Write-DaVerdict -Verb $Verb -Code $daCode -Detail $daDetail
exit $daCode
