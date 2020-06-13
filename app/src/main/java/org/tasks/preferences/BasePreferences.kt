package org.tasks.preferences

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import org.tasks.R
import org.tasks.databinding.ActivityPreferencesBinding
import org.tasks.injection.InjectingPreferenceFragment
import org.tasks.injection.ThemedInjectingAppCompatActivity

private const val EXTRA_TITLE = "extra_title"

abstract class BasePreferences : ThemedInjectingAppCompatActivity(),
    PreferenceFragmentCompat.OnPreferenceStartFragmentCallback, Toolbar.OnMenuItemClickListener {

    lateinit var toolbar: Toolbar
    var menu: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityPreferencesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        toolbar = binding.toolbar.toolbar
        if (savedInstanceState == null) {
            val rootPreference = getRootPreference()
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, rootPreference)
                .commit()
            toolbar.title = getString(getRootTitle())
            setupMenu(rootPreference)
        } else {
            toolbar.title = savedInstanceState.getCharSequence(EXTRA_TITLE)
            setupMenu()
        }
        supportFragmentManager.addOnBackStackChangedListener {
            if (supportFragmentManager.backStackEntryCount == 0) {
                toolbar.title = getString(getRootTitle())
            }
        }
        toolbar.navigationIcon =
            getDrawable(R.drawable.ic_outline_arrow_back_24px)
        toolbar.setNavigationOnClickListener { onBackPressed() }
        toolbar.setOnMenuItemClickListener(this)
        themeColor.apply(toolbar)
    }

    private fun setupMenu() = setupMenu(supportFragmentManager.findFragmentById(R.id.settings))

    private fun setupMenu(fragment: Fragment?) {
        this.menu = if (fragment is InjectingPreferenceFragment) fragment.getMenu() else 0
        toolbar.menu.clear()
        if (menu > 0) {
            toolbar.inflateMenu(menu)
        }
    }

    abstract fun getRootTitle(): Int

    abstract fun getRootPreference(): InjectingPreferenceFragment

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putCharSequence(EXTRA_TITLE, toolbar.title)
    }

    override fun onBackPressed() {
        if (supportFragmentManager.popBackStackImmediate()) {
            setupMenu()
        } else {
            super.onBackPressed()
        }
    }

    override fun onPreferenceStartFragment(
        caller: PreferenceFragmentCompat,
        pref: Preference
    ): Boolean {
        val args = pref.extras
        val fragment = supportFragmentManager.fragmentFactory.instantiate(
            classLoader,
            pref.fragment
        ).apply {
            arguments = args
            setTargetFragment(caller, 0)
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.settings, fragment)
            .addToBackStack(null)
            .commit()
        toolbar.title = pref.title
        setupMenu(fragment)
        return true
    }

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        return if (item?.itemId == R.id.menu_help_and_feedback) {
            startActivity(Intent(this, HelpAndFeedback::class.java))
            true
        } else {
            false
        }
    }
}