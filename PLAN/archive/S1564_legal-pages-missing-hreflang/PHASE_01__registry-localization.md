# Phase 01 - Registry Localization

**Strategic spec:** [`../S1564_legal-pages-missing-hreflang.md`](../S1564_legal-pages-missing-hreflang.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 2 / 2
**Started:** 2026-08-11
**Completed:** 2026-08-11

---

## Objective

Make the legal-downloads language cluster explicit in the registry and keep the read-only suggestion helper executable.

---

## Prerequisites

- [x] Strategic §6 research items blocking this phase are resolved.
- [x] Branch is `DEBUG-v031`.

---

## Files Touched

| File | New / Modified | Line budget |
|---|:---:|---:|
| `docs/DOCUMENT_REGISTRY.jsonl` | Modified | N/A - JSONL record |
| `scripts/document_registry/suggest_localized_urls.ps1` | Modified | ≤ 120 |
| `sitemap.xml` | Generated | N/A - do not edit manually |

---

## Steps

### Step 01.1 - Declare verified privacy-policy URLs

**Files:** `docs/DOCUMENT_REGISTRY.jsonl`, `docs/PRIVACY_POLICY.md`, `docs/PRIVACY_POLICY.ru.md`, `docs/PRIVACY_POLICY.uk.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Set `legal-downloads.localized_urls` to the three exact privacy-policy permalinks from front matter. Do not infer paths from filenames or change legal text.

**Why:**

The sitemap generator emits a full cluster only when the registry provides every localized URL, while the front matter is the reliable public-path source.

**Verification:**

- `docs/DOCUMENT_REGISTRY.jsonl` contains `legal-downloads` with `en`, `ru`, and `uk` localized URLs.
- Each URL equals the corresponding privacy-policy front-matter permalink.

**Status:** `[x]` done

---

### Step 01.2 - Repair locale suggestion parsing

**Files:** `scripts/document_registry/suggest_localized_urls.ps1`
**Depends on:** Step 01.1

**Prompt for developer:**

> Make the helper parse in PowerShell and recognize dotted, underscored, and unsuffixed Markdown locale names. Keep it read-only and preserve its JSON suggestion output.

**Why:**

The helper is allowed to prepare reviewed candidates for future registry records, but it must not invent or write public URLs.

**Verification:**

- `pwsh -NoProfile -File scripts/document_registry/suggest_localized_urls.ps1` exits 0.
- The script contains locale patterns for `.ru.md`, `_ru.md`, `.uk.md`, `_uk.md`, and unsuffixed `.md`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] `pwsh -NoProfile -File scripts/document_registry/validate.ps1` exits 0.
- [x] Generated sitemap contains the three privacy-policy URLs with an x-default alternate.

---

## Handoff Notes to Next Phase

The registry is the only sitemap input; regenerate generated views rather than editing them.

---

## Rollback Plan

Revert the registry record and helper edit; no data migration or application surface changed.
