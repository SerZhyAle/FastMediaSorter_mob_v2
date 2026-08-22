# Phase 04 - Docs, registry and gates

**Strategic spec:** [`../S1931_launcher-has-no-icon-slot-on-the-site.md`](../S1931_launcher-has-no-icon-slot-on-the-site.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** none - final phase
**Steps done:** 3 / 3
**Started:** 2026-08-22
**Completed:** 2026-08-22

---

## Objective

Revalidate and regenerate the document registry's derived files, prove no icon or documentation gate needed an exception for the launcher, and close the change through the facade.

---

## Prerequisites

- [x] All phases in "Depends on" are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/DOCS_MAP.md` | Modified (generated) | generated |
| `sitemap.xml` | Modified (generated) | generated |
| `dev/CHANGELOG.md` | Modified (via script) | generated |

---

## Steps

### Step 04.1 - Revalidate and regenerate the document registry

**Files:** `docs/DOCS_MAP.md`, `sitemap.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Run `scripts/document_registry/validate.ps1`, then `generate.ps1`, then `generate.ps1 -Check`, and confirm each exits 0. The three new guide pages fall under the `user-guides` record, which is indexable, so validation fails unless each page declares its own `permalink:` - fix the page rather than the record if it does.

**Why:**

The `user-guides` registry record matches `docs/howto/*.md` and is indexable, so three new pages change what the generated sitemap must announce, and strategic §11 criterion 6 requires no gate to need an exception for the launcher.

**Verification:**

- Exit code - `validate.ps1` returned 0.
- Exit code - `generate.ps1 -Check` returned 0.
- `Grep` - `scenario-launcher-mode` matches in `sitemap.xml` for all three locales.

**Status:** `[x]` done

---

### Step 04.2 - Run the icon and documentation gates

**Files:** none - verification only
**Depends on:** Step 04.1

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/quality/assert-icon-inventory-sync.ps1` and confirm it exits 0, proving the hand-edited map and the generated assets agree without an exception for `ic_launcher_mode`.

**Why:**

Strategic §11 criterion 6 states that no icon or documentation gate may require an exception for the launcher, and this gate is the one that judges drift between the doc-icon map and the generated asset tree.

**Verification:**

- Exit code - `assert-icon-inventory-sync.ps1` returned 0.
- `Grep` - no baseline or allowlist file in `scripts/quality/` names `ic_launcher_mode`.

**Status:** `[x]` done

---

### Step 04.3 - Close the change through the facade

**Files:** `dev/CHANGELOG.md`
**Depends on:** Step 04.2

**Prompt for developer:**

> Close the whole ticket's file set in one call: `pwsh -NoProfile -File scripts/post-change.ps1 -Files "<every file the four phases touched>" -Target "S1931" -Description "Launcher mode gets a landing card and its own guide page in three locales" -ChangeType Doc -ScopeToFile`. Read the verdict line: only a bare `post-change: PASS` is clean, and `PASS WITH ADVISORIES` names what still needs reading.

**Why:**

CLAUDE.md section 12 requires mechanical closure through the facade with the whole changed set named, because naming one file while changing several certifies only the file that was named.

**Verification:**

- Exit code - `post-change.ps1` returned 0.
- Value equality - the verdict line reads `post-change: PASS`, or `PASS WITH ADVISORIES` with every advisory read and accounted for.
- `Grep` - `dev/CHANGELOG.md` carries exactly one new entry naming `S1931`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles - not applicable, no source, resource or build file touched.
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `scripts/post-change.ps1`.
- [x] `dev/CATALOG/<module>.jsonl` regeneration not required - no Kotlin touched.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings (documentation only, no audit trigger fired).

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Revert the earlier phases and re-run `document_registry/generate.ps1`; the two generated files return to their prior content on their own.
