package com.sza.fastmediasorter.ui.addresource

import com.sza.fastmediasorter.databinding.ActivityAddResourceBinding
import com.sza.fastmediasorter.databinding.ViewAddResourceCloudBinding
import com.sza.fastmediasorter.databinding.ViewAddResourceLocalBinding
import com.sza.fastmediasorter.databinding.ViewAddResourceSftpBinding
import com.sza.fastmediasorter.databinding.ViewAddResourceSmbBinding

/**
 * S1519: lazy ViewStub-backed access to the four resource-type forms. The screen used to inflate all
 * four (roughly 560 of the layout's 671 lines) in one `setContentView` pass while showing exactly one;
 * each form now inflates on first access - whether that access is the user picking the type or a
 * prefill/copy flow touching a field - so the order of operations never matters for correctness.
 *
 * The inflated root keeps its XML `visibility="gone"`, so inflation alone never shows a form; the
 * `*OrNull` accessors let hide paths skip forms that were never inflated instead of inflating them
 * just to hide them.
 */
internal class AddResourceFormBindings(private val binding: ActivityAddResourceBinding) {

    private var localBinding: ViewAddResourceLocalBinding? = null
    private var smbBinding: ViewAddResourceSmbBinding? = null
    private var sftpBinding: ViewAddResourceSftpBinding? = null
    private var cloudBinding: ViewAddResourceCloudBinding? = null

    val local: ViewAddResourceLocalBinding
        get() = localBinding
            ?: ViewAddResourceLocalBinding.bind(binding.stubLocalFolder.inflate()).also {
                localBinding = it
            }

    val smb: ViewAddResourceSmbBinding
        get() = smbBinding
            ?: ViewAddResourceSmbBinding.bind(binding.stubSmbFolder.inflate()).also {
                smbBinding = it
            }

    val sftp: ViewAddResourceSftpBinding
        get() = sftpBinding
            ?: ViewAddResourceSftpBinding.bind(binding.stubSftpFolder.inflate()).also {
                sftpBinding = it
            }

    val cloud: ViewAddResourceCloudBinding
        get() = cloudBinding
            ?: ViewAddResourceCloudBinding.bind(binding.stubCloudStorage.inflate()).also {
                cloudBinding = it
            }

    val localOrNull: ViewAddResourceLocalBinding? get() = localBinding
    val smbOrNull: ViewAddResourceSmbBinding? get() = smbBinding
    val sftpOrNull: ViewAddResourceSftpBinding? get() = sftpBinding
    val cloudOrNull: ViewAddResourceCloudBinding? get() = cloudBinding
}
