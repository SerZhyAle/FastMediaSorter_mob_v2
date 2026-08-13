# Phase 02 - Layout XML Source

**Strategic spec:** [`../S0284_settings-search-auto-index.md`](../S0284_settings-search-auto-index.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 3 / 3
**Started:** 2026-05-21
**Completed:** 2026-05-21

---

## Objective

Implement the runtime layout XML scanner: `LayoutSettingsSearchSource` walks every settings-fragment layout and emits one `RawSettingsSearchEntry` per discovered settings row, button, header, or input field.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Strategic §6 Research items #1 (runtime vs buildtime), #2 (atypical headline fallback), #3 (help-popup inclusion), #4 (action button inclusion) are all `Resolved` — the defaults baked into this phase are: runtime via `Resources.getXml`, `android:hint` as fallback title, help-popup text excluded from pool, action buttons included.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/search/LayoutSettingsSearchSource.kt` | New | ≤ 250 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/search/SettingsSearchLayoutCatalog.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/search/XmlAttributeReader.kt` | New | ≤ 100 |

---

## Steps

### Step 02.1 - Add layout catalog (list of layout resIds to scan)

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/search/SettingsSearchLayoutCatalog.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create an object `SettingsSearchLayoutCatalog` exposing `val layoutResIds: List<Int>` containing every layout that `LayoutSettingsSearchSource` must scan. Required entries (must match `SettingsSearchTabMapping.assignmentFor()` keys): `R.layout.fragment_settings_general`, `R.layout.fragment_settings_playback`, `R.layout.fragment_settings_images`, `R.layout.fragment_settings_video`, `R.layout.fragment_settings_audio`, `R.layout.fragment_settings_documents`, `R.layout.fragment_settings_other`, `R.layout.fragment_settings_destinations`, `R.layout.fragment_settings_backup_restore`. Do NOT include `fragment_settings_media_container` — it is a tab host with no settings rows of its own. Add a one-line KDoc comment at the top of the object explaining the source-of-truth role and that any new settings fragment layout must be appended here.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/search/SettingsSearchLayoutCatalog.kt` exists.
- `Grep` - `object SettingsSearchLayoutCatalog` matches exactly once.
- `Grep` - `val layoutResIds: List<Int>` matches exactly once.
- `Grep` - `R.layout.fragment_settings_media_container` does NOT appear (verified by zero hits for that token in the file).
- `Grep` count - exactly 9 `R.layout.fragment_settings_` references in the file.

**Status:** `[x]` done

**Step Log:**

- 2026-05-21 - Verification 5/5 PASS. Files: app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/search/SettingsSearchLayoutCatalog.kt (+25 LOC).

---

### Step 02.2 - Add `XmlAttributeReader` helper

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/search/XmlAttributeReader.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Create a small utility object `XmlAttributeReader` wrapping `XmlPullParser` attribute reads for our specific needs. Functions:
> - `fun attrResourceValue(parser: XmlPullParser, namespace: String?, name: String): Int?` — returns the resource id behind `@string/xxx` references via `parser.getAttributeResourceValue(...)`, or `null` if the attribute is missing or not a reference.
> - `fun attrStringValue(parser: XmlPullParser, namespace: String?, name: String): String?` — returns the literal string value (for inline `"text"` attributes), or `null` if missing or if the value is a `@string/...` reference (callers should prefer `attrResourceValue` then fall back here).
> - `fun attrId(parser: XmlPullParser): Int?` — returns the view's `android:id` resource id, or `null` if no id is declared.
>
> Use constants for the two namespaces: `ANDROID_NS = "http://schemas.android.com/apk/res/android"` and `APP_NS = "http://schemas.android.com/apk/res-auto"`. Keep the file dependency-free apart from `org.xmlpull.v1.XmlPullParser`.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/search/XmlAttributeReader.kt` exists.
- `Grep` - `object XmlAttributeReader` matches exactly once.
- `Grep` - `fun attrResourceValue` matches exactly once.
- `Grep` - `fun attrStringValue` matches exactly once.
- `Grep` - `fun attrId` matches exactly once.
- `Grep` - `ANDROID_NS` and `APP_NS` both present as constants.

**Status:** `[x]` done

**Step Log:**

- 2026-05-21 - Verification 6/6 PASS. Files: app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/search/XmlAttributeReader.kt (+46 LOC).
- 2026-05-21 - FIX: phase prompt said `org.xmlpull.v1.XmlPullParser`, but `getAttributeResourceValue` lives on `AttributeSet`; on Android the concrete return type of `Resources.getXml()` is `android.content.res.XmlResourceParser` (implements both). Parameter type changed to `XmlResourceParser`. Re-verified: 6/6 PASS, build SUCCESS.

---

### Step 02.3 - Implement `LayoutSettingsSearchSource`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/search/LayoutSettingsSearchSource.kt`
**Depends on:** Step 02.1, Step 02.2

**Prompt for developer:**

> Create a class `LayoutSettingsSearchSource @Inject constructor(@ApplicationContext private val context: Context)` implementing `SettingsSearchSource`. Implement `override fun collect(): List<RawSettingsSearchEntry>` as follows:
>
> 1. Iterate `SettingsSearchLayoutCatalog.layoutResIds`. For each layout resId, open `context.resources.getXml(layoutResId)` as an `XmlPullParser`. Wrap each layout in try/catch on `XmlPullParserException` and `IOException` — log via `Timber.w` and skip that layout silently (do not crash the whole index over one broken layout).
> 2. Walk parser events. On each `XmlPullParser.START_TAG`:
>    - Read `XmlAttributeReader.attrId(parser)`. If `null`, skip — only id'd views are searchable.
>    - Determine `kind: EntryKind` from the tag name suffix:
>      - `SettingsToggleRow` → `EntryKind.TOGGLE_ROW`
>      - `CollapsibleSectionHeader` → `EntryKind.SECTION_HEADER`
>      - `*Button` / `MaterialButton` / `ImageButton` → `EntryKind.BUTTON`
>      - `TextInputEditText` / `EditText` → `EntryKind.TEXT_INPUT`
>      - `AutoCompleteTextView` / `MaterialAutoCompleteTextView` / `Spinner` → `EntryKind.SPINNER`
>      - any other tag → skip (not a searchable surface; container views are filtered out by lack of recognized kind).
>    - Read source attributes based on `kind`:
>      - `TOGGLE_ROW`: `titleResId` from `app:str_title` (resource value), fallback to `attrStringValue` for inline; `subtitleResId` from `app:str_subtitle`.
>      - `SECTION_HEADER`: `titleResId` from `app:csh_title`; no subtitle.
>      - `BUTTON`: `titleResId` from `android:text` (resource value), fallback inline.
>      - `TEXT_INPUT` / `SPINNER`: `titleResId` from `android:hint` (resource value), fallback inline; `hintResId` mirrors the same for clarity.
>    - Build a `RawSettingsSearchEntry` with `viewId`, `layoutResId`, `kind`, and the title/subtitle/hint resource references collected above. If all title/subtitle/hint slots are null AND the kind is not `TOGGLE_ROW`/`SECTION_HEADER`/`BUTTON`/`TEXT_INPUT`/`SPINNER`, skip the entry (defensive — collector in Phase 03 also filters).
> 3. Aggregate entries across all layouts into a single `List<RawSettingsSearchEntry>` and return it.
>
> Constraints:
> - Do NOT inflate any view — only XML token parsing. Inflation would cost orders of magnitude more time and pull in unrelated side effects.
> - Do NOT recurse into `<include>` or `<merge>` tags in this phase — settings fragments do not use them for row content (verified during research). If a future settings layout adds `<include>`, a follow-up spec extends the source.
> - Do NOT extract `app:str_helpTitle` or `app:str_helpMessage` — these are help-popup texts and are excluded from the index by strategic §6.3 decision (presumed; confirm during research-item resolution).
> - Logging: emit one `Timber.d("S0284: scanned <layoutName> -> <count> entries")` per layout for verification during BlockNeedUserTest. The S0284 prefix is added in Phase 04 when the spec moves into BlockNeedUserTest; for this phase use a placeholder `Timber.d("Settings search scan: <layoutName> -> <count> entries")` and Phase 04 swaps the prefix.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/search/LayoutSettingsSearchSource.kt` exists.
- `Grep` - `class LayoutSettingsSearchSource` matches exactly once.
- `Grep` - `: SettingsSearchSource` present in class header.
- `Grep` - `override fun collect(): List<RawSettingsSearchEntry>` matches exactly once.
- `Grep` - `Log\.d\(` returns zero hits (Timber-only rule).
- `Grep` - `getXml(` (any whitespace) matches at least once.
- `Grep` - `app:str_helpTitle` does NOT appear (help-popup text exclusion).
- `Grep` - `csh_title` matches at least once.

**Status:** `[x]` done

**Step Log:**

- 2026-05-21 - Verification 8/8 PASS (`app:str_helpTitle` = 0 confirms help-popup exclusion). Files: app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/search/LayoutSettingsSearchSource.kt (+162 LOC).
- 2026-05-21 - FIX: aligned parser variable + helper-call sites to `XmlResourceParser` after the same compile-error fix in 02.2. `XmlPullParser` import retained for `XmlPullParser.START_TAG` / `END_DOCUMENT` constants. Re-verified: 8/8 PASS, build SUCCESS.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

- `LayoutSettingsSearchSource` produces the raw entries. It is the only thing in the pipeline that touches XML.
- Phase 03 takes raw entries (with `titleResId`/`subtitleResId` references) and resolves them across 3 locales.
- Decisions baked into this phase (presumed pending research-item resolution): help-popup text excluded, buttons included, `android:hint` as fallback title for hint-only fields.

---

## Rollback Plan

Revert phase commit - the source has no consumer yet (Phase 04 wires it in). No data migration, no user-facing surface changed.
