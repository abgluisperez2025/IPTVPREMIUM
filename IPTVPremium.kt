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
            "Ingresa la URL de tu lista M3U"
        )
    )

    companion object {
        private val mutex = Mutex()
        private val okHttpClient by lazy { OkHttpClient.Builder().build() }
        @Volatile private var categoriesCache: Map<String, List<PlaylistItem>>? = null
        @Volatile private var cacheTimestamp: Long = 0L
        private const val CACHE_TTL_MS = 5 * 60 * 1000L
        private val ATTRIBUTES_REGEX = """(\w+)="([^"]*)""".toRegex()
    }

    private fun getPlaylistUrl(): String {
        return try {
            val customUrl = getKey<String>("customPlaylistUrl")
            if (!customUrl.isNullOrBlank()) customUrl else mainUrl
        } catch (e: Exception) {
            mainUrl
        }
    }

    private suspend fun obtenerCategorias(): Map<String, List<PlaylistItem>> = withContext(Dispatchers.IO) {
        categoriesCache?.let {
            if (System.currentTimeMillis() - cacheTimestamp < CACHE_TTL_MS) return@withContext it
        }
        mutex.withLock {
            categoriesCache?.let {
                if (System.currentTimeMillis() - cacheTimestamp < CACHE_TTL_MS) return@withLock it
            }
            Log.d("IPTVPremium", "Leyendo lista por streaming (línea por línea)")
            val url = getPlaylistUrl()
            val categorias = mutableMapOf<String, MutableList<PlaylistItem>>()

            val previousCache = categoriesCache
            try {
                val request = Request.Builder().url(url).build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
                    val body = response.body ?: throw Exception("Sin body")
                    body.byteStream().bufferedReader().use { reader ->
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.isBlank()) continue
                            if (!line.trimStart().startsWith("#EXTINF:")) continue

                            val attrs = line.extractAttributes()
                            val titulo = line.substringAfterLast(",").trim()
                            var nextLine: String? = null
                            while (true) {
                                val candidate = reader.readLine() ?: break
                                if (candidate.isBlank()) continue
                                if (candidate.trimStart().startsWith("#")) continue
                                nextLine = candidate.trim()
                                break
                            }

                            if (!nextLine.isNullOrBlank()) {
                                val categoria = attrs["group-title"].orEmpty().trim().ifBlank { "Sin categoría" }
                                val item = PlaylistItem(
                                    title = titulo,
                                    url = nextLine,
                                    category = categoria,
                                    poster = attrs["tvg-logo"].orEmpty(),
                                    country = attrs["tvg-country"].orEmpty()
                                )
                                categorias.getOrPut(categoria) { mutableListOf() }.add(item)
                            }
                        }
                    }
                }
                Log.d("IPTVPremium", "Lectura completada: ${categorias.size} categorías")
            } catch (e: Exception) {
                Log.e("IPTVPremium", "Error leyendo lista: ${e.message}")
                return@withContext previousCache ?: emptyMap()
            }

            categoriesCache = categorias
            cacheTimestamp = System.currentTimeMillis()
            categorias
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val categorias = obtenerCategorias()
        val secciones = categorias.map { (titulo, items) ->
            HomePageList(titulo, items.map { it.toSearchResponse(this) }, isHorizontalImages = true)
        }
        return newHomePageResponse(secciones, hasNext = false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val categorias = obtenerCategorias()
        if (query.isBlank()) return emptyList()

        val q = query
        val resultados = mutableListOf<SearchResponse>()
        for (items in categorias.values) {
            for (item in items) {
                if (item.title.contains(q, ignoreCase = true)) {
                    resultados.add(item.toSearchResponse(this))
                }
            }
        }
        return resultados
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse {
        val datos   = obtenerDatos(url)
        val categorias = obtenerCategorias()
        val itemsEnCategoria = categorias[datos.categoria] ?: emptyList()
        val recomendaciones = itemsEnCategoria
            .filter { it.title.toString() != datos.nombre }
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
        val datos = obtenerDatos(data)

        callback.invoke(
            newExtractorLink(
                source = this.name,
                name   = datos.nombre,
                url    = datos.url,
                type   = ExtractorLinkType.M3U8
            ) {
                this.quality = Qualities.Unknown.value
            }
        )
        return true
    }

    private fun String.extractAttributes(): Map<String, String> {
        val attrs = mutableMapOf<String, String>()
        ATTRIBUTES_REGEX.findAll(this).forEach { match ->
            attrs[match.groupValues[1]] = match.groupValues[2]
        }
        return attrs
    }

    private fun PlaylistItem.toSearchResponse(api: MainAPI): LiveSearchResponse {
        return api.newLiveSearchResponse(
            title,
            DatosCanal(url, title, poster, category, country).toJson(),
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
        return runCatching {
            parseJson<DatosCanal>(data)
        }.getOrElse {
            DatosCanal(
                url = data,
                nombre = "Canal",
                poster = "",
                categoria = "",
                pais = ""
            )
        }
    }
}

data class PlaylistItem(
    val title    : String,
    val url      : String,
    val category : String,
    val poster   : String = "",
    val country  : String = ""
)
