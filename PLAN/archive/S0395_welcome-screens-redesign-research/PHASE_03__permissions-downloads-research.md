# Phase 03 - Permissions & Downloads Research

**Strategic spec:** [`../S0395_welcome-screens-redesign-research.md`](../S0395_welcome-screens-redesign-research.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04, 05
**Steps done:** 2 / 2
**Started:** 2026-06-10
**Completed:** 2026-06-10

---

## Objective

Produce artifacts for strategic §6.5 (permission map + page ordering) and §6.7 (downloads during onboarding incl. store policy) - the two questions that can overturn the owner's draft page order.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done (`research/06__page4-functionality-toggles.md` exists - supplies the all-files-mode and download-trigger facts).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `PLAN/S0395_welcome-screens-redesign-research/research/05__permissions-ordering.md` | New | ≤ 400 |
| `PLAN/S0395_welcome-screens-redesign-research/research/07__onboarding-downloads.md` | New | ≤ 400 |

---

## Steps

### Step 03.1 - Map permissions per API level and derive page order

**Files:** `PLAN/S0395_welcome-screens-redesign-research/research/05__permissions-ordering.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Read `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PermissionsManagementFragment.kt`, the `getRequiredMediaPermissions()` logic in `WelcomeActivity.kt`, `app_v2/src/main/AndroidManifest.xml` and every flavor manifest (`app_v2/src/*/AndroidManifest.xml`). Build the full permission map: media-read permissions per API tier (23-32 vs 33+), all-files access (`MANAGE_EXTERNAL_STORAGE` - is it declared, in which flavors, what Play policy says for the published standard build - verify on developer.android.com/Play policy pages), notifications (API 33+, needed for download progress per artifact 07's subject), and anything else the manifest requests at runtime. Then resolve the ordering conflict from strategic §6.5: file-manager mode is chosen on page 4 AFTER permissions on page 3, and all-files consent cannot be granted before the choice exists. Author the artifact with `## Options` covering at least: functionality page before permissions page; staged re-request after page 4; defer all-files to first feature use. Conclude with a recommended page order and per-API behavior notes (legacy minSdk 23 included).

**Verification:**

- `Glob` - `research/05__permissions-ordering.md` exists under the ticket folder.
- `Grep` - `MANAGE_EXTERNAL_STORAGE` present.
- `Grep` - `## Options` present and `## Conclusion` present.

**Status:** `[x]` done

**Step Log:**

- 2026-06-10 - Verification 3/3 PASS. Key result: lazy all-files pattern means owner order is workable, but functionality-before-permissions (swap pages 3/4) is strictly better - adaptive batch, fewer system screens, download pipelining. Goes to SYNTHESIS as deviation #1.

---

### Step 03.2 - Research downloads during onboarding

**Files:** `PLAN/S0395_welcome-screens-redesign-research/research/07__onboarding-downloads.md`
**Depends on:** Step 03.1

**Prompt for developer:**

> Using artifact 06 (toggle → deliverable mapping) plus `delivery/INVENTORY.md` (real asset sizes), `domain/delivery/DeliverableSetDownloader.kt` (progress model) and the S0386 strategic spec's hosting facts (GitHub Release assets, SHA-256 pinning), author the artifact answering: when a download should start - immediately on toggle flip, on onboarding completion, or after explicit confirmation on a summary step; how progress is surfaced and whether onboarding can complete while downloads continue in background; offline and failure behavior (onboarding must never dead-end - strategic §3.2 forbids downloads blocking flow completion); interaction with the page-2 network choice (a user who just declined network kinds must not get silent downloads); realistic total download size/time for the typical toggle set; store-policy stance for release builds on downloading native `.so` payloads (S0386 already ships this from settings - record its policy reasoning and whether onboarding-triggered download changes anything). Conclude with a recommended download lifecycle for onboarding.

**Verification:**

- `Glob` - `research/07__onboarding-downloads.md` exists under the ticket folder.
- `Grep` - `offline` (case-insensitive) present.
- `Grep` - `## Conclusion` present.

**Status:** `[x]` done

**Step Log:**

- 2026-06-10 - Verification 3/3 PASS. Key results: enqueue-on-flip via WorkManager-class runner + inline progress; no dead-ends (Extensions Retry + first-use interception as fallbacks); page-2/S0391 has NO mechanical coupling to downloads; CRITICAL policy finding - OCR/FFmpeg .so direct GitHub download on Play-acquired standard installs violates Device-and-Network-Abuse policy → S0386 follow-up is a page-4 prerequisite for standard.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] No source/config file modified - S0395 changes confined to the ticket folder + dev/CHANGELOG.
- [x] Dev log entry added for each artifact via post-change.ps1 (Doc) - 05 and 07 recorded 2026-06-10.

---

## Handoff Notes to Next Phase

Artifacts 05 + 07 fix the page order and download lifecycle; Phase 04's flavor matrix and defaults strategy must be consistent with them.

---

## Rollback Plan

Delete the artifact files - no code or data surface touched.
