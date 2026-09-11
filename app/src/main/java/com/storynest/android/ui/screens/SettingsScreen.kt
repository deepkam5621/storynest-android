package com.storynest.android.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.storynest.android.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val hasKey by viewModel.hasKey.collectAsStateWithLifecycle()
    val masked by viewModel.masked.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(saved) {
        if (saved) {
            message = if (hasKey) "API key saved on this device." else "API key cleared."
            draft = ""
            viewModel.consumeSaved()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Gemini API key", style = MaterialTheme.typography.titleLarge)
            Text(
                "StoryNest uses Google’s Gemini Developer API to write stories and draw cartoon pages. " +
                    "Your key stays encrypted on this device and is never logged.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (hasKey) {
                Text(
                    "Saved key: $masked",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Text(
                    "No key saved yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Paste API key") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true
            )

            Button(
                onClick = {
                    if (draft.isBlank()) {
                        message = "Paste a key first."
                    } else {
                        viewModel.save(draft)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save key") }

            if (hasKey) {
                OutlinedButton(
                    onClick = { viewModel.clear() },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Clear key") }
            }

            message?.let {
                Text(it, color = MaterialTheme.colorScheme.secondary)
            }

            Spacer(Modifier.height(8.dp))
            Text("Get a free key", style = MaterialTheme.typography.titleMedium)
            Text(
                "Open Google AI Studio, create an API key, then paste it above.\n\n" +
                    "Quota note: story text uses the free Gemini text tier. Pictures need separate " +
                    "image quota (or billing). Free-tier image limits are often exhausted (HTTP 429) — " +
                    "StoryNest still saves the full story with cozy placeholders so reading works offline. " +
                    "Enable billing or raise image limits at aistudio.google.com / ai.dev/rate-limit.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = {
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://aistudio.google.com/apikey")
                    )
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                Spacer(Modifier.height(0.dp))
                Text("  aistudio.google.com/apikey")
            }

            Spacer(Modifier.height(16.dp))
            Text("Offline library", style = MaterialTheme.typography.titleMedium)
            Text(
                "Books already on this phone open without network. Creating or regenerating needs internet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
