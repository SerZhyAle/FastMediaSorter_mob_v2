# Phase 04 — Docs & catalog cleanup

**Strategic spec:** [`../S0197_threads-ig-data-sjs-extractor.md`](../S0197_threads-ig-data-sjs-extractor.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** —
**Steps done:** 2 / 2
**Started:** 2026-05-14
**Completed:** 2026-05-14

---

## Objective

Regenerate the class catalog and record dev-log entries for every file modified in Phases 01–03. `docs/FEATURES.md` is intentionally not touched — strategic §8 says "Без изменений", and this ticket fixes an existing failure path, not introduces a new user-visible feature.

---

## Prerequisites

- [ ] Phase 01, Phase 02, Phase 03 are all ✅ Done.
- [ ] Working tree contains the changes from those phases (not yet committed is fine).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Modified (auto) | unbounded |
| `dev/CATALOG/app_v2.md` | Modified (auto) | unbounded |

---

## Steps

### Step 04.1 — Regenerate the app_v2 catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** — start of phase

**Prompt for developer:**

> Run `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`. Then run `pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2`. Both files should refresh auto-fields without dropping any manual `role`/`status` annotations on existing entries.

**Verification:**

- `Bash` — `git diff --name-only` includes `dev/CATALOG/app_v2.jsonl` and `dev/CATALOG/app_v2.md`.
- `Grep` — `StructuredMediaSniffer` matches in `dev/CATALOG/app_v2.md` (sanity that scan picked up touched files).

**Status:** `[x] done`

**Step Log:**

- 2026-05-14 — Verification 2/2 PASS. Catalog regen scanned 1042 files; render produced `dev/CATALOG/app_v2.md` with 2 StructuredMediaSniffer hits.

---

### Step 04.2 — Record dev-log entries for every modified `.kt` file

**Files:** `dev/CHANGELOG.md` (via script — do not edit by hand)
**Depends on:** Step 04.1

**Prompt for developer:**

> For each Kotlin file touched in Phases 01–03, append a `dev/CHANGELOG.md` entry via `.\scripts\add_to_dev_log.ps1 "<relative path>" "spec-dev" "S0197: <one-line description>"`. The file list:
>
> - `app_v2/src/main/java/com/sza/fastmediasorter/data/link/auth/KnownAuthResources.kt`
> - `app_v2/src/main/java/com/sza/fastmediasorter/data/link/InvisibleWebViewExtractionStrategy.kt`
> - `app_v2/src/main/java/com/sza/fastmediasorter/data/link/HtmlPageExtractionStrategy.kt`
> - `app_v2/src/main/java/com/sza/fastmediasorter/data/link/CandidateSelectionPolicy.kt`
> - `dev/CATALOG/app_v2.jsonl`
> - `dev/CATALOG/app_v2.md`

**Verification:**

- `Grep` — `S0197` matches at least 6 times in `dev/CHANGELOG.md` (entries may use `S0197 Phase X.Y:` or `S0197 :` formats; the bare token is what matters).
- `Grep` — last 60 lines of `dev/CHANGELOG.md` (read via `Read` with `offset`) include the four `.kt` paths above.

**Status:** `[x] done`

**Step Log:**

- 2026-05-14 — Verification 2/2 PASS. 15 S0197 dev-log entries recorded across phases 01-04 (per-step logs during spec-dev run).

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] No further modifications pending — working tree contains only the spec-driven changes.

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate. After this phase the ticket should be ready for `/spec-check S0197` (which transitions Status to `Verified`/`Partial`/`Broken`).

---

## Rollback Plan

Catalog regen and dev-log entries are non-destructive — rollback consists of reverting the prior phases' code changes; catalog/dev-log entries can simply be re-generated against the rolled-back state.
