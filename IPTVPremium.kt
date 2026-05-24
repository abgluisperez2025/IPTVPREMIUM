package com.abgluisperez

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class IPTVPremium : MainAPI() {

    override var name = "IPTV Premium"
    override var mainUrl = ""
    override var lang = "es"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Live)

    companion object {
        private val client = OkHttpClient()
        private val mutex = Mutex()
        private var cache: List<PlaylistItem>? = null
        private var cacheTime = 0L
        private const val TTL = 5 * 60 * 1000L
    }

    // ===== SETTINGS (URL editable en menú) =====
    override val settingsForProvider = listOf(
        CustomSite(
            "iptv_url",
            "URL IPTV M3U",
            "",
            "http://kazan-tv.com:8091/get.php?username=AlbertoMi&password=FTud8386d&type=m3u_plus"
        )
    )

    private fun getUrl(): String {
        return try {
            getKey<String>("iptv_url").takeIf { !it.isNullOrBlank() } ?: mainUrl
        } catch (e: Exception) {
            mainUrl
        }
    }

    // ===== PARSER M3U =====
    private suspend fun loadPlaylist(): List<PlaylistItem> = withContext(Dispatchers.IO) {
        cache?.let {
            if (System.currentTimeMillis() - cacheTime < TTL) return@withContext it
        }

        mutex.withLock {
            cache?.let {
                if (System.currentTimeMillis() - cacheTime < TTL) return@withLock it
            }

            val url = getUrl()
            Log.d("IPTV", "Cargando: $url")

            val list = mutableListOf<PlaylistItem>()

            val request = Request.Builder().url(url).build()

            client.newCall(request).execute().use { res ->
                if (!res.isSuccessful) throw Exception("HTTP ${res.code}")

                val body = res.body?.string() ?: return@use

                val lines = body.split("\n")

                var title = ""
                var logo = ""
                var group = ""

                for (line in lines) {
                    when {
                        line.startsWith("#EXTINF") -> {
                            title = line.substringAfterLast(",").trim()

                            val regex = Regex("""(\w+)="([^"]*)"""")
                            regex.findAll(line).forEach {
                                when (it.groupValues[1]) {
                                    "tvg-logo" -> logo = it.groupValues[2]
                                    "group-title" -> group = it.groupValues[2]
                                }
                            }
                        }

                        line.startsWith("http") -> {
                            list.add(
                                PlaylistItem(
                                    title = title,
                                    url = line.trim(),
                                    category = group.ifBlank { "General" },
                                    poster = logo
                                )
                            )
                        }
                    }
                }
            }

            cache = list
            cacheTime = System.currentTimeMillis()

            list
        }
    }

    // ===== MAIN PAGE =====
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val items = loadPlaylist()

        val grouped = items.groupBy { it.category }

        val pages = grouped.map { (cat, list) ->
            HomePageList(
                cat,
                list.map {
                    newLiveSearchResponse(
                        it.title,
                        it.toJson(),
                        TvType.Live
                    ) {
                        this.posterUrl = it.poster
                    }
                },
                isHorizontalImages = true
            )
        }

        return newHomePageResponse(pages, hasNext = false)
    }

    // ===== SEARCH =====
    override suspend fun search(query: String): List<SearchResponse> {
        val items = loadPlaylist()

        return items.filter {
            it.title.contains(query, ignoreCase = true)
        }.map {
            newLiveSearchResponse(
                it.title,
                it.toJson(),
                TvType.Live
            ) {
                this.posterUrl = it.poster
            }
        }
    }

    override suspend fun quickSearch(query: String) = search(query)

    // ===== LOAD =====
    override suspend fun load(url: String): LoadResponse {
        val data = parseJson<PlaylistItem>(url)

        return newLiveStreamLoadResponse(
            data.title,
            data.url,
            url
        ) {
            this.posterUrl = data.poster
            this.plot = data.category
        }
    }

    // ===== PLAY =====
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val item = parseJson<PlaylistItem>(data)

        callback.invoke(
            newExtractorLink(
                source = name,
                name = item.title,
                url = item.url,
                type = ExtractorLinkType.M3U8
            ) {
                quality = Qualities.Unknown.value
            }
        )

        return true
    }
}

// ===== DATA =====
data class PlaylistItem(
    val title: String,
    val url: String,
    val category: String = "General",
    val poster: String = ""
)
