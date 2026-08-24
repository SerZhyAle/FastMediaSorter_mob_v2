---
description: "Use to execute a tactical spec step by step, running each step's Verification before flipping it to done. Triggers: 'spec-dev Sxxxx', 'implement this tactical plan', 'continue the spec'."
---

# Specification Developer Executor

Execute a tactical spec step by step. Reads `PLAN/Sxxxx_<short-name>/INDEX.md` + phase files, follows `Prompt for developer:` in dependency order, runs each step's `Verification:` predicate before flipping it to `[x] done`.

## Ticket Lease Ownership

After resolving the ticket, standalone `/spec-dev` claims the ticket lease before reading or changing a phase:

```powershell
pwsh -NoProfile -File scripts/spec_catalog/ticket-lease.ps1 -Verb Claim -Id <Sxxxx> -Reason "/spec-dev"
```

Exit 3 means a live sibling already owns the ticket. Report that outcome and stop without work. Re-claim at each phase boundary to refresh the lease heartbeat. On every final standalone exit, release it with `ticket-lease.ps1 -Verb Release -Id <Sxxxx>`.

If invocation context includes `lease-owner=spec-all`, this is a parent-owned lease: re-claim only to refresh it and never release it. The parent `/spec-all` invocation performs the final release.

Everything needed to run, verify and stop a step is here. Look-ups live in `.claude/reference/spec-dev.md`; each pointer names its section and when to open it.

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

`--verify-smoke` runs `/verify --build` once with no scenario (default smoke: launch + screenshot + crash scan). Smoke fails → status flip **aborted**: ticket stays `In Progress`, last phase's `## Step Log` gets a `VERIFY-SMOKE FAIL` line. Rationale, if asked to justify the flag: `.claude/reference/spec-dev.md` §`--verify-smoke` rationale.

---

## Status Gate

Read the strategic `Status:` before touching any step. `Tactical` → allowed, advance to `In Progress` on first executed step. `In Progress` → allowed, continue. **Any other status → look it up in `.claude/reference/spec-dev.md` §Status gate and obey it before touching a step.** Each one aborts, stops for the user, or takes an auto-fix pass first; the table is the authority on which.

---

## Process

Fixed order: resolve scope → optional dry-run → execute one step at a time → run that step's verification → update phase/status metadata.

> **Out-of-scope discoveries (CLAUDE.md §3.1):** mid-step you hit a problem unrelated to this ticket and non-trivial (own research + fix) → do NOT fix inline, do NOT expand current step. Park via `/spec-draft`, note `parked: Sxxxx` in step log, continue planned step. First discovery of a run → `.claude/reference/spec-dev.md` §Out-of-scope discoveries (dedup step, in-scope exception).

**1 - Parse arguments, load state.**

Compute target step set from flags. Read strategic spec, INDEX, all phase files in scope. Verify Status Gate. Verify all Pre-Implementation Blockers in INDEX ticked - any unchecked → abort with blocker text.

**2 - If `--dry-run`:** print planned step table and exit. No further processing.

**3 - Execute steps, one at a time.**

For each step in plan order:

1. **Re-read phase file.** If `Status:` no longer `[ ]`/`[~]` → log "PRE-RESOLVED - skipped".
2. **Verify dependencies.** `Depends on:` step must be `[x] done` - else abort: "Dependency violation: NN.M depends on NN.K which is not done."
3. **Read `Prompt for developer:` and `Why:`** + `Files Touched` row(s). For each referenced existing class/method, confirm it exists at expected path - else abort: "Prompt references `<symbol>` at `<path>`, not found." The `Why:` field (S1343, mandatory since 2026-08-02) carries the step's sourced rationale - read it before deciding anything the prompt does not cover, and prefer it to re-opening the strategic spec. `Why:` reading `not stated in strategic spec` is not a defect and not a blocker; it means the rationale was never written down, so an uncovered edge case needs the strategic spec or `/spec-quiz`, not a guess.
4. **Ambiguity check.** Prompt contains `<TODO>`, `<choose ..>`, `???`, or any unresolved placeholder → abort, request spec update via `/spec-update`. If requires user input, set status `BlockQuestions` and stop.
5. **Pre-edit guards:**
   - Read-only zone → abort. Per CLAUDE.md Rule 4 (read-only zones) - obey it as written.
   - Per CLAUDE.md Rule 5 (backup before editing >500 LOC) - obey it as written; put the timestamped copy under `temp/<Sxxxx>/`.
   - Per CLAUDE.md Rule 2 (2000 LOC file size limit) - obey it as written; a projected post-edit size that crosses it → abort: "line budget violation, split via Manager pattern."
   - File in `res/layout/` → per CLAUDE.md Rule 11 (layout-land parity) - obey it as written. If the landscape variant exists and is NOT listed in this step's `Files Touched` → abort: "landscape counterpart `res/layout-land/<file>.xml` not covered in step - update `Files Touched` and prompt before proceeding."
   - **Flavor isolation guard:** see Hard Stops #14 - abort on a `src/main/java/**` flavor guard; do not silently rewrite, push back through `/spec-update`.
6. **Flip step to `[~] in progress`** with `pwsh -NoProfile -File scripts/spec_catalog/plan-tick.ps1 -Id <Sxxxx> -Phase <NN> -Steps <M> -State InProgress`. Never hand-edit the marker: the tool keeps the phase file, its `**Steps done:**` header and the INDEX row in step, and it refuses when those already disagree.
6a. **CODE.LOCK (CLAUDE.md Rule 23).** Only when this step touches `app_v2/`/`wear/` source (`.kt`/`.java`/`.xml`/`build.gradle.kts`) - run `pwsh -NoProfile -File scripts/utils/enter-code-lock.ps1 -Reason "/spec-dev <Sxxxx> step <NN.M>"` before the edit. Skip for doc/spec-only steps; released automatically by `post-change.ps1` at step 10. It warns → `.claude/reference/spec-dev.md` §CODE.LOCK warnings before reacting.
7. **Apply the edit** - `Edit` or `Write` per Prompt. Scope strictly to what prompt specifies: no surrounding refactors, no extra comments, no unrelated import cleanup.
7a. **Communication policy check.** If edit adds/changes user-visible strings, verify against `docs/COMMUNICATION_POLICY.md` §2 and §6 before marking step done.
7b. **Android string edit shortcut.** For `<string>` work prefer `pwsh -NoProfile -File scripts/utils/set-android-string.ps1` (byte-preserving) over manual XML editing. Use manual edits only for `plurals`, `string-array`, comments, regrouping, or bulk rewrites. `-Action set|add|get|remove|rename|list` flags before the first call: `.claude/reference/spec-dev.md` §Android string edit shortcut - never guess them.
8. **Run `Verification:` predicates** (Glob/Grep/value equality/size checks).
9. **Outcome:**
   - All predicates PASS → `plan-tick.ps1 -Id <Sxxxx> -Phase <NN> -Steps <M> -State Done -Log "<what the run proved>"`, which flips the marker, appends the Step Log entry and recomputes both counters in one call. **Consecutive steps finished in the same pass are ticked in one call** - `-Steps 3,4,5` - rather than one call each; that batching is the whole point (S1596 measured 1 437 bookkeeping edits in a week, about 61% of all plan-file edits).
   - Any predicate FAIL → leave at `[~] in progress`. Append FAIL note. **Hard stop.**

   The tool writes state; it judges nothing. A step reaches `Done` only when every Verification predicate returned PASS in the current run - passing `-State Done` for a step that did not is exactly the false tick the tool refuses to make easy, which is why it has no whole-phase form.
10. **Run mechanical post-change closure** for every modified file.
  - `pwsh -NoProfile -File scripts/post-change.ps1 -File "<path>" -Target "<target>" -Description "<short EN description>" -ChangeType <Doc|Script|Config|Tooling|Kotlin|Xml|Mixed> [-Module <app_v2|wear>] [-KeyPrefix "<key_prefix>"]`.
  - Unsure which `-ChangeType` a step's files map to → `.claude/reference/spec-dev.md` §ChangeType selection.
  - Spec status transitions and feature-inventory (`docs/ALL_FEATURES.jsonl`) decisions stay outside this command.

After all planned steps in current phase complete:

- **Final-phase debug-tag insertion (before the build).** If this is the **last** phase in scope AND strategic spec's acceptance includes on-device verification, insert the `Timber.d("Sxxxx: <entry-point description>")` tags now - one at each changed flow entry across all phases (per CLAUDE.md "Debug Verification Tags": one tag per flow entry, not per modified line). Tags are the **last code edits** and go in **before** the `Project compiles` build below, so a single build validates implementation + tags. Never insert tags after the build - that forces a redundant second build.
- **Phase Done Criteria check.** For each checkbox:
  - Mechanically verifiable → run check, tick if PASS.
  - Build required (`Project compiles`) → run `pwsh -NoProfile -File ./a.ps1 dq` automatically via PowerShell (no permission prompt; CLAUDE.md section 9 owns the target list, and per section 6's 120 s threshold a fast check runs in the FOREGROUND). Exit 0 → tick. Non-zero → append last 30 lines of output, hard stop: "Build FAILED". When final-phase tags just inserted, this is the single build that validates code + tags - schedule no further build for tag validation.
  - Manual review required → leave unticked, mark `MANUAL-REQUIRED`.
- **UI phase refusal (S1338).** A phase whose `Files Touched` names `res/layout*`, an `Activity`/`Fragment`/`*View`/`ui/**` class, or a settings surface **may not be flipped to `✅ Done`** until both hold: (1) the strategic spec carries a recorded placement decision - a `/ui-clarify` record or an owner ruling quoted verbatim - and (2) **layout evidence** for the changed screen was captured this phase via `pwsh -NoProfile -File scripts/devtest/adb.ps1 shot` (or the `run-fastmediasorter` skill) and its path is written into the phase's Step Log. Evidence means an artifact that actually shows the placement: normally the screenshot, but on a screen under `FLAG_SECURE` - `AddResourceActivity` and `ResourceEditorActivity` while `secureSensitiveScreens` is on; `SettingsActivity` left this set in S1784 and now screenshots normally - the frame is black by design, so the artifact is the `uiautomator` node tree `shot` now pulls beside it and prints as `TREE <path> (N node(s))`. **Recording the black frame's path does not satisfy this gate** (S1520): the requirement was met and nothing was shown, which is the exact failure the gate exists to prevent. Record the `TREE` path, or turn `secureSensitiveScreens` off and shoot the screen for real. Missing decision → `/ui-clarify` now, or `BlockQuestions`. No device attached → write `screenshot deferred (no device)` in the Step Log and leave the phase `🚧 In Progress` only if the phase's own Done Criteria demand the shot; otherwise record the deferral and continue. A placement decision must be visible before it ships - 33% of owner corrections are placement, and no gate stood between the guess and the owner.
- Every criterion ticked → flip phase `Status:` to `✅ Done`, set `Completed:`, update INDEX row + counter.
- Any criterion unticked → leave `🚧 In Progress`, update step counter only. Hard stop.
- **Phase-boundary audit (mandatory - CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md` "Phase-boundary audits").** Immediately after this phase flips `✅ Done`, before the next phase's first step: self-audit this phase's `Files Touched` against the protocol. Layer 1 (architecture/readability) always; Layer 2 (lifecycle/coroutine/concurrency), Layer 3 (memory/listener ownership), Layer 4 (Room) when the phase touched that surface. Skip entirely when `Files Touched` is empty or doc-only (e.g. `docs-catalog-cleanup`). Tag findings by severity. **Audit found something → `.claude/reference/spec-dev.md` §Audit severity handling, follow the P0/P1/P2/P3 branch it names** before editing or deferring anything; P0/P1 are always fixed in this phase, never deferred. A fix that requires a design decision not derivable from the spec/codebase is an ambiguity → Hard Stop #1 (`BlockQuestions`), same as any other ambiguous prompt - do not guess.
After all phases done:

- **Optional verification smoke (only when `--verify-smoke`).** Before flipping strategic status, run `/verify --build` once with no scenario arg. Skill writes artefacts under `temp/scratch/verify_*`; do not touch them. **Read `.claude/reference/spec-dev.md` §Verify-smoke verdicts before reading the verdict line** - it holds the strings to match and the literal stop message. The decisions: clean → flip status as normal; any FAIL row, any crash, any fresh exception from the package under test → **abort the status flip**, leave the ticket at `In Progress`, append a `VERIFY-SMOKE FAIL` line to the last phase's `## Step Log`, stop; no usable device (`ready: false`, still exit 0) → skip and flip as normal.
- **Bugfix repro refusal (S1338).** A ticket whose work is a bugfix - it fixes a reported defect, a crash, or wrong behaviour rather than adding a capability - **may not be flipped to `Implemented`** until the strategic spec carries a before/after repro record: the failing observation with its evidence (logcat excerpt, screenshot, failing test, or the exact reproduction steps and what was seen), and the same observation repeated after the fix showing it gone. "No completion claim without proof" had no gate at the point it matters most, and 39 of 232 active tickets are bugfixes. Escape path, because a requirement the pipeline routes around is worse than none: when the defect cannot be reproduced on demand, write `REPRO: not reproducible on demand - <reason>` into the spec plus whatever indirect evidence exists (the fixed code path, a unit test covering it). A recorded reason is acceptable; silence is not.
- **No on-device gate** → flip strategic `Status:` to `Implemented`, add `**Implemented date:** <YYYY-MM-DD>`. No debug tags. Per-phase builds already validated compilation.
- **On-device verification is part of acceptance** → `Timber.d("Sxxxx:")` tags already inserted before final phase's `Project compiles` build (see "Final-phase debug-tag insertion") and validated by that single build. Here just flip journal status to `BlockNeedUserTest` and run a dev log line for each file that gained a tag. Do not insert tags or rebuild at this point.
- FEATURES / feature-doc updates and rest of finalization run **after** the build - never rebuild after the doc step.
- **Finalization (batched).** Use `close-and-log.ps1` for journal flip + dev log batch + feature-inventory record + catalog scan/render in one pwsh process. **Open `.claude/reference/spec-dev.md` §Finalization before this call, every time** - exact invocation, `-DevLogs` JSON contract, mandatory `-StatusNote`, `-FuncOp` ADD/CHANGE/DELETE selection, individual-call fallback. Never reconstruct it from memory.
- **Auto-chain to `/spec-check`:** immediately invoke `/spec-check <Sxxxx>` to audit implementation. Skip only if status flipped to `BlockNeedUserTest` - in that case apply **Device-test gate** below.

- **Device-test gate (on `BlockNeedUserTest`).** When status flipped to `BlockNeedUserTest`, do not just stop - probe for a device and auto-run on-device verification when one attached:

  ```powershell
  pwsh -NoProfile -File scripts/devtest/device-ready.ps1 -Package com.sza.fastmediasorter.debug -CheckMcp -Json
  ```

  Exit 0 (device online) → auto-chain `/spec-test-device <Sxxxx>` then `/spec-check <Sxxxx>`. Exit 2/1/3/6 (no usable device) → do not run, leave the ticket in `BlockNeedUserTest`. Both branches print a fixed chat note - copy it verbatim from `.claude/reference/spec-dev.md` §Device-test gate.

**Chat output:** `<Sxxxx>: N steps done. Cursor: <next step>. [Stop reason if any]. [verify-smoke PASS/SKIPPED/FAIL when --verify-smoke]. → Running /spec-check…`

---

## Hard Stops

Stop immediately and report - never guess or recover, never assume missing/ambiguous details, never attempt speculative recovery - on any of:

1. **Ambiguous prompt** - placeholder text, missing class/method name, unspecified Hilt scope, unspecified dispatcher. Set status `BlockQuestions` with `-StatusNote '<which placeholder/field is missing and where>'`.
2. **Verification FAIL** after edit - step left `[~]`. User investigates.
3. **Read-only zone touch.**
4. **Line budget violation** - per CLAUDE.md Rule 2 (2000 LOC file size limit), obey it as written.
5. **Build FAIL** - `.\a.ps1 dq` returned non-zero after auto-run for Phase Done Criteria. Stop with error excerpt. If the excerpt is a `BUILD.LOCK held` refusal (another agent session mid-build, CLAUDE.md Rule 23), this is not a code regression - note it distinctly, wait/retry once the holder finishes rather than debugging the source.
6. **Room schema change** - prompt mentions bumping `@Database(version)` or adding `Migration`. Stop only if step does **not** specify new version number and migration class name explicitly. If both named → proceed automatically, note in chat.
7. **Hilt module graph change** - adds `@Module`, `@Provides`, or modifies Hilt graph beyond a single `@Inject constructor`. Stop only if scope/qualifier not explicit in prompt. If scope named → proceed automatically, note in chat.
8. **Missing symbol** - prompt names class/method not found at stated path and prompt does not also create it.
9. **Dependency violation** - `Depends on:` step not `[x] done`.
10. **Catalog-affecting change without regen step** - public API change in touched file but no catalog regen step in phase. Stop, suggest `/spec-update --tactical --phase NN`.
11. **External system touch** - network, file deletion outside `temp/`, force push, CI edit. Stop, require explicit permission.
12. **Trilingual gap** - step adds UI string but prompt names <3 `values/` files. Stop, never fabricate translations.
12a. **Communication policy violation** - step adds/modifies a user-visible string failing §6 tone checklist of `docs/COMMUNICATION_POLICY.md` (raw exception text as primary message, "Are you sure?" without consequence, "operation completed successfully", empty state with no CTA). Rewrite to comply before proceeding; do not commit policy-violating copy.
13. **External dependency missing** - step needs library version / hardware / third-party state not present. Set status `BlockExternal` with `-StatusNote '<what is missing and what must happen to unblock>'`, stop.
14. **Flavor leak** - step writes a `BuildConfig.IS_*` / `SUPPORT_*` / `ENABLE_*` flavor guard inside `src/main/java/**`, OR places a flavor-only class (any new file containing `vr.*Activity`, `Vr*Renderer`, `*NoLegal*`, NewPipe/yt-dlp wrappers, OpenXR JNI) under `src/main/java/**` instead of `src/<flavor>/java/**`. Stop: tactical spec is wrong and needs `/spec-update`. Per CLAUDE.md Rule 14 (flavor isolation) - obey it as written; see also `dev/FLAVOR_DEVELOPMENT_RULES.md`. Never compensate by adding the guard "just for this step".

---

## Phase File Conventions

Step `Status:` progression: `[ ] not done` → `[~] in progress` → `[x] done`, plus `[manual - deferred]` for a step whose gate is hands-on. All four are written by `plan-tick.ps1 -State NotDone|InProgress|Done|Manual`, never by hand.

The Step Log is written by the same call (`-Log "<text>"`) and is append-only - re-executions add lines, never overwrite. A wrong batch is undone with the same tool (`-State NotDone`), which restores every marker and deliberately leaves the log: changing a state back does not erase the record that it was once set.

Ticking a `Prerequisites` or `Phase Done Criteria` bullet uses the same tool's checkbox form, `-Checkbox "<label fragment>" -State Done`. A fragment matching zero or several bullets is refused with the candidates named - it never guesses which gate you meant.

---

## INDEX Conventions

The `Steps` cell, the row `Status` cell, `**Phases:** X/N done` and `**Last updated:**` are all recomputed by `plan-tick.ps1` from the phase file on every call - do not edit them. `**Last updated:**` is read mechanically by the drift check, so a hand edit that forgets it silently weakens a gate.

Recomputed, never incremented: if the index and the phase file already disagree, the tool exits 3, writes nothing and names both counts. Reconcile them first rather than letting a batch paper over the divergence.

Do NOT touch `Pre-Implementation Blockers`, `Blockers Log`, `Change Log` - owned by user/`/spec-tech`. `Completion Gate` bullets are ticked with `-Checkbox ... -Target Index`.
If user manually set phase to `⛔ Blocked` between runs → stop and ask whether to resume.

---

## Constraints

- Command limits: never run `gradle`, `./gradlew`, or `npm` directly. For Phase Done Criteria compile checks, run the `a.ps1` target named in CLAUDE.md section 9 (`dq` for a debug build, `fk` for a compile-only symbol change) automatically via PowerShell - do not pause or ask permission. Never run `git commit`, `git push`, `git rebase`.
- Execution discipline: never skip planned steps, never mark multiple consecutive steps done in one pass, never combine their status updates into one edit. Using a repo helper script to complete the current step is allowed when script is explicitly part of the step or required to perform it safely.
- Edit scope: never refactor surrounding code, add comments, or adjust unrelated imports (Process step 7). Never choose a name not explicitly stated in prompt.
- Code quality on every `.kt` edit: per CLAUDE.md Rule 19 (neuroslop avoidance, detekt-clean-first), Rule 20 (dead-weight hygiene) and Rule 9 (comment discipline) - obey them as written. **Read `.claude/reference/spec-dev.md` §Implementation constraints before the first `.kt` edit of a phase, and again before deleting anything a step orphans** - it carries the spec-dev detail the CLAUDE.md rules do not.
- Repo tooling: per CLAUDE.md Rule 13 (script ownership) - obey it as written; fix it inside the current step, then continue that same step rather than deferring.
- Completion rules: step is `[x] done` only when every Verification predicate returned PASS in the current run - never on intent. Idempotency: running twice with no changes is a no-op on the second run. Never auto-revert a failed edit - user decides from Step Log.
- Tracking: dev log per file, per step - run immediately after step completion. Cursor recomputed from phase file `Status:` on every invocation - never from memory.
- **Debug verification tags belong only to `BlockNeedUserTest`.** Insert `Timber.d("Sxxxx: …")` tags as the final code edits before the last phase's `Project compiles` build, only when on-device verification is part of acceptance - never at `Implemented`, never per-step, never after the build. Never remove tags from this skill - that is `/spec-check`'s job on the `Verified` transition (or `/spec-update`'s on a re-open). Before writing the first tag of a ticket: `.claude/reference/spec-dev.md` §Debug verification tags (prefix reservation, re-open case).
- **Landscape parity (MANDATORY):** per CLAUDE.md Rule 11 (layout-land parity) - obey it as written. Execution-time form of it: any step editing `res/layout/*.xml` MUST list the corresponding `res/layout-land/*.xml` in `Files Touched`. If the landscape variant exists and is absent from the step → abort (see Pre-edit guards). If landscape variant does not exist but screen supports rotation → `.claude/reference/spec-dev.md` §Landscape parity.

---

## Spec Catalog hooks

- **Argument resolution.** First positional arg is `Sxxxx` (preferred) or a slug. If slug, resolve via `pwsh -NoProfile -File scripts/spec_catalog/select.ps1 -Name "<slug>" -Format json` to obtain id. Read current status the same way before first phase touches code.
- **Status transitions.** Normal closure goes through `close-and-log.ps1` (see Finalization). When flipping a status by hand instead - first step started, a hard stop that blocks, an on-device hand-off - read `.claude/reference/spec-dev.md` §Spec catalog hooks for the exact `update.ps1` command per lifecycle edge. A `-StatusNote` is **mandatory** on every `Block*` transition.
- **Forbidden:** per CLAUDE.md Rule 12 (spec catalog is script-owned) - obey it as written. Additionally, never set journal status to `Verified` from this skill - that is `/spec-check`'s job.
