# Specification Audit Fix-up

Consume a `/spec-check` audit report and apply mechanical fixes to the repository. Modifies codebase, not specs (`/spec-update` does that).

## Usage

```text
/spec-fix <short-name>
/spec-fix <short-name> --date YYYY-MM-DD
/spec-fix <short-name> --only FAIL
/spec-fix <short-name> --only WARN
/spec-fix <short-name> --dry-run
/spec-fix <short-name> --include <pattern>
/spec-fix <short-name> --exclude <pattern>
```

Requires `PLAN/spec_<short-name>__audit_<YYYY-MM-DD>.md`. Aborts if absent — run `/spec-check <short-name>` first.

---

## Auto-applicable fixes

A fix is auto-applicable iff purely mechanical — no logic, design, or naming decisions.

| Category | Auto fix |
| --- | --- |
| Missing dev log entry | Run `.\scripts\add_to_dev_log.ps1` using the step's intent as description. |
| Trilingual string gap | Add key with `<!-- TODO translate: <EN text> -->` placeholder. Never invent translation. |
| Stale catalog entry | Run `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module <app_v2\|wear>`. Note manual `role`/`status` still need `set.ps1`. |
| `Log.d()` on executable line (1–3 hits) | Replace with `Timber.d(`, add `import timber.log.Timber` if missing. |
| Missing `import timber.log.Timber` | Add import in canonical position. |
| INDEX counter drift | Recompute counter from phase statuses, overwrite. |
| INDEX row status drift | Update row to match phase file header. |
| Missing `**Audit:**` pointer in spec | Insert line under `Status:`. |
| FEATURES bullet missing in RU/UK | Mirror EN bullet as `<!-- TODO translate: <EN> -->` placeholder. |
| Orphan `TODO(phase-NN)` markers | List them — do NOT auto-delete. Record as follow-up. |

Everything else becomes a **manual follow-up**. Never modifies method bodies, class signatures, data models, SQL, or control flow. Never invents translations. Never bumps Room version or adds migrations. Never creates new Kotlin files.

---

## Process

**1 — Locate audit report.**

Glob `PLAN/spec_<short-name>__audit_*.md`. Pick most recent unless `--date` given.
Abort if absent. If `Outcome: Verified` → exit: "Already Verified — nothing to fix."

**2 — Parse §7 Action Items.**

Classify each as `auto` (maps to category table), `manual` (requires dev attention), or `skipped` (filtered by flags).

**3 — Apply auto fixes** (skip if `--dry-run` — print plan and exit).

Deterministic order:

1. Catalog regeneration (`scan.ps1`) — first, so subsequent checks see fresh state.
2. Trilingual string mirrors.
3. FEATURES trilingual bullets.
4. `Log.d` → `Timber.d` rewrites + imports.
5. INDEX counter / status drift corrections.
6. Spec `**Audit:**` pointer line insertion.
7. Dev log entries — last, after all file edits.

Before each fix: re-verify the precondition (audit may be stale). If already fixed → record as `PRE-RESOLVED`, no action.

**4 — Write fix log** to `PLAN/spec_<short-name>__fix_<YYYY-MM-DD>.md` (suffix `_2`, `_3` on collision).

**5 — Annotate audit report.**

For each §7 Action Item: prepend `[FIXED]`, `[PARTIAL]`, or `[FOLLOW-UP]`. Leave untouched items alone.

**6 — Run dev log.**

```powershell
.\scripts\add_to_dev_log.ps1 "PLAN/spec_<short-name>__fix_<YYYY-MM-DD>.md" "spec-fix" "Fix-up run"
.\scripts\add_to_dev_log.ps1 "PLAN/spec_<short-name>__audit_<DATE>.md" "spec-fix" "Annotate audit"
# plus one line per modified source file
```

**Chat output:** `Auto-fixed: N. Follow-ups: N — [title1, title2, ..]. Run /spec-check <short-name> to confirm.`

---

## Fix Log Template

```markdown
# Spec Fix Run: <short-name>

**Source audit:** `spec_<short-name>__audit_<YYYY-MM-DD>.md`
**Fix date:** <YYYY-MM-DD>
**Mode:** full | --only FAIL | --only WARN | --dry-run
**Auto-applied:** N
**Manual follow-ups:** N

---

## 1. Auto-applied Fixes

| # | Origin | Category | Files | Outcome |
|---|--------|----------|-------|:-------:|
| 1 | [FAIL §3.2.3] | dev log | `dev/CHANGELOG.md` | ✅ |

---

## 2. Manual Follow-ups

### Follow-up 1 — [FAIL §3.2.2 — Step 02.3]

- **What the audit said:** <verbatim>
- **Why not auto-fixed:** <one sentence>
- **Suggested next action:** <concrete step + files>

---

## 3. Skipped (filter flags)

- [WARN §2.4] — excluded by `--exclude trilingual`.

---

## 4. PRE-RESOLVED

- [FAIL §3.2.3] — dev log entry for `path/File.kt` already present. Fixed between audit and this run.

---

## 5. Next Steps

1. Address manual follow-ups.
2. Run `/spec-check <short-name>` to confirm Verified.
```

---

## Constraints

- Never modify application code beyond the category table.
- Never invent translations — only `<!-- TODO translate: <EN text> -->` placeholders.
- Re-verify every precondition before patching — audit may be hours old.
- Dev log entries run last, batch-applied.
- Never touch `Status:` on specs — only `/spec-check` moves those.
- Never run the build or tests — static edits only.
- Read-only zones never modified: `V1/`, `v2_6/`, `spec_v2/`, `dev/archive/`.
- One fix log per run. Never overwrite a prior fix log.
- `--dry-run`: print complete plan (auto + manual + skipped), no writes, exit.
- Running twice with no intervening changes must be a no-op (all items PRE-RESOLVED).
