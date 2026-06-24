# Specification Audit Fix-up

Apply mechanical fixes flagged by latest audit. Modifies codebase, not spec body - `/spec-update` does that.

> **No fix file written.** Reads `## Last Audit` block of `PLAN/Sxxxx_<slug>.md`, applies auto-fixes, annotates each action item in place (`[FIXED]` / `[PARTIAL]` / `[FOLLOW-UP]` / `[PRE-RESOLVED]`). Journal `updated` timestamp moves every run. Old fix-log files abolished.

## Usage

```text
/spec-fix <Sxxxx-or-slug>
/spec-fix <Sxxxx-or-slug> --only FAIL
/spec-fix <Sxxxx-or-slug> --only WARN
/spec-fix <Sxxxx-or-slug> --dry-run
/spec-fix <Sxxxx-or-slug> --include <pattern>
/spec-fix <Sxxxx-or-slug> --exclude <pattern>
```

Requires `## Last Audit` block in `PLAN/Sxxxx_<slug>.md`. Aborts if absent - run `/spec-check <Sxxxx>` first.

---

## Auto-applicable fixes

Auto-applicable iff purely mechanical - no logic, design, or naming decisions.

| Category | Auto fix |
| --- | --- |
| Missing dev log entry | Run `.\scripts\add_to_dev_log.ps1` using step's intent as description. |
| Trilingual string gap | Add key with `<!-- TODO translate: <EN text> -->` placeholder. If stored as XML comment rather than `<string>` body, manual XML edit allowed (`set-android-string.ps1` only writes string values). Never invent translation. |
| Stale catalog entry | Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module <app_v2\|wear>` (one-shot scan + render). Manual `role`/`status` still need `set.ps1`. |
| `Log.d()` on executable line (1-3 hits) | Replace with `Timber.d(`, add `import timber.log.Timber` if missing. |
| Missing `import timber.log.Timber` | Add import in canonical position. |
| Stale `Timber.d("S\d{4}:` debug tag | Delete line if tag's spec not currently `BlockNeedUserTest` (resolve via `select.ps1 -Id <Sxxxx> -Format json`). Applies to audited spec's own tags and any such tag in a `.kt` file already being edited for another fix. Never delete a tag whose spec is `BlockNeedUserTest`. See CLAUDE.md "Debug Verification Tags". |
| INDEX counter drift | Recompute counter from phase statuses, overwrite. |
| INDEX row status drift | Update row to match phase file header. |
| FEATURES bullet missing in RU/UK | Only if strategic §8 contains FEATURES sentence (not "Без изменений"). Mirror EN bullet as `<!-- TODO translate: <EN> -->` placeholder. |
| Orphan `TODO(phase-NN)` markers | List them - do NOT auto-delete. Record as follow-up. |

Everything else becomes **manual follow-up**. Never modifies method bodies, class signatures, data models, SQL, or control flow. Never invents translations. Never bumps Room version or adds migrations. Never creates new Kotlin files.

---

## Process

**1 - Locate audit block.**

Read `PLAN/Sxxxx_<slug>.md`. Locate `## Last Audit`. Abort if absent. `Outcome: Verified` -> exit: "Already Verified - nothing to fix."

**2 - Parse Action items.**

Classify each as `auto` (maps to category table), `manual` (requires dev attention), or `skipped` (filtered by flags).

**3 - Apply auto fixes** (skip if `--dry-run` - print plan and exit).

Deterministic order:

1. Catalog regeneration (`catalog_sync.ps1`) - first, so subsequent checks see fresh state.
2. Trilingual string mirrors.
3. FEATURES trilingual bullets.
4. `Log.d` -> `Timber.d` rewrites + imports.
5. Stale `Timber.d("S\d{4}:` debug-tag deletions (spec not `BlockNeedUserTest`).
6. INDEX counter / status drift corrections.
7. Dev log entries - last, after all file edits.

Before each fix: re-verify precondition (block may be stale). Already fixed -> mark `[PRE-RESOLVED]`, no action.

**4 - Annotate `## Last Audit` block in place.**

For each action item: prepend `[FIXED]`, `[PARTIAL]`, `[FOLLOW-UP]`, `[PRE-RESOLVED]`, or `[SKIPPED]` exactly once. Do not rewrite rest of block. Do not add new sections.

**5 - Run dev log + finalize (batched).**

`/spec-fix` does not flip journal status (`/spec-check`'s job), but touches `updated` and may add a functionality log entry. Use `close-and-log.ps1` with spec's current status so timestamp moves without status change:

```powershell
# Read current status first
$cur = (& pwsh -NoProfile -File scripts/spec_catalog/select.ps1 -Id <Sxxxx> -Format json | ConvertFrom-Json).status

pwsh -NoProfile -File scripts/spec_catalog/close-and-log.ps1 `
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

`-StatusOnly` calls `update.ps1` (touches `updated`) instead of `close.ps1` so no `closed_at` set. Pass `-SkipFuncLog` (or omit `-FuncOp`/`-FuncDesc`) for runs touching only dev log entries, INDEX counter drift, catalog regeneration, or stale debug-tag deletions - none affect what user sees. Skip the call entirely on `--dry-run`.

Individual-call fallback (when `close-and-log.ps1` unavailable): one `add_to_dev_log.ps1` per file + `update.ps1 -Id <Sxxxx>` (no flags) to touch `updated`. Feature inventory: only if fix changed a shipped capability's user-visible behaviour, upsert it active via `scripts/all_features/add.ps1 -Id "<area>.<feature>" -Area .. -Name .. -Description ".." -Flavors .. -Spec <Sxxxx>` (inventory has no `FIX` op; pure bug fix captured by dev-log + git, so skip it).

**6 - Auto-chain to `/spec-check`.**

After at least one fix applied - immediately invoke `/spec-check <Sxxxx>` to re-audit and update status. Skip if `--dry-run`.

**Chat output:** `<Sxxxx>: auto-fixed N. Follow-ups: N - [title1, title2, ..]. -> Running /spec-check to confirm…`

---

## Constraints

- Never modify application code beyond category table.
- Never invent translations - only `<!-- TODO translate: <EN text> -->` placeholders.
- Repo helper script used by auto-fix broken or insufficient → fix the script first instead of working around it.
- Prefer `scripts/utils/set-android-string.ps1` when auto-fix updates/inserts Android `<string>` keys; manual XML edits only for structural resource changes.
- Re-verify every precondition before patching - audit block may be hours old.
- Dev log entries run last, batch-applied.
- Never touch `Status:` on specs - only `/spec-check` moves those.
- Never run build or tests - static edits only.
- Read-only zones never modified: `V1/`, `v2_6/`, `spec_v2/`, `dev/archive/`.
- Annotations are append-style markers in place - never overwrite or delete prior items in audit block; next `/spec-check` rewrites whole block.
- **Never create or read `PLAN/Sxxxx_<slug>__audit_*.md` or `__fix_*.md` files.** Abolished.
- `--dry-run`: print complete plan (auto + manual + skipped), no writes, exit.
- Running twice with no intervening changes must be a no-op (all items `[PRE-RESOLVED]`).

---

## Spec Catalog hooks

- **Argument resolution.** First positional argument is `Sxxxx` (preferred) or a slug.
- **Status transition.** After at least one fix applied, touch journal `updated` timestamp without changing status: `pwsh -NoProfile -File scripts/spec_catalog/update.ps1 -Id <Sxxxx>` (no other flags). On `--dry-run` skip the update.
- **Forbidden:** never set journal status from this skill - verdict belongs to `/spec-check`. Never write to `PLAN/spec-catalog.jsonl` directly.
