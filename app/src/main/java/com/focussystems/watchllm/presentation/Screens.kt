package com.focussystems.watchllm.presentation

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.focussystems.watchllm.llm.PRESETS
import com.focussystems.watchllm.llm.Preset

/** Step 1: pick a preset. */
@Composable
fun PresetsScreen(onPreset: (Preset) -> Unit) {
    val listState = rememberScalingLazyListState()
    ScreenScaffold(scrollState = listState) { padding ->
        ScalingLazyColumn(state = listState, contentPadding = padding) {
            item { ListHeader { Text("Ask") } }
            items(PRESETS.size) { i ->
                val preset = PRESETS[i]
                Button(
                    onClick = { onPreset(preset) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.filledTonalButtonColors(),
                ) { Text(preset.label) }
            }
        }
    }
}

/**
 * Input step for a preset. Keyboard (step 2) and voice (step 3) plug in here; for now it offers the
 * preset's sample input so each preset's system prompt can be tested on the watch.
 */
@Composable
fun InputScreen(preset: Preset, ready: Boolean, onSend: (String) -> Unit) {
    val listState = rememberTransformingLazyColumnState()
    ScreenScaffold(
        scrollState = listState,
        edgeButton = {
            EdgeButton(onClick = { onSend(preset.sampleInput) }, enabled = ready) {
                Text(if (ready) "Try sample" else "Loading…")
            }
        },
    ) { padding ->
        TransformingLazyColumn(state = listState, contentPadding = padding) {
            item { ListHeader { Text(preset.label) } }
            item {
                Text(
                    text = "\"${preset.sampleInput}\"",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Streamed reply + t/s footer. Leaving this screen cancels generation. */
@Composable
fun ResultScreen(state: UiState, onStop: () -> Unit, onLeave: () -> Unit, onDone: () -> Unit) {
    val generating = state.phase == Phase.Generating

    // Keep the screen on only while a reply is being generated.
    val view = LocalView.current
    SideEffect { view.keepScreenOn = generating }
    DisposableEffect(Unit) { onDispose { view.keepScreenOn = false; onLeave() } }

    val listState = rememberTransformingLazyColumnState()
    // Follow the streaming text: item 0 is a top spacer, 1 the reply, 2 the t/s footer.
    LaunchedEffect(state.reply.length, generating) {
        if (generating) listState.scrollToItem(2)
    }
    // When the reply is done, go back to its beginning so it can be read from the top.
    LaunchedEffect(generating) {
        if (!generating) listState.scrollToItem(0)
    }
    ScreenScaffold(
        scrollState = listState,
        edgeButton = {
            EdgeButton(
                onClick = if (generating) onStop else onDone,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            ) { Text(if (generating) "Stop" else "Done") }
        },
    ) { padding ->
        TransformingLazyColumn(contentPadding = padding, state = listState) {
            // Keeps the first line out of the narrow, clipped top of the round screen.
            item { Spacer(Modifier.height(24.dp)) }
            item {
                Text(
                    text = state.error ?: state.reply.ifEmpty { "…" },
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
