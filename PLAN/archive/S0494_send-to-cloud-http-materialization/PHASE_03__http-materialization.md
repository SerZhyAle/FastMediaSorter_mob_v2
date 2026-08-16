# Phase 03 - http(s) materialization

**Strategic spec:** [`../S0494_send-to-cloud-http-materialization.md`](../S0494_send-to-cloud-http-materialization.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 4 / 4
**Started:** 2026-08-15
**Completed:** 2026-08-15

---

## Objective

Materialize an `http(s)://` source into a local cache copy so «Отправить в..» works for direct web files, leaving manifest and live URLs on the existing failure path.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/link/HttpFileDownloader.kt` | New | ≤ 180 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/MaterializeShareContentUseCase.kt` | Modified | ≤ 190 |
| `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/MaterializeShareContentHelpersTest.kt` | Modified | ≤ 90 |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/link/HttpFileDownloaderTest.kt` | New | ≤ 150 |

---

## Steps

### Step 03.1 - Add HttpFileDownloader over the shared link-download client

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/link/HttpFileDownloader.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `HttpFileDownloader` as an `@Inject`-constructed class taking the `@Named("linkDownload") OkHttpClient` already provided by `LinkDownloadModule`. Expose `suspend fun download(url: String, target: File, onProgress: ((Int) -> Unit)?): Boolean` that issues a GET, streams the body to `target` in 64 KB chunks on `Dispatchers.IO`, reports percent from the response `Content-Length` when it is positive, deletes a partial file on any failure, and returns false on a non-2xx response or an `IOException`. Do not route through `DirectFileExtractionStrategy`.

**Why:**

INDEX ADR-1 records why the extraction strategy is not reused: its `MediaMimeWhitelist` gate exists to protect link ingest from arbitrary web content and would refuse legitimate shares of a file the user already has open.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/data/link/HttpFileDownloader.kt` exists.
- `Grep` - `class HttpFileDownloader` matches exactly once in that file.
- `Grep` - `suspend fun download(` present in that file.
- `Grep` - `linkDownload` present in that file.
- `Grep` - `Log\.d\(` returns zero hits in that file.

**Status:** `[x]` done

---

### Step 03.2 - Accept http(s) in the scheme gate, excluding manifests and live URLs

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/MaterializeShareContentUseCase.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Add `isHttpScheme(path)` to the companion object and include it in `isDownloadableScheme`, but return false for a URL whose path ends in a streaming manifest extension (`.m3u8`, `.mpd`, `.ism`) so live and adaptive sources keep the current failure path. Update the companion KDoc that currently declares http(s) out of scope.

**Why:**

INDEX ADR-2 records that a manifest or live stream has no finite file to hand a receiver, so admitting it into materialization would download an unbounded body and still produce nothing shareable.

**Verification:**

- `Grep` - `internal fun isHttpScheme(` present in that file.
- `Grep` - `m3u8` present in that file.
- `Grep` - `http\(s\)://\) return` returns zero hits in that file.

**Status:** `[x]` done

---

### Step 03.3 - Route the http(s) branch in downloadTo

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/MaterializeShareContentUseCase.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Inject `dagger.Lazy<HttpFileDownloader>` and extend `downloadTo` with an http(s) branch that calls `download(sourcePath, targetFile, onProgress)` and returns `targetFile` on success or null on failure. Keep the existing cloud and network branches unchanged, and keep the injection lazy so flavors and flows that never share a web file do not construct the OkHttp stack.

**Why:**

Strategic §2 requires the download primitive to be reachable from materialization; without this branch the scheme gate would admit http(s) and then fail at the router, which is a worse outcome than today's honest refusal.

**Verification:**

- `Grep` - `httpDownloader` present in that file.
- `Grep` - `Lazy<HttpFileDownloader>` present in that file.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

---

### Step 03.4 - Update and extend unit coverage

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/MaterializeShareContentHelpersTest.kt`, `app_v2/src/test/java/com/sza/fastmediasorter/data/link/HttpFileDownloaderTest.kt`
**Depends on:** Step 03.3

**Prompt for developer:**

> Flip the two assertions in `MaterializeShareContentHelpersTest` that pin http and https as not downloadable, and add cases asserting that `.m3u8` and `.mpd` URLs stay rejected. Add `HttpFileDownloaderTest` driving `download` against `MockWebServer` for three cases: a 200 response with `Content-Length` writes the body and reports percent, a 404 returns false and leaves no file behind, and a body that fails mid-stream leaves no partial file.

**Why:**

`MaterializeShareContentHelpersTest` is the only coverage of the scheme gate and currently asserts the opposite of the new behaviour, so it fails the moment step 03.2 lands.

**Verification:**

- `Grep` - `assertTrue` with `http` present in `MaterializeShareContentHelpersTest.kt`.
- `Grep` - `m3u8` present in `MaterializeShareContentHelpersTest.kt`.
- `Glob` - `app_v2/src/test/java/com/sza/fastmediasorter/data/link/HttpFileDownloaderTest.kt` exists.
- `.\a.ps1 fu` - both test classes pass.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Deviations recorded during implementation

- `mockwebserver` is declared only as an `androidTestImplementation`, so it is unavailable to unit tests and no new dependency was added. `HttpFileDownloaderTest` instead mocks `OkHttpClient`/`Call` with mockk and builds synthetic `Response` objects, matching the neighbouring `DirectFileExtractionStrategyTest`. All three required cases plus a malformed-URL case are covered.
- Phase 01 changed `CloudFileOperationHandler`'s constructor without updating `CloudFileOperationHandlerTest`, which broke the whole unit-test source set and made this phase's test verification impossible until fixed. One argument was added to the test's constructor call. Phase 01 debt, repaired here.
- `isHttpScheme` strips query and fragment and lower-cases before matching the manifest extensions, because a signed CDN URL such as `https://host/live.m3u8?token=..` would otherwise pass the gate and start downloading an endless manifest, contradicting ADR-2.
- `MaterializeShareContentUseCase` ended at 196 LOC against a 190 budget: the cloud branch moved into its own private function so `downloadTo` stays a three-branch `when`. Behaviour unchanged; the budget overrun is accepted as the more readable shape.

---

## Handoff Notes to Next Phase

Every scheme the share panel can produce now has a download primitive except streaming manifests, which stay on the documented failure path.

---

## Rollback Plan

Revert the phase commit - the new class has one caller and no persisted state.
