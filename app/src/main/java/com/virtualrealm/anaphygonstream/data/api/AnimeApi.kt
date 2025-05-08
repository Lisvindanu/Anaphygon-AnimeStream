package com.virtualrealm.anaphygonstream.data.api

import com.virtualrealm.anaphygonstream.data.models.AnimeDetailResponse
import com.virtualrealm.anaphygonstream.data.models.AnimeListResponse
import com.virtualrealm.anaphygonstream.data.models.ApiResponse
import com.virtualrealm.anaphygonstream.data.models.EpisodeDetailResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface AnimeApi {
    // Otakudesu endpoints
    @GET("otakudesu/ongoing")
    suspend fun getOngoingAnime(@Query("page") page: Int = 1): ApiResponse<AnimeListResponse>

    @GET("otakudesu/completed")
    suspend fun getCompleteAnime(@Query("page") page: Int = 1): ApiResponse<AnimeListResponse>

    @GET("otakudesu/anime/{animeId}")
    suspend fun getAnimeDetail(@Path("animeId") animeId: String): ApiResponse<AnimeDetailResponse>

    @GET("otakudesu/episode/{episodeId}")
    suspend fun getEpisodeDetail(@Path("episodeId") episodeId: String): ApiResponse<EpisodeDetailResponse>

    @GET("otakudesu/search")
    suspend fun searchAnime(@Query("q") query: String, @Query("page") page: Int = 1): ApiResponse<AnimeListResponse>

    // New endpoints from Otakudesu API documentation
    @GET("otakudesu/home")
    suspend fun getHomeAnime(): ApiResponse<AnimeListResponse>

    @GET("otakudesu/anime")
    suspend fun getAnimeList(@Query("page") page: Int = 1): ApiResponse<AnimeListResponse>

    @GET("otakudesu/schedule")
    suspend fun getSchedule(): ApiResponse<Map<String, List<Map<String, String>>>>

    // Genre endpoints for Otakudesu
    @GET("otakudesu/genres")
    suspend fun getGenres(): ApiResponse<List<Map<String, String>>>

    @GET("otakudesu/genres/{genreId}")
    suspend fun getAnimeByGenre(
        @Path("genreId") genreId: String,
        @Query("page") page: Int = 1
    ): ApiResponse<AnimeListResponse>

    // Samehadaku API as fallback
    @GET("samehadaku/ongoing")
    suspend fun getSamehadakuOngoing(
        @Query("page") page: Int = 1,
        @Query("order") order: String = "update"
    ): ApiResponse<AnimeListResponse>

    @GET("samehadaku/completed")
    suspend fun getSamehadakuCompleted(
        @Query("page") page: Int = 1,
        @Query("order") order: String = "update"
    ): ApiResponse<AnimeListResponse>

    @GET("samehadaku/recent")
    suspend fun getSamehadakuRecent(@Query("page") page: Int = 1): ApiResponse<AnimeListResponse>

    @GET("samehadaku/home")
    suspend fun getSamehadakuHome(): ApiResponse<AnimeListResponse>

    @GET("samehadaku/search")
    suspend fun searchSamehadakuAnime(
        @Query("q") query: String,
        @Query("page") page: Int = 1
    ): ApiResponse<AnimeListResponse>

    @GET("samehadaku/anime/{animeId}")
    suspend fun getSamehadakuAnimeDetail(@Path("animeId") animeId: String): ApiResponse<AnimeDetailResponse>

    @GET("samehadaku/episode/{episodeId}")
    suspend fun getSamehadakuEpisodeDetail(@Path("episodeId") episodeId: String): ApiResponse<EpisodeDetailResponse>

    // Genre endpoints for Samehadaku
    @GET("samehadaku/genres")
    suspend fun getSamehadakuGenres(): ApiResponse<List<Map<String, String>>>

    @GET("samehadaku/genres/{genreId}")
    suspend fun getSamehadakuAnimeByGenre(
        @Path("genreId") genreId: String,
        @Query("page") page: Int = 1
    ): ApiResponse<AnimeListResponse>
}