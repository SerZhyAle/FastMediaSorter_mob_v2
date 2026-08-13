# Phase 02 — Add YouTube to KnownAuthResources

**Strategic spec:** [`../S0187_nolegal-youtube-extraction-recovery.md`](../S0187_nolegal-youtube-extraction-recovery.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 2 / 2
**Started:** 2026-05-14
**Completed:** 2026-05-14

---

## Objective

Register `youtube.com` in `KnownAuthResources.all` so that YouTube and YouTube Music URLs route through the account-picker flow (`[S0166] known social`), and extend `KnownAuthResourcesTest` with YouTube coverage.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/link/auth/KnownAuthResources.kt` | Modified | ≤ 75 |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/link/auth/KnownAuthResourcesTest.kt` | Modified | ≤ 55 |

---

## Steps

### Step 02.1 — Add youtube.com entry to KnownAuthResources.all

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/link/auth/KnownAuthResources.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Append a new `KnownAuthResource` entry inside `KnownAuthResources.all` list, after the existing
> `Facebook` entry and before the closing `)` of `listOf(...)`:
>
> ```kotlin
> KnownAuthResource(
>     displayName = "YouTube",
>     host = "youtube.com",
>     loginUrl = "https://accounts.google.com/ServiceLogin?service=youtube",
>     // YouTube videos are publicly accessible — SocialPreviewOnly signals
>     // age-restriction or private content, not a mandatory login wall.
> ),
> ```
>
> `matchHost("music.youtube.com")` already resolves via the `.endsWith(".youtube.com")` branch —
> no separate entry needed. `matchHost("youtu.be")` does NOT match (different eTLD+1) — out of scope.

**Verification:**

- `Grep` — `"youtube.com"` matches exactly once inside `KnownAuthResources.kt` (the `host =` line).
- `Grep` — `displayName = "YouTube"` matches exactly once in `KnownAuthResources.kt`.
- `Grep` — `Log\.d\(` returns zero hits in `KnownAuthResources.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-14 — Verification 3/3 PASS. Files: KnownAuthResources.kt (+9 LOC). Dev log recorded.

---

### Step 02.2 — Extend KnownAuthResourcesTest with YouTube coverage

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/data/link/auth/KnownAuthResourcesTest.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add a new `@Test` method to `KnownAuthResourcesTest` that covers YouTube host matching:
>
> ```kotlin
> @Test
> fun `youtube and music youtube resolve to youtube entry`() {
>     assertEquals("youtube.com", KnownAuthResources.matchHost("youtube.com")?.host)
>     assertEquals("youtube.com", KnownAuthResources.matchHost("www.youtube.com")?.host)
>     assertEquals("youtube.com", KnownAuthResources.matchHost("music.youtube.com")?.host)
>     assertFalse(KnownAuthResources.isPreviewSensitiveHost("youtube.com"))
> }
> ```
>
> No other changes to the file. Do not add a test for `youtu.be` — it is out of scope.

**Verification:**

- `Grep` — `youtube and music youtube resolve to youtube entry` matches exactly once in `KnownAuthResourcesTest.kt`.
- `Grep` — `music.youtube.com` matches exactly once in `KnownAuthResourcesTest.kt`.
- `Grep` — `Log\.d\(` returns zero hits in `KnownAuthResourcesTest.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-14 — Verification 3/3 PASS. Files: KnownAuthResourcesTest.kt (+9 LOC). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles — BUILD SUCCESSFUL in 48s.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entries added for `KnownAuthResources.kt` and `KnownAuthResourcesTest.kt` via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

- `KnownAuthResources.matchHost("youtube.com")` and `matchHost("music.youtube.com")` now both return
  the YouTube `KnownAuthResource` entry.
- `ReceiveShareActivity.routeSingleLinkAutoDownload()` will log `[S0166] known social: host=youtube.com`
  and route through `maybeOfferAuthThenDownload()`.
- `[S0166] unknown host, standard pipeline: host=youtube.com` will no longer appear in logs.

---

## Rollback Plan

Revert changes to `KnownAuthResources.kt` and `KnownAuthResourcesTest.kt`. No data migration or user-facing surface changed.
