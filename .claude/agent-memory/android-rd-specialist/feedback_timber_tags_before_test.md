---
name: timber-tags-before-test
description: Debug tags are bound to BlockNeedUserTest status - removal happens only as a side effect of leaving that status, never speculatively
type: feedback
---

Do NOT remove a `Timber.d("Sxxxx:` tag while its spec is still in status `BlockNeedUserTest`. The tag is the operator's logcat probe for that round of on-device testing.

**Why:** The tags are the primary tool for verifying code paths in logcat during testing. Removing them before testing leaves the user unable to diagnose problems if the feature misbehaves. (This predates the formal doctrine and motivated it.)

**How to apply:** Since CLAUDE.md "Debug Verification Tags", the tag lifecycle is bound to `BlockNeedUserTest` - tags exist iff the spec is `BlockNeedUserTest`. Insert tags only when a spec moves INTO that status; delete them only as a side effect of it moving OUT (`/spec-check`→Verified, `/spec-update` re-open, `/spec-all` resume, `/spec-arc`, manual `update.ps1 -Status`). Never write a phase step or do an ad-hoc edit that strips a tag from a spec that is currently `BlockNeedUserTest`. "Build passes" is not a substitute for the device test that gates the Verified transition. A tag whose spec is NOT currently `BlockNeedUserTest` is stale and may be removed on sight.
