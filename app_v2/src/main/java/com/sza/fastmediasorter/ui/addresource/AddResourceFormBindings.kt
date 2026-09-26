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
 *
 * S3735: a form's listeners and adapters are wired from [InflationHooks], fired once right after that
 * form is bound. Wiring every form from the host's view setup is what inflated all four stubs on each
 * open and defeated the laziness above.
 */
internal class AddResourceFormBindings(private val binding: ActivityAddResourceBinding) {

    interface InflationHooks {
        fun onLocal(form: ViewAddResourceLocalBinding)
        fun onSmb(form: ViewAddResourceSmbBinding)
        fun onSftp(form: ViewAddResourceSftpBinding)
        fun onCloud(form: ViewAddResourceCloudBinding)
    }

    private var hooks: InflationHooks? = null

    /** Registers [hooks] and replays them for any form a restore or result path inflated earlier. */
    fun setInflationHooks(hooks: InflationHooks) {
        this.hooks = hooks
        localBinding?.let(hooks::onLocal)
        smbBinding?.let(hooks::onSmb)
        sftpBinding?.let(hooks::onSftp)
        cloudBinding?.let(hooks::onCloud)
    }

    private var localBinding: ViewAddResourceLocalBinding? = null
    private var smbBinding: ViewAddResourceSmbBinding? = null
    private var sftpBinding: ViewAddResourceSftpBinding? = null
    private var cloudBinding: ViewAddResourceCloudBinding? = null

    val local: ViewAddResourceLocalBinding
        get() = localBinding
            ?: ViewAddResourceLocalBinding.bind(binding.stubLocalFolder.inflate()).also {
                localBinding = it
                hooks?.onLocal(it)
            }

    val smb: ViewAddResourceSmbBinding
        get() = smbBinding
            ?: ViewAddResourceSmbBinding.bind(binding.stubSmbFolder.inflate()).also {
                smbBinding = it
                hooks?.onSmb(it)
            }

    val sftp: ViewAddResourceSftpBinding
        get() = sftpBinding
            ?: ViewAddResourceSftpBinding.bind(binding.stubSftpFolder.inflate()).also {
                sftpBinding = it
                hooks?.onSftp(it)
            }

    val cloud: ViewAddResourceCloudBinding
        get() = cloudBinding
            ?: ViewAddResourceCloudBinding.bind(binding.stubCloudStorage.inflate()).also {
                cloudBinding = it
                hooks?.onCloud(it)
            }

    val localOrNull: ViewAddResourceLocalBinding? get() = localBinding
    val smbOrNull: ViewAddResourceSmbBinding? get() = smbBinding
    val sftpOrNull: ViewAddResourceSftpBinding? get() = sftpBinding
    val cloudOrNull: ViewAddResourceCloudBinding? get() = cloudBinding
}
