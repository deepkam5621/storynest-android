package com.storynest.android.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.storynest.android.data.model.PageDetail
import com.storynest.android.ui.theme.Cream
import com.storynest.android.ui.theme.MoonCream
import com.storynest.android.ui.theme.NightInk
import com.storynest.android.ui.theme.SoftCoral
import com.storynest.android.ui.theme.WarmInk
import com.storynest.android.viewmodel.ReaderViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    bookId: String,
    viewModel: ReaderViewModel,
    onBack: () -> Unit,
    onNeedApiKey: () -> Unit
) {
    LaunchedEffect(bookId) { viewModel.load(bookId) }
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var showRegen by remember { mutableStateOf(false) }

    val night = ui.nightMode
    // Soft cream pages by day; cozy night ink at bedtime.
    val bg = if (night) NightInk else Cream
    val fg = if (night) MoonCream else WarmInk

    LaunchedEffect(ui.error) {
        if (ui.error == "API_KEY_REQUIRED") {
            viewModel.clearError()
            onNeedApiKey()
        }
    }

    Scaffold(
        containerColor = bg,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        ui.book?.title ?: "Reading…",
                        color = fg,
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = fg
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleNight() }) {
                        Icon(
                            if (night) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Toggle night mode",
                            tint = fg
                        )
                    }
                    IconButton(
                        onClick = { showRegen = true },
                        enabled = !ui.regenerating && ui.pages.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Regenerate page", tint = fg)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bg)
            )
        }
    ) { padding ->
        val pages = ui.pages
        if (pages.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = SoftCoral)
            }
        } else {
            val pagerState = rememberPagerState(pageCount = { pages.size })
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                if (ui.regenerating) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(Modifier.height(18.dp), strokeWidth = 2.dp, color = SoftCoral)
                        Text(ui.status ?: "Updating…", color = fg, fontSize = 13.sp)
                    }
                } else if (ui.status != null) {
                    Text(
                        ui.status!!,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        color = fg.copy(alpha = 0.7f),
                        fontSize = 13.sp
                    )
                }
                ui.error?.let {
                    if (it != "API_KEY_REQUIRED") {
                        Text(
                            it,
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp
                        )
                    }
                }
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f)
                ) { index ->
                    PageContent(page = pages[index], textColor = fg, night = night)
                }
                Text(
                    "Page ${pagerState.currentPage + 1} of ${pages.size} · swipe 👈👉",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    color = fg.copy(alpha = 0.6f),
                    fontSize = 13.sp
                )
            }

            if (showRegen) {
                val current = pages.getOrNull(pagerState.currentPage)
                AlertDialog(
                    onDismissRequest = { showRegen = false },
                    title = { Text("Regenerate this page?") },
                    text = {
                        Text(
                            "Redraw the illustration for page ${(current?.pageNumber) ?: (pagerState.currentPage + 1)}. " +
                                "You can also rewrite the text. Needs network + API key."
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            showRegen = false
                            current?.let { viewModel.regenerate(it.pageNumber, alsoText = false) }
                        }) { Text("Image only") }
                    },
                    dismissButton = {
                        Row {
                            TextButton(onClick = {
                                showRegen = false
                                current?.let { viewModel.regenerate(it.pageNumber, alsoText = true) }
                            }) { Text("Text + image") }
                            TextButton(onClick = { showRegen = false }) { Text("Cancel") }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun PageContent(page: PageDetail, textColor: Color, night: Boolean) {
    val frameShape = RoundedCornerShape(28.dp)
    val frameBg = if (night) Color(0xFF2F2748) else Color(0xFFFFF0E0)
    val frameBorder = if (night) Color(0xFF4A3F6A) else SoftCoral.copy(alpha = 0.35f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .clip(frameShape)
                .background(frameBg)
                .border(3.dp, frameBorder, frameShape)
        ) {
            val path = page.imagePath
            if (path != null && File(path).exists()) {
                AsyncImage(
                    model = File(path),
                    contentDescription = "Page illustration",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(frameShape),
                    contentScale = ContentScale.Crop
                )
            }
            if (page.isPlaceholder) {
                Text(
                    "Placeholder art",
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                        .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    color = Color.White,
                    fontSize = 11.sp
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            page.text,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 22.sp,
                lineHeight = 34.sp,
                color = textColor
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(40.dp))
    }
}
