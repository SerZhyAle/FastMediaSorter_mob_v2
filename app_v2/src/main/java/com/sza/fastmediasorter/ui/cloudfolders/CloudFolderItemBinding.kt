package com.sza.fastmediasorter.ui.cloudfolders

import android.view.View
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.sza.fastmediasorter.databinding.ItemDropboxFolderBinding
import com.sza.fastmediasorter.databinding.ItemGoogleDriveFolderBinding
import com.sza.fastmediasorter.databinding.ItemOnedriveFolderBinding

/**
 * Thin sealed abstraction over three structurally identical cloud folder item layouts.
 * All three XMLs share the same view IDs — this avoids reflection while keeping a single adapter.
 */
sealed interface CloudFolderItemBinding {
    val root: View
    val tvFolderName: TextView
    val btnSelect: MaterialButton
    val btnEnter: MaterialButton
    val btnBack: MaterialButton

    class GDrive(private val b: ItemGoogleDriveFolderBinding) : CloudFolderItemBinding {
        override val root get() = b.root
        override val tvFolderName get() = b.tvFolderName
        override val btnSelect get() = b.btnSelect
        override val btnEnter get() = b.btnEnter
        override val btnBack get() = b.btnBack
    }

    class Dropbox(private val b: ItemDropboxFolderBinding) : CloudFolderItemBinding {
        override val root get() = b.root
        override val tvFolderName get() = b.tvFolderName
        override val btnSelect get() = b.btnSelect
        override val btnEnter get() = b.btnEnter
        override val btnBack get() = b.btnBack
    }

    class OneDrive(private val b: ItemOnedriveFolderBinding) : CloudFolderItemBinding {
        override val root get() = b.root
        override val tvFolderName get() = b.tvFolderName
        override val btnSelect get() = b.btnSelect
        override val btnEnter get() = b.btnEnter
        override val btnBack get() = b.btnBack
    }
}
