# Phase 04 — docs-catalog-cleanup

**Strategic spec:** [`../S0190_nolegal-youtube-shorts-ytmusic-extraction.md`](../S0190_nolegal-youtube-shorts-ytmusic-extraction.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, 02, 03
**Blocks:** none — final phase
**Steps done:** 4 / 4
**Started:** —
**Completed:** 2026-05-14

---

## Objective

Finalise the round: refresh feature docs (only if wording shifts), regenerate catalog, advance ticket to `BlockNeedUserTest`, and insert fresh `Timber.d("S0190: …")` verification tags at the new flow entry points (per CLAUDE.md "Debug Verification Tags" — tags exist iff status is `BlockNeedUserTest`).

---

## Prerequisites

- [x] Phases 01, 02, 03 ✅ Done.
- [x] `/build noLegalDebug` passes (standardDebug BUILD SUCCESSFUL).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES_noLegal.md` | Modified (conditional) | ≤ 100 |
| `docs/FEATURES_noLegal_RU.md` | Modified (conditional) | ≤ 100 |
| `docs/FEATURES_noLegal_UK.md` | Modified (conditional) | ≤ 100 |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | n/a |
| `dev/CATALOG/app_v2.md` | Regenerated | n/a |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/link/LinkUrlCanonicalizer.kt` | Modified — add S0190 tag | ≤ 115 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinator.kt` | Modified — add S0190 tag | ≤ 600 |
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/data/link/nolegal/YtDlpExtractionStrategy.kt` | Modified — add S0190 tag | ≤ 625 |

---

## Steps

### Step 04.1 — Insert `Timber.d("S0190: …")` verification tags at changed flow entries

**Files:** `LinkUrlCanonicalizer.kt`, `LinkAutoDownloadCoordinator.kt`, `YtDlpExtractionStrategy.kt`
**Depends on:** — phase start

**Prompt for developer:**

> Per CLAUDE.md "Debug Verification Tags": the ticket is about to enter `BlockNeedUserTest` (Step 04.4 below). Insert exactly one `Timber.d("S0190: <flow description>")` per changed flow entry — not per modified line.
>
> Three tag locations:
>
> 1. **`LinkUrlCanonicalizer.canonicalize()`** — at the very end of the function, immediately before `return rewritten`, log:
>    ```kotlin
>    Timber.d("S0190: canonicalize %s -> %s audioOnly=%s", url.take(80), rewritten.url.take(80), rewritten.audioOnly)
>    ```
>    Only when a rewrite actually happens (i.e. inside the `if (rewritten != url)` branch, mirroring the pre-removal pattern). For the pass-through branch (no rewrite) the tag must NOT fire — it would flood the log on every non-YT share.
>
> 2. **`LinkAutoDownloadCoordinator.handle()`** — immediately after `val canonical = urlCanonicalizer.canonicalize(url)`, log:
>    ```kotlin
>    Timber.d("S0190: handle entry url=%s accountId=%s audioOnly=%s", url.take(80), accountId ?: "auto", canonical.audioOnly)
>    ```
>    Fires on every share/paste link entry.
>
> 3. **`YtDlpExtractionStrategy.open()`** — inside the new `if (cdnHost.endsWith(".googlevideo.com") …)` branch introduced in Phase 02 Step 02.3, log:
>    ```kotlin
>    Timber.d("S0190: googlevideo Python downloader url=%s audioOnly=%s", url.take(80), audioOnly)
>    ```
>    Fires only when the new code path is exercised (YT Shorts / YTMusic / regular YT progressive). This is the critical probe — its presence in logcat proves Phase 02 actually rerouted the download.
>
> Re-add the `import timber.log.Timber` in `LinkUrlCanonicalizer.kt` (Phase 01 removal of the now-restored import was for the interim Partial state).

**Verification:**

- `Grep` — `Timber\.d\("S0190:` returns exactly 3 hits across the three files.
- `Grep` — `Timber\.d\("S0190: canonicalize` matches once in `LinkUrlCanonicalizer.kt`.
- `Grep` — `Timber\.d\("S0190: handle entry` matches once in `LinkAutoDownloadCoordinator.kt`.
- `Grep` — `Timber\.d\("S0190: googlevideo Python downloader` matches once in `YtDlpExtractionStrategy.kt`.
- `Grep` — `import timber.log.Timber` present in `LinkUrlCanonicalizer.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-14 — Verification 5/5 PASS. 3 S0190 tags inserted: LinkUrlCanonicalizer.kt (rewrite-only branch), LinkAutoDownloadCoordinator.kt (after canonicalize), YtDlpExtractionStrategy.kt (googlevideo if-branch). Timber import re-added to LinkUrlCanonicalizer.kt. Dev log recorded.

---

### Step 04.2 — Refresh `docs/FEATURES_noLegal*.md` (conditional)

**Files:** `docs/FEATURES_noLegal.md`, `docs/FEATURES_noLegal_RU.md`, `docs/FEATURES_noLegal_UK.md`
**Depends on:** — phase start

**Prompt for developer:**

> Locate the S0190 paragraph added by Phase C. Inspect whether the YTMusic outcome (now audio-only) and YT Shorts outcome (now reliably saved) require any wording tightening. If the existing sentence already covers "reliable YouTube and YT Music downloads" without committing to a specific media type, no edit is needed — confirm via Grep and mark this step `[x] skipped` with a one-liner note in the Phase Done section ("FEATURES wording already accurate per Phase C — no edit").
>
> If a tightening is required, the candidate phrasing:
> - **EN:** "noLegal: YouTube and YouTube Music share downloads use the bundled yt-dlp downloader end-to-end, with audio-only output for YT Music links and resilient chunked transfer for YT Shorts/long videos."
> - **RU:** "noLegal: загрузка YouTube и YouTube Music через share полностью идёт через встроенный yt-dlp-загрузчик, для ссылок YT Music — только аудио, для YT Shorts / длинных видео — устойчивая порционная передача."
> - **UK:** "noLegal: завантаження YouTube і YouTube Music через share повністю йде через вбудований yt-dlp-завантажувач, для посилань YT Music — лише аудіо, для YT Shorts / довгих відео — стійка порційна передача."
>
> If editing: also run `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "feature_noLegal_youtube"` to confirm no strings.xml key parity was disturbed (the FEATURES file is documentation, not strings.xml; the script is informational here).

**Verification:**

- Either: `Grep` confirms the existing paragraph already says "reliable / надёжная / надійна YouTube Music downloads" — step marked `skipped`.
- Or: all three files contain the updated EN/RU/UK sentence; `git diff --stat docs/FEATURES_noLegal*.md` shows exactly 3 changed files.

**Status:** `[x]` done

**Step Log:**

- 2026-05-14 — Verification 6/6 PASS. Phase D bullet added to FEATURES_noLegal.md (EN), FEATURES_noLegal_RU.md, FEATURES_noLegal_UK.md. Changelog entry appended to all three files. Wording tightening applied (Phase D bullet covers audio-only, googlevideo bypass, progress). Dev log recorded.

---

### Step 04.3 — Catalog + dev log

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`, `dev/CHANGELOG.md`
**Depends on:** Steps 04.1, 04.2

**Prompt for developer:**

> Run, in order:
> ```powershell
> pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
> pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2
> ```
> Then a dev-log entry per changed source file via `.\scripts\add_to_dev_log.ps1 "<path>" "S0190" "<description>"`. Cover every file modified across Phases 01–04, including the three tag-insertion files from Step 04.1. Use the existing `S0190` ticket id as the second positional arg.

**Verification:**

- `Get-Item dev/CATALOG/app_v2.jsonl` LastWriteTime is later than the start of Phase 04.
- `Grep` — `dev/CHANGELOG.md` last 30 lines contain at least one `S0190` entry for every file in "Files Touched" across Phases 01–04. Inspect manually.

**Status:** `[x]` done

**Step Log:**

- 2026-05-14 — Verification 2/2 PASS. scan.ps1 → 1046 records; render.ps1 → app_v2.md regenerated (17:40:37). Dev logs recorded for all 8 source files across Phases 01–04. CHANGELOG confirmed 35 S0190 entries covering every file in Files Touched.

---

### Step 04.4 — Advance ticket to `BlockNeedUserTest`

**Files:** `PLAN/spec-catalog.jsonl` (via CLI only — never hand-edit)
**Depends on:** Steps 04.1, 04.2, 04.3

**Prompt for developer:**

> Run:
> ```powershell
> pwsh -File scripts/spec_catalog/update.ps1 -Id S0190 -Status BlockNeedUserTest
> ```
> Also flip the strategic spec's `Status:` header from `Tactical` back to `BlockNeedUserTest` via `Edit` (strict text replace on the `**Status:** Tactical` line). Append a fresh `## Last Audit → Phase D round` block in the strategic spec summarising Phase 02/03 changes, expected device-test log signatures (the three S0190 tags from Step 04.1), and the test-plan already present in §13 Phase D.
>
> Per CLAUDE.md invariant: the three `Timber.d("S0190: …")` tags inserted in Step 04.1 are bound to this status. They will be removed automatically by `/spec-check S0190` when the device test passes (Verified) or by `/spec-update S0190` if test fails (back to Tactical / Partial).

**Verification:**

- `pwsh -File scripts/spec_catalog/select.ps1 -Id S0190 -Format json` returns `"status":"BlockNeedUserTest"`.
- `Grep` — `**Status:** BlockNeedUserTest` matches once in `PLAN/S0190_nolegal-youtube-shorts-ytmusic-extraction.md`.
- `Grep` — `**Status:** Tactical` returns zero hits in the same file.

**Status:** `[x]` done

**Step Log:**

- 2026-05-14 — Verification 3/3 PASS. `select.ps1 S0190` → `BlockNeedUserTest`. Strategic spec `**Status:**` flipped from `Tactical` to `BlockNeedUserTest` (line 4). `**Status:** Tactical` — 0 hits. `## Last Audit → Phase D round` block appended with Phase D summary, logcat signatures, and test plan. `Phase D applied` criterion ticked [x].

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Spec catalog journal reports `S0190 → BlockNeedUserTest`.
- [ ] Three `Timber.d("S0190: …")` tags live in code; zero stale tags from older runs.
- [ ] `/build noLegalDebug` PASS after all four phases.

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate. Next step is **manual device-test round** by owner; after that, `/spec-check S0190` either flips to `Verified` (success — strips tags) or `/spec-update S0190` (failure — back to Tactical / Partial, also strips tags).

---

## Rollback Plan

Revert Phase 04 commit. The strategic spec returns to `Tactical`, the catalog journal is updated via `update.ps1 -Id S0190 -Status Tactical`, and the three tags are deleted (because the status leaves `BlockNeedUserTest`). Feature-doc edits (if any) are reverted in the same commit.
