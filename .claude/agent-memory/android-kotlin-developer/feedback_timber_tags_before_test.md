---
name: feedback_timber_tags_before_test
description: Debug tags are bound to BlockNeedUserTest status - removal happens only as a side effect of leaving that status, never speculatively
metadata:
  type: feedback
---

Do NOT remove a `Timber.d("Sxxxx:` tag while its spec is still in status `BlockNeedUserTest`. The tag is the operator's logcat probe for that round of on-device testing.

**Why:** The tags are the primary tool for verifying code paths in logcat during testing. Removing them before testing leaves the user unable to diagnose problems if the feature misbehaves. (This predates the formal doctrine and motivated it.)

**How to apply:** When implementing a phase that moves a spec into `BlockNeedUserTest`, insert exactly one `Timber.d("Sxxxx: <short flow description>")` at the entry point of each changed flow - not on every modified line. When in a later session the spec leaves `BlockNeedUserTest` (any status transition), grep `Timber.d\("Sxxxx:` across all `.kt` and delete every matching line, then include the removal in the same commit as the status flip. While the spec is still in `BlockNeedUserTest`, never strip a tag for that id - that is the user's only logcat probe. A tag whose spec is NOT currently `BlockNeedUserTest` is stale and may be removed on sight when you touch the surrounding file.
