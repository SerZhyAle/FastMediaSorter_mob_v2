package com.sza.fastmediasorter.ui.settings.fragments

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.PermissionEntry
import com.sza.fastmediasorter.domain.model.PermissionStatus
import com.sza.fastmediasorter.domain.repository.PermissionRegistryRepository
import com.sza.fastmediasorter.domain.usecase.CheckPermissionStatusUseCase
import com.sza.fastmediasorter.domain.usecase.RequestContextualPermissionUseCase
import com.sza.fastmediasorter.ui.common.permissions.PermissionDenialHandler
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class PermissionsManagementFragment : Fragment() {

    interface WelcomeCompleteListener {
        fun onWelcomeComplete()
    }

    companion object {
        private const val ARG_FROM_WELCOME = "from_welcome"

        fun newInstance(fromWelcome: Boolean = false): PermissionsManagementFragment =
            PermissionsManagementFragment().apply {
                arguments = Bundle().apply { putBoolean(ARG_FROM_WELCOME, fromWelcome) }
            }
    }

    @Inject lateinit var registry: PermissionRegistryRepository
    @Inject lateinit var checkStatus: CheckPermissionStatusUseCase
    @Inject lateinit var requestContextual: RequestContextualPermissionUseCase

    private val fromWelcome get() = arguments?.getBoolean(ARG_FROM_WELCOME, false) ?: false

    // These permissions cannot be granted via requestPermission() — each requires a dedicated
    // system settings screen. Batch-requesting them via requestMultiplePermissions() is silently ignored.
    private val specialGrantPermissions = setOf(
        Manifest.permission.MANAGE_EXTERNAL_STORAGE,
        Manifest.permission.MANAGE_MEDIA,
        Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
    )

    private lateinit var adapter: PermissionRowAdapter

    private val requestMultiple = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refreshAdapter()
        updateGrantAllVisibility()
        // After the regular permissions dialog, launch the next denied special permission (if any).
        launchNextSpecialPermission()
    }

    private val requestSingle = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        refreshAdapter()
        updateGrantAllVisibility()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_permissions_management, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = PermissionRowAdapter { entry, status ->
            when (status) {
                PermissionStatus.DENIED -> {
                    if (entry.manifestName in specialGrantPermissions) launchSpecialGrantSettings(entry)
                    else requestSingle.launch(entry.manifestName)
                }
                PermissionStatus.PERMANENTLY_DENIED -> PermissionDenialHandler.handle(this, entry)
                PermissionStatus.GRANTED -> openAppSettings()
                PermissionStatus.NOT_APPLICABLE -> Unit
            }
        }

        view.findViewById<RecyclerView>(R.id.rv_permissions).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@PermissionsManagementFragment.adapter
        }

        view.findViewById<Button>(R.id.btn_grant_all).setOnClickListener {
            // Exclude special permissions — they require dedicated system settings screens
            // and are silently ignored when passed to requestMultiplePermissions().
            val denied = registry.getEntries()
                .filter { checkStatus(requireContext(), it) == PermissionStatus.DENIED }
                .filter { it.manifestName !in specialGrantPermissions }
                .map { it.manifestName }
                .toTypedArray()
            if (denied.isNotEmpty()) {
                // launchNextSpecialPermission() is called in the requestMultiple callback.
                requestMultiple.launch(denied)
            } else {
                // No regular permissions left — open the first pending special permission directly.
                launchNextSpecialPermission()
            }
        }

        view.findViewById<Button>(R.id.btn_open_system_settings).setOnClickListener {
            openAppSettings()
        }

        view.findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        val btnContinue = view.findViewById<Button>(R.id.btn_continue_to_app)
        if (fromWelcome) {
            (activity as? AppCompatActivity)?.supportActionBar?.setDisplayHomeAsUpEnabled(false)
            btnContinue.visibility = View.VISIBLE
            btnContinue.setOnClickListener {
                (activity as? WelcomeCompleteListener)?.onWelcomeComplete()
            }
        } else {
            btnContinue.visibility = View.GONE
        }

        refreshAdapter()
        updateGrantAllVisibility()
    }

    override fun onResume() {
        super.onResume()
        refreshAdapter()
        updateGrantAllVisibility()
    }

    private fun buildRows(): List<PermissionRow> {
        val entries = registry.getEntries()
        val headers = registry.getGroups()
        val rows = mutableListOf<PermissionRow>()
        headers.forEach { header ->
            rows += PermissionRow.Header(header)
            entries.filter { it.group == header.group }
                .forEach { entry -> rows += PermissionRow.Entry(entry, checkStatus(requireContext(), entry)) }
        }
        return rows
    }

    private fun refreshAdapter() = adapter.refresh(buildRows())

    private fun updateGrantAllVisibility() {
        val hasDenied = registry.getEntries().any { checkStatus(requireContext(), it) == PermissionStatus.DENIED }
        view?.findViewById<Button>(R.id.btn_grant_all)?.visibility = if (hasDenied) View.VISIBLE else View.GONE
    }

    /** Opens the system settings screen for the first still-denied special permission, one at a time. */
    private fun launchNextSpecialPermission() {
        val entry = registry.getEntries()
            .filter { it.manifestName in specialGrantPermissions }
            .firstOrNull { checkStatus(requireContext(), it) == PermissionStatus.DENIED }
        if (entry != null) launchSpecialGrantSettings(entry)
    }

    private fun launchSpecialGrantSettings(entry: PermissionEntry) {
        val pkg = requireContext().packageName
        val intent = when (entry.manifestName) {
            Manifest.permission.MANAGE_EXTERNAL_STORAGE -> when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> try {
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:$pkg")
                    }
                } catch (e: Exception) {
                    Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                }
                else -> null
            }
            Manifest.permission.MANAGE_MEDIA -> when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                    Intent(Settings.ACTION_REQUEST_MANAGE_MEDIA).apply {
                        data = Uri.parse("package:$pkg")
                    }
                else -> null
            }
            Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS ->
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$pkg")
                }
            else -> null
        }
        startActivity(intent ?: Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", pkg, null)
        })
    }

    private fun openAppSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", requireContext().packageName, null)
        })
    }
}
