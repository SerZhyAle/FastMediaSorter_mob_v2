package com.sza.fastmediasorter.wear.data.openin

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.FileProvider
import com.sza.fastmediasorter.wear.domain.files.WearSendToReachability
import com.sza.fastmediasorter.wear.domain.model.WEAR_OPEN_IN_TEXT_LIMIT
import com.sza.fastmediasorter.wear.domain.model.WearOpenInAction
import com.sza.fastmediasorter.wear.domain.model.WearOpenInCandidate
import com.sza.fastmediasorter.wear.domain.model.WearOpenInCandidates
import com.sza.fastmediasorter.wear.domain.model.WearOpenInLaunchResult
import com.sza.fastmediasorter.wear.domain.model.WearOpenInObject
import com.sza.fastmediasorter.wear.domain.model.WearOpenInTarget
import com.sza.fastmediasorter.wear.domain.openin.WearOpenInTextClassifier
import com.sza.fastmediasorter.wear.domain.openin.WearOpenInTextShape
import com.sza.fastmediasorter.wear.domain.repository.WearOpenInRepository
import com.sza.fastmediasorter.wear.util.queryIntentActivitiesCompat
import com.sza.fastmediasorter.wear.util.resolveActivityCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S4381: asks the system which installed applications can take an object and starts the one the wearer picked.
 *
 * The list comes from the system's own answer for the object's intent, with the "not available on the watch" stub
 * and this application removed (research 02), so a row is never offered that would end in a refusal. A file reaches
 * the receiver by a content URI with a read grant, a text above [WEAR_OPEN_IN_TEXT_LIMIT] as a cached file, because
 * an intent extra travels through the process-wide binder limit. No log line carries the object or an exception
 * message: both can quote the URI.
 */
@Singleton
class AndroidWearOpenInRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : WearOpenInRepository {

    private val packageManager: PackageManager
        get() = context.packageManager

    override suspend fun candidatesFor(obj: WearOpenInObject): WearOpenInCandidates = withContext(Dispatchers.IO) {
        val defaultBrowser = defaultPackage(WearOpenInIntents.defaultBrowserProbe())
        val defaultMaps = defaultPackage(WearOpenInIntents.defaultMapsProbe())
        val apps = when (val prepared = prepare(obj)) {
            is WearOpenInObject.Text -> textHandlers(prepared.text, defaultBrowser, defaultMaps)
            is WearOpenInObject.File -> fileHandlers(prepared)
            null -> emptyList()
        }
        WearOpenInCandidates(apps, defaultBrowser, defaultMaps)
    }

    override suspend fun launch(target: WearOpenInTarget, obj: WearOpenInObject): WearOpenInLaunchResult {
        val prepared = withContext(Dispatchers.IO) { prepare(obj) }
        val intent = prepared?.let { intentFor(target.action, it) }
        return if (intent == null) {
            WearOpenInLaunchResult.Refused
        } else {
            intent.component = ComponentName(target.packageName, target.activityName)
            start(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), target.packageName)
        }
    }

    private fun start(intent: Intent, packageName: String): WearOpenInLaunchResult = try {
        context.startActivity(intent)
        Timber.i("Open in: started %s", packageName)
        WearOpenInLaunchResult.Launched
    } catch (e: ActivityNotFoundException) {
        Timber.w("Open in: %s is gone (%s)", packageName, e.javaClass.simpleName)
        WearOpenInLaunchResult.NotFound
    } catch (e: SecurityException) {
        Timber.w("Open in: %s refused (%s)", packageName, e.javaClass.simpleName)
        WearOpenInLaunchResult.Refused
    }

    private fun textHandlers(
        text: String,
        defaultBrowser: String?,
        defaultMaps: String?,
    ): List<WearOpenInCandidate> = when (WearOpenInTextClassifier.classify(text)) {
        WearOpenInTextShape.WEB_ADDRESS, WearOpenInTextShape.LINK ->
            handlers(WearOpenInIntents.viewLink(text), WearOpenInAction.VIEW_LINK)
        WearOpenInTextShape.GEO_POINT -> handlers(WearOpenInIntents.place(text), WearOpenInAction.VIEW_PLACE)
        WearOpenInTextShape.PLAIN -> plainTextHandlers(text, defaultBrowser, defaultMaps)
    }

    /**
     * Free text: any application taking text, plus the default maps application as a place query and the default
     * browser as a web search - each only when it really handles that intent (research 03). The role action wins
     * for a package that appears twice.
     */
    private fun plainTextHandlers(
        text: String,
        defaultBrowser: String?,
        defaultMaps: String?,
    ): List<WearOpenInCandidate> {
        val place = handlers(WearOpenInIntents.place(text), WearOpenInAction.VIEW_PLACE)
            .filter { it.packageName == defaultMaps }
        val search = handlers(WearOpenInIntents.webSearch(text), WearOpenInAction.WEB_SEARCH)
            .filter { it.packageName == defaultBrowser }
        val send = handlers(WearOpenInIntents.sendText(text), WearOpenInAction.SEND_TEXT)
        return (place + search + send).distinctBy { it.packageName }
    }

    private fun fileHandlers(file: WearOpenInObject.File): List<WearOpenInCandidate> {
        val uri = uriOf(file)
        return if (uri == null) {
            emptyList()
        } else {
            handlers(WearOpenInIntents.viewFile(uri, file.mimeType), WearOpenInAction.VIEW_FILE)
        }
    }

    private fun handlers(intent: Intent, action: WearOpenInAction): List<WearOpenInCandidate> =
        packageManager.queryIntentActivitiesCompat(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .mapNotNull { info ->
                info.activityInfo
                    ?.takeUnless { it.packageName == context.packageName || it.packageName == STUB_PACKAGE }
                    ?.let { activity ->
                        val label = info.loadLabel(packageManager).toString()
                        WearOpenInCandidate(activity.packageName, activity.name, label, action)
                    }
            }

    private fun defaultPackage(probe: Intent): String? =
        packageManager.resolveActivityCompat(probe, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName
            ?.takeUnless { it == RESOLVER_PACKAGE || it == STUB_PACKAGE || it == context.packageName }

    private fun intentFor(action: WearOpenInAction, obj: WearOpenInObject): Intent? = when (obj) {
        is WearOpenInObject.Text -> textIntent(action, obj.text)
        is WearOpenInObject.File -> fileIntent(action, obj)
    }

    private fun fileIntent(action: WearOpenInAction, file: WearOpenInObject.File): Intent? {
        val uri = uriOf(file)
        return if (action == WearOpenInAction.VIEW_FILE && uri != null) {
            WearOpenInIntents.viewFile(uri, file.mimeType)
        } else {
            null
        }
    }

    private fun textIntent(action: WearOpenInAction, text: String): Intent? = when (action) {
        WearOpenInAction.VIEW_LINK -> WearOpenInIntents.viewLink(text)
        WearOpenInAction.VIEW_PLACE -> WearOpenInIntents.place(text)
        WearOpenInAction.SEND_TEXT -> WearOpenInIntents.sendText(text)
        WearOpenInAction.WEB_SEARCH -> WearOpenInIntents.webSearch(text)
        WearOpenInAction.VIEW_FILE -> null
    }

    private fun uriOf(file: WearOpenInObject.File): Uri? = when {
        file.contentUri != null -> Uri.parse(file.contentUri)
        file.path != null -> try {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(file.path))
        } catch (e: IllegalArgumentException) {
            Timber.w("Open in: a file is outside the provider's paths (%s)", e.javaClass.simpleName)
            null
        }
        else -> null
    }

    /** A text over the limit becomes a cached file; null when that file cannot be written. */
    private fun prepare(obj: WearOpenInObject): WearOpenInObject? = when {
        obj !is WearOpenInObject.Text || obj.text.length <= WEAR_OPEN_IN_TEXT_LIMIT -> obj
        else -> writeLongText(obj.text)
    }

    private fun writeLongText(text: String): WearOpenInObject.File? = try {
        val directory = File(context.cacheDir, LONG_TEXT_DIRECTORY).apply { mkdirs() }
        val file = File(directory, LONG_TEXT_NAME)
        file.writeText(text)
        WearOpenInObject.File(mimeType = TEXT_MIME_TYPE, displayName = LONG_TEXT_NAME, path = file.absolutePath)
    } catch (e: IOException) {
        Timber.w("Open in: the long text could not be written (%s)", e.javaClass.simpleName)
        null
    }

    private companion object {
        const val STUB_PACKAGE = WearSendToReachability.STUB_PACKAGE
        const val RESOLVER_PACKAGE = "android"
        const val LONG_TEXT_DIRECTORY = "open-in"
        const val LONG_TEXT_NAME = "text.txt"
        const val TEXT_MIME_TYPE = "text/plain"
    }
}
