# Phase 01 — Known auth resources registry

**Strategic spec:** [`../S0144_fix-link-download-auth-ux.md`](../S0144_fix-link-download-auth-ux.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 03, Phase 05
**Steps done:** 1 / 1
**Started:** 2026-05-10
**Completed:** 2026-05-10

---

## Objective

Introduce a static, in-build catalog of known social-media resources (display name, base host, canonical web login URL) plus a host-matching helper. No UI, no DI module — plain Kotlin object consumed later by the picker (Phase 03) and the share-offer flow (Phase 05).

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/link/auth/KnownAuthResources.kt` | New | ≤ 120 |

---

## Steps

### Step 01.1 — Create `KnownAuthResources` catalog

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/link/auth/KnownAuthResources.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create `data class KnownAuthResource(val displayName: String, val host: String, val loginUrl: String)` and a Kotlin `object KnownAuthResources` exposing `val all: List<KnownAuthResource>` with at least these ten entries (brand names are proper nouns — keep them as literals, do NOT add string resources for them): Instagram (`instagram.com` → `https://www.instagram.com/accounts/login/`), Pinterest (`pinterest.com` → `https://www.pinterest.com/login/`), TikTok (`tiktok.com` → `https://www.tiktok.com/login`), X / Twitter (`x.com` → `https://x.com/login`), DeviantArt (`deviantart.com` → `https://www.deviantart.com/users/login`), Threads (`threads.net` → `https://www.threads.net/login`), Reddit (`reddit.com` → `https://www.reddit.com/login/`), Tumblr (`tumblr.com` → `https://www.tumblr.com/login`), Flickr (`flickr.com` → `https://identity.flickr.com/login`), ArtStation (`artstation.com` → `https://www.artstation.com/users/sign_in`). Add `fun matchHost(host: String?): KnownAuthResource?` that lowercases the input, strips a leading `www.`, and returns the entry whose `host` equals the input or is a dot-suffix of it (so `www.instagram.com` and `m.instagram.com` both match `instagram.com`). Add a one-line KDoc referencing S0116 and S0144. No Hilt annotations — it is a stateless object.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/data/link/auth/KnownAuthResources.kt` exists.
- `Grep` — `object KnownAuthResources` matches exactly once.
- `Grep` — `data class KnownAuthResource` matches exactly once.
- `Grep` — `fun matchHost(` present.
- `Grep -c` — `KnownAuthResource(` (constructor call lines) ≥ 10.
- `Grep -n "Log\.d\("` — zero hits in the file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-10 — Verification 6/6 PASS (object×1, data class×1, matchHost×1, ctor calls 11≥10, Log.d 0). Files: data/link/auth/KnownAuthResources.kt (+48 LOC, new). Dev log recorded.

---

## Phase Done Criteria

- [x] Step 01.1 is `[x] done`.
- [x] Project compiles — `build-debug.PS1` → BUILD SUCCESSFUL (2026-05-10, v2.60.5101.625).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for the new file.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated; `KnownAuthResources` present, role/status set.

---

## Handoff Notes to Next Phase

`KnownAuthResources.all` is the single source of truth for the picker list (Phase 03) and the share-offer host match (Phase 05). `matchHost` is the only entry point for host → resource resolution — do not re-implement matching elsewhere.

---

## Rollback Plan

Revert phase commit — new file only, no data migration or user-facing surface.
