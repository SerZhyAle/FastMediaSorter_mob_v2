# Phase 01 - Channel identity derivation

**Strategic spec:** [`../S1832_stable-channel-identity-survives-prune.md`](../S1832_stable-channel-identity-survives-prune.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** 2 / 2
**Started:** 2026-08-20
**Completed:** -

---

## Objective

Introduce `StreamChannelIdentity`, the single function that turns a channel address into the key every
kind of user data will be filed under. No storage, no callers, no schema yet.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done - none.
- [ ] Strategic §6 research items blocking this phase are Resolved - both are.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/util/StreamChannelIdentity.kt` | New | ≤ 60 |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/util/StreamChannelIdentityTest.kt` | New | ≤ 160 |

---

## Steps

### Step 01.1 - Add `StreamChannelIdentity`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/util/StreamChannelIdentity.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `object StreamChannelIdentity` with a single public function `of(url: String): String`. It
> delegates to `StreamUrlNormalizer.normalize(url)` for the four rules already shipped there - scheme
> case, host case, trailing slash, default port - and then applies the one rule this ticket adds: an
> `http` or `https` scheme is replaced by the single token `web`, so the two protocols produce one key.
> Every other scheme, `rtsp` included, is left exactly as `normalize` returned it. Do not modify
> `StreamUrlNormalizer`: its output is the key `stream_quality_memory` rows are already filed under, and
> changing it would silently invalidate every learned rung on every device.
> Write a KDoc that states what the key is for and names the two rules it must never acquire without a
> fresh bank measurement.

**Why:**

Strategic ADR-2 as revised limits normalization to the four shipped rules plus the protocol fold, and
research artifact 02 measured that the fold is the only rule of the five that changes anything the
shipped normalizer does not already handle; without it strategic goal 2 delivers nothing new.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/data/util/StreamChannelIdentity.kt` exists.
- `Grep` - `object StreamChannelIdentity` matches exactly once in that file.
- `Grep` - `fun of(url: String): String` present.
- `Grep` - `StreamUrlNormalizer.normalize` present in that file.
- `Grep` - `data/util/StreamUrlNormalizer.kt` is unchanged against HEAD.

**Status:** `[x]` done

---

### Step 01.2 - Cover the derivation with unit tests

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/data/util/StreamChannelIdentityTest.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add a JUnit test class asserting, one test per claim: `http` and `https` on the same host and path
> produce the same key; an `rtsp` address keeps its own scheme and does not collide with an `http`
> address that differs only by scheme; a trailing slash, an upper-case host and an explicit default port
> all fold, matching what `StreamUrlNormalizerTest` already asserts for `normalize`; a path's case is
> preserved; and an unparsable address still yields a key of its own rather than an empty string. Include
> one test built from a real collision pair recorded in research artifact 02 -
> `http://dispatcher.rndfnk.com/rbb/fritz/live/mp3/mid` against its `https` twin - so the measurement
> that justified the rule is pinned by a test rather than by prose.

**Why:**

Strategic §7 names a mis-normalization that merges two genuinely different channels as a loss identical
to the one the ticket exists to prevent, so the boundary of the rule - what folds and what must not -
has to be executable rather than described.

**Verification:**

- `Glob` - `app_v2/src/test/java/com/sza/fastmediasorter/data/util/StreamChannelIdentityTest.kt` exists.
- `Grep` - `class StreamChannelIdentityTest` matches exactly once.
- `Grep` - `rndfnk` present, pinning the measured pair.
- `Grep` - `rtsp` present, pinning the non-folding case.
- `.\a.ps1 fu` - `StreamChannelIdentityTest` passes.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] `Grep -n "Log\.d\("` returns zero hits in both files touched.
- [ ] Dev log entry added via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated - a new public object was added.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

`StreamChannelIdentity.of` is the one derivation. Phase 02's migration must call this exact function to
backfill, and no phase may reimplement the rule inline - a second copy would drift from the first and
reissue every key the day it did.

---

## Rollback Plan

Revert the phase commit - two new files, no caller, no schema, no data.
