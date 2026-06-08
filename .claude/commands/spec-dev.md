# Specification Developer Executor

Execute a tactical spec step by step. Reads `PLAN/Sxxxx_<short-name>/INDEX.md` + phase files, follows `Prompt for developer:` in dependency order, runs each step's `Verification:` predicate before flipping to `[x] done`.

## Usage

```text
/spec-dev <Sxxxx-or-slug>                    # continue from first non-done step
/spec-dev <Sxxxx-or-slug> --phase <NN>       # all remaining steps in one phase
/spec-dev <Sxxxx-or-slug> --step <NN.M>      # single step
/spec-dev <Sxxxx-or-slug> --until <NN.M>     # steps up to and including this one
/spec-dev <Sxxxx-or-slug> --resume           # re-scan state, then continue
/spec-dev <Sxxxx-or-slug> --dry-run          # print plan without writing
/spec-dev <Sxxxx-or-slug> --verify-smoke     # after all phases done, run /verify smoke before flipping status
```

`--verify-smoke` is opt-in: catches a trivial launch-crash before the strategic spec is recorded `Implemented`/`BlockNeedUserTest`. Runs `/verify --build` once with no scenario (default smoke: launch + screenshot + crash scan). If smoke fails, the status flip is **aborted**: ticket stays `In Progress`, the last phase's `## Step Log` gets a `VERIFY-SMOKE FAIL` line. Safety net, not a replacement for `/spec-test-device` or `/spec-check`.

---

## Status Gate

| Strategic `Status:` | Behavior |
| --- | --- |
| `Tactical` | Allowed - advance to `In Progress` on first executed step. |
| `In Progress` | Allowed - continue. |
| `Draft` / `Approved` | Abort: no tactical folder. Run `/spec-tech` first. |
| `Implemented` / `Verified` | Abort: feature closed. |
| `Partial` / `Broken` | **Auto-fix pass:** run `/spec-fix <Sxxxx>` to apply all mechanical fixes, then re-read status. If still `Partial`/`Broken` after the fix pass, list remaining FAIL items and stop - manual resolution. If all resolved, continue. |
| `BlockNeedUserTest` | Note in chat and stop. User must confirm on-device test result before re-running. |
| `BlockByOtherTask` / `BlockQuestions` / `BlockExternal` | Abort: blocked. Resolve the block first (see §10 of strategic spec), then `update.ps1 -Status <prev>`. |

---

## Process

Order is fixed: resolve scope → optional dry-run → execute one step at a time → run that step's verification → update phase/status metadata.

**1 - Parse arguments, load state.**

Compute target step set from flags. Read strategic spec, INDEX, all phase files in scope. Verify Status Gate. Verify all Pre-Implementation Blockers in INDEX are ticked - if any unchecked → abort with blocker text.

**2 - If `--dry-run`:** print planned step table and exit. No further processing.

**3 - Execute steps, one at a time.**

For each step in plan order:

1. **Re-read the phase file.** If `Status:` is no longer `[ ]`/`[~]` → log "PRE-RESOLVED - skipped".
2. **Verify dependencies.** `Depends on:` step must be `[x] done` - else abort: "Dependency violation: NN.M depends on NN.K which is not done."
3. **Read `Prompt for developer:`** + `Files Touched` row(s). For each referenced existing class/method, confirm it exists at the expected path - else abort: "Prompt references `<symbol>` at `<path>`, not found."
4. **Ambiguity check.** If prompt contains `<TODO>`, `<choose ..>`, `???`, or any unresolved placeholder → abort, request spec update via `/spec-update`. If it requires user input, set status `BlockQuestions` and stop.
5. **Pre-edit guards:**
   - Read-only zone (`V1/`, `v2_6/`, `spec_v2/`, `dev/archive/`) → abort.
   - File >500 lines and not yet backed up → create timestamped copy in `temp/` first.
   - Projected post-edit size >1500 lines → abort: "line budget violation, split via Manager pattern."
   - File in `res/layout/` → **check `res/layout-land/` counterpart**. If landscape variant exists and is NOT listed in this step's `Files Touched` → abort: "landscape counterpart `res/layout-land/<file>.xml` not covered in step - update `Files Touched` and prompt before proceeding."
   - **Flavor isolation guard:** see Hard Stops #14 - abort on a `src/main/java/**` flavor guard; do not silently rewrite, push back through `/spec-update`.
6. **Flip step to `[~] in progress`** in phase file.
7. **Apply the edit** - `Edit` or `Write` per the Prompt. Scope strictly to what the prompt specifies: no surrounding refactors, no extra comments, no unrelated import cleanup.
7a. **Communication policy check.** If the edit adds/changes user-visible strings, verify them against `docs/COMMUNICATION_POLICY.md` §2 and §6 before marking the step done.
7b. **Android string edit shortcut.** For `<string>` work prefer `pwsh -NoProfile -File scripts/utils/set-android-string.ps1` (byte-preserving) over manual XML editing. Update an existing value in one locale: `-Action set -Module <module> -Locale en|ru|uk -Key <key> -Value <text>` (add `-ExpectedOldValue` to guard, `-CreateIfMissing` to upsert). New key across all three locales at once: `-Action add -Key <key> -En <text> -Ru <text> -Uk <text>` (parity-enforced, fails if key exists). Lookup/lifecycle across all `strings*.xml`: `-Action get|remove|rename|list`. Use manual edits only for `plurals`, `string-array`, comments, regrouping, or bulk rewrites.
8. **Run `Verification:` predicates** (Glob/Grep/value equality/size checks).
9. **Outcome:**
   - All predicates PASS → flip to `[x] done`. Append Step Log entry.
   - Any predicate FAIL → leave at `[~] in progress`. Append FAIL note. **Hard stop.**
10. **Run mechanical post-change closure** for every modified file.
  - `pwsh -NoProfile -File scripts/post-change.ps1 -File "<path>" -Target "<target>" -Description "<short EN description>" -ChangeType <Doc|Script|Config|Kotlin|Xml|Mixed> [-Module <app_v2|wear>] [-KeyPrefix "<key_prefix>"]`.
  - Choose `Kotlin` for executable `.kt`/`.java` edits, `Xml` for string/resource changes, `Doc` for spec/doc-only edits, `Mixed` only when one step genuinely spans code plus strings.
  - Spec status transitions and functionality-log decisions stay outside this command.

After all planned steps in the current phase complete:

- **Final-phase debug-tag insertion (before the build).** If this is the **last** phase in scope AND the strategic spec's acceptance includes on-device verification, insert the `Timber.d("Sxxxx: <entry-point description>")` tags now - one at each changed flow entry across all phases (per CLAUDE.md "Debug Verification Tags": one tag per flow entry, not per modified line). Tags are the **last code edits** and go in **before** the `Project compiles` build below, so a single build validates implementation + tags. Never insert tags after the build - that forces a redundant second build.
- **Phase Done Criteria check.** For each checkbox:
  - Mechanically verifiable → run check, tick if PASS.
  - Build required (`Project compiles`) → run `.\build-debug.PS1` automatically via PowerShell (no permission prompt). Exit 0 → tick. Non-zero → append last 30 lines of output, hard stop: "Build FAILED". When final-phase tags were just inserted, this is the single build that validates code + tags - schedule no further build for tag validation.
  - Manual review required → leave unticked, mark `MANUAL-REQUIRED`.
- If every criterion ticked → flip phase `Status:` to `✅ Done`, set `Completed:`, update INDEX row + counter.
- If any criterion unticked → leave `🚧 In Progress`, update step counter only. Hard stop.
- **Write session snapshot (S0268 Agent Continuity Layer).** After the phase boundary closes (success or hard-stop), invoke `scripts/agent_continuity/session-snapshot.ps1` with `-Ticket <Sxxxx>` (active spec id), `-Goal "<phase title>"` (just-finished phase title), `-FilesTouched @(<file1>, <file2>, ...)` (from this phase's `Files Touched` table), `-NextStep "<cursor>"` (next step printed in chat, or `phase-complete` when the whole phase was the final one). One call per phase boundary; snapshot lands under `temp/sessions/` as the resume-layer hand-off for the next session.

After all phases done:

- **Optional verification smoke (only when `--verify-smoke`).** Before flipping strategic status, run `/verify --build` once with no scenario arg. The skill writes artefacts under `temp/verify_*`; do not touch them. Read the single-line verdict:
  - `verify: ... PASS/SKIPPED ...` with `log errors 0` and `crashes 0` → proceed with the status flip below as normal.
  - Any FAIL row in the run table, any `crashes K > 0`, or any `log errors` with a fresh exception from the package under test → **abort the status flip**. Leave ticket at `In Progress`. Append one `VERIFY-SMOKE FAIL` line to the last phase's `## Step Log` pointing at the scenario path in `temp/verify_*.md`. Stop with: `<Sxxxx>: verify-smoke FAIL, status not advanced. See temp/verify_<TS>.md.`
  - `device-ready.ps1` exit ≠ 0 (no device, mobile-mcp missing) → **do not** abort: log the skip in chat (`verify-smoke skipped: <reason>`) and proceed with the original status flip. Smoke is a bonus, never a hard gate when no device is present.
- **No on-device gate** → flip strategic `Status:` to `Implemented`, add `**Implemented date:** <YYYY-MM-DD>`. No debug tags. The per-phase builds already validated compilation.
- **On-device verification is part of acceptance** → the `Timber.d("Sxxxx:")` tags were already inserted before the final phase's `Project compiles` build (see "Final-phase debug-tag insertion") and validated by that single build. Here just flip journal status to `BlockNeedUserTest` and run a dev log line for each file that gained a tag. Do not insert tags or rebuild at this point.
- FEATURES / feature-doc updates and the rest of finalization run **after** the build - never rebuild after the doc step.
- **Finalization (batched).** Use `close-and-log.ps1` for the journal flip + dev log batch + functionality log + catalog scan/render in one pwsh process:

  ```powershell
  pwsh -NoProfile -File scripts/spec_catalog/close-and-log.ps1 `
      -Id <Sxxxx> `
      -Status <Implemented|BlockNeedUserTest> `
      -DevLogs @(
          '{"file":"PLAN/Sxxxx_<slug>.md","target":"spec-dev","desc":"All phases done; status -> <new>"}',
          '{"file":"app_v2/src/.../X.kt","target":"spec-dev","desc":"<phase-NN.M edit summary>"}'
          # ...one entry per modified source file
        ) `
      -FuncOp <ADD|CHANGE|""> -FuncDesc "<english summary or omit>" `
      -CatalogModule app_v2
  ```

  Functionality log block (encoded in `-FuncOp` / `-FuncDesc`):
  - **`ADD`** - spec introduces a new user-visible capability (no prior equivalent). Hints: §2 Goals describe a new feature; §8 mentions a new entry in `docs/FEATURES.md`; touched files are new classes / new screens / new menu entries.
  - **`CHANGE`** - spec modifies an existing user-visible behaviour. Hints: §2 Goals describe a behaviour change / UX improvement / reordering / visibility change; §8 says "Без изменений" but UI strings or visible state shifted.
  - Pass `-SkipFuncLog` (or omit `-FuncOp`) when the spec is purely internal (refactor, performance, build/CI plumbing). Document the skip in chat output.
  - Description: concise user-visible summary, reusing the spec title or first sentence of §2 Goals.

  Individual-call fallback (`update.ps1 -Status` + `post-change.ps1 -ChangeType ...` × N + `add_to_functionality_log.ps1` + `catalog_sync.ps1` only when a separate catalog repair is still needed) remains valid when `close-and-log.ps1` is unavailable, but each call is a separate pwsh process.

- **Auto-chain to `/spec-check`:** immediately invoke `/spec-check <Sxxxx>` to audit the implementation. Skip only if status was flipped to `BlockNeedUserTest` - in that case apply the **Device-test gate** below.

- **Device-test gate (on `BlockNeedUserTest`).** When the status was flipped to `BlockNeedUserTest`, do not just stop - probe for a device and auto-run on-device verification when one is attached:

  ```powershell
  pwsh -NoProfile -File scripts/devtest/device-ready.ps1 -Package com.sza.fastmediasorter.debug -CheckMcp -Json
  ```

  - **Exit 0 (device online):** auto-chain `/spec-test-device <Sxxxx>` (full evidence run) → then `/spec-check <Sxxxx>`. `/spec-check` converts harvested evidence into `Verified` / `Partial` / `Broken` and, on a transition out of `BlockNeedUserTest`, removes the `Timber.d("Sxxxx:` tags. Note in chat: `→ Device online: ran /spec-test-device + /spec-check. End status: <new>.`
  - **Exit 2/1/3/6 (no usable device):** do not run. Note: `→ Awaiting on-device test. Debug tags inserted: N. No device attached - run /spec-sweep (or /spec-test-device <Sxxxx>) when a device is online; /spec-check removes the tags on the Verified transition.` Leave ticket in `BlockNeedUserTest`.

**Chat output:** `<Sxxxx>: N steps done. Cursor: <next step>. [Stop reason if any]. [verify-smoke PASS/SKIPPED/FAIL when --verify-smoke]. → Running /spec-check…`

---

## Hard Stops

Stop immediately and report - never guess or recover, never assume missing/ambiguous details, never attempt speculative recovery - on any of:

1. **Ambiguous prompt** - placeholder text, missing class/method name, unspecified Hilt scope, unspecified dispatcher. Set status `BlockQuestions`.
2. **Verification FAIL** after edit - step left `[~]`. User investigates.
3. **Read-only zone touch.**
4. **Line budget violation** - projected >1500 lines.
5. **Build FAIL** - `.\build-debug.PS1` returned non-zero after auto-run for Phase Done Criteria. Stop with error excerpt.
6. **Room schema change** - prompt mentions bumping `@Database(version)` or adding `Migration`. Stop only if the step does **not** specify the new version number and migration class name explicitly. If both named → proceed automatically, note in chat.
7. **Hilt module graph change** - adds `@Module`, `@Provides`, or modifies Hilt graph beyond a single `@Inject constructor`. Stop only if scope/qualifier not explicit in the prompt. If scope named → proceed automatically, note in chat.
8. **Missing symbol** - prompt names class/method not found at stated path and prompt does not also create it.
9. **Dependency violation** - `Depends on:` step not `[x] done`.
10. **Catalog-affecting change without regen step** - public API change in touched file but no catalog regen step in phase. Stop, suggest `/spec-update --tactical --phase NN`.
11. **External system touch** - network, file deletion outside `temp/`, force push, CI edit. Stop, require explicit permission.
12. **Trilingual gap** - step adds UI string but prompt names <3 `values/` files. Stop, never fabricate translations.
12a. **Communication policy violation** - step adds/modifies a user-visible string failing §6 tone checklist of `docs/COMMUNICATION_POLICY.md` (raw exception text as primary message, "Are you sure?" without consequence, "operation completed successfully", empty state with no CTA). Rewrite to comply before proceeding; do not commit policy-violating copy.
13. **External dependency missing** - step needs library version / hardware / third-party state not present. Set status `BlockExternal`, stop.
14. **Flavor leak** - step writes a `BuildConfig.IS_*` / `SUPPORT_*` / `ENABLE_*` flavor guard inside `src/main/java/**`, OR places a flavor-only class (any new file containing `vr.*Activity`, `Vr*Renderer`, `*NoLegal*`, NewPipe/yt-dlp wrappers, OpenXR JNI) under `src/main/java/**` instead of `src/<flavor>/java/**`. Stop: the tactical spec is wrong and needs `/spec-update`. Correct pattern is interface in `src/main/` + impl in flavor source set + Hilt module per flavor. See `dev/FLAVOR_DEVELOPMENT_RULES.md` and CLAUDE.md Rule 15. Never compensate by adding the guard "just for this step".

---

## Phase File Conventions

Step `Status:` progression: `[ ] not done` → `[~] in progress` → `[x] done`.

Append Step Log on each execution (create on first):

```markdown
**Status:** `[x] done`

**Step Log:**

- <YYYY-MM-DD> - Verification N/N PASS. Files: path/Foo.kt (+N LOC). Dev log recorded.
```

Step Log is append-only. Re-executions add new lines, never overwrite.

---

## INDEX Conventions

After step completion → bump row `Steps` counter.
After phase completion → flip status to `✅ Done`, bump `Phases: X/N done`.
Do NOT touch `Pre-Implementation Blockers`, `Completion Gate`, `Blockers Log`, `Change Log` - owned by user/`/spec-tech`.
If user manually set phase to `⛔ Blocked` between runs → stop and ask whether to resume.

---

## Constraints

- Command limits: never run `gradle`, `./gradlew`, or `npm` directly. For Phase Done Criteria compile checks, run `.\build-debug.PS1` automatically via PowerShell - do not pause or ask permission. Never run `git commit`, `git push`, `git rebase`.
- Execution discipline: never skip planned steps, never mark multiple consecutive steps done in one pass, never combine their status updates into one edit. Using a repo helper script to complete the current step is allowed when the script is explicitly part of the step or required to perform it safely.
- Edit scope: never refactor surrounding code, add comments, or adjust unrelated imports. Never choose a name not explicitly stated in the prompt. When a comment is genuinely warranted (prompt asks for it, or new logic is non-obvious), keep it English-only and explain WHY - cover only non-obvious business logic, a handled edge-case, a workaround, or an invariant the code cannot express; never restate what the code plainly does.
- Localization: never translate UI strings to RU/UK.
- Neuroslop avoidance (CLAUDE.md Rule 20): while implementing, do not emit AI-slop - no trivial restating comments, no empty/broad swallowing `catch` (recover, return a safe default, or log a plain-English degradation at the right level), no hardcoded `="#hex"` in `res/layout*` (use `?attr/` or `@color/`), no bare `lifecycleScope.launch { flow.collect { } }` on a view-bound Flow (use `collectOnLifecycle`). The `neuroslop-gate` in `post-change.ps1` rejects regressions.
- Dead-weight hygiene (CLAUDE.md Rule 21): when a step replaces or orphans code, resources, or dependencies, delete the dead remnant in the same step - don't leave it for R8 or a later cleanup, and drop any `-keep` rule naming a deleted class. Before deleting a zero-reference artifact, grep `PLAN/` for active-ticket scaffolding (`Partial` / `In Progress` / `Block*`) and do not remove another ticket's in-flight work. For "removed from the build" claims (native libs, `assets/`, jar data-resources, flavor-scoped deps), verify on a `release`/target-variant artifact, not a debug APK.
- Repo tooling: if a repo helper script in the current step is broken or insufficient, fix the script instead of working around it, then continue the same step. For Android `<string>` edits prefer `scripts/utils/set-android-string.ps1` - see Process step 7b for the `-Action set|add|get|remove|rename|list` usage.
- Completion rules: step is `[x] done` only when every Verification predicate returned PASS in the current run - never on intent. Idempotency: running twice with no changes is a no-op on the second run. Never auto-revert a failed edit - user decides from Step Log.
- Tracking: dev log per file, per step - run immediately after step completion. Cursor recomputed from phase file `Status:` on every invocation - never from memory.
- **Debug verification tags belong only to `BlockNeedUserTest`.** Insert `Timber.d("Sxxxx: …")` tags as the final code edits before the last phase's `Project compiles` build, only when on-device verification is part of acceptance - never at `Implemented`, never per-step, never after the build. Never remove tags from this skill - that is `/spec-check`'s job on the `Verified` transition (or `/spec-update`'s on a re-open). Reserve the `Sxxxx:` prefix for these temporary probes only; do not put it into persistent `Timber.i/w/e` or long-lived `Timber.d` messages. See CLAUDE.md "Debug Verification Tags".
- **Landscape parity (MANDATORY):** any step editing `res/layout/*.xml` MUST list the corresponding `res/layout-land/*.xml` in `Files Touched`. If the landscape variant exists and is absent from the step → abort (see Pre-edit guards). If the landscape variant does not exist but the screen supports rotation → add an explicit note in the step or a dedicated sub-step to create it.

---

## Spec Catalog hooks

- **Argument resolution.** First positional arg is `Sxxxx` (preferred) or a slug. If slug, resolve via `pwsh -NoProfile -File scripts/spec_catalog/select.ps1 -Name "<slug>" -Format json` to obtain the id. Read current status the same way before the first phase touches code.
- **Status transitions.**
  - Before the first non-done step is started: `pwsh -NoProfile -File scripts/spec_catalog/update.ps1 -Id <Sxxxx> -Status "In Progress"` (skip if already `In Progress` or later).
  - After every phase has all steps `[x] done` and final dev log is written: `pwsh -NoProfile -File scripts/spec_catalog/update.ps1 -Id <Sxxxx> -Status Implemented`.
  - When flipping to `BlockNeedUserTest` (on-device acceptance): the `Timber.d("Sxxxx: …")` debug tags were already inserted before the final phase's build (see Process - "Final-phase debug-tag insertion"); here just `update.ps1 -Id <Sxxxx> -Status BlockNeedUserTest`.
  - When a hard stop indicates a block: `update.ps1 -Id <Sxxxx> -Status BlockQuestions | BlockExternal | BlockByOtherTask` per the stop reason.
- **Forbidden:** never write `PLAN/spec-catalog.jsonl` directly; never set the journal status to `Verified` from this skill - that is `/spec-check`'s job.
