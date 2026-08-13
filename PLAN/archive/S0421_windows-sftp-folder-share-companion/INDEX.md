# Tactical Plan: S0421 - windows-sftp-folder-share-companion

**Strategic spec:** [`../S0421_windows-sftp-folder-share-companion.md`](../S0421_windows-sftp-folder-share-companion.md)
**Research inputs:** none (strategic §6 items resolved inline via quiz decisions 2026-06-19 / 2026-06-30 / 2026-07-10)
**Feature:** Windows SFTP folder-share companion (Level A MVP)
**Tier:** 3 - Moderate (ad-hoc, cross-project: Go/Wails desktop subproject + Android import slice)
**Priority:** 50
**Status:** Done (pending user end-to-end test - ticket in BlockNeedUserTest)
**Phases:** 8 / 8 done
**Last updated:** 2026-07-10

> **Scope:** tactical, English, developer handoff. This plan covers **Level A only** (SFTP helper: pick folder -> run server -> LAN/mDNS + port-forward -> export config -> one-action Android import). Level B (connect-by-ID P2P, rendezvous S0530, PAKE, `pion/webrtc`) is a later spec phase and is out of scope here.
>
> **Cross-project note.** Phases 01-06 build a **Go + Wails desktop subproject** whose root is **`P:\windows\fms_companion`** - **outside this Android repository** (owner decision, spec-quiz 2026-07-10). Phase-file paths written as `desktop/windows-companion/...` denote that external root (i.e. `P:\windows\fms_companion\internal\...`); they are not paths inside this repo. The Android/Kotlin build gates (`a.ps1`, `BUILD.LOCK`, detekt, `catalog_sync`, `/build`) do NOT apply to those phases - their verification uses the Go/Wails toolchain (`go build`, `go vet`, `wails doctor`, `wails build`). Phase 07 (Android config import) is normal Kotlin under `app_v2/` in THIS repo and DOES use the standard toolchain. Phase 08 is cleanup.
>
> **Cross-repo sync (consequence of out-of-repo location).** The config contract lives twice: `CONFIG_FORMAT.md` + canonical vector in the companion repo (Phase 05), and the Kotlin parser + test fixture in this repo (Phase 07). Phase 07 step 07.5 must copy the frozen canonical vector into Android test resources; a `schemaVersion` bump on either side is a breaking change for the other.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | subproject-scaffolding | - | ✅ Done | 6/6 | [PHASE_01__subproject-scaffolding.md](PHASE_01__subproject-scaffolding.md) |
| 02 | sftp-server-core | 01 | ✅ Done | 6/6 | [PHASE_02__sftp-server-core.md](PHASE_02__sftp-server-core.md) |
| 03 | service-tray-ipc | 02 | ✅ Done | 6/6 | [PHASE_03__service-tray-ipc.md](PHASE_03__service-tray-ipc.md) |
| 04 | network-access | 02 | ✅ Done | 6/6 | [PHASE_04__network-access.md](PHASE_04__network-access.md) |
| 05 | config-export-contract | 03, 04 | ✅ Done | 5/5 | [PHASE_05__config-export-contract.md](PHASE_05__config-export-contract.md) |
| 06 | onboarding-and-installer | 05 | ✅ Done | 5/5 | [PHASE_06__onboarding-and-installer.md](PHASE_06__onboarding-and-installer.md) |
| 07 | android-config-import | 05 | ✅ Done | 6/6 | [PHASE_07__android-config-import.md](PHASE_07__android-config-import.md) |
| 08 | docs-catalog-cleanup | all | ✅ Done | 5/5 | [PHASE_08__docs-catalog-cleanup.md](PHASE_08__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

Phase 04 depends on 02 (needs the running server's listen port to map) and runs parallel to 03. Phase 05 fuses their outputs (service identity + reachable address) into the export contract; 06 (desktop onboarding) and 07 (Android import) both consume 05's config format and can run in parallel.

---

## Pre-Implementation Blockers

Phase 01 must not start while any blocker is unchecked.

- [x] **Toolchain (desktop) - RESOLVED 2026-07-10 20:31:** Go 1.26.4 + Node 24.16.0 + npm 11.13.0 installed by owner; Wails CLI v2.13.0 installed via `go install github.com/wailsapp/wails/v2/cmd/wails@latest`; WebView2 150.0.4078.65 present. `wails doctor` = SUCCESS ("Your system is ready for Wails development!"). Optional upx/nsis not installed (nsis needed only for Phase 06 installer).
- [x] **Owner decision - subproject location (resolved spec-quiz 2026-07-10):** companion root is **`P:\windows\fms_companion`**, outside this repo. Since it is out-of-repo, no `settings.gradle.kts` / catalog exclusion is needed (Phase 01 step 01.5 becomes: confirm the external root, no in-repo isolation required).
- [x] **Owner decision - client authentication (resolved spec-quiz 2026-07-10):** companion generates a per-install SFTP username + random high-entropy password, embedded in the exported config (never typed by hand). Maps onto the existing `NetworkCredentialsEntity` encrypted-password store with no new schema. Phase 02 step 02.2 and Phase 07 implement this; no keypair path.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] Capability recorded in `docs/ALL_FEATURES.jsonl` (`network-cloud.companion-sftp-import`). NOTE: original bullet demanded a direct `docs/FEATURES*.md` edit - superseded by CLAUDE.md §11 (showcase is `/skill-release`-owned, populated from the ALL_FEATURES diff at release); strategic §8 sentence stays available for that pass.
- [x] `dev/CHANGELOG.md` entries (post-change Phase 07 + close-and-log batch at closure; one entry per logical change per CLAUDE.md §12).
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (Phase 07 classes indexed; desktop Go code not catalogued).
- [ ] `/spec-check S0421` returns `Verified` - pending the user end-to-end walkthrough (desktop install + phone import + reboot survival); ticket parked in `BlockNeedUserTest` with the walkthrough note.
- [x] Strategic spec `Status:` = `BlockNeedUserTest` (allowed by this gate while desktop/device end-to-end verification is pending).

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, set journal status via `update.ps1` to the matching `Block*`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0421`.

---

## Blockers Log

- 2026-07-10 - Not started: desktop toolchain absent (Go/Wails/Node) - only remaining blocker. Subproject location (`P:\windows\fms_companion`, out-of-repo) and client-auth model (login + random password) resolved via spec-quiz 2026-07-10.
- 2026-07-10 20:31 - RESOLVED: toolchain installed (Go 1.26.4, Node 24.16, Wails 2.13.0, WebView2), `wails doctor` green. No open blockers; Phase 01 may start.

---

## Change Log

- 2026-07-10 - Initial tactical plan authored by `/spec-tech`. Level A only; Go core + Wails UI stack (quiz 2026-07-10).
