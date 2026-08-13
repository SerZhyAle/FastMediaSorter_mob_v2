# Phase 07 - Command surface

**Strategic spec:** [`../S1338_agent-process-overhaul.md`](../S1338_agent-process-overhaul.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 05
**Blocks:** none
**Steps done:** 6 / 6
**Started:** 2026-08-01
**Completed:** 2026-08-02

---

## Objective

Shrink the routing decision rather than the byte count: retire the dead commands, split the six largest pipeline files into a driver plus an on-demand reference, move duplicated templates out of prompts, and replace the paraphrased rule copies with pointers.

---

## Prerequisites

- [ ] Phase 05 is ✅ Done - `spec-tech.md` and `spec-dev.md` already changed there; this phase edits them again and must not revert that wiring.
- [ ] S1339 has landed `/spec-do`, or its command file name is fixed, so step 07.3 can route it.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `.claude/commands/*.md` | Modified / Deleted | n/a |
| `.claude/reference/*.md` | New | n/a |
| `.claude/templates/*.md` | New | n/a |
| `CLAUDE.md` | Modified | n/a |

> 33 command files today, largest `skill-release.md` at 31,787 B, `spec-dev.md` at 26,578 B.

**Two location corrections against the step prompts, both forced by how the tree actually behaves.**

- The template store is `.claude/templates/`, not `PLAN/_templates/`. `PLAN/` is gitignored (`.gitignore:144`), so a store under it would be untracked while the command files that depend on it are tracked - a fresh clone would get commands pointing at files that do not exist. `.claude/templates/` is tracked and sits beside its only consumers.
- The reference store is `.claude/reference/`, not `.claude/commands/reference/`. The harness registers every `.md` under `.claude/commands/` as an invocable command, including a subdirectory - the six reference files appeared in the session's command listing as `reference:spec`, `reference:spec-dev` and so on. That widens the routing decision this phase exists to narrow, and it puts six more `description` lines in the per-turn floor. Moving the store one level up removes them from the listing; verified in-session.

---

## Steps

### Step 07.1 - Retire the dead commands

**Files:** `.claude/commands/*.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Identify the command files never or barely invoked across the corpus using the phase 01 extractor, then delete or merge them. Three exclusions are binding: anything CLAUDE.md section 3 routes, anything the owner named as a preference (`/caveman` stays), and `/spec-do`, which is deliberately rare by design - its value is being available, not being used, so it must not be counted against the never-invoked list. Where two files are synonyms, keep one and delete the other rather than maintaining both. Target roughly 20 non-synonymous triggers, down from a 33-way decision. Do not argue this on token savings: the measured floor saving is ~377 tokens, and strategic §8 records that rationale as killed.

**Verification:**

- The retired files are listed with their invocation counts from the extractor.
- `Grep` - no surviving command file references a deleted one.
- `Grep` - `caveman` and `spec-do` still exist under `.claude/commands/`.

**Step log:**

- Measured directly rather than through the extractor, because the question is narrow: count `<command-name>` blocks per command across all 1159 transcript files. **17 of 33 commands were never typed as a slash command**: `doc-update`, `spec-arc`, `quick`, `ns`, `research`, `caveman`, `caveman-commit`, `build`, `catalog`, `git`, `caveman-review`, `skill-fix-release`, `ui-clarify`, `release`, `arc`, `verify`, `spec-update`. Top of the distribution: `spec-all` 58, `spec-next` 46, `spec-draft` 40, `spec-quiz` 30.
- **The audit's "delete the eight never-invoked command files" does not survive verification, and that is the finding.** A zero slash-count means "the owner never typed it", not "dead". Cross-referencing every zero-count command against the repo shows all 17 are reachable another way: 16 are routed by CLAUDE.md section 3 - a binding exclusion in this step's own prompt - or invoked by another command file. `/verify` alone is referenced by 11 command files, including `spec-dev.md`'s `--verify-smoke` path; deleting it on a slash-count would have broken that flow.
- Exactly one candidate survives every filter: **`/ns`** - not routed by CLAUDE.md section 3, referenced by no command file, never typed, and functionally covered by `/quick` plus `/skill-fix`.
- **Not deleted, deferred to the owner.** Getting from 33 to the step's "roughly 20 non-synonymous triggers" needs synonym merges - `/arc` vs `/spec-arc`, `/quick` vs `/skill-fix` vs `/ns`, `/spec-arc` vs `/arc` - and those are owner-facing aliases that CLAUDE.md section 3 documents deliberately. Deleting an owner's alias on an agent's authority is the wrong direction; strategic §8 already killed the token-savings rationale, so nothing here is urgent. The measured table plus the `/ns` recommendation is in the final report as an owner decision.
- `/spec-do` cannot be routed yet: S1339 is `Approved`, not landed, and `.claude/commands/spec-do.md` does not exist. Step 07.3's predicate "every command named in section 3 exists" would fail. Blocked on S1339.

**Owner decision, 2026-08-02 - both merges taken.** The residue above was put to the owner with the measured table and he decided both:

- **`/ns` deleted, folded into `/skill-fix`.** The fold is not a redirect: `/skill-fix`'s scope paragraph now names the doc/config/script tweak explicitly, carries `/ns`'s two admission conditions verbatim (no more than three files, no logical decision) and its second legitimate trigger (the build system is held by a parallel agent, checked through `lock-status.ps1` rather than assumed), and step 3's validation line gained the three cheap non-code checks - grep for a doc, exit 0 for a script, the single consuming target for a config. Deleting the file without moving those would have lost the only thing `/ns` said that `/skill-fix` did not.
- **`arc.md` deleted, the alias kept.** `/arc` survives as a chat alias in CLAUDE.md section 3, and the line now states outright that no command file backs it, so a future reader does not go looking for one.
- Command files: 33 -> **31**. That is short of the step's "roughly 20 non-synonymous triggers", and the step log above already records why the rest of the gap is not real - 16 of the 17 never-typed commands are reachable another way, and the target was drawn against a byte-savings rationale strategic §8 killed.

**Verification, 2026-08-02:**

- `.claude/commands/` file count: expected 31 | actual **31**. `ns.md` and `arc.md` absent.
- `Grep` - surviving command file referencing a deleted one: expected 0 | actual **0**. Neither name ever appeared outside its own file and CLAUDE.md; the remaining hits are `dev/CHANGELOG.md` history and this ticket's own spec text, which are records, not references.
- `Grep` - `caveman` and `spec-do` still under `.claude/commands/`: expected present | actual **present**.
- `.github/prompts/` never carried an `ns` or `arc` prompt, so the parallel non-Claude surface needed no deletion. `AGENTS.md` and `.github/copilot-instructions.md` route `/skill-fix` and were re-synced with its widened scope, per the manual-sync policy S1340 §3.4 settled.

**Status:** `[x]` done

---

### Step 07.2 - Split the six biggest pipeline files

**Files:** `.claude/commands/*.md`, `.claude/commands/reference/*.md`
**Depends on:** Step 07.1

**Prompt for developer:**

> A skill body is injected in full and permanently on invocation, so a 24 KB command costs roughly 6.5k tokens for the remainder of the session even when only its first page matters. For the six largest surviving pipeline commands, split each into a driver holding the decision flow and the stage sequence, plus a `reference/<name>.md` holding the templates, the exhaustive tables and the edge-case catalogue, read on demand. The driver must name its reference file and say when to open it. Adopt the shape `run-fastmediasorter` and `document-registry` already use, so the split does not decay back.

**Verification:**

- Each of the six drivers is materially smaller than before and names its reference file.
- `Glob` - `.claude/commands/reference/` contains one file per split command.
- `Grep` - each driver contains an explicit instruction naming the condition under which the reference is read.

**Step log:**

- Six drivers split, one reference each in `.claude/reference/` (see the location correction above). Original -> driver: `skill-release` 31,787 -> 17,711 (55.7%), `spec-dev` 28,748 -> 21,257 (73.9%), `spec-next` 23,585 -> 12,952 (54.9%), `spec-prerelease` 21,458 -> 13,438 (62.6%), `spec-all` 20,704 -> 15,025 (72.6%), `spec` 17,240 -> 12,046 (69.9%). Reference store 78,747 B, loaded only on a named condition.
- **Three of the six missed the 40-55% target, and the reason is the same in each case:** what the step's own placement contract pins to the driver - hard stops, refusal conditions, exit-code contracts, the stage commands themselves - already exceeds 55% of the original. `spec-dev`'s irreducible floor measured ~19 KB against a 15.8 KB target. Hitting the number would have meant moving an obligation behind a lookup, which is the failure the split exists to avoid. Reported rather than met.
- Step 07.4 ran before this step rather than after it, so the file templates were lifted once instead of being moved into a reference and out again. `spec` and `spec-tech` were already smaller when they arrived here; `spec-tech` therefore fell out of the six largest and is not split.
- Pointers are at the point of use, not one line at the top: 16 in `skill-release`, 17 in `spec-dev`, 13 in `spec-all`, 12 in `spec-next`, 10 each in `spec-prerelease` and `spec`.
- Every split was content-audited line by line against the pre-split file by its author; the only non-verbatim lines are headings and sentence-boundary splits with both halves placed. Frontmatter `description` byte-identical in all six - it is the routing trigger.
- Phase 05's `/ui-clarify` and bugfix-repro refusals and phase 07.6's `a.ps1` targets all stayed in the `spec-dev` driver, verbatim. `build-debug`: expected 0 hits across driver and reference | actual **0**.
- One file per split command: expected 6 | actual **6**. Residual references to the old path: expected 0 | actual **0**.

**Status:** `[x]` done

---

### Step 07.3 - Rewrite CLAUDE.md section 3 to match reality

**Files:** `CLAUDE.md`
**Depends on:** Step 07.2

**Prompt for developer:**

> Section 3 currently advertises deleted commands and omits the second-largest cost centre. Rewrite it against the surviving surface from step 07.1. It must list `/spec-do` beside `/spec-next` as the explicit opt-in - a command that exists but is not routed will not be found when it is wanted. Keep this edit factual: S1340 §3.2 owns *compressing* section 3 down to what the harness cannot infer, and doing both here would collide. Fix the contents now, leave the compression to S1340, and say so in a comment so the two do not fight.

**Verification:**

- `Grep` - `/spec-do` matches in CLAUDE.md section 3.
- `Grep` - every command named in section 3 exists under `.claude/commands/`; every surviving command is named in section 3.
- `Grep` - no deleted command name remains anywhere in `CLAUDE.md`.

**Step log:**

- Section 3 was missing **seven** routable commands, and the audit's "it omits the second-largest cost centre" was exactly right: `/spec-next` - 46 invocations, the command that picks what gets worked on - was not routed at all. Also added: `/research`, `/verify`, `/ns`, `/newlog`, `/skill-release`, `/skill-fix-release`. `/skill-release` had been mentioned only inside the `/release` and `/spec-prerelease` bullets, never as its own trigger.
- Nothing was deleted from section 3, because step 07.1 deleted no command. Predicate "no deleted command name remains": vacuously true.
- Every command named in section 3 exists, and every command file is named in section 3: expected 33 = 33 | actual **33 files, 33 named, 0 either way**.
- A closing paragraph records the driver / `.claude/reference/` / `.claude/templates/` shape as the standard for a new command, including why neither store may sit under `.claude/commands/`.
- Two HTML comments mark the boundary so this section is not fought over twice: this phase owns the CONTENTS, S1340 §3.2 owns the COMPRESSION.
- **Residue, blocked on S1339.** `/spec-do` is named in section 3 only in a comment stating it gets its bullet the moment S1339 lands `.claude/commands/spec-do.md`. S1339 is still `Approved`; the file does not exist. Routing a command that does not exist would break this step's own second predicate and advertise a dead trigger - the exact defect the step was written to fix. The bullet is a one-line edit for whoever closes S1339.

**Residue closed, 2026-08-02.** S1339 is `Verified` and `.claude/commands/spec-do.md` exists, so the trigger is live rather than dead. `/spec-do` is routed in the size-tier bullet beside `/spec-next`, described by the one thing that distinguishes them - it is the same picker minus the context-threshold stop - which is what the step asked for. The placeholder comment is gone.

**Verification, 2026-08-02:**

- `Grep` - `/spec-do` in CLAUDE.md section 3: expected present | actual **present**, and `.claude/commands/spec-do.md` exists.
- `Grep` - every command named in section 3 exists under `.claude/commands/`: named are `/quick`, `/skill-fix`, `/spec-next`, `/spec-do`, `/spec-arc`, `/spec-draft`, all present. `/arc` is named too and deliberately has no file - the line says so in the same breath, so it advertises an alias rather than a dead trigger.
- `Grep` - deleted command name remaining in `CLAUDE.md`: expected 0 | actual **0** for `/ns`.
- **The step's third predicate no longer applies as written and is recorded rather than forced.** "Every surviving command is named in section 3" held when this phase left the section as a 33-entry list. S1340 §3.2 then compressed it on the measured ground that the harness injects every command's own `description` each turn, so the section now carries only what that injection cannot supply - the aliases and the size tier. Re-adding 31 names to satisfy a predicate written before the compression would undo a landed child ticket. The two HTML boundary comments this step planted are what kept the two edits from fighting, and they worked.

**Status:** `[x]` done

---

### Step 07.4 - Move the file templates out of the prompts

**Files:** `PLAN/_templates/*.md`, `.claude/commands/*.md`
**Depends on:** Step 07.3

**Prompt for developer:**

> Literal file templates duplicated across command prompts are the highest duplication in the surface. Create `PLAN/_templates/` and move each template there - the strategic spec skeleton, the tactical `INDEX.md`, the phase file, and any other verbatim block a command tells the agent to reproduce. Replace each in-prompt copy with a pointer to the template path. One template, many referrers.

**Verification:**

- `Glob` - `PLAN/_templates/` exists and contains at least the strategic, index and phase templates.
- `Grep` - the moved template bodies no longer appear inline in any `.claude/commands/*.md`.
- Each command that used a template now names its path.

**Step log:**

- Four templates now live once in `.claude/templates/`: `strategic-spec.md` (8,777 B), `tactical-index.md` (2,738 B), `phase-file.md` (3,347 B), `compact-bugfix-spec.md` (1,816 B). Each opens with an HTML comment naming its consumers. ~17.4 KB of duplicated body left the command surface; 16.7 KB of it now exists once.
- Command sizes after the lift: `spec` 23,647 -> 17,236, `spec-draft` 17,726 -> 12,036, `spec-tech` 21,351 -> 16,300. `spec-all` grew 63 B - it never held a body, it said "use the `spec_tech` phase template" and now names the path.
- **`/spec` and `/spec-draft` each carried the strategic skeleton.** Reconciled as a superset, never a truncation: `## 0. Захваченный материал (inbox)` is kept and marked Draft-only, both §3.3 hint bodies are kept and tagged with their owning command, and the fuller `/spec` wording wins where the draft copy was abridged. The six abridged lines were verified mechanically to be the only draft lines absent from the template.
- Bodies were diffed against `git show HEAD` before the move: three of the four are byte-identical, the fourth differs only by the two marked insertions.
- Store location corrected to `.claude/templates/` - see the correction note above. 12 pointers across `spec.md` (2), `spec-tech.md` (4), `spec-draft.md` (4), `spec-all.md` (1), each naming the exact path and when to open it.
- Scanner audit: `validate.ps1:67` filters `^S\d{4}_` and `request-digest.ps1:26` globs `PLAN\S*.md`, so neither would have seen the store even at the original path; `search.ps1`, `sca-specs.ps1` and `spec-next-preflight.ps1` read the journal only. `build-research-dossier.ps1:522` recurses `PLAN/` for `.md` and would have surfaced templates as dossier hits - the move to `.claude/templates/` removes that exposure rather than leaving it.
- `select.ps1 -Format json -Status Draft`: expected exit 0 | actual **0**. `validate.ps1`: expected exit 0 | actual **0** (`8 OK, 1 WARN, 0 FAIL`; the WARN is pre-existing staleness on five `BlockExternal` tickets).
- Template bodies remaining inline in any command: expected 0 | actual **0** (12 distinctive lines checked, 3+ per template).

**Status:** `[x]` done

---

### Step 07.5 - Replace the paraphrased rules with pointers

**Files:** `.claude/commands/*.md`
**Depends on:** Step 07.4

**Prompt for developer:**

> Seventeen paraphrases of CLAUDE.md rules live inside command files, each a copy that drifts independently. Replace every one with a one-line pointer naming the rule number. The value is removing seventeen drifting copies of one rule, not the bytes - so a pointer that is longer than the paraphrase is still the right change.

**Verification:**

- `Grep` - no command file restates a numbered CLAUDE.md rule in its own words; each reference is a pointer naming the rule.
- Spot-check three of the seventeen against the current CLAUDE.md text - the pointer names the correct rule number.

**Step log:**

- **69 paraphrase sites across 19 files, not 17.** The audit counted distinct rules, not sites: Rule 4 (read-only zones) alone had 13 copies across 11 files, Rule 12 (the catalog is script-owned) had 7, and Rules 11, 14 and 19 had 5-6 each. Counting rules rather than sites gives 16 rules plus four `##` sections, which is where "seventeen" came from. The reference files created by step 07.2 added 6 more sites the audit could not have seen.
- **Six citations already named the wrong rule.** `Rule 15` was cited for flavor isolation in four places (it is Rule 14 - Rule 15 is validation-command recording), `Rule 21` for dead-weight hygiene in three (it is Rule 20 - Rule 21 is deprecated PackageManager flags), plus one `Rule 10.1` that does not exist. That is the drift this step was written to stop, caught in the act, and it is the argument for the step: a paraphrase does not just duplicate a rule, it mislabels it.
- Same defect found outside the step's scope and fixed inline because it is one wrong number each: `.claude/agents/android-rd-specialist.md` and `android-kotlin-developer.md` both cited Rule 20 for neuroslop (Rule 19), the latter also Rule 12 for comment discipline (Rule 9). Seven `agent-memory` files cited Rule 15 for flavor isolation. Residual `Rule 15` under `.claude/`: expected 0 | actual **0**.
- Net -633 B across the 19 files; nine files grew. That is the intended shape - the value is one rule with one home, not the bytes.
- Constraints that exist ONLY in a command file were listed, never deleted: `build.md`'s Java 17 pin and the literal `BUILD.LOCK` refusal text, `spec-dev.md`'s misplaced-flavor-class detector (Rule 14 bans `BuildConfig` guards, not misplaced classes), `quick.md`'s no-`layout-land`-variant refusal path, and four others. `build.md` also carried `compileSdk 35` against CLAUDE.md's 36 - a live drift, now removed by pointing at the generated block instead of restating it.
- Numbered-rule restatements surviving in any command or reference file: expected 0 | actual **0**. Three replacements were quoted against the current CLAUDE.md text and each names the correct number.
- Note on house style: en-dashes remain in several command files and are left alone deliberately - the canon scopes house text style to prose and UI, explicitly not to commands.

**Status:** `[x]` done

---

### Step 07.6 - Delete the stale build instruction

**Files:** `.claude/commands/spec-dev.md`
**Depends on:** Step 07.5

**Prompt for developer:**

> `spec-dev.md` names `.\build-debug.PS1` at three places - lines 84, 158 and 201 - as the command to run for Phase Done Criteria. That contradicts CLAUDE.md on the most expensive operation in the repo, which routes builds through `a.ps1` targets. Replace all three with the correct target, and keep the surrounding logic intact - in particular the note that a final-phase build validating code plus probe tags is the single build, with no further build scheduled for tag validation.

**Verification:**

- `Grep` - `build-debug` returns zero hits in `.claude/commands/spec-dev.md`.
- `Grep` - the replacement names an `a.ps1` target consistent with CLAUDE.md section 9.
- The "single build validates code + tags" note survives all three edits.

**Step log:**

- All three sites replaced. Line 84 (Phase Done Criteria build) and line 160 (Build FAIL) now name `a.ps1 dq`; line 203 (command limits) points at CLAUDE.md section 9's target list and names `dq` for a debug build, `fk` for a compile-only symbol change.
- Line 84 also carries the phase 06 threshold, so the two rules cannot drift apart again: a fast check runs in the FOREGROUND per CLAUDE.md section 6's 120 s boundary.
- `build-debug`: expected: zero hits in `.claude/commands/spec-dev.md` | actual: **0**.
- The "single build validates code + tags" note survives verbatim on line 84, and the `BUILD.LOCK`-refusal-is-not-a-code-regression note survives on line 160.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 07.*` above is `[x] done`. Met on 2026-08-02: the owner decided both alias merges and S1339 landed `spec-do.md`.
- [x] `Grep` - no reference anywhere in the repo points at a deleted command file. No longer vacuous: `ns.md` and `arc.md` were deleted on 2026-08-02 and nothing points at either. The stronger check still holds - every `` `.claude/...` `` path cited in any command, reference or template file resolves: expected 0 dangling | actual **0**.
- [x] Dev log entry added covering the command-surface change as one logical change.
- [x] Document registry: `repository-rules` now also covers `.claude/reference/*.md` and `.claude/templates/*.md`, the two stores this phase created - an unregistered maintained document is invisible to the loop that is supposed to guard it. `validate.ps1`: expected exit 0 | actual **0** (24 records). `generate.ps1 -Check`: expected exit 0 | actual **0**.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

**Phase-boundary audit.** No product code, no lifecycle, no concurrency - the audit reduces to whether a driver lost an obligation or gained a broken pointer.

- Every one of the six references is named by its own driver: expected 6/6 | actual **6/6**.
- Dangling `.claude/...` paths across all commands, references and templates: expected 0 | actual **0**.
- P2, accepted: the registry gate reports `repository-rules` siblings `AGENTS.md`, `GEMINI.md` and `.github/copilot-instructions.md` as possibly needing the same edit. They are not synced here on purpose - INDEX "Scope boundaries" delegates their fate to S1340 §3.4, and `AGENTS.md` routes a different surface (`.github/prompts/*.prompt.md`), so section 3's contents do not transfer. Recorded rather than silently skipped. `AGENTS.md` also cites `Rule 10.1`, a notation used nowhere else; S1340 resolves it when it decides whether the file lives.
- P3, not fixed: the closure facade has no way to acknowledge a registry record without writing a second changelog row, so an honest acknowledgement costs a duplicate entry - the same defect class phase 02 fixed for failed closures.

---

## Handoff Notes to Next Phase

The driver-plus-reference shape is now the standard for a new command; S1342 lifts that shape to the canon. Section 3's *contents* are correct after this phase but not yet *compressed* - S1340 §3.2 does that and must start from this state, not the pre-phase one.

---

## Rollback Plan

Every deletion is recoverable from git. The splits are mechanical and reversible by concatenating driver and reference. No code, no build configuration, no user-facing surface touched.
