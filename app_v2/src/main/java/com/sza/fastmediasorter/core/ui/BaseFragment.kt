package com.sza.fastmediasorter.core.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding
import timber.log.Timber

/**
 * Base Fragment that provides common functionality for all fragments.
 * - Manages ViewBinding lifecycle
 * - Provides logging
 */
abstract class BaseFragment<VB : ViewBinding> : Fragment() {

    private var _binding: VB? = null
    protected val binding: VB
        get() = _binding ?: throw IllegalStateException("Binding is only valid between onCreateView and onDestroyView")

    abstract fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?): VB
    abstract fun setupViews()
    abstract fun observeData()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Timber.d("onCreateView: ${this::class.simpleName}")
        _binding = getViewBinding(inflater, container)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Timber.d("onViewCreated: ${this::class.simpleName}")
        setupViews()
        observeData()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        Timber.d("onDestroyView: ${this::class.simpleName}")
    }
}
