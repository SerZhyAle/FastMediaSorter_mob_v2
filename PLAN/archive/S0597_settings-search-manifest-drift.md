**Status:** Archived

# S0597 - Settings-search manifest drift: 3 live settings dropped, 3 new settings never doc-synced

## 0. Capture (raw)

Surfaced while regenerating `docs/settings/settings-manifest.json` for an unrelated rotation-string rename (player/program follow-OS decoupling). The committed manifest did not match the live Robolectric scan (`SettingsManifestExportTest`), i.e. the settings-doc-sync gate Stage 2 (manifest-fresh) was already red before that change.

Regenerating the manifest from the live scan produced this real delta (ignoring reordering):

- Dropped from the manifest (present in old committed manifest, absent from current live scan):
  - `actvCacheSizeLimit` (General settings - max local cache disk space)
  - `actvNetworkParallelism` (General settings - simultaneous network connections)
  - `etSlideshowInterval` (Playback settings - default slide duration)
- Newly present in the live scan (absent from old committed manifest, now added):
  - `btnTakeScreenshotNow`
  - `headerBackgroundAudio`
  - `rowEnableStreams`

The 3 dropped keys still exist as real, reachable settings in the app UI (`fragment_settings_general.xml`, `fragment_settings_playback.xml`, `GeneralSettings*Helper.kt`, `PlaybackSettingsFragment.kt`). They are EditText/AutoComplete inputs; other input-kind keys (`etMaxRecipients`, `etIconSize`) are still scanned, so the cause is not "input widgets are excluded".

To unblock the rotation change (which mechanically required a full manifest regen - a partial manifest fails Stage 2), the 3 now-orphaned annotation entries were removed from `docs/settings/settings-annotations.json` and the reference was re-rendered. Their EN/RU/UK annotation text is preserved in git history and reproduced below for restoration.

## 1. Problem

Three live, user-facing settings silently fell out of the settings-search manifest at some earlier settings-search refactor, without a manifest regeneration. Effect: cache-size limit, network parallelism, and slideshow interval are no longer findable via in-app settings search. Need to determine whether the de-listing is an intentional design change or a regression, and either restore them to the search catalog (with their annotations) or confirm the removal.

## 2. Evidence

- `scripts/quality/assert-settings-doc-sync.ps1` Stage 2 byte-diff (committed vs live scan) was failing pre-change.
- Manifest git diff: +`btnTakeScreenshotNow`/`headerBackgroundAudio`/`rowEnableStreams`, -`actvCacheSizeLimit`/`actvNetworkParallelism`/`etSlideshowInterval`.
- Scan source: `SettingsManifestExportTest` (Robolectric) over the layouts catalogued by `SettingsSearchLayoutCatalog`.
- Probable cause: an in-flight, uncommitted settings-widget refactor in the working tree (new `SettingsInputRow` / `SettingsDropdownRow` / `SettingsSelectionRow` / `FormCheckboxRow` widgets). The 3 dropped `actv`/`et` ids are most likely being migrated onto these new input widgets with new ids; the 3 added keys (`btnTakeScreenshotNow` / `headerBackgroundAudio` / `rowEnableStreams`) are new settings from the same WIP. So the de-listing is probably intentional churn, not a shipped regression - confirm before acting. If that refactor is covered by another (untracked) effort, archive this and fold the check there.
- Removed annotation text (for restoration if this is a regression):
  - `actvCacheSizeLimit`: EN "Sets the maximum disk space the app may use for its local cache." / RU "Задаёт максимальный объём дискового пространства для локального кэша приложения." / UK "Задає максимальний обсяг дискового простору для локального кешу застосунку."
  - `actvNetworkParallelism`: EN "Sets the number of simultaneous network connections used when accessing remote resources." / RU "Задаёт количество одновременных сетевых подключений при доступе к удалённым ресурсам." / UK "Задає кількість одночасних мережевих підключень при доступі до віддалених ресурсів."
  - `etSlideshowInterval`: EN "Sets the default number of seconds each slide is shown during a slideshow." / RU "Задаёт количество секунд по умолчанию, в течение которых показывается каждый слайд в слайдшоу." / UK "Задає кількість секунд за замовчуванням, протягом яких показується кожен слайд у слайдшоу."

## 3. Scope to investigate

- Why `SettingsSearchLayoutCatalog` / the scan no longer emits the 3 dropped keys (layout id move, container restructure, kind filter).
- Whether `btnTakeScreenshotNow` / `headerBackgroundAudio` / `rowEnableStreams` are correctly searchable (they are now annotated and in the manifest - confirm intent and copy quality).
- If de-listing is a regression: restore the 3 settings to the catalog, regenerate manifest, re-add the 3 annotations, re-render reference.
- If intentional: keep removed; add a brief note so future audits do not re-flag.

## 4. Resolution

Verdict: regression, not intentional churn.

Root cause: the S0567 settings-widget unification migrated the 3 inputs onto the new `SettingsInputRow` compound widget. That widget carries its label in `app:sir_title` (its `TextInputEditText` has no `android:hint`), but the search scanner `LayoutSettingsSearchSource.kindFromTag` recognized only raw widget tags (`EditText` / `TextInputEditText` / `AutoCompleteTextView` / ..) plus `SettingsToggleRow` / `CollapsibleSectionHeader`. An unrecognized tag yields no entry, so the migrated inputs silently fell out of the search index.

Scope is exactly the 3 captured keys. Confirmed via git: `actvCacheSizeLimit` / `actvNetworkParallelism` / `etSlideshowInterval` previously appeared in the manifest; the sibling `SettingsDropdownRow` keys (`spinnerLanguage`, `actvPrefetchCache`, ..) and `SettingsSelectionRow` keys (`rowDeviceProfile`, ..) never appeared - raw `Spinner` / custom rows had no scannable title, so they were never searchable. Their de-listing is a pre-existing limitation, not this regression.

Fix:
- New `EntryKind.INPUT_ROW`; `kindFromTag` maps `SettingsInputRow` to it; `buildEntry` extracts title from `app:sir_title` and hint from `app:sir_hint` (help attributes stay excluded per strategic §6.3).
- Regenerated `docs/settings/settings-manifest.json` (the 3 return with `kind: INPUT_ROW`, identical EN/RU/UK titles).
- Re-added the 3 annotations to `docs/settings/settings-annotations.json` and re-rendered `SETTINGS_REFERENCE*.md`.

Verification: `assert-settings-doc-sync.ps1 -Gate` exit 0 (all 5 stages). The manifest is produced by the same `LayoutSettingsSearchSource.collect()` the app ships, so a fresh manifest proves the in-app search index now surfaces the 3 settings.

Follow-up (out of scope): `SettingsDropdownRow` / `SettingsSelectionRow` rows now carry explicit `sdr_title` / `ssr_title` labels and could be made searchable (Language, Color theme, Sort mode, Device profile, ..) - a net-new enhancement, not part of this regression.
