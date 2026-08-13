# S1187 - Settings-dump diagnostic banner prints obfuscated field names in a minified release build

**Status:** Archived
**Priority:** 40
**Created:** 2026-07-25

## 0. Captured material (verbatim)

Found via `/log-reader` batch analysis of 5 device logs (real device `ums512_1h10_Natv`, Android 14 API 29, `standard` release flavor), 2026-07-25.

`logs/fastmediasorter_20260720_204350.log`, `..20260722_102754.log`, `..20260724_102519.log` (app version `2.60.7070.937`) all print the `FAST MEDIA SORTER V2 - SETTINGS DUMP` banner with full field names:

```
acceptSharedFiles                   : true
allFiles                            : false
allowDelete                         : true
..
```

`logs/fastmediasorter_20260724_175804.log` (same device, later app version `2.60.7191.740`, also `Build type: release`) prints the same banner with single/double-letter field names instead:

```
A                                   : true
A0                                  : CYRILLIC
A1                                  : true
A2                                  : false
B                                   : 1024
B0                                  : RANDOM
..
```

Values are correct and in the same order; only the label lost its meaning.

## 1. Root cause (read from code, not yet fixed)

`FastMediaSorterApp.logSettingsInfo()` (`app_v2/src/main/java/com/sza/fastmediasorter/FastMediaSorterApp.kt:622-654`) builds this banner via raw Java reflection over `AppSettings.javaClass.declaredFields`, printing `field.name` directly - by design, so a new `AppSettings` property auto-appears in the dump without touching this method.

`app_v2/proguard-rules.pro` has no keep rule for `AppSettings` (or its field names). In a minified build where R8 renames fields, `field.name` returns the obfuscated name, and the whole point of the dump - a human-readable settings snapshot for support/diagnostic log reading - is defeated.

Version `2.60.7070.937` (2026-07-20/22/24 morning) does not show this - names are intact. Version `2.60.7191.740` (2026-07-24 late) does. Something changed between these builds (an R8/keep-rule config edit, or `AppSettings` moved into a differently-shrunk module) that started actually renaming these fields; not yet identified which.

## 2. Why parked, not fixed inline

- Unrelated to whatever ticket produced these logs (device was running ordinary stream playback, not a settings-screen change).
- Needs research: pin down which config change (between 7070 and 7191) started obfuscating `AppSettings`, decide the right keep rule (`-keepclassmembernames class com.sza.fastmediasorter.domain.model.AppSettings { *; }` or similar, scoped narrowly to avoid re-inflating the R8 shrink footprint), and verify on an actual minified build afterward (CLAUDE.md §13 "R8 / minified proof").
- This is a documented diagnostic tool other workflows rely on (`/log-reader` itself parses this banner for every support/device-log read) - worth its own ticket rather than a one-line guess.

## 3. What to verify once picked up

- Which build/commit between `2.60.7070.937` and `2.60.7191.740` changed minification behavior for `AppSettings`.
- A keep rule restores full field names in a fresh minified `standard release` (or noLegal release) build's `logSettingsInfo()` output.
- No unrelated increase in APK size / newly-kept classes beyond `AppSettings` fields.

### 3.3 Owner inputs (Approval gate)

- **Requested mode:** Owner requested implementation of S1187 on 2026-07-25.
- **Scope:** Modify only the release minification contract for `AppSettings` and the S1187 specification artifacts.
- **Success signal:** A minified standard release preserves readable `AppSettings` field names in reflection while keeping the rule scoped to field names.
- **UI decisions:** n/a - no user-visible UI changes.
- **Related tickets:** none.

## 4. Implementation record

- Root cause confirmed: `b4106200` removed the broad `domain.model` keep rule that had incidentally preserved `AppSettings` field names.
- Added narrow R8 rules that retain only `AppSettings` fields and their names. The class name remains obfuscatable and methods remain eligible for shrinking.
- Fresh `standardRelease` artifact built at `app_v2/build/outputs/apk/standard/release/FastMediaSorter_standard_v2.60.7262.102.apk`.
- R8 mapping confirms the class is still obfuscated (`AppSettings -> bx`). Direct DEX inspection confirms the reflected fields retain their source names, including `allFiles`, `allowDelete`, and `acceptSharedFiles`.
- No device install was required: the R8 mapping plus direct `classes2.dex` evidence proves the released reflection contract.
