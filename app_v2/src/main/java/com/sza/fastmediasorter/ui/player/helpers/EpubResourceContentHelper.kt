package com.sza.fastmediasorter.ui.player.helpers

import android.util.Base64
import android.webkit.WebResourceResponse
import io.documentnode.epub4j.domain.Book
import io.documentnode.epub4j.domain.Resource
import org.jsoup.Jsoup
import timber.log.Timber
import java.io.ByteArrayInputStream

class EpubResourceContentHelper {
    data class ReaderStyle(
        val theme: EpubStyleManager.ReaderTheme,
        val fontSizePx: Int,
        val fontFamily: String,
        val lineHeight: Float,
        val horizontalPaddingPx: Int,
    )

    fun preprocessHtml(htmlContent: String, resource: Resource, book: Book?, style: ReaderStyle): String {
        val doc = Jsoup.parse(htmlContent)

        doc.select("script").remove()
        doc.body()?.append(
            """<script>
               document.addEventListener('selectionchange', function() {
                   if (typeof EpubSelectionBridge !== 'undefined') {
                       EpubSelectionBridge.onSelectionChanged(window.getSelection().toString());
                   }
               });
               </script>"""
        )

        doc.head().prepend(
            EpubStyleManager.generateCss(
                theme = style.theme,
                fontSizePx = style.fontSizePx,
                fontFamily = style.fontFamily,
                lineHeight = style.lineHeight,
                horizontalPaddingPx = style.horizontalPaddingPx
            )
        )

        if (book != null) inlineBookImages(doc, resource, book)
        return doc.html()
    }

    fun assetResponse(resourcePath: String, book: Book): WebResourceResponse? {
        val imageResource = findImageResourceByPath(resourcePath, book)
        if (imageResource != null) {
            return try {
                val imageData = imageResource.data
                val mimeType = imageResource.mediaType?.name ?: "image/jpeg"
                Timber.d("EPUB: Serving intercepted asset '$resourcePath' from EPUB (${imageData.size} bytes, $mimeType)")
                WebResourceResponse(mimeType, "UTF-8", ByteArrayInputStream(imageData))
            } catch (e: Exception) {
                Timber.e(e, "EPUB: Error serving intercepted asset '$resourcePath'")
                null
            }
        }

        Timber.w("EPUB: Asset '$resourcePath' not found in EPUB resources")
        val imageResources = book.resources.all.filter { it.mediaType?.name?.startsWith("image/") == true }
        Timber.w("EPUB: Available images: ${imageResources.map { it.href }.joinToString()}")
        return null
    }

    private fun inlineBookImages(doc: org.jsoup.nodes.Document, resource: Resource, book: Book) {
        val images = doc.select("img")
        Timber.d("EPUB: Found ${images.size} <img> tags in chapter")
        for (img in images) {
            val src = img.attr("src")
            if (src.isNotBlank() && !src.startsWith("data:") && !src.startsWith("http")) {
                convertResourceToDataUri(img, "src", src, resource, book)
            }
        }

        val elementsWithStyle = doc.select("[style*=url]")
        Timber.d("EPUB: Found ${elementsWithStyle.size} elements with background-image in style")
        for (element in elementsWithStyle) {
            val style = element.attr("style")
            if (style.contains("url(") && !style.contains("data:")) {
                inlineBackgroundImage(element, style, resource, book)
            }
        }
    }

    private fun inlineBackgroundImage(
        element: org.jsoup.nodes.Element,
        style: String,
        resource: Resource,
        book: Book
    ) {
        val urlStart = style.indexOf("url(") + 4
        val urlEnd = style.indexOf(")", urlStart)
        if (urlEnd <= urlStart) return

        val url = style.substring(urlStart, urlEnd).trim('\'', '"', ' ')
        if (url.isBlank() || url.startsWith("data:") || url.startsWith("http")) return

        val imageResource = findImageResource(url, resource, book)
        if (imageResource != null) {
            val dataUri = dataUriFor(imageResource)
            element.attr("style", style.replace("url($url)", "url($dataUri)"))
            Timber.d("EPUB: Converted background-image '$url' to data URI")
        } else {
            Timber.w("EPUB: Background image not found: $url")
        }
    }

    private fun convertResourceToDataUri(
        element: org.jsoup.nodes.Element,
        attrName: String,
        src: String,
        baseResource: Resource,
        book: Book
    ) {
        try {
            val imageResource = findImageResource(src, baseResource, book)
            if (imageResource != null) {
                val imageData = imageResource.data
                element.attr(attrName, dataUriFor(imageResource))
                Timber.d("EPUB: Converted image '$src' to data URI (${imageData.size} bytes, mime=${imageResource.mediaType?.name ?: "image/jpeg"})")
            } else {
                Timber.w("EPUB: Image resource not found after all attempts: original='$src'")
                if (src.contains("cover", ignoreCase = true)) {
                    val imageResources = book.resources.all.filter { it.mediaType?.name?.startsWith("image/") == true }
                    Timber.w("EPUB: Available images in EPUB: ${imageResources.map { it.href }.joinToString()}")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "EPUB: Failed to process image '$src'")
        }
    }

    private fun dataUriFor(resource: Resource): String {
        val imageData = resource.data
        val base64 = Base64.encodeToString(imageData, Base64.NO_WRAP)
        return "data:${resource.mediaType?.name ?: "image/jpeg"};base64,$base64"
    }

    private fun findImageResource(src: String, baseResource: Resource, book: Book): Resource? {
        val resourceHref = resolveResourcePath(baseResource.href, src)
        Timber.d("EPUB: Resolving image - original='$src', base='${baseResource.href}', resolved='$resourceHref'")

        var imageResource = book.resources.getByHref(resourceHref)
        if (imageResource == null) {
            imageResource = book.resources.getByHref(src)
            if (imageResource != null) Timber.d("EPUB: Found image by original path '$src'")
        }
        if (imageResource == null) {
            val simplePath = src.trimStart('/', '.')
            imageResource = book.resources.getByHref(simplePath)
            if (imageResource != null) Timber.d("EPUB: Found image by simple path '$simplePath'")
        }
        if (imageResource == null) {
            val filename = src.substringAfterLast('/')
            for (res in book.resources.all) {
                if (res.href.endsWith(filename)) {
                    imageResource = res
                    Timber.d("EPUB: Found image by filename match '${res.href}'")
                    break
                }
            }
        }
        return imageResource
    }

    private fun findImageResourceByPath(path: String, book: Book): Resource? {
        var resource = book.resources.getByHref(path)
        if (resource != null) {
            Timber.d("EPUB: Found resource by exact path '$path'")
            return resource
        }

        val pathWithoutSlash = path.trimStart('/')
        resource = book.resources.getByHref(pathWithoutSlash)
        if (resource != null) {
            Timber.d("EPUB: Found resource by path without slash '$pathWithoutSlash'")
            return resource
        }

        val commonPrefixes = listOf("OEBPS/", "OPS/", "EPUB/", "")
        for (prefix in commonPrefixes) {
            resource = book.resources.getByHref(prefix + pathWithoutSlash)
            if (resource != null) {
                Timber.d("EPUB: Found resource with prefix '$prefix$pathWithoutSlash'")
                return resource
            }
        }

        val filename = path.substringAfterLast('/')
        for (res in book.resources.all) {
            if (res.href.endsWith(filename)) {
                Timber.d("EPUB: Found resource by filename match '${res.href}' for request '$path'")
                return res
            }
        }
        return null
    }

    private fun resolveResourcePath(baseHref: String, relativePath: String): String {
        val cleaned = relativePath.removePrefix("./")
        if (!baseHref.contains("/")) return cleaned

        val resolvedParts = baseHref.split("/").dropLast(1).toMutableList()
        for (part in cleaned.split("/")) {
            when (part) {
                ".." -> if (resolvedParts.isNotEmpty()) resolvedParts.removeAt(resolvedParts.size - 1)
                "." -> Unit
                else -> resolvedParts.add(part)
            }
        }
        return resolvedParts.joinToString("/")
    }
}
