package com.focussystems.watchllm.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.focussystems.watchllm.presentation.theme.WatchllmTheme

private const val DEMO_PROMPT = "Explain what a black hole is."

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by viewModel.state.collectAsState()
            WatchLlmScreen(
                state = state,
                onAsk = { viewModel.ask(DEMO_PROMPT) },
                onStop = viewModel::stop,
            )
        }
    }
}

@Composable
fun WatchLlmScreen(state: UiState, onAsk: () -> Unit, onStop: () -> Unit) {
    val generating = state.phase == Phase.Generating

    // Keep the screen on only while a reply is being generated.
    val view = LocalView.current
    SideEffect { view.keepScreenOn = generating }

    WatchllmTheme {
        AppScaffold {
            val listState = rememberTransformingLazyColumnState()
            // Follow the streaming text: item 0 is the reply, item 1 the t/s footer.
            LaunchedEffect(state.reply.length, generating) {
                if (generating) listState.scrollToItem(1)
            }
            ScreenScaffold(
                scrollState = listState,
                edgeButton = {
                    EdgeButton(
                        onClick = if (generating) onStop else onAsk,
                        enabled = state.phase == Phase.Ready || generating,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                    ) {
                        Text(
                            when (state.phase) {
                                Phase.Loading -> "Loading…"
                                Phase.Generating -> "Stop"
                                else -> "Ask"
                            },
                        )
                    }
                },
            ) { contentPadding ->
                TransformingLazyColumn(contentPadding = contentPadding, state = listState) {
                    item {
                        Text(
                            text = state.error ?: state.reply.ifEmpty { hint(state.phase) },
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (state.error != null) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    item {
                        Text(
                            text = state.stats?.let { "%.1f t/s".format(it.tokensPerSecond) } ?: " ",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun hint(phase: Phase) = when (phase) {
    Phase.Loading -> "Loading model…"
    Phase.Generating -> "…"
    else -> "\"$DEMO_PROMPT\""
}
