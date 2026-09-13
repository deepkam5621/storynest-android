package com.storynest.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.storynest.android.ui.theme.CardSunny
import com.storynest.android.ui.theme.SoftCoral
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
            .background(MaterialTheme.colorScheme.background)
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (val s = state) {
            is CreateUiState.Idle, is CreateUiState.Running -> {
                NestSpinner()
                Spacer(Modifier.height(20.dp))
                Text(
                    "🪺 Nesting your story…",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Writing magical pages… pictures come next! ✨",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            is CreateUiState.Progress -> {
                NestSpinner()
                Spacer(Modifier.height(20.dp))
                Text(
                    playfulStage(s.stage, s.currentPage),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                if (s.totalPages > 0) {
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { s.currentPage.toFloat() / s.totalPages.toFloat() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        color = SoftCoral,
                        trackColor = SoftCoral.copy(alpha = 0.2f)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Page ${s.currentPage} of ${s.totalPages}",
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
                Text("🎉 Ready!", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                s.warning?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.secondary)
                }
            }
            is CreateUiState.Error -> {
                Text("😅 Couldn’t finish", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    s.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))
                if (s.needsApiKey) {
                    Button(
                        onClick = onSettings,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SoftCoral)
                    ) {
                        Text("Add API key")
                    }
                    Spacer(Modifier.height(8.dp))
                }
                TextButton(onClick = onFailedHome) { Text("Back to library") }
            }
        }
    }
}

@Composable
private fun NestSpinner() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(CardSunny),
            contentAlignment = Alignment.Center
        ) {
            Text("🪺", fontSize = 40.sp)
        }
        Spacer(Modifier.height(16.dp))
        CircularProgressIndicator(
            modifier = Modifier.size(40.dp),
            color = SoftCoral,
            strokeWidth = 4.dp
        )
    }
}

/** Make repository stage strings a bit more kid-friendly without changing logic. */
private fun playfulStage(stage: String, currentPage: Int): String {
    val lower = stage.lowercase()
    return when {
        lower.contains("writing") -> "✍️ Writing your story…"
        lower.contains("waiting for backup") -> "🎨 Finding a backup artist…"
        lower.contains("backup artist") ->
            if (currentPage > 0) "🎨 Drawing page $currentPage… (backup artist)"
            else "🎨 Drawing with backup artist…"
        lower.contains("illustrat") && currentPage > 0 -> "🎨 Drawing page $currentPage…"
        lower.contains("illustrat") -> "🎨 Drawing pictures…"
        lower.contains("saved") -> "📚 Saved to your nest!"
        else -> stage
    }
}
