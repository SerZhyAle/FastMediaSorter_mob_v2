#requires -Version 7.0
<#
.SYNOPSIS
    S0826: run the fast static Kotlin quality gates in ONE process and aggregate.

.DESCRIPTION
    A convenience batch over the cheap (non-gradle) gates so a dev iterating on a Kotlin
    change runs them with a single command and a single exit code, instead of N separate
    pwsh launches. detekt is gradle-backed and slow, so it is opt-in via -IncludeDetekt.

    Gates (in order):
      - assert-no-ticket-logs        (Sxxxx probe / permanent-log invariant)
      - assert-source-gates          (S1338: every lexical ratchet rule over ONE walk of the
                                      tree - the nine neuroslop rules plus flavor-flags,
                                      deprecated-pm-flags, public-mutable-flow, window-insets,
                                      swallowed-cancellation, activity-logic (S1329),
                                      untracked-dialog and the two string-resource rules)
      - assert-listener-symmetry
      - assert-wear-settings-parity  (S2093 watch settings present on one side of the pair only)
      - assert-wear-canonical-key-parity (S2579 a watch program key that is neither a phone route
                                      key nor a declared watch-only program)
      - assert-wear-record-merge-parity (S2502 the two resource merge-rule copies diverging)
      - assert-wear-wire-nullability (S2885 a bridge collection field Gson leaves null despite its
        Kotlin default)
      - assert-qualifier-shadowing   (values-land key a smallestWidth bucket always outranks)
      - assert-qualified-gradle-tasks (S2172 a Gradle task name missing its :module: segment)
- assert-device-ready-module   (S2611 a device-ready.ps1 call site that names no -Module)
      - assert-tactical-step-form    (S1343 Why-field ratchet over PLAN/*/PHASE_*.md)
      - assert-no-line-budget        (S2037 a self-cost estimate column in planning output)
      - assert-flavor-matrix-docs    (S1392 doc flavor tables vs the generated capability snapshot)
      - assert-code-domain-writers   (S2635 a script writing a Code.* path without taking the domain)
      - assert-gate-placement        (S2870 the gate-placement registry vs where the gates are wired)
- assert-sdk-pin-claims        (S1438 SDK pins stated in prose vs the build files)
- assert-flavor-count-prose    (S2445 flavor counts and complete-set lists in prose vs the matrix)
      - assert-gate-count-prose      (S2935 the fg gate count in prose vs the $gates table below)
      - assert-ctor-arg-slots        (S1470 primary constructors near the 255 argument-slot ceiling)
      - assert-packaging-excludes-parity (S1679 shared-library payload stripped in one module only)
      - assert-module-version-parity  (app_v2 / wear version fields under one applicationId)
      - assert-retired-dependency-names (S1489 prose naming a dependency the project replaced)
      - assert-notification-small-icon (S1399 a small-icon setter handed a drawable literal)
      - assert-backup-rules-consistent (S1552 API 31+ extraction rules vs the pre-31 backup rules)
      - assert-launcher-reset-coverage (S1540 launcher settings vs the launcher reset's field list)
      - assert-maestro-oracle        (S1612 Maestro flows that are green without proving anything)
      - assert-hook-inventory        (S1604 registered Claude Code hooks vs docs/AGENT_HOOKS.md)
      - assert-rule-digest-sync      (S1548 CLAUDE.md numbered rules vs the two full digests)
      - assert-gson-persistence-contract (S1639 a durable Gson model whose wire names nothing pins)
      - assert-stream-asset-revisions (S1828 a pinned stream-catalog asset that would stop being published)
      - assert-migration-test-pairing (S1844 a Room migration with no instrumented migration test)
 - assert-migration-schema-conformance (S2306 migration SQL that disagrees with the exported schema)
      - assert-launcher-contrast     (S1895 a launcher colour measured under 7:1 on its own surface)
      - assert-detekt                (only with -IncludeDetekt; honours -ChangedFiles)

    S1939 moved three tree-scope gates out of this batch to
    scripts/quality/assert-release-scope-gates.ps1. Between them they produced 68 of the 191 red
    lines this runner emitted over 53 runs, none of them about the work in front of the operator -
    which is how a runner teaches its reader to skim past red - and one of them alone spent 33
    minutes of closure time in a month to report a single finding. Rule 20 already said the
    dead-weight sweep belongs on a release build; the placement test is CLAUDE.md Rule 33.

    S2870: WHICH gates those were, and every other placement decision, is no longer recorded here.
    It lives in scripts/quality/gate-placement.jsonl - one row per gate carrying its scope class,
    the deciding ticket, the date and the reason - because a decision written as a paragraph can
    be neither queried nor checked, so nothing could tell that a retired gate had been quietly
    added back. assert-gate-placement.ps1 now fails the closure when the registry and the runners
    disagree. Read the registry, not a comment, for where a gate belongs.

    Each child runs as its own process so a child `exit` cannot kill this aggregator.

    S2451: the children run CONCURRENTLY. They used to run in a strict foreach, so the batch cost
    the SUM of 45 independent read-only scans plus 45 interpreter starts - 142.8 s measured
    2026-09-03, past the 120 s foreground threshold CLAUDE.md Rule 6 pins this target below. The
    verdict then arrived as a background notification, which is the delivery Rule 26 exists to
    forbid, and every session passed through it silently. Nothing required the sequence: no gate
    reads another's output, none of them touches gradle (so no BUILD.LOCK wait is being hidden),
    and every gate writes only to its own baseline/snapshot file - the one shared sink is the
    telemetry journal, and the PARENT writes that, after the fan-in, so its append stays
    single-threaded. Rescoping the expensive gates to release scope under Rule 33 was the wrong
    trade here: 142.8 s was the batch's schedule, not its size, and those gates judge exactly what
    the operator changed. -Sequential restores the old order when one gate has to be debugged.

    Ordering survives the change. Output is buffered per gate and printed in table order, never
    interleaved as it completes - a gate's output is its evidence, and interleaved evidence is
    unreadable. The summary is printed in table order too, because runs are compared against each
    other by it.

    Modes:
      (default)       Run each gate in -Gate mode; print per-gate PASS/FAIL; exit 1 if any failed.
      -IncludeDetekt  Also run the (slow) gradle detekt gate. Always last, and never concurrent
                      with the others: it is the one gradle-backed child and takes BUILD.LOCK.
      -ChangedFiles   Diff-scoped judgement. Forwarded to detekt AND to every gate in the
                      table that accepts the parameter (see $changedFilesAware below).
                      Omit it - as a release or CI run does - and every gate keeps its
                      strict project-wide judgement.
      -FailOnSkipped  Count a gate that could not verify (child exit 3) as a failure. For a caller
                      that requires full coverage rather than "everything runnable here was green".
      -Sequential     Run the children one at a time, as before S2451.
      -ThrottleLimit  Concurrent children; 0 (default) derives it from the core count.

    Two verdicts under -ChangedFiles (S2693):
      Given a changed set, the summary splits in two. YOUR SET holds the gates that actually
      received the set and judged it; THE TREE holds every gate that judged the whole project
      regardless. Only YOUR SET decides the exit code; a red TREE names its gates, says the work
      belongs to someone else, and leaves the exit code at 0.

      One promotion, and it is the only way a tree gate reaches the exit code: a tree gate whose
      FAIL output names a path from the changed set moves into YOUR SET (journal scope
      `set-named`). Without it a gate that takes no -ChangedFiles - exit contract, script
      references, memory budget - would report the caller's OWN defect as advisory, and the
      split would hide exactly the red it exists to surface (found by the S2693 re-audit).

      Why: measured over the ten days to 2026-09-07, the battery ran 62 times and was clean on
      none of them, with a median of three red gates per run - the reds being project-wide
      invariants catching another session's unfinished work in a tree that carries up to six
      concurrent sessions. A verdict that is red on every run is not read, and the operator who
      stops reading it also stops seeing the reds that ARE his. The closure facade has judged a
      delta against HEAD since S1338 for the same reason and holds a 31% FAIL rate against this
      battery's 100%. This does not weaken the tree's invariants: with no changed set - a release
      run, a CI run, `.\a.ps1 fg` typed bare - the behaviour is exactly as before, and
      assert-release-scope-gates.ps1 keeps the tree fatal at the boundary that ships.

    Exit codes:
      0  every gate passed; or, with -ChangedFiles, every gate that judged the changed set
         passed and only project-wide gates were red. A gate that exited 3 - it could not verify,
         see below - does not affect this code unless -FailOnSkipped was passed.
      1  at least one gate failed or is MISSING; with -ChangedFiles, at least one gate that
         judged the changed set failed or is MISSING; or -FailOnSkipped and a gate was skipped.

    Skipped gates (S3075):
      A child exit code of 3 means "this run judged nothing", not "this run found nothing wrong" -
      lib/absent-input.ps1 uses it when a gitignored root (PLAN/, .claude/) is not in the checkout,
      lib/fixed-input-scope.ps1 when a fixed-input gate was handed a set that charges it nothing.
      Such a gate is reported as SKIP in its own NOT RUN block and is left out of both scope blocks.
      Folding it into FAIL is what made the GitHub "Static Gates" job state one published fact -
      that PLAN/ and .claude/ are not published - as nine separate findings, in a summary that was
      red on every run and therefore read on none. -FailOnSkipped restores the strict count for a
      caller that requires full coverage.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-fast-gates.ps1
    pwsh -NoProfile -File scripts/quality/assert-fast-gates.ps1 -IncludeDetekt -Module app_v2 -ChangedFiles app_v2/src/main/.../Foo.kt
    pwsh -NoProfile -File scripts/quality/assert-fast-gates.ps1 -Sequential
#>
[CmdletBinding()]
param(
    [switch]$IncludeDetekt,
    [ValidateSet('app_v2', 'wear')]
    [string]$Module,
    [string[]]$ChangedFiles,
    [switch]$Sequential,
    [switch]$FailOnSkipped,
    [ValidateRange(0, 64)]
    [int]$ThrottleLimit = 0
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'lib/gate-telemetry.ps1')

# S2453: the batch's own wall clock, which is what `docs/BUILD_TEST_FAST_PATH.md` claims for
# `a.ps1 fg` and what the caller waits on. Started before any gate so the record covers the
# whole run including this script's own setup.
$batchStopwatch = [System.Diagnostics.Stopwatch]::StartNew()

$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") {
    "$env:ProgramFiles\PowerShell\7\pwsh.exe"
}
else {
    'pwsh'
}

# name -> extra args (beyond -Gate). Order matters: cheapest/most-deterministic first.
$gates = [ordered]@{
    'assert-no-ticket-logs.ps1'                 = @('-Quiet')
    'assert-ticket-acceptance-probes.ps1'       = @('-Quiet')
    'assert-acceptance-preconditions.ps1'       = @('-Quiet')
    'assert-spec-catalog-valid.ps1'             = @('-Quiet')
    # S3084: the CI cost map in docs/BUILD_VS_RELEASE.md against the `on:` blocks and `jobs:` keys of
    # .github/workflows/*.yml. Three files and one table, read as text - no gradle daemon.
    'assert-ci-cost-map.ps1'                    = @('-Quiet')
    # S1338: one entry, twelve lexical rules, ONE walk of the tree. It replaces the five
    # separate entries that each spawned a pwsh process and each re-walked app_v2/src -
    # neuroslop (nine rules), flavor-flags, public-mutable-flow and deprecated-pm-flags.
    # The individual scripts still exist as wrappers for any direct caller.
    'assert-source-gates.ps1'                   = @()
    # S2930: an Activity that skips LocaleHelper.applyLocale resolves its strings from the framework
    # configuration rather than the language the user chose. Per-ticket by Rule 33 - the subject is a
    # single source file, and the author of a new Activity is the only one who can say whether it is
    # the rare one that must not wrap its context. Exclusions are named with a reason, never counted.
    'assert-activity-locale-wrapper.ps1'        = @()
    'assert-wear-route-literals.ps1'            = @()
    # S2547: the declared watch pre-release walk against the module it claims to walk. Per-ticket by
    # Rule 33 - the subject is a wear string, so a rename must fail in the ticket that made it rather
    # than months later on the pre-release run, where the failure reads as a broken screen instead.
    'assert-wear-walk-contract.ps1'             = @()
    # S2880: the bridge scenario registry against the Data Layer route catalogs it claims to cover.
    # Same placement as the walk gate above: the subject is a route constant, so adding a route must
    # fail in the ticket that made it rather than at the next joint-device campaign, where the
    # unclassified route reads as a broken bridge instead of an unclassified route.
    'assert-bridge-scenario-coverage.ps1'       = @()
    # S2380: the declared phone UI sweep against the module it claims to walk. Same four failure modes
    # as the watch gate above, plus a coverage half the watch one does not need - the phone catalog is
    # built from two sources (the activity catalog and the settings manifest), so a screen can go
    # missing from the sweep by being added to the module and to neither list. Per-ticket by Rule 33:
    # the subject is an app_v2 string or activity, so a rename must fail in the ticket that made it
    # rather than on the sweep run months later, where it reads as a broken screen instead of a rename.
    'assert-ui-sweep-catalog.ps1'               = @()
    # S2093: a watch setting present on one side of the phone/watch pair and absent on the other. The
    # list used to live in four independently maintained places, so a one-sided setting diverged in
    # silence and was found only when the owner could not see it where it was expected. Reads the two
    # WearSettingsRegistry copies against the payload, the watch store and the settings reference; no
    # gradle daemon. Per-ticket by Rule 33: only the author knows whether a new one-sided setting was
    # meant to be one-sided, and the reference it guards is read by agents between releases.
    'assert-wear-settings-parity.ps1'           = @('-Quiet')
    # S2579: WearAppId declares canonicalKey to be the phone's route key for the same program, and
    # nothing compared the two literal sets - the watch test checks the watch against itself. The
    # rule is two-part because a watch-only program is legitimate, so the exceptions are declared in
    # wear-canonical-key-watch-only-baseline.txt with their reason. Two files, no gradle daemon.
    # Per-ticket by Rule 33: only the author of a new entry knows whether it is meant to be one-sided.
    'assert-wear-canonical-key-parity.ps1'      = @('-Quiet')
    # S2642: eight vocabularies cross the Wear Data Layer outside settings, each written twice by hand.
    # Refuses a divergence between the two copies, and refuses a new mirrored enum added without being
    # declared in the gate's table. Dot-sources lib/wear-vocabulary-parsers.ps1; no gradle daemon.
    'assert-wear-wire-vocabulary-parity.ps1'      = @('-Quiet')
    # S2885: a bridge envelope field declared as a non-null collection WITH a Kotlin default. Gson
    # runs no constructor, so an absent key leaves it null under a non-null type and the receiver's
    # first dereference kills the whole exchange - both listener services swallow it into one generic
    # message. Stood in four fields; two were live compatibility defects against an older sender.
    # Reads an explicit envelope list, no gradle daemon. Per-ticket by Rule 33: the subject is a field
    # declaration a ticket writes, so it must fail in that ticket rather than on the next joint-device
    # campaign, where it reads as a dead bridge instead of a missing question mark.
    'assert-wear-wire-nullability.ps1'          = @('-Quiet')
    # S2502: the resource merge rule is written once per module because the two share no artifact.
    # If the copies disagree the exchange never converges - each side keeps its own version and
    # believes it won - which is invisible until the owner notices an edit that will not stick.
    # Per-ticket by Rule 33: it judges two files a ticket touches, not a tree-wide property.
    'assert-wear-record-merge-parity.ps1'       = @('-Quiet')
    'assert-listener-symmetry.ps1'              = @()
    'assert-orientation-implied-feature.ps1'    = @()
    # S1549: an activity that absorbs 'orientation' in configChanges never re-inflates on
    # rotation, so its res/layout-land variant applies only on a landscape cold start. The
    # same defect was fixed pointwise twice before anyone inventoried the project.
    'assert-orientation-layout-pairing.ps1'     = @()
    # S1568: a string resource nothing references. 397 had accumulated in values/strings.xml, each
    # one paid for again by every locale tranche. Baseline is an allowlist of NAMES, not a count, so
    # a new dead key cannot hide behind a deleted one.
    # S1612: Maestro flow YAML vs the oracle convention. A flow that carries optional: true on its
    # proof assertion, a regex selector Maestro never matches, or a coordinate tap standing in for
    # an assertion is GREEN while proving nothing - that is how the previous generation of flows
    # became fictitious. Static YAML scan of two directories, no device and no gradle daemon.
    'assert-maestro-oracle.ps1'                 = @('-Quiet')
    # S1070: guards the tooling itself rather than app sources - a bare Write-Error under
    # EAP=Stop makes the following `exit N` unreachable, so a script's documented code
    # collapses to 1. Cheap (scans scripts/*.ps1 only) and the class has regrown 3 times.
    'assert-exit-contract.ps1'                  = @('-Quiet')
    # S2619: a .ps1 the parser cannot read at all. The rest of the battery scans line by line with
    # regular expressions, which matches text the same whether the file is valid or wrecked - so a
    # script truncated by a bad edit passes closure and waits in the tree for its first caller.
    # Two did, both halves of the dimen pipeline, one with two fragments spliced mid-block. Binary
    # class, so no baseline: a file either parses or cannot run. Parses .ps1 text, no gradle daemon.
    'assert-script-parses.ps1'                  = @('-Quiet')
    # S2654: a gate called by another script can exist locally without entering git, so a fresh
    # clone or release worktree fails only when the caller reaches it. The parser resolves
    # non-comment .ps1 tokens and refuses untracked existing targets; no Gradle daemon runs.
    'assert-invoked-tracked.ps1'                 = @('-Quiet')
    # S2172: a Gradle task name written without its module segment. Gradle expands such a name across
    # every project that declares it, so `assembleStandardDebug` began meaning "app_v2 AND wear" the
    # day S2090 gave the watch a `standard` flavor - changing what 40 call sites did without editing
    # one of them. The entry point then writes into wear/build/** while holding only Build.Phone, so
    # a sibling session's watch build dies on a locked R.jar reading as broken code. Scans scripts/*.ps1
    # only, no gradle daemon.
    'assert-qualified-gradle-tasks.ps1'         = @('-Quiet')
    # S2611: a device-ready.ps1 call site that names no -Module, so the probe takes whatever device
    # is attached. S2600 built the form-factor selection and left the parameter undefaulted on
    # purpose - which meant twelve call sites carried none and the protection was off. Per-ticket by
    # Rule 33: a new call site arrives with a ticket, and its failure is a verdict about the wrong
    # device rather than a crash, so it has to be refused where it is written. Scans scripts/ and
    # .claude/ only, no gradle daemon.
    'assert-device-ready-module.ps1'            = @('-Quiet')
    # S2447: a Hilt binding for a type src/main injects unconditionally, present only in source sets
    # some flavors do not mount. `fk` compiles standard and `fkn` noLegal, both of which happened to
    # carry the AccessibilityServiceControl binding; the five flavors that did not went unbuilt for
    # two weeks until :app_v2:hiltJavaCompileLiteDebug failed with Dagger/MissingBinding. Compiling
    # those five is 10+ minutes against this batch's 32 s, so the check is lexical: it reads the
    # mount map out of app_v2/build.gradle.kts and treats a conditionally mounted directory as
    # covering only when EVERY branch of its if/else provides the type - which is the second half of
    # the same defect, since standard's only binding sat behind an overlay flag. No gradle daemon.
    'assert-flavor-binding-coverage.ps1'        = @('-Quiet')
    # S1844: a Room migration with no instrumented migration test. Nothing compiled androidTest and
    # nothing checked the pairing, so a migration could ship untested while a plausible-looking test
    # file sat beside it - AppDatabaseMigration50To51Test.kt referenced a constant it never declared
    # and could not compile at all. Ratchet over two directory listings, no gradle daemon; the 12
    # migrations that predate the habit are baselined so only a NEW gap fails.
    # S2355: judges EVERY Room database in scripts/quality/lib/room-databases.ps1, not app_v2 alone,
    # in one run - a gate the caller must remember to invoke twice is the wiring mistake the registry
    # removes. Baseline keys carry the module, so one module's frozen debt cannot silence a finding in
    # another. A module with an exported schema and zero migrations is a clean skip, not "cannot
    # verify": that is the watch's legitimate state at version 1, and refusing it would defer the
    # gate's activation to somebody else's ticket.
    # S1905 (2026-09-05): -Gate is what makes this fatal. Without it the script prints its FAIL
    # banner and exits 0, so this batch recorded PASS while the finding stood in plain text. That is
    # exactly what shipped Migration54To55 with an index the entity does not declare and wiped the
    # owner's database on update - both migration gates named the defect and neither could stop it.
    # S2569: no '-Gate' here - the runner supplies it (see the invocation below), and a second copy
    # made PowerShell refuse to bind, so this gate reported FAIL without ever executing.
    'assert-migration-test-pairing.ps1'         = @()
    # S2306: the other half of the same contract - a migration test proves a test EXISTS, this proves
    # the migration's SQL says what the exported schema Room validates against says. S2251 had neither:
    # the SQL added `screen_index`, LauncherCellEntity declared `screenIndex`, and the disagreement was
    # visible in two files in this tree while every check ran green. Room compares them on the user's
    # device on the first launch after an update, and the recovery path deletes the database when they
    # differ. Two directory listings and a JSON parse, no gradle daemon.
    # S2355: reads the same registry as the pairing gate above and reports one summary per database,
    # naming the module in every finding. Sharing the registry is deliberate - two checks each holding
    # a private idea of which databases exist is the S1621 failure, and here the cost would be higher
    # than a disagreement: one of them would silently call a database unverifiable.
    'assert-migration-schema-conformance.ps1'   = @('-Quiet')
    # S1895: the launcher taskbar and Start panel measured against the surfaces they land on, in all
    # eight themes. The previous change to these same colours was closed on a visual check and
    # shipped the Start label at 4.22:1; contrast is arithmetic, so it can be checked rather than
    # looked at. Reads four resource files, no gradle daemon.
    'assert-launcher-contrast.ps1'              = @('-Quiet')
    # S1075: dev/TECH_REQUIREMENTS.md pins vs Gradle truth. Static parse of build files
    # + one doc; no gradle daemon. Catches a dependency bump that forgot the doc.
    'assert-doc-pin-drift.ps1'                  = @('-Quiet')
    # S1679: a packaging exclusion for a SHARED transitive library present in app_v2 but not in wear.
    # The pin gate above compares versions against docs and cannot express "both modules must strip
    # the same dead payload". S0385 excluded BouncyCastle's post-quantum tables in app_v2 only, and
    # wear shipped them for months - 1.2 MB, 10 % of its release APK, on a watch. Parses the two
    # build files by brace balance, no gradle daemon.
    'assert-packaging-excludes-parity.ps1'      = @('-Quiet')
    # S2129: the wear copy of the resource-icon set against app_v2, its source of truth. The watch
    # resolves a synced `ico-NN-NNN` id locally, so a missing copy answers null and falls back to
    # the type glyph - the very defect S2129 removes, returning with no crash and no log. Per-ticket
    # by Rule 33: the fallback is silent, so between releases nothing else would surface it.
    # Forwards to the generator's own -Check, so gate and fix cannot disagree; no gradle daemon.
    'assert-resource-icon-parity.ps1'           = @('-Quiet')
    # The version fields of app_v2 and wear are one contract under one applicationId: the same
    # versionName, and versionCodes that must NOT collide - Play refuses the repeat at submission
    # time, long after the build passed. The tree carried both modules on 260815161. Reads the two
    # checked-in constants; no gradle daemon.
    'assert-module-version-parity.ps1'          = @('-Quiet')
    # S1872: a script nothing calls is invisible - the only way to find one was a hand sweep of the
    # repository, which is an answer that is true once. Judges live wiring only: a mention in an
    # archived spec or a changelog row remembers a script, it does not call one. Ratcheted against
    # script-reference-baseline.txt; no gradle daemon.
    'assert-script-references.ps1'              = @('-Quiet')
    # S1872: the inventory is generated from script headers, so a script with no synopsis lands in
    # it as a name and a parameter list and explains nothing. Two ratcheted counts, kept apart so
    # neither hides behind the other: no synopsis, and exits without a documented contract.
    # Baselines in script-described-baseline.txt; no gradle daemon.
    'assert-script-described.ps1'               = @('-Quiet')
    # S1270: Rule 2's 2000-line ceiling had no mechanical check of any kind - detekt carries
    # LongMethod but no FileLength, and it never sees a .cpp at all. xr_session.cpp grew 2101 ->
    # 2154 lines while a ticket about its size sat open. Ratcheted count over .kt/.java/.cpp/.h;
    # no gradle daemon.
    'assert-file-line-ceiling.ps1'              = @('-Quiet')
    # S1356's gate existed and nothing ran it. Re-freezing a detekt baseline is the quietest way
    # to make a file look clean while its debt grows, and five tickets were written about the same
    # mechanism in five different files - S1186, S1198, S1247, S1269, S1311 - before anyone noticed
    # the check was never in the batch. Reads the committed ID snapshot; no gradle daemon.
    'assert-detekt-baseline-absorption.ps1'     = @()
    # S1438: SDK pins stated in ORDINARY PROSE, which the managed-block gate above cannot see -
    # an index line, an architecture bullet, an agent definition, agent memory. Eight copies said
    # compileSdk 35 while Gradle compiled against 36, and an agent reading one concludes an API is
    # unavailable. Reads the value from the build file every run, so it can never itself go stale.
    'assert-sdk-pin-claims.ps1'                 = @('-Quiet')
    # S2445: the same shape one axis over - the flavor COUNT stated in ordinary prose, which
    # assert-flavor-matrix-docs cannot see because its manifest scopes it to glyph table cells.
    # S0403's seventh flavor left thirteen documents and a.ps1's help text saying six, and the help
    # text hid a working `-Flavor Foss` behind a six-name list. Second run of the drift S1392 opened.
    # Only quantified claims are judged, so a deliberate subset ("standard/legacy/noLegal/vr - HLS")
    # is not a finding. Reads the generated matrix JSON, no gradle daemon.
    'assert-flavor-count-prose.ps1'             = @('-Quiet')
    # S2935: the gate count the `fg` row of docs/BUILD_TEST_FAST_PATH.md states, against the table
    # it is standing in. That count moves in whichever ticket adds a gate - a ticket about
    # something else, written by someone with no reason to open that document - so it had gone
    # fifteen revisions stale in silence, 45 against a live 61. Per-ticket by Rule 33 for the same
    # reason: the only session that can fix it cheaply is the one adding the entry below. Reads
    # this file by AST, no gradle daemon and no child process.
    'assert-gate-count-prose.ps1'               = @('-Quiet')
    # S1259: android:id parity between layout-land and layout-w600dp siblings. w600dp beats
    # -land on wide landscape devices, so an id missing on one side is a latent findViewById
    # null (the recording-indicator include NPE). Static regex over 4 shared files, ~ms.
    'assert-layout-variant-id-parity.ps1'       = @('-Quiet')
    # S1282: a values-land / values-w600dp key that values-sw320dp already declares. smallestWidth
    # outranks orientation and sw320dp matches every device, so the declaration never applies -
    # invisible to the build and to lint, and it survived a year in dimens.xml. Parses a handful of
    # small values-*.xml files, no gradle daemon.
    'assert-qualifier-shadowing.ps1'            = @('-Quiet')
    # S1825: detect orphaned intermediate .flat compiled resources in merged_res/
    'assert-orphaned-merged-resources.ps1'       = @('-Quiet')
    # S1828: a pinned stream-catalog asset that would stop being published. External consumers
    # hard-code revisioned asset names and never roll forward, and nothing here deletes an asset -
    # so a pinned revision survives only because no action removes it. Reads the pinned names from
    # docs/STREAM_CATALOG_CONSUMERS.md and the revision defaults from the publisher; two file reads.
    'assert-stream-asset-revisions.ps1'         = @('-Quiet')
    # S1470: primary constructors approaching the 255 argument-slot ceiling. AppSettings crossed it
    # at one field per ticket; kotlinc and D8 both accepted the class and only the runtime verifier
    # refused it, so the build stayed green while the app could not start at all. Source parse of
    # two directories, no gradle daemon.
    'assert-ctor-arg-slots.ps1'                 = @('-Quiet')
    # S1254: settings-dump secret masking layers - @field:SensitiveSetting on hint-matching
    # AppSettings fields, the dump's annotation check, and the S1187 keep rules. The keep rule
    # vanished once and the leak is only visible in logs exported from a stranger's device.
    'assert-sensitive-settings-annotated.ps1'   = @('-Quiet')
    # S1338: the agent-memory index is injected into EVERY turn, so its size is billed against
    # the whole corpus. It was manually compacted twice and both compactions were undone within
    # a week at ~1.1 KB/day of regrowth - a budget that is not mechanical is not a budget. Ratchet
    # on one file's length plus two advisory scans over ~220 small .md files, no gradle daemon.
    'assert-memory-budget.ps1'                  = @()
    # S2517: every text file injected into EVERY request - CLAUDE.md, AGENTS.md, the active agent
    # definition - against a ceiling that only goes down. Measured 2026-09-04: the fixed preamble is
    # 37.8% of all billed cache_read and CLAUDE.md grew 2.35x in 35 days, having already been cleaned
    # once by S1340 and having doubled again since. Three byte counts, no gradle daemon.
    'assert-always-loaded-budget.ps1'           = @('-Quiet')
    # S1343: the mandatory `**Why:**` field on every tactical step, adopted on the pilot
    # verdict in dev/spec-form-pilot.jsonl. Count ratchet against a checked-in baseline
    # rather than a HEAD diff - PLAN/ is gitignored, so no phase file has a HEAD blob and
    # the diff-scoping every sibling gate uses returns nothing here. Parses ~300 small .md
    # files, no gradle daemon.
    'assert-tactical-step-form.ps1'             = @('-Quiet')
    # S2037: the `Line budget` column of a Files Touched table - a self-cost estimate CLAUDE.md
    # section 1 bans by name. It survived because the ban had nothing mechanical behind it: the
    # column is in no template and no script, so /spec-tech reproduced it from habit and nothing
    # failed - S1954's plan carried it in all four phase files, written the day the rule came into
    # force, and 49 live phase files carried it when S2037 measured. Absolute rather than ratcheted
    # (unlike the gate above over the same corpus): S2037's sweep zeroed the live files, so a
    # baseline would read 0 forever and only offer -UpdateBaseline as a way to bury a regression.
    # Judged per table CELL, so the prose that names the column - CLAUDE.md's own rule, S2037's
    # captured material - is not its first offender. Reads ~300 small .md files, no gradle daemon.
    'assert-no-line-budget.ps1'                 = @('-Quiet')
    # S1392: documentation flavor tables vs the generated capability snapshot. Nothing compared a
    # markdown matrix to build.gradle.kts before - the pin checker covers pins, the release
    # snapshot covers `standard` only - so docs/HOW_TO.md sat inverted against the lite gates on
    # two rows until a sibling ticket happened to derive wording from the gates instead. Parses one
    # JSON plus four small docs, no gradle daemon.
    'assert-flavor-matrix-docs.ps1'             = @('-Quiet')
    # S2635: a script that rewrites a file owned by a Code.* domain must take that domain, or two
    # sessions writing the same generated artifact are separated by nothing. S2615 built the helper
    # and adopted eleven generators; the question it carried was what stops the NEXT writer, since
    # the only convention that existed was written in one script's header, checked by nothing and
    # obeyed by no caller. Checks the registry's adoption exactly, plus a deliberately narrow
    # ratcheted heuristic for a writer nobody registered. Reads the .ps1 tree, no gradle daemon.
    'assert-code-domain-writers.ps1'            = @('-Quiet')
    # S2870: the gate-placement registry against where the gates are actually wired. Reads the
    # registry, the five runners and the gate tree - text only, no gradle daemon.
    'assert-gate-placement.ps1'                 = @('-Quiet')
    # S1489: a dependency the project retired, still named in prose. The pin gate above watches jsch
    # but compares the VERSION in one gated row, which was correct the whole time - a version
    # comparator cannot express "this name must not appear at all". So SSHJ survived the S0207/S0046
    # migration in nine documents including the published privacy policy in three locales, plus two
    # Kotlin comments. Regex over docs, dev, store_assets and the two source trees, no gradle daemon.
    'assert-retired-dependency-names.ps1'       = @('-Quiet')
    # S1399: a notification small-icon setter handed an `R.drawable.` literal instead of the one
    # owner. The defect survived thirteen call sites in eleven classes purely by never being checked -
    # with no default to reach for, three background workers settled on the audio glyph and the owner
    # watched a music note while the app moved files on a schedule. Regex over the two source trees,
    # no gradle daemon.
    'assert-notification-small-icon.ps1'        = @('-Quiet')
    # S1552: the API 31+ data-extraction rules against the pre-31 backup rules. Two files express the
    # same intent for different platform versions with nothing tying them together, and the newer one
    # carried the generic value-resource root for its whole life - a valid resource file the build
    # packages and lint accepts, declaring no rule at all. So the settings DataStore and the
    # Keystore-encrypted credentials database were backed up and restored on every Android 12+ device
    # against the exclusions written down next door, which the owner saw as settings rolling back
    # after each reinstall. Parses two small XML files, no gradle daemon.
    'assert-backup-rules-consistent.ps1'        = @('-Quiet')
    # S1453: a test in the shared src/test set for a type that lives only in a flavor-scoped source
    # set. src/test compiles for EVERY flavor, so one misplaced test breaks unit-test COMPILATION on
    # every flavor mounting the disabled counterpart - the release-blocking permission-parity test
    # could not run at all on lite while that was true. Compiling lite's unit tests instead was
    # rejected: lite is the only flavor mounting src/cloudDisabled, so it sees half the defect class.
    # Parses one build file and two source trees, no gradle daemon.
    'assert-shared-test-flavor-scope.ps1'       = @('-Quiet')
    # S1540: the launcher reset lists the settings it restores by name, and nothing held that list.
    # A forgotten line compiles and passes every other gate, then reaches the user as a reset that
    # returns half the launcher to defaults and leaves the rest as it was - an inconsistent store, not
    # a cosmetic miss. Compares two source files as text, no gradle daemon.
    'assert-launcher-reset-coverage.ps1'        = @('-Quiet')
    # S1598: the closure facade prints a recovery hint under every failed gate, keyed by the
    # gate label. A label the hint registry never heard of fails mute, and the gap is invisible
    # until that gate next fails - which is precisely the moment the hint was needed. Reads two
    # files as text, no gradle daemon.
    'assert-gate-hints-sync.ps1'                = @()
    # S1604: five of the eleven registered hooks were documented nowhere an agent reads, and two
    # of those alter the tool call itself - one refuses a Grep/Glob, one rewrites Read input. An
    # agent refused by an undocumented guard cannot find out what refused it. Compares the two
    # settings files against docs/AGENT_HOOKS.md as text, no gradle daemon.
    'assert-hook-inventory.ps1'                 = @()
    # S1548: the two full digests of CLAUDE.md's numbered rules are written by hand and nothing
    # compared them, so copilot-instructions.md silently lacked five rules - including four whose
    # violation a hook refuses outright. Reads four markdown files as text, no gradle daemon.
    'assert-rule-digest-sync.ps1'               = @()
    # S1639: a model whose Gson JSON outlives the process, with neither @SerializedName on its fields
    # nor a keep rule holding their names. R8 renames them per build, so the writer and the reader are
    # two different mappings and the record read after an update is wrong or half-null. The class
    # reached users six times (S0719, S0737, S1630, S1631, S1632, S1638) and was fixed one model at a
    # time because nothing tied "this goes to storage" to "its names are pinned" - the two facts live
    # in different files and usually different modules. Parses both source trees and the two
    # proguard-rules.pro files, no gradle daemon.
    'assert-gson-persistence-contract.ps1'      = @('-Quiet')
    # S1674: Room, DataStore and SharedPreferences can persist enum `name` values across an
    # update. R8 must keep the matching enum fields stable or a later version cannot `valueOf`
    # the earlier value. The gate inventories those boundaries against base release rules.
    'assert-enum-persistence-contract.ps1'      = @('-Quiet')
}

# S1338: these five accept -ChangedFiles and used to be invoked with no arguments at
# all, so every one of them scanned project-wide and went red on another ticket's
# in-flight drift - a 42% FAIL rate that trained the operator to read gate output as
# noise. Forwarded only when the caller actually supplied the parameter, so a release
# or CI run keeps the strict project-wide judgement.
$changedFilesAware = @(
    'assert-source-gates.ps1',
    'assert-listener-symmetry.ps1',
    'assert-gson-persistence-contract.ps1',
    'assert-device-ready-module.ps1',
    'assert-gate-placement.ps1'
)

# Build the work list first so a MISSING gate is settled without spawning anything, and so
# every item carries the table index - the one thing that restores table order after the
# children finish out of order.
# S2693: $split is the whole condition for the two-verdict form. A gate's scope is not a property
# of the gate - it is a property of THIS invocation: the same gate judges the caller's files when
# it was handed them and the whole tree when it was not, which is ADR-2 of S2693 (the area a gate
# judges follows the caller). So the classification is computed here, beside the forwarding
# decision it mirrors, and never looked up from a second list that could drift away from it.
$split = [bool]$ChangedFiles
$index = 0
$work = [System.Collections.Generic.List[object]]::new()
$missing = [System.Collections.Generic.List[object]]::new()
foreach ($entry in $gates.GetEnumerator()) {
    $judgesSet = $split -and ($entry.Key -in $changedFilesAware)
    $scope = if (-not $split) { $null } elseif ($judgesSet) { 'set' } else { 'tree' }
    $path = Join-Path $PSScriptRoot $entry.Key
    if (-not (Test-Path $path)) {
        $missing.Add([pscustomobject]@{ Index = $index; Gate = $entry.Key; Status = 'MISSING'; ExitCode = 2; Ms = 0; Output = ''; Scope = $scope })
        $index++
        continue
    }
    $extraArgs = @($entry.Value)
    if ($judgesSet) {
        # S1184: comma-joined, never one element per file - `pwsh -File` binds only the
        # first element to [string[]] and rejects the rest as positional arguments.
        $extraArgs += @('-ChangedFiles', ($ChangedFiles -join ','))
    }
    $work.Add([pscustomobject]@{ Index = $index; Gate = $entry.Key; Path = $path; GateArgs = $extraArgs; Scope = $scope })
    $index++
}

# S2451: 45 read-only scans against 20 logical cores. Two are held back from the pool - one for
# this aggregator, one so the box stays usable - and the ceiling caps memory, since every child
# is a whole interpreter. The critical path is the slowest single gate (37.6 s for
# assert-source-gates), so raising the throttle past that buys nothing.
$throttle = if ($ThrottleLimit -gt 0) {
    $ThrottleLimit
}
else {
    [Math]::Max(2, [Math]::Min(12, [Environment]::ProcessorCount - 2))
}

$runOne = {
    # 2>&1 folds the child's stderr into the captured text. Without it a gate's FAIL reason -
    # written as a Write-Error by every gate in the table - would bypass the buffer and land in
    # the console detached from the block it belongs to.
    $ErrorActionPreference = 'Continue'
    $item = $_
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $captured = & $pwshExe -NoProfile -File $item.Path -Gate @($item.GateArgs) 2>&1 | Out-String
    $code = [int]$LASTEXITCODE
    $sw.Stop()
    # S3075: 3 is "this run judged nothing" - lib/absent-input.ps1 when a gitignored root is not in
    # the checkout, lib/fixed-input-scope.ps1 when the changed set charges the gate nothing. Folding
    # it into FAIL stated one published fact (PLAN/ and .claude/ are not published) as nine findings.
    [pscustomobject]@{
        Index    = $item.Index
        Gate     = $item.Gate
        Status   = switch ($code) { 0 { 'PASS' } 3 { 'SKIP' } default { 'FAIL' } }
        ExitCode = $code
        Ms       = [int]$sw.Elapsed.TotalMilliseconds
        Output   = $captured
        Scope    = $item.Scope
    }
}

$completed = if ($Sequential) {
    @($work | ForEach-Object -Process $runOne)
}
else {
    # $pwshExe is resolved in this scope, so the parallel body needs it passed in explicitly;
    # a runspace does not inherit the caller's variables.
    $parallelBody = [scriptblock]::Create(('$pwshExe = ' + "'$pwshExe'" + "`n") + $runOne.ToString())
    @($work | ForEach-Object -Parallel $parallelBody -ThrottleLimit $throttle)
}

# S2693 re-audit: the needles a red tree gate is searched for. Both slash forms of the repo-relative
# path, never the bare file name - a name like README.md would promote on someone else's finding.
$changedNeedles = @()
if ($split) {
    $repoRootPath = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path.TrimEnd('\', '/')
    foreach ($entry in @($ChangedFiles)) {
        foreach ($raw in @([string]$entry -split ',')) {
            $rel = $raw.Trim().Replace('\', '/')
            if (-not $rel) { continue }
            if ($rel.StartsWith('./')) { $rel = $rel.Substring(2) }
            $rootFwd = $repoRootPath.Replace('\', '/')
            if ($rel.StartsWith($rootFwd, [System.StringComparison]::OrdinalIgnoreCase)) {
                $rel = $rel.Substring($rootFwd.Length).TrimStart('/')
            }
            $changedNeedles += @($rel, $rel.Replace('/', '\'))
        }
    }
}
function Test-OutputNamesChangedFile {
    param([string]$Output)
    if (-not $Output) { return $false }
    foreach ($needle in $changedNeedles) {
        if ($Output.IndexOf($needle, [System.StringComparison]::OrdinalIgnoreCase) -ge 0) { return $true }
    }
    return $false
}

$results = [System.Collections.Generic.List[object]]::new()
foreach ($r in (@($completed) + @($missing) | Sort-Object Index)) {
    if ($r.Output) { Write-Host $r.Output.TrimEnd() }
    $rowScope = $r.Scope
    if ($rowScope -eq 'tree' -and $r.Status -eq 'FAIL' -and (Test-OutputNamesChangedFile -Output $r.Output)) {
        $rowScope = 'set-named'
    }
    $r.Scope = $rowScope
    $results.Add([pscustomobject]@{ Gate = $r.Gate; Status = $r.Status; Ms = $r.Ms; Scope = $r.Scope })
    # Written here, by the parent, never inside a runspace: Write-GateTelemetryRecord appends to
    # one JSONL file and swallows its own failures, so a concurrent append would lose records in
    # silence rather than report an error.
    $telemetry = @{
        Runner    = 'assert-fast-gates'
        Gate      = $r.Gate
        Status    = $r.Status
        ExitCode  = $r.ExitCode
        ElapsedMs = $r.Ms
    }
    if ($r.Scope) { $telemetry['Scope'] = $r.Scope }
    Write-GateTelemetryRecord @telemetry
}

if ($IncludeDetekt) {
    $detektArgs = @('-NoProfile', '-File', (Join-Path $PSScriptRoot 'assert-detekt.ps1'), '-Gate')
    if ($PSBoundParameters.ContainsKey('Module')) { $detektArgs += @('-Module', $Module) }
    if ($ChangedFiles) { $detektArgs += @('-ChangedFiles', ($ChangedFiles -join ',')) }
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    & $pwshExe @detektArgs | Write-Host
    $sw.Stop()
    $detektExitCode = [int]$LASTEXITCODE
    $status = ($detektExitCode -eq 0) ? 'PASS' : 'FAIL'
    # Handed the changed set above, so under -ChangedFiles it narrows its findings to those files
    # and belongs to the set verdict.
    $detektScope = if ($split) { 'set' } else { 'tree' }
    $results.Add([pscustomobject]@{ Gate = 'assert-detekt.ps1'; Status = $status; Ms = [int]$sw.Elapsed.TotalMilliseconds; Scope = $detektScope })
    $detektTelemetry = @{
        Runner    = 'assert-fast-gates'
        Gate      = 'assert-detekt.ps1'
        Status    = $status
        ExitCode  = $detektExitCode
        ElapsedMs = [int]$sw.Elapsed.TotalMilliseconds
    }
    $detektTelemetry['Scope'] = $detektScope
    . (Join-Path $PSScriptRoot 'lib/detekt-report.ps1')
    $detektRepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
    $detektModules = if ($PSBoundParameters.ContainsKey('Module')) { @($Module) } else { @('app_v2', 'wear') }
    $detektFindings = Get-DetektFindings -RepoRoot $detektRepoRoot -Modules $detektModules
    $detektTelemetry['FindingDetailsAvailable'] = [bool]$detektFindings.Ok
    if ($detektFindings.Ok) {
        $detektTelemetry['FindingCount'] = @($detektFindings.Findings).Count
        $detektTelemetry['FindingPaths'] = @($detektFindings.Findings | ForEach-Object { $_.File } |
                Sort-Object -Unique)
    }
    Write-GateTelemetryRecord @detektTelemetry
}

function Write-GateBlock {
    param(
        [Parameter(Mandatory = $true)][string]$Title,
        [Parameter(Mandatory = $true)][AllowEmptyCollection()][object[]]$Rows
    )

    Write-Host $Title -ForegroundColor Cyan
    foreach ($r in $Rows) {
        $color = switch ($r.Status) { 'PASS' { 'Green' } 'FAIL' { 'Red' } default { 'Yellow' } }
        $named = if ($r.Scope -eq 'set-named') { ' - names a file in your set' } else { '' }
        Write-Host ("  {0,-40} {1} ({2} ms){3}" -f $r.Gate, $r.Status, $r.Ms, $named) -ForegroundColor $color
    }
    # S3075: SKIP is not a failure and not a pass - the gate did not run, so it is reported in its
    # own block below and never contributes to a block's failure list.
    return @($Rows | Where-Object { $_.Status -notin @('PASS', 'SKIP') })
}

# S3075: the skipped gates leave the two scope blocks entirely. Keeping them in place would make
# every reader check the status column of a line that has nothing to say about the tree, and the
# whole reason the split exists is that a summary nobody reads closely catches nothing.
$skippedRows = @($results | Where-Object { $_.Status -eq 'SKIP' })
$results = [System.Collections.Generic.List[object]]@($results | Where-Object { $_.Status -ne 'SKIP' })

Write-Host ''
if ($split) {
    $setRows = @($results | Where-Object { $_.Scope -in @('set', 'set-named') })
    $treeRows = @($results | Where-Object { $_.Scope -notin @('set', 'set-named') })
    $setFailures = @(Write-GateBlock -Title 'assert-fast-gates summary - YOUR SET:' -Rows $setRows)
    Write-Host ''
    $treeFailures = @(Write-GateBlock -Title 'assert-fast-gates summary - THE TREE (advisory):' -Rows $treeRows)
    $failed = $setFailures.Count
}
else {
    $allFailures = @(Write-GateBlock -Title 'assert-fast-gates summary:' -Rows @($results))
    $treeFailures = @()
    $failed = $allFailures.Count
}

if ($skippedRows.Count -gt 0) {
    Write-Host ''
    [void](Write-GateBlock -Title 'assert-fast-gates summary - NOT RUN (input absent):' -Rows $skippedRows)
    Write-Host '  Each line above printed the path it is missing. A gitignored root - PLAN/, .claude/ -' -ForegroundColor Yellow
    Write-Host '  is absent by design on a fresh clone, a release worktree and a CI runner; -FailOnSkipped' -ForegroundColor Yellow
    Write-Host '  makes these fatal for a caller that requires full coverage.' -ForegroundColor Yellow
}

$batchStopwatch.Stop()
$batchMs = [int]$batchStopwatch.Elapsed.TotalMilliseconds
Write-Host ("  {0,-40} {1} ms (batch wall clock)" -f '(batch)', $batchMs) -ForegroundColor Cyan

$batchScope = if ($split) { 'split' } else { $null }
$batchArgs = @{ Runner = 'assert-fast-gates'; ElapsedMs = $batchMs }
if ($batchScope) { $batchArgs['Scope'] = $batchScope }

if ($treeFailures.Count -gt 0) {
    Write-Host ''
    Write-Host ("assert-fast-gates: {0} project-wide gate(s) are red on work outside your set - {1}." -f
        $treeFailures.Count, (($treeFailures | ForEach-Object { $_.Gate }) -join ', ')) -ForegroundColor Yellow
    Write-Host '  Advisory: these judge the whole tree, which carries other sessions. They stay fatal' -ForegroundColor Yellow
    Write-Host '  in assert-release-scope-gates.ps1 and in any run given no -ChangedFiles.' -ForegroundColor Yellow
}

if ($FailOnSkipped -and $skippedRows.Count -gt 0) {
    $failed += $skippedRows.Count
    Write-Host ("assert-fast-gates: -FailOnSkipped - {0} gate(s) could not be verified: {1}." -f
        $skippedRows.Count, (($skippedRows | ForEach-Object { $_.Gate }) -join ', ')) -ForegroundColor Red
}

if ($failed -gt 0) {
    Write-GateBatchTelemetryRecord @batchArgs -ExitCode 1
    Write-Host "assert-fast-gates: FAIL ($failed gate(s))." -ForegroundColor Red
    exit 1
}
Write-GateBatchTelemetryRecord @batchArgs -ExitCode 0
$verdict = if ($split) { 'PASS (every gate judging your set is green).' } else { 'PASS (all fast gates green).' }
if ($skippedRows.Count -gt 0) { $verdict += " $($skippedRows.Count) gate(s) NOT RUN - see the block above." }
Write-Host "assert-fast-gates: $verdict" -ForegroundColor Green
exit 0
