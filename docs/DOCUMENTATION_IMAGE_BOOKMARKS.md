# Documentation Image Bookmarks Specification & Authoring Guide

This specification defines the canonical image bookmark format for the FastMediaSorter v2 documentation corpus (S2977).

## Purpose

To enable authors of all 22 thematic documentation tickets (S2946 - S2967) to author rich, illustrated culinary recipes without needing to manually capture or crop screenshots. Authors insert standardized image bookmark placeholders specifying the desired screen, state, and device profile.

During **Phase B (Batch Capture)**, automated capture scenarios execute on target emulators/devices and replace the bookmark placeholders with optimized production screenshots.

---

## Canonical Image Bookmark Syntax

Authors place image bookmarks using the `doc-img-bookmark` container in HTML:

```html
<div class="doc-img-bookmark"
     data-shot-id="audio.queue-reorder"
     data-device-profile="phone"
     data-screen-state="audio-queue-active"
     data-alt="Music player playback queue view displaying track list and drag-reorder handles"
     data-caption="Figure: Reordering tracks in the active playback queue.">
    <div class="doc-img-bookmark-inner">
        <span class="doc-img-bookmark-badge">Image Bookmark [Planned]</span>
        <strong class="doc-img-bookmark-title">Screenshot: Audio Playback Queue</strong>
        <p class="doc-img-bookmark-desc">Shows multi-track queue with drag handles and shuffle button active.</p>
        <div class="doc-img-bookmark-meta">
            <span>Profile: <code>phone (1080x2400)</code></span>
            <span>ID: <code>audio.queue-reorder</code></span>
        </div>
    </div>
</div>
```

---

## Data Attributes Reference

| Attribute | Required | Description | Example |
|-----------|:--------:|-------------|---------|
| `data-shot-id` | Yes | Unique dot-separated screenshot identifier | `audio.queue-reorder`, `settings.search-query` |
| `data-device-profile` | Yes | Target device profile (`phone`, `tablet`, `wear-round`, `wear-square`) | `phone`, `wear-round` |
| `data-screen-state` | Yes | Identifier of the UI screen state or dialog | `settings-overview-main`, `browser-multiselect` |
| `data-alt` | Yes | Complete accessibility alt-text for the eventual image | `"Browsing local storage folders with metadata badges"` |
| `data-caption` | No | Optional figure caption text | `"Figure 1: Navigating storage folders."` |

---

## Supported Device Profiles

1. `phone`: Standard Android Phone (1080x2400 portrait / landscape)
2. `tablet`: Android Tablet (1920x1200 / 2560x1600 landscape)
3. `wear-round`: Wear OS Round Watch (Large 227dp / Small 192dp)
4. `wear-square`: Wear OS Square Watch

---

## Validation & Quality Gates

- `scripts/quality/assert-docs-screenshots.ps1` parses all HTML/Markdown files in `documentation/`.
- The closure (`scripts/post-change.ps1`) runs it, fatal, whenever the changed set carries a file under `documentation/`, `docs/content/` or `docs/docs-pages-manifest.jsonl`.
- In normal development mode, valid image bookmarks are counted and reported as pending capture items.
- In strict mode (`-Strict`, used in release certification S2975), any unrendered image bookmark causes a gate failure.
