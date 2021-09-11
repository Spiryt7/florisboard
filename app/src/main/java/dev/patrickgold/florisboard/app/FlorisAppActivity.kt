/*
 * Copyright (C) 2021 Patrick Goldinger
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.patrickgold.florisboard.app.prefs.AppPrefs
import dev.patrickgold.florisboard.app.ui.Routes
import dev.patrickgold.florisboard.app.ui.components.SystemUi
import dev.patrickgold.florisboard.app.ui.settings.AdvancedScreen
import dev.patrickgold.florisboard.app.ui.settings.HomeScreen
import dev.patrickgold.florisboard.app.ui.settings.about.AboutScreen
import dev.patrickgold.florisboard.app.ui.settings.about.ProjectLicenseScreen
import dev.patrickgold.florisboard.app.ui.settings.about.ThirdPartyLicensesScreen
import dev.patrickgold.florisboard.app.ui.theme.FlorisAppTheme
import dev.patrickgold.florisboard.util.AndroidVersion
import dev.patrickgold.florisboard.util.PackageManagerUtils
import dev.patrickgold.jetpref.datastore.preferenceModel

enum class AppTheme(val id: String) {
    AUTO("auto"),
    LIGHT("light"),
    DARK("dark"),
    AMOLED_DARK("amoled_dark"),
}

val LocalNavController = staticCompositionLocalOf<NavController> {
    error("LocalNavController not initialized")
}

class FlorisAppActivity : ComponentActivity() {
    val prefs by preferenceModel(::AppPrefs)
    val appTheme = mutableStateOf(AppTheme.AUTO)
    var showAppIcon = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs.advanced.settingsTheme.observe(this) {
            appTheme.value = it
        }
        if (AndroidVersion.ATMOST_P) {
            prefs.advanced.showAppIcon.observe(this) {
                showAppIcon = it
            }
        }

        setContent {
            FlorisAppTheme(theme = appTheme.value) {
                Surface(color = MaterialTheme.colors.background) {
                    SystemUi()
                    AppContent()
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()

        // App icon visibility control was restricted in Android 10.
        // See https://developer.android.com/reference/android/content/pm/LauncherApps#getActivityList(java.lang.String,%20android.os.UserHandle)
        if (AndroidVersion.ATMOST_P) {
            if (showAppIcon) {
                PackageManagerUtils.showAppIcon(this)
            } else {
                PackageManagerUtils.hideAppIcon(this)
            }
        } else {
            PackageManagerUtils.showAppIcon(this)
        }
    }
}

@Composable
private fun AppContent() {
    val navController = rememberNavController()
    CompositionLocalProvider(
        LocalNavController provides navController,
    ) {
        NavHost(navController = navController, startDestination = Routes.Settings.Home) {
            composable(Routes.Settings.Home) { HomeScreen() }

            composable(Routes.Settings.Advanced) { AdvancedScreen() }

            composable(Routes.Settings.About) { AboutScreen() }
            composable(Routes.Settings.ProjectLicense) { ProjectLicenseScreen() }
            composable(Routes.Settings.ThirdPartyLicenses) { ThirdPartyLicensesScreen() }
        }
    }
}