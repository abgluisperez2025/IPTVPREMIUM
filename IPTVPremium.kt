package com.abgluisperez

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson
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
    }

    // ===== SETTINGS CORRECTO CLOUDSTREAM =====
    override val settingsForProvider = listOf(
        CustomSite(
            "iptv_url",
            "URL IPTV M3U",
            "",
            "Pega aquí tu URL M3U"
        )
    )

    private fun getUrl(): String {
        return try {
            getKey<String>("iptv_url") ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    // ===== DESCARGA M3U =====
    private fun loadM3U(): List<Pair<String, String>> {
        val url = getUrl()
        if (url.isBlank()) return emptyList()

        val request = Request.Builder().url(url).build()

        val response = client.newCall(request).execute().body?.string() ?: return emptyList()

        val lines = response.split("\n")

        val result = mutableListOf<Pair<String, String>>()

        var name = ""

        for (line in lines) {
            when {
                line.startsWith("#EXTINF") -> {
                    name = line.substringAfterLast(",")
                }
                line.startsWith("http") -> {
                    result.add(name to line.trim())
                }
            }
        }

        return result
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {

        val items = loadM3U()

        val list = items.map {
            newLiveSearchResponse(
                it.first,
                it.second,
                TvType.Live
            )
        }

        return newHomePageResponse(
            listOf(HomePageList("Canales", list)),
            hasNext = false
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val items = loadM3U()

        return items.filter {
            it.first.contains(query, ignoreCase = true)
        }.map {
            newLiveSearchResponse(it.first, it.second, TvType.Live)
        }
    }

    override suspend fun load(url: String): LoadResponse {
        return newMovieLoadResponse("Canal", url, TvType.Live)
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        callback.invoke(
            newExtractorLink(
                source = name,
                name = "Stream",
                url = data,
                type = ExtractorLinkType.M3U8
            )
        )

        return true
    }
}
