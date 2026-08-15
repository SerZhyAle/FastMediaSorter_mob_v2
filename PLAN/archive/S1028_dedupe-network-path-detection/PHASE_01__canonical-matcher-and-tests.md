# Phase 01 - canonical-matcher-and-tests

**Goal:** One canonical home in `PathUtils` with two functions + a comprehensive unit test as the regression net.

## Steps

- [ ] **1.1** In `core/util/PathUtils.kt`, evolve `isNetworkPath` to `fun isNetworkPath(path: String, includeCloud: Boolean = true): Boolean` - scheme-based (keep the existing `getScheme` approach); when `includeCloud` is false, exclude `cloud` from the accepted schemes. Default `true` keeps every current caller of the no-arg form unchanged. Verify: both arities resolve; compiles.
- [ ] **1.2** Add `fun fileMatchesProtocol(rawFilePath: String, protocol: String): Boolean` to `PathUtils` - body VERBATIM from `FileOperationUseCase`'s local `File.isNetworkPath` (the 4-branch form: `"$protocol://"`, `"/$protocol://"`, `"/$protocol:/"`, `"$protocol:/"`). This preserves the java.io.File slash-mangling tolerance exactly. Do NOT "fix" the mislabeled `// Single colon case` comment (owner deferred edge-case fixes) - carry it or drop the comment, but keep the logic identical. Verify: function present; compiles.
- [ ] **1.3** Add `PathUtilsTest` (or extend it) covering: well-formed `smb://`/`sftp://`/`ftp://`/`cloud://` with includeCloud true/false; a non-network `/local/path` and `content://`; and for `fileMatchesProtocol` the four slash-mangled forms per protocol + a negative. These lock the union behavior before routing. Verify: `gradlew testStandardDebugUnitTest --tests "*PathUtilsTest*"` PASS.

## Done criteria
- Canonical functions + tests green; behavior of each original site reproducible through them.
