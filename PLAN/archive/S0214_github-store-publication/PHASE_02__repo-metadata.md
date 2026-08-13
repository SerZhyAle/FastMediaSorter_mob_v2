# Phase 02 — Repo Metadata Update

**Strategic spec:** [`../S0214_github-store-publication.md`](../S0214_github-store-publication.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done (4/4 done)
**Depends on:** Phase 01
**Blocks:** Phase 06
**Steps done:** 4 / 4
**Started:** 2026-05-15
**Completed:** 2026-05-16

---

## Objective

Apply the GitHub repository metadata (`description`, `topics`, `homepage`) decided in Phase 01 to `SerZhyAle/FastMediaSorter_mob_v2` via a reproducible PowerShell helper. Make the application idempotent so it can re-run after Phase 01 decisions change.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done — `DECISIONS.md` is final.
- [x] GitHub credentials available: either `gh auth login` completed locally, or `$env:GITHUB_TOKEN` exported with `repo` scope.
- [ ] Repo `SerZhyAle/FastMediaSorter_mob_v2` is the active `origin`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/release/apply-github-store-metadata.ps1` | New | ≤ 180 |

---

## Steps

### Step 02.1 — Create `scripts/release/` folder and the metadata applier skeleton

**Files:** `scripts/release/apply-github-store-metadata.ps1`
**Depends on:** — start of phase

**Prompt for developer:**

> Create folder `scripts/release/` if it does not exist. Inside, create `apply-github-store-metadata.ps1` as a PowerShell 7 script with: param block declaring `[switch] $DryRun` and optional `[string] $Owner = "SerZhyAle"` / `[string] $Repo = "FastMediaSorter_mob_v2"`; comment-based help summarising purpose; `$ErrorActionPreference = "Stop"`; a helper function that resolves credentials in order — `gh` CLI first (via `gh auth status`), then `$env:GITHUB_TOKEN`, abort with explicit error if neither works. No metadata mutations yet.

**Verification:**

- `Glob` — `scripts/release/apply-github-store-metadata.ps1` exists.
- `Grep` — `param\(` matches exactly once.
- `Grep` — `\[switch\] \$DryRun` present.
- `Grep` — `\$ErrorActionPreference = "Stop"` present.
- `Grep` — both `gh auth status` and `GITHUB_TOKEN` referenced.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Verification 5/5 PASS (`param(` 1×, `[switch] $DryRun` 1×, `$ErrorActionPreference = "Stop"` 1×, `gh auth status` + `GITHUB_TOKEN` 6× combined). File exists at `scripts/release/apply-github-store-metadata.ps1`. Dev log recorded.

---

### Step 02.2 — Parse `DECISIONS.md` for topics + description

**Files:** `scripts/release/apply-github-store-metadata.ps1`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add a parsing function to the script that reads `PLAN/S0214_github-store-publication/DECISIONS.md` and extracts: (a) the topic list from the `## Topics` section as a flat string array (one per `- ` bullet, stripped of inline rationale after first ` — `), and (b) the description string from the `## Repo description` section (first non-empty line under the heading). Both must validate against the rules in Phase 01 (topic count ≤ 20, each ≤ 50 chars; description ≤ 350 chars). On validation failure, abort with the specific rule that failed. Print the parsed values when `-DryRun` is supplied.

**Verification:**

- `Grep` — function name matching `function .*-Decisions` defined exactly once.
- `Grep` — string `DECISIONS.md` referenced via `Resolve-Path` or absolute path construction.
- `Grep` — `350` and `50` length checks present.
- Dry run check: `pwsh -File scripts/release/apply-github-store-metadata.ps1 -DryRun` exits 0 and prints both topics array and description string.
- expected exit code: 0 | actual: 0.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Initial run failed: credential resolver ran before parser, dry-run aborted on missing `gh`/token. Reordered: dry-run now exits before credential resolution. Verification PASS: `function .*-Decisions` 1×, `DECISIONS.md` 9×, length checks (`350`, `50`) 4×; dry-run exit 0 with full topic list + description printed. Files: scripts/release/apply-github-store-metadata.ps1 (+76 LOC; reordered main). Dev log recorded.

---

### Step 02.3 — Apply via GitHub REST API (PATCH `/repos/{owner}/{repo}`)

**Files:** `scripts/release/apply-github-store-metadata.ps1`
**Depends on:** Step 02.2

**Prompt for developer:**

> Extend the script to perform two API calls when `-DryRun` is **absent**: (1) `PATCH /repos/{owner}/{repo}` with JSON body `{ "description": "<desc>", "homepage": "https://serzhyale.github.io/FastMediaSorter_mob_v2/" }`; (2) `PUT /repos/{owner}/{repo}/topics` with JSON body `{ "names": [<topics>] }` and header `Accept: application/vnd.github+json`. Use `Invoke-RestMethod`. On HTTP error, surface the response body and exit non-zero. On success, print before/after diff for both fields by fetching the repo a second time. The script must be idempotent — repeated runs converge to the same final state.

**Verification:**

- `Grep` — `PATCH` method present.
- `Grep` — `PUT` method present.
- `Grep` — endpoint `/repos/` referenced twice.
- `Grep` — string `application/vnd.github` present.
- `Grep` — `https://serzhyale.github.io/FastMediaSorter_mob_v2/` present as literal homepage URL.
- Manual run after Phase 01: `pwsh -File scripts/release/apply-github-store-metadata.ps1` returns 0 and the next `curl https://api.github.com/repos/SerZhyAle/FastMediaSorter_mob_v2` shows non-empty `topics` and the chosen description.
- expected: `topics.length >= 10`, `description` matches DECISIONS.md verbatim, `homepage` is the GitHub Pages URL | actual: deferred to Step 02.4 (live run requires credentials).

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Static verification 5/5 PASS (PATCH 1×, PUT 1×, `/repos/` 8×, `application/vnd.github` 1×, homepage URL literal 1×). Dry-run still exits 0 (regression check). Live API verify is the responsibility of Step 02.4 and is BLOCKED on owner credentials. Files: scripts/release/apply-github-store-metadata.ps1 (+102 LOC; PATCH /repos + PUT /repos/.../topics + before/after diff). Dev log recorded.

---

### Step 02.4 — Run the applier and confirm GitHub state

**Files:** _(no source file changes — this is an execution step)_
**Depends on:** Step 02.3

**Prompt for developer:**

> Execute `pwsh -File scripts/release/apply-github-store-metadata.ps1` once. Capture the post-apply repo metadata via `curl -s https://api.github.com/repos/SerZhyAle/FastMediaSorter_mob_v2` and `curl -s -H 'Accept: application/vnd.github+json' https://api.github.com/repos/SerZhyAle/FastMediaSorter_mob_v2/topics`. Verify against DECISIONS.md.

**Verification:**

- Curl response field `description` matches the value from `DECISIONS.md` §`## Repo description` byte-for-byte.
- Curl response field `homepage` equals `https://serzhyale.github.io/FastMediaSorter_mob_v2/`.
- Curl response field `topics.names` is a non-empty array containing the same 15 topics as `DECISIONS.md` §`## Topics`; GitHub normalizes topic order server-side, so closure checks set equality rather than insertion order.
- expected: description match, homepage match, topic-set equality | actual: PASS (`description` exact match, `homepage` exact match, 15/15 topics present via GitHub API snapshot on 2026-05-16).

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Cannot execute: no `gh` CLI session and no `$env:GITHUB_TOKEN` available in the spec-dev session. Owner can resume this step manually by running `pwsh -File scripts/release/apply-github-store-metadata.ps1` with a valid credential, then ticking the predicates.
- 2026-05-16 — Live run PASS with active `gh` session for `SerZhyAle`. `pwsh -File scripts/release/apply-github-store-metadata.ps1` returned 0, applied the DECISIONS.md description + homepage, and updated topics via `PUT /repos/.../topics`. Evidence log: `temp/sessions/20260516_223715_S0214_phase02_apply_metadata.txt`. Follow-up API snapshot confirmed exact `description`, exact `homepage`, and topic-set equality (15/15 topics present; order normalized by GitHub).
- 2026-05-16 — Idempotency PASS: repeated live run returned 0 with identical BEFORE/AFTER state for `description`, `homepage`, and topic set. Evidence log: `temp/sessions/20260516_223859_S0214_phase02_idempotency.txt`.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Live `https://api.github.com/repos/SerZhyAle/FastMediaSorter_mob_v2` returns the new `description`, `topics`, `homepage` values.
- [x] Re-running `apply-github-store-metadata.ps1` with no arguments is a no-op (idempotency confirmed).
- [x] Dev log entry added for `scripts/release/apply-github-store-metadata.ps1` via `.\scripts\add_to_dev_log.ps1`.
- [x] `Grep -n "TODO(phase-02)"` returns zero hits.

---

## Handoff Notes to Next Phase

Phase 03 (release publisher) and Phase 05 (README badge) operate independently of this phase. Phase 06 (final cleanup) confirms metadata applier is recorded in the dev log.

---

## Rollback Plan

The script itself: `git rm scripts/release/apply-github-store-metadata.ps1`. The applied GitHub-side metadata can be reverted by running the script with previous DECISIONS.md values, or manually via the GitHub UI. No data migration involved.
