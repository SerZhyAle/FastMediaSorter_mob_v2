# /spec-dev - Reference

On-demand companion to the driver `.claude/commands/spec-dev.md`. Nothing here is read unconditionally - the driver names the section and the condition at the point of use. Nothing here is needed to execute a step, run its verification, or hard-stop.

Sections:

1. Status gate - per-status behaviour (Status Gate)
2. `--verify-smoke` rationale (Usage)
3. Out-of-scope discoveries (Process)
4. CODE.LOCK warnings (Process step 6a)
5. Android string edit shortcut - flag catalogue (Process step 7b)
6. ChangeType selection (Process step 10)
7. Audit severity handling (phase boundary)
8. Session snapshot arguments (phase boundary)
9. Verify-smoke verdicts (after all phases)
10. Finalization - `close-and-log.ps1` (after all phases)
11. Device-test gate - chat notes (after all phases)
12. Step Log format (Phase File Conventions)
13. Landscape parity - the no-variant branch (Constraints)
14. Debug verification tags - prefix reservation (Constraints)
15. Implementation constraints - full text (Constraints)
16. Spec catalog hooks - status transition commands (Spec Catalog hooks)

---

## Status gate - per-status behaviour

Open when the strategic `Status:` is anything other than `Tactical` or `In Progress`.

| Strategic `Status:` | Behavior |
| --- | --- |
| `Tactical` | Allowed - advance to `In Progress` on first executed step. |
| `In Progress` | Allowed - continue. |
| `Draft` / `Approved` | Abort: no tactical folder. Run `/spec-tech` first. |
| `Implemented` / `Verified` | Abort: feature closed. |
| `Partial` / `Broken` | **Auto-fix pass:** run `/spec-fix <Sxxxx>` to apply all mechanical fixes, then re-read status. If still `Partial`/`Broken` after fix pass, list remaining FAIL items and stop - manual resolution. If all resolved, continue. |
| `BlockNeedUserTest` | Note in chat and stop. User must confirm on-device test result before re-running. |
| `BlockByOtherTask` / `BlockQuestions` / `BlockExternal` | Abort: blocked. Resolve block first (see §10 of strategic spec), then `update.ps1 -Status <prev>`. |

---

## `--verify-smoke` rationale

`--verify-smoke` opt-in: catches a trivial launch-crash before strategic spec recorded `Implemented`/`BlockNeedUserTest`. Safety net, not a replacement for `/spec-test-device` or `/spec-check`.

---

## Out-of-scope discoveries

Open on the first out-of-scope discovery of a run (CLAUDE.md §3.1).

Dedup via `scripts/spec_catalog/search.ps1` first, before creating the `/spec-draft`. Trivial in-scope issues still handled inline.

---

## CODE.LOCK warnings

Open when `scripts/utils/enter-code-lock.ps1` prints a warning.

If it warns about a live `BUILD.LOCK`, that's informational only (don't start a build now; editing itself is unaffected). The lock is released automatically by `post-change.ps1` at step 10 - no explicit release needed here.

---

## Android string edit shortcut - flag catalogue

Open before the first call to `scripts/utils/set-android-string.ps1` in a step (Process step 7b).

Update existing value in one locale: `-Action set -Module <module> -Locale en|ru|uk -Key <key> -Value <text>` (add `-ExpectedOldValue` to guard, `-CreateIfMissing` to upsert). New key across all three locales at once: `-Action add -Key <key> -En <text> -Ru <text> -Uk <text>` (parity-enforced, fails if key exists). Lookup/lifecycle across all `strings*.xml`: `-Action get|remove|rename|list`.

---

## ChangeType selection

Open when a step's files do not map obviously onto one `-ChangeType`.

Choose `Kotlin` for executable `.kt`/`.java` edits, `Xml` for string/resource changes, `Doc` for spec/doc-only edits, `Mixed` only when one step genuinely spans code plus strings.

---

## Audit severity handling

Open whenever a phase-boundary audit finds anything.

- **P0/P1 → fix now.** Small scoped edit, re-run the affected step's `Verification:` predicates, run `post-change.ps1` closure, append `AUDIT-FIX: <finding>` to this phase's Step Log. Fixing here costs this phase's rework; leaving it costs every later phase's rework plus the end-of-pipeline `/spec-check` finding it cold.
- **P2 → fix if trivial**, else append `AUDIT-P2: <finding>` to Step Log (recurring pattern → flag as mechanical-gate candidate, CLAUDE.md Rule 19/20).
- **P3 → fix inline or skip.**

This is a fast self-review scoped to one phase, not a replacement for `/spec-check`'s deeper end-of-pipeline audit.

---

## Session snapshot arguments

Open before calling `scripts/agent_continuity/session-snapshot.ps1` at a phase boundary.

Pass `-Ticket <Sxxxx>` (active spec id), `-Goal "<phase title>"` (just-finished phase title), `-FilesTouched` (from this phase's `Files Touched` table), `-NextStep "<cursor>"` (next step printed in chat, or `phase-complete` when whole phase was the final one).

`-FilesTouched` takes **a comma-joined string** when the script is run the usual way, `pwsh -NoProfile -File ...`: that invocation hands the script flat strings, so a real `@(a, b, c)` array arrives as separate tokens. Until 2026-08-06 those extra tokens bound to `-Decisions` and `-Blockers` by declaration order and the script still exited 0, writing a snapshot that named one file and filed the rest under the wrong headings (found in S0429). The script now binds by name only, so that shape fails loudly instead - pass `-FilesTouched "a.kt,b.kt,c.kt"`, or call it through the `&` operator where a genuine array survives.

---

## Verify-smoke verdicts

Open before reading the `/verify --build` verdict line, under `--verify-smoke`.

- `verify: ... PASS/SKIPPED ...` with `log errors 0` and `crashes 0` → proceed with status flip below as normal.
- Any FAIL row in run table, any `crashes K > 0`, or any `log errors` with fresh exception from package under test → **abort status flip**. Leave ticket at `In Progress`. Append one `VERIFY-SMOKE FAIL` line to last phase's `## Step Log` pointing at scenario path in `temp/scratch/verify_*.md`. Stop with: `<Sxxxx>: verify-smoke FAIL, status not advanced. See temp/scratch/verify_<TS>.md.`
- `device-ready.ps1` reports `ready: false` (no device, mobile-mcp missing) - it still exits 0, so read the payload, not the exit code → **do not** abort: log skip in chat (`verify-smoke skipped: <reason>`) and proceed with original status flip. Smoke is a bonus, never a hard gate when no device present.

---

## Finalization - `close-and-log.ps1`

Open before the finalization call, every time.

```powershell
pwsh -NoProfile -File scripts/spec_catalog/close-and-log.ps1 `
    -Id <Sxxxx> `
    -Status <Implemented|BlockNeedUserTest> `
    -StatusNote '<For BlockNeedUserTest: what to verify on device. Omit for Implemented.>' `
    -DevLogs '[{"file":"PLAN/Sxxxx_<slug>.md","target":"spec-dev","desc":"All phases done; status -> <new>"},{"file":"app_v2/src/.../X.kt","target":"spec-dev","desc":"<phase-NN.M edit summary>"}]' `
    -FuncOp <ADD|CHANGE|DELETE|""> -FuncDesc "<english summary or omit>" `
    -FeatArea "<inventory area, e.g. Video Player>" `
    -FeatName "<short capability name, e.g. Trace log outcome label>" `
    -FeatFlavors "<exact builds that ship it, e.g. standard,vr>" `
    -CatalogModule app_v2
```

`-DevLogs` takes ONE string holding a JSON array - one `{file,target,desc}` object per modified source file. Never a PowerShell array literal `@('{..}','{..}')`: `pwsh -File` binds only its first element and the rest become positional args, which `close-and-log.ps1` now rejects at bind time (S1063).

`-StatusNote` **mandatory** when `-Status BlockNeedUserTest`; omit or leave empty for `Implemented`.

Feature-inventory block (records delivered capability in `docs/ALL_FEATURES.jsonl`, EN-only developer inventory that replaced `dev/FUNCTIONALITY.log`). `docs/FEATURES*` is curated public showcase, touched ONLY by `/skill-release` from inventory diff - never write a per-spec entry into FEATURES here:

- **`ADD`** - spec introduces new user-visible capability (no prior equivalent). Hints: §2 Goals describe a new feature; touched files are new classes / new screens / new menu entries. Pass `-FeatArea`/`-FeatFlavors` so record lands in right area with correct flavor availability.
- **`CHANGE`** - spec modifies existing user-visible behaviour. Hints: §2 Goals describe a behaviour change / UX improvement / reordering / visibility change.
- **`DELETE`** - spec removes a previously-shipped user-visible capability without archiving the spec. Marks existing record `status: removed` (keeps it for history). Use when §2 Goals remove a feature/menu/screen. (Archiving a capability-removing spec instead uses `/spec-arc --removes-functionality`.)
- Pass `-SkipFuncLog` (or omit `-FuncOp`) when spec purely internal (refactor, performance, build/CI plumbing). Document skip in chat output.
- Description: concise user-visible summary, reusing spec title or first sentence of §2 Goals. EN-only.
- `-FuncOp` requires `-FeatArea`/`-FeatName`/`-FeatFlavors` (S1072); a missing one is exit 2 with nothing mutated. The facade no longer invents them, so the `-FuncOp` shortcut and a direct `add.ps1` call now produce the SAME record - reach for `scripts/all_features/add.ps1 -Id <area>.<feature> -Area -Name -Description -Flavors [-Spec Sxxxx]` only when recording outside a ticket closure.
- Read `-FeatFlavors` off the real gate - the `BuildConfig` flag in `app_v2/build.gradle.kts` or the source set the code lives in - never off a sibling record: sibling records for the same screen disagree (S0777 vs S0782), so copying one is how a wrong list spreads.

Individual-call fallback (`update.ps1 -Status` + `post-change.ps1 -ChangeType ...` × N + `scripts/all_features/add.ps1` + `catalog_sync.ps1` only when a separate catalog repair still needed) remains valid when `close-and-log.ps1` unavailable, but each call is a separate pwsh process.

---

## Device-test gate - chat notes

Open on the `BlockNeedUserTest` device probe, before writing the chat note.

- **Exit 0 (device online):** auto-chain `/spec-test-device <Sxxxx>` (full evidence run) → then `/spec-check <Sxxxx>`. `/spec-check` converts harvested evidence into `Verified` / `Partial` / `Broken` and, on transition out of `BlockNeedUserTest`, removes the `Timber.d("Sxxxx:` tags. Note in chat: `→ Device online: ran /spec-test-device + /spec-check. End status: <new>.`
- **Exit 2/1/3/6 (no usable device):** do not run. Note: `→ Awaiting on-device test. Debug tags inserted: N. No device attached - run /spec-sweep (or /spec-test-device <Sxxxx>) when a device is online; /spec-check removes the tags on the Verified transition.` Leave ticket in `BlockNeedUserTest`.

---

## Step Log format

Open when creating a phase file's `## Step Log` for the first time.

```markdown
**Status:** `[x] done`

**Step Log:**

- <YYYY-MM-DD> - Verification N/N PASS. Files: path/Foo.kt (+N LOC). Dev log recorded.
```

---

## Landscape parity - the no-variant branch

Open when a step edits `res/layout/*.xml` and no `res/layout-land/` counterpart exists.

If landscape variant does not exist but screen supports rotation → add an explicit note in the step or a dedicated sub-step to create it.

---

## Debug verification tags - prefix reservation

Open before writing the first `Timber.d("Sxxxx: …")` tag of a ticket.

Reserve the `Sxxxx:` prefix for these temporary probes only; do not put it into persistent `Timber.i/w/e` or long-lived `Timber.d` messages. See CLAUDE.md "Debug Verification Tags".

---

## Implementation constraints - full text

Open before the first `.kt` edit of a phase, and again before deleting anything a step orphans.

- Localization: never translate UI strings to RU/UK.
- Repo tooling, for Android `<string>` edits: prefer `scripts/utils/set-android-string.ps1` - see Process step 7b.
- Edit scope, on comments: per CLAUDE.md Rule 9 (comment discipline) - obey it as written. A comment is warranted here only when the prompt asks for it or the new logic is non-obvious: non-obvious business logic, a handled edge-case, a workaround, or an invariant the code cannot express.
- Per CLAUDE.md Rule 19 (neuroslop avoidance, detekt-clean-first) - obey it as written. Two spec-dev specifics: the `neuroslop-gate` in `post-change.ps1` rejects regressions at step close, and on an always-dirty tree you close via `post-change.ps1 -ScopeToFile` so detekt is diff-scoped and project-wide ratchets go advisory (CLAUDE.md section 12 "Validation & Post-Change").
- Per CLAUDE.md Rule 20 (dead-weight hygiene) - obey it as written; the step that orphans something is the step that deletes it, never a later cleanup. The spec-dev addition: before deleting a zero-reference artifact, grep `PLAN/` for active-ticket scaffolding (`Partial` / `In Progress` / `Block*`) and do not remove another ticket's in-flight work.

---

## Spec catalog hooks - status transition commands

Open when flipping a status by hand instead of through `close-and-log.ps1`.

- Before first non-done step started: `pwsh -NoProfile -File scripts/spec_catalog/update.ps1 -Id <Sxxxx> -Status "In Progress"` (skip if already `In Progress` or later).
- After every phase has all steps `[x] done` and final dev log written: `pwsh -NoProfile -File scripts/spec_catalog/update.ps1 -Id <Sxxxx> -Status Implemented`.
- When flipping to `BlockNeedUserTest` (on-device acceptance): `Timber.d("Sxxxx: …")` debug tags already inserted before final phase's build (see Process - "Final-phase debug-tag insertion"); here just `update.ps1 -Id <Sxxxx> -Status BlockNeedUserTest -StatusNote '<1-2 sentences: what the user must verify on device>'`.
- When a hard stop indicates a block: `update.ps1 -Id <Sxxxx> -Status BlockQuestions|BlockExternal|BlockByOtherTask -StatusNote '<reason and what resolves it>'` - note is **mandatory** for every Block* transition.
