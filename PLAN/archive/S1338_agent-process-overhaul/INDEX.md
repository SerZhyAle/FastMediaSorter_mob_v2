# Tactical Plan: S1338 - agent-process-overhaul

**Strategic spec:** [`../S1338_agent-process-overhaul.md`](../S1338_agent-process-overhaul.md)
**Research inputs:** none - `dev/AGENT_PROCESS_AUDIT_2026-07-31.md` is the source analysis, cited by the strategic spec
**Feature:** Agent process overhaul (umbrella)
**Tier:** 3
**Priority:** 75
**Status:** In Progress
**Phases:** 11 / 11 done
**Last updated:** 2026-08-03

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | measurement-and-sensors | - | ✅ Done | 9/9 | [PHASE_01__measurement-and-sensors.md](PHASE_01__measurement-and-sensors.md) |
| 02 | honest-verdicts | 01 | ✅ Done | 8/8 | [PHASE_02__honest-verdicts.md](PHASE_02__honest-verdicts.md) |
| 03 | read-discipline-hook | 01 | ✅ Done | 4/4 | [PHASE_03__read-discipline-hook.md](PHASE_03__read-discipline-hook.md) |
| 04 | gate-consolidation | 02 | ✅ Done | 7/7 | [PHASE_04__gate-consolidation.md](PHASE_04__gate-consolidation.md) |
| 05 | missing-quality-gates | 04 | ✅ Done | 6/6 | [PHASE_05__missing-quality-gates.md](PHASE_05__missing-quality-gates.md) |
| 06 | build-hot-path | 02 | ✅ Done | 6/6 | [PHASE_06__build-hot-path.md](PHASE_06__build-hot-path.md) |
| 07 | command-surface | 05 | ✅ Done | 6/6 | [PHASE_07__command-surface.md](PHASE_07__command-surface.md) |
| 08 | agent-memory | 02 | ✅ Done | 6/6 | [PHASE_08__agent-memory.md](PHASE_08__agent-memory.md) |
| 09 | script-contracts | 02 | ✅ Done | 8/8 | [PHASE_09__script-contracts.md](PHASE_09__script-contracts.md) |
| 10 | ksp-migration | 06 | ✅ Done | 6/6 | [PHASE_10__ksp-migration.md](PHASE_10__ksp-migration.md) |
| 11 | docs-catalog-cleanup | all | ✅ Done | 5/5 | [PHASE_11__docs-catalog-cleanup.md](PHASE_11__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

Eleven phases exceeds the usual 3-8 target. This is an umbrella that has already been split once - S1339, S1340, S1341 and S1342 carry the three largest pieces plus the canon propagation - and strategic §5 fixes the order of what remains. Splitting further would separate packages that must be judged against one baseline.

---

## Package to phase map

Every work package in strategic §4 maps to a phase. Nothing is silently dropped, per strategic §3.

| Package | Phase | Note |
|---|---|---|
| A - measurement | 01 | steps 01.1 to 01.7 |
| B - statusline | 01 | step 01.8, plus promoting the wiring out of `settings.local.json` |
| C - `--configuration-cache` | 01 | step 01.9, now measure-then-decide (see below) |
| C - timeouts, lock `-Wait`, backgrounding rule, build cache, `-Tests`, catalog_sync | 06 | |
| C - KSP migration | 10 | alone and last, per strategic §5 and §7 |
| D - `-ChangedFiles` plumbing | 02 | step 02.7 |
| D - single pass, retire/fold/promote, ratchet, red gates, detekt preflight, settings-doc delta | 04 | |
| E - closure facade | 02 | except the memory-file retirement, which is phase 08 |
| F - read hook | 03 | |
| G - command surface | 07 | |
| H - agent memory | 08 | includes E's 29 workaround memory files |
| I - missing quality gates | 05 | gates only; the rule text is S1340 §3.1 |
| J - script contracts | 09 | except `AGENTS.md`, delegated - see below |

---

## Planning findings that correct the strategic spec

Three facts were verified against the working tree during planning and change what the steps can assume. Each is written into the owning step rather than left as a footnote.

- **`--configuration-cache` is not a safe one-liner.** `gradle.properties:28` sets `org.gradle.configuration-cache=false` deliberately, because Chaquopy 17.x breaks the configuration-cache store for the noLegal task graph. Strategic §4 package C calls this the highest saving-to-effort item in the audit on the assumption the flag simply works. Step 01.9 measures first and accepts "recorded decision not to land" as a valid outcome.
- **The local build cache is disabled on Windows on purpose.** `gradle.properties:41` claims `org.gradle.caching=true`, but `settings.gradle.kts` disables the local cache because Gradle 9.x fails packing outputs such as `RClassOutputJar`. Strategic §4 package C asks to "verify the build cache is actually enabled"; step 06.4 answers it and corrects the misleading property either way.
- **The KSP precedent covers one processor, not three.** `wear/build.gradle.kts` applies KSP for the Hilt compiler only - it declares no Room and no Glide processor. Strategic §4 package C's "wear already did it and the plugin is on the classpath" is true for Hilt and unevidenced for Room and Glide. Step 10.1 records this before migrating, and steps 10.2 to 10.4 are separable so one processor can revert alone.

---

## Scope boundaries with the child tickets

The umbrella and S1340 both list several CLAUDE.md corrections. Resolved as follows, and recorded so neither ticket re-litigates it:

- **Lands here (S1338):** the corrections that must ship in the same change as the script or hook they describe - section 12's `-ScopeToFile` text (phase 02, which both specs already agree on), line 77's parallel-build contradiction (phase 06), section 7's PowerShell-tool scope and Rule 24's disagreement with the hook (phase 09), and section 3's contents (phase 07).
- **Belongs to S1340:** the *compression* of the rules, the rule text for the four new gates in §3.1, and the fate of `AGENTS.md` and `.github/copilot-instructions.md` (§3.4). Phase 05 builds those gates; S1340 writes their rules.
- **Consumed by S1339:** `scripts/spec_catalog/unverified-backlog.ps1` from step 05.5 - its exit codes and `-Json` shape are a contract.
- **Consumed by S1342:** the portable artefacts named in strategic §9 - the extractor (phase 01), the statusline (phase 01), the read hook (phase 03), the closure invariants (phase 02) and the memory rules (phase 08).

---

## Cross-cutting constraints

- **Regenerate the cheatsheet whenever a `param()` block changes.** `docs/SCRIPT_CHEATSHEET.md` is generated from every script's parameter block via the AST and gated by `assert-script-cheatsheet-sync.ps1`. Phases 01, 02, 04, 05, 06, 08 and 09 all change signatures; each carries the regeneration in its Done Criteria.
- **No product code.** Strategic §3 excludes `app_v2/` and `wear/` source, user-facing strings and release artefacts. Phase 10 touches `app_v2/build.gradle.kts` only - the build graph, not the code.
- **No probe tags.** This ticket never enters `BlockNeedUserTest`, so no `Timber.d("S1338:` line may ever exist. Checked in phase 11.
- **`CODE.LOCK` per phase.** Phases 02, 04, 05, 06, 09 and 10 acquire it at the start and release it in their Done Criteria.

---

## Pre-Implementation Blockers

None. Strategic §6 carries no Open research items - the audit resolved them, and Phase 01 can start immediately.

## Phase ordering conditions

Not blockers on Phase 01; each is enforced by the named phase's own Prerequisites.

- Phase 07 step 07.3 routes `/spec-do` in CLAUDE.md section 3. S1339 owns that command file. Either S1339 has landed it, or its filename is fixed, before phase 07 starts.
- Phase 10 starts only after every other phase is Done - strategic §5 sequences it last and alone.

---

## Completion Gate

- [x] All phases show ✅ Done, or carry an explicit recorded decision not to land, per strategic §10.
- [x] `docs/FEATURES*.md` - **skipped**. Strategic §3 excludes user-facing surfaces; this ticket ships no capability and writes no `docs/ALL_FEATURES.jsonl` record.
- [x] `dev/CHANGELOG.md` has an entry for every logical change.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated - `catalog_sync.ps1 -Module app_v2` exit 0, "up to date"; phase 10 changed the build graph, not a class.
- [x] `docs/SCRIPT_CHEATSHEET.md` regenerated and its sync gate green (phase 11 step 11.1).
- [x] Strategic §6 re-measurement run and its numbers written back into the spec file - §6.1 to §6.4.
- [ ] `/spec-check S1338` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

**Note on the last two.** Strategic §10 makes `Verified` conditional on the §6 re-measurement, and §6 asks for that re-run two weeks after the first four packages landed - that window closes around 2026-08-14. The KSP numbers are in §6.4 and true now; the detekt FAIL rate and the failed-`Edit` counter-metric need the full window. `Implemented` is the honest status until then.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1338`.

---

## Blockers Log

- **2026-08-03 - phase 10 and step 11.3 UNBLOCKED and closed.** S1317 landed and moved to `BlockNeedUserTest`, `a.ps1 fk` returns 0, and the task the blocker named no longer exists in the graph at all. Phase 10 ran end to end: all three processors on KSP, six flavors compiling, a minified `assembleNoLegalRelease` with no R8 warning naming generated DI, Room or Glide code, the full unit suite green, and the annotation chain measured 73.69 s -> 12.05 s. Step 11.3 re-ran: eleven gates green, `fk` green, exit-contract and memory-budget green, full-project detekt red on 13 findings in one file this ticket never opened (649 two days ago).
- **2026-08-01 - phase 10 and step 11.3, blocked on S1317.** `:app_v2:kaptStandardDebugKotlin` fails on the working tree, so `a.ps1 fk` exits 1 and the full-project detekt gate reports 649 weighted issues. S1338 has changed no `.kt` file in any phase, and the same failure is in a build log timestamped before this session's first edit. The KSP migration is a measured change - it needs a compile baseline and a green per-flavor build, and neither exists on a tree that does not compile. Unblocks when S1317 (`Tactical`) lands or reverts.
- **2026-08-01 - step 07.3, blocked on S1339.** `/spec-do` cannot be routed in CLAUDE.md section 3 until S1339 lands `.claude/commands/spec-do.md`. One-line edit for whoever closes S1339.
- **2026-08-01 - step 07.1, owner decision.** The command-alias merges are owner-facing; the measured table and the single `/ns` recommendation are in the phase log.

---

## Change Log

- 2026-08-03 - Phases 10 and 11 Done; umbrella `Implemented`. All three processors on KSP with zero `kapt` tokens left in `app_v2/build.gradle.kts`, proven across six flavors, a minified `assembleNoLegalRelease`, a byte-identical Room schema and the full unit suite. The annotation chain measures 73.69 s -> 12.05 s and the full chain 143.41 s -> 61.55 s (strategic §6.4), with `ksp.incremental=false` recorded as the migration's cost rather than omitted. The phase-boundary audit removed three ProGuard keep rules for a Glide facade KSP does not generate - and which kapt had emitted one package lower, so they had matched nothing all along - and the minified build was re-run afterwards. Closing the ticket exposed a false CANNOT-VERIFY in `assert-detekt`'s staleness check that would fire on any closure not touching `wear/`; fixed to judge staleness per module, verified in both directions.
- 2026-07-31 - Initial tactical plan authored by `/spec-tech`.
- 2026-07-31 - Phase 02 Done. The closure facade now scopes every gate to the whole `-Files` set, refuses an unexpanded argument with exit 2, prints PASS only when nothing failed, runs the gates before the mutating steps, forwards `-ChangedFiles` to the five gates that accept it, and prints rule/line/message on a detekt failure. Two P1 defects were found by running it rather than by reading it - see the phase's audit block.
- 2026-07-31 - Phase 03 Done. `guard-uncapped-read.ps1` blocks an uncapped `Read` of a file over 200 lines and is live in the session that wrote it; the policy it enforces is documented in `docs/AGENT_COST_PLAYBOOK.md` and deliberately in no command file.
- 2026-08-01 - Phase 04 Done (7/7). Steps 04.5 and 04.7 landed: three of the five not-green gates now exit 0 (the 16 KB gate was a false red from a rejected `-Gate` switch; the icon SVGs, legends and doc-icon map were regenerated from source), and the settings-doc gate stopped reporting a build failure as manifest drift - it exits 2 CANNOT-VERIFY, which is what it does today because S1317's in-flight Kotlin breaks kapt. The lexical detekt preflight ships measured rather than asserted: ImportOrdering 100/100, MaxLineLength 90.5/100, MagicNumber 80.6/46.3 precision/recall against the real detekt report. The settings gate's delta path takes its worst case from 31.4 s to 1.2 s. `a.ps1 fg` now 18.8 s against the 26.5 s baseline.
- 2026-08-01 - INDEX reconciled with the working tree: phase 09 shows 5/8 In Progress, which is what its step statuses say. The row had never been updated when those steps landed.
- 2026-08-01 - Phase 08 Done (6/6). The memory index is now gated by `assert-memory-budget.ps1` in the fast batch, with two advisories - dead paths and tickets that no longer exist. The measurement corrected the phase's own premise: only `MEMORY.md` is billed per turn, so the prune took the 14-file intersection of never-read and dead-ticket rather than the ~130-file union, and the index came down 19,122 -> 16,595 B with every surviving pointer intact. The 9,000 B target ships as a ratchet, not as a red gate - closing the rest is an owner decision. `[[link]]` hygiene fails pre-existing and is parked as S1345.
- 2026-08-01 - Phase 06 Done (6/6). Every gradle-backed gate now runs under a 600 s ceiling through `scripts/utils/process-timeout.ps1`, and a timeout is exit 2 CANNOT-VERIFY rather than a green verdict on a gate that never ran. `Enter-AgentLock` and `lock-status.ps1` can wait instead of refusing, reclaiming a dead holder on the next poll. The backgrounding rule is now a measured 120 s threshold with both halves stated. `catalog_sync.ps1` no-ops when the index is current - 66 s saved per redundant call on this host. The build-cache item closes as a recorded decision: gradle itself prints that no cache is configured, and the comparison needs a tree that builds. `a.ps1 fk` stays red at kapt from another ticket's WIP, which predates this phase and is the one Done criterion left unticked.
- 2026-08-01 - Phase 05 Done (6/6). Rule 17 has a gate: `window-insets` is the thirteenth matcher in the single pass, baselined at 28 pre-existing sites and proven to fail on a planted host. `/ui-clarify` and the bugfix repro requirement are now refusal conditions on the two transitions that ship UI and close a bugfix, not prose. `unverified-backlog.ps1` publishes the backlog S1339 stops on - and disagreed with the audit's 6.9:1 ratio, which is arithmetically 2.23:1 over the same counts; the strategic spec is corrected. `post-change.ps1` triggers the document-registry loop on registered paths and degrades its verdict until the records are acknowledged.
- 2026-08-01 - Phase 09 Done (8/8). The `.ps1` guard's heredoc and quote handling was verified against the live hook rather than its header - all four predicates hold. The spec-header mutator now writes atomically and its two undocumented invariants (targeted splice, skip the no-op write) are written down as the failures they prevent. `CLAUDE.md` section 7 now scopes the batching syntax to the PowerShell tool, and Rule 24 states the find hook's two actual conditions instead of a blanket ban.
- 2026-08-01 - Phase 11 steps 11.1, 11.2, 11.4 and 11.5 done; 11.3 partial. The cheatsheet regenerated at 256 scripts (248 at phase 01 - this ticket added eight), both catalogs reported already-current in under a second, and the re-measurement is written into strategic §6.2 as a carry-forward rather than an effect, because the two-week window section 6 asks for has not elapsed. Measured and true now: `a.ps1 fg` 26.5 -> 18.9 s, `MEMORY.md` 18,839 -> 16,595 B, the fast-gate battery 42% FAIL -> 11/11 PASS. Unchanged and expected to be: the pre-compaction median at 389,197 exactly, since S1339 owns that threshold. `## Last Audit` gives all ten packages a verdict. Phase 10 is blocked, so the umbrella is `BlockByOtherTask`, not `Implemented` - a blocker is not a recorded decision.
- 2026-08-01 - Phase 07 steps 07.2, 07.4 and 07.5 done; 07.1 and 07.3 carry a named residue each. The six largest pipeline commands are now drivers with an on-demand companion in `.claude/reference/`, the four file skeletons live once in `.claude/templates/`, and 69 paraphrases of numbered rules across 19 files became pointers - six of which had been citing the wrong rule number, which is the drift the step existed to stop. Both stores had to move out of the locations the plan named: `PLAN/` is gitignored, and everything under `.claude/commands/` is registered as an invocable command, so the reference files had briefly widened the routing surface this phase exists to narrow. CLAUDE.md section 3 was missing seven routable commands including `/spec-next`, the command that decides what gets worked on.
- 2026-07-31 - Phase 04 steps 04.1-04.4 and 04.6 done. Twelve lexical rules are now judged over one walk of the tree, every count identical to its standalone predecessor; `a.ps1 fg` 25.8 s -> 18.2 s; six baselines ratcheted down and stable on a second run. Steps 04.5 (red unwatched gates) and 04.7 (detekt preflight, settings-doc delta) remain.
