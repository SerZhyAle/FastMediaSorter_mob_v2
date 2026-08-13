# Research 01 - Recordings folder resolution and MediaStore indexing

Resolves strategic §6.1.

## Question

Where does a quick voice note land so the user finds it as an ordinary recording, and so the system indexes it across API levels?

## Findings

- `LocalDestinationClassifier.matchPublicCollectionKind` (data/transfer/local/LocalDestinationClassifier.kt:73-85) maps `MUSIC`, `PODCASTS`, `AUDIOBOOKS`, `RINGTONES`, `NOTIFICATIONS`, `ALARMS` to `AUDIO`; `MOVIES` to `VIDEO`; `PICTURES`/`DCIM` to `IMAGES`; `DOWNLOADS` to `DOWNLOADS`. It does **not** map `Environment.DIRECTORY_RECORDINGS`.
- A path under `<external>/Recordings/` therefore classifies as `NonPublic`, taking the `FileOutputStream` branch of `MediaStoreLocalDestinationWriter` instead of the MediaStore publish branch. On API 29+ scoped storage this is not indexed (and can fail with EACCES under restrictive OEM policy).
- `Environment.DIRECTORY_RECORDINGS` exists only on API 31+. minSdk is 26 (standard) and 23 (legacy).
- `DIRECTORY_MUSIC` is the established in-repo precedent for voice artifacts: both `BrowseMicRecordingManager` (temp file dir) and `QuickAudioRecorderService` use `Environment.DIRECTORY_MUSIC`. `DIRECTORY_MUSIC` already maps to `AUDIO`, so a Music write indexes correctly today.

## Decision

- Quick voice destination resolves to `DIRECTORY_RECORDINGS` on API 31+, else `DIRECTORY_MUSIC`.
- Extend `LocalDestinationClassifier`: add a guarded `getRecordingsDirectoryName()` (returns `Environment.DIRECTORY_RECORDINGS` on API 31+, literal `"Recordings"` below) and include it in the `AUDIO` branch, mirroring the existing `getAudiobooksDirectoryName()` API guard. This keeps plain-path classification crash-free on older devices and indexes Recordings as `AUDIO` on API 31+.
- Writes go through the existing `LocalDestinationWriter` (MediaStore publish on API 29+, `FileOutputStream` below). No writer change is needed - only the classifier mapping plus the policy resolver.

## Consequence for phases

- Phase 02 implements `CaptureDestinationPolicy.resolveQuickVoiceDestination()` and the classifier `Recordings` mapping.
- Photo and video need no new policy or classifier work: `DCIM`/`Camera` and `MOVIES` are already mapped, and `CaptureDestinationPolicy.resolveCameraDestination(null)` / `resolveVideoDestination(null)` already return them.
