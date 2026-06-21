# Competitive Feature Research for FastMediaSorter v2

Last updated: 2026-06-21
Audience: product, architecture, and future spec authors.
Scope: comparable Android gallery, media organizer, file manager, sync, and private photo library products reviewed through official product pages and documentation.

## Goal

Identify feature areas that are repeatedly present in adjacent products but are not yet exposed as first-class FastMediaSorter features, then translate them into a practical backlog.

## Short conclusion

FastMediaSorter already has an unusually broad foundation: multi-protocol browsing, powerful file operations, media playback, OCR, translation, duplicates, instant sort destinations, and remote/cloud access. The strongest missing layer is not another protocol or another viewer. It is a smarter organization and automation layer on top of the existing storage engine.

The most promising additions are:

1. Smart albums and saved filters.
2. Tags, ratings, and metadata write-back.
3. Calendar, map, people, and review-first library views.
4. Near-duplicate and burst-shot cleanup.
5. Background sync and backup profiles for NAS/cloud targets.
6. Secure private vault and privacy-aware sharing/export.
7. Shared albums and optional public/private link workflows.

## Recommended feature backlog

### 1. Smart albums and saved filter presets

Why it matters:
- F-Stop, Mylio, and Immich all reduce browsing cost by letting users define reusable views instead of manually re-entering filters.
- FastMediaSorter already has many resource and media dimensions; users need a way to save them as reusable collections.

What to implement:
- User-defined smart albums based on folder, resource, date range, media type, tag, rating, location, OCR text, duplicate state, or file-size rules.
- Saved filter presets for common workflows such as "today's screenshots", "videos from NAS", "unsorted camera photos", or "OCR documents from this month".
- Optional pinning of smart albums into the main browser and player entry flows.

Evidence:
- F-Stop promotes smart albums.
- Mylio centers its product around smart organization and declutter views.
- Immich exposes advanced search filters across people, albums, tags, location, rating, and archive state.

### 2. Tags, ratings, labels, and metadata write-back

Why it matters:
- Several competitors treat metadata as the main organization primitive, not only folders.
- FastMediaSorter currently has strong file movement, but weaker semantic organization.

What to implement:
- Manual tags.
- Star ratings.
- Color labels or review states such as keep, archive, delete later, or print.
- Metadata write-back where technically safe, preferably EXIF/XMP/IPTC aligned, so data survives export and desktop workflows.
- Batch metadata edit and batch rename for selected files.

Evidence:
- F-Stop highlights tags, ratings, EXIF editing, and search by tag/rating.
- Piktures explicitly stores user tags in photo metadata.
- Aves positions itself as a metadata explorer.

### 3. Calendar, timeline, map, and people views

Why it matters:
- Competing apps increasingly offer multiple navigation lenses over the same library.
- FastMediaSorter is already rich in content types; it now needs richer library navigation.

What to implement:
- Calendar view for day-based browsing.
- Map view for geotagged media, with reverse-geocoded country/city grouping.
- People view powered by optional face clustering.
- Review timeline optimized for large camera rolls and recent captures.

Evidence:
- Piktures offers calendar and location navigation.
- Aves flows from albums to tags to maps and supports GeoTIFF-aware metadata browsing.
- Google Photos made search by place, person/pet, and subject a baseline user expectation.
- Immich and Mylio both expose people-oriented navigation.

### 4. Global smart search across metadata, OCR, people, and content

Why it matters:
- FastMediaSorter already includes OCR and rich metadata access, but the discovery layer is still mostly manual.
- Search becomes a force multiplier across local, NAS, and cloud libraries.

What to implement:
- One search surface that can query filename, path, OCR text, tags, ratings, location, camera metadata, and file type.
- Optional semantic search module for image content, implemented as an extension or heavier optional component.
- Search shortcuts like "similar to this", "more from this location", or "find all pages mentioning this text".

Evidence:
- Google Photos searches by place, person/pet, and subject.
- Immich supports contextual search, OCR search, tag search, location search, rating filters, and face filters.
- Aves emphasizes quick movement between albums, tags, and maps.

### 5. Near-duplicate, burst, and best-shot review

Why it matters:
- Exact duplicate removal is already present, but users also need help with "almost the same" photos.
- This is one of the clearest upgrade paths from current FastMediaSorter strengths.

What to implement:
- Perceptual near-duplicate detection alongside the existing exact-match pipeline.
- Burst grouping.
- Side-by-side compare and quick review queue.
- Optional "keep best candidate" suggestions using blur, exposure, face visibility, and resolution heuristics.

Evidence:
- Mylio promotes Duplicate Remover and Quick Review for similar or burst shots.
- Google Photos and other modern photo products train users to expect cleanup assistance beyond exact binary duplicates.

### 6. Background backup and sync profiles

Why it matters:
- PhotoSync and Immich show that transfer automation is a product pillar by itself.
- FastMediaSorter already has the remote protocol layer needed to make this especially powerful for NAS-heavy users.

What to implement:
- Backup profiles targeting local folders, SMB, SFTP, FTP, WebDAV-like cloud endpoints where supported, and existing cloud integrations.
- Trigger conditions: Wi-Fi SSID, charging, schedule, instant new-photo detection, and optional geofence.
- One-way mirror mode for camera folders and selected albums.
- Destination folder templates driven by date, album, device, resource, media type, or metadata.
- Post-transfer actions such as mark complete, move source, or delete source after verified success.

Evidence:
- PhotoSync provides Wi-Fi, geolocation, schedule, instant-capture, and charging triggers plus automatic folder naming and optional delete-after-transfer.
- Immich mirrors mobile albums to server-side albums and limits uploads to Wi-Fi by default.
- Mylio's positioning strongly reinforces the value of private multi-device sync.

### 7. Shared albums, partner libraries, and controlled public links

Why it matters:
- FastMediaSorter already spans devices, shares, and clouds, but its collaboration layer is mostly export-oriented.
- Even private home/NAS users increasingly want lightweight family-sharing flows.

What to implement:
- Shared albums between trusted FastMediaSorter users or between devices tied to the same backup target.
- Editor/viewer roles for album collaboration where backend capability allows it.
- Optional public links with password and expiry for explicitly shared sets.
- Shared upload drop-box style flow for event collection.

Evidence:
- Immich supports shared albums, partner sharing, and public links with passwords and expiration.
- Mylio promotes family photo sharing as a core workflow.

### 8. Secure private vault

Why it matters:
- Privacy features are highly visible and understandable to users.
- FastMediaSorter already handles many sensitive file types and remote resources.

What to implement:
- Encrypted private vault for photos, videos, documents, and possibly notes.
- Dedicated UI entry, PIN or biometric gate, and safe export/restore behavior.
- Optional vault-aware exclusion from search, slideshow, widgets, and external intents.

Evidence:
- Piktures offers Secret Space with encryption and a dedicated restore/export story.
- X-plore offers Vault for sensitive files.

### 9. Privacy-aware share and export presets

Why it matters:
- FastMediaSorter already has Send to.. and copy/move/export flows, so this can improve an existing strength.
- Users increasingly care about location leaks and oversized shares.

What to implement:
- Share presets such as original, compressed, resized, metadata stripped, GPS stripped, or text-only extract.
- Per-target defaults, for example "Telegram -> compressed, no GPS" or "Keep -> original".
- Batch export profiles for social, archive, OCR handoff, or legal/document flows.

Evidence:
- Piktures highlights GPS removal before sharing.
- PhotoSync emphasizes custom transfer quality and target-specific organization.

### 10. Power-user file navigation upgrades

Why it matters:
- FastMediaSorter is already closer to a file manager than most galleries.
- NAS and archive-oriented users will notice when the browser is less efficient than dedicated file managers.

What to implement:
- Optional dual-pane browser mode for tablet, TV, desktop-mode, and landscape-heavy use.
- Expandable tree navigation for large folder hierarchies.
- Disk usage view or storage heatmap for large local folders and removable media.
- Richer archive support such as 7z/RAR/TAR families where licensing and maintenance are acceptable.
- Local Wi-Fi web portal or browser access for quick device pickup from a PC.

Evidence:
- Solid Explorer and X-plore both lean heavily on dual-pane productivity.
- X-plore adds tree navigation, disk map, LAN discovery, browser access, and Wi-Fi sharing.
- Solid Explorer differentiates with broad encrypted archive support.

### 11. Special-media detection and richer media taxonomy

Why it matters:
- Rich libraries contain more than plain JPEG and MP4 assets.
- FastMediaSorter already has VR and advanced playback, so media taxonomy can become a visible advantage.

What to implement:
- First-class detection and badges for motion photos, panoramas, 360 content, GeoTIFF, RAW families, slow-motion clips, and bursts.
- View-mode filters and saved smart albums based on these media classes.
- Optional dedicated actions for each class, such as "show panorama viewer" or "extract motion-photo still".

Evidence:
- Aves explicitly detects motion photos, panoramas, 360 videos, and GeoTIFF files.
- PhotoSync surfaces RAW handling as a product feature.

## Suggested rollout order

### Phase A - Highest value, lowest platform risk

- Smart albums and saved filters.
- Tags, ratings, and metadata write-back.
- Calendar and map views.
- Near-duplicate and burst review.

### Phase B - Strong differentiation for NAS and power users

- Background backup and sync profiles.
- Privacy-aware share/export presets.
- Dual-pane and tree navigation improvements.

### Phase C - Strategic and optional-heavy features

- People view and face clustering.
- Semantic image search.
- Shared albums and controlled public links.
- Secure private vault.

## Best fit with the current codebase

- Duplicate finder can evolve from exact matching into perceptual matching and burst review.
- Existing OCR and translation stack can feed searchable text indexes.
- Current transfer strategies and background infrastructure are a strong base for backup profiles.
- Existing local, network, and cloud abstractions already solve the hardest transport problem for sync workflows.
- Optional heavy features such as face clustering or semantic search fit the current "downloadable extensions / on-demand components" philosophy.

## Recommended first spec candidates

1. Smart albums + saved filters + pinning.
2. Tags/ratings + metadata write-back + batch rename.
3. Near-duplicate and burst review.
4. Backup profiles for local/SMB/SFTP/cloud targets.
5. Secure private vault.

## Sources reviewed

- Aves: https://github.com/deckerst/aves
- F-Stop: https://play.google.com/store/apps/details?hl=en_US&id=com.fstop.photo
- Solid Explorer: https://neatbytes.com/solidexplorer/
- PhotoSync: https://www.photosync-app.com/home
- Google Photos: https://www.google.com/intl/en_us/photos/about/
- Immich facial recognition: https://docs.immich.app/features/facial-recognition/
- Immich searching: https://docs.immich.app/features/searching/
- Immich mobile backup: https://docs.immich.app/features/mobile-backup/
- Immich sharing: https://docs.immich.app/features/sharing/
- Immich folder view: https://docs.immich.app/features/folder-view/
- Mylio Photos: https://mylio.com/
- X-plore: https://www.lonelycatgames.com/apps/xplore
- Piktures home/help: https://help.piktures.app/
- Piktures Secret Space: https://help.piktures.app/protect-photos/secret-space
- Piktures calendar view: https://help.piktures.app/searching/calendar-view
- Piktures location filter: https://help.piktures.app/searching/location-filter
- Piktures product principles: https://help.piktures.app/our-principles/product-principles
