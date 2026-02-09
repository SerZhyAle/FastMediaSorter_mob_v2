# PlayerActivity Touch Zone Definitions

## Map Names and Configurations

### REG-9100

9 touch zones, taking 100% of screen width and height, divided proportionally (30%-40%-30% both horizontal and vertical).

**Tap Zones:**

- **Left-Top:** Return to Browse screen (to file list)
- **Top:** Dialog "Copy to destination"
- **Right-Top:** Dialog "Rename to"
- **Left-Middle:** Previous file
- **Middle (Center):** Dialog "Move to destination"
- **Right-Middle:** Next file
- **Left-Bottom:** Switch to Command Panel mode
- **Middle-Bottom:** Dialog "Delete"
- **Right-Bottom:** Start/Stop slideshow mode

**Gestures (Swipes/Drags):**

- **Swipe Left:** Next file
- **Swipe Right:** Previous file
- **Swipe Bottom:** Return to Browse screen (to file list)
- **Swipe Top:** Switch to Command Panel mode
- _Note: Need to recognize sweeping (dragging) prior to tap touching._

---

### REG-975

9 touch zones, taking 100% of screen width but only **75% of screen height**. The bottom 25% is reserved for player controls. Width is divided proportionally (30%-40%-30% both horizontal and vertical)

**Tap Zones:**

- **Left-Top:** Return to Browse screen (to file list)
- **Top:** Dialog "Copy to destination"
- **Right-Top:** Dialog "Rename to"
- **Left-Middle:** Previous file
- **Middle (Center):** Dialog "Move to destination"
- **Right-Middle:** Next file
- **Left-Bottom:** Switch to Command Panel mode
- **Middle-Bottom:** Dialog "Delete"
- **Right-Bottom:** Start/Stop slideshow mode

**Gestures (Swipes/Drags):**

- **Swipe Left:** Next file
- **Swipe Right:** Previous file
- **Swipe Bottom:** Return to Browse screen (to file list)
- **Swipe Top:** Switch to Command Panel mode
- _Note: Need to recognize sweeping (dragging) prior to tap touching._

---

### REG-DOC

No tap zones (no reaction to single-finger taps).

**Gestures:**

- **Swipe Left:** Next file
- **Swipe Right:** Previous file
- **Swipe Bottom:** One page up
- **Swipe Top:** One page down
- **Two Fingers:** Zoom in/out

**UI Elements:**

- **Fullscreen Mode:** In the top-right corner, a small "X" button is required to switch to Command Panel mode.

---

### REG-3100

3 vertical touch zones, taking 100% of picture box width and height.

- **Left:** 25% width
- **Middle:** 50% width
- **Right:** 25% width

**Tap Zones:**

- **Left:** Previous file
- **Middle:** Gesture area (Zoom in/out, Rotate, Move zoomed picture). Double tap to Zoom In x2 / Zoom Out x2.
- **Right:** Next file

**Gestures (Swipes/Drags outside middle zone):**

- **Swipe Left:** Next file
- **Swipe Right:** Previous file
- **Swipe Bottom:** Zoom out x2
- **Swipe Top:** Zoom in x2
- _Note: Need to recognize sweeping (dragging) prior to tap touching._
- _Note: **Ignore sweeping (dragging) if it starts in the middle zone** (reserved for pan/zoom gestures)._

---

### REG-375

3 vertical touch zones, taking 100% of player width and **75% of player height** (bottom 25% reserved for controls).

- **Left:** 25% width
- **Middle:** 50% width
- **Right:** 25% width

**Tap Zones:**

- **Left:** Previous file
- **Middle:** Pause/Resume
- **Right:** Next file

**Gestures (Swipes/Drags):**

- **Swipe Left:** Next file
- **Swipe Right:** Previous file
- **Swipe Bottom:** Go to beginning of file
- **Swipe Top:** Go to end of file
- _Note: Need to recognize sweeping (dragging) prior to tap touching._

---

## Usage Mapping

### Fullscreen Mode

- **IMAGE:** `REG-9100`
- **GIF (Animation):** `REG-9100`
- **VIDEO:** `REG-975`, player command panel is visible and takes 25% bottom of height. It is autohide after 10 seconds and appears agaion once user toch the bottom 25% area.
- **AUDIO:** `REG-975`, player command panel is always visible and takes 25% bottom of height
- **PDF:** `REG-DOC`, document command panel is always visible in the bottom
- **EPUB:** `REG-DOC`, document command panel is always visible in the bottom
- **TXT:** `REG-DOC`

### Command Panel Mode

- **IMAGE:** `REG-3100`
- **GIF (Animation):** `REG-3100`
- **VIDEO:** `REG-375`, player command panel is visible and takes 25% bottom of height. It is autohide after 10 seconds and appears agaion once user toch the bottom 25% area.
- **AUDIO:** `REG-375`, player command panel is always visible and takes 25% bottom of height
- **PDF:** `REG-DOC`, document command panel is always visible in the bottom
- **EPUB:** `REG-DOC`, document command panel is always visible in the bottom
- **TXT:** `REG-DOC`

If user rotate the device and orientation is changed - zones must follow the changes.

---

## Implementation Plan

> **Status: ✅ COMPLETED** 
>
> All 6 phases need to be check:
>
> - Phase 1: `feat(player): Add TouchZoneConfig with zone map system`
> - Phase 2: `feat(player): Add vertical swipe gestures with zone-specific actions`
> - Phase 3: `feat(player): Update 9-zone grid actions (CommandPanel/Slideshow)`
> - Phase 4: `feat(player): Implement REG-3100 and REG-375 zone layouts`
> - Phase 5: `feat(player): Implement REG-DOC mode for documents with exit button`
> - Phase 6: `chore(player): Reduce touch zone logging verbosity`

### Phase 1: Core Zone Configuration System

**Goal:** Create enum-based zone map system with clear configuration classes.

**Files to create/modify:**

- `ui/player/helpers/TouchZoneConfig.kt` - New file with zone map enums and configuration
- `TouchZoneDetector.kt` - Refactor to use configurations (consider moving to helpers)

**Deliverables:**

- `enum class TouchZoneMap { REG_9100, REG_975, REG_3100, REG_375, REG_DOC }`
- `enum class TouchZoneAction` - All possible actions
- Zone boundary calculation per configuration (supporting 30-40-30 and 25-50-25 splits)
- Media type → Zone map resolver function

---

### Phase 2: Swipe Gesture Implementation

**Goal:** Add vertical swipe detection and all swipe actions.

**Files to modify:**

- `TouchZoneGestureManager.kt` - Add vertical swipe handling

**Deliverables:**

- Detect horizontal swipes (existing: Next/Previous)
- Detect vertical swipes (new: Back/CommandPanel, Zoom, Seek)
- Swipe actions vary by zone map (REG-9100 vs REG-375 vs REG-DOC)
- Ignore swipes starting in middle zone for REG-3100

---

### Phase 3: 9-Zone Actions Update (REG-9100, REG-975)

**Goal:** Update 9-zone tap actions to match spec.

**Files to modify:**

- `TouchZoneGestureManager.kt` - Update handleTouchZone()
- `TouchZoneCallback` interface - Add new callbacks

**Deliverables:**

- Left-Bottom: Switch to Command Panel (was Rotate Left)
- Right-Bottom: Toggle Slideshow (was Rotate Right)
- Center swipes work correctly

---

### Phase 4: 3-Zone Implementation (REG-3100, REG-375)

**Goal:** Implement command panel mode zones for images and video.

**Files to modify:**

- `TouchZoneGestureManager.kt` - handleCommandPanelTouchZones()
- `PlayerGestureSetupManager.kt` - Zone boundary updates

**Deliverables:**

- REG-3100: 25%|50%|25% for images (Left=Prev, Middle=PhotoView, Right=Next)
- REG-375: 25%|50%|25% for video (Left=Prev, Middle=Pause, Right=Next)
- Vertical swipes: Zoom for images, Seek for video

---

### Phase 5: Document Mode (REG-DOC)

**Goal:** Implement PDF/EPUB/TXT touch handling.

**Files to modify:**

- Document-related managers (PDF/EPUB/Text)
- Add X button for fullscreen exit

**Deliverables:**

- No tap zones (taps do nothing)
- Horizontal swipes: Next/Previous file
- Vertical swipes: Page up/down
- Two-finger zoom (already exists via WebView/PhotoView)
- X button in top-right corner in fullscreen mode

---

### Phase 6: Testing & Polish

**Goal:** Verify all combinations work correctly.

**Test Matrix:**
| MediaType | Fullscreen | Command Panel |
|-----------|------------|---------------|
| IMAGE | REG-9100 | REG-3100 |
| GIF | REG-9100 | REG-3100 |
| VIDEO | REG-975 | REG-375 |
| AUDIO | REG-975 | REG-375 |
| PDF | REG-DOC | REG-DOC |
| EPUB | REG-DOC | REG-DOC |
| TXT | REG-DOC | REG-DOC |

**Deliverables:**

- Orientation change handling
- Remove deprecated rotate actions
- Clean up logging
- Build verification

---

## Developer Prompts

### Prompt 1: Phase 1 - Core Zone Configuration System

```
TASK: Create TouchZoneConfig.kt with zone map system and 30-40-30 layout

CONTEXT:
- Read dev/TOUCH_ZONES.md for full specification
- Current TouchZoneDetector.kt has hardcoded zones
- Need flexible system for 5 different zone maps with 30-40-30 distribution

CREATE FILE: app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TouchZoneConfig.kt

REQUIREMENTS:
1. Create enum TouchZoneMap with values: REG_9100, REG_975, REG_3100, REG_375, REG_DOC
2. Create enum TouchZoneAction with all actions: BACK, COPY, RENAME, PREVIOUS, MOVE, NEXT, COMMAND_PANEL, DELETE, SLIDESHOW, PAUSE_RESUME, NONE
3. Create data class ZoneConfiguration with:
   - horizontalZones: Int (3 or 9)
   - verticalZones: Int (3 or 9)
   - heightPercent: Float (0.75 or 1.0)
   - widthRatios: List<Float> (e.g., [0.30, 0.40, 0.30] or [0.25, 0.50, 0.25])
   - heightRatios: List<Float>
4. Create function getZoneMapForMediaType(mediaType: MediaType, isFullscreen: Boolean): TouchZoneMap
5. Create function getZoneConfiguration(map: TouchZoneMap): ZoneConfiguration
   - REG_9100/975 must use 30%|40%|30% width and height ratios
   - REG_3100/375 use 25%|50%|25% width ratios

MODIFY FILE: TouchZoneDetector.kt (Move to helpers if needed or import Config from helpers)
- Add function detectZone(x, y, screenWidth, screenHeight, zoneMap: TouchZoneMap): TouchZoneAction
- Ensure detectZone calculates dynamic boundaries based on screenWidth/Height (handling rotation)
- Keep old detectZone() for backward compatibility during transition

BUILD & TEST:
.\dev\build-with-version.ps1
Verify no compilation errors

COMMIT: "feat(player): Add TouchZoneConfig with 30-40-30 zone map system"
```

---

### Prompt 2: Phase 2 - Swipe Gesture Implementation

```
TASK: Implement vertical swipe detection and zone-specific swipe actions

CONTEXT:
- Read dev/TOUCH_ZONES.md for swipe specifications per zone map
- Current code only handles horizontal swipes in onFling()
- Need vertical swipes with different actions per zone map

MODIFY FILE: ui/player/helpers/TouchZoneGestureManager.kt

REQUIREMENTS:
1. In onFling(), add vertical swipe detection:
   - If absVelocityY > absVelocityX → vertical swipe
   - velocityY > 0 → Swipe Down (finger moved down)
   - velocityY < 0 → Swipe Up (finger moved up)

2. Create enum SwipeDirection { LEFT, RIGHT, UP, DOWN }

3. Create function getSwipeAction(direction: SwipeDirection, zoneMap: TouchZoneMap): TouchZoneAction
   - REG-9100/REG-975: LEFT=Next, RIGHT=Prev, DOWN=Back, UP=CommandPanel
   - REG-3100: LEFT=Next, RIGHT=Prev, DOWN=ZoomOut, UP=ZoomIn (ignore if starts in middle)
   - REG-375: LEFT=Next, RIGHT=Prev, DOWN=SeekStart, UP=SeekEnd
   - REG-DOC: LEFT=Next, RIGHT=Prev, DOWN=PageUp, UP=PageDown

4. For REG-3100: Track swipe start position, ignore if started in middle 50%

5. Add callbacks to TouchZoneCallback:
   - onSwitchToCommandPanel()
   - onZoomIn() / onZoomOut()
   - onSeekToStart() / onSeekToEnd()
   - onPageUp() / onPageDown()

BUILD & TEST:
.\dev\build-with-version.ps1
Test swipes manually on device/emulator

COMMIT: "feat(player): Add vertical swipe gestures with zone-specific actions"
```

---

### Prompt 3: Phase 3 - 9-Zone Actions Update

```
TASK: Update 9-zone tap actions to match specification

CONTEXT:
- Read dev/TOUCH_ZONES.md for REG-9100 and REG-975 tap zones
- Current Left-Bottom = RotateLeft, Right-Bottom = RotateRight
- Spec says Left-Bottom = CommandPanel, Right-Bottom = Slideshow

MODIFY FILE: TouchZoneGestureManager.kt

REQUIREMENTS:
1. In handleTouchZone(), update zone actions:
   - TouchZone.ROTATE_LEFT case → call onSwitchToCommandPanel() instead
   - TouchZone.ROTATE_RIGHT case → call onToggleSlideshow() instead

2. Rename enum values in TouchZone (or add new ones):
   - ROTATE_LEFT → COMMAND_PANEL
   - ROTATE_RIGHT → SLIDESHOW

3. Update TouchZoneDetector.kt to use new action names

4. Update callback.onRotateLeft() calls to callback.onSwitchToCommandPanel()
   Update callback.onRotateRight() calls to callback.onToggleSlideshow()

5. Remove unused rotate callbacks from TouchZoneCallback if not needed elsewhere

6. Update any UI overlay labels if they show "Rotate" for these zones

BUILD & TEST:
.\dev\build-with-version.ps1
Test: Tap left-bottom should open command panel
Test: Tap right-bottom should toggle slideshow

COMMIT: "fix(player): Update 9-zone actions - LeftBottom=CommandPanel, RightBottom=Slideshow"
```

---

### Prompt 4: Phase 4 - 3-Zone Implementation

```
TASK: Implement REG-3100 and REG-375 zone layouts for command panel mode

CONTEXT:
- Read dev/TOUCH_ZONES.md for REG-3100 (images) and REG-375 (video)
- Both use 25%|50%|25% horizontal split
- REG-375 uses 75% height (bottom 25% for player controls)

MODIFY FILE: TouchZoneGestureManager.kt

REQUIREMENTS:
1. Update handleCommandPanelTouchZones(x, y) to use proper zone map:
   - Detect if IMAGE/GIF → use REG-3100 logic
   - Detect if VIDEO/AUDIO → use REG-375 logic

2. REG-3100 (Images in command panel):
   - Left 25%: Previous file
   - Middle 50%: Do nothing (PhotoView handles zoom/pan)
   - Right 25%: Next file
   - Double-tap in middle: Toggle 2x zoom (already works via PhotoView)

3. REG-375 (Video in command panel):
   - Height: Only top 75% (bottom 25% for ExoPlayer controls)
   - Left 25%: Previous file
   - Middle 50%: Pause/Resume playback
   - Right 25%: Next file

4. Add callback.onPauseResume() for video middle tap

5. Ensure vertical swipes work per zone map:
   - REG-3100: Swipe up/down → Zoom in/out (ignore if in middle zone)
   - REG-375: Swipe up/down → Seek to end/start

BUILD & TEST:
.\dev\build-with-version.ps1
Test image in command panel: left=prev, right=next, middle=no action
Test video in command panel: left=prev, right=next, middle=pause

COMMIT: "feat(player): Implement REG-3100 and REG-375 zone layouts"
```

---

### Prompt 5: Phase 5 - Document Mode (REG-DOC)

```
TASK: Implement REG-DOC touch handling for PDF/EPUB/TXT

CONTEXT:
- Read dev/TOUCH_ZONES.md for REG-DOC specification
- No tap zones - taps should do nothing
- Swipes: horizontal=Next/Prev file, vertical=Page up/down
- Need X button in fullscreen mode to exit

MODIFY FILES:
- TouchZoneGestureManager.kt
- PlayerActivity.kt or relevant layout XML

REQUIREMENTS:
1. For PDF/EPUB/TXT in handleTouchZone():
   - Return early, do not process taps
   - Or route to REG-DOC handler that does nothing on tap

2. Swipe handling for REG-DOC:
   - Swipe Left: Next file
   - Swipe Right: Previous file
   - Swipe Up (finger up): Page down (scroll content down)
   - Swipe Down (finger down): Page up (scroll content up)

3. Two-finger zoom: Already handled by WebView (PDF/EPUB) or PhotoView

4. Add X button in top-right corner for fullscreen mode:
   - Only visible when mediaType is PDF/EPUB/TXT AND isFullscreen=true
   - Clicking X calls onSwitchToCommandPanel()
   - Small size (e.g., 48dp), semi-transparent background
   - Position: top-right with margin

5. Update activity_player_unified.xml if needed for X button

BUILD & TEST:
.\dev\build-with-version.ps1
Test PDF in fullscreen: taps do nothing, swipes work, X button visible
Test EPUB: same behavior
Test TXT: same behavior

COMMIT: "feat(player): Implement REG-DOC mode for documents with exit button"
```

---

### Prompt 6: Phase 6 - Testing & Cleanup

```
TASK: Final testing, cleanup, and verification

CONTEXT:
- All zone maps implemented
- Need to verify all combinations work
- Clean up deprecated code and excessive logging

REQUIREMENTS:
1. Test all combinations (see test matrix in TOUCH_ZONES.md)

2. Orientation change test:
   - Rotate device while viewing each media type
   - Verify zones recalculate correctly
   - No crashes on rotation

3. Remove deprecated code:
   - Old ROTATE_LEFT/ROTATE_RIGHT enum values if fully replaced
   - Unused callbacks (onRotateLeft, onRotateRight if not used)
   - Dead code paths

4. Clean up logging:
   - Keep Timber.d() for debug builds
   - Remove or reduce Timber.w() flood in zone detection
   - Keep important action logs

5. Update any help/overlay text that mentions old zone actions

6. Final build and smoke test:
   .\dev\build-with-version.ps1
   Install on device, test each media type in both modes

COMMIT: "chore(player): Clean up touch zone implementation, remove deprecated code"
```

---

## Notes for Developer

1. **Build after each phase** - Don't accumulate changes without verification
2. **Commit atomically** - Each phase = one commit with clear message
3. **If compilation fails** - Fix before moving to next phase
4. **If behavior differs from spec** - Discuss before proceeding
5. **Backward compatibility** - Not required; we're replacing existing behavior
6. **Settings** - No user settings needed; zone maps are hardcoded per media type + mode
