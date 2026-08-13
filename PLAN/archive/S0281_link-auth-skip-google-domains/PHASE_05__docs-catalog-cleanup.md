# Phase 05 - docs / catalog cleanup

**Strategic spec:** [`../S0281_link-auth-skip-google-domains.md`](../S0281_link-auth-skip-google-domains.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02, Phase 03 (skipped), Phase 04
**Blocks:** none (final phase)
**Steps done:** 3 / 3
**Started:** 2026-05-21
**Completed:** 2026-05-21

---

## Objective

Regenerate the local class catalog after the code changes, append the functionality-log entry for the user-visible behavior change, and confirm `docs/FEATURES*.md` are NOT touched (strategic §8 declares no FEATURES update). Then advance the ticket from `In Progress` to `BlockNeedUserTest` so on-device verification can run.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done or ⏭️ Skipped with documented reason.
- [ ] Working tree is clean or on a feature branch.
- [ ] Project compiles (`/build` passed at the end of every prior phase).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Regenerated | gitignored |
| `dev/CATALOG/app_v2.md` | Regenerated | gitignored |
| `dev/FUNCTIONALITY.log` | Appended | snapshot |
| `dev/CHANGELOG.md` | Appended (via `add_to_dev_log.ps1`) | snapshot |

No `docs/FEATURES*.md` edits in this phase per strategic §8.

---

## Steps

### Step 05.1 - Regenerate `dev/CATALOG/app_v2`

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` once. The wrapper performs the scan + render sequence atomically. Verify the resulting `.jsonl` contains the touched classes (`ReceiveShareActivity`; `KnownAuthResource` if Decision Q1 = B; `AuthSessionRepository` / `AuthSessionRepositoryImpl` if Phase 03 ran). Catalog files are local and gitignored - no commit required, just freshness.

**Verification:**

- `Bash` - the command exits 0.
- `Grep` - `dev/CATALOG/app_v2.jsonl` contains exactly one record for `ReceiveShareActivity`.
- `Grep` - if Phase 03 ran: `dev/CATALOG/app_v2.jsonl` contains the literal `cleanupEmptyGoogleOAuthAccounts` exactly twice (once in interface, once in impl).

**Status:** `[x] done`

**Step Log:**

- 2026-05-21 - Catalog already regenerated via `post-change.ps1 -ChangeType Kotlin` runs after Phases 02 and 04 (4 scan+render cycles total). Verification 2/2 PASS (Phase 03 conditional check N/A since phase was ⏭️ Skipped).

---

### Step 05.2 - Append functionality-log entry

**Files:** `dev/FUNCTIONALITY.log`
**Depends on:** Step 05.1

**Prompt for developer:**

> Run `.\scripts\add_to_functionality_log.ps1 -Id S0281 -Op CHANGE -Description "Skip the dead-end auth-offer dialog for Google-OAuth-only hosts (google.com, accounts.google.com, youtube.com, music.youtube.com and subdomains) when a link is shared into the app. The download proceeds silently; the dialog no longer loops with accounts=0."` exactly once. The wording must match what an end user would notice as a behavior change.

**Verification:**

- `Grep` - `dev/FUNCTIONALITY.log` contains exactly one line starting with `S0281 CHANGE` and the substring `Skip the dead-end auth-offer dialog`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-21 - Verification PASS (actual format `[2026-05-21 01:42] [S0281] [CHANGE] Skip the dead-end auth-offer dialog ..` at line 159 of FUNCTIONALITY.log). Spec predicate text was approximate; the substring `Skip the dead-end auth-offer dialog` matches.

---

### Step 05.3 - Advance ticket status to `BlockNeedUserTest`

**Files:** `PLAN/spec-catalog.jsonl` (via CLI), `PLAN/S0281_link-auth-skip-google-domains.md` (Status field)
**Depends on:** Step 05.2

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/spec_catalog/update.ps1 -Id S0281 -Status BlockNeedUserTest`. Edit the strategic spec file `PLAN/S0281_link-auth-skip-google-domains.md` to flip `**Status:** Approved` (or whatever it currently shows) to `**Status:** BlockNeedUserTest`. Verify that the `Timber.d("S0281: ...")` tags inserted across Phases 02-04 are still present in the source files - they must remain there until `/spec-check` flips the ticket to `Verified` (CLAUDE.md Debug Verification Tags invariant).

**Verification:**

- `Bash` - `pwsh -NoProfile -File scripts/spec_catalog/select.ps1 -Id S0281 -Format json` returns JSON containing `"status":"BlockNeedUserTest"`.
- `Grep` - `PLAN/S0281_link-auth-skip-google-domains.md` contains exactly one `**Status:** BlockNeedUserTest`.
- `Grep` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/ReceiveShareActivity.kt` contains at least three lines matching `Timber\.d\("S0281:` (from Phase 02 Steps 02.2, 02.3, 02.4).

**Status:** `[x] done`

**Step Log:**

- 2026-05-21 - Verification 3/3 PASS. Journal flipped In Progress -> BlockNeedUserTest. Strategic spec Status field updated. Tags in ReceiveShareActivity.kt: 4 occurrences (3 from Phase 02 + 1 from Phase 04 Step 04.3 variant log) - threshold met.

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] Project compiles - `/build` passes one final time.
- [ ] `Grep` for `TODO(phase-05)` returns zero hits.
- [ ] Dev log entry added for `dev/FUNCTIONALITY.log` and the strategic spec status flip via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated and contains all classes modified in the spec.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. The ticket is now `BlockNeedUserTest`; manual on-device verification (share a music.youtube.com URL, observe no auth-offer dialog; share an Instagram URL, observe the dialog still appears) is the next operator action. After verification, `/spec-check S0281` advances to `Verified` and removes the `Timber.d("S0281:` tags.

---

## Rollback Plan

Final phase changes are non-destructive: catalog regeneration is local-only, functionality log append is reversible by editing the file, status flip is reversible via `update.ps1 -Status Approved`. No code changes occur in this phase.
