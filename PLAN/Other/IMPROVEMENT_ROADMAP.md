# FastMediaSorter v2 — Improvement Roadmap

**Date**: March 11, 2026 (updated: March 23, 2026)
**Source**: `PLAN/IMPROVEMENT_PROPOSAL.md`
**Ordering**: Risk & compliance first → Quick wins → Feature completion → Strategic

---

## Recently Completed (March 2026)

| Item | Description | Date |
|------|-------------|------|
| III.2 | Network delete confirmation dialog | March 22 |
| III.3 | Favorites export/import (JSON, file picker, preview) | March 22 |
| III.4 | Favorites backup (integrated into BackupRestoreFragment) | March 22 |
| — | Camera Photos virtual folder + widget | March 22 |
| — | Widget preview images for Android 12+ picker | March 22 |
| — | Settings subtitles for all toggles/checkboxes (EN/RU/UK) | March 22 |
| — | Player red glow pressed-state buttons | March 22 |
| — | Enhanced lyrics search (4 sources) + cover art (3 APIs) | March 22 |
| — | CP1251 auto-detect for Cyrillic ID3 tags | March 22 |
| — | Virtual aggregate folders (5 types + Camera) | March 14–22 |
| — | Google Drive backup/restore | March 12–14 |
| — | Resume Next Time | March 16 |
| — | Background audio service | March 16–19 |
| — | Standalone player / "Open with" | March 18–20 |
| — | Random Music + Camera Photos widgets | March 21 |
| X.15 | Edge-to-Edge / Insets — full WindowInsets impl for all Activities | March 23 |
| X.14 | Material You — DynamicColors for wallpaper-based theming on Android 12+ | March 23 |
| X.13 | Gradle Version Catalog — `libs.versions.toml` for dependency management | March 23 |
| IV.10 | Debug timing cleanup — guarded DEBUG timing code with BuildConfig.DEBUG | March 23 |
| X.19 | App Shortcuts — long-press static + dynamic shortcuts for recent resources | March 23 |
| X.17 | Favorites in GDrive backup — favorites included in BackupPayload v2 | March 23 |
| X.16 | Quick Settings Tile — audio play/pause toggle from notification shade | March 23 |

---

## TIER 0 — Security & Compliance (MUST before next release)

| # | Item | Description | Why urgent |
|---|------|-------------|------------|
| V.2 | Remove test credentials | Move `sza_resources.xml` to debug sourceSet | Plaintext passwords in any APK build |
| IV.2 | TrustAll SSL audit | Audit lint-baseline entries; eliminate or pin certs | MITM vulnerability in FTPS/SFTP |

---

## TIER 1 — Quick Wins (1-2h, zero risk)

| # | Item | Description | Why easy |
|---|------|-------------|----------|
| ~~IX.4~~ | ~~WorkManager backoff~~ | ~~Add `BackoffPolicy.EXPONENTIAL` to 4 workers~~ | **DONE** — all 6 workers now have exponential backoff |
| VIII.1 | Text min 12sp | Set minimum 12sp for all text elements | Update 3-4 values in `dimens.xml` |
| I.4 | Update screenshots | Refresh store_assets/ and docs/images/ | Manual capture, no code risk |

---

## TIER 2 — Easy (2-4h, low risk)

| # | Item | Description | Why easy |
|---|------|-------------|----------|
| ~~IX.2~~ | ~~IntegrationTestRunner → debug~~ | ~~Move 4471-LOC test runner out of production code~~ | **DONE** — already in src/debug + src/release stub |
| ~~VIII.3~~ | ~~Wear OS localization~~ | ~~Add `values-ru/` and `values-uk/` to wear~~ | **DONE** — 52 strings translated to RU + UK |
| IX.3 | Metadata cache TTL | Auto-cleanup old FileMetadataCache entries | Add timestamp column + cleanup query |
| IX.6 | Audio cache TTL | TTL/LRU for AudioMetadataCacheRepository | Add timestamp + size limit |

---

## TIER 3 — Moderate (4-8h, medium risk)

| # | Item | Description | Risk factor |
|---|------|-------------|-------------|
| III.13 | Now Playing UI | Audio queue, bottom sheet or notification with track info | Must integrate with AudioPlaybackService state |
| III.11 | StandalonePlayer file ops | Delete, Share, Favorite, Open-in-Browse actions | Needs file operation infrastructure wiring |
| III.12 | StandalonePlayer playlist | Build temp playlist from ACTION_SEND_MULTIPLE | Multi-URI handling + prev/next nav |
| X.12 | KAPT → KSP | Migrate Room, Hilt, Glide to KSP | Build config change; test all annotation processors |
| X.10 | Crashlytics integration | Activate Firebase Crashlytics + Performance traces | Firebase config + proguard; touches release pipeline |
| X.5 | HEIF/HEIC support | Test Glide support; add fallback decoder if needed | Device-specific failures possible |
| VIII.2 | TalkBack testing | Fix accessibility issues across key screens | Manual audit + targeted fixes |
| V.1 | WebView EPUB sandbox | CSP headers, disable JS for untrusted content | Risk of breaking scroll tracking |
| II.4 | Screen transition animations | Add shared element / Activity transitions | Must not break backstack or orientation |
| V.3 | FTP deprecation warning | Show warning in AddResource for FTP protocol | UI-only dialog, very low risk |

---

## TIER 4 — Substantial (8-16h, notable risk)

| # | Item | Description | Risk factor |
|---|------|-------------|-------------|
| III.6 | Wear OS export/import | Send resource config to watch via Wearable Data Layer | Cross-device sync API complexity |
| III.7 | Batch rename | Mass-rename files with templates/patterns | File system ops at scale; undo complexity |
| IX.1 | Unified Result type | Single `AppResult<T>` replacing 5+ result types | Touches every UseCase/Repository; migration risk |
| IV.8-9 | CI/CD + Coverage | GitHub Actions pipeline + Jacoco coverage | Infrastructure setup; secrets management |
| VIII.4 | Landscape dialogs | Landscape-adaptive layouts for 25+ dialogs | Large regression surface |
| X.11 | Background thumbnail preload | WorkManager-based thumbnail pre-generation | Network traffic management; cache coordination |
| X.2 | Cast / Screen Mirror | Chromecast slideshow output | Google Cast SDK; receiver app needed |
| III.3+ | Favorites cloud sync | Auto-sync favorites via GDrive/Dropbox | Conflict resolution logic |
---

## TIER 5 — Complex (16-50h, high risk)

| # | Item | Description | Why hard |
|---|------|-------------|---------|
| X.1 | Duplicate detection | Find duplicate files across resources (local/SMB/SFTP/FTP/cloud); Resource Ops menu in Browse with 4 operations | 29 impl steps; 26 new files; hashing across all protocols incl. cloud; 3-phase algorithm; WorkManager integration |
| IV.1 | Refactor giant files | Decompose 11+ files exceeding 1500OC (25k+ total) | Touches core flows; BrowseViewModel 3.4k LOC |
| III.5 | RAW formats | CR2/NEF/ARW/DNG preview support | Native decoders / LibRaw; device-specific failures |
| II.3 | Tablet / large screen | Two-pane layouts for Browse + Player | Full regression across key screens |
| III.14 | Custom virtual folders | User-defined aggregate folders with filters | New entity + config UI + scan integration |
| X.9 | Auto-sort rules | Rule engine for automatic file sorting | Rule builder + scheduler + file watchers |
| X.18 | File comparison / Diff | Side-by-side image/text comparison | New UI paradigm + visual diff algorithms |

---

## TIER 6 — Mind-Blowing (architectural shifts)

| # | Item | Description | Why monumental |
|---|------|-------------|----------------|
| II.1 | Navigation Component | Replace all `Intent`/`startActivity` navigation | Migrate 12+ Activities to nav graph |
| II.2 | Compose adoption | Migrate XML layouts to Jetpack Compose | 137+ XML layouts to replace incrementally |
| III.8 | Tags / labels system | User-defined color tags for files | New Room entity + UI filtering + cross-resource |
| III.10 | Drag-and-drop | File operations via drag-and-drop | Platform DnD API + two-pane prerequisite |
| X.7 | Built-in DLNA server | Share local folders over network | Entirely new subsystem; protocol complexity |
| X.8 | Voice commands | Hands-free player control | SpeechRecognizer + intent mapping for custom actions |

---

## Recommended Execution Order

### Phase 1: Security & Play Store Compliance (Sprint 1)
> Zero-tolerance items. Complete before any Play Store update.

1. **V.2** — Test credentials → debug sourceSet (1h)
2. **IV.2** — TrustAll SSL audit (2-4h)

### Phase 2: Quick Wins + Feature Completion (Sprint 2)
> Maximum user-visible improvement with minimal risk.

3. **IX.2** — IntegrationTestRunner out of production (2h)
4. **VIII.1** — Min text 12sp (1h)
5. **IX.4** — WorkManager backoff (1h)
6. **III.13** — Now Playing UI / audio queue management (8-16h)

### Phase 3: StandalonePlayer + Infrastructure (Sprint 3)
> Complete the standalone player story + build health.

7. **III.11** — StandalonePlayer file operations (4-8h)
8. **III.12** — StandalonePlayer multi-file playlist (4-8h)
9. **X.12** — KAPT → KSP migration (4-8h)
10. **IX.3** + **IX.6** — Cache TTL policies (4h total)
11. **X.10** — Crashlytics + Performance (4-8h)

### Phase 4: Platform Coverage (Sprint 4)
> Wear OS, accessibility, cross-device experience.

12. **VIII.3** — Wear OS localization (2-4h)
13. **III.6** — Wear OS resource sync (8-16h)
14. **X.5** — HEIC/HEIF support (4-8h)
15. **IV.8-9** — CI/CD + Coverage pipeline (8-16h)

### Phase 5: Quality & Polish (ongoing)
> Parallel with feature work.

16. **I.4** — Screenshot refresh
17. **VIII.2** — TalkBack audit
18. **VIII.4** — Landscape dialogs
19. **V.3** — FTP deprecation warning

### Phase 6: Strategic Initiatives (dedicated sprints, require design phase)
> High-impact features that need PLAN/ specs before implementation.

20. **III.7** — Batch rename
21. **X.1** — Duplicate detection
22. **X.11** — Background thumbnail preload
23. **X.2** — Chromecast slideshow
24. **IV.1** — Decompose giant files (ongoing, 1-2 files per sprint)

### Phase 7: Architectural Evolution (only when stable)
> Long-term investments. Start only after Phase 1-4 complete.

25. **II.2** — Compose adoption (new screens only → gradual)
26. **II.3** — Tablet two-pane layout
27. **II.1** — Navigation Component migration
28. **III.8** — Tags/labels system

---

## Key Changes vs. Previous Roadmap (March 11 → March 23)

| Change | Rationale |
|--------|-----------|
| **IV.10, X.13, X.14, X.15, X.16, X.17, X.19 COMPLETED** | Debug timing, Version Catalog, Material You, Edge-to-Edge, QS Tile, Favorites backup, App Shortcuts all shipped March 23 |
| **III.2, III.3, III.4 removed** (completed) | Network delete dialog, favorites export/import all shipped March 22 |
| **X.12 KAPT→KSP at TIER 3** | KAPT is deprecated; KSP improves build speed; Room/Hilt support KSP (Glide stays KAPT—no KSP support) |
| **X.13 Version Catalog added** | Dependencies hardcoded in 2 gradle files; toml enables IDE update hints |
| **X.15-X.19 new items** | Edge-to-Edge, Quick Settings, Favorites backup integration, File diff, App Shortcuts |
| **IV.2 TrustAll downgraded to audit** | Only found in lint-baseline (may be library transitive); needs investigation not code change |
| **Phase 1 → Security-first** | Security/compliance must ship before any Play Store update — this is the most impactful first sprint |
| **III.13 elevated** | Background audio exists but lacks visible queue management — incomplete feature is worse than no feature |
| **Execution order renumbered** | 5 completed items removed from phases, 33 → 28 remaining items |

---

*Generated from PLAN/IMPROVEMENT_PROPOSAL.md (March 23, 2026). Keep this file in sync when proposals are added or completed.*
