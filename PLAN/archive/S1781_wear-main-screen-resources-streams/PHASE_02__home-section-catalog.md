# Phase 02 - Home section catalog

**Strategic spec:** [`../S1781_wear-main-screen-resources-streams.md`](../S1781_wear-main-screen-resources-streams.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 05
**Steps done:** 5 / 5
**Started:** 2026-08-18
**Completed:** 2026-08-18

---

## Objective

Replace `HomeScreen.kt`'s hardcoded chip list with a section catalog the screen only renders - the catalog, not the screen, decides which named sections exist and in what order.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done - `WearViewMode`, `GridColumnFit` and the two preference flows exist.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `wear/src/main/java/com/sza/fastmediasorter/wear/domain/model/HomeSection.kt` | New | ≤ 60 |
| `wear/src/main/java/com/sza/fastmediasorter/wear/ui/home/HomeSectionCatalog.kt` | New | ≤ 90 |
| `wear/src/main/java/com/sza/fastmediasorter/wear/ui/home/HomeUiState.kt` | New | ≤ 30 |
| `wear/src/main/java/com/sza/fastmediasorter/wear/ui/home/HomeViewModel.kt` | New | ≤ 170 |
| `wear/src/main/java/com/sza/fastmediasorter/wear/ui/navigation/WearRoutes.kt` | Modified | ≤ 90 |
| `wear/src/main/java/com/sza/fastmediasorter/wear/ui/home/HomeScreen.kt` | Modified | ≤ 220 |
| `wear/src/main/java/com/sza/fastmediasorter/wear/domain/repository/WearFavoritesRepository.kt` | Modified | ≤ 20 |
| `wear/src/main/java/com/sza/fastmediasorter/wear/data/repository/WearFavoritesRepositoryImpl.kt` | Modified | ≤ 115 |
| `wear/src/main/java/com/sza/fastmediasorter/wear/domain/repository/WearPreferencesRepository.kt` | Modified | ≤ 45 |
| `wear/src/main/java/com/sza/fastmediasorter/wear/data/preferences/WearPreferencesRepositoryImpl.kt` | Modified | ≤ 330 |
| `wear/src/main/java/com/sza/fastmediasorter/wear/MainActivity.kt` | Modified | ≤ 400 |
| `wear/src/main/java/com/sza/fastmediasorter/wear/ui/home/LocalHomeScreen.kt` | New | ≤ 110 |
| `wear/src/main/java/com/sza/fastmediasorter/wear/ui/home/PhoneHomeScreen.kt` | New | ≤ 110 |
| `wear/src/main/java/com/sza/fastmediasorter/wear/ui/common/NotYetHereScreen.kt` | New | ≤ 60 |
| `wear/src/main/res/values/strings.xml` | Modified | ≤ 60 |

---

## Steps

### Step 02.1 - Define the HomeSection model and catalog

**Files:** `wear/src/main/java/com/sza/fastmediasorter/wear/domain/model/HomeSection.kt`, `wear/src/main/java/com/sza/fastmediasorter/wear/ui/home/HomeSectionCatalog.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `enum class HomeSectionId { LAST_USED_RESOURCE, FAVOURITES, RESOURCES, PHONE, LOCAL, STREAMS, APPS }` in the owner's fixed order, and `data class HomeSection(val id: HomeSectionId, @StringRes val labelRes: Int, val route: String, val dynamicLabel: String? = null)` - `dynamicLabel` overrides `labelRes` for the last-used-resource entry, which is shown by its own resource name rather than a generic caption. Add a small `data class HomeSectionVisibility(val favouritesEnabled: Boolean, val hasLastUsedResource: Boolean, val lastUsedResourceLabel: String?, val streamsEnabled: Boolean)` carrying only booleans and the one dynamic string, so the catalog itself takes no repository dependency. In `object HomeSectionCatalog`, add `fun sectionsFor(visibility: HomeSectionVisibility): List<HomeSection>` that returns the seven sections filtered to those actually visible, in the fixed order: LAST_USED_RESOURCE only if `hasLastUsedResource`, FAVOURITES only if `favouritesEnabled`, RESOURCES always, PHONE always, LOCAL always, STREAMS only if `streamsEnabled`, APPS always.

**Why:**

Strategic §5.1 "Каталог разделов главного экрана" states the screen no longer decides what to show - it renders what the catalog returns, and each section carries its own visibility condition rather than the screen branching on it; §5.1 also names this catalog entry as the point S1710 is waiting for (the Apps section) and the point S1708 needed answered (the Streams section) - both list simply as ordinary catalog rows, not screen-specific code.

**Verification:**

- `Glob` - both files exist.
- `Grep` - `enum class HomeSectionId` matches exactly once.
- `Grep` - `STREAMS` and `APPS` both present in `HomeSection.kt`.
- `Grep` - `fun sectionsFor` present in `HomeSectionCatalog.kt`.
- `Grep` - `Log\.d\(` returns zero hits in both files.

**Status:** `[x]` done

**Step Log:**

- 2026-08-18 - HomeSectionId, HomeSection and HomeSectionVisibility added; HomeSectionCatalog.sectionsFor returns the seven sections in the owner's fixed order with per-section visibility. Routes and label strings it references are added by steps 02.3 and 02.4, so the module compiles at the phase build, not mid-phase.

---

### Step 02.2 - Add HomeViewModel and its visibility sources

**Files:** `wear/src/main/java/com/sza/fastmediasorter/wear/ui/home/HomeViewModel.kt`, `wear/src/main/java/com/sza/fastmediasorter/wear/ui/home/HomeUiState.kt`, `wear/src/main/java/com/sza/fastmediasorter/wear/domain/repository/WearFavoritesRepository.kt`, `wear/src/main/java/com/sza/fastmediasorter/wear/data/repository/WearFavoritesRepositoryImpl.kt`, `wear/src/main/java/com/sza/fastmediasorter/wear/domain/repository/WearPreferencesRepository.kt`, `wear/src/main/java/com/sza/fastmediasorter/wear/data/preferences/WearPreferencesRepositoryImpl.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add `data class HomeUiState(val sections: List<HomeSection> = emptyList())`. Add `@HiltViewModel class HomeViewModel @Inject constructor(...)` exposing `val uiState: StateFlow<HomeUiState>` built by combining `HomeSectionCatalog.sectionsFor(...)` with three sources: `WearFavoritesRepository.hasAnyFavorite(): Boolean` (new suspend method - the repository currently exposes `addFavorite`/`removeFavorite`/`isFavorite`/`getPendingDelta` but no aggregate read, so add it alongside the existing encrypted-SharedPreferences-backed methods in the impl), a new `lastUsedResourceName: Flow<String?>` on `WearPreferencesRepository` (null means no history yet) with a matching `setLastUsedResource(name: String)` / `clearLastUsedResource()` pair, added to the same `wear_settings` DataStore Phase 01 used for `viewMode`, and a new `streamsSectionEnabled: Flow<Boolean>` defaulting to `true` on the same repository. No new Hilt module - constructor injection resolves the existing repository bindings.

**Why:**

Strategic §5.1 lists the three sections whose visibility is conditional - Favourites when favourites is enabled, Last used resource when the history is non-empty, Streams when the section is enabled by default per owner point §3.1.7 - and §5.1 "Каталог разделов" makes the screen a passive renderer, so the combining logic has to live in a ViewModel rather than in `HomeScreen.kt`'s current inline `filter {}`.

**Verification:**

- `Glob` - `HomeViewModel.kt` and `HomeUiState.kt` exist.
- `Grep` - `class HomeViewModel` and `@HiltViewModel` both present.
- `Grep` - `hasAnyFavorite` present in `WearFavoritesRepository.kt` and in `WearFavoritesRepositoryImpl.kt`.
- `Grep` - `lastUsedResourceName` and `streamsSectionEnabled` both present in `WearPreferencesRepository.kt`.
- `.\a.ps1 fk` - exit 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-18 - HomeUiState and HomeViewModel added; hasAnyFavorite on the favourites repository, lastUsedResourceName and streamsSectionEnabled on the preferences repository. All greps PASS, :wear:compileDebugKotlin exit 0, detekt-scoped PASS over 9 files. Known limitation recorded in the code: favourites are a suspend read, not a flow, so the section appears on the next home entry after the first star rather than instantly. Hilt graph NOT proven by a compile check - the phase build is what validates it.

---

### Step 02.3 - Add the missing home section routes

**Files:** `wear/src/main/java/com/sza/fastmediasorter/wear/ui/navigation/WearRoutes.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add `const val LOCAL_HOME = "local_home"`, `const val PHONE_HOME = "phone_home"`, a phone-virtual-resources pattern `PHONE_BROWSE_PATTERN = "browse_phone/{$ARG_MEDIA_TYPE}"` with a `fun browsePhone(mediaType: String): String` builder, `const val FAVOURITES = "favourites"`, `const val STREAMS = "streams"` and `const val APPS = "apps"`. `PHONE_HOME` and `LOCAL_HOME` are the section entrances - the list of that origin's categories - while `PHONE_BROWSE_PATTERN` is the per-category destination reached from `PHONE_HOME`, mirroring how `BROWSE_PATTERN` already serves Local. Keep `PHONE_RESOURCE` untouched - that route (S1697) is a raw phone-folder browser reached through a request/response token protocol (`WearPhoneResourceRequest`/`WearPhoneResourceItem`) and is a different feature from the new Phone section, which lists the phone's virtual Video/Audio/Images/Documents/All-files categories the same way `BROWSE_PATTERN` already does for Local. Point the Streams and Apps routes at placeholder composables that show a one-line message naming the owning ticket, S1708 for Streams and S1710 for Apps - they are entrances only, not implementations.

**Why:**

Strategic §5.1 "Раздел Трансляции как вход" and the non-goals in §2 both say this ticket produces only the Streams and Apps entrances, leaving the screens themselves to S1708 and S1710; §5.1 "Раздел Телефон и раздел Локальное" draws the line by content origin, not by media type, which is why Phone needs its own route family instead of reusing `browse()`.

**Verification:**

- `Grep` - `PHONE_BROWSE_PATTERN`, `PHONE_HOME`, `LOCAL_HOME`, `FAVOURITES`, `STREAMS`, `APPS` all present in `WearRoutes.kt`.
- `Grep` - `PHONE_RESOURCE` still present and unchanged (`git diff` shows no line touching it).
- `.\a.ps1 fk` - exit 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-18 - WearRoutes gained LOCAL_HOME, PHONE_HOME, FAVOURITES, STREAMS, APPS, PHONE_BROWSE_PATTERN and browsePhone(); PHONE_RESOURCE left untouched and documented as S1697's separate feature. Plan corrected first: FAVOURITES and PHONE_HOME were missing from the step while step 02.1's catalog needs both. The seven wear_section_* strings were added here rather than in 02.4 so the module compiles; parity audit exit 0, :wear:compileDebugKotlin exit 0 (the WEAR module, not the plan's a.ps1 fk which builds app_v2)

---

> **Plan correction, 2026-08-18 (`/spec-dev`).** The step originally declared neither a Favourites
> route nor a Phone section entrance, while step 02.1's catalog needs both: strategic §5.1 lists
> Favourites as a conditional section and draws Phone as an origin with its own category list, not as
> a single media type. Added `FAVOURITES` and `PHONE_HOME` rather than letting the catalog reference
> names nothing declares.

---

### Step 02.4 - Rewrite HomeScreen to render the catalog

**Files:** `wear/src/main/java/com/sza/fastmediasorter/wear/ui/home/HomeScreen.kt`, `wear/src/main/res/values/strings.xml`
**Depends on:** Step 02.2, Step 02.3

**Prompt for developer:**

> Replace the hardcoded `allCategories`/`filteredCategories` list and the four separate `Chip` blocks with a single loop over `HomeViewModel.uiState.sections`, still rendered as a `ScalingLazyColumn` of `Chip`s for now - Phase 03 introduces the grid. Each chip's `onClick` navigates to the section's route; the Settings chip stays for this phase only (Phase 03.3 moves it to the command bar and removes it from here). Add the seven section display strings through `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key wear_section_<name> -En "<value>" -Ru "<value>" -Uk "<value>"`, one lockstep call per key, prefixed `wear_section_`.

**Why:**

Strategic §5.1 "Каталог разделов главного экрана" - "Экран не решает, что показать, а рисует то, что каталог отдал" - is the literal instruction for this step; §11 criterion 1 is the strategic-level pass condition this step exists to satisfy.

**Verification:**

- `Grep` - `HomeSectionCatalog` or `viewModel.uiState` present in `HomeScreen.kt`; the old `allCategories`/`MediaCategory` list is gone.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "wear_section_"` - exit 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.
- `.\a.ps1 fk` - exit 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-18 - HomeScreen now renders HomeViewModel.uiState.sections; hardcoded allCategories/filteredCategories and the four separate chip blocks removed, MediaCategory deleted as orphaned (no reference outside this file). Icons map from HomeSectionId inside the screen so the domain model carries no Compose types. The pre-existing composing log was restored after an out-of-scope removal. Section labels are plain navigation nouns - COMMUNICATION_POLICY 6 clean. Strings parity exit 0, :wear:compileDebugKotlin exit 0.

---

### Step 02.5 - Register a destination for every catalog route

**Files:** `wear/src/main/java/com/sza/fastmediasorter/wear/MainActivity.kt`, `wear/src/main/java/com/sza/fastmediasorter/wear/ui/home/LocalHomeScreen.kt`, `wear/src/main/java/com/sza/fastmediasorter/wear/ui/home/PhoneHomeScreen.kt`, `wear/src/main/java/com/sza/fastmediasorter/wear/ui/common/NotYetHereScreen.kt`
**Depends on:** Step 02.3, Step 02.4

**Prompt for developer:**

> Register a composable destination in the watch navigation graph for every route `HomeSectionCatalog` can return, so no section is a dead row. `LOCAL_HOME` gets `LocalHomeScreen`, listing the Music, Videos and Photos entries that used to sit on the home screen and navigating to `WearRoutes.browse(mediaType)` - a move of existing rows, not new behaviour, still filtered by the media-type toggles. `PHONE_HOME` gets `PhoneHomeScreen`, which lists the phone's virtual categories navigating to `WearRoutes.browsePhone(mediaType)` and, as its last row, the existing paired-phone folder browser at `WearRoutes.PHONE_RESOURCE`. Add a shared `NotYetHereScreen(ownerTicket: String)` composable showing one line naming the ticket that owns the screen, and point `FAVOURITES`, `STREAMS` and `APPS` at it with S1781, S1708 and S1710 respectively. Every `browsePhone` destination itself is out of scope here: `PhoneHomeScreen` rows that have no destination yet route to `NotYetHereScreen` naming S1781 rather than to a route nothing declares.

**Why:**

Strategic §5.1 makes the home screen a passive renderer of the catalog, which means every catalog row must lead somewhere or the change is a regression rather than a feature; the paired-phone folder browser is specifically preserved because its existing code comment records that it stays reachable with no phone connected - the screen itself explains the absence and offers Retry - and removing its only entrance would delete a shipped S1697 capability that this ticket never set out to touch.

**Verification:**

- `Glob` - `LocalHomeScreen.kt`, `PhoneHomeScreen.kt` and `NotYetHereScreen.kt` all exist.
- `Grep` - `WearRoutes.LOCAL_HOME`, `WearRoutes.PHONE_HOME`, `WearRoutes.FAVOURITES`, `WearRoutes.STREAMS`, `WearRoutes.APPS` each appear in the navigation graph file as a registered `composable(..)` destination.
- `Grep` - `WearRoutes.PHONE_RESOURCE` still reachable: it appears in `PhoneHomeScreen.kt`.
- `Grep` - `Log\.d\(` returns zero hits in the three new files.
- `pwsh -NoProfile -File scripts/builders/check-standard-fast.ps1 -Module wear -Mode Code` - exit 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-18 - Every catalog route now has a registered destination: LOCAL_HOME->LocalHomeScreen (the three media chips moved down from home, still filtered by the media-type toggles), PHONE_HOME->PhoneHomeScreen (five virtual categories plus the S1697 folder browser kept reachable as its last row), PHONE_BROWSE_PATTERN/FAVOURITES/STREAMS/APPS->NotYetHereScreen. Deviation from the step prompt: the placeholder shows a friendly line and logs the owning ticket instead of printing the ticket id on screen - a ticket id in the interface is developer text and fails COMMUNICATION_POLICY 6. detekt caught an ImportOrdering violation in MainActivity, fixed. detekt-scoped PASS, :wear:compileDebugKotlin exit 0.

---

> **Plan correction, 2026-08-18 (`/spec-dev`), second pass.** Step 02.4 as written replaced the home
> screen's chips with the seven catalog sections, but five of those routes had no registered
> destination and the step removed the only entrance to S1697's paired-phone folder browser along
> with them. Implementing it literally would have shipped a home screen with five dead rows and
> deleted a working capability. Step 02.5 was added to give every catalog row a destination and to
> re-home the S1697 entrance; the phase step count moves from 4 to 5.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/wear.jsonl` regenerated via `pwsh -NoProfile -File dev/CATALOG/scripts/scan.ps1 -Module wear`.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

`HomeSection`, `HomeSectionCatalog` and `HomeViewModel` exist; `HomeScreen.kt` renders the catalog as a list. `streamsSectionEnabled` defaults to `true` in `WearPreferencesRepository`; Step 03.5 places its toggle in the existing Media types settings section, per the owner ruling of 2026-08-18, so strategic §3.1.7 is covered rather than deferred. `lastUsedResourceName` is read but nothing writes it yet - Phase 05.2 wires the write at the Resources page's browse-source call site, so the Last used resource section stays correctly invisible (empty history) until Phase 05 lands.

---

## Rollback Plan

Revert phase commit(s) - `HomeScreen.kt` reverts to its Phase-01-era hardcoded chip list; the new repository fields are additive and unread by anything else if reverted.
