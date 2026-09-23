# Rules Digest (Свод правил)

## Purpose, portability, and status

This is a research-oriented index of code, UI, testing, and workflow controls.
It has two deliberately separate layers:

1. **Portable canon candidates** are expressed without Android, FastMediaSorter,
   owner, language, or tool dependencies. Another project can adopt one only
   when its architecture and risk model need it; an absent capability is simply
   not adopted.
2. **FastMediaSorter profile** records the project's concrete implementation:
   Android/Wear specifics, scripts, CI jobs, ticket states, and named gates. It
   is evidence that a portable rule is implemented here, not a demand that
   another project copy the mechanism.

The document intentionally excludes personal or conversational instructions:
language/tone preferences, response style, individual identities, agent chat
habits, and context-budget tactics. It also does not promote a tool name,
directory name, ticket prefix, device serial, or a current implementation
threshold into the canon. An implementation decision still follows the
normative source linked below.

Status labels:

| Label | Meaning |
|---|---|
| **Mechanical** | A script, build, test, or hook can refuse a violation. |
| **Automated run** | The check has an executable runner, but it only protects a change when that runner is invoked by the selected workflow or CI. |
| **Process control** | The repository provides a lifecycle or state-transition mechanism; its use can still depend on the active workflow. |
| **Human evidence** | A person must inspect, operate, or judge the result. |
| **Planned** | Explicitly proposed, but not currently implemented as a required control. |
| **Canon candidate** | Portable rule suitable for a canon proposal; its local implementation is optional. |
| **FMS profile** | FastMediaSorter-specific rule or mechanism; retain it as an overlay only. |

The inventory distinguishes a control existing from it being executed in every
environment. The local `a.ps1` runner, the change-closure facade, specification
transitions, release pipelines, and GitHub Actions activate different subsets.

## Canon sources, project sources, and execution surfaces

| Source | Owns |
|---|---|
| SZA canon: `rules/DEVELOPMENT.md` | Portable engineering discipline: architecture, evidence, validation, lifecycle, gates, locks, and audits. |
| SZA canon: `rules/TESTING_AND_QA.md` | Definition of done, evidence ladder, test tiers, device verification, release sweep, and gate-cost discipline. |
| SZA canon: `rules/INVARIANTS.md` | Always-on cross-project safety and evidence invariants. |
| SZA canon: `rules/DOCUMENTATION_CONCEPT.md` | Single sources of truth and documentation lifecycle. |
| SZA canon: `rules/SECURITY_AND_PRIVACY.md` | Secret handling, justified permissions, privacy promise, and third-party-binary controls. |
| `AGENTS.md` | Repository contract for Codex/ZCode, including architecture, UI, validation, locks, and specification lifecycle. |
| `docs/NON_CLAUDE_RUNTIME_RULES.md` | Equivalent mandatory behavior when Claude hooks are unavailable. |
| `docs/AGENT_HOOKS.md` | Complete hook inventory, events, verdicts, and escape hatches. |
| `docs/CODE_AUDIT_PROTOCOL.md` | Risk-triggered audit, evidence ladder, and dynamic-analysis policy. |
| `docs/BUILD_TEST_FAST_PATH.md` and `a.ps1` | Validation targets and their phone/Wear module scope. |
| `scripts/quality/assert-*.ps1` | Static, consistency, release, and process gates. |
| `scripts/post-change.ps1` | Closure facade: change journal, catalog/document updates, and relevant gates. |
| `scripts/spec_catalog/*.ps1` | Ticket state transitions and closing gates. |
| `.github/workflows/android-ci.yml`, `.github/workflows/maestro-tests.yml` | CI build, static-gate, lint, unit-test, and emulator E2E execution. |

## 1. Portable canon candidates

These are the transferable rules proposed for canon consideration. They state a
result, not a preferred framework, script, or repository layout.

| Candidate | Portable rule | Typical evidence |
|---|---|---|
| Evidence before completion | Do not claim a change is done, fixed, or passing without fresh evidence that directly proves it. Record the command/check, expected result, actual result, and verdict. | A passed command, test, or observed device flow. |
| Risk-matched validation | Use the cheapest validation rung that can detect the relevant failure: document check, script, build/config check, compile, targeted test, release variant, then device/user flow. | A validation plan selected from change risk. |
| Layered design | Keep presentation, application/domain, and data responsibilities separate. UI delegates business decisions; I/O sits at the data boundary. | Architecture review and static checks where feasible. |
| Role-revealing names and decomposition | Names communicate responsibility; grow code by extracting cohesive units rather than accumulating unrelated logic. | Review, naming checks, measured size guardrails. |
| Comments and logs serve maintenance | Comments explain non-obvious reasons and invariants; stale commentary is removed. Use one project logging abstraction with meaningful levels. | Review plus lint/static rules where stable. |
| Generated knowledge is queried, not guessed | Consult the project index/catalog before broad search; generated artifacts have one writer and are regenerated rather than hand-edited. | Catalog/registry command and ownership check. |
| Recurring defect becomes executable knowledge | Turn a recurring review or incident finding into a test, static gate, or benchmark. Encode legitimate exceptions narrowly and visibly rather than weakening the general rule. | New/strengthened gate with explicit allowlist or baseline. |
| Scope-aware automation | Per-change checks judge the changed set; whole-tree and shipping-artifact checks run at their declared release/CI boundary. A pre-existing unrelated failure must not mask a change verdict. | Gate placement metadata and changed-input test. |
| Test real boundaries | Unit-test domain logic; use integration tests where mocks hide a real contract; prove reflection, serialization, DI, startup, and shrinking on the release target. | Targeted unit/integration/release-variant checks. |
| User-visible behavior is exercised | A user-facing flow is not proved by compilation alone. Run a repeatable emulator/device/clean-environment flow, and keep device-only evidence in an explicit handoff state. | E2E harness or observed manual flow. |
| Audit high-risk changes | Escalate review for lifecycle, concurrency, listener ownership, persistence/migration, heavy resources, startup, DI, build/minification, and incidents. Audit each phase before beginning the next. | Risk checklist and phase audit record. |
| Safe concurrent work | Keep scratch artifacts in a bounded workspace; serialize shared expensive work; derive lock domains from changed paths; acquire multi-domain sets atomically; withdraw abandoned queue requests. | Lock/queue and preflight evidence. |
| Documentation has ownership | Maintained documentation has a discoverable owner/source, declared update triggers, validation, and generated views where appropriate. | Registry coverage and generated-view drift check. |
| Inclusive interaction and adaptable layout | When a product has a UI, support its declared input modes, preserve semantic action roles, avoid unsafe system UI areas, and define behavior across required form factors/orientations. | Accessibility/layout checks plus device verification. |
| Localized product text is data | Keep user-visible text externalized; define the supported locale set and verify formatting, liveness, and translation/release workflow. | Resource/localization checks. |
| Secure-by-design release | Keep secrets out of source, justify permissions, preserve privacy declarations, and track bundled third-party binaries/licenses. | Secret/permission/license/release checks. |
| Optimise gates with measurements | Measure gate cost and finding yield before removing, weakening, or reordering a gate. Cache only clean verdicts whose complete inputs are unchanged; never trade safety for a shorter loop. | Per-gate duration and result telemetry. |

### Explicit non-candidates

The following remain out of a canon proposal unless a receiving project chooses
an equivalent for its own reasons: `Sxxxx` status names, the exact 2,000 LOC
and 500 LOC thresholds, Kotlin/Android naming examples, Timber, Hilt, Room,
Media3, XML/ViewBinding, Wear/phone parity, Android resource qualifiers,
Maestro, PowerShell, `a.ps1`, GitHub Actions workflow names, FastMediaSorter
flavors, release queue order, device fleet classifications, and every named
FMS gate. They are retained below solely as the project profile.

## 2. FastMediaSorter code profile

| Rule family | Requirement | Status and enforcement |
|---|---|---|
| Module architecture | Keep product logic out of Activities; move it to suitably named managers. Keep files at or below the 2,000 LOC ceiling. | **Mechanical + process control.** `activity-logic`, `assert-activity-logic-not-growing.ps1`, `assert-file-line-ceiling.ps1`; audit Layer 1. |
| Naming and API design | Use `VerbNounUseCase`, `NounRepository`, `NounViewModel`, and `NounVerbManager`; retain constructor headroom. | **Mechanical.** `class-architecture-naming` and `assert-ctor-arg-slots.ps1`. |
| Comments and readability | Comments and KDoc are English, explain why, are read before editing, and stale/trivial comments are removed. No long dash in Kotlin. | **Partly mechanical.** `trivial-comments`, `assert-neuroslop.ps1`, Detekt; intent and staleness remain **human evidence**. |
| Lifecycle and concurrency | No `GlobalScope`, unsafe UI Flow collection, swallowed cancellation, unjoined test scopes, or asymmetric listeners. | **Mechanical + audit.** Source rules, `assert-globalscope.ps1`, `assert-unsafe-collect.ps1`, `assert-swallowed-cancellation.ps1`, `assert-listener-symmetry.ps1`; lifecycle ownership remains audited by a human. |
| Shared state and persistence | Public mutable flows are prohibited; durable Gson and enum names must survive R8; Room changes have migration and executed migration-test evidence. | **Mechanical + human evidence.** Source rules plus Gson/enum/migration gates; `a.ps1 fam` executes migrations on a selected device. |
| Dependency, build, and flavor boundaries | No raw PackageManager flags, no growing `BuildConfig.IS_*` guards in `src/main`, source-set isolation for flavor behavior, no retired dependency names. | **Mechanical.** PM, flavor, dependency, package, and build gates. |
| Resource ownership and performance | Use `dagger.Lazy` and `ViewStub` where optional; release Media3 resources when paused; avoid eager startup and main-thread work. | **Process control + human audit.** Static support exists for some patterns, while player, cache, startup, and I/O ownership are checked through audit Layers 2, 3, 5, and 6. |
| Logging and temporary probes | Timber only; ticket probes are permitted only during `BlockNeedUserTest` and must be removed before leaving it. | **Mechanical.** `assert-no-ticket-logs.ps1`, `assert-no-release-probes.ps1`, ticket closing gates. |

### Lexical source-gate rule set

`assert-source-gates.ps1` runs 23 baseline-ratcheted rules in one source walk.
New occurrences fail a gated invocation; a rule's baseline may only decrease.

`trivial-comments`, `caption-value-split`, `raw-imagebutton-in-bar`,
`unsafe-collect`, `public-mutable-flow`, `window-insets`,
`swallowed-cancellation`, `swallowed-cancellation-wear`,
`unpoliced-animation`, `unpoliced-animation-wear`, `test-unjoined-scope`,
`activity-logic`, `untracked-dialog`, `string-quote-escaping`,
`string-lone-backslash`, `class-architecture-naming`,
`appsettings-persistence`, `hardcoded-drive-path`, `inline-delivery-block`,
`wear-list-start`, `transitive-dialog-insets`, `landscape-orphan-layout`, and
`landscape-focus-parity`.

## 3. FastMediaSorter UI, accessibility, and localization profile

| Surface | Requirement | Status and enforcement |
|---|---|---|
| Input parity | Every interactive surface supports keyboard, D-pad/TV, and mouse with focusability, clickability, and directional focus. | **Mechanical + human evidence.** Focus/highlight/parity gates catch declared structure; actual navigation needs device or emulator verification. |
| Portrait/landscape parity | A portrait layout change is paired with `layout-land`; required IDs, focus order, and behavior stay aligned. | **Mechanical.** Source rules plus orientation, layout-variant, and focus-parity gates. |
| Insets | UI observes `systemBars` and `displayCutout` safe bounds; `fitsSystemWindows` alone is insufficient. | **Mechanical + human evidence.** `window-insets`, `transitive-dialog-insets`, `assert-window-insets.ps1`; visual verification still matters. |
| Dialogs and sheets | Every confirm/cancel pair uses `DialogConfirm`, `DialogCancel`, or `DialogDestructive`; dialogs are tracked and inset-safe. | **Mechanical.** `assert-dialog-cancel-style.ps1`, `untracked-dialog`, `transitive-dialog-insets`. |
| UI design decisions | Placement, visibility, fallback, and ambiguity are settled through `/ui-clarify` before implementation. | **Process control + human judgement.** It structures the decision; it cannot determine product intent. |
| Settings | Setting changes update manifests, references, annotations, search/doc-scope catalogs, and sensitive-setting metadata. | **Mechanical.** Settings synchronization and completeness gates; generated references are part of closure. |
| Strings and locales | New UI text is authored in English, Russian, and Ukrainian; the declared locale set is fanned out at release. Formatting, quoting, liveness, and phone/Wear mirroring are checked. | **Mechanical + release process.** String and localization gates; the full locale fan-out is a release-boundary action. |
| Visual quality | Resource/icon parity, RTL attributes, launcher contrast/reset coverage, splash branding, image-button and caption layout patterns are controlled. | **Mechanical**, supplemented by UI sweeps and Maestro/device evidence. |
| Accessibility semantics | An interactive element carrying an icon only declares an accessible name; a custom interactive view exposes role and state through an accessibility delegate; a clickable element reaches the 48dp touch-target floor. The demand is contextual, not a blanket `contentDescription` rule, and a decorative exception comes from an explicit allowlist row with a reason, never from the rule's silence. | **Mechanical.** `assert-a11y-semantics.ps1` at closure, baseline-ratcheted; exceptions live in `scripts/quality/a11y-decorative-allowlist.txt`. |
| State restore | Every screen and long operation has a written fate for rotation, process death, relaunch, and cancellation: state preserved with its mechanism named, a deliberate reset with its reason, or the end state a cancelled operation leaves behind. | **Process control + executed proof.** The fates are declared in `docs/STATE_RESTORE_MATRIX.md`; the critical rows run as `CriticalStateRestoreTest` in the nightly device contour. The long tail is filled as each screen is touched. |

## 4. FastMediaSorter security and privacy profile

This section instantiates the canon security and privacy source for this
project. The canon states the principle; the table below states what the
principle means in this repository and which control refuses a breach of it.
The profile covers seven surfaces: secrets in version control, credential
storage, log redaction, diagnostic exports, dependency admission, manifest
risk, and the contract every operation that leaves the process is held to.

| Rule family | Requirement | Status and enforcement |
|---|---|---|
| Secrets in version control | No credential, token, API key, or private-key block reaches a tracked file. A fixture or test-only literal is admitted through the commented allowlist, never by silence. | **Mechanical.** `assert-no-secrets.ps1` over the changed set at closure and over the tracked tree on a schedule; exceptions live in `scripts/quality/no-secrets-allowlist.txt`. |
| Credential storage | A persisted credential-shaped field reaches storage only through the existing encryption boundary - `CryptoHelper` for database columns, `EncryptedCookieStore` for cookies. A plain `String` credential field is a finding. | **Mechanical.** `assert-credential-encryption.ps1`, baseline-ratcheted. Lifecycle of stored credentials stays with `CredentialAuditor` and `DeleteUnusedCredentialsUseCase`. |
| Log redaction | A Timber call in the network, cloud, remote, or transfer tree of either module routes a credential or a user resource path through the masking seam; unmasked interpolation is a finding. The Wear log tree ships remotely and is held to the same rule. | **Mechanical.** `assert-log-redaction.ps1`, baseline-ratcheted; the seam is `core/security/SecretMasker.kt`. |
| Diagnostic exports | Every report-payload field carrying a file path, a resource name, or a credential passes through the masking seam before it leaves the device - the Wear log report and the phone diagnostics bundle alike. | **Mechanical.** `assert-diagnostics-redaction.ps1`, baseline-ratcheted. |
| Backup and data extraction | The backup and data-extraction rule pair stays consistent, so nothing sensitive leaves the device through auto-backup or device transfer. | **Mechanical.** `assert-backup-rules-consistent.ps1`. |
| Sensitive settings | A setting that exposes sensitive data carries its annotation, so documentation, search, and screenshot surfaces can treat it as sensitive. | **Mechanical.** `assert-sensitive-settings-annotated.ps1`. |
| Third-party notices | Every flavor ships the OSS notices payload matching its own dependency set. | **Mechanical.** `assert-oss-notices.ps1`. |
| Deobfuscation retention | Release mapping and deobfuscation artifacts are retained, so a shipped crash stays readable without weakening the shipped build. | **Mechanical.** `assert-deobfuscation-retained.ps1`. |
| Dependency admission - advisories and licences | A new or bumped dependency passes vulnerability-advisory review and carries a licence on the allowlist before it merges. | **Automated run, PR-bound.** `dependency-scan.yml` job `dependency-review`, judged against `scripts/quality/dependency-license-allowlist.json`; the graph it compares is submitted by the same workflow on a push to `main`. |
| Software bill of materials (SBOM) | Every shipping module has a machine-readable inventory of what it carries. | **Automated run, scheduled.** `dependency-scan.yml` job `sbom` (CycloneDX, both modules) through `scripts/builders/build-sbom.ps1`, also runnable locally as `a.ps1 sbom -Module <app_v2\|wear>`. |
| Deep vulnerability scanning | Not adopted. OWASP dependency-check needs an NVD API key, an external secret this project does not carry. | **Planned.** Revisit only if the native advisory feed proves insufficient; the decision and its precondition are recorded in the `dependency-scan.yml` header. |
| Manifest risk - the registry | Every permission, exported component, cleartext allowance, backup attribute and foreground service type has a row in `docs/manifest-risk-registry.jsonl` naming its justification and the flavors it reaches. The registry IS the allowlist; a row with no justification, or naming neither a flavor nor the source set that gates it, is refused like a missing one. | **Mechanical.** `assert-manifest-risk-diff.ps1` at closure. |
| Manifest risk - who writes the justification | The change that adds the declaration writes the row, in one sentence a reviewer who has never seen the code can read, naming the feature it serves and the ticket where one exists. | **Process control + mechanical.** The gate refuses an empty justification; only a person can judge whether the sentence is true. |
| Manifest risk - after merging | A declaration a library contributes at merge time, which no source diff shows, is judged per flavor against the same registry - and a row claiming fewer flavors than it actually reaches fails. | **Automated run, CI.** `android-ci.yml` job `manifest-risk`, one merged manifest per phone and watch flavor. |
| External I/O contract | Every operation that leaves the process - network, file or cloud - declares its own timeout, cancels cooperatively, classifies its failure into the existing `NetworkException` taxonomy, retries only a classification that is safe to repeat, and is idempotent wherever a repeat is possible. The seam the contract governs is the transfer strategy layer; SMB, FTP, SFTP, cloud and local are five instances of one rule, not five rules. | **Process control + executed proof.** Written out in `docs/ARCHITECTURE.md` "External I/O contract"; proved at the seam by `IoContractTimeoutCancellationTest` and `IoContractErrorRetryTest` over fake transports. |
| File-operation journal pairing | A destructive file operation - delete, move, rename, overwrite - names the actor that records it in the change journal the browsing surface reconciles from. Where the operation is declared below the layer that can address a resource, the naming is of the caller, not an inline call. An operation no actor records is invisible to the surface: the file is gone from disk and still on screen. | **Mechanical.** `assert-fileop-journal-pairing.ps1` at closure. Read-only opt-outs live in `scripts/quality/fileops-read-only-allowlist.txt`, each with a mandatory reason; the 59 transport-layer operations in `fileop-journal-pairing-baseline.txt` are a named structural exclusion - they register through their caller, the list may shrink and may never grow. |
| Mutation producer registration | A component the framework starts outside the `ui/` tree - a Worker, a Service, a receiver, a widget provider, a `FileObserver` - that changes user content either records the change or declares how the screen stays correct without it. It has no surface of its own, so nothing redraws because it ran. | **Mechanical.** `assert-mutation-producer-registration.ps1` at closure. `scripts/quality/mutation-producer-registry.txt` holds the exceptions and nothing else - `refresh:<Class>.<member>`, resolved in source so an unwired refresh is refused, or `own-state` for a producer that removes only what the app itself created. Each row carries a reason. |

The four redaction and storage gates are baseline-ratcheted: a baseline may only
shrink, so an existing finding is debt with a ceiling, not a permission. The three
dependency rows are the only ones in this profile enforced outside the developer's
own machine - a PR check and a weekly job - because a dependency arrives as a
version-catalog line rather than as source a local gate can read.

## 5. FastMediaSorter validation and self-test ladder

| Need | Phone target | Wear target | Evidence type |
|---|---|---|---|
| Kotlin symbols | `a.ps1 fk` or `fkn` | `fw` / `fwn` | Compile |
| Resources or manifest | `fr` | `fwr` / `fwrn` | Resource link/merge |
| Mixed small change | `fc` | module-appropriate compile plus resource target | Compile + resource link |
| Focused unit tests | `fu -Tests "*NameTest"` | `fwu` / `fwun` | Executed tests |
| Android lint | `fl` | `flw` | Static Android analysis |
| Custom lint detector tests | `flr` | `flr` | Executed detector suite |
| Static gate batch | `fg` | same batch, with Wear gates included | PowerShell quality controls |
| Script regression suites | `fs -ChangedFiles "..."` | same | Executed PowerShell tests |
| Room migration execution | `fam -DeviceId <serial>` | n/a | On-device migration validation |
| UI/end-to-end | Maestro suite or approved device workflow | Maestro Wear flows where applicable | Emulator/device behavior |
| Performance | `mb`, `gbp`, Perfetto workflow | applicable harnesses | Measured runtime evidence |

The runner deliberately separates `app_v2` from `wear`: a green phone target is
not evidence about a changed Wear file. Release-sensitive reflection,
serialization, DI, manifest, and dependency changes require a minified target
variant in addition to debug checks.

**Flaky test policy.** An intermittently failing test is quarantined with a
ticket, never retried to green, and never silently deleted. The quarantine
ledger is `docs/test-flaky-quarantine.jsonl`, one JSON object per line carrying
the test id exactly as the JUnit report spells it, the symptom, a live `Sxxxx`
and the date it entered; a row whose ticket the spec catalog no longer carries
is a stale row. Two mechanisms hold the policy up. The refusal is
`scripts/quality/assert-no-test-retry.ps1`, a per-ticket gate that reads the test
source sets of both modules and the four build scripts and refuses a retry
annotation, a retry `TestRule`, a retry runner or the Gradle test-retry plugin -
it matches mechanisms, never names, so a test **of** a product retry policy is
not a subject. The report is `scripts/ci/triage-junit-flaky.ps1`, run in the CI
unit-test job after the test task: a quarantined failure prints a named warning
carrying its ticket and symptom, and a failure nobody quarantined stays a hard
failure. Retry-to-green is forbidden because it does not remove the flake, it
removes the report of the flake, at a fixed probability, for as long as the
mechanism stays in the tree.

**Numeric performance budgets.** `scripts/quality/perf-budgets.json` carries one
row per budgeted metric - the unit, the value, the tolerance and the source
measurement that produced it - and `scripts/quality/assert-perf-budget.ps1`
compares a supplied measurement artifact against budget plus tolerance at
release scope. A release that supplied no fresh measurement for a budgeted
metric answers "could not verify", not "pass". The first budget is the Wear cold
start at 4503 ms, the number S3368 achieved rather than the 5292 ms it started
from.

## 6. FastMediaSorter audit controls and remaining human work

The audit protocol is mandatory for new long-lived components, lifecycle/
coroutine/Flow/listener changes, shared state, Room migrations, player/image/
cache/network work, startup or DI scope changes, release build/R8 changes, and
crash/ANR/OOM/jank/leak incidents. A multi-phase task is audited at every phase
boundary.

| Audit layer | Automation already present | Human judgement still required |
|---|---|---|
| Architecture/readability | Source gates, Detekt, naming and line-ceiling checks | Cohesion, ownership, and whether an extraction is appropriate |
| Lifecycle/concurrency | GlobalScope, unsafe collection, cancellation, listener gates | Lifecycle completeness, dispatcher correctness, race analysis |
| Memory/resources | Listener symmetry and debug LeakCanary availability | Retained UI graph, player/cursor/surface/cache ownership |
| Room | Migration pairing/schema gates and migration runner | Query bounds, data semantics, upgrade scenario selection |
| Startup/main thread | Startup markers and static checks | I/O path, eager work, and cold-start causality |
| Performance | Macrobenchmark/Baseline Profile harness and Perfetto playbook | Scenario selection and interpretation of regressions |
| Release/R8 | Release variant build path, Gson/enum/keep-rule checks | Reflection/DI/minification runtime behavior |

Implemented diagnostic infrastructure includes debug StrictMode, `StrictModeHelper`,
LeakCanary, ProfileInstaller, startup markers, Macrobenchmark/Baseline Profile,
and a Perfetto playbook.

The six custom Lint rules the protocol once listed as future work are **mechanical**,
not planned: Activity business logic (`ActivityLogicViolation`), UI context retained by
a singleton or long-lived holder (`UiContextLeak`), lifecycle-unsafe Flow collection
(`UnsafeFlowCollect`), unreleased player ownership (`PlayerNotReleased`), main-thread disk
I/O (`MainThreadIo`) and main-thread Room access (`MainThreadRoom`). A seventh detector,
`NetworkDataSourceDispatcher`, guards blocking socket I/O on the caller's dispatcher.
All seven live in `:lint-rules` and reach both modules through `lintChecks`, so they run
in every `fl` and `flw` pass and in the IDE. The proof target is the detector suite
itself - `a.ps1 flr` - where each rule carries a seeded violation and a clean
counterpart, so a rule that stops detecting fails a test rather than going quiet.

Still **planned**, not current mandatory automation: LeakCanary instrumented-test
integration in CI, and broader performance CI.

## 7. FastMediaSorter workflow and specification profile

| Control | Status and mechanism |
|---|---|
| Script-owned state | **Mechanical.** Ticket catalog, feature ledger, change log, class catalog, string editing, document maps, and settings artifacts have named script owners. |
| Change closure | **Automated run.** `post-change.ps1` chains change log, catalog sync, source/resource gates, and document-related checks for its changed-file scope. |
| Locks and queues | **Process control.** Derived code/build domains serialize edits and Gradle; queue, wait, withdraw, and preflight tooling detects abandoned work. |
| Release freeze | **Mechanical/process.** A freeze blocks opening a new ticket, while reading and existing-ticket work remain allowed. |
| Device safety | **Process control.** Per-device authority is maintained only in `docs/DEVICE_FLEET.md`; destructive device actions use guarded wrappers. |
| Documentation registry | **Mechanical + process.** Registry query, validation, generated map/sitemap drift check, and reverse coverage gate keep maintained documents discoverable. |
| Specification lifecycle | **Mechanical.** Catalog CLIs own status changes and call closing gates; queue ranking is script-assisted. |
| Evidence and closure | **Process control.** Validation records expected versus actual outcomes; preflight checks identity, session records, locks, queues, and leases. |

### Specification closing gates

The `Assert-ClosingGates` path protects catalog transitions. It requires:

1. a status note for every transition into `Block*`, including a resolvable
   blocker reference for `BlockByOtherTask`;
2. every research open item to be resolved or carried into a named ticket before
   `Implemented` or `Verified`;
3. a non-empty `Last Audit` block before `Verified` and before
   `BlockNeedUserTest`;
4. an owner-visible Timber probe, or approved baseline reason, before
   `BlockNeedUserTest`;
5. removal of that probe before leaving `BlockNeedUserTest`;
6. unique level-two headings in the strategic and tactical specification files;
7. unresolved human checklist entries to end in `BlockNeedUserTest`, not
   `Verified`; and
8. a discovered defect to leave `BlockNeedUserTest` immediately with an
   actionable status note.

## 8. FastMediaSorter hook implementation

The portable candidate is "enforce repeatable tool-safety rules at the earliest
reliable boundary." The hook names, events, and runtime dependence below are
an FMS/Claude implementation, not canon content.

The Claude runtime has 20 registered hooks. They are unavailable to other
runtimes, which must follow `docs/NON_CLAUDE_RUNTIME_RULES.md` manually.

| Verdict | Hooks |
|---|---|
| Refuse unsafe action | `guard-find-command`, `guard-ps1-in-bash`, `guard-fire-and-forget`, `guard-bash-unavailable-command`, `guard-catalog-before-kt-search`, `guard-mcp-one-way-tools`, `guard-release-freeze`, `guard-manual-task-wait`, `refuse-spec-do-stop`, `refuse-unexplained-red-verdict` |
| Rewrite | `guard-uncapped-read` |
| Observe or arm | `observe-empty-grep`, `observe-plan-tick-batching`, `sweep-agent-lock-queues`, `reset-catalog-touch-marker`, `post-agent-chat-session` |
| Warn or nudge | `warn-context-size`, `nudge-small-task-tier`, `nudge-context-budget`, `nudge-check-target-by-change-type` |

## 9. FastMediaSorter complete quality-gate inventory

There are 144 `scripts/quality/assert-*.ps1` entry points. This is the complete
filename inventory, retained here for automation research; the gate-placement
registry and each script define its exact trigger and refusal scope. These names
are an FMS implementation map, not a portable rule list.

```text
assert-16kb-alignment                 assert-acceptance-preconditions
assert-a11y-semantics                 assert-activity-locale-wrapper
assert-activity-logic-not-growing        assert-activity-logic-not-growing
assert-allfeatures-sync               assert-always-loaded-budget
assert-appsettings-persistence        assert-archive-artefacts
assert-artifact-version-fresh         assert-backup-rules-consistent
assert-baseline-inventory
assert-bridge-scenario-coverage       assert-ci-cost-map
assert-code-domain-writers            assert-codex-transcript-hygiene
assert-credential-encryption          assert-ctor-arg-slots
assert-delivery-size-estimates        assert-deobfuscation-retained
assert-deprecated-pm-flags            assert-detekt
assert-detekt-baseline-absorption     assert-device-profile-matrix
assert-device-ready-module            assert-diagnostics-redaction
assert-dialog-cancel-style            assert-doc-house-style
assert-doc-icons-sync                 assert-doc-pin-drift
assert-document-registry-coverage     assert-dotsource-tracked
assert-docs-coverage                  assert-docs-crosslinks
assert-docs-external-content          assert-docs-screenshots
assert-docs-search                    assert-docs-termbase
assert-enum-persistence-contract      assert-exit-contract
assert-fast-gates                     assert-fgs-notifications
assert-file-line-ceiling              assert-fileop-journal-pairing
assert-flavor-binding-coverage        assert-flavor-count-prose
assert-mutation-producer-registration
assert-flavor-flags-not-growing       assert-flavor-matrix-docs
assert-focus-highlight                assert-focus-parity
assert-gate-count-prose               assert-gate-hints-sync
assert-gate-placement                 assert-gate-timing-claims
assert-globalscope                    assert-gson-persistence-contract
assert-guide-coverage                 assert-harness-drift
assert-hook-inventory                 assert-howto-settings-paths
assert-icon-inventory-sync            assert-invoked-tracked
assert-icon-style
assert-launcher-contrast              assert-launcher-reset-coverage
assert-lint-baseline-absorption
assert-layout-variant-id-parity       assert-listener-symmetry
assert-log-redaction                  assert-maestro-oracle
assert-manifest-risk-diff             assert-memory-budget
assert-meta-packaging-limits          assert-migration-schema-conformance
assert-migration-test-pairing         assert-module-version-parity
assert-neuroslop                      assert-new-lexemes-translated
assert-no-line-budget                 assert-no-orphan-merged-resources
assert-no-release-probes              assert-no-secrets
assert-no-test-retry                  assert-no-ticket-logs
assert-non-null-assertion             assert-notification-small-icon
assert-orientation-implied-feature    assert-orientation-layout-pairing
assert-orphaned-merged-resources      assert-oss-notices
assert-packaging-excludes-parity      assert-perf-budget
assert-play-listing-graphics          assert-play-listing-locales
assert-play-listing-screenshot-geometry
assert-prerelease-content-gates       assert-qualified-gradle-tasks
assert-qualifier-shadowing            assert-quantity-format-seam
assert-release-scope-gates            assert-resource-icon-parity
assert-retired-dependency-names       assert-rtl-layout-attrs
assert-rule-digest-sync               assert-script-cheatsheet-sync
assert-script-described               assert-script-file-size
assert-script-parses                  assert-script-references
assert-sdk-pin-claims                 assert-sensitive-settings-annotated
assert-security-posture
assert-settings-catalog-complete      assert-settings-doc-sync
assert-shared-test-flavor-scope       assert-source-gates
assert-spec-catalog-valid             assert-splash-brand-sync
assert-stream-asset-revisions         assert-string-format
assert-suite-tracked                  assert-swallowed-cancellation
assert-tactical-step-form             assert-temp-root-inventory
assert-test-suite-complete            assert-ticket-acceptance-probes
assert-trivial-scope                  assert-ui-sweep-catalog
assert-unreferenced-strings           assert-unsafe-collect
assert-untracked-dialogs              assert-wear-64bit-abi
assert-wear-canonical-key-parity      assert-wear-mirrored-strings
assert-wear-phone-identity-parity     assert-wear-record-merge-parity
assert-wear-route-literals            assert-wear-settings-parity
assert-wear-store-boundary            assert-wear-walk-contract
assert-wear-wire-nullability          assert-wear-wire-vocabulary-parity
assert-window-insets
```

The inventory spans architecture, coroutine/lifecycle safety, UI/XML/resources,
accessibility, strings/localization, settings, persistence/migration, build and
flavor parity, Wear/phone data contracts, documentation, scripts and harness,
test coverage, artifacts, Play listing, release content, and agent workflow.

`assert-fast-gates.ps1` is the normal local/CI batch. Its changed-file-aware
members prevent unrelated in-flight work from making a ticket fail; project-wide
advisories are intentionally escalated at release scope.
`assert-release-scope-gates.ps1` and prerelease flows cover whole-tree or
shipping-artifact controls.

## 10. FastMediaSorter CI and device automation

| Workflow | Current automated coverage |
|---|---|
| `android-ci.yml` | `app_v2` lint, unit tests, Standard debug assembly; Wear and detector verification; PowerShell fast static gates; extra phone-flavor builds; release assembly; optional lint-baseline regeneration. |
| `maestro-tests.yml` | Builds Standard debug, starts an Android emulator, runs Maestro smoke and critical flows, and uploads reports/recordings. |
| `jekyll-gh-pages.yml` | Builds and deploys the documentation site. |
| `nightly-device-loop.yml` | Nightly and manual only. Starts an emulator and runs the leak-watch instrumented suite over the critical flows declared in `scripts/quality/leak-watch-flows.txt`, uploading a JSON verdict plus the connected test reports. Fails on any retained instance, and equally on a run whose seeded canary did not fire - a contour that detects nothing proves nothing. PR gating is deferred until the nightly runs have a failure history (strategic ADR-4); cadence and measured cost are in `docs/BUILD_VS_RELEASE.md`. |

CI confirms a substantial common baseline. It does not replace device-specific
manual checks, the specification handoff, or release acceptance: physical-device
permissions, visual judgement, performance interpretation, and user acceptance
remain intentionally outside generic CI.

## 11. Research-ready measurement model

To evaluate effectiveness rather than merely count controls, collect one row per
gate/run with: rule family, trigger surface, changed-file scope, elapsed time,
verdict, finding type, true/false positive decision, remediation time, detection
stage, escaped-defect linkage, and whether the same condition recurs. Aggregate:

1. prevention yield: true findings per run and per changed file;
2. cost: wall time, queue delay, and developer/agent context cost per finding;
3. latency: first detection stage versus implementation and release stages;
4. noise: advisories, unrelated-tree failures, duplicates, and overridden or
   re-run verdicts;
5. coverage gaps: audit findings with no existing gate, separated from findings
   that static analysis cannot reliably decide; and
6. automation maturity: manual, executable-on-demand, locally mandatory, CI
   mandatory, release mandatory, and device/owner mandatory.

The strongest candidates for new automation are recurring audit findings with a
stable, observable pattern. The audit protocol defines the preferred progression:
project script gate first, custom Android Lint for structural feedback, then a
benchmark threshold for quantitative regressions.
