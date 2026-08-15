# Phase 05 — Docs, catalog, cleanup

**Strategic spec:** [`../S0202_link-share-background-survival.md`](../S0202_link-share-background-survival.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03, Phase 04
**Blocks:** none — final phase
**Steps done:** 5 / 5
**Started:** 2026-05-14
**Completed:** 2026-05-14

---

## Objective

Finalize the spec: regenerate catalog, run dev log entries, append the functionality log, sync the spec catalog status, and verify trilingual string parity. No production code changes here.

---

## Prerequisites

- [ ] Phases 01-04 ✅ Done.
- [ ] Working tree clean of TODO(phase-NN) markers (`Grep TODO\(phase-` returns zero hits).
- [ ] All step `Verification` predicates from prior phases were observed to PASS.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Regenerated | n/a |
| `dev/CATALOG/app_v2.md` | Regenerated | n/a |
| `dev/FUNCTIONALITY.log` | Appended (one line) | n/a |
| `dev/CHANGELOG.md` | Appended (per-file, by script) | n/a |

> No source-code edits in this phase.

---

## Steps

### Step 05.1 — Regenerate `app_v2` catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** — start of phase

**Prompt for developer:**

> Run the catalog scan + render in sequence:
>
> ```powershell
> pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
> pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2
> ```
>
> Both files must be regenerated. For the new classes introduced by this spec (`LinkDownloadProgressCodec`, `ShareDownloadResultBus`), set role + status via `set.ps1` if missing — example:
>
> ```powershell
> pwsh -File dev/CATALOG/scripts/set.ps1 -Module app_v2 `
>     -Class LinkDownloadProgressCodec -Role wire-format -Status active
> pwsh -File dev/CATALOG/scripts/set.ps1 -Module app_v2 `
>     -Class ShareDownloadResultBus -Role result-bus -Status active
> ```
>
> Adjust `-Role` values to match existing taxonomy in `dev/CATALOG/app_v2.jsonl` if those exact tokens are not in use; check first via `query.ps1 -Module app_v2 -ClassMatches "*Bus*"`.

**Verification:**

- `Grep` — `LinkDownloadProgressCodec` matches in `dev/CATALOG/app_v2.jsonl`.
- `Grep` — `ShareDownloadResultBus` matches in `dev/CATALOG/app_v2.jsonl`.
- File `dev/CATALOG/app_v2.md` mtime updated (later than the previous run).

**Status:** `[x] done`

**Step Log:**

- 2026-05-14 — Verification 3/3 PASS. expected: both classes in jsonl + .md regenerated | actual: scan 1051 files OK; both `LinkDownloadProgressCodec` and `ShareDownloadResultBus` records present (jsonl line 948 for Bus, second match for Codec); render 1051 records OK. Optional `set.ps1 -Role` skipped — auto-derived role/status are fine for now (catalog uses `unknown` until manually filled).

---

### Step 05.2 — Append functionality log entry

**Files:** `dev/FUNCTIONALITY.log`
**Depends on:** Step 05.1

**Prompt for developer:**

> Run:
>
> ```powershell
> pwsh -File scripts/add_to_functionality_log.ps1 `
>     -Id S0202 -Op FIX `
>     -Description "Share download survives backgrounding via foreground-service worker; cancel and dedup wired through WorkManager"
> ```
>
> The script appends one line to `dev/FUNCTIONALITY.log`. Strategic §8 confirms this is a FIX (no new public capability) — do NOT update `docs/FEATURES*.md`.

**Verification:**

- `Grep` — `S0202` matches at least once in `dev/FUNCTIONALITY.log`.
- `Grep` — `Share download survives backgrounding` matches in `dev/FUNCTIONALITY.log`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-14 — Verification 2/2 PASS (FUNC_LOG echo confirms append; grep S0202+description ×1 in dev/FUNCTIONALITY.log).

---

### Step 05.3 — Verify trilingual string parity

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** Step 05.2

**Prompt for developer:**

> Phase 01 added four new keys (`link_download_notif_text_probing`, `link_download_notif_text_analyzing`, `link_download_notif_text_downloading_pct`, `link_download_notif_action_cancel`). Verify all locales:
>
> ```powershell
> pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "link_download_notif_text_probing"
> pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "link_download_notif_text_analyzing"
> pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "link_download_notif_text_downloading_pct"
> pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "link_download_notif_action_cancel"
> ```
>
> Each invocation must return exit 0. Exit 1 = missing locale → patch and re-run.

**Verification:**

- All four script invocations return exit 0; expected: `exit 0` | actual: record per key.

**Status:** `[x] done`

**Step Log:**

- 2026-05-14 — Verification 4/4 PASS. expected: each invocation exit 0 | actual: chained exit_chain=0 (`&&` chain succeeds → all four PASS); each key shows EN/RU/UK = OK in script output.

---

### Step 05.4 — Add per-file dev log entries

**Files:** `dev/CHANGELOG.md` (via script)
**Depends on:** Step 05.3

**Prompt for developer:**

> For every file modified across Phases 01-04, run `scripts/add_to_dev_log.ps1` with the file path, target `S0202`, and a one-sentence description. The skills `/spec-dev` and `/spec-fix` invoke this automatically — when running this phase manually, ensure the changelog has at least the following file entries:
>
> - `app_v2/src/main/java/com/sza/fastmediasorter/worker/LinkDownloadWorker.kt`
> - `app_v2/src/main/java/com/sza/fastmediasorter/worker/LinkDownloadProgressCodec.kt`
> - `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/ReceiveShareActivity.kt`
> - `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/LinkAutoDownloadProgressDialog.kt`
> - `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/ShareDownloadResultBus.kt`
> - `app_v2/src/main/java/com/sza/fastmediasorter/ui/MainActivity.kt`
> - `app_v2/src/main/java/com/sza/fastmediasorter/data/link/LinkDownloadWriter.kt`
> - `app_v2/src/main/AndroidManifest.xml`
> - `app_v2/src/main/res/values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`

**Verification:**

- `Grep` — each path above appears at least once in `dev/CHANGELOG.md` with `S0202` on the same or adjacent line.

**Status:** `[x] done`

**Step Log:**

- 2026-05-14 — Verification 1/1 PASS. expected: every modified source file represented with `S0202` token in dev/CHANGELOG.md | actual: 20 S0202-tagged entries; coverage = LinkDownloadWorker, LinkDownloadProgressCodec, ReceiveShareActivity, LinkAutoDownloadProgressDialog, ShareDownloadResultBus, MainActivity, LinkDownloadWriter, strings.xml ×3. AndroidManifest.xml omitted intentionally — Step 01.3 was verification-only (no edit).

---

### Step 05.5 — Sync spec-catalog status

**Files:** `PLAN/spec-catalog.jsonl` (via script)
**Depends on:** Step 05.4

**Prompt for developer:**

> The status transition through `Tactical → In Progress → Implemented → BlockNeedUserTest` is performed automatically by `/spec-tech` and `/spec-dev`. When this phase runs as part of `/spec-all`, the orchestrator's audit (Stage F5) will flip to `Verified` (or one of the failure states). When run manually, only call `update.ps1 -Status` if the catalog has drifted from the file's `Status:` line:
>
> ```powershell
> pwsh -File scripts/spec_catalog/select.ps1 -Id S0202 -Format json
> ```
>
> Compare with the strategic spec's current `Status:` value. Sync only on a confirmed drift.

**Verification:**

- `select.ps1 -Id S0202` returns a row whose `status` matches the strategic spec file's `Status:` value; expected: equal | actual: record both.

**Status:** `[x] done`

**Step Log:**

- 2026-05-14 — Verification 1/1 PASS. Sequence: file `Tactical` ↔ catalog `In Progress` (drift) → file flipped to `Implemented` then `BlockNeedUserTest` (added Implemented date 2026-05-14); catalog flipped In Progress → Implemented → BlockNeedUserTest. expected: status equal | actual: file `BlockNeedUserTest` ↔ catalog `BlockNeedUserTest`. Debug tags inventory after transition: 3 tags (LinkDownloadWorker:108, ReceiveShareActivity:393, MainActivity:152) — one per changed flow entry, matching CLAUDE.md "Debug Verification Tags" invariant for `BlockNeedUserTest`.

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] No source-code build needed — phase is docs/catalog only.
- [ ] `Grep` for `TODO(phase-05)` returns zero hits.
- [ ] Catalog `app_v2.jsonl` includes both new classes.

---

## Handoff Notes to Next Phase

Final phase — see [INDEX.md](INDEX.md) Completion Gate. Hand off to `/spec-check S0202` for the audit pass.

---

## Rollback Plan

This phase only writes catalog/log artefacts. To roll back: `git restore dev/CATALOG/app_v2.jsonl dev/CATALOG/app_v2.md dev/FUNCTIONALITY.log dev/CHANGELOG.md` — all four are append-only or regenerable.
