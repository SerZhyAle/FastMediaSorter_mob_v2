# Recovery hints for the closure facade (S1598).
#
# Key   = the gate label exactly as scripts/post-change.ps1 prints it.
# Repro = one command that runs THAT gate alone, so the fix loop costs seconds
#         instead of a full facade run.
# Fix   = one sentence naming what to do with the finding.
#
# A label with no entry here prints without a hint - that is not an error, but
# scripts/quality/assert-gate-hints-sync.ps1 reports the gap so it does not go
# unnoticed until the gate next fails.
#
# This file is DATA on purpose (S1598 ADR-2): registering a gate must never mean
# editing the facade's output logic.

@{
    'ticket-log-audit' = @{
        Repro = 'pwsh -NoProfile -File scripts/quality/assert-no-ticket-logs.ps1'
        Fix   = 'Each listed File:Line holds a ticket id in a permanent log, or a probe whose ticket left BlockNeedUserTest - delete the Timber line, or flip the ticket back if the device test is still pending.'
    }

    'neuroslop-gate' = @{
        Repro = 'pwsh -NoProfile -File scripts/quality/assert-neuroslop.ps1 -Gate -ChangedFiles "<your,files>"'
        Fix   = 'A banned pattern grew in your files (CLAUDE.md Rule 19): trivial comment, empty catch, hardcoded layout colour, lifecycle-unsafe collect, GlobalScope, non-Timber log, shipped TODO(), long dash. Remove it - the baseline only ratchets down.'
    }

    'listener-symmetry-gate' = @{
        Repro = 'pwsh -NoProfile -File scripts/quality/assert-listener-symmetry.ps1 -Gate -ChangedFiles "<your,files>"'
        Fix   = 'A register/add call in your files has no matching unregister/remove on the paired lifecycle callback - add the removal, or move both to the same owner.'
    }

    'activity-locale-wrapper-gate' = @{
        Repro = 'pwsh -NoProfile -File scripts/quality/assert-activity-locale-wrapper.ps1 -Gate -ChangedFiles "<your,files>"'
        Fix   = 'An Activity in your files resolves resources outside the app locale wrapper, so it shows the framework configuration language instead of the one the user chose. Extend BaseActivity, or override attachBaseContext with super.attachBaseContext(LocaleHelper.applyLocale(newBase)). If it genuinely must not wrap its context (the print trampoline is the one such case), add it to scripts/quality/activity-locale-wrapper-baseline.txt with the reason - a row with no reason fails the gate.'
    }

    'quantity-format-seam-gate' = @{
        Repro = 'pwsh -NoProfile -File scripts/quality/assert-quantity-format-seam.ps1 -Gate -ChangedFiles "<your,files>"'
        Fix   = 'A user-facing quantity in your files is formatted past the seam, so the device setting or the locale decides its clock length or its scale instead of the app UnitSystem. Route it through QuantityFormatter (phone) or WearUnitDateTimeFormatter (watch). If it really is an INTERNAL timestamp - a file name, a log stamp, an export field or a parsed input - add the file to scripts/quality/quantity-format-seam-baseline.txt with the reason, because changing one of those breaks parsing or uniqueness. A finding naming PATTERN_* instead is the two modules disagreeing: UnitScale and WearUnitDateTimeFormatter mirror four literals and change in one edit.'
    }

    'ci-cost-map' = @{
        Repro = 'pwsh -NoProfile -File scripts/quality/assert-ci-cost-map.ps1'
        Fix   = 'The CI cost map in docs/BUILD_VS_RELEASE.md disagrees with .github/workflows - the workflow file is the truth and the table is what moves. The finding names the workflow, the divergence class (row, jobs, trigger, branch, debug) and which cell to correct.'
    }

    'doc-pin-drift' = @{
        Repro = 'pwsh -NoProfile -File scripts/quality/assert-doc-pin-drift.ps1'
        Fix   = 'A version pin quoted in the docs no longer matches the build files - update the doc line to the value the report names, never the other way round.'
    }

    'doc-house-style' = @{
        Repro = 'pwsh -NoProfile -File scripts/quality/assert-doc-house-style.ps1'
        Fix   = 'A documentation page carries a typographic dash in prose. Replace it with the house-style hyphen: pwsh -NoProfile -File scripts/utils/fix-house-style.ps1 -Area Prose -Rules long-dash -Path <page> -Apply. The generated FEATURES_noLegal pages are a parked draft, not an excuse.'
    }

    'memory-budget-gate' = @{
        Repro = 'pwsh -NoProfile -File scripts/quality/assert-memory-budget.ps1 -Gate'
        Fix   = 'The always-loaded agent-memory index is over its ceiling, and every turn of every session pays for the overshoot. Split the biggest SECTION into a second-level .claude/agent-memory/android-rd-specialist/INDEX_<topic>.md and leave one pointer line behind - measure first (bytes per section), never trim a hook mid-sentence, because a squeezed pointer costs its bytes while saying nothing. Raising the ceiling is refused by the gate itself.'
    }

    'always-loaded-budget-gate' = @{
        Repro = 'pwsh -NoProfile -File scripts/quality/assert-always-loaded-budget.ps1 -Gate'
        Fix   = 'A file injected into EVERY request is over its ceiling, so the overshoot is billed to the whole corpus rather than to the change that added it. Move the RATIONALE, not the rule: incident narrative - dates, measured values, ticket ids - belongs in the refusal text of the gate that enforces the rule, where it is read exactly when the rule is broken. A rule with no mechanical gate keeps its rationale, because there the rationale IS the mechanism. Deleting a rule to fit the ceiling is forbidden (S2517 ADR-4). Once the file is smaller, run the same script with -UpdateBaseline; raising a ceiling is refused by the gate itself.'
    }

    'doc-script-references' = @{
        Repro = 'pwsh -NoProfile -File scripts/quality/assert-script-references.ps1 -Docs'
        Fix   = 'A document names a .ps1 that does not exist. Correct the path, or say so on its line: External: for a script shipped outside this repository, Historical: for a retired one. Adding the line to doc-script-reference-baseline.txt is not a fix.'
    }

    'settings-doc-sync-gate' = @{
        Repro = 'pwsh -NoProfile -File scripts/quality/assert-settings-doc-sync.ps1'
        Fix   = 'A settings surface changed without regenerating its docs (CLAUDE.md Rule 22) - regenerate docs/settings/settings-manifest.json and docs/SETTINGS_REFERENCE*.md, and annotate the new key. Exit 3 is not that (S2604): it means every stage passed and only the project-wide reference render diverged, while no file you changed feeds that render - the drift belongs to whichever ticket last moved the manifest, the annotations or an availability module, and regenerating it would commit their user-visible text under your change. Leave it; re-run with no -ChangedFiles for the project-wide verdict.'
    }

    'detekt-baseline-absorption' = @{
        Repro = 'pwsh -NoProfile -File scripts/quality/assert-detekt-baseline-absorption.ps1 -Gate'
        Fix   = 'A committed detekt baseline absorbed live findings wholesale - re-freeze only the intended entries, or fix the findings instead of accepting them.'
    }

    'flavor-matrix-doc-gate' = @{
        Repro = 'pwsh -NoProfile -File scripts/quality/assert-flavor-matrix-docs.ps1'
        Fix   = 'A doc restates the flavor grid from memory instead of the generated one - regenerate docs/FLAVOR_MATRIX.md via scripts/docs/generate-flavor-matrix.ps1 and align the offending line to it.'
    }

    'script-cheatsheet-sync-gate' = @{
        Repro = 'pwsh -NoProfile -File scripts/quality/assert-script-cheatsheet-sync.ps1'
        Fix   = 'A repository script was added, renamed or removed without re-rendering the cheatsheet - regenerate it, do not hand-edit the render target.'
    }

    'new-lexeme-count' = @{
        Repro = 'pwsh -NoProfile -File scripts/utils/list-new-lexemes.ps1'
        Fix   = 'Advisory only - a new string does not yet reach all thirteen declared locales. Nothing to fix at close time: the pre-release step 0.8 translates the whole release in one bulk round trip. Fill values early only if they are already known, via set-android-string.ps1 -Translations.'
    }

    'strings-audit' = @{
        Repro = 'pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "<prefix>"'
        Fix   = 'A string key is missing from EN, RU or UK - add the missing locales via scripts/utils/set-android-string.ps1 -Action add, which enforces parity.'
    }

    'string-format-gate' = @{
        Repro = 'pwsh -NoProfile -File scripts/quality/assert-string-format.ps1 -Gate'
        Fix   = 'Format specifiers disagree between locales of the same key - make every locale carry the same set, in positional form when there is more than one.'
    }

    'oss-notices-gate' = @{
        Repro = 'pwsh -NoProfile -File scripts/quality/assert-oss-notices.ps1'
        Fix   = 'A dependency changed without its third-party notice - regenerate the notices artifact so the shipped package keeps declaring what it bundles.'
    }

    'detekt-preflight' = @{
        Repro = 'pwsh -NoProfile -File scripts/quality/detekt-scoped.ps1 -ChangedFiles "<your,files>"'
        Fix   = 'detekt found NEW findings in your files - fix them in the source; never widen the baseline to absorb them.'
    }

    'detekt-gate' = @{
        Repro = 'pwsh -NoProfile -File scripts/quality/assert-detekt.ps1 -Gate -ChangedFiles "<your,files>"'
        Fix   = 'detekt found findings above the committed baseline - fix the rule lines printed above; ReturnCount, MagicNumber and the 120-char limit are the usual three.'
    }

    'acceptance-probe-gate' = @{
        Repro = 'pwsh -NoProfile -File scripts/quality/assert-ticket-acceptance-probes.ps1 -Gate'
        Fix   = 'An acceptance predicate names a literal that no source line carries - grep the literal in its Timber form and correct the predicate to what the code prints.'
    }

    'fgs-notification-gate' = @{
        Repro = 'pwsh -NoProfile -File scripts/quality/assert-fgs-notifications.ps1 -Gate'
        Fix   = 'A foreground-service notification uses a ?attr-tinted small icon or skips ensuring its channel - use a solid drawable and ensure the channel before startForeground.'
    }

    'focus-highlight-gate' = @{
        Repro = 'pwsh -NoProfile -File scripts/quality/assert-focus-highlight.ps1 -Gate'
        Fix   = 'An interactive view in your layout has no visible focus indication (CLAUDE.md Rule 16) - apply the project focus frame; the baseline only ratchets down.'
    }

    'dialog-cancel-style-gate' = @{
        Repro = 'pwsh -NoProfile -File scripts/quality/assert-dialog-cancel-style.ps1 -Gate'
        Fix   = 'A confirm/cancel pair uses a one-off button style - switch to Widget.FastMediaSorter.Button.DialogConfirm / DialogCancel / DialogDestructive.'
    }

    'rtl-layout-attrs-gate' = @{
        Repro = 'pwsh -NoProfile -File scripts/quality/assert-rtl-layout-attrs.ps1 -Gate'
        Fix   = 'A layout uses left/right attributes without their start/end counterpart - add the start/end form so RTL locales lay out correctly.'
    }

    'orientation-implied-feature-gate' = @{
        Repro = 'pwsh -NoProfile -File scripts/quality/assert-orientation-implied-feature.ps1 -Gate'
        Fix   = 'A manifest orientation lock implies a hardware feature requirement that would shrink device reach - declare the feature as not required.'
    }

    'orientation-layout-pairing-gate' = @{
        Repro = 'pwsh -NoProfile -File scripts/quality/assert-orientation-layout-pairing.ps1 -Gate'
        Fix   = 'An activity absorbs orientation in configChanges while owning a landscape layout, so that layout never applies on rotation - stop absorbing it, re-apply the variant in code and record the exemption with its reason in scripts/quality/orientation-layout-pairing-exceptions.txt, or delete the layout if it encodes no difference.'
    }

    'layout-variant-id-parity-gate' = @{
        Repro = 'pwsh -NoProfile -File scripts/quality/assert-layout-variant-id-parity.ps1 -Gate'
        Fix   = 'One copy of a layout dropped a view id its sibling declares - re-declare it. [config-variant] means layout-land and layout-w600dp disagree, and both must match. [flavor-override] means a flavor copy lost an id that src/main declares and shared code binds, so add it back; the reverse is allowed and extra ids in an override are never a finding.'
    }

    'focus-parity-gate' = @{
        Repro = 'pwsh -NoProfile -File scripts/quality/assert-focus-parity.ps1 -Gate'
        Fix   = 'A portrait layout declares nextFocus* while its existing layout-land counterpart carries none, so the D-pad chain dies on rotation (S3254). Carry the portrait declarations into the counterpart; a counterpart with no chain of its own should not exist - delete it rather than leave it stale (spec 5.1 prefers no counterpart to a stale one).'
    }

    'all-features-gate' = @{
        Repro = 'pwsh -NoProfile -File scripts/all_features/validate.ps1'
        Fix   = 'The capability inventory is invalid or lost records - fix the JSONL row; add capabilities through scripts/all_features/add.ps1, never by hand.'
    }

    'howto-settings-paths-gate' = @{
        Repro = 'pwsh -NoProfile -File scripts/quality/assert-howto-settings-paths.ps1 -Gate'
        Fix   = 'A guide names a settings path that no longer exists - correct the path to the one the settings manifest records, in every locale of that guide.'
    }

    'ctor-arg-slots-gate' = @{
        Repro = 'pwsh -NoProfile -File scripts/quality/assert-ctor-arg-slots.ps1'
        Fix   = 'A primary constructor is at or near the JVM ceiling of 255 descriptor slots, which no compiler reports and the device verifier refuses at class load - the app dies in Application.onCreate with a VerifyError and every copy(..) of that class is equally dead. Move a cohesive group of properties into a nested data class held as one field (see LauncherSettings in AppSettings), which costs one slot instead of one per field. Never buy a single slot back: the next ordinary field addition crosses the line again.'
    }

    'migration-schema-conformance-gate' = @{
        Repro = 'pwsh -NoProfile -File scripts/quality/assert-migration-schema-conformance.ps1 -List'
        Fix   = 'A Room migration disagrees with the exported schema Room validates the upgraded database against, and that comparison happens on the user device during the first launch after an update - a mismatch there deletes the database (S2251 cost the owner 20 resources, 26 network credentials, 7 favourites and a 139-cell desktop on 2026-09-01). Read the named dimension: column-name means the ALTER TABLE column is spelled differently from the entity property (Room is case- and underscore-exact); column-default means the entity declares an @ColumnInfo(defaultValue = ..) the SQL does not write; not-null means the nullability differs or a NOT NULL column was added without a DEFAULT, which SQLite refuses outright; registration means a migration exists but DatabaseModule.addMigrations() never lists it, so the hop throws. Every finding names the module it belongs to - read that first, because the repository has more than one Room database and the fix belongs in the named one. Fix the SQL or the entity so both say the same thing, rebuild that module to regenerate its exported <module>/schemas/<db-fqcn>/<version>.json, then re-run. Never baseline a migration that has not shipped.'
    }

    'migration-test-pairing-gate' = @{
        Repro = 'pwsh -NoProfile -File scripts/quality/assert-migration-test-pairing.ps1 -List'
        Fix   = 'A Room migration has no instrumented migration test. The finding names its module - put the test in THAT module''s instrumented database directory, not the phone''s: app_v2/src/androidTest/java/com/sza/fastmediasorter/data/local/db/ for app_v2, wear/src/androidTest/java/com/sza/fastmediasorter/wear/data/db/ for wear. Name it <Database>Migration<N>To<M>Test.kt beside its siblings: create the database at <N>, seed a row, call helper.runMigrationsAndValidate(TEST_DB, <M>, true, MIGRATION_<N>_<M>) - that call is the same schema comparison the device performs on update - and assert the seeded row survived. Verify it compiles with .\a.ps1 fa for app_v2 or .\a.ps1 faw for wear; fa compiles the phone set only, so quoting it under a wear change is a verdict about the other module.'
    }

    'gson-persistence-contract-gate' = @{
        Repro = 'pwsh -NoProfile -File scripts/quality/assert-gson-persistence-contract.ps1'
        Fix   = 'A model whose Gson JSON outlives the process has no pinned wire names - annotate every property with @SerializedName, or keep its fields by name in that module''s proguard-rules.pro. An enum reported separately needs its constants pinned, which neither form on the containing model covers. If the model genuinely does not need pinning, add a line with a written justification to scripts/quality/gson-persistence-exemptions-baseline.txt.'
    }

    'launcher-reset-coverage-gate' = @{
        Repro = 'pwsh -NoProfile -File scripts/quality/assert-launcher-reset-coverage.ps1 -Gate'
        Fix   = 'A launcher preference is not covered by the reset path - add it there so a reset leaves no stale state behind.'
    }

    'wear-settings-parity-gate' = @{
        Repro = 'pwsh -NoProfile -File scripts/quality/assert-wear-settings-parity.ps1 -Gate'
        Fix   = 'A watch setting exists on one side of the phone/watch pair and not the other. The message names the missing side: add the field to that WearSettingsPayload copy, the key to the watch DataStore, the entry to the other WearSettingsRegistry copy, or the row to SettingsDocScopeCatalog.wearEntries. A setting that is deliberately one-sided is legal, but only with a written exceptionReason on its registry entry - without one it is indistinguishable from a forgotten side.'
    }

    'gate-placement-gate' = @{
        Repro = 'pwsh -NoProfile -File scripts/quality/assert-gate-placement.ps1 -Gate'
        Fix   = 'The gate-placement registry (scripts/quality/gate-placement.jsonl) disagrees with where the gates are actually wired. Four shapes. (1) "has no registry record" - you added a gate: add its row, naming its scope class, who decided it and why. CLAUDE.md Rule 33 requires a new gate to name its class at birth, and unnamed means per-ticket. (2) "declared per-ticket but scripts/post-change.ps1 does not reference it" - the gate runs only when someone types .\a.ps1 fg. Membership in assert-fast-gates.ps1 does NOT satisfy per-ticket: that is the shape that crashed the app (S2300) and deleted the owner''s database (S2306). Wire it into post-change.ps1, or change the row to the class it really has. (3) "declared X but its wiring says Y" - a gate moved between runners without the registry being updated; update whichever is wrong, and if the placement genuinely moved, record the deciding ticket and the reason so the next reader is not left guessing. (4) "which is not on disk" - a row survived its gate; delete it. Scope classes and the runner that satisfies each are in the gate''s own header and in docs/DEV_OPS.md.'
    }

    'wear-canonical-key-parity-gate' = @{
        Repro = 'pwsh -NoProfile -File scripts/quality/assert-wear-canonical-key-parity.ps1 -Gate'
        Fix   = 'A WearAppId canonicalKey is neither an InternalRouteCatalog KEY_* value nor a declared watch-only program. The key is the phone''s address for the same program, which is what will let the watch list be absorbed into the phone''s registry without renaming a key already saved on a device - so if the phone has this program, spell the key exactly as the phone spells it, and rename on the watch rather than on the phone (the phone key is saved in launcher cells and app-launch panel layouts; the watch key is saved nowhere). If the program exists on the watch alone, that is legal: add its key to scripts/quality/wear-canonical-key-watch-only-baseline.txt with the reason there is no counterpart. The other two shapes are a spent baseline row - one naming a key the enum no longer has, or one whose program the phone has since gained - and both are fixed by deleting the row.'
    }

    'wear-walk-contract-gate' = @{
        Repro = 'pwsh -NoProfile -File scripts/quality/assert-wear-walk-contract.ps1 -Gate'
        Fix   = 'A wear screen in your changed set is classified in neither list of scripts/devtest/wear-prerelease-screens.json, so the watch pre-release walk neither opens it nor declares it skipped - it would ship unwalked in silence. Add it to screens[] with the label that opens it and a marker the destination renders and its parent does not (expect + expectRes, and mind that most watch screens repeat their parent chip label as their title, which makes the title useless as a marker), or to excluded[] with a reason from the closed set: not-a-destination, arg-external, gesture-only, timed, needs-seeded-content, pre-graph-gate, no-static-marker, absent-from-this-flavor. The other shapes are drift in an existing entry - a renamed string, a resource no composable renders, or a screen name that no longer exists. Note the scope (S2621): this per-ticket run judges only screens declared in your changed files, plus everything if the list itself is in the set, so a neighbour''s unclassified screen is not yours to fix; the whole-tree run is .\a.ps1 fg.'
    }

    'ui-sweep-catalog-gate' = @{
        Repro = 'pwsh -NoProfile -File scripts/quality/assert-ui-sweep-catalog.ps1 -Gate'
        Fix   = 'A phone screen in your changed set is mapped to no entry of scripts/devtest/ui-sweep-screens.json and named in no excluded list, so the UI sweep neither walks it nor declares it skipped - it would ship unwalked in silence. Add it to the walk list with the label that opens it and a marker the destination renders (expect + expectRes, where the resolved string must CONTAIN expect, because the sweep matches the UI tree by substring), or to the matching excluded list with a reason from the closed set the file declares in reasonSet. The other three shapes are drift in an existing entry: an expectRes that no longer resolves, a marker alive in resources but referenced by no layout, menu or source under app_v2/src/main (so it could never match a node), and an exclusion carrying no reason. Scope (S2824): this per-ticket run is fixed-input - fatal only when the set carries the screen list, dev/ACTIVITY_CATALOG/app_v2.jsonl or docs/settings/settings-manifest.json, advisory otherwise, so a neighbour''s unclassified screen is not yours to fix. The whole-tree run is .\a.ps1 fg.'
    }

    'bridge-scenario-coverage-gate' = @{
        Repro = 'pwsh -NoProfile -File scripts/quality/assert-bridge-scenario-coverage.ps1 -Gate'
        Fix   = 'A Data Layer route declared in either WearDataLayerPaths.kt is named by no scenario in scripts/devtest/bridge-scenarios.json and explained by no excluded[] record, so the joint-device campaign will never measure it - the S2861 gap class that hid two of thirty-eight routes, both of them exactly where the campaign''s only confirmed defect lived. Add the route to the new scenario''s channels[] (the string is the verdict marker logcat filters on), or add an excluded[] record with the reason no scenario will ever cover it. The other shapes: a route declared in one catalog only (the mirror is broken - declare it on both sides or drop it), an excluded record with no reason, an excluded path no catalog declares, or a path both scenario-named and excluded. Scope (S2723): the per-ticket run judges only routes your changed set owns - a catalogue or registry file in the set, or a registry record your edit touched; a neighbour''s unclassified route is not yours to fix. The whole-tree run is .\a.ps1 fg.'
    }

    'wear-mirrored-strings-gate' = @{
        Repro = 'pwsh -NoProfile -File scripts/quality/assert-wear-mirrored-strings.ps1 -Gate -Scope Authored'
        Fix   = 'Two failure shapes, and the message tells them apart. (1) "exists in both modules but ... classifies it neither Mirrored nor Independent" - a new key now present in both modules is undeclared: add it to scripts/quality/wear-mirrored-strings.psd1 as Mirrored if the two sides must read the same, or Independent with a Reason if they are meant to word it differently. Only the author who added the key knows which it was, which is why this is asked at closure and not later. (2) "differs in values / values-ru / values-uk" - a pair declared Mirrored diverged in a locale the owner writes by hand: bring the two copies into line, or move the pair to Independent with a Reason if the divergence is deliberate. Note the scope (S2562): this per-ticket run compares ONLY the authored locales en/ru/uk. Divergence in the ten batch-translated locales is not reported here at all and is not yours to fix - those texts come from a release-boundary batch translation that app_v2 and wear make separately, so they are compared by assert-prerelease-content-gates.ps1 once that import has run. To see the full picture anyway, re-run the repro with -Scope All.'
    }

    # S1939: hints for icon-inventory-sync, doc-icons-sync and device-profile-matrix were removed
    # with the gates themselves - they moved to scripts/quality/assert-release-scope-gates.ps1, which
    # prints each child's own remediation line rather than reading this table.
    'doc-pins-sync' = @{
        Repro = 'pwsh -NoProfile -File scripts/quality/generate-toolchain-pins.ps1'
        Fix   = 'The generated toolchain pins are stale - regenerate them; the generated block is a render target and is never hand-edited.'
    }

    'rule-digest-sync-gate' = @{
        Repro = 'pwsh -NoProfile -File scripts/quality/assert-rule-digest-sync.ps1'
        Fix   = 'A numbered CLAUDE.md rule is missing from a full digest - state it in the named file and cite it as the literal "Rule N"; a range like "Rules 24-29" does not count. Roles: dev/RULE_AND_SKILL_AUTHORING.md "Rule mirroring contract".'
    }

    'document-registry' = @{
        Repro = 'pwsh -NoProfile -File scripts/document_registry/query.ps1 -ProductArea "<area>"'
        Fix   = 'Your changed set touches registered documents - read the named records and pass their ids back as -RegistryAck on the same run.'
    }

    'codex-transcript-hygiene-gate' = @{
        Repro = 'pwsh -NoProfile -File scripts/quality/assert-codex-transcript-hygiene.ps1 -Id <Sxxxx>'
        Fix   = 'A Codex session authored this closure and its own rollout transcript carries a bounded-read violation - read the finding list (oversized/truncated/cross-ticket/sleep-poll), fix the flagged calls in that Codex session, or accept the advisory if the flagged reads were already necessary and reviewed.'
    }

    'resource-link-gate' = @{
        Repro = 'pwsh -NoProfile -File ./a.ps1 fr'
        Fix   = 'A changed resource or manifest does not link. The aapt line above names the file and the reference it could not resolve - fix that, because nothing else in the facade runs aapt and fk stays green on a broken layout. Exit 2 is a DIFFERENT answer: the target never started (most often JAVA_HOME pointing at a JDK that no longer exists), so nothing was checked and the resource is still unproven. A THIRD shape (S2121): the gate names resource paths belonging to no registered Gradle module and refuses without linking anything - add the module row in scripts/utils/gradle-modules.ps1 rather than passing -Module, which this gate deliberately ignores.'
    }

    'detekt-baseline-split-sync' = @{
        Repro = 'pwsh -NoProfile -File scripts/quality/split-detekt-baseline.ps1 -Gate'
        Fix   = 'The format/signal view files derived from the detekt baseline are stale against the baseline you changed - regenerate them with -Update on the same script. Exit 2 is a different answer: the operational baseline or config/detekt/rule-categories.txt is missing, unparseable, or names a rule the table does not classify, so nothing was compared. Staleness matters between releases because agents read these views to decide how much debt of each kind exists (S2105).'
    }

    'detekt-format' = @{
        Repro = 'pwsh -NoProfile -File scripts/quality/detekt-scoped.ps1 -ChangedFiles "<your,files>" -AutoCorrect'
        Fix   = 'The formatting pass rewrote your files before they were judged; nothing is wrong unless the step itself failed, in which case ktlint could not parse a file - read the error above and fix the syntax. Never widen the pass to files you did not change: its own rewrap trips LargeClass on untouched code (S2116).'
    }

    'script-suite-regression' = @{
        Repro = 'pwsh -NoProfile -File scripts/quality/run-script-suites.ps1 -ChangedFiles "<your,files>"'
        Fix   = 'A regression suite guarding a script you changed is red - read its output above and fix the script, not the suite. Run -ListOnly to see which suite claims your file as its subject. Exit 2 is a different answer: the suite could not run for want of an environment tool (rg, for instance), which is advisory here and fatal only before a release (S2122).'
    }

    'suite-tracked' = @{
        Repro = 'pwsh -NoProfile -File scripts/quality/assert-suite-tracked.ps1 -Gate -ChangedFiles "<your,files>"'
        Fix   = 'A contract suite you changed exists on this machine only - it is not in the git index, so a fresh clone and the release worktree discover a smaller set and print the same green verdict. Stage it with the `git add` command the gate printed; nothing in the closure path stages for you, and once missed a directory is never picked up again (`git commit -a` stages tracked files only). Exit 2 is a different answer: git could not be asked at all (S2411).'
    }

    'script-file-size' = @{
        Repro = 'pwsh -NoProfile -File scripts/quality/assert-script-file-size.ps1 -Gate -ChangedFiles "<your,files>"'
        Fix   = 'A repository script in your changed set is above the 2000-line ceiling of CLAUDE.md Rule 2. Extract a self-contained block - run state, wrappers, a family of predicates - into a dot-sourced library beside its siblings in scripts/quality/lib/ and dot-source it back, so the caller`s scope, its $script: state and $LASTEXITCODE stay shared and nothing the script does changes. This is a hard limit and not a ratchet: no baseline absorbs it, because after S3150 no script in the tree is above it. Do not answer it by splitting one long function into two long functions in the same file - the ceiling measures the file.'
    }

    'dotsource-tracked' = @{
        Repro = 'pwsh -NoProfile -File scripts/quality/assert-dotsource-tracked.ps1 -Gate -ChangedFiles "<your,files>"'
        Fix   = 'A script one of your changed files dot-sources exists on this machine only - it is not in the git index. This is harsher than the suite-tracked case beside it: a dot-source is resolved when the CONSUMER is parsed, so in a fresh clone or the release worktree the consumer does not start at all. That is how one unstaged helper took down fk, fkn, fc, fr, fu and every a.ps1 target above check-standard-fast.ps1 while its own ticket sat in Verified (S2616). Stage it with the `git add` command the gate printed; nothing in the closure path stages for you. Exit 2 is a different answer: git could not be asked at all.'
    }

    'androidtest-compile-gate' = @{
        Repro = 'pwsh -NoProfile -File ./a.ps1 fa'
        Fix   = 'The instrumented set (app_v2 src/androidTest) does not compile. No other check compiles it - fk/fkn build src/main, fu builds src/test - so a break here can only surface via this gate. Read the compiler error above and fix the test source; a migration test that cannot compile is indistinguishable from an absent one.'
    }

    'catalog-sync' = @{
        Repro = 'pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2'
        Fix   = 'The class catalog could not be rebuilt - read the error above; the index is a gitignored artifact, so re-running after the fix is safe.'
    }

    'dev-log' = @{
        Repro = 'pwsh -NoProfile -File scripts/add_to_dev_log.ps1 "<path>" "<target>" "<description>"'
        Fix   = 'The changelog row could not be written - read the error above; never edit dev/CHANGELOG.md by hand to work around it.'
    }

    'wear-wire-vocabulary-parity-gate' = @{
        Repro = 'pwsh -NoProfile -File scripts/quality/assert-wear-wire-vocabulary-parity.ps1 -Gate'
        Fix   = 'A phone/watch wire vocabulary outside settings diverged between app_v2 and wear, a LocalOnly safety rule failed, or a new mirrored enum was added without being declared in the gate table. Align the declarations or declare the new enum in $vocabularies.'
    }
    'wear-wire-nullability-gate' = @{
        Repro = 'pwsh -NoProfile -File scripts/quality/assert-wear-wire-nullability.ps1 -Gate'
        Fix   = 'A bridge envelope field is declared non-null WITH a Kotlin default that Gson will never apply - it fills by reflection and runs no constructor, so an absent key leaves null in a reference field and the JVM zero in a primitive. Declare the field nullable and move the old default to every receive site: `.orEmpty()` for a collection, `?: <the old default>` for anything else, and mirror the edit in the other module. A version marker, whose absent-key 0 correctly means "the sender predates every known version", goes in scripts/quality/wear-wire-nullability-baseline.txt with a per-field justification instead. Exit 2 means a declared envelope file is missing from the tree or a baseline row carries no justification.'
    }
}
