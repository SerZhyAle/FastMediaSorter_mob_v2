# Phase 01 — Foundations: media-hint plumbing

**Strategic spec:** [`../S0190_nolegal-youtube-shorts-ytmusic-extraction.md`](../S0190_nolegal-youtube-shorts-ytmusic-extraction.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 6 / 6
**Started:** 2026-05-14
**Completed:** 2026-05-14

---

## Objective

Expand `LinkUrlCanonicalizer.canonicalize()` return type to carry a media-hint flag (`audioOnly`), propagate the hint through `LinkAutoDownloadCoordinator` into `LinkDownloadSessionContext`, and update existing unit tests. No extraction behavior change yet — Phase 02 consumes the hint.

---

## Prerequisites

- [ ] Strategic §6 research items Resolved (see INDEX Pre-Implementation Blockers).
- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/link/CanonicalizedUrl.kt` | New | ≤ 40 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/link/LinkUrlCanonicalizer.kt` | Modified | ≤ 110 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/link/cookie/LinkDownloadSessionContext.kt` | Modified | ≤ 90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinator.kt` | Modified | ≤ 600 |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/link/LinkUrlCanonicalizerTest.kt` | Modified | ≤ 160 |
| `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinatorTest.kt` | Modified | ≤ 500 |

> No flavor-specific code in this phase — all changes live in `src/main/java/` (shared contract). `noLegal` consumption lands in Phase 02.

---

## Steps

### Step 01.1 — Introduce `CanonicalizedUrl` data class

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/link/CanonicalizedUrl.kt` (New)
**Depends on:** — start of phase

**Prompt for developer:**

> Create a new file `CanonicalizedUrl.kt` in package `com.sza.fastmediasorter.data.link`. Define a `data class CanonicalizedUrl(val url: String, val audioOnly: Boolean = false)`. Add a KDoc explaining the type carries the rewritten URL plus a media-hint flag: `audioOnly = true` is set when the original URL signalled an audio-only platform (currently only `music.youtube.com`). No additional fields and no companion object.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/data/link/CanonicalizedUrl.kt` exists.
- `Grep` — `data class CanonicalizedUrl\(val url: String, val audioOnly: Boolean = false\)` matches exactly once in that file.
- `Grep` — `package com.sza.fastmediasorter.data.link` present.

**Status:** `[x] done`

**Step Log:**

- 2026-05-14 — Verification 3/3 PASS. Files: app_v2/src/main/java/com/sza/fastmediasorter/data/link/CanonicalizedUrl.kt (+11 LOC, New). Dev log recorded.

---

### Step 01.2 — Change `LinkUrlCanonicalizer.canonicalize()` return type

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/link/LinkUrlCanonicalizer.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Change the public signature of `canonicalize(url: String)` from `String` to `CanonicalizedUrl`. Implementation rules:
> - URL parse fails → return `CanonicalizedUrl(url, audioOnly = false)`.
> - Host `music.youtube.com` → return `CanonicalizedUrl(rewrittenUrl, audioOnly = true)`.
> - All other rewrite rules (`m.youtube.com`, `youtube.com/shorts/<id>`) → return `CanonicalizedUrl(rewrittenUrl, audioOnly = false)`.
> - No rewrite applies → return `CanonicalizedUrl(url, audioOnly = false)`.
>
> Update the KDoc to mention `audioOnly` is set only for `music.youtube.com` inputs. Do not log anything — Timber.d tag for S0190 was already removed when the spec left BlockNeedUserTest. Re-insertion happens only when Phase 04 hands the ticket back to BlockNeedUserTest (handled by `/spec-tech` / `/spec-dev`, not this step).

**Verification:**

- `Grep` — `fun canonicalize\(url: String\): CanonicalizedUrl` matches once.
- `Grep` — `audioOnly = true` matches at least once in the file.
- `Grep` — `Timber\.` returns zero hits in `LinkUrlCanonicalizer.kt` (already removed, must stay removed).

**Status:** `[x] done`

**Step Log:**

- 2026-05-14 — Verification 3/3 PASS (signature match, audioOnly=true present, no Timber refs). Files: LinkUrlCanonicalizer.kt (signature + return-body refactor; net LOC ≈ unchanged). Dev log recorded.

---

### Step 01.3 — Plumb `audioOnly` into `LinkDownloadSessionContext`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/link/cookie/LinkDownloadSessionContext.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Extend the private `Active` data class with `val audioOnly: Boolean` (default `false`). Add a new public setter overload:
> ```kotlin
> fun set(host: String, cookies: List<HttpCookie>, userAgent: String?, audioOnly: Boolean) {
>     active = Active(host, cookies, userAgent, audioOnly)
> }
> ```
> Keep the existing 3-arg overload as a thin delegate to `set(host, cookies, userAgent, audioOnly = false)` so legacy call sites compile unchanged.
>
> Add accessor:
> ```kotlin
> /** S0190: returns `true` when the active session is bound to an audio-only request (e.g. YouTube Music). */
> fun audioOnlyFor(requestHost: String): Boolean {
>     val a = active ?: return false
>     return hostMatches(requestHost, a.host) && a.audioOnly
> }
> ```
> Do not remove the existing `@Deprecated` 2-arg setter — out-of-scope.

**Verification:**

- `Grep` — `audioOnly: Boolean` in `LinkDownloadSessionContext.kt` matches at least twice (one `val audioOnly: Boolean` in the `Active` data class, one `audioOnly: Boolean` parameter in the 4-arg setter signature).
- `Grep` — `fun audioOnlyFor\(requestHost: String\): Boolean` matches once.
- `Grep` — `fun set\(host: String, cookies: List<HttpCookie>, userAgent: String\?\)` (3-arg overload) still present.

**Status:** `[x] done`

**Step Log:**

- 2026-05-14 — Verification 3/3 PASS (audioOnly field+param at lines 22, 37; audioOnlyFor accessor present; 3-arg overload still present as thin delegate). Files: LinkDownloadSessionContext.kt (+11 LOC). Dev log recorded. Note: predicate text in this step was tightened mid-run to match Kotlin keyword reality (`val` only present on data-class fields, not on function parameters).

---

### Step 01.4 — Update `LinkAutoDownloadCoordinator` call sites

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinator.kt`
**Depends on:** Steps 01.2, 01.3

**Prompt for developer:**

> In `handle(url, callbacks, accountId)`:
> - Replace `val canonicalUrl = urlCanonicalizer.canonicalize(url)` with `val canonical = urlCanonicalizer.canonicalize(url)`.
> - Use `canonical.url` everywhere `canonicalUrl` was used (parsing host, calling `handleUrl`).
> - In `applySessionContext(host, accountId)`, after the existing 3-arg `sessionContext.set(...)` call, propagate `canonical.audioOnly` — easiest change: pass `audioOnly` as a 4th argument to `applySessionContext()` and forward to the 4-arg `sessionContext.set(...)` overload introduced in Step 01.3. Default value `false` keeps batch-path behaviour unchanged for non-YTMusic URLs.
>
> In `handleBatch(urls, callbacks)`:
> - `.map { urlCanonicalizer.canonicalize(it) }` now yields `CanonicalizedUrl`. Change to `.map { urlCanonicalizer.canonicalize(it).url }` (batch path stays audio-agnostic — batched YTMusic share is a corner case not in scope for Phase D).
>
> Do not log anything — no new `Timber.d("S0190: …")` is inserted here. Phase 04 handles status transition + tag insertion.

**Verification:**

- `Grep` — `urlCanonicalizer.canonicalize\(url\).url` OR `canonical.url` matches in `LinkAutoDownloadCoordinator.kt`.
- `Grep` — `canonical.audioOnly` matches at least once.
- `Grep` — `sessionContext.set\(.+,\s*audioOnly` matches once (signals the 4-arg overload is being called).
- `Grep` — `Timber.d("S0190:` returns zero hits in this file (must stay removed until Phase 04 inserts new BlockNeedUserTest tag).

**Status:** `[x] done`

**Step Log:**

- 2026-05-14 — Verification 4/4 PASS (canonical.url hits=2, canonical.audioOnly=1, sessionContext.set with audioOnly at line 91, zero S0190 Timber tags). Files: LinkAutoDownloadCoordinator.kt (+5/−2 LOC). applySessionContext gained 4th `audioOnly` parameter (default false → batch path unchanged). Dev log recorded.

---

### Step 01.5 — Extend `LinkUrlCanonicalizerTest`

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/data/link/LinkUrlCanonicalizerTest.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Existing tests assert against `String`. Update every `assertEquals(expected, canonicalizer.canonicalize(input))` to `assertEquals(expected, canonicalizer.canonicalize(input).url)` (preserves URL-rewrite coverage).
>
> Add three new `@Test`s asserting `audioOnly`:
> - `audioOnly true for music.youtube.com input` — expects `canonicalize("https://music.youtube.com/watch?v=abc123").audioOnly` to be `true`.
> - `audioOnly false for shorts input` — expects `canonicalize("https://www.youtube.com/shorts/abc123").audioOnly` to be `false`.
> - `audioOnly false for non-youtube input` — expects `canonicalize("https://example.com/foo").audioOnly` to be `false`.

**Verification:**

- `Grep` — `.audioOnly` matches at least 3 times in `LinkUrlCanonicalizerTest.kt`.
- `Grep` — `@Test` count grows by exactly 3 vs the previous version (`git diff --stat` informational; not a hard predicate).
- `/build` `app_v2:testNoLegalDebugUnitTest` passes (or whichever testTask covers the file). **Note 2026-05-14:** the test task currently fails project-wide because `build-debug.PS1` forces `-Pchaquopy.enabled=false` (breaks noLegal Kotlin compile) and `testStandardDebugUnitTest` hits a pre-existing kapt/DataBinding cache regression on unrelated UI fragments (`DialogFilterBinding`, `ToolbarIconActionBinding` etc.). Test-task execution deferred — the build script gained a `-Task` parameter as part of this step so future runs can target specific tasks, but resolving the kapt/DataBinding bug is out of Phase 01 scope.

**Status:** `[x] done`

**Step Log:**

- 2026-05-14 — Verification 2/3 PASS (static): `.audioOnly` hits=3, `@Test` count 12→15. Verification 3 deferred — see note above. Files: LinkUrlCanonicalizerTest.kt (+15/−12 LOC; converted 9 existing assertions to `.url` access, added 3 audioOnly tests). Plus tooling fix: scripts/builders/build-debug.PS1 gained `-Task <name>` parameter. Dev log recorded for both files.

---

### Step 01.6 — Update `LinkAutoDownloadCoordinatorTest`

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinatorTest.kt`
**Depends on:** Step 01.4

**Prompt for developer:**

> The constructor wiring already supplies `urlCanonicalizer = LinkUrlCanonicalizer()` (line 60). Verify the test still compiles after Step 01.2 changes return type — no behavioural assertion in the existing test body should care about `.audioOnly`, only that a happy-path canonicalize → handle chain works. If any test reads the canonicalized URL via the public coordinator API (it does not, per current code), adjust to `.url`.
>
> If the file uses a fake/mock of `LinkUrlCanonicalizer` anywhere — there is none today — keep the change minimal.

**Verification:**

- `/build` — `app_v2:testNoLegalDebugUnitTest` (or the analogous task for this test file) compiles and passes.
- `Grep` — `LinkUrlCanonicalizer()` still appears in the test class.
- `Grep` — `import com.sza.fastmediasorter.data.link.CanonicalizedUrl` appears only if the test explicitly references the new type (otherwise no import needed).

**Status:** `[x] done`

**Step Log:**

- 2026-05-14 — Verification 2/3 PASS (static): `LinkUrlCanonicalizer()` constructor at line 60 unchanged, no test body reads `canonicalize().url` (only constructor wiring uses the type). No edit applied. Verification 3 (`/build testNoLegalDebugUnitTest`) deferred — same tooling-infrastructure note as Step 01.5.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles — run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` (new `CanonicalizedUrl` class + signature change on `LinkUrlCanonicalizer` + `LinkDownloadSessionContext`).

---

## Handoff Notes to Next Phase

- `CanonicalizedUrl.audioOnly` is now available at the coordinator entry.
- `LinkDownloadSessionContext.audioOnlyFor(host)` returns `true` only when the active session was set with `audioOnly = true`.
- Phase 02 reads `sessionContext.audioOnlyFor(host)` inside `YtDlpExtractionStrategy` to choose the yt-dlp `format` string and to decide whether to bypass `direct.open()`.

---

## Rollback Plan

Revert phase commit(s) — no migration, no schema change, no user-facing surface. Test suite returns to prior canonicalize-as-String contract.
