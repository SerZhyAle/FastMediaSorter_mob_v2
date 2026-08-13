# Research 01 - Streams edit architecture (S1145)

**Date:** 2026-07-22
**Method:** read-only codebase research (catalog query + source read).

## Baseline: edit already exists (S0660)

A per-row "Edit" action already ships (spec S0660, `docs/ALL_FEATURES.jsonl` record `streams.card-overflow-actions-menu`). Its dialog exposes **only URL + Title**. S1145 is an **extension** of this dialog toward "any parameter", not a net-new feature.

## Stream entity & editable parameters

`StreamSourceEntity` - `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/StreamSourceEntity.kt` (table `stream_sources`):
- `id`, `url` (unique index `index_stream_sources_url`), `title`.
- `mediaKind` - RTSP / VIDEO / AUDIO; today **auto-derived** from the URL, never user-chosen.
- `category`, `topic`, `language`, `country` - documented CATALOG-only; **always null for MANUAL rows** (KDoc lines 8-13). Several UI call sites rely on this invariant.
- `sourceOrigin` (MANUAL / IMPORTED / CATALOG), `pin`, `sortIndex`, `lastPlayOutcome`, `addedAt`.
- `favicon` - not on the entity; sidecar `FaviconAtlasStore` keyed by URL, rewritten only on catalog import; MANUAL rows have no atlas entry.

For a MANUAL stream the user-defining parameters are therefore: **URL, Title, Type (mediaKind)**. Catalog fields do not apply to manual channels.

## Current edit path (data flow)

1. Overflow menu `ID_EDIT` in `StreamSourceAdapter` (`ui/streams/StreamSourceAdapter.kt:193-197`, gated `sourceOrigin == "MANUAL"`) and mirrored in `StreamGridAdapter` (`ui/streams/StreamGridAdapter.kt`).
2. `onEdit(source)` -> `StreamsActivity.showEditDialog(source)` (`ui/streams/StreamsActivity.kt:642-665`) inflates `DialogAddStreamBinding` (`res/layout/dialog_add_stream.xml` - only `etUrl`, `etTitle`), pre-fills, swaps title, routes positive button to `viewModel.onEdit(source, url, title)`.
3. `StreamsViewModel.onEdit` (`ui/streams/StreamsViewModel.kt:194-199`) -> `UpdateStreamSourceUseCase.invoke(source, url, title)` (`domain/usecase/streams/UpdateStreamSourceUseCase.kt`).
4. Use case: rejects non-MANUAL (`NotEditable`), rejects unsupported scheme (`InvalidUrl`) via `StreamMediaKindClassifier.isSupportedScheme`, **re-derives mediaKind** from the new URL (`classifier.classify(trimmedUrl)`), calls `StreamSourceRepository.updateUserFields(id, url, title, mediaKind)`.
5. `StreamSourceDao.updateUserFields` (`data/local/db/StreamSourceDao.kt:73-77`) - raw `UPDATE ... WHERE id = :id AND sourceOrigin = 'MANUAL'` (SQL-layer origin scoping). Room `observeAll()`/`observePinned()` Flow re-emits -> list/grid re-render automatically.

## Reuse candidates

- **Dialog:** extend `dialog_add_stream.xml` + the shared `showSourceDialog`/`showEditDialog` inflate path; add a media-kind picker (radio group), toggled visible per add-vs-edit like `tilTitle.isVisible = !isImport` (`StreamsActivity.kt:617`).
- **Dup guard:** `GetStreamSourceByUrlUseCase` (`domain/usecase/streams/GetStreamSourceByUrlUseCase.kt`) already resolves a row by URL and is currently unused - natural pre-write check.
- **Keyboard submit:** `DialogKeyboardDelegate.applyTo(...)` (Enter-to-confirm) already used by these dialogs.
- **Classifier:** `StreamMediaKindClassifier` (`domain/usecase/streams/StreamMediaKindClassifier.kt`) - keep as the "Auto" behaviour; explicit pick overrides it.

## Flavor gating

`BuildConfig.SUPPORT_STREAMS`, read only via `CapabilityAvailability.isStreamsAvailable()` (`core/capability/CapabilityAvailability.kt:47`). true: standard / noLegal / legacy / vr. false: lite / photos. All streams-enabled flavors also ship media3 hls/dash/rtsp - a user-chosen type is playable in every flavor that shows the Streams UI. Streams UI lives in `src/main` (no flavor source-set split).

## Risks

| Risk | Evidence | Severity |
|------|----------|----------|
| Editing URL to collide with another row's unique url index -> uncaught `SQLiteConstraintException` (no pre-check, no try/catch, `viewModelScope.launch` no handler) -> crash | `StreamSourceEntity.kt:16-18`; `UpdateStreamSourceUseCase.kt:17-31`; `StreamsViewModel.kt:194-199` | High |
| `mediaKind` silently re-derived from URL on every edit - an explicit type pick is overwritten unless the use-case/DAO signature accepts an explicit kind | `UpdateStreamSourceUseCase.kt:28`; `StreamMediaKindClassifier.kt:20-28` | Med |
| Catalog-only fields become inconsistent if ever exposed for MANUAL (breaks "null for MANUAL" invariant) | `StreamSourceEntity.kt:8-13`; `StreamSourceAdapter.kt:124-135` | Med (avoided by keeping them out of scope) |
| Favicon (URL-keyed sidecar) orphaned if edit ever widened past MANUAL-only | `FaviconAtlasStore.kt:12-18`; `StreamSourceDao.kt:73-77` | Low (contained by MANUAL-only scoping) |

## Test coverage gap

No unit test on the edit write path: `UpdateStreamSourceUseCase`, `AddStreamSourceUseCase`, `StreamMediaKindClassifier`, `StreamSourceRepository.updateUserFields`, `StreamsViewModel.onEdit` - zero tests. Adjacent tests only (catalog merge, favicon, filter, title formatter). The tactical plan should close this for the new explicit-kind + dup-guard logic.

## Resolved product decisions (defaults for the strategic spec)

1. **Type picker: YES** (Auto / Audio / Video; RTSP stays scheme-driven). Without it the ticket adds nothing over S0660.
2. **Catalog fields on MANUAL: NO** - keep CATALOG-only/null invariant; out of scope.
3. **Dup-URL guard: YES** - correctness requirement, S1145 touches the exact crash path.
4. **Pinned-panel edit: excluded** - full-screen list stays the edit surface.
5. **FEATURES: extend S0660 record (CHANGE)**, not a new record.

## /spec-draft candidate (Add-path twin bug, out of scope)

`AddStreamSourceUseCase.invoke` never checks for an existing URL before `repository.add()` -> `dao.upsert()`; the `@Upsert` conflict target is the PK only, so a collision on the unique `url` index can throw an uncaught `SQLiteConstraintException` in `viewModelScope.launch`. `AddResult.Duplicate` exists but is documented unreachable. Evidence: `AddStreamSourceUseCase.kt:15-33,41-47`. Distinct shipped code path (Add, not Edit); shares root cause with the Edit-path High risk. Candidate for a separate ticket.
