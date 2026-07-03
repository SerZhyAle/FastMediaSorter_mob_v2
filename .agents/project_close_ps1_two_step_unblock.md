---
name: close-ps1-two-step-unblock
description: close.ps1 / close-and-log.ps1 refuse a direct BlockNeedUserTest->Verified flip ("Unblock first"); update to the pre-block status, then Verified
metadata:
  type: project
---

`close.ps1` (and `close-and-log.ps1`, which wraps it) **refuse a direct transition from any `Block*` status to `Verified`**. The error is `close.ps1 exited 1` with `[status] Unblock first: update.ps1 -Id <id> -Status <previous>`.

**Why:** closing wants to clear the block note via a clean unblock step first; a `Block*` source status is rejected by the close guard.

**How to apply:** to finalize a `BlockNeedUserTest` ticket to `Verified` (e.g. after a passing device test in `/spec-sweep` / `/spec-check`), do it in two `update.ps1` steps - first to the pre-block status (usually `Implemented`, which also clears the block note), then to `Verified`:

```
pwsh -NoProfile -Command "& { ./scripts/spec_catalog/update.ps1 -Id Sxxxx -Status Implemented; ./scripts/spec_catalog/update.ps1 -Id Sxxxx -Status Verified }"
```

Then run the dev-log separately. Pair the flip with the debug-tag removal (tag exists iff `BlockNeedUserTest`). If `close-and-log` is wanted for the dev-log/catalog chaining, it still trips this guard on the Block* source - prefer the two-step `update.ps1` + a separate `add_to_dev_log.ps1`.
