# Code Audit Protocol

This protocol defines how FastMediaSorter audits Android code for correctness, readability, memory safety, lifecycle safety, concurrency safety, and performance.

Scope:
- `app_v2/`
- `wear/`
- shared build, script, and documentation surfaces that affect runtime behavior

Out of scope:
- visual design review
- copywriting polish
- product-priority decisions unless they affect code risk

## Goals

- keep code readable and cheap to reason about
- catch memory leaks before release
- prevent lifecycle-unsafe coroutine and listener usage
- keep concurrent access correct, not only leak-free
- keep startup and hot user flows fast
- make regressions reproducible with evidence
- classify every finding by severity so triage is consistent
- turn recurring review comments into mechanical gates

## Core rule

Audit in layers:

1. prevent
2. reproduce
3. explain
4. gate

Do not jump straight to profiler sessions for every change. First use the cheapest proof that can reject a bad change.

## Evidence ladder

Use the lowest-cost proof that matches the risk.

1. Static code review
2. Existing quality gates
3. Targeted compile or resource validation
4. Focused unit test
5. Debug runtime detection
6. Device smoke
7. Repeatable benchmark
8. Trace or heap analysis

Examples:
- architectural rule check -> static review + gate
- suspected coroutine leak -> gate + debug runtime + LeakCanary
- startup regression -> benchmark + Perfetto trace

## Finding severity taxonomy

Tag every finding so a leaked Activity is never weighed the same as an extra `!!`.

- P0 - crash, ANR, OOM, retained Activity/Fragment/View, deadlock, data loss. Blocks release.
- P1 - data race on shared mutable state, main-thread disk or network I/O, unbounded cache growth, unreleased heavy resource (player, cursor, surface). Fix before merge.
- P2 - allocation churn on a hot path, repeated expensive lookups, missing lifecycle-awareness without a proven leak, over-eager startup work. Fix or file a ticket.
- P3 - readability, naming, `!!` without justification, dead comment, minor style. Fix inline or note.

Rule:
- attach a severity to each finding in review comments and in `## Last Audit` notes
- P0 and P1 require evidence at the matching rung of the evidence ladder, not opinion

## Audit triggers

Run the protocol when any of these is true:

- new screen, manager, worker, repository, or long-lived helper is added
- lifecycle code changes
- coroutine, Flow, callback, listener, or observer code changes
- shared mutable state, synchronization, or dispatcher usage changes
- Room entity, DAO, query, migration, or transaction changes
- player, image loading, caching, or network path changes
- startup path changes
- DI scope or singleton ownership changes
- build, manifest, R8, or keep-rule changes affect profiling, startup, minification, or process behavior
- crash, ANR, OOM, jank, or leak is reported
- a phase of a multi-phase task just finished (tactical spec phase via `/spec-dev`, or any large task split into sequential phases manually) - audit that phase before starting the next one, not only at the end of the whole task

## Phase-boundary audits

Cost of a fix grows with how much later work has already been layered on top of the defect. Catching an issue at the boundary right after the phase that introduced it costs about one phase's worth of rework. Leaving it for a single end-of-task audit costs every subsequent phase's worth - later phases may consume the defective API, mirror the mistake into new files, or make the eventual fix a cross-phase rewrite.

Rule: whenever a task is split into sequential phases, run this protocol against the phase just completed before starting the next phase's first step - scope the review to that phase's changed files.

- Layer 1 (static architecture/readability) always applies.
- Layer 2 (lifecycle/coroutine/concurrency), Layer 3 (memory/listener ownership), Layer 4 (Room) apply when the phase touched the matching surface.
- P0/P1 findings get fixed immediately, in the same phase boundary, before the next phase starts.
- P2 findings get fixed if trivial, otherwise logged for follow-up; a recurring P2 is a mechanical-gate candidate (Layer 8).
- P3 findings get fixed inline or skipped.

This is a fast self-review, not a substitute for the deeper end-of-task audit (e.g. `/spec-check`) - it exists to keep that final audit from finding a phase's worth of unrelated debt in every phase at once.

`/spec-dev` runs this automatically as its "Phase-boundary audit" step; apply the same discipline manually for phased work driven outside that skill.

## Layer 1 - Static architecture and readability audit

Check these first:

- UI -> ViewModel -> UseCase -> Repository -> DataSource is preserved
- no business logic is introduced into Activities
- singleton classes do not retain `Activity`, `Fragment`, `View`, `Dialog`, or adapter instances
- manager classes own a clear release point
- dependencies are scoped intentionally in Hilt
- classes carry few enough responsibilities to review safely
- comments explain why, not what

Class-size methodology - rank by responsibility, not by raw line count:

- run `scripts/quality/measure-hotspots.ps1`; it scores each `src/main` Kotlin file by `publicApi + collaborators + callbackSites + 3*extractMarkers`, with LOC reported only for context
- a high score means too many responsibilities, which is the real refactor signal; the 1500 LOC rule is only a coarse backstop
- prefer extracting a real responsibility into a `helpers/*Manager.kt` over cosmetic line-count splitting

Readability checks:

- no `!!` without a justified reason; prefer `?.`, `requireNotNull(x) { "why" }`, or a sealed/early-return path. Mechanically ratcheted by `scripts/quality/assert-non-null-assertion.ps1` (S1032, under the neuroslop umbrella): the `src/main` count may not grow, only burn down
- bounded nesting depth; collapse arrow code with early returns and guard clauses
- nullability is expressed in types, not defended with scattered null checks
- screen and domain state is modeled with sealed classes or enums, not loose booleans and flags
- avoid boolean-trap parameters; name intent at the call site

Project hooks already present:

- no Activity logic rule from `CLAUDE.md`
- file-size and helper-extraction rules
- responsibility ranking via `scripts/quality/measure-hotspots.ps1`
- custom script gates under `scripts/quality/`
- `detekt` + ktlint formatting ratchet gate

## Layer 2 - Lifecycle, coroutine, and concurrency audit

For every coroutine or Flow change, verify:

- no `GlobalScope`
- long-lived jobs use injected or lifecycle-bound scopes only
- UI collection is lifecycle-aware (prefer `repeatOnLifecycle` or `flowWithLifecycle` instead of direct collection in `lifecycleScope.launch` to prevent background execution/leaks when the app is in the background)
- cancellation is not swallowed
- dispatchers are not hardcoded deep inside business logic unless justified
- `launch` vs `async` usage is intentional
- callbacks and listeners are unregistered on pause, stop, or destroy as needed

Concurrency correctness (beyond leaks):

- shared mutable state is confined to one thread, or guarded with `Mutex` / `synchronized` / `@Volatile`; no read-modify-write race
- suspend functions are main-safe: `withContext(Dispatchers.IO)` sits at the Repository or DataSource boundary, not buried in business logic or pushed onto callers
- hot or shared Flows use `stateIn` / `shareIn` with `SharingStarted.WhileSubscribed(5_000)` so upstream stops when nothing observes
- emissions are trimmed with `distinctUntilChanged`, `conflate`, or `buffer` where the collector cannot keep up
- no blocking call (`runBlocking`, blocking I/O, `Thread.sleep`) on a UI or single-threaded dispatcher

Current project gates:

- `scripts/quality/assert-globalscope.ps1`
- `scripts/quality/assert-unsafe-collect.ps1`

Deterministic lifecycle probe (no profiler needed):

- enable Developer Options "Don't keep activities" and background process limit = 1
- keep debug `StrictMode` with `penaltyDeath` on
- exercise rotation, background/foreground, and recents-swipe; surviving this proves state restoration and scope ownership without a heap dump

Review questions:

- what owns this job
- when is it cancelled
- what keeps this object reachable
- what mutates this state, and from which thread
- what happens on rotation, background, and process death

## Layer 3 - Memory ownership audit

Focus on object retention, cache growth, and heavy-resource release.

Check:

- player instances are always released
- Glide or bitmap requests are cancelled when the host dies
- handlers, runnables, and delayed tasks do not capture dead UI
- adapters do not keep stale listeners or view references
- caches have size bounds and eviction rules
- large temporary allocations are not retained across screens
- `Context` usage is minimized and `applicationContext` is used when UI context is not required
- `ViewBinding` references in Fragments are set to `null` in `onDestroyView()` to avoid keeping view hierarchies alive
- `ViewModel` holds no `View`, `Context`, `Fragment`, or `Activity` reference
- `ViewHolder` does not store strong references to Activity/Fragment/Context
- resource closure is enforced using Kotlin's `.use { .. }` block for all `Closeable` types (streams, cursors, DB sessions)

Register/unregister symmetry (high-value for this MediaStore-heavy app):

- every `registerContentObserver` has a matching `unregisterContentObserver` on a lifecycle edge
- every `registerReceiver` has a matching `unregisterReceiver`
- every `addListener` / `addCallback` / `addObserver` (especially `Player.Listener`) has a matching `removeListener` / `removeCallback`
- registration and removal happen on symmetric lifecycle callbacks (`onStart`/`onStop`, `onResume`/`onPause`, `onCreate`/`onDestroy`), never split across asymmetric ones

ExoPlayer / Media3 ownership:

- a single owner holds each player instance; no duplicate or orphaned instance
- release on `onStop` for API 24+ multi-window, re-create on `onStart`
- `setVideoSurface(null)` before release; remove every added `Player.Listener`
- audio focus is abandoned on release; `playWhenReady` is reset
- the player family is mirrored per host - apply the same release contract to every host, not only the one being edited

Glide / bitmap ownership:

- decode at display size with `override(w, h)`; do not load full-resolution into a thumbnail
- prefer `RGB_565` for opaque thumbnails to halve bitmap memory
- `clear(target)` on detach; do not bind `Glide.with(activity)` to a long-lived target
- memory cache and pool sizes are intentional, not default-by-accident

Shared-state ownership:

- run `scripts/quality/audit-shared-state-writers.ps1` to confirm a single intentional writer per shared state, and that consumers read eligibility rather than override it

Debug detector:

- `LeakCanary` in debug builds (`BuildConfig.ENABLE_LEAKCANARY`)

Project integration already present:

- `app_v2/src/debug/java/com/sza/fastmediasorter/core/debug/DebugToolsBootstrap.kt`

Required leak scenarios for critical flows:

- open player -> close player
- browse -> player -> back
- rotate during active playback
- dialog or bottom-sheet open -> dismiss -> reopen
- repeated open/close of the same heavy screen
- process death and restore (via "Don't keep activities")

## Layer 4 - Database and Room audit

Room is core to the stack (2.7.0). Audit every entity, DAO, query, or migration change.

Check:

- no main-thread queries; `allowMainThreadQueries()` stays banned
- DAO methods are `suspend` or return `Flow`, never blocking calls on a UI path
- multi-step writes that must be atomic are wrapped in `@Transaction`
- `@Transaction` is used for relation reads that issue more than one query, to keep the result consistent
- cursors and `RawQuery` results are closed (`.use { .. }`) and not held open across screens
- queries do not load a full table when a bounded page, `LIMIT`, or projection suffices
- frequent `WHERE` / `ORDER BY` columns are indexed; verify with `EXPLAIN QUERY PLAN` for hot queries
- no N+1 pattern - one query per row inside a loop becomes a single join or `IN (..)` query
- `Flow` queries are deduplicated (`distinctUntilChanged`) so an unrelated table write does not re-emit and re-render
- every schema change ships a migration and a migration test; no destructive fallback in release

Review questions:

- does this query run off the main thread end to end
- is the result bounded, or can it grow with the library size
- is this read consistent under concurrent writes
- does a write to an unrelated row wake this Flow

## Layer 5 - Main-thread and startup audit

Check for avoidable work on the main thread:

- disk reads and writes
- database queries
- network calls
- expensive parsing
- oversized object graph initialization
- non-critical startup work that can be deferred

Current project hooks:

- debug `StrictMode`
- `StrictModeHelper`
- `AppStartupInitializer`
- deferred startup worker path
- `reportFullyDrawn()` marker in `MainActivity`

Required questions:

- does this work need to happen before first interaction
- can this be lazy-loaded
- can this move to deferred startup
- can the dependency be injected as `dagger.Lazy<T>`

## Layer 6 - Performance audit

Use two levels:

Static performance review:

- avoid repeated allocation inside hot loops
- avoid repeated path parsing, decoding, sorting, and filtering when results can be reused
- check collection churn in adapters and player helpers
- confirm cache hit path is cheaper than miss path
- prioritize primitive-optimized collections (`SparseArray`, `LongSparseArray`, `ArrayMap`, `ArraySet`) over Java `HashMap`/`HashSet` when keys are numeric IDs to avoid autoboxing
- use `value class` (inline classes) for domain identifiers to eliminate runtime object allocation overhead
- implement partial updates in lists using `DiffUtil` payloads (`getChangePayload`) to prevent full rebinds
- RecyclerView hygiene: stable IDs where rows persist, `setHasFixedSize` when size is fixed, shared `RecycledViewPool` for nested lists, and never `notifyDataSetChanged` when a targeted notify or `ListAdapter` diff applies

Measured performance review:

- benchmark cold start
- benchmark open browse on a large dataset
- benchmark open player
- benchmark first frame or first audio start
- benchmark back-navigation from player

Preferred tooling:

- `Macrobenchmark`
- `Baseline Profiles`
- `Perfetto`
- Android Studio Allocation Tracker for churn, when static review cannot locate the source

Rule:

- use benchmarks to prove regression
- use Perfetto to explain regression
- use allocation tracking to locate churn

## Layer 7 - Release-build and R8 correctness audit

A passing debug build does not prove the shipped artifact is correct. For changes that touch reflection, serialization, DI graphs, manifests, or dependencies, verify on the minified release/target variant.

Check:

- the release build of the affected flavor compiles, packages, and runs the touched flow
- keep rules cover any reflective or serialized type (Gson/Moshi models, Room, reflection-based libraries)
- no unexpected `R8: missing class` or `unresolved` warnings in the minify log
- dead-code shrink did not remove a runtime-needed entry point (ties to `CLAUDE.md` Rule 20)
- the change behaves identically across the flavor matrix it claims to support

Rule:

- a P0/P1 change that affects reflection, DI, or manifests is not done until it is proven on a minified build, not only on debug

## Layer 8 - Mechanical gates

If a review comment appears repeatedly, convert it into a gate.

Preferred order:

1. project script gate
2. custom Android Lint check
3. benchmark threshold

Use script gates for fast pattern enforcement.
Use custom Lint when the rule is structural and should appear directly in IDE feedback.
Use benchmark thresholds when the problem is quantitative.

Recommended future custom Lint rules for this project:

- no Activity business logic
- no UI context stored in singleton or long-lived manager
- no lifecycle-unsafe Flow collection in UI
- no unreleased player/listener ownership pattern
- no direct main-thread disk I/O outside approved wrappers
- no main-thread Room access

Recommended CI/CD automated dynamic analysis additions:

- integrate LeakCanary into instrumented tests via `leakcanary-android-instrumentation` to automatically catch memory leaks on CI

## Standard audit procedure for a change

1. Classify the change and assign an expected severity ceiling.
2. Pick the lowest-cost evidence.
3. Run static architecture and readability review.
4. Run lifecycle, concurrency, memory, and Room review if coroutines, shared state, listeners, player, cache, database, or startup are touched.
5. Run project gates.
6. Run the cheapest validation command from `docs/BUILD_TEST_FAST_PATH.md`.
7. If risk remains, run debug runtime checks (LeakCanary, StrictMode, "Don't keep activities").
8. If the change touches reflection, DI, manifests, or dependencies, prove it on a minified release/target variant.
9. If regression is observed, capture benchmark evidence.
10. If the benchmark fails or the issue is still unclear, capture Perfetto trace, allocation trace, or heap dump.
11. Convert recurring findings into a permanent gate.

## Standard audit checklist for PR review

Use this checklist in review comments or self-review; tag each finding with a severity:

- ownership of every long-lived object is explicit
- every listener, callback, observer, receiver, and job has a symmetric release point
- no dead UI object can remain strongly referenced after destroy
- shared mutable state has one owner and no unsynchronized race
- every Room query runs off the main thread and is bounded
- no heavy startup work is done eagerly without reason
- no main-thread disk, database, or network path is introduced
- cache growth is bounded
- hot paths avoid repeated allocation and repeated expensive lookups
- failure paths clean up resources too
- reflection/serialization/DI changes are proven on a minified build
- logs are useful and do not become permanent debug noise
- readability: no unjustified `!!`, no boolean traps, state is typed
- validation evidence matches the risk of the change

## Standard incident procedure

For crash, ANR, leak, OOM, or jank:

1. classify severity and reproduce on the narrowest possible scenario
2. capture logs
3. capture LeakCanary evidence for retention issues
4. capture benchmark numbers for reproducible slowness
5. capture Perfetto trace for unexplained slowness or jank
6. reduce to one owner, one resource, one lifecycle edge, one race, or one hot path
7. add or tighten a gate so the same class of issue becomes cheaper to catch next time

## FastMediaSorter current baseline

Already present:

- debug `StrictMode`
- `StrictModeHelper`
- `LeakCanary`
- `profileinstaller`
- standard-flavor Macrobenchmark + Baseline Profile harness (`benchmark/`, S0722)
- startup markers
- Perfetto workflow playbook (`docs/PERFETTO_PLAYBOOK.md`)
- quality gates for `GlobalScope` and unsafe Flow collect
- `detekt` + ktlint formatting ratchet gate
- listener symmetry ratchet gate (`scripts/quality/assert-listener-symmetry.ps1`)
- responsibility ranking (`measure-hotspots.ps1`) and shared-state writer audit (`audit-shared-state-writers.ps1`)
- startup deferral infrastructure

Recommended next additions:

1. extend perf coverage beyond `standard` if a flavor-specific hotspot appears
2. wire selected perf commands into CI or managed-device automation when the local flow stabilizes
3. ratchet benchmark JSON summaries once representative device baselines are committed

## Repo commands and anchors

Useful local commands:

```powershell
.\a.ps1 fk
.\a.ps1 fr
.\a.ps1 fc
.\a.ps1 fu
.\a.ps1 mb
.\a.ps1 gbp
.\a.ps1 adb launch
.\a.ps1 adb log -Tail 400 -Grep "FATAL|ANR|Sxxxx"
pwsh -NoProfile -File scripts/quality/assert-globalscope.ps1 -Gate
pwsh -NoProfile -File scripts/quality/assert-unsafe-collect.ps1 -Gate
pwsh -NoProfile -File scripts/quality/assert-listener-symmetry.ps1 -Gate
pwsh -NoProfile -File scripts/quality/measure-hotspots.ps1
pwsh -NoProfile -File scripts/quality/audit-shared-state-writers.ps1
```

Important code anchors:

- `app_v2/src/main/java/com/sza/fastmediasorter/FastMediaSorterApp.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/core/debug/StrictModeHelper.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/core/init/AppStartupInitializer.kt`
- `app_v2/src/debug/java/com/sza/fastmediasorter/core/debug/DebugToolsBootstrap.kt`
- `docs/BUILD_TEST_FAST_PATH.md`
- `docs/PERFETTO_PLAYBOOK.md`

## External references

These references informed the protocol as checked on 2026-06-26:

- Android StrictMode
- Android coroutines best practices
- Kotlin Flow and StateIn/ShareIn guidance
- Android Lint and custom lint checks
- detekt static analysis for Kotlin
- ktlint formatter
- Room best practices and threading
- Media3 ExoPlayer lifecycle and resource release
- Glide caching and bitmap configuration
- Android Macrobenchmark overview
- Android Baseline Profiles overview
- LeakCanary fundamentals
- Perfetto documentation
- Android ProfilingManager API
- OWASP MASTG

Reference URLs:

- https://developer.android.com/reference/android/os/StrictMode
- https://developer.android.com/kotlin/coroutines/coroutines-best-practices
- https://developer.android.com/kotlin/flow/statein-sharein
- https://developer.android.com/studio/write/lint
- https://detekt.dev/
- https://pinterest.github.io/ktlint/
- https://developer.android.com/training/data-storage/room
- https://developer.android.com/media/media3/exoplayer/lifecycle
- https://bumptech.github.io/glide/
- https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview
- https://developer.android.com/topic/performance/baselineprofiles/overview
- https://square.github.io/leakcanary/fundamentals-how-leakcanary-works/
- https://perfetto.dev/docs/
- https://developer.android.com/reference/android/os/ProfilingManager
- https://mas.owasp.org/MASTG/
