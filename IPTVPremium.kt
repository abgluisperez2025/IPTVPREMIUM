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
            "Ingresa la URL de tu lista M3U"
        )
    )

    companion object {
        private val mutex = Mutex()
        private var categoriesCache: Map<String, List<PlaylistItem>>? = null
        private var cacheTimestamp: Long = 0L
        private const val CACHE_TTL_MS = 30 * 60 * 1000L
    }

    private fun getPlaylistUrl(): String {
        return try {
            val customUrl = getKey<String>("customPlaylistUrl")
            if (!customUrl.isNullOrBlank()) customUrl else mainUrl
        } catch (e: Exception) {
            mainUrl
        }
    }

    private suspend fun obtenerCategorias(): Map<String, List<PlaylistItem>> {
        categoriesCache?.let {
            if (System.currentTimeMillis() - cacheTimestamp < CACHE_TTL_MS) return it
        }
        return mutex.withLock {
            categoriesCache?.let {
                if (System.currentTimeMillis() - cacheTimestamp < CACHE_TTL_MS) return@withLock it
            }
            Log.d("IPTVPremium", "Leyendo lista por streaming (línea por línea)")
            val url = getPlaylistUrl()
            val categorias = mutableMapOf<String, MutableList<PlaylistItem>>()
            
            try {
                val response = app.get(url)
                response.body.byteStream().bufferedReader().use { reader ->
                    var line = reader.readLine()
                    while (line != null) {
                        if (line.isNotBlank()) {
                            when {
                                line.startsWith("#EXTINF:") -> {
                                    val attrs = line.extractAttributes()
                                    val titulo = line.substringAfterLast(",").trim()
                                    val nextLine = reader.readLine()?.trim()
                                    
                                    if (!nextLine.isNullOrBlank() && !nextLine.startsWith("#")) {
                                        val categoria = attrs["group-title"] ?: "Sin categoría"
                                        val item = PlaylistItem(
                                            title = titulo,
                                            attributes = attrs,
                                            url = nextLine
                                        )
                                        categorias.getOrPut(categoria) { mutableListOf() }.add(item)
                                        line = reader.readLine()
                                        continue
                                    }
                                }
                            }
                        }
                        line = reader.readLine()
                    }
                }
                Log.d("IPTVPremium", "Lectura completada: ${categorias.size} categorías")
            } catch (e: Exception) {
                Log.e("IPTVPremium", "Error leyendo lista: ${e.message}")
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
        val q = query.lowercase()
        return categorias.values
            .flatten()
            .filter { it.title?.lowercase()?.contains(q) == true }
            .map { it.toSearchResponse(this) }
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
        val regex = """(\w+)="([^"]*)"""".toRegex()
        regex.findAll(this).forEach { match ->
            attrs[match.groupValues[1]] = match.groupValues[2]
        }
        return attrs
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
        return if (data.startsWith("{")) {
            parseJson<DatosCanal>(data)
        } else {
            // Si es una URL, devolverla como está
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
    val title     : String? = null,
    val attributes: Map<String, String> = emptyMap(),
    val url       : String? = null
)
