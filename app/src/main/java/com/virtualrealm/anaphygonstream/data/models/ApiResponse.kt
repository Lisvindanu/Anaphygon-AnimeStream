package com.virtualrealm.anaphygonstream.data.models

data class ApiResponse<T>(
    val statusCode: Int,
    val statusMessage: String,
    val message: String,
    val ok: Boolean,
    val data: T,
    val pagination: Pagination? = null
)

data class Pagination(
    val currentPage: Int,
    val hasPrevPage: Boolean,
    val prevPage: Int?,
    val hasNextPage: Boolean,
    val nextPage: Int?,
    val totalPages: Int
)

data class AnimeListResponse(
    val animeList: List<AnimeItem>
)

// Updated AnimeItem data class in ApiResponse.kt to support both ongoing and completed anime fields

data class AnimeItem(
    val title: String,
    val poster: String,
    val episodes: Int = 0,
    val releaseDay: String? = "",
    val latestReleaseDate: String? = "",
    val score: String? = null,            // Used in completed anime
    val lastReleaseDate: String? = null,  // Used in completed anime
    val status: String? = null,           // Some APIs include this
    val animeId: String,
    val href: String,
    val otakudesuUrl: String? = null
)

data class AnimeDetailResponse(
    val title: String,
    val japaneseTitle: String,
    val poster: String,
    val rating: String,
    val synopsis: String,
    val genres: List<String>,
    val episodes: List<Episode>
)

data class Episode(
    val title: String,
    val episodeId: String,
    val href: String,
    val otakudesuUrl: String
)

data class EpisodeDetailResponse(
    val title: String,
    val poster: String,
    val streams: List<Stream>
)

data class Stream(
    val quality: String,
    val url: String,
    val streamId: String
)