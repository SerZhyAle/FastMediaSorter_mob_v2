# Phase 06 — README IzzyOnDroid badge (EN / RU / UK)

**Strategic spec:** [`../S0215_fdroid-publish-research.md`](../S0215_fdroid-publish-research.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 05
**Blocks:** Phase 07
**Steps done:** 4 / 4
**Started:** 2026-05-16
**Completed:** 2026-05-16

---

## Objective

Add the IzzyOnDroid "Get it on IzzyOnDroid" badge with a deep-link to the application page in all three READMEs (`README.md`, `docs/README_RU.md`, `docs/README_UK.md`). The link is the canonical IzzyOnDroid permalink, which becomes live once the recipe submitted in Phase 05 is accepted.

---

## Prerequisites

- [x] Phase 05 ✅ Done (submission issue opened).
- [ ] IzzyOnDroid deep-link confirmed available — canonical form: `https://apt.izzysoft.de/fdroid/index/apk/<applicationId>`. For this project: `https://apt.izzysoft.de/fdroid/index/apk/com.sza.fastmediasorter`.
- [ ] Badge image — IzzyOnDroid provides an official SVG/PNG badge at `https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png` (or equivalent path; verify current URL in step 06.1).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `README.md` | Modified | +2..4 lines for the badge block |
| `docs/README_RU.md` | Modified | +2..4 lines for the badge block |
| `docs/README_UK.md` | Modified | +2..4 lines for the badge block |

---

## Steps

### Step 06.1 — Confirm canonical IzzyOnDroid badge image URL

**Files:** none — verification step
**Depends on:** — start of phase

**Prompt for developer:**

> Verify the current canonical IzzyOnDroid badge image URL. Check (in order):
>
> 1. `https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png` — historical path.
> 2. `https://apt.izzysoft.de/badge/IzzyOnDroid.png` — alternative.
> 3. Official IzzyOnDroid docs at `https://gitlab.com/IzzyOnDroid/repo/-/wikis/home` for the current recommended URL.
>
> Use `curl -sI <url>` to confirm the URL returns `200 OK` and `content-type: image/...`. Use the first URL that responds successfully in steps 06.2 / 06.3 / 06.4. If none respond, record the failure and use an `https://img.shields.io/badge/` fallback badge styled as IzzyOnDroid.

**Verification:**

- `Bash` — `curl -sI <chosen-url> | head -1` returns `HTTP/.. 200 OK` (or `HTTP/2 200`).
- `Bash` — `curl -sI <chosen-url> | grep -i content-type` mentions `image`.
- expected: HTTP 200, content-type image | actual: response captured.

**Status:** `[x] done`

**Step Log:**

- 2026-05-16 — `curl -sI https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png` → HTTP/1.1 200 OK, content-type: image/png. Alternative `https://apt.izzysoft.de/badge/IzzyOnDroid.png` → 404. expected: HTTP 200, content-type image | actual: PASS (primary URL).

---

### Step 06.2 — Add badge to `README.md` (English)

**Files:** `README.md`
**Depends on:** Step 06.1

**Prompt for developer:**

> Open `README.md`. Locate the section right after `**📖 Other Languages:**` line (around line 6). Insert a new "Download" section immediately after that line, before `## About the Project`:
>
> ```markdown
>
> **📦 Download:** [<img src="<badge-image-url>" alt="Get it on IzzyOnDroid" height="56">](https://apt.izzysoft.de/fdroid/index/apk/com.sza.fastmediasorter)
>
> ```
>
> Replace `<badge-image-url>` with the URL confirmed in Step 06.1. Use HTML `<img>` (not pure markdown) so the `height="56"` constraint renders consistently. The badge becomes live once IzzyOnDroid accepts the recipe submitted in Phase 05.

**Verification:**

- `Grep` — `Get it on IzzyOnDroid` matches exactly once in `README.md`.
- `Grep` — `apt.izzysoft.de/fdroid/index/apk/com.sza.fastmediasorter` matches exactly once in `README.md`.
- `Grep` — `height="56"` matches exactly once in `README.md`.
- expected: badge present in the download section | actual: grep result.

**Status:** `[x] done`

**Step Log:**

- 2026-05-16 — Badge inserted after "Other Languages" line. Grep confirms: `Get it on IzzyOnDroid` = 1, `apt.izzysoft.de/fdroid/index/apk/com.sza.fastmediasorter` = 1, `height="56"` = 1. expected: all 3 markers present | actual: PASS.

---

### Step 06.3 — Add badge to `docs/README_RU.md` (Russian)

**Files:** `docs/README_RU.md`
**Depends on:** Step 06.2

**Prompt for developer:**

> Open `docs/README_RU.md`. Insert the same badge block in the corresponding location (after the languages line, before `## О проекте` or equivalent first content heading). Translate the inline caption "📦 Download:" → "📦 Скачать:" — the rest of the HTML stays identical. Author style: `..` not `...`, `ё`/`Ё` enforced. The COMMUNICATION_POLICY_RU §6 checklist applies to the caption text.

**Verification:**

- `Grep` — `Get it on IzzyOnDroid` matches exactly once in `docs/README_RU.md`.
- `Grep` — `apt.izzysoft.de/fdroid/index/apk/com.sza.fastmediasorter` matches exactly once in `docs/README_RU.md`.
- `Grep` — `Скачать` matches at least once in `docs/README_RU.md` (badge caption).
- expected: badge present with localized caption | actual: grep result.

**Status:** `[x] done`

**Step Log:**

- 2026-05-16 — Badge inserted with `📦 Скачать:` caption. Grep confirms: `Get it on IzzyOnDroid` = 1, `apt.izzysoft.de` = 1, `Скачать` = 1. expected: badge present with localized caption | actual: PASS.

---

### Step 06.4 — Add badge to `docs/README_UK.md` (Ukrainian)

**Files:** `docs/README_UK.md`
**Depends on:** Step 06.3

**Prompt for developer:**

> Open `docs/README_UK.md`. Insert the same badge block. Translate the inline caption "📦 Download:" → "📦 Завантажити:". COMMUNICATION_POLICY_UK §6 applies. Use `..` not `...`.

**Verification:**

- `Grep` — `Get it on IzzyOnDroid` matches exactly once in `docs/README_UK.md`.
- `Grep` — `apt.izzysoft.de/fdroid/index/apk/com.sza.fastmediasorter` matches exactly once in `docs/README_UK.md`.
- `Grep` — `Завантажити` matches at least once in `docs/README_UK.md`.
- expected: badge present with localized caption | actual: grep result.

**Status:** `[x] done`

**Step Log:**

- 2026-05-16 — Badge inserted with `📦 Завантажити:` caption. Grep confirms: `Get it on IzzyOnDroid` = 1, `apt.izzysoft.de` = 1, `Завантажити` = 1. expected: badge present with localized caption | actual: PASS.

---

## Phase Done Criteria

- [x] Every `Step 06.*` above is `[x] done`.
- [x] All three READMEs contain the badge linking to `apt.izzysoft.de/fdroid/index/apk/com.sza.fastmediasorter`.
- [x] Captions localized: EN `Download`, RU `Скачать`, UK `Завантажити`.
- [x] Dev log entries added for `README.md`, `docs/README_RU.md`, `docs/README_UK.md`.

---

## Handoff Notes to Next Phase

Phase 07 (cleanup) is the final phase. It verifies catalogue stability (no `.kt` changes in this entire tactical plan), runs the post-change scripts, and confirms the Completion Gate items in `INDEX.md`.

---

## Rollback Plan

Revert phase commit — badge blocks disappear from all three READMEs. No runtime code, no contract changes.
