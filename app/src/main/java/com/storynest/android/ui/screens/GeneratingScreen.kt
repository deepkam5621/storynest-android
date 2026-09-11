package com.storynest.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.storynest.android.viewmodel.CreateBookViewModel
import com.storynest.android.viewmodel.CreateUiState

@Composable
fun GeneratingScreen(
    viewModel: CreateBookViewModel,
    onDone: (String) -> Unit,
    onFailedHome: () -> Unit,
    onSettings: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state) {
        val s = state
        if (s is CreateUiState.Success) {
            onDone(s.bookId)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (val s = state) {
            is CreateUiState.Idle, is CreateUiState.Running -> {
                CircularProgressIndicator(Modifier.size(56.dp))
                Spacer(Modifier.height(20.dp))
                Text("Nesting your story…", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Writing pages with Gemini. Illustrations come next.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            is CreateUiState.Progress -> {
                CircularProgressIndicator(Modifier.size(56.dp))
                Spacer(Modifier.height(20.dp))
                Text(s.stage, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                if (s.totalPages > 0) {
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { s.currentPage.toFloat() / s.totalPages.toFloat() },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "${s.currentPage} / ${s.totalPages}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                s.warning?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
            is CreateUiState.Success -> {
                Text("Ready!", style = MaterialTheme.typography.titleLarge)
                s.warning?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.secondary)
                }
            }
            is CreateUiState.Error -> {
                Text("Couldn’t finish", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    s.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))
                if (s.needsApiKey) {
                    Button(onClick = onSettings, modifier = Modifier.fillMaxWidth()) {
                        Text("Add API key")
                    }
                    Spacer(Modifier.height(8.dp))
                }
                TextButton(onClick = onFailedHome) { Text("Back to library") }
            }
        }
    }
}
