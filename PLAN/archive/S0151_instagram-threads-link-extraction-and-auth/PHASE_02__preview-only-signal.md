# Phase 02 — preview-only-signal

**Strategic spec:** [`../S0151_instagram-threads-link-extraction-and-auth.md`](../S0151_instagram-threads-link-extraction-and-auth.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 6 / 6
**Started:** —
**Completed:** —

---

## Objective

Introduce `OpenResult.SocialPreviewOnly` as a distinct outcome for HTML and dynamic extraction strategies when the only candidates from a known video-first social host are OG/image previews. Wire the coordinator to fall through on this outcome across strategies, then surface `Result.Failed.SocialPreviewOnly` when no real media was found after all strategies ran.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (`threads.com` present in `KnownAuthResources.all`).
- [ ] Working tree is clean or on a feature branch.
- [ ] Strategic §6.1 research blocker reviewed (implementation can proceed speculatively; outcome validated at `BlockNeedUserTest`).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/UrlExtractionStrategy.kt` | Modified | ≤ 90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/link/auth/KnownAuthResources.kt` | Modified | ≤ 75 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/link/HtmlPageExtractionStrategy.kt` | Modified | ≤ 345 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/link/InvisibleWebViewExtractionStrategy.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinator.kt` | Modified | ≤ 465 |

---

## Steps

### Step 02.1 — Add `OpenResult.SocialPreviewOnly` to `UrlExtractionStrategy.kt`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/UrlExtractionStrategy.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Add a new sealed interface subtype to `OpenResult` in `UrlExtractionStrategy.kt`:
>
> ```kotlin
> /**
>  * S0151: a known video-first social host returned only an OG/image preview — no real
>  * media content (video, audio, or streaming manifest) was found. The coordinator
>  * falls through to the next strategy; if all strategies return this, it surfaces
>  * [LinkAutoDownloadCoordinator.Result.Failed.SocialPreviewOnly] to the UI.
>  */
> data class SocialPreviewOnly(val host: String) : OpenResult
> ```
>
> Place it after `Batch` and before `NotFound`. No other changes to this file.

**Verification:**

- `Grep` — `data class SocialPreviewOnly` matches exactly once in `UrlExtractionStrategy.kt`.
- `Grep` — `Log\.d\(` returns zero hits in `UrlExtractionStrategy.kt`.

**Status:** `[ ]` not done

---

### Step 02.2 — Add `previewOnlyMeansLogin` flag + `isPreviewSensitiveHost()` to `KnownAuthResources`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/link/auth/KnownAuthResources.kt`
**Depends on:** Step 02.1

**Context:** strategic §6.3 Resolved → "applies to the whole registry, **but with judgement**": the registry already contains image-first sites (Pinterest, Flickr, DeviantArt, ArtStation, Tumblr, Reddit) where the OG/preview image IS the desired result — applying the rule to them would break image downloads there. So `KnownAuthResource` gets a per-entry `previewOnlyMeansLogin: Boolean` flag (default `false`), set `true` only for video/reel-first hosts (Instagram, Threads `.net`/`.com`, TikTok, X). The predicate `isPreviewSensitiveHost(host)` reads that flag. **If the speculative code already has `isVideoFirstHost()` with a hardcoded `VIDEO_FIRST_HOSTS` set, replace it (and its callers) with the flag-based `isPreviewSensitiveHost()`.**

**Prompt for developer:**

> In `KnownAuthResources.kt`:
>
> **1. Add the flag to `KnownAuthResource`:**
> ```kotlin
> data class KnownAuthResource(
>     val displayName: String,
>     val host: String,
>     val loginUrl: String,
>     /**
>      * S0151 §6.3: when true, an extraction result consisting solely of OG/image
>      * previews for this host is treated as "no real content" → the coordinator
>      * surfaces [LinkAutoDownloadCoordinator.Result.Failed.SocialPreviewOnly].
>      * Keep false for image-first sites where the preview image is the desired result.
>      */
>     val previewOnlyMeansLogin: Boolean = false,
> )
> ```
>
> **2. Set the flag in `all`** — `previewOnlyMeansLogin = true` for: `Instagram` (`instagram.com`), `Threads` (`threads.net`), `Threads` (`threads.com`), `TikTok` (`tiktok.com`), `X (Twitter)` (`x.com`). Leave the default `false` for `Pinterest`, `DeviantArt`, `Reddit`, `Tumblr`, `Flickr`, `ArtStation`.
>
> **3. Replace `isVideoFirstHost` / `VIDEO_FIRST_HOSTS`** with:
> ```kotlin
> /** S0151 §6.3: true iff [host] is a known auth resource flagged [KnownAuthResource.previewOnlyMeansLogin]. */
> fun isPreviewSensitiveHost(host: String?): Boolean = matchHost(host)?.previewOnlyMeansLogin == true
> ```
> Remove the old `VIDEO_FIRST_HOSTS` set and `isVideoFirstHost` function. Update all callers (`HtmlPageExtractionStrategy`, `InvisibleWebViewExtractionStrategy`, any test) to `isPreviewSensitiveHost(host)`. `matchHost()` and `all` (apart from the new flag values) are otherwise unchanged.

**Verification:**

- `Grep` — `previewOnlyMeansLogin` matches in `KnownAuthResources.kt` on the data-class field and on at least the `Instagram`, `threads.com`, `tiktok.com`, `x.com` entries.
- `Grep` — `fun isPreviewSensitiveHost` matches exactly once in `KnownAuthResources.kt`.
- `Grep` — `isVideoFirstHost` returns zero hits across `.kt` (old name + host set fully removed).
- `Grep` — `Log\.d\(` returns zero hits in `KnownAuthResources.kt`.

**Status:** `[ ]` not done

---

### Step 02.3 — Signal `SocialPreviewOnly` in `HtmlPageExtractionStrategy`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/link/HtmlPageExtractionStrategy.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> In `HtmlPageExtractionStrategy.open()`, after `CandidateSelectionPolicy.choose(filtered)` returns a non-null candidate, insert a social-preview-only guard before delegating to `direct.open()`. The guard fires only when (a) the host is in `KnownAuthResources.isPreviewSensitiveHost()` AND (b) the filtered candidate list contains no non-image candidates (i.e., every candidate has source `OG_IMAGE`, `IMG_TAG`, or `IMG_SRCSET`):
>
> ```kotlin
> // S0151: for known video-first social hosts, OG-image-only results are not real content.
> val host = httpUrl.host
> if (KnownAuthResources.isPreviewSensitiveHost(host)) {
>     val hasRealContent = filtered.any { c ->
>         c.source != HtmlMediaCandidate.Source.OG_IMAGE &&
>             c.source != HtmlMediaCandidate.Source.IMG_TAG &&
>             c.source != HtmlMediaCandidate.Source.IMG_SRCSET
>     }
>     if (!hasRealContent) {
>         LinkDownloadTrace.verbose(
>             "html-strategy social-preview-only host=${LinkDownloadTrace.truncateUrl(httpUrl.toString())}",
>         )
>         return OpenResult.SocialPreviewOnly(host = host)
>     }
> }
> ```
>
> Insert this block immediately before the final `return direct.open(chosen.url, onProgress)`. Streaming manifest candidates (`HLS_MANIFEST`, `DASH_MANIFEST`) are already excluded from the image-source list, so they are treated as real content. Add `import com.sza.fastmediasorter.data.link.auth.KnownAuthResources` to the file's import list.

**Verification:**

- `Grep` — `SocialPreviewOnly` matches in `HtmlPageExtractionStrategy.kt`.
- `Grep` — `KnownAuthResources.isPreviewSensitiveHost` matches in `HtmlPageExtractionStrategy.kt`.
- `Grep` — `Log\.d\(` returns zero hits in `HtmlPageExtractionStrategy.kt`.

**Status:** `[ ]` not done

---

### Step 02.4 — Signal `SocialPreviewOnly` in `InvisibleWebViewExtractionStrategy`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/link/InvisibleWebViewExtractionStrategy.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> In `InvisibleWebViewExtractionStrategy.open()`, after computing `merged` and before the `preferred` derivation, insert a social-preview-only guard. Replace the existing `preferred` derivation block:
>
> ```kotlin
> val preferred = merged.filterNot(::isImageCandidate).ifEmpty { merged }
> ```
>
> with:
>
> ```kotlin
> val nonImageCandidates = merged.filterNot(::isImageCandidate)
> // S0151: if the dynamic render produced only image candidates on a known video-first
> // social host, signal SocialPreviewOnly — do not fall back to downloading the preview.
> if (nonImageCandidates.isEmpty()) {
>     val host = httpUrl.host
>     if (KnownAuthResources.isPreviewSensitiveHost(host)) {
>         LinkDownloadTrace.verbose(
>             "dynamic-strategy social-preview-only host=${LinkDownloadTrace.truncateUrl(url)}",
>         )
>         return OpenResult.SocialPreviewOnly(host = host)
>     }
> }
> val preferred = nonImageCandidates.ifEmpty { merged }
> ```
>
> `httpUrl` is derived from `url.toHttpUrlOrNull()` — it is already computed at the start of `open()` for the `NonHttpScheme` guard, so extract it to a val at that point if it is not already one. Add `import com.sza.fastmediasorter.data.link.auth.KnownAuthResources` to the import list.

**Verification:**

- `Grep` — `SocialPreviewOnly` matches in `InvisibleWebViewExtractionStrategy.kt`.
- `Grep` — `nonImageCandidates` matches in `InvisibleWebViewExtractionStrategy.kt`.
- `Grep` — `KnownAuthResources.isPreviewSensitiveHost` matches in `InvisibleWebViewExtractionStrategy.kt`.
- `Grep` — `Log\.d\(` returns zero hits in `InvisibleWebViewExtractionStrategy.kt`.

**Status:** `[ ]` not done

---

### Step 02.5 — Handle `SocialPreviewOnly` in `LinkAutoDownloadCoordinator`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinator.kt`
**Depends on:** Steps 02.1, 02.3, 02.4

**Prompt for developer:**

> Make the following changes to `LinkAutoDownloadCoordinator`:
>
> **1. Add constructor parameter** — inject `AuthSessionRepository`:
> ```kotlin
> @Singleton
> class LinkAutoDownloadCoordinator @Inject constructor(
>     private val settingsRepository: SettingsRepository,
>     private val registry: LinkExtractionRegistry,
>     private val writer: LinkDownloadWriter,
>     private val streamingPipeline: StreamingPipeline,
>     private val authSessionRepository: com.sza.fastmediasorter.domain.repository.AuthSessionRepository,
> )
> ```
> `AuthSessionRepository` is already bound in `LinkDownloadStrategiesModule` — no new Hilt `@Module` needed.
>
> **2. Add result type** — inside the `Result.Failed` sealed interface, add:
> ```kotlin
> /**
>  * S0151: all extraction strategies returned only an OG/image preview for a known
>  * video-first social host. The UI should offer sign-in (or re-sign-in if a session
>  * existed) and retry the download.
>  */
> data class SocialPreviewOnly(
>     val host: String,
>     val originalUrl: String,
>     val hadExistingSession: Boolean,
> ) : Failed
> ```
>
> **3. Track social preview in `handleUrl()`** — declare `var socialPreviewHost: String? = null` before the strategy loop, then add a branch in the `when (val opened = ...)` block:
> ```kotlin
> is OpenResult.SocialPreviewOnly -> {
>     if (socialPreviewHost == null) socialPreviewHost = opened.host
>     Timber.v(
>         "LinkAutoDownloadCoordinator: %s social-preview-only host=%s, trying next",
>         strategy.id,
>         opened.host,
>     )
>     continue
> }
> ```
>
> **4. Return `SocialPreviewOnly` result** — replace `val stream = openedStream ?: return Result.Failed.NoMediaFound` with:
> ```kotlin
> val stream = openedStream
> if (stream == null) {
>     val previewHost = socialPreviewHost
>     if (previewHost != null) {
>         val hadSession = runCatching {
>             authSessionRepository.hasSession(previewHost)
>         }.getOrDefault(false)
>         return Result.Failed.SocialPreviewOnly(
>             host = previewHost,
>             originalUrl = url,
>             hadExistingSession = hadSession,
>         )
>     }
>     return Result.Failed.NoMediaFound
> }
> return writeStreamResult(stream = stream, settings = settings, callbacks = callbacks)
> ```
>
> **5. Update `renderFailureReason()` in `LinkAutoDownloadResultPresenter`** — this is handled in Phase 03 step 03.2. For compilation in this phase, add a temporary stub in the coordinator's `when` expression if the compiler requires exhaustiveness — `SocialPreviewOnly` is a new `Result.Failed` subtype, so the `when` expression in `renderFailureReason()` and `runBatch()` will fail to compile until Phase 03. If implementing in one commit, defer build verification to after Phase 03 step 03.2. If implementing phase-by-phase, add a stub in `renderFailureReason()` now (returning an empty string) and update it properly in Phase 03.

**Verification:**

- `Grep` — `class SocialPreviewOnly` matches in `LinkAutoDownloadCoordinator.kt` (the result type).
- `Grep` — `socialPreviewHost` matches in `LinkAutoDownloadCoordinator.kt`.
- `Grep` — `authSessionRepository` matches in `LinkAutoDownloadCoordinator.kt`.
- `Grep` — `Log\.d\(` returns zero hits in `LinkAutoDownloadCoordinator.kt`.

**Status:** `[ ]` not done

---

### Step 02.6 — Structured extraction-outcome diagnostic for known auth hosts

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinator.kt`
**Depends on:** Step 02.5

**Context:** strategic §6.1 Resolved (diagnostics part). To later answer the still-Open §6.1 architecture question on-device, the log must show — for a link to a known auth host — which strategy produced what. Add one structured `Timber.d` line per finished strategy attempt when `KnownAuthResources.isPreviewSensitiveHost(host)` holds: strategy id, candidate breakdown (counts by kind: video / audio / streaming-manifest / image-preview / other), whether a saved session was applied to that attempt, and the resulting `OpenResult` kind. This is **not** an `Sxxxx:` debug-verification tag — it is a permanent diagnostic line and stays regardless of ticket status.

**Prompt for developer:**

> In `LinkAutoDownloadCoordinator.handleUrl()`, inside the strategy loop, after each strategy's `open()` returns and before the `when (val opened = ...)` dispatch, when the URL host is a known auth host (`KnownAuthResources.isPreviewSensitiveHost(host)`), emit:
>
> ```kotlin
> Timber.d(
>     "S0151-diag: host=%s strategy=%s sessionApplied=%s outcome=%s candidates=%s",
>     host,
>     strategy.id,
>     sessionAppliedForThisAttempt,
>     outcomeKindOf(opened),
>     candidateBreakdownOf(opened),
> )
> ```
>
> - `host` — the registrable host of the shared URL.
> - `sessionAppliedForThisAttempt` — whether a saved auth session was injected for this strategy's attempt (the coordinator already knows whether cookies were applied; reuse that flag — do not add new persistence).
> - `outcomeKindOf(opened)` — a short label: `stream` / `batch(n)` / `social-preview-only` / `not-found` / `non-http` / `other`.
> - `candidateBreakdownOf(opened)` — for `Batch`/stream results, a compact string like `video=1 image=3 hls=0 other=0`; for `SocialPreviewOnly`/`NotFound`, `image=<n> other=<n>` derived from whatever the strategy exposed (if the strategy does not expose its rejected candidates, log `image=? other=?` — do not change strategy signatures just for the log).
>
> Implement `outcomeKindOf` / `candidateBreakdownOf` as private helpers in the coordinator. No `Log.d`. No new Hilt bindings. The label `S0151-diag:` is intentionally distinct from the `S0151:` debug-tag prefix so `/spec-check`'s tag grep does not touch it.

**Verification:**

- `Grep` — `S0151-diag:` matches in `LinkAutoDownloadCoordinator.kt`.
- `Grep` — `fun outcomeKindOf` and `fun candidateBreakdownOf` match in `LinkAutoDownloadCoordinator.kt`.
- `Grep` — `Log\.d\(` returns zero hits in `LinkAutoDownloadCoordinator.kt`.
- `Grep "Timber.d(\"S0151:"` (note the colon, not the dash) — returns zero hits in `LinkAutoDownloadCoordinator.kt` (this line is `S0151-diag:`, not an `S0151:` debug tag).

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every step above is `[x] done`.
- [ ] Project compiles — run `/build` (note: `renderFailureReason` must have a branch for `SocialPreviewOnly` before build succeeds; add stub if Phase 03 not yet done).
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entries added for all files in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated: `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` then `render.ps1`.

---

## Handoff Notes to Next Phase

- `OpenResult.SocialPreviewOnly` is a first-class pipeline outcome. Both `html` and `dynamic` strategies can return it for any host in the known auth resources registry (`KnownAuthResources.isPreviewSensitiveHost`) — §6.3 (b).
- The coordinator falls through across strategies on `SocialPreviewOnly` and builds `Result.Failed.SocialPreviewOnly(host, url, hadExistingSession)` after all strategies are exhausted.
- `hadExistingSession` tells the UI whether to show "sign in" (false) or "sign in again" (true).
- Preview images are never downloaded automatically for known auth hosts.
- The `S0151-diag:` log line (Step 02.6) is permanent — it stays regardless of ticket status and is the on-device source for resolving the still-Open §6.1 architecture question.
- `§6.2 (b)` "save partial carousel + N-of-M message" is **not** in this phase: it depends on actually extracting carousel elements, which is gated on the §6.1 architecture decision (Open). It will be added by a follow-up `/spec-update --tactical` once §6.1 architecture is resolved on-device; until then a fully empty result stays "content unavailable".

---

## Rollback Plan

Revert phase commit(s). No data migration. No Room schema change. No user data affected.
