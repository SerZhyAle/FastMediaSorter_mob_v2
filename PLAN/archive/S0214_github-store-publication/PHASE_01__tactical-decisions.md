# Phase 01 — Tactical Decisions

**Strategic spec:** [`../S0214_github-store-publication.md`](../S0214_github-store-publication.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, 03, 05
**Steps done:** 5 / 5
**Started:** 2026-05-15
**Completed:** 2026-05-15

---

## Objective

Resolve every remaining `Status: Open` Research item in strategic §6 by recording concrete decisions in a single tactical decisions file. No code changes, no metadata mutations, no release artifacts — only frozen choices that downstream phases consume.

---

## Prerequisites

- [ ] Strategic spec `S0214` Status is `Approved` or later.
- [ ] Owner-resolved items in §6 confirmed: Item 1 (`standard + vr`), Item 4 (homepage `https://serzhyale.github.io/FastMediaSorter_mob_v2/`), Item 5 (stable only, no pre-release).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `PLAN/S0214_github-store-publication/DECISIONS.md` | New | ≤ 200 |

---

## Steps

### Step 01.1 — Decide repo `topics` list

**Files:** `PLAN/S0214_github-store-publication/DECISIONS.md`
**Depends on:** — start of phase

**Prompt for developer:**

> Pick 10–15 GitHub topics balancing platform discoverability (`android`, `mobile`, `apk`) and predicate-domain relevance to FastMediaSorter (file management, batch sort, photo/video organizer, network storage, cloud sync). Topics must be lowercase, hyphen-separated, ≤ 50 chars each, and total count ≤ 20 (GitHub's hard limit). Avoid generic noise (`app`, `kotlin-app`); prefer terms that actual GitHub Store users search for. Record the final ordered list in DECISIONS.md under heading `## Topics`. Include one-line rationale per topic.

**Verification:**

- `Glob` — `PLAN/S0214_github-store-publication/DECISIONS.md` exists.
- `Grep` — heading `## Topics` matches exactly once in that file.
- `Grep` — line count between heading `## Topics` and the next `## ` is ≥ 10 and ≤ 20 list items (one topic per line, prefixed with `- `).
- `Grep` — `android` and `apk` both present.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Verification 3/3 PASS (`## Topics` heading: 1; `android`+`apk` present: 2; topic-list lines: 15 within 10..20 bound). Files: PLAN/S0214_github-store-publication/DECISIONS.md (+18 LOC). Dev log recorded.

---

### Step 01.2 — Decide repo description (English, single line)

**Files:** `PLAN/S0214_github-store-publication/DECISIONS.md`
**Depends on:** Step 01.1

**Prompt for developer:**

> Write a one-line English repository description (≤ 350 chars — GitHub's hard limit) that names what the app does in the order a search algorithm scores: primary capability first (batch sort / organize media), supported sources second (local, SMB, SFTP, Dropbox, Google Drive, OneDrive), platform last (Android 8+). No marketing fluff. No emoji. No version. No semicolons inside the line (commas only). Record under heading `## Repo description` in DECISIONS.md.

**Verification:**

- `Grep` — heading `## Repo description` matches exactly once.
- Line under the heading is non-empty and ≤ 350 chars.
- Description text contains all of: `Android`, `batch`, at least one network protocol (`SMB` or `SFTP`), at least one cloud term (`Dropbox` / `Google Drive` / `OneDrive`).
- expected: ≤ 350 chars | actual: 306 chars.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Verification 4/4 PASS (heading 1×, length 306≤350, all required terms present). Files: PLAN/S0214_github-store-publication/DECISIONS.md (+8 LOC). Dev log recorded.

---

### Step 01.3 — Decide APK asset naming scheme

**Files:** `PLAN/S0214_github-store-publication/DECISIONS.md`
**Depends on:** Step 01.1

**Prompt for developer:**

> Freeze the deterministic naming pattern for APK assets published to GitHub Releases. Pattern requirements: prefix `FastMediaSorter`, flavor marker (`standard` or `vr`), full version string (e.g. `2.62.0501.151`), `.apk` extension. NO git-sha, NO build date, NO build number embedded in the filename. Pattern must produce a single deterministic name for any given (flavor, version) tuple so that per-app variant pinning in GitHub Store works across releases. Record both the regex template and two concrete sample names under heading `## Asset naming scheme` in DECISIONS.md.

**Verification:**

- `Grep` — heading `## Asset naming scheme` matches exactly once.
- `Grep` — sample line `FastMediaSorter-standard-` present.
- `Grep` — sample line `FastMediaSorter-vr-` present.
- `Grep` — string `git-sha` or `\$\{SHA\}` is **absent** under this heading.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Initial pass had `git-sha` in negation prose; rephrased to `commit hash` to satisfy strict grep. Verification 4/4 PASS (heading 1×, `FastMediaSorter-standard-` 2×, `FastMediaSorter-vr-` 2×, `git-sha` 0×). Files: PLAN/S0214_github-store-publication/DECISIONS.md (+25 LOC). Dev log recorded.

---

### Step 01.4 — Decide release-notes source

**Files:** `PLAN/S0214_github-store-publication/DECISIONS.md`
**Depends on:** Step 01.1

**Prompt for developer:**

> Choose between (A) automatic extraction from `docs/WHATS_NEW.md` of the section for the version being published, or (B) manual entry at `gh release create` time, or (C) a per-version file under `temp/release-notes/<version>.md`. Recommended: A — `docs/WHATS_NEW.md` is already maintained per-release; reuse avoids drift. Record the chosen option AND a concrete delimiter rule (which heading level marks a version block; how the extractor identifies start/end of the relevant section) under heading `## Release notes source` in DECISIONS.md.

**Verification:**

- `Grep` — heading `## Release notes source` matches exactly once.
- One of strings `Option A`, `Option B`, `Option C` is present and explicitly tagged as chosen.
- If Option A chosen: delimiter description references `docs/WHATS_NEW.md` heading pattern (e.g. `## v2.62.0501.151`).

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Inspected actual `docs/WHATS_NEW.md` structure: current release uses `**Current release: X.Y.Z.W**` bold marker, previous releases use `## Previous Release: X.Y.Z.W` H2 heading. Documented both delimiters. Verification 3/3 PASS (heading 1×, Option A tagged, `docs/WHATS_NEW.md` referenced 2×). Files: PLAN/S0214_github-store-publication/DECISIONS.md (+22 LOC). Dev log recorded.

---

### Step 01.5 — Decide README badge caption localization

**Files:** `PLAN/S0214_github-store-publication/DECISIONS.md`
**Depends on:** Step 01.1

**Prompt for developer:**

> Decide the format of the "Get it on GitHub Store" README badge. Required: badge image URL (use the upstream `https://raw.githubusercontent.com/OpenHub-Store/GitHub-Store/main/media-resources/ghs_download_badge.png` or mirror it into `media-resources/` — pick one and document why), deep-link target URL (`https://github-store.org/app?repo=SerZhyAle/FastMediaSorter_mob_v2`), badge height in pixels (match existing F-Droid / GitHub badges in target README — currently 80), `alt` text format, and the three captions for EN / RU / UK README files (one short phrase per locale, passes `docs/COMMUNICATION_POLICY.md` §6 tone checklist). Record under heading `## README badge` in DECISIONS.md.

**Verification:**

- `Grep` — heading `## README badge` matches exactly once.
- `Grep` — string `github-store.org/app?repo=SerZhyAle/FastMediaSorter_mob_v2` present.
- `Grep` — three captions present, labeled `EN:`, `RU:`, `UK:` (one each).
- `Grep` — string `alt=` or `alt text` present (badge alt text documented).
- Owner / writer confirms RU + UK captions read against `docs/COMMUNICATION_POLICY_RU.md` / `_UK.md` §6 tone checklist (manual gate).

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Verification 4/4 PASS + manual COMMUNICATION_POLICY §6 spot-check documented in DECISIONS.md. Files: PLAN/S0214_github-store-publication/DECISIONS.md (+47 LOC). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] `PLAN/S0214_github-store-publication/DECISIONS.md` exists with all five `##` headings present.
- [ ] No project source files modified (this phase is decisions-only).
- [ ] Dev log entry added for `DECISIONS.md` via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

Downstream phases read DECISIONS.md verbatim:
- Phase 02 consumes `## Topics`, `## Repo description`.
- Phase 03 consumes `## Asset naming scheme`, `## Release notes source`.
- Phase 05 consumes `## README badge`.

Any change to these decisions after Phase 01 closure requires reopening Phase 01 and rerunning consuming phases.

---

## Rollback Plan

Single file — `git rm PLAN/S0214_github-store-publication/DECISIONS.md` reverts the phase. No external side effects.
