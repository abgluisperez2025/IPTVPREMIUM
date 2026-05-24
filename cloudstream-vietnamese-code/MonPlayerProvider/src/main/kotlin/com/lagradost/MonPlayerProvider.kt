package com.anhdaden

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import okhttp3.Interceptor

class MonPlayerProvider(mainUrl: String, searchUrl: String?, name: String, type: Set<TvType>) : MainAPI() {
    override var mainUrl = mainUrl
    override var name = name
    override val hasMainPage = true
    override var lang = "vi"
    override val hasDownloadSupport = false
    override val supportedTypes = type

    override var mainPage = mainPageOf("" to "")

    private var links = mutableMapOf<String, MutableList<String>>()
    private val headers = mapOf("User-Agent" to "Dart/3.6 (dart:io)")
    private val searchUrl = searchUrl

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        links = mutableMapOf<String, MutableList<String>>()
        val items = mutableListOf<HomePageList>()
        var home = mutableListOf<SearchResponse>()
        var hasNext = false
        if (page == 1) {
            val mainPageElements = mutableListOf<Pair<String, String>>()
            app.get(mainUrl, headers = headers).parsedSafe<ResponseHome>()?.groups?.forEach { group ->
                home = mutableListOf<SearchResponse>()
                val title = group.name
                if (title != "Có thể bạn quan tâm" && title != "Diễn Viên") {
                    var isHorizontal = true
                    group.channels?.forEach { it ->
                        val url = if (it.remote_data?.url != null) it.remote_data?.url ?: "" else it.toJson()
                        val description = if (it.description != null) it.description else it.org_metadata?.description ?: ""
                        val data = LoadData(url, it.name, it.image.url, it.type, description).toJson()
                        isHorizontal = if (it.image.height < it.image.width) true else false
                        val currentItems = links.computeIfAbsent(title) { mutableListOf() }
                        currentItems.add(it.name)
                        home.add(newAnimeSearchResponse(it.name, data) {
                            this.posterUrl = it.image.url
                        })
                    }
                    if (group.url?.isNullOrBlank() == false) {
                        hasNext = true
                        mainPageElements.add(group.url to title)
                    } else if (group.remote_data?.url?.isNullOrBlank() == false) {
                        hasNext = true
                        mainPageElements.add(group.remote_data.url to title)
                    }
                    items.add(HomePageList(title, home, isHorizontalImages = isHorizontal))
                }
            }
            mainPage = mainPageOf(*mainPageElements.toTypedArray())
        } else {
            var isHorizontal = true
            val newPage = if (page > 2) page - 1 else 1
            val requestUrl = if (request.data.contains("?")) "${request.data}&page=${newPage}" else "${request.data}?page=${newPage}"
            val response = app.get(requestUrl, headers = headers).parsedSafe<ResponseHomeDetail>()
            if (response?.loadMore?.pageInfo?.current_page != null && response?.loadMore?.pageInfo?.last_page != null) {
                if (response.loadMore.pageInfo.current_page <= response.loadMore.pageInfo.last_page) {
                    response?.channels?.forEach { it ->
                        val url = if (it.remote_data?.url != null) it.remote_data?.url ?: "" else it.toJson()
                        val description = if (it.description != null) it.description else it.org_metadata?.description ?: ""
                        val data = LoadData(url, it.name, it.image.url, it.type, description).toJson()
                        isHorizontal = if (it.image.height < it.image.width) true else false
                        val currentItems = links.computeIfAbsent(request.name) { mutableListOf() }
                        if (!currentItems.contains(it.name)) {
                            currentItems.add(it.name)
                            home.add(newAnimeSearchResponse(it.name, data) {
                                this.posterUrl = it.image.url
                            })
                        }
                    }
                    items.add(HomePageList(request.name, home, isHorizontalImages = isHorizontal))
                    hasNext = true
                }
            }
        }

        return newHomePageResponse(items, hasNext = hasNext)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        var home = mutableListOf<SearchResponse>()
        if (searchUrl == null) {
            app.get(mainUrl, headers = headers).parsedSafe<ResponseHome>()?.groups?.forEach { group ->
                val title = group.name
                if (title != "Có thể bạn quan tâm" && title != "Diễn Viên") {
                    group.channels?.forEach { it ->
                        if (it.name.removeVietnameseAccents().contains(query.removeVietnameseAccents())) {
                            val url = if (it.remote_data?.url != null) it.remote_data?.url ?: "" else it.toJson()
                            val description = if (it.description != null) it.description else it.org_metadata?.description ?: ""
                            val data = LoadData(url, it.name, it.image.url, it.type, description).toJson()
                            val existingItem = home.find { it.url == data }
                            if (existingItem == null) {
                                home.add(newAnimeSearchResponse(it.name, data) {
                                    this.posterUrl = it.image.url
                                })
                            }
                        }
                    }
                }
            }
        } else {
            val response = app.get(searchUrl + query, headers = headers).parsedSafe<ResponseHome>()
            response?.groups?.forEach { group ->
                group.channels?.forEach { it ->
                    val url = if (it.remote_data?.url != null) it.remote_data?.url ?: "" else it.toJson()
                    val description = if (it.description != null) it.description ?: "" else it.org_metadata?.description ?: ""
                    val data = LoadData(url, it.name, it.image.url, it.type, description).toJson()
                    val existingItem = home.find { it.url == data }
                    if (existingItem == null) {
                        home.add(newAnimeSearchResponse(it.name, data) {
                            this.posterUrl = it.image.url
                        })
                    }
                }
            }
            if (home.size == 0) {
                response?.channels?.forEach { it ->
                    val url = if (it.remote_data?.url != null) it.remote_data?.url ?: "" else it.toJson()
                    val description = if (it.description != null) it.description ?: "" else it.org_metadata?.description ?: ""
                    val data = LoadData(url, it.name, it.image.url, it.type, description).toJson()
                    val existingItem = home.find { it.url == data }
                    if (existingItem == null) {
                        home.add(newAnimeSearchResponse(it.name, data) {
                            this.posterUrl = it.image.url
                        })
                    }
                }
            }
        }

        return home
    }

    override suspend fun load(url: String): LoadResponse {
        val loadData = parseJson<LoadData>(url)
        
        val title = loadData.title.toString()
        val poster = loadData.poster
        val description = loadData.description

        return if (loadData.type == "playlist" && supportedTypes.first() != TvType.Live) {
            var seasons = mutableListOf<Pair<String, Int>>()
            var episodes = mutableListOf<Episode>()
            if (loadData.url.startsWith("http")) {
                app.get(loadData.url, headers = headers).parsedSafe<ResponseDetail>()?.sources?.forEach { it ->
                    it.contents?.forEachIndexed { seasonIndex, ele ->
                        val seasonNum = seasonIndex + 1
                        seasons.add(Pair(ele.name, seasonNum))
                        ele.streams?.forEach { ele2 ->
                            val data = LinkData(ele2.id, loadData.url).toJson()
                            if (ele2.name.toIntOrNull() != null) {
                                episodes.add(Episode(data = data, season = seasonNum, episode = ele2.name.toIntOrNull()))
                            } else {
                                episodes.add(Episode(data = data, season = seasonNum, name = ele2.name))
                            }
                        }
                    }
                }
            } else {
                val response = tryParseJson<ResponseDetail>(loadData.url)
                response?.sources?.forEach { it ->
                    it.contents?.forEachIndexed { seasonIndex, ele ->
                        val seasonNum = seasonIndex + 1
                        seasons.add(Pair(ele.name, seasonNum))
                        ele.streams?.forEach { ele2 ->
                            val data = LinkData(ele2.id, loadData.url).toJson()
                            if (ele2.name.toIntOrNull() != null) {
                                episodes.add(Episode(data = data, season = seasonNum, episode = ele2.name.toIntOrNull()))
                            } else {
                                episodes.add(Episode(data = data, season = seasonNum, name = ele2.name))
                            }
                        }
                    }
                }
            }
            newTvSeriesLoadResponse(title, url, if (supportedTypes.first() == TvType.Live) TvType.Others else TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = description
                this.seasonNames = seasons.map {(name, int) -> SeasonData(int, name)}
            }
        } else {
            val data = if (loadData.url.startsWith("http") || loadData.url.contains("hls")) LinkData("", loadData.url).toJson() else ""
            val type = if (supportedTypes.first() == TvType.Live) {
                if (title.startsWith("Fullmatch") || title.startsWith("Full ") || title.startsWith("Highlight")) TvType.Others else TvType.Live
            } else {
                supportedTypes.first()
            }
            newMovieLoadResponse(title, url, type, data) {
                this.posterUrl = poster
                this.plot = description
            }
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val linkData = parseJson<LinkData>(data)
        suspend fun processStreams(sources: List<Source>?) {
            sources?.forEach { 
                val nameSource = it.name
                it.contents?.forEach { ele1 ->
                    ele1.streams?.forEach { ele2 ->
                        if (ele2.id == linkData.id || linkData.id == "") {
                            try {
                                if (ele2.remote_data?.url.toString().isNotEmpty()) {
                                    val response = app.get(ele2.remote_data?.url.toString(), headers = headers).parsedSafe<ResponseStream>()
                                    response?.stream_links?.forEach { ele3 ->
                                        if (ele3.type == "hls") {
                                            val headers = ele3.request_headers?.mapNotNull { ele4 ->
                                                val key = ele4.key
                                                val value = ele4.value
                                                if (key != null && value != null) key to value else null
                                            }?.toMap() ?: emptyMap()
                                            val refererValue = ele3.request_headers?.firstOrNull { it.key == "Referer" }?.value ?: ""
                                            callback.invoke(
                                                ExtractorLink(
                                                    name,
                                                    if (nameSource != ele3.name) nameSource + " " + ele3.name else nameSource,
                                                    ele3.url,
                                                    referer = refererValue,
                                                    quality = Qualities.Unknown.value,
                                                    isM3u8 = true,
                                                    headers = headers,
                                                )
                                            )
                                            ele3.subtitles?.forEach { ele4 ->
                                                if (ele4.label.contains("Việt", ignoreCase = true)) {
                                                    subtitleCallback.invoke(SubtitleFile("Vietnamese", ele4.url))
                                                }
                                            }
                                        }
                                    }
                                }
                            } catch(e: Exception) {
                            }
                            try {
                                ele2.stream_links?.forEach { ele3 ->
                                    if (ele3.type == "hls") {
                                        val headers = ele3.request_headers?.mapNotNull { ele4 ->
                                            val key = ele4.key
                                            val value = ele4.value
                                            if (key != null && value != null) key to value else null
                                        }?.toMap() ?: emptyMap()
                                        val refererValue = ele3.request_headers?.firstOrNull { it.key == "Referer" }?.value ?: ""
                                        callback.invoke(
                                            ExtractorLink(
                                                name,
                                                if (nameSource != ele3.name) nameSource + " " + ele3.name else nameSource,
                                                ele3.url,
                                                referer = refererValue,
                                                quality = Qualities.Unknown.value,
                                                isM3u8 = true,
                                                headers = headers,
                                            )
                                        )
                                        ele3.subtitles?.forEach { ele4 ->
                                            if (ele4.label.contains("Việt", ignoreCase = true)) {
                                                subtitleCallback.invoke(SubtitleFile("Vietnamese", ele4.url))
                                            }
                                        }
                                    }
                                }
                            } catch(e: Exception) {
                            }
                        }
                    }
                }
            }
        }
        if (linkData.url.startsWith("http")) {
            val responseDetail = app.get(linkData.url, headers = headers).parsedSafe<ResponseDetail>()
            val responseChanel = app.get(linkData.url, headers = headers).parsedSafe<ResponseChanel>()
            responseDetail?.sources?.let { processStreams(it) }
            responseChanel?.channel?.sources?.let { processStreams(it) }
        } else {
            val responseDetail = tryParseJson<ResponseDetail>(linkData.url)
            val responseChanel = tryParseJson<ResponseChanel>(linkData.url)
            responseDetail?.sources?.let { processStreams(it) }
            responseChanel?.channel?.sources?.let { processStreams(it) }
        }

        return true
    }

    @Suppress("ObjectLiteralToLambda")
    override fun getVideoInterceptor(extractorLink: ExtractorLink): Interceptor? {
        return object : Interceptor {
            override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
                val request = chain.request()

                return chain.proceed(request)
            }
        }
    }
}