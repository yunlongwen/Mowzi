package com.mowzi.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.mowzi.app.ui.characters.CharacterSelectScreen

sealed class Route(val path: String) {
    data object Onboarding : Route("onboarding")
    data object CharacterSelect : Route("characterSelect")
    data object Chat : Route("chat/{conversationId}") {
        fun createRoute(conversationId: String) = "chat/$conversationId"
    }
    data object ConversationList : Route("conversationList")
    data object PinEntry : Route("pinEntry")
    data object ParentDashboard : Route("parentDashboard")
}

@Composable
fun MowziNavGraph(
    navController: NavHostController,
    startDestination: String = Route.CharacterSelect.path
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Route.Onboarding.path) {
            // TODO: Implement onboarding screen
        }

        composable(Route.CharacterSelect.path) {
            CharacterSelectScreen(navController = navController)
        }

        composable(
            route = Route.Chat.path,
            arguments = listOf(
                navArgument("conversationId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val conversationId = backStackEntry.arguments?.getString("conversationId") ?: return@composable
            // TODO: Implement chat screen with conversationId
        }

        composable(Route.ConversationList.path) {
            // TODO: Implement conversation list screen
        }

        composable(Route.PinEntry.path) {
            // TODO: Implement PIN entry screen
        }

        composable(Route.ParentDashboard.path) {
            // TODO: Implement parent dashboard screen
        }
    }
}