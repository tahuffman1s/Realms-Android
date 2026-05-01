package com.realmsoffate.game

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.realmsoffate.game.data.updater.InstallLauncher
import com.realmsoffate.game.data.updater.UpdateRepositoryHolder
import com.realmsoffate.game.data.updater.UpdateState
import com.realmsoffate.game.game.GameViewModel
import com.realmsoffate.game.game.Screen
import com.realmsoffate.game.ui.game.GameScreen
import com.realmsoffate.game.ui.setup.ApiSetupScreen
import com.realmsoffate.game.ui.setup.CharacterCreationScreen
import com.realmsoffate.game.ui.settings.SettingsScreen
import com.realmsoffate.game.ui.setup.DeathScreen
import com.realmsoffate.game.ui.setup.TitleScreen
import com.realmsoffate.game.ui.theme.RealmsTheme

class MainActivity : ComponentActivity() {

    private val viewModel: GameViewModel by viewModels { GameViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        com.realmsoffate.game.debug.DebugHook.onAttach?.invoke(this, viewModel)
        setContent {
            RealmsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    RealmsRoot(viewModel)
                    ObserveUpdateInstall(this@MainActivity)
                }
            }
        }
    }
}

@Composable
private fun ObserveUpdateInstall(activity: ComponentActivity) {
    val state by UpdateRepositoryHolder.instance.state.collectAsState()
    LaunchedEffect(state) {
        val s = state
        if (s is UpdateState.ReadyToInstall) {
            val mismatch = BuildConfig.DEBUG &&
                InstallLauncher.signatureMismatchAgainstInstalled(activity, s.file)
            val launch = {
                runCatching { activity.startActivity(InstallLauncher.buildIntent(activity, s.file)) }
            }
            if (mismatch) {
                android.app.AlertDialog.Builder(activity)
                    .setTitle("Replace debug build?")
                    .setMessage(
                        "Installing the release build will conflict with this debug build's " +
                        "signing certificate. Android will require you to uninstall first, which " +
                        "deletes save data. Continue?"
                    )
                    .setPositiveButton("Continue") { _, _ -> launch() }
                    .setNegativeButton("Cancel", null)
                    .show()
            } else {
                launch()
            }
        }
    }
}

@Composable
fun RealmsRoot(vm: GameViewModel) {
    val screen by vm.screen.collectAsState()
    when (screen) {
        Screen.ApiSetup -> ApiSetupScreen(vm)
        Screen.Title -> TitleScreen(vm)
        Screen.CharacterCreation -> CharacterCreationScreen(vm)
        Screen.Game -> GameScreen(vm)
        Screen.Death -> DeathScreen(vm)
        Screen.Settings -> SettingsScreen(vm)
    }
}
