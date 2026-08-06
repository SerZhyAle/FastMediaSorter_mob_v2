package com.sza.fastmediasorter.ui.common.permissions

import android.content.Context
import com.sza.fastmediasorter.domain.model.PermissionRationale
import com.sza.fastmediasorter.domain.model.PermissionTask
import com.sza.fastmediasorter.domain.repository.PermissionRegistryRepository
import dagger.hilt.android.EntryPointAccessors

/**
 * S1436: the words a permission request shows, taken from the registry rather than from whichever
 * screen happens to be asking. The shell always stays the caller's - this decides the wording, never
 * whether it blocks, how long it shows, or what the buttons say.
 */
private fun Context.permissionRegistry(): PermissionRegistryRepository =
    EntryPointAccessors
        .fromApplication(applicationContext, PermissionRationaleEntryPoint::class.java)
        .permissionRegistry()

/**
 * The full explanation for [permission]: the registry's paragraph, plus the sentence declared for
 * [task] on its own line. Empty when the registry has no entry - a call site asking about a
 * permission the registry never heard of is a defect worth seeing rather than a filled-in blank.
 */
fun Context.permissionRationale(permission: String, task: PermissionTask? = null): String =
    permissionRegistry().getRationale(permission, task)?.message(this).orEmpty()

/**
 * The one short line a toast is allowed (`docs/COMMUNICATION_POLICY.md` §2.1). When [task] declares an
 * addendum that line is the addendum: it is the sentence written for exactly this job, and a toast has
 * room for one line, not for the row label and the job sentence both.
 */
fun Context.permissionRationaleShort(permission: String, task: PermissionTask? = null): String =
    permissionRegistry().getRationale(permission, task)
        ?.let { getString(it.taskAddendumRes ?: it.summaryRes) }
        .orEmpty()

private fun PermissionRationale.message(context: Context): String {
    val base = context.getString(detailRes)
    val addendum = taskAddendumRes?.let { context.getString(it) }
    return if (addendum.isNullOrBlank()) base else "$base\n\n$addendum"
}
