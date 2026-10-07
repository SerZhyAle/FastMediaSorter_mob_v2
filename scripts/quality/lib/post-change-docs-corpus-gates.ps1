<#
.SYNOPSIS
    The documentation-corpus gates of scripts/post-change.ps1 - the published help pages under
    documentation/ and the sources they are generated from.

.DESCRIPTION
    Ten gates with one subject, each PER-TICKET by Rule 33 for the reason S2974 recorded for the
    termbase gate: jekyll-gh-pages.yml builds the whole repository as the site, so a corpus page
    reaches readers with no Android release in between, and only the author of the change knows
    which word, link, image or generated page they meant.

      - docs-termbase         (S2974) forbidden synonyms in a changed page or termbase record
      - docs-crosslinks       (S2945) page and root landing links against the published site (S3705)
      - docs-screenshots      (S2977) every referenced image exists and carries alt text
      - docs-search           (S2970) search index shape, sample queries, responsive stylesheet
      - docs-external-content (S3410) Markdown recipes, snippets and the HTML generated from them
      - docs-portal-ui-ux     (S3533) portal UI/UX, responsive styles, search client and link integrity
      - site-addresses        (S4097, S4101) not-found page, forwarders of moved or retired pages, retirement with the capability, addresses held outside the site
      - site-origins          (S4098) third-party hosts declared, named on the privacy pages, advertising only on the landing
      - showcase-inventory    (S4102) every showcase and current What's New bullet anchors a shipped inventory record
      - docs-hub-shell        (S4114) the hand-kept portal hubs carry a current shared shell

    site-origins reads the whole published set - every page, stylesheet and script, 39 MB - and took
    1.9 s when added, so its trigger is any published page, stylesheet, script or template, never a
    script or a PLAN file.

    The last five judge the whole corpus, which S3422 measured at 271-568 ms each, and run only
    when the changed set carries one of their inputs, so a closure outside the corpus never pays
    for them. They carry no -ChangedFiles: the corpus has no author but the documentation
    programme, so a finding in it belongs to whoever changed it.

    Extracted here because the facade reached 1997 of its 2000-line ceiling (CLAUDE.md Rule 2,
    S3422). It is DOT-SOURCED, not invoked, so $root, $changedFiles, $ScopeToFile, Invoke-Gate,
    Skip-Step, Start-PooledGate and Test-AnyChangedFile resolve in the caller's scope exactly as
    they did inline.

    Sourced, never executed directly, so it declares no exit codes of its own.
#>

$runsDocsTermbase = Test-AnyChangedFile '^(docs/termbase\.jsonl|docs/content/recipes/.*\.md|documentation/.*\.md)$'
$runsDocsCorpus = Test-AnyChangedFile '^(documentation/|docs/content/|docs/docs-pages-manifest\.jsonl$|index[^/]*\.html$)'

$runsSiteAddresses = Test-AnyChangedFile '^(404\.html$|docs/site-redirects\.jsonl$|docs/docs-pages-manifest\.jsonl$|docs/coverage-manifest\.jsonl$|documentation/|docs/content/|scripts/docs/(generate-site-redirects|retire-docs-page)\.ps1$|scripts/docs/lib/(site|held)-addresses\.ps1$|scripts/quality/assert-site-addresses(\.ps1$|\.tests/)|docs/site-held-addresses\.jsonl$|docs/site-address-groups\.json$|_data/languages\.yml$|app_v2/src/main/java/com/sza/fastmediasorter/ui/(common/support/SupportIntentFactory|common/input/InputHelpLinkResolver|settings/helpers/GeneralSettingsLinkButtonsSetupHelper|broadcast/helpers/BroadcastShareLinkFactory)\.kt$|wear/src/main/java/com/sza/fastmediasorter/wear/domain/model/WearPortalLinks\.kt$|README\.md$|docs/README[^/]*\.md$|play/listing/|fastlane/metadata/|store_assets/post\.md$|meta/listing/README\.md$)'

$runsSiteOrigins = Test-AnyChangedFile '^([^/]+\.(html|css|js)$|README\.md$|_config\.yml$|assets/|documentation/|docs/.*\.(md|html|css|js)$|_layouts/|_includes/|scripts/quality/(assert-site-origins\.ps1|site-origins\.psd1|install-trust\.psd1)$)'

$runsSiteFacts = Test-AnyChangedFile '^(index[^/]*\.html$|nolegal[^/]*\.html$|README\.md$|assets/site-ui\.js$|documentation/.*\.html$|docs/(content/.*\.md|README[^/]*\.md|QUICK_START[^/]*\.md|DOCUMENTATION_VOICE_GUIDE\.md|flavors/(flavor-matrix\.json|public-editions\.psd1))$|scripts/(quality/assert-site-facts\.ps1|docs/lib/site-facts\.ps1)$)'

$argvDocsTermbase = @('-NoProfile', '-File', (Join-Path $root "scripts/quality/assert-docs-termbase.ps1"))
if ($ScopeToFile -and $changedFiles.Count -gt 0) { $argvDocsTermbase += @('-ChangedFiles', ($changedFiles -join ',')) }

$argvDocsCrosslinks = @('-NoProfile', '-File', (Join-Path $root "scripts/quality/assert-docs-crosslinks.ps1"))
$argvDocsScreenshots = @('-NoProfile', '-File', (Join-Path $root "scripts/quality/assert-docs-screenshots.ps1"))
$argvDocsSearch = @('-NoProfile', '-File', (Join-Path $root "scripts/quality/assert-docs-search.ps1"))
$argvDocsExternalContent = @('-NoProfile', '-File', (Join-Path $root "scripts/quality/assert-docs-external-content.ps1"))
$argvDocsPortalUiUx = @('-NoProfile', '-File', (Join-Path $root "scripts/quality/assert-docs-portal-ui-ux.ps1"))
$argvSiteAddresses = @('-NoProfile', '-File', (Join-Path $root "scripts/quality/assert-site-addresses.ps1"))
$argvSiteOrigins = @('-NoProfile', '-File', (Join-Path $root "scripts/quality/assert-site-origins.ps1"))
$runsShowcaseInventory = Test-AnyChangedFile '^(docs/(FEATURES|WHATS_NEW)[^/]*\.md$|docs/ALL_FEATURES\.jsonl$|scripts/quality/assert-showcase-inventory(\.ps1$|\.tests/))'
$argvShowcaseInventory = @('-NoProfile', '-File', (Join-Path $root "scripts/quality/assert-showcase-inventory.ps1"), '-Quiet')
$runsDocsHubShell = Test-AnyChangedFile '^(documentation/(index|overview)[^/]*\.html$|documentation/design-system/index\.html$|scripts/docs/(update-docs-shell|DocumentationShell)\.ps1$|scripts/quality/assert-docs-hub-shell\.ps1$)'
$argvDocsHubShell = @('-NoProfile', '-File', (Join-Path $root "scripts/quality/assert-docs-hub-shell.ps1"))
$argvSiteFacts = @('-NoProfile', '-File', (Join-Path $root "scripts/quality/assert-site-facts.ps1"))

# Started together so the children overlap; each Invoke-Gate below joins its own.
if ($runsSiteAddresses) { Start-PooledGate @argvSiteAddresses }
if ($runsSiteOrigins) { Start-PooledGate @argvSiteOrigins }
if ($runsSiteFacts) { Start-PooledGate @argvSiteFacts }
if ($runsShowcaseInventory) { Start-PooledGate @argvShowcaseInventory }
if ($runsDocsHubShell) { Start-PooledGate @argvDocsHubShell }
if ($runsDocsTermbase) { Start-PooledGate @argvDocsTermbase }
if ($runsDocsCorpus) {
    Start-PooledGate @argvDocsCrosslinks
    Start-PooledGate @argvDocsScreenshots
    Start-PooledGate @argvDocsSearch
    Start-PooledGate @argvDocsExternalContent
    Start-PooledGate @argvDocsPortalUiUx
}

# S2974: fatal - passing it is the closure condition of every documentation topic ticket.
if ($runsDocsTermbase) {
    Invoke-Gate "docs-termbase" { Invoke-GateChild @argvDocsTermbase }
}
else {
    Skip-Step "docs-termbase" "not applicable - no changed termbase, recipe or documentation/*.md page"
}

# S4097: fatal - SITE-STRUCTURE 0.1 rules 8, 13 and 15; the subject is a handful of small files, so it runs on its own trigger.
if ($runsSiteAddresses) {
    Invoke-Gate "site-addresses" { Invoke-GateChild @argvSiteAddresses }
}
else {
    Skip-Step "site-addresses" "not applicable - no changed 404.html, redirect or page manifest, documentation/ or docs/content/ file, held-address list or holder, or site-address script"
}

# S4098: fatal - SITE-EXPERIENCE 0.1 rule 14; a page that starts loading a new host is the change that must declare it.
if ($runsSiteOrigins) {
    Invoke-Gate "site-origins" { Invoke-GateChild @argvSiteOrigins }
}
else {
    Skip-Step "site-origins" "not applicable - no changed published page, stylesheet, script, template, _config.yml or origins declaration"
}

# S4100: fatal - SITE-REPRESENTATION 0.1 rules 3 and 4; a count, a minimum Android version or an edition name typed into a page is compared with its source.
if ($runsSiteFacts) {
    Invoke-Gate "site-facts" { Invoke-GateChild @argvSiteFacts }
}
else {
    Skip-Step "site-facts" "not applicable - no changed landing page, documentation page, recipe, README, label script, editions declaration or matrix"
}

# S4102: fatal - SITE-REPRESENTATION 0.1 rule 11; the closure that edits the showcase or the notes is the one that named the capability.
if ($runsShowcaseInventory) {
    Invoke-Gate "showcase-inventory" { Invoke-GateChild @argvShowcaseInventory }
}
else {
    Skip-Step "showcase-inventory" "not applicable - no changed showcase, release notes, inventory or showcase gate"
}

# S4114: fatal - SITE-STRUCTURE 0.1 rules 5 and 7; a hand-kept hub gets its shell only from update-docs-shell.ps1.
if ($runsDocsHubShell) {
    Invoke-Gate "docs-hub-shell" { Invoke-GateChild @argvDocsHubShell }
}
else {
    Skip-Step "docs-hub-shell" "not applicable - no changed portal hub, design-system page or shell script"
}

# Each label is written out, never looped over: assert-gate-hints-sync.ps1 pairs a hint with a
# quoted label standing before its script block, and a label held in a variable is invisible to it.
if ($runsDocsCorpus) {
    Invoke-Gate "docs-crosslinks" { Invoke-GateChild @argvDocsCrosslinks }
    Invoke-Gate "docs-screenshots" { Invoke-GateChild @argvDocsScreenshots }
    Invoke-Gate "docs-search" { Invoke-GateChild @argvDocsSearch }
    Invoke-Gate "docs-external-content" { Invoke-GateChild @argvDocsExternalContent }
    Invoke-Gate "docs-portal-ui-ux" { Invoke-GateChild @argvDocsPortalUiUx }
}
else {
    $corpusSkipReason = "not applicable - no changed documentation/, docs/content/, docs-pages-manifest or root index*.html file"
    Skip-Step "docs-crosslinks" $corpusSkipReason
    Skip-Step "docs-screenshots" $corpusSkipReason
    Skip-Step "docs-search" $corpusSkipReason
    Skip-Step "docs-external-content" $corpusSkipReason
    Skip-Step "docs-portal-ui-ux" $corpusSkipReason
}
