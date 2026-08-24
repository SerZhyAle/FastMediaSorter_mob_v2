---
name: play-update-rejected-all-files-access
description: Play rejected the 2026-08-23 update over the All files access declaration; nothing ships to the store until the console resubmission (S1991) is accepted.
metadata:
  type: project
---

Update 2.60.8232.251 (versionCode 260823225) was **rejected** on 2026-08-24 with
`All Files Access Permission policy: Not a core feature`, plus two `Not adhering to Google Play
Developer Program policies` records whose details nobody has opened yet. The live version from
2026-08-17 keeps serving. The Wear artifact 26082322 rides the same package and the same review, so
the watch does not ship either.

`Managed publishing on` in the Console was read as "the owner is holding changes" - that reading was
wrong. The changes were not held, they were rejected.

**Why:** the declaration and the store description are reviewed as a pair, and both were weak - the
declaration made two claims a reviewer refutes in a minute, and the description opened as a media
player. S1989 fixed the repository half: `store_assets/PLAY_PERMISSIONS_DECLARATION.md` now holds the
form text, `play/listing/<locale>/` leads with file organizing, and the second stale description set
under `store_assets/play_store_description_*.txt` is gone. What remains is the owner's console work,
parked as **S1991**.

**S1992 (closed `Verified` 2026-08-24) belongs to this story.** On the exact build that was reviewed,
granting All files access left the `Permissions required` dialog on screen and `appops` reading
`allow` - the reviewer's own path ended in the app saying the permission it had just been given did
not work. Root cause was `SettingsIntentLauncher` asking for an activity result across
`FLAG_ACTIVITY_NEW_TASK`, which the platform cancels at launch. Causation with the rejection cannot be
claimed, but any resubmission or evidence video must be shot on a build that carries this fix.

**How to apply:** before recommending or running any release flow, check S1991's status - a build
that cannot pass review is not a release. Also note the `targetSdk 36` warning with `Fix by Aug 31,
2026`: `app_v2/build.gradle.kts` already sets 36, so the warning clears itself, but only once an
update actually reaches the store - which makes the resubmission time-bound rather than routine.
Verify against the Console before acting; this is a snapshot of 2026-08-24.

Related: [[index-release]], [[project-play-listing-api-vs-public-page]].
