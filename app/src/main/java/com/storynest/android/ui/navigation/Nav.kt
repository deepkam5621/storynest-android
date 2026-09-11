package com.storynest.android.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.storynest.android.data.prefs.SettingsRepository
import com.storynest.android.data.repository.BookRepository
import com.storynest.android.ui.screens.CreateBookScreen
import com.storynest.android.ui.screens.GeneratingScreen
import com.storynest.android.ui.screens.HomeScreen
import com.storynest.android.ui.screens.ReaderScreen
import com.storynest.android.ui.screens.SettingsScreen
import com.storynest.android.viewmodel.CreateBookViewModel
import com.storynest.android.viewmodel.LibraryViewModel
import com.storynest.android.viewmodel.ReaderViewModel
import com.storynest.android.viewmodel.SettingsViewModel
import com.storynest.android.viewmodel.StoryNestViewModelFactory

object Routes {
    const val HOME = "home"
    const val CREATE = "create"
    const val GENERATING = "generating"
    const val READER = "reader/{bookId}"
    const val SETTINGS = "settings"
    fun reader(bookId: String) = "reader/$bookId"
}

@Composable
fun StoryNestNav(
    bookRepository: BookRepository,
    settingsRepository: SettingsRepository
) {
    val nav = rememberNavController()
    val factory = remember {
        StoryNestViewModelFactory(bookRepository, settingsRepository)
    }
    val activity = LocalContext.current as ComponentActivity
    // Shared across Create → Generating so pipeline state survives navigation
    val createVm: CreateBookViewModel = viewModel(viewModelStoreOwner = activity, factory = factory)

    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            val vm: LibraryViewModel = viewModel(factory = factory)
            HomeScreen(
                viewModel = vm,
                onCreate = { nav.navigate(Routes.CREATE) },
                onOpenBook = { id -> nav.navigate(Routes.reader(id)) },
                onSettings = { nav.navigate(Routes.SETTINGS) }
            )
        }
        composable(Routes.CREATE) {
            CreateBookScreen(
                viewModel = createVm,
                onBack = { nav.popBackStack() },
                onNeedApiKey = { nav.navigate(Routes.SETTINGS) },
                onStartGenerating = {
                    nav.navigate(Routes.GENERATING) {
                        popUpTo(Routes.CREATE) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.GENERATING) {
            GeneratingScreen(
                viewModel = createVm,
                onDone = { bookId ->
                    createVm.reset()
                    nav.navigate(Routes.reader(bookId)) {
                        popUpTo(Routes.HOME)
                        launchSingleTop = true
                    }
                },
                onFailedHome = {
                    createVm.reset()
                    nav.popBackStack(Routes.HOME, inclusive = false)
                },
                onSettings = { nav.navigate(Routes.SETTINGS) }
            )
        }
        composable(
            Routes.READER,
            arguments = listOf(navArgument("bookId") { type = NavType.StringType })
        ) { entry ->
            val bookId = entry.arguments?.getString("bookId") ?: return@composable
            val vm: ReaderViewModel = viewModel(factory = factory)
            ReaderScreen(
                bookId = bookId,
                viewModel = vm,
                onBack = { nav.popBackStack() },
                onNeedApiKey = { nav.navigate(Routes.SETTINGS) }
            )
        }
        composable(Routes.SETTINGS) {
            val vm: SettingsViewModel = viewModel(factory = factory)
            SettingsScreen(
                viewModel = vm,
                onBack = { nav.popBackStack() }
            )
        }
    }
}
