package com.storynest.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.storynest.android.ui.navigation.StoryNestNav
import com.storynest.android.ui.theme.StoryNestTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as StoryNestApp
        setContent {
            StoryNestTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    StoryNestNav(
                        bookRepository = app.books,
                        settingsRepository = app.settings
                    )
                }
            }
        }
    }
}
