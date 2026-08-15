# S0952 - Cross-host gesture parity audit and matrix

**Ticket:** S0952
**Status:** Archived
**Priority:** 45
**Date:** 2026-07-05
**Tier:** 3 - Moderate
**Source:** Split from S0951 (`/spec-draft`) 2026-07-05

> The "Part B" audit half of S0951. The confirmed PDF standalone bug is fixed under S0951; this ticket owns the broader parity question the owner also raised. Matrix delivered, all owner forks resolved; implementation of the CLEAN gaps delegated to child S0953.

## 0. Captured request

**Captured:** 2026-07-05

**Text:**

> все жесты у всех плееров должны быть одинаковые - всё проверить и починить

Split from S0951 during `/spec-all`: S0951 landed the confirmed, codebase-resolvable bug (standalone PDF vertical page-swipe missing). This request half is a cross-host parity audit that cannot proceed without owner decisions on scope and canonical behavior.

**Attachments:** none.

## 1. Problem

The project currently has several intentionally separate gesture subsystems per host and per media family, so "all gestures identical across all players" is not a point fix - it needs a defined target matrix before any change:

- in-app player PDF: vertical swipe = page nav, long press = text selection, single tap = link/zone.
- standalone document host: button + keyboard parity present; PDF touch parity landed in S0951; EPUB/other touch parity unconfirmed.
- EPUB: own swipe detector (chapter / navigation / font-size).
- text viewer: horizontal swipe = font size.
- video standalone: separate `StandaloneVideoTouchDelegate`.
- photo/video standalone: separate host + gesture model.
- legacy `StandalonePlayerActivity`: reachable-until-removal status affects whether it must mirror.

## 2. Owner-gated decisions (resolved 2026-07-05 via /spec-quiz)

1. Scope of "all players" - **Resolved**: all standalone hosts (document, photo/video, legacy) + the in-app player. Full `host x media-family x gesture` matrix, matching the literal request.
2. Canonical source of truth when hosts differ - **Resolved**: the in-app player is the reference; standalone hosts conform to it.
3. `StandalonePlayerActivity` (legacy) - **Resolved**: it is still live (1130 LOC, declared in manifest, 23 references), so it must mirror parity until it is fully removed. Not frozen.
4. Existing per-family differences (EPUB/TXT horizontal-swipe = font-size vs PDF vertical-swipe = page) - **Resolved**: preserved as deliberate per-family semantics. Unify only where two hosts of the *same* family diverge (a real parity bug), not across families.

### Quiz decisions (2026-07-05)

- Scope? → All standalone hosts + in-app player (full matrix; matches "все жесты у всех плееров").
- Canonical when hosts differ? → In-app player wins; standalone conforms.
- Legacy StandalonePlayerActivity mirror? → Yes, mirror until removed (still reachable + in manifest + 23 refs, not dead).
- Per-family differences? → Preserve as deliberate; only same-family cross-host divergence is a bug to unify.

## 3. Rough direction

- Produce a gesture matrix (host x media-family x gesture) as the first deliverable, not ad hoc fixes.
- Prefer extracting shared document-gesture wiring over cloning per host.
- Canonical = in-app player; standalone hosts (incl. legacy `StandalonePlayerActivity` until removed) conform to it within each family.
- Treat same-family cross-host divergence from the in-app reference as a bug; keep deliberate per-family gesture semantics.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0951 (Part A - standalone PDF page-swipe, fixed), S0949 (shared PDF zoom contract), S0953 (child - CLEAN PDF parity gaps, spawned by this audit), S0393 (standalone host split), S0920 (standalone UI parity).
- **Flavor scope:** the audit spans `src/main` gesture wiring shared by all flavors; no `BuildConfig` specifics. The two intentional divergences (video swipe vocabulary vs in-app 9-zone grid; audio controller-only) are product behavior decisions, not flavor-gated.

## 5. Gesture matrix + divergences (research delivered 2026-07-05)

First deliverable (the matrix the request asked for) is done. Canonical reference = in-app player.

### Wiring topology
- EPUB / TXT / Office gestures live INSIDE shared managers (`EpubViewerManager`/`EpubWebViewLifecycle`, `TextViewerManager`/`TextViewerGestureDetectors`, noLegal `OfficeDocumentViewerManager`). Every host that constructs them inherits byte-identical behavior - no cross-host divergence possible by construction. These are OUT of the bug set.
- PDF exposes only gesture-callback methods from `PdfViewerManager` (`handlePdfFling`/`handlePdfTap`/`handlePdfLongPress`, `stepPdfZoom`); each host must attach PhotoView listeners itself -> divergence surface.
- VIDEO/AUDIO: in-app reference is the 9-zone/3-zone tap grid (`TouchZoneGestureManager`); the swipe-based `VideoTouchDelegate` is present but permanently disabled in-app (`PlayerGestureSetupManager` L289 `if (false)`). Standalone/legacy hosts instead wire `StandaloneVideoTouchDelegate` (a live twin of that disabled delegate) -> a vocabulary with zero overlap with the reference.
- IMAGE/AUDIO: standalone/legacy hosts have NO custom gesture layer (silent gaps).

### Ranked same-family cross-host divergences
1. **VIDEO/AUDIO (High, OWNER-GATED -> Resolved 2026-07-05 as deliberate per-host difference, §6.1):** standalone/legacy use brightness/volume/seek-swipe + double-tap ±10s (`StandaloneVideoTouchDelegate`); in-app uses 9-zone tap navigation (`TouchZoneGestureManager`). The two vocabularies are mutually exclusive (both claim the same screen real estate) - unifying is a real UX decision, not wiring. Owner ruled this an intentional per-host difference, NOT a bug (no porting). Sites: `PhotoVideoStandaloneActivity.setupVideoControls` L1056-1073, `StandalonePlayerActivity` L900-919 vs `PlayerGestureSetupManager.setupPlayerViewTouchListener` L296-335.
2. **PDF tap/long-press (High, CLEAN):** `DocumentStandaloneActivity` never wires single-tap link-open (`handlePdfTap`) or long-press text-selection (`handlePdfLongPress`), though `PdfViewerManager` exposes both. In-app wires them in `PlayerGestureSetupManager.configurePhotoViewGestures` L381-467. Pure parity gap - no UX decision.
3. **PDF legacy (Medium, CLEAN):** legacy `StandalonePlayerActivity` has NO PDF gesture wiring at all (missed S0951 fling + S0949 zoom + tap/long-press); buttons only.
4. **IMAGE (Medium):** standalone/legacy image surfaces have no zone-nav / app-defined double-tap zoom / long-press zoom; fall back to raw PhotoView default. Porting the image branch of `TouchZoneGestureManager` is sizable.
5. **AUDIO (Low, OWNER-GATED -> Resolved 2026-07-05 as controller-only reference, §6.2):** `AudioStandaloneActivity` has no touch layer (controller always visible). Owner confirmed controller-only is the intended reference - a deliberate per-family difference, not a bug.

### Dead-weight found (separate cleanup)
- `VideoTouchDelegate` (in-app) is a byte-twin of `StandaloneVideoTouchDelegate` but permanently `if (false)`-gated - dead code. The Divergence-1 decision it was waiting on is now made (§6.1: deliberate difference), so it is unambiguously removable in the follow-up cleanup.

## 6. Owner-gated decisions (surfaced by the matrix, 2026-07-05 - RESOLVED via /spec-quiz)

The §2 quiz resolved scope + the "in-app is canonical" principle, but the matrix revealed that principle CONFLICTS with existing standalone UX for video, which the §2 quiz did not cover. All three forks are now resolved:

1. **VIDEO/AUDIO vocabulary - Resolved:** deliberate per-host difference. Standalone/legacy keep the brightness/volume/seek swipe vocabulary (`StandaloneVideoTouchDelegate`, the modern fullscreen-video standard); the in-app player keeps its 9-zone tap grid (`TouchZoneGestureManager`). Divergence 1 is reclassified as intentional, NOT a same-family parity bug - it is the video-family analogue of the per-family carve-out in §2 Q4. No porting either direction.
2. **AUDIO zones - Resolved:** controller-only is the intended reference. `AudioStandaloneActivity` keeps media controller + paging buttons with no touch-zone layer; audio has no visual frame that tap-nav would address. Deliberate per-family difference, Divergence 5 is not a bug.
3. **Split - Resolved:** split. A child ticket owns the CLEAN PDF parity gaps only (Divergence 2 DocumentStandalone tap/long-press, Divergence 3 legacy PDF fling); any residual video work stays separate. The clean gaps ship decoupled from the video decision.

Consequences for scope: Divergences 1 and 5 are now closed as intentional (no code, documentation only). Divergence 4 (IMAGE zone-nav parity) remains an open, sizable item to schedule separately. The dead `VideoTouchDelegate` (in-app `if (false)` twin) is now unambiguously removable - the Divergence-1 decision it was waiting on is made.

### Quiz decisions (2026-07-05)

- Video vocabulary reconciliation? → Deliberate per-host difference (standalone keeps swipe vocab, in-app keeps 9-zone grid; Divergence 1 not a bug).
- Audio touch zones? → Controller-only is the reference (no audio touch layer; Divergence 5 not a bug).
- Split model? → Split: a child ticket for the CLEAN PDF gaps (Divergence 2/3) only; video work separate.

## 4. Related

- S0951 - the confirmed PDF standalone page-swipe bug (Part A), fixed separately.
- S0393 - standalone host split and parity harvesting from the legacy standalone activity.
- S0920 - recent standalone player UI parity work.
- S0949 - document horizontal swipe zoom (adjacent gesture-contract draft; PDF zoom now shared via `PdfViewerManager.stepPdfZoom`).
- S0953 - child ticket implementing the CLEAN PDF parity gaps (Divergence 2 + 3), spawned by this audit.

## Last Audit

**Date:** 2026-07-05
**Mode:** strategic (audit ticket - deliverable is the matrix + decisions, not code)
**Outcome:** Verified

This ticket's contract was: (1) produce the host x media-family x gesture matrix, (2) resolve the owner UX forks the matrix surfaced, (3) split the actionable CLEAN gaps into their own ticket. All three are done:

- Matrix + wiring topology + 5 ranked divergences delivered (§5).
- All owner forks resolved via /spec-quiz (§6): video vocabulary and audio are deliberate per-host/per-family differences (no code); split model chosen.
- CLEAN parity gaps delegated to child S0953 (Divergence 2 DocumentStandalone tap/long-press + Divergence 3 legacy PDF fling), which owns the implementation.

Residual (tracked, not this ticket's code):
- Divergence 4 (IMAGE zone-nav parity) - sizable, schedule separately.
- `VideoTouchDelegate` dead-code removal - now unblocked by the §6.1 decision; fold into a cleanup pass.

No code was authored under S0952 itself; all implementation lives in S0953. No debug tags belong to this id.
