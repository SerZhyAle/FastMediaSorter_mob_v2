# Specification Implementation Audit

Audit a spec against the actual repository state. Auto-detects strategic vs tactical scope.

## Usage

```text
/spec-check <short-name>
/spec-check <short-name> --strategic
/spec-check <short-name> --tactical
/spec-check <short-name> --phase <NN>
/spec-check <short-name> --phases <01,03,05>
/spec-check <short-name> --strict        # treat WARN as FAIL
/spec-check <short-name> --quick         # skip grep-heavy invariants
```

Report: `PLAN/spec_<short-name>__audit_<YYYY-MM-DD>.md` (suffix `_2`, `_3` on same-day collision).

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

Check `PLAN/spec_<short-name>.md` exists — abort if not.
Check `PLAN/spec_<short-name>/INDEX.md` — record presence.
Apply auto-detection table.

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

**4 — Score.**

- `Verified` — every check is PASS, MANUAL, or EXEMPT. Zero WARN and FAIL.
- `Partial` — zero FAIL, ≥1 WARN. Collapses to `Broken` under `--strict`.
- `Broken` — ≥1 FAIL.

**5 — Write audit report** to `PLAN/spec_<short-name>__audit_<YYYY-MM-DD>.md` (template below).

**6 — Update `Status:` fields.**

Full/strategic mode: flip strategic spec `Status:` to score. If Broken/Partial, add:

```markdown
**Audit:** see `PLAN/spec_<short-name>__audit_<YYYY-MM-DD>.md`
```

Tactical-only mode: update INDEX `Status:` + audited phase rows + phase headers. Do not touch strategic `Status:`.

**7 — Run dev log.**

```powershell
.\scripts\add_to_dev_log.ps1 "PLAN/spec_<short-name>__audit_<YYYY-MM-DD>.md" "spec-check" "Audit <short-name>"
.\scripts\add_to_dev_log.ps1 "PLAN/spec_<short-name>.md" "spec-check" "Status → <score>"
# plus one line per updated phase / INDEX
```

**Chat output:** `<score>. PASS/WARN/FAIL: N/N/N. Top issues: [list]. Next: /spec-fix <short-name>`

---

## Audit Report Template

```markdown
# Spec Audit: <short-name>

**Strategic spec:** [`spec_<short-name>.md`](spec_<short-name>.md)
**Tactical plan:** [`spec_<short-name>/INDEX.md`](spec_<short-name>/INDEX.md)
**Audit date:** <YYYY-MM-DD>
**Mode:** full | strategic | tactical | phase-<NN>
**Flags:** strict | quick | —
**Outcome:** Verified | Partial | Broken

---

## 1. Summary

| Metric | Count |
|--------|------:|
| Checks total | N |
| PASS | N |
| WARN | N |
| FAIL | N |
| MANUAL | N |
| EXEMPT | N |

<One-paragraph verdict.>

---

## 2. Strategic Audit

### 2.1 Goals Coverage (§2)

| # | Goal | Referenced in phase(s) | Status | Action |
|---|------|------------------------|:------:|--------|

### 2.2 Constraints (§3.2)

| # | Constraint | Verification | Status | Evidence | Action |
|---|-----------|--------------|:------:|----------|--------|

### 2.3 Open Research Items (§6)

- **WARN** — §6.1 "<title>" still `Status: Open`.

### 2.4 User-Facing Text (§8)

| Artefact | Status | Evidence | Action |
|---------|:------:|----------|--------|
| `docs/FEATURES.md` | PASS/FAIL | line N | — |
| `docs/FEATURES_RU.md` | .. | .. | .. |
| `docs/FEATURES_UK.md` | .. | .. | .. |

### 2.5 Completion Criteria (§11)

- [ ] <criterion 1>
- [ ] <criterion 2>

---

## 3. Tactical Audit

### 3.1 INDEX Consistency

| Check | Status | Evidence | Action |
|-------|:------:|----------|--------|
| Phase counter matches statuses | PASS/FAIL | 3/5 vs 4/5 | "Bump to 4/5" |
| Phase-file headers match INDEX rows | PASS/FAIL | .. | .. |
| Pre-Implementation Blockers all ticked | PASS/WARN | 1 unchecked | .. |

### 3.2 Phase NN — <Title>

**Outcome:** Verified | Partial | Broken

#### 3.2.1 Files Touched

| File | Expected | Exists? | Lines vs budget | Status |
|------|---------|:-------:|:---------------:|:------:|

#### 3.2.2 Steps

| # | Step | Claimed | Verification | Outcome | Evidence | Action |
|---|------|:-------:|--------------|:-------:|----------|--------|

#### 3.2.3 Phase Done Criteria

| Criterion | Status | Evidence | Action |
|-----------|:------:|----------|--------|

---

## 4. Cross-Reference Checks

- Goal §2.X (strategic) ↔ phase(s) implementing it — PASS/MISSING.
- ADR §9.X ↔ phases applying it — PASS/MISSING.

---

## 5. Manual Acceptance Signals

- [ ] <signal from §11>
- [ ] <Build passes — from phase Done Criteria>

---

## 6. Action Items (FAIL + WARN, priority order)

1. **[FAIL §3.2.2 — Step 02.3]** <description> — <concrete fix>.
2. **[FAIL §3.2.3 — Phase 02]** <description> — <concrete fix>.
3. **[WARN §2.3]** <description> — <concrete fix>.
```

---

## Constraints

- Never mutate spec content beyond `Status:` and the `**Audit:**` pointer line.
- Strategic audit is qualitative (keyword overlap for goal coverage). Tactical audit is strict (static predicates).
- A grep miss is FAIL. Hit-count mismatch (expected 1, found 3) is WARN with all hits listed.
- Never run `./gradlew` or build commands — static analysis only.
- Read-only zones ignored: `V1/`, `v2_6/`, `spec_v2/`, `dev/archive/`.
- `--quick` skips grep-heavy invariants (forbidden-call checks, trilingual greps) — annotates summary.
- Never approve `Verified` if any tactical phase is Broken.
- Grep hits on declaration lines only — not comments or string literals.
