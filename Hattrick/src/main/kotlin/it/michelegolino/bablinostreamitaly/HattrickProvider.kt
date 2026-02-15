package it.michelegolino.bablinostreamitaly

import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LiveSearchResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.VPNStatus
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newLiveSearchResponse
import com.lagradost.cloudstream3.newLiveStreamLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor

class HattrickProvider : MainAPI() {
    override var mainUrl = "https://htsport.ws"
    override var name = "Hattrick"
    override val supportedTypes = setOf(TvType.Live)
    override var lang = "it"
    override val hasMainPage = true
    override val vpnStatus = VPNStatus.MightBeNeeded
    override val hasDownloadSupport = false
    override val instantLinkLoading = false

    @Suppress("ConstPropertyName")
    companion object {
        private const val poster =
            "https://raw.githubusercontent.com/michelegolino/bablinostreamitaly/refs/heads/master/Hattrick/hattrick.png"
        
        // Lista dei canali disponibili
        private val channels = mapOf(
            "dazn1" to "DAZN 1",
            "dazn1hd" to "DAZN 1 HD",
            "live2" to "Live 2",
            "live7" to "Live 7"
        )
    }

    private fun buildChannelList(): List<LiveSearchResponse> {
        return channels.map { (id, name) ->
            val channelUrl = "$mainUrl/$id.htm"
            newLiveSearchResponse(name, channelUrl) {
                posterUrl = poster
            }
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val channelList = buildChannelList()
        val sections = listOf(
            HomePageList(
                "Hattrick Sport Channels",
                channelList,
                isHorizontalImages = false
            )
        )
        return newHomePageResponse(sections, false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val channelList = buildChannelList()
        return channelList.filter {
            query.lowercase() in it.name.lowercase()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val channelName = channels.entries.find { 
            url.contains(it.key) 
        }?.value ?: "Hattrick Channel"
        
        return newLiveStreamLoadResponse(channelName, url, url) {
            posterUrl = poster
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        // Usa l'extractor personalizzato
        return loadExtractor(data, null, subtitleCallback, callback)
    }
}
