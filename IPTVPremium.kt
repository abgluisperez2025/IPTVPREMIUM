class IPTVPremium : MainAPI() {
    override var name = "TEST IPTV"
    override var mainUrl = ""
    override var lang = "es"
    override val supportedTypes = setOf(TvType.Live)

    override suspend fun search(query: String): List<SearchResponse> {
        return emptyList()
    }
}
