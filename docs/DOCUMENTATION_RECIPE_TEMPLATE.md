# Documentation Recipe Template & Structure Guide

This guide defines the standard structure for all culinary-style user documentation recipes across FastMediaSorter v2 (S2945 - S2967).

## Recipe Philosophy

Every documentation article is structured like a recipe in a classic cookbook:
1. **Appetizing introduction**: Why you want to do this and what real-world problem it solves.
2. **Clear ingredients**: What flavor, permissions, storage, or devices are required before starting.
3. **Step-by-step preparation**: Numbered, visual steps showing the exact buttons, gestures, and settings.
4. **Taste test**: How to confirm everything worked properly.
5. **Chef's tips & variations**: Helpful tricks, caveats, and troubleshooting advice.
6. **Next dishes**: Cross-links to related recipes and advanced techniques.

---

## Canonical Markdown / HTML Skeleton

```html
<!-- Canonical Recipe Skeleton -->
<article class="doc-content">
    <header class="doc-page-header">
        <div class="doc-badge-group">
            <span class="doc-badge doc-badge-category">Category Name</span>
            <span class="doc-badge doc-badge-flavor">All Editions / sideload</span>
        </div>
        <h1 class="doc-title">How to [Perform User Goal]</h1>
        <p class="doc-lead">
            A short 2-3 sentence overview introducing the scenario and what you will accomplish.
        </p>
    </header>

    <!-- 1. The Scenario / Life Scene -->
    <section id="scenario" class="doc-section">
        <h2>Why You'll Love This</h2>
        <p>
            Describe the everyday situation (e.g., managing photos after a vacation, organizing an SD card,
            streaming music to a Bluetooth speaker). Keep it relatable, warm, and practical.
        </p>
    </section>

    <!-- 2. Prerequisites & Ingredients -->
    <section id="prerequisites" class="doc-section">
        <h2>What You Need</h2>
        <div class="doc-ingredients-card">
            <ul class="doc-checklist">
                <li><strong>Supported Editions:</strong> Standard, NoLegal, Lite, Photos, Legacy, VR, FOSS (or specific flavor noted).</li>
                <li><strong>Required Permissions:</strong> Storage Access Framework (SAF) / Manage All Files / Notifications.</li>
                <li><strong>Hardware:</strong> Android Phone, Tablet, Foldable, TV, or Wear OS watch.</li>
            </ul>
        </div>
    </section>

    <!-- 3. Step-by-Step Instructions -->
    <section id="steps" class="doc-section">
        <h2>Step-by-Step Instructions</h2>

        <div class="doc-step">
            <div class="doc-step-number">1</div>
            <div class="doc-step-body">
                <h3>Open the Source Media Folder</h3>
                <p>Launch Fast Media Sorter and select your source storage from the left drawer or bottom navigation bar.</p>
                <figure class="doc-figure">
                    <img src="assets/images/sample-step1.png" alt="Selecting source folder from drawer" class="doc-screenshot" />
                    <figcaption>Figure 1: Storage drawer with internal and SD card sources.</figcaption>
                </figure>
            </div>
        </div>

        <div class="doc-step">
            <div class="doc-step-number">2</div>
            <div class="doc-step-body">
                <h3>Perform the Primary Action</h3>
                <p>Tap the action button or use the quick gesture to execute the operation.</p>
            </div>
        </div>
    </section>

    <!-- 4. Expected Outcome -->
    <section id="outcome" class="doc-section">
        <h2>Expected Outcome</h2>
        <div class="doc-callout doc-callout-success">
            <p>Your media files are sorted into target folders and playlists are updated immediately.</p>
        </div>
    </section>

    <!-- 5. Pro Tips & Common Pitfalls -->
    <section id="tips" class="doc-section">
        <h2>Chef's Tips & Troubleshooting</h2>
        <div class="doc-callout doc-callout-tip">
            <h4>Pro Tip</h4>
            <p>Use long-press on any thumbnail to enter batch multi-selection mode quickly.</p>
        </div>
        <div class="doc-callout doc-callout-warning">
            <h4>Common Mistake</h4>
            <p>Ensure SD card write permissions are granted via SAF before attempting batch moving.</p>
        </div>
    </section>

    <!-- 6. Next Steps & Cross-Links -->
    <section id="related" class="doc-section">
        <h2>What to Try Next</h2>
        <ul class="doc-related-links">
            <li><a href="sample-settings-recipe.html">Customizing Playback and Sorting Defaults</a></li>
            <li><span class="doc-bookmark" data-page-id="storage.batch-renaming">Batch Renaming Patterns [Planned]</span></li>
        </ul>
    </section>
</article>
```

---

## Checklist for Documentation Authors

- [ ] Every article addresses a clear human task, not a class hierarchy.
- [ ] Prerequisites explicitly name flavor compatibility and required permissions.
- [ ] Every step includes a visual aid (screenshot, diagram, or UI icon reference).
- [ ] Illustrations include descriptive alt-text.
- [ ] Cross-links to unwritten pages use `<span class="doc-bookmark" data-page-id="...">`.
