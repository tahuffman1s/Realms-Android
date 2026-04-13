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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.realmsoffate.game.game.GameViewModel
import com.realmsoffate.game.game.Screen
import com.realmsoffate.game.ui.game.GameScreen
import com.realmsoffate.game.ui.setup.ApiSetupScreen
import com.realmsoffate.game.ui.setup.CharacterCreationScreen
import com.realmsoffate.game.ui.setup.DeathScreen
import com.realmsoffate.game.ui.setup.TitleScreen
import com.realmsoffate.game.ui.theme.RealmsTheme

class MainActivity : ComponentActivity() {

    private val viewModel: GameViewModel by viewModels { GameViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            RealmsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    RealmsRoot(viewModel)
                }
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
    }
}
