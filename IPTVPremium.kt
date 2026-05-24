class IPTVPremium : MainAPI() {

    override var name = "IPTV Premium"
    override var mainUrl = "https://google.com"
    override var lang = "es"

    override val hasMainPage = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Live)

}
