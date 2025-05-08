package com.virtualrealm.anaphygonstream.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.virtualrealm.anaphygonstream.ui.screens.*

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onNavigateToDetail = { animeId ->
                    navController.navigate("detail/$animeId")
                },
                onNavigateToSearch = {
                    navController.navigate("search")
                },
                onNavigateToGenres = {
                    navController.navigate("genres")
                }
            )
        }

        composable(
            route = "detail/{animeId}",
            arguments = listOf(navArgument("animeId") { type = NavType.StringType })
        ) { backStackEntry ->
            val animeId = backStackEntry.arguments?.getString("animeId") ?: ""
            AnimeDetailScreen(
                animeId = animeId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToEpisode = { episodeId ->
                    navController.navigate("player/$episodeId")
                }
            )
        }

        composable(
            route = "player/{episodeId}",
            arguments = listOf(navArgument("episodeId") { type = NavType.StringType })
        ) { backStackEntry ->
            val episodeId = backStackEntry.arguments?.getString("episodeId") ?: ""
            VideoPlayerScreen(
                episodeId = episodeId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable("search") {
            SearchScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDetail = { animeId ->
                    navController.navigate("detail/$animeId")
                }
            )
        }

        // Add a new route for genre browsing
        composable(
            route = "genre/{genreId}",
            arguments = listOf(
                navArgument("genreId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val genreId = backStackEntry.arguments?.getString("genreId") ?: ""
            GenreAnimeScreen(
                genreId = genreId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDetail = { animeId ->
                    navController.navigate("detail/$animeId")
                }
            )
        }

        // Add a new route for all genres listing
        composable("genres") {
            GenreListScreen(
                onNavigateBack = { navController.popBackStack() },
                onGenreSelected = { genreId ->
                    navController.navigate("genre/$genreId")
                }
            )
        }
    }
}