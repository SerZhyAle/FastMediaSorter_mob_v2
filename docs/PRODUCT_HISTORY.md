# FastMediaSorter v2 - Product History

**Snapshot Date**: July 17, 2026
**Audience**: Developer / product owner
**Purpose**: A documented timeline of product lineage and major milestones, combining repository evidence with owner-supplied historical context where the current checkout is incomplete.

## 1. Evidence Boundary

This history is reconstructed from:

- `dev/CHANGELOG.md`
- current checked-in documentation
- the current Git history available in this checkout
- owner-provided product history context

Important limitation:

- The current checkout provides dense, structured engineering evidence from **March 2, 2026** onward.
- The visible Git history in this workspace begins later, on **June 22, 2026**.
- Earlier milestones do exist, but they must be reconstructed from secondary artifacts such as screenshot filenames, policy dates, public-facing docs, and owner recollection.
- In this document, milestones are treated as:
  - **Owner-provided** when they come from the project owner and are not yet directly provable from this checkout alone.
  - **Corroborated by repo artifacts** when local files support the timeframe indirectly.
  - **Directly evidenced** when the current repo contains explicit dated records.

## 2. High-Level Product Arc

FastMediaSorter as a whole evolved across three eras:

- a long Windows lineage,
- a first Android generation,
- and the current Android v2 rewrite / expansion.

The current Android v2 product has grown into a broad media-management platform with:

- local, network, and cloud file handling,
- integrated players and document viewers,
- OCR / translation features,
- widgets and automation,
- Wear OS support,
- VR / noLegal sideload variants,
- and, by July 2026, an in-progress launcher / desktop mode.

## 3. Lineage Before Android v2

| Period | Milestone | Confidence |
|:--|:--|:--|
| ~1999 | Very early Windows file sorter in the Visual Basic era. Owner recalls a Visual Basic 4-based first sorter around 1999. | Owner-provided |
| 2013 | **FastMediaSorter** appears as a Windows / VB.NET product. Owner dates this line to 2013 on `.VB NET 4.5`. | Owner-provided |
| 2025-05 (approx.) | First Android version appears. Owner states the first Android app existed around **May 2025** and was not published. | Owner-provided |
| 2025-09 (approx.) | Current Android **v2** development begins. Owner states this branch has been under development since **September 2025**. | Owner-provided |

## 4. Repo-Corroborated Early Android v2 Milestones

These do not prove the first day of v2, but they do prove that the app already existed and had substantial surface before the structured March 2026 process.

| Date / Period | Milestone | Evidence |
|:--|:--|:--|
| 2025-11-09 | Existing Android UI screenshots show the app was already running and visually documented by early November 2025. | `docs/images/Screenshot_20251109_*.png`, `docs/README*.md` |
| 2025-11-14 | Additional player screenshot indicates the player surface already existed by mid-November 2025. | `docs/images/Screenshot_20251114_184930.png`, `docs/README*.md` |
| 2025-11-30 | Privacy-policy file dates show public/legal documentation was already being maintained by late November 2025. | `docs/PRIVACY_POLICY.uk.md` |
| 2025-12 | Versioning examples in tooling reference December 2025 builds, which supports the claim that Android development and build discipline were already active in 2025. | `dev/build-with-version.ps1` |

## 5. Main Milestones in the Current Checkout

| Date | Milestone | Evidence |
|:--|:--|:--|
| 2026-03 | Owner states this month was a process reset: memory was cleared, the ticket system was introduced, and structured engineering workflow started. Releases and development had already existed before that point. | Owner-provided; consistent with the sudden appearance of structured changelog / spec / workflow artifacts in repo |
| 2026-03-02 | Earliest clearly documented v2 baseline in the current repo: technical requirements doc, release packaging work, changelog discipline | `dev/CHANGELOG.md` earliest entries |
| 2026-03-19 | Platform baseline broadened: standard minSdk lowered to Android 8 (API 26), legacy flavor covers API 23-25; toolchain/docs updated | `dev/CHANGELOG.md`, `dev/TECH_REQUIREMENTS.md` |
| 2026-03-19 | Standalone/default-player integration became a first-class product area | `dev/CHANGELOG.md` entries around standalone player and default-player flows |
| 2026-03-21 to 2026-03-23 | Product expanded into widgets, Camera Photos virtual resource, dynamic shortcuts, and stronger public-facing docs | `dev/CHANGELOG.md`, `docs/README.md` |
| 2026-03-27 to 2026-04-02 | Scheduled file operations entered the product, including DB support, dialogs, and release gating | `dev/CHANGELOG.md` |
| 2026-03-30 to 2026-04-01 | Duplicate finder / cleanup feature added with dedicated screen and workflows | `dev/CHANGELOG.md` |
| 2026-04-11 to 2026-04-14 | Document surface matured: EPUB / PDF / TXT search, translation, selection actions, and TTS parity | `dev/CHANGELOG.md` |
| 2026-04-14 | Wear OS work was formalized at roadmap level with a master plan and release tooling | `dev/CHANGELOG.md` |
| 2026-06-22 | VR stabilization and standalone-player parity work were actively landing; repository process matured with stronger prerelease and audit discipline | `dev/CHANGELOG.md` |
| 2026-06-27 | Google Play availability explicitly confirmed for version `v2.60.6270.802` | `.agents/MEMORY.md` |
| 2026-07-10 | Windows companion strategy pivoted: instead of a separate companion app, the functionality was redirected into the existing `FastMediaSorter_Lite` Windows product. | `PLAN/spec-catalog-archive.jsonl` (`S0421`) |
| 2026-07-17 | Launcher-mode / desktop-style surface is under active implementation; Room schema has reached version 41 | `dev/CHANGELOG.md`, `AppDatabase.kt` |

## 6. Development Phases

### Phase 0 - Product Lineage Before the Current Process

Before the current structured repo process, the product had already passed through:

- a Windows lineage beginning in the Visual Basic era,
- a VB.NET Windows FastMediaSorter line by 2013,
- a first Android attempt around May 2025,
- and the start of the Android v2 effort around September 2025.

The current repository does not fully preserve those earliest engineering records, but it does preserve indirect proof that Android v2 was already active by November 2025.

### Phase A - Process Reset and Scope Expansion (March 2026)

March 2026 appears to be the point where the project became formally structured: changelog discipline, specs, workflow documents, and more systematic planning. The product already had enough surface to justify technical requirements, release packaging automation, and public documentation maintenance. During March, the codebase broadened rapidly across:

- default-player behavior,
- widgets,
- camera shortcuts,
- scheduled operations,
- duplicate cleanup,
- and stronger document support.

### Phase B - Feature Maturation (April 2026)

April evidence shows the product moving beyond simple media sorting toward richer reading and automation workflows:

- EPUB / PDF / TXT parity work,
- translation and selection actions,
- TTS,
- and feature hardening across dialogs and settings.

### Phase C - Specialized Surfaces and Release Hardening (June 2026)

June evidence shows stabilization and specialization:

- VR-specific fixes,
- stream/player parity work,
- dialog/UI unification,
- and stronger prerelease/documentation process gates.

### Phase D - Product as Platform (July 2026)

By mid-July, the product is clearly operating as a platform-style Android app:

- launcher/desktop mode is being added,
- the database schema has grown to version 41,
- and the repo has formalized document-registry and phase-audit workflows.

## 7. Current Reading

As of July 17, 2026, the repository shows a product that has moved through these stages:

1. Windows file-sorting utility lineage,
2. Windows FastMediaSorter product line,
3. first Android generation,
4. Android v2 rewrite / expansion,
5. multi-surface Android product,
6. platform-like shell with launcher ambitions.

## 8. Working Historical Summary

The best current historical reading is:

- the product family starts in the Windows world, likely as early as **1999** in the Visual Basic era;
- the named **FastMediaSorter** Windows line dates to about **2013** in **VB.NET 4.5**;
- the first Android generation existed around **May 2025** and was not published;
- the current Android **v2** effort has been running since about **September 2025**;
- by **March 2026**, the project switched into the current ticketed / process-driven development model;
- by **June 27, 2026**, this Android v2 line was definitely live on Google Play;
- by **July 2026**, the app had become a large platform-style Android product with launcher ambitions.

For code-size and effort estimation, see `dev/PRODUCT_COMPLEXITY_ASSESSMENT.md`.
