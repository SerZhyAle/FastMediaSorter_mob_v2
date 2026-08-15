# Phase 03 - Resource media-type controls

## Goal

Expose Office documents wherever resource create/edit flows already expose document media-type choices.

## Tasks

- Add Office document checkboxes to SMB and SFTP add-resource media-type sections.
- Preserve profile presets and all-files behavior for the new checkboxes.
- Add Office documents to resource editor media-type selection.
- Preserve Office media-type state while loading, editing and saving resources.
- Add Office to local resource selection badges where the compact PDF badge already exists.

## Verification

- Static layout check confirms add-resource and resource-editor default layouts include Office controls and have no `layout-land` counterparts.
- Static code check confirms `MediaType.OFFICE_DOCUMENT` flows through add/edit read and write paths.
