---
name: resource-vs-folder-terminology
description: Canonical Resource-vs-Folder wording rule (S0799) + two-icon split (S0842) - when to say resource vs folder in UI/docs
type: feedback
---

Two distinct concepts, never conflate them in UI strings, docs, or icons. Owner-defined 2026-07-01 (S0799/S0842).

- **Resource (ресурс)** = an INTERNAL entity of our app: a registered address (local / network / cloud) that the user created by hand or imported. May act as a destination, a media library, or be virtual (All Music/All Video aggregates, favorites, streams). A resource POINTS TO one folder or a branch of folders. Use "resource / ресурс / ресурс" whenever the UI talks about the app's registered entity: add/edit/copy/remove/select/refresh a resource, resource name/type/path/pin, the resource list, "select destination resource".
- **Folder / Directory (папка / тека / директория)** = a filesystem directory that exists INDEPENDENTLY of our app: a local Android dir, or a dir on a remote SFTP/FTP/cloud server. Use "folder" only for genuine directory ops: the system/manual folder picker, folder path the resource points to, subfolder scanning, create-folder-on-disk, cloud-provider folder IDs, current/parent dir navigation.

**Why:** Owner complaint - button "Add resource" opened a dialog titled "Додати теку" (UK "Add folder"). Translators literally rendered EN "Folder" as папка/тека even where the EN source itself mislabeled a resource. Root cause: no glossary; EN source strings are the origin of most mislabeling.

**How to apply:**
- Two canonical icons, must be visually distinct: resource = `ic_resource` (stacked-layers glyph, neutral `?attr/colorControlNormal`); folder = existing Material folder (`ic_folder` family). Never use the folder glyph for a resource affordance or vice-versa.
- Resource-select/add affordances are icon-only (owner choice) but MUST carry contentDescription + tooltip + D-pad focus.
- Per-type icons `ic_resource_{local,cloud,smb,ftp,sftp,favorites}` stay for showing a resource's TYPE; the generic `ic_resource` is the umbrella/concept marker.
- Fix terminology in BOTH program strings and documentation (COMMUNICATION_POLICY / SETTINGS_REFERENCE / HOW_TO / FEATURES / site).
- Live-vs-dead: some legacy editor keys (`edit_resource`, `resource_path`, `resource_type`, `add_resource`, `edit_resource_with_type`) are 0-ref dead - delete, don't rename. Live editor is `ui/resourceeditor/` (already mostly clean).
