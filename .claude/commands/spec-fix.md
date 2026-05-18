# Specification Audit Fix-up

Apply mechanical fixes flagged by the latest audit. Modifies the codebase, not the spec body - `/spec-update` does that.

> **No fix file is written.** This skill reads the `## Last Audit` block of `PLAN/Sxxxx_<slug>.md`, applies the auto-fixes, and annotates each action item in place (`[FIXED]` / `[PARTIAL]` / `[FOLLOW-UP]` / `[PRE-RESOLVED]`). The journal `updated` timestamp moves on every run. Old fix-log files are abolished.

## Usage

```text
/spec-fix <Sxxxx-or-slug>
/spec-fix <Sxxxx-or-slug> --only FAIL
/spec-fix <Sxxxx-or-slug> --only WARN
/spec-fix <Sxxxx-or-slug> --dry-run
/spec-fix <Sxxxx-or-slug> --include <pattern>
/spec-fix <Sxxxx-or-slug> --exclude <pattern>
```

Requires a `## Last Audit` block in `PLAN/Sxxxx_<slug>.md`. Aborts if absent - run `/spec-check <Sxxxx>` first.

---

## Auto-applicable fixes

A fix is auto-applicable iff purely mechanical - no logic, design, or naming decisions.

| Category | Auto fix |
| --- | --- |
| Missing dev log entry | Run `.\scripts\add_to_dev_log.ps1` using the step's intent as description. |
| Trilingual string gap | Add key with `<!-- TODO translate: <EN text> -->` placeholder. If this is stored as an XML comment rather than a `<string>` body, manual XML edit is allowed because `set-android-string.ps1` only writes string values. Never invent translation. |
| Stale catalog entry | Run `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module <app_v2\|wear>`. Note manual `role`/`status` still need `set.ps1`. |
| `Log.d()` on executable line (1–3 hits) | Replace with `Timber.d(`, add `import timber.log.Timber` if missing. |
| Missing `import timber.log.Timber` | Add import in canonical position. |
| Stale `Timber.d("S\d{4}:` debug tag | Delete the line if the tag's spec is not currently `BlockNeedUserTest` (resolve via `select.ps1 -Id <Sxxxx> -Format json`). Applies to the audited spec's own tags and to any such tag found in a `.kt` file already being edited for another fix. Never delete a tag whose spec is `BlockNeedUserTest`. See CLAUDE.md "Debug Verification Tags". |
| INDEX counter drift | Recompute counter from phase statuses, overwrite. |
| INDEX row status drift | Update row to match phase file header. |
| FEATURES bullet missing in RU/UK | Only if strategic §8 contains a FEATURES sentence (not "Без изменений"). Mirror EN bullet as `<!-- TODO translate: <EN> -->` placeholder. |
| Orphan `TODO(phase-NN)` markers | List them - do NOT auto-delete. Record as follow-up. |

Everything else becomes a **manual follow-up**. Never modifies method bodies, class signatures, data models, SQL, or control flow. Never invents translations. Never bumps Room version or adds migrations. Never creates new Kotlin files.

---

## Process

**1 - Locate audit block.**

Read `PLAN/Sxxxx_<slug>.md`. Locate `## Last Audit`. Abort if absent. If `Outcome: Verified` → exit: "Already Verified - nothing to fix."

**2 - Parse Action items.**

Classify each as `auto` (maps to category table), `manual` (requires dev attention), or `skipped` (filtered by flags).

**3 - Apply auto fixes** (skip if `--dry-run` - print plan and exit).

Deterministic order:

1. Catalog regeneration (`scan.ps1`) - first, so subsequent checks see fresh state.
2. Trilingual string mirrors.
3. FEATURES trilingual bullets.
4. `Log.d` → `Timber.d` rewrites + imports.
5. Stale `Timber.d("S\d{4}:` debug-tag deletions (spec not `BlockNeedUserTest`).
6. INDEX counter / status drift corrections.
7. Dev log entries - last, after all file edits.

Before each fix: re-verify the precondition (block may be stale). If already fixed → mark `[PRE-RESOLVED]`, no action.

**4 - Annotate the `## Last Audit` block in place.**

For each action item: prepend `[FIXED]`, `[PARTIAL]`, `[FOLLOW-UP]`, `[PRE-RESOLVED]`, or `[SKIPPED]` exactly once. Do not rewrite the rest of the block. Do not add new sections.

**5 - Run dev log + finalize (batched).**

`/spec-fix` does not flip the journal status (that is `/spec-check`'s job), but it does touch `updated` and may need to add a functionality log entry. Use `close-and-log.ps1` with the spec's current status so the journal timestamp moves without a status change:

```powershell
# Read current status first
$cur = (& pwsh -File scripts/spec_catalog/select.ps1 -Id <Sxxxx> -Format json | ConvertFrom-Json).status

pwsh -File scripts/spec_catalog/close-and-log.ps1 `
    -Id <Sxxxx> `
    -Status $cur `
    -StatusOnly `
    -DevLogs @(
        '{"file":"PLAN/Sxxxx_<slug>.md","target":"spec-fix","desc":"Annotate Last Audit (<Sxxxx>)"}'
        # plus one entry per modified source file
      ) `
    -FuncOp <FIX|""> -FuncDesc "<english summary or omit>" `
    -CatalogModule app_v2
```

`-StatusOnly` calls `update.ps1` (touches `updated`) instead of `close.ps1` so no `closed_at` field is set. Pass `-SkipFuncLog` (or omit `-FuncOp`/`-FuncDesc`) for runs that only touch dev log entries, INDEX counter drift, catalog regeneration, or stale debug-tag deletions - none of those affect what the user sees. Skip the call entirely on `--dry-run`.

Individual-call fallback (when `close-and-log.ps1` unavailable): one `add_to_dev_log.ps1` per file + `add_to_functionality_log.ps1 -Id <Sxxxx> -Op FIX -Description "..."` + `update.ps1 -Id <Sxxxx>` (no flags) to touch `updated`.

**6 - Auto-chain to `/spec-check`.**

After at least one fix was applied - immediately invoke `/spec-check <Sxxxx>` to re-audit and update the status. Skip if `--dry-run`.

**Chat output:** `<Sxxxx>: auto-fixed N. Follow-ups: N - [title1, title2, ..]. → Running /spec-check to confirm…`

---

## Constraints

- Never modify application code beyond the category table.
- Never invent translations - only `<!-- TODO translate: <EN text> -->` placeholders.
- If a repo helper script used by the auto-fix is broken or insufficient, fix the script first instead of working around it.
- Prefer `scripts/utils/set-android-string.ps1` when an auto-fix updates or inserts Android `<string>` keys; manual XML edits are only for structural resource changes.
- Re-verify every precondition before patching - the audit block may be hours old.
- Dev log entries run last, batch-applied.
- Never touch `Status:` on specs - only `/spec-check` moves those.
- Never run the build or tests - static edits only.
- Read-only zones never modified: `V1/`, `v2_6/`, `spec_v2/`, `dev/archive/`.
- Annotations are append-style markers in place - never overwrite or delete prior items in the audit block; the next `/spec-check` rewrites the whole block.
- **Never create or read `PLAN/Sxxxx_<slug>__audit_*.md` or `__fix_*.md` files.** They are abolished.
- `--dry-run`: print complete plan (auto + manual + skipped), no writes, exit.
- Running twice with no intervening changes must be a no-op (all items `[PRE-RESOLVED]`).

---

## Spec Catalog hooks

- **Argument resolution.** First positional argument is `Sxxxx` (preferred) or a slug.
- **Status transition.** After at least one fix is applied, touch the journal `updated` timestamp without changing status: `pwsh -File scripts/spec_catalog/update.ps1 -Id <Sxxxx>` (no other flags). On `--dry-run` skip the update.
- **Forbidden:** never set the journal status from this skill - verdict belongs to `/spec-check`. Never write to `PLAN/spec-catalog.jsonl` directly.
