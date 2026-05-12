package com.sza.fastmediasorter.ui.settings.auth

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import com.google.android.material.appbar.MaterialToolbar
import com.sza.fastmediasorter.R
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AuthSessionsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auth_sessions)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                replace(R.id.authSessionsContainer, AuthSessionsListFragment())
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, AuthSessionsActivity::class.java))
        }
    }
}