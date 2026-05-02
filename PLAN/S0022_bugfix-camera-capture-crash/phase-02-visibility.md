# Phase 02 — Предохранитель видимости команды

**Файл:** `BrowseManagerInitializer.kt`, `BrowseCameraCaptureManager.kt`

## Суть изменения

В `onResourceOpsClicked()` (BrowseManagerInitializer) добавить проверку `queryIntentActivities` после существующей проверки `isCameraCaptureVisible`. Если обработчиков нет — скрыть команду и записать Timber.w.

Также в `BrowseCameraCaptureManager.launch()` переместить вызов `queryIntentActivities` ПЕРЕД созданием temp-файла, чтобы при `handlers=0` файл не создавался на диске.

## Добавить в `BrowseCameraCaptureManager` companion object

```kotlin
fun hasCameraHandler(context: android.content.Context, resource: MediaResource): Boolean {
    val captureVideo = resource.supportedMediaTypes.let { types ->
        !resource.allFiles &&
            types.none { it == MediaType.IMAGE || it == MediaType.GIF } &&
            types.any { it == MediaType.VIDEO }
    }
    val action = if (captureVideo) MediaStore.ACTION_VIDEO_CAPTURE
                 else MediaStore.ACTION_IMAGE_CAPTURE
    val intent = Intent(action)
    val handlers = context.packageManager.queryIntentActivities(intent, 0)
    if (handlers.isEmpty()) {
        Timber.w("CameraCapture: no handlers, command hidden action=%s", action)
    }
    return handlers.isNotEmpty()
}
```

## В `onResourceOpsClicked()` заменить

```kotlin
val isCameraVisible = BrowseStateUiUpdater.isCameraCaptureVisible(viewModel.state.value, settings)
```

на

```kotlin
val isCameraVisibleByState = BrowseStateUiUpdater.isCameraCaptureVisible(viewModel.state.value, settings)
val isCameraVisible = isCameraVisibleByState &&
    viewModel.state.value.resource?.let { res ->
        BrowseCameraCaptureManager.hasCameraHandler(activity, res)
    } ?: false
```

## Чеклист

- [x] `hasCameraHandler` добавлен в companion object
- [x] `onResourceOpsClicked` использует двойную проверку
- [x] `launch()` вызывает `queryIntentActivities` перед `createTemp`
- [x] Timber.w при handlers=0
