package com.mowzi.app.ui.navigation

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.mowzi.app.ui.characters.CharacterSelectScreen
import com.mowzi.app.ui.chat.ChatScreen
import com.mowzi.app.ui.chat.ChatViewModel
import com.mowzi.app.ui.onboarding.WelcomeScreen
import com.mowzi.app.ui.parent.ParentDashboardScreen
import com.mowzi.app.ui.parent.ParentDashboardViewModel
import com.mowzi.app.ui.parent.PinAuthViewModel
import com.mowzi.app.ui.parent.PinEntryScreen

sealed class Route(val path: String) {
    data object Onboarding : Route("onboarding")
    data object CharacterSelect : Route("characterSelect")
    data object Chat : Route("chat/{conversationId}/{characterId}/{characterName}") {
        fun createRoute(conversationId: String, characterId: String = "", characterName: String = ""): String {
            return "chat/$conversationId/$characterId/${java.net.URLEncoder.encode(characterName, "UTF-8")}"
        }
    }
    data object ConversationList : Route("conversationList")
    data object PinEntry : Route("pinEntry")
    data object ParentDashboard : Route("parentDashboard")
}

@Composable
fun MowziNavGraph(
    navController: NavHostController,
    startDestination: String = Route.Onboarding.path
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Route.Onboarding.path) {
            WelcomeScreen(
                onRegistered = {
                    navController.navigate(Route.CharacterSelect.path) {
                        popUpTo(Route.Onboarding.path) { inclusive = true }
                    }
                },
                onHasActiveConversation = { conversationId ->
                    navController.navigate(Route.Chat.createRoute(conversationId)) {
                        popUpTo(Route.Onboarding.path) { inclusive = true }
                    }
                },
                onGoToCharacterSelect = {
                    navController.navigate(Route.CharacterSelect.path) {
                        popUpTo(Route.Onboarding.path) { inclusive = true }
                    }
                }
            )
        }

        composable(Route.CharacterSelect.path) {
            CharacterSelectScreen(navController = navController)
        }

        composable(
            route = Route.Chat.path,
            arguments = listOf(
                navArgument("conversationId") { type = NavType.StringType },
                navArgument("characterId") { type = NavType.StringType },
                navArgument("characterName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val conversationId = backStackEntry.arguments?.getString("conversationId") ?: return@composable
            val characterId = backStackEntry.arguments?.getString("characterId") ?: ""
            val characterName = backStackEntry.arguments?.getString("characterName") ?: ""
            Log.d("wyl", "MowziNavGraph: Chat route, conversationId=$conversationId, characterId=$characterId, characterName=$characterName")
            val viewModel: ChatViewModel = hiltViewModel()
            LaunchedEffect(conversationId) {
                viewModel.setConversation(conversationId, characterId, characterName)
            }
            ChatScreen(
                viewModel = viewModel,
                onCharacterSwitch = {
                    navController.navigate(Route.CharacterSelect.path) {
                        popUpTo(Route.Chat.path) { inclusive = true }
                    }
                }
            )
        }

        composable(Route.ConversationList.path) {
            com.mowzi.app.ui.conversations.ConversationListScreen(
                onConversationClick = { convId ->
                    navController.navigate(Route.Chat.createRoute(convId))
                }
            )
        }

        composable(Route.PinEntry.path) {
            val parentAuthViewModel: PinAuthViewModel = hiltViewModel()
            PinEntryScreen(
                viewModel = parentAuthViewModel,
                onAuthenticated = {
                    navController.navigate(Route.ParentDashboard.path) {
                        popUpTo(Route.PinEntry.path) { inclusive = true }
                    }
                }
            )
        }

        composable(Route.ParentDashboard.path) {
            val parentDashboardViewModel: ParentDashboardViewModel = hiltViewModel()
            ParentDashboardScreen(
                viewModel = parentDashboardViewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
