# Phase 04 - Core strings: feature surfaces

**Strategic spec:** [`../S1420_locale-bulk-translation-remaining-tranches.md`](../S1420_locale-bulk-translation-remaining-tranches.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** Phase 05
**Steps done:** 4 / 4
**Started:** 2026-08-11
**Completed:** 2026-08-11

---

## Objective

Translate the `strings.xml` prefixes that belong to named feature areas - streams, camera, screenshots, gestures, sharing and the companion surfaces.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values-{ar,bn,de,es,fr,hi,it,pt,ur}/strings.xml` | Modified | n/a - generated |
| `app_v2/src/main/res/values-b+zh+Hans/strings.xml` | Modified | n/a - generated |

---

## Tranche procedure

As defined in [PHASE_02 "Tranche procedure"](PHASE_02__setup-screen-completion.md#tranche-procedure).

---

## Steps

### Step 04.1 - `streams_` and `stream_` prefixes

**Files:** `app_v2/src/main/res/values-*/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Run the tranche procedure for `-KeyPrefix streams_` (127 keys) and `-KeyPrefix stream_` (54 keys). Channel names, station names and other proper nouns inside these values stay untranslated - translate the surrounding UI text only.

**Why:**

Strategic §5 orders `strings.xml` by user visibility, and the stream catalogue is a top-level destination rather than a setting reached through a menu.

**Verification:**

- Keys per locale: `streams_` expected 127, actual 127; `stream_` expected 54, actual 54. Both hold in all ten.
- Seeder exit code per locale per prefix: expected 0, actual 0 - `rejected 0` throughout. File totals stepped 701 -> 828 -> 882.
- Phase 03 prefixes still at their expected counts - verified by recomputing every eligible key of both prefixes from `values/strings.xml` after the last write and finding it present in all ten locale files, `missing=0` ten times. A count-only check cannot tell a closed prefix from one that gained a key after its tranche was dumped, which is how Phase 03 caught a mid-phase corpus change.

**Status:** `[x]` done

**Quoting decision recorded here because the maps diverged on it:** the English source stores the station name wrapped in `&quot;`, which decodes to a raw `"` in the shipped resource. A literal `"` in a map therefore reproduces the English XML byte for byte, and the seeder's escaping pass is what puts the entity back. `de`, `fr`, `pt`, `hi`, `ur` did that; `es`, `it`, `ar`, `zh-Hans` used their own locale's quotation marks instead. Both are correct - the second is idiomatic and the first is proven by the English source already building - so neither was rewritten.

---

### Step 04.2 - `camera_` and `screenshot_` prefixes

**Files:** `app_v2/src/main/res/values-*/strings.xml`
**Depends on:** Step 04.1

**Prompt for developer:**

> Run the tranche procedure for `-KeyPrefix camera_` (79 keys) and `-KeyPrefix screenshot_` (45 keys).

**Why:**

Strategic §7 mitigates the half-translated screen by closing a surface in one tranche, and these two prefixes each back a full capture screen.

**Verification:**

- Keys per locale: `camera_` expected 79, actual 79; `screenshot_` expected 45, actual 45. Both hold in all ten.
- Seeder exit code per locale per prefix: expected 0, actual 0 - `rejected 0` throughout. File totals stepped 882 -> 961 -> 1006.
- Step 04.1 prefixes unchanged - all 305 keys of steps 04.1 and 04.2 recomputed from `values/strings.xml` and found present in every locale file, `missing=0` ten times.

**Status:** `[x]` done

**Known limitation, recorded rather than fixed:** `screenshot_accessibility_permission_rationale` instructs the user by quoting Android's own Settings labels - "Controlled by restricted setting", "App was denied access", "App info", "Allow restricted settings". Those four are system strings this app does not own, so a translation can only guess the wording the OS shows in that locale. Eight locales translated them into the AOSP wording they believed correct; `ur` deliberately left them in English so the user can still match them on screen. Neither choice is verifiable without a device per locale, and both fail safe - the surrounding instruction stays readable either way. Not a defect of this tranche, and not worth a ticket while the strings remain outside the app's control.

---

### Step 04.3 - `statistics_`, `gesture_` and `tooltip_` prefixes

**Files:** `app_v2/src/main/res/values-*/strings.xml`
**Depends on:** Step 04.2

**Prompt for developer:**

> Run the tranche procedure for `-KeyPrefix statistics_` (75 keys), `-KeyPrefix gesture_` (53) and `-KeyPrefix tooltip_` (66). Tooltip text is read by the screen reader as well as shown, so keep it a complete phrase rather than a truncated label.

**Why:**

Strategic §3.3 records that accessibility text is translated in the same tranche as the visible text of its surface, with no separate pass.

**Verification:**

- Keys per locale: `statistics_` expected 75, actual 75; `gesture_` expected 53, actual 53; `tooltip_` expected 66, actual 66. All three hold in all ten.
- Seeder exit code per locale per prefix: expected 0, actual 0 - `rejected 0` throughout, 30 runs. File totals stepped 1006 -> 1200.
- Steps 04.1-04.2 prefixes unchanged - every eligible key of all fifteen closed prefixes recomputed from `values/strings.xml` after the last write and found present in all ten locale files, `missing=0` ten times.

**Status:** `[x]` done

**Drift closed inside this step rather than deferred:** the recompute check that Phase 03 introduced fired for real here - `launcher_` had grown from the 236 keys Phase 03 dumped to 249, so thirteen English keys existed with no translation in any of the ten locales. The set is one coherent addition - the share-a-place launcher action, two new launcher actions, and the altitude and satellite gadget readouts. Translated and merge-seeded in the same step, because leaving them would have meant Phase 04 closing over a launcher surface that had silently reopened. Final total per locale is therefore 1213, not the 1200 this phase planned; the extra 13 are Phase 03's, not this phase's. This is the second time the corpus moved mid-plan, which is why the check is a recompute and not a count.

---

### Step 04.4 - `share_`, `link_`, `browse_`, `wear_` and `companion_` prefixes

**Files:** `app_v2/src/main/res/values-*/strings.xml`
**Depends on:** Step 04.3

**Prompt for developer:**

> Run the tranche procedure for `-KeyPrefix share_` (32 keys), `-KeyPrefix link_` (42), `-KeyPrefix browse_` (25), `-KeyPrefix wear_` (25) and `-KeyPrefix companion_` (21).

**Why:**

Strategic §5 orders by visibility and these five prefixes close the last of the named feature destinations before the plan moves on to settings and the long tail.

**Verification:**

- Keys per locale: `share_` expected 32, actual 32; `link_` 42/42; `browse_` 25/25; `wear_` 25/25; `companion_` 21/21. All five hold in all ten.
- Seeder exit code per locale per prefix: expected 0, actual 0 - `rejected 0` throughout, 50 runs.
- Every prefix from phases 03-04 still at its expected count - all 1362 eligible keys of the twenty closed prefixes recomputed from `values/strings.xml` and found present in every locale file, `missing=0` and `extra=0` ten times.

**Status:** `[x]` done

**Maps were checked before seeding, not after:** [`evidence/verify-maps.ps1`](evidence/verify-maps.ps1) ran over all fifty maps first and returned `PASS`, exit 0. It is worth doing in that order because the seeder reports a rejection one locale at a time and only after it has already rewritten that locale's file, so a bad tranche would be half seeded before the first complaint. Ten values came back byte-identical to English and were read rather than fixed: `Audio`, `Video`, `Text`, `Images`, `Email` are the correct word in the target language, and `de` kept `Wear Companion` as a product name.

**Second drift, same cause as the first:** four more `launcher_` keys - the `launcher_gadget_search_*` set - appeared in the English source while this step's translations were being written, taking the prefix from 249 to 253. Translated and merge-seeded before the phase closed. The per-locale total is therefore 1362 rather than the 1345 this phase planned: 1213 at the end of step 04.3, plus this step's 145, plus these 4. The corpus has now moved twice inside one phase because another ticket is adding launcher gadget strings on the same tree, which is the argument for keeping the closing check a recompute against the live source rather than a stored count.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fr` (resources/manifest) BUILD SUCCESSFUL in 13s, exit 0. That is the ladder rung for a resource-only change: AAPT2 parsed and merged all ten locale files, which is exactly what these edits can break.
- [x] Each `values-*/strings.xml` holds 1362 keys, not the 1345 planned.

> Per-prefix counts corrected against the seeder's own `-DumpSource` on 2026-08-11, per the INDEX rule that the seeder is the authority: `camera_` 79 not 77, `statistics_` 75 not 76, `share_` 32 not 33. The three deltas cancel, so the phase total stays 644; the Phase 03 base is 701, which is the figure that phase actually closed on rather than the 703 planned here.
>
> The final 1362 is 1345 plus the seventeen `launcher_` keys this phase inherited: thirteen found by the step 04.3 recompute and four more by the step 04.4 one. They belong to Phase 03's prefix, not to this phase's work, and are counted here only because this is where they were closed.
- [x] No file under `values/`, `values-ru/` or `values-uk/` modified by this phase. Those three files are dirty in the working tree, but the change is another session's: they carry the same `launcher_gadget_*` keys it is adding, and the seeder was only ever invoked with the ten best-effort locales. It writes exclusively to the directory `Get-LocaleResourceDir` returns for the `-Locale` it is given, so it cannot reach a locale that was not named.
