package com.abgluisperez

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.InputStream

class IPTVPremium : MainAPI() {
    override var mainUrl            = "https://raw.githubusercontent.com/abgluisperez2025/IPTVPREMIUM/builds/IPTVPREMIUM_optimizada.m3u"
    override var name               = "IPTV Premium"
    override val hasMainPage        = true
    override var lang               = "es"
    override val hasQuickSearch     = true
    override val hasDownloadSupport = false
    override val supportedTypes     = setOf(TvType.Live)

    override val settingsForProvider = listOf(
        CustomSite(
            "customPlaylistUrl",
            "URL personalizada de lista M3U",
            "https://raw.githubusercontent.com/abgluisperez2025/IPTVPREMIUM/builds/IPTVPREMIUM_optimizada.m3u",
            "Si deseas usar una lista diferente, ingresa la URL aquí"
        )
    )

    // CACHE EN MEMORIA: descarga y parsea la lista UNA sola vez.
    companion object {
        private var cachePlaylist: Playlist? = null
        private var cacheTimestamp: Long = 0L
        private const val CACHE_TTL_MS = 30 * 60 * 1000L
        private val mutex = Mutex()
    }

    private fun getPlaylistUrl(): String {
        return try {
            val customUrl = getKey<String>("customPlaylistUrl")
            if (!customUrl.isNullOrBlank()) customUrl else mainUrl
        } catch (e: Exception) {
            mainUrl
        }
    }

    private suspend fun obtenerPlaylist(): Playlist {
        cachePlaylist?.let {
            if (System.currentTimeMillis() - cacheTimestamp < CACHE_TTL_MS) return it
        }
        return mutex.withLock {
            cachePlaylist?.let {
                if (System.currentTimeMillis() - cacheTimestamp < CACHE_TTL_MS) return@withLock it
            }
            Log.d("IPTVPremium", "Descargando lista")
            val url = getPlaylistUrl()
            
            // Intenta streaming primero (mejor para archivos grandes)
            val playlist = try {
                Log.d("IPTVPremium", "Intentando descarga en streaming")
                val response = app.get(url)
                IptvPlaylistParser().parseM3U(response.body.byteStream())
            } catch (e: Exception) {
                Log.w("IPTVPremium", "Streaming falló, intentando método alternativo: ${e.message}")
                try {
                    // Fallback: descarga completa pero con timeout
                    val response = app.get(url, timeout = 120L)
                    val content = response.text
                    IptvPlaylistParser().parseM3U(content)
                } catch (e2: Exception) {
                    Log.e("IPTVPremium", "Ambos métodos fallaron: ${e2.message}")
                    Playlist(emptyList())
                }
            }
            
            cachePlaylist  = playlist
            cacheTimestamp = System.currentTimeMillis()
            playlist
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val canales = obtenerPlaylist()
        val secciones = canales.items
            .groupBy { it.attributes["group-title"] ?: "Sin categoría" }
            .map { (titulo, grupo) ->
                HomePageList(titulo, grupo.map { it.toSearchResponse(this) }, isHorizontalImages = true)
            }
        return newHomePageResponse(secciones, hasNext = false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val canales = obtenerPlaylist()
        val q = query.lowercase()
        return canales.items
            .filter { it.title?.lowercase()?.contains(q) == true }
            .map { it.toSearchResponse(this) }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse {
        val datos   = obtenerDatos(url)
        val canales = obtenerPlaylist()
        val recomendaciones = canales.items
            .filter { it.attributes["group-title"].toString() == datos.categoria && it.title.toString() != datos.nombre }
            .take(30)
            .map { it.toSearchResponse(this) }

        return newLiveStreamLoadResponse(datos.nombre, datos.url, url) {
            this.posterUrl       = datos.poster
            this.plot            = "📺 ${datos.categoria}"
            this.tags            = listOf(datos.categoria, datos.pais)
            this.recommendations = recomendaciones
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val datos   = obtenerDatos(data)
        val canales = obtenerPlaylist()
        val canal   = canales.items.firstOrNull { it.url == datos.url }

        callback.invoke(
            newExtractorLink(
                source = this.name,
                name   = datos.nombre,
                url    = datos.url,
                type   = ExtractorLinkType.M3U8
            ) {
                this.referer = canal?.headers?.get("referrer") ?: ""
                this.quality = Qualities.Unknown.value
                this.headers = canal?.headers ?: emptyMap()
            }
        )
        return true
    }

    private fun PlaylistItem.toSearchResponse(api: MainAPI): LiveSearchResponse {
        val streamUrl = url.toString()
        val nombre    = title.toString()
        val poster    = attributes["tvg-logo"].toString()
        val categoria = attributes["group-title"].toString()
        val pais      = attributes["tvg-country"].toString()

        return api.newLiveSearchResponse(
            nombre,
            DatosCanal(streamUrl, nombre, poster, categoria, pais).toJson(),
            type = TvType.Live
        ) {
            this.posterUrl = poster
        }
    }

    data class DatosCanal(
        val url: String,
        val nombre: String,
        val poster: String,
        val categoria: String,
        val pais: String
    )

    private suspend fun obtenerDatos(data: String): DatosCanal {
        if (data.startsWith("{")) return parseJson<DatosCanal>(data)
        val canales = obtenerPlaylist()
        val canal   = canales.items.first { it.url == data }
        return DatosCanal(
            url       = canal.url.toString(),
            nombre    = canal.title.toString(),
            poster    = canal.attributes["tvg-logo"].toString(),
            categoria = canal.attributes["group-title"].toString(),
            pais      = canal.attributes["tvg-country"].toString()
        )
    }
}

data class Playlist(val items: List<PlaylistItem> = emptyList())

data class PlaylistItem(
    val title     : String?              = null,
    val attributes: Map<String, String>  = emptyMap(),
    val headers   : Map<String, String>  = emptyMap(),
    val url       : String?              = null,
    val userAgent : String?              = null
)

class IptvPlaylistParser {

    fun parseM3U(content: String): Playlist = parseM3U(content.byteInputStream())

    @Throws(PlaylistParserException::class)
    fun parseM3U(input: InputStream): Playlist {
        val reader = input.bufferedReader()

        var firstLine = reader.readLine()
        while (firstLine != null && firstLine.isBlank()) {
            firstLine = reader.readLine()
        }
        if (firstLine == null || !firstLine.trimStart('\uFEFF', ' ').startsWith(EXT_M3U)) {
            throw PlaylistParserException.InvalidHeader()
        }

        val items: MutableList<PlaylistItem> = mutableListOf()
        var index = 0
        var line: String? = reader.readLine()

        while (line != null) {
            if (line.isNotEmpty()) {
                when {
                    line.startsWith(EXT_INF) -> {
                        items.add(PlaylistItem(line.getTitle(), line.getAttributes()))
                    }
                    line.startsWith(EXT_VLC_OPT) -> {
                        if (items.isNotEmpty()) {
                            val item      = items[index]
                            val userAgent = item.userAgent ?: line.getTagValue("http-user-agent")
                            val referrer  = line.getTagValue("http-referrer")
                            val headers   = mutableMapOf<String, String>()
                            if (userAgent != null) headers["user-agent"] = userAgent
                            if (referrer  != null) headers["referrer"]   = referrer
                            items[index]  = item.copy(userAgent = userAgent, headers = headers)
                        }
                    }
                    !line.startsWith("#") -> {
                        if (items.isNotEmpty()) {
                            val item      = items[index]
                            val url       = line.getUrl()
                            val userAgent = line.getUrlParameter("user-agent")
                            val referrer  = line.getUrlParameter("referer")
                            val urlHdrs   = if (referrer != null) item.headers + mapOf("referrer" to referrer) else item.headers
                            items[index]  = item.copy(url = url, headers = item.headers + urlHdrs, userAgent = userAgent ?: item.userAgent)
                            index++
                        }
                    }
                }
            }
            line = reader.readLine()
        }
        return Playlist(items)
    }

    private fun String.replaceQuotesAndTrim()  = replace("\"", "").trim()

    private fun String.getTitle(): String? =
        split(",").lastOrNull()?.replaceQuotesAndTrim()

    private fun String.getUrl(): String? =
        split("|").firstOrNull()?.replaceQuotesAndTrim()

    private fun String.getUrlParameter(key: String): String? {
        val urlRegex     = Regex("^(.*)\\|", RegexOption.IGNORE_CASE)
        val keyRegex     = Regex("$key=(\\w[^&]*)", RegexOption.IGNORE_CASE)
        val paramsString = replace(urlRegex, "").replaceQuotesAndTrim()
        return keyRegex.find(paramsString)?.groups?.get(1)?.value
    }

    private fun String.getAttributes(): Map<String, String> {
        val extInfRegex      = Regex("(#EXTINF:.?[0-9]+)", RegexOption.IGNORE_CASE)
        val attributesString = replace(extInfRegex, "").replaceQuotesAndTrim().split(",").first()
        return attributesString.split(Regex("\\s")).mapNotNull {
            val pair = it.split("=")
            if (pair.size == 2) pair.first() to pair.last().replaceQuotesAndTrim() else null
        }.toMap()
    }

    private fun String.getTagValue(key: String): String? {
        val keyRegex = Regex("$key=(.*)", RegexOption.IGNORE_CASE)
        return keyRegex.find(this)?.groups?.get(1)?.value?.replaceQuotesAndTrim()
    }

    companion object {
        const val EXT_M3U     = "#EXTM3U"
        const val EXT_INF     = "#EXTINF"
        const val EXT_VLC_OPT = "#EXTVLCOPT"
    }
}

sealed class PlaylistParserException(message: String) : Exception(message) {
    class InvalidHeader : PlaylistParserException("Archivo M3U inválido")
}
