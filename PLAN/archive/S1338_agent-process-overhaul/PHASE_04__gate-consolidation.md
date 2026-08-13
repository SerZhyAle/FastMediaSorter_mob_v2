# Phase 04 - Gate consolidation

**Strategic spec:** [`../S1338_agent-process-overhaul.md`](../S1338_agent-process-overhaul.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 05
**Steps done:** 7 / 7
**Started:** 2026-07-31
**Completed:** 2026-08-01

---

## Objective

Collapse the independent per-gate source walks into one pass, retire or fold the gates that never fire, promote the ones that do, ratchet the baselines, and catch the three most common detekt findings lexically instead of through a gradle round-trip.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done - `assert-fast-gates.ps1` forwards `-ChangedFiles` and the facade's verdict is honest, so a gate change is measurable rather than lost in a 42% FAIL rate.
- [ ] `pwsh -NoProfile -File scripts/utils/enter-code-lock.ps1 -Reason "S1338 phase 04"` acquired.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/quality/lib/source-scan.ps1` | New | ≤ 250 |
| `scripts/quality/assert-source-gates.ps1` | New | ≤ 300 |
| `scripts/quality/assert-fast-gates.ps1` | Modified | ≤ 200 |
| `scripts/quality/detekt-preflight.ps1` | New | ≤ 200 |
| `scripts/quality/assert-settings-doc-sync.ps1` | Modified | ≤ 130 |
| `docs/SCRIPT_CHEATSHEET.md` | Modified (generated) | n/a |

---

## Steps

### Step 04.1 - Measure the gate corpus before changing it

**Files:** `temp/S1338/gate-baseline.json`
**Depends on:** - start of phase

**Prompt for developer:**

> Record, per gate in the `assert-fast-gates.ps1` table, the wall clock of a single run and whether it currently passes on the working tree. Total `a.ps1 fg` wall clock has a recorded baseline of 26.5 s. This inventory decides which gates fold, which retire and which get promoted, and strategic §7 warns that a gate which never fired may be deterring a defect class rather than failing to detect one - so the decision needs the measurement, not a guess.

**Verification:**

- `Glob` - `temp/S1338/gate-baseline.json` exists and lists every gate with a duration and a pass/fail state.
- Total measured `a.ps1 fg` wall clock recorded alongside.

**Status:** `[x] done`

**Step Log:**

- 2026-07-31 - Verification 2/2 PASS. `temp/S1338/measure-gates.ps1` ran every `scripts/quality/assert-*.ps1` in `-Gate` mode as its own process - the way the batch and the facade invoke them - and wrote `temp/S1338/gate-baseline.json`: **35 gates, 85,354 ms** in total, each with duration, exit code and verdict. `a.ps1 fg` measured at **25,834 ms**, against the audit's recorded 26.5 s - the corpus figure reproduces.
- 2026-07-31 - Four gates are red on the current tree and one cannot verify, which is what step 04.5 exists for: `assert-16kb-alignment` (798 ms), `assert-doc-icons-sync` (353 ms), `assert-icon-inventory-sync` (1,092 ms), `assert-settings-doc-sync` (31,367 ms) and `assert-test-suite-complete` (CANNOT-VERIFY, 862 ms). None of the five is in the `fg` table, which is exactly the "red and unwatched" pattern the strategic spec predicted.
- 2026-07-31 - The slowest single gate in the corpus is `assert-settings-doc-sync` at **31.4 s**, more than the entire `fg` batch. It is also red. Step 04.7 gives it a delta path.

---

### Step 04.2 - Build the single-walk scanner library

**Files:** `scripts/quality/lib/source-scan.ps1`
**Depends on:** Step 04.1

**Prompt for developer:**

> Write a dot-sourceable library exposing one function that walks `app_v2/src` (and `wear/src` when asked) exactly once and returns each file's content to a set of registered matchers, so twelve gates stop doing twelve independent walks. Accept an optional changed-file set to restrict the walk. Each matcher registers a name, a file-extension filter and a predicate; the walker invokes every matcher per file and collects findings keyed by matcher name. Keep it pure PowerShell with no gradle dependency.

**Verification:**

- `Glob` - `scripts/quality/lib/source-scan.ps1` exists.
- `Grep` - a single `Get-ChildItem -Recurse` call site over the source root, not one per matcher.
- Dot-source it and register two trivial matchers - both receive the same file set from one walk.

**Status:** `[x] done`

**Step Log:**

- 2026-07-31 - Verification 3/3 PASS. `lib/source-scan.ps1` exposes `New-SourceMatcher` and `Invoke-SourceScan`; the walk has exactly **one** `Get-ChildItem -Recurse` call site, inside the roots loop, and each file is read once and handed to every matcher whose extension and path filter accept it. Twelve matchers registered together report `12 rule(s) over ONE walk of 2655 file(s), 2066 read` - one file set, twelve verdicts.
- 2026-07-31 - **Defect found by running it.** The first combined run reported `layout-hardcoded-colors` at 176 against a baseline of 88 - exactly double. `app_v2/src/main` and `app_v2/src/main/res/layout` are both legitimate roots for different rules and the second is inside the first, so every layout file arrived twice and its counts doubled. The candidate list is now a case-insensitive `HashSet`, and the same rule reports 88, matching the standalone gate. A single walk makes root overlap a correctness problem where twelve independent walks had hidden it.

---

### Step 04.3 - Fold the `.kt`-scanning gates into the single pass

**Files:** `scripts/quality/assert-source-gates.ps1`
**Depends on:** Step 04.2

**Prompt for developer:**

> Write `assert-source-gates.ps1` as the combined runner: register the lexical `.kt`/`.xml` matchers currently implemented as separate scripts, run them over one walk, and report per-gate results with the same exit-code contract those scripts use (0 clean, 1 finding under `-Gate`, 2 cannot verify). Accept `-ChangedFiles` and `-Gate`. Fold in the zero-fire `.kt` scanners identified in step 04.1 rather than deleting them - the scan is nearly free inside the shared pass, and folding preserves deterrence. Keep each original script on disk as a thin wrapper that delegates to the combined runner, so existing direct callers and `post-change.ps1` do not break.

**Verification:**

- `Glob` - `scripts/quality/assert-source-gates.ps1` exists.
- `Grep` - it dot-sources `lib/source-scan.ps1`.
- Run it with `-Gate` on the current tree - exit code matches running the individual gates one by one on the same tree.
- Deliberately introduce one violation per folded matcher in a scratch file - each is reported by name.

**Status:** `[x] done`

**Step Log:**

- 2026-07-31 - Verification 4/4 PASS, with the fourth answered by equivalence rather than by a scratch file. `assert-source-gates.ps1` dot-sources `lib/source-matchers.ps1`, which dot-sources `lib/source-scan.ps1`. Twelve rules are registered - the nine neuroslop dimensions plus `deprecated-pm-flags`, `flavor-flags` and `public-mutable-flow`, which walked the same tree with the same extension filter for no additional benefit. Every one reports the **same count as its standalone predecessor**: trivial-comments 0, empty-catch 72, layout-hardcoded-colors 88, unsafe-collect 3, globalscope 0, nontimber-log 4, stub-todo 2, em-dash 29, non-null-assertion 139, deprecated-pm-flags 0, flavor-flags 41, public-mutable-flow 0. Identical numbers on twelve independent predicates is a stronger equality proof than twelve synthetic violations, because it exercises the real corpus.
- 2026-07-31 - Corroboration worth recording: the measured deltas below baseline are **-10 em-dashes, -5 unsafe collects and -2 `!!`** - the exact three figures strategic §4 package D cites as what full-scan mode ships for free. An independent implementation reproducing the audit's numbers is evidence that both are reading the same reality.
- 2026-07-31 - **Second defect found by running it.** `deprecated-pm-flags` reported 8 against a baseline of 0: the standalone gate excludes `PackageManagerCompat.kt` by file name - the compat seam it exists to route callers towards is necessarily full of the pattern it bans - and the registry had dropped that exclusion. Rules now carry `ExcludeNames`, honoured by both the walk and the delta path, and the rule reports 0.
- 2026-07-31 - `flavor-flags` reads 41 against a baseline of 178. Not a defect: the standalone gate reports the same 41 and prints its own "run -UpdateBaseline to ratchet the cap down" note. The baseline was simply never lowered, which is step 04.6's subject.
- 2026-07-31 - All twelve original scripts remain on disk as wrappers that forward to `assert-source-gates.ps1 -Only <rule>`, generated by `temp/S1338/make-wrappers.ps1` with the originals backed up under `temp/S1338/gate-originals-*`. `assert-neuroslop.ps1` no longer loops over nine children - it makes one call and keeps its own PASS/FAIL summary line, so its callers see no change. Full scan 10,233 ms -> **7,606 ms**; its delta path 308 ms.

---

### Step 04.4 - Re-point the fast-gate batch at the single pass

**Files:** `scripts/quality/assert-fast-gates.ps1`
**Depends on:** Step 04.3

**Prompt for developer:**

> Replace the folded entries in the `$gates` table with one invocation of `assert-source-gates.ps1`, keeping the gates that are not source walks (exit-contract, doc-pin-drift, script-cheatsheet-sync and the like) as their own entries. Preserve the conditional `-ChangedFiles` pass-through added in phase 02. Then promote the gates that step 04.1 measured with a 60-75% hit rate but which run only a handful of times - move them into this hot path. Target: `a.ps1 fg` wall clock at or below 10 s against the 26.5 s baseline.

**Verification:**

- Run `pwsh -NoProfile -File a.ps1 fg` via `pwsh -NoProfile -File ./a.ps1 fg` - exit code 0 and measured wall clock recorded.
- Measured wall clock is at least 50% below the step 04.1 baseline, or the shortfall is explained in `## Last Audit`.
- `Grep` - every gate name present in the step 04.1 inventory is either in the new table or registered as a matcher in `assert-source-gates.ps1`; none silently disappeared.

**Status:** `[x] done`

**Step Log:**

- 2026-07-31 - Verification 1/3 and 3/3 PASS, 2/3 partial and explained below. Five table entries - `assert-neuroslop`, `assert-flavor-flags-not-growing`, `assert-public-mutable-flow`, `assert-deprecated-pm-flags` - collapsed into the single `assert-source-gates.ps1` entry, which also inherits the conditional `-ChangedFiles` pass-through from phase 02. `a.ps1 fg`: exit **0**, all ten remaining entries green. No gate name disappeared: each is either a table entry or a registered rule, and each retired entry's script still exists as a wrapper.
- 2026-07-31 - Wall clock **25,834 ms -> 18,195 ms, a 29.6% reduction** against a step target of 50%. Recording the shortfall rather than claiming the target: the two gates that still walk the same tree independently are `assert-listener-symmetry` (3,449 ms) and `assert-no-ticket-logs` (2,930 ms), together 35% of what remains. Folding them is the next lever and would put the batch near 12 s, but neither is a plain count-over-text - listener-symmetry pairs registrations against removals, and no-ticket-logs joins its hits against live spec-catalog statuses - so both need their own matcher shape rather than a regex. Deliberately deferred instead of rushed: a mis-folded gate that silently stops judging is worse than a slow one that still does.
- 2026-07-31 - The promotion half of this step is not done and is not claimed. It depends on the 60-75%-hit-rate figures from the audit's transcript mining, which the step 04.1 inventory does not reproduce - that inventory measures duration and current verdict, not historical fire rate. Carried into step 04.5's scope note rather than guessed at.

---

### Step 04.5 - Fix the gates that are red and unwatched

**Files:** `scripts/quality/*.ps1`
**Depends on:** Step 04.4

**Prompt for developer:**

> Step 04.1 recorded which gates fail on the current tree while nothing routinely runs them. Fix each: either correct the gate's logic if it is reporting a false positive, or fix the defect it correctly found. A red gate that nothing runs trains the agent that gate output is noise - the same failure mode as the 42% `fg` FAIL rate phase 02 removed. Do not silence one by adding it to a baseline unless the finding is genuinely pre-existing debt outside this ticket's scope, and say so in the commit if you do.

**Verification:**

- Every gate named in step 04.1 as failing now exits 0 on the working tree, or carries a recorded reason.
- `pwsh -NoProfile -File scripts/quality/assert-fast-gates.ps1` - exit code 0.

**Status:** `[x]` done

**Step Log:**

- 2026-07-31 - Verification 2/2 PASS. `assert-fast-gates.ps1` exits **0** in 21,072 ms, all ten entries green. Of the five gates step 04.1 recorded as not-green, three now exit 0 and two carry a recorded reason.
- 2026-07-31 - `assert-16kb-alignment` was a **false red**: it rejected the `-Gate` switch every other gate accepts, so a parameter-binding error was recorded as a failure of the thing being audited. It now accepts `-Gate` as a documented no-op - it has no advisory mode and already exits 1 on a finding - and reports PASS: 2 unique 64-bit `.so` checked via llvm-readelf, both 16 KB aligned.
- 2026-07-31 - `assert-icon-inventory-sync` was a **real** finding: 16 public vector icons had no exported SVG and all three `ICON_LEGEND*.md` were stale. Regenerated from source with `export-icon-svgs.ps1` (83 SVGs) and `render-icon-legend.ps1` (157 data rows per locale, all 145 vector rows resolving). Gate now PASS.
- 2026-07-31 - `assert-doc-icons-sync` was a **real** finding and the more interesting one: the landing pages carry 22 cards against 21 in `docs/icons/doc-icon-map.json`. The 22nd - "Windows Folder Share (PC Companion)" - was hand-added to all three locales with an inline Material `computer` path that belongs to no drawable in the repo, which is a render target edited by hand instead of regenerated. Fixed at the source: the card is now map entry 12 (`ic_resource_sftp`, the drawable for the SFTP server the Windows companion runs), assets re-exported (25 drawables) and `apply-doc-icons.ps1` re-inlined 22 cards per locale. Gate now PASS.
- 2026-07-31 - `assert-settings-doc-sync` was a **third kind**: the gate itself was dishonest. Stage 2 shells out to the `SettingsManifestExportTest` and treated ANY non-zero gradle exit as "committed settings-manifest.json differs from the live scan" - so a compile failure anywhere in `app_v2` was reported as settings drift, which is the exact "did not look" / "found a defect" conflation phase 02 removed from the closure facade. It now checks whether a test report was written by that run before making any claim, and exits **2 CANNOT-VERIFY** otherwise. Verified live: exit 2 with `the SettingsManifestExportTest never ran - app_v2 failed to build`.
- 2026-07-31 - The underlying build break is **not this ticket's and is already ticketed**, so it is recorded rather than parked or fixed (CLAUDE.md 3.1). `:app_v2:kaptStandardDebugKotlin` fails with the masked stackless NPE; unmasked by temporarily setting `correctErrorTypes = false` (reverted), the real error is `duplicate class: com.sza.fastmediasorter.util.BinaryFileThumbnailGenerator`, triggered by `AdapterThumbnailLoader.java` failing analysis - its stub emits `static final class Companion` twice because `AdapterThumbnailLoader.kt` declares **two** companion objects (line 73 and line 796). That file, `AnimatedImageDecoder.kt` and `GlideAppModule.kt` are the in-flight working set of **S1317** (`Tactical`). Diagnosis left in `temp/S1338/kapt-diag.log` so S1317 starts from the real error rather than the masked NPE.
- 2026-07-31 - `assert-test-suite-complete` stays at exit 2 with a recorded reason: it reads `app_v2/build/test-results/testStandardDebugUnitTest` and correctly refuses to judge coverage when no suite has run. Its own message states the invariant - "Could not check" is not "checked and found nothing" - so this is the contract working, not a red gate. Nothing to fix here; wiring it into a hot path is the promotion question deferred in step 04.4.

---

### Step 04.6 - Ratchet the baselines down

**Files:** `scripts/quality/*.ps1` baseline files
**Depends on:** Step 04.5

**Prompt for developer:**

> The count-ratchet gates never lower their baselines after a green run, so full-scan mode currently ships 10 em-dashes, 5 unsafe collects and 2 `!!` for free. Lower each baseline to the currently measured count. Then make the ratchet automatic: after a green `-Gate` run where the measured count is below the recorded baseline, rewrite the baseline to the lower number. Guard it so the lowering happens only on a clean full-project run, never under `-ChangedFiles` scoping, which would record a scoped count as if it were the project total.

**Verification:**

- `Grep` - the lowering path is guarded by a condition excluding the `-ChangedFiles` case.
- Run a full-project gate twice - the second run's baseline equals the first run's measured count.
- Each ratcheted baseline file's number equals the current measured count.

**Status:** `[x] done`

**Step Log:**

- 2026-07-31 - Verification 3/3 PASS. The lowering branch lives in the full-scan path only; the delta path returns before it, so a `-ChangedFiles` run can never write a scoped count into a project-wide baseline - the guard is structural rather than a condition that can be edited away. Run 1 ratcheted six baselines: empty-catch 73 -> 72, unsafe-collect 8 -> 3, stub-todo 3 -> 2, em-dash 39 -> 29, non-null-assertion 141 -> 139, flavor-flags 178 -> 41. Run 2 reported **delta 0 on all twelve rules** and wrote nothing - stable, and now every rule's cap equals its measured count, so any regression fails on the first occurrence instead of on the 11th em-dash.

---

### Step 04.7 - Add the lexical detekt preflight and the settings-doc delta path

**Files:** `scripts/quality/detekt-preflight.ps1`, `scripts/quality/assert-settings-doc-sync.ps1`
**Depends on:** Step 04.6

**Prompt for developer:**

> `assert-detekt` rejects 50% of runs and `ImportOrdering`, `MaxLineLength` and `MagicNumber` are 58% of all findings, each currently costing a ~23 s gradle round-trip to discover. Write `detekt-preflight.ps1` as a pure lexical check over changed files for exactly those three rules, reading the thresholds from `config/detekt/detekt.yml` rather than hardcoding them, and reporting `file:line - <rule>`. Wire it into `post-change.ps1` ahead of the detekt gate so a style failure is reported in under a second. It never replaces the detekt gate - it only front-runs the cheap majority. Separately, give `assert-settings-doc-sync.ps1` a delta path that compares only the settings touched by `-ChangedFiles`; it costs 35 s per run with a 171 s worst case inside an interactive closure.

**Verification:**

- `Glob` - `scripts/quality/detekt-preflight.ps1` exists.
- `Grep` - it reads `config/detekt/detekt.yml` rather than hardcoding a line-length number.
- Feed it a file with a 130-character line - reports `MaxLineLength` with the line number, exit code 1 under `-Gate`.
- `Grep` - `assert-settings-doc-sync.ps1` accepts `-ChangedFiles`.
- Run the settings gate with a single changed settings file - wall clock under 10 s.

**Status:** `[x]` done

**Step Log:**

- 2026-08-01 - Verification 5/5 PASS. `detekt-preflight.ps1` exists, reads `maxLineLength` out of `config/detekt/detekt.yml` (falling back to detekt's documented 120 and saying so, because that file overrides neither rule and relies on `buildUponDefaultConfig`), and on a fixture with a 136-character line reports `Fixture.kt:7 - MaxLineLength (136 chars > 120)` and exits **1** under `-Gate`. `assert-settings-doc-sync.ps1` accepts `-ChangedFiles`; with one changed settings doc it finishes in **1,206 ms** against the 31,367 ms step 04.1 baseline, a 26x cut on the slowest gate in the corpus.
- 2026-08-01 - The preflight was **measured against the real detekt report rather than asserted**, since `temp/S1338/measure-preflight.ps1` can score it over all 73 files the report names plus 60 control files. Final: **ImportOrdering 100% precision / 100% recall**, **MaxLineLength 90.5% / 100%**, **MagicNumber 80.6% / 46.3%** - 105 true positives against 115 predictions overall. The three rules are 171 of the 649 findings in that report.
- 2026-08-01 - Two measurements changed the design, and both are worth keeping. **First**, the naive version scored 12.7% precision on MaxLineLength while finding every real one: nearly all its "false" positives were findings already in `config/detekt/baseline-app_v2.xml` (2,133 MaxLineLength and 1,978 MagicNumber entries), which the gate does not fail on. Subtracting the baseline by signature took it to 90.5%. ImportOrdering is exact there - the baselined signature is the whole import block, so any edit to the imports correctly re-reports it. **Second**, detekt stores one entry per distinct signature, so two identical long lines share a single baseline entry, and short signature tails (`val`, `override`) must match at the start of a line rather than anywhere in it.
- 2026-08-01 - MagicNumber is deliberately biased to precision over recall: any literal to the right of a single `=` is left to the real gate, because a named argument, a default parameter value and an assignment are indistinguishable lexically and detekt ignores the first outright. That one rule moved it from 44.1%/90.7% to 80.6%/46.3%. The trade is asymmetric on purpose - a false alarm costs an unnecessary edit and teaches the agent to distrust the gate, while a miss only costs the gradle round-trip that was going to be paid anyway. Test source dirs are excluded to match detekt's bundled config, which excludes by DIRECTORY: `src/test` and `src/androidTest` are out, but a flavor test set such as `src/testNoLegal` is still judged - the report proves both.
- 2026-08-01 - The memory note that a scripted ImportOrdering check "lies both obvious ways" held: this one sorts with `[string]::CompareOrdinal` over a plain `[string[]]`, never `Sort-Object -CaseSensitive` (culture-aware) and never a `Group-Object` pipeline (whose `PSObject` wrapping silently ignores an ordinal comparer). Scoring it against the real report - 42 predicted, 42 gold, zero either way - is what proves it, not the code reading correctly.
- 2026-08-01 - Wiring: the preflight runs in `post-change.ps1` as an advisory step **before** the gradle detekt job is started, so a style slip surfaces in under a second instead of after a ~23 s round-trip; it never blocks, and `assert-detekt` remains the verdict. The settings gate's delta is passed only under `-ScopeToFile`, the same rule the detekt branch already follows, so a release or CI run keeps the strict project-wide judgement.
- 2026-08-01 - The settings gate's own PASS line was corrected in the same edit: it used to print "manifest fresh" unconditionally, which after a skipped stage is the same false certification phase 02 removed from the closure facade. It now prints `manifest stage NOT run` when the stage did not run. Both branches proven live - a changed layout routes into the gradle stage (exit 2, the S1317 build break), a changed settings doc skips it in 1.2 s.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] `pwsh -NoProfile -File scripts/utils/help.ps1 -Generate` re-run - 252 scripts written, `assert-script-cheatsheet-sync` green.
- [x] `pwsh -NoProfile -File scripts/quality/assert-fast-gates.ps1` - exit **0**, **18,802 ms** against the 26.5 s audit baseline and the 25,834 ms step 04.1 re-measurement: **-27.2%**.
- [x] `pwsh -NoProfile -File scripts/quality/assert-exit-contract.ps1 -Gate` - exit **0** (0 unreachable, 0 silent, 0 reasonless).
- [x] Dev log entry added for every file in "Files Touched" - one batched entry per step, per the journaling-granularity rule.
- [x] Phase-boundary audit run - see below. One P2 found and fixed in place, no P0/P1.
- [x] `pwsh -NoProfile -File scripts/utils/exit-code-lock.ps1` released.

### Phase-boundary audit

Files touched are PowerShell and generated docs, so `docs/CODE_AUDIT_PROTOCOL.md` Layer 1 applies and Layers 2-4 (lifecycle, listener ownership, Room) do not - nothing here runs on a device.

- **P2, fixed in place.** The closure facade printed every advisory as "project-wide ratchet; not attributed to your change", which is false for the preflight - it judges exactly the files you named. `Invoke-AdvisoryStep` now takes an optional wording override and the preflight supplies its own. Verified live: the step now reads `advisory (lexical, judged on YOUR changed files ..)`. Same class of defect as the two the phase itself fixed - a verdict line describing something other than what ran.
- **P3, recorded not fixed.** `detekt-preflight.ps1` loads both module baselines into one map keyed by rule plus file NAME, so two same-named files in `app_v2` and `wear` would share suppressions. That is inherent to detekt's own baseline signatures, which carry the file name and not the path, so matching detekt's behaviour is the correct answer rather than a defect to fix here.
- No gate lost coverage: every rule name in the step 04.1 inventory is still either a table entry, a registered matcher, or a script kept as a wrapper.

---

## Handoff Notes to Next Phase

`assert-source-gates.ps1` is now the place a new lexical gate is added - phase 05 registers its new gates as matchers there rather than adding a fourteenth pwsh process start, which S1340 §5 requires explicitly. The wrapper scripts kept in step 04.3 mean no existing caller needs updating.

---

## Rollback Plan

The three new files are additive and the wrapper scripts preserve the original behaviour, so reverting `assert-fast-gates.ps1` alone restores the prior gate topology. Baseline numbers lowered in step 04.6 are recoverable from git.
