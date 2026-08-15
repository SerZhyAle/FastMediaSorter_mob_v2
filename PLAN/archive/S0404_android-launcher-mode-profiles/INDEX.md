# Tactical Plan: S0404 - android-launcher-mode-profiles

**Strategic spec:** [`../S0404_android-launcher-mode-profiles.md`](../S0404_android-launcher-mode-profiles.md)
**Research inputs:** [`research/01__home-role-acquisition.md`](research/01__home-role-acquisition.md) · [`research/02__package-visibility-launch.md`](research/02__package-visibility-launch.md) · [`research/03__profiles-flavor-mapping.md`](research/03__profiles-flavor-mapping.md) · [`research/04__custom-status-bar-and-kiosk.md`](research/04__custom-status-bar-and-kiosk.md) · [`research/05__android-settings-and-quick-targets.md`](research/05__android-settings-and-quick-targets.md) · [`research/12__play-launcher-publishing.md`](research/12__play-launcher-publishing.md)
**Historical / deferred research (no phase here):** `research/06` (kiosk rationale - withdrawn), `research/07` → S0426, `research/08` → S0427, `research/09` → S0428, `research/10` → S0429.
**Feature:** Launcher mode - the app as the device home screen with a Windows-desktop-style configurable surface (iteration 1)
**Tier:** 4 - Strategic (ad-hoc)
**Priority:** 50
**Status:** Verified (2026-07-18 - owner device pass confirmed, incl. vendor Home-role/reboot gate)
**Phases:** 10 / 10 done (Phase 09 descoped to S1102)
**Last updated:** 2026-07-18

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.
>
> **This plan replaces the 2026-06-11 plan** (archived at `temp/S0404/stale_tactical_20260717/`), which targeted the pre-rewrite model (kiosk ladder, flavor-split registry, in-activity status bar). Do not consult the archived files.
>
> **Iteration-1 boundary (strategic §3.3, quiz 2026-07-16/17):** Windows-desktop model - user-filled grid of shortcut cells + our gadgets, persistent taskbar (Start / recents / pinned / tray), Start menu, own launch journal. Carrier flavors: `standard` + `noLegal` (noLegal is the all-inclusive sideload superset - project invariant); `lite` / `photos` / `legacy` / `vr` get the no-op contract. NO kiosk, NO device owner, NO `QUERY_ALL_PACKAGES`, NO third-party AppWidgets, NO desktop pages, NO preset layout gallery (item 12), NO unit tests for launcher-mode UI (owner validation decision).

---

## Reuse map (read before any phase)

The **App-Launch Panel** stack (S0623/S0663) already implements most primitives. Phases below extend, never duplicate:

- Command-in-a-TEXT-column pattern: `domain/model/panel/AppLaunchPanelRouteTarget.kt` (prefix encode/decode).
- Internal feature catalog: `core/panel/InternalRouteCatalog.kt` (route key → label/icon/intent, `settingsIntent` degradation).
- OS settings targets with resolve-guard: `core/panel/OsShortcutCatalog.kt` (17 curated `Settings.ACTION_*`).
- Launch executor pattern: `domain/usecase/panel/LaunchAppLaunchPanelTileUseCase.kt`.
- Installed-apps enumeration: `domain/usecase/panel/QueryLaunchableAppsUseCase.kt` → `LaunchableApp(packageName, label, icon)`.
- Pickers (FragmentResult-based): `ui/applaunchpanel/edit/AppPickerDialogFragment.kt`, `InternalRoutePickerDialogFragment.kt`, `ResourcePickerDialogFragment.kt`, `OsShortcutPickerDialogFragment.kt`.
- Launch-intent factories: `BrowseActivity.createIntent(context, resourceId, ...)`, `PlayerActivity.createPanelIntent(context, resourceId, ..., isSlideshowEnabled, shuffleOnStart, ...)`, `StreamsActivity.createPlayIntent(context, url)`.
- `<queries>` MAIN/LAUNCHER already declared in `app_v2/src/main/AndroidManifest.xml` (S0623) - **no new visibility declaration needed**.
- Capability source-set pattern: `src/ocrEnabled` / `src/screenCapture` mounts + `addStaticManifestFile` in `app_v2/build.gradle.kts`.
- Profile presets: `DeviceProfileRepository` + `DeviceProfileType` (`data/model/DeviceProfile.kt`); profile seeds once, user owns the result (ADR-4).

Key decisions frozen for this plan:

- New Room tables (`launcher_cells`, `launcher_journal`, `launcher_pins`, `launcher_state`) + a new command codec `LauncherCellCommand` (superset of the panel prefixes, adds `app:`, `res:<id>:<mode>`, `stream:<id>`). Panel storage/encoding is NOT touched.
- Persistence + domain layers live in `src/main` (precedent: `AppLaunchPanelTileEntity` lives in main while the panel ships in 2 flavors). All launcher UI lives in `src/launcherEnabled`.
- Settings placement: a NEW collapsible group "System launcher" on the Operations settings surface (`fragment_settings_destinations.xml`, hosted by `OperationsSettingsFragment`), sibling of the "Operating system interaction" group. Owner addition 2026-07-17 supersedes the 2026-06-11 "rows inside System apps" placement.
- Welcome wizard: launcher-mode toggle on the very first page (owner addition 2026-07-17).

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | capability-foundation | - | ✅ Done | 6/6 | [PHASE_01__capability-foundation.md](PHASE_01__capability-foundation.md) |
| 02 | config-persistence | 01 | ✅ Done | 6/6 | [PHASE_02__config-persistence.md](PHASE_02__config-persistence.md) |
| 03 | command-execution | 02 | ✅ Done | 4/4 | [PHASE_03__command-execution.md](PHASE_03__command-execution.md) |
| 04 | home-surface | 01, 02, 03 | ✅ Done | 6/6 | [PHASE_04__home-surface.md](PHASE_04__home-surface.md) |
| 05 | taskbar-start-menu | 03, 04 | ✅ Done | 6/6 | [PHASE_05__taskbar-start-menu.md](PHASE_05__taskbar-start-menu.md) |
| 05B | desktop-2d-grid | 04 | ✅ Done | 5/5 | [PHASE_05B__desktop-2d-grid.md](PHASE_05B__desktop-2d-grid.md) |
| 06 | gadgets | 05B | ✅ Done | 6/6 | [PHASE_06__gadgets.md](PHASE_06__gadgets.md) |
| 07 | edit-mode | 05B, 06 | ✅ Done | 6/6 | [PHASE_07__edit-mode.md](PHASE_07__edit-mode.md) |
| 08 | activation-settings-seeding | 05, 06, 07 | ✅ Done | 7/7 | [PHASE_08__activation-settings-seeding.md](PHASE_08__activation-settings-seeding.md) |
| ~~09~~ | ~~user-docs~~ → **moved to S1102** | 08 | ⏭️ Descoped 2026-07-18 | - | [PHASE_09__user-docs.md](PHASE_09__user-docs.md) |
| 10 | docs-catalog-cleanup | 07, 08 | ✅ Done | 4/4 | [PHASE_10__docs-catalog-cleanup.md](PHASE_10__docs-catalog-cleanup.md) |

> **Phase 09 descoped 2026-07-18 (owner decision).** End-user documentation (HOW_TO + FAQ, site build) is moved to **S1102** (`PLAN/S1102_launcher-mode-user-docs.md`) and executed after the launcher refinements (S1087-S1101) land and the owner signs off on the UX. Phase 10 (epic-closing bookkeeping) no longer depends on Phase 09. FEATURES showcase still flows from the Phase 10 ALL_FEATURES record via `/skill-release`.

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- none. (Strategic §6 item 14 - vendor Home-role check on the owner's live device - was re-scoped by owner quiz 2026-07-17: it runs in parallel and gates **release/verify**, not tactic or implementation start. It is tracked in the Completion Gate below. Items 12/13 are explicitly out of iteration-1.)

---

## Completion Gate

- [x] All phases show ✅ Done. (09 descoped to S1102.)
- [x] **Vendor gate (strategic §6 item 14):** Home role assignment + reboot survival confirmed on the owner's live target device 2026-07-18.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - NOT edited per-spec; §8 sentence is delivered via `docs/ALL_FEATURES.jsonl` record (Phase 10.1, `settings-navigation.launcher-mode`) and picked up by `/skill-release`.
- [x] `dev/CHANGELOG.md` has an entry for every phase - Phase 10 logged 2026-07-18.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (public API changed) - Phase 10.2.
- [x] Release `standard` merged manifest contains no `QUERY_ALL_PACKAGES` (Phase 10.3 check; strategic §11.13) - actual 0.
- [x] S0404 advanced to `Verified` 2026-07-18 after the owner device pass; `S0404:` probes removed, `.\a.ps1 fk` BUILD SUCCESSFUL. (A full `/spec-check S0404` audit can still be run on demand.)

> **Closure note 2026-07-18 (owner decision "close S0404 now").** All S0404 code is complete. The only open work is on-device verification (tactical 07.6 + 08.7 walkthroughs + the §6.14 vendor gate on the live target device), which is exactly what `BlockNeedUserTest` tracks - so the epic is parked there rather than left `In Progress`. User docs (Phase 09) → **S1102**; refinements from device testing → **S1087-S1101**, executed on top of this baseline. `/spec-check S0404` → `Verified` runs after the owner's device pass and removes the `S0404:` probes.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal` (with `-StatusNote`).
5. All done: flip `Status:` to `Done`, run `/spec-check S0404`.

---

## Deep Audit - phases 01-04 (2026-07-17)

Layered audit per `docs/CODE_AUDIT_PROTOCOL.md` across 6 dimensions (Room/data, concurrency, flavor/build, codec contract, spec compliance, dead weight), every finding then handed to an independent skeptic instructed to refute it: **27 raised → 19 refuted → 7 distinct defects. No P0, no P1, no release blocker.**

Fixed immediately:

- **P2 - recents strip enumerated every installed app.** `QueryRecentLauncherAppsUseCase` called `QueryLaunchableAppsUseCase()` inside `Flow.map`, so each journal write (every launch) re-queried `MAIN/LAUNCHER` across all packages and decoded every icon, to keep six. Rewritten to resolve the journalled packages one by one with a terminal `flowOn(Dispatchers.IO)`, mirroring `ResolveAppLaunchPanelTilesUseCase`. Latent (no collector until Phase 05) but Phase 05 is structurally barred from fixing it from outside - hence fixed before it lands.
- **P3 - double-tap guard released too early.** `launchInFlight` cleared when the journal write finished (~10 ms), not when the launch was over, so it missed the 150-300 ms gesture it was added for. Now held until `onHomeResumed()` (the lifecycle edge that ends the exposure) and released immediately on the failure path so a dead cell stays retryable. The audit also disproved the original harm claim - `singleTask` + HOME task means a second tap never produced a second Activity - and rejected the tempting `FLAG_ACTIVITY_SINGLE_TOP` "fix", which would have broken same-resource relaunch through the shared `startIntent`.
- **P3 ×3 - dead weight (Rule 21).** Deleted `LauncherPinDao.replaceAll/clearAll/insertAll` (+ its `@Transaction` import) and `LauncherDesktopRepository.updateCell` (+ impl): zero consumers repo-wide and zero mentions in PHASE_05..PHASE_10. `addCell` already replaces by id (`@Insert(REPLACE)`), the same collapse `AppLaunchPanelRepository.setTile` made.
- **P3 - stale doc.** `.github/copilot-instructions.md` still advertised `-Flavor Standard|NoLegal` after Phase 01 widened the ValidateSet.
- **P3 - plan hygiene.** Step 02.4 still read `[~] in progress` while its phase was Done 6/6; its deferred schema-41 predicate had been resolved in step 02.6.

**Design gap surfaced (D4/spanH):** `GridLayoutManager` supports only horizontal spans, so the persisted `spanH` had no mechanism behind it - every planned "2×2" gadget would have rendered one row tall. This audit recorded a workaround (force the gadget cell height to `cellSizePx × spanH`) and handed it to Phase 06. **That workaround is SUPERSEDED** - the Phase 06 discovery sweep found it incomplete on four counts and traced the real root cause: the persistence model is a 2D canvas and the renderer was a linear flow, so `rowIndex`/`colIndex` never reached the screen either. Owner decision 2026-07-17: true 2D (ADR-9), delivered by the inserted **Phase 05B**.

Notable refutations (recorded so they are not re-litigated): the `updateColumns` read-modify-write race (raised 3×) has no reader and no concurrent writer; `ExecuteLauncherCommandUseCase`'s PackageManager calls on `Main.immediate` are single cached binder hops, matching every shipped precedent; `getAt` being span-blind cannot fire because nothing writes a cell yet; the focus-highlight gate's green on `src/launcherEnabled` was empirically shown correct, not a false green; the renderer ignoring `rowIndex/colIndex` is Phase 07/08's job.

---

## Deep Audit - phase 05 (2026-07-17)

Same shape as the 01-04 audit (6 dimensions, then two lens-diverse skeptics per finding, both told to refute): **20 raised → 14 unique → 3 refuted → 11 defects. Details and the recorded refutations live in [PHASE_05](PHASE_05__taskbar-start-menu.md#phase-audit-2026-07-17).**

**One P0, and it was shipped by me, not inherited:** all nine `launcher_tray_battery_*` strings carried a PowerShell escape backtick inside the format specifier (`%1``$d`), so `getString(id, percent)` threw `UnknownFormatConversionException` on the tray's seed path during `setupViews()` - killing the declared HOME activity into a **crash loop with no usable home screen**. Fixed; repo-wide grep for `%[0-9]``\$` now returns 0.

The lesson is about gates, not about the typo. `check_strings_localized.ps1` proves keys exist, not that values format; aapt2 only rejects multi-arg non-positional strings; detekt never reads string values. **Nothing static could have caught it** - only the device pass in step 05.6, which was ticked without running. That tick is now corrected to build-half-only, and the device pass is flagged as the highest-value item of the Phase 10 `BlockNeedUserTest` run.

Also fixed: the all-apps grid inflated every installed app in one main-thread pass (`wrap_content` RecyclerView in a `NestedScrollView` → measured `UNSPECIFIED`, zero recycling); a second All-apps tap started a duplicate full enumeration (guard read `itemCount`, which stays 0 while in flight); the resource picker could be stacked twice; enough pins would have laid the tray out past the right edge (fixed pre-emptively - latent until Phase 07 adds pin writes, which is why one skeptic refuted it); the tray lost right-edge anchoring when recents was hidden; the exit dialog leaked its window on an Activity-recreating config change; one orphaned import; three plan-honesty defects.

Notable refutation (recorded so it is not re-litigated): the Android-settings section shipping as ONE row instead of the "settings + Wi-Fi + date/time" list was refuted **on its premise** - the step cites research 05 as its authority, and that document makes Wi-Fi and date/time explicitly optional. The contract is satisfied.

---

## Deep Audit - phase 06 (2026-07-17)

Four dimension auditors, findings hand-verified against the code. **9 defects, no P0/P1. Every step predicate re-ran clean.** Details and refutations in [PHASE_06](PHASE_06__gadgets.md#phase-audit-2026-07-17).

Two were live user-visible bugs no gate could reach:

- **The `ic_cast` fallback was a white glyph on a white card** - `ic_cast.xml` fills `@android:color/white`, carries no vector tint, and I set none. Notably my first fix was *worse than the bug*: a blanket `app:tint` on the row would have repainted every favicon bitmap flat and destroyed `ic_music_note`'s gold gradient. Tint is now per-row.
- **"Home stays cheap" was false in two independent ways.** The desktop rebuilt itself 2-3x on every Home entry (two replaying StateFlows both landing in `bind()`), and each rebuild threw away the gadget views - which re-decoded the **6.44 MB** favicon atlas (measured from the shipped artifact) to use 40 KB of it, and re-ran a **full physical folder rescan** because my KDoc's claim that `GetMediaFilesUseCase` "consults its own caches first" is untrue (its skip gate needs `rememberFileList`, default false).

Also fixed: `take(4)` on a 5000-file folder returned arbitrary files (`GetMediaFilesUseCase` skips sorting above 1000, so ordering is now the launcher's own job); `runCatching` reported leaving Home as a scan failure; `.first()` where the contract said collect; a duplicated alpha literal; one deviation the step had explicitly ordered recorded.

The UI dimension found the worst one by reading RecyclerView's source: **the folder preview rendered as two tall columns, not a 2x2** - `GridLayoutManager` measures a child on the scroll axis against the whole viewport, so a `match_parent` tile became full-grid-height and two of the four files fell below the fold. Fixed by deleting the RecyclerView: four tiles are a fixed count, so recycling bought nothing while the LayoutManager fought the layout. Also: play/random had no press feedback (focus drawable used as `background` replaces the ripple - its own KDoc forbids it), and the clock clipped at density factor 1.5.

Recorded refutation: `HTTP_STREAM`/`RTSP_STREAM` DO take the local branch (`isNetworkResource` answers "carries SMB-style credentials", not "touches the network") - but `MediaScannerFactory` throws on them, so no socket opens. The protection is **incidental**, not designed; a future refactor of that throw would silently put Home on the network.

Parked out-of-scope (CLAUDE.md 3.1): **`S1081`** - `android:foreground` on a `MaterialCardView` is overwritten by the library in its constructor, so the focus ring never draws on ANY card, including the shipped app-launch panel tile. Affects D-pad/TV, which is exactly this feature's target hardware.

---

## Deep Audit - phase 07 part 1 (2026-07-17)

Four dimension auditors over the landed half of the phase. **No P0, no P1 in what landed** - the no-overlap invariant, the `findOverlapping` SQL, the transactions and the picker retrofit were all confirmed correct rather than assumed. Details and refutations in [PHASE_07](PHASE_07__edit-mode.md#phase-audit-2026-07-17).

The theme this time was **claims of mine that were not true**, in both directions. Two recorded "problems" were not real (Rule 22 does not reach a settings flag with no UI row; gadget default spans already existed), and two real ones were dressed as done: step 07.1's verification predicate still grepped for `EmptySlot`/`cellRemoveBadge`, which ADR-9 had made impossible - it could not pass, so ticking it would have been fiction - and the invariant that is this phase's headline had no test at all, though `InMemoryRoomHelper` was already used by 22 other repository tests and 05B had set the precedent by testing render geometry. Now covered by 23 new tests (34/34 green with the pre-existing geometry suite).

Two defects shared one root: **the grid clamp was re-typed in three files and one copy floored the column but not the row**. That copy would have put a "tap to add" slot on top of a live cell. Fixed at the cause - `LauncherGridGeometry.footprint` is now the only definition - and paired with write-side normalisation, because a zero span makes a rectangle that intersects nothing, so the DB would have reported an occupied square free forever while the screen showed the cell. Unreachable today; step 07.2 is where literal spans first reach `addCell`.

Also fixed: the remove badge had no press feedback (the same `focus_button_background`-as-`background` misuse phase 06 fixed project-wide one phase earlier), every badge announced identical words to a screen reader, and `launcher_edit_kind_resource` called a resource a "folder" in all three locales - forbidden by name in `COMMUNICATION_POLICY.md:149`.

**Owner decision 2026-07-17 - drag onto an occupied square: equal footprints trade places, mismatched ones are rejected.** This supersedes both the plan's blanket swap and the blanket reject I had recorded after part 1. My reject-all argument leaned on "Windows does not swap desktop icons", which was wrong about the hardware: this is a finger/D-pad grid on a phone, tablet or TV, where Pixel Launcher, One UI and Nova all swap equal icons, and 1x1-onto-1x1 is the most common drag there is. The reject-half survives - a 2x2 cannot take a 1x1's place without covering that 1x1's neighbours. A trade exchanges *anchors*, never the dropped square: that is collision-free by construction and needs no second lookup.

---

## Blockers Log

- 2026-07-18 - **listener-symmetry gate +1 (baseline 133 -> 134), advisory.** Source is `LauncherTrayManager` (phase 05): `lifecycle.addObserver(this)` (auto-detached on destroy) + `registerReceiver(null, IntentFilter(ACTION_BATTERY_CHANGED))` (sticky-intent read, nothing to unregister). The real registrations - `ContextCompat.registerReceiver` and `registerDefaultNetworkCallback` - are balanced by `unregisterReceiver`/`unregisterNetworkCallback` (lines 102/120). Assessed a **heuristic false positive, not a leak**; NOT introduced by the Phase 10 close (which added only `Timber.d` probes). Baseline deliberately left un-ratcheted for the phase 07/08 finish audit to confirm/refine, rather than bumped during a bookkeeping close. Does not block the epic's device pass.

---

## Change Log

- 2026-07-17 - Plan re-authored from scratch by `/spec-tech` after the 2026-07-16 strategic rewrite (Windows-desktop model). Previous 2026-06-11 plan archived to `temp/S0404/stale_tactical_20260717/`.
