# S0392 - Divergence Matrix: player family vs in-app etalon

**Phase A deliverable.** Source: two read-only research passes (in-app etalon inventory + standalone-hosts inventory), 2026-06-10.

## Legend

Status per host cell:
- ✅ present, real implementation
- 🟡 partial / stub / present-but-inert (button or hook exists but does nothing, or only part works)
- ❌ absent
- ≈ present but behaves differently
- — N/A for this host's media lane

Hosts (columns): **IA** = in-app `PlayerActivity` (etalon) · **PV** = `PhotoVideoStandaloneActivity` · **AU** = `AudioStandaloneActivity` · **DOC** = `DocumentStandaloneActivity` · **TXT** = `TextStandaloneActivity` · **LSA** = legacy `StandalonePlayerActivity` (retained fallback, not in external routing).

Blocker class (why a gap exists):
- **B1** helper binding-coupled (typed to `PlayerActivity` / `ActivityPlayerUnifiedBinding` / in-app `PlayerViewModel.state`)
- **B2** needs resource/playlist context standalone lacks (single external file)
- **B3** trimmed standalone layout (view/stub absent)
- **B4** flavor/type gate (`SUPPORT_*`/`ENABLE_*`)
- **B5** intentional non-goal (contract decision)

Coupling of the etalon implementation (from research): **BC** binding-coupled · **GEN** generic/reusable as-is · **MIXED** generic engine + BC wiring. This says how cheap protraction is once a host-seam exists.

---

## 1. Core file actions

| Capability | IA | PV | AU | DOC | TXT | LSA | Coupling | Blocker |
|---|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|
| Delete (+ undo window) | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | MIXED | - |
| Favorite | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | BC→own | - |
| Share | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | MIXED | - |
| Rename | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | BC→own | - |
| Rename keeps playback alive | ✅ | — | ✅ | — | — | ✅ | - | - |
| File info | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | BC→own | - |
| Send to Telegram | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | MIXED | B1 |
| Open in FastMediaSorter | — | ✅ | ✅ | ✅ | ✅ | ✅ | - | - |
| UNDO file operation | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | BC | B2 |
| Copy / Move side panels | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | BC | B2 |

## 2. Navigation & playlist

| Capability | IA | PV | AU | DOC | TXT | LSA | Coupling | Blocker |
|---|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|
| Folder paging prev/next/random (S0389) | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ | BC | B2 (LSA predates S0389) |
| Slideshow auto-advance | ✅ | 🟡 | 🟡 | ❌ | ❌ | ❌ | BC | B2 |
| Resource/library list navigation | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | BC | B2 |
| Open in separate window (tear-off) | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | BC | B2 |

## 3. Command-panel system & overlays

| Capability | IA | PV | AU | DOC | TXT | LSA | Coupling | Blocker |
|---|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|
| `PlayerHostCapabilities` implemented | ✅ | ✅ | ✅ | ❌ | ❌ | ✅ | - | B1 (DOC/TXT manager-only) |
| Bar↔overflow priority planner | ✅ | ≈ | ≈ | ≈ | ≈ | ≈ | GEN(`planLayout`) | B1 (standalone static; S0389 scroll+pin) |
| Command-panel folding / fullscreen | ✅ | ≈ | ≈ | ≈ | ≈ | ≈ | BC | B1 (own `StandaloneFullscreenManager`) |
| Picture-in-Picture | ✅ | ✅ | ❌ | ❌ | ❌ | ✅ | MIXED | PV wired (S0393 U1) |
| Black-screen overlay | ✅ | ❌ | ❌ | — | — | ❌ | GEN | B1-cheap |
| Stereo / VR stereo modes | ✅ | ✅ | ✅ | ❌ | ❌ | ✅ | GEN | B1 (DOC/TXT no capabilities) |
| Open in VR | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | BC | B4 (vr/noLegal only) |

## 4. Image type-specific actions

| Capability | IA | PV | Coupling | Blocker |
|---|:--:|:--:|:--:|:--:|
| Crop (in-place) | ✅ | ✅ (S0390) | BC→seam | - |
| Crop to file | ✅ | ✅ (S0390) | BC→seam | - |
| Compress copy | ✅ | ✅ (S0390) | BC→seam | - |
| Screen-rotation toggle | ✅ | ✅ (S0390) | BC→own | - |
| Draw overlay (annotate) | ✅ | ❌ | BC | B1/B3 (deferred → S0390 ph06) |
| Image edit dialog (rotate/flip/filters/adjust) | ✅ | ❌ | MIXED | B1 |
| OCR image | ✅ | ❌ | BC | B1 |
| Google Lens image | ✅ | ❌ | MIXED | B1 |
| Image translation overlay (ML Kit) | ✅ | ❌ | BC | B1/B4 |
| Print image | ✅ | ❌ | MIXED | B1 |

## 5. Video type-specific actions

| Capability | IA | PV | AU | LSA | Coupling | Blocker |
|---|:--:|:--:|:--:|:--:|:--:|:--:|
| Video playback + transport controls | ✅ | ✅ | — | ✅ | - | - |
| Brightness/volume/seek gestures | ✅ | ✅ | — | ✅ | GEN | - |
| Playback-control dialog (speed/track/colour) | ✅ | ✅ | — | ✅ | BC | PV wired (S0393 U2); AU deferred |
| Save current frame | ✅ | ❌ | — | 🟡 | BC | B1 |
| Sleep timer | ✅ | ❌ | ❌ | 🟡 | MIXED | B1 |
| Cast / Chromecast | ✅ | ❌ | ❌ | ❌ | MIXED | B5 (`supportsCast=false`) |
| Black-screen (video) | ✅ | ❌ | ❌ | ❌ | GEN | B1-cheap |

## 6. Audio type-specific actions

| Capability | IA | AU | LSA | Coupling | Blocker |
|---|:--:|:--:|:--:|:--:|:--:|
| Audio playback | ✅ | ✅ | ✅ | - | - |
| Persistent background audio service | ✅ | ❌ | ❌ | BC | B2/B4 (`supportsPersistentAudio=false`) |
| Lyrics search & overlay | ✅ | ❌ | ❌ | MIXED | B1 |
| YouTube Music search | ✅ | ❌ | ❌ | MIXED | B1 |
| Sleep timer | ✅ | ❌ | 🟡 | MIXED | B1 |
| Black-screen (audio) | ✅ | ❌ | ❌ | GEN | B1-cheap |
| Keyboard / D-pad navigation | ✅ | ✅ | ✅ | GEN(parser) | AU wired (S0393 U4/U5, shared StandaloneKeyboardManager) |

## 7. Document type-specific actions (PDF / EPUB / Office / Text)

| Capability | IA | DOC | TXT | LSA | Coupling | Blocker |
|---|:--:|:--:|:--:|:--:|:--:|:--:|
| PDF page nav + zoom | ✅ | ✅ | — | ✅ | MIXED | - |
| PDF search | ✅ | ✅ | — | ✅ | MIXED | - |
| PDF translate | ✅ | ✅ | — | ✅ | BC | - |
| PDF OCR | ✅ | 🟡 | — | 🟡 | BC | B1 (`displayOcrText` inert) |
| PDF Google Lens | ✅ | 🟡 | — | ❌ | BC | B1 (`shareFileToGoogleLens` inert) |
| PDF scroll-mode / colour-mode / thumbnails | ✅ | 🟡 | — | 🟡 | MIXED | B1/B3 |
| EPUB chapter/TOC/font/reader-settings | ✅ | ✅ | — | ✅ | MIXED | - |
| EPUB cross-chapter search | ✅ | ✅ | — | ✅ | MIXED | - |
| EPUB translate | ✅ | ✅ | — | ✅ | BC | - |
| EPUB OCR | ✅ | 🟡 | — | 🟡 | BC | B1 (`btnOcrEpubCmd` no listener) |
| Office viewer | ✅ | ✅ | — | ✅ | MIXED | - |
| Office translate / OCR | ✅ | 🟡 | — | 🟡 | BC | B1 |
| Text view | ✅ | — | ✅ | ✅ | MIXED | - |
| Text search | ✅ | — | 🟡 | ✅ | MIXED | B1 (TXT `btnSearchTextCmd` no listener) |
| Text copy | ✅ | — | 🟡 | ✅ | BC | B1 |
| Text edit | ✅ | — | 🟡 | 🟡 | BC | B1 (TXT `isWritable=false`) |
| Text translate | ✅ | — | 🟡 | 🟡 | BC | B1 (inert) |
| Reopen with encoding | ✅ | — | 🟡 | — | MIXED | B1 (`showEncodingDialog` inert) |
| Toggle markdown render | ✅ | — | 🟡 | — | MIXED | B1 |
| Reader settings | ✅ | — | 🟡 | — | MIXED | B1 |
| Read aloud (TTS) | ✅ | ❌ | ❌ | ❌ | BC | B1 |
| WebView selection ActionMode (translate/search) | ✅ | ✅ | ❌ | ✅ | BC | DOC wired (S0393 U3) |

## 8. Input

| Capability | IA | PV | AU | DOC | TXT | LSA | Coupling | Blocker |
|---|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|
| Keyboard / D-pad / TV navigation | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | GEN(parser) | wired on all hosts (S0393 U4/U5) |
| Gestures (brightness/volume/seek/zoom/swipe) | ✅ | ✅(video) | ❌ | 🟡 | ❌ | ✅ | GEN core | B1 |
| Mouse-wheel navigation | ✅ | 🟡 | ❌ | ❌ | ❌ | ✅ | GEN | B1 |

---

## 9. Divergences WITHIN the standalone family

These are gaps between siblings, independent of the etalon - inconsistencies a user feels when opening different file types externally:

1. **`PlayerHostCapabilities` not implemented** by DOC + TXT (implemented by PV/AU/LSA) → the shared capability-driven dialog/coordinator pipeline cannot bind to Document/Text at all.
2. **Keyboard/D-pad** present only on PV + LSA; absent on AU/DOC/TXT → TV/keyboard users cannot navigate audio/document/text external opens. Text-scroll keys work in LSA but not in the specialized TXT host.
3. **Playback-control dialog** (speed/track/colour) real in LSA, stubbed in PV, absent in AU.
4. **PiP** wired only in LSA; PV declares `supportsPictureInPicture` in the manifest but never wires it (declared-but-dead).
5. **Folder paging (S0389)** in PV/AU/DOC/TXT but not LSA (LSA predates it - inverse gap).
6. **Group A image edit (S0390)** only in PV (correct, but lone host with `editableImageFile` gating).
7. **Overflow reachability**: only PV pins `btnOverflowMenu` outside a `HorizontalScrollView`; DOC keeps overflow inside the scroll; AU/TXT have no scroll wrapper → overflow clips inconsistently on narrow widths.
8. **`updateAudioMediaItem`** (rename-during-playback continuity) wired only in AU + LSA.
9. **WebView selection ActionMode** (Translate / Search-in-Google) only in LSA; DOC selection yields the bare system menu.
10. **Translation model-download prompt** inert in DOC + TXT → first-use translation on a missing model silently no-ops.
11. **Present-but-inert command buttons** shipped in DOC + TXT layouts (Lens/OCR/copy/edit/text-translate) with no host-side listeners → dead ids vs the etalon (dead-weight, Rule 20).

---

## 10. Blocker tally (what unblocks the most)

- **B1 (binding-coupled helper)** dominates the per-file actions: draw, image-edit-dialog, OCR, Lens, image-translation, print, save-frame, sleep-timer, lyrics, YT-Music, read-aloud, text actions, playback-control dialog. A single binding-agnostic **host-seam** unblocks this whole class at once. This is the highest-leverage fundamental.
- **B2 (no resource/playlist context)** is structural: list-nav, copy/move panels, undo, separate-window, persistent audio. Mostly intentional single-file semantics; partial relief already shipped via S0389 folder paging.
- **B3 (trimmed layout)** is local per host (add stub/view).
- **B4 (flavor/type gate)** already handled by `SUPPORT_*`/`ENABLE_*`.
- **B5 (non-goal)** cast - explicit contract decision.

## 11. Resolved research items (strategic §6)

- §6.1 etalon capability list → §1-§8 above + research reports.
- §6.2 capability × host matrix → §1-§8.
- §6.3 blocker/cost classification → blocker column + §10.
- §6.4 host-seam design → see `ROADMAP.md` (fundamental).
- §6.5 per-host non-goals → cast (B5), list-context actions (B2): list-nav/copy-move/undo/separate-window/persistent-audio.

---

## Update after S0393 (2026-06-10)

The host-seam foundation + legacy harvest (S0393) moved these rows toward present:

- §3 PiP: **PV 🟡→✅** (PictureInPictureManager made binding-agnostic + wired).
- §5 Playback-control dialog: **PV 🟡→✅** (un-stubbed via the host's PlayerHostCapabilities/VideoPlayerHandle).
- §8 Keyboard / D-pad: **AU/DOC/TXT ❌→✅** (shared StandaloneKeyboardManager + per-host routes).
- §7 WebView selection ActionMode: **DOC ❌→✅** (startActionMode override aggregating the active viewer callback).
- §7 EPUB translate button: **DOC 🟡→✅** (was a dead/gone button; now orientation-guarded + reachable).
- §4 Crop family: now served by ONE seam delegate (`PlayerCropDelegate`) across in-app + PV (StandaloneImageEditController deleted) - no behaviour change, just de-duplicated.

Legacy `StandalonePlayerActivity` is now `@Deprecated` (nothing routes to it). Still open: draw-overlay migration (seam now exposes the bitmap/rect/mount it needs), Document/Text full `PlayerHostCapabilities`, OCR/Lens/print/translate-image/save-frame/lyrics waves (consume the seam next), on-device verification.
