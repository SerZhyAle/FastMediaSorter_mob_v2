# Research 03 - Clipboard image source, decoupled from save destination

**Strategic item:** §6.3 (and ADR-2)
**Status:** Resolved

## Question

What image does the clipboard get when the post-capture action is silent save, the destination is a network resource, or no resource is selected - i.e. when `SaveScreenshotUseCase.SaveResult.Success.savedUri` is null or non-uniform?

## Finding

`savedUri` is an unreliable clipboard source:

- Selected **local** resource → FileProvider URI (works for clipboard).
- Selected **network** resource → `savedUri` is `null` (FileProvider scope miss) - nothing to copy.
- **Public MediaStore** collection → `content://media/...` URI (works, but different grant/lifetime semantics).

`SaveScreenshotUseCase` also **recycles the bitmap** in its `finally` block and deletes its own temp PNG, so after the save call there is no in-memory frame to fall back on.

Decision: the clipboard always sources a **dedicated app-cache PNG copy** produced from the live bitmap, independent of the save destination. This gives one uniform behavior for every destination and for silent save without a selected resource.

## Consequence for the plan

- In `ScreenCaptureService.processCapture`, when the clipboard flag is on, run the clipboard copy from the still-alive bitmap **before** `SaveScreenshotUseCase` consumes/recycles it.
- The clipboard copy and the destination save are independent: a clipboard failure must not abort the save, and vice versa (separate `try`/`catch`, separate user confirmation).
- The cache copy lives under a stable `cacheDir` subfolder addressable via the existing FileProvider `cache-path`. Old clipboard cache files may be overwritten/pruned on each capture; the URI only needs to outlive the paste, not be permanent.

## Sources

- In-repo: `domain/usecase/SaveScreenshotUseCase.kt` (savedUri null for network, MediaStore URI for public, bitmap recycle + temp delete in `finally`).
- In-repo: `screencapture/ScreenCaptureService.kt` (bitmap created in `processCapture`, passed to save use case).
