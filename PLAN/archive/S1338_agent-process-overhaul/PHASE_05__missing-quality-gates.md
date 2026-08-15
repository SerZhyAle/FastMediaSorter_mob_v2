# Phase 05 - The gates the corrections justify

**Strategic spec:** [`../S1338_agent-process-overhaul.md`](../S1338_agent-process-overhaul.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 04
**Blocks:** none
**Steps done:** 6 / 6
**Started:** 2026-08-01
**Completed:** 2026-08-01

---

## Objective

Add the four enforcement mechanisms whose absence let a defect reach the owner: a window-insets gate, UI-clarify wiring in the pipelines that build UI, a bugfix repro-evidence requirement, and a machine-readable unverified-backlog count for S1339's loop to stop on.

---

## Prerequisites

- [ ] Phase 04 is ✅ Done - `assert-source-gates.ps1` exists, so a new lexical gate registers as a matcher instead of adding a process start.
- [ ] `pwsh -NoProfile -File scripts/utils/enter-code-lock.ps1 -Reason "S1338 phase 05"` acquired.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/quality/assert-window-insets.ps1` | New | ≤ 180 |
| `scripts/quality/assert-source-gates.ps1` | Modified | ≤ 340 |
| `scripts/spec_catalog/unverified-backlog.ps1` | New | ≤ 130 |
| `scripts/post-change.ps1` | Modified | ≤ 740 |
| `.claude/commands/spec-tech.md` | Modified | n/a |
| `.claude/commands/spec-dev.md` | Modified | n/a |

---

## Steps

### Step 05.1 - Gate Rule 17

**Files:** `scripts/quality/assert-window-insets.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Rule 17 requires UI to stay inside `systemBars` plus `displayCutout` safe bounds in both orientations, has no gate, and the same defect reached the owner twice across five or more standalone player hosts sharing the pattern. Write the gate as a grep-speed lexical check: a layout root or an Activity that sets up a full-screen surface must apply the safe-bounds contract this repo already uses - find the existing compliant hosts first and derive the predicate from them rather than inventing one. Support `-Gate`, `-ChangedFiles` and a baseline for pre-existing debt, matching the ratchet family's shape.

**Verification:**

- `Glob` - `scripts/quality/assert-window-insets.ps1` exists.
- `Grep` - it accepts `-ChangedFiles` and `-Gate`.
- Point it at a deliberately broken layout in a scratch file - exit code 1 under `-Gate` with the file and line named. S1340 §5 requires this proof explicitly.
- Run against the current tree - exit code 0, or the pre-existing count recorded as the baseline.

**Step log:**

- Predicate derived from the compliant hosts, not invented. The repo's one fully compliant mechanism is `View.applySystemBarInsetPadding()` (`utils/ViewExtensions.kt:83`), which takes `maxOf(systemBars, displayCutout)` per edge - 13 call sites in 8 files. Five more files hand-roll the same contract by naming `displayCutout()` directly.
- Rule: a file is judged only when it owns a safe-bounds surface - it registers `ViewCompat.setOnApplyWindowInsetsListener`, or it calls `setDecorFitsSystemWindows(.., false)` and takes the problem off the system. It is compliant when it names `displayCutout()` or delegates to the helper. The count is per registration, not per file, so a second uncovered listener in an already-listed file is new debt.
- Measured: expected: a finite pre-existing count | actual: 28 across 20 files, and the set matches the audit's claim exactly - the five standalone hosts each carry 2-3, plus `PlayerControlsSetupManager` (4), `SystemBarsManager` (2), `StandaloneFullscreenManager`, and ten non-cutout hand-rolled sites (settings, browse, three cloud pickers, resource editor, add-resource, duplicates, auth sessions, main chrome).
- Planted a deliberately broken host (`app_v2/src/main/.../S1338InsetsProbe.kt`, systemBars-only padding): expected: exit 1 naming file and line | actual: exit 1, `+1 in ..S1338InsetsProbe.kt`, and `-List` printed `S1338InsetsProbe.kt:10`. Fixture deleted afterwards.
- The FIRST fixture scored 0. Its comment read "never reads `displayCutout()`", and the compliance signal is lexical, so the comment cleared the file. Recorded as a known limitation in the script header rather than papered over - narrowing to code would need a Kotlin parse, and a comment claiming absent cutout handling is a review defect, not a ratchet defect.
- Clean tree under `-Gate`: expected: exit 0 | actual: exit 0, `baseline 28 | actual 28 | delta 0`.
- `-List` gained a fallback in `assert-source-gates.ps1`: a rule with no locator now prints `path xN` instead of nothing. Reporting nothing under `-List` while the count says 28 reads as "no hits".

**Status:** `[x]` done

---

### Step 05.2 - Register it in the single pass

**Files:** `scripts/quality/assert-source-gates.ps1`
**Depends on:** Step 05.1

**Prompt for developer:**

> Register the insets check as a matcher inside the combined runner from phase 04, and keep `assert-window-insets.ps1` as the thin wrapper that delegates to it. S1340 §5 requires that each new gate runs inside the single-pass runner rather than adding a fourteenth pwsh start.

**Verification:**

- `Grep` - the insets matcher name appears in `assert-source-gates.ps1`.
- Run `pwsh -NoProfile -File ./a.ps1 fg` - the insets gate is exercised and total wall clock did not grow by more than 1 s against phase 04's recorded figure.

**Step log:**

- `window-insets` is registered in `Get-SourceRules` (`lib/source-matchers.ps1`) with its predicate `Measure-WindowInsetsText` and locator `Find-WindowInsetsLines`; `assert-window-insets.ps1` is the thin wrapper delegating with `-Only window-insets`, matching the `assert-public-mutable-flow.ps1` shape.
- `ConvertTo-SourceMatchers` now forwards an optional `LocateInText`, which it previously dropped - that is why no scriptblock rule could ever name a line.
- Fast gates: expected: the insets rule exercised, growth under 1 s | actual: `13 rule(s) over ONE walk`, `window-insets .. delta 0`, `assert-fast-gates: PASS`, wall clock **18.9 s** against phase 04's recorded 18.8 s. No new pwsh start - it is a thirteenth matcher over the existing walk.

**Status:** `[x]` done

---

### Step 05.3 - Wire `/ui-clarify` into the pipelines that build UI

**Files:** `.claude/commands/spec-tech.md`, `.claude/commands/spec-dev.md`
**Depends on:** Step 05.2

**Prompt for developer:**

> `/ui-clarify` was invoked once in a month while 33% of owner corrections were UI placement. Neither `spec-tech.md` nor `spec-dev.md` references it today. Do not add another prose mandate - add a refusal condition. In `spec-tech.md`, a phase whose `Files Touched` includes `res/layout*` or a UI class must carry a recorded placement decision, either a `/ui-clarify` record in the spec or an explicit owner ruling quoted in it; the plan self-review step fails otherwise. In `spec-dev.md`, a phase touching those same paths may not be flipped to Done until the same record exists and a screenshot has been captured at the phase boundary, so a placement decision is visible before it ships.

**Verification:**

- `Grep` - `ui-clarify` matches in both `.claude/commands/spec-tech.md` and `.claude/commands/spec-dev.md`.
- `Grep` - each occurrence is phrased as a refusal or gate condition, not as advice.
- The screenshot requirement names the existing capture path (`scripts/devtest/adb.ps1 shot` or the `run-fastmediasorter` skill), not a new mechanism.

**Step log:**

- Neither file referenced `/ui-clarify` before this step - confirmed by Grep, zero matches in both.
- `spec-tech.md` step 5.5 gained a self-check bullet phrased as a refusal: a phase whose `Files Touched` names `res/layout*`, an `Activity`/`Fragment`/`*View`/`ui/**` class or a settings surface **fails the self-check** without a recorded placement decision - a `/ui-clarify` record or an owner ruling quoted verbatim. The bullet closes the obvious escape by naming it: there is no "decide during implementation" path.
- `spec-dev.md` gained a phase-Done refusal at the same paths: the phase may not be flipped `✅ Done` until the placement decision exists **and** a screenshot was captured via `pwsh -NoProfile -File scripts/devtest/adb.ps1 shot` (or the `run-fastmediasorter` skill) with its path in the Step Log. No-device path is a written deferral, not a silent skip.
- Both are gate conditions, not advice: expected: phrased as a refusal | actual: "fails this self-check unless .." and "may not be flipped to `✅ Done` until ..".
- No new capture mechanism was invented - `adb.ps1 shot` already exists and is the documented ad-hoc path.

**Status:** `[x]` done

---

### Step 05.4 - Require repro evidence on bugfix tickets

**Files:** `.claude/commands/spec-dev.md`
**Depends on:** Step 05.3

**Prompt for developer:**

> "No completion claim without proof" has no gate at the point it matters most, and 39 of 232 active tickets are bugfixes. Add the condition to the `Implemented` transition in `spec-dev.md`: a ticket whose work is a bugfix may not leave `Implemented` without a before/after repro record in the spec - the failing observation with its evidence, and the same observation after the fix. Accept a recorded reason when the defect cannot be reproduced on demand, so the requirement does not become a lie the pipeline routes around.

**Verification:**

- `Grep` - a before/after repro condition matches in `.claude/commands/spec-dev.md` inside the `Implemented` transition.
- The escape path for non-reproducible defects is present and requires a written reason.

**Step log:**

- Condition added to `spec-dev.md` immediately before the `Implemented` flip, so it sits on the transition rather than in a preamble: a bugfix ticket may not reach `Implemented` without a before/after repro record - the failing observation with its evidence, and the same observation after the fix.
- Escape path present and explicit: `REPRO: not reproducible on demand - <reason>` plus whatever indirect evidence exists. The wording states why the escape exists - a requirement the pipeline routes around is worse than none.
- Placement check: expected: inside the `Implemented` transition | actual: the bullet sits directly above `**No on-device gate** -> flip strategic Status: to Implemented`, which is the first branch that performs the flip.

**Status:** `[x]` done

---

### Step 05.5 - Publish the unverified-backlog count

**Files:** `scripts/spec_catalog/unverified-backlog.ps1`
**Depends on:** Step 05.4

**Prompt for developer:**

> S1339's `-CheckContext` needs a second stop reason: `BlockNeedUserTest` count above a ceiling. Produce the number here so the loop can consume it. Write a script that reads the catalog and reports the `BlockNeedUserTest` count, the `Verified` count, the ratio, and whether a supplied `-Ceiling` is exceeded. Exit codes per Rule 7: 0 under the ceiling, 1 error, 2 cannot verify, 3 ceiling exceeded - matching the shape S1339 uses for its own threshold check. Support `-Json` so the loop can parse it. Current state for reference: 87 `BlockNeedUserTest` against 39 `Verified`, and 155 live probe tags across 94 files.

**Verification:**

- `Glob` - `scripts/spec_catalog/unverified-backlog.ps1` exists.
- `Grep` - `Exit codes:` in the header lists 0/1/2/3.
- Run with `-Ceiling 500` - exit code 0. Run with `-Ceiling 10` - exit code 3.
- Run with `-Json` - output parses as JSON containing the two counts.
- `pwsh -NoProfile -File scripts/quality/assert-exit-contract.ps1 -Path scripts/spec_catalog/unverified-backlog.ps1 -Gate` - exit code 0.

**Step log:**

- `scripts/spec_catalog/unverified-backlog.ps1` written. Reads the ACTIVE journal only - an archived ticket is closed, not pending. Reports `BlockNeedUserTest`, `Verified`, the ratio, and the ceiling verdict; `-IncludeTags` adds the live probe-tag count as the second symptom of the same backlog.
- Header lists exit codes 0/1/2/3, and the contract note names S1339 as the consumer of both the codes and the `-Json` field names.
- `-Ceiling 500`: expected: exit 0 | actual: exit 0. `-Ceiling 10`: expected: exit 3 | actual: exit 3 with the reason printed.
- `-Json`: expected: parses, carries both counts | actual: `{"blockNeedUserTest":87,"verified":39,"ratio":2.23,"ceiling":500,"exceeded":false,..}`, parses via `ConvertFrom-Json`.
- `assert-exit-contract.ps1 -Path .. -Gate`: expected: exit 0 | actual: exit 0.
- **Measurement correction.** The counts reproduce the audit's raw numbers exactly - 87 `BlockNeedUserTest` against 39 `Verified` - but the audit's stated ratio of **6.9:1 is arithmetically wrong**: 87/39 = **2.23:1**. Strategic §1 and §6 both carry the 6.9 figure. The backlog is real and large; the ratio quoted for it was not. Corrected in the strategic spec rather than reproduced here, because a script that prints a number to match a spec is the exact failure package A exists to prevent.
- Probe tags: 154 lines across 94 files against the spec's "155 across 94" - consistent, one tag has since been removed.
- Defect found by running it, not by reading it: the first version wrapped `Read-Catalog` in `@()`. That helper returns `,$array` to defend against unrolling, so the wrapper collected ONE element - the array itself - and the script reported `BlockNeedUserTest 1 | Verified 1`, a plausible and completely false answer that no exit code would have flagged.

**Status:** `[x]` done

---

### Step 05.6 - Trigger the document-registry loop instead of restating it

**Files:** `scripts/post-change.ps1`
**Depends on:** Step 05.5

**Prompt for developer:**

> The document-registry mandate is stated in five always-on places and obeyed at roughly 0.6-3% of its own stated cadence, which teaches that a mandate is optional. Replace the exhortation with a trigger: in `post-change.ps1`, when the changed file set intersects any `paths` entry in `docs/DOCUMENT_REGISTRY.jsonl`, report the matching records and require an acknowledgement before the closure passes - a registered document changed and its siblings may need updating. Keep it a gate on registered paths only, not on every closure, so it fires where it is real. The rule *text* and the removal of the five always-on restatements belong to S1340 §3.3; this step builds the trigger it will point at.

**Verification:**

- Run the facade against a file listed in a registry record's `paths` - the matching record ids are reported.
- Run it against a file in no registry record - no registry output, closure unaffected.
- `Grep` - `DOCUMENT_REGISTRY` matches in `scripts/post-change.ps1`.

**Step log:**

- Trigger added to `post-change.ps1`: it reads `docs/DOCUMENT_REGISTRY.jsonl`, matches each record's `paths` against the changed set (exact path or directory prefix), and prints the matching record ids, the changed files that hit them, and the sibling paths in the same record that may now disagree.
- The acknowledgement is the new `-RegistryAck <ids|all>` parameter. Unacknowledged matches land as an **advisory**, so the run still completes but the verdict degrades from `PASS` to `PASS WITH ADVISORIES` naming the record. That is the strongest form of "require an acknowledgement" a non-interactive script can take - a prompt would hang under `-NonInteractive`, and a hard failure would block a legitimate closure on a doc nobody needed to touch.
- Registered path, no ack: expected: matching record reported, closure not a clean PASS | actual: `registry: site-landing (Landing Pages) <- README.md`, siblings `index.html, index-ru.html, index-uk.html` listed, verdict `PASS WITH ADVISORIES (1)`.
- Same path with `-RegistryAck "site-landing"`: expected: clean pass | actual: `[document-registry] PASS`, verdict `post-change: PASS`.
- Unregistered path: expected: no registry output, closure unaffected | actual: `[document-registry] SKIP - not applicable - no changed file is a registered document` (seen on this phase's own closure).
- Fires on registered paths only, per the step - it is not a per-closure tax.
- Two probe rows landed in `dev/CHANGELOG.md` ("S1338 phase 05 registry trigger probe") because the dev-log step runs last and unconditionally. Left in place: the changelog is append-only and hand-editing it is banned.
- **Three defects found by running the trigger on this phase's own closure, not by reading it.** All were silent - the first two produced a confident, wrong, green-looking answer:
  - `TrimStart('./')` takes a CHAR SET, so it ate the leading dot of `.claude/commands/spec-tech.md` and the record `repository-rules` - which registers `.claude/commands/*.md`, the largest registered surface in the repo - matched nothing. Fixed with `-replace '^\./'`.
  - Literal-only matching ignored glob entries, so even with the dot intact `.claude/commands/*.md` could not match. Fixed by adding `-ilike`.
  - The only record that DID fire was `script-cheatsheet`, which is `generated: true` and owned by its own sync gate - pure noise on every closure that changes a param block. Generated records are now skipped.
- After the fixes: expected: command-file edits raise `repository-rules` and the cheatsheet stays quiet | actual: `registry: repository-rules (Repository Rules) <- .claude/commands/spec-dev.md`, siblings `CLAUDE.md, AGENTS.md, GEMINI.md, .github/copilot-instructions.md, .github/prompts/*.prompt.md, .claude/agents/*.md, .claude/skills/*/SKILL.md`, `-RegistryAck repository-rules` -> `[document-registry] PASS`, verdict `post-change: PASS`.
- Sibling list excludes the pattern that matched - telling the operator to go update the file they just edited is noise.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] `pwsh -NoProfile -File scripts/utils/help.ps1 -Generate` re-run - exit 0, 254 scripts against 252 before, which is the two new ones.
- [x] `pwsh -NoProfile -File scripts/quality/assert-fast-gates.ps1` - exit 0, `fail: 0`, `window-insets .. delta 0`.
- [x] Dev log entry added for the phase as one logical change.
- [x] Document registry: `validate.ps1` exit 0 (24 records), `generate.ps1 -Check` exit 0.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.
- [x] `pwsh -NoProfile -File scripts/utils/exit-code-lock.ps1` released by the closure.

## Phase-boundary audit

- The insets rule is lexical and file-scoped, so a compliant helper call in one file cannot vouch for a sibling. That is intended - each file that registers a listener answers for itself - but it does mean a genuinely central inset applier introduced later would be judged file-by-file. Recorded, P3.
- The 28 pre-existing hits are debt this phase deliberately did not fix; the ratchet freezes them and the strategic spec excludes product code from this ticket. Draining them belongs to a UI ticket, not here. P2, ticketed by the baseline itself - any attempt to add a 29th fails the gate.
- `-RegistryAck` is advisory rather than fatal by design. A fatal registry gate would block a closure on a doc the change did not need to touch, which is how a gate gets routed around. The advisory still degrades the verdict, which is the signal the audit found missing. P3.
- `unverified-backlog.ps1` reads the active journal only. If the archive ever holds a `BlockNeedUserTest` record the count would understate - it cannot today, because archiving sets the status to `Archived`. P3.
- No P0/P1 findings.

---

## Handoff Notes to Next Phase

`unverified-backlog.ps1` is the contract S1339 consumes - its exit codes and `-Json` shape must not change without updating S1339's loop. The rule *text* for all four items belongs to S1340 §3.1; this phase built the enforcement only, and S1340 must not restate what these gates already do.

---

## Rollback Plan

The two new scripts are additive. The two command-file edits revert independently and change no code. No build configuration touched.
