# spec-all pipeline log: camera-capture-command

**Started:** 2026-04-25 11:00
**Idea source:** PLAN/take-a-photo-video-button.md
**Idea (excerpt):** В Видеоколлекциях, Фотоколекциях и невиртуальных ресурсах новая кнопка - сделать фото/видео (команда с низким приоритетом в верхней командной панели ресурса) Команда Видна Для виртуальных ресурсов "Все видео" и "все фото" — результат помещается в стандартную папку камеры. Для прочих ресурсов — в корневую папку ресурса. Кнопка не нужна там где и видео и фото отфильтровано.

---

## Stage log

<!-- append entries below as each stage completes -->
- Stage 1 DONE 11:05 — strategic spec created, Status: Approved.
  File: PLAN/spec_camera-capture-command.md
- Stage 2 DONE 11:08 — spec-update --apply-all.
  Applied: 3 ACCEPT + 0 REVIEW. Proposed (DISCUSS): 1 (photo vs video separate buttons — keeping single button, OS decides).
  SPEC-PATCHED: FR-1 clarified allFiles condition; FR-2 temp-file strategy specified; FR-5 subordination described explicitly.
  SPEC-PATCHED (user correction): button is in BrowseActivity, not PlayerActivity; CAMERA_PHOTOS virtual also shows button.
  SPEC-PATCHED (user addition): after save — list refresh + scroll-to-new-file.
- Stage 3 DONE 11:30 — tactical plan created. Phases: 6.
  Index: PLAN/spec_camera-capture-command/INDEX.md
- Stage 4 DONE 11:32 — spec-update --tactical --apply-all.
  Applied: 5 total. Proposed (DISCUSS): 0.
