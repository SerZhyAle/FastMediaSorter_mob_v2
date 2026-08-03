package com.sza.fastmediasorter.ui.settings.helpers

import androidx.fragment.app.Fragment
import com.sza.fastmediasorter.databinding.FragmentSettingsGeneralBinding
import com.sza.fastmediasorter.ui.settings.SettingsViewModel

// S1351: plain classes on purpose, never data classes - detekt's LongParameterList.ignoreDataClasses
// defaults to true, so a data holder would be invisible to the very gate these bundles exist to
// satisfy (same precedent as ui/launcher/LauncherHomeDependencies.kt and
// ui/browse/BrowseViewModelDependencies.kt).

/** Shared host trio for `GeneralSettingsViewSetupHelper` - binding, view model and owning fragment. */
class GeneralSettingsHostContext(
    val binding: FragmentSettingsGeneralBinding,
    val viewModel: SettingsViewModel,
    val fragment: Fragment,
)

/** The five General Settings delegate helpers `setupActionButtons()` wires its buttons to. */
class GeneralSettingsActionHelpers(
    val cacheHelper: GeneralSettingsCacheHelper,
    val importExportHelper: GeneralSettingsImportExportHelper,
    val credentialHelper: GeneralSettingsCredentialHelper,
    val logHelper: GeneralSettingsLogHelper,
    val resetHelper: GeneralSettingsResetHelper,
)
