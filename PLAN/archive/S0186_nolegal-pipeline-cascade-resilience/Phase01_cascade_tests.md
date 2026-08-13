# Phase 01 — Safety-net unit tests for cascade fall-through

**Goal:** add unit tests that fail on current code and pass after Phase 02 lands. Tests must prove (a) cascade continues to the next strategy after `open()` throws, (b) `CancellationException` is **not** swallowed.

## Touch points

- `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinatorTest.kt` (add new `@Test` methods at end of class).

## Steps

1. Open `LinkAutoDownloadCoordinatorTest.kt`. Locate the `@Before setUp()` block (line 47-60) and the existing `noOpCallbacks` (line 41).

2. Add a helper `private fun stubStrategy(id: String, ...)` returning `UrlExtractionStrategy` mock-built with mockk. The helper should accept:
   - `id: String`
   - `probeResult: ProbeResult` (default `ProbeResult.Applicable(tentativeSizeBytes = null)`)
   - `openBehaviour: suspend () -> OpenResult` (default `{ throw IllegalStateException("open should not be reached") }`)
   - Build via `mockk<UrlExtractionStrategy>().apply { every { id } returns ...; coEvery { probe(any()) } returns ...; coEvery { open(any(), any()) } answers { openBehaviour() } }`.

3. Add test `open_throwing_strategy_does_not_abort_cascade`:
   - `strategyA` — id `"ytdlp"`, probe returns `Applicable`, open throws `RuntimeException("simulated PyException")`.
   - `strategyB` — id `"site"`, probe returns `Applicable`, open returns `OpenResult.NotFound`.
   - `every { registry.ordered() } returns listOf(strategyA, strategyB)`.
   - Stub `cookieStore.loadForAccount(any(), any()) returns emptyList()`, `cookieStore.loadUserAgentForAccount(any(), any()) returns null`, `cookieStore.listAllAccounts() returns emptyList()`, `authSessionRepository.hasAnySession(any()) returns false`.
   - Call `coordinator.handle("https://example.com/x", noOpCallbacks, null)`.
   - **Verify:** `coVerify { strategyB.open(any(), any()) }` — strategy B reached.
   - **Verify:** result is `Result.Failed.NoMediaFound` (NOT a thrown exception, NOT `Result.Failed.Other`).

4. Add test `cancellation_in_open_propagates_immediately`:
   - `strategyA` — open throws `kotlinx.coroutines.CancellationException("user cancelled")`.
   - `strategyB` — open would return Success (sentinel for "must not be reached").
   - `every { registry.ordered() } returns listOf(strategyA, strategyB)`.
   - Stub auxiliary mocks as in step 3.
   - Wrap call in `try { coordinator.handle(...) } catch (e: CancellationException) { caught = true }`. Inside `runTest`.
   - **Verify:** `caught == true`.
   - **Verify:** `coVerify(exactly = 0) { strategyB.probe(any()) }` and `coVerify(exactly = 0) { strategyB.open(any(), any()) }` — chain stopped on cancellation.

5. Add test `probe_throwing_already_handled_does_not_regress` (regression guard for the existing line 169-175 wrapper):
   - `strategyA` — probe throws `RuntimeException("simulated")`, open is sentinel that should not be reached.
   - `strategyB` — probe returns `Applicable`, open returns `OpenResult.NotFound`.
   - `every { registry.ordered() } returns listOf(strategyA, strategyB)`.
   - **Verify:** `coVerify(exactly = 0) { strategyA.open(any(), any()) }` and `coVerify { strategyB.probe(any()) }`.

6. Run the unit test target locally (will be done in build gate after Phase 02). Before Phase 02 lands, tests 1 and 2 are expected to FAIL — that confirms the safety net catches the bug. Do not run tests in this phase as a verification step; they verify the fix in Phase 02.

## Verification predicates

- `LinkAutoDownloadCoordinatorTest.kt` compiles (signatures match — `UrlExtractionStrategy.id`, `probe`, `open` exist).
- Three new `@Test` methods present.
- No production code modified in this phase.

## Spec catalog sync

`pwsh -File scripts/spec_catalog/update.ps1 -Id S0186 -Status Tactical` (already set by /spec-tech; this phase keeps it).
