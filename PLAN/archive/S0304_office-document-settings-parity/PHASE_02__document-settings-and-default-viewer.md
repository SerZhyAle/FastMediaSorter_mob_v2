# Phase 02 - Document settings and default viewer

## Goal

Expose Office documents in Document settings and remove the PDF-only default document viewer flow.

## Tasks

- Add a visible Office documents row beside Text, PDF and EPUB in document settings.
- Bind the row to `supportOfficeDocuments` and all-files behavior.
- Hide the row when the flavor has no Office document family support.
- Replace the PDF-only default document viewer action with a PDF/Office type chooser.
- Keep default viewer logic MIME-specific and flavor-safe.

## Verification

- Static layout check confirms the default layout has the new row and no `layout-land` counterpart exists.
- Static code check confirms the default document viewer action can choose PDF or Office MIME setup.
