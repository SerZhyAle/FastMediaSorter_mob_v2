# Phase 01 - Search query launch layer

**Strategic spec:** [`../S1566_launcher-google-search-widget.md`](../S1566_launcher-google-search-widget.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 2 / 2
**Started:** 2026-08-11
**Completed:** 2026-08-11

---

## Objective

Introduce the shared layer that turns a query string into an opened search page, with a determinate false
result when nothing can open it. No gadget, no layout, no user-visible string yet.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/search/WebSearchLaunchManager.kt` | New | ≤ 90 |
| `app_v2/src/testLauncherEnabled/java/com/sza/fastmediasorter/ui/launcher/search/WebSearchLaunchManagerTest.kt` | New | ≤ 80 |

> Backup / split thresholds: see Constraints (>500 LOC → backup step, >1500 LOC → split via Manager pattern).
>
> **Flavor placement.** Both files live in the `launcherEnabled` source set, which is the gate for `standard`
> and `noLegal`. Nothing goes to `src/main`: `SUPPORT_LAUNCHER` is off in `lite`, `photos`, `legacy` and `vr`,
> and a gadget never branches on the flag.

---

## Steps

### Step 01.1 - Add `WebSearchLaunchManager`

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/search/WebSearchLaunchManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `WebSearchLaunchManager` with an `@Inject constructor()` and a single public entry point
> `fun launch(context: Context, query: String): Boolean`. Blank query returns `false` without touching the
> system. Otherwise build the search page URL through a companion function
> `buildSearchUrl(query: String): String` that percent-encodes the trimmed query with
> `java.net.URLEncoder.encode(query, Charsets.UTF_8.name())` and appends it to the constant
> `https://www.google.com/search?q=`, open it with `Intent(Intent.ACTION_VIEW, url.toUri())` plus
> `FLAG_ACTIVITY_NEW_TASK`, and return whether it started. Before starting, check
> `context.packageManager.resolveActivityCompat(intent)`; a null result returns `false` after one
> `Timber.i` line. Wrap `startActivity` in `runCatching` and return `false` on failure after one `Timber.w`.
> Do not query installed search applications and do not fall back to `ACTION_WEB_SEARCH` or `ACTION_ASSIST`.

**Why:**

Strategic ADR-3 fixes one behaviour on every device and flavor - the query always goes to the browser search
page and an installed search application is never consulted - so that `noLegal` without Google services
behaves identically to `standard`; the boolean result is what lets §11.3 hold, where a device with no browser
says so instead of leaving a dead cell.

**Verification:**

- `Glob` - `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/search/WebSearchLaunchManager.kt` exists.
- `Grep` - `class WebSearchLaunchManager @Inject constructor` matches exactly once.
- `Grep` - `fun launch(context: Context, query: String): Boolean` present.
- `Grep` - `fun buildSearchUrl(query: String): String` present.
- `Grep` - `ACTION_WEB_SEARCH` returns zero hits in that file.
- `Grep` - `getLaunchIntentForPackage` returns zero hits in that file.
- `Grep` - `Log\.d\(` returns zero hits in that file.

**Status:** `[x]` done

**Step Log:**

- 2026-08-11 - Verification 6\6 PASS. Files: app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/search/WebSearchLaunchManager.kt (+54 LOC, new). Dev log recorded.

---

### Step 01.2 - Cover URL building with a unit test

**Files:** `app_v2/src/testLauncherEnabled/java/com/sza/fastmediasorter/ui/launcher/search/WebSearchLaunchManagerTest.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add a JUnit test for `WebSearchLaunchManager.buildSearchUrl` covering three cases: a plain single word, a
> multi-word query whose spaces are encoded, and a query carrying characters that must be percent-encoded
> such as `&`, `?` and a non-Latin word. Assert the full expected URL string each time, not a substring.
> Plain JUnit with `org.junit.Test`, the same shape as `LauncherStarterSetsParityTest` in this source set.
> Do not add a Robolectric runner.

**Encoder constraint (verified 2026-08-11, do not substitute `Uri.encode`):** the module sets
`isReturnDefaultValues = true`, so `android.net.Uri.encode` returns null under plain JUnit and the assertion
would pass or fail for the wrong reason; the only alternative is a Robolectric runner, and the unit suite
already exhausts its worker heap. `URLEncoder` is pure JVM, which is why step 01.1 specifies it.

**Why:**

Research records that no unit test exists for any gadget class, and URL assembly is the only pure logic this
ticket produces; an encoding bug here silently sends a wrong query rather than failing loudly, which no device
test would catch reliably.

**Verification:**

- `Glob` - `app_v2/src/testLauncherEnabled/java/com/sza/fastmediasorter/ui/launcher/search/WebSearchLaunchManagerTest.kt` exists.
- `Grep` - `buildSearchUrl` matches at least three times in that file.
- `.\a.ps1 fu` - the new test class passes.

**Status:** `[x]` done

**Step Log:**

- 2026-08-11 - Verification 3\3 PASS. Files: app_v2/src/testLauncherEnabled/java/com/sza/fastmediasorter/ui/launcher/search/WebSearchLaunchManagerTest.kt (+38 LOC, new). Ran targeted instead of the full suite: `scripts/builders/check-standard-fast.ps1 -Mode Unit -Tests "*WebSearchLaunchManagerTest*"`, BUILD SUCCESSFUL in 1m38s; result XML records tests=3 failures=0 errors=0 at 12:20:41Z. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - proven by `testStandardDebugUnitTest`, which compiles `src/main` and
      `src/launcherEnabled` before it can run and reported BUILD SUCCESSFUL at 14:20. A separate `dq` was not
      run: this phase added no resource, manifest or packaging surface, so it would recompile the same sources
      for no new information.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated - `catalog-sync` ran inside both `post-change` calls, 2771 records.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings. Layer 1: naming follows the Manager suffix, the
      class stays at 54 LOC, both functions hold two returns against detekt's limit, Timber is the only logger.
      Layer 3: `Context` is a call parameter and is never held as a field, so the singleton cannot leak an
      Activity. Layers 2 and 4 not applicable - no coroutine, listener, or Room surface in this phase.

---

## Handoff Notes to Next Phase

`WebSearchLaunchManager.launch` returns `false` for every reason a search cannot start - blank query, nothing
resolves, or the start throws. Phase 02 renders one message for that single false, and does not attempt to
distinguish the causes on screen.

---

## Rollback Plan

Revert phase commit(s) - two new files, no existing behaviour touched, no data migration and no user-facing
surface changed.
