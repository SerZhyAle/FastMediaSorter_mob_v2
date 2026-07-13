---
name: glide-requestlistener-fires-before-view-bind
description: Glide RequestListener.onResourceReady runs BEFORE the drawable is set into the ImageView; reading view.drawable there is still null
type: project
---

Glide's `RequestListener<Drawable>.onResourceReady(..)` fires **before** Glide binds the decoded drawable into the target `ImageView`. In the standard `.into(imageView)` flow, Glide only calls `target.onResourceReady(..)` (which does `setImageDrawable`) *after* every RequestListener returned `false`. So inside the listener, `imageView.drawable` is still `null`.

**Why:** bit us in S1041 - a deferred auto-OCR/translate that read `photoView.drawable?.toBitmap()` from inside the listener got a null drawable and toasted `ocr_extract_image_failed`, even though `onResourceReady` had fired (device log showed the `S1041: image ready` tag then the null branch).

**How to apply:** when a callback must read the *view's* drawable after a Glide load, don't run it directly in `onResourceReady` - dispatch via `view.post { .. }` so it runs on the next main-thread message, after Glide has bound the drawable. Alternatively use the `resource` arg the listener already hands you, or subclass `DrawableImageViewTarget` and act in `super.onResourceReady`. Return `false` from the listener so Glide still sets the resource into the target. See `StandaloneViewManager.onImageReadyListener`.
