# Phase 02 — structured-standards

**Strategic spec:** [`../S0140_extend-market-url-coverage.md`](../S0140_extend-market-url-coverage.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, 04
**Steps done:** 3 / 3
**Started:** 2026-05-10
**Completed:** 2026-05-10

---

## Objective

Harvest standard web metadata (JSON-LD and oEmbed) before the generic static HTML sweep so the market pipeline gains cheap coverage without site-specific code.

---

## Prerequisites

- [x] Phase 01 is ✅ Done.
- [x] The generic HTML extraction path remains the owning abstraction.
- [x] No Phase 03 research dependency applies to this slice.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/link/StructuredMediaSniffer.kt` | New | <= 260 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/link/HtmlMediaCandidate.kt` | Modified | <= 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/link/CandidateSelectionPolicy.kt` | Modified | <= 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/link/HtmlPageExtractionStrategy.kt` | Modified | <= 340 |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/link/StructuredMediaSnifferTest.kt` | New | <= 220 |

---

## Steps

### Step 02.1 — Add a dedicated structured-media sniffer

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/link/StructuredMediaSniffer.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create a reusable helper that parses JSON-LD `VideoObject` / `MediaObject` / `ImageObject` blocks and best-effort JSON oEmbed endpoints. Keep failures silent and generic so the existing HTML strategy can fall through naturally.

**Verification:**

- `Glob` — `StructuredMediaSniffer.kt` exists.
- `Grep` — `class StructuredMediaSniffer` matches once.
- `Grep` — `harvestJsonLd` and `harvestOEmbed` both exist.

**Status:** `[x]` done

**Step Log:**

- 2026-05-10 — Verification PASS. File: `StructuredMediaSniffer.kt`. Dev log recorded.

---

### Step 02.2 — Merge structured candidates ahead of static HTML harvest

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/link/HtmlMediaCandidate.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/link/CandidateSelectionPolicy.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/link/HtmlPageExtractionStrategy.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add `JSON_LD` / `OEMBED` source priorities, inject the new sniffer into `HtmlPageExtractionStrategy`, and merge its output before the existing static HTML candidates so structured hints win cheaply when present.

**Verification:**

- `Grep` — `JSON_LD` and `OEMBED` both exist in `HtmlMediaCandidate.kt`.
- `Grep` — `private val structuredMediaSniffer` matches once in `HtmlPageExtractionStrategy.kt`.
- `Grep` — `structured=` appears in the HTML trace line.

**Status:** `[x]` done

**Step Log:**

- 2026-05-10 — Verification PASS. Files: `HtmlMediaCandidate.kt`, `CandidateSelectionPolicy.kt`, `HtmlPageExtractionStrategy.kt`. Dev log recorded.

---

### Step 02.3 — Add focused JVM coverage and validate production compilation

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/data/link/StructuredMediaSnifferTest.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Add a pure-JVM test suite for JSON-LD and oEmbed harvesting with a stubbed OkHttp client. Validate the production slice with touched-file diagnostics and a focused `:app_v2:compileStandardDebugKotlin` run, because the shared `src/test` lane currently contains unrelated pre-existing failures.

**Verification:**

- `Glob` — `StructuredMediaSnifferTest.kt` exists.
- `Get-errors` — no diagnostics in the touched `data/link` main/test files.
- `Command` — `./gradlew.bat :app_v2:compileStandardDebugKotlin` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-05-10 — Verification PASS. Added `StructuredMediaSnifferTest.kt`; touched-file diagnostics clean; `:app_v2:compileStandardDebugKotlin` PASS. Note: `:app_v2:testStandardDebugUnitTest --tests StructuredMediaSnifferTest` is still blocked by unrelated pre-existing `src/test` compilation failures outside S0140. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Touched-file diagnostics report no errors.
- [x] `./gradlew.bat :app_v2:compileStandardDebugKotlin` succeeds.
- [x] Dev log entry added for every file in "Files Touched" via `./scripts/add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

Static coverage is broader now, but SPA pages and soft login-walls still need the dynamic extractor and heuristic settings from Phase 03.

---

## Rollback Plan

Revert the Phase 02 commit(s). No persisted state or schema changes were introduced.