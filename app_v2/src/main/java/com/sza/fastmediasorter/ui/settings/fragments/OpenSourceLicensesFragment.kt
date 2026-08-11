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
import androidx.recyclerview.widget.LinearLayoutManager
import com.sza.fastmediasorter.databinding.FragmentOpenSourceLicensesBinding
import com.sza.fastmediasorter.ui.settings.OpenSourceLicenseAdapter
import dagger.hilt.android.AndroidEntryPoint
import org.json.JSONArray
import org.json.JSONException
import timber.log.Timber

@AndroidEntryPoint
class OpenSourceLicensesFragment : Fragment() {

    private var _binding: FragmentOpenSourceLicensesBinding? = null
    private val binding get() = _binding!!

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

        binding.toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }

        val notices = loadNotices()
        binding.noticesList.layoutManager = LinearLayoutManager(requireContext())
        binding.noticesList.adapter = OpenSourceLicenseAdapter(notices, ::openUrl)
        binding.emptyState.visibility = if (notices.isEmpty()) View.VISIBLE else View.GONE
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
                        note = notice.optString("note").takeIf { it.isNotBlank() }
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
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        const val OSS_NOTICES_PAYLOAD_KEY = "com.sza.fastmediasorter.OSS_NOTICES_PAYLOAD"
    }
}
