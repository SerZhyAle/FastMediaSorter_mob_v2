---
name: changing-description-between-post-change-reruns-duplicates-the-row
description: Re-running post-change.ps1 after fixing an advisory writes a SECOND permanent changelog row if the -Description text changed, because the dev-log dedup guard matches on description
metadata:
  type: feedback
---

Re-run `post-change.ps1` with the **byte-identical** `-Description` you used the first time. Change
the file set with `-Files` freely, but do not reword the description.

**Why:** the dev-log guard in `scripts/add_to_dev_log.ps1` is a *recent-duplicate guard keyed on the
description string* (its own comment: "Bypass the recent-duplicate guard .. genuine second
identical-desc change", switch `-AllowDuplicate`). S1622 is usually quoted as "the guard identifies
the change and ignores the size of its file set" - true, but the identity it uses is the description.
Observed 2026-08-23 on S1978: first close named three docs, the registry gate returned an advisory
asking for a fourth sibling, and the re-run said "four wear docs" instead of "three". Two rows landed
in `dev/CHANGELOG.md`, 57 seconds apart, for one change. `dev/CHANGELOG.md` may not be hand-edited
and `add_to_dev_log.ps1` has no remove or amend verb, so the duplicate is permanent.

**How to apply:** when a closure returns `PASS WITH ADVISORIES` and you intend to fix what the
advisory named and re-run, copy the `-Description` verbatim from the first call before editing
anything else. This bites hardest on the `document-registry` advisory, whose whole point is to send
you off to edit sibling files - which is exactly the moment the file count in your description
changes. Write the description without a file count in the first place ("reconcile the wear docs
with the tree", not "reconcile three wear docs") and the trap cannot spring.

Related: [[registry-ack-up-front]].
