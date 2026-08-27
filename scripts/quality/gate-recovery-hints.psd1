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

    'doc-pin-drift' = @{
        Repro = 'pwsh -NoProfile -File scripts/quality/assert-doc-pin-drift.ps1'
        Fix   = 'A version pin quoted in the docs no longer matches the build files - update the doc line to the value the report names, never the other way round.'
    }

    'doc-script-references' = @{
        Repro = 'pwsh -NoProfile -File scripts/quality/assert-script-references.ps1 -Docs'
        Fix   = 'A document names a .ps1 that does not exist. Correct the path, or say so on its line: External: for a script shipped outside this repository, Historical: for a retired one. Adding the line to doc-script-reference-baseline.txt is not a fix.'
    }

    'settings-doc-sync-gate' = @{
        Repro = 'pwsh -NoProfile -File scripts/quality/assert-settings-doc-sync.ps1'
        Fix   = 'A settings surface changed without regenerating its docs (CLAUDE.md Rule 22) - regenerate docs/settings/settings-manifest.json and docs/SETTINGS_REFERENCE*.md, and annotate the new key.'
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

    'all-features-gate' = @{
        Repro = 'pwsh -NoProfile -File scripts/all_features/validate.ps1'
        Fix   = 'The capability inventory is invalid or lost records - fix the JSONL row; add capabilities through scripts/all_features/add.ps1, never by hand.'
    }

    'howto-settings-paths-gate' = @{
        Repro = 'pwsh -NoProfile -File scripts/quality/assert-howto-settings-paths.ps1 -Gate'
        Fix   = 'A guide names a settings path that no longer exists - correct the path to the one the settings manifest records, in every locale of that guide.'
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
}
