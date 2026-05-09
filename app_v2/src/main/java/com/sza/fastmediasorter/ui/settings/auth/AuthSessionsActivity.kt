package com.sza.fastmediasorter.ui.settings.auth

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import com.sza.fastmediasorter.R
import dagger.hilt.android.AndroidEntryPoint

/**
 * S0116 §5.1 pillar K: thin host Activity for [AuthSessionsListFragment].
 *
 * Settings UI uses ViewPager2 tabs at the top level (`SettingsActivity`), so a
 * sub-screen launched from a settings tab cannot be a fragment-replace — it has
 * to be its own host. Mirrors the project's existing pattern of opening
 * dedicated Activities for non-tab settings surfaces.
 */
@AndroidEntryPoint
class AuthSessionsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auth_sessions)
        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                replace(R.id.authSessionsContainer, AuthSessionsListFragment())
            }
        }
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, AuthSessionsActivity::class.java))
        }
    }
}
