package org.tasks.preferences.fragments

import android.app.Activity.RESULT_OK
import android.content.ComponentName
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import androidx.annotation.StringRes
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import com.todoroo.astrid.api.Filter
import org.tasks.BuildConfig
import org.tasks.LocalBroadcastManager
import org.tasks.R
import org.tasks.Strings.isNullOrEmpty
import org.tasks.activities.FilterSelectionActivity
import org.tasks.billing.Inventory
import org.tasks.billing.PurchaseActivity
import org.tasks.dialogs.ColorPalettePicker
import org.tasks.dialogs.ColorPalettePicker.Companion.newColorPalette
import org.tasks.dialogs.ColorPickerAdapter
import org.tasks.dialogs.ColorWheelPicker
import org.tasks.dialogs.ThemePickerDialog
import org.tasks.dialogs.ThemePickerDialog.Companion.newThemePickerDialog
import org.tasks.gtasks.PlayServices
import org.tasks.injection.FragmentComponent
import org.tasks.injection.InjectingPreferenceFragment
import org.tasks.locale.Locale
import org.tasks.locale.LocalePickerDialog
import org.tasks.preferences.DefaultFilterProvider
import org.tasks.preferences.Preferences
import org.tasks.themes.ThemeAccent
import org.tasks.themes.ThemeBase
import org.tasks.themes.ThemeBase.DEFAULT_BASE_THEME
import org.tasks.themes.ThemeBase.EXTRA_THEME_OVERRIDE
import org.tasks.themes.ThemeColor
import org.tasks.themes.ThemeColor.getLauncherColor
import org.tasks.ui.ChipProvider
import org.tasks.ui.NavigationDrawerFragment.Companion.REQUEST_PURCHASE
import org.tasks.ui.SingleCheckedArrayAdapter
import org.tasks.ui.Toaster
import javax.inject.Inject

private const val REQUEST_THEME_PICKER = 10001
private const val REQUEST_COLOR_PICKER = 10002
private const val REQUEST_ACCENT_PICKER = 10003
private const val REQUEST_LAUNCHER_PICKER = 10004
private const val REQUEST_DEFAULT_LIST = 10005
private const val REQUEST_LOCALE = 10006
private const val FRAG_TAG_LOCALE_PICKER = "frag_tag_locale_picker"
private const val FRAG_TAG_THEME_PICKER = "frag_tag_theme_picker"
private const val FRAG_TAG_COLOR_PICKER = "frag_tag_color_picker"

class LookAndFeel : InjectingPreferenceFragment() {

    @Inject lateinit var themeBase: ThemeBase
    @Inject lateinit var themeColor: ThemeColor
    @Inject lateinit var themeAccent: ThemeAccent
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var localBroadcastManager: LocalBroadcastManager
    @Inject lateinit var locale: Locale
    @Inject lateinit var defaultFilterProvider: DefaultFilterProvider
    @Inject lateinit var playServices: PlayServices
    @Inject lateinit var inventory: Inventory
    @Inject lateinit var toaster: Toaster
    @Inject lateinit var chipProvider: ChipProvider

    override fun getPreferenceXml() = R.xml.preferences_look_and_feel

    override fun setupPreferences(savedInstanceState: Bundle?) {
        val themePref = findPreference(R.string.p_theme)
        val themeNames = resources.getStringArray(R.array.base_theme_names)
        themePref.summary = themeNames[themeBase.index]
        themePref.setOnPreferenceClickListener {
            newThemePickerDialog(this, REQUEST_THEME_PICKER, themeBase.index)
                .show(parentFragmentManager, FRAG_TAG_THEME_PICKER)
            false
        }

        findPreference(R.string.p_chip_style).setOnPreferenceChangeListener { _, newValue ->
            chipProvider.setStyle(Integer.parseInt(newValue as String))
            true
        }

        findPreference(R.string.p_chip_appearance).setOnPreferenceChangeListener { _, newValue ->
            chipProvider.setAppearance(Integer.parseInt(newValue as String))
            true
        }

        findPreference(R.string.p_desaturate_colors).setOnPreferenceChangeListener { _, _ ->
            if (context?.resources?.configuration?.uiMode?.and(Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES) {
                activity?.recreate()
            }
            true
        }

        val sortGroups = findPreference(R.string.p_disable_sort_groups) as SwitchPreferenceCompat
        sortGroups.isChecked = sortGroups.isChecked || preferences.usePagedQueries()
        findPreference(R.string.p_use_paged_queries).setOnPreferenceChangeListener { _, value ->
            sortGroups.isChecked = value as Boolean
            true
        }

        val defaultList = findPreference(R.string.p_default_open_filter)
        val filter = defaultFilterProvider.defaultOpenFilter
        defaultList.summary = filter.listingTitle
        defaultList.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            val intent = Intent(context, FilterSelectionActivity::class.java)
            intent.putExtra(
                FilterSelectionActivity.EXTRA_FILTER,
                defaultFilterProvider.defaultOpenFilter
            )
            intent.putExtra(FilterSelectionActivity.EXTRA_RETURN_FILTER, true)
            startActivityForResult(intent, REQUEST_DEFAULT_LIST)
            true
        }

        val languagePreference = findPreference(R.string.p_language)
        updateLocale()
        languagePreference.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            val dialog = LocalePickerDialog.newLocalePickerDialog()
            dialog.setTargetFragment(this, REQUEST_LOCALE)
            dialog.show(parentFragmentManager, FRAG_TAG_LOCALE_PICKER)
            false
        }

        @Suppress("ConstantConditionIf")
        if (BuildConfig.FLAVOR != "googleplay") {
            removeGroup(R.string.TEA_control_location)
        }
    }

    override fun onResume() {
        super.onResume()

        setupColorPreference(
            R.string.p_theme_color,
            themeColor.pickerColor,
            ColorPickerAdapter.Palette.COLORS,
            REQUEST_COLOR_PICKER
        )
        setupColorPreference(
            R.string.p_theme_accent,
            themeAccent.pickerColor,
            ColorPickerAdapter.Palette.ACCENTS,
            REQUEST_ACCENT_PICKER
        )
        updateLauncherPreference()

        @Suppress("ConstantConditionIf")
        if (BuildConfig.FLAVOR == "googleplay") {
            setupLocationPickers()
        }
    }

    private fun updateLauncherPreference() {
        val launcher = getLauncherColor(context, preferences.getInt(R.string.p_theme_launcher, 7))
        setupColorPreference(
            R.string.p_theme_launcher,
            launcher.pickerColor,
            ColorPickerAdapter.Palette.LAUNCHERS,
            REQUEST_LAUNCHER_PICKER
        )
    }

    private fun setupLocationPickers() {
        val choices =
            listOf(getString(R.string.map_provider_mapbox), getString(R.string.map_provider_google))
        val singleCheckedArrayAdapter = SingleCheckedArrayAdapter(requireContext(), choices)
        val mapProviderPreference = findPreference(R.string.p_map_provider)
        mapProviderPreference.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            dialogBuilder
                .newDialog()
                .setSingleChoiceItems(
                    singleCheckedArrayAdapter,
                    getMapProvider()
                ) { dialog: DialogInterface, which: Int ->
                    if (which == 1) {
                        if (!playServices.refreshAndCheck()) {
                            playServices.resolve(activity)
                            dialog.dismiss()
                            return@setSingleChoiceItems
                        }
                    }
                    preferences.setInt(R.string.p_map_provider, which)
                    mapProviderPreference.summary = choices[which]
                    dialog.dismiss()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            false
        }
        val mapProvider: Int = getMapProvider()
        mapProviderPreference.summary =
            if (mapProvider == -1) getString(R.string.none) else choices[mapProvider]

        val placeProviderPreference = findPreference(R.string.p_place_provider)
        placeProviderPreference.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            dialogBuilder
                .newDialog()
                .setSingleChoiceItems(
                    singleCheckedArrayAdapter,
                    getPlaceProvider()
                ) { dialog: DialogInterface, which: Int ->
                    if (which == 1) {
                        if (!playServices.refreshAndCheck()) {
                            playServices.resolve(activity)
                            dialog.dismiss()
                            return@setSingleChoiceItems
                        }
                        if (!inventory.hasPro()) {
                            toaster.longToast(R.string.requires_pro_subscription)
                            dialog.dismiss()
                            return@setSingleChoiceItems
                        }
                    }
                    preferences.setInt(R.string.p_place_provider, which)
                    placeProviderPreference.summary = choices[which]
                    dialog.dismiss()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            false
        }
        val placeProvider: Int = getPlaceProvider()
        placeProviderPreference.summary = choices[placeProvider]
    }

    private fun getPlaceProvider(): Int {
        return if (playServices.isPlayServicesAvailable && inventory.hasPro()) preferences.getInt(
            R.string.p_place_provider,
            0
        ) else 0
    }

    private fun getMapProvider(): Int {
        return if (playServices.isPlayServicesAvailable) preferences.getInt(
            R.string.p_map_provider,
            0
        ) else 0
    }

    private fun setBaseTheme(index: Int) {
        activity?.intent?.removeExtra(EXTRA_THEME_OVERRIDE)
        preferences.setInt(R.string.p_theme, index)
        if (themeBase.index != index) {
            Handler().post {
                ThemeBase(index).setDefaultNightMode()
                recreate()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_PURCHASE) {
            val index = if (inventory.hasPro()) {
                data?.getIntExtra(ThemePickerDialog.EXTRA_SELECTED, DEFAULT_BASE_THEME)
                    ?: themeBase.index
            } else {
                preferences.themeBase
            }
            setBaseTheme(index)
        } else if (requestCode == REQUEST_THEME_PICKER) {
            val index = data?.getIntExtra(ThemePickerDialog.EXTRA_SELECTED, DEFAULT_BASE_THEME)
                ?: preferences.themeBase
            if (resultCode == RESULT_OK) {
                if (inventory.purchasedThemes() || ThemeBase(index).isFree) {
                    setBaseTheme(index)
                } else {
                    startActivityForResult(
                        Intent(context, PurchaseActivity::class.java),
                        REQUEST_PURCHASE
                    )
                }
            } else {
                setBaseTheme(index)
            }
        } else if (requestCode == REQUEST_COLOR_PICKER) {
            if (resultCode == RESULT_OK) {
                val color = data?.getIntExtra(
                    ColorWheelPicker.EXTRA_SELECTED,
                    themeColor.primaryColor
                )
                    ?: themeColor.primaryColor
                if (preferences.defaultThemeColor != color) {
                    preferences.setInt(R.string.p_theme_color, color)
                    recreate()
                }
            }
        } else if (requestCode == REQUEST_ACCENT_PICKER) {
            if (resultCode == RESULT_OK) {
                val index = data!!.getIntExtra(ColorPalettePicker.EXTRA_SELECTED, 0)
                if (preferences.getInt(R.string.p_theme_accent, -1) != index) {
                    preferences.setInt(R.string.p_theme_accent, index)
                    recreate()
                }
            }
        } else if (requestCode == REQUEST_LAUNCHER_PICKER) {
            if (resultCode == RESULT_OK) {
                val index = data!!.getIntExtra(ColorPalettePicker.EXTRA_SELECTED, 0)
                setLauncherIcon(index)
                preferences.setInt(R.string.p_theme_launcher, index)
                updateLauncherPreference()
            }
        } else if (requestCode == REQUEST_DEFAULT_LIST) {
            if (resultCode == RESULT_OK) {
                val filter: Filter =
                    data!!.getParcelableExtra(FilterSelectionActivity.EXTRA_FILTER)!!
                defaultFilterProvider.defaultOpenFilter = filter
                findPreference(R.string.p_default_open_filter).summary = filter.listingTitle
                localBroadcastManager.broadcastRefresh()
            }
        } else if (requestCode == REQUEST_LOCALE) {
            if (resultCode == RESULT_OK) {
                val newValue: Locale =
                    data!!.getSerializableExtra(LocalePickerDialog.EXTRA_LOCALE) as Locale
                val override: String? = newValue.languageOverride
                if (isNullOrEmpty(override)) {
                    preferences.remove(R.string.p_language)
                } else {
                    preferences.setString(R.string.p_language, override)
                }
                updateLocale()
                if (locale != newValue) {
                    showRestartDialog()
                }
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun updateLocale() {
        val languagePreference = findPreference(R.string.p_language)
        val preference = preferences.getStringValue(R.string.p_language)
        languagePreference.summary = locale.withLanguage(preference).displayName
    }

    private fun setLauncherIcon(index: Int) {
        val packageManager: PackageManager? = context?.packageManager
        for (i in ThemeColor.LAUNCHERS.indices) {
            val componentName = ComponentName(
                requireContext(),
                "com.todoroo.astrid.activity.TaskListActivity" + ThemeColor.LAUNCHERS[i]
            )
            packageManager?.setComponentEnabledSetting(
                componentName,
                if (index == i) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        }
    }

    override fun inject(component: FragmentComponent) {
        component.inject(this)
    }

    private fun setupColorPreference(
        @StringRes prefId: Int,
        color: Int,
        palette: ColorPickerAdapter.Palette,
        requestCode: Int
    ) {
        tintColorPreference(prefId, color)
        findPreference(prefId).setOnPreferenceClickListener {
            newColorPalette(this, requestCode, color, palette)
                .show(parentFragmentManager, FRAG_TAG_COLOR_PICKER)
            false
        }
    }
}