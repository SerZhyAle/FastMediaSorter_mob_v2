#requires -Version 7.0
<#
.SYNOPSIS
    S3151: capture a Draft ticket from a slug and the owner's verbatim text in one call.

.DESCRIPTION
    /spec-draft used to read a template, fill its frontmatter, allocate an id, insert the record,
    copy attachments and journal the change by hand - seven agent steps for what is a mechanical
    capture. This script is all of them; the agent only chooses the slug.

    The text lands byte-for-byte: it is substituted last and with String.Replace, so a `$`, a
    `{{ID}}` token or a regex metacharacter inside it is written as typed. The file is UTF-8
    without a BOM.

    A bugfix-/hotfix- slug takes .claude/templates/compact-bugfix-spec.md, any other slug
    .claude/templates/draft-spec.md. The id is allocated inside insert.ps1's catalog lock
    (-Slug), so two concurrent captures cannot be handed the same id.

.PARAMETER Slug
    Kebab-case ticket name, [a-z0-9][a-z0-9-]*.

.PARAMETER Text
    The owner's text. Exactly one of -Text and -TextFile.

.PARAMETER TextFile
    UTF-8 file holding the owner's text - the safe form for multi-line text or quotes.

.PARAMETER Attach
    Semicolon-separated files copied to PLAN/<id>_<slug>/attachments/NN__<name>.

.PARAMETER Tier
    0..4; defaults to 3.

.PARAMETER Priority
    0..100; defaults to 95 for hotfix-, 90 for bugfix-, 50 otherwise.

.PARAMETER DedupQuery
    Symptom searched in the catalog first; up to five hits print as `dedup:` lines. With -WhatIf
    nothing is created, so the caller can decide on the hits.

    A hit whose status is CLOSED - the list is `grammar.closedStatuses` in .sza-profile.json - stops
    the capture with exit 3 and names that ticket, because the defect behind the symptom is already
    fixed in the tree and the capture would be the sixth duplicate of a ticket archived the same day.
    A hit that is still open only prints, as before.

.PARAMETER AllowClosedDuplicate
    Capture anyway after a closed-status hit. The escape for a genuine regression: the old defect
    really did come back, and the new ticket is a report rather than a duplicate.

.PARAMETER Verify
    Verification level written as the `**Verify:**` header line under `**Tier:**`, in the form
    `<edit|build|emulator|device> - <one-line reason>` (.claude/rules/spec-catalog.md
    "Verification level"). `/spec-all` routes an `edit`/`build` Draft straight to its Trivial Path,
    so a batch whose findings already carry file, line and fix direction is not researched again
    (S3782). Omitted -> no line, the pre-existing routing.

.PARAMETER Complexity
    Complexity level written as the `**Complexity:**` header line right after `**Verify:**` (after
    `**Tier:**` when there is none), in the form `<light|medium|hard> - <one-line reason>`
    (.claude/rules/spec-catalog.md "Complexity level"). The queue runner maps it to Haiku, Sonnet or
    Opus for the NEXT pass (S4193). Always written: omitted -> `medium - not assessed at capture`, so
    no captured ticket is without a level and the first pass's research is what confirms or revises it.

.PARAMETER AppendToOpen
    Before creating anything, look for an open ticket - status Draft or Approved - named -Slug or
    -Slug-<n>. When one exists, the text is appended to its inbox block under a dated
    `**Дописано:**` marker and no ticket is created: an audit campaign keeps ONE open device batch
    per screen, which later slices extend, instead of one batch per slice (S3782). A ticket past
    Approved is never appended to - a run may be implementing it - so a new one is created, under
    the first free name of -Slug, -Slug-2, -Slug-3.

.PARAMETER Parked
    S4190. The capture is an agent's auto-parked out-of-scope finding (CLAUDE.md 3.1), not a ticket the
    owner asked for. After the insert the ticket moves to BlockQuestions with a status note carrying
    'Parked finding, untriaged' and 'parked: yy-MM-dd', which keeps it out of the runner's automatic
    ranking until /spec-sweep Phase D triages it; archive-untriaged-drafts.ps1 archives it after 14
    days untriaged, except a finding about a crash, a release or security (S4268), which only triage
    removes. Measured 2026-10-13: the runner attempted three fresh parked drafts within half an
    hour of their capture, and 27% of September's tickets left no dev-log row.

.PARAMETER RepoRoot
    Project root whose catalog receives the ticket. Defaults to this repository; the contract
    suite passes a fixture.

    Exit codes:
      0  ticket created or text appended, or -WhatIf finished its dedup report.
      1  the catalog refused the insert, the spec file could not be written, or -Parked could not
         move the new ticket to BlockQuestions.
      2  bad invocation - invalid slug, no text or both text forms, missing attachment or template.
      3  refused - the dedup query hit a ticket in a closed status; pass -AllowClosedDuplicate for a
         genuine regression.

.EXAMPLE
    pwsh -NoProfile -File scripts/utils/capture-draft.ps1 -Slug bugfix-grid-crash -TextFile temp/scratch/draft-bugfix-grid-crash.txt
#>
[CmdletBinding(SupportsShouldProcess)]
param(
    [string]$Slug = '',
    [string]$Text,
    [string]$TextFile = '',
    [string]$Attach = '',
    [ValidateRange(0, 4)]
    [int]$Tier = 3,
    [int]$Priority = -1,
    [string]$DedupQuery = '',
    [switch]$AllowClosedDuplicate,
    [string]$Verify = '',
    [string]$Complexity = '',
    [switch]$AppendToOpen,
    [switch]$Parked,
    [string]$RepoRoot = ''
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Stop-BadInvocation([string]$Reason) {
    Write-Host "capture-draft: $Reason"
    exit 2
}

if (-not $RepoRoot) { $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path }
$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") { "$env:ProgramFiles\PowerShell\7\pwsh.exe" } else { 'pwsh' }

if ($Slug -notmatch '^[a-z0-9][a-z0-9-]*$' -or $Slug -match '^spec[_-]') {
    Stop-BadInvocation "invalid -Slug '$Slug' - kebab-case [a-z0-9-], starting alphanumeric, no spec prefix"
}
$hasText = $PSBoundParameters.ContainsKey('Text')
if ($hasText -eq [bool]$TextFile) { Stop-BadInvocation 'give exactly one of -Text and -TextFile' }
if ($TextFile) {
    if (-not (Test-Path -LiteralPath $TextFile -PathType Leaf)) { Stop-BadInvocation "text file '$TextFile' does not exist" }
    $Text = [System.IO.File]::ReadAllText((Resolve-Path -LiteralPath $TextFile).Path, [System.Text.Encoding]::UTF8)
}
if ($Priority -gt 100) { Stop-BadInvocation "-Priority $Priority is above 100" }
if ($Verify -and $Verify -notmatch '^(edit|build|emulator|device) - \S') {
    Stop-BadInvocation "-Verify '$Verify' is not '<edit|build|emulator|device> - <reason>'"
}
if ($Complexity -and $Complexity -notmatch '^(light|medium|hard) - \S') {
    Stop-BadInvocation "-Complexity '$Complexity' is not '<light|medium|hard> - <reason>'"
}

$attachments = @($Attach -split ';' | ForEach-Object { $_.Trim() } | Where-Object { $_ })
foreach ($path in $attachments) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { Stop-BadInvocation "attachment '$path' does not exist" }
}

$isBugfix = $Slug -match '^(bugfix|hotfix)-'
if ($Priority -lt 0) {
    $Priority = if ($Slug -like 'hotfix-*') { 95 } elseif ($isBugfix) { 90 } else { 50 }
}
$templateName = if ($isBugfix) { 'compact-bugfix-spec.md' } else { 'draft-spec.md' }
$templatePath = Join-Path $RepoRoot ".claude/templates/$templateName"
if (-not (Test-Path -LiteralPath $templatePath)) { Stop-BadInvocation "template '$templatePath' is missing" }

if ($DedupQuery) {
    $hits = @()
    $json = & $pwshExe -NoProfile -File (Join-Path $RepoRoot 'scripts/spec_catalog/search.ps1') -Query $DedupQuery -Format json 2> $null
    if ($LASTEXITCODE -eq 0 -and $json) { $hits = @(($json | Out-String) | ConvertFrom-Json) }
    foreach ($hit in @($hits | Select-Object -First 5)) {
        Write-Host "dedup: $($hit.id) $($hit.status) $($hit.name)"
    }

    # The set is declared in the profile rather than written out here: a status added to the
    # vocabulary later must widen or narrow this refusal deliberately, not by whatever this body
    # happened to list on the day it was written.
    $closedStatuses = @()
    $profilePath = Join-Path $RepoRoot '.sza-profile.json'
    if (Test-Path -LiteralPath $profilePath) {
        try { $closedStatuses = @((Get-Content -LiteralPath $profilePath -Raw | ConvertFrom-Json).grammar.closedStatuses) }
        catch { $closedStatuses = @() }
    }

    $closedHit = @($hits | Where-Object { $closedStatuses -contains [string]$_.status }) | Select-Object -First 1
    if ($closedHit -and -not $AllowClosedDuplicate) {
        Write-Host "capture-draft: refused - $($closedHit.id) already carries this symptom and is $($closedHit.status)."
        Write-Host "capture-draft: nothing was written. If the defect genuinely came back, re-run with -AllowClosedDuplicate."
        exit 3
    }
}
if (-not $PSCmdlet.ShouldProcess($Slug, 'capture Draft ticket')) { exit 0 }

if ($AppendToOpen) {
    # The journal is append-only: the last row of an id is its current state.
    $current = [ordered]@{}
    $journal = Join-Path $RepoRoot 'PLAN/spec-catalog.jsonl'
    if (Test-Path -LiteralPath $journal) {
        foreach ($line in [System.IO.File]::ReadAllLines($journal, [System.Text.Encoding]::UTF8)) {
            if (-not $line.Trim()) { continue }
            try { $row = $line | ConvertFrom-Json } catch { continue }
            if ($row.PSObject.Properties.Name -contains 'id' -and $row.PSObject.Properties.Name -contains 'name') { $current[[string]$row.id] = $row }
        }
    }
    $namePattern = '^' + [regex]::Escape($Slug) + '(-\d+)?$'
    $family = @($current.Values | Where-Object { [string]$_.name -match $namePattern })
    $open = @($family | Where-Object { @('Draft', 'Approved') -contains [string]$_.status }) | Select-Object -Last 1
    if ($open) {
        $openFile = Join-Path $RepoRoot ([string]$open.file)
        if (-not (Test-Path -LiteralPath $openFile -PathType Leaf)) {
            Write-Host "capture-draft: $($open.id) is open but its spec '$($open.file)' is missing on disk."
            exit 1
        }
        $existing = [System.IO.File]::ReadAllText($openFile, [System.Text.Encoding]::UTF8)
        $enl = if ($existing -match "`r`n") { "`r`n" } else { "`n" }
        $textAt = $existing.IndexOf('**Текст:**')
        $ruleAt = if ($textAt -ge 0) { $existing.IndexOf("$enl---", $textAt) } else { -1 }
        if ($ruleAt -lt 0) {
            Write-Host "capture-draft: $($open.id) has no inbox block (**Текст:** followed by ---) to append to."
            exit 1
        }
        $stamp = Get-Date -Format 'yyyy-MM-dd'
        $addition = "$enl**Дописано:** $stamp$enl$enl" + $Text.TrimEnd("`r", "`n") + $enl
        $updated = $existing.Insert($ruleAt, $addition)
        try { [System.IO.File]::WriteAllText($openFile, $updated, [System.Text.UTF8Encoding]::new($false)) }
        catch {
            Write-Host "capture-draft: $($open.id) could not be appended to: $($_.Exception.Message)"
            exit 1
        }
        Write-Host "$($open.id) $($open.name) - appended ($($open.status)). Captured: $($Text.Length) chars."
        exit 0
    }
    $taken = @($family | ForEach-Object { [string]$_.name })
    if ($taken -contains $Slug) {
        $n = 2
        while ($taken -contains "$Slug-$n") { $n++ }
        $Slug = "$Slug-$n"
    }
}

$insertOut = & $pwshExe -NoProfile -File (Join-Path $RepoRoot 'scripts/spec_catalog/insert.ps1') `
    -Name $Slug -Slug $Slug -Status Draft -Tier $Tier -Priority $Priority 2>&1 | Out-String
$id = [regex]::Match($insertOut, 'S\d{4}(?=\s*$)').Value
if ($LASTEXITCODE -ne 0 -or -not $id) {
    Write-Host "capture-draft: the catalog refused the insert (exit $LASTEXITCODE): $($insertOut.Trim())"
    exit 1
}

$specRel = "PLAN/${id}_$Slug.md"
$attachmentLines = [System.Collections.Generic.List[string]]::new()
$index = 0
foreach ($path in $attachments) {
    $index++
    $name = '{0:D2}__{1}' -f $index, [System.IO.Path]::GetFileName($path)
    $destRel = "PLAN/${id}_$Slug/attachments/$name"
    $dest = Join-Path $RepoRoot $destRel
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $dest) | Out-Null
    Copy-Item -LiteralPath $path -Destination $dest
    $attachmentLines.Add("- $([System.IO.Path]::GetFileName($path)) - ``$destRel``")
}

$tierLabels = @('0 - Security/Compliance (ad-hoc)', '1 - Quick Win (ad-hoc)', '2 - Easy (ad-hoc)', '3 - Moderate (ad-hoc)', '4 - Strategic (ad-hoc)')
$today = Get-Date -Format 'yyyy-MM-dd'
$body = [System.IO.File]::ReadAllText($templatePath, [System.Text.Encoding]::UTF8)
# The template's own authoring comments are not part of the ticket.
$body = ($body -replace '(?m)^<!--.*-->\r?\n', '').TrimStart("`r", "`n")
$nl = if ($body -match "`r`n") { "`r`n" } else { "`n" }
$attachmentBlock = if ($attachmentLines.Count) { "$nl**Вложения:**$nl$nl" + ($attachmentLines -join $nl) + $nl } else { '' }

if ($isBugfix) {
    $body = $body.Replace('<Краткое название дефекта>', $Slug).Replace('<Sxxxx>', $id).Replace('<short-name>', $Slug)
    $body = $body.Replace('<0..100>', [string]$Priority).Replace('<YYYY-MM-DD>', $today).Replace('<метка>', $tierLabels[$Tier])
    $body = $body.Replace('<Sxxxx-зависимости / связанные, либо «none»>', 'none')
    $body = [regex]::Replace($body, "\*\*Вложения:\*\* \(опустить если нет\)\r?\n- [^\r\n]*\r?\n", '')
    $body = $body.Replace('<вербатим-текст пользователя, без переписывания; или «нет текста»>', '{{TEXT}}')
    $body = $body.Replace("{{TEXT}}$nl", "{{TEXT}}$nl$attachmentBlock")
}
else {
    $body = $body.Replace('{{ID}}', $id).Replace('{{SLUG}}', $Slug).Replace('{{PRIORITY}}', [string]$Priority)
    $body = $body.Replace('{{DATE}}', $today).Replace('{{TIER}}', $tierLabels[$Tier]).Replace('{{ATTACHMENTS}}', $attachmentBlock)
}
# One insertion for both header lines so their order is fixed: Verify, then Complexity. A level is
# written on every capture - the runner reads it at each launch, and a ticket with no line would be
# routed by the older tier rule and never reach the post-research checkpoint that revises a level.
$levelLine = if ($Complexity) { $Complexity } else { 'medium - not assessed at capture' }
$headerLines = ''
if ($Verify) { $headerLines += "$nl**Verify:** $Verify" }
$headerLines += "$nl**Complexity:** $levelLine"
$body = ([regex]'(?m)^\*\*Tier:\*\* [^\r\n]*').Replace($body, { param($m) $m.Value + $headerLines }, 1)
$shownText = if ($Text) { $Text } else { '<нет текста>' }
$body = $body.Replace('{{TEXT}}', $shownText)

try {
    [System.IO.File]::WriteAllText((Join-Path $RepoRoot $specRel), $body, [System.Text.UTF8Encoding]::new($false))
}
catch {
    Write-Host "capture-draft: $id is in the catalog but $specRel could not be written: $($_.Exception.Message)"
    exit 1
}

$logOut = & $pwshExe -NoProfile -File (Join-Path $RepoRoot 'scripts/add_to_dev_log.ps1') $specRel $id "Capture Draft $id $Slug" 2>&1 | Out-String
if ($LASTEXITCODE -ne 0) { Write-Host "capture-draft: dev-log row not written (exit $LASTEXITCODE): $($logOut.Trim())" }

$statusShown = 'Draft'
if ($Parked) {
    $deadline = (Get-Date).AddDays(14).ToString('yy-MM-dd')
    $note = "Parked finding, untriaged (S4190): /spec-sweep Phase D promotes it with a **Triage:** line or archives it; " +
        "archived automatically after $deadline if still untriaged, except a crash, release or security finding (S4268). " +
        "parked: $((Get-Date).ToString('yy-MM-dd'))"
    $parkOut = & $pwshExe -NoProfile -File (Join-Path $RepoRoot 'scripts/spec_catalog/update.ps1') `
        -Id $id -Status BlockQuestions -StatusNote $note 2>&1 | Out-String
    if ($LASTEXITCODE -ne 0) {
        Write-Host "capture-draft: $id was captured as Draft but could not be parked (exit $LASTEXITCODE): $($parkOut.Trim())"
        exit 1
    }
    $statusShown = 'BlockQuestions (parked, untriaged)'
}

Write-Host "$id $Slug - $statusShown. Captured: $($Text.Length) chars + $($attachments.Count) attachment(s)."
exit 0
