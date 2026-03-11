# FastMediaSorter v2 — Improvement Roadmap

**Date**: March 11, 2026  
**Source**: `dev/IMPROVEMENT_PROPOSAL.md`  
**Ordering**: Easy & non-risky → Difficult & mind-blowing

---

## TIER 1 — Quick Wins (1-2h, zero risk)

| # | Item | Description | Why easy |
|---|------|-------------|----------|
| IX.4 | WorkManager backoff | Add `BackoffPolicy.EXPONENTIAL` to 4 workers | Config change only |
| VIII.1 | Text min 12sp | Set minimum 12sp for all text elements | Update 3-4 values in `dimens.xml` |
| II.6 | Touch Zones "?" button | Add help button in player toolbar | Single toolbar icon + help dialog |
| V.2 | Remove test credentials | Move `sza_resources.xml` to debug sourceSet | Gradle sourceSet config only |

---

## TIER 2 — Easy (2-4h, low risk)

| # | Item | Description | Why easy |
|---|------|-------------|----------|
| X.3 | App Shortcuts | Long-press shortcuts for recent resources | Static XML + `ShortcutManager` — well-documented API |
| VIII.3 | Wear OS localization | Add `values-ru/` and `values-uk/` to wear | Copy + translate ~45 strings |
| X.4 | Stale cache indicator | Show "Cached: {time}" badge when offline | Add `lastFetchedAt` field + badge in toolbar |
| IX.3 | Metadata cache TTL | Auto-cleanup old metadata entries | Add timestamp column + cleanup query in `OrphanCleanupWorker` |
| I.4 | Update screenshots | Refresh store_assets/ and docs/images/ | Manual capture task, no code risk |
| III.2 | Network delete confirm | Enhanced confirmation for network deletions | UI-only dialog change |

---

## TIER 3 — Moderate (4-8h, medium risk)

| # | Item | Description | Risk factor |
|---|------|-------------|-------------|
| III.4 | Backup favorites | Include favorites in settings backup/restore | Room query + JSON; must not break existing backup |
| V.1 | WebView EPUB sandbox | CSP headers, disable JS for untrusted content | Risk of breaking scroll tracking in EPUB reader |
| IV.2 | TrustAll audit | Eliminate `TrustAllX509TrustManager` from release | May break self-signed SFTP setups |
| I.3 | String sync (~200 keys) | Synchronize RU/UK translations | Volume of translations; subtle phrasing errors |
| X.10 | Crashlytics integration | Activate Firebase Crashlytics + Performance | Firebase config + proguard; touches release pipeline |
| VIII.2 | TalkBack testing | Fix accessibility issues, add screen-reader audit | Requires manual audit + fixes across multiple screens |
| II.4 | Screen transition animations | Add animations between Activity transitions | Must not break backstack or orientation changes |

---

## TIER 4 — Substantial (8-16h, notable risk)

| # | Item | Description | Risk factor |
|---|------|-------------|-------------|
| III.6 | Wear OS export/import | Send resource config to watch via Data Layer | Wearable Data Layer API; cross-device sync |
| III.7 | Batch rename | Mass-rename files with templates/patterns | File system ops at scale; undo complexity |
| IX.1 | Unified Result type | Single `AppResult<T>` replacing 5+ result types | Touches every UseCase/Repository; migration risk |
| IV.8-9 | CI/CD + Coverage | GitHub Actions pipeline + Jacoco coverage | Infrastructure setup; secrets management |
| VIII.4 | Landscape dialogs | Landscape-adaptive layouts for 25+ dialogs | Large regression surface |
| X.2 | Cast / Screen Mirror | Chromecast slideshow output | Google Cast SDK; receiver app needed |
| III.3 | Favorites sync | Cross-device export/import of favorites | Conflict resolution logic |

---

## TIER 5 — Complex (16-50h, high risk)

| # | Item | Description | Why hard |
|---|------|-------------|---------|
| III.5 | RAW formats | CR2/NEF/ARW/DNG preview support | Native decoders / LibRaw; device-specific failures |
| X.1 | Duplicate detection | Find duplicate files across resources | Hashing large files across network; performance |
| II.3 | Tablet / large screen | Two-pane layouts for Browse + Player | Full regression across key screens |
| IV.1 | Refactor giant files | Decompose 14 files exceeding 1000 LOC (25k+ LOC total) | Touches core flows; BrowseViewModel 3.3x over limit |

---

## TIER 6 — Mind-Blowing (architectural shifts)

| # | Item | Description | Why monumental |
|---|------|-------------|----------------|
| II.1 | Navigation Component | Replace all `Intent`/`startActivity` navigation | Migrate 11 Activities to nav graph |
| II.2 | Compose adoption | Migrate XML layouts to Jetpack Compose | 137 XML layouts to replace incrementally |
| III.8 | Tags / labels system | User-defined color tags for files | New Room entity + UI filtering + cross-resource model |
| III.10 | Drag-and-drop | File operations via drag-and-drop | Platform DnD API + two-pane prerequisite |
| X.7 | Built-in DLNA server | Share local folders over network | Entirely new subsystem; protocol complexity |
| X.9 | Auto-sort rules | Rule engine for automatic file sorting | Rule builder + scheduler + file watchers |
| X.8 | Voice commands | Hands-free player control | SpeechRecognizer + intent mapping for custom actions |

---

## Additional Items (slot into Tiers 2-5 depending on scope)

| # | Item | Estimated Tier |
|---|------|---------------|
| X.5 | HEIF/HEIC support | 2-3 |
| V.3 | FTP deprecation warning | 2 |
| X.6 | Batch EXIF edit | 4-5 |
| X.11 | Background thumbnail preload | 4 |
| VIII.5 | RTL audit | 3 |
| IX.5 | DB migration rollback plan | 3 |
| III.9 | Usage statistics | 4 |
| IX.2 | IntegrationTestRunner to debug | 3 |

---

## Recommended Execution Order

1. **Sprint 1**: Sweep Tier 1 + Tier 2 — maximum credibility gain, near-zero risk
2. **Sprint 2**: Tier 3 security items (IV.2, V.1, V.2) + Crashlytics (X.10)
3. **Sprint 3**: Tier 3 remaining (favorites backup, strings, TalkBack)
4. **Sprint 4+**: Tier 4 items by priority (CI/CD, batch rename, Wear OS sync)
5. **Ongoing**: Tier 5 items in parallel with feature work
6. **Strategic**: Tier 6 items as dedicated initiatives with design phase

---

*Generated from IMPROVEMENT_PROPOSAL.md. Keep this file in sync when proposals are added or completed.*
