package com.storynest.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.storynest.android.data.model.AgeBand
import com.storynest.android.data.model.StoryLength
import com.storynest.android.data.model.StoryMood
import com.storynest.android.viewmodel.CreateBookViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateBookScreen(
    viewModel: CreateBookViewModel,
    onBack: () -> Unit,
    onNeedApiKey: () -> Unit,
    onStartGenerating: () -> Unit
) {
    var idea by remember { mutableStateOf(viewModel.idea) }
    var age by remember { mutableStateOf(viewModel.ageBand) }
    var length by remember { mutableStateOf(viewModel.length) }
    var mood by remember { mutableStateOf(viewModel.mood) }
    var localError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create a book") },
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
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "One idea is enough. StoryNest writes every page and draws the pictures.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!viewModel.hasApiKey()) {
                Text(
                    "You’ll need a Gemini API key before creating.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
                TextButton(onClick = onNeedApiKey) { Text("Open Settings") }
            }

            OutlinedTextField(
                value = idea,
                onValueChange = { idea = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                label = { Text("Story idea") },
                placeholder = {
                    Text("A sleepy fox who learns to share the moon…")
                }
            )

            Text("Age band", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AgeBand.entries.forEach { option ->
                    FilterChip(
                        selected = age == option,
                        onClick = { age = option },
                        label = { Text(option.label) }
                    )
                }
            }

            Text("Length", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StoryLength.entries.forEach { option ->
                    FilterChip(
                        selected = length == option,
                        onClick = { length = option },
                        label = { Text(option.label) }
                    )
                }
            }

            Text("Mood", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StoryMood.entries.forEach { option ->
                    FilterChip(
                        selected = mood == option,
                        onClick = { mood = option },
                        label = { Text(option.label) }
                    )
                }
            }

            localError?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    if (!viewModel.hasApiKey()) {
                        onNeedApiKey()
                        return@Button
                    }
                    if (idea.trim().length < 3) {
                        localError = "Please enter a short story idea."
                        return@Button
                    }
                    viewModel.idea = idea.trim()
                    viewModel.ageBand = age
                    viewModel.length = length
                    viewModel.mood = mood
                    viewModel.startGeneration()
                    onStartGenerating()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text("Create", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
