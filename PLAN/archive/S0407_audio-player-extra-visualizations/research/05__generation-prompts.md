# Ресёрч: промпты генерации новых визуализаций

**Ticket:** S0407
**Research item:** §6.5 - промпты для AI-генерации ~5 новых фонов
**Date:** 2026-06-12
**Статус:** Resolved (промпты предложены; финальный отбор/правка - за владельцем)

---

## Назначение

Промпты для text-to-video (рекомендованный сервис - Google Veo / Gemini, платный тариф; альтернативы - Kling/Runway/Luma/Pika). Цель - декоративный фон аудиоплеера: спокойное непрерывное амбиентное движение за UI, тёмная палитра, без текста и резких камер-движений, пригодно для бесшовного цикла.

---

## Общие требования ко всем промптам (loop-friendly)

Добавлять к каждому промпту как «технический хвост»:

> Slow continuous ambient motion, no camera movement, no cuts, no text, no people, no logos. Dark, low-key palette suitable as a UI background. Subtle and non-distracting. Seamless loop-friendly: motion should be uniform with no strong directional sweep so first and last frames blend. 16:9, high quality.

Параметры генерации: длительность ≈ вдвое больше существующих фонов (генерировать с запасом и вырезать стабильный участок), максимальное доступное разрешение/качество (размер не критичен - ухудшаем уже на этапе перекодирования при необходимости).

---

## Пять промптов (разные настроения)

1. **Liquid gradient**
   > Deep indigo-to-teal liquid gradient slowly undulating, soft caustic light refractions drifting across the surface, gentle flowing waves of color, abstract and dreamy.

2. **Floating particles / bokeh**
   > Tiny glowing particles and soft bokeh dust slowly floating upward in dark space, faint violet-to-cyan color shift, shallow depth of field, calm drifting motion.

3. **Aurora ribbons**
   > Aurora-like ribbons of soft light slowly morphing and flowing over a near-black sky, gentle waves of green-violet luminescence, smooth continuous undulation.

4. **Ink / smoke swirl**
   > Soft volumetric ink and smoke slowly swirling in darkness, gentle turbulence, monochrome with a faint warm amber glow at the core, organic flowing motion.

5. **Neon waveform (audio-themed)**
   > Abstract neon sound-wave bars and flowing equalizer lines gently pulsing and rippling over a dark background, soft cyan-magenta glow, smooth rhythmic motion as if breathing.

---

## Постобработка (ручной этап, ffmpeg)

1. Вырезать стабильный сегмент нужной длительности.
2. Сделать бесшовный цикл техникой boomerang (прямой+реверс) - выбрано владельцем.
3. Перекодировать в `.mp4` H.264 в единый профиль с существующими (разрешение/битрейт), качество выше прежнего (размер не критичен).
4. Назвать `anim_audio_bg_6.mp4` .. (продолжение нумерации; 8–10 новых -> `_6`..`_13`/`_15`).
5. Снять SHA-256 + размер каждого файла для регистрации в каталоге доставки.

---

## За владельцем

- Отобрать/поправить промпты, сгенерировать ролики. Цель - 8–10 новых: можно взять несколько вариаций (сид/палитра/темп) на каждый из пяти мотивов, чтобы добрать число при сохранении разнообразия.
- Подтвердить сервис (Veo по умолчанию) и платный тариф с коммерческими правами.
