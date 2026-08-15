# Phase 01 — Activation surface: Instagram + html-cheap-path

**Strategic spec:** [`../S0197_threads-ig-data-sjs-extractor.md`](../S0197_threads-ig-data-sjs-extractor.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 3 / 3
**Started:** 2026-05-14
**Completed:** 2026-05-14

---

## Objective

Activate the existing data-sjs JSON harvester for all Threads + Instagram public-post hosts, on both the cheap HTML strategy and the dynamic WebView strategy. Replace the hard-coded `THREADS_HOSTS` gate with a property on `KnownAuthResource` so the host list is owned in one place.

---

## Prerequisites

- [x] All phases in "Depends on" are ✅ Done — none.
- [x] Strategic §6 research items blocking this phase are Resolved — none block this phase.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/link/auth/KnownAuthResources.kt` | Modified | ≤ 100 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/link/InvisibleWebViewExtractionStrategy.kt` | Modified | ≤ 760 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/link/HtmlPageExtractionStrategy.kt` | Modified | ≤ 360 |

---

## Steps

### Step 01.1 — Add `supportsEmbeddedJson` property to `KnownAuthResource`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/link/auth/KnownAuthResources.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Add a new `Boolean` field `supportsEmbeddedJson` (default `false`) to the `KnownAuthResource` data class — after `previewOnlyMeansLogin`. Set this flag to `true` on the three entries `Instagram` (`instagram.com`), `Threads` (`threads.net`), and `Threads` (`threads.com`). Add a static helper `KnownAuthResources.supportsEmbeddedJson(host: String?): Boolean = matchHost(host)?.supportsEmbeddedJson == true` mirroring the shape of `isPreviewSensitiveHost`.

**Verification:**

- `Grep` — `val supportsEmbeddedJson: Boolean = false` matches in `KnownAuthResources.kt`.
- `Grep` — `supportsEmbeddedJson = true` matches exactly 3 times in `KnownAuthResources.kt`.
- `Grep` — `fun supportsEmbeddedJson(host: String\?)` matches once in `KnownAuthResources.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-14 — Verification 3/3 PASS. Files: `app_v2/.../auth/KnownAuthResources.kt` (+10 LOC). Dev log recorded.

---

### Step 01.2 — Replace `THREADS_HOSTS` gate in dynamic strategy with `KnownAuthResources.supportsEmbeddedJson`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/link/InvisibleWebViewExtractionStrategy.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> In `InvisibleWebViewExtractionStrategy`, replace the body of `shouldInspectEmbeddedJson(pageUrl: String?)` so it delegates to `KnownAuthResources.supportsEmbeddedJson(host)` where `host` is the lowercased host extracted from `pageUrl`. Remove the private `THREADS_HOSTS` constant from the companion object — it becomes dead code. Add `import com.sza.fastmediasorter.data.link.auth.KnownAuthResources` if not already present.

**Verification:**

- `Grep` — `THREADS_HOSTS` returns zero hits in `InvisibleWebViewExtractionStrategy.kt`.
- `Grep` — `KnownAuthResources.supportsEmbeddedJson` matches in `InvisibleWebViewExtractionStrategy.kt`.
- `Grep` — `import com.sza.fastmediasorter.data.link.auth.KnownAuthResources` matches in `InvisibleWebViewExtractionStrategy.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-14 — Verification 3/3 PASS. Files: `app_v2/.../link/InvisibleWebViewExtractionStrategy.kt` (-3 LOC). Dev log recorded.

---

### Step 01.3 — Wire embedded-JSON harvest into `HtmlPageExtractionStrategy.harvestCandidates`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/link/HtmlPageExtractionStrategy.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> In `HtmlPageExtractionStrategy.harvestCandidates`, after the existing call to `structuredMediaSniffer.sniff(html, baseUri = baseUri)`, gate-call `structuredMediaSniffer.sniffEmbeddedJson(html, baseUri = baseUri)` only when `KnownAuthResources.supportsEmbeddedJson(baseUri.toHttpUrlOrNull()?.host) == true`. Merge the embedded-JSON candidates BEFORE the static candidates so they take priority in input order. Update the `LinkDownloadTrace.tag` log line to count embedded-JSON separately (new local `embeddedCount`). Add `import com.sza.fastmediasorter.data.link.auth.KnownAuthResources` if missing.

**Verification:**

- `Grep` — `structuredMediaSniffer.sniffEmbeddedJson` matches in `HtmlPageExtractionStrategy.kt`.
- `Grep` — `KnownAuthResources.supportsEmbeddedJson` matches in `HtmlPageExtractionStrategy.kt`.
- `Grep` — `embedded=` matches in `HtmlPageExtractionStrategy.kt` (the trace tag).

**Status:** `[x] done`

**Step Log:**

- 2026-05-14 — Verification 3/3 PASS. Files: `app_v2/.../link/HtmlPageExtractionStrategy.kt` (+15 LOC). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles — run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` (then `render.ps1`).

---

## Handoff Notes to Next Phase

After Phase 01: every Threads/IG public-post URL on either strategy produces EMBEDDED_JSON candidates whenever `<script data-sjs>` payload contains a post. Phase 02 changes how these candidates compete with OG/IMG candidates in `CandidateSelectionPolicy`.

---

## Rollback Plan

Revert the three file edits — `KnownAuthResource` schema returns to its previous shape, dynamic strategy regains its hard-coded `THREADS_HOSTS`, html strategy stops calling `sniffEmbeddedJson`. No data migration, no user-visible surface affected.
