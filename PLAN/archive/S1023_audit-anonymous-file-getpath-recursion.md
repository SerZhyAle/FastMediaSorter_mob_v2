# S1023 - Audit anonymous `File` subclasses for getPath()/getAbsolutePath() self-recursion

**Status:** Archived

## 0. Raw finding (auto-parked from S1021, 2026-07-12)

While fixing S1021 (Browse background Copy/Move of a network source silently failing) the root
cause was infinite self-recursion in `BrowseFileTransferWorker.toFile()`:

```kotlin
object : File(path) {
    override fun getPath(): String = path          // `path` binds to File.getPath(), not the receiver -> recursion
    override fun getAbsolutePath(): String = path
}
```

Inside `object : File(..)` the bare name `path` resolves to the `File.getPath()` member being
overridden (the object's own implicit receiver), not to the enclosing `BrowseFileTransferSource.path`
the author intended - so `getPath()` calls itself until `StackOverflowError`. Fixed in S1021 by
capturing the intended value into a local first.

`rg "override fun getPath\(\): String = path"` finds 8 more sites with the identical override shape:

- app_v2/.../domain/usecase/DeleteByFileSizeUseCase.kt:72
- app_v2/.../ui/dialog/DeleteDialog.kt:73
- app_v2/.../ui/player/fileops/PlayerFileOperation.kt:145
- app_v2/.../ui/browse/managers/BrowseShareOperationsHelper.kt:258
- app_v2/.../ui/browse/managers/BrowseRenameDialogManager.kt:210
- app_v2/.../ui/browse/managers/BrowseFileOperationsManager.kt:299, 585, 686
- app_v2/.../ui/browse/managers/BrowseDeleteManager.kt:100

## 1. Why this needs its own ticket (not fixed inline)

Two things must be established per site before deciding whether each is a real bug:

- **Name-resolution**: in S1021 the shadowed `path` was an extension-receiver property. In these
  sites the `path` is typically a local `val` / lambda parameter. Whether Kotlin still resolves the
  bare `path` inside `getPath()` to `File.getPath()` (inner implicit receiver) rather than the
  enclosing local must be confirmed - the shadowing rule points that way, but it needs a concrete
  test/decompile, not assumption, because a local in the lexical scope may win.
- **Live-path**: several of these are delete/rename/share flows. If any resolves to the recursive
  `File.getPath()` AND its object is ever passed somewhere that reads `.path`/`.absolutePath`
  (e.g. `FileOperationUseCase.isNetworkPath`), it is a latent StackOverflowError just like S1021;
  if the object's path is never read via those getters, it is dormant. Each site needs its
  live-usage traced.

## 2. Audit result (2026-07-13) - all sites SAFE, zero defects

Read-only audit of every site (whole enclosing function + downstream `.path`/`.absolutePath`
consumption traced). **No live bug, no latent/dormant bug.** In each site the intended `path` is a
local `val` or a parameter (function or lambda param), which per Kotlin scoping shadows the object's
own implicit-receiver synthetic `getPath()` - so the bare `path` inside the override binds to the
author's value, not to the recursive synthetic property. The S1021 trap reproduces ONLY when the
intended value is an implicit-receiver member/extension property (S1021's `BrowseFileTransferSource.path`),
which is not the case anywhere here.

| Site | `path` binding | Verdict |
|------|----------------|---------|
| DeleteByFileSizeUseCase.kt:70-73 | LOCAL_VAL (`val path = mediaFile.path`) | SAFE |
| DeleteDialog.kt:71-74 | LOCAL_VAL (`val path = file.absolutePath`) | SAFE |
| PlayerFileOperation.kt:143-147 | PARAMETER (`createNetworkAwareFile(path, name)`) | SAFE |
| BrowseShareOperationsHelper.kt:256-261 | PARAMETER (`createNetworkAwareFile(path, name, size)`) | SAFE |
| BrowseRenameDialogManager.kt:208-212 | PARAMETER (`createDisplayFile(path, displayName)`) | SAFE |
| BrowseFileOperationsManager.kt:297-301 | LAMBDA PARAM (`fileOnlyPaths.map { path -> }`) | SAFE |
| BrowseFileOperationsManager.kt:583-587 | LAMBDA PARAM | SAFE |
| BrowseFileOperationsManager.kt:684-688 | LAMBDA PARAM | SAFE |
| BrowseDeleteManager.kt:98-101 | LAMBDA PARAM | SAFE |

Downstream consumption (`.path`/`.absolutePath` via `FileOperationUseCase`/`isNetworkPath`) does occur
at every site, so point (b) holds - but it is moot because point (a) (name-resolution) is SAFE everywhere.

## 3. Disposition

- **No code change.** The audited concern is verified absent at all 9 override sites.
- Preventive hardening (a shared `networkAwareFile(path, name?, size?)` factory so params always make
  the pattern safe, plus a mechanical `assert-*` gate banning `object : File(..)` getPath-overrides
  outside the factory) is real but **inseparable from network-path predicate consolidation** - the
  existing `createNetworkAwareFile` variants diverge on the `content://` scheme, so unifying them
  changes behaviour and needs device verification of file-op flows. Folded into **S1028
  (dedupe-network-path-detection)** §10, whose scope already owns the network-path predicate; not a
  fresh ticket.
- S1021 hardening (widened `catch (Throwable)` in `FileOperationUseCase.executeInternal` and
  `BrowseFileTransferWorker.doWork`) already surfaces any future such recursion as a logged
  `StackOverflowError` instead of a silent WorkManager failure.
