# Settings IA Model — S0119

**Source:** Strategic spec S0119, Phase 02 execution, 2026-05-08
**Purpose:** Canonical placement model. Developer reference for new settings placement decisions and future migration specs.

---

## Surface Hierarchy

The settings surface is organized into six placement levels. Each level has a distinct entity type, navigation depth, and activation contract.

### Level 1 — Top-level Tab

- **Navigation depth:** 0 (directly visible in `SettingsActivity` ViewPager)
- **Entity types allowed:** category groupings of preferences and capability-enable items that belong to one logical domain (appearance, browse behavior, media types, playback, operations)
- **Input activation:** touch, D-pad (tab strip is focusable), keyboard (Ctrl+Tab / arrow key navigation via `SettingsKeyboardNavigationManager`)
- **Decision rule:** Use this level when the domain has ≥ 5 distinct preference items and users expect to find them as a named category

### Level 2 — Collapsible Section within Tab

- **Navigation depth:** 1 (expand/collapse within a tab's scroll area)
- **Entity types allowed:** sub-domain groupings of preferences within a tab; helper affordances (tooltip buttons) are at this level
- **Input activation:** touch, D-pad (header is focusable), keyboard (Enter/Space activates focused header)
- **Decision rule:** Use this level when items share a sub-domain name and the section can meaningfully be collapsed when not in use; do not use sections to hide the entity-type mixing problem

### Level 3 — Dedicated Management Screen

- **Navigation depth:** 2 (back-stack Fragment or separate Activity)
- **Entity types allowed:** management-surface items (list editors, credential managers, license viewers, keybinding editors); service-action flows requiring multi-step interaction
- **Input activation:** touch, D-pad, keyboard — must be fully navigable without touch
- **Decision rule:** Use this level when the interaction has ≥ 3 items in a list, supports add/edit/delete, or requires a dedicated back-stack context; do not flatten a management surface into a toggle row

### Level 4 — Contextual Control on Feature Screen

- **Navigation depth:** outside Settings (lives in Browse, Player, or per-feature UI)
- **Entity types allowed:** per-session options, per-operation parameters, feature-specific one-time selections that apply only in the context of using that feature
- **Input activation:** determined by the host screen
- **Decision rule:** Use this level when the user needs the option only while performing a specific action, not as a persistent application-wide preference; prefer this over adding a global setting

### Level 5 — Onboarding / Permission-request Flow

- **Navigation depth:** outside Settings; triggered on first-use or when feature requires access
- **Entity types allowed:** OS permission requests, initial setup steps
- **Input activation:** OS-standard dialogs
- **Decision rule:** Use this level for OS permission requests (`READ_MEDIA_*`, `RECORD_AUDIO`, `POST_NOTIFICATIONS`); never replicate OS permission dialogs as custom settings controls

### Level 6 — System Redirect

- **Navigation depth:** outside app (opens OS Settings)
- **Entity types allowed:** OS-managed settings (battery optimization, manage-all-files permission, default app assignment)
- **Input activation:** touch (Intent-based, system handles navigation)
- **Decision rule:** Use this level when the setting is managed entirely by Android and the app can only redirect; never recreate OS settings management inside the app

---

## Entity Type Classification

| Type | Definition | Examples |
|---|---|---|
| `preference` | Persistent user choice that changes application behavior globally | language, sort mode, cache size, slideshow interval |
| `capability-enable` | Turns on a media type or system capability; may trigger OS permission request | support images, support audio, persistent audio playback, primary media player |
| `service-action` | One-shot action: destructive, maintenance, or data operation | clear cache, export settings, reset section, sync now, backup |
| `management-surface` | Opens a list-based editor for a complex resource | destinations list, keybindings remap, saved authorizations, permissions management |
| `permission-redirect` | Routes to OS permission dialog or OS Settings | local files, media management, notifications |
| `debug` | Expert / developer control; hidden in production builds | integration tests, import test credentials |
| `informational-link` | External URL or app screen, not user-configurable | user guide, privacy policy, open-source licenses |
| `helper-affordance` | Tooltip / contextual help for an adjacent control | iconHelp*, help buttons in section headers |

---

## Placement Checklist

Answer in order. First positive match defines the recommended placement level.

1. **Is this a one-shot destructive or maintenance action** (clear, reset, export, sync now, backup)?
   → Level 3 (Dedicated Management Screen) **or** a clearly-labeled service-action section within Level 2 that is visually separated from preferences. **Never mix service-actions with toggle rows without a visual divider.**

2. **Does this require a list of ≥ 3 user-created items with add/edit/delete** (destinations, keybindings, auth sessions, scheduled operations)?
   → Level 3 (Dedicated Management Screen). Do not inline a list in a Level 2 section.

3. **Is this an OS permission or a redirect to OS Settings**?
   → Level 5 (permission flow) or Level 6 (system redirect). Not a preference row in Level 1/2.

4. **Is this only relevant while performing a specific feature** (per-operation parameter, per-session choice, not a permanent app preference)?
   → Level 4 (Contextual Control). Do not create a global setting for a contextual option.

5. **Does this have a flavor dependency** (only relevant in `standard`, `lite`, `photos`, or `legacy`)? If yes — gate at leaf level (hide the control when the build flag is false). Do not create a separate tab or section per flavor.

6. **Does this affect a single media type capability** (enable/disable a media category) and does the app have a Media tab?
   → Level 2 within the Media tab, under the relevant media-type collapsible section.

7. **Is this a persistent global preference** (affects app behavior across all sessions, all media types)?
   → Level 1 / Level 2 within the most relevant top-level tab (see Tab Ownership below).

7. **Is this an expert / debug / developer-only control**?
   → Level 2 within a dedicated `debug` section, hidden in production builds.

8. **Is this a link to documentation, legal text, or external help**?
   → Level 2 within an `About` or informational section, clearly not user-configurable. Do not mix with preferences.

### Tab Ownership (for Level 1/2 placement)

| Preference domain | Canonical tab |
|---|---|
| Language, UI density, appearance, font | General |
| Browse behavior, file visibility, subfolder display | General |
| Network parallelism, default credentials, background sync | General |
| Cache, storage, streaming cache | General |
| Import / export / reset / backup | General (service-action sub-section) |
| Permissions, app integrations | General (management-surface entry point) |
| Media type capabilities (images, video, audio, docs, other) | Media |
| Sort mode, grid mode, icon size | Playback |
| Slideshow, player UI, touch zones, player behavior | Playback |
| Default player assignment, input source (camera, link) | Playback |
| Destructive operation controls (allow delete, allow rename) | Operations |
| Safety & confirmation (safe mode, confirm delete/move, trash) | Operations |
| Destinations list, copy/move operations | Operations |
| Scheduled operations | Operations |

---

## Flavor Gating Contract

The four product flavors differ in which media type tabs and leaf-settings are present.

| Flavor | Top-level tabs present | Leaf-setting differences |
|---|---|---|
| `standard` | General, Media (all 5 sub-sections), Playback, Operations | All features including Cloud backup, Scheduled ops, EPUB |
| `lite` | General, Media (Images, Video only), Playback, Operations | No Audio, Docs, Other sub-sections; no Cloud backup; no Scheduled ops |
| `photos` | General, Media (Images, Other only), Playback, Operations | No Video, Audio, Docs sub-sections |
| `legacy` | General, Media (Images, Video, Audio, Other), Playback, Operations | No Docs, no Cloud; ANIM support |

**Gate mechanism:** `BuildConfig.SUPPORT_IMAGES`, `BuildConfig.SUPPORT_VIDEO`, `BuildConfig.SUPPORT_AUDIO`, `BuildConfig.SUPPORT_DOCUMENTS` control leaf-section visibility. `BuildConfig.ENABLE_SCHEDULED_OPERATIONS` gates the scheduled section.

**Invariant:** The four top-level tabs (General / Media / Playback / Operations) are present in all flavors. Flavors may hide sections or leaf items, but must not rename or reorder the top-level tabs. A user who moves between flavor builds must find the same mental model.

**Flavor divergence rule:**
- Leaf-level gating (hide a switch when feature is not compiled): **allowed**.
- Section-level gating (hide an entire collapsible section when no items are visible): **allowed** (e.g., hide Audio section in `lite`).
- Tab renaming or reordering per flavor: **prohibited**.
- Creating a different tab structure per flavor: **prohibited**.

---

## Multi-Input Surface Contract

Settings is a multi-input surface. All elements must be activatable via touch, mouse, keyboard, and D-pad.

### Per-element type requirements

| Element type | Touch | Mouse | Keyboard | D-pad |
|---|---|---|---|---|
| Toggle (Switch) | tap | click | Enter/Space when focused | D-pad center key |
| Dropdown (AutoCompleteTextView) | tap to expand | click | Enter to expand, arrow to navigate | D-pad center to expand |
| Text input (EditText) | tap to focus + IME | click to focus + IME | Tab to focus, type, Enter/Tab to commit | D-pad center to focus |
| Button | tap | click | Enter/Space when focused | D-pad center key |
| Row (management-surface entry) | tap | click | Enter/Space when focused | D-pad center key |
| Collapsible section header | tap | click | Enter/Space when focused | D-pad center key |
| Tab strip | tap tab | click tab | Ctrl+Tab / Ctrl+Shift+Tab or arrow keys (via `SettingsKeyboardNavigationManager`) | D-pad left/right on tab strip |
| Search overlay open | touch search icon | click | Ctrl+F (via `SettingsKeyboardNavigationManager`) | — |
| Search overlay close | touch outside / back | click outside | Escape | Back button |

**Anchor:** `SettingsKeyboardNavigationManager` is the single coordination point for all keyboard shortcuts within `SettingsActivity`. Any restructuring that changes tab count, search overlay behavior, or adds new surface types must update this class.

**Focus order rule:** Focus order within a tab must follow visual top-to-bottom order. Newly added controls must be inserted into the focus chain at the logically correct position (not appended after all existing items).

**Non-touch activation parity rule:** Any element that was directly activatable without touch before a reorganization must remain directly activatable without touch after.

---

## Responsive Contract

Settings layouts adapt to screen width without changing the logical grouping of controls.

### Window modes

| Mode | Width range | Layout rules |
|---|---|---|
| Narrow portrait | < 420dp | Single-column only. All controls full-width. No side-by-side pairs. Scrollable vertically. |
| Standard | 420dp – 840dp | Single-column. Pairs of related controls (e.g., sync enable + sync interval) may be placed side-by-side only if both items remain ≥ 120dp wide. |
| Wide / tablet-like | > 840dp | Max content width: 720dp (centered). Controls do not stretch to fill full width. Related controls grouped in proximity-constrained regions (no pair may be > 400dp apart horizontally). Management-surface rows may use two-column card grid. |

### Grouping proximity rule

Controls that share a dependency (parent switch enables sub-controls) must remain within 2 visual rows of each other in all modes. A reorganization that visually separates a parent toggle from its child controls in any mode is a layout regression.

### Orientation parity rule

`res/layout/*.xml` and `res/layout-land/*.xml` must be consistent in structure. Any implementation phase that modifies a portrait layout must apply the equivalent change to the landscape counterpart if it exists, in the same commit.

---

## Theme Parity Contract

All design decisions for settings UI must be verified in both light and dark theme.

### Minimum verification set

- Group section headers: foreground contrast ≥ 4.5:1 (WCAG AA) against the section background in both themes.
- Collapsed section headers: distinguishable from expanded state in both themes (not just a color change — use icon or indentation too).
- Helper buttons (`iconHelp*`): visible and recognizable in both themes.
- Search overlay background: sufficient contrast against the tab content beneath in both themes.
- Selected / focused states for switches, rows, and buttons: visible in both themes.
- Disabled states (flavor-gated or capability-gated controls): visually distinct but not invisible in both themes.

### Implementation rule

A visual change to any settings element is not complete until the developer has manually verified the element in both light and dark theme on a real device or emulator with forced theme toggle.

---

## Multilingual Search Contract

Settings search is a multilingual discoverability layer, not an English-only shortcut.

### Corpus structure

Each searchable settings item has:
- `key`: stable canonical identifier (e.g., `general.language`)
- `title`: display string (current locale)
- `keywords`: English alias list (legacy, retained as fallback)
- `localizedKeywords`: `Map<String, List<String>>` keyed by BCP-47 tag — EN/RU/UK locale sets — **added by Phase 04**

**Implementation anchor:** `SettingsSearchRegistry` is the Kotlin object that owns the `entries` list and `search()` function. All contract changes are implemented there.

### Search behavior

- Matching: substring (partial-word) in `title` OR any item in `keywords` OR any item in any `localizedKeywords` value list.
- Active UI locale does NOT restrict which alias set is searched — all three locale sets are always searched.
- Search result is displayed in the current UI locale.
- Search result navigates to the **canonical** placement of the element (see `destination` in `SettingsSearchIndex`). If an element is relocated by a migration spec, the registry `destination` must be updated in the same commit.

### Alias corpus sources

- Section titles from `strings.xml` (e.g., `settings_tab_general` → "Основные" / "Загальні")
- Setting labels from `strings.xml`
- Common user synonyms known for each locale
- Historical names for renamed settings

### Section display format

Search results show a section label in the current UI locale. `SettingsSearchAdapter.formatSection()` maps `sectionId` to a localized string resource. Any new section must be added to this map.

### Discoverability baseline

The canonical IA structure must be navigable without search for the 5 most common user tasks:
1. Change interface language
2. Add or remove a copy destination
3. Enable or disable a media type
4. Change default sort order
5. Enable or disable safe mode

A user who knows what they are looking for must reach any of these in ≤ 2 navigation steps from the Settings entry point.
