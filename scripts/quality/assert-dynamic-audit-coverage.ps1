#requires -Version 7.0
<#
.SYNOPSIS
    S4166: validate the coverage contract of the deep dynamic audit - the capability manifest, the scene
    catalog and the property matrix against the inventories the CLAUDE.md pointer chain names.

.DESCRIPTION
    Re-derives the live capability list from the chain CLAUDE.md section 11 -> .sza-profile.json
    (paths.allFeatures, paths.allFeaturesPrivate) -> the ledgers they name, and compares it with
    manifest.json, capabilities.json, scenes.json and matrix.json under the audit root, plus budgets.json,
    runs.json and evidence.json (the last two optional: absent means no run and no evidence yet).
    The data contract is PLAN/S4166_deep-dynamic-audit/research/04__audit-data-schema.md; the exit
    contract is strategic section 5.6 of the ticket.

    What it reports:
      - exit-2 class: an absent or unparsable audit file, ledger, CLAUDE.md or profile; a schemaVersion
        other than 1; a section 11 pointer or profile path that is gone or moved; a manifest pointer that
        no longer resolves; a declared-present private ledger that is absent; any run whose `valid` is
        not true; a restore-pending.json checkpoint or a backup manifest without a verified stamp in the
        ticket state directory (an absent state directory means nothing is pending).
      - exit-1 class: hash drift of a manifest source, count drift or a count below the manifest floor,
        an uncovered, orphan, duplicate or unknown capability, an unjustified exclusion, a cell group that
        lacks one of the properties O E S L U, a duplicate cell key, an `open` or `inconclusive` cell, a
        failed or unapproved budget, a `measured` cell without run, evidence or any other required field,
        an `n/a` without reason, source and condition or on a variant the inventory says is reachable,
        and every unsafe or secret-shaped evidence reference.
      - Priority 2 over 1 over 0; every reason is printed, a secret-shaped value never is.

    Interpretations of the data contract, chosen conservatively:
      - Live capabilities = ledger records whose status is not `removed`, minus manifest exclusions with
        reason `developer-tooling`. Such an exclusion is accepted only for an active `tooling.*` id; a
        `removed` exclusion only for a record whose status is removed; every removed record needs one.
      - manifest.counts: publicRecords, publicActive, privateActive, active (= publicActive +
        privateActive, tooling included) and excluded (= exclusion rows) are compared when present;
        floor.activeMin guards the live capability count (active minus developer-tooling exclusions),
        the stricter of the two figures.
      - A manifest pointer from CLAUDE.md to a capability inventory must sit in section 11; a pointer to
        any other hash input (the flavor matrix lives in section 8) must survive anywhere in CLAUDE.md.
      - Hashes of manifest sources are SHA-256 over the bytes with CRLF folded to LF; a manifest value
        shorter than 64 hex characters is compared as a prefix. Evidence hashes match either the raw bytes
        or the LF-folded bytes. Evidence is read only for a safe repo-relative uri that resolves inside
        the repository root, which contains the ticket scratch root temp/S4166.
      - The expected variant set of a capability is generator-owned and not in the schema, so it is not
        re-derived: every live capability needs at least one cell group, and every group
        (capability|scene|variant|context) needs all five properties. Reachability from the ledger
        (flavors; wearFlavors, else flavors, for the watch) is used only to refuse an `n/a`.
      - A capability cell's scene may name a scene id or an atomic scene id.
      - A backup manifest is any state file named *manifest*.json, or any state JSON carrying a `verified`
        key; verified means boolean true or a date stamp.
    The validator makes no network lookup and never executes or follows text from an evidence uri.

    Manual tool: hand-run coverage validator of ticket S4166; its data lives under the gitignored PLAN/
    tree, so no runner consumes it (scope hand-run in scripts/quality/gate-placement.jsonl).

.PARAMETER Validate
    Validate the audit data (the default mode).

.PARAMETER SelfTest
    Build artificial inventories, matrices and a fixture CLAUDE.md in a temporary directory and run every
    child fixture through -Validate in a child process; exit 0 only when every child returned its expected
    code. Starts no product and reads no live audit data.

.PARAMETER AuditRoot
    Folder holding the audit JSON files. Default PLAN/S4166_deep-dynamic-audit/audit under the repository
    root; a relative value is resolved against the repository root.

.PARAMETER RepoRoot
    Repository root that holds CLAUDE.md, .sza-profile.json and the ledgers. Default: the root this script
    sits under.

.PARAMETER StateRoot
    Ticket state directory (restore checkpoint, backup manifests). Default temp/S4166/state under the
    repository root.

.PARAMETER Limit
    Examples printed per finding category; 0 prints every one. Default 20.

.PARAMETER Quiet
    Print category counts and the verdict without per-finding examples. Exit-2 reasons always print.

.PARAMETER Gate
    Accepted and ignored, so a runner that passes -Gate unconditionally cannot fail parameter binding.

.NOTES
    Exit codes:
      0  complete coverage, no open or inconclusive cell, every measured cell passes an approved budget,
         every n/a justified; in -SelfTest, every child fixture returned its expected code.
      1  a verifiable contract or budget defect, a missing cell or capability, an open or inconclusive
         cell, a false measured or n/a; in -SelfTest, at least one child fixture mismatched.
      2  could not verify: an absent or unreadable source, an unsupported schema, a removed pointer, an
         invalid run, an unverified backup or a pending restore.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-dynamic-audit-coverage.ps1
.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-dynamic-audit-coverage.ps1 -SelfTest
#>
[CmdletBinding()]
param(
    [switch]$Validate,
    [switch]$SelfTest,
    [string]$AuditRoot,
    [string]$RepoRoot,
    [string]$StateRoot,
    [int]$Limit = 20,
    [switch]$Quiet,
    [switch]$Gate
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$GateName = 'assert-dynamic-audit-coverage'
$AuditRel = 'PLAN/S4166_deep-dynamic-audit/audit'
$StateRel = 'temp/S4166/state'
$Properties = @('O', 'E', 'S', 'L', 'U')
$Variants = @('standard', 'noLegal', 'vr', 'wear-standard', 'wear-noLegal', 'outside-first-pass')
$Statuses = @('measured', 'n/a', 'open')

if (-not $RepoRoot) { $RepoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot) }
$RepoRoot = [System.IO.Path]::GetFullPath($RepoRoot).TrimEnd('\', '/')
$repoPrefix = $RepoRoot + [System.IO.Path]::DirectorySeparatorChar
if (-not $AuditRoot) { $AuditRoot = $AuditRel }
if (-not [System.IO.Path]::IsPathRooted($AuditRoot)) { $AuditRoot = Join-Path $RepoRoot $AuditRoot }
if (-not $StateRoot) { $StateRoot = $StateRel }
if (-not [System.IO.Path]::IsPathRooted($StateRoot)) { $StateRoot = Join-Path $RepoRoot $StateRoot }
$null = $Validate, $Gate

. (Join-Path $PSScriptRoot 'lib/check-subject.ps1')

# --- shared helpers -------------------------------------------------------------------------------

$script:blockers = [System.Collections.Generic.List[string]]::new()
$script:defects = [ordered]@{}

function Add-Blocker([string]$Text) { $script:blockers.Add($Text) }

function Add-Defect([string]$Category, [string]$Text) {
    if (-not $script:defects.Contains($Category)) {
        $script:defects[$Category] = [System.Collections.Generic.List[string]]::new()
    }
    $script:defects[$Category].Add($Text)
}

function Get-RelPath([string]$Path) {
    $full = [System.IO.Path]::GetFullPath($Path)
    if ($full.StartsWith($repoPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        return $full.Substring($repoPrefix.Length) -replace '\\', '/'
    }
    return $full
}

function Get-LfSha256([string]$Path) {
    # Latin-1 maps every byte to one char and back, so the fold touches CR LF pairs and nothing else.
    $latin = [System.Text.Encoding]::Latin1
    $text = $latin.GetString([System.IO.File]::ReadAllBytes($Path)).Replace("`r`n", "`n")
    return [Convert]::ToHexString([System.Security.Cryptography.SHA256]::HashData($latin.GetBytes($text))).ToLowerInvariant()
}

function Get-RawSha256([string]$Path) {
    return [Convert]::ToHexString([System.Security.Cryptography.SHA256]::HashData([System.IO.File]::ReadAllBytes($Path))).ToLowerInvariant()
}

function Test-ShaMatch([string]$Expected, [string]$Actual) {
    $e = ([string]$Expected).Trim().ToLowerInvariant()
    if ($e -notmatch '^[0-9a-f]{16,64}$') { return $false }
    return $Actual.StartsWith($e, [System.StringComparison]::Ordinal)
}

$rxSecretKeyword = [regex]'(?i)(?:pass(?:word|wd)?|secret|token|api[_-]?key|apikey|access[_-]?key|private[_-]?key|client[_-]?secret|auth[_-]?token|credential|bearer|cookie|session[_-]?id)\s*[=:]'
$rxSecretProvider = [regex]'(?:AIza[0-9A-Za-z_\-]{20,}|gh[pousr]_[A-Za-z0-9]{20,}|AKIA[0-9A-Z]{12,}|xox[abprs]-[A-Za-z0-9\-]{10,}|sk-[A-Za-z0-9]{20,}|eyJ[A-Za-z0-9_\-]{10,}\.|PRIVATE KEY)'
$rxLongRun = [regex]'[A-Za-z0-9+=]{32,}'

function Test-SecretShaped([string]$Text) {
    if ([string]::IsNullOrEmpty($Text)) { return $false }
    if ($rxSecretKeyword.IsMatch($Text) -or $rxSecretProvider.IsMatch($Text)) { return $true }
    # A long unbroken run mixing upper, lower and digits reads as a credential; lowercase hex
    # (a content hash in a file name) does not.
    foreach ($m in $rxLongRun.Matches($Text)) {
        $v = $m.Value
        if ($v -cmatch '[A-Z]' -and $v -cmatch '[a-z]' -and $v -match '[0-9]') { return $true }
    }
    return $false
}

function Get-RefProblem([string]$Ref) {
    if ([string]::IsNullOrWhiteSpace($Ref)) { return 'empty reference' }
    if (Test-SecretShaped $Ref) { return 'secret-shaped reference' }
    if ($Ref -match '[\x00-\x1f]') { return 'control character' }
    if ($Ref.Contains('..')) { return 'path traversal' }
    if ($Ref -match '^[A-Za-z]:') { return 'drive letter' }
    if ($Ref.Contains(':')) { return 'scheme' }
    if ($Ref -match '^[\\/~]') { return 'absolute path' }
    if ($Ref.Contains('?')) { return 'query string' }
    if ($Ref.Contains('%')) { return 'percent-encoding' }
    return $null
}

function Resolve-InsideRepo([string]$Ref) {
    $full = [System.IO.Path]::GetFullPath((Join-Path $RepoRoot $Ref))
    if (-not $full.StartsWith($repoPrefix, [System.StringComparison]::OrdinalIgnoreCase)) { return $null }
    return $full
}

function Format-Ref([string]$Ref) {
    if (Test-SecretShaped $Ref) { return '<redacted>' }
    return "'$Ref'"
}

function Test-Filled($Value) {
    if ($null -eq $Value) { return $false }
    if ($Value -is [string]) { return -not [string]::IsNullOrWhiteSpace($Value) }
    if ($Value -is [System.Collections.IDictionary]) { return $Value.Count -gt 0 }
    if ($Value -is [System.Array]) { return $Value.Count -gt 0 }
    return $true
}

function Read-AuditJson([string]$Path, [string]$Label, [switch]$Optional) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        if (-not $Optional) { Add-Blocker "source unreadable: $Label is absent ($(Get-RelPath $Path))" }
        return $null
    }
    try {
        $data = Get-Content -LiteralPath $Path -Raw -Encoding utf8 | ConvertFrom-Json -AsHashtable -Depth 100
    } catch {
        $msg = $_.Exception.Message
        if ($msg.Length -gt 160) { $msg = $msg.Substring(0, 160) + '..' }
        Add-Blocker "source unreadable: $Label does not parse as JSON ($msg)"
        return $null
    }
    if ($data -isnot [System.Collections.IDictionary]) {
        Add-Blocker "source unreadable: $Label top level is not a JSON object"
        return $null
    }
    $version = $data['schemaVersion']
    if ("$version" -ne '1') {
        Add-Blocker "unsupported schema: $Label schemaVersion is '$version', this validator reads 1"
        return $null
    }
    return $data
}

function Get-List($Data, [string]$Key, [string]$Label, [switch]$Required) {
    if ($null -eq $Data) { return @() }
    if (-not $Data.Contains($Key) -or $null -eq $Data[$Key]) {
        if ($Required) { Add-Blocker "source unreadable: $Label has no '$Key' list" }
        return @()
    }
    $value = $Data[$Key]
    if ($value -is [System.Collections.IDictionary] -or $value -is [string]) {
        Add-Blocker "source unreadable: $Label '$Key' is not a list"
        return @()
    }
    return @($value)
}

function Read-Ledger([string]$Path, [string]$Label, [string]$Source) {
    $lines = @([System.IO.File]::ReadAllLines($Path) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    $records = $null
    try {
        # One parse for the whole file; only a failure pays the per-line pass that names the line.
        $records = @(('[' + ($lines -join ',') + ']') | ConvertFrom-Json -AsHashtable -Depth 32 -NoEnumerate)
        if ($records.Count -eq 1 -and $records[0] -is [System.Array]) { $records = @($records[0]) }
    } catch {
        for ($i = 0; $i -lt $lines.Count; $i++) {
            try { $null = $lines[$i] | ConvertFrom-Json -AsHashtable } catch {
                Add-Blocker "inventory unreadable: $Label record $($i + 1) does not parse"
                return $null
            }
        }
        Add-Blocker "inventory unreadable: $Label does not parse"
        return $null
    }
    $i = 0
    foreach ($r in $records) {
        $i++
        if ($r -isnot [System.Collections.IDictionary] -or -not (Test-Filled $r['id'])) {
            Add-Blocker "inventory unreadable: $Label record $i is not an object with an id"
            return $null
        }
        $r['__source'] = $Source
    }
    return , $records
}

function Get-LowerSet($Value) {
    $set = @{}
    foreach ($v in @($Value)) { if ($null -ne $v) { $set[([string]$v).ToLowerInvariant()] = $true } }
    return $set
}

function Test-Reachable($Record, [string]$Variant) {
    $flavors = Get-LowerSet $Record['flavors']
    $wear = if (Test-Filled $Record['wearFlavors']) { Get-LowerSet $Record['wearFlavors'] } else { $flavors }
    switch -CaseSensitive ($Variant) {
        'standard' { return $flavors.Contains('standard') }
        'noLegal' { return $flavors.Contains('nolegal') }
        'vr' { return $flavors.Contains('vr') }
        'wear-standard' { return $wear.Contains('standard') }
        'wear-noLegal' { return $wear.Contains('nolegal') }
    }
    return $false
}

# --- validation -----------------------------------------------------------------------------------

function Invoke-CoverageValidation {
    $stats = [ordered]@{}

    # 1. Restore and backup state of the stand: a pending or failed restore forbids any verdict.
    if (Test-Path -LiteralPath $StateRoot -PathType Container) {
        $pending = Join-Path $StateRoot 'restore-pending.json'
        if (Test-Path -LiteralPath $pending) {
            $state = 'pending'
            try {
                $p = Get-Content -LiteralPath $pending -Raw | ConvertFrom-Json -AsHashtable
                if ($p -is [System.Collections.IDictionary] -and (Test-Filled $p['state'])) { $state = [string]$p['state'] }
            } catch { $state = 'unreadable checkpoint' }
            Add-Blocker "restore pending: $(Get-RelPath $pending) is present (state: $state); finish the restore before any verdict"
        }
        foreach ($f in @(Get-ChildItem -LiteralPath $StateRoot -Recurse -Depth 3 -File -Filter '*.json')) {
            if ($f.Name -eq 'restore-pending.json') { continue }
            $named = $f.Name -match 'manifest'
            $j = $null
            try { $j = Get-Content -LiteralPath $f.FullName -Raw | ConvertFrom-Json -AsHashtable } catch {
                if ($named) { Add-Blocker "backup unverified: $(Get-RelPath $f.FullName) does not parse" }
                continue
            }
            if ($j -isnot [System.Collections.IDictionary]) { continue }
            if ($j.Contains('verified')) {
                $v = $j['verified']
                $ok = ($v -is [bool] -and $v) -or ($v -is [datetime]) -or
                    ($v -is [string] -and $v -as [datetime])
                if (-not $ok) { Add-Blocker "backup unverified: $(Get-RelPath $f.FullName) carries verified='$v'" }
            } elseif ($named) {
                Add-Blocker "backup unverified: $(Get-RelPath $f.FullName) carries no verified stamp"
            }
        }
    }

    # 2. The pointer chain: CLAUDE.md section 11 -> .sza-profile.json -> ledgers.
    $claudePath = Join-Path $RepoRoot 'CLAUDE.md'
    $profilePath = Join-Path $RepoRoot '.sza-profile.json'
    $sectionTokens = @{}
    $sectionFound = $false
    $claudeTokens = @{}
    $profileValues = @{}
    $publicRel = $null
    $privateRel = $null
    if (-not (Test-Path -LiteralPath $claudePath -PathType Leaf)) {
        Add-Blocker 'source unreadable: CLAUDE.md is absent at the repository root'
    } else {
        $claudeText = (Get-Content -LiteralPath $claudePath -Raw -Encoding utf8) -replace "`r`n", "`n"
        foreach ($m in [regex]::Matches($claudeText, '`([^`\s]+)`')) { $claudeTokens[$m.Groups[1].Value] = $true }
        $section = [regex]::Match($claudeText, '(?ms)^## 11\.[^\n]*\n(.*?)(?=^## |\z)')
        if (-not $section.Success) {
            Add-Blocker 'pointer removed: CLAUDE.md has no section 11, the chain to the capability inventory is gone'
        } else {
            $sectionFound = $true
            foreach ($m in [regex]::Matches($section.Groups[1].Value, '`([^`\s]+)`')) { $sectionTokens[$m.Groups[1].Value] = $true }
        }
    }
    $profileData = $null
    if (-not (Test-Path -LiteralPath $profilePath -PathType Leaf)) {
        Add-Blocker 'source unreadable: .sza-profile.json is absent at the repository root'
    } else {
        try { $profileData = Get-Content -LiteralPath $profilePath -Raw -Encoding utf8 | ConvertFrom-Json -AsHashtable -Depth 64 } catch {
            Add-Blocker 'source unreadable: .sza-profile.json does not parse as JSON'
        }
    }
    if ($profileData -is [System.Collections.IDictionary]) {
        $paths = $profileData['paths']
        if ($paths -isnot [System.Collections.IDictionary]) {
            Add-Blocker 'pointer removed: .sza-profile.json has no paths object'
        } else {
            foreach ($k in $paths.Keys) { if ($paths[$k] -is [string]) { $profileValues[[string]$paths[$k]] = $true } }
            if (-not (Test-Filled $paths['allFeatures'])) {
                Add-Blocker 'pointer removed: .sza-profile.json paths.allFeatures is absent'
            } else { $publicRel = [string]$paths['allFeatures'] }
            if (Test-Filled $paths['allFeaturesPrivate']) { $privateRel = [string]$paths['allFeaturesPrivate'] }
        }
    }
    foreach ($pair in @(@('public', $publicRel), @('private', $privateRel))) {
        $rel = $pair[1]
        if (-not $rel) { continue }
        $problem = Get-RefProblem $rel
        if ($problem) { Add-Blocker "pointer removed: the $($pair[0]) inventory path $(Format-Ref $rel) is unsafe ($problem)"; continue }
        if ($sectionFound -and -not $sectionTokens.Contains($rel)) {
            Add-Blocker "pointer removed: CLAUDE.md section 11 no longer names the $($pair[0]) inventory '$rel' that .sza-profile.json declares"
        }
    }

    # 3. Ledgers named by the chain.
    $records = [System.Collections.Generic.List[object]]::new()
    $publicPresent = $false
    $privatePresent = $false
    $publicRecords = 0; $publicActive = 0; $privateActive = 0; $privateRecords = 0
    if ($publicRel -and -not (Get-RefProblem $publicRel)) {
        $pp = Join-Path $RepoRoot $publicRel
        if (-not (Test-Path -LiteralPath $pp -PathType Leaf)) {
            Add-Blocker "inventory unreadable: the public ledger '$publicRel' is absent"
        } else {
            $publicPresent = $true
            $pub = Read-Ledger $pp $publicRel 'public'
            if ($null -ne $pub) {
                $publicRecords = $pub.Count
                foreach ($r in $pub) { $records.Add($r); if ([string]$r['status'] -ne 'removed') { $publicActive++ } }
            }
        }
    }
    if ($privateRel -and -not (Get-RefProblem $privateRel)) {
        $vp = Join-Path $RepoRoot $privateRel
        if (Test-Path -LiteralPath $vp -PathType Leaf) {
            $privatePresent = $true
            $priv = Read-Ledger $vp $privateRel 'private'
            if ($null -ne $priv) {
                $privateRecords = $priv.Count
                foreach ($r in $priv) { $records.Add($r); if ([string]$r['status'] -ne 'removed') { $privateActive++ } }
            }
        }
    }
    $ledger = @{}
    foreach ($r in $records) {
        $id = [string]$r['id']
        if ($ledger.Contains($id)) { Add-Defect 'inventory-duplicate-id' "id '$id' appears more than once across the ledgers"; continue }
        $ledger[$id] = $r
    }

    # 4. Audit files.
    $manifest = Read-AuditJson (Join-Path $AuditRoot 'manifest.json') 'manifest.json'
    $capsData = Read-AuditJson (Join-Path $AuditRoot 'capabilities.json') 'capabilities.json'
    $scenesData = Read-AuditJson (Join-Path $AuditRoot 'scenes.json') 'scenes.json'
    $budgets = Read-AuditJson (Join-Path $AuditRoot 'budgets.json') 'budgets.json'
    $matrix = Read-AuditJson (Join-Path $AuditRoot 'matrix.json') 'matrix.json'
    $runsData = Read-AuditJson (Join-Path $AuditRoot 'runs.json') 'runs.json' -Optional
    $evidenceData = Read-AuditJson (Join-Path $AuditRoot 'evidence.json') 'evidence.json' -Optional

    # 5. Manifest: pointers, sources and hashes, private ledger, counts, floor, exclusions.
    $exclusions = @{}
    if ($null -ne $manifest) {
        $pointers = Get-List $manifest 'pointers' 'manifest.json' -Required
        $pointerTokens = @{}
        foreach ($p in $pointers) {
            if ($p -isnot [System.Collections.IDictionary]) { Add-Blocker 'pointer removed: manifest.json holds a pointer that is not an object'; continue }
            $from = [string]$p['from']; $tok = [string]$p['token']
            $pointerTokens[$tok] = $true
            $tokProblem = Get-RefProblem $tok
            if ($tokProblem) { Add-Blocker "pointer removed: manifest pointer token $(Format-Ref $tok) is unsafe ($tokProblem)"; continue }
            $fromFile = (($from -split '[#:\s]')[0]).Trim()
            if ($fromFile -eq 'CLAUDE.md') {
                # The capability inventories must stay in section 11; any other hash-input pointer
                # (the flavor matrix sits in section 8) only has to survive somewhere in the file.
                if ($tok -eq $publicRel -or $tok -eq $privateRel) {
                    if (-not $sectionTokens.Contains($tok)) { Add-Blocker "pointer removed: CLAUDE.md section 11 no longer names '$tok'" }
                } elseif (-not $claudeTokens.Contains($tok)) { Add-Blocker "pointer removed: CLAUDE.md no longer names '$tok'" }
            } elseif ($fromFile -eq '.sza-profile.json') {
                if (-not $profileValues.Contains($tok)) { Add-Blocker "pointer removed: .sza-profile.json paths no longer name '$tok'" }
            } else {
                $fromProblem = Get-RefProblem $fromFile
                $fromPath = if ($fromProblem) { $null } else { Resolve-InsideRepo $fromFile }
                if (-not $fromPath -or -not (Test-Path -LiteralPath $fromPath -PathType Leaf)) {
                    Add-Blocker "pointer removed: pointer origin $(Format-Ref $from) cannot be read"
                } elseif (-not (Get-Content -LiteralPath $fromPath -Raw).Contains($tok)) {
                    Add-Blocker "pointer removed: '$fromFile' no longer names '$tok'"
                }
            }
        }
        foreach ($rel in @($publicRel, $(if ($privateRel) { $privateRel }))) {
            if ($rel -and -not $pointerTokens.Contains($rel)) {
                Add-Defect 'manifest-incomplete' "manifest pointers omit '$rel', which the live chain names"
            }
        }

        $sources = Get-List $manifest 'sources' 'manifest.json' -Required
        $sourcePaths = @{}
        foreach ($s in $sources) {
            if ($s -isnot [System.Collections.IDictionary]) { Add-Blocker 'source unreadable: manifest.json holds a source that is not an object'; continue }
            $rel = [string]$s['path']
            $sourcePaths[$rel] = $true
            $problem = Get-RefProblem $rel
            $full = if ($problem) { $null } else { Resolve-InsideRepo $rel }
            if (-not $full) { Add-Blocker "source unreadable: manifest source $(Format-Ref $rel) is not a safe repo-relative path"; continue }
            if (-not (Test-Path -LiteralPath $full -PathType Leaf)) {
                if ($s['required'] -eq $true) { Add-Blocker "source unreadable: required source '$rel' is absent" }
                continue
            }
            $actual = Get-LfSha256 $full
            if (-not (Test-ShaMatch ([string]$s['sha256']) $actual)) {
                Add-Defect 'hash-drift' "'$rel' changed since the manifest was built (manifest $([string]$s['sha256']), now $($actual.Substring(0, 16))..) - rebuild manifest.json"
            }
        }
        foreach ($rel in @($publicRel, 'CLAUDE.md', '.sza-profile.json', $(if ($privatePresent) { $privateRel }))) {
            if ($rel -and -not $sourcePaths.Contains($rel)) { Add-Defect 'manifest-incomplete' "manifest sources do not hash '$rel'" }
        }

        $pl = $manifest['privateLedger']
        if ($pl -isnot [System.Collections.IDictionary]) {
            Add-Defect 'manifest-incomplete' 'manifest.json has no privateLedger object'
        } else {
            if ($pl['present'] -eq $true -and -not $privatePresent) {
                Add-Blocker "inventory unreadable: manifest declares the private ledger present, but '$privateRel' is absent - the denominator would shrink silently"
            } elseif ($pl['present'] -ne $true -and $privatePresent) {
                Add-Defect 'private-ledger-undeclared' "'$privateRel' is present but the manifest declares it absent - the denominator grew"
            }
            if ($privatePresent -and $pl.Contains('count') -and "$($pl['count'])" -ne "$privateActive" -and "$($pl['count'])" -ne "$privateRecords") {
                Add-Defect 'count-drift' "privateLedger.count is $($pl['count']), the private ledger holds $privateActive active record(s)"
            }
        }

        foreach ($e in (Get-List $manifest 'exclusions' 'manifest.json')) {
            if ($e -isnot [System.Collections.IDictionary]) { Add-Defect 'exclusion-unjustified' 'an exclusion row is not an object'; continue }
            $id = [string]$e['id']; $reason = [string]$e['reason']
            $rec = $ledger[$id]
            if ($null -eq $rec) { Add-Defect 'exclusion-unjustified' "exclusion '$id' names no ledger record"; continue }
            $removed = [string]$rec['status'] -eq 'removed'
            switch ($reason) {
                'removed' { if (-not $removed) { Add-Defect 'exclusion-unjustified' "exclusion '$id' says removed, the ledger record is active" } }
                'developer-tooling' {
                    if ($removed -or -not $id.StartsWith('tooling.')) { Add-Defect 'exclusion-unjustified' "exclusion '$id' says developer-tooling, but it is not an active tooling.* record" }
                }
                default { Add-Defect 'exclusion-unjustified' "exclusion '$id' carries reason '$reason', expected removed or developer-tooling" }
            }
            $exclusions[$id] = $reason
        }
        foreach ($id in $ledger.Keys) {
            if ([string]$ledger[$id]['status'] -eq 'removed' -and -not $exclusions.Contains($id)) {
                Add-Defect 'exclusion-missing' "removed record '$id' has no exclusion row"
            }
        }

        $liveCounts = [ordered]@{
            publicRecords = $publicRecords; publicActive = $publicActive; privateActive = $privateActive
            active = $publicActive + $privateActive; excluded = $exclusions.Count
        }
        $counts = $manifest['counts']
        if ($counts -isnot [System.Collections.IDictionary]) {
            Add-Defect 'manifest-incomplete' 'manifest.json has no counts object'
        } else {
            foreach ($k in $liveCounts.Keys) {
                if ($counts.Contains($k) -and "$($counts[$k])" -ne "$($liveCounts[$k])") {
                    Add-Defect 'count-drift' "counts.$k is $($counts[$k]), the live inventories give $($liveCounts[$k])"
                }
            }
        }
    }

    $live = @{}
    foreach ($id in $ledger.Keys) {
        $r = $ledger[$id]
        if ([string]$r['status'] -eq 'removed') { continue }
        if ($exclusions[$id] -eq 'developer-tooling') { continue }
        $live[$id] = $r
    }
    $stats['live capabilities'] = $live.Count

    if ($null -ne $manifest) {
        $floor = $manifest['floor']
        $floorMin = if ($floor -is [System.Collections.IDictionary]) { $floor['activeMin'] } else { $null }
        if ($null -eq $floorMin -or $floorMin -is [string] -or $floorMin -is [bool] -or $null -eq ($floorMin -as [double])) {
            Add-Defect 'manifest-incomplete' 'manifest.json has no numeric floor.activeMin'
        } elseif ($live.Count -lt $floorMin) {
            Add-Defect 'count-below-floor' "the live capability count $($live.Count) is below the manifest floor $floorMin - the denominator shrank"
        }
    }

    # 6. Scene catalog.
    $sceneIds = @{}
    $knownSceneRefs = @{}
    $sceneAtomics = @{}
    $unscopedAtomics = [System.Collections.Generic.List[object]]::new()
    foreach ($sc in (Get-List $scenesData 'scenes' 'scenes.json' -Required)) {
        if ($sc -isnot [System.Collections.IDictionary] -or -not (Test-Filled $sc['id'])) { Add-Defect 'scene-malformed' 'a scene has no id'; continue }
        $sid = [string]$sc['id']
        if ($sceneIds.Contains($sid)) { Add-Defect 'scene-duplicate' "scene id '$sid' repeats" }
        $sceneIds[$sid] = $sc
        $knownSceneRefs[$sid] = $true
        $atomics = @{}
        foreach ($a in @($sc['atomic'])) {
            if ($null -eq $a) { continue }
            if ($a -isnot [System.Collections.IDictionary] -or -not (Test-Filled $a['id'])) { Add-Defect 'scene-malformed' "scene '$sid' holds an atomic scene without an id"; continue }
            $aid = [string]$a['id']
            if ($atomics.Contains($aid)) { Add-Defect 'scene-duplicate' "atomic scene '$sid/$aid' repeats" }
            $atomics[$aid] = $a
            $knownSceneRefs[$aid] = $true
            if ($a['applicable'] -eq $false) {
                if (-not (Test-Filled $a['naReason']) -or -not (Test-Filled $a['naSource'])) {
                    Add-Defect 'na-incomplete' "atomic scene '$sid/$aid' is not applicable without naReason and naSource"
                }
            }
            if ($sc['capabilityScoped'] -eq $false) { $unscopedAtomics.Add(@($sid, $aid, $a)) }
        }
        $sceneAtomics[$sid] = $atomics
    }

    # 7. Capability list against the live inventories.
    $listed = @{}
    foreach ($item in (Get-List $capsData 'items' 'capabilities.json' -Required)) {
        if ($item -isnot [System.Collections.IDictionary] -or -not (Test-Filled $item['id'])) { Add-Defect 'capability-malformed' 'a capabilities.json item has no id'; continue }
        $id = [string]$item['id']
        if ($listed.Contains($id)) { Add-Defect 'capability-duplicate' "capability id '$id' is listed more than once"; continue }
        $listed[$id] = $item
        if (-not $live.Contains($id)) {
            $why = if ($exclusions.Contains($id)) { "excluded as $($exclusions[$id])" } elseif ($ledger.Contains($id)) { 'removed in the ledger' } else { 'absent from every ledger' }
            Add-Defect 'capability-orphan' "capability '$id' is listed but $why"
            continue
        }
        $rec = $live[$id]
        if ([string]$item['area'] -cne [string]$rec['area']) { Add-Defect 'capability-drift' "capability '$id' area '$($item['area'])' differs from the ledger '$($rec['area'])'" }
        if ($item.Contains('source') -and [string]$item['source'] -ne [string]$rec['__source']) { Add-Defect 'capability-drift' "capability '$id' source '$($item['source'])' differs from the ledger '$($rec['__source'])'" }
        $scene = [string]$item['scene']
        if (-not $scene) { Add-Defect 'capability-unmapped' "capability '$id' has no scene - a diagnosed coverage gap" }
        elseif (-not $knownSceneRefs.Contains($scene)) { Add-Defect 'capability-unknown-scene' "capability '$id' names scene '$scene', absent from scenes.json" }
    }
    if ($null -ne $capsData) {
        foreach ($id in ($live.Keys | Sort-Object)) {
            if (-not $listed.Contains($id)) { Add-Defect 'capability-uncovered' "live capability '$id' ($($live[$id]['__source'])) is absent from capabilities.json" }
        }
    }
    $stats['listed capabilities'] = $listed.Count

    # 8. Runs, evidence and budgets.
    $runs = @{}
    foreach ($run in (Get-List $runsData 'runs' 'runs.json' -Required:($null -ne $runsData))) {
        if ($run -isnot [System.Collections.IDictionary] -or -not (Test-Filled $run['runId'])) { Add-Blocker 'invalid run: runs.json holds a run without a runId'; continue }
        $rid = [string]$run['runId']
        if ($runs.Contains($rid)) { Add-Defect 'run-duplicate' "run '$rid' repeats in runs.json" }
        $runs[$rid] = $run
        if ($run['valid'] -ne $true) {
            Add-Blocker "invalid run: '$rid' is not valid ($([string]$run['invalidReason'])); a run is never replaced silently by a later good one"
        }
    }
    $evidence = @{}
    foreach ($ev in (Get-List $evidenceData 'evidence' 'evidence.json' -Required:($null -ne $evidenceData))) {
        if ($ev -isnot [System.Collections.IDictionary] -or -not (Test-Filled $ev['id'])) { Add-Defect 'evidence-malformed' 'an evidence entry has no id'; continue }
        $eid = [string]$ev['id']
        if ($evidence.Contains($eid)) { Add-Defect 'evidence-duplicate' "evidence id $(Format-Ref $eid) repeats" }
        $evidence[$eid] = $ev
    }
    $evidenceVerdict = @{}
    $budgetVersion = if ($null -ne $budgets) { [string]$budgets['version'] } else { '' }
    $budgetState = if ($null -ne $budgets) { [string]$budgets['state'] } else { '' }
    $budgetIds = @{}
    foreach ($b in (Get-List $budgets 'items' 'budgets.json')) { if ($b -is [System.Collections.IDictionary]) { $budgetIds[[string]$b['id']] = $true } }

    # 9. Matrix cells (capability-bound and scene-bound), one pass each, hashtables only.
    $groups = @{}
    $capsWithCells = @{}
    $atomicWithCells = @{}
    $tally = @{ open = 0; 'n/a' = 0; measured = 0; inconclusive = 0 }
    foreach ($kind in @('cells', 'sceneCells')) {
        $isCap = $kind -eq 'cells'
        $keys = @{}
        $list = @(Get-List $matrix $kind 'matrix.json' -Required:$isCap)
        foreach ($c in $list) {
            if ($c -isnot [System.Collections.IDictionary]) { Add-Defect 'cell-malformed' "a $kind entry is not an object"; continue }
            $first = if ($isCap) { [string]$c['capability'] } else { [string]$c['scene'] }
            $second = if ($isCap) { [string]$c['scene'] } else { [string]$c['atomic'] }
            $variant = [string]$c['variant']; $context = [string]$c['context']; $prop = [string]$c['property']
            if (-not ($first -and $second -and $variant -and $context -and $prop) -and (Test-Filled $c['key'])) {
                $parts = ([string]$c['key']).Split('|')
                if ($parts.Count -eq 5) { $first, $second, $variant, $context, $prop = $parts }
            }
            $key = "$first|$second|$variant|$context|$prop"
            if (-not ($first -and $second -and $variant -and $context -and $prop)) { Add-Defect 'cell-malformed' "$kind entry '$key' lacks a key field"; continue }
            if ($Variants -cnotcontains $variant) { Add-Defect 'cell-malformed' "$kind '$key' has unknown variant '$variant'"; continue }
            if ($Properties -cnotcontains $prop) { Add-Defect 'cell-malformed' "$kind '$key' has unknown property '$prop'"; continue }
            if ($keys.Contains($key)) { Add-Defect 'cell-duplicate-key' "$kind key '$key' repeats" }
            $keys[$key] = $true

            $capRecord = $null
            $atomic = $null
            if ($isCap) {
                if (-not $listed.Contains($first)) {
                    $hint = if ($live.Contains($first)) { 'live but not in capabilities.json' } else { 'in no capability list' }
                    Add-Defect 'cell-unknown-capability' "cell '$key' names capability '$first', $hint"
                } else { $capsWithCells[$first] = $true }
                $capRecord = $live[$first]
                if (-not $knownSceneRefs.Contains($second)) { Add-Defect 'cell-unknown-scene' "cell '$key' names scene '$second', absent from scenes.json" }
            } else {
                $atomics = $sceneAtomics[$first]
                if ($null -eq $atomics) { Add-Defect 'cell-unknown-scene' "scene cell '$key' names scene '$first', absent from scenes.json" }
                elseif (-not $atomics.Contains($second)) { Add-Defect 'cell-unknown-scene' "scene cell '$key' names atomic scene '$second', absent from scene '$first'" }
                else { $atomic = $atomics[$second]; $atomicWithCells["$first|$second"] = $true }
            }
            $gk = "$kind|$first|$second|$variant|$context"
            $g = $groups[$gk]
            if ($null -eq $g) { $g = @{}; $groups[$gk] = $g }
            $g[$prop] = $true

            $status = [string]$c['status']
            switch -CaseSensitive ($status) {
                'open' {
                    $tally.open++
                    $missing = @(foreach ($f in 'reason', 'owner', 'nextStep') { if (-not (Test-Filled $c[$f])) { $f } })
                    if ($missing.Count) { Add-Defect 'open-incomplete' "open $kind '$key' lacks $($missing -join ', ')" }
                    Add-Defect 'cell-open' "$key - $([string]$c['reason'])"
                    if ($null -ne $atomic -and $atomic['applicable'] -eq $false) { Add-Defect 'na-wrong' "scene cell '$key' is open, but its atomic scene is declared not applicable" }
                }
                'n/a' {
                    $tally['n/a']++
                    $missing = @(foreach ($f in 'reason', 'source', 'condition') { if (-not (Test-Filled $c[$f])) { $f } })
                    if ($missing.Count) { Add-Defect 'na-incomplete' "n/a $kind '$key' lacks $($missing -join ', ')" }
                    if ($variant -eq 'outside-first-pass') { Add-Defect 'na-wrong' "$kind '$key' is n/a outside the first pass - that variant is open, never n/a" }
                    if ($null -ne $capRecord -and (Test-Reachable $capRecord $variant)) {
                        Add-Defect 'na-wrong' "cell '$key' is n/a, but the ledger makes '$first' reachable in $variant"
                    }
                    $src = [string]$c['source']
                    if ($src) {
                        if (Test-SecretShaped $src) { Add-Defect 'na-wrong' "$kind '$key' cites a secret-shaped source <redacted>; not followed" }
                        elseif ($src.Contains('..') -or $src -match '^[A-Za-z][A-Za-z0-9+.-]*://|^[A-Za-z]:[\\/]|^[\\/]') {
                            Add-Defect 'na-wrong' "$kind '$key' cites an unsafe source '$src'; not followed"
                        } else {
                            $pm = [regex]::Match($src, '^((?:[A-Za-z0-9_.\-]+/)*[A-Za-z0-9_\-][A-Za-z0-9_.\-]*\.(?:md|json|jsonl|kts|kt|xml|txt|ps1|yml|yaml|csv))(?=$|[#:\s,;)])')
                            if ($pm.Success) {
                                $sp = Resolve-InsideRepo $pm.Groups[1].Value
                                if (-not $sp -or -not (Test-Path -LiteralPath $sp -PathType Leaf)) { Add-Defect 'na-wrong' "$kind '$key' cites source '$($pm.Groups[1].Value)', which does not exist" }
                            }
                        }
                    }
                }
                'measured' {
                    $tally.measured++
                    $missing = @(foreach ($f in 'run', 'method', 'metric', 'unit', 'n', 'budgetVersion', 'evidence', 'verdict') { if (-not (Test-Filled $c[$f])) { $f } })
                    if ($missing -contains 'evidence') { Add-Defect 'measured-without-evidence' "measured $kind '$key' carries no evidence" }
                    if ($missing.Count) { Add-Defect 'measured-incomplete' "measured $kind '$key' lacks $($missing -join ', ')" }
                    if ((Test-Filled $c['n']) -and -not (($c['n'] -as [long]) -gt 0)) { Add-Defect 'measured-incomplete' "measured $kind '$key' has a non-positive sample size '$($c['n'])'" }
                    $rid = [string]$c['run']
                    if ($rid -and -not $runs.Contains($rid)) { Add-Defect 'measured-unknown-run' "measured $kind '$key' names run '$rid', absent from runs.json" }
                    $eid = [string]$c['evidence']
                    if ($eid) {
                        if (-not $evidenceVerdict.Contains($eid)) {
                            $evidenceVerdict[$eid] = $true
                            $ev = $evidence[$eid]
                            if ($null -eq $ev) {
                                if (Test-SecretShaped $eid) { Add-Defect 'evidence-secret-shaped' "a measured cell cites a secret-shaped evidence reference <redacted>; not followed" }
                                else { Add-Defect 'measured-without-evidence' "evidence '$eid' is absent from evidence.json" }
                                $evidenceVerdict[$eid] = $false
                            } else {
                                $uri = [string]$ev['uri']
                                $problem = Get-RefProblem $uri
                                $full = if ($problem) { $null } else { Resolve-InsideRepo $uri }
                                if ($problem -eq 'secret-shaped reference') {
                                    Add-Defect 'evidence-secret-shaped' "evidence $(Format-Ref $eid) has a secret-shaped uri <redacted>; not followed"
                                    $evidenceVerdict[$eid] = $false
                                } elseif (-not $full) {
                                    Add-Defect 'evidence-unsafe' "evidence $(Format-Ref $eid) uri $(Format-Ref $uri) is refused ($(if ($problem) { $problem } else { 'outside the repository' })); not followed"
                                    $evidenceVerdict[$eid] = $false
                                } elseif (-not (Test-Path -LiteralPath $full -PathType Leaf)) {
                                    Add-Defect 'measured-without-evidence' "evidence $(Format-Ref $eid) file '$uri' is absent"
                                    $evidenceVerdict[$eid] = $false
                                } else {
                                    $want = [string]$ev['sha256']
                                    if (-not ((Test-ShaMatch $want (Get-RawSha256 $full)) -or (Test-ShaMatch $want (Get-LfSha256 $full)))) {
                                        Add-Defect 'evidence-hash-mismatch' "evidence $(Format-Ref $eid) file '$uri' does not match its sha256"
                                        $evidenceVerdict[$eid] = $false
                                    }
                                }
                            }
                        }
                    }
                    $bv = [string]$c['budgetVersion']
                    if ($bv -and $bv -ne $budgetVersion) { Add-Defect 'budget-version-mismatch' "measured $kind '$key' was judged against budget '$bv', budgets.json is '$budgetVersion'" }
                    if ((Test-Filled $c['budget']) -and -not $budgetIds.Contains([string]$c['budget'])) { Add-Defect 'budget-version-mismatch' "measured $kind '$key' names budget item '$($c['budget'])', absent from budgets.json" }
                    switch -CaseSensitive ([string]$c['verdict']) {
                        'pass' { }
                        'fail' { Add-Defect 'budget-failed' "measured $kind '$key' failed its budget" }
                        'inconclusive' { $tally.inconclusive++; Add-Defect 'verdict-inconclusive' "measured $kind '$key' is inconclusive" }
                        default { if (Test-Filled $c['verdict']) { Add-Defect 'cell-malformed' "measured $kind '$key' has verdict '$($c['verdict'])'" } }
                    }
                    if ($null -ne $atomic -and $atomic['applicable'] -eq $false) { Add-Defect 'na-wrong' "scene cell '$key' is measured, but its atomic scene is declared not applicable" }
                }
                default { Add-Defect 'cell-malformed' "$kind '$key' has status '$status', expected measured, n/a or open" }
            }
        }
        $stats[$kind] = $list.Count
    }
    if ($tally.measured -gt 0 -and $budgetState -ne 'approved') {
        Add-Defect 'budget-not-approved' "$($tally.measured) measured cell(s) are judged against budgets.json state '$budgetState'; only an approved budget passes"
    }
    foreach ($gk in $groups.Keys) {
        $g = $groups[$gk]
        $absent = @($Properties | Where-Object { -not $g.Contains($_) })
        if ($absent.Count) { Add-Defect 'property-missing' "$($gk.Substring($gk.IndexOf('|') + 1)) lacks property $($absent -join ', ')" }
    }
    if ($null -ne $matrix) {
        foreach ($id in ($listed.Keys | Sort-Object)) {
            if ($live.Contains($id) -and -not $capsWithCells.Contains($id)) { Add-Defect 'capability-without-cells' "capability '$id' has no matrix cell" }
        }
        foreach ($u in $unscopedAtomics) {
            if (-not $atomicWithCells.Contains("$($u[0])|$($u[1])")) { Add-Defect 'scene-atomic-uncovered' "atomic scene '$($u[0])/$($u[1])' has no scene cell" }
        }
    }
    $stats['open'] = $tally.open; $stats['n/a'] = $tally['n/a']; $stats['measured'] = $tally.measured
    return $stats
}

function Invoke-ValidateMode {
    Write-CheckSubject -Axes ([ordered]@{ module = 'app_v2'; scope = 'dynamic-audit-coverage'; mode = 'validate'; files = (Get-RelPath $AuditRoot) })
    $stats = Invoke-CoverageValidation
    Write-Host ("{0}: {1}" -f $GateName, (($stats.Keys | ForEach-Object { "$_ $($stats[$_])" }) -join ', '))
    foreach ($b in $script:blockers) { Write-Host "  [could not verify] $b" }
    $findings = 0
    foreach ($cat in $script:defects.Keys) {
        $items = $script:defects[$cat]
        $findings += $items.Count
        Write-Host ("  [{0}] {1} finding(s)" -f $cat, $items.Count)
        if ($Quiet) { continue }
        $shown = if ($Limit -le 0) { $items.Count } else { [Math]::Min($Limit, $items.Count) }
        for ($i = 0; $i -lt $shown; $i++) { Write-Host "      $($items[$i])" }
        if ($shown -lt $items.Count) { Write-Host ("      .. {0} more (raise -Limit, 0 lists all)" -f ($items.Count - $shown)) }
    }
    if ($script:blockers.Count -gt 0) {
        $more = if ($script:blockers.Count -gt 1) { " (+$($script:blockers.Count - 1) more reason(s) above)" } else { '' }
        Write-Host ("assert-dynamic-audit-coverage: COULD NOT VERIFY - {0}{1}; {2} exit-1 finding(s) also listed." -f $script:blockers[0], $more, $findings)
        exit 2
    }
    if ($findings -gt 0) {
        Write-Host ("assert-dynamic-audit-coverage: FAIL - {0} finding(s) in {1} categor(ies); coverage is incomplete or a cell breaks the contract." -f $findings, $script:defects.Count)
        exit 1
    }
    Write-Host 'assert-dynamic-audit-coverage: PASS - complete coverage, every measured cell passes an approved budget, every n/a is justified.'
    exit 0
}

# --- self-test ------------------------------------------------------------------------------------

function Invoke-SelfTestMode {
    Write-CheckSubject -Axes ([ordered]@{ module = 'app_v2'; scope = 'dynamic-audit-coverage'; mode = 'selftest'; files = 'synthetic-fixtures' })
    $pwshPath = (Get-Process -Id $PID).Path
    $base = Join-Path ([System.IO.Path]::GetTempPath()) ('s4166-coverage-' + [guid]::NewGuid().ToString('N'))
    $utf8 = [System.Text.UTF8Encoding]::new($false)
    $ledgerRel = 'docs/ALL_FEATURES.jsonl'
    $privateLedgerRel = 'docs/ALL_FEATURES_noLegal.jsonl'
    $schemaRel = 'docs/ALL_FEATURES.schema.json'
    $flavorMatrixRel = 'docs/FLAVOR_MATRIX.md'
    $evidenceRel = 'temp/S4166/evidence/e1.txt'
    # Canary values are assembled at run time so no secret-shaped literal sits in this file.
    $canary = ('g' + 'hp_') + (-join [char[]](65..90)) + '0123456789'
    $driveRef = [string][char]67 + ':/fixture/evidence.txt'

    function Write-FixtureText([string]$Root, [string]$Rel, [string]$Text) {
        $path = Join-Path $Root $Rel
        $dir = Split-Path -Parent $path
        if (-not (Test-Path -LiteralPath $dir)) { $null = New-Item -ItemType Directory -Path $dir -Force }
        [System.IO.File]::WriteAllText($path, $Text, $utf8)
    }
    function Write-FixtureJson([string]$Root, [string]$Rel, $Data) { Write-FixtureText $Root $Rel (($Data | ConvertTo-Json -Depth 32) + "`n") }
    function Read-FixtureJson([string]$Root, [string]$Rel) { return Get-Content -LiteralPath (Join-Path $Root $Rel) -Raw | ConvertFrom-Json -AsHashtable -Depth 32 }
    function Get-AuditRel([string]$Name) { return "$AuditRel/$Name" }

    function Get-FixtureClaude([string]$Where) {
        $bullet = '- **Feature inventory**: `docs/ALL_FEATURES.jsonl` is the fixture inventory; noLegal-only records go to `docs/ALL_FEATURES_noLegal.jsonl`.'
        $s10 = if ($Where -eq '10') { $bullet } else { '- Nothing here.' }
        $s11 = if ($Where -eq '11') { $bullet } else { '- **Feature inventory**: the inventory lives somewhere else now.' }
        $s8 = '- **Flavors**: the grid is `docs/FLAVOR_MATRIX.md`.'
        return "# Fixture CLAUDE.md`n`n## 8. Project Structure`n$s8`n`n## 10. Strict Rules`n$s10`n`n## 11. Feature & UI Policies`n$s11`n`n## 12. Validation`n- End.`n"
    }

    function Get-LedgerLines {
        $rows = @(
            [ordered]@{ id = 'alpha.one'; area = 'Media Browsing'; name = 'Alpha one'; description = 'd'; flavors = @('standard', 'noLegal'); status = 'active' },
            [ordered]@{ id = 'beta.two'; area = 'File Operations'; name = 'Beta two'; description = 'd'; flavors = @('standard'); status = 'active' },
            [ordered]@{ id = 'gamma.gone'; area = 'Main Screen'; name = 'Gamma gone'; description = 'd'; flavors = @('standard'); status = 'removed' },
            [ordered]@{ id = 'tooling.helper'; area = 'General'; name = 'Helper'; description = 'd'; flavors = @('standard'); status = 'active' })
        return @($rows | ForEach-Object { $_ | ConvertTo-Json -Compress -Depth 4 })
    }

    function Update-FixtureManifest([string]$Root) {
        $m = Read-FixtureJson $Root (Get-AuditRel 'manifest.json')
        $sources = @()
        foreach ($rel in @($ledgerRel, 'CLAUDE.md', '.sza-profile.json')) {
            $sources += [ordered]@{ path = $rel; sha256 = (Get-LfSha256 (Join-Path $Root $rel)); required = $true }
        }
        $m['sources'] = $sources
        $lines = @([System.IO.File]::ReadAllLines((Join-Path $Root $ledgerRel)) | Where-Object { $_ })
        $active = @($lines | Where-Object { $_ -notmatch '"status":"removed"' }).Count
        $m['counts'] = [ordered]@{ publicRecords = $lines.Count; publicActive = $active; privateActive = 0; active = $active; excluded = 2 }
        Write-FixtureJson $Root (Get-AuditRel 'manifest.json') $m
    }

    function New-Cell([string]$Cap, [string]$Scene, [string]$Prop) {
        return [ordered]@{
            capability = $Cap; scene = $Scene; variant = 'standard'; context = 'en/touch/idle'; property = $Prop
            status = 'measured'; sampled = $true; run = 'r1'; method = 'external-probe'; metric = 'p95'; unit = 'ms'
            n = 120; budgetVersion = 'b1'; evidence = 'e1'; verdict = 'pass'
        }
    }

    function New-CoverageFixture([string]$Id) {
        $root = Join-Path $base $Id
        $null = New-Item -ItemType Directory -Path $root -Force
        Write-FixtureText $root 'CLAUDE.md' (Get-FixtureClaude '11')
        Write-FixtureJson $root '.sza-profile.json' ([ordered]@{ paths = [ordered]@{ allFeatures = $ledgerRel; allFeaturesPrivate = $privateLedgerRel; allFeaturesSchema = $schemaRel } })
        Write-FixtureText $root $ledgerRel (((Get-LedgerLines) -join "`n") + "`n")
        Write-FixtureText $root $evidenceRel "fixture evidence`n"
        $cells = @()
        foreach ($cap in @(@('alpha.one', 'C05.open'), @('beta.two', 'C07.copy'))) {
            foreach ($p in $Properties) { $cells += New-Cell $cap[0] $cap[1] $p }
        }
        $sceneCells = @(foreach ($p in $Properties) {
                [ordered]@{ scene = 'C04'; atomic = 'C04.rdp'; variant = 'standard'; context = 'en/touch/idle'; property = $p; status = 'n/a'
                    sampled = $false; reason = 'an RDP session is not an Android app lifecycle'; source = 'CLAUDE.md'; condition = 'product is android' }
            })
        Write-FixtureJson $root (Get-AuditRel 'matrix.json') ([ordered]@{ schemaVersion = 1; cells = $cells; sceneCells = $sceneCells })
        Write-FixtureJson $root (Get-AuditRel 'capabilities.json') ([ordered]@{ schemaVersion = 1; items = @(
                    [ordered]@{ id = 'alpha.one'; area = 'Media Browsing'; name = 'Alpha one'; flavors = @('standard', 'noLegal'); wearFlavors = $null; gate = $null; source = 'public'; scene = 'C05'; stratum = 's1'; sampled = $true },
                    [ordered]@{ id = 'beta.two'; area = 'File Operations'; name = 'Beta two'; flavors = @('standard'); wearFlavors = $null; gate = $null; source = 'public'; scene = 'C07'; stratum = 's2'; sampled = $true }) })
        Write-FixtureJson $root (Get-AuditRel 'scenes.json') ([ordered]@{ schemaVersion = 1; scenes = @(
                    [ordered]@{ id = 'C04'; title = 'RDP'; areas = @(); capabilityScoped = $false; atomic = @([ordered]@{ id = 'C04.rdp'; title = 'RDP connect'; variants = @('standard'); inputs = @(); states = @(); applicable = $false; naReason = 'not an Android lifecycle'; naSource = 'CLAUDE.md' }) },
                    [ordered]@{ id = 'C05'; title = 'Browse'; areas = @('Media Browsing'); capabilityScoped = $true; atomic = @([ordered]@{ id = 'C05.open'; title = 'Open folder'; variants = @('standard'); inputs = @('touch'); states = @('idle'); applicable = $true; naReason = $null; naSource = $null }) },
                    [ordered]@{ id = 'C07'; title = 'File ops'; areas = @('File Operations'); capabilityScoped = $true; atomic = @([ordered]@{ id = 'C07.copy'; title = 'Copy'; variants = @('standard'); inputs = @('touch'); states = @('idle'); applicable = $true; naReason = $null; naSource = $null }) }) })
        Write-FixtureJson $root (Get-AuditRel 'budgets.json') ([ordered]@{ schemaVersion = 1; version = 'b1'; state = 'approved'; items = @(
                    [ordered]@{ id = 'key-response'; metric = 'p95'; unit = 'ms'; p95 = 100; p99 = 200; bound = 'max'; condition = 'warm'; appliesTo = @('O') }) })
        Write-FixtureJson $root (Get-AuditRel 'runs.json') ([ordered]@{ schemaVersion = 1; runs = @(
                    [ordered]@{ runId = 'r1'; scene = 'C05'; variant = 'standard'; build = [ordered]@{ package = 'fixture.pkg'; versionName = '1'; versionCode = 1; apkSha256 = 'none' }
                        device = [ordered]@{ kind = 'emulator'; avd = 'fixture'; api = 36 }; valid = $true; invalidReason = $null; startedAt = '2026-10-11T00:00:00Z'; cycles = 100; steadyMinutes = 60; calibration = 'fixture' }) })
        Write-FixtureJson $root (Get-AuditRel 'evidence.json') ([ordered]@{ schemaVersion = 1; evidence = @(
                    [ordered]@{ id = 'e1'; uri = $evidenceRel; sha256 = (Get-RawSha256 (Join-Path $root $evidenceRel)) }) })
        Write-FixtureJson $root (Get-AuditRel 'manifest.json') ([ordered]@{
                schemaVersion = 1; product = 'android'; generatedBy = 'self-test'; generatedAt = '2026-10-11T00:00:00Z'
                sources = @()
                pointers = @(
                    [ordered]@{ from = 'CLAUDE.md'; token = $ledgerRel }, [ordered]@{ from = 'CLAUDE.md'; token = $privateLedgerRel },
                    [ordered]@{ from = '.sza-profile.json'; token = $ledgerRel }, [ordered]@{ from = '.sza-profile.json'; token = $privateLedgerRel },
                    [ordered]@{ from = 'CLAUDE.md'; token = $flavorMatrixRel })
                privateLedger = [ordered]@{ declared = $true; present = $false; count = 0; sha256 = $null }
                counts = [ordered]@{}
                exclusions = @([ordered]@{ id = 'gamma.gone'; reason = 'removed' }, [ordered]@{ id = 'tooling.helper'; reason = 'developer-tooling' })
                duplicateNameGroups = @()
                floor = [ordered]@{ activeMin = 2 }
                selectionRule = [ordered]@{ stratum = 'area+reach'; representative = 'lowest-id'; variantPriority = @('standard', 'noLegal', 'vr'); locales = @('en'); rtlWalk = 'ar' }
            })
        Update-FixtureManifest $root
        return $root
    }

    function Edit-Matrix([string]$Root, [scriptblock]$Change) {
        $m = Read-FixtureJson $Root (Get-AuditRel 'matrix.json')
        & $Change $m
        Write-FixtureJson $Root (Get-AuditRel 'matrix.json') $m
    }
    function Edit-Audit([string]$Root, [string]$Name, [scriptblock]$Change) {
        $d = Read-FixtureJson $Root (Get-AuditRel $Name)
        & $Change $d
        Write-FixtureJson $Root (Get-AuditRel $Name) $d
    }

    $cases = @(
        @{ Id = 'G00'; Title = 'complete green matrix'; Expect = 0; Contains = @('coverage: PASS'); Mutate = $null },
        @{ Id = 'F01'; Title = 'one open cell'; Expect = 1; Contains = @('cell-open'); Mutate = {
                param($r) Edit-Matrix $r { param($m) $c = $m['cells'][0]; $c['status'] = 'open'; $c['reason'] = 'not run'; $c['owner'] = 'S4166'; $c['nextStep'] = 'run it' } } },
        @{ Id = 'F02'; Title = 'missing property U'; Expect = 1; Contains = @('property-missing'); Mutate = {
                param($r) Edit-Matrix $r { param($m) $m['cells'] = @($m['cells'] | Where-Object { -not ($_['capability'] -eq 'beta.two' -and $_['property'] -eq 'U') }) } } },
        @{ Id = 'F03'; Title = 'budget failure'; Expect = 1; Contains = @('budget-failed'); Mutate = {
                param($r) Edit-Matrix $r { param($m) $m['cells'][1]['verdict'] = 'fail' } } },
        @{ Id = 'F04'; Title = 'measured cell without evidence'; Expect = 1; Contains = @('measured-without-evidence'); Mutate = {
                param($r) Edit-Matrix $r { param($m) $m['cells'][2].Remove('evidence') } } },
        @{ Id = 'F05'; Title = 'evidence file hash mismatch'; Expect = 1; Contains = @('evidence-hash-mismatch'); Mutate = {
                param($r) Write-FixtureText $r $evidenceRel "tampered evidence`n" } },
        @{ Id = 'F06'; Title = 'wrong n/a on a reachable variant'; Expect = 1; Contains = @('na-wrong'); Mutate = {
                param($r) Edit-Matrix $r { param($m) $c = $m['cells'][0]; $c['status'] = 'n/a'; $c['reason'] = 'no tool'; $c['source'] = 'CLAUDE.md'; $c['condition'] = 'always' } } },
        @{ Id = 'F07'; Title = 'n/a without a condition'; Expect = 1; Contains = @('na-incomplete'); Mutate = {
                param($r) Edit-Matrix $r { param($m) $m['sceneCells'][0].Remove('condition') } } },
        @{ Id = 'F08'; Title = 'duplicate capability id'; Expect = 1; Contains = @('capability-duplicate'); Mutate = {
                param($r) Edit-Audit $r 'capabilities.json' { param($d) $d['items'] = @($d['items']) + @($d['items'][0]) } } },
        @{ Id = 'F09'; Title = 'duplicate cell key'; Expect = 1; Contains = @('cell-duplicate-key'); Mutate = {
                param($r) Edit-Matrix $r { param($m) $m['cells'] = @($m['cells']) + @($m['cells'][0]) } } },
        @{ Id = 'F10'; Title = 'unknown capability id in the matrix'; Expect = 1; Contains = @('cell-unknown-capability'); Mutate = {
                param($r) Edit-Matrix $r { param($m) $m['cells'] = @($m['cells']) + @(New-Cell 'zeta.ghost' 'C05.open' 'O') } } },
        @{ Id = 'F11'; Title = 'inconclusive verdict'; Expect = 1; Contains = @('verdict-inconclusive'); Mutate = {
                param($r) Edit-Matrix $r { param($m) $m['cells'][3]['verdict'] = 'inconclusive' } } },
        @{ Id = 'F12'; Title = 'mutation: inventory gains a capability, matrix unchanged'; Expect = 1; Contains = @('delta.new'); Mutate = {
                param($r)
                $line = [ordered]@{ id = 'delta.new'; area = 'Sharing'; name = 'Delta'; description = 'd'; flavors = @('standard'); status = 'active' } | ConvertTo-Json -Compress
                Write-FixtureText $r $ledgerRel (((Get-LedgerLines) + $line -join "`n") + "`n") } },
        @{ Id = 'F13'; Title = 'mutation with a rebuilt manifest: still uncovered'; Expect = 1; Contains = @('capability-uncovered', 'delta.new'); Mutate = {
                param($r)
                $line = [ordered]@{ id = 'delta.new'; area = 'Sharing'; name = 'Delta'; description = 'd'; flavors = @('standard'); status = 'active' } | ConvertTo-Json -Compress
                Write-FixtureText $r $ledgerRel (((Get-LedgerLines) + $line -join "`n") + "`n")
                Update-FixtureManifest $r } },
        @{ Id = 'F14'; Title = 'count below the manifest floor'; Expect = 1; Contains = @('count-below-floor'); Mutate = {
                param($r) Edit-Audit $r 'manifest.json' { param($d) $d['floor']['activeMin'] = 10 } } },
        @{ Id = 'F15'; Title = 'secret-shaped evidence reference'; Expect = 1; Contains = @('evidence-secret-shaped', 'not followed'); NotContains = @($canary); Mutate = {
                param($r) Edit-Audit $r 'evidence.json' { param($d) $d['evidence'][0]['uri'] = 'temp/S4166/evidence/' + $canary } } },
        @{ Id = 'F16'; Title = 'path traversal evidence uri'; Expect = 1; Contains = @('evidence-unsafe', 'path traversal', 'not followed'); Mutate = {
                param($r)
                $outside = Join-Path (Split-Path -Parent $r) ('outside-' + (Split-Path -Leaf $r) + '.txt')
                [System.IO.File]::WriteAllText($outside, "fixture evidence`n", $utf8)
                $sha = Get-RawSha256 $outside
                Edit-Audit $r 'evidence.json' { param($d) $d['evidence'][0]['uri'] = 'temp/../../' + (Split-Path -Leaf $outside); $d['evidence'][0]['sha256'] = $sha } } },
        @{ Id = 'F17'; Title = 'drive-letter evidence uri'; Expect = 1; Contains = @('evidence-unsafe', 'drive letter', 'not followed'); Mutate = {
                param($r) Edit-Audit $r 'evidence.json' { param($d) $d['evidence'][0]['uri'] = $driveRef } } },
        @{ Id = 'X01'; Title = 'damaged source: matrix.json not JSON'; Expect = 2; Contains = @('does not parse'); Mutate = {
                param($r) Write-FixtureText $r (Get-AuditRel 'matrix.json') '{ "schemaVersion": 1, "cells": [ ' } },
        @{ Id = 'X02'; Title = 'absent source: capabilities.json'; Expect = 2; Contains = @('capabilities.json is absent'); Mutate = {
                param($r) Remove-Item -LiteralPath (Join-Path $r (Get-AuditRel 'capabilities.json')) } },
        @{ Id = 'X03'; Title = 'damaged inventory line'; Expect = 2; Contains = @('inventory unreadable'); Mutate = {
                param($r) Write-FixtureText $r $ledgerRel (((Get-LedgerLines) + '{"id":"broken.line",' -join "`n") + "`n") } },
        @{ Id = 'X04'; Title = 'unsupported schema version'; Expect = 2; Contains = @('unsupported schema'); Mutate = {
                param($r) Edit-Audit $r 'scenes.json' { param($d) $d['schemaVersion'] = 2 } } },
        @{ Id = 'X05'; Title = 'invalid probe run'; Expect = 2; Contains = @('invalid run'); Mutate = {
                param($r) Edit-Audit $r 'runs.json' { param($d) $d['runs'][0]['valid'] = $false; $d['runs'][0]['invalidReason'] = 'scheduler lateness p99 over 10 ms' } } },
        @{ Id = 'X06'; Title = 'restore pending'; Expect = 2; Contains = @('restore pending'); Mutate = {
                param($r) Write-FixtureJson $r "$StateRel/restore-pending.json" ([ordered]@{ runId = 'r1'; state = 'pending' }) } },
        @{ Id = 'X07'; Title = 'restore failure left its checkpoint'; Expect = 2; Contains = @('state: failed'); Mutate = {
                param($r) Write-FixtureJson $r "$StateRel/restore-pending.json" ([ordered]@{ runId = 'r1'; state = 'failed' }) } },
        @{ Id = 'X08'; Title = 'unverified backup manifest'; Expect = 2; Contains = @('backup unverified'); Mutate = {
                param($r) Write-FixtureJson $r "$StateRel/r1/backup-manifest.json" ([ordered]@{ runId = 'r1'; verified = $false }) } },
        @{ Id = 'X09'; Title = 'pointer removed from CLAUDE.md'; Expect = 2; Contains = @('pointer removed'); Mutate = {
                param($r) Write-FixtureText $r 'CLAUDE.md' (Get-FixtureClaude 'none') } },
        @{ Id = 'X10'; Title = 'pointer moved out of section 11'; Expect = 2; Contains = @('pointer removed'); Mutate = {
                param($r) Write-FixtureText $r 'CLAUDE.md' (Get-FixtureClaude '10') } },
        @{ Id = 'X11'; Title = 'private ledger declared present, absent on disk'; Expect = 2; Contains = @('declares the private ledger present'); Mutate = {
                param($r) Edit-Audit $r 'manifest.json' { param($d) $d['privateLedger']['present'] = $true } } },
        @{ Id = 'X12'; Title = 'priority: open cell plus invalid run keeps both reasons'; Expect = 2; Contains = @('invalid run', 'cell-open'); Mutate = {
                param($r)
                Edit-Matrix $r { param($m) $c = $m['cells'][0]; $c['status'] = 'open'; $c['reason'] = 'not run'; $c['owner'] = 'S4166'; $c['nextStep'] = 'run it' }
                Edit-Audit $r 'runs.json' { param($d) $d['runs'][0]['valid'] = $false; $d['runs'][0]['invalidReason'] = 'sampler gaps' } } }
    )

    $matched = 0
    $mismatched = 0
    try {
        $null = New-Item -ItemType Directory -Path $base -Force
        foreach ($case in $cases) {
            $root = New-CoverageFixture $case.Id
            if ($case.Mutate) { & $case.Mutate $root }
            $output = (& $pwshPath -NoProfile -File $PSCommandPath -RepoRoot $root -Limit 50 2>&1 | Out-String)
            $actual = $LASTEXITCODE
            $notes = [System.Collections.Generic.List[string]]::new()
            foreach ($s in @($case['Contains'] | Where-Object { $_ })) { if (-not $output.Contains($s)) { $notes.Add("output lacks '$s'") } }
            foreach ($s in @($case['NotContains'] | Where-Object { $_ })) { if ($output.Contains($s)) { $notes.Add('output leaks the canary') } }
            $ok = ($actual -eq $case.Expect) -and $notes.Count -eq 0
            if ($ok) { $matched++ } else { $mismatched++ }
            $word = if ($ok) { 'MATCH' } else { 'MISMATCH' }
            Write-Host ("  [{0}] {1} - expected: {2} | actual: {3} - {4}" -f $case.Id, $case.Title, $case.Expect, $actual, $word)
            if (-not $ok) {
                foreach ($n in $notes) { Write-Host "      $n" }
                Write-Host (($output.Replace($canary, '<canary>') -split "`n" | Select-Object -First 30 | ForEach-Object { "      | $_" }) -join "`n")
            }
        }
    } finally {
        Remove-Item -LiteralPath $base -Recurse -Force -ErrorAction SilentlyContinue
    }

    if ($mismatched -gt 0) {
        Write-Host ("assert-dynamic-audit-coverage: FAIL - self-test: {0} of {1} fixture(s) did not return their expected code." -f $mismatched, $cases.Count)
        exit 1
    }
    Write-Host ("assert-dynamic-audit-coverage: PASS - self-test: all {0} fixture(s) returned their expected code." -f $matched)
    exit 0
}

if ($SelfTest) { Invoke-SelfTestMode }
try {
    Invoke-ValidateMode
} catch {
    # A validator that crashed judged nothing; under Stop it would otherwise surface as 1 and read as FAIL.
    Write-Host ("assert-dynamic-audit-coverage: COULD NOT VERIFY - validator error at line {0}: {1}" -f $_.InvocationInfo.ScriptLineNumber, $_.Exception.Message)
    exit 2
}
