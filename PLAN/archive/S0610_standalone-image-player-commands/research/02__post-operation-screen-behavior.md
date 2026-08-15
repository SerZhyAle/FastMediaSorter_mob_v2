# Research 02 - Screen behavior after copy / move in standalone

**Strategic §6 item:** 2
**Status:** Resolved

## Question

After a copy (source stays) and after a move (source is gone), should the standalone screen stay or close?

## Finding

Standalone already finishes the activity after a destructive single-file operation: `StandaloneFileOperationsHandler.onDeleteSuccess()`
calls `activity.finish()`. The host has no list/folder navigation guarantee for the post-move file, and the in-app
`goToNextAfterCopy` / list-advance semantics depend on a resource the standalone lacks.

## Decision

- Copy success: keep the screen open (source file still present), show a factual success confirmation.
- Move success: the source no longer exists at the shown URI, so finish the activity - mirroring the delete path.

This keeps standalone single-file semantics consistent: an operation that removes the shown file closes the viewer;
an operation that preserves it leaves the viewer open.

## Implication for the plan

Copy and move dispatch through the existing `fileOperationUseCase`; the standalone callback finishes on move success
and stays on copy success. No queue / no list navigation is introduced.
