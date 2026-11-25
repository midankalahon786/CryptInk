package com.cryptink.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cryptink.app.core.crypto.KeySessionManager
import com.cryptink.app.ui.editor.EditorScreen
import com.cryptink.app.ui.feed.FeedScreen
import com.cryptink.app.ui.theme.CryptInkTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var sessionManager: KeySessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Block screenshots and Recent Apps thumbnail previews
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        enableEdgeToEdge()

        setContent {
            CryptInkTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CryptInkNavHost(sessionManager = sessionManager)
                }
            }
        }
    }
}

@Composable
fun CryptInkNavHost(sessionManager: KeySessionManager) {
    val navController = rememberNavController()
    val sessionState by sessionManager.sessionState.collectAsStateWithLifecycle()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

    // If the vault locks while the user is on EditorScreen, immediately pop back to the scrambled feed
    LaunchedEffect(sessionState.isSessionAuthenticated, currentRoute) {
        if (!sessionState.isSessionAuthenticated && currentRoute?.startsWith("editor") == true) {
            navController.popBackStack(route = "feed", inclusive = false)
        }
    }

    NavHost(
        navController = navController,
        startDestination = "feed"
    ) {
        composable(route = "feed") {
            FeedScreen(
                onNavigateToEditor = { entryId ->
                    val targetId = entryId ?: -1L
                    navController.navigate("editor/$targetId")
                }
            )
        }

        composable(
            route = "editor/{entryId}",
            arguments = listOf(
                navArgument("entryId") {
                    type = NavType.LongType
                    defaultValue = -1L
                }
            )
        ) {
            EditorScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}