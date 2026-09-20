package com.focussystems.watchllm.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.focussystems.watchllm.llm.presetById
import com.focussystems.watchllm.presentation.theme.WatchllmTheme

private object Route {
    const val PRESETS = "presets"
    const val INPUT = "input/{id}"
    const val TYPE = "type/{id}"
    const val RESULT = "result"
    fun input(id: String) = "input/$id"
    fun type(id: String) = "type/$id"
}

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by viewModel.state.collectAsState()
            val nav = rememberSwipeDismissableNavController()
            WatchllmTheme {
                AppScaffold {
                    SwipeDismissableNavHost(navController = nav, startDestination = Route.PRESETS) {
                        composable(Route.PRESETS) {
                            PresetsScreen(onPreset = { nav.navigate(Route.input(it.id)) })
                        }
                        composable(Route.INPUT) { entry ->
                            val preset = presetById(entry.arguments?.getString("id"))
                            if (preset == null) nav.popBackStack()
                            else InputScreen(
                                preset = preset,
                                ready = state.phase == Phase.Ready,
                                onType = { nav.navigate(Route.type(preset.id)) },
                                onSend = { text ->
                                    viewModel.ask(preset, text)
                                    nav.navigate(Route.RESULT)
                                },
                            )
                        }
                        composable(Route.TYPE) { entry ->
                            val preset = presetById(entry.arguments?.getString("id"))
                            if (preset == null) nav.popBackStack()
                            else TypeScreen(preset = preset, onSend = { text ->
                                viewModel.ask(preset, text)
                                // Result replaces the typing screen so Back returns to the input step.
                                nav.navigate(Route.RESULT) { popUpTo(Route.TYPE) { inclusive = true } }
                            })
                        }
                        composable(Route.RESULT) {
                            ResultScreen(
                                state = state,
                                onStop = viewModel::stop,
                                onLeave = viewModel::stop, // leaving the screen cancels a running reply
                                onDone = { nav.popBackStack() },
                            )
                        }
                    }
                }
            }
        }
    }

    // Generation only runs while the app is in the foreground.
    override fun onStop() {
        viewModel.stop()
        super.onStop()
    }
}
