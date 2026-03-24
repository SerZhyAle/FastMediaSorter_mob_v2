# FastMediaSorter v2 — Effort Estimate

**Date**: March 23, 2026  
**Scope**: Full project (app_v2 + wear modules)  
**Profile**: Single middle-level Android developer

---

## Codebase Metrics (as of March 2026)

| Module | Files (.kt) | Lines of Code |
|---|---|---|
| `app_v2` (main app) | 498 | ~131,500 |
| `wear` (companion) | 44 | ~5,200 |
| **Total** | **542** | **~136,700** |

---

## Complexity Drivers

**High-complexity subsystems:**
- Video player — 59 helper files + custom renderer + ExoPlayer/Media3 integration
- 59 domain use cases covering all features
- 3 network protocols: SMB (SMBJ), SFTP (SSHJ), FTP (Apache Commons Net)
- 3 cloud providers: Google Drive, OneDrive (MSAL), Dropbox SDK
- Custom Glide `NetworkFileModelLoader` pipeline for all remote protocols
- Room v6 DB with migrations
- 4 product flavors (`standard`, `lite`, `photos`, `legacy`) with `BuildConfig` feature gating
- Wear OS companion with its own full MVVM/Clean Architecture stack

---

## Effort Estimate by Domain

| Domain | Hours |
|---|---|
| Architecture setup (MVVM + Clean + Hilt + Room + flavors) | 120–160 |
| Browse / file management UI + managers | 200–280 |
| Video player (ExoPlayer + 59 helpers + renderer) | 500–700 |
| Audio player | 80–120 |
| Image viewer + slideshow | 120–160 |
| PDF / EPUB / Text viewers | 80–120 |
| OCR + translation pipeline | 120–180 |
| SMB integration (streaming + reconnect + pool) | 240–320 |
| SFTP integration | 160–220 |
| FTP integration | 100–150 |
| Google Drive integration | 160–200 |
| OneDrive integration (MSAL) | 160–200 |
| Dropbox integration | 100–150 |
| File transfer strategies (11+ strategies) | 160–220 |
| Glide custom loaders for all protocols | 80–120 |
| Settings (12 fragments + helpers) | 100–140 |
| Widgets + background workers | 80–120 |
| Wear OS companion app | 200–280 |
| Build scripts, CI/CD, automation | 60–80 |
| Documentation (~30 .md files) | 60–80 |
| **Total** | **~2,900 – 4,000** |

---

## Final Estimate

> **~3,500 – 4,500 person-hours** for a middle developer (median: ~4,000h)

At a standard pace of **6 productive hours/day** (accounting for research, debugging, reviews):

| Calendar pace | Duration |
|---|---|
| Full-time (5 days/week) | ~2.5 – 3.5 years |
| Sprint mode (7 days/week) | ~1.5 – 2 years |

---

## Notes

- SMB/SFTP/FTP + cloud combinations each require significant protocol-level debugging — higher cost for middle vs. senior dev.
- The video player subsystem is senior-level complexity; a middle dev will spend extra time here.
- Multi-flavor build config + per-flavor feature gating requires careful architecture upfront.
- Wear OS companion adds a full second MVVM stack and a separate UI paradigm (Compose for Wear).
- Estimates assume no pre-existing shared libraries or proprietary SDKs — everything built from scratch.
