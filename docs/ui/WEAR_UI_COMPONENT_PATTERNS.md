# Wear UI Component Patterns and Unification Catalogue

Scope: The `wear` module, covering all Jetpack Compose for Wear OS screens, dialogs, tiles, and player surfaces across `wear/src/main/java/com/sza/fastmediasorter/wear/ui/`.

This document is an architectural standard, not an informal report. Every rule and contract specified below is mandatory for future changes touching the Wear OS user interface. All metrics and counts are reproducible from the codebase audit conducted during ticket S3232 (Phase 01 and Phase 02 inventories in `PLAN/S3232_research-wear-ui-component-patterns/research/`).

The phone module (`app_v2`) is out of scope and governed separately by `docs/ui/PHONE_UI_COMPONENT_PATTERNS.md`.

---

## 1. Design Tokens and Theme Hierarchy

### 1.1 Palette Architecture and Theme Inheritance

The Wear OS UI is built exclusively on declarative Jetpack Compose. Color and styling tokens resolve through a strict two-layer hierarchy:

```
WearAppTheme (WearColorScheme)
  ├── MaterialTheme.colors (androidx.wear.compose.material.Colors)
  └── WearAppTheme.colors (com.sza.fastmediasorter.wear.ui.theme.WearAppColors)
```

1. **`MaterialTheme.colors`:** Provides the standard 13 Wear Material color roles (`primary`, `primaryVariant`, `secondary`, `secondaryVariant`, `background`, `surface`, `error`, `onPrimary`, `onSecondary`, `onBackground`, `onSurface`, `onSurfaceVariant`, `onError`).
2. **`WearAppColors` (`WearAppTheme.colors`):** Provides project-specific functional state colors that must remain consistent across all color schemes:
   - `recording` (`0xFFFF4438`): Active microphone / voice recording indicator. Warmer and lighter than `colors.error` to avoid communicating a failure state (S2161 / ADR-1).
   - `toggleOn` (`0xFF4A90E2`): Dedicated blue tone for active toggle states (S2468 / ADR-1).
   - `toggleOff` (`0xFF8D6E63`): Dedicated brown tone for inactive toggle states (S2468).
   - `guideArrow` (`0xFFFFA000`): Amber guidance tone for visual walkthrough hints and game guidance arrows (S2494).
   - `heartRate` (`0xFFFF5252`): Heart-rate identity tone, shared by the tourist hero card label and the athlete card accent - scheme-independent (S3258).
   - `touristAccent` (`0xFF00BFA5`): Tourist dashboard metric-label accent, and the speed metric's identity tone on the athlete card - scheme-independent (S3258).
   - `steps` (`0xFFFFAB00`): Step-count identity tone on the athlete card - scheme-independent (S3258).
   - `distance` (`0xFF448AFF`): Trip-distance identity tone on the athlete card - scheme-independent (S3258).
   - `gpsFix` (`0xFF00E676`): Satellite count while a GNSS fix is held - scheme-independent (S3258).
   - `sunrise` (`0xFFFFFF00`): Sunrise caption on the tourist sun-time card - scheme-independent (S3258).
   - `sunset` (`0xFFFF8A80`): Sunset caption on the tourist sun-time card - scheme-independent (S3258).
   - `compassNorth` (`0xFFE53935`): North half of the compass needle - scheme-independent (S3258).
   - `compassSouth` (`0xFF9E9E9E`): South half of the compass needle - scheme-independent (S3258).
   - `unlockHint` (`0xFFFFCC00`): Tourist lock hint, which rides on the black athlete canvas - scheme-independent (S3258).
   - `canvasBlack` (`0xFF000000`): The deliberate true-black canvas of the athlete card and of the calculator, game and motion-monitor scaffolds - scheme-independent (S3258).
   - `athleteOnCanvas` (`0xFFFFFFFF`): Athlete card focal digits. Dedicated rather than `onSurface` because that canvas stays black under every scheme while `onSurface` follows the scheme (S3258).
   - `isLight` (`Boolean`): Flag indicating whether the active palette is light or dark (S2522).

### 1.2 Color Attributes vs Hardcoded Literals

- **Rule:** A Composable names colors strictly via `MaterialTheme.colors.*` or `WearAppTheme.colors.*`. Hardcoded `Color(0x...)` or `Color.White` / `Color.Gray` literals inside screen composables are prohibited.
- **Scrims and Overlays:** Transparent scrims must be defined as named tokens in `WearAppColors` or derived using `.copy(alpha = ...)` on semantic theme colors.
- **Backgrounds:** Content rendered over the dynamic particle canvas or wallpaper scrim must inherit from `WearAppBackground` rather than drawing opaque black rectangles unless in Ambient Mode.

### 1.3 Typography Scale (`MaterialTheme.typography`)

A Composable must never declare inline `fontSize = ...sp`. Text styling must resolve through `MaterialTheme.typography`:

| Typography Token | Role & Semantic Purpose | Canonical Sizing / Font Weight |
|---|---|---|
| `typography.display1` | The single focal reading of a screen drawn to be read at arm's length while moving (`TouristAthleteCard`) | 48sp, ExtraBold - **re-sized by the app**, see below |
| `typography.display2` | Large focal metric beside other content: hero telemetry, body-sensor reading, stopwatch lap, player overlay | 34sp, Medium |
| `typography.display3` | Secondary large reading (`BloodPressureEstimateContent`, `SyncResultScreen`) | 30sp, Medium |
| `typography.title1` | Top-level screen title on wide round displays | 24sp, Bold |
| `typography.title2` | Subtitle or category header | 20sp, Bold |
| `typography.title3` | Modal dialog titles and file action headers | 16sp, Medium |
| `typography.body1` | Primary text in information rows and descriptions | 16sp, Normal |
| `typography.body2` | Secondary body text and metric values | 14sp, Normal |
| `typography.button` | Primary label inside standard chips and action buttons | 15sp, Bold |
| `typography.caption1` | Chip titles and primary status labels | 14sp, Medium |
| `typography.caption2` | Subtitles in chips, toggle captions, and timestamp rows | 12sp, Normal |
| `typography.caption3` | Fine metadata, time stamps, and badge labels | 10sp, Normal |

**Where the scale itself lives.** Every token above is the Wear Material default except `display1`, which `wear/ui/theme/WearTypography.kt` re-sizes from 40sp to 48sp/52sp ExtraBold and `WearAppTheme` passes to `MaterialTheme`. That is the only sanctioned way to introduce a size the table has no name for: change the token, in that one file, with the reason written beside it - never the call site. `display2` and `display3` stay at their library values precisely because several screens already take them, so moving one to suit a single screen would re-size the others silently.

**The gate.** `wear-inline-font-size` (a dimension of `scripts/quality/assert-source-gates.ps1`, baseline `scripts/quality/wear-inline-font-size-baseline.txt`) refuses a new literal `NN.sp` anywhere in `wear/src/main`. It judges digits before `.sp`, so three constructions that apply `.sp` to a computed value remain legal and are the only exceptions: `WearCaptionText` stepping its own shrink scale, `ThumbnailCell` converting that scale's floor into dp, and `CalculatorHistoryPage` applying the owner's stored history size. `WearTypography.kt` is excluded by name, being the seam the rule routes callers towards.

### 1.4 Corner Shapes and Radii

Corner radii are standardized into three discrete role-based tokens:

- **`WearCellCorner` (`8.dp`):** Small items, list chips (`LongPressChip`), rectangular grid buttons (`RectangularButton`), and input fields.
- **`WearCardCorner` (`12.dp`):** Secondary cards (`TouristSecondaryCard`), selection grids, and action badges.
- **`WearHeroCorner` (`16.dp`):** Hero cards (`TouristHeroCard`), sensor summary cards, and primary media cards.

---

## 2. Standard Component Primitives

### 2.1 Standard Chips and Toggles

#### Standard List Chip (`StandardWearChip` & `StandardWearLongPressChip`)
Standard list rows must enforce a minimum height of `52.dp`, `shapes.small` (8dp corner radius), `ChipDefaults.ContentPadding` (14dp H, 6dp V), and standard icon layout (24dp icon with 6dp spacing):

```kotlin
@Composable
fun StandardWearChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    secondaryLabel: String? = null,
    icon: (@Composable BoxScope.() -> Unit)? = null,
    primary: Boolean = true,
    enabled: Boolean = true
) {
    Chip(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
        colors = if (primary) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors(),
        icon = icon?.let { iconSlot ->
            {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .wrapContentSize(Alignment.Center),
                    content = iconSlot
                )
            }
        },
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.caption1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        secondaryLabel = secondaryLabel?.let { sec ->
            {
                Text(
                    text = sec,
                    style = MaterialTheme.typography.caption2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    )
}
```

For list items requiring long-press action menus (file lists, voice notes), `LongPressChip` (`ui/common/LongPressChip.kt`) is mandatory to prevent touch event swallowing (S1953).

#### Standard Toggle Chip (`StandardWearToggleChip`)
The module's one toggle row, shipped in S3260 - it replaced the hand-rolled `WearSettingsToggleCell`, which is deleted. Standard `ToggleChip` architecture, project toggle state colors:

```kotlin
@Composable
fun StandardWearToggleChip(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    radio: Boolean = false,
    accessibilityLabel: String = label,
    narrow: Boolean = false
) {
    val toggleIcon = if (radio) {
        ToggleChipDefaults.radioIcon(checked)
    } else {
        ToggleChipDefaults.switchIcon(checked)
    }
    ToggleChip(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.caption1,
                maxLines = if (narrow) 2 else 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        toggleControl = {
            Icon(
                imageVector = toggleIcon,
                contentDescription = null,
                tint = if (checked) WearAppTheme.colors.toggleOn else WearAppTheme.colors.toggleOff,
                modifier = Modifier.size(24.dp)
            )
        },
        colors = ToggleChipDefaults.toggleChipColors()
    )
}
```

### 2.2 Cards and Containers

The module's one card container, shipped in S3261 (`ui/common/StandardWearCard.kt`) - it replaced the hand-rolled `Box + clip(RoundedCornerShape) + background` pseudo-cards of the tourist hero panel and both health last-reading panels, and parents `androidx.wear.compose.material.Card`. The radius tokens of section 1.4 live beside it as `WearCardShape` / `WearHeroShape`. Two panels stay outside it by owner ruling: `TouristSecondaryCard`, a compact tile that must not stretch inside the `FlowRow` cloud, and `TouristAthleteCard`, whose full-screen black canvas is the running-mode contrast:

```kotlin
@Composable
fun StandardWearCard(
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    title: (@Composable () -> Unit)? = null,
    timeOrBadge: (@Composable () -> Unit)? = null,
    cornerShape: Shape = WearCardShape,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        onClick = onClick ?: {},
        enabled = onClick != null,
        contentColor = MaterialTheme.colors.onSurface,
        modifier = modifier.fillMaxWidth(),
        shape = cornerShape,
        backgroundPainter = CardDefaults.cardBackgroundPainter(
            startBackgroundColor = MaterialTheme.colors.surface,
            endBackgroundColor = MaterialTheme.colors.surface
        ),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (title != null || timeOrBadge != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f, fill = false)) {
                        title?.invoke()
                    }
                    if (timeOrBadge != null) {
                        Box(modifier = Modifier.padding(start = 6.dp)) {
                            timeOrBadge()
                        }
                    }
                }
            }
            content()
        }
    }
}
```

### 2.3 Dialogs and Alerts

#### Standard Alert Dialog (`StandardWearAlertDialog`)
Full-screen alert adhering to Wear OS design guidelines with standardized confirm, cancel, and destructive button styling. Shipped by S3262 in `wear/ui/common/StandardWearAlertDialog.kt`; the contract below is the one in the tree:

```kotlin
@Composable
fun StandardWearAlertDialog(
    show: Boolean,
    title: String,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    confirmLabel: String = stringResource(android.R.string.ok),
    cancelLabel: String? = stringResource(android.R.string.cancel),
    isDestructive: Boolean = false,
    content: (@Composable ColumnScope.() -> Unit)? = null
)
```

- `isDestructive = true` colours the confirming chip `MaterialTheme.colors.error` on `onError`; every deletion in the module asks this way.
- `cancelLabel = null` is the acknowledgement form: one full-width action chip and no negative button. It is drawn by the chip overload of `Alert`, because the two-button overload cannot omit its negative slot and would push the remaining button off centre.
- `content` is the slot for progress indicators, result rows and extra action chips. With a cancel label the message is rendered as the first content row, since the two-button overload of `Alert` carries no `message` parameter.
- `icon` and a separate `onCancel` are deliberately not part of the contract: no call site needs either, and with them the parameter list crosses detekt's `LongParameterList` threshold.

### 2.4 Player Transport and Command Controls

- **Touch Targets:** Fixed `48.dp` minimum touch target on all clickable media transport buttons (`PlayerCommandButton.kt`).
- **Command Glyphs:** Primary playback actions use `32.dp` glyphs (`COMMAND_GLYPH_DP`). Secondary/back actions use `24.dp` glyphs.
- **Adaptive Column Geometry:**
  - `STORE` geometry mode: 3 primary buttons (`PRIMARY_ROW_COLUMNS = 3`) to ensure 48dp buttons fit within round screen chords.
  - `ORIGINAL` geometry mode: 4 buttons (`ORIGINAL_ROW_COLUMNS = 4`).
- **Progress Ring:** Wrapped around center transport buttons using `PlayerProgressRing` (2dp stroke `CircularProgressIndicator` matching parent bounds without inflating touch measurement).

---

## 3. Rotary Input and Focus Management Rules

### 3.1 The Focus Stack Architecture (`LocalWearRotaryFocusStack`)

Wear OS delivers rotary events only to the focused node. Multiple composables on screen (e.g. background list vs modal overlay) must coordinate through `WearRotaryFocusStack`:

1. Every rotary-sensitive composable registers its `FocusRequester` via `rememberRotaryFocus()`.
2. The newest entrant on the stack receives rotary crown focus.
3. When an overlay is dismissed, focus returns automatically to the underlying scrolling list.

### 3.2 Continuous vs Stepped Rotary Modifiers

- **Continuous Lists (`ScalingLazyColumn`):** Use `Modifier.rotaryActionScroll(listState)`. Automatically applied in `WearListColumn`.
- **Continuous Scroll Containers (`ScrollState`):** Use `Modifier.rotaryActionScroll(scrollState)`.
- **Stepped Detents (Volume, Pages, Scrubbing, Zoom):** Use `Modifier.rotaryActionSteps(onStep: (Int) -> Unit)`. `RotaryStepAccumulator` defaults to `stepPixels = 48f`, so a call site states what a step does and nothing else.
- **Haptic Feedback:** `rotaryActionSteps` performs `HapticFeedbackType.TextHandleMove` on every emitted step. A call site whose own action already vibrates passes `hapticFeedbackEnabled = false` rather than re-implementing the tick.
- **Non-scrolling Full-screen Surfaces (overlays, viewers, splash, safety screens):** Use `Modifier.rotaryActionSwallow()`. It claims the focus stack and consumes the turn, which is what stops the crown reaching the screen underneath; the old inline `focusRequester(..) + focusable() + onRotaryScrollEvent { true }` triple is not written by hand any more.
- **A list-backed screen is already standard:** `WearListColumn` and `WearStateBlock` apply `rotaryActionScroll` themselves. Adding a second rotary modifier over one of them pushes a competing focus-stack entry and kills the crown scrolling it was meant to fix.

---

## 4. Screen Geometry Adaptation and Insets

### 4.1 Round vs Square Displays

1. **Round Displays:**
   - Top and bottom chords are significantly narrower than the center diameter.
   - Fixed vertical and horizontal paddings must follow `ROUND_INSET_FRACTION = 0.10f` (10% of display dimension).
   - Inscribed square containers (`WearInscribedSquareScaffold`) must constrain non-scaling content to `0.70f` of diameter (S2008).
2. **Square Displays:**
   - No chord clipping. Insets use `SQUARE_INSET = 4.dp`.

### 4.2 Scaffold and Chrome Placement

- **`TimeText`:** Positioned along the top curved edge using `TimeTextDefaults` with `scrollAway` linkage to the list's `ScalingLazyListState`.
- **`PositionIndicator`:** Rendered on the right curved edge, automatically driven by `ScalingLazyListState` or `ScrollState`. Never omitted on scrollable screens.
- **`BatteryBar`:** Displayed under `WearScreenScaffold` with color tiers: White (>25%), Amber (10%-25%), Red (<10%).

---

## 5. Ambient Mode and Power Efficiency

Wear OS watches transition to low-power Ambient Mode (Always-On Display) when idle:

1. **True Black Background:** In ambient mode, backgrounds must be pure `Color.Black` (`#000000`) to disable OLED pixels and preserve battery.
2. **Visual Simplification:**
   - Disable particle canvas animations (`WaveParticleBackground`).
   - Stop progress tickers and continuous polling loops (`PlaybackProgressTicker`, `KeepScreenOnEffect`).
   - Render high-contrast monochrome text and outlines.
3. **No Interactive Traps:** Ambient mode must never intercept touch or rotary events intended to wake the screen.
