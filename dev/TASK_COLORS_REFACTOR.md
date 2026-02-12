# Task: System Color Replacement & Semantic Color Implementation
**Status**: Pending
**Priority**: Medium
**Based on**: `dev/colors_task.md`

## 1. Context & Objective
The application currently relies on hardcoded Android system colors (`@android:color/*`) in several layout files. This causes issues with:
1.  **Theming**: System colors do not adapt to app-specific themes or Night Mode properly.
2.  **Consistency**: Hinder the enforcement of a unified design system.
3.  **Maintainability**: "Magic colors" like `holo_red_dark` are scattered across layouts.

**Goal**: Replace all identified system color references with:
- **Project Colors**: `@color/white`, `@color/black` (controlled in `values/colors.xml` and `values-night/colors.xml`).
- **Semantic Colors**: New abstract color tokens for states (Error, Warning, Success).

## 2. Implementation Steps

### Step 1: Define Semantic Colors
Update `app_v2/src/main/res/values/colors.xml` to include the following semantic tokens.
*Note: Ensure these are also defined/overridden in `values-night/colors.xml` if dark mode requires different shades.*

```xml
<!-- Semantic Status Colors -->
<color name="error_color">#FFCC0000</color>   <!-- Replaces holo_red_dark -->
<color name="warning_color">#FFFF8800</color> <!-- Replaces holo_orange_dark -->
<color name="success_color">#FF669900</color> <!-- Replaces holo_green_light -->
```

### Step 2: Refactor Layout Files
Iterate through the codebase and apply the following replacement rules.

#### Replacement Rules Table
| Source (@android:color/...) | Target (@color/...) | Notes |
| :--- | :--- | :--- |
| `white` | `white` | Use project-defined white |
| `black` | `black` | Use project-defined black |
| `holo_red_dark` | `error_color` | Semantic replacement |
| `holo_red_light` | `error_color` | Semantic replacement (unify errors) |
| `holo_orange_dark` | `warning_color` | Semantic replacement |
| `holo_green_light` | `success_color` | Semantic replacement |
| `holo_blue_light` | *Analyze Usage* | Likely specific UI element tint |
| `darker_gray` | *Analyze Usage* | consider `typography_secondary` or similar |
| `transparent` | *Keep as is* | System transparent is safe |

#### Targeted Files (Primary Scope)
Based on `dev/colors_task.md` analysis:

1.  **Activities**
    -   `activity_browse.xml` (multiple variants)
    -   `activity_main.xml` (multiple variants)

2.  **List Items**
    -   `item_resource.xml`
    -   `item_destination_button.xml`
    -   `item_color.xml`
    -   `item_resource_grid.xml` (found via scan)
    -   `bottom_sheet_binary_file.xml` (found via scan)

3.  **Dialogs**
    -   `dialog_color_picker.xml`

#### Additional Scope (Player Modules)
While `colors_task.md` notes the Player is "mostly fixed", scans show remaining usages in:
-   `activity_player_unified.xml` (numerous `white` tints)
-   `custom_player_controls.xml`
-   `player_command_panel_mode.xml`

**Action**: Verify if these should be replaced or are intentional overrides. If safe, replace `white`/`black` with project equivalents.

## 3. Verification Protocol

### Build Verification
-   Run `./build-debug.PS1` to ensure no resource linking errors occur.

### Visual Verification
1.  **Theme Check**: Switch between Light and Dark themes. Ensure no text becomes invisible (e.g., white text on white background) which often happens when system colors don't invert but backgrounds do.
2.  **State Check**:
    -   **Error States**: Trigger error conditions (e.g., invalid file) to verify `error_color`.
    -   **Selection**: Check `activity_browse` selection highlights.

### Search Validation
Run the following grep command to ensure zero remaining forbidden usages:
```powershell
grep -r "@android:color/" app_v2/src/main/res/layout | grep -v "transparent"
```
*Expected Output: Empty (or only verified exceptions).*
