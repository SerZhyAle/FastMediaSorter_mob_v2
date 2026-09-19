# play-vitals-filing.ps1 (S2917) - file one Draft ticket per red Play vitals finding. Dot-source it.
#
# Two halves, so the dedup decision is tested without touching any catalog:
#
#   Get-PlayVitalsFilingPlan  - pure: findings + catalog records -> one action per finding key
#   Invoke-PlayVitalsFiling   - executes the plan through the catalog CLI of -ProjectRoot
#
# One finding key maps to one deterministic catalog name, and the name is the whole dedup: an active
# record under that name either takes the new evidence (it is still in PLAN/RELEASE_QUEUE.md) or is
# left alone (it sits in PLAN/RELEASE_READY.md - Implemented, Verified, BlockNeedUserTest - with its
# verdict pending; the release archives it, after which the next red run files a fresh ticket against
# the released version). No separate registry exists to drift from the catalog (S2917 research 5).
#
# Every write goes through scripts/spec_catalog/insert.ps1 - never into the catalog journal itself - and
# the spec body comes from .claude/templates/strategic-spec.md the way /spec-draft step 5 renders it.
#
# S3286 adds a SECOND dedup key beside the name, because the name one is blind in exactly one place: once
# the release archives a vitals ticket, the crash behind it is still reported by Play until a build carrying
# the fix has been in the field, so the next red run files it again. The crash-cluster id is the only exact
# key the two reads share, and docs/play-vitals-cluster-registry.jsonl is where it is written down.

Set-StrictMode -Version Latest

. (Join-Path $PSScriptRoot 'play-vitals-cluster-registry.ps1')

$script:PlayVitalsQueueStatuses = @(
    'Draft', 'Approved', 'Tactical', 'In Progress', 'Partial', 'Broken',
    'BlockQuestions', 'BlockExternal', 'BlockByOtherTask'
)
$script:PlayVitalsReadyStatuses = @('Implemented', 'Verified', 'BlockNeedUserTest')
$script:PlayVitalsEvidenceHeading = '**Evidence log** (one line per run while the finding persists):'
$script:PlayVitalsNameLimit = 60

function Get-PlayVitalsTicketName {
    param([Parameter(Mandatory)] [string] $Key)
    $rest = $Key -replace '^play-vitals:', ''
    # camelCase metric names become words, so the name reads "user-perceived-crash-rate".
    $spaced = $rest -creplace '([a-z0-9])([A-Z])', '$1-$2'
    $slug = (($spaced.ToLowerInvariant()) -replace '[^a-z0-9]+', '-').Trim('-')
    $name = "play-vitals-$slug"
    if ($name.Length -le $script:PlayVitalsNameLimit) { return $name }
    $sha = [System.Security.Cryptography.SHA256]::HashData([System.Text.Encoding]::UTF8.GetBytes($Key))
    $hash = ([System.BitConverter]::ToString($sha) -replace '-', '').Substring(0, 8).ToLowerInvariant()
    $room = $script:PlayVitalsNameLimit - $hash.Length - 1
    $cut = $name.Substring(0, $room)
    $lastHyphen = $cut.LastIndexOf('-')
    if ($lastHyphen -gt 'play-vitals'.Length) { $cut = $cut.Substring(0, $lastHyphen) }
    return "$($cut.TrimEnd('-'))-$hash"
}

function Get-PlayVitalsClusterOwners {
    [CmdletBinding()]
    param(
        [AllowEmptyCollection()] [AllowNull()] [object[]] $ErrorIssues,
        [AllowEmptyCollection()] [AllowNull()] [object[]] $ClusterRegistry
    )
    # Two counts and the owners, so the caller can tell "every reported cluster is owned" from "some are".
    $owners = [System.Collections.Generic.List[object]]::new()
    $total = 0
    foreach ($issue in $ErrorIssues) {
        if ($null -eq $issue) { continue }
        $clusterId = "$(Get-ClusterRegistryField -Entry $issue -Name 'clusterId')".Trim()
        if (-not $clusterId) { continue }
        $total++
        $entry = Find-ClusterRegistryEntry -Registry $ClusterRegistry -ClusterId $clusterId
        if ($null -ne $entry) { $owners.Add($entry) }
    }
    return [pscustomobject] @{ Total = $total; Owners = $owners.ToArray() }
}

function Get-PlayVitalsFilingPlan {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)] [AllowEmptyCollection()] [object[]] $Findings,
        [Parameter(Mandatory)] [AllowEmptyCollection()] [object[]] $Records,
        [AllowEmptyCollection()] [AllowNull()] [object[]] $ClusterRegistry = @(),
        [AllowEmptyCollection()] [AllowNull()] [object[]] $ErrorIssues = @()
    )
    # Cluster pre-pass (S3286). A finding is refused only when the snapshot reported at least one error
    # issue and EVERY one of them is already owned by a ticket: a single unowned cluster means the red band
    # carries something nobody has looked at, and that ticket must still be filed.
    $clusters = Get-PlayVitalsClusterOwners -ErrorIssues $ErrorIssues -ClusterRegistry $ClusterRegistry
    $allClustersOwned = $clusters.Total -gt 0 -and $clusters.Owners.Count -eq $clusters.Total
    $clusterReason = ''
    if ($allClustersOwned) {
        $first = $clusters.Owners[0]
        $clusterReason = "every reported crash cluster is already registered (e.g. $(Get-ClusterRegistryField -Entry $first -Name 'clusterId') -> $(Get-ClusterRegistryField -Entry $first -Name 'ticketId'), $(Get-ClusterRegistryField -Entry $first -Name 'ticketStatus'), state $(Get-ClusterRegistryField -Entry $first -Name 'state'))"
    }
    $plan = [System.Collections.Generic.List[object]]::new()
    $seen = @{}
    foreach ($finding in $Findings) {
        if ($null -eq $finding -or $finding.Color -ne 'red') { continue }
        if ($seen.ContainsKey($finding.Key)) { continue }
        $seen[$finding.Key] = $true
        $name = Get-PlayVitalsTicketName -Key $finding.Key
        $record = @($Records | Where-Object { $null -ne $_ -and $_.name -eq $name -and $_.status -ne 'Archived' }) |
            Select-Object -First 1
        $action = 'file'
        $reason = 'no active ticket carries this finding'
        if ($null -ne $record) {
            if ($script:PlayVitalsQueueStatuses -contains $record.status) {
                $action = 'append'
                $reason = "open ticket in status $($record.status)"
            } elseif ($script:PlayVitalsReadyStatuses -contains $record.status) {
                $action = 'skip'
                $reason = "ticket is $($record.status) - its verdict is pending and the release archives it"
            } else {
                $action = 'skip'
                $reason = "ticket status '$($record.status)' is not one this filer knows"
            }
        }
        # An 'append' is never downgraded: an open ticket still needs its evidence line.
        if ($action -eq 'file' -and $allClustersOwned) {
            $action = 'skip'
            $reason = $clusterReason
        }
        $plan.Add([pscustomobject] @{
            Action       = $action
            Key          = $finding.Key
            Name         = $name
            Finding      = $finding
            RecordId     = if ($record) { $record.id } else { $null }
            RecordStatus = if ($record) { $record.status } else { $null }
            RecordFile   = if ($record) { $record.file } else { $null }
            Reason       = $reason
        })
    }
    return $plan.ToArray()
}

function Format-PlayVitalsNumber($Value) {
    if ($null -eq $Value -or "$Value" -eq '') { return '-' }
    if ($Value -is [string]) { return $Value }
    return ([double] $Value).ToString('0.######', [System.Globalization.CultureInfo]::InvariantCulture)
}

function Get-PlayVitalsEvidenceLine {
    param($Finding, [string] $Date)
    return ('- {0} - value {1} (band {2}, {3}), {4} distinct users, window {5}.' -f $Date,
        (Format-PlayVitalsNumber $Finding.Value), (Format-PlayVitalsNumber $Finding.Threshold), $Finding.Color,
        (Format-PlayVitalsNumber $Finding.DistinctUsers), $Finding.Window)
}

function Get-PlayVitalsEvidenceText {
    param($Finding, $Snapshot, [int] $TopIssues, [string] $MeasuredDate)
    $lines = [System.Collections.Generic.List[string]]::new()
    $lines.Add("**Finding key:** ``$($Finding.Key)``")
    $lines.Add('')
    $lines.Add('Filed automatically by `scripts/release/watch-play-vitals.ps1` (S2917) on a red band. No model took part in detecting it.')
    $lines.Add('')
    $lines.Add(('- Metric: `{0}` - value {1} against band {2} ({3})' -f $Finding.Metric, (Format-PlayVitalsNumber $Finding.Value),
        (Format-PlayVitalsNumber $Finding.Threshold), $Finding.Color))
    $lines.Add("- Scope: $($Finding.Scope)")
    $lines.Add("- Window: $($Finding.Window); measured $MeasuredDate (UTC)")
    $lines.Add("- versionCodes with users in the window: $(@($Finding.VersionCodes) -join ', ')")
    $lines.Add("- Distinct users: $(Format-PlayVitalsNumber $Finding.DistinctUsers)")
    if ($Finding.Detail) { $lines.Add("- Detail: $($Finding.Detail)") }
    $issues = @($Snapshot.errorIssues | Where-Object { $null -ne $_ } | Select-Object -First $TopIssues)
    if ($issues.Count -gt 0) {
        $lines.Add('- Top error issues by distinct users:')
        foreach ($issue in $issues) {
            $lines.Add(('  - `{0}` {1} at `{2}` - {3} users, {4} reports, last versionCode {5} - [console]({6})' -f $issue.type,
                $issue.cause, $issue.location, $issue.distinctUsers, $issue.errorReportCount, $issue.lastAppVersionCode, $issue.issueUri))
        }
    } else {
        $lines.Add('- Top error issues: none reported in the window.')
    }
    $lines.Add('')
    $lines.Add($script:PlayVitalsEvidenceHeading)
    $lines.Add('')
    $lines.Add((Get-PlayVitalsEvidenceLine -Finding $Finding -Date $MeasuredDate))
    return $lines
}

function ConvertTo-PlayVitalsSpec {
    param([string] $Template, [string] $Id, [string] $Name, $Finding, [string[]] $Evidence, [string] $Today)
    $text = $Template -replace "`r`n", "`n"
    # The template opens with authoring notes for the agent that renders it; a filed ticket starts at its title.
    $titleAt = $text.IndexOf("`n# ")
    if ($text.StartsWith('# ')) { $titleAt = -1 }
    if ($titleAt -ge 0) { $text = $text.Substring($titleAt + 1) }
    $title = "Android vitals: красная полоса - $($Finding.Metric), $($Finding.Scope)"
    $text = $text.Replace('<Sxxxx>', $Id).Replace('<short-name>', $Name).Replace('<Название фичи>', $title)
    $text = $text -replace '(?m)^\*\*Priority:\*\*.*$', '**Priority:** 90'
    $text = $text -replace '(?m)^\*\*Tier:\*\*.*$', '**Tier:** 2 - Easy (ad-hoc)'
    $text = $text -replace '(?m)^\*\*Roadmap entry:\*\*.*$', "**Roadmap entry:** Ad-hoc - filed by watch-play-vitals.ps1 (S2917) on $Today"
    $text = $text.Replace('<YYYY-MM-DD>', $Today)
    $text = $text.Replace('<вербатим-текст пользователя, без переписывания; или «нет текста»>', ($Evidence -join "`n"))
    # No attachments: the evidence is the text itself, and the console links point at the rest.
    $text = [regex]::Replace($text, '(?s)\*\*Вложения:\*\*.*?(?=\n---)', '')
    $text = [regex]::Replace($text, "`n{3,}", "`n`n")
    return $text
}

function Add-PlayVitalsEvidence {
    param([string] $Path, [string] $Line, [string] $Date)
    $raw = [System.IO.File]::ReadAllText($Path)
    $eol = if ($raw -match "`r`n") { "`r`n" } else { "`n" }
    $text = $raw -replace "`r`n", "`n"
    if ($text.Contains("`n- $Date - ")) { return 'already recorded today' }
    $sectionAt = $text.IndexOf("`n## 0.")
    if ($sectionAt -lt 0) { return 'the ticket has no section 0 to append to' }
    $sectionEnd = $text.IndexOf("`n---", $sectionAt + 1)
    if ($sectionEnd -lt 0) { $sectionEnd = $text.Length }
    $head = $text.Substring(0, $sectionEnd).TrimEnd("`n")
    $next = $head + "`n" + $Line + "`n" + $text.Substring($sectionEnd)
    [System.IO.File]::WriteAllText($Path, ($next -replace "`n", $eol), [System.Text.UTF8Encoding]::new($false))
    return $null
}

function Invoke-PlayVitalsCatalogCli {
    param([string] $ProjectRoot, [string] $Script, [string[]] $Arguments)
    $pwshExe = [Diagnostics.Process]::GetCurrentProcess().MainModule.FileName
    $path = Join-Path $ProjectRoot "scripts/spec_catalog/$Script"
    if (-not (Test-Path -LiteralPath $path)) { throw "play-vitals-filing: catalog CLI not found - $path" }
    $output = & $pwshExe -NoProfile -File $path @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "play-vitals-filing: $Script exited $LASTEXITCODE - $(@($output | ForEach-Object { "$_" }) -join ' | ')"
    }
    return @($output | Where-Object { $_ -isnot [System.Management.Automation.ErrorRecord] } | ForEach-Object { "$_" })
}

function Register-PlayVitalsClusters {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)] [string] $ProjectRoot,
        [AllowEmptyCollection()] [AllowNull()] [object[]] $ErrorIssues,
        [Parameter(Mandatory)] [string] $TicketId,
        [string] $TicketStatus = 'Draft'
    )
    # Written at filing time and nowhere else: a registry populated later would leave exactly the window
    # this ticket exists to close - the read that happens between the fix and the release.
    $registryPath = Get-ClusterRegistryPath -ProjectRoot $ProjectRoot
    $added = 0
    foreach ($issue in $ErrorIssues) {
        if ($null -eq $issue) { continue }
        $clusterId = "$(Get-ClusterRegistryField -Entry $issue -Name 'clusterId')".Trim()
        if (-not $clusterId) { continue }
        $existing = Find-ClusterRegistryEntry -Registry (Read-ClusterRegistry -Path $registryPath) -ClusterId $clusterId
        if ($null -ne $existing) { continue }
        $null = Add-ClusterRegistryEntry -Path $registryPath -Entry @{
            clusterId    = $clusterId
            ticketId     = $TicketId
            ticketStatus = $TicketStatus
            state        = 'open'
        }
        $added++
    }
    return $added
}

function Invoke-PlayVitalsFiling {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)] [AllowEmptyCollection()] [object[]] $Plan,
        [Parameter(Mandatory)] [string] $ProjectRoot,
        [Parameter(Mandatory)] $Snapshot,
        [int] $TopIssues = 5,
        [Parameter(Mandatory)] [string] $MeasuredDate,
        [AllowEmptyCollection()] [AllowNull()] [object[]] $ErrorIssues = @(),
        [string] $Today = ([DateTime]::UtcNow.ToString('yyyy-MM-dd'))
    )
    if ($null -eq $ErrorIssues -or $ErrorIssues.Count -eq 0) {
        $issueProperty = $Snapshot.PSObject.Properties['errorIssues']
        $ErrorIssues = if ($null -eq $issueProperty) { @() } else { @($issueProperty.Value | Where-Object { $null -ne $_ }) }
    }
    $results = [System.Collections.Generic.List[object]]::new()
    foreach ($item in $Plan) {
        switch ($item.Action) {
            'file' {
                $templatePath = Join-Path $ProjectRoot '.claude/templates/strategic-spec.md'
                if (-not (Test-Path -LiteralPath $templatePath)) { throw "play-vitals-filing: template not found - $templatePath" }
                # -Slug lets insert.ps1 allocate the id and build the path under one catalog lock, so
                # two filers racing cannot both take the id next-id.ps1 would have printed.
                $out = Invoke-PlayVitalsCatalogCli -ProjectRoot $ProjectRoot -Script 'insert.ps1' -Arguments @(
                    '-Name', $item.Name, '-Slug', $item.Name, '-Status', 'Draft', '-Tier', '2', '-Priority', '90')
                $id = ([string] ($out | Select-Object -Last 1)).Trim()
                if ($id -notmatch '^S\d{4}$') { throw "play-vitals-filing: insert.ps1 returned '$id', not a ticket id" }
                $evidence = Get-PlayVitalsEvidenceText -Finding $item.Finding -Snapshot $Snapshot -TopIssues $TopIssues -MeasuredDate $MeasuredDate
                $template = [System.IO.File]::ReadAllText($templatePath)
                $spec = ConvertTo-PlayVitalsSpec -Template $template -Id $id -Name $item.Name -Finding $item.Finding -Evidence $evidence -Today $Today
                $specPath = Join-Path $ProjectRoot "PLAN/${id}_$($item.Name).md"
                [System.IO.File]::WriteAllText($specPath, $spec, [System.Text.UTF8Encoding]::new($false))
                $null = Register-PlayVitalsClusters -ProjectRoot $ProjectRoot -ErrorIssues $ErrorIssues -TicketId $id -TicketStatus 'Draft'
                $results.Add([pscustomobject] @{ Action = 'filed'; Id = $id; Name = $item.Name; Note = $item.Reason })
            }
            'append' {
                $specPath = Join-Path $ProjectRoot $item.RecordFile
                if (-not (Test-Path -LiteralPath $specPath)) {
                    $results.Add([pscustomobject] @{ Action = 'skipped'; Id = $item.RecordId; Name = $item.Name; Note = "spec file missing - $($item.RecordFile)" })
                    continue
                }
                $line = Get-PlayVitalsEvidenceLine -Finding $item.Finding -Date $MeasuredDate
                $why = Add-PlayVitalsEvidence -Path $specPath -Line $line -Date $MeasuredDate
                # The clusters belong to the ticket that took the evidence, whichever run first saw them.
                $null = Register-PlayVitalsClusters -ProjectRoot $ProjectRoot -ErrorIssues $ErrorIssues `
                    -TicketId $item.RecordId -TicketStatus $item.RecordStatus
                if ($why) {
                    $results.Add([pscustomobject] @{ Action = 'skipped'; Id = $item.RecordId; Name = $item.Name; Note = $why })
                } else {
                    $results.Add([pscustomobject] @{ Action = 'appended'; Id = $item.RecordId; Name = $item.Name; Note = $item.Reason })
                }
            }
            default {
                $results.Add([pscustomobject] @{ Action = 'skipped'; Id = $item.RecordId; Name = $item.Name; Note = $item.Reason })
            }
        }
    }
    return $results.ToArray()
}
