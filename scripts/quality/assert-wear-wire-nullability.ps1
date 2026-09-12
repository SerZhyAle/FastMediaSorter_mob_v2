#requires -Version 7.0
<#
.SYNOPSIS
    S2885/S2887: refuse a phone/watch bridge envelope field whose Kotlin default is not what Gson will
    actually leave in it when the key is absent, because the declaration then states a protection the
    receiver does not have.

.DESCRIPTION
    Gson fills fields by reflection and runs no Kotlin constructor, so a Kotlin default never executes
    on the receiving side. What an absent JSON key leaves behind is decided by the JVM alone: null in a
    reference field, the JVM zero in a primitive. A default that disagrees with that value is a claim
    the code cannot keep, and the two disagreements fail in opposite ways.

      val tombstones: List<WearSourceTombstonePayload> = emptyList()

    reads as protected and is not: a sender older than the field omits the key, the receiver holds null
    under a non-null type, and the first `.isEmpty()` throws. Both listener services wrap the parse and
    the use case in one catch, so the whole exchange dies as a single generic message - not one skipped
    field. That is the loud half (S2885).

      val requiresLocalFile: Boolean = true

    fails silently instead: the primitive takes `false`, the opposite of the declared default, and it
    arrives as a perfectly valid value that no reader can tell apart from a deliberate one. Nothing
    throws and nothing is logged (S2887).

    Measured 2026-09-10 across both modules: the loud shape stood in four fields and eight
    declarations, of which two (`WearSyncPayload.tombstones`, `WearSourcesExportPayload.tombstones`)
    were live compatibility defects. The repository already knew the rule - `endpoints` (S2488) and
    `deselectedIds` (S2882) carry it in their KDoc verbatim - and the knowledge still failed to reach
    `tombstones` and `applicableTypes`, which were introduced by the same spec-driven process with the
    same explicit reasoning about older builds. That is the argument for a gate rather than four edits:
    the knowledge exists and is not enforced anywhere durable.

    S2887 then swept the same 26 files for the silent half and found the rule as first written was too
    narrow to be the durable enforcement it claimed to be. Judging "is this a collection or a nested
    DTO" answered correctly for the fields that produced S2885 and stayed silent on
    `WearNetworkSourcePayload.basePath: String = "/"`, which is the SAME loud defect in a type the rule
    did not look at, dereferenced on both sides. One predicate now replaces both rules rather than a
    second rule sitting beside the first, because two definitions of one declaration are free to drift
    apart and this file exists precisely because knowledge that is not in one place does drift.

    SHAPE JUDGED. A property declared WITHOUT `?` and WITH a default, unless the default is exactly
    what Gson will leave there - which is possible only for the eight Kotlin primitives, and only when
    the default is that primitive's JVM zero. Both halves of the trigger are required:

      - Without a default (`val sources: List<T>`) the field is a required key its sender has always
        written; there is no compatibility gap and no claim of protection to refute. Seven such fields
        exist on the bridge and none is reported, which is why this gate landed with an empty baseline.
      - Without the non-null declaration (`val endpoints: List<T>? = null`) the field is already the
        cure this gate exists to enforce.

    A primitive at its JVM zero (`val servedOnWatch: Boolean = false`) is the third passing case and
    the one the widening had to preserve: the default and the absent-key value agree, so the
    declaration tells the truth. Nine such declarations stand on the bridge today, and reporting them
    would have made the widened rule fire on roughly every second field it scans.

    BASELINE. A version marker is the one shape that trips the predicate while behaving correctly: an
    absent `schemaVersion` reading as `0` says "the sender predates every known version", which is what
    a receiver branching on it should conclude. Those live in the baseline with a per-field
    justification rather than in the rule, because "is this field a version marker" is a judgement
    about intent that no line-shape test can make.

    INPUTS ARE AN EXPLICIT LIST, not reachability from a `gson.fromJson` call site. A reachability walk
    would answer correctly for today's envelopes and stay silent on the next one added, which is the
    failure that produced this ticket. The list below is read by eye, and an envelope missing from it
    is visible as a missing line rather than as an absence of findings. Adding a bridge DTO means
    adding it here.

    NOT IN SCOPE. A DTO gson-serialised into this build's own preferences and read back by this build
    is not bridge traffic. An older build writing a preference a newer build reads is the same hazard
    with the writer separated by an app update rather than a network hop; that case belongs to
    assert-gson-persistence-contract.ps1's durable-sink model, not here.

    LINE SHAPE. Declarations are matched per line, which is how every declaration in the scanned set is
    written. A property wrapped across lines would not be seen; if one is ever written that way, the
    fix is to unwrap it, because a bridge declaration that cannot be read at a glance is its own defect.

.PARAMETER RepoRoot
    Repository root to scan. Defaults to the root this script sits in. A test suite hands a synthetic
    tree here, which is the only way to exercise a shape the real repository does not contain.

.PARAMETER BaselinePath
    Exemption file to honour. Defaults to wear-wire-nullability-baseline.txt beside this script. Only
    a test suite passes it: the baseline belongs to the repository, not to the caller, and a run that
    chose its own exemptions would prove nothing. It is a parameter because -RepoRoot alone cannot
    exercise the baseline at all - the synthetic tree has no baseline of its own to point at (S2887).

.PARAMETER ChangedFiles
    Optional CSV or array of repo-relative paths. Supplied by post-change.ps1 -ScopeToFile. When it is
    supplied and names none of the declared inputs, findings are advisory (exit 3) rather than fatal -
    the S2824 fixed-input rule, because the author of a divergence in a file this caller never touched
    is another session.

.PARAMETER Gate
    Batch-runner mode: print findings and the verdict line, nothing else.

.PARAMETER Quiet
    Same output reduction as -Gate.

.OUTPUTS
    Exit 0 - every declared envelope is clean, or every finding is covered by the baseline.
    Exit 1 - at least one chargeable finding the baseline does not cover.
    Exit 2 - could not verify: a declared input file is missing, or a baseline line carries no
             justification.
    Exit 3 - findings exist but are not chargeable to this caller's changed set (advisory).
#>

[CmdletBinding()]
param(
    [string]$RepoRoot,
    [string]$BaselinePath,
    [string[]]$ChangedFiles,
    [switch]$Gate,
    [switch]$Quiet
)

$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot 'lib/fixed-input-scope.ps1')

if (-not $RepoRoot) {
    $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
}

$terse = $Gate -or $Quiet

# The bridge envelopes, both hand-mirrored copies of each. Read by eye; see DESCRIPTION.
$declaredInputs = @(
    'app_v2/src/main/java/com/sza/fastmediasorter/domain/model/WearSyncPayload.kt'
    'app_v2/src/main/java/com/sza/fastmediasorter/domain/model/WearSourcesExportPayload.kt'
    'app_v2/src/main/java/com/sza/fastmediasorter/domain/model/WearSendToReceiversPayload.kt'
    'app_v2/src/main/java/com/sza/fastmediasorter/domain/model/WearPhoneResourcePayload.kt'
    'app_v2/src/main/java/com/sza/fastmediasorter/domain/model/WearStreamPinsPayload.kt'
    'app_v2/src/main/java/com/sza/fastmediasorter/domain/model/WearFavoritesPayload.kt'
    'app_v2/src/main/java/com/sza/fastmediasorter/domain/model/WearSettingsPayload.kt'
    'app_v2/src/main/java/com/sza/fastmediasorter/domain/model/WearPlaybackStatePayload.kt'
    'app_v2/src/main/java/com/sza/fastmediasorter/domain/model/WearStreamTransferPayload.kt'
    'app_v2/src/main/java/com/sza/fastmediasorter/domain/model/WearCameraSessionPayload.kt'
    'app_v2/src/main/java/com/sza/fastmediasorter/domain/model/WearListenSessionPayload.kt'
    'app_v2/src/main/java/com/sza/fastmediasorter/domain/model/WearLogReportPayload.kt'
    'app_v2/src/main/java/com/sza/fastmediasorter/domain/model/WearEventEnvelope.kt'
    'wear/src/main/java/com/sza/fastmediasorter/wear/domain/model/WearSyncPayload.kt'
    'wear/src/main/java/com/sza/fastmediasorter/wear/domain/model/WearSourcesExportPayload.kt'
    'wear/src/main/java/com/sza/fastmediasorter/wear/domain/model/WearSendToReceiversPayload.kt'
    'wear/src/main/java/com/sza/fastmediasorter/wear/domain/model/WearPhoneResourcePayload.kt'
    'wear/src/main/java/com/sza/fastmediasorter/wear/domain/model/WearStreamPinsPayload.kt'
    'wear/src/main/java/com/sza/fastmediasorter/wear/domain/model/WearFavoritesPayload.kt'
    'wear/src/main/java/com/sza/fastmediasorter/wear/domain/model/WearSettingsPayload.kt'
    'wear/src/main/java/com/sza/fastmediasorter/wear/domain/model/WearPlaybackStatePayload.kt'
    'wear/src/main/java/com/sza/fastmediasorter/wear/domain/model/WearStreamTransferPayload.kt'
    'wear/src/main/java/com/sza/fastmediasorter/wear/domain/model/CameraSessionPayload.kt'
    'wear/src/main/java/com/sza/fastmediasorter/wear/domain/model/ListenSessionPayload.kt'
    'wear/src/main/java/com/sza/fastmediasorter/wear/data/wear/WearLogReportPayload.kt'
    'wear/src/main/java/com/sza/fastmediasorter/wear/domain/model/WearEventEnvelope.kt'
)

$baselinePath = if ($BaselinePath) { $BaselinePath } else {
    Join-Path $PSScriptRoot 'wear-wire-nullability-baseline.txt'
}

# --- read the baseline -------------------------------------------------------------------------
$exempt = @{}
if (Test-Path -LiteralPath $baselinePath) {
    $lineNo = 0
    foreach ($raw in (Get-Content -LiteralPath $baselinePath)) {
        $lineNo++
        $line = $raw.Trim()
        if (-not $line -or $line.StartsWith('#')) { continue }
        # <file>::<field> | <justification>
        $parts = $line -split '\|', 2
        if ($parts.Count -lt 2 -or -not $parts[1].Trim()) {
            Write-Host "assert-wear-wire-nullability: baseline line ${lineNo} carries no justification: $line"
            exit 2
        }
        $exempt[$parts[0].Trim().ToLowerInvariant()] = $parts[1].Trim()
    }
}

# --- read the scanned set -----------------------------------------------------------------------
$fileText = @{}
foreach ($rel in $declaredInputs) {
    $full = Join-Path $RepoRoot $rel
    if (-not (Test-Path -LiteralPath $full)) {
        Write-Host "assert-wear-wire-nullability: declared input is missing: $rel"
        Write-Host '  Either the envelope moved and this list was not updated, or the list names a file that never existed.'
        exit 2
    }
    $fileText[$rel] = Get-Content -LiteralPath $full
}

# --- the absent-key value, per type ---------------------------------------------------------------
# The eight Kotlin primitives, each mapped to the spellings of its JVM zero that a declaration may
# legitimately use. Everything absent from this table is a reference type, whose absent-key value is
# null and which therefore can never agree with a non-null declaration carrying any default at all.
$primitiveZeroes = @{
    'Boolean' = @('false')
    'Byte'    = @('0')
    'Short'   = @('0')
    'Int'     = @('0')
    'Long'    = @('0L', '0')
    'Float'   = @('0f', '0F', '0.0f', '0.0F', '0.0')
    'Double'  = @('0.0', '0.0d', '0.0D', '0')
    'Char'    = @("' '", "'\\u0000'")
}

# --- scan ---------------------------------------------------------------------------------------
$findings = @()

foreach ($rel in $declaredInputs) {
    $lineNo = 0
    foreach ($line in $fileText[$rel]) {
        $lineNo++
        $stripped = ($line -replace '^\s*@\w+\([^)]*\)\s*', '')
        if ($stripped -notmatch '^\s*(?:val|var)\s+(\w+)\s*:\s*(.+?)\s*=\s*(.+?)\s*,?\s*$') { continue }

        $field = $Matches[1]
        $type = $Matches[2].Trim()
        # A trailing line comment is not part of the default: `val domain: String = "", // SMB domain`.
        $default = (($Matches[3] -split '\s+//', 2)[0]).Trim().TrimEnd(',').Trim()

        # Already nullable - this is the cure, not the defect.
        if ($type.EndsWith('?')) { continue }

        # A primitive whose default is its own JVM zero states exactly what Gson will leave there.
        if ($primitiveZeroes.ContainsKey($type)) {
            $normalised = $default -replace '_', ''
            if ($primitiveZeroes[$type] -contains $normalised) { continue }
        }

        $key = "$rel::$field".ToLowerInvariant()
        $findings += [pscustomobject]@{
            File     = $rel
            Line     = $lineNo
            Field    = $field
            Type     = $type
            Default  = $default
            Absent   = if ($primitiveZeroes.ContainsKey($type)) { $primitiveZeroes[$type][0] } else { 'null' }
            Exempt   = $exempt.ContainsKey($key)
            Reason   = if ($exempt.ContainsKey($key)) { $exempt[$key] } else { '' }
        }
    }
}

$live = @($findings | Where-Object { -not $_.Exempt })
$hidden = @($findings | Where-Object { $_.Exempt })

$chargeable = Test-FixedInputsChargeable -ChangedFiles $ChangedFiles -InputPaths $declaredInputs

if (-not $terse) {
    Write-Host "assert-wear-wire-nullability: scanned $($declaredInputs.Count) bridge envelope file(s)."
}

$collectionHeads = @('List', 'MutableList', 'Set', 'MutableSet', 'Map', 'MutableMap', 'Collection', 'Array')

foreach ($f in $live) {
    $cure = if ($collectionHeads -contains ($f.Type -split '<', 2)[0].Trim()) {
        'read it through .orEmpty() at every receive site'
    } else {
        "read it through '?: $($f.Default)' at every receive site"
    }
    Write-Host "  $($f.File):$($f.Line)  $($f.Field): $($f.Type) = $($f.Default)"
    Write-Host '    Non-null declaration with a Kotlin default. Gson runs no constructor, so an absent key'
    Write-Host "    leaves $($f.Absent) here, not $($f.Default) - the declaration states a protection it does not have."
    Write-Host "    Fix: declare it '$($f.Type)? = null' and $cure."
}

if ($live.Count -eq 0) {
    Write-Host "assert-wear-wire-nullability: PASS (0 findings, $($hidden.Count) baselined)"
    exit 0
}

if (-not $chargeable) {
    Write-Host "assert-wear-wire-nullability: ADVISORY - $($live.Count) finding(s), none in this caller's changed set (S2824)."
    exit 3
}

Write-Host "assert-wear-wire-nullability: FAIL - $($live.Count) finding(s), $($hidden.Count) baselined."
exit 1
