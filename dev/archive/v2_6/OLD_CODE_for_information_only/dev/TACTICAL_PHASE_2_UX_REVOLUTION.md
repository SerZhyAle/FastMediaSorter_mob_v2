# Tactical Plan: Phase 2 - User Experience Revolution (UX 2.0)

**Parent Strategy:** [STRATEGIC_GROWTH_PLAN.md](STRATEGIC_GROWTH_PLAN.md)
**Focus:** Interface that creates a "Wow-effect" and is intuitive for any user.

---

## 1. Next-Level Adaptability

### Objective

Ensure the app looks and works perfectly on any form factor.

### Tactical Initiatives

- [ ] **Foldable Support**:
  - implement `Jetpack WindowManager` to detect hinge position.
  - create adaptive layouts (List-Detail) that split content on open foldables.
- [ ] **Tablet Optimization**:
  - design and implement a permanent navigation rail for large screens.
  - enable multi-column grid views dynamically based on screen width.
- [ ] **Desktop Mode**:
  - support mouse hover states and right-click context menus.
  - optimize for keyboard navigation (Tab focus, arrow keys).

## 2. Personalization

### Objective

Let users make the app truly theirs.

### Tactical Initiatives

- [ ] **Theme Engine**:
  - implement dynamic color extraction from wallpaper (Material You).
  - create a style picker for custom accent colors and shapes.
- [ ] **Custom Toolbars**:
  - design a drag-and-drop editor for toolbar actions.
  - persist user preferences for button visibility and order.

## 3. Interactivity

### Objective

Make file management feel tactile and fluid.

### Tactical Initiatives

- [ ] **Advanced Gestures**:
  - implement custom `ItemTouchHelper` for complex swipe actions.
  - add pinch-to-zoom listener for changing grid span count dynamically.
- [ ] **Drag-and-Drop**:
  - implement system-wide Drag-and-Drop API support.
  - enable dragging files from the app to split-screen apps (e.g., Telegram).
- [ ] **Shared Element Transitions**:
  - implement motion transitions when opening media viewer.
  - use `MaterialContainerTransform` for expanding cards.
