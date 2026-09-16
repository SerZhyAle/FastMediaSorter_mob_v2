package com.sza.fastmediasorter.ui.settings.fragments

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.sza.fastmediasorter.databinding.FragmentOpenSourceLicensesBinding
import com.sza.fastmediasorter.ui.common.OverlayFocusTrap
import com.sza.fastmediasorter.ui.settings.OpenSourceLicenseAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import timber.log.Timber

@AndroidEntryPoint
class OpenSourceLicensesFragment : Fragment() {

    private var _binding: FragmentOpenSourceLicensesBinding? = null
    private val binding get() = _binding!!

    private var hiddenSiblings: List<View> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOpenSourceLicensesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // S2899: Hide the activity's underlying content so D-pad focus cannot escape the overlay.
        hiddenSiblings = OverlayFocusTrap.hideSiblings(view)

        binding.toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.noticesList.layoutManager = LinearLayoutManager(requireContext())

        // S3159: the notices are a raw resource parsed as JSON - reading them inline blocked the main
        // thread while the screen was being laid out. The adapter and the empty state are bound only
        // after the parse returns, so the list never flashes an empty state it then replaces.
        viewLifecycleOwner.lifecycleScope.launch {
            val notices = withContext(Dispatchers.IO) { loadNotices() }
            if (_binding == null) {
                return@launch
            }
            binding.noticesList.adapter = OpenSourceLicenseAdapter(notices, ::openUrl)
            binding.emptyState.visibility = if (notices.isEmpty()) View.VISIBLE else View.GONE
        }

        // S2899: Ensure initial focus on TV / D-pad
        view.post {
            if (isAdded && _binding != null) {
                binding.toolbar.requestFocus()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (activity?.currentFocus == null) {
            _binding?.toolbar?.requestFocus()
        }
    }

    private fun loadNotices(): List<OpenSourceLicenseAdapter.Notice> = try {
        val resourceId = requireContext().packageManager.getApplicationInfo(
            requireContext().packageName,
            PackageManager.GET_META_DATA
        ).metaData?.getInt(OSS_NOTICES_PAYLOAD_KEY) ?: 0
        val notices = JSONArray(resources.openRawResource(resourceId).bufferedReader().use { it.readText() })
        buildList {
            for (index in 0 until notices.length()) {
                val notice = notices.getJSONObject(index)
                add(
                    OpenSourceLicenseAdapter.Notice(
                        name = notice.getString("name"),
                        coordinate = notice.getString("coordinate"),
                        spdx = notice.getString("spdx"),
                        licenseUrl = notice.getString("licenseUrl"),
                        sourceUrl = notice.getString("sourceUrl"),
                        oss = notice.getBoolean("oss"),
                        // optString renders a JSON null as the four-letter string "null", which then
                        // passes isNotBlank and prints a "null" line under every entry that has no note.
                        note = notice.optString("note").takeIf { it.isNotBlank() && it != "null" }
                    )
                )
            }
        }
    } catch (exception: JSONException) {
        Timber.e(exception, "Unable to parse generated OSS notices")
        emptyList()
    } catch (exception: Resources.NotFoundException) {
        Timber.e(exception, "Generated OSS notices resource is unavailable")
        emptyList()
    } catch (exception: PackageManager.NameNotFoundException) {
        Timber.e(exception, "Unable to resolve the OSS notices resource")
        emptyList()
    }

    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Timber.w(e, "OpenSourceLicensesFragment: no browser to open URL")
            Toast.makeText(requireContext(), url, Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroyView() {
        OverlayFocusTrap.restore(hiddenSiblings)
        hiddenSiblings = emptyList()
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val OSS_NOTICES_PAYLOAD_KEY = "com.sza.fastmediasorter.OSS_NOTICES_PAYLOAD"
    }
}
