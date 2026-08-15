# S1056 - MKV & ISO playback support - Tactical INDEX

**Ticket:** S1056
**Strategic spec:** `PLAN/S1056_mkv-iso-playback-support.md`
**Research:** `research/01__external-feasibility-legal.md`, `research/02__codebase-architecture.md`
**Status:** Tactical

---

## Phase map

| Phase | Title | Flavor | Gate | State |
|-------|-------|--------|------|-------|
| P01 | Remote decoder parity (route network helpers through tuned renderers factory) | standard + noLegal (all) | none - Play-safe, no native build | **DONE** (BlockNeedUserTest, device-test) |
| P02 | Tier A - Play-safe SW video (AV1 dav1d + VP9 libvpx, bundled) | standard | external WSL native build + owner APK-size decision | **spun out -> S1059** (BlockExternal) |
| P03 | Tier B - alternate engine (libVLC) - any-codec MKV + DVD/BD ISO | noLegal only | external build + `/ui-clarify` | **spun out -> S1060** (Approved) |

## Execution order

1. **P01** - DONE: implemented in 4 network helpers, `standard debug` green, gates PASS; device-test deferred (BlockNeedUserTest).
2. **P02 -> S1059** - owner GO 2026-07-15 (максимум в standard). Blocked on external WSL/NDK build of dav1d/libvpx `.so`; then gradle-bundle + device-test.
3. **P03 -> S1060** - owner GO 2026-07-15 (остальное в noLegal). Dependency `org.videolan.android:libvlc-all:3.6.0` is on Maven (~82 MB, LGPL); large integration - next `/spec-tech` + `/ui-clarify`.

## Non-goals (from strategic §2)

- Animated WEBP (S1026).
- Blu-ray AACS / DVD CSS in `standard`.
- Full DVD navigation (menus/titles/angles) in first iteration.

## Delivered capability (on P01 Implemented)

Network video (SMB/FTP/SFTP/Cloud) uses the same decoder set as local files - MKV and other containers with FFmpeg-decodable audio tracks (DTS/APE/WMA/WavPack/TTA/DSD) no longer lose audio over the network.
