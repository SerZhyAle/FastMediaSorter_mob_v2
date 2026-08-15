# 03 - Что из «receiver»-части уже закрыто

**Research item:** §6.3
**Дата:** 2026-06-11
**Статус:** Resolved

## Вопрос

Доходит ли системный скриншот, отправленный через Share, до приложения сегодня, и чего не хватает для бесшовного приёма.

## Находки (по коду)

- В манифесте объявлен набор `ACTION_SEND`-aliases на `ReceiveShareActivity`: `StandaloneImageSender` (image/*), `StandaloneVideoSender`, `StandaloneAudioSender`, `StandaloneTextSender`.
- `ReceiveShareActivity` - прозрачная активность: перехватывает `ACTION_SEND`/`ACTION_SEND_MULTIPLE`, показывает диалог выбора назначения и пишет через существующий сценарий файловых операций.
- Aliases по умолчанию `enabled=false` и включаются/выключаются в рантайме через `DefaultPlayerManager.applyShareReceiverState()`.
- Следствие: системный скриншот, отправленный пользователем через Share sheet как `image/png`, уже доходит до приложения и попадает в общий путь сохранения - при включённом image-приёмнике.

## Вывод для спеки

- «Receiver»-часть для пути «через Share» уже существует и переиспользуется ролью сохранения (§5.1 C) - отдельная новая приёмная точка под скриншоты не требуется.
- Новизна S0405 - в «maker»-части: захват экрана вне собственных окон (хэндл + MediaProjection), а не в приёме.
- Опциональный (вероятно вне первого объёма) сценарий «авто-подхват новых системных скриншотов без Share»: потребовал бы наблюдения за `MediaStore`/папкой Screenshots (ContentObserver), что тяжело и шумно по разрешениям. Кандидат в non-goal первой итерации; решение - за владельцем.

## Источники

- Манифест `app_v2/src/main/AndroidManifest.xml` (aliases `Standalone*Sender`, `ReceiveShareActivity`).
- `ui/share/ReceiveShareActivity.kt`, `ui/settings/helpers/DefaultPlayerManager.kt` (`applyShareReceiverState`).
