package org.tasks.injection

import android.app.Activity
import android.os.Bundle
import androidx.annotation.StringRes
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceScreen
import org.tasks.R
import org.tasks.dialogs.DialogBuilder
import org.tasks.preferences.Device
import org.tasks.themes.DrawableUtil
import javax.inject.Inject


abstract class InjectingPreferenceFragment : PreferenceFragmentCompat() {

    companion object {
        const val FRAG_TAG_TIME_PICKER = "frag_tag_time_picker"

        fun tintIcons(pref: Preference, color: Int) {
            if (pref is PreferenceGroup) {
                for (i in 0 until pref.preferenceCount) {
                    tintIcons(pref.getPreference(i), color)
                }
            } else {
                if (pref.icon != null) {
                    pref.icon = pref.icon.mutate()
                    pref.icon.setTint(color)
                }
            }
        }
    }

    @Inject lateinit var device: Device
    @Inject lateinit var dialogBuilder: DialogBuilder

    private var injected = false

    final override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(getPreferenceXml(), rootKey)

        tintIcons(preferenceScreen, requireContext().getColor(R.color.icon_tint_with_alpha))

        setupPreferences(savedInstanceState)
    }

    override fun onAttach(activity: Activity) {
        super.onAttach(activity)
        if (!injected) {
            inject((activity as InjectingActivity).component.plus(FragmentModule(this)))
            injected = true
        }
    }

    protected open fun showRestartDialog() {
        dialogBuilder
            .newDialog()
            .setMessage(R.string.restart_required)
            .setPositiveButton(R.string.restart_now) { _, _ -> restart() }
            .setNegativeButton(R.string.restart_later, null)
            .show()
    }

    protected fun restart() {
        kotlin.system.exitProcess(0)
    }

    protected fun tintColorPreference(resId: Int, tint: Int) {
        val pref = findPreference(resId)
        pref.icon = DrawableUtil.getWrapped(requireContext(), R.drawable.color_picker)
        DrawableUtil.setTint(pref.icon, tint)
    }

    protected fun requires(check: Boolean, vararg resIds: Int) {
        if (!check) {
            remove(preferenceScreen as PreferenceGroup, resIds)
        }
    }

    protected fun removeGroup(key: Int) {
        val preference = findPreference(key)
        (findPreference(R.string.preference_screen) as PreferenceScreen).removePreference(preference)
    }

    protected fun remove(vararg resIds: Int) {
        remove(preferenceScreen, resIds)
    }

    private fun remove(preferenceGroup: PreferenceGroup, resIds: IntArray) {
        for (resId in resIds) {
            val preference: Preference? = preferenceGroup.findPreference(getString(resId))
            if (preference != null) {
                preferenceGroup.removePreference(preference)
            }
        }
    }

    open fun getMenu() = R.menu.menu_preferences

    abstract fun getPreferenceXml(): Int

    abstract fun setupPreferences(savedInstanceState: Bundle?)

    protected fun recreate() {
        requireActivity().recreate()
    }

    protected fun findPreference(@StringRes prefId: Int): Preference {
        return findPreference(getString(prefId))!!
    }

    protected abstract fun inject(component: FragmentComponent)
}