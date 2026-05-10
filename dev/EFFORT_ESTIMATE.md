# FastMediaSorter v2 — Effort Estimate

**Date**: May 10, 2026  
**Scope**: Full project (app_v2 + wear modules)  
**Profile**: Single middle-level Android developer

---

## Codebase Metrics (as of May 2026)

| Module | Files (.kt) | Lines of Code |
|---|---|---|
| `app_v2` (main app) | 1,130 | ~205,800 |
| `wear` (companion) | 70 | ~7,500 |
| **Total** | **1,200** | **~213,300** |

> Growth since March 2026: +127% files, +56% LOC (app_v2); +59% files, +45% LOC (wear).

---

## Complexity Drivers

**High-complexity subsystems:**
- Video player — 213+ .kt files in player subsystem + custom renderer + ExoPlayer/Media3 integration
- 92 domain use cases covering all features (up from 59)
- 3 network protocols: SMB (SMBJ), SFTP (SSHJ), FTP (Apache Commons Net)
- 3 cloud providers: Google Drive, OneDrive (MSAL), Dropbox SDK
- Custom Glide `NetworkFileModelLoader` pipeline for all remote protocols
- Room v6 DB with migrations
- 4 product flavors (`standard`, `lite`, `photos`, `legacy`) with `BuildConfig` feature gating
- Settings subsystem — 51 .kt files across 12+ fragments with helpers (up from 12 fragments)
- Wear OS companion with its own full MVVM/Clean Architecture stack (70 .kt files)

---

## Effort Estimate by Domain

| Domain | Hours |
|---|---|
| Architecture setup (MVVM + Clean + Hilt + Room + flavors) | 120–160 |
| Browse / file management UI + managers | 280–380 |
| Video player (ExoPlayer + 213+ files + custom renderer) | 700–1,000 |
| Audio player | 100–150 |
| Image viewer + slideshow | 160–210 |
| PDF / EPUB / Text viewers | 100–150 |
| OCR + translation pipeline | 160–240 |
| SMB integration (streaming + reconnect + pool) | 320–440 |
| SFTP integration | 210–290 |
| FTP integration | 130–180 |
| Google Drive integration | 200–260 |
| OneDrive integration (MSAL) | 200–260 |
| Dropbox integration | 130–180 |
| File transfer strategies (11+ strategies) | 200–280 |
| Glide custom loaders for all protocols | 100–150 |
| Settings (51 .kt files, 12+ fragments + helpers) | 180–260 |
| Widgets + background workers | 100–150 |
| Wear OS companion app (70 .kt files) | 290–400 |
| Build scripts, CI/CD, automation | 80–110 |
| Documentation (~40 .md files) | 80–110 |
| **Total** | **~4,040 – 5,760** |

---

## Final Estimate

> **~4,500 – 6,000 person-hours** for a middle developer (median: ~5,200h)

At a standard pace of **6 productive hours/day** (accounting for research, debugging, reviews):

| Calendar pace | Duration |
|---|---|
| Full-time (5 days/week) | ~3 – 4 years |
| Sprint mode (7 days/week) | ~2 – 2.5 years |

---

## Notes

- SMB/SFTP/FTP + cloud combinations each require significant protocol-level debugging — higher cost for middle vs. senior dev.
- The video player subsystem (now 213+ .kt files) is senior-level complexity; a middle dev will spend extra time here.
- Multi-flavor build config + per-flavor feature gating requires careful architecture upfront.
- Wear OS companion adds a full second MVVM stack and a separate UI paradigm (Compose for Wear).
- Estimates assume no pre-existing shared libraries or proprietary SDKs — everything built from scratch.
- Since March 2026 the codebase grew +56% in LOC and +127% in file count — the domain-level hour ranges have been scaled accordingly.
- Settings subsystem complexity increased significantly: 12 fragments grew to 51 .kt files, reflecting extensive helper/manager extraction.
- Use case count grew from 59 to 92 — domain logic coverage broadened substantially.
