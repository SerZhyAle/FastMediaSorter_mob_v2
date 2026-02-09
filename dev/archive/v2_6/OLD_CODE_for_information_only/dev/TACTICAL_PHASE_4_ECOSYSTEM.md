# Tactical Plan: Phase 4 - Ecosystem Without Borders

**Parent Strategy:** [STRATEGIC_GROWTH_PLAN.md](STRATEGIC_GROWTH_PLAN.md)
**Focus:** FastMediaSorter everywhere your files are.

---

## 1. Cross-Platform Expansion

### Objective

Extend the experience beyond Android.

### Tactical Initiatives

- [ ] **Kotlin Multiplatform (KMP)**:
  - extract business logic (domain/data layers) into a KMP shared module.
  - set up Compose Multiplatform for Desktop UI.
- [ ] **Desktop Companion**:
  - build installers for Windows (MSI), macOS (DMG), and Linux (Deb/RPM).
  - implement local peer discovery to find phone on the same LAN.

## 2. Web Interface

### Objective

Access mobile files from any browser.

### Tactical Initiatives

- [ ] **Embedded Server**:
  - implement Ktor embedded server or similar lightweight HTTP server.
  - secure endpoints with temporary session tokens/QR code auth.
- [ ] **Web UI**:
  - build a responsive single-page application (React/Vue/KotlinJS) served by the app.
  - support file upload/download via browser.

## 3. Plugin System

### Objective

Allow community to extend functionality.

### Tactical Initiatives

- [ ] **Plugin API**:
  - define a stable Interface Definition Language (AIDL or custom) for plugins.
  - create a sandbox environment for executing plugin code safely.
- [ ] **Marketplace Support**:
  - implement logic to fetch, verify signature, and install plugins from a repo.

## 4. Media Casting

### Objective

View content on big screens.

### Tactical Initiatives

- [ ] **DLNA/UPnP**:
  - implement a UPnP control point to discover renderers (TVs).
  - allow streaming local media to remote renderers.
- [ ] **Google Cast**:
  - integrate Cast SDK.
  - implement remote display presentation for sliding shows.
