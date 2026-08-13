# S1325 research 04 - what cancellation leaves behind

Date: 2026-07-31. Code-level pass, no device run.

## How file transfers behave today

`BrowseFileTransferWorker.runTransfer` calls `ensureActive()` on every progress event of the file operation, so a cancelled job stops between files. Already-copied files stay at the destination; for a move, the source entry is deleted only after its own copy succeeded, so nothing is lost - the selection ends up split across the two locations. The terminal event is `Cancelled` and the notification reports it.

For directories the same worker checks `ensureActive()` once per top-level directory only, and the per-protocol recursive copy runs to completion without any cancellation check inside it.

## Decision for S1325

Directories adopt the file semantics, not a new model:

- Cancellation is honoured between entries of the tree, not only between top-level folders.
- What is already written at the destination stays; nothing is rolled back.
- For a move, an entry is removed from the source only after its copy is confirmed, so a cancelled move leaves a partially moved tree and never a hole.
- The terminal message states how many entries were processed before the stop, so the user knows the tree is partial.

Granularity limit accepted for this ticket: the per-entry check is wired where the progress callback is invoked. Protocol strategies that report progress per file gain per-entry cancellation; any path that does not report per file keeps its current coarser granularity, and that is stated in the phase rather than left silent.

## Rejected alternative

Rolling back a cancelled copy by deleting what was written. Rejected: deleting at the destination on the user's behalf is the one behaviour that can destroy data if the destination already held files of the same names, and undo is explicitly out of scope for this ticket (S1326).
