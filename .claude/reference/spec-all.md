# /spec-all - Reference

On-demand companion to the driver `.claude/commands/spec-all.md`. Nothing here is read unconditionally - the driver names the section and the condition at the point of use.

Sections:

1. Drift-check rationale (Stage 0a-drift)
2. Approval gate stub (Stage S1)
3. Refinement passes (Stage F2)
4. BUILD-REQUIRED stop override (Stage F3)
5. MANUAL-REQUIRED stop (Stage F3)
6. Inline resolution catalogue (Stage F3)
7. Out-of-scope dependency (Stage F3)
8. Build gate - edited-file accounting (Stage F4)
9. Final report format
10. Finalization shortcut - `close-and-log.ps1`
11. Device-test gate
12. Spec Catalog hooks

---

## 1. Drift-check rationale (Stage 0a-drift)

Catches "spec written ahead of code, fix committed later, spec never updated" - otherwise wastes full F2+F3 cycle.

---

## 2. Approval gate stub (Stage S1)

**Approval gate stub.** Compact specs still hit Draft -> Approved gate. Append minimal §3.3 block before flipping status:

```markdown
### 3.3 Owner inputs (Approval gate)

- **Related tickets:** <none | Sxxxx,Sxxxx>
```

If compact spec touches sensitive scope (UI / flavor / data / API), follow `/spec` Process step 5.1 detection and emit matching bullets in same block - gate uses same `check-owner-inputs.ps1`.

---

## 3. Refinement passes (Stage F2)

> **Refinement passes** (`/spec-update`) skipped unless §6 has Open research items unresolvable from codebase. If resolvable inline - resolve, persist findings to `PLAN/Sxxxx_<slug>/research/<NN>__<topic-slug>.md` with `**Артефакт:**` link in §6 (see `/research` step 5), patch spec, continue.

---

## 4. BUILD-REQUIRED stop override (Stage F3)

**BUILD-REQUIRED stop override:**

1. Invoke `/build` -> `standard debug`. Use `a.ps1 dq` (quiet debug - same `assembleStandardDebug`, suppresses UP-TO-DATE / deprecated-DSL / known-acceptable warnings) for resume-mode iteration builds; use `a.ps1 d` only when investigating build failure needing full Gradle output.
2. PASS -> tick criterion `[x] (auto-build - PASS)`, continue `--resume`.
3. FAIL -> fix minimal error. Retry up to MAX_BUILD_RETRIES. Exception: a `BUILD.LOCK held` refusal (CLAUDE.md Rule 23, another agent session mid-build) is not a code error - it still counts against MAX_BUILD_RETRIES, but don't attempt a source "fix" for it, just retry (the lock self-clears when the other build finishes or goes stale).
4. Still failing -> hard-stop -> final report as Blocked.
5. Any `src/vr/` file modified: also run `vr debug` after standard passes.

---

## 5. MANUAL-REQUIRED stop (Stage F3)

**MANUAL-REQUIRED stop:** tick as `[manual - deferred to human]`. Continue `--resume`. If manual gate is on-device verification, `/spec-dev` inserts `Timber.d("Sxxxx: <entry-point description>")` tags as final code edits **before the last phase's build** (CLAUDE.md "Debug Verification Tags") - that build validates code + tags in one pass, no extra build after. Then set status `BlockNeedUserTest` and apply **Device-test gate** (section 11) - auto-run `/spec-test-device` + `/spec-check` when device online.

---

## 6. Inline resolution catalogue (Stage F3)

**Hard stop - attempt inline resolution:**

- Missing symbol/wrong path -> Grep/Glob actual location; patch spec; resume.
- Verification fail -> re-read file, correct edit, re-run predicates.
- Trilingual gap -> add `<!-- TODO translate: <EN text> -->` in missing locale; continue.
- Line budget warning: per CLAUDE.md Rule 5 (backup before editing >500 LOC) - obey it as written, put the copy under `temp/<Sxxxx>/`, then continue rather than stopping the pipeline.
- Ambiguous step (placeholder, missing name) -> attempt to resolve from codebase; resolved -> patch step, continue; still ambiguous after 1 attempt -> mark `[DEFERRED - ambiguous]`, add to manual list, skip to next step. Never stop pipeline for one ambiguous step when others unblocked.
- Unresolvable after 2 attempts -> mark `[DEFERRED]`, add to manual list, continue with remaining steps.

---

## 7. Out-of-scope dependency (Stage F3)

**Out-of-scope dependency:**

- Minor (no new classes, no schema change, <= ~30 min) -> implement inline.
- Significant -> allocate new `Sxxxx` via `insert.ps1`, write `PLAN/Sxxxx_<dependency-slug>.md` (`Status: Approved`, `<!-- discovered by /spec-all - <date> -->`). If dependency is **Full**-complexity, create full tactical folder too. Continue current pipeline. Set parent's status to `BlockByOtherTask` only if dependency must finish first, and then record the id as a `Blocker: Sxxxx` token (§10 or the status note) - a bare mention under §10 reads as a relation, not a dependency, and the tooling ignores it (S1482). Dependency need not finish first -> just record it under §10 as prose.

---

## 8. Build gate - edited-file accounting (Stage F4)

Consider only files **this pipeline run actually edited** (F3 tracks them - do NOT derive from `git diff`, which mixes in unrelated WIP across many tickets on this repo). Exclude `PLAN/`, `docs/`, `dev/CHANGELOG.md`, `*.md`.

- **Skip when F3 already built post-tags.** If F3's final phase ended with `Project compiles` build that already included inserted Timber tags (the `BlockNeedUserTest` path) and no code changed since, F4 is redundant second build - skip.
- Code files present (and no post-tags build in F3) -> `/build` -> `standard debug`. Persistent FAIL -> hard-stop.
- `src/vr/` among edited files -> also `/build` -> `vr debug`.
- Docs-only changes -> skip.

---

## 9. Final report format

```text
spec-all: <Sxxxx> <short-name> - <Verified ✅ | Partial ⚠️ | Blocked 🛑 | Incomplete ⏱️>
Spec:   PLAN/Sxxxx_<short-name>.md  [Simple]
  - or -
Spec:   PLAN/Sxxxx_<short-name>/INDEX.md  [Full]
Audit:  inline in spec - `## Last Audit` section.

Manual / unresolved:
- <item>   (empty -> "All closed automatically.")
```

```powershell
.\scripts\add_to_dev_log.ps1 "PLAN/Sxxxx_<short-name>.md" "spec-all" "Pipeline <status>: <Sxxxx>"
```

---

## 10. Finalization shortcut - `close-and-log.ps1`

**Finalization shortcut.** When pipeline closes a ticket (`Verified` / final `BlockNeedUserTest` / `BlockExternal`) and code was touched, prefer `close-and-log.ps1` - one pwsh process instead of 6-7 launches:

```powershell
pwsh -NoProfile -File scripts/spec_catalog/close-and-log.ps1 `
    -Id <Sxxxx> `
    -Status <Verified|BlockNeedUserTest|...> `
    -DevLogs '[{"file":"PLAN/Sxxxx_*.md","target":"spec-all","desc":"<spec-level msg>"},{"file":"app_v2/src/.../X.kt","target":"spec-all","desc":"<code msg>"}]' `
    -FuncOp FIX -FuncDesc "<user-visible summary>" `
    -FeatArea "<inventory area, e.g. Streams>" `
    -FeatName "<short capability name>" `
    -FeatFlavors "<exact builds that ship it, e.g. standard,legacy,noLegal>" `
    -CatalogModule app_v2
```

`-DevLogs` takes ONE string holding a JSON array, never a PowerShell array literal `@('{..}','{..}')` - `pwsh -File` binds only its first element and the rest become positional args, which `close-and-log.ps1` now rejects at bind time (S1063).

`-FuncOp` requires `-FeatArea`/`-FeatName`/`-FeatFlavors` (S1072). The record asserts facts the script cannot know, and it used to guess them (`General` / an 80-char cut of `-FuncDesc` / `standard`), yielding a record that is structurally valid, plausible and false - `validate.ps1` passes it and the error only surfaces in the release showcase built from this inventory. Read `-FeatFlavors` off the actual gate (the `BuildConfig` flag in `app_v2/build.gradle.kts`, or the source set the code lives in), never off a sibling record - sibling records disagree.

Sub-skills (`/spec-dev`, `/spec-check`, `/spec-fix`, `/spec-arc`) call this internally. Use directly from `/spec-all` only when orchestrator itself owns the closing step (rare - usually a sub-skill ran last).

---

## 11. Device-test gate

**Device-test gate.** Whenever this pipeline sets a ticket to `BlockNeedUserTest` (resume-mode MANUAL-REQUIRED stop, or `Device/hardware verification required` hard-stop row), do not just park the block - probe for attached device and auto-run on-device verification when present. Keeps `/spec-all` unattended: adds device test only when device online, silent no-op otherwise.

```powershell
pwsh -NoProfile -File scripts/devtest/device-ready.ps1 -Package com.sza.fastmediasorter.debug -CheckMcp -Json
```

- **Exit 0 (device online):** tags already inserted, status `BlockNeedUserTest`; auto-chain `/spec-test-device <Sxxxx>` (full evidence) -> `/spec-check <Sxxxx>`. `/spec-check` converts evidence into `Verified` / `Partial` / `Broken` and removes tags on transition out of `BlockNeedUserTest`. Record resulting status in final report instead of `BlockNeedUserTest`.
- **Exit 2/1/3/6 (no usable device):** silent no-op. Leave ticket in `BlockNeedUserTest`, keep tags, add one-line `Manual / unresolved` note: `device-test deferred (no device) - run /spec-sweep when a device is online`.

Non-blocking: failed device-ready probe never stops pipeline. Batch drain for parked tickets is `/spec-sweep`.

---

## 12. Spec Catalog hooks

- **Argument resolution.** Accept `Sxxxx`, slug, or path (`PLAN/Sxxxx_<slug>.md`). For `Sxxxx`, resolve via `pwsh -NoProfile -File scripts/spec_catalog/select.ps1 -Id Sxxxx -Format json` and skip Stage 0 short-name derivation.
- **Stage transitions** (orchestrator does not duplicate sub-skill updates - these fire from underlying skills):
  - F1: `/spec` runs `insert.ps1` (Status `Draft`); `/spec-all` then auto-flips `Draft -> Approved` via `update.ps1 -Status Approved`.
  - F2: `/spec-tech` flips to `Tactical`.
  - F3: `/spec-dev` flips to `In Progress` then `Implemented`.
  - F5: `/spec-check` flips to `Verified` / `Partial` / `Broken`.
- **Final report.** Always include `Ticket: Sxxxx` on first line, alongside spec slug.
- **Forbidden:** never write to `PLAN/spec-catalog.jsonl` directly. Never produce path with `_spec_` segment. Do not bypass an underlying skill's catalog update.
