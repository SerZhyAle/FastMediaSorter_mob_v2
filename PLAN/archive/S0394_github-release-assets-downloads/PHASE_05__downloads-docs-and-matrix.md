# Phase 05 - Downloads docs + distribution matrix

**Strategic spec:** [`../S0394_github-release-assets-downloads.md`](../S0394_github-release-assets-downloads.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** Phase 06
**Steps done:** 2 / 2
**Started:** 2026-06-10
**Completed:** 2026-06-10

---

## Objective

Point the user-facing Downloads docs at the GitHub Release as the primary source (Google Drive demoted to a mirror), and document the full-spectrum GitHub-assets publish step in the release pipeline's distribution matrix.

---

## Prerequisites

- [ ] Phase 03 ✅ Done (publisher + asset naming finalized).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/DOWNLOADS_EN.md` | Modified | ≤ 80 |
| `docs/DOWNLOADS_RU.md` | Modified | ≤ 80 |
| `docs/DOWNLOADS_UK.md` | Modified | ≤ 80 |
| `.claude/commands/skill-release.md` | Modified | ≤ existing |

---

## Steps

### Step 05.1 - Rewrite Downloads docs to GitHub Release primary

**Files:** `docs/DOWNLOADS_EN.md`, `docs/DOWNLOADS_RU.md`, `docs/DOWNLOADS_UK.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Rewrite each Downloads doc so the primary download source is the GitHub Release (`https://github.com/SerZhyAle/FastMediaSorter_mob_v2/releases/latest`), listing the public flavors (standard, vr, lite, photos, legacy, wear) with their asset-name pattern `FastMediaSorter-<flavor>-<version>.apk`. Keep the Google Drive folder mentioned as a mirror (no longer the only channel). Do not list noLegal in the public Downloads docs (it lives behind `nolegal*.html`). EN/RU/UK must stay in content parity.

**Verification:**

- `Grep` - `releases/latest` present in all three `docs/DOWNLOADS_*.md`.
- `Grep` - Google Drive still mentioned (mirror) in all three.
- `Grep` - `noLegal` / `nolegal` absent from all three public Downloads docs.

**Status:** `[x]` done

**Step Log:**

- 2026-06-10 - Verification PASS. Rewrote `docs/DOWNLOADS_{EN,RU,UK}.md` to make the GitHub Release the primary source (public flavors + `FastMediaSorter-<flavor>-<version>.apk` pattern), Google Drive kept as a mirror, noLegal not listed (`nolegal`=0 in all three). EN/RU/UK in content parity.

---

### Step 05.2 - Add the GitHub-assets step to the release distribution matrix

**Files:** `.claude/commands/skill-release.md`
**Depends on:** Step 05.1

**Prompt for developer:**

> In the release pipeline's Step 12a "Distribution channels - full matrix", document the new automated step: build the release spectrum at one version (`scripts/release/build-release-spectrum.ps1`) then publish all seven assets to one GitHub Release (`scripts/release/publish-github-release.ps1`, now full-spectrum). Note that the website + Downloads docs consume this release automatically. Confirm the file path first with `Glob` (`.claude/commands/skill-release.md`); if absent, locate the skill-release source via Glob before editing.

**Verification:**

- `Glob` - the skill-release command file exists.
- `Grep` - `build-release-spectrum.ps1` referenced in its distribution matrix.
- `Grep` - the GitHub-assets full-spectrum step is described (not just standard+vr).

**Status:** `[x]` done

**Step Log:**

- 2026-06-10 - Verification PASS. Step 12a code block now runs `build-release-spectrum.ps1` before publish; matrix item 2 rewritten to "full spectrum (standard, vr, lite, photos, legacy, wear, noLegal)" with all-seven-assets + website/Downloads consumption noted.

---

## Phase Done Criteria

- [x] Every `Step 05.*` is `[x] done`.
- [x] String/locale parity: EN/RU/UK Downloads docs carry equivalent content (same sections + flavor list).
- [x] `Grep` for `TODO(phase-05)` returns zero hits.
- [x] Dev log entry added for every touched file.

---

## Handoff Notes to Next Phase

Docs + pipeline now describe the GitHub-assets channel. Phase 06 runs the mechanical closure (changelog, catalog, completion gate).

---

## Rollback Plan

Revert the phase commit - docs return to the Google-Drive-only wording; no code or release affected.
