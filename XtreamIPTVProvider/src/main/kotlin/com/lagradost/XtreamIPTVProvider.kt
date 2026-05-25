package com.anhdaden

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request

class XtreamIPTVProvider(
    override var mainUrl: String,
    override var name: String,
    private val username: String,
    private val password: String
) : MainAPI() {

    override val hasMainPage = true
    override var lang = "vi"
    override val hasQuickSearch = true
    override val hasDownloadSupport = false

    override val supportedTypes = setOf(TvType.Live)

    private val client = OkHttpClient()

    private val apiURL = "$mainUrl/player_api.php?username=$username&password=$password"
    private val serverUrlWithData = "$mainUrl/$username/$password/"

    private var items = mutableMapOf<String, List<Stream>>()

    // -----------------------------
    // HTTP (OKHTTP REPLACEMENT)
    // -----------------------------

    private fun getUrl(url: String): String {
        val request = Request.Builder()
            .url(url)
            .build()

        return client.newCall(request).execute().body?.string() ?: "[]"
    }

    // -----------------------------
    // MAIN PAGE
    // -----------------------------

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {

        val home = mutableListOf<HomePageList>()

        val categories = parseJson<List<Category>>(getUrl("$apiURL&action=get_live_categories"))
        items[name] = parseJson(getUrl("$apiURL&action=get_live_streams"))

        categories.forEach { category ->

            val streams = mutableListOf<SearchResponse>()

            items[name]?.forEach { stream ->
                if (stream.category_id == category.category_id) {

                    val data = Data(
                        num = stream.num,
                        name = stream.name,
                        stream_type = stream.stream_type,
                        stream_id = stream.stream_id,
                        stream_icon = stream.stream_icon,
                        epg_channel_id = stream.epg_channel_id,
                        added = stream.added,
                        is_adult = stream.is_adult,
                        category_id = stream.category_id,
                        custom_sid = stream.custom_sid,
                        tv_archive = stream.tv_archive,
                        direct_source = stream.direct_source,
                        tv_archive_duration = stream.tv_archive_duration,
                    ).toJson()

                    streams.add(
                        LiveSearchResponse(
                            name = stream.name,
                            url = data,
                            apiName = this.name,
                            type = TvType.Live,
                            posterUrl = stream.stream_icon
                        )
                    )
                }
            }

            home.add(
                HomePageList(
                    category.category_name,
                    streams,
                    isHorizontalImages = true
                )
            )
        }

        return newHomePageResponse(home, hasNext = false)
    }

    // -----------------------------
    // SEARCH
    // -----------------------------

    override suspend fun search(query: String): List<SearchResponse> {

        if (items[name] == null) {
            items[name] = parseJson(getUrl("$apiURL&action=get_live_streams"))
        }

        return items[name]!!
            .filter { it.name.contains(query, ignoreCase = true) }
            .map { item ->

                val data = Data(
                    num = item.num,
                    name = item.name,
                    stream_type = item.stream_type,
                    stream_id = item.stream_id,
                    stream_icon = item.stream_icon,
                    epg_channel_id = item.epg_channel_id,
                    added = item.added,
                    is_adult = item.is_adult,
                    category_id = item.category_id,
                    custom_sid = item.custom_sid,
                    tv_archive = item.tv_archive,
                    direct_source = item.direct_source,
                    tv_archive_duration = item.tv_archive_duration,
                ).toJson()

                LiveSearchResponse(
                    name = item.name,
                    url = data,
                    apiName = this.name,
                    type = TvType.Live,
                    posterUrl = item.stream_icon
                )
            }
    }

    override suspend fun quickSearch(query: String) = search(query)

    // -----------------------------
    // LOAD
    // -----------------------------

    override suspend fun load(url: String): LoadResponse {
        val data = parseJson<Data>(url)

        return newMovieLoadResponse(
            data.name,
            url,
            TvType.Live,
            url
        ) {
            posterUrl = data.stream_icon
        }
    }

    // -----------------------------
    // LINK RESOLUTION
    // -----------------------------

    private fun checkLinkType(url: String): String {
        return try {
            when {
                url.contains(".m3u8", true) -> "m3u8"
                url.contains(".mpegts", true) -> "mpegts"
                url.contains(".mp4", true) -> "mp4"
                url.contains(".flv", true) -> "flv"

                else -> {
                    val request = Request.Builder()
                        .url(url)
                        .head()
                        .build()

                    val response = client.newCall(request).execute()
                    val contentType = response.header("Content-Type") ?: ""

                    when {
                        contentType.contains("mpegurl", true) -> "m3u8"
                        contentType.startsWith("text/", true) -> "m3u8"
                        else -> "unknown"
                    }
                }
            }
        } catch (e: Exception) {
            "unknown"
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val parsed = parseJson<Data>(data)
        val url = serverUrlWithData + parsed.stream_id

        val isM3u8 = checkLinkType(url) == "m3u8"

        callback(
            newExtractorLink(
                source = name,
                name = parsed.name,
                url = url,
                referer = "",
                quality = Qualities.Unknown.value,
                isM3u8 = isM3u8
            )
        )

        return true
    }

    // -----------------------------
    // OPTIONAL INTERCEPTOR
    // -----------------------------

    override fun getVideoInterceptor(extractorLink: ExtractorLink): Interceptor? {
        return Interceptor { chain ->
            chain.proceed(chain.request())
        }
    }
}
