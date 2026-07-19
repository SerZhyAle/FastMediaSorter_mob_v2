---
name: stream-catalog-publish
description: How to refresh + publish the downloadable stream catalog (channels + favicon atlas) to the GitHub release the app fetches
type: reference
---

The app fetches its stream catalog from GitHub release **`delivery-so-v1`**, asset **`stream-catalog.zip`** (hard-coded `CATALOG_URL` in `ImportStreamCatalogUseCase`). The owner periodically asks to "обнови список каналов и опубликуй". Tooling: `scripts/streams/collect-stream-candidates.ps1` (PowerShell 7).

**Refresh + build favicon atlas + publish (one shot):**
`pwsh -NoProfile -File scripts/streams/collect-stream-candidates.ps1 -WithFavicons -Publish`
- `-WithFavicons` builds `delivery/stream-catalog/favicon-atlas.png` from each row's `homepage` column and stamps a `favicon_index` column into `streams.csv` (added S0668).
- Discovery (default axes) appends only newly-discovered ALIVE channels - usually few.

**Safer two-step (validate before clobbering production):** run `-WithFavicons` WITHOUT `-Publish` first, validate `streams.csv` (19 cols since S1117 added trailing `access`, favicon_index range, no gaps) + atlas (512 wide, < 3MB), then publish the validated files with `-CatalogOnly -SkipLiveness -Publish` (no re-probe/re-discovery/re-atlas).

**Why:** publish does `gh release upload delivery-so-v1 stream-catalog.zip --clobber`, mutating the live asset all users fetch. A bad CSV would reach everyone. The packer is image-heavy (System.Drawing) and was first written in S0668.

**How to apply:**
- `gh` CLI is at `C:\Program Files\GitHub CLI\gh.exe`, authed as `SerZhyAle` (scope `repo`) - resolves in **pwsh, NOT the Git-Bash PATH**. Run publish from the PowerShell tool, not Bash. (Verify auth still valid: `gh auth status`; a token can expire.)
- The zip MUST keep `streams.csv` as entry 0 (packed before the atlas) so already-installed OLD apps read the CSV without streaming the whole atlas; atlas stays < 3 MB (`-MaxAtlasBytes`) to fit the 30 s import callTimeout. Backward-compat is load-bearing - see [[feedback_third_party_branding_not_a_blocker]] and the S0668 spec.
- Typical run is long (favicon fetch over ~1900 homepages); run in background and validate the log tail, not the trailing echo exit code (see [[feedback_background_task_exit_code_is_echo]]).
