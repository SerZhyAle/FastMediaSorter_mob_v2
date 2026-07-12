---
name: fms-companion-subproject
description: Windows companion (S0421) lives OUT of the Android repo at P:\windows\fms_companion - Go+Wails toolchain, devserve harness, portable NSIS
metadata:
  type: project
---

The S0421 Windows SFTP companion is a separate Go project at `P:\windows\fms_companion` (module `github.com/sza/fastmediasorter-companion`), built 2026-07-10. NOT discoverable from the Android repo - no Gradle/gitignore/catalog trace.

**PIVOT (2026-07-10 22:21, owner):** the standalone product is CLOSED. All Level A functionality ports into **FastMediaSorter LITE** (`P:\WINDOWS\FastMediaSorter_Lite`, VB.NET/.NET Fx 4.8, Store+winget; rebranded 2026-07 to "Fast Media Sorter for Windows" - see [[fms-windows-rebrand]]). Handoff spec: `P:\WINDOWS\FastMediaSorter_Lite\SPECIFICATION_ANDROID_FOLDER_SHARE.md`; headless `fms-share-worker.exe` (cmd/worker, IPC incl. ExportConfig with QR PNG) vendored at `FastMediaSorter_Lite\payload\companion\`. Architecture: Go worker sidecar + VB UI over named pipe `\\.\pipe\fms-companion` - NO free production SFTP server exists for .NET Fx 4.8 (SSH.NET client-only, FxSsh net8-only, Rebex ~$499). S0421 sits in BlockExternal until the Lite port ships.

**Why (original split):** owner decision (spec-quiz 2026-07-10) - full decoupling from Android build/gates; superseded same day by the pivot above (don't multiply Windows products; Lite has brand/signing/channels).

**How to apply:**
- Toolchain: Go 1.26 + Node 24 (`C:\Program Files\Go\bin`, `C:\Program Files\nodejs` - NOT on Git Bash PATH, export manually), Wails CLI in `~/go/bin`. Verify with `wails doctor`.
- Build: `wails build` (exe) / `.\build.ps1 -Installer` (NSIS). Portable NSIS lives at `C:\Users\serzh\tools\nsis-3.11` (winget NSIS.NSIS silently fails unelevated); build.ps1 auto-prepends it.
- Android<->companion contract: `docs/CONFIG_FORMAT.md` (companion repo) + canonical vector frozen byte-identically in `app_v2/src/test/resources/companion/canonical_vector.json` - a schemaVersion bump on either side breaks the other.
- E2E testing without the UI: `go build ./cmd/devserve && ./devserve.exe <folder> <port> <out.fmscfg>` serves a real SFTP share and emits the import config; emulator reaches the host LAN IP directly (proven 2026-07-10: 434-file browse).
- Android import entry: Add resource -> SFTP/FTP -> "Import from companion" (`AddResourceCompanionCoordinator`).
- Wails v2 has NO native system tray (v3 feature) - companion ships a dashboard window instead; revisit on v3.
