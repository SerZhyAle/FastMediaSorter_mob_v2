<#
.SYNOPSIS
  S2779 - the watch walk's standing position, and the decision to recover it.

.DESCRIPTION
  Pure functions: no adb call, no device, no writes, no script-scope state. The walk owns the stack
  and passes it in on every call, which is what makes the model testable without a watch - the three
  fixes this script has already taken (S2547, S2767, S2782) are each pinned by a suite that never
  touches a device, and a position model kept in a `$script:` variable could not join them.

  What the stack is. `wear-prerelease-screens.json` declares a walk, not a tree: an entry taps its
  label, and `backAfter` says how many levels it climbs out afterwards - 0 keeps the walk inside the
  section it just opened, so the next entry is reached from in there. The standing position is
  therefore the running total of those pushes and pops, and until this library it existed nowhere:
  no field held it, nothing checked it, and a single BACK that a screen swallowed left every later
  entry hunting for a control on a screen that could not carry it. That is the 2026-09-09 run in
  which eleven consecutive entries were recorded unreachable in 23 minutes (S2779 section 0).

  Why the stack follows OUTCOMES rather than the declared list. An entry that never opened pressed
  no BACK either (S2767 removed that press deliberately: climbing out of a parent the walk is still
  standing on is what turned one unrecognised screen into six unreachable ones), so its iteration
  moves nothing. Pushing on every iteration regardless would drift by exactly the entries the
  recovery exists to correct for.

  Sourced, never executed directly, so it declares no exit codes of its own.
#>

function Get-WearWalkEntryField {
    # Read a declared field off a screen entry. Through PSObject rather than as `$Entry.label`: an
    # entry that declares no control has no such property at all, and the callers include a suite
    # under Set-StrictMode, where a missing property is a terminating error rather than $null.
    param($Entry, [string]$Name)

    if ($null -eq $Entry) { return '' }
    $property = $Entry.PSObject.Properties[$Name]
    if ($null -eq $property) { return '' }
    return [string]$property.Value
}

function Push-WearWalkPosition {
    <#
    .SYNOPSIS
      The stack after an entry that OPENED.

    .DESCRIPTION
      A level records HOW its screen was opened, not which row opened it, because a replay has to
      repeat that tap: an entry naming a `resourceId` is re-entered with `adb.ps1 tap-id` and one
      naming a label with `tap-label`, the same choice the walk's own reach makes. Storing the label
      alone would silently drop every id-driven entry from the stack - the wear module declares no
      testTag today, so no entry names an id yet, and a model that broke on the first one to do so
      would break exactly at the point someone added the more reliable of the two selectors.

      An entry declaring neither adds no level: `home` is reached by the launch itself, so it opens
      nothing.
    #>
    param(
        [object[]]$Position,
        $Entry
    )

    $stack = @($Position | Where-Object { $_ })
    $resourceId = Get-WearWalkEntryField -Entry $Entry -Name 'resourceId'
    $label = Get-WearWalkEntryField -Entry $Entry -Name 'label'
    if ([string]::IsNullOrWhiteSpace($resourceId) -and [string]::IsNullOrWhiteSpace($label)) { return $stack }
    return @($stack + [pscustomobject]@{
        resourceId = $resourceId
        label      = $label
        # What the operator reads in walk.json and in the run's own lines. The id is preferred
        # because it is what the reach would use, and it is not translated (CLAUDE.md section 9).
        name       = if ($resourceId) { $resourceId } else { $label }
    })
}

function Pop-WearWalkPosition {
    <#
    .SYNOPSIS
      The stack after `BackAfter` BACK presses.

    .DESCRIPTION
      Never below empty. A `backAfter` larger than the current depth means the walk left the app
      entirely, which the existing app-in-front guard catches on the next iteration - so the answer
      here is Home, not a negative depth nothing could replay.
    #>
    param(
        [object[]]$Position,
        [int]$BackAfter
    )

    $stack = @($Position | Where-Object { $_ })
    if ($BackAfter -le 0) { return $stack }
    if ($BackAfter -ge $stack.Count) { return @() }
    return @($stack[0..($stack.Count - $BackAfter - 1)])
}

function Test-WearWalkCascade {
    <#
    .SYNOPSIS
      $true when a run of unreachable entries is better explained by a lost position than by absent
      controls.

    .DESCRIPTION
      Two in a row is the default threshold and it is a claim about the screen list, not a tuning
      constant: the declared walk names one destination per entry and consecutive entries usually sit
      in different sections, so two neighbours failing together is already unlike two screens
      independently missing - while a single failure is the ordinary case an `optional` or a
      state-dependent entry produces on a clean device.

      A threshold of 0 or less disables the recovery, which is what a caller passes to reproduce a
      pre-S2779 run.
    #>
    param(
        [int]$ConsecutiveUnreachable,
        [int]$Threshold
    )

    if ($Threshold -le 0) { return $false }
    return ($ConsecutiveUnreachable -ge $Threshold)
}
