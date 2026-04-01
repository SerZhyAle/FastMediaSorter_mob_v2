package com.sza.fastmediasorter.ui.duplicates

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ActivityDuplicatesBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class DuplicatesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDuplicatesBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDuplicatesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, DuplicatesFragment())
                .commit()
        }
    }
}
