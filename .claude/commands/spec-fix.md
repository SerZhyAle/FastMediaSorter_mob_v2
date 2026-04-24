# Specification Audit Fix-up

Consume the latest `/spec-check` audit report and apply **trivial, mechanical** fixes to the repository so the next audit flips to `Verified`. This skill modifies the codebase (not the specs) and is deliberately conservative — anything that requires design judgement is recorded as a follow-up for the developer, not patched blindly.

## Usage

```text
/spec-fix <short-name>                     # process every FAIL + WARN in the latest audit
/spec-fix <short-name> --date YYYY-MM-DD   # target a specific audit file if multiple exist
/spec-fix <short-name> --only FAIL         # skip WARN — patch hard failures only
/spec-fix <short-name> --only WARN         # patch WARN only (rare)
/spec-fix <short-name> --dry-run           # print the patch plan without writing anything
/spec-fix <short-name> --include <pattern> # only action items whose body matches the regex
/spec-fix <short-name> --exclude <pattern> # skip action items whose body matches the regex
```

Examples:

- `/spec-fix player-keybinding-remapping`
- `/spec-fix background-thumbnail-preload --only FAIL`
- `/spec-fix vr-hand-tracking --date 2026-04-24 --dry-run`

Contract: the audit report at `PLAN/spec_<short-name>__audit_<YYYY-MM-DD>.md` must exist and be produced by `/spec-check`. If no audit exists, abort with a message suggesting `/spec-check <short-name>` first.

---

## What `/spec-fix` Will Do Automatically

A fix is **auto-applicable** iff it is purely mechanical — no logic, no design, no naming decisions. The categories below are the only ones the skill touches:

| Action category | Example FAIL / WARN | Auto fix |
|-----------------|--------------------|----------|
| Missing dev log entry | `dev/CHANGELOG.md` has no line for `path/to/File.kt` | Run `.\scripts\add_to_dev_log.ps1 "<path>" "<target>" "<description>"` using the step's intent as description. |
| Trilingual string gap | `values-ru/strings.xml` missing key present in `values/strings.xml` | Add the key with the EN value placeholder `<!-- TODO translate: <EN text> -->` plus a warning in the fix log. Never invent a translation. |
| Stale catalog entry | `dev/CATALOG/<module>.jsonl` missing a newly-added class | Run `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module <app_v2|wear>` to refresh auto-fields. Record that manual `role`/`status` still need `set.ps1`. |
| Forbidden `Log.d()` on a single line | 1–3 hits of `Log\.d\(` in a touched file | Replace with `Timber.d(` and ensure `import timber.log.Timber` is present. |
| Missing `import timber.log.Timber` | new file uses Timber but import missing | Add import in canonical position. |
| INDEX counter drift (tactical) | `Phases: 3/5 done` but 4 phases have `✅ Done` | Recompute counter from phase statuses and overwrite. |
| INDEX row status drift | row says `🚧 In Progress` but phase header says `✅ Done` | Update INDEX row to match phase header. |
| Missing `Audit:` pointer line in spec | strategic spec moved to `Partial`/`Broken` without link | Insert `**Audit:** see ..` line under `Status:`. |
| Missing FEATURES bullet (single file) | EN has bullet, RU/UK missing | Mirror the EN bullet into `_RU.md` and `_UK.md` as a placeholder with the TODO translate comment. |
| Orphan `TODO(phase-NN)` markers | grep for `TODO(phase-<NN>)` returns hits after Phase NN flipped to Done | List them — the skill does NOT auto-delete. Record as follow-up. |

Everything not in this table becomes a **follow-up** entry in the fix log (see below). The skill never:

- Changes method bodies, class signatures, data models, SQL, or control flow.
- Invents translated strings.
- Bumps Room `@Database(version = ..)` or adds `Migration` objects.
- Renames classes, files, or identifiers.
- Creates new Kotlin/Java source files from scratch.
- Edits specs (`/spec-update` does that).
- Edits spec `Status:` fields (`/spec-check` does that).

---

## Process

When this command is invoked with `$ARGUMENTS`:

**Step 1 — Locate the target audit report.**

- Parse `<short-name>` and flags.
- Glob `PLAN/spec_<short-name>__audit_*.md`. If multiple: pick the most recent date unless `--date` was given.
- Abort if no audit exists — suggest `/spec-check <short-name>`.
- Read the report; confirm the `Outcome:` is `Partial` or `Broken`. If `Verified`, report "Already Verified — nothing to fix" and exit.

**Step 2 — Parse §7 Action Items.**

- Each numbered item has an origin tag (`[FAIL § 3.2.2 — Step 02.3]`), a short description, and an implicit fix.
- Classify each into one of:
  - **auto** — maps to an action category in the table above.
  - **manual** — requires developer attention (logic change, missing class, design decision).
  - **skipped** — excluded by `--only` / `--include` / `--exclude`.

**Step 3 — Dry-run preview.**

Before any modification, print (to chat) a plan table:

| # | Origin | Classification | Planned action | Files |
|---|--------|:--------------:|----------------|-------|
| 1 | [FAIL §3.2.2 — Step 02.3] | manual | follow-up: missing `@Inject` on constructor | `path/Foo.kt` |
| 2 | [FAIL §3.2.3 — Phase 02] | auto | add dev log entry | `dev/CHANGELOG.md` + `path/File3.kt` |
| 3 | [WARN §2.3] | manual | follow-up: open research §6.1 | — |

If `--dry-run`, stop here. Otherwise continue to Step 4.

**Step 4 — Apply auto fixes one category at a time.**

Deterministic order (a later category can depend on an earlier one):

1. Catalog regeneration — single `scan.ps1` invocation touches many files; run first so subsequent checks see fresh state.
2. Trilingual string mirrors — add missing keys with TODO placeholders.
3. FEATURES trilingual bullets — mirror placeholders.
4. `Log.d` → `Timber.d` rewrites + import additions.
5. INDEX counter / status drift corrections.
6. Spec `Audit:` pointer line insertion.
7. Dev log entries — runs LAST, after all file edits, so every modified file gets logged in a single pass.

For each auto fix:

- Confirm the precondition still holds (re-Read the file / re-Grep — state may have changed since the audit).
- Apply the minimal edit via `Edit` / `Write`.
- Record: action, file, line, outcome. Use the fix-log table (see Step 6).

**Step 5 — Record manual fixes as follow-ups.**

Every `manual` classification becomes a row in the fix log's "Manual Follow-ups" section with:

- Origin tag (copied verbatim from audit).
- Why auto-fix is unsafe (one sentence).
- Suggested next action for the developer.
- File(s) to touch.

Do NOT attempt to patch manual items even if they look simple. Rule of thumb: if the fix requires reading surrounding code or choosing a name, it is manual.

**Step 6 — Write the fix log** to `PLAN/spec_<short-name>__fix_<YYYY-MM-DD>.md` using the template below. Suffix `_2`, `_3`, .. on collision.

**Step 7 — Annotate the audit report.**

Reopen the audit file. For each §7 Action Item the skill handled:

- Prepend `[FIXED]` to the item's origin tag if fully resolved (auto path applied).
- Prepend `[PARTIAL]` if the auto fix mitigated but manual work remains (e.g. trilingual mirror added with TODO translate).
- Prepend `[FOLLOW-UP]` if the item became a manual follow-up.
- Leave untouched items alone.

Do not rewrite other sections of the audit report.

**Step 8 — Run the dev log command.**

```powershell
.\scripts\add_to_dev_log.ps1 "PLAN/spec_<short-name>__fix_<YYYY-MM-DD>.md" "spec-fix" "Fix-up run for <short-name>"
.\scripts\add_to_dev_log.ps1 "PLAN/spec_<short-name>__audit_<DATE>.md" "spec-fix" "Annotate audit with FIXED/PARTIAL/FOLLOW-UP tags"
# plus one line per source file the skill actually modified
```

**Step 9 — Summarise to the user** (Russian): number of auto-applied fixes, number of manual follow-ups with one-sentence summary of each, and recommend `/spec-check <short-name>` to confirm.

---

## Fix Log Template

```markdown
# Spec Fix Run: <short-name>

**Source audit:** [`spec_<short-name>__audit_<YYYY-MM-DD>.md`](spec_<short-name>__audit_<YYYY-MM-DD>.md)
**Fix date:** <YYYY-MM-DD>
**Run mode:** full | --only FAIL | --only WARN | --dry-run
**Auto-applied:** N
**Manual follow-ups:** N
**Skipped (filters):** N

---

## 1. Auto-applied Fixes

| # | Origin | Category | Action | Files | Outcome |
|---|--------|----------|--------|-------|:-------:|
| 1 | [FAIL §3.2.3 — Phase 02] | dev log | add entry for `path/File3.kt` | `dev/CHANGELOG.md` | ✅ |
| 2 | [FAIL §3.2.2 — Step 01.4] | Log.d→Timber | replaced 2 hits, import added | `path/Foo.kt` | ✅ |
| 3 | [WARN §3.1] | INDEX counter | 3/5 → 4/5 | `PLAN/spec_<name>/INDEX.md` | ✅ |
| .. | .. | .. | .. | .. | .. |

---

## 2. Manual Follow-ups

<Every row here is work for a human. `/spec-fix` never attempts these.>

### Follow-up 1 — [FAIL §3.2.2 — Step 02.3]

- **What the audit said:** Missing `@Inject` on `FooManager` constructor at `path/Foo.kt`.
- **Why not auto-fixed:** Adding constructor injection requires deciding which scope / qualifier to use and may ripple through the Hilt module graph. Needs human review.
- **Suggested next action:** Add `@Inject constructor(..)` to `FooManager`; if scoping unclear, read `app_v2/src/main/java/com/sza/fastmediasorter/di/AppModule.kt` for the nearest precedent.
- **Files:** `path/Foo.kt`, possibly `di/AppModule.kt`.

### Follow-up 2 — [WARN §2.3]

- **What the audit said:** Strategic §6.1 research item still `Status: Open`.
- **Why not auto-fixed:** Resolving research is a decision, not a refactor.
- **Suggested next action:** Either resolve the question (update strategic §6.1 to `Status: Resolved` with findings) or add an ADR (strategic §9) explaining the trade-off chosen. Consider `/spec-update <short-name>` once resolved.
- **Files:** `PLAN/spec_<short-name>.md`.

<Repeat for every manual item.>

---

## 3. Skipped (filter flags)

<If filters were used, list items excluded by them so nothing is silently dropped.>

- [WARN §2.4] — excluded by `--exclude trilingual`.

---

## 4. Precondition Mismatches

<Items where the audit said something was broken but by Step 4 the skill found the state already fixed (e.g. developer patched between audit and fix run). Record as `PRE-RESOLVED` — no action taken, no error raised.>

- [FAIL §3.2.3 — Phase 02] PRE-RESOLVED: dev log entry for `path/File3.kt` is already present (line 2412 of `dev/CHANGELOG.md`). Likely manual fix between audit and this run.

---

## 5. Next Steps

1. Address each follow-up in §2 manually.
2. Run `/spec-check <short-name>` to confirm the audit flips to `Verified` (or see which items remain).
3. If §2 follow-ups exposed gaps in the spec itself rather than the implementation, run `/spec-update <short-name>` to improve the spec before re-auditing.
```

---

## Quality Rules

- **Never modify application code beyond the trivial category list.** If in doubt, record as a follow-up. A false-positive auto-fix is worse than a missed one.
- **Never invent translations.** Placeholders are `<!-- TODO translate: <EN text> -->`. These are always recorded as PARTIAL, never FIXED.
- **Re-verify every precondition before patching.** The audit may be hours or days old; the tree may have changed. If the precondition is gone, record `PRE-RESOLVED`.
- **Dev log entries run last and batch-apply.** One `add_to_dev_log.ps1` invocation per modified file, all at Step 4's final sub-step.
- **Do not touch `Status:` on strategic or tactical specs.** Only `/spec-check` moves those. `/spec-fix` only adds the `**Audit:**` pointer line if it was missing.
- **Do not run the build or tests.** Static edits only. The developer runs `/build` after accepting the fixes.
- **Read-only zones** (`V1/`, `v2_6/`, `spec_v2/`, `dev/archive/`) are never modified. If an action item points there, record as follow-up with "read-only zone — implementation must not live here".
- **One fix log per run.** Collision on date → append `_2`, `_3`, .. Never overwrite a prior fix log.
- **Author style:** `..` not `...`. Russian only in chat summary; the fix log body is English to match audit report language.
- **`--dry-run` is exhaustive.** It must print the complete plan (auto + manual + skipped), produce no file system changes, and exit cleanly. No partial writes.
- **Determinism:** running `/spec-fix` twice with no intervening code changes must be a no-op on the second run (every item becomes PRE-RESOLVED). Test against this mentally before applying a fix.

---

## Failure Modes to Watch

- **Audit staleness:** a multi-day-old audit may have most items already fixed. The precondition re-check in Step 4 prevents bogus edits. Record every PRE-RESOLVED so the reason for inaction is visible.
- **Trilingual placeholder rot:** TODO-translate markers live forever if nobody translates them. Every PARTIAL placeholder row in the fix log must also be echoed to the chat summary so the user sees them immediately.
- **Over-aggressive `Log.d` rewrite:** must NOT rewrite `Log.d` inside third-party samples, commented-out code, or string literals. Only real call sites on executable lines.
- **Catalog scan mass-edit:** the scan script can rewrite many files. If the resulting `git diff` is huge and unrelated to the feature, stop and record the situation as a follow-up rather than committing a noisy change.
- **Feedback loop:** if `/spec-fix` emits placeholders, the next `/spec-check` should NOT flip to `Verified` just because placeholders exist. The audit skill is responsible for treating `TODO translate` as WARN; the fix skill only needs to surface the placeholders clearly.
