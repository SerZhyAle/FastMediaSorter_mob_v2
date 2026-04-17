# SPECIFICATION: Unified Graphical Elements System (Buttons)

**Status**: Draft  
**Date**: 2026-04-17  
**Version**: 1.0  

---

## 1. Problem Statement

The application currently uses graphical elements (buttons) with inconsistent styling, colors, and sizing across different screens and density buckets. Button assets vary in:
- Visual style and appearance
- Color palette and theming
- Size handling (not properly DPI-aware)
- Naming conventions and organization

This creates a fragmented user experience and makes maintenance difficult when design updates are needed.

---

## 2. Objectives

- Establish a unified, scalable graphical schema for all button elements
- Ensure consistent styling across all flavors (`standard`, `lite`, `photos`, `legacy`)
- Implement DPI-aware, vector-based assets for automatic scaling
- Create a reusable button component library
- Enable straightforward designer handoff and bulk redesign support

---

## 3. Scope

### In Scope
- All interactive button elements across all activities/fragments
- Button asset inventory (PNG, VectorDrawable, etc.)
- Naming and organization scheme
- Design library documentation and technical specifications

### Out of Scope
- Custom UI controls or gesture handlers
- Animation timing or transitions (handled separately)
- Accessibility (separate spec)
- Flavor-specific button variants (inherit from standard schema)

**Affected Flavors**: `standard`, `lite`, `photos`, `legacy`  
**Min API Level**: 26 (Android 8.0)

---

## 4. Functional Requirements

### 4.1 Button Taxonomy & Naming
- Establish naming convention: `btn_[group]_[name].[ext]`
  - `group`: functional category (e.g., `action`, `nav`, `control`, `social`)
  - `name`: semantic name (e.g., `play`, `pause`, `save`, `close`)
  - `ext`: file type (e.g., `xml` for VectorDrawable, `png` for raster)

### 4.2 Asset Organization
- Centralize button assets in drawable directories with clear folder structure
- Create a master inventory listing all buttons with metadata (group, size, color, state)
- Use VectorDrawable format as primary (DPI-independent scaling)

### 4.3 Common Design Schema
- Define base properties:
  - Size scale (small, medium, large, extra-large)
  - Color palette (primary, secondary, accent, disabled, pressed states)
  - Padding/margin standards
  - Stroke width and radius standards
  - Elevation/shadow rules

### 4.4 State Variations
- Support standard Material states: default, pressed, disabled, focused, selected

---

## 5. Design & Architecture

### 5.1 Asset Delivery Format
- **Primary**: VectorDrawable (`.xml`) for all new assets — automatically scales to any DPI
- **Fallback**: PNG raster at standard densities if vector is insufficient
- Location: `app_v2/src/main/res/drawable/` (grouped by subdirectories)

### 5.2 Implementation Strategy
- Phase 1: Audit and inventory all existing button assets
- Phase 2: Create design specification document for designer
- Phase 3: Designer provides redesigned button set following spec
- Phase 4: Integrate redesigned assets into app, update code references
- Phase 5: Validate across all screen sizes and densities

### 5.3 Designer Handoff
- Provide technical task document with:
  - Complete button inventory (current state)
  - Naming convention rules
  - Size specifications and DPI scaling requirements
  - Color and styling guidelines
  - Export format requirements (VectorDrawable XML or SVG → XML)
  - Usage context per button (where it appears in the app)

---

## 6. Non-Functional Requirements

- **Performance**: VectorDrawable rendering shall have negligible impact (<5ms inflation time)
- **Scalability**: Assets scale automatically from 160dpi (ldpi) to 560dpi (xxxhdpi)
- **Maintainability**: Single-point asset updates propagate to all usages
- **Compatibility**: Support API 26+ across all flavors

---

## 7. Testing Plan

### 7.1 Functional Testing
- [ ] Verify all buttons render correctly on small, medium, large, and extra-large screens
- [ ] Verify all buttons scale correctly across density buckets
- [ ] Verify state transitions (pressed, disabled, focused) work as designed
- [ ] Verify button interactions (clicks, long-clicks) register correctly

### 7.2 Visual Testing
- [ ] Render on physical devices (API 26, 30, 35)
- [ ] Render on emulators (hdpi, xhdpi, xxhdpi, xxxhdpi)
- [ ] Screenshot comparison for all states across screen densities

### 7.3 Regression Testing
- Run existing UI test suite after asset updates
- Verify no button layout breakage on edge-case screen sizes

---

## 8. Accessibility

- All buttons must meet WCAG 2.1 AA contrast ratios (4.5:1 for text, 3:1 for graphics)
- Minimum touch target size: 48dp × 48dp (per Material Design)
- Content descriptions provided via `android:contentDescription` on all ImageButton/ImageView elements
- Not applicable to drawable assets themselves, but enforced at usage layer

---

## 9. Acceptance Criteria

- [x] Button asset inventory complete with group/name metadata
- [ ] Naming convention document approved by team
- [ ] Design specification and technical task provided to designer
- [ ] All redesigned button assets delivered in VectorDrawable format
- [ ] Assets integrated into codebase with updated drawable references
- [ ] All buttons tested across screen densities and API levels
- [ ] No regressions in existing button interactions
- [ ] Documentation updated with button usage guidelines

---

## 10. Notes & ADRs

**ADR: VectorDrawable vs. PNG**
- Decision: Prioritize VectorDrawable for all new/redesigned buttons
- Rationale: Eliminates DPI bucket fragmentation, reduces APK size, future-proof for design changes
- Tradeoff: Slight runtime inflation cost (negligible), limited to simple shapes (acceptable for buttons)

**ADR: Centralized vs. Feature-Scoped Assets**
- Decision: Centralize all button assets in shared `drawable/` directory
- Rationale: Single source of truth, easier designer handoff, reduces duplication
- Tradeoff: Fewer feature-local customizations (acceptable — buttons should be standardized)

---

## 11. Out-of-Scope / Future Improvements

- Dynamic theming or runtime color adjustments (separate feature)
- Animated button sequences (separate feature)
- Localized button labels or RTL-specific layouts (covered by existing UI layer)
