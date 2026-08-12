---
name: epub-asset-handler-log-noise
description: E AndroidProtocolHandler "Unable to open asset URL" in the EPUB viewer is benign by design - not an image-loading defect
metadata:
  type: project
---

`E AndroidProtocolHandler: Unable to open asset URL: file:///android_asset/<path>` during a document-viewer session is **expected noise**, not a broken image. S1611 was filed on this line and archived as a non-defect on 2026-08-12.

**Why:** the EPUB chapter is loaded with base URL `file:///android_asset/`, so WebView probes the real asset dir first (fails, logs `E`), and only then `shouldInterceptRequest` serves the bytes out of the unpacked book. The failing probe and the successful serve are ~2 ms apart on the same tid.

**How to apply:** before treating it as a defect, look for the neighbouring line. Success = `D EpubResourceContentHelper: EPUB: Serving intercepted asset '<path>' … (N bytes, <mime>)`. A real failure = `W EPUB: Asset '<path>' not found in EPUB resources` plus the available-images dump. Also normal: `EPUB: Found 0 <img> tags in chapter` on Draft2Digital books - their cover is not an `<img>`, so it goes through interception instead of `data:`-URI inlining. Changing the base URL to silence the `E` line was considered and rejected - it moves the document origin and touches the selection JS bridge in a shipped feature.
