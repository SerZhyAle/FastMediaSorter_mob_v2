# S0718 Code Readability Audit - Findings

Date: 2026-06-26. Scope: `app_v2/` and `wear/` (main + flavor source sets, excluding test/androidTest). Layer 1 of `docs/CODE_AUDIT_PROTOCOL.md`.

Method: four readability dimensions were swept - risky/unjustified `!!` (not-null assertions), deep nesting / arrow code / over-complex functions, boolean-trap parameters and call-site opacity, and loose state modeling (illegal states representable). An explicit anti-slop bar was applied: a candidate is reported only if it is a genuine defect (correctness hazard, reader-misleading construct, or concrete maintenance/drift hazard with evidence), never a cosmetic style preference. Candidates were de-duplicated by symptom and each survivor was put through strict adversarial verification against the actual source (claims re-checked line-by-line, severities revised up or down on evidence). Of 11 verified candidates, 3 were rejected or refuted as stylistic, leaving 8 confirmed findings below.

## Severity summary

| Severity | Count | Description |
|----------|-------|-------------|
| P1 | 0 | Crash / correctness defect with a realistic, frequently-hit path |
| P2 | 4 | Reader-misleading or latent-defect constructs warranting refactor |
| P3 | 4 | Real but mild clarity / duplication / loose-state hazards |
| Total | 8 | |

## Confirmed findings

### P2

**1. Latent NPE via `!request?.isForMainFrame!!`**
- File: `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/auth/WebViewAuthDialogFragment.kt:125`
- Symbol: `WebViewClient.shouldInterceptRequest`
- Dimension: risky / unjustified `!!` (not-null assertion)
- Rationale: `!request?.isForMainFrame!!` parses as `!((request?.isForMainFrame)!!)`. When `request` is null, `request?.isForMainFrame` is null and the trailing `!!` throws `KotlinNullPointerException` on a background thread inside the WebViewClient callback. The parameter is declared nullable (`request: WebResourceRequest?`), the same line uses `request?.url` defensively, and sibling `onReceivedError` at line 155 uses the correct safe form `request?.isForMainFrame == true` - proving the author's own intent. The `!!` defeats surrounding null-safety and looks safe via `?.` but isn't. Downgraded from suspected P1 to P2 because modern Chromium WebView reliably passes a non-null request, so the crash is latent (contract-permitted but rarely hit) rather than frequently fired.
- Fix sketch: Replace with `if (harvestMode && request?.isForMainFrame == false)` (matches the safe pattern already used at line 155).
- Confidence: high

**2. Deep nesting in SMB move with hand-duplicated bookkeeping**
- File: `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbFileOperationHandler.kt:172`
- Symbol: `executeMove`
- Dimension: deep nesting / arrow code / over-complex functions
- Rationale: The destination-SMB branch nests `forEachIndexed -> when(3 arms) -> try -> if/else -> if/else`, reaching ~6 indent levels (verified lines 172-348). The bridge-move arm (sftp/ftp: download->upload->delete, lines 222-264) is a distinct multi-step I/O responsibility interleaved with result bookkeeping; its outcome if/else spans 26 lines (download-else at 237, matching upload-error else at 256), forcing the reader to pair distant else-branches by hand. The success/partial bookkeeping (`movedPaths.add` + `successCount++` vs `errors.add`) is duplicated in every arm and nesting level, so partial-success semantics differ subtly per path and are easy to desync. The author's own confusion is corroborated by self-described dead code at lines 320-327 ("Reached unreachable code after destination SMB check"). Not P1: the two `as SmbResult.Error` casts (257, 298) are guarded by the enclosing `is SmbResult.Success` else, so no cast/NPE risk.
- Fix sketch: Extract each `when`-arm into a per-arm suspend helper (`moveSmbToSmb`/`moveBridgeToSmb`/`moveLocalToSmb`) returning a small `MoveOutcome(Moved|Error|PendingPermission)` sealed result, and centralize the `movedPaths`/`successCount`/`errors` bookkeeping in the loop via guard-style early returns.
- Confidence: high

**3. Callback pyramid in invisible-WebView extraction**
- File: `app_v2/src/main/java/com/sza/fastmediasorter/data/link/InvisibleWebViewExtractionStrategy.kt:411`
- Symbol: `onPageFinished` (anonymous `WebViewClient`)
- Dimension: deep nesting / arrow code / over-complex functions
- Rationale: `onPageFinished` nests ~8 levels (verified lines 411-461): `postDelayed` lambda -> `runCatching` -> `evaluateJavascript(DOM)` callback -> `if(shouldInspectEmbeddedJson)` -> `runCatching` -> `evaluateJavascript(outerHTML)` callback -> `CoroutineScope.launch` -> `mainHandler.post`, reaching column ~44+. The `else` fallback (line 448) sits 26 lines below its `if` (line 422), separated by the entire nested embedded-json success path, so tracing the dom-fallback branch means scrolling past the whole pyramid. The two diverging extraction paths (embedded-json vs dom-only) are genuinely hard to trace. Not a correctness issue - the only `!!` (`baseUri!!` at 432) is guarded by `!baseUri.isNullOrBlank()` at 431. Some callback nesting is inherent to `evaluateJavascript`'s API, capping severity at P2.
- Fix sketch: Extract the embedded-json inner block (outerHTML eval -> IO launch -> `mainHandler.post` finish) into a private `fun inspectEmbeddedJson(target, domCandidates, pageUrl, fallbackUrl, finish)`, collapsing `onPageFinished` to the if/else dispatch.
- Confidence: medium

**4. Screen status split across three independent observables**
- File: `app_v2/src/main/java/com/sza/fastmediasorter/core/ui/BaseViewModel.kt:33`
- Symbol: `BaseViewModel.loading` / `error`
- Dimension: loose state modeling (illegal states representable)
- Rationale: `BaseViewModel` exposes three independent observables - subclass `state`, `loading` (line 33), `error` (line 36) - with no unifying sealed `UiState`. The split is not theoretical: `MainActivity` recombines content/empty/error ad-hoc in TWO separate collectors that each snapshot the other flow via `.value`. The `state` collector (lines 950-956) reads `viewModel.error.value`; the `error` collector (lines 985-996) independently re-reads `viewModel.state.value.resources` to recompute the same visibility triple - duplicated reconciliation kept in sync by hand. A third collector (line 970) drives `progressBar` from `loading` with no awareness of error/empty. Illegal combos are reachable: `setError`/`setLoading`/`clearError` are independent, so `loading=true` while `error!=null`, and empty-while-loading, are both representable. Below P1 (no crash/NPE). The separate loading/error flows are a common Android idiom, tempering severity, but the duplicated cross-flow `.value` reconciliation is a concrete maintenance hazard already showing seams.
- Fix sketch: Replace the separate `loading`/`error` flows with a single sealed `UiState<Content>` (Loading/Content/Empty/Error) so each screen renders one observable and illegal combos become unrepresentable.
- Confidence: medium

### P3

**5. `mime!!` relies on a non-local null invariant**
- File: `app_v2/src/main/java/com/sza/fastmediasorter/data/link/DirectFileExtractionStrategy.kt:120`
- Symbol: `open`
- Dimension: risky / unjustified `!!` (not-null assertion)
- Rationale: `mime` is nullable (`response.header("Content-Type")?.substringBefore(';')?.trim()`). The bare `mime!!` at line 120 is safe only because line 111's `if (!MediaMimeWhitelist.isAllowed(mime)) return` filters null, and `isAllowed`'s null rejection is real (`MediaMimeWhitelist.kt:41` `if (mime == null) return false`) - so no NPE path actually reaches the `!!` (rules out P1). But it is a genuine non-local invariant: Kotlin cannot smart-cast across the `isAllowed` call boundary, so a reader at the call site sees `String?` plus a bare `!!` and must open another file to prove safety. The coupling between the whitelist's incidental null-rejection and the assertion's correctness is invisible locally and brittle (changing `isAllowed` to accept null silently re-arms the crash). Real but mild - P3.
- Fix sketch: Replace the `!isAllowed` guard with a value-binding early return: `val mime = response.header(..)?.. ; if (mime == null || !MediaMimeWhitelist.isAllowed(mime)) { response.close(); return@withContext OpenResult.Blocked(BlockedReason.MimeNotAllowed) }` so `mime` smart-casts to non-null and the `!!` at 120/124 disappears.
- Confidence: high

**6. Verbatim COPY/MOVE duplication with identical Success/PartialSuccess arms**
- File: `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ExecuteScheduledOperationUseCase.kt:125`
- Symbol: `invoke` / `when(operation.operationType)`
- Dimension: deep nesting / arrow code / over-complex functions
- Rationale: The COPY (lines 138-178) and MOVE (193-233) result handlers are near-identical ~40-line `when`-blocks differing only in the FileOperation type and log label; within each, the Success and PartialSuccess branches are byte-identical (139-148 == 149-158; 194-203 == 204-213), each duplicating the same `skippedCount>0 -> SKIP / else -> successCount++/OK` logic. Duplication has already produced drift: the permission-stop log differs between arms (line 165 `Timber.w "ScheduledOp[..] COPY halted"` vs line 220 `Timber.d "S0710: MOVE halted"`). The DELETE arm (246-247) already merges `Success || PartialSuccess`, proving the merge is idiomatic in this same file. Each arm is locally clear and correct (no NPE/misleading hazard), so it stays P3: real, fixable, low-risk, evidenced by drift, zero functional impact.
- Fix sketch: Merge `Success||PartialSuccess` in each arm (as DELETE already does) and extract a private `handleFileResult(result, file, opLabel, srcLabel, dstLabel)` helper; the three arms then differ only in the FileOperation built and the op label.
- Confidence: high

**7. Boolean-trap positional literals in `updateState`**
- File: `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/loading/BrowseLoadingManager.kt:199`
- Symbol: `LoadingCallbacks.updateState`
- Dimension: boolean-trap parameters and call-site opacity
- Rationale: Signature (line 50): `updateState(mediaFiles, usePagination: Boolean, loadingProgress: Int, totalFileCount: Int, isScanCancellable: Boolean)`. Five call sites (199, 209, 251, 259, 266) pass bare positional literals, e.g. `updateState(files, false, files.size, files.size, true)`. The two booleans read as anonymous flags; a reader cannot tell `false` is `usePagination` and the trailing `true/false` is `isScanCancellable` without opening the signature. The inconsistency is reinforced by the file's own contrasting idiom: the `getMediaFilesUseCase` call 11 lines earlier (173-184) names every argument including `useChunkedLoading = false`. Suspected P2 overstated it - the two booleans are separated by two Int args, so they cannot be swapped without a type error (classic silent-reversal does not apply), and only `isScanCancellable` varies. No crash risk; a genuine but minor clarity defect with low blast radius - P3.
- Fix sketch: Add argument names at all 5 call sites: `updateState(files, usePagination = false, loadingProgress = files.size, totalFileCount = files.size, isScanCancellable = true)`.
- Confidence: high

**8. Duplicated empty/error reconciliation that ignores `loading`**
- File: `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt:950`
- Symbol: `MainActivity` (state/error/loading observers)
- Dimension: loose state modeling (illegal states representable)
- Rationale: The empty/error visibility decision (`errorStateView.isVisible = hasError && isEmpty; emptyStateView.isVisible = !hasError && isEmpty`) is duplicated verbatim in two independent collectors: the state observer (lines 954-955) and the error observer (lines 990-991). `loading` is a third independent StateFlow (`BaseViewModel.kt:33-34, 55-57`) observed in isolation (line 970) and never factored into the empty/error decision. Because `setLoading(true)` does not mutate `state` and the three flows are orthogonal, an illegal combination is reachable: at cold start the initial empty state makes `emptyStateView` visible while `loadResources()` (`MainViewModel.kt:236-262`) concurrently sets loading=true and shows the progressBar - so empty placeholder and spinner can render together. The duplication is a real DRY/drift hazard. Report overstates reach: filter/tab changes retain the prior list (no flash), and the error-view-mid-load case is not realistically reachable - the concrete symptom is a cold-start cosmetic overlap, not a logic error or crash. P3.
- Fix sketch: Model list UI as a single sealed `ListUiState` (Loading/Empty/Error/Content) derived from `combine(state, loading, error)` and render visibility in one observer, so loading suppresses empty/error and the duplicated block is removed.
- Confidence: high

## Coverage notes

**risky / unjustified `!!`** (3 reported)
Enumerated all 145 `!!` in `app_v2/src` (main + flavor sets vr/noLegal) and `wear/src`, excluding test/androidTest; read the surrounding function for every non-obvious case. The large majority are idiomatic-safe and were not reported: ~55 `private val binding get() = _binding!!` ViewBinding accessors; `!!` on just-assigned locals/fields within the same try/withContext block (DropboxClient `dbxClient`, NetworkFileModelLoader smb/sftp/ftp clients, AudioPlaybackService/PositionSaveLoop runnable, PdfViewerManager); `!!` immediately after an explicit null guard or smart-cast (SftpDataSource `channel!!`, ResourceEditorOutcomeRenderer `statistics!!`, AudioEmptyStateController `surfaceTexture!!`, GoogleDrive/OneDrive `signOutError!!`, wear BrowseViewModel `_sourceId!!`, wear `uiState.error!!`); `!!` after a `filter{x!=null}`/require XOR guarantee (CandidateSelectionPolicy, ImportStreamCatalogUseCase faviconIndex, BackfillSmbCredentialShareNameUseCase credentialsId, DeleteByFileSizeUseCase, SmbOperationStrategy sourceInfo/destInfo); `getDrawable(resId)!!` on bundled resources (ResourceIconComposer). Only 1 realistic NPE path (WebViewAuthDialogFragment) plus 2 fragile-but-currently-safe type smells (P3) found. Total `!!` count: 145; flagged: 3. This dimension is largely clean.

**deep nesting / arrow code / over-complex functions** (4 reported)
Scanned `app_v2/src` (main + cloudEnabled/debug flavor sets) and `wear/src` via `measure-hotspots.ps1` (top 15 hotspots reviewed) plus an indentation sweep (awk RLENGTH) flagging ~60 control-flow sites at >=24-char / >=32-char indent; read the surrounding function for every flagged site. Most deep-indent hits were inherent parser code that reads fine (PdfInfoParser, GifFrameCounter), well-modeled sealed-class exhaustive dispatch (LinkAutoDownloadCoordinator, CloudAuthStateMachine), or sequential cloud/network upload-delete bookkeeping where nesting tracks a real protocol (CloudFileOperationHandler.executeMove) - not reported. Excluded `debug/IntegrationTestRunner.kt` (non-shipping test harness). Reporting 4 genuine cases (3 P2, 1 P3); ExecuteScheduledOperationUseCase is the strongest. No P1 in this dimension (deep nesting alone carries no direct crash path).

**boolean-trap parameters and call-site opacity** (1 reported)
Enumerated all declarations with 2+ Boolean params across `app_v2` (main + vr/noLegal/debug) and `wear` via Grep (~40 declarations), then traced positional-literal call sites for the call-prone ones (nextFile/previousFile, reloadFiles, captureCamera, prepareAndLaunch, updateTile, applyOverlayState, populate/itemCount, setupPipButton, updateState, render). The codebase is strongly disciplined: nearly all boolean call sites use named arguments or self-describing variables/expressions, and the many `(false, true)` matches are idiomatic `AtomicBoolean.compareAndSet`. Wear module clean. Only 1 genuine boolean-trap call site found (BrowseLoadingManager.updateState); reported it rather than padding with stylistic nitpicks per the anti-slop rule.

**loose state modeling (illegal states representable)** (3 reported)
Enumerated 31 state/UiState classes plus the BaseViewModel hierarchy. Most are already well-modeled: DuplicatesState uses a sealed ScanState (Idle/Running/Error); StatisticsListItem and StatisticsEvent are sealed; ResourceEditorUiState.canSave is a single-source derived cache (line 531), not independently set; cloud folder pickers and AddResourceState route errors through one-shot events and use distinct flags for genuinely distinct subflows (isScanning vs isScanningShares). The one systemic loose-state pattern is the BaseViewModel split (state/loading/error), surfaced most visibly in MainActivity's duplicated reconciliation; ManualNetworkSyncUiState is a localized P3. No P1 (these are clarity/UI-flicker, not crash). Reported 3 of ~5 candidates after rejecting derived-getter and event-based-error cases as non-defects.

## Method

Each dimension was swept exhaustively over `app_v2/` and `wear/` (main + flavor source sets, excluding test/androidTest) using mechanical enumeration (full `!!` census, declaration Grep for multi-boolean signatures, hotspot + indentation sweeps, state-class enumeration) followed by reading the surrounding function for every non-obvious candidate. An explicit anti-slop bar gated every candidate: report only genuine defects (correctness hazard, reader-misleading construct, or concrete maintenance/drift hazard with evidence), never cosmetic style preference. Candidates were de-duplicated by symptom, then each survivor underwent strict adversarial verification - claims re-checked line-by-line against source, severities revised up or down on evidence (e.g. WebViewAuthDialogFragment downgraded P1->P2; BrowseLoadingManager and ExecuteScheduledOperationUseCase downgraded P2->P3 after the suspected silent-reversal/crash paths were refuted). Of 11 verified candidates, 3 were rejected or refuted as stylistic/non-defects, leaving the 8 confirmed findings above.
