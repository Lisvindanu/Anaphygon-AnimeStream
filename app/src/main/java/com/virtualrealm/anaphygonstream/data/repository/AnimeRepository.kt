package com.virtualrealm.anaphygonstream.data.repository

import com.virtualrealm.anaphygonstream.data.api.ApiClient
import com.virtualrealm.anaphygonstream.data.models.AnimeDetailResponse
import com.virtualrealm.anaphygonstream.data.models.AnimeListResponse
import com.virtualrealm.anaphygonstream.data.models.ApiResponse
import com.virtualrealm.anaphygonstream.data.models.EpisodeDetailResponse

class AnimeRepository {
    private val api = ApiClient.animeApi

    suspend fun getOngoingAnime(page: Int = 1): ApiResponse<AnimeListResponse> {
        return try {
            api.getOngoingAnime(page)
        } catch (e: Exception) {
            // Fallback ke endpoint alternatif jika gagal
            try {
                // Coba gunakan endpoint home sebagai fallback
                val homeResponse = api.getHomeAnime()
                // Konversi respons home ke format yang diharapkan
                ApiResponse(
                    statusCode = 200,
                    statusMessage = "Success (fallback)",
                    message = "Data retrieved from fallback",
                    ok = true,
                    data = homeResponse.data,
                    pagination = null
                )
            } catch (e: Exception) {
                createErrorResponse("Failed to fetch ongoing anime: ${e.message}")
            }
        }
    }

    suspend fun getCompleteAnime(page: Int = 1): ApiResponse<AnimeListResponse> {
        return try {
            api.getCompleteAnime(page)
        } catch (e: Exception) {
            // Coba dengan endpoint alternatif atau gunakan API Samehadaku sebagai fallback
            try {
                api.getAnimeList(page) // Endpoint alternatif yang mungkin ada
            } catch (e: Exception) {
                createErrorResponse("Failed to fetch complete anime: ${e.message}")
            }
        }
    }

    // Helper untuk membuat response error
    private fun <T> createErrorResponse(errorMessage: String): ApiResponse<T> {
        return ApiResponse(
            statusCode = 500,
            statusMessage = "Error",
            message = errorMessage,
            ok = false,
            data = null as T,
            pagination = null
        )
    }

    suspend fun getAnimeDetail(animeId: String): ApiResponse<AnimeDetailResponse> {
        return api.getAnimeDetail(animeId)
    }

    suspend fun getEpisodeDetail(episodeId: String): ApiResponse<EpisodeDetailResponse> {
        return api.getEpisodeDetail(episodeId)
    }

    suspend fun searchAnime(query: String, page: Int = 1): ApiResponse<AnimeListResponse> {
        return api.searchAnime(query, page)
    }
}