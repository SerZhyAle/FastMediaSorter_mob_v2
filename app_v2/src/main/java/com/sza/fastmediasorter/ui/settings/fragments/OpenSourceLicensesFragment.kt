package com.sza.fastmediasorter.ui.settings.fragments

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.sza.fastmediasorter.databinding.FragmentOpenSourceLicensesBinding

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

        // SMBJ
        binding.btnSmbjSource.setOnClickListener {
            openUrl("https://github.com/hierynomus/smbj")
        }
        binding.btnSmbjLicense.setOnClickListener {
            openUrl("https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html")
        }

        // epub4j
        binding.btnEpub4jSource.setOnClickListener {
            openUrl("https://github.com/documentnode/epub4j")
        }
        binding.btnEpub4jLicense.setOnClickListener {
            openUrl("https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html")
        }
    }

    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
