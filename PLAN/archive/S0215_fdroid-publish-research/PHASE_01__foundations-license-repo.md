# Phase 01 — Foundations: LICENSE file + repository metadata

**Strategic spec:** [`../S0215_fdroid-publish-research.md`](../S0215_fdroid-publish-research.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 05, Phase 06
**Steps done:** 4 / 4
**Started:** 2026-05-15
**Completed:** 2026-05-15

---

## Objective

Add a FLOSS-compatible `LICENSE` file to the repository root (Apache 2.0) and set GitHub repository description + topics so the project meets the minimum baseline for FOSS catalogue discovery (IzzyOnDroid + GitHub Store + future F-Droid). No code changes.

---

## Prerequisites

- [x] Strategic §6.4 (LICENSE choice) resolved → Apache 2.0 (see INDEX Pre-Implementation Blockers).
- [ ] Working tree clean or on a feature branch (current: `DEBUG-v002`).
- [ ] Owner has GitHub repository write access (required for steps 01.3 and 01.4 — set via `gh repo edit`).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `LICENSE` | New | ≤ 220 (Apache 2.0 standard text) |
| `README.md` | Modified | unchanged section count; +1 license badge line near the top |
| `docs/README_RU.md` | Modified | +1 license badge line near the top |
| `docs/README_UK.md` | Modified | +1 license badge line near the top |

> No `.kt` files touched in this phase — catalogue sync is a no-op.

---

## Steps

### Step 01.1 — Add Apache 2.0 LICENSE file at repository root

**Files:** `LICENSE`
**Depends on:** — start of phase

**Prompt for developer:**

> Create a new file `LICENSE` at the repository root containing the verbatim Apache License 2.0 text (the standard text from https://www.apache.org/licenses/LICENSE-2.0.txt). Use the boilerplate copyright header at the top:
>
> ```
> Copyright 2024-2026 SerZhyAle
>
> Licensed under the Apache License, Version 2.0 (the "License");
> ..
> ```
>
> Then the full Apache 2.0 body. Do not modify or paraphrase the license text. UTF-8, LF line endings (consistent with other text files in the repo).

**Verification:**

- `Glob` — file `LICENSE` exists at repository root.
- `Grep` — `Apache License` matches at least once in `LICENSE`.
- `Grep` — `Version 2.0, January 2004` matches at least once in `LICENSE` (canonical Apache 2.0 header).
- `Grep` — `Copyright 2024-2026 SerZhyAle` matches exactly once in `LICENSE`.
- expected line count: ≥ 175 | actual: 215.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Verification 5/5 PASS. Files: `LICENSE` (new, 215 lines, Apache 2.0 standard text). Grep counts: `Apache License`=5, `Version 2.0, January 2004`=1, `Copyright 2024-2026 SerZhyAle`=1. Dev log recorded.

---

### Step 01.2 — Add license badge to README trilingual

**Files:** `README.md`, `docs/README_RU.md`, `docs/README_UK.md`
**Depends on:** Step 01.1

**Prompt for developer:**

> In all three README files, add a license badge **right after** the existing Kotlin/Android shields-io badges block (e.g. after `![Android](..)` line in `README.md`). Use this exact markdown for the badge:
>
> ```markdown
> ![License](https://img.shields.io/badge/License-Apache_2.0-blue?style=flat-square&logo=apache)
> ```
>
> Do not change other lines. The badge stays on its own line with the same `![..](..)` style as the existing two shields-io badges. Strings on the badge are not user-visible app text — `COMMUNICATION_POLICY` does not apply.

**Verification:**

- `Grep` — `License-Apache_2.0-blue` matches exactly once in `README.md`.
- `Grep` — `License-Apache_2.0-blue` matches exactly once in `docs/README_RU.md`.
- `Grep` — `License-Apache_2.0-blue` matches exactly once in `docs/README_UK.md`.
- expected total occurrences across project: 3 | actual: 3 via `Grep -c`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Verification 4/4 PASS. Files: `README.md` (+1), `docs/README_RU.md` (+1), `docs/README_UK.md` (+1). Apache 2.0 license badge inserted after `![Android]` shield in each. Dev log recorded (3 files).

---

### Step 01.3 — Update GitHub repository description

**Files:** none in this repo (GitHub side via `gh repo edit`)
**Depends on:** Step 01.1 (LICENSE must be in place when GitHub re-scans the repo)

**Prompt for developer:**

> Run the following PowerShell command from the project root (uses the `gh` CLI; owner must be authenticated):
>
> ```powershell
> gh repo edit SerZhyAle/FastMediaSorter_mob_v2 --description "Android media file organizer with SMB, FTP, SFTP, cloud storage and Wear OS support. Browse, sort, copy and play photos, videos, audio and documents from local, network and cloud sources."
> ```
>
> Description is capped at ~350 characters by GitHub. Keep the text above as-is — it is built from canonical feature surface (matches `docs/FEATURES.md` headers) and contains keywords for FOSS catalogue search ranking.

**Verification:**

- `Bash` — `gh repo view SerZhyAle/FastMediaSorter_mob_v2 --json description --jq .description` returns the exact description above.
- expected: description starts with `Android media file organizer` | actual: full text matches.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Verification 1/1 PASS. External: `gh repo edit SerZhyAle/FastMediaSorter_mob_v2 --description` set canonical text. Verified via `gh repo view --json description`. Dev log recorded.

---

### Step 01.4 — Set GitHub repository topics for FOSS-catalogue discovery

**Files:** none in this repo (GitHub side via `gh repo edit`)
**Depends on:** Step 01.3

**Prompt for developer:**

> Run the following PowerShell command from the project root:
>
> ```powershell
> gh repo edit SerZhyAle/FastMediaSorter_mob_v2 --add-topic android --add-topic kotlin --add-topic file-manager --add-topic media-player --add-topic photo-organizer --add-topic video-player --add-topic audio-player --add-topic smb --add-topic sftp --add-topic ftp --add-topic cloud-storage --add-topic apk --add-topic fastmediasorter
> ```
>
> Existing topics (if any) are preserved by `--add-topic`. GitHub allows up to 20 topics; this set is 13.

**Verification:**

- `Bash` — `gh repo view SerZhyAle/FastMediaSorter_mob_v2 --json repositoryTopics --jq '.repositoryTopics[].name'` returns at least the 13 topics above.
- expected: 13 topics from the list present | actual: 13/13 present (android, apk, audio-player, cloud-storage, fastmediasorter, file-manager, ftp, kotlin, media-player, photo-organizer, sftp, smb, video-player).

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Verification 1/1 PASS. External: `gh repo edit --add-topic` (13 topics applied). Verified via `gh repo view --json repositoryTopics`. Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] `LICENSE` file present at repository root with Apache 2.0 content.
- [ ] All three README files contain the license badge.
- [ ] GitHub repository description matches the canonical text.
- [ ] GitHub repository has the 13 topics from step 01.4 set.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for `LICENSE`, `README.md`, `docs/README_RU.md`, `docs/README_UK.md` via `.\scripts\add_to_dev_log.ps1`.
- [ ] No `.kt` files touched → no catalogue regeneration needed.

---

## Handoff Notes to Next Phase

Phase 02 (fastlane EN metadata) consumes:

- The LICENSE file as the basis for `full_description.txt`'s license line.
- The GitHub description as the basis for `short_description.txt` (compressed to ≤ 80 chars).
- The GitHub topics as keyword anchors for the full description.

---

## Rollback Plan

Revert phase commit(s) — no runtime code or user-visible surface changed. GitHub-side changes (description, topics) revert by running `gh repo edit` with the prior values; capture pre-change values in the commit message body for traceability.
