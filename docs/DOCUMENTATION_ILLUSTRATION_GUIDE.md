# Documentation Illustration Conventions Guide

This guide defines conventions for capturing, naming, placing, and annotating visual assets across the documentation site.

## Directory Structure

All visual assets reside under `documentation/assets/`:
```text
documentation/
  assets/
    images/
      getting-started/
      browsing/
      storage/
      network/
      player/
      audio/
      launcher/
      wear/
      vr/
      common/
```

## File Naming Conventions

- Format: lowercase kebab-case `[category]-[action]-[screen-or-element].[ext]`.
- Examples:
  - `audio-playback-queue-view.png`
  - `storage-saf-grant-dialog.png`
  - `launcher-desktop-widget-resize.png`

## Screenshot Standards

1. **Clean Status Bar**: Battery full, WiFi connected, no personal notification clutter.
2. **Standard Resolution**: 1080x2400 (Phone) or 1920x1080 (Landscape/Tablet/TV), saved in PNG or optimized WebP.
3. **Highlighting**: Use rounded accent badges (#3fb950 or gold #d29922) or subtle borders to indicate relevant UI elements. Never use hand-drawn circles or arrows.
4. **Dark & Light Mode**: Default to Dark Theme screenshot unless illustrating Light Mode settings.

## Accessibility & Alt-Text

Every `<img>` element must include a meaningful `alt-text` describing the action and visible UI state:
- **Good:** `<img src="assets/images/audio-queue.png" alt="FastMediaSorter music player showing active playlist queue and shuffle toggle enabled" />`
- **Bad:** `<img src="assets/images/screenshot.png" alt="Screenshot" />`

## Image Markup Example

```html
<figure class="doc-figure">
    <img src="assets/images/audio/audio-playback-queue-view.png"
         alt="Music player playback queue view displaying track list and reorder handles"
         class="doc-screenshot"
         loading="lazy" />
    <figcaption>Figure: Reordering tracks in the active playback queue.</figcaption>
</figure>
```
