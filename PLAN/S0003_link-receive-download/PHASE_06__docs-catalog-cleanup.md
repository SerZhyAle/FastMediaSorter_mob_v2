# Phase 06 — Docs / Catalog Cleanup

**Strategic spec:** [`../S0003_link-receive-download.md`](../S0003_link-receive-download.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, 02, 03, 04, 05
**Blocks:** none — final phase
**Steps done:** 4 / 4
**Started:** 2026-04-29
**Completed:** 2026-05-01  (Step 06.3 role assignments completed by `/spec-fix S0003` on 2026-05-01)

---

## Objective

Land the user-facing documentation entry across all three locales, regenerate the auto-fields of `dev/CATALOG/app_v2.{jsonl,md}`, fill in the manual `role`/`status` fields for every new class, and emit the closing batch of dev-log entries.

---

## Prerequisites

- [ ] Phases 01–05 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | — |
| `docs/FEATURES_RU.md` | Modified | — |
| `docs/FEATURES_UK.md` | Modified | — |
| `dev/CATALOG/app_v2.jsonl` | Modified | — |
| `dev/CATALOG/app_v2.md` | Modified | — |

---

## Steps

### Step 06.1 — Update `docs/FEATURES.md` and locale mirrors

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** — start of phase

**Prompt for developer:**

> Locate the existing "Share / Receive" section (or equivalent — the same place where `acceptSharedFiles` is documented). Add a single concise bullet (3–4 lines max per locale) describing the new behaviour:
>
> - English (FEATURES.md): paraphrase strategic §8 EN. Mention master toggle, optional destination resource, auto-open in player, fallback to Downloads on resource unavailable.
> - Russian (FEATURES_RU.md): same content in Russian. Use `..` not `...`, use `ё`/`Ё` correctly.
> - Ukrainian (FEATURES_UK.md): same content in Ukrainian. Use `ї`/`є`/`ґ` correctly.
>
> Do not introduce new sections; keep the bullet aligned with existing formatting.

**Verification:**

- `Grep -n "link"` in `docs/FEATURES.md` near the Share/Receive section matches at least once (heuristic — section structure varies).
- `Grep -n "ссылк"` in `docs/FEATURES_RU.md` matches at least once.
- `Grep -n "посиланн"` in `docs/FEATURES_UK.md` matches at least once.
- `Grep -n "\\.\\.\\." docs/FEATURES_RU.md` (literal three dots) returns zero hits in the new bullet (use grep -F).

**Status:** `[ ]` not done

---

### Step 06.2 — Regenerate catalogue auto-fields

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Step 06.1 (cosmetic; ordering is reversible).

**Prompt for developer:**

> Run:
>
> ```powershell
> pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
> pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2
> ```
>
> The scan refreshes auto-fields for the new classes (`UrlExtractionStrategy`, `LinkExtractionRegistry`, `MediaMimeWhitelist`, `DirectFileExtractionStrategy`, `HtmlPageExtractionStrategy`, `HtmlMediaCandidate`, `CandidateSelectionPolicy`, `LinkDownloadWriter`, `LinkAutoDownloadCoordinator`, `LinkAutoDownloadProgressDialog`, `UrlInTextDetector`, `LinkDownloadModule`, `LinkDownloadStrategiesModule`). The render rewrites the human-readable `.md`. Verify both files appear in `git status`.

**Verification:**

- `Grep -n "DirectFileExtractionStrategy"` in `dev/CATALOG/app_v2.jsonl` matches exactly once.
- `Grep -n "LinkAutoDownloadCoordinator"` in `dev/CATALOG/app_v2.jsonl` matches exactly once.
- `Grep -n "HtmlPageExtractionStrategy"` in `dev/CATALOG/app_v2.jsonl` matches exactly once.

**Status:** `[ ]` not done

---

### Step 06.3 — Set `role` + `status` for new entries

**Files:** `dev/CATALOG/app_v2.jsonl` (via `set.ps1`)
**Depends on:** Step 06.2

**Prompt for developer:**

> For each new class, fill the manual fields via the catalogue CLI:
>
> ```powershell
> pwsh -File dev/CATALOG/scripts/set.ps1 -Module app_v2 -Path "<path-substring>" -Role <role> -Status tested
> ```
>
> `set.ps1`'s `-Path` is the canonical selector (fuzzy substring match) — it does not accept a `-Class` flag. `-Status` is one of `new | tested | legacy | todo | unknown`; pick `tested` for shipped & verified classes.
>
> Suggested roles (single short token):
>
> - `LinkAutoDownloadCoordinator` → `coordinator`
> - `LinkExtractionRegistry` → `registry`
> - `UrlExtractionStrategy` → `contract`
> - `DirectFileExtractionStrategy`, `HtmlPageExtractionStrategy` → `strategy`
> - `LinkDownloadWriter` → `writer`
> - `MediaMimeWhitelist` → `policy`
> - `CandidateSelectionPolicy` → `policy`
> - `HtmlMediaCandidate` → `model`
> - `UrlInTextDetector` → `parser`
> - `LinkAutoDownloadProgressDialog` → `dialog`
> - `LinkDownloadModule`, `LinkDownloadStrategiesModule` → `di`
>
> Re-run the render script after `set.ps1` so `app_v2.md` reflects the new metadata.

**Verification:**

- `Grep -n "\"role\":\"strategy\"" dev/CATALOG/app_v2.jsonl` matches at least twice (direct + html).
- `Grep -n "\"role\":\"coordinator\"" dev/CATALOG/app_v2.jsonl` matches at least once.

**Status:** `[ ]` not done

---

### Step 06.4 — Final dev-log batch

**Files:** `dev/CHANGELOG.md` (via `add_to_dev_log.ps1`)
**Depends on:** Steps 06.1–06.3

**Prompt for developer:**

> Run, in order:
>
> ```powershell
> .\scripts\add_to_dev_log.ps1 "docs/FEATURES.md" "spec-all" "S0003: documented link auto-download channel"
> .\scripts\add_to_dev_log.ps1 "docs/FEATURES_RU.md" "spec-all" "S0003: documented link auto-download channel (RU)"
> .\scripts\add_to_dev_log.ps1 "docs/FEATURES_UK.md" "spec-all" "S0003: documented link auto-download channel (UK)"
> .\scripts\add_to_dev_log.ps1 "dev/CATALOG/app_v2.jsonl" "spec-all" "S0003: catalogue refresh"
> .\scripts\add_to_dev_log.ps1 "dev/CATALOG/app_v2.md" "spec-all" "S0003: catalogue refresh"
> ```
>
> Never edit `dev/CHANGELOG.md` directly.

**Verification:**

- `Grep -n "S0003: documented link auto-download channel"` in `dev/CHANGELOG.md` matches at least once.
- `Grep -n "S0003: catalogue refresh"` in `dev/CHANGELOG.md` matches at least once.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 06.*` above is `[x] done`.
- [ ] `Grep` for `TODO(phase-06)` returns zero hits.

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate. After this phase, run `/spec-check S0003`.

---

## Rollback Plan

Revert phase commit(s). Doc updates and catalogue regen are non-destructive; rerunning the pipeline reproduces them.
