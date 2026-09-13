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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.storynest.android.data.model.AgeBand
import com.storynest.android.data.model.StoryLength
import com.storynest.android.data.model.StoryMood
import com.storynest.android.ui.theme.CardLavender
import com.storynest.android.ui.theme.CardMint
import com.storynest.android.ui.theme.CardSky
import com.storynest.android.ui.theme.CardSunny
import com.storynest.android.ui.theme.SoftCoral
import com.storynest.android.viewmodel.CreateBookViewModel

private fun ageChipLabel(band: AgeBand): String = when (band) {
    AgeBand.AGES_3_5 -> "🐣 3–5"
    AgeBand.AGES_6_8 -> "🦊 6–8"
    AgeBand.AGES_9_10 -> "⭐ 9–10"
    AgeBand.AGES_11_12 -> "🚀 11–12"
}

private fun lengthChipLabel(length: StoryLength): String = when (length) {
    StoryLength.SHORT -> "📘 5 pages"
    StoryLength.MEDIUM -> "📚 8 pages"
    StoryLength.LONG -> "✨ 12 pages"
}

private fun moodChipLabel(mood: StoryMood): String = when (mood) {
    StoryMood.COZY -> "🧸 Cozy"
    StoryMood.FUNNY -> "😄 Funny"
    StoryMood.SOFT_ADVENTURE -> "🗺️ Soft adventure"
}

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
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "✨ Let’s make a story!",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                "Tell us one fun idea — StoryNest writes every page and draws the pictures! 🎨",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!viewModel.hasApiKey()) {
                Text(
                    "Parents: you’ll need a Gemini API key in Settings before creating.",
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
                    .height(150.dp),
                shape = RoundedCornerShape(20.dp),
                label = { Text("What’s your story idea? 💭") },
                placeholder = {
                    Text("A sleepy fox who learns to share the moon with her forest friends…")
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SoftCoral,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                )
            )

            Text("Who’s listening? 👂", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AgeBand.entries.forEach { option ->
                    PlayfulChip(
                        selected = age == option,
                        label = ageChipLabel(option),
                        selectedColor = CardSunny,
                        onClick = { age = option }
                    )
                }
            }

            Text("How long? 📏", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StoryLength.entries.forEach { option ->
                    PlayfulChip(
                        selected = length == option,
                        label = lengthChipLabel(option),
                        selectedColor = CardSky,
                        onClick = { length = option }
                    )
                }
            }

            Text("What feeling? 💖", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StoryMood.entries.forEach { option ->
                    PlayfulChip(
                        selected = mood == option,
                        label = moodChipLabel(option),
                        selectedColor = CardMint,
                        onClick = { mood = option }
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
                        localError = "Tell us a little more about your idea! ✍️"
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
                    .height(58.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SoftCoral,
                    contentColor = Color.White
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
            ) {
                Text(
                    "🪄 Create my story!",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PlayfulChip(
    selected: Boolean,
    label: String,
    selectedColor: Color,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        },
        shape = RoundedCornerShape(16.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = selectedColor,
            selectedLabelColor = MaterialTheme.colorScheme.onBackground,
            containerColor = CardLavender.copy(alpha = 0.45f),
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}
