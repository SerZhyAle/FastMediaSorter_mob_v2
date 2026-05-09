# Specification Developer Executor

Execute a tactical specification step by step. Reads `PLAN/Sxxxx_<short-name>/INDEX.md` + phase files, follows `Prompt for developer:` in dependency order, runs each step's `Verification:` predicate before flipping to `[x] done`.

## Usage

```text
/spec-dev <Sxxxx-or-slug>                    # continue from first non-done step
/spec-dev <Sxxxx-or-slug> --phase <NN>       # all remaining steps in one phase
/spec-dev <Sxxxx-or-slug> --step <NN.M>      # single step
/spec-dev <Sxxxx-or-slug> --until <NN.M>     # steps up to and including this one
/spec-dev <Sxxxx-or-slug> --resume           # re-scan state, then continue
/spec-dev <Sxxxx-or-slug> --dry-run          # print plan without writing
```

---

## Status Gate

| Strategic `Status:` | Behavior |
| --- | --- |
| `Tactical` | Allowed — advance to `In Progress` on first executed step. |
| `In Progress` | Allowed — continue. |
| `Draft` / `Approved` | Abort: no tactical folder. Run `/spec-tech` first. |
| `Implemented` / `Verified` | Abort: feature closed. |
| `Partial` / `Broken` | **Auto-fix pass:** run `/spec-fix <Sxxxx>` to apply all mechanical fixes, then re-read status. If status is still `Partial`/`Broken` after the fix pass, list the remaining FAIL items and stop — manual resolution required. If all issues resolved, continue. |
| `BlockNeedUserTest` | Note in chat and stop. User must confirm on-device test result before re-running. |
| `BlockByOtherTask` / `BlockQuestions` / `BlockExternal` | Abort: blocked. Resolve the block first (see §10 of strategic spec), then `update.ps1 -Status <prev>`. |

---

## Process

**1 — Parse arguments, load state.**

Compute target step set from flags. Read strategic spec, INDEX, all phase files in scope. Verify Status Gate. Verify all Pre-Implementation Blockers in INDEX are ticked — if any unchecked → abort with blocker text.

**2 — If `--dry-run`:** print planned step table and exit. No further processing.

**3 — Execute steps, one at a time.**

For each step in plan order:

1. **Re-read the phase file.** If `Status:` is no longer `[ ]`/`[~]` → log "PRE-RESOLVED — skipped".
2. **Verify dependencies.** `Depends on:` step must be `[x] done` — else abort: "Dependency violation: NN.M depends on NN.K which is not done."
3. **Read `Prompt for developer:`** + `Files Touched` row(s). For each referenced existing class/method, confirm it exists at the expected path — else abort: "Prompt references `<symbol>` at `<path>`, not found."
4. **Ambiguity check.** If prompt contains `<TODO>`, `<choose ..>`, `???`, or any unresolved placeholder → abort, request spec update via `/spec-update`. If it requires user input, set spec status to `BlockQuestions` and stop.
5. **Pre-edit guards:**
   - Read-only zone (`V1/`, `v2_6/`, `spec_v2/`, `dev/archive/`) → abort.
   - File >500 lines and not yet backed up → create timestamped copy in `temp/` first.
   - Projected post-edit size >1500 lines → abort: "line budget violation, split via Manager pattern."
   - File is in `res/layout/` → **check `res/layout-land/` counterpart**. If landscape variant exists and is NOT listed in this step's `Files Touched` → abort: "landscape counterpart `res/layout-land/<file>.xml` not covered in step — update `Files Touched` and prompt before proceeding."
6. **Flip step to `[~] in progress`** in phase file.
7. **Apply the edit** — `Edit` or `Write` per the Prompt. Scope strictly to what the prompt specifies: no surrounding refactors, no extra comments, no unrelated import cleanup.
8. **Run `Verification:` predicates** (Glob/Grep/value equality/size checks).
9. **Outcome:**
   - All predicates PASS → flip to `[x] done`. Append Step Log entry.
   - Any predicate FAIL → leave at `[~] in progress`. Append FAIL note. **Hard stop.**
10. **Run dev log** for every modified source file (one `add_to_dev_log.ps1` invocation per file).

After all planned steps in the current phase complete:

- **Phase Done Criteria check.** For each checkbox:
  - Mechanically verifiable → run check, tick if PASS.
  - Build required (`Project compiles`) → run `.\build-debug.PS1` automatically via PowerShell (no permission prompt). Exit code 0 → tick criterion. Non-zero → append last 30 lines of output, hard stop: "Build FAILED".
  - Manual review required → leave unticked, mark `MANUAL-REQUIRED`.
- If every criterion ticked → flip phase `Status:` to `✅ Done`, set `Completed:`, update INDEX row + counter.
- If any criterion unticked → leave `🚧 In Progress`, update step counter only. Hard stop.

After all phases done:

- Flip strategic `Status:` to `Implemented`. Add `**Implemented date:** <YYYY-MM-DD>`.
- If on-device verification is part of the acceptance — also flip journal status to `BlockNeedUserTest`.
- **Auto-chain to `/spec-check`:** immediately invoke `/spec-check <Sxxxx>` to audit the implementation. Skip only if status was flipped to `BlockNeedUserTest` — in that case note: `→ Awaiting on-device test. Run /spec-check <Sxxxx> after verification.`

**Chat output:** `<Sxxxx>: N steps done. Cursor: <next step>. [Stop reason if any]. → Running /spec-check…`

---

## Hard Stops

Stop immediately and report — never guess or recover — on any of:

1. **Ambiguous prompt** — placeholder text, missing class/method name, unspecified Hilt scope, unspecified dispatcher. Set status `BlockQuestions`.
2. **Verification FAIL** after edit — step left `[~]`. User investigates.
3. **Read-only zone touch.**
4. **Line budget violation** — projected >1500 lines.
5. **Build FAIL** — `.\build-debug.PS1` returned non-zero after auto-run for Phase Done Criteria. Stop with error excerpt.
6. **Room schema change** — prompt mentions bumping `@Database(version)` or adding `Migration`. Stop only if the step does **not** specify the new version number and migration class name explicitly. If both are named in the step → proceed automatically, note the change in chat.
7. **Hilt module graph change** — adds `@Module`, `@Provides`, or modifies Hilt graph beyond a single `@Inject constructor`. Stop only if scope/qualifier are not explicit in the prompt. If scope is named → proceed automatically, note in chat.
8. **Missing symbol** — prompt names class/method not found at stated path and prompt does not also create it.
9. **Dependency violation** — `Depends on:` step not `[x] done`.
10. **Catalog-affecting change without regen step** — public API change in touched file but no catalog regen step in phase. Stop, suggest `/spec-update --tactical --phase NN`.
11. **External system touch** — network, file deletion outside `temp/`, force push, CI edit. Stop, require explicit permission.
12. **Trilingual gap** — step adds UI string but prompt names <3 `values/` files. Stop, never fabricate translations.
12a. **Communication policy violation** — step adds or modifies a user-visible string that fails §6 tone checklist of `docs/COMMUNICATION_POLICY.md` (raw exception text as primary message, "Are you sure?" without consequence, "operation completed successfully", empty state with no CTA). Rewrite the string to comply before proceeding; do not commit policy-violating copy.
13. **External dependency missing** — step needs library version / hardware / third-party state not present. Set status `BlockExternal`, stop.

---

## Phase File Conventions

Step `Status:` progression: `[ ] not done` → `[~] in progress` → `[x] done`.

Append Step Log on each execution (create on first):

```markdown
**Status:** `[x] done`

**Step Log:**

- <YYYY-MM-DD> — Verification N/N PASS. Files: path/Foo.kt (+N LOC). Dev log recorded.
```

Step Log is append-only. Re-executions add new lines, never overwrite.

---

## INDEX Conventions

After step completion → bump row `Steps` counter.
After phase completion → flip status to `✅ Done`, bump `Phases: X/N done`.
Do NOT touch `Pre-Implementation Blockers`, `Completion Gate`, `Blockers Log`, `Change Log` — owned by user/`/spec-tech`.
If user manually set phase to `⛔ Blocked` between runs → stop and ask whether to resume.

---

## Constraints

- Never invoke `gradle`, `./gradlew`, or `npm` directly. For Phase Done Criteria compile checks, run `.\build-debug.PS1` automatically via PowerShell — do not pause or ask for permission.
- Never run `git commit`, `git push`, `git rebase`.
- Never skip steps or combine consecutive steps into one edit.
- Never refactor surrounding code, add comments, or adjust unrelated imports.
- Never choose a name not explicitly stated in the prompt.
- Never translate UI strings to RU/UK.
- Step is `[x] done` only when every Verification predicate returned PASS in the current run — never on intent.
- Idempotency: running twice with no changes is a no-op on the second run.
- Never auto-revert a failed edit — user decides from Step Log.
- Dev log per file, per step — run immediately after step completion.
- Cursor recomputed from phase file `Status:` on every invocation — never from memory.
- **Landscape parity (MANDATORY):** any step editing `res/layout/*.xml` MUST list the corresponding `res/layout-land/*.xml` in `Files Touched`. If the landscape variant exists and is absent from the step → abort (see Pre-edit guards). If the landscape variant does not exist but the screen supports rotation → add an explicit note in the step or a dedicated sub-step to create it.

---

## Spec Catalog hooks

- **Argument resolution.** First positional argument is `Sxxxx` (preferred) or a slug. If slug, resolve via `pwsh -File scripts/spec_catalog/select.ps1 -Name "<slug>" -Format json` to obtain the id. Read current status the same way before the first phase touches code.
- **Status transitions.**
  - Before the first non-done step is started: `pwsh -File scripts/spec_catalog/update.ps1 -Id <Sxxxx> -Status "In Progress"` (skip if status is already `In Progress` or later).
  - After every phase has all steps `[x] done` and final dev log is written: `pwsh -File scripts/spec_catalog/update.ps1 -Id <Sxxxx> -Status Implemented`.
  - When a hard stop indicates a block: `update.ps1 -Id <Sxxxx> -Status BlockQuestions | BlockExternal | BlockByOtherTask | BlockNeedUserTest` per the stop reason.
- **Forbidden:** never write to `PLAN/spec-catalog.jsonl` directly; never set the journal status to `Verified` from this skill — that is `/spec-check`'s job.
