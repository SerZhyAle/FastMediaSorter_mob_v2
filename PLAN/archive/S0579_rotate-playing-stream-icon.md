**Status:** Archived

# S0579 - Вращающаяся иконка у проигрываемого аудио-стрима в списке

## Goal (RU)

Маленькой зелёной надписи "сейчас играет" под карточкой аудио-стрима недостаточно, особенно на большом экране. Иконка стрима в строке должна вращаться, пока этот стрим проигрывается, - как анимация при проигрывании музыки в файловом браузере.

## 0. Raw capture

Текст пользователя (verbatim):
Трансляции. список. Маленькая зелёная надпись "сейчас играет" под каточкой аудио-стрима - этого недостаточно, чтобы понять. Особенно на большом экране. Рядом есть иконка "кнотка" - пусть она вращается при прогрывании этого стрима! У нас уже есть подобная анимация - когда проигрываем музыку прямо в браузере файлов

## 2. Resolved decisions

- Reuse the existing browser animation `InlinePlaybackAnimator.startNote()` (`ObjectAnimator` rotation 0->360, 1200ms, linear, infinite) rather than a new one - identical motion to the file-browser inline playback.
- Rotate the row kind icon `ivKind` (the audio glyph) of the now-playing row. Inline playback is audio-only (video/RTSP open fullscreen), so the now-playing id always maps to an audio row.
- Lifecycle: one animator per ViewHolder targeting `ivKind`; start when bound as playing, stop+reset when not; cancel in `onViewRecycled` to avoid leaking a spinning animator onto a recycled row.
- Keep the existing green "сейчас играет" label - the rotation is an additional, stronger cue.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0565 (Streams screen), S0588 (streams row icons)
- **UI placement:** the row leading icon `ivKind`; no layout change.
- **UI visibility/fallback:** rotates only while that row is the inline-playing stream; resets to 0deg otherwise and on recycle.
- **Input support:** purely decorative; no focus/interaction change.

## 4. Acceptance

- The kind icon of the currently-playing audio stream rotates continuously while it plays.
- Stopping playback (or switching streams) stops and resets the rotation; scrolling does not leave a stray spinning icon.
- Motion matches the file-browser inline playback animation.

## 5. Implementation phases

### Phase 01 - Rotate the now-playing row icon

- In `StreamSourceAdapter`, give each `VH` an `InlinePlaybackAnimator` targeting `binding.ivKind`.
- In `bind(source, isPlaying)`: `startNote()` when `isPlaying`, else `stopNote()`.
- Override `onViewRecycled` to `stopAll()` on the holder's animator.
- Verification: `a.ps1 fc` passes; the playing row's icon spins, others static, no stray spin after scroll.
