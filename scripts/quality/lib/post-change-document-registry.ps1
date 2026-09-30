#requires -Version 7.0
<#
.SYNOPSIS
    post-change.ps1 library: the document-registry step.

.DESCRIPTION
    Dot-sourced by scripts/post-change.ps1 at the point the step runs, never invoked. It is the
    step's body, not a function: it reads $root, $changedFiles and $RegistryAck and calls the step
    runners exactly as the inline block did, in the caller's scope. Moved out of the facade by S3515
    for the Rule 2 ceiling, together with the one behaviour change below.

    S3515 - A SMALL SET GETS THE RECORDS AS INFORMATION, NOT AS AN ADVISORY. The advisory asked for a
    re-run with -RegistryAck, and in four of the five sessions measured on 2026-09-24 the agent re-ran
    the whole closure for exactly that - a second gate batch whose only product was a clean PASS
    line. On a set no larger than the Trivial limit (`trivial.maxFiles` in .sza-profile.json, counted
    without PLAN/, temp/ and registry-generated paths) the records and their siblings are still
    printed, and the step passes. A larger set keeps the advisory, because there a registered
    document moving alongside several others is the case the acknowledgement exists for.

.NOTES
    Exit codes (CLAUDE.md Rule 7): none - this file is dot-sourced and never exits.
#>
# S1338 phase 05: the document-registry trigger. Reads docs/DOCUMENT_REGISTRY.jsonl and reports
# every record whose `paths` cover a file in this change - a registered document moved, so its
# siblings (other locales, the site export, the mirrored page) may now disagree with it. Fires on
# registered paths only, never on every closure, so it stays real where it fires.
$registryPath = Join-Path $root 'docs/DOCUMENT_REGISTRY.jsonl'
if (Test-Path -LiteralPath $registryPath) {
    # `-replace '^\./'`, never `TrimStart('./')`: TrimStart takes a CHAR SET, so it ate the
    # leading dot of `.claude/commands/*.md` and every command-file edit missed its record.
    $normalizedChanged = @($changedFiles | ForEach-Object { ($_ -replace '\\', '/') -replace '^\./', '' })
    $matchedRecords = @()
    $generatedRegistryPaths = @()
    foreach ($line in (Get-Content -LiteralPath $registryPath -Encoding UTF8)) {
        $trimmed = "$line".Trim()
        if (-not $trimmed) { continue }
        try { $record = $trimmed | ConvertFrom-Json } catch { continue }
        if (-not ($record.PSObject.Properties.Name -contains 'paths')) { continue }
        # A generated document is owned by its generator and its own sync gate; asking the
        # operator to acknowledge it teaches nothing. Skipping it is why the cheatsheet does
        # not raise an advisory on every closure that changes a param block.
        if (($record.PSObject.Properties.Name -contains 'generated') -and $record.generated) {
            $generatedRegistryPaths += @($record.paths | ForEach-Object { ([string]$_) -replace '\\', '/' })
            continue
        }
        $hitPaths = @()
        $hitPatterns = @()
        foreach ($registered in @($record.paths)) {
            $reg = ($registered -replace '\\', '/')
            foreach ($changed in $normalizedChanged) {
                if ($changed -ieq $reg -or $changed -ilike "$reg/*" -or $changed -ilike $reg) { $hitPatterns += $reg }
                # Exact path, a directory prefix, or a glob - `repository-rules` registers
                # `.claude/commands/*.md`, and a literal-only match silently missed every
                # command-file edit, which is the largest registered surface in the repo.
                if ($changed -ieq $reg -or $changed -ilike "$reg/*" -or $changed -ilike $reg) { $hitPaths += $changed }
            }
        }
        if ($hitPaths.Count -gt 0) {
            $matchedRecords += [pscustomobject]@{
                Id     = [string]$record.id
                Title  = [string]$record.title
                Files  = @($hitPaths | Select-Object -Unique)
                # Siblings are the registered entries this change did NOT touch - listing the
                # pattern that just matched as something to go and update is noise.
                Others = @(@($record.paths) | Where-Object { ($_ -replace '\\', '/') -notin $hitPatterns })
            }
        }
    }

    if ($matchedRecords.Count -eq 0) {
        Skip-Step "document-registry" "not applicable - no changed file is a registered document"
    }
    else {
        # S1340: pwsh -File does not re-split a quoted CSV into array elements (feedback_string_array_param_csv_via_file.md) -
        # split each bound element on comma too, so `-RegistryAck "a,b"` and `-RegistryAck a,b` both work from the Bash tool.
        $ackSet = @($RegistryAck | ForEach-Object { $_ -split ',' } | ForEach-Object { $_.Trim() } | Where-Object { $_ })
        $ackAll = ($ackSet -contains 'all')
        $unacked = @($matchedRecords | Where-Object { -not $ackAll -and $_.Id -notin $ackSet })
        foreach ($rec in $matchedRecords) {
            $recordLines = @(("  registry: {0} ({1}) <- {2}" -f $rec.Id, $rec.Title, ($rec.Files -join ', ')))
            if ($rec.Others.Count -gt 0) {
                $recordLines += ("    siblings that may need the same edit: {0}" -f ($rec.Others -join ', '))
            }
            # S3151: an acknowledged record is a decision the caller already made, so on a clean run its
            # sibling list goes to the protocol only; an unacknowledged one still reaches the console.
            $acknowledged = $ackAll -or $rec.Id -in $ackSet
            foreach ($recordLine in $recordLines) {
                if ($acknowledged -and -not $script:ConsolePasses) { Write-ProtocolLine $recordLine }
                else { Write-Host $recordLine }
            }
        }
        # Paths a closure never authors by hand do not make a change larger: PLAN/ and temp/ are
        # gitignored, and a generated document is rewritten by its generator, not chosen.
        $registrySmallSetMax = 3
        $trivialProfile = (Get-Content -LiteralPath (Join-Path $root '.sza-profile.json') -Raw -ErrorAction SilentlyContinue |
                ConvertFrom-Json -ErrorAction SilentlyContinue).trivial
        if ($trivialProfile -and $trivialProfile.maxFiles) { $registrySmallSetMax = [int]$trivialProfile.maxFiles }
        $registryJudgedCount = @($normalizedChanged | Where-Object {
                $path = $_
                $path -notmatch '^(PLAN|temp)/' -and
                -not @($generatedRegistryPaths | Where-Object { $path -ieq $_ -or $path -ilike $_ -or $path -ilike "$_/*" }).Count
            }).Count
        if ($unacked.Count -eq 0) {
            Invoke-Gate "document-registry" {
                Write-Host ("  acknowledged: {0}" -f (($matchedRecords | ForEach-Object { $_.Id }) -join ', '))
                $global:LASTEXITCODE = 0
            }
        }
        elseif ($registryJudgedCount -le $registrySmallSetMax) {
            Invoke-Gate "document-registry" {
                Write-Host ("  small change ({0} file(s), limit {1}): the records above are for reading; no -RegistryAck re-run is needed." -f
                    $registryJudgedCount, $registrySmallSetMax)
                $global:LASTEXITCODE = 0
            }
        }
        else {
            Invoke-AdvisoryStep "document-registry" {
                $global:LASTEXITCODE = 1
            } -AdvisoryDetails ("registered document(s) changed and not acknowledged: " +
                (($unacked | ForEach-Object { $_.Id }) -join ', ') +
                ". Read them, update the siblings listed above, then re-run with -RegistryAck '" +
                (($unacked | ForEach-Object { $_.Id }) -join ',') + "'.")
        }
    }
}
else {
    Skip-Step "document-registry" "cannot verify - docs/DOCUMENT_REGISTRY.jsonl not found"
}
