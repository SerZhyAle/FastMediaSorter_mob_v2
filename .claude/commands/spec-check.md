# Specification Implementation Audit

Audit a spec against the actual repository state. Auto-detects strategic vs tactical scope.

> **No audit file is written.** Findings are recorded in a compact `## Last Audit` block at the bottom of the strategic spec. The block is **overwritten** on each run — only the most recent audit is preserved. The journal `updated` timestamp moves on every audit. Old audit history is intentionally discarded.

## Usage

```text
/spec-check <Sxxxx-or-slug>
/spec-check <Sxxxx-or-slug> --strategic
/spec-check <Sxxxx-or-slug> --tactical
/spec-check <Sxxxx-or-slug> --phase <NN>
/spec-check <Sxxxx-or-slug> --phases <01,03,05>
/spec-check <Sxxxx-or-slug> --strict        # treat WARN as FAIL
/spec-check <Sxxxx-or-slug> --quick         # skip grep-heavy invariants
```

---

## Auto-detection

| Strategic file | Tactical folder | Flag | Mode |
| :---: | :---: | --- | --- |
| exists | exists | none | **full** — strategic + every phase |
| exists | missing | none | strategic only |
| exists | exists | `--strategic` | strategic only |
| exists | exists | `--tactical` | every phase |
| exists | exists | `--phase NN` | single phase |
| missing | any | any | abort |

---

## Process

**1 — Parse arguments, locate spec.**

Resolve `Sxxxx` and slug via `select.ps1`. Check `PLAN/Sxxxx_<slug>.md` exists — abort if not. Check `PLAN/Sxxxx_<slug>/INDEX.md` — record presence. Apply auto-detection table.

**2 — Extract verification contract.**

Strategic: parse §2 Goals, §3.2 Constraints, §6 Research items, §8 FEATURES text, §11 Criteria.
Tactical: parse INDEX Phase Overview, Blockers, Completion Gate; each phase's Files Touched, Steps Verification, Done Criteria.

**3 — Run checks. Record outcome per check:**

`PASS` / `WARN` / `FAIL` / `MANUAL` / `UNCHECKABLE` / `EXEMPT`

Verification mechanics:

| Check | How |
| --- | --- |
| File exists | `Glob` with exact path |
| Class/function declared | `Grep` for `class <Name>` / `fun <name>` — verify hit is declaration, not comment/string |
| No forbidden call | `Grep` for pattern; PASS iff zero hits |
| String resource present | `Grep` for `name="<key>"` in all three `values/strings.xml` files |
| Room version | Read `AppDatabase.kt`, match `@Database(version = N` |
| Dev log entry | `Grep` for file path in `dev/CHANGELOG.md` |
| Catalog up-to-date | `Grep` for class name in `dev/CATALOG/<module>.jsonl` |
| FEATURES trilingual | `Grep` for keyword in all three FEATURES docs — PASS only if all three hit |
| File size vs budget | `Read` file, count lines, compare to step budget |
| Flavor gating | `Grep` for `BuildConfig.<FLAG>` if §3.2 names a flag |
| Step status consistency | Parse `[x] done` in phase file; cross-check Verification predicates |
| Phase status consistency | INDEX row status == phase `Status:` header |
| Debug-tag invariant | If current journal status is `BlockNeedUserTest`: `Grep` for `Timber.d("<Sxxxx>:` across `.kt` — PASS iff ≥1 hit, FAIL if none (spec lost its device-test probe). For any other status: PASS iff zero hits — surviving tags are stale (WARN, list them; the verdict flip in step 6 will delete them). |

**4 — Score.**

- `Verified` — every check is PASS, MANUAL, or EXEMPT. Zero WARN and FAIL.
- `Partial` — zero FAIL, ≥1 WARN. Collapses to `Broken` under `--strict`.
- `Broken` — ≥1 FAIL.

**5 — Write `## Last Audit` block** at the bottom of `PLAN/Sxxxx_<slug>.md` (overwrite if present). The block uses the compact template below — keep it under ~40 lines, including only PASS counts and FAIL/WARN action items. No verbose tables. Action items are the input that `/spec-fix` consumes.

**6 — Update `Status:` fields.**

Full/strategic mode: flip strategic spec `Status:` to score (`Verified` / `Partial` / `Broken`). Always touch the journal status via `update.ps1`.

Whenever the verdict flips the journal/strategic status (full or strategic mode), enforce the debug-tag invariant: the spec is no longer `BlockNeedUserTest`, so it must carry zero `Timber.d("<Sxxxx>:` tags. `Grep` all `.kt` for `Timber.d("<Sxxxx>:` and delete every matching line (idempotent no-op if none). Run a dev log line per `.kt` file that lost a tag. See CLAUDE.md "Debug Verification Tags". This is the only `.kt` mutation `/spec-check` performs.

Tactical-only mode: update INDEX `Status:` + audited phase rows + phase headers. Do not touch strategic `Status:`. Do not touch debug tags.

**7 — Run dev log.**

```powershell
.\scripts\add_to_dev_log.ps1 "PLAN/Sxxxx_<slug>.md" "spec-check" "Audit <Sxxxx> → <score>; PASS/WARN/FAIL N/N/N"
# plus one line per modified phase / INDEX
```

**8 — Auto-chain to `/spec-fix`.**

If outcome is `Partial` or `Broken` — immediately invoke `/spec-fix <Sxxxx>` to apply all mechanical fixes. If outcome is `Verified` — no further action needed.

**Chat output:** `<Sxxxx>: <score>. PASS/WARN/FAIL: N/N/N. Debug tags removed: N. Top issues: [list]. → Running /spec-fix…` (or `→ All checks passed. Debug tags removed: N.` on Verified)

---

## `## Last Audit` block — compact template

```markdown
## Last Audit

**Date:** <YYYY-MM-DD>
**Mode:** full | strategic | tactical | phase-<NN>
**Flags:** strict | quick | —
**Outcome:** Verified | Partial | Broken
**Counts:** PASS N · WARN N · FAIL N · MANUAL N · EXEMPT N

### Action items

1. **[FAIL §3.2.2 — Step 02.3]** <one line> — <concrete fix>.
2. **[FAIL §3.2.3 — Phase 02 Done]** <one line> — <concrete fix>.
3. **[WARN §2.3]** <one line> — <concrete fix>.

### Manual / on-device

- [ ] <signal from §11>
- [ ] <build / device check>

<If `Verified`: drop the "Action items" section, keep "Manual / on-device" only.>
```

The block replaces the previous `## Last Audit` block in full. The rest of the strategic spec (§1..§12) is untouched.

---

## Constraints

- Never mutate spec content beyond `Status:`, `**Priority:**` (only when explicitly recomputed), and the `## Last Audit` block.
- **Debug-tag removal exception:** when the verdict flips the journal/strategic status, delete every `Timber.d("<Sxxxx>:` line from `.kt` (CLAUDE.md "Debug Verification Tags"). This is the only `.kt` mutation this skill performs. Never delete tags in tactical-only / `--quick`-without-status-flip runs. Never delete a tag while the journal status is staying at `BlockNeedUserTest`.
- **Never write `PLAN/Sxxxx_<slug>__audit_*.md` or any other file in `PLAN/` to record audit findings.** All findings live inline in the spec.
- Strategic audit is qualitative (keyword overlap for goal coverage). Tactical audit is strict (static predicates).
- A grep miss is FAIL. Hit-count mismatch (expected 1, found 3) is WARN with all hits listed.
- Never run `./gradlew` or build commands — static analysis only.
- Read-only zones ignored: `V1/`, `v2_6/`, `spec_v2/`, `dev/archive/`.
- `--quick` skips grep-heavy invariants (forbidden-call checks, trilingual greps) — annotates the block.
- Never approve `Verified` if any tactical phase is Broken.
- Grep hits on declaration lines only — not comments or string literals.

---

## Spec Catalog hooks

- **Argument resolution.** First positional argument is `Sxxxx` (preferred) or a slug. If slug, resolve via `pwsh -File scripts/spec_catalog/select.ps1 -Name "<slug>" -Format json`.
- **Status transition** (after the audit verdict is final):
  - Verdict `Verified` → `pwsh -File scripts/spec_catalog/close.ps1 -Id <Sxxxx> -Status Verified`. (`close.ps1` also stamps `closed_at` on the record.)
  - Verdict `Partial`  → `pwsh -File scripts/spec_catalog/update.ps1 -Id <Sxxxx> -Status Partial`.
  - Verdict `Broken`   → `pwsh -File scripts/spec_catalog/update.ps1 -Id <Sxxxx> -Status Broken`.
- **Debug tags.** Every verdict that flips the journal status away from `BlockNeedUserTest` (Verified / Partial / Broken — and trivially also from `Implemented`, where there are none) requires the grep-and-delete of `Timber.d("<Sxxxx>:` lines from `.kt` (Process step 6). This holds in `--quick` mode too.
- **Read-only mode (`--quick`):** still emits the status transition and the debug-tag removal above; the difference is in scope of audit checks, not in journal effect.
- **Forbidden:** never write to `PLAN/spec-catalog.jsonl` directly; never demote a `Verified` ticket back to `Implemented` — only `/spec-fix` followed by `/spec-check` can change the verdict. Never create audit files in `PLAN/`.
