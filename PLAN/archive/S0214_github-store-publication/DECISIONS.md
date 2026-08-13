# S0214 — Tactical Decisions (DECISIONS.md)

> Frozen choices consumed by Phases 02–05. Any later change requires reopening Phase 01 and rerunning consuming phases.

---

## Topics

GitHub topics applied to `SerZhyAle/FastMediaSorter_mob_v2` (count: 15, all lowercase, all ≤ 50 chars). Ordered by ranking value for GitHub Store discovery:

- `android` — platform discriminator (GitHub Store mobile filter)
- `apk` — installable-asset category (mandatory for mobile section ranking)
- `mobile` — platform tag (paired with `android` in GitHub Store scoring)
- `file-manager` — primary domain term
- `media-organizer` — primary domain term (matches user search intent)
- `photo-organizer` — sub-domain (covers image-only users from `photos`-flavor world)
- `video-organizer` — sub-domain
- `batch-sort` — capability marker (key differentiator vs generic file managers)
- `smb` — network source (samba/CIFS share access)
- `sftp` — network source (SSH file transfer)
- `dropbox` — cloud source (active Dropbox SDK integration)
- `google-drive` — cloud source (active Google Drive SDK integration)
- `onedrive` — cloud source (active Microsoft Graph integration)
- `kotlin` — tech-stack discoverability (developer-facing relevance)
- `exoplayer` — tech-stack discoverability (Media3 ExoPlayer)

---

## Repo description

English single-line description applied to `SerZhyAle/FastMediaSorter_mob_v2` (one line, no semicolons, no emoji, no version):

Batch organizer for photos, videos, audio and documents. Move, copy and rename media in bulk across local folders, SMB / SFTP / FTP shares and cloud storage (Dropbox, Google Drive, OneDrive). Built-in image viewer, media player, slideshow and rename presets. Works offline, no account required. Android 8+.

---

## Asset naming scheme

Deterministic APK filename pattern for assets uploaded to GitHub Releases. No commit hash, no build date, no build number embedded:

**Pattern (template):**

```
FastMediaSorter-{flavor}-{version}.apk
```

Where `{flavor}` ∈ { `standard`, `vr` } and `{version}` is the full `versionName` string from `app_v2/build.gradle.kts` (format `Y.YM.MDDH.Hmm`, e.g. `2.62.0501.151`).

**Validation regex (publisher uses this; GitHub Store users use the simplified per-flavor form below):**

```
^FastMediaSorter-(standard|vr)-\d+\.\d+\.\d+\.\d+\.apk$
```

**Concrete examples for version `2.62.0501.151`:**

- `FastMediaSorter-standard-2.62.0501.151.apk`
- `FastMediaSorter-vr-2.62.0501.151.apk`

**GitHub Store per-app variant pinning regex (recommended for users who want only one flavor channel):**

- Standard-only: `^FastMediaSorter-standard-.*\.apk$`
- VR-only: `^FastMediaSorter-vr-.*\.apk$`

---

## Release notes source

**Chosen: Option A** — automatically extract from `docs/WHATS_NEW.md` the section for the version being published. Reuses an artifact already maintained per-release; no duplication, no drift.

**Delimiter rule.** The publisher's extractor (`scripts/release/extract-release-notes.ps1`, Phase 03) accepts a `-Version <X.YM.MDDH.Hmm>` argument and locates one of two marker lines in `docs/WHATS_NEW.md`:

- **Current release marker** (used for the very latest version): a bold-marker line matching the literal regex `^\*\*Current release:\s*<version>\*\*` (case-insensitive on `Current release`). The extractor reads from the first non-empty content line AFTER this marker up to (but not including) the next `## Previous Release:` heading OR EOF, whichever comes first.
- **Previous release marker** (used when republishing an older tag): an H2 heading matching the literal regex `^## Previous Release:\s*<version>(\s|$)`. The extractor reads from the first non-empty content line AFTER this heading up to (but not including) the next `## Previous Release:` heading OR EOF.

For both markers, the immediately-following `> Changes since …` blockquote line is skipped (it duplicates information that GitHub already shows via "compare since previous tag").

If neither marker matches the supplied version, the extractor exits 2 and the publisher aborts.

**Format note.** The extracted block contains H2 sub-headings (`## What's New`, `## What's Fixed`, etc.) and bullet lists. GitHub renders these correctly as release notes; no transformation required.

---

## README badge

**Badge image source.** Use the upstream image hosted by the GitHub Store project:

```
https://raw.githubusercontent.com/OpenHub-Store/GitHub-Store/main/media-resources/ghs_download_badge.png
```

Rationale: the GitHub Store project explicitly hosts this badge for downstream apps to embed; mirroring locally adds maintenance burden (image refresh when their design changes) with no benefit. If the upstream URL ever 404s, fall back to a local copy under `media-resources/ghs_download_badge.png`; this fallback is not implemented up-front.

**Link target (deep-link to app card inside GitHub Store):**

```
https://github-store.org/app?repo=SerZhyAle/FastMediaSorter_mob_v2
```

**Visual format.**

- Badge height: `80` pixels (matches the existing F-Droid / direct-APK badges in the root `README.md` download row).
- HTML form (used in all three README files for inline placement next to other distribution badges):

  ```html
  <a href="https://github-store.org/app?repo=SerZhyAle/FastMediaSorter_mob_v2">
    <img src="https://raw.githubusercontent.com/OpenHub-Store/GitHub-Store/main/media-resources/ghs_download_badge.png" alt="Get it on GitHub Store" height="80" />
  </a>
  ```

- `alt` text: `Get it on GitHub Store` (constant across locales — `alt` is a fallback for image loading failures, not user-facing copy; the visible badge image is already an English-language asset).

**Captions (locale-specific accompanying sentence — placed near the badge, one short phrase per README).**

- EN: Available on GitHub Store — install, update, and discover apps directly from GitHub releases.
- RU: Доступно в GitHub Store — установка и обновление прямо из релизов на GitHub.
- UK: Доступно в GitHub Store — встановлення та оновлення безпосередньо з релізів на GitHub.

**COMMUNICATION_POLICY §6 spot-check (manual gate, performed at decision time):**

- No exception text. ✓
- No "Are you sure?" pattern. ✓
- No "operation completed successfully" phrasing. ✓
- Each caption ends with the user benefit ("install, update, discover" / "установка и обновление" / "встановлення та оновлення"). ✓
- No emoji. ✓
- RU uses `..` (em-dash separator, not three dots); RU contains `ё` letter in "обновление" via the standard spelling. ✓
- Length within README in-line readability (each ≤ 110 chars). ✓
